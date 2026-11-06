package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Collections

/** Swaps two checksum-valid native PA8 records in one Legends: Arceus pasture. */
object LegendsArceusPokemonSwapEditing {
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

    fun inspect(
        source: ByteArray,
        expectedSha256: String,
        pasture: Int,
        sourcePosition: Int
    ): LegendsArceusPokemonSwapSelection {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val sourcePokemon = inspectRecord(pastureRecord(blocks, pasture, sourcePosition), "selected")
        val candidates = (1..30).mapNotNull { position ->
            if (position == sourcePosition) null else runCatching {
                LegendsArceusPokemonSwapCandidate(
                    position, inspectRecord(pastureRecord(blocks, pasture, position), "destination")
                )
            }.getOrNull()
        }
        require(candidates.isNotEmpty()) {
            "No other checksum-valid Pokémon exists in Pasture $pasture."
        }
        return LegendsArceusPokemonSwapSelection(
            pasture, sourcePosition, sourcePokemon, Collections.unmodifiableList(candidates)
        )
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        pasture: Int,
        sourcePosition: Int,
        destinationPosition: Int
    ): LegendsArceusPokemonSwapCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(sourcePosition != destinationPosition) { "Choose a different Pokémon in this Pasture." }
        val before = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val sourceRecord = pastureRecord(before, pasture, sourcePosition)
        val destinationRecord = pastureRecord(before, pasture, destinationPosition)
        val sourcePokemon = inspectRecord(sourceRecord, "selected")
        val destinationPokemon = inspectRecord(destinationRecord, "destination")
        val sourceWrite = LegendsArceusSaveValidation.writePastureRecord(
            original, pasture, sourcePosition, destinationRecord
        )
        val destinationWrite = LegendsArceusSaveValidation.writePastureRecord(
            sourceWrite.bytes, pasture, destinationPosition, sourceRecord
        )
        verify(
            original, destinationWrite.bytes, pasture, sourcePosition, destinationPosition,
            sourceWrite.absoluteOffset, destinationWrite.absoluteOffset
        )
        return LegendsArceusPokemonSwapCopyPlan(
            original, destinationWrite.bytes, sourceRecord, destinationRecord,
            sourcePokemon, destinationPokemon, pasture, sourcePosition, destinationPosition,
            sourceWrite.absoluteOffset, destinationWrite.absoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        pasture: Int,
        sourcePosition: Int,
        destinationPosition: Int,
        sourceAbsoluteOffset: Int,
        destinationAbsoluteOffset: Int
    ) {
        require(sourcePosition != destinationPosition) { "A swap requires two different native slots." }
        val beforeStorage = LegendsArceusStorageReadOnly.read(original)
        val afterStorage = LegendsArceusStorageReadOnly.read(edited)
        require(original.size == edited.size && beforeStorage.version == afterStorage.version) {
            "The swapped copy changed native title identity or size."
        }
        val before = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val after = LegendsArceusSaveValidation.readPokemonBlocks(edited)
        val sourceRecord = pastureRecord(before, pasture, sourcePosition)
        val destinationRecord = pastureRecord(before, pasture, destinationPosition)
        val sourcePokemon = inspectRecord(sourceRecord, "selected")
        val destinationPokemon = inspectRecord(destinationRecord, "destination")
        val reopenedSourceRecord = pastureRecord(after, pasture, sourcePosition)
        val reopenedDestinationRecord = pastureRecord(after, pasture, destinationPosition)
        val reopenedSource = inspectRecord(reopenedSourceRecord, "reopened source")
        val reopenedDestination = inspectRecord(reopenedDestinationRecord, "reopened destination")
        require(reopenedSourceRecord.contentEquals(destinationRecord) &&
            reopenedDestinationRecord.contentEquals(sourceRecord) &&
            reopenedSource == destinationPokemon && reopenedDestination == sourcePokemon) {
            "The two exact native PA8 stored records did not reopen in their reviewed swapped positions."
        }
        require(before.party.contentEquals(after.party)) {
            "Native party records and their party-only tails changed during the pasture swap."
        }
        val sourceRelativeOffset = relativeOffset(pasture, sourcePosition)
        val destinationRelativeOffset = relativeOffset(pasture, destinationPosition)
        require(before.pastures.indices.all { index ->
            index in sourceRelativeOffset until sourceRelativeOffset + LegendsArceusPokemonReadOnly.STORED_SIZE ||
                index in destinationRelativeOffset until destinationRelativeOffset + LegendsArceusPokemonReadOnly.STORED_SIZE ||
                before.pastures[index] == after.pastures[index]
        }) { "An unrelated native pasture slot changed." }
        val footerOffset = original.size - FOOTER_SIZE
        require(original.indices.all { index ->
            index in sourceAbsoluteOffset until sourceAbsoluteOffset + LegendsArceusPokemonReadOnly.STORED_SIZE ||
                index in destinationAbsoluteOffset until destinationAbsoluteOffset + LegendsArceusPokemonReadOnly.STORED_SIZE ||
                index in footerOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun inspectRecord(record: ByteArray, label: String): LegendsArceusPokemonRead {
        require(record.size == LegendsArceusPokemonReadOnly.STORED_SIZE) {
            "A complete 360-byte native stored PA8 record is required."
        }
        val pokemon = (LegendsArceusPokemonReadOnly.read(record) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The $label native PA8 is not checksum-valid.")
        require(!pokemon.isEgg) { "Egg PA8 records cannot be swapped." }
        require(!pokemon.isNoble) { "Noble PA8 records cannot be moved between native pasture slots." }
        require(pokemon.nature in 0..24 && pokemon.statNature in 0..24) {
            "The $label PA8 nature data is outside Legends: Arceus native limits."
        }
        require(pokemon.currentHandler in 0..1) { "The $label PA8 current-handler identity is invalid." }
        require(pokemon.ivs.all { it in 0..31 }) { "The $label PA8 IV values exceed native limits." }
        require(pokemon.ganbaruValues.all { it in 0..10 }) {
            "The $label PA8 Ganbaru Values exceed native limits."
        }
        require(pokemon.hyperTrainingFlags and 0xC0 == 0) {
            "The $label PA8 hyper-training flags contain unsupported bits."
        }
        val maximumExperience = runCatching {
            LegendsArceusPokemonLevelEditing.maximumExperience(pokemon)
        }.getOrElse {
            throw IllegalArgumentException(
                "The $label PA8 species/form has no uniquely verified native Legends: Arceus profile."
            )
        }
        require(pokemon.experience <= maximumExperience) {
            "The $label PA8 experience exceeds the verified Level 100 limit for this species/form."
        }
        return pokemon
    }

    private fun pastureRecord(
        blocks: LegendsArceusPokemonBlocks,
        pasture: Int,
        position: Int
    ): ByteArray {
        require(pasture in 1..32 && position in 1..30) {
            "The selected native Legends: Arceus pasture slot is outside storage."
        }
        val offset = relativeOffset(pasture, position)
        return blocks.pastures.copyOfRange(
            offset, offset + LegendsArceusPokemonReadOnly.STORED_SIZE
        )
    }

    private fun relativeOffset(pasture: Int, position: Int): Int =
        ((pasture - 1) * 30 + position - 1) * LegendsArceusPokemonReadOnly.STORED_SIZE
}

data class LegendsArceusPokemonSwapCandidate(
    val position: Int,
    val pokemon: LegendsArceusPokemonRead
)

data class LegendsArceusPokemonSwapSelection(
    val pasture: Int,
    val sourcePosition: Int,
    val sourcePokemon: LegendsArceusPokemonRead,
    val candidates: List<LegendsArceusPokemonSwapCandidate>
)

class LegendsArceusPokemonSwapCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    destinationRecord: ByteArray,
    val sourcePokemon: LegendsArceusPokemonRead,
    val destinationPokemon: LegendsArceusPokemonRead,
    val pasture: Int,
    val sourcePosition: Int,
    val destinationPosition: Int,
    val sourceAbsoluteOffset: Int,
    val destinationAbsoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    private val destinationRecord = destinationRecord.copyOf()
    val sourceSha256 = LegendsArceusPokemonSwapEditing.sha256(this.original)
    val sourceRecordSha256 = LegendsArceusPokemonSwapEditing.sha256(this.sourceRecord)
    val destinationRecordSha256 = LegendsArceusPokemonSwapEditing.sha256(this.destinationRecord)
    val editedSha256 = LegendsArceusPokemonSwapEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This swap review was already used. Reopen Pokémon Boxes." }
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
            LegendsArceusPokemonSwapEditing.verify(
                original, edited, pasture, sourcePosition, destinationPosition,
                sourceAbsoluteOffset, destinationAbsoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Pokémon swap."
            }
            LegendsArceusPokemonSwapEditing.verify(
                original, persisted, pasture, sourcePosition, destinationPosition,
                sourceAbsoluteOffset, destinationAbsoluteOffset
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Pokémon swap verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
