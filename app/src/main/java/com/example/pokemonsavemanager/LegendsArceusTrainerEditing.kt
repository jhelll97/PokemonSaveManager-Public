package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** First Gen 1-ordered Legends: Arceus Trainer Data edit: the native trainer-name field. */
object LegendsArceusTrainerEditing {
    private const val MAX_SAVE_SIZE = 0x13AD06
    private const val NAME_SIZE = 26

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val remaining = MAX_SAVE_SIZE + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= MAX_SAVE_SIZE) {
                "The document exceeds the supported Legends: Arceus save size."
            }
        }
        output.toByteArray()
    }

    fun stage(source: ByteArray, expectedSha256: String, requestedName: String): LegendsArceusTrainerNameCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = LegendsArceusTrainerReadOnly.read(original)
        val name = requestedName.trim()
        val encoded = encodeName(name)
        require(name != before.name) { "Enter a different trainer name." }
        val write = LegendsArceusSaveValidation.writeTrainerName(original, encoded)
        verify(original, write.bytes, name, write.absoluteOffset)
        return LegendsArceusTrainerNameCopyPlan(original, write.bytes, before.name, name, write.absoluteOffset)
    }

    internal fun verify(original: ByteArray, edited: ByteArray, expectedName: String, absoluteOffset: Int) {
        val beforeValidation = LegendsArceusSaveValidation.inspect(original)
        val afterValidation = LegendsArceusSaveValidation.inspect(edited)
        require(beforeValidation is LegendsArceusValidation.Verified &&
            afterValidation is LegendsArceusValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The trainer edit changed title identity or save size."
        }
        val before = LegendsArceusTrainerReadOnly.read(original)
        val after = LegendsArceusTrainerReadOnly.read(edited)
        require(after.name == expectedName && before.copy(name = expectedName) == after) {
            "The trainer-name edit did not reopen exactly."
        }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + NAME_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
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
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class LegendsArceusTrainerNameCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val beforeName: String,
    val afterName: String,
    val nameOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LegendsArceusTrainerEditing.sha256(this.original)
    val editedSha256: String = LegendsArceusTrainerEditing.sha256(this.edited)
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
            LegendsArceusTrainerEditing.verify(original, edited, afterName, nameOffset)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LegendsArceusTrainerEditing.verify(original, persisted, afterName, nameOffset)
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
