package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class SwordShieldPokemonPartyMoveDirection(val label: String) {
    TO_PARTY("Move to Party"),
    TO_BOX("Move to PC Box")
}

data class SwordShieldPokemonPartyMoveSelection(
    val pokemon: SwordShieldPokemonRead,
    val direction: SwordShieldPokemonPartyMoveDirection,
    val sourceArea: SwordShieldPokemonArea,
    val sourceBox: Int,
    val sourcePosition: Int,
    val destinationBox: Int,
    val destinationPosition: Int,
    val partyCountBefore: Int,
    val partyCountAfter: Int
) {
    val sourceLocation: String get() = if (sourceArea == SwordShieldPokemonArea.BOX)
        "Box $sourceBox • Slot $sourcePosition" else "Party $sourcePosition"
    val destinationLocation: String get() = if (direction == SwordShieldPokemonPartyMoveDirection.TO_PARTY)
        "Party $destinationPosition" else "Box $destinationBox • Slot $destinationPosition"
}

/** Moves one exact native PK8 between the party and PC storage in a reviewed new copy. */
object SwordShieldPokemonPartyMoveEditing {
    private const val MAX_FILE_SIZE = 0x1876B1
    private const val PARTY_CAPACITY = 6

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
                "The document exceeds the supported Sword/Shield save size."
            }
        }
        output.toByteArray()
    }

    fun readSaveBytes(input: InputStream): ByteArray = readDocumentBytes(input).also {
        require(SwordShieldSaveValidation.inspect(it) is SwordShieldValidation.Verified) {
            "A validated native Sword/Shield save is required."
        }
    }

    fun inspect(
        source: ByteArray,
        expectedSha256: String,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int
    ): SwordShieldPokemonPartyMoveSelection {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val count = partyCount(blocks)
        requirePartyRecords(blocks, count)
        val sourceRecord = targetRecord(blocks, area, box, position)
        val pokemon = inspectRecord(sourceRecord)
        return if (area == SwordShieldPokemonArea.BOX) {
            require(count < PARTY_CAPACITY) { "The native Sword/Shield party already contains six Pokémon." }
            SwordShieldPokemonPartyMoveSelection(
                pokemon, SwordShieldPokemonPartyMoveDirection.TO_PARTY,
                area, box, position, 0, count + 1, count, count + 1
            )
        } else {
            require(count > 1) { "At least one Pokémon must remain in the native Sword/Shield party." }
            val storage = SwordShieldStorageReadOnly.read(original)
            val destination = (0 until 32).asSequence()
                .flatMap { current -> storage.box(current).asSequence() }
                .firstOrNull { it.read is SwordShieldSlotRead.Empty }
                ?: throw IllegalArgumentException("All 960 native Sword/Shield PC-box slots are occupied.")
            SwordShieldPokemonPartyMoveSelection(
                pokemon, SwordShieldPokemonPartyMoveDirection.TO_BOX,
                area, 0, position, destination.box + 1, destination.position,
                count, count - 1
            )
        }
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int
    ): SwordShieldPokemonPartyMoveCopyPlan {
        val original = source.copyOf()
        val selection = inspect(original, expectedSha256, area, box, position)
        val before = SwordShieldSaveValidation.readPokemonBlocks(original)
        val sourceRecord = targetRecord(before, area, box, position)
        val desired = desiredBlocks(before, selection, sourceRecord)
        val written = SwordShieldSaveValidation.writePokemonBlocks(
            original, desired.first, desired.second
        )
        verify(
            original, written.bytes, selection,
            written.boxesAbsoluteOffset, written.partyAbsoluteOffset
        )
        return SwordShieldPokemonPartyMoveCopyPlan(
            original, written.bytes, sourceRecord, selection,
            written.boxesAbsoluteOffset, written.partyAbsoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        selection: SwordShieldPokemonPartyMoveSelection,
        boxesAbsoluteOffset: Int,
        partyAbsoluteOffset: Int
    ) {
        val before = SwordShieldSaveValidation.readPokemonBlocks(original)
        val after = SwordShieldSaveValidation.readPokemonBlocks(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The moved copy changed native title identity or save size."
        }
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
            "The selected source PK8 changed before verification."
        }
        val desired = desiredBlocks(before, selection, sourceRecord)
        require(after.boxes.contentEquals(desired.first) && after.party.contentEquals(desired.second)) {
            "The native party and PC storage did not reopen in the reviewed state."
        }
        val reopenedRecord = if (selection.direction == SwordShieldPokemonPartyMoveDirection.TO_PARTY) {
            partyRecord(after, selection.destinationPosition)
        } else {
            boxRecord(after, selection.destinationBox, selection.destinationPosition)
        }
        require(reopenedRecord.contentEquals(sourceRecord) && inspectRecord(reopenedRecord) == selection.pokemon) {
            "The exact selected PK8 did not reopen at its reviewed destination."
        }

        val size = SwordShieldPokemonReadOnly.RECORD_SIZE
        val boxRelativeOffset = if (selection.direction == SwordShieldPokemonPartyMoveDirection.TO_PARTY)
            boxOffset(selection.sourceBox, selection.sourcePosition)
        else boxOffset(selection.destinationBox, selection.destinationPosition)
        val partyRange = if (selection.direction == SwordShieldPokemonPartyMoveDirection.TO_PARTY) {
            val start = selection.partyCountBefore * size
            start until start + size
        } else {
            val start = (selection.sourcePosition - 1) * size
            start until selection.partyCountBefore * size
        }
        val countRelativeOffset = PARTY_CAPACITY * size
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in boxesAbsoluteOffset + boxRelativeOffset until
                boxesAbsoluteOffset + boxRelativeOffset + size ||
                index in partyAbsoluteOffset + partyRange.first until
                    partyAbsoluteOffset + partyRange.last + 1 ||
                index == partyAbsoluteOffset + countRelativeOffset ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted Sword/Shield save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun desiredBlocks(
        before: SwordShieldPokemonBlocks,
        selection: SwordShieldPokemonPartyMoveSelection,
        sourceRecord: ByteArray
    ): Pair<ByteArray, ByteArray> {
        val boxes = before.boxes.copyOf()
        val party = before.party.copyOf()
        val size = SwordShieldPokemonReadOnly.RECORD_SIZE
        val countOffset = PARTY_CAPACITY * size
        if (selection.direction == SwordShieldPokemonPartyMoveDirection.TO_PARTY) {
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
            ByteArray(size).copyInto(party, (selection.partyCountBefore - 1) * size)
        }
        party[countOffset] = selection.partyCountAfter.toByte()
        return boxes to party
    }

    private fun partyCount(blocks: SwordShieldPokemonBlocks): Int {
        val count = blocks.party[PARTY_CAPACITY * SwordShieldPokemonReadOnly.RECORD_SIZE].toInt() and 255
        require(count in 1..PARTY_CAPACITY) {
            "The native Sword/Shield party count must be one through six."
        }
        return count
    }

    private fun requirePartyRecords(blocks: SwordShieldPokemonBlocks, count: Int) {
        require((1..count).all { position ->
            SwordShieldPokemonReadOnly.read(partyRecordBytes(blocks, position)) is SwordShieldSlotRead.Pokemon
        }) { "Every occupied native Sword/Shield party record must pass its PK8 checksum before this move." }
    }

    private fun targetRecord(
        blocks: SwordShieldPokemonBlocks,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int
    ): ByteArray = if (area == SwordShieldPokemonArea.BOX) boxRecord(blocks, box, position)
        else partyRecord(blocks, position)

    private fun boxRecord(blocks: SwordShieldPokemonBlocks, box: Int, position: Int): ByteArray {
        require(box in 1..32 && position in 1..30) {
            "The selected native Sword/Shield PC-box slot is outside storage."
        }
        val offset = boxOffset(box, position)
        return blocks.boxes.copyOfRange(offset, offset + SwordShieldPokemonReadOnly.RECORD_SIZE)
    }

    private fun partyRecord(blocks: SwordShieldPokemonBlocks, position: Int): ByteArray {
        val count = partyCount(blocks)
        require(position in 1..count) { "The selected native Sword/Shield party slot is not occupied." }
        return partyRecordBytes(blocks, position)
    }

    private fun partyRecordBytes(blocks: SwordShieldPokemonBlocks, position: Int): ByteArray {
        require(position in 1..PARTY_CAPACITY) { "The native Sword/Shield party slot is outside the six-slot party." }
        val offset = (position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
        return blocks.party.copyOfRange(offset, offset + SwordShieldPokemonReadOnly.RECORD_SIZE)
    }

    private fun boxOffset(box: Int, position: Int): Int =
        ((box - 1) * 30 + position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE

    private fun inspectRecord(record: ByteArray): SwordShieldPokemonRead {
        require(record.size == SwordShieldPokemonReadOnly.RECORD_SIZE) {
            "A complete 344-byte native PK8 record is required."
        }
        val pokemon = (SwordShieldPokemonReadOnly.read(record) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK8 is not checksum-valid.")
        require(!pokemon.isEgg) { "Egg PK8 records cannot be moved between the party and PC Box." }
        require(pokemon.cachedLevel in 1..100 && pokemon.nature in 0..24 && pokemon.statNature in 0..24) {
            "The selected PK8 Level or nature data is outside Sword/Shield's native limits."
        }
        require(pokemon.dynamaxLevel in 0..10) {
            "The selected PK8 Dynamax Level is outside Sword/Shield's native limit."
        }
        require(pokemon.evs.all { it in 0..252 } && pokemon.evs.sum() <= 510) {
            "The selected PK8 EV values exceed Sword/Shield's native per-stat or total limit."
        }
        require(pokemon.ivs.all { it in 0..31 } && pokemon.hyperTrainingFlags and 0xC0 == 0) {
            "The selected PK8 IV or hyper-training data exceeds Sword/Shield's native limits."
        }
        val maximumExperience = runCatching {
            SwordShieldPokemonLevelEditing.maximumExperience(pokemon)
        }.getOrElse {
            throw IllegalArgumentException(
                "This PK8 species/form has no uniquely verified native Sword/Shield profile."
            )
        }
        require(pokemon.experience <= maximumExperience) {
            "The selected PK8 experience exceeds the verified Level 100 limit for this species/form."
        }
        return pokemon
    }
}

class SwordShieldPokemonPartyMoveCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    val selection: SwordShieldPokemonPartyMoveSelection,
    val boxesAbsoluteOffset: Int,
    val partyAbsoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    val sourceSha256 = SwordShieldPokemonPartyMoveEditing.sha256(this.original)
    val sourceRecordSha256 = SwordShieldPokemonPartyMoveEditing.sha256(this.sourceRecord)
    val editedSha256 = SwordShieldPokemonPartyMoveEditing.sha256(this.edited)
    private val movedBox: Int = if (selection.direction == SwordShieldPokemonPartyMoveDirection.TO_PARTY)
        selection.sourceBox else selection.destinationBox
    private val movedBoxPosition: Int =
        if (selection.direction == SwordShieldPokemonPartyMoveDirection.TO_PARTY)
            selection.sourcePosition else selection.destinationPosition
    val boxAbsoluteSlotOffset: Int = boxesAbsoluteOffset +
        ((movedBox - 1) * 30 + movedBoxPosition - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
    val partyAbsoluteRangeStart: Int = partyAbsoluteOffset +
        (if (selection.direction == SwordShieldPokemonPartyMoveDirection.TO_PARTY)
            selection.partyCountBefore else selection.sourcePosition - 1) *
            SwordShieldPokemonReadOnly.RECORD_SIZE
    val partyAbsoluteRangeEndExclusive: Int = partyAbsoluteOffset +
        selection.partyCountBefore * SwordShieldPokemonReadOnly.RECORD_SIZE +
        if (selection.direction == SwordShieldPokemonPartyMoveDirection.TO_PARTY)
            SwordShieldPokemonReadOnly.RECORD_SIZE else 0
    val partyCountAbsoluteOffset: Int = partyAbsoluteOffset +
        6 * SwordShieldPokemonReadOnly.RECORD_SIZE
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
            SwordShieldPokemonPartyMoveEditing.verify(
                original, edited, selection, boxesAbsoluteOffset, partyAbsoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed party move."
            }
            SwordShieldPokemonPartyMoveEditing.verify(
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
