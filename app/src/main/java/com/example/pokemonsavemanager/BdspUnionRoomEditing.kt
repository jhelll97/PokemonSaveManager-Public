package com.example.pokemonsavemanager

import java.security.MessageDigest

data class BdspUnionRoomEditRead(
    val version: BdspVersion,
    val revision: BdspSaveRevision,
    val record: BdspUnionRoomRead
)

data class BdspUnionRoomReplacement(
    val initTalkWord: Long,
    val penaltyCount: Int,
    val penaltyTimeBits: Long
)

/** Reviewed editing for the three documented native BDSP Union Room fields. */
object BdspUnionRoomEditing {
    private const val BASE_OFFSET = 0xCEA10
    private const val RECORD_LENGTH = 12
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun read(source: ByteArray): BdspUnionRoomEditRead {
        val record = BdspUnionRoomReadOnly.read(source)
        return BdspUnionRoomEditRead(record.version, record.revision, record)
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        replacement: BdspUnionRoomReplacement
    ): BdspUnionRoomCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(replacement.penaltyTimeBits in 0L..0xFFFF_FFFFL) {
            "Changed PenaltyTime bits must be an unsigned 32-bit word."
        }
        val before = read(original)
        val edited = original.copyOf()
        val ranges = mutableListOf<IntRange>()
        val fields = mutableListOf<String>()

        if (replacement.initTalkWord != before.record.rawInitTalkWord) {
            require(replacement.initTalkWord in 0L..1L) {
                "Changed initial-talk flag must be 0 clear or 1 set."
            }
            writeUInt32(edited, BASE_OFFSET, replacement.initTalkWord)
            ranges += BASE_OFFSET until BASE_OFFSET + 4
            fields += "initial-talk flag"
        }
        if (replacement.penaltyCount != before.record.penaltyCount) {
            writeInt32(edited, BASE_OFFSET + 4, replacement.penaltyCount)
            ranges += BASE_OFFSET + 4 until BASE_OFFSET + 8
            fields += "PenaltyCount"
        }
        if (replacement.penaltyTimeBits != before.record.rawPenaltyTimeBits) {
            writeUInt32(edited, BASE_OFFSET + 8, replacement.penaltyTimeBits)
            ranges += BASE_OFFSET + 8 until BASE_OFFSET + RECORD_LENGTH
            fields += "PenaltyTime raw bits"
        }
        require(ranges.isNotEmpty()) { "Change at least one saved Union Room field before reviewing." }
        refreshDigest(edited)
        val after = read(edited)
        require(
            after.record.rawInitTalkWord == replacement.initTalkWord &&
                after.record.penaltyCount == replacement.penaltyCount &&
                after.record.rawPenaltyTimeBits == replacement.penaltyTimeBits
        ) { "The requested Union Room values did not stage exactly." }
        verify(original, edited, after, ranges)
        return BdspUnionRoomCopyPlan(original, edited, before, after, ranges.toList(), fields.toList())
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: BdspUnionRoomEditRead,
        allowedRanges: List<IntRange>
    ) {
        val before = BdspUnionRoomReadOnly.read(original)
        val after = BdspUnionRoomReadOnly.read(edited)
        require(
            original.size == edited.size && before.version == after.version &&
                before.revision == after.revision
        ) { "The Union Room edit changed title identity, save revision, or file size." }
        require(after.rawBytes == expected.record.rawBytes) {
            "The reviewed Union Room record did not reopen exactly."
        }
        require(original.indices.all { index ->
            allowedRanges.any { index in it } ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[index] == edited[index]
        }) { "The Union Room edit changed an unrelated BDSP save byte." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun writeInt32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }

    private fun writeUInt32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class BdspUnionRoomCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: BdspUnionRoomEditRead,
    val after: BdspUnionRoomEditRead,
    val allowedRanges: List<IntRange>,
    val changedFields: List<String>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = BdspUnionRoomEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Union Room Saved Data." }
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
            BdspUnionRoomEditing.verify(original, edited, after, allowedRanges)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspUnionRoomEditing.verify(original, persisted, after, allowedRanges)
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
