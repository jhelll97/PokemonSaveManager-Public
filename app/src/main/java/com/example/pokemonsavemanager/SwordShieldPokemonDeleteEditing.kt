package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class SwordShieldPokemonDeleteSelection(
    val pokemon: SwordShieldPokemonRead,
    val sourceArea: SwordShieldPokemonArea,
    val sourceBox: Int,
    val sourcePosition: Int,
    val partyCountBefore: Int,
    val partyCountAfter: Int
) {
    val sourceLocation: String get() = if (sourceArea == SwordShieldPokemonArea.BOX)
        "Box $sourceBox • Slot $sourcePosition" else "Party $sourcePosition"
}

/** Deletes one native PK8 from Sword/Shield party or PC storage in a reviewed new copy. */
object SwordShieldPokemonDeleteEditing {
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
    ): SwordShieldPokemonDeleteSelection {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val count = partyCount(blocks)
        requirePartyRecords(blocks, count)
        val pokemon = inspectRecord(targetRecord(blocks, area, box, position))
        val after = if (area == SwordShieldPokemonArea.PARTY) {
            require(count > 1) { "At least one Pokémon must remain in the native Sword/Shield party." }
            count - 1
        } else count
        return SwordShieldPokemonDeleteSelection(pokemon, area, box, position, count, after)
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int
    ): SwordShieldPokemonDeleteCopyPlan {
        val original = source.copyOf()
        val selection = inspect(original, expectedSha256, area, box, position)
        val before = SwordShieldSaveValidation.readPokemonBlocks(original)
        val sourceRecord = targetRecord(before, area, box, position)
        val edited: ByteArray
        val boxesAbsoluteOffset: Int
        val partyAbsoluteOffset: Int
        if (area == SwordShieldPokemonArea.BOX) {
            val written = SwordShieldSaveValidation.writeBoxRecord(
                original, box, position, ByteArray(SwordShieldPokemonReadOnly.RECORD_SIZE)
            )
            edited = written.bytes
            boxesAbsoluteOffset = written.absoluteOffset - boxOffset(box, position)
            partyAbsoluteOffset = -1
        } else {
            val party = desiredParty(before, selection)
            val written = SwordShieldSaveValidation.writePokemonBlocks(
                original, before.boxes, party
            )
            edited = written.bytes
            boxesAbsoluteOffset = written.boxesAbsoluteOffset
            partyAbsoluteOffset = written.partyAbsoluteOffset
        }
        verify(original, edited, selection, boxesAbsoluteOffset, partyAbsoluteOffset)
        return SwordShieldPokemonDeleteCopyPlan(
            original, edited, sourceRecord, selection, boxesAbsoluteOffset, partyAbsoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        selection: SwordShieldPokemonDeleteSelection,
        boxesAbsoluteOffset: Int,
        partyAbsoluteOffset: Int
    ) {
        val before = SwordShieldSaveValidation.readPokemonBlocks(original)
        val after = SwordShieldSaveValidation.readPokemonBlocks(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The deleted copy changed native title identity or save size."
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
        val size = SwordShieldPokemonReadOnly.RECORD_SIZE
        if (selection.sourceArea == SwordShieldPokemonArea.BOX) {
            val relativeOffset = boxOffset(selection.sourceBox, selection.sourcePosition)
            require(SwordShieldPokemonReadOnly.read(after.boxes.copyOfRange(
                relativeOffset, relativeOffset + size
            )) is SwordShieldSlotRead.Empty) {
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
            }) { "An unrelated encrypted Sword/Shield save byte changed." }
        } else {
            require(before.boxes.contentEquals(after.boxes)) {
                "Native PC-box data changed during party Pokémon deletion."
            }
            val desired = desiredParty(before, selection)
            require(after.party.contentEquals(desired)) {
                "The native party did not reopen in the reviewed compacted state."
            }
            val sourceIndex = selection.sourcePosition - 1
            require((sourceIndex until selection.partyCountBefore - 1).all { oldIndex ->
                val oldStart = (oldIndex + 1) * size
                val newStart = oldIndex * size
                before.party.copyOfRange(oldStart, oldStart + size)
                    .contentEquals(after.party.copyOfRange(newStart, newStart + size))
            }) { "A retained native party PK8 changed during compaction." }
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
            }) { "An unrelated encrypted Sword/Shield save byte changed." }
        }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun desiredParty(
        before: SwordShieldPokemonBlocks,
        selection: SwordShieldPokemonDeleteSelection
    ): ByteArray {
        require(selection.sourceArea == SwordShieldPokemonArea.PARTY) {
            "Party compaction requires a native party source."
        }
        val party = before.party.copyOf()
        val size = SwordShieldPokemonReadOnly.RECORD_SIZE
        val sourceIndex = selection.sourcePosition - 1
        for (index in sourceIndex until selection.partyCountBefore - 1) {
            before.party.copyInto(party, index * size, (index + 1) * size, (index + 2) * size)
        }
        party.fill(0, (selection.partyCountBefore - 1) * size, selection.partyCountBefore * size)
        party[PARTY_CAPACITY * size] = selection.partyCountAfter.toByte()
        return party
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
        }) { "Every occupied native Sword/Shield party record must pass its PK8 checksum before deletion." }
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
        require(!pokemon.isEgg) { "Egg PK8 records remain protected from deletion." }
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

class SwordShieldPokemonDeleteCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    val selection: SwordShieldPokemonDeleteSelection,
    val boxesAbsoluteOffset: Int,
    val partyAbsoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    val sourceSha256 = SwordShieldPokemonDeleteEditing.sha256(this.original)
    val sourceRecordSha256 = SwordShieldPokemonDeleteEditing.sha256(this.sourceRecord)
    val editedSha256 = SwordShieldPokemonDeleteEditing.sha256(this.edited)
    val changedAbsoluteStart: Int = if (selection.sourceArea == SwordShieldPokemonArea.BOX) {
        boxesAbsoluteOffset + ((selection.sourceBox - 1) * 30 + selection.sourcePosition - 1) *
            SwordShieldPokemonReadOnly.RECORD_SIZE
    } else {
        partyAbsoluteOffset + (selection.sourcePosition - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
    }
    val changedAbsoluteEndExclusive: Int = if (selection.sourceArea == SwordShieldPokemonArea.BOX) {
        changedAbsoluteStart + SwordShieldPokemonReadOnly.RECORD_SIZE
    } else {
        partyAbsoluteOffset + selection.partyCountBefore * SwordShieldPokemonReadOnly.RECORD_SIZE
    }
    val partyCountAbsoluteOffset: Int = if (partyAbsoluteOffset < 0) -1 else
        partyAbsoluteOffset + PARTY_CAPACITY * SwordShieldPokemonReadOnly.RECORD_SIZE
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
            SwordShieldPokemonDeleteEditing.verify(
                original, edited, selection, boxesAbsoluteOffset, partyAbsoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Pokémon deletion."
            }
            SwordShieldPokemonDeleteEditing.verify(
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
