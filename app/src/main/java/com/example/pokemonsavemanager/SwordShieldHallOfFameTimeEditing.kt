package com.example.pokemonsavemanager

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.time.Instant

data class SwordShieldHallOfFameTimeEditRead(
    val version: SwordShieldVersion,
    val epochSeconds: Long
) {
    val utcTimestamp: String?
        get() = runCatching { Instant.ofEpochSecond(epochSeconds).toString() }.getOrNull()
}

/** Reviewed editing for PKHeX's documented native Sword/Shield Hall of Fame Unix timestamp. */
object SwordShieldHallOfFameTimeEditing {
    private const val FIELD_SIZE = 8

    fun readDocumentBytes(input: InputStream): ByteArray = SwordShieldTrainerEditing.readDocumentBytes(input)

    fun read(source: ByteArray): SwordShieldHallOfFameTimeEditRead {
        val block = SwordShieldSaveValidation.readHallOfFameTimeBlock(source)
        val value = ByteBuffer.wrap(block.value).order(ByteOrder.LITTLE_ENDIAN).long
        return SwordShieldHallOfFameTimeEditRead(block.version, value)
    }

    fun stage(source: ByteArray, expectedSha256: String, replacement: Long): SwordShieldHallOfFameTimeCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = read(original)
        require(replacement != before.epochSeconds) { "Change the Hall of Fame timestamp before reviewing." }
        val written = SwordShieldSaveValidation.writeHallOfFameTime(original, replacement)
        val after = SwordShieldHallOfFameTimeEditRead(before.version, replacement)
        verify(original, written.bytes, after, written.absoluteOffset)
        return SwordShieldHallOfFameTimeCopyPlan(original, written.bytes, before, after, written.absoluteOffset)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: SwordShieldHallOfFameTimeEditRead,
        absoluteOffset: Int
    ) {
        val before = read(original)
        val after = read(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The Hall of Fame timestamp edit changed title identity or save size."
        }
        require(after == expected) { "The Hall of Fame timestamp did not reopen exactly." }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + FIELD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "The Hall of Fame timestamp edit changed unrelated encrypted container data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class SwordShieldHallOfFameTimeCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: SwordShieldHallOfFameTimeEditRead,
    val after: SwordShieldHallOfFameTimeEditRead,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldHallOfFameTimeEditing.sha256(this.original)
    val editedSha256 = SwordShieldHallOfFameTimeEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Hall of Fame Time." }
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

    private fun verifyPlan(candidate: ByteArray) = SwordShieldHallOfFameTimeEditing.verify(
        original, candidate, after, absoluteOffset
    )
}
