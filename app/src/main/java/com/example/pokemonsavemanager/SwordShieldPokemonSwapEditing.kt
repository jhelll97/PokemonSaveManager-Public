package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Collections

/** Swaps two checksum-valid native PK8 records in one Sword/Shield PC box. */
object SwordShieldPokemonSwapEditing {
    private const val MAX_FILE_SIZE = 0x1876B1

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
        box: Int,
        sourcePosition: Int
    ): SwordShieldPokemonSwapSelection {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val sourcePokemon = inspectRecord(boxRecord(blocks, box, sourcePosition), "selected")
        val candidates = (1..30).mapNotNull { position ->
            if (position == sourcePosition) null else runCatching {
                SwordShieldPokemonSwapCandidate(
                    position, inspectRecord(boxRecord(blocks, box, position), "destination")
                )
            }.getOrNull()
        }
        require(candidates.isNotEmpty()) {
            "No other checksum-valid Pokémon exists in Sword/Shield Box $box."
        }
        return SwordShieldPokemonSwapSelection(
            box, sourcePosition, sourcePokemon, Collections.unmodifiableList(candidates)
        )
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        box: Int,
        sourcePosition: Int,
        destinationPosition: Int
    ): SwordShieldPokemonSwapCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(sourcePosition != destinationPosition) { "Choose a different Pokémon in this Box." }
        val before = SwordShieldSaveValidation.readPokemonBlocks(original)
        val sourceRecord = boxRecord(before, box, sourcePosition)
        val destinationRecord = boxRecord(before, box, destinationPosition)
        val sourcePokemon = inspectRecord(sourceRecord, "selected")
        val destinationPokemon = inspectRecord(destinationRecord, "destination")

        val sourceWrite = SwordShieldSaveValidation.writeBoxRecord(
            original, box, sourcePosition, destinationRecord
        )
        val destinationWrite = SwordShieldSaveValidation.writeBoxRecord(
            sourceWrite.bytes, box, destinationPosition, sourceRecord
        )
        verify(
            original, destinationWrite.bytes, box, sourcePosition, destinationPosition,
            sourceWrite.absoluteOffset, destinationWrite.absoluteOffset
        )
        return SwordShieldPokemonSwapCopyPlan(
            original, destinationWrite.bytes, sourceRecord, destinationRecord,
            sourcePokemon, destinationPokemon, box, sourcePosition, destinationPosition,
            sourceWrite.absoluteOffset, destinationWrite.absoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        box: Int,
        sourcePosition: Int,
        destinationPosition: Int,
        sourceAbsoluteOffset: Int,
        destinationAbsoluteOffset: Int
    ) {
        require(sourcePosition != destinationPosition) { "A swap requires two different native slots." }
        val before = SwordShieldSaveValidation.readPokemonBlocks(original)
        val after = SwordShieldSaveValidation.readPokemonBlocks(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The swapped copy changed native title identity or save size."
        }
        val sourceRecord = boxRecord(before, box, sourcePosition)
        val destinationRecord = boxRecord(before, box, destinationPosition)
        val sourcePokemon = inspectRecord(sourceRecord, "selected")
        val destinationPokemon = inspectRecord(destinationRecord, "destination")
        val reopenedSourceRecord = boxRecord(after, box, sourcePosition)
        val reopenedDestinationRecord = boxRecord(after, box, destinationPosition)
        val reopenedSource = inspectRecord(reopenedSourceRecord, "reopened source")
        val reopenedDestination = inspectRecord(reopenedDestinationRecord, "reopened destination")
        require(reopenedSourceRecord.contentEquals(destinationRecord) &&
            reopenedDestinationRecord.contentEquals(sourceRecord) &&
            reopenedSource == destinationPokemon && reopenedDestination == sourcePokemon) {
            "The two exact native PK8 records did not reopen in their reviewed swapped positions."
        }
        require(before.party.contentEquals(after.party)) {
            "Native party data changed during the boxed Pokémon swap."
        }
        val sourceRelativeOffset = relativeOffset(box, sourcePosition)
        val destinationRelativeOffset = relativeOffset(box, destinationPosition)
        require(before.boxes.indices.all { index ->
            index in sourceRelativeOffset until sourceRelativeOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                index in destinationRelativeOffset until destinationRelativeOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                before.boxes[index] == after.boxes[index]
        }) { "An unrelated native PC-box slot changed." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in sourceAbsoluteOffset until sourceAbsoluteOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                index in destinationAbsoluteOffset until destinationAbsoluteOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun inspectRecord(record: ByteArray, label: String): SwordShieldPokemonRead {
        require(record.size == SwordShieldPokemonReadOnly.RECORD_SIZE) {
            "A complete 344-byte native PK8 record is required."
        }
        val pokemon = (SwordShieldPokemonReadOnly.read(record) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The $label native PK8 is not checksum-valid.")
        require(!pokemon.isEgg) { "Egg PK8 records cannot be swapped." }
        require(pokemon.cachedLevel in 1..100 && pokemon.nature in 0..24 && pokemon.statNature in 0..24) {
            "The $label PK8 Level or nature data is outside Sword/Shield's native limits."
        }
        require(pokemon.dynamaxLevel in 0..10) {
            "The $label PK8 Dynamax Level is outside Sword/Shield's native limit."
        }
        require(pokemon.evs.all { it in 0..252 } && pokemon.evs.sum() <= 510) {
            "The $label PK8 EV values exceed Sword/Shield's native per-stat or total limit."
        }
        require(pokemon.ivs.all { it in 0..31 }) {
            "The $label PK8 IV values exceed Sword/Shield's native limit."
        }
        require(pokemon.hyperTrainingFlags and 0xC0 == 0) {
            "The $label PK8 hyper-training flags contain unsupported bits."
        }
        val maximumExperience = runCatching {
            SwordShieldPokemonLevelEditing.maximumExperience(pokemon)
        }.getOrElse {
            throw IllegalArgumentException(
                "The $label PK8 species/form has no uniquely verified native Sword/Shield profile."
            )
        }
        require(pokemon.experience <= maximumExperience) {
            "The $label PK8 experience exceeds the verified Level 100 limit for this species/form."
        }
        return pokemon
    }

    private fun boxRecord(
        blocks: SwordShieldPokemonBlocks,
        box: Int,
        position: Int
    ): ByteArray {
        require(box in 1..32 && position in 1..30) {
            "The selected native Sword/Shield PC-box slot is outside storage."
        }
        val offset = relativeOffset(box, position)
        return blocks.boxes.copyOfRange(offset, offset + SwordShieldPokemonReadOnly.RECORD_SIZE)
    }

    private fun relativeOffset(box: Int, position: Int): Int =
        ((box - 1) * 30 + position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
}

data class SwordShieldPokemonSwapCandidate(
    val position: Int,
    val pokemon: SwordShieldPokemonRead
)

data class SwordShieldPokemonSwapSelection(
    val box: Int,
    val sourcePosition: Int,
    val sourcePokemon: SwordShieldPokemonRead,
    val candidates: List<SwordShieldPokemonSwapCandidate>
)

class SwordShieldPokemonSwapCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    destinationRecord: ByteArray,
    val sourcePokemon: SwordShieldPokemonRead,
    val destinationPokemon: SwordShieldPokemonRead,
    val box: Int,
    val sourcePosition: Int,
    val destinationPosition: Int,
    val sourceAbsoluteOffset: Int,
    val destinationAbsoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    private val destinationRecord = destinationRecord.copyOf()
    val sourceSha256 = SwordShieldPokemonSwapEditing.sha256(this.original)
    val sourceRecordSha256 = SwordShieldPokemonSwapEditing.sha256(this.sourceRecord)
    val destinationRecordSha256 = SwordShieldPokemonSwapEditing.sha256(this.destinationRecord)
    val editedSha256 = SwordShieldPokemonSwapEditing.sha256(this.edited)
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
            SwordShieldPokemonSwapEditing.verify(
                original, edited, box, sourcePosition, destinationPosition,
                sourceAbsoluteOffset, destinationAbsoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Pokémon swap."
            }
            SwordShieldPokemonSwapEditing.verify(
                original, persisted, box, sourcePosition, destinationPosition,
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
