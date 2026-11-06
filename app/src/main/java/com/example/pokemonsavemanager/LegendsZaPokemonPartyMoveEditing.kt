package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class LegendsZaPokemonPartyMoveDirection(val label: String) {
    TO_PARTY("Move to Party"),
    TO_BOX("Move to PC Box")
}

data class LegendsZaPokemonPartyMoveSelection(
    val pokemon: LegendsZaPokemonRead,
    val direction: LegendsZaPokemonPartyMoveDirection,
    val sourceArea: LegendsZaPokemonArea,
    val sourceBox: Int,
    val sourcePosition: Int,
    val destinationBox: Int,
    val destinationPosition: Int,
    val partyCountBefore: Int,
    val partyCountAfter: Int
) {
    val sourceLocation: String get() = if (sourceArea == LegendsZaPokemonArea.BOX)
        "Box $sourceBox • Slot $sourcePosition" else "Party $sourcePosition"
    val destinationLocation: String get() =
        if (direction == LegendsZaPokemonPartyMoveDirection.TO_PARTY)
            "Party $destinationPosition" else "Box $destinationBox • Slot $destinationPosition"
}

/** Moves one exact native PA9 between Legends: Z-A party and PC storage in a reviewed new copy. */
object LegendsZaPokemonPartyMoveEditing {
    private const val MAX_FILE_SIZE = 0x309FB3
    private const val FOOTER_SIZE = 32
    private const val PARTY_CAPACITY = 6

