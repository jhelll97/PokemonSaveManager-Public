package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class LegendsArceusPastureUnlockEditRead(
    val version: LegendsArceusVersion,
    val unlockedPastures: Int
)

/** Reviewed editing for PKHeX's documented native U8 BoxesUnlocked value. */
object LegendsArceusPastureUnlockEditing {
    private const val MAX_SAVE_SIZE = 0x13AD06
    private const val MAX_PASTURES = 32
    private const val FIELD_LENGTH = 1

    fun read(source: ByteArray): LegendsArceusPastureUnlockEditRead {
        val value = LegendsArceusSaveValidation.readPastureUnlockCount(source)
        return LegendsArceusPastureUnlockEditRead(value.version, value.unlockedPastures)
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
        replacement: Int
    ): LegendsArceusPastureUnlockCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(replacement in 0..MAX_PASTURES) {
            "Unlocked Pastures must be between 0 and 32."
        }
        val before = read(original)
        require(replacement != before.unlockedPastures) {
            "Change the unlocked-Pasture count before reviewing."
        }
        val write = LegendsArceusSaveValidation.writePastureUnlockCount(original, replacement)
        val after = read(write.bytes)
        require(after.unlockedPastures == replacement) {
            "The requested unlocked-Pasture count did not stage exactly."
        }
        verify(original, write.bytes, after, write.absoluteOffset)
        return LegendsArceusPastureUnlockCopyPlan(
            original, write.bytes, before, after, write.absoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: LegendsArceusPastureUnlockEditRead,
        absoluteOffset: Int
    ) {
        val beforeValidation = LegendsArceusSaveValidation.inspect(original)
        val afterValidation = LegendsArceusSaveValidation.inspect(edited)
        require(
            beforeValidation is LegendsArceusValidation.Verified &&
                afterValidation is LegendsArceusValidation.Verified &&
                beforeValidation.version == afterValidation.version &&
                original.size == edited.size
        ) { "The unlocked-Pasture edit changed title identity or save size." }
        val reopened = read(edited)
        require(reopened == expected) {
            "The reviewed unlocked-Pasture count did not reopen exactly."
        }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + FIELD_LENGTH ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "The unlocked-Pasture edit changed unrelated save data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class LegendsArceusPastureUnlockCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: LegendsArceusPastureUnlockEditRead,
    val after: LegendsArceusPastureUnlockEditRead,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = LegendsArceusPastureUnlockEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) {
            "This unlocked-Pasture review was already used. Reopen Pokémon Boxes."
        }
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
            LegendsArceusPastureUnlockEditing.verify(
                original, edited, after, absoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed unlocked-Pasture edit."
            }
            LegendsArceusPastureUnlockEditing.verify(
                original, persisted, after, absoluteOffset
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Unlocked-Pasture copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
