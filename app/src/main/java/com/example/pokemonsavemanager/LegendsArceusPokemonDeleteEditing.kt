package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class LegendsArceusPokemonDeleteSelection(
    val pokemon: LegendsArceusPokemonRead,
    val sourceArea: LegendsArceusPokemonArea,
    val sourcePasture: Int,
    val sourcePosition: Int,
    val partyCountBefore: Int,
    val partyCountAfter: Int
) {
    val sourceLocation: String get() = if (sourceArea == LegendsArceusPokemonArea.PASTURE)
        "Pasture $sourcePasture • Slot $sourcePosition" else "Party $sourcePosition"
}

/** Deletes one native PA8 from the Legends: Arceus party or pasture in a reviewed new copy. */
object LegendsArceusPokemonDeleteEditing {
    private const val MAX_FILE_SIZE = 0x13AD06
    private const val FOOTER_SIZE = 32
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
                "The document exceeds the supported Legends: Arceus save size."
            }
        }
        output.toByteArray()
    }

    fun readSaveBytes(input: InputStream): ByteArray = readDocumentBytes(input).also {
        require(LegendsArceusSaveValidation.inspect(it) is LegendsArceusValidation.Verified) {
            "A validated native Legends: Arceus save is required."
        }
        LegendsArceusStorageReadOnly.read(it)
    }

    fun inspect(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int
    ): LegendsArceusPokemonDeleteSelection {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val count = partyCount(blocks)
        requirePartyRecords(blocks, count)
        val pokemon = inspectRecord(targetRecord(blocks, area, pasture, position))
        val after = if (area == LegendsArceusPokemonArea.PARTY) {
            require(count > 1) {
                "At least one Pokémon must remain in the native Legends: Arceus party."
            }
            count - 1
        } else count
        return LegendsArceusPokemonDeleteSelection(
            pokemon, area, pasture, position, count, after
        )
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int
    ): LegendsArceusPokemonDeleteCopyPlan {
        val original = source.copyOf()
        val selection = inspect(original, expectedSha256, area, pasture, position)
        val before = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val sourceRecord = targetRecord(before, area, pasture, position)
        val pastures = before.pastures.copyOf()
        val party = before.party.copyOf()
        if (area == LegendsArceusPokemonArea.PASTURE) {
            val start = pastureOffset(pasture, position)
            pastures.fill(0, start, start + LegendsArceusPokemonReadOnly.STORED_SIZE)
        } else compactParty(party, selection)
        val written = LegendsArceusSaveValidation.writePokemonBlocks(original, pastures, party)
        verify(
            original, written.bytes, selection,
            written.pasturesAbsoluteOffset, written.partyAbsoluteOffset
        )
        return LegendsArceusPokemonDeleteCopyPlan(
            original, written.bytes, sourceRecord, selection,
            written.pasturesAbsoluteOffset, written.partyAbsoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        selection: LegendsArceusPokemonDeleteSelection,
        pasturesAbsoluteOffset: Int,
        partyAbsoluteOffset: Int
    ) {
        val before = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val after = LegendsArceusSaveValidation.readPokemonBlocks(edited)
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
            before, selection.sourceArea, selection.sourcePasture, selection.sourcePosition
        )
        require(inspectRecord(sourceRecord) == selection.pokemon) {
            "The selected source PA8 changed before verification."
        }
        val hashOffset = original.size - FOOTER_SIZE
        if (selection.sourceArea == LegendsArceusPokemonArea.PASTURE) {
            val relativeOffset = pastureOffset(selection.sourcePasture, selection.sourcePosition)
            require(LegendsArceusPokemonReadOnly.read(after.pastures.copyOfRange(
                relativeOffset,
                relativeOffset + LegendsArceusPokemonReadOnly.STORED_SIZE
            )) is LegendsArceusSlotRead.Empty) {
                "The reviewed native pasture slot did not reopen empty."
            }
            require(before.party.contentEquals(after.party)) {
                "Native party data changed during pasture Pokémon deletion."
            }
            require(before.pastures.indices.all { index ->
                index in relativeOffset until relativeOffset + LegendsArceusPokemonReadOnly.STORED_SIZE ||
                    before.pastures[index] == after.pastures[index]
            }) { "An unrelated native pasture slot changed." }
            val absoluteOffset = pasturesAbsoluteOffset + relativeOffset
            require(original.indices.all { index ->
                index in absoluteOffset until absoluteOffset + LegendsArceusPokemonReadOnly.STORED_SIZE ||
                    index in hashOffset until original.size || original[index] == edited[index]
            }) { "An unrelated encrypted Legends: Arceus save byte changed." }
        } else {
            require(before.pastures.contentEquals(after.pastures)) {
                "Native pasture data changed during party Pokémon deletion."
            }
            val desired = before.party.copyOf().also { compactParty(it, selection) }
            require(after.party.contentEquals(desired)) {
                "The native party did not reopen in the reviewed compacted state."
            }
            val size = LegendsArceusPokemonReadOnly.PARTY_SIZE
            val sourceIndex = selection.sourcePosition - 1
            require((sourceIndex until selection.partyCountBefore - 1).all { oldIndex ->
                val oldStart = (oldIndex + 1) * size
                val newStart = oldIndex * size
                before.party.copyOfRange(oldStart, oldStart + size)
                    .contentEquals(after.party.copyOfRange(newStart, newStart + size))
            }) { "A retained native party PA8 changed during compaction." }
            val clearedStart = (selection.partyCountBefore - 1) * size
            require(after.party.copyOfRange(clearedStart, clearedStart + size).all { it == 0.toByte() }) {
                "The released native party tail slot was not cleared."
            }
            val changedStart = partyAbsoluteOffset + sourceIndex * size
            val changedEnd = partyAbsoluteOffset + selection.partyCountBefore * size
            val countOffset = partyAbsoluteOffset + PARTY_CAPACITY * size
            require(original.indices.all { index ->
                index in changedStart until changedEnd || index == countOffset ||
                    index in hashOffset until original.size || original[index] == edited[index]
            }) { "An unrelated encrypted Legends: Arceus save byte changed." }
        }
        LegendsArceusStorageReadOnly.read(edited)
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun compactParty(
        party: ByteArray,
        selection: LegendsArceusPokemonDeleteSelection
    ) {
        require(selection.sourceArea == LegendsArceusPokemonArea.PARTY) {
            "Party compaction requires a native party source."
        }
        val size = LegendsArceusPokemonReadOnly.PARTY_SIZE
        val sourceIndex = selection.sourcePosition - 1
        for (index in sourceIndex until selection.partyCountBefore - 1) {
            party.copyInto(party, index * size, (index + 1) * size, (index + 2) * size)
        }
        party.fill(0, (selection.partyCountBefore - 1) * size, selection.partyCountBefore * size)
        party[PARTY_CAPACITY * size] = selection.partyCountAfter.toByte()
    }

    private fun partyCount(blocks: LegendsArceusPokemonBlocks): Int {
        val count = blocks.party[
            PARTY_CAPACITY * LegendsArceusPokemonReadOnly.PARTY_SIZE
        ].toInt() and 255
        require(count in 1..PARTY_CAPACITY) {
            "The native Legends: Arceus party count must be one through six."
        }
        return count
    }

    private fun requirePartyRecords(blocks: LegendsArceusPokemonBlocks, count: Int) {
        require((1..count).all { position ->
            LegendsArceusPokemonReadOnly.read(
                partyRecordBytes(blocks, position)
            ) is LegendsArceusSlotRead.Pokemon
        }) {
            "Every occupied native Legends: Arceus party record must pass its PA8 checksum."
        }
    }

    private fun targetRecord(
        blocks: LegendsArceusPokemonBlocks,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int
    ): ByteArray = if (area == LegendsArceusPokemonArea.PASTURE) {
        pastureRecord(blocks, pasture, position)
    } else partyRecord(blocks, position)

    private fun pastureRecord(
        blocks: LegendsArceusPokemonBlocks,
        pasture: Int,
        position: Int
    ): ByteArray {
        require(pasture in 1..32 && position in 1..30) {
            "The selected native Legends: Arceus pasture slot is outside storage."
        }
        val offset = pastureOffset(pasture, position)
        return blocks.pastures.copyOfRange(
            offset, offset + LegendsArceusPokemonReadOnly.STORED_SIZE
        )
    }

    private fun partyRecord(
        blocks: LegendsArceusPokemonBlocks,
        position: Int
    ): ByteArray {
        require(position in 1..partyCount(blocks)) {
            "The selected native Legends: Arceus party slot is not occupied."
        }
        return partyRecordBytes(blocks, position)
    }

    private fun partyRecordBytes(
        blocks: LegendsArceusPokemonBlocks,
        position: Int
    ): ByteArray {
        require(position in 1..PARTY_CAPACITY) {
            "The native Legends: Arceus party slot is outside the six-slot party."
        }
        val offset = (position - 1) * LegendsArceusPokemonReadOnly.PARTY_SIZE
        return blocks.party.copyOfRange(
            offset, offset + LegendsArceusPokemonReadOnly.PARTY_SIZE
        )
    }

    private fun pastureOffset(pasture: Int, position: Int): Int =
        ((pasture - 1) * 30 + position - 1) * LegendsArceusPokemonReadOnly.STORED_SIZE

    private fun inspectRecord(record: ByteArray): LegendsArceusPokemonRead {
        require(record.size == LegendsArceusPokemonReadOnly.STORED_SIZE ||
            record.size == LegendsArceusPokemonReadOnly.PARTY_SIZE) {
            "A complete 360-byte stored or 376-byte party PA8 record is required."
        }
        val pokemon = (LegendsArceusPokemonReadOnly.read(record)
            as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA8 is not checksum-valid.")
        require(!pokemon.isEgg) { "Egg PA8 records remain protected from deletion." }
        require(!pokemon.isNoble) { "Noble PA8 records remain protected from deletion." }
        require(pokemon.nature in 0..24 && pokemon.statNature in 0..24) {
            "The selected PA8 nature data is outside Legends: Arceus native limits."
        }
        require(pokemon.currentHandler in 0..1) {
            "The selected PA8 current-handler identity is invalid."
        }
        require(pokemon.ivs.all { it in 0..31 }) {
            "The selected PA8 IV values exceed native limits."
        }
        require(pokemon.ganbaruValues.all { it in 0..10 }) {
            "The selected PA8 Ganbaru Values exceed native limits."
        }
        require(pokemon.hyperTrainingFlags and 0xC0 == 0) {
            "The selected PA8 hyper-training flags contain unsupported bits."
        }
        val maximumExperience = runCatching {
            LegendsArceusPokemonLevelEditing.maximumExperience(pokemon)
        }.getOrElse {
            throw IllegalArgumentException(
                "This PA8 species/form has no uniquely verified native Legends: Arceus profile."
            )
        }
        require(pokemon.experience <= maximumExperience) {
            "The selected PA8 experience exceeds the verified Level 100 limit for this species/form."
        }
        require(pokemon.cachedLevel == null || pokemon.cachedLevel in 1..100) {
            "The selected party-format PA8 cached Level is outside native limits."
        }
        if (record.size == LegendsArceusPokemonReadOnly.PARTY_SIZE) {
            require(LegendsArceusPokemonLevelEditing.verifyPartyRecord(record) == pokemon) {
                "The selected party PA8 cache did not verify against its stored data."
            }
        }
        return pokemon
    }
}

class LegendsArceusPokemonDeleteCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    val selection: LegendsArceusPokemonDeleteSelection,
    val pasturesAbsoluteOffset: Int,
    val partyAbsoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    val sourceSha256 = LegendsArceusPokemonDeleteEditing.sha256(this.original)
    val sourceRecordSha256 = LegendsArceusPokemonDeleteEditing.sha256(this.sourceRecord)
    val editedSha256 = LegendsArceusPokemonDeleteEditing.sha256(this.edited)
    val changedAbsoluteStart: Int = if (selection.sourceArea == LegendsArceusPokemonArea.PASTURE)
        pasturesAbsoluteOffset + ((selection.sourcePasture - 1) * 30 + selection.sourcePosition - 1) *
            LegendsArceusPokemonReadOnly.STORED_SIZE
    else partyAbsoluteOffset + (selection.sourcePosition - 1) *
        LegendsArceusPokemonReadOnly.PARTY_SIZE
    val changedAbsoluteEndExclusive: Int = if (
        selection.sourceArea == LegendsArceusPokemonArea.PASTURE
    ) changedAbsoluteStart + LegendsArceusPokemonReadOnly.STORED_SIZE
    else partyAbsoluteOffset + selection.partyCountBefore * LegendsArceusPokemonReadOnly.PARTY_SIZE
    val partyCountAbsoluteOffset: Int = if (selection.sourceArea == LegendsArceusPokemonArea.PARTY)
        partyAbsoluteOffset + 6 * LegendsArceusPokemonReadOnly.PARTY_SIZE else -1
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
            LegendsArceusPokemonDeleteEditing.verify(
                original, edited, selection, pasturesAbsoluteOffset, partyAbsoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Pokémon deletion."
            }
            LegendsArceusPokemonDeleteEditing.verify(
                original, persisted, selection, pasturesAbsoluteOffset, partyAbsoluteOffset
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
}
