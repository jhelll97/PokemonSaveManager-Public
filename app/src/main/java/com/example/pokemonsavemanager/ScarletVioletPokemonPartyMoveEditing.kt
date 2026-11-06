package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class ScarletVioletPokemonPartyMoveDirection(val label: String) {
    TO_PARTY("Move to Party"),
    TO_BOX("Move to PC Box")
}

data class ScarletVioletPokemonPartyMoveSelection(
    val pokemon: ScarletVioletPokemonRead,
    val direction: ScarletVioletPokemonPartyMoveDirection,
    val sourceArea: ScarletVioletPokemonArea,
    val sourceBox: Int,
    val sourcePosition: Int,
    val destinationBox: Int,
    val destinationPosition: Int,
    val partyCountBefore: Int,
    val partyCountAfter: Int
) {
    val sourceLocation: String get() = if (sourceArea == ScarletVioletPokemonArea.BOX)
        "Box $sourceBox • Slot $sourcePosition" else "Party $sourcePosition"
    val destinationLocation: String get() =
        if (direction == ScarletVioletPokemonPartyMoveDirection.TO_PARTY)
            "Party $destinationPosition" else "Box $destinationBox • Slot $destinationPosition"
}

/** Moves one exact native PK9 between Scarlet/Violet party and PC storage in a reviewed new copy. */
object ScarletVioletPokemonPartyMoveEditing {
    private const val MAX_FILE_SIZE = 0x43B2E7
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
                "The document exceeds the supported Scarlet/Violet save size."
            }
        }
        output.toByteArray()
    }

    fun readSaveBytes(input: InputStream): ByteArray = readDocumentBytes(input).also {
        require(ScarletVioletSaveValidation.inspect(it) is ScarletVioletValidation.Verified) {
            "A validated native Scarlet/Violet save is required."
        }
        ScarletVioletStorageReadOnly.read(it)
    }

    fun inspect(
        source: ByteArray,
        expectedSha256: String,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int
    ): ScarletVioletPokemonPartyMoveSelection {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val count = partyCount(blocks)
        requirePartyRecords(blocks, count)
        val sourceRecord = targetRecord(blocks, area, box, position)
        val pokemon = inspectRecord(sourceRecord)
        return if (area == ScarletVioletPokemonArea.BOX) {
            require(count < PARTY_CAPACITY) {
                "The native Scarlet/Violet party already contains six Pokémon."
            }
            require(ScarletVioletPokemonReadOnly.read(
                partyRecordBytes(blocks, count + 1)
            ) is ScarletVioletSlotRead.Empty) {
                "The next native Scarlet/Violet party slot is not empty."
            }
            ScarletVioletPokemonPartyMoveSelection(
                pokemon, ScarletVioletPokemonPartyMoveDirection.TO_PARTY,
                area, box, position, 0, count + 1, count, count + 1
            )
        } else {
            require(count > 1) {
                "At least one Pokémon must remain in the native Scarlet/Violet party."
            }
            val storage = ScarletVioletStorageReadOnly.read(original)
            val destination = (0 until 32).asSequence()
                .flatMap { current -> storage.box(current).asSequence() }
                .firstOrNull { it.read is ScarletVioletSlotRead.Empty }
                ?: throw IllegalArgumentException(
                    "All 960 native Scarlet/Violet PC-box slots are occupied."
                )
            ScarletVioletPokemonPartyMoveSelection(
                pokemon, ScarletVioletPokemonPartyMoveDirection.TO_BOX,
                area, 0, position, destination.box + 1, destination.position,
                count, count - 1
            )
        }
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int
    ): ScarletVioletPokemonPartyMoveCopyPlan {
        val original = source.copyOf()
        val selection = inspect(original, expectedSha256, area, box, position)
        val before = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val sourceRecord = targetRecord(before, area, box, position)
        val desired = desiredBlocks(before, selection, sourceRecord)
        val written = ScarletVioletSaveValidation.writePokemonBlocks(
            original, desired.boxes, desired.party
        )
        verify(
            original, written.bytes, selection,
            written.boxesAbsoluteOffset, written.partyAbsoluteOffset
        )
        return ScarletVioletPokemonPartyMoveCopyPlan(
            original, written.bytes, sourceRecord, selection,
            written.boxesAbsoluteOffset, written.partyAbsoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        selection: ScarletVioletPokemonPartyMoveSelection,
        boxesAbsoluteOffset: Int,
        partyAbsoluteOffset: Int
    ) {
        val beforeStorage = ScarletVioletStorageReadOnly.read(original)
        val afterStorage = ScarletVioletStorageReadOnly.read(edited)
        require(original.size == edited.size && beforeStorage.version == afterStorage.version) {
            "The moved copy changed native title identity or save size."
        }
        require(beforeStorage.rideLegend == afterStorage.rideLegend) {
            "The dedicated native ride Pokémon changed during the party move."
        }
        val before = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val after = ScarletVioletSaveValidation.readPokemonBlocks(edited)
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
            "The selected source PK9 changed before verification."
        }
        if (selection.direction == ScarletVioletPokemonPartyMoveDirection.TO_BOX) {
            require(ScarletVioletPokemonReadOnly.read(boxRecord(
                before, selection.destinationBox, selection.destinationPosition
            )) is ScarletVioletSlotRead.Empty) {
                "The reviewed native PC-box destination is no longer empty."
            }
        }
        val desired = desiredBlocks(before, selection, sourceRecord)
        require(after.boxes.contentEquals(desired.boxes) &&
            after.party.contentEquals(desired.party)) {
            "The native party and PC storage did not reopen in the reviewed state."
        }
        val reopenedRecord = if (
            selection.direction == ScarletVioletPokemonPartyMoveDirection.TO_PARTY
        ) partyRecord(after, selection.destinationPosition)
        else boxRecord(after, selection.destinationBox, selection.destinationPosition)
        require(reopenedRecord.contentEquals(sourceRecord) &&
            inspectRecord(reopenedRecord) == selection.pokemon) {
            "The exact selected PK9 did not reopen at its reviewed destination."
        }

        val boxRelativeOffset = if (
            selection.direction == ScarletVioletPokemonPartyMoveDirection.TO_PARTY
        ) boxOffset(selection.sourceBox, selection.sourcePosition)
        else boxOffset(selection.destinationBox, selection.destinationPosition)
        val size = ScarletVioletPokemonReadOnly.RECORD_SIZE
        val partyRange = if (
            selection.direction == ScarletVioletPokemonPartyMoveDirection.TO_PARTY
        ) {
            val start = selection.partyCountBefore * size
            start until start + size
        } else {
            val start = (selection.sourcePosition - 1) * size
            start until selection.partyCountBefore * size
        }
        val countRelativeOffset = PARTY_CAPACITY * size
        val hashOffset = original.size - FOOTER_SIZE
        require(original.indices.all { index ->
            index in boxesAbsoluteOffset + boxRelativeOffset until
                boxesAbsoluteOffset + boxRelativeOffset + size ||
                index in partyAbsoluteOffset + partyRange.first until
                    partyAbsoluteOffset + partyRange.last + 1 ||
                index == partyAbsoluteOffset + countRelativeOffset ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) {
            "An unrelated encrypted Scarlet/Violet save byte changed, including ride Pokémon data."
        }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun desiredBlocks(
        before: ScarletVioletPokemonBlocks,
        selection: ScarletVioletPokemonPartyMoveSelection,
        sourceRecord: ByteArray
    ): DesiredBlocks {
        val boxes = before.boxes.copyOf()
        val party = before.party.copyOf()
        val size = ScarletVioletPokemonReadOnly.RECORD_SIZE
        if (selection.direction == ScarletVioletPokemonPartyMoveDirection.TO_PARTY) {
            ByteArray(size).copyInto(
                boxes, boxOffset(selection.sourceBox, selection.sourcePosition)
            )
            sourceRecord.copyInto(party, selection.partyCountBefore * size)
        } else {
            sourceRecord.copyInto(
                boxes, boxOffset(selection.destinationBox, selection.destinationPosition)
            )
            val sourceIndex = selection.sourcePosition - 1
            for (index in sourceIndex until selection.partyCountBefore - 1) {
                party.copyInto(
                    party, index * size, (index + 1) * size, (index + 2) * size
                )
            }
            ByteArray(size).copyInto(
                party, (selection.partyCountBefore - 1) * size
            )
        }
        party[PARTY_CAPACITY * size] = selection.partyCountAfter.toByte()
        return DesiredBlocks(boxes, party)
    }

    private fun partyCount(blocks: ScarletVioletPokemonBlocks): Int {
        val count = blocks.party[
            PARTY_CAPACITY * ScarletVioletPokemonReadOnly.RECORD_SIZE
        ].toInt() and 255
        require(count in 1..PARTY_CAPACITY) {
            "The native Scarlet/Violet party count must be one through six."
        }
        return count
    }

    private fun requirePartyRecords(blocks: ScarletVioletPokemonBlocks, count: Int) {
        require((1..count).all { position ->
            ScarletVioletPokemonReadOnly.read(
                partyRecordBytes(blocks, position)
            ) is ScarletVioletSlotRead.Pokemon
        }) {
            "Every occupied native Scarlet/Violet party record must pass its PK9 checksum."
        }
    }

    private fun targetRecord(
        blocks: ScarletVioletPokemonBlocks,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int
    ): ByteArray = if (area == ScarletVioletPokemonArea.BOX) {
        boxRecord(blocks, box, position)
    } else partyRecord(blocks, position)

    private fun boxRecord(
        blocks: ScarletVioletPokemonBlocks,
        box: Int,
        position: Int
    ): ByteArray {
        require(box in 1..32 && position in 1..30) {
            "The selected native Scarlet/Violet PC-box slot is outside storage."
        }
        val offset = boxOffset(box, position)
        return blocks.boxes.copyOfRange(
            offset, offset + ScarletVioletPokemonReadOnly.RECORD_SIZE
        )
    }

    private fun partyRecord(
        blocks: ScarletVioletPokemonBlocks,
        position: Int
    ): ByteArray {
        require(position in 1..partyCount(blocks)) {
            "The selected native Scarlet/Violet party slot is not occupied."
        }
        return partyRecordBytes(blocks, position)
    }

    private fun partyRecordBytes(
        blocks: ScarletVioletPokemonBlocks,
        position: Int
    ): ByteArray {
        require(position in 1..PARTY_CAPACITY) {
            "The native Scarlet/Violet party slot is outside the six-slot party."
        }
        val offset = (position - 1) * ScarletVioletPokemonReadOnly.RECORD_SIZE
        return blocks.party.copyOfRange(
            offset, offset + ScarletVioletPokemonReadOnly.RECORD_SIZE
        )
    }

    private fun boxOffset(box: Int, position: Int): Int =
        ((box - 1) * 30 + position - 1) * ScarletVioletPokemonReadOnly.RECORD_SIZE

    private fun inspectRecord(record: ByteArray): ScarletVioletPokemonRead {
        require(record.size == ScarletVioletPokemonReadOnly.RECORD_SIZE) {
            "A complete 344-byte native PK9 record is required."
        }
        val pokemon = (ScarletVioletPokemonReadOnly.read(record)
            as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK9 is not checksum-valid.")
        require(!pokemon.isEgg) {
            "Egg PK9 records cannot be moved between the party and PC Box."
        }
        require(pokemon.cachedLevel in 1..100 &&
            pokemon.nature in 0..24 && pokemon.statNature in 0..24) {
            "The selected PK9 Level or nature data is outside Scarlet/Violet native limits."
        }
        require(pokemon.currentHandler in 0..1) {
            "The selected PK9 current-handler identity is invalid."
        }
        require(pokemon.ivs.all { it in 0..31 }) {
            "The selected PK9 IV values exceed native limits."
        }
        require(pokemon.evs.all { it in 0..252 } && pokemon.evs.sum() <= 510) {
            "The selected PK9 EV values exceed native per-stat or total limits."
        }
        require(pokemon.hyperTrainingFlags and 0xC0 == 0) {
            "The selected PK9 hyper-training flags contain unsupported bits."
        }
        val maximumExperience = runCatching {
            ScarletVioletPokemonLevelEditing.maximumExperience(pokemon)
        }.getOrElse {
            throw IllegalArgumentException(
                "This PK9 species/form has no uniquely verified native Scarlet/Violet profile."
            )
        }
        require(pokemon.experience <= maximumExperience) {
            "The selected PK9 experience exceeds the verified Level 100 limit for this species/form."
        }
        return pokemon
    }
}

class ScarletVioletPokemonPartyMoveCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    val selection: ScarletVioletPokemonPartyMoveSelection,
    val boxesAbsoluteOffset: Int,
    val partyAbsoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    val sourceSha256 = ScarletVioletPokemonPartyMoveEditing.sha256(this.original)
    val sourceRecordSha256 = ScarletVioletPokemonPartyMoveEditing.sha256(this.sourceRecord)
    val editedSha256 = ScarletVioletPokemonPartyMoveEditing.sha256(this.edited)
    private val movedBox: Int =
        if (selection.direction == ScarletVioletPokemonPartyMoveDirection.TO_PARTY)
            selection.sourceBox else selection.destinationBox
    private val movedBoxPosition: Int =
        if (selection.direction == ScarletVioletPokemonPartyMoveDirection.TO_PARTY)
            selection.sourcePosition else selection.destinationPosition
    val boxAbsoluteSlotOffset: Int = boxesAbsoluteOffset +
        ((movedBox - 1) * 30 + movedBoxPosition - 1) *
            ScarletVioletPokemonReadOnly.RECORD_SIZE
    val partyAbsoluteRangeStart: Int = partyAbsoluteOffset +
        (if (selection.direction == ScarletVioletPokemonPartyMoveDirection.TO_PARTY)
            selection.partyCountBefore else selection.sourcePosition - 1) *
            ScarletVioletPokemonReadOnly.RECORD_SIZE
    val partyAbsoluteRangeEndExclusive: Int = partyAbsoluteOffset +
        selection.partyCountBefore * ScarletVioletPokemonReadOnly.RECORD_SIZE +
        if (selection.direction == ScarletVioletPokemonPartyMoveDirection.TO_PARTY)
            ScarletVioletPokemonReadOnly.RECORD_SIZE else 0
    val partyCountAbsoluteOffset: Int = partyAbsoluteOffset +
        6 * ScarletVioletPokemonReadOnly.RECORD_SIZE
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
            ScarletVioletPokemonPartyMoveEditing.verify(
                original, edited, selection, boxesAbsoluteOffset, partyAbsoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed party move."
            }
            ScarletVioletPokemonPartyMoveEditing.verify(
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

