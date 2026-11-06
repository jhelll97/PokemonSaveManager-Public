package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class ScarletVioletPokemonDeleteSelection(
    val pokemon: ScarletVioletPokemonRead,
    val sourceArea: ScarletVioletPokemonArea,
    val sourceBox: Int,
    val sourcePosition: Int,
    val partyCountBefore: Int,
    val partyCountAfter: Int
) {
    val sourceLocation: String get() = if (sourceArea == ScarletVioletPokemonArea.BOX)
        "Box $sourceBox • Slot $sourcePosition" else "Party $sourcePosition"
}

/** Deletes one native PK9 from Scarlet/Violet party or PC storage in a reviewed new copy. */
object ScarletVioletPokemonDeleteEditing {
    private const val MAX_FILE_SIZE = 0x43B2E7
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
    ): ScarletVioletPokemonDeleteSelection {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val count = partyCount(blocks)
        requirePartyRecords(blocks, count)
        val pokemon = inspectRecord(targetRecord(blocks, area, box, position))
        val after = if (area == ScarletVioletPokemonArea.PARTY) {
            require(count > 1) { "At least one Pokémon must remain in the native Scarlet/Violet party." }
            count - 1
        } else count
        return ScarletVioletPokemonDeleteSelection(pokemon, area, box, position, count, after)
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int
    ): ScarletVioletPokemonDeleteCopyPlan {
        val original = source.copyOf()
        val selection = inspect(original, expectedSha256, area, box, position)
        val before = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val sourceRecord = targetRecord(before, area, box, position)
        val boxes = before.boxes.copyOf()
        val party = before.party.copyOf()
        if (area == ScarletVioletPokemonArea.BOX) {
            val start = boxOffset(box, position)
            boxes.fill(0, start, start + ScarletVioletPokemonReadOnly.RECORD_SIZE)
        } else compactParty(party, selection)
        val written = ScarletVioletSaveValidation.writePokemonBlocks(original, boxes, party)
        verify(
            original, written.bytes, selection,
            written.boxesAbsoluteOffset, written.partyAbsoluteOffset
        )
        return ScarletVioletPokemonDeleteCopyPlan(
            original, written.bytes, sourceRecord, selection,
            written.boxesAbsoluteOffset, written.partyAbsoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        selection: ScarletVioletPokemonDeleteSelection,
        boxesAbsoluteOffset: Int,
        partyAbsoluteOffset: Int
    ) {
        val beforeStorage = ScarletVioletStorageReadOnly.read(original)
        val afterStorage = ScarletVioletStorageReadOnly.read(edited)
        require(beforeStorage.version == afterStorage.version && original.size == edited.size) {
            "The deleted copy changed native title identity or save size."
        }
        require(beforeStorage.rideLegend == afterStorage.rideLegend) {
            "The dedicated native ride Pokémon changed during deletion."
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
        val size = ScarletVioletPokemonReadOnly.RECORD_SIZE
        if (selection.sourceArea == ScarletVioletPokemonArea.BOX) {
            val relativeOffset = boxOffset(selection.sourceBox, selection.sourcePosition)
            require(ScarletVioletPokemonReadOnly.read(after.boxes.copyOfRange(
                relativeOffset, relativeOffset + size
            )) is ScarletVioletSlotRead.Empty) {
                "The reviewed native PC-box slot did not reopen empty."
            }
            require(before.party.contentEquals(after.party)) {
                "Native party data changed during boxed Pokémon deletion."
            }
            require(before.boxes.indices.all { index ->
                index in relativeOffset until relativeOffset + size ||
                    before.boxes[index] == after.boxes[index]
            }) { "An unrelated native PC-box slot changed." }
            val hashOffset = original.size - 32
            val absoluteOffset = boxesAbsoluteOffset + relativeOffset
            require(original.indices.all { index ->
                index in absoluteOffset until absoluteOffset + size ||
                    index in hashOffset until original.size || original[index] == edited[index]
            }) {
                "An unrelated encrypted Scarlet/Violet save byte changed, including ride Pokémon data."
            }
        } else {
            require(before.boxes.contentEquals(after.boxes)) {
                "Native PC-box data changed during party Pokémon deletion."
            }
            val desired = before.party.copyOf().also { compactParty(it, selection) }
            require(after.party.contentEquals(desired)) {
                "The native party did not reopen in the reviewed compacted state."
            }
            val sourceIndex = selection.sourcePosition - 1
            require((sourceIndex until selection.partyCountBefore - 1).all { oldIndex ->
                val oldStart = (oldIndex + 1) * size
                val newStart = oldIndex * size
                before.party.copyOfRange(oldStart, oldStart + size)
                    .contentEquals(after.party.copyOfRange(newStart, newStart + size))
            }) { "A retained native party PK9 changed during compaction." }
            val clearedStart = (selection.partyCountBefore - 1) * size
            require(after.party.copyOfRange(clearedStart, clearedStart + size).all { it == 0.toByte() }) {
                "The released native party tail slot was not cleared."
            }
            val countOffset = PARTY_CAPACITY * size
            val hashOffset = original.size - 32
            val changedStart = partyAbsoluteOffset + sourceIndex * size
            val changedEnd = partyAbsoluteOffset + selection.partyCountBefore * size
            require(original.indices.all { index ->
                index in changedStart until changedEnd || index == partyAbsoluteOffset + countOffset ||
                    index in hashOffset until original.size || original[index] == edited[index]
            }) {
                "An unrelated encrypted Scarlet/Violet save byte changed, including ride Pokémon data."
            }
        }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun compactParty(
        party: ByteArray,
        selection: ScarletVioletPokemonDeleteSelection
    ) {
        require(selection.sourceArea == ScarletVioletPokemonArea.PARTY) {
            "Party compaction requires a native party source."
        }
        val size = ScarletVioletPokemonReadOnly.RECORD_SIZE
        val sourceIndex = selection.sourcePosition - 1
        for (index in sourceIndex until selection.partyCountBefore - 1) {
            party.copyInto(party, index * size, (index + 1) * size, (index + 2) * size)
        }
        party.fill(0, (selection.partyCountBefore - 1) * size, selection.partyCountBefore * size)
        party[PARTY_CAPACITY * size] = selection.partyCountAfter.toByte()
    }

    private fun partyCount(blocks: ScarletVioletPokemonBlocks): Int {
        val count = blocks.party[PARTY_CAPACITY * ScarletVioletPokemonReadOnly.RECORD_SIZE].toInt() and 255
        require(count in 1..PARTY_CAPACITY) {
            "The native Scarlet/Violet party count must be one through six."
        }
        return count
    }

    private fun requirePartyRecords(blocks: ScarletVioletPokemonBlocks, count: Int) {
        require((1..count).all { position ->
            ScarletVioletPokemonReadOnly.read(partyRecordBytes(blocks, position)) is ScarletVioletSlotRead.Pokemon
        }) { "Every occupied native Scarlet/Violet party record must pass its PK9 checksum before deletion." }
    }

    private fun targetRecord(
        blocks: ScarletVioletPokemonBlocks,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int
    ): ByteArray = if (area == ScarletVioletPokemonArea.BOX) boxRecord(blocks, box, position)
        else partyRecord(blocks, position)

    private fun boxRecord(blocks: ScarletVioletPokemonBlocks, box: Int, position: Int): ByteArray {
        require(box in 1..32 && position in 1..30) {
            "The selected native Scarlet/Violet PC-box slot is outside storage."
        }
        val offset = boxOffset(box, position)
        return blocks.boxes.copyOfRange(offset, offset + ScarletVioletPokemonReadOnly.RECORD_SIZE)
    }

    private fun partyRecord(blocks: ScarletVioletPokemonBlocks, position: Int): ByteArray {
        val count = partyCount(blocks)
        require(position in 1..count) { "The selected native Scarlet/Violet party slot is not occupied." }
        return partyRecordBytes(blocks, position)
    }

    private fun partyRecordBytes(blocks: ScarletVioletPokemonBlocks, position: Int): ByteArray {
        require(position in 1..PARTY_CAPACITY) { "The native Scarlet/Violet party slot is outside the six-slot party." }
        val offset = (position - 1) * ScarletVioletPokemonReadOnly.RECORD_SIZE
        return blocks.party.copyOfRange(offset, offset + ScarletVioletPokemonReadOnly.RECORD_SIZE)
    }

    private fun boxOffset(box: Int, position: Int): Int =
        ((box - 1) * 30 + position - 1) * ScarletVioletPokemonReadOnly.RECORD_SIZE

    private fun inspectRecord(record: ByteArray): ScarletVioletPokemonRead {
        require(record.size == ScarletVioletPokemonReadOnly.RECORD_SIZE) {
            "A complete 344-byte native PK9 record is required."
        }
        val pokemon = (ScarletVioletPokemonReadOnly.read(record) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK9 is not checksum-valid.")
        require(!pokemon.isEgg) { "Egg PK9 records remain protected from deletion." }
        require(pokemon.cachedLevel in 1..100 && pokemon.nature in 0..24 && pokemon.statNature in 0..24) {
            "The selected PK9 Level or nature data is outside Scarlet/Violet native limits."
        }
        require(pokemon.currentHandler in 0..1) {
            "The selected PK9 current-handler identity is invalid."
        }
        require(pokemon.evs.all { it in 0..252 } && pokemon.evs.sum() <= 510) {
            "The selected PK9 EV values exceed Scarlet/Violet's native per-stat or total limit."
        }
        require(pokemon.ivs.all { it in 0..31 }) {
            "The selected PK9 IV values exceed native limits."
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

class ScarletVioletPokemonDeleteCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    val selection: ScarletVioletPokemonDeleteSelection,
    val boxesAbsoluteOffset: Int,
    val partyAbsoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    val sourceSha256 = ScarletVioletPokemonDeleteEditing.sha256(this.original)
    val sourceRecordSha256 = ScarletVioletPokemonDeleteEditing.sha256(this.sourceRecord)
    val editedSha256 = ScarletVioletPokemonDeleteEditing.sha256(this.edited)
    val changedAbsoluteStart: Int = if (selection.sourceArea == ScarletVioletPokemonArea.BOX) {
        boxesAbsoluteOffset + ((selection.sourceBox - 1) * 30 + selection.sourcePosition - 1) *
            ScarletVioletPokemonReadOnly.RECORD_SIZE
    } else {
        partyAbsoluteOffset + (selection.sourcePosition - 1) * ScarletVioletPokemonReadOnly.RECORD_SIZE
    }
    val changedAbsoluteEndExclusive: Int = if (selection.sourceArea == ScarletVioletPokemonArea.BOX) {
        changedAbsoluteStart + ScarletVioletPokemonReadOnly.RECORD_SIZE
    } else {
        partyAbsoluteOffset + selection.partyCountBefore * ScarletVioletPokemonReadOnly.RECORD_SIZE
    }
    val partyCountAbsoluteOffset: Int = if (selection.sourceArea == ScarletVioletPokemonArea.PARTY)
        partyAbsoluteOffset + PARTY_CAPACITY * ScarletVioletPokemonReadOnly.RECORD_SIZE else -1
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This delete review was already used. Reopen Pokémon Boxes." }
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
            ScarletVioletPokemonDeleteEditing.verify(
                original, edited, selection, boxesAbsoluteOffset, partyAbsoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Pokémon deletion."
            }
            ScarletVioletPokemonDeleteEditing.verify(
                original, persisted, selection, boxesAbsoluteOffset, partyAbsoluteOffset
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Pokémon deletion verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }

    private companion object { const val PARTY_CAPACITY = 6 }
}
