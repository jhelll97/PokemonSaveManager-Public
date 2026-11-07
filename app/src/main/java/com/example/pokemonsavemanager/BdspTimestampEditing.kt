package com.example.pokemonsavemanager

import java.security.MessageDigest

data class BdspTimestampEditValue(
    val kind: BdspTimestampKind,
    val count: Long,
    val fileTimeTicks: Long,
    val utcTimestamp: String?
)

data class BdspTimestampEditRead(
    val version: BdspVersion,
    val revision: BdspSaveRevision,
    val values: List<BdspTimestampEditValue>
)

/** Reviewed editing for the four native BDSP UTC FILETIME tick fields; saved counters remain exact. */
object BdspTimestampEditing {
    private const val BASE_OFFSET = 0x95DAC
    private const val RECORD_SIZE = 12
    private const val TICKS_OFFSET = 4
    private const val TICKS_SIZE = 8
    private const val HASH_OFFSET = 0xE9818
    const val MAX_FILE_TIME_TICKS = 2650467743999999999L

    fun read(source: ByteArray): BdspTimestampEditRead {
        val read = BdspTimestampsReadOnly.read(source)
        return BdspTimestampEditRead(
            read.version,
            read.revision,
            read.entries.map { BdspTimestampEditValue(it.kind, it.count, it.fileTimeTicks, it.utcTimestamp) }
        )
    }

    fun stage(source: ByteArray, expectedSha256: String, replacement: List<Long>): BdspTimestampCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = read(original)
        require(replacement.size == BdspTimestampKind.values().size) {
            "All four native timestamp fields are required."
        }
        val changes = replacement.indices.map { replacement[it] != before.values[it].fileTimeTicks }
        require(changes.any { it }) { "Change at least one saved timestamp before reviewing." }
        replacement.indices.filter { changes[it] }.forEach { index ->
            require(replacement[index] in 0L..MAX_FILE_TIME_TICKS) {
                "Changed timestamps must fit the documented UTC FILETIME range."
            }
        }

        val edited = original.copyOf()
        replacement.indices.filter { changes[it] }.forEach { index ->
            writeI64(edited, tickOffset(index), replacement[index])
        }
        rebuildMd5(edited)
        val after = read(edited)
        verify(original, edited, after, changes)
        return BdspTimestampCopyPlan(original, edited, before, after, changes)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: BdspTimestampEditRead,
        changes: List<Boolean>
    ) {
        require(changes.size == BdspTimestampKind.values().size)
        val before = read(original)
        val after = read(edited)
        require(original.size == edited.size && before.version == after.version && before.revision == after.revision) {
            "The timestamp edit changed title identity, save revision or file size."
        }
        require(after == expected) { "The reviewed timestamp values did not reopen exactly." }
        require(before.values.indices.all { before.values[it].count == after.values[it].count }) {
            "A saved timestamp counter changed."
        }
        require(original.indices.all { index ->
            changes.indices.any { changes[it] && index in tickOffset(it) until tickOffset(it) + TICKS_SIZE } ||
                index in HASH_OFFSET until HASH_OFFSET + 16 ||
                original[index] == edited[index]
        }) { "The timestamp edit changed an unrelated BDSP save byte." }
    }

    fun tickOffset(index: Int): Int {
        require(index in BdspTimestampKind.values().indices)
        return BASE_OFFSET + index * RECORD_SIZE + TICKS_OFFSET
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun writeI64(bytes: ByteArray, offset: Int, value: Long) {
        repeat(TICKS_SIZE) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }

    private fun rebuildMd5(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + 16)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class BdspTimestampCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: BdspTimestampEditRead,
    val after: BdspTimestampEditRead,
    val changes: List<Boolean>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = BdspTimestampEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Saved Timestamps." }
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
            BdspTimestampEditing.verify(original, edited, after, changes)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspTimestampEditing.verify(original, persisted, after, changes)
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
