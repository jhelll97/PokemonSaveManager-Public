package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** First Gen 1-ordered Trainer Data edit: one native Let's Go trainer-name field. */
object LetsGoTrainerEditing {
    private const val STATUS_OFFSET = 0x1000
    private const val STATUS_LENGTH = 0x168
    private const val NAME_OFFSET = STATUS_OFFSET + 0x38
    private const val NAME_SIZE = 26
    private const val STATUS_CRC_OFFSET = LetsGoSaveValidation.DESCRIPTORS + (2 * 8) + 6

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

    fun stage(source: ByteArray, expectedSha256: String, requestedName: String): LetsGoTrainerNameCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = LetsGoTrainerReadOnly.read(original)
        val name = requestedName.trim()
        val encoded = encodeName(name)
        require(name != before.name) { "Enter a different trainer name." }

        val edited = original.copyOf()
        encoded.copyInto(edited, NAME_OFFSET)
        writeU16(edited, STATUS_CRC_OFFSET, LetsGoSaveValidation.crc16(edited, STATUS_OFFSET, STATUS_LENGTH))
        verify(original, edited, name)
        return LetsGoTrainerNameCopyPlan(original, edited, before.name, name)
    }

    internal fun verify(original: ByteArray, edited: ByteArray, expectedName: String) {
        val before = LetsGoTrainerReadOnly.read(original)
        val after = LetsGoTrainerReadOnly.read(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The trainer edit changed title identity or save size."
        }
        require(after.name == expectedName && before.copy(name = expectedName) == after) {
            "The trainer-name edit did not reopen exactly."
        }
        require(original.indices.all { index ->
            index in NAME_OFFSET until NAME_OFFSET + NAME_SIZE ||
                index in STATUS_CRC_OFFSET until STATUS_CRC_OFFSET + 2 ||
                original[index] == edited[index]
        }) { "The trainer-name edit changed unrelated save data." }
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
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class LetsGoTrainerNameCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val beforeName: String,
    val afterName: String
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LetsGoTrainerEditing.sha256(this.original)
    val editedSha256: String = LetsGoTrainerEditing.sha256(this.edited)
    val nameOffset: Int = 0x1038
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
            LetsGoTrainerEditing.verify(original, edited, afterName)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LetsGoTrainerEditing.verify(original, persisted, afterName)
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
