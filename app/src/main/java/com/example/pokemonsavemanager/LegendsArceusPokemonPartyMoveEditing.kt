package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class LegendsArceusPokemonPartyMoveDirection(val label: String) {
    TO_PARTY("Move to Party"),
    TO_PASTURE("Move to PC Box")
}

data class LegendsArceusPokemonPartyMoveSelection(
    val pokemon: LegendsArceusPokemonRead,
    val direction: LegendsArceusPokemonPartyMoveDirection,
    val sourceArea: LegendsArceusPokemonArea,
    val sourcePasture: Int,
    val sourcePosition: Int,
    val destinationPasture: Int,
    val destinationPosition: Int,
    val partyCountBefore: Int,
    val partyCountAfter: Int
) {
    val sourceLocation: String get() = if (sourceArea == LegendsArceusPokemonArea.PASTURE)
        "Pasture $sourcePasture • Slot $sourcePosition" else "Party $sourcePosition"
    val destinationLocation: String get() =
        if (direction == LegendsArceusPokemonPartyMoveDirection.TO_PARTY)
            "Party $destinationPosition" else "Pasture $destinationPasture • Slot $destinationPosition"
}

/** Moves one native PA8 between the party and pasture storage in a reviewed new copy. */
object LegendsArceusPokemonPartyMoveEditing {
    private const val MAX_FILE_SIZE = 0x13AD06
    private const val FOOTER_SIZE = 32
    private const val PARTY_CAPACITY = 6

