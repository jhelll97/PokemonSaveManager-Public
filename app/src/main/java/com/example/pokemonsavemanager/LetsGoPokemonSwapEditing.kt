package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Collections

/** Swaps two unassigned native PB7 records in the single Let's Go Pokémon Box. */
object LetsGoPokemonSwapEditing {
    private const val STORAGE_RECORDS = 0x5C00
    private const val STORAGE_RECORDS_LENGTH = 0x3F7A0
    private const val FOOTER = 0xB8600
    private const val DESCRIPTORS = FOOTER + 0x14
    private const val RECORD_BLOCK = 9

    fun readDocumentBytes(input: InputStream): ByteArray = readBounded(
        input, LetsGoSaveValidation.FILE_SIZE,
        "The document exceeds the supported Let's Go save size."
    )

    fun readSaveBytes(input: InputStream): ByteArray = readDocumentBytes(input).also {
        require(it.size == LetsGoSaveValidation.FILE_SIZE) { "Select one complete native Let's Go save." }
    }

    fun inspect(
        source: ByteArray,
        expectedSha256: String,
        sourceIndex: Int
    ): LetsGoPokemonSwapSelection {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val storage = readVerifiedStorage(original)
        val sourceSlot = protectedBoxedPokemon(storage, sourceIndex, "selected")
        val sourcePokemon = (sourceSlot.read as LetsGoSlotRead.Pokemon).value
        val candidates = (0 until storage.count).mapNotNull { index ->
            if (index == sourceIndex) null else runCatching {
                val slot = protectedBoxedPokemon(storage, index, "destination")
                LetsGoPokemonSwapCandidate(index, (slot.read as LetsGoSlotRead.Pokemon).value)
            }.getOrNull()
        }
        require(candidates.isNotEmpty()) {
            "No other checksum-valid, unassigned Pokémon exists in the native Let's Go Pokémon Box."
        }
        return LetsGoPokemonSwapSelection(
            sourceIndex, sourcePokemon, Collections.unmodifiableList(candidates)
        )
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        sourceIndex: Int,
        destinationIndex: Int
    ): LetsGoPokemonSwapCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = readVerifiedStorage(original)
        require(sourceIndex != destinationIndex) { "Choose a different Pokémon in this Box." }
        val sourceSlot = protectedBoxedPokemon(before, sourceIndex, "selected")
        val destinationSlot = protectedBoxedPokemon(before, destinationIndex, "destination")
        val sourcePokemon = (sourceSlot.read as LetsGoSlotRead.Pokemon).value
        val destinationPokemon = (destinationSlot.read as LetsGoSlotRead.Pokemon).value
        val sourceOffset = recordOffset(sourceIndex)
        val destinationOffset = recordOffset(destinationIndex)
        val sourceRecord = original.copyOfRange(
            sourceOffset, sourceOffset + LetsGoPokemonReadOnly.RECORD_SIZE
        )
        val destinationRecord = original.copyOfRange(
            destinationOffset, destinationOffset + LetsGoPokemonReadOnly.RECORD_SIZE
        )

        val edited = original.copyOf()
        destinationRecord.copyInto(edited, sourceOffset)
        sourceRecord.copyInto(edited, destinationOffset)
        writeBlockCrc(edited, RECORD_BLOCK, STORAGE_RECORDS, STORAGE_RECORDS_LENGTH)
        verify(original, edited, sourceIndex, destinationIndex)
        return LetsGoPokemonSwapCopyPlan(
            original, edited, sourceRecord, destinationRecord,
            sourcePokemon, destinationPokemon, sourceIndex, destinationIndex,
            sourceOffset, destinationOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        sourceIndex: Int,
        destinationIndex: Int
    ) {
        require(sourceIndex != destinationIndex) { "A swap requires two different native records." }
        val beforeValidation = LetsGoSaveValidation.inspect(original)
        val afterValidation = LetsGoSaveValidation.inspect(edited)
        require(beforeValidation is LetsGoValidation.Verified &&
            afterValidation is LetsGoValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The swapped copy changed native title identity, size, or block integrity."
        }
        val before = LetsGoStorageReadOnly.read(original)
        val after = LetsGoStorageReadOnly.read(edited)
        require(before.count == after.count && before.partnerIndex == after.partnerIndex) {
            "The swap changed the native storage count or partner pointer."
        }
        val sourceSlot = protectedBoxedPokemon(before, sourceIndex, "selected")
        val destinationSlot = protectedBoxedPokemon(before, destinationIndex, "destination")
        val sourceOffset = recordOffset(sourceIndex)
        val destinationOffset = recordOffset(destinationIndex)
        val sourceRecord = original.copyOfRange(
            sourceOffset, sourceOffset + LetsGoPokemonReadOnly.RECORD_SIZE
        )
        val destinationRecord = original.copyOfRange(
            destinationOffset, destinationOffset + LetsGoPokemonReadOnly.RECORD_SIZE
        )
        require(edited.copyOfRange(
            sourceOffset, sourceOffset + LetsGoPokemonReadOnly.RECORD_SIZE
        ).contentEquals(destinationRecord) && edited.copyOfRange(
            destinationOffset, destinationOffset + LetsGoPokemonReadOnly.RECORD_SIZE
        ).contentEquals(sourceRecord)) {
            "The two exact native PB7 records were not exchanged."
        }
        val reopenedSource = protectedBoxedPokemon(after, sourceIndex, "reopened source")
        val reopenedDestination = protectedBoxedPokemon(after, destinationIndex, "reopened destination")
        require((reopenedSource.read as LetsGoSlotRead.Pokemon).value ==
            (destinationSlot.read as LetsGoSlotRead.Pokemon).value &&
            (reopenedDestination.read as LetsGoSlotRead.Pokemon).value ==
            (sourceSlot.read as LetsGoSlotRead.Pokemon).value) {
            "The persisted PB7 records did not reopen in their reviewed swapped positions."
        }
        require((0 until before.count).all { index ->
            before.slot(index).partyPositions == after.slot(index).partyPositions &&
                before.slot(index).partner == after.slot(index).partner &&
                (index == sourceIndex || index == destinationIndex || run {
                    val offset = recordOffset(index)
                    original.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE)
                        .contentEquals(edited.copyOfRange(
                            offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE
                        ))
                })
        }) { "An unrelated Pokémon record, party pointer, or partner pointer changed." }