    private data class DesiredBlocks(
        val boxes: ByteArray,
        val party: ByteArray
    )

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val remaining = MAX_FILE_SIZE + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= MAX_FILE_SIZE) {
                "The document exceeds the supported Legends: Z-A save size."
            }
        }
        output.toByteArray()
    }

    fun readSaveBytes(input: InputStream): ByteArray = readDocumentBytes(input).also {
        require(LegendsZaSaveValidation.inspect(it) is LegendsZaValidation.Verified) {
            "A validated native Legends: Z-A save is required."
        }
        LegendsZaStorageReadOnly.read(it)
    }

    fun inspect(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsZaPokemonArea,
        box: Int,
        position: Int
    ): LegendsZaPokemonPartyMoveSelection {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = LegendsZaSaveValidation.readPokemonBlocks(original)
        val count = partyCount(blocks)
        requirePartyRecords(blocks, count)
        val sourceRecord = targetRecord(blocks, area, box, position)
        val pokemon = inspectRecord(sourceRecord)
        return if (area == LegendsZaPokemonArea.BOX) {
            require(count < PARTY_CAPACITY) {
                "The native Legends: Z-A party already contains six Pokémon."
            }
            require(LegendsZaPokemonReadOnly.read(
                partyRecordBytes(blocks, count + 1)
            ) is LegendsZaSlotRead.Empty) {
                "The next native Legends: Z-A party slot is not empty."
            }
            LegendsZaPokemonPartyMoveSelection(
                pokemon, LegendsZaPokemonPartyMoveDirection.TO_PARTY,
                area, box, position, 0, count + 1, count, count + 1
            )
        } else {
            require(count > 1) {
                "At least one Pokémon must remain in the native Legends: Z-A party."
            }
            val storage = LegendsZaStorageReadOnly.read(original)
            val destination = (0 until 32).asSequence()
                .flatMap { current -> storage.box(current).asSequence() }
                .firstOrNull { it.read is LegendsZaSlotRead.Empty }
                ?: throw IllegalArgumentException(
                    "All 960 native Legends: Z-A PC-box slots are occupied."
                )
            LegendsZaPokemonPartyMoveSelection(
                pokemon, LegendsZaPokemonPartyMoveDirection.TO_BOX,
                area, 0, position, destination.box + 1, destination.position,
                count, count - 1
            )
        }
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsZaPokemonArea,
        box: Int,
        position: Int
    ): LegendsZaPokemonPartyMoveCopyPlan {
        val original = source.copyOf()
        val selection = inspect(original, expectedSha256, area, box, position)
        val before = LegendsZaSaveValidation.readPokemonBlocks(original)
        val sourceRecord = targetRecord(before, area, box, position)
        val desired = desiredBlocks(before, selection, sourceRecord)
        val written = LegendsZaSaveValidation.writePokemonBlocks(
            original, desired.boxes, desired.party
        )
        verify(
            original, written.bytes, selection,
            written.boxesAbsoluteOffset, written.partyAbsoluteOffset
        )
        return LegendsZaPokemonPartyMoveCopyPlan(
            original, written.bytes, sourceRecord, selection,
            written.boxesAbsoluteOffset, written.partyAbsoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        selection: LegendsZaPokemonPartyMoveSelection,
        boxesAbsoluteOffset: Int,
        partyAbsoluteOffset: Int
    ) {
        val beforeStorage = LegendsZaStorageReadOnly.read(original)
        val afterStorage = LegendsZaStorageReadOnly.read(edited)
        require(original.size == edited.size && beforeStorage.version == afterStorage.version &&
            beforeStorage.revision == afterStorage.revision) {
            "The moved copy changed native title/revision identity or save size."
        }
        val before = LegendsZaSaveValidation.readPokemonBlocks(original)
        val after = LegendsZaSaveValidation.readPokemonBlocks(edited)
        require(partyCount(before) == selection.partyCountBefore &&
            partyCount(after) == selection.partyCountAfter) {
            "The native party count did not reopen with the reviewed value."
        }
        requirePartyRecords(before, selection.partyCountBefore)
        requirePartyRecords(after, selection.partyCountAfter)
        val sourceRecord = targetRecord(
            before, selection.sourceArea, selection.sourceBox, selection.sourcePosition
        )
        require(inspectRecord(sourceRecord) == selection.pokemon) {
            "The selected source PA9 changed before verification."
        }
        if (selection.direction == LegendsZaPokemonPartyMoveDirection.TO_BOX) {
            require(LegendsZaPokemonReadOnly.read(boxRecord(
                before, selection.destinationBox, selection.destinationPosition
            )) is LegendsZaSlotRead.Empty) {
                "The reviewed native PC-box destination is no longer empty."
            }
        }
        val desired = desiredBlocks(before, selection, sourceRecord)
        require(after.boxes.contentEquals(desired.boxes) &&
            after.party.contentEquals(desired.party)) {
            "The native party and PC storage did not reopen in the reviewed state."
        }
        val reopenedRecord = if (
            selection.direction == LegendsZaPokemonPartyMoveDirection.TO_PARTY
        ) partyRecord(after, selection.destinationPosition)
        else boxRecord(after, selection.destinationBox, selection.destinationPosition)
        require(reopenedRecord.contentEquals(sourceRecord) &&
            inspectRecord(reopenedRecord) == selection.pokemon) {
            "The exact selected PA9 did not reopen at its reviewed destination."
        }

        val boxRelativeOffset = if (
            selection.direction == LegendsZaPokemonPartyMoveDirection.TO_PARTY
        ) boxOffset(selection.sourceBox, selection.sourcePosition)
        else boxOffset(selection.destinationBox, selection.destinationPosition)
        val size = LegendsZaPokemonReadOnly.RECORD_SIZE
        val partyBodyOffsets = if (
            selection.direction == LegendsZaPokemonPartyMoveDirection.TO_PARTY
        ) {
            listOf(selection.partyCountBefore * LegendsZaStorageReadOnly.PARTY_STRIDE)
        } else {
            (selection.sourcePosition - 1 until selection.partyCountBefore).map {
                it * LegendsZaStorageReadOnly.PARTY_STRIDE
            }
        }
        val hashOffset = original.size - FOOTER_SIZE
        require(original.indices.all { index ->
            index in boxesAbsoluteOffset + boxRelativeOffset until
                boxesAbsoluteOffset + boxRelativeOffset + size ||
                partyBodyOffsets.any { offset ->
                    index in partyAbsoluteOffset + offset until
                        partyAbsoluteOffset + offset + size
                } ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) {
            "An unrelated encrypted Legends: Z-A save byte changed, including a native slot footer."
        }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun desiredBlocks(
        before: LegendsZaPokemonBlocks,
        selection: LegendsZaPokemonPartyMoveSelection,
        sourceRecord: ByteArray
    ): DesiredBlocks {
        val boxes = before.boxes.copyOf()
        val party = before.party.copyOf()
        val size = LegendsZaPokemonReadOnly.RECORD_SIZE
        if (selection.direction == LegendsZaPokemonPartyMoveDirection.TO_PARTY) {
            ByteArray(size).copyInto(
                boxes, boxOffset(selection.sourceBox, selection.sourcePosition)
            )
            sourceRecord.copyInto(
                party, selection.partyCountBefore * LegendsZaStorageReadOnly.PARTY_STRIDE
            )
        } else {
            sourceRecord.copyInto(
                boxes, boxOffset(selection.destinationBox, selection.destinationPosition)
            )
            val sourceIndex = selection.sourcePosition - 1
            for (index in sourceIndex until selection.partyCountBefore - 1) {
                party.copyInto(
                    party,
                    index * LegendsZaStorageReadOnly.PARTY_STRIDE,
                    (index + 1) * LegendsZaStorageReadOnly.PARTY_STRIDE,
                    (index + 1) * LegendsZaStorageReadOnly.PARTY_STRIDE + size
                )
            }
            ByteArray(size).copyInto(
                party,
                (selection.partyCountBefore - 1) * LegendsZaStorageReadOnly.PARTY_STRIDE
            )
        }
        return DesiredBlocks(boxes, party)
    }

    private fun partyCount(blocks: LegendsZaPokemonBlocks): Int {
        val count = (0 until PARTY_CAPACITY).firstOrNull {
            !LegendsZaStorageReadOnly.hasNativePresence(
                blocks.party, it * LegendsZaStorageReadOnly.PARTY_STRIDE
            )
        } ?: PARTY_CAPACITY
        require(count in 1..PARTY_CAPACITY && (count until PARTY_CAPACITY).all {
            !LegendsZaStorageReadOnly.hasNativePresence(
                blocks.party, it * LegendsZaStorageReadOnly.PARTY_STRIDE
            )
        }) { "The native Legends: Z-A party must be one contiguous prefix of one through six Pokémon." }
        return count
    }

    private fun requirePartyRecords(blocks: LegendsZaPokemonBlocks, count: Int) {
        require((1..count).all { position ->
            LegendsZaPokemonReadOnly.read(
                partyRecordBytes(blocks, position)
            ) is LegendsZaSlotRead.Pokemon
        }) {
            "Every occupied native Legends: Z-A party record must pass its PA9 checksum."
        }
    }

    private fun targetRecord(
        blocks: LegendsZaPokemonBlocks,
        area: LegendsZaPokemonArea,
        box: Int,
        position: Int
    ): ByteArray = if (area == LegendsZaPokemonArea.BOX) {
        boxRecord(blocks, box, position)
    } else partyRecord(blocks, position)

    private fun boxRecord(
        blocks: LegendsZaPokemonBlocks,
        box: Int,
        position: Int
    ): ByteArray {
        require(box in 1..32 && position in 1..30) {
            "The selected native Legends: Z-A PC-box slot is outside storage."
        }
        val offset = boxOffset(box, position)
        return blocks.boxes.copyOfRange(
            offset, offset + LegendsZaPokemonReadOnly.RECORD_SIZE
        )
    }

    private fun partyRecord(
        blocks: LegendsZaPokemonBlocks,
        position: Int
    ): ByteArray {
        require(position in 1..partyCount(blocks)) {
            "The selected native Legends: Z-A party slot is not occupied."
        }
        return partyRecordBytes(blocks, position)
    }

    private fun partyRecordBytes(
        blocks: LegendsZaPokemonBlocks,
        position: Int
    ): ByteArray {
        require(position in 1..PARTY_CAPACITY) {
            "The native Legends: Z-A party slot is outside the six-slot party."
        }
        val offset = (position - 1) * LegendsZaStorageReadOnly.PARTY_STRIDE
        return blocks.party.copyOfRange(
            offset, offset + LegendsZaPokemonReadOnly.RECORD_SIZE
        )
    }

    private fun boxOffset(box: Int, position: Int): Int =
        ((box - 1) * 30 + position - 1) * LegendsZaStorageReadOnly.BOX_STRIDE

    private fun inspectRecord(record: ByteArray): LegendsZaPokemonRead {
        require(record.size == LegendsZaPokemonReadOnly.RECORD_SIZE) {
            "A complete 344-byte native PA9 record is required."
        }
        val pokemon = (LegendsZaPokemonReadOnly.read(record)
            as? LegendsZaSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA9 is not checksum-valid.")
        require(!pokemon.isEgg) {
            "Egg PA9 records cannot be moved between the party and PC Box."
        }
        require(pokemon.cachedLevel in 1..100 &&
            pokemon.nature in 0..24 && pokemon.statNature in 0..24) {
            "The selected PA9 Level or nature data is outside Legends: Z-A native limits."
        }
        require(pokemon.currentHandler in 0..1) {
            "The selected PA9 current-handler identity is invalid."
        }
        require(pokemon.ivs.all { it in 0..31 }) {
            "The selected PA9 IV values exceed native limits."
        }
        require(pokemon.evs.all { it in 0..252 } && pokemon.evs.sum() <= 510) {
            "The selected PA9 EV values exceed native per-stat or total limits."
        }
        require(pokemon.hyperTrainingFlags and 0xC0 == 0) {
            "The selected PA9 hyper-training flags contain unsupported bits."
        }
        val maximumExperience = runCatching {
            LegendsZaPokemonLevelEditing.maximumExperience(pokemon)
        }.getOrElse {
            throw IllegalArgumentException(
                "This PA9 species/form has no uniquely verified native Legends: Z-A profile."
            )
        }
        require(pokemon.experience <= maximumExperience) {
            "The selected PA9 experience exceeds the verified Level 100 limit for this species/form."
        }
        return pokemon
    }
}

class LegendsZaPokemonPartyMoveCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    val selection: LegendsZaPokemonPartyMoveSelection,
    val boxesAbsoluteOffset: Int,
    val partyAbsoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    val sourceSha256 = LegendsZaPokemonPartyMoveEditing.sha256(this.original)
    val sourceRecordSha256 = LegendsZaPokemonPartyMoveEditing.sha256(this.sourceRecord)
    val editedSha256 = LegendsZaPokemonPartyMoveEditing.sha256(this.edited)
    private val movedBox: Int =
        if (selection.direction == LegendsZaPokemonPartyMoveDirection.TO_PARTY)
            selection.sourceBox else selection.destinationBox
    private val movedBoxPosition: Int =
        if (selection.direction == LegendsZaPokemonPartyMoveDirection.TO_PARTY)
            selection.sourcePosition else selection.destinationPosition
    val boxAbsoluteSlotOffset: Int = boxesAbsoluteOffset +
        ((movedBox - 1) * 30 + movedBoxPosition - 1) *
            LegendsZaStorageReadOnly.BOX_STRIDE
    val partyAbsoluteBodyOffsets: List<Int> = if (
        selection.direction == LegendsZaPokemonPartyMoveDirection.TO_PARTY
    ) listOf(
        partyAbsoluteOffset + selection.partyCountBefore * LegendsZaStorageReadOnly.PARTY_STRIDE
    ) else (selection.sourcePosition - 1 until selection.partyCountBefore).map {
        partyAbsoluteOffset + it * LegendsZaStorageReadOnly.PARTY_STRIDE
    }
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This move review was already used. Reopen Pokémon Boxes." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) {
            "Choose a new empty document; existing files cannot be replaced."
        }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) {
            "The recovery backup did not verify. Nothing was written."
        }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LegendsZaPokemonPartyMoveEditing.verify(
                original, edited, selection, boxesAbsoluteOffset, partyAbsoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed party move."
            }
            LegendsZaPokemonPartyMoveEditing.verify(
                original, persisted, selection, boxesAbsoluteOffset, partyAbsoluteOffset
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Party move verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}

