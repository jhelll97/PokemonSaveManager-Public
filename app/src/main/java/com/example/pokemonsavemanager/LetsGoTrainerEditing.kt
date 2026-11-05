package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Gen 1-ordered Trainer Data edits for the native Let's Go status block. */
object LetsGoTrainerEditing {
    private const val STATUS_OFFSET = 0x1000
    private const val STATUS_LENGTH = 0x168
    private const val NAME_OFFSET = STATUS_OFFSET + 0x38
    private const val NAME_SIZE = 26
    private const val TRAINER_ID_OFFSET = STATUS_OFFSET
    private const val TRAINER_ID_SIZE = 2
    private const val MISC_OFFSET = 0x4C00
    private const val MISC_LENGTH = 0x930
    private const val MONEY_OFFSET = MISC_OFFSET + 4
    private const val MONEY_SIZE = 4
    private const val MAX_MONEY = 9_999_999L
    private const val STATUS_CRC_OFFSET = LetsGoSaveValidation.DESCRIPTORS + (2 * 8) + 6
    private const val MISC_CRC_OFFSET = LetsGoSaveValidation.DESCRIPTORS + (5 * 8) + 6

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val remaining = LetsGoSaveValidation.FILE_SIZE + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= LetsGoSaveValidation.FILE_SIZE) {
                "The document exceeds the supported Let's Go save size."
            }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        requestedName: String,
        requestedTrainerId: Int,
        requestedMoney: Long
    ): LetsGoTrainerCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = LetsGoTrainerReadOnly.read(original)
        val name = if (requestedName == before.name) before.name else requestedName.trim()
        require(requestedTrainerId in 0..65535) { "Trainer ID must be between 0 and 65535." }
        require(requestedMoney in 0L..MAX_MONEY) { "Money must be between 0 and 9,999,999." }
        val nameChanged = name != before.name
        val trainerIdChanged = requestedTrainerId != before.tid16
        val moneyChanged = requestedMoney != before.money
        require(nameChanged || trainerIdChanged || moneyChanged) {
            "Change the Trainer Name, Trainer ID, or Money before reviewing."
        }

        val edited = original.copyOf()
        if (nameChanged) encodeName(name).copyInto(edited, NAME_OFFSET)
        if (trainerIdChanged) writeU16(edited, TRAINER_ID_OFFSET, requestedTrainerId)
        if (nameChanged || trainerIdChanged) {
            writeU16(edited, STATUS_CRC_OFFSET, LetsGoSaveValidation.crc16(edited, STATUS_OFFSET, STATUS_LENGTH))
        }
        if (moneyChanged) {
            writeU32(edited, MONEY_OFFSET, requestedMoney)
            writeU16(edited, MISC_CRC_OFFSET, LetsGoSaveValidation.crc16(edited, MISC_OFFSET, MISC_LENGTH))
        }
        verify(
            original, edited, name, requestedTrainerId, requestedMoney,
            nameChanged, trainerIdChanged, moneyChanged
        )
        return LetsGoTrainerCopyPlan(
            original, edited, before.name, name, before.tid16, requestedTrainerId,
            before.money, requestedMoney, nameChanged, trainerIdChanged, moneyChanged
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expectedName: String,
        expectedTrainerId: Int,
        expectedMoney: Long,
        nameChanged: Boolean,
        trainerIdChanged: Boolean,
        moneyChanged: Boolean
    ) {
        val before = LetsGoTrainerReadOnly.read(original)
        val after = LetsGoTrainerReadOnly.read(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The trainer edit changed title identity or save size."
        }
        val expectedId32 = (before.id32 and 0xFFFF0000L) or expectedTrainerId.toLong()
        require(
            after.name == expectedName && after.tid16 == expectedTrainerId &&
                after.money == expectedMoney &&
                before.copy(
                    name = expectedName, id32 = expectedId32, tid16 = expectedTrainerId,
                    money = expectedMoney
                ) == after
        ) {
            "The Trainer Data edit did not reopen exactly."
        }
        require(original.indices.all { index ->
            (nameChanged && index in NAME_OFFSET until NAME_OFFSET + NAME_SIZE) ||
                (trainerIdChanged && index in TRAINER_ID_OFFSET until TRAINER_ID_OFFSET + TRAINER_ID_SIZE) ||
                (moneyChanged && index in MONEY_OFFSET until MONEY_OFFSET + MONEY_SIZE) ||
                ((nameChanged || trainerIdChanged) && index in STATUS_CRC_OFFSET until STATUS_CRC_OFFSET + 2) ||
                (moneyChanged && index in MISC_CRC_OFFSET until MISC_CRC_OFFSET + 2) ||
                original[index] == edited[index]
        }) { "The Trainer Data edit changed unrelated save data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun encodeName(name: String): ByteArray {
        require(name.isNotEmpty()) { "Trainer name must contain 1–12 characters." }
        require(name.length <= 12) { "Trainer name must contain at most 12 UTF-16 code units." }
        require(name.none { Character.isISOControl(it) }) { "Trainer name cannot contain control characters." }
        val encoded = try {
            val buffer = Charsets.UTF_16LE.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(name))
            ByteArray(buffer.remaining()).also { buffer.get(it) }
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("Trainer name contains malformed Unicode.")
        }
        require(encoded.size <= 24) { "Trainer name must fit the native 12-unit field." }
        return ByteArray(NAME_SIZE).also { encoded.copyInto(it) }
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class LetsGoTrainerCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val beforeName: String,
    val afterName: String,
    val beforeTrainerId: Int,
    val afterTrainerId: Int,
    val beforeMoney: Long,
    val afterMoney: Long,
    val nameChanged: Boolean,
    val trainerIdChanged: Boolean,
    val moneyChanged: Boolean
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LetsGoTrainerEditing.sha256(this.original)
    val editedSha256: String = LetsGoTrainerEditing.sha256(this.edited)
    val nameOffset: Int = 0x1038
    val trainerIdOffset: Int = 0x1000
    val moneyOffset: Int = 0x4C04
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Trainer Data." }
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
            LetsGoTrainerEditing.verify(
                original, edited, afterName, afterTrainerId, afterMoney,
                nameChanged, trainerIdChanged, moneyChanged
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LetsGoTrainerEditing.verify(
                original, persisted, afterName, afterTrainerId, afterMoney,
                nameChanged, trainerIdChanged, moneyChanged
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