        val recordCrc = descriptorCrcOffset(RECORD_BLOCK)
        require(original.indices.all { offset ->
            offset in sourceOffset until sourceOffset + LetsGoPokemonReadOnly.RECORD_SIZE ||
                offset in destinationOffset until destinationOffset + LetsGoPokemonReadOnly.RECORD_SIZE ||
                offset in recordCrc until recordCrc + 2 || original[offset] == edited[offset]
        }) { "An unrelated Let's Go save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun readVerifiedStorage(bytes: ByteArray): LetsGoStorageRead {
        require(LetsGoSaveValidation.inspect(bytes) is LetsGoValidation.Verified) {
            "A validated native Let's Go save is required."
        }
        return LetsGoStorageReadOnly.read(bytes)
    }

    private fun protectedBoxedPokemon(
        storage: LetsGoStorageRead,
        index: Int,
        label: String
    ): LetsGoStorageSlot {
        require(index in 0 until storage.count) { "The $label native storage record no longer exists." }
        val slot = storage.slot(index)
        val pokemon = (slot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The $label native PB7 is no longer checksum-valid.")
        require(slot.partyPositions.isEmpty() && !slot.partner && !pokemon.isPartnerForm) {
            "The $label Pokémon is assigned to the party or partner role, so it cannot be swapped as boxed storage."
        }
        require(!pokemon.isEgg) { "Egg PB7 records cannot be swapped." }
        require(LetsGoPersonalCatalogue.resolve(pokemon.species, pokemon.form) != null) {
            "The $label PB7 species/form has no verified native Let's Go profile."
        }
        return slot
    }

    private fun recordOffset(index: Int): Int =
        STORAGE_RECORDS + index * LetsGoPokemonReadOnly.RECORD_SIZE

    private fun readBounded(input: InputStream, maximum: Int, message: String): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val remaining = maximum + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= maximum) { message }
        }
        output.toByteArray()
    }

    private fun descriptorCrcOffset(block: Int): Int = DESCRIPTORS + block * 8 + 6
    private fun writeBlockCrc(bytes: ByteArray, block: Int, offset: Int, length: Int) {
        val value = LetsGoSaveValidation.crc16(bytes, offset, length)
        val crc = descriptorCrcOffset(block)
        bytes[crc] = value.toByte()
        bytes[crc + 1] = (value ushr 8).toByte()
    }
}

data class LetsGoPokemonSwapCandidate(val index: Int, val pokemon: LetsGoPokemonRead)
data class LetsGoPokemonSwapSelection(
    val sourceIndex: Int,
    val sourcePokemon: LetsGoPokemonRead,
    val candidates: List<LetsGoPokemonSwapCandidate>
)

class LetsGoPokemonSwapCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    destinationRecord: ByteArray,
    val sourcePokemon: LetsGoPokemonRead,
    val destinationPokemon: LetsGoPokemonRead,
    val sourceIndex: Int,
    val destinationIndex: Int,
    val sourceOffset: Int,
    val destinationOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    private val destinationRecord = destinationRecord.copyOf()
    val sourceSha256: String = LetsGoPokemonSwapEditing.sha256(this.original)
    val sourceRecordSha256: String = LetsGoPokemonSwapEditing.sha256(this.sourceRecord)
    val destinationRecordSha256: String = LetsGoPokemonSwapEditing.sha256(this.destinationRecord)
    val editedSha256: String = LetsGoPokemonSwapEditing.sha256(this.edited)
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
            LetsGoPokemonSwapEditing.verify(original, edited, sourceIndex, destinationIndex)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Pokémon swap."
            }
            LetsGoPokemonSwapEditing.verify(original, persisted, sourceIndex, destinationIndex)
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
