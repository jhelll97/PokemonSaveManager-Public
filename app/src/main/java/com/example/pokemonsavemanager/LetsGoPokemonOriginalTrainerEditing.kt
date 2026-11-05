package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Changes only one native PB7 Original Trainer name in a reviewed new Let's Go save copy. */
object LetsGoPokemonOriginalTrainerEditing {
    private const val STORAGE_RECORDS = 0x5C00
    private const val STORAGE_RECORDS_LENGTH = 0x3F7A0
    private const val FOOTER = 0xB8600
    private const val DESCRIPTORS = FOOTER + 0x14
    private const val RECORD_BLOCK = 9
    private const val ORIGINAL_TRAINER = 0xB0
    private const val ORIGINAL_TRAINER_LENGTH = 26
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: InputStream): ByteArray = readBounded(
        input, LetsGoSaveValidation.FILE_SIZE, "The document exceeds the supported Let's Go save size."
    )

    fun readSaveBytes(input: InputStream): ByteArray = readDocumentBytes(input).also {
        require(it.size == LetsGoSaveValidation.FILE_SIZE) { "Select one complete native Let's Go save." }
    }

    fun stage(source: ByteArray, expectedSha256: String, storageIndex: Int, requestedOriginalTrainer: String): LetsGoOriginalTrainerCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val validation = LetsGoSaveValidation.inspect(original)
        require(validation is LetsGoValidation.Verified) { "A validated native Let's Go save is required." }
        val storage = LetsGoStorageReadOnly.read(original)
        require(storageIndex in 0 until storage.count) { "The selected native Pokémon record no longer exists." }
        val before = storage.slot(storageIndex).read as? LetsGoSlotRead.Pokemon
            ?: throw IllegalArgumentException("The selected native PB7 is not checksum-valid.")
        val originalTrainer = normalizeOriginalTrainer(requestedOriginalTrainer)
        require(originalTrainer != before.value.originalTrainer) { "Enter a different Original Trainer name before review." }

        val recordOffset = STORAGE_RECORDS + storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        val afterRecord = editRecord(beforeRecord, originalTrainer)
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        writeU16(edited, descriptorCrcOffset(RECORD_BLOCK),
            LetsGoSaveValidation.crc16(edited, STORAGE_RECORDS, STORAGE_RECORDS_LENGTH))
        verify(original, edited, storageIndex, originalTrainer)
        return LetsGoOriginalTrainerCopyPlan(original, edited, storageIndex, recordOffset, before.value, originalTrainer)
    }

    internal fun verify(original: ByteArray, edited: ByteArray, storageIndex: Int, originalTrainer: String) {
        val beforeValidation = LetsGoSaveValidation.inspect(original)
        val afterValidation = LetsGoSaveValidation.inspect(edited)
        require(beforeValidation is LetsGoValidation.Verified && afterValidation is LetsGoValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The edited copy changed native title identity, size, or block integrity."
        }
        val beforeStorage = LetsGoStorageReadOnly.read(original)
        val afterStorage = LetsGoStorageReadOnly.read(edited)
        require(beforeStorage.count == afterStorage.count && storageIndex in 0 until beforeStorage.count) {
            "The native stored-list identity changed."
        }
        val recordOffset = STORAGE_RECORDS + storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        require((0 until beforeStorage.count).all { index ->
            index == storageIndex || run {
                val offset = STORAGE_RECORDS + index * LetsGoPokemonReadOnly.RECORD_SIZE
                original.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE)
                    .contentEquals(edited.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE))
            }
        }) { "Another native Pokémon record changed." }

        val beforeSlot = beforeStorage.slot(storageIndex)
        val afterSlot = afterStorage.slot(storageIndex)
        require(beforeSlot.partyPositions == afterSlot.partyPositions && beforeSlot.partner == afterSlot.partner) {
            "The selected Pokémon's party or partner identity changed."
        }
        val beforePokemon = (beforeSlot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PB7 no longer reopens as a Pokémon.")
        val afterPokemon = (afterSlot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PB7 did not reopen as a Pokémon.")
        require(afterPokemon.originalTrainer == originalTrainer && afterPokemon.copy(originalTrainer = beforePokemon.originalTrainer) == beforePokemon) {
            "The persisted PB7 changed more than its Original Trainer name."
        }

        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        val afterRecord = edited.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in ORIGINAL_TRAINER until ORIGINAL_TRAINER + ORIGINAL_TRAINER_LENGTH ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PB7 edit exceeded the Original Trainer name and checksum fields." }

        val crcOffset = descriptorCrcOffset(RECORD_BLOCK)
        require(original.indices.all { offset ->
            offset in recordOffset until recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE ||
                offset in crcOffset until crcOffset + 2 || original[offset] == edited[offset]
        }) { "An unrelated Let's Go save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun normalizeOriginalTrainer(value: String): String {
        val originalTrainer = value.trim()
        require(originalTrainer.isNotEmpty()) { "Enter an Original Trainer name." }
        require(originalTrainer.length <= 12) { "Let's Go Original Trainer names are limited to 12 UTF-16 characters." }
        require(originalTrainer.none { it == '\u0000' || Character.isISOControl(it) }) {
            "The Original Trainer name contains unsupported control characters."
        }
        val encoded = originalTrainer.toByteArray(Charsets.UTF_16LE)
        require(encoded.size <= 24 && String(encoded, Charsets.UTF_16LE) == originalTrainer) {
            "The Original Trainer name cannot be represented safely in the native UTF-16 field."
        }
        return originalTrainer
    }

    private fun editRecord(record: ByteArray, originalTrainer: String): ByteArray {
        val plain = decryptAndUnshuffle(record)
        plain.fill(0, ORIGINAL_TRAINER, ORIGINAL_TRAINER + ORIGINAL_TRAINER_LENGTH)
        originalTrainer.toByteArray(Charsets.UTF_16LE).copyInto(plain, ORIGINAL_TRAINER)
        var checksum = 0
        for (offset in 8 until 0xE8 step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun decryptAndUnshuffle(record: ByteArray): ByteArray {
        require(record.size == LetsGoPokemonReadOnly.RECORD_SIZE) { "Expected one complete native PB7 record." }
        val ec = u32(record, 0)
        val decrypted = record.copyOf()
        xorWords(decrypted, 8, 0xE8, ec)
        xorWords(decrypted, 0xE8, LetsGoPokemonReadOnly.RECORD_SIZE, ec)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val plain = decrypted.copyOf()
        for (block in 0..3) {
            val from = 8 + (order[block] - '0') * 56
            decrypted.copyInto(plain, 8 + block * 56, from, from + 56)
        }
        return plain
    }

    private fun shuffleAndEncrypt(plain: ByteArray): ByteArray {
        val ec = u32(plain, 0)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val shuffled = plain.copyOf()
        for (block in 0..3) {
            val destination = 8 + (order[block] - '0') * 56
            plain.copyInto(shuffled, destination, 8 + block * 56, 8 + (block + 1) * 56)
        }
        xorWords(shuffled, 8, 0xE8, ec)
        xorWords(shuffled, 0xE8, LetsGoPokemonReadOnly.RECORD_SIZE, ec)
        return shuffled
    }

    private fun xorWords(bytes: ByteArray, from: Int, endExclusive: Int, seed: Long) {
        var state = seed
        for (offset in from until endExclusive step 2) {
            state = (state * 0x41C64E6DL + 0x6073L) and 0xFFFFFFFFL
            writeU16(bytes, offset, u16(bytes, offset) xor (state ushr 16).toInt())
        }
    }

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
    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long =
        u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }
}

class LetsGoOriginalTrainerCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val storageIndex: Int,
    val recordOffset: Int,
    val before: LetsGoPokemonRead,
    val originalTrainer: String
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LetsGoPokemonOriginalTrainerEditing.sha256(this.original)
    val editedSha256: String = LetsGoPokemonOriginalTrainerEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This Original Trainer review was already used. Reopen Pokémon Boxes." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) { "The recovery backup did not verify. Nothing was written." }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LetsGoPokemonOriginalTrainerEditing.verify(original, edited, storageIndex, originalTrainer)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed Original Trainer edit." }
            LetsGoPokemonOriginalTrainerEditing.verify(original, persisted, storageIndex, originalTrainer)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Original Trainer copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
