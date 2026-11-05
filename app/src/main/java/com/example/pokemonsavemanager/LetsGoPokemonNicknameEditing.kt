package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Changes only one native PB7 nickname in a reviewed new Let's Go save copy. */
object LetsGoPokemonNicknameEditing {
    private const val STORAGE_RECORDS = 0x5C00
    private const val STORAGE_RECORDS_LENGTH = 0x3F7A0
    private const val FOOTER = 0xB8600
    private const val DESCRIPTORS = FOOTER + 0x14
    private const val RECORD_BLOCK = 9
    private const val NICKNAME = 0x40
    private const val NICKNAME_LENGTH = 26
    private const val IV32 = 0x74
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: InputStream): ByteArray = readBounded(
        input, LetsGoSaveValidation.FILE_SIZE, "The document exceeds the supported Let's Go save size."
    )

    fun readSaveBytes(input: InputStream): ByteArray = readDocumentBytes(input).also {
        require(it.size == LetsGoSaveValidation.FILE_SIZE) { "Select one complete native Let's Go save." }
    }

    fun stage(source: ByteArray, expectedSha256: String, storageIndex: Int, requestedNickname: String): LetsGoNicknameCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val validation = LetsGoSaveValidation.inspect(original)
        require(validation is LetsGoValidation.Verified) { "A validated native Let's Go save is required." }
        val storage = LetsGoStorageReadOnly.read(original)
        require(storageIndex in 0 until storage.count) { "The selected native Pokémon record no longer exists." }
        val before = storage.slot(storageIndex).read as? LetsGoSlotRead.Pokemon
            ?: throw IllegalArgumentException("The selected native PB7 is not checksum-valid.")
        val nickname = normalizeNickname(requestedNickname)
        require(nickname != before.value.nickname) { "Enter a different nickname before review." }

        val recordOffset = STORAGE_RECORDS + storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        val afterRecord = editRecord(beforeRecord, nickname)
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        writeU16(edited, descriptorCrcOffset(RECORD_BLOCK),
            LetsGoSaveValidation.crc16(edited, STORAGE_RECORDS, STORAGE_RECORDS_LENGTH))
        verify(original, edited, storageIndex, nickname)
        return LetsGoNicknameCopyPlan(original, edited, storageIndex, recordOffset, before.value, nickname)
    }

    internal fun verify(original: ByteArray, edited: ByteArray, storageIndex: Int, nickname: String) {
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
        require(afterPokemon.nickname == nickname && afterPokemon.copy(nickname = beforePokemon.nickname) == beforePokemon) {
            "The persisted PB7 changed more than its nickname."
        }

        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        val afterRecord = edited.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(u32(afterPlain, IV32) == (u32(beforePlain, IV32) or 0x80000000L)) {
            "The native nickname flag was not set exactly."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in NICKNAME until NICKNAME + NICKNAME_LENGTH || offset == IV32 + 3 ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PB7 edit exceeded the nickname, nickname flag, and checksum fields." }

        val crcOffset = descriptorCrcOffset(RECORD_BLOCK)
        require(original.indices.all { offset ->
            offset in recordOffset until recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE ||
                offset in crcOffset until crcOffset + 2 || original[offset] == edited[offset]
        }) { "An unrelated Let's Go save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun normalizeNickname(value: String): String {
        val nickname = value.trim()
        require(nickname.isNotEmpty()) { "Enter a nickname." }
        require(nickname.length <= 12) { "Let's Go nicknames are limited to 12 UTF-16 characters." }
        require(nickname.none { it == '\u0000' || Character.isISOControl(it) }) {
            "The nickname contains unsupported control characters."
        }
        val encoded = nickname.toByteArray(Charsets.UTF_16LE)
        require(encoded.size <= 24 && String(encoded, Charsets.UTF_16LE) == nickname) {
            "The nickname cannot be represented safely in the native UTF-16 field."
        }
        return nickname
    }

    private fun editRecord(record: ByteArray, nickname: String): ByteArray {
        val plain = decryptAndUnshuffle(record)
        plain.fill(0, NICKNAME, NICKNAME + NICKNAME_LENGTH)
        nickname.toByteArray(Charsets.UTF_16LE).copyInto(plain, NICKNAME)
        writeU32(plain, IV32, u32(plain, IV32) or 0x80000000L)
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
    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        writeU16(bytes, offset, value.toInt()); writeU16(bytes, offset + 2, (value ushr 16).toInt())
    }
}

class LetsGoNicknameCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val storageIndex: Int,
    val recordOffset: Int,
    val before: LetsGoPokemonRead,
    val nickname: String
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LetsGoPokemonNicknameEditing.sha256(this.original)
    val editedSha256: String = LetsGoPokemonNicknameEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This nickname review was already used. Reopen Pokémon Boxes." }
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
            LetsGoPokemonNicknameEditing.verify(original, edited, storageIndex, nickname)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed nickname edit." }
            LetsGoPokemonNicknameEditing.verify(original, persisted, storageIndex, nickname)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Nickname copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
