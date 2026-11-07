package com.example.pokemonsavemanager

import java.security.MessageDigest

data class LetsGoAdventureStartEditRead(
    val version: LetsGoVersion,
    val secondsSinceEpoch: ULong,
    val utcTimestamp: String?
)

/** Reviewed editing for PlayerGeoLocation block 7's native unsigned 64-bit adventure-start timestamp. */
object LetsGoAdventureStartEditing {
    private const val FIELD_OFFSET = 0x5800 + 0x48
    private const val FIELD_SIZE = 8
    private const val CRC_OFFSET = LetsGoSaveValidation.DESCRIPTORS + 7 * 8 + 6

    fun read(source: ByteArray): LetsGoAdventureStartEditRead {
        val read = LetsGoAdventureBeginReadOnly.read(source)
        return LetsGoAdventureStartEditRead(read.version, read.secondsSinceEpoch, read.utcTimestamp)
    }

    fun stage(source: ByteArray, expectedSha256: String, secondsSinceEpoch: ULong): LetsGoAdventureStartCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = read(original)
        require(secondsSinceEpoch != before.secondsSinceEpoch) {
            "Change the adventure-start timestamp before reviewing."
        }
        val edited = original.copyOf()
        repeat(FIELD_SIZE) { index ->
            edited[FIELD_OFFSET + index] = (secondsSinceEpoch shr (index * 8)).toByte()
        }
        writeU16(edited, CRC_OFFSET, LetsGoSaveValidation.crc16(edited, 0x5800, 0x130))
        val after = read(edited)
        require(after.secondsSinceEpoch == secondsSinceEpoch) {
            "The adventure-start timestamp did not reopen exactly."
        }
        verify(original, edited, after)
        return LetsGoAdventureStartCopyPlan(original, edited, before, after)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: LetsGoAdventureStartEditRead
    ) {
        val before = read(original)
        val after = read(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The adventure-start edit changed title identity or save size."
        }
        require(after == expected) { "The reviewed adventure-start value did not reopen exactly." }
        require(original.indices.all { index ->
            index in FIELD_OFFSET until FIELD_OFFSET + FIELD_SIZE ||
                index in CRC_OFFSET until CRC_OFFSET + 2 ||
                original[index] == edited[index]
        }) { "The adventure-start edit changed an unrelated Let's Go save byte." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class LetsGoAdventureStartCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: LetsGoAdventureStartEditRead,
    val after: LetsGoAdventureStartEditRead
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = LetsGoAdventureStartEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Adventure Start." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        require(retainBackup(original.copyOf()).contentEquals(original)) {
            "The recovery backup did not verify. Nothing was written."
        }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LetsGoAdventureStartEditing.verify(original, edited, after)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LetsGoAdventureStartEditing.verify(original, persisted, after)
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