    private data class DesiredBlocks(
        val pastures: ByteArray,
        val party: ByteArray,
        val destinationRecord: ByteArray
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
    ): LegendsArceusPokemonPartyMoveSelection {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val count = partyCount(blocks)
        requirePartyRecords(blocks, count)
        val sourceRecord = targetRecord(blocks, area, pasture, position)
        val pokemon = inspectRecord(sourceRecord)
        return if (area == LegendsArceusPokemonArea.PASTURE) {
            require(count < PARTY_CAPACITY) {
                "The native Legends: Arceus party already contains six Pokémon."
            }
            LegendsArceusPokemonLevelEditing.partyRecordFromStored(sourceRecord)
            LegendsArceusPokemonPartyMoveSelection(
                pokemon, LegendsArceusPokemonPartyMoveDirection.TO_PARTY,
                area, pasture, position, 0, count + 1, count, count + 1
            )
        } else {
            require(count > 1) {
                "At least one Pokémon must remain in the native Legends: Arceus party."
            }
            val storage = LegendsArceusStorageReadOnly.read(original)
            val destination = (0 until 32).asSequence()
                .flatMap { current -> storage.pasture(current).asSequence() }
                .firstOrNull { it.read is LegendsArceusSlotRead.Empty }
                ?: throw IllegalArgumentException(
                    "All 960 native Legends: Arceus pasture slots are occupied."
                )
            LegendsArceusPokemonPartyMoveSelection(
                pokemon, LegendsArceusPokemonPartyMoveDirection.TO_PASTURE,
                area, 0, position, destination.pasture + 1, destination.position,
                count, count - 1
            )
        }
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int
    ): LegendsArceusPokemonPartyMoveCopyPlan {
        val original = source.copyOf()
        val selection = inspect(original, expectedSha256, area, pasture, position)
        val before = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val sourceRecord = targetRecord(before, area, pasture, position)
        val desired = desiredBlocks(before, selection, sourceRecord)
        val written = LegendsArceusSaveValidation.writePokemonBlocks(
            original, desired.pastures, desired.party
        )
        verify(
            original, written.bytes, selection,
            written.pasturesAbsoluteOffset, written.partyAbsoluteOffset
        )
        return LegendsArceusPokemonPartyMoveCopyPlan(
            original, written.bytes, sourceRecord, desired.destinationRecord, selection,
            written.pasturesAbsoluteOffset, written.partyAbsoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        selection: LegendsArceusPokemonPartyMoveSelection,
        pasturesAbsoluteOffset: Int,
        partyAbsoluteOffset: Int
    ) {
        val before = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val after = LegendsArceusSaveValidation.readPokemonBlocks(edited)
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
            before, selection.sourceArea, selection.sourcePasture, selection.sourcePosition
        )
        require(inspectRecord(sourceRecord) == selection.pokemon) {
            "The selected source PA8 changed before verification."
        }
        if (selection.direction == LegendsArceusPokemonPartyMoveDirection.TO_PASTURE) {
            require(LegendsArceusPokemonReadOnly.read(pastureRecord(
                before, selection.destinationPasture, selection.destinationPosition
            )) is LegendsArceusSlotRead.Empty) {
                "The reviewed native pasture destination is no longer empty."
            }
        }
        val desired = desiredBlocks(before, selection, sourceRecord)
        require(after.pastures.contentEquals(desired.pastures) &&
            after.party.contentEquals(desired.party)) {
            "The native party and pasture storage did not reopen in the reviewed state."
        }
        val reopenedRecord = if (
            selection.direction == LegendsArceusPokemonPartyMoveDirection.TO_PARTY
        ) partyRecord(after, selection.destinationPosition)
        else pastureRecord(after, selection.destinationPasture, selection.destinationPosition)
        require(reopenedRecord.contentEquals(desired.destinationRecord)) {
            "The converted native PA8 did not reopen exactly at its reviewed destination."
        }
        val reopened = inspectRecord(reopenedRecord)
        require(storedIdentity(reopened) == storedIdentity(selection.pokemon)) {
            "The moved native PA8 changed stored Pokémon data."
        }
        val sourceCore = sourceRecord.copyOfRange(0, LegendsArceusPokemonReadOnly.STORED_SIZE)
        require(reopenedRecord.copyOfRange(
            0, LegendsArceusPokemonReadOnly.STORED_SIZE
        ).contentEquals(sourceCore)) {
            "The move did not preserve the exact 360-byte native PA8 stored core."
        }

        val pastureRelativeOffset = if (
            selection.direction == LegendsArceusPokemonPartyMoveDirection.TO_PARTY
        ) pastureOffset(selection.sourcePasture, selection.sourcePosition)
        else pastureOffset(selection.destinationPasture, selection.destinationPosition)
        val partySize = LegendsArceusPokemonReadOnly.PARTY_SIZE
        val partyRange = if (
            selection.direction == LegendsArceusPokemonPartyMoveDirection.TO_PARTY
        ) {
            val start = selection.partyCountBefore * partySize
            start until start + partySize
        } else {
            val start = (selection.sourcePosition - 1) * partySize
            start until selection.partyCountBefore * partySize
        }
        val countRelativeOffset = PARTY_CAPACITY * partySize
        val hashOffset = original.size - FOOTER_SIZE
        require(original.indices.all { index ->
            index in pasturesAbsoluteOffset + pastureRelativeOffset until
                pasturesAbsoluteOffset + pastureRelativeOffset +
                    LegendsArceusPokemonReadOnly.STORED_SIZE ||
                index in partyAbsoluteOffset + partyRange.first until
                    partyAbsoluteOffset + partyRange.last + 1 ||
                index == partyAbsoluteOffset + countRelativeOffset ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted Legends: Arceus save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun desiredBlocks(
        before: LegendsArceusPokemonBlocks,
        selection: LegendsArceusPokemonPartyMoveSelection,
        sourceRecord: ByteArray
    ): DesiredBlocks {
        val pastures = before.pastures.copyOf()
        val party = before.party.copyOf()
        val storedSize = LegendsArceusPokemonReadOnly.STORED_SIZE
        val partySize = LegendsArceusPokemonReadOnly.PARTY_SIZE
        val countOffset = PARTY_CAPACITY * partySize
        val destinationRecord: ByteArray
        if (selection.direction == LegendsArceusPokemonPartyMoveDirection.TO_PARTY) {
            ByteArray(storedSize).copyInto(
                pastures, pastureOffset(selection.sourcePasture, selection.sourcePosition)
            )
            destinationRecord = LegendsArceusPokemonLevelEditing.partyRecordFromStored(sourceRecord)
            destinationRecord.copyInto(party, selection.partyCountBefore * partySize)
        } else {
            destinationRecord = sourceRecord.copyOfRange(0, storedSize)
            destinationRecord.copyInto(
                pastures, pastureOffset(selection.destinationPasture, selection.destinationPosition)
            )
            val sourceIndex = selection.sourcePosition - 1
            for (index in sourceIndex until selection.partyCountBefore - 1) {
                party.copyInto(
                    party, index * partySize, (index + 1) * partySize, (index + 2) * partySize
                )
            }
            ByteArray(partySize).copyInto(
                party, (selection.partyCountBefore - 1) * partySize
            )
        }
        party[countOffset] = selection.partyCountAfter.toByte()
        return DesiredBlocks(pastures, party, destinationRecord)
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
        require(!pokemon.isEgg) { "Egg PA8 records cannot be moved between party and pasture storage." }
        require(!pokemon.isNoble) { "Noble PA8 records have no legal native pasture equivalent." }
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

    private fun storedIdentity(pokemon: LegendsArceusPokemonRead): LegendsArceusPokemonRead =
        pokemon.copy(cachedLevel = null, maximumHp = null, cachedStats = null)
}

class LegendsArceusPokemonPartyMoveCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    destinationRecord: ByteArray,
    val selection: LegendsArceusPokemonPartyMoveSelection,
    val pasturesAbsoluteOffset: Int,
    val partyAbsoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    private val destinationRecord = destinationRecord.copyOf()
    val sourceSha256 = LegendsArceusPokemonPartyMoveEditing.sha256(this.original)
    val sourceRecordSha256 = LegendsArceusPokemonPartyMoveEditing.sha256(this.sourceRecord)
    val destinationRecordSha256 =
        LegendsArceusPokemonPartyMoveEditing.sha256(this.destinationRecord)
    val editedSha256 = LegendsArceusPokemonPartyMoveEditing.sha256(this.edited)
    val partyTailBytesRemoved: Int = if (
        selection.direction == LegendsArceusPokemonPartyMoveDirection.TO_PASTURE
    ) sourceRecord.size - LegendsArceusPokemonReadOnly.STORED_SIZE else 0
    private val movedPasture: Int =
        if (selection.direction == LegendsArceusPokemonPartyMoveDirection.TO_PARTY)
            selection.sourcePasture else selection.destinationPasture
    private val movedPasturePosition: Int =
        if (selection.direction == LegendsArceusPokemonPartyMoveDirection.TO_PARTY)
            selection.sourcePosition else selection.destinationPosition
    val pastureAbsoluteSlotOffset: Int = pasturesAbsoluteOffset +
        ((movedPasture - 1) * 30 + movedPasturePosition - 1) *
            LegendsArceusPokemonReadOnly.STORED_SIZE
    val partyAbsoluteRangeStart: Int = partyAbsoluteOffset +
        (if (selection.direction == LegendsArceusPokemonPartyMoveDirection.TO_PARTY)
            selection.partyCountBefore else selection.sourcePosition - 1) *
            LegendsArceusPokemonReadOnly.PARTY_SIZE
    val partyAbsoluteRangeEndExclusive: Int = partyAbsoluteOffset +
        selection.partyCountBefore * LegendsArceusPokemonReadOnly.PARTY_SIZE +
        if (selection.direction == LegendsArceusPokemonPartyMoveDirection.TO_PARTY)
            LegendsArceusPokemonReadOnly.PARTY_SIZE else 0
    val partyCountAbsoluteOffset: Int = partyAbsoluteOffset +
        6 * LegendsArceusPokemonReadOnly.PARTY_SIZE
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
            LegendsArceusPokemonPartyMoveEditing.verify(
                original, edited, selection, pasturesAbsoluteOffset, partyAbsoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed party move."
            }
            LegendsArceusPokemonPartyMoveEditing.verify(
                original, persisted, selection, pasturesAbsoluteOffset, partyAbsoluteOffset
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
