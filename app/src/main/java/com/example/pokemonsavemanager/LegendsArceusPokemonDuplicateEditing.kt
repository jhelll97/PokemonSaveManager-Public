package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Duplicates one checksum-valid native PA8 into a reviewed new Legends: Arceus save copy. */
object LegendsArceusPokemonDuplicateEditing {
    private const val MAX_FILE_SIZE = 0x13AD06
    private const val FOOTER_SIZE = 32

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

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int
    ): LegendsArceusPokemonDuplicateCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val beforeBlocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val sourceRecord = targetRecord(beforeBlocks, area, pasture, position)
        val pokemon = inspectSourceRecord(sourceRecord)
        val storedRecord = sourceRecord.copyOfRange(0, LegendsArceusPokemonReadOnly.STORED_SIZE)
        inspectSourceRecord(storedRecord)
        val storage = LegendsArceusStorageReadOnly.read(original)
        val destination = (0 until 32).asSequence().flatMap { current -> storage.pasture(current).asSequence() }
            .firstOrNull { it.read is LegendsArceusSlotRead.Empty }
            ?: throw IllegalArgumentException("All 960 native Legends: Arceus pasture slots are occupied.")
        val destinationPasture = destination.pasture + 1
        val destinationPosition = destination.position
        require(area != LegendsArceusPokemonArea.PASTURE ||
            pasture != destinationPasture || position != destinationPosition) {
            "The source Pokémon cannot also be the reviewed empty destination."
        }
        val written = LegendsArceusSaveValidation.writePastureRecord(
            original, destinationPasture, destinationPosition, storedRecord
        )
        verify(
            original, written.bytes, area, pasture, position, sourceRecord, storedRecord,
            destinationPasture, destinationPosition, written.absoluteOffset
        )
        return LegendsArceusPokemonDuplicateCopyPlan(
            original, written.bytes, sourceRecord, storedRecord, pokemon, area, pasture, position,
            destinationPasture, destinationPosition, written.absoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        sourceArea: LegendsArceusPokemonArea,
        sourcePasture: Int,
        sourcePosition: Int,
        sourceRecord: ByteArray,
        storedRecord: ByteArray,
        destinationPasture: Int,
        destinationPosition: Int,
        absoluteOffset: Int
    ) {
        val before = LegendsArceusStorageReadOnly.read(original)
        val after = LegendsArceusStorageReadOnly.read(edited)
        require(original.size == edited.size && before.version == after.version) {
            "The duplicated copy changed native title identity or size."
        }
        val beforeBlocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsArceusSaveValidation.readPokemonBlocks(edited)
        val currentSource = targetRecord(beforeBlocks, sourceArea, sourcePasture, sourcePosition)
        require(currentSource.contentEquals(sourceRecord) &&
            targetRecord(afterBlocks, sourceArea, sourcePasture, sourcePosition).contentEquals(sourceRecord)) {
            "The selected source PA8 changed during duplication."
        }
        inspectSourceRecord(sourceRecord)
        val expectedStored = sourceRecord.copyOfRange(0, LegendsArceusPokemonReadOnly.STORED_SIZE)
        require(storedRecord.contentEquals(expectedStored)) {
            "The reviewed PA8 stored core changed."
        }
        val storedPokemon = inspectSourceRecord(storedRecord)
        require(before.pasture(destinationPasture - 1)[destinationPosition - 1].read
            is LegendsArceusSlotRead.Empty) {
            "The reviewed native pasture destination was not empty in the source save."
        }
        val reopened = after.pasture(destinationPasture - 1)[destinationPosition - 1].read
            as? LegendsArceusSlotRead.Pokemon
            ?: throw IllegalArgumentException("The duplicated PA8 did not reopen as a Pokémon.")
        require(reopened.value == storedPokemon) {
            "The duplicated PA8 did not reopen as the exact native stored core."
        }

        val relativeOffset = ((destinationPasture - 1) * 30 + destinationPosition - 1) *
            LegendsArceusPokemonReadOnly.STORED_SIZE
        require(afterBlocks.pastures.copyOfRange(
            relativeOffset, relativeOffset + LegendsArceusPokemonReadOnly.STORED_SIZE
        ).contentEquals(storedRecord)) {
            "The exact native PA8 stored core was not preserved in the duplicate."
        }
        require(beforeBlocks.party.contentEquals(afterBlocks.party)) {
            "Native party data changed during Pokémon duplication."
        }
        require(beforeBlocks.pastures.indices.all { index ->
            index in relativeOffset until relativeOffset + LegendsArceusPokemonReadOnly.STORED_SIZE ||
                beforeBlocks.pastures[index] == afterBlocks.pastures[index]
        }) { "An existing native pasture slot changed." }

        val footerOffset = original.size - FOOTER_SIZE
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + LegendsArceusPokemonReadOnly.STORED_SIZE ||
                index in footerOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun inspectSourceRecord(record: ByteArray): LegendsArceusPokemonRead {
        require(record.size == LegendsArceusPokemonReadOnly.STORED_SIZE ||
            record.size == LegendsArceusPokemonReadOnly.PARTY_SIZE) {
            "A complete 360-byte stored or 376-byte party PA8 record is required."
        }
        val pokemon = (LegendsArceusPokemonReadOnly.read(record) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA8 is not checksum-valid.")
        require(!pokemon.isEgg) { "Egg PA8 records cannot be duplicated." }
        require(!pokemon.isNoble) { "Noble PA8 records have no legal native pasture equivalent." }
        require(pokemon.nature in 0..24 && pokemon.statNature in 0..24) {
            "The PA8 nature data is outside Legends: Arceus native limits."
        }
        require(pokemon.currentHandler in 0..1) { "The PA8 current-handler identity is invalid." }
        require(pokemon.ivs.all { it in 0..31 }) { "The PA8 IV values exceed native limits." }
        require(pokemon.ganbaruValues.all { it in 0..10 }) {
            "The PA8 Ganbaru Values exceed native limits."
        }
        require(pokemon.hyperTrainingFlags and 0xC0 == 0) {
            "The PA8 hyper-training flags contain unsupported bits."
        }
        val maximumExperience = runCatching {
            LegendsArceusPokemonLevelEditing.maximumExperience(pokemon)
        }.getOrElse {
            throw IllegalArgumentException(
                "This PA8 species/form has no uniquely verified native Legends: Arceus profile, so it remains protected."
            )
        }
        require(pokemon.experience <= maximumExperience) {
            "The PA8 experience exceeds the verified Level 100 limit for this species/form."
        }
        require(pokemon.cachedLevel == null || pokemon.cachedLevel in 1..100) {
            "The party-format PA8 cached Level is outside native limits."
        }
        return pokemon
    }

    private fun targetRecord(
        blocks: LegendsArceusPokemonBlocks,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int
    ): ByteArray {
        val size = if (area == LegendsArceusPokemonArea.PASTURE)
            LegendsArceusPokemonReadOnly.STORED_SIZE else LegendsArceusPokemonReadOnly.PARTY_SIZE
        val source: ByteArray
        val offset: Int
        if (area == LegendsArceusPokemonArea.PASTURE) {
            require(pasture in 1..32 && position in 1..30) {
                "The selected native pasture slot is outside storage."
            }
            source = blocks.pastures
            offset = ((pasture - 1) * 30 + position - 1) * size
        } else {
            require(position in 1..6) { "The selected native party slot is outside the six-slot party." }
            val count = blocks.party[6 * size].toInt() and 255
            require(position <= count) { "The selected native party slot is not occupied." }
            source = blocks.party
            offset = (position - 1) * size
        }
        return source.copyOfRange(offset, offset + size)
    }
}

class LegendsArceusPokemonDuplicateCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    storedRecord: ByteArray,
    val pokemon: LegendsArceusPokemonRead,
    val sourceArea: LegendsArceusPokemonArea,
    val sourcePasture: Int,
    val sourcePosition: Int,
    val destinationPasture: Int,
    val destinationPosition: Int,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    private val storedRecord = storedRecord.copyOf()
    val sourceSha256 = LegendsArceusPokemonDuplicateEditing.sha256(this.original)
    val recordSha256 = LegendsArceusPokemonDuplicateEditing.sha256(this.sourceRecord)
    val storedRecordSha256 = LegendsArceusPokemonDuplicateEditing.sha256(this.storedRecord)
    val partyTailBytes = this.sourceRecord.size - this.storedRecord.size
    val editedSha256 = LegendsArceusPokemonDuplicateEditing.sha256(this.edited)
    val sourceLocation: String get() = if (sourceArea == LegendsArceusPokemonArea.PASTURE)
        "Pasture $sourcePasture • Slot $sourcePosition" else "Party $sourcePosition"
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This duplicate review was already used. Reopen Pokémon Boxes." }
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
            LegendsArceusPokemonDuplicateEditing.verify(
                original, edited, sourceArea, sourcePasture, sourcePosition,
                sourceRecord, storedRecord, destinationPasture, destinationPosition, absoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Pokémon duplicate."
            }
            LegendsArceusPokemonDuplicateEditing.verify(
                original, persisted, sourceArea, sourcePasture, sourcePosition,
                sourceRecord, storedRecord, destinationPasture, destinationPosition, absoluteOffset
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Pokémon duplication verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
