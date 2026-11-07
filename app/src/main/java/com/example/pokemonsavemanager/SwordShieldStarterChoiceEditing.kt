package com.example.pokemonsavemanager

import java.io.InputStream
import java.security.MessageDigest

data class SwordShieldStarterChoiceEditRead(
    val version: SwordShieldVersion,
    val rawValue: Long,
    val starterName: String?
)

/** Reviewed editing for Sword/Shield's documented native starter-choice record. */
object SwordShieldStarterChoiceEditing {
    private const val FIELD_SIZE = 4

    fun readDocumentBytes(input: InputStream): ByteArray = SwordShieldTrainerEditing.readDocumentBytes(input)

    fun read(source: ByteArray): SwordShieldStarterChoiceEditRead {
        val block = SwordShieldSaveValidation.readStarterChoiceBlock(source)
        val raw = block.value.foldIndexed(0L) { index, result, value ->
            result or ((value.toLong() and 0xFF) shl (index * 8))
        }
        return SwordShieldStarterChoiceEditRead(block.version, raw, name(raw))
    }

    fun stage(source: ByteArray, expectedSha256: String, starter: Long): SwordShieldStarterChoiceCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(starter in 0L..2L) { "Choose Grookey, Scorbunny, or Sobble." }
        val before = read(original)
        require(before.rawValue != starter) { "Choose a different starter record before reviewing." }
        val written = SwordShieldSaveValidation.writeStarterChoice(original, starter)
        verify(original, written.bytes, starter, written.absoluteOffset)
        return SwordShieldStarterChoiceCopyPlan(
            original, written.bytes, before.rawValue, before.starterName,
            starter, name(starter)!!, written.absoluteOffset
        )
    }

    internal fun verify(original: ByteArray, edited: ByteArray, expectedStarter: Long, absoluteOffset: Int) {
        val before = read(original)
        val after = read(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The starter-choice edit changed title identity or save size."
        }
        require(after.rawValue == expectedStarter && after.starterName == name(expectedStarter)) {
            "The starter-choice record did not reopen exactly."
        }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + FIELD_SIZE ||
                index in hashOffset until original.size ||
                original[index] == edited[index]
        }) { "The starter-choice edit changed unrelated encrypted container data." }
    }

    fun name(value: Long): String? = when (value) {
        0L -> "Grookey"
        1L -> "Scorbunny"
        2L -> "Sobble"
        else -> null
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class SwordShieldStarterChoiceCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val beforeValue: Long,
    val beforeName: String?,
    val afterValue: Long,
    val afterName: String,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldStarterChoiceEditing.sha256(this.original)
    val editedSha256 = SwordShieldStarterChoiceEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Starter Choice." }
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
            verifyPlan(edited)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            verifyPlan(persisted)
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

    private fun verifyPlan(candidate: ByteArray) = SwordShieldStarterChoiceEditing.verify(
        original, candidate, afterValue, absoluteOffset
    )
}
