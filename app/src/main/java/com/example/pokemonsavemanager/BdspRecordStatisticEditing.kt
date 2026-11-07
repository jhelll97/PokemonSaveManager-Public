package com.example.pokemonsavemanager

import java.security.MessageDigest

data class BdspRecordStatisticEditRead(
    val version: BdspVersion,
    val revision: BdspSaveRevision,
    val records: BdspRecordStatisticsRead,
    val entry: BdspRecordStatisticRead
)

/** Reviewed edits for one native record word in BDSP's active head record set. */
object BdspRecordStatisticEditing {
    private const val BASE_OFFSET = 0x8F3DC
    private const val RECORD_COUNT = 30
    private const val SET_COUNT = 12
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun read(source: ByteArray, recordId: Int): BdspRecordStatisticEditRead {
        require(recordId in 0 until RECORD_COUNT) { "The record ID must be 0 through 29." }
        val records = BdspRecordStatisticsReadOnly.read(source)
        return BdspRecordStatisticEditRead(records.version, records.revision, records, records.set(0).entry(recordId))
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        recordId: Int,
        replacement: Int
    ): BdspRecordStatisticCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = read(original, recordId)
        require(replacement != before.entry.signedValue) { "Change the saved head record before reviewing." }
        require(replacement in 0..before.entry.referenceLimit) {
            "Changed ${before.entry.displayName} must be 0 through ${before.entry.referenceLimit}."
        }
        val edited = original.copyOf()
        writeInt(edited, absoluteOffset(recordId), replacement)
        refreshDigest(edited)
        val after = read(edited, recordId)
        verify(original, edited, recordId, after)
        return BdspRecordStatisticCopyPlan(original, edited, before, after)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        recordId: Int,
        expected: BdspRecordStatisticEditRead
    ) {
        val before = BdspRecordStatisticsReadOnly.read(original)
        val after = BdspRecordStatisticsReadOnly.read(edited)
        require(
            original.size == edited.size && before.version == after.version &&
                before.revision == after.revision
        ) { "The record-statistic edit changed title identity, save revision, or file size." }
        require(after.set(0).entry(recordId).rawValue == expected.entry.rawValue) {
            "The reviewed head record did not reopen exactly."
        }
        require((0 until RECORD_COUNT).all { id ->
            id == recordId || before.set(0).entry(id).rawValue == after.set(0).entry(id).rawValue
        }) { "A different head record changed." }
        require((1 until SET_COUNT).all { set ->
            before.set(set).rawBytes == after.set(set).rawBytes
        }) { "A non-head record set changed." }
        require(original.indices.all { index ->
            index in absoluteOffset(recordId) until absoluteOffset(recordId) + 4 ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[index] == edited[index]
        }) { "The record-statistic edit changed an unrelated BDSP save byte." }
    }

    fun absoluteOffset(recordId: Int): Int {
        require(recordId in 0 until RECORD_COUNT)
        return BASE_OFFSET + recordId * 4
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun writeInt(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class BdspRecordStatisticCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: BdspRecordStatisticEditRead,
    val after: BdspRecordStatisticEditRead
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = BdspRecordStatisticEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Record Statistics." }
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
            BdspRecordStatisticEditing.verify(original, edited, before.entry.recordId, after)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspRecordStatisticEditing.verify(original, persisted, before.entry.recordId, after)
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
