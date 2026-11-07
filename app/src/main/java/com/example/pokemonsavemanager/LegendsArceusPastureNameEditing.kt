package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class LegendsArceusPastureNameEditRead(
    val version: LegendsArceusVersion,
    val pasture: Int,
    val name: String,
    val names: List<String>
) {
    val displayName: String get() = name.ifBlank { "Pasture $pasture" }
}

/** Reviewed editing for PKHeX's documented 32-entry native Pasture-name object. */
object LegendsArceusPastureNameEditing {
    private const val MAX_SAVE_SIZE = 0x13AD06
    private const val PASTURE_COUNT = 32
    private const val NAME_BYTES = 0x22
    private const val MAX_NAME_CODE_UNITS = 17

    fun read(source: ByteArray, pasture: Int): LegendsArceusPastureNameEditRead {
        require(pasture in 1..PASTURE_COUNT) { "Pasture must be between 1 and 32." }
        val block = LegendsArceusSaveValidation.readPastureNameBlock(source)
        require(block.data.size == PASTURE_COUNT * NAME_BYTES) {
            "The native Legends: Arceus Pasture-name object has the wrong size."
        }
        val names = List(PASTURE_COUNT) { index ->
            decode(block.data.copyOfRange(index * NAME_BYTES, (index + 1) * NAME_BYTES))
        }
        return LegendsArceusPastureNameEditRead(block.version, pasture, names[pasture - 1], names)
    }

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

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        pasture: Int,
        replacement: String
    ): LegendsArceusPastureNameCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val encoded = encode(replacement)
        val before = read(original, pasture)
        require(replacement != before.name) { "Change the Pasture name before reviewing." }
        val write = LegendsArceusSaveValidation.writePastureName(original, pasture, encoded)
        val after = read(write.bytes, pasture)
        require(after.name == replacement) { "The requested Pasture name did not stage exactly." }
        verify(original, write.bytes, before, after, write.absoluteOffset)
        return LegendsArceusPastureNameCopyPlan(
            original, write.bytes, before, after, write.absoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        before: LegendsArceusPastureNameEditRead,
        expected: LegendsArceusPastureNameEditRead,
        absoluteOffset: Int
    ) {
        val beforeValidation = LegendsArceusSaveValidation.inspect(original)
        val afterValidation = LegendsArceusSaveValidation.inspect(edited)
        require(
            beforeValidation is LegendsArceusValidation.Verified &&
                afterValidation is LegendsArceusValidation.Verified &&
                beforeValidation.version == afterValidation.version &&
                original.size == edited.size
        ) { "The Pasture-name edit changed title identity or save size." }
        val reopened = read(edited, expected.pasture)
        require(reopened.name == expected.name && reopened.names == expected.names) {
            "The reviewed Pasture name did not reopen exactly."
        }
        require(before.names.indices.all { index ->
            index == before.pasture - 1 || before.names[index] == reopened.names[index]
        }) { "A different native Pasture name changed." }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + NAME_BYTES ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "The Pasture-name edit changed unrelated save data." }
    }

    private fun decode(bytes: ByteArray): String {
        require(bytes.size == NAME_BYTES) { "A complete native Pasture-name field is required." }
        val result = StringBuilder(MAX_NAME_CODE_UNITS)
        for (offset in bytes.indices step 2) {
            val value = (bytes[offset].toInt() and 255) or
                ((bytes[offset + 1].toInt() and 255) shl 8)
            if (value == 0) break
            result.append(value.toChar())
        }
        return result.toString()
    }

    private fun encode(value: String): ByteArray {
        require(value.length <= MAX_NAME_CODE_UNITS) {
            "Pasture names are limited to 17 UTF-16 code units."
        }
        require(value.none { it == '\u0000' || it.isISOControl() }) {
            "Pasture names cannot contain null or control characters."
        }
        val encoded = value.toByteArray(Charsets.UTF_16LE)
        require(encoded.size <= NAME_BYTES &&
            encoded.toString(Charsets.UTF_16LE) == value) {
            "The Pasture name cannot be represented exactly in the native UTF-16 field."
        }
        return ByteArray(NAME_BYTES).also { encoded.copyInto(it) }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class LegendsArceusPastureNameCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: LegendsArceusPastureNameEditRead,
    val after: LegendsArceusPastureNameEditRead,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = LegendsArceusPastureNameEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This Pasture-name review was already used. Reopen Pokémon Boxes." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) {
            "Choose a new empty document; existing files cannot be replaced."
        }
        require(retainBackup(original.copyOf()).contentEquals(original)) {
            "The recovery backup did not verify. Nothing was written."
        }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LegendsArceusPastureNameEditing.verify(original, edited, before, after, absoluteOffset)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Pasture-name edit."
            }
            LegendsArceusPastureNameEditing.verify(original, persisted, before, after, absoluteOffset)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Pasture-name copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
