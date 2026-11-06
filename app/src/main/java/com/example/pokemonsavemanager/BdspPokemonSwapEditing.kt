package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Collections

/** Swaps two checksum-valid native PB8 records in one BDSP PC box. */
object BdspPokemonSwapEditing {
    private const val BOX_OFFSET = 0x14EF4
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        val maximum = BdspSaveRevision.values().maxOf { it.fileSize }
        while (true) {
            val remaining = maximum + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= maximum) { "The document exceeds the supported BDSP save size." }
        }
        output.toByteArray()
    }

    fun readSaveBytes(input: InputStream): ByteArray = readDocumentBytes(input).also {
        BdspStorageReadOnly.read(it)
    }

    fun inspect(
        source: ByteArray,
        expectedSha256: String,
        box: Int,
        sourcePosition: Int
    ): BdspPokemonSwapSelection {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val validation = BdspSaveValidation.inspect(original)
        require(validation is BdspValidation.Verified) { "A validated native BDSP save is required." }
        val sourcePokemon = inspectRecord(record(original, box, sourcePosition), "selected")
        val candidates = (1..30).mapNotNull { position ->
            if (position == sourcePosition) null else runCatching {
                BdspPokemonSwapCandidate(position, inspectRecord(record(original, box, position), "destination"))
            }.getOrNull()
        }
        require(candidates.isNotEmpty()) { "No other checksum-valid Pokémon exists in BDSP Box $box." }
        return BdspPokemonSwapSelection(
            box, sourcePosition, sourcePokemon, Collections.unmodifiableList(candidates)
        )
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        box: Int,
        sourcePosition: Int,
        destinationPosition: Int
    ): BdspPokemonSwapCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(sourcePosition != destinationPosition) { "Choose a different Pokémon in this Box." }
        val validation = BdspSaveValidation.inspect(original)
        require(validation is BdspValidation.Verified) { "A validated native BDSP save is required." }
        val sourceOffset = offset(box, sourcePosition)
        val destinationOffset = offset(box, destinationPosition)
        val sourceRecord = original.copyOfRange(
            sourceOffset, sourceOffset + BdspPokemonReadOnly.RECORD_SIZE
        )
        val destinationRecord = original.copyOfRange(
            destinationOffset, destinationOffset + BdspPokemonReadOnly.RECORD_SIZE
        )
        val sourcePokemon = inspectRecord(sourceRecord, "selected")
        val destinationPokemon = inspectRecord(destinationRecord, "destination")
        val edited = original.copyOf()
        destinationRecord.copyInto(edited, sourceOffset)
        sourceRecord.copyInto(edited, destinationOffset)
        refreshDigest(edited)
        verify(original, edited, box, sourcePosition, destinationPosition)
        return BdspPokemonSwapCopyPlan(
            original, edited, sourceRecord, destinationRecord,
            sourcePokemon, destinationPokemon, box, sourcePosition, destinationPosition,
            sourceOffset, destinationOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        box: Int,
        sourcePosition: Int,
        destinationPosition: Int
    ) {
        require(sourcePosition != destinationPosition) { "A swap requires two different native slots." }
        val beforeValidation = BdspSaveValidation.inspect(original)
        val afterValidation = BdspSaveValidation.inspect(edited)
        require(beforeValidation is BdspValidation.Verified &&
            afterValidation is BdspValidation.Verified &&
            beforeValidation.version == afterValidation.version &&
            beforeValidation.revision == afterValidation.revision &&
            original.size == edited.size) {
            "The swapped copy changed native title identity, revision, size, or whole-file MD5."
        }
        val sourceOffset = offset(box, sourcePosition)
        val destinationOffset = offset(box, destinationPosition)
        val sourceRecord = original.copyOfRange(
            sourceOffset, sourceOffset + BdspPokemonReadOnly.RECORD_SIZE
        )
        val destinationRecord = original.copyOfRange(
            destinationOffset, destinationOffset + BdspPokemonReadOnly.RECORD_SIZE
        )
        val sourcePokemon = inspectRecord(sourceRecord, "selected")
        val destinationPokemon = inspectRecord(destinationRecord, "destination")
        val reopenedSourceRecord = edited.copyOfRange(
            sourceOffset, sourceOffset + BdspPokemonReadOnly.RECORD_SIZE
        )
        val reopenedDestinationRecord = edited.copyOfRange(
            destinationOffset, destinationOffset + BdspPokemonReadOnly.RECORD_SIZE
        )
        val reopenedSource = inspectRecord(reopenedSourceRecord, "reopened source")
        val reopenedDestination = inspectRecord(reopenedDestinationRecord, "reopened destination")
        require(reopenedSourceRecord.contentEquals(destinationRecord) &&
            reopenedDestinationRecord.contentEquals(sourceRecord) &&
            reopenedSource == destinationPokemon && reopenedDestination == sourcePokemon) {
            "The two exact native PB8 records did not reopen in their reviewed swapped positions."
        }
        BdspStorageReadOnly.read(edited)
        require(original.indices.all { index ->
            index in sourceOffset until sourceOffset + BdspPokemonReadOnly.RECORD_SIZE ||
                index in destinationOffset until destinationOffset + BdspPokemonReadOnly.RECORD_SIZE ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH || original[index] == edited[index]
        }) { "An unrelated Pokémon slot, party byte, or BDSP save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun inspectRecord(record: ByteArray, label: String): BdspPokemonRead {
        require(record.size == BdspPokemonReadOnly.RECORD_SIZE) {
            "A complete 344-byte native PB8 record is required."
        }
        val pokemon = (BdspPokemonReadOnly.read(record) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The $label native PB8 is not checksum-valid.")
        require(!pokemon.isEgg) { "Egg PB8 records cannot be swapped." }
        require(pokemon.cachedLevel in 1..100 && pokemon.nature in 0..24 && pokemon.statNature in 0..24) {
            "The $label PB8 Level or nature data is outside BDSP's native limits."
        }
        require(pokemon.currentHandler in 0..1) { "The $label PB8 current-handler identity is invalid." }
        require(pokemon.ivs.all { it in 0..31 }) { "The $label PB8 IV values exceed BDSP's native limit." }
        require(pokemon.evs.all { it in 0..252 } && pokemon.evs.sum() <= 510) {
            "The $label PB8 EV values exceed BDSP's native per-stat or total limit."
        }
        require(pokemon.hyperTrainingFlags and 0xC0 == 0) {
            "The $label PB8 hyper-training flags contain unsupported bits."
        }
        val maximumExperience = runCatching {
            BdspPokemonLevelEditing.maximumExperience(pokemon)
        }.getOrElse {
            throw IllegalArgumentException(
                "The $label PB8 species/form has no uniquely verified native BDSP profile."
            )
        }
        require(pokemon.experience <= maximumExperience) {
            "The $label PB8 experience exceeds the verified Level 100 limit for this species/form."
        }
        return pokemon
    }

    private fun record(source: ByteArray, box: Int, position: Int): ByteArray {
        val start = offset(box, position)
        return source.copyOfRange(start, start + BdspPokemonReadOnly.RECORD_SIZE)
    }

    private fun offset(box: Int, position: Int): Int {
        require(box in 1..40 && position in 1..30) {
            "The selected native BDSP PC-box slot is outside storage."
        }
        return BOX_OFFSET + ((box - 1) * 30 + position - 1) * BdspPokemonReadOnly.RECORD_SIZE
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

data class BdspPokemonSwapCandidate(val position: Int, val pokemon: BdspPokemonRead)
data class BdspPokemonSwapSelection(
    val box: Int,
    val sourcePosition: Int,
    val sourcePokemon: BdspPokemonRead,
    val candidates: List<BdspPokemonSwapCandidate>
)

class BdspPokemonSwapCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    destinationRecord: ByteArray,
    val sourcePokemon: BdspPokemonRead,
    val destinationPokemon: BdspPokemonRead,
    val box: Int,
    val sourcePosition: Int,
    val destinationPosition: Int,
    val sourceOffset: Int,
    val destinationOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    private val destinationRecord = destinationRecord.copyOf()
    val sourceSha256 = BdspPokemonSwapEditing.sha256(this.original)
    val sourceRecordSha256 = BdspPokemonSwapEditing.sha256(this.sourceRecord)
    val destinationRecordSha256 = BdspPokemonSwapEditing.sha256(this.destinationRecord)
    val editedSha256 = BdspPokemonSwapEditing.sha256(this.edited)
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
            BdspPokemonSwapEditing.verify(original, edited, box, sourcePosition, destinationPosition)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Pokémon swap."
            }
            BdspPokemonSwapEditing.verify(original, persisted, box, sourcePosition, destinationPosition)
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
