package com.example.pokemonsavemanager

import java.security.MessageDigest

data class BdspBattleTrainerStatusEditRead(
    val version: BdspVersion,
    val revision: BdspSaveRevision,
    val status: BdspBattleTrainerStatusRead,
    val entry: BdspBattleTrainerStatusEntry
)

data class BdspBattleTrainerStatusReplacement(
    val winWord: Long,
    val battleSearcherWord: Long
)

/** Reviewed edits for one native BDSP battle-trainer status record. */
object BdspBattleTrainerStatusEditing {
    private const val BASE_OFFSET = 0x7D3E0
    private const val ENTRY_COUNT = 707
    private const val ENTRY_SIZE = 8
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun read(source: ByteArray, index: Int): BdspBattleTrainerStatusEditRead {
        require(index in 0 until ENTRY_COUNT) { "The saved trainer index must be 0 through 706." }
        val status = BdspBattleTrainerStatusReadOnly.read(source)
        return BdspBattleTrainerStatusEditRead(status.version, status.revision, status, status.entry(index))
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        index: Int,
        replacement: BdspBattleTrainerStatusReplacement
    ): BdspBattleTrainerStatusCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = read(original, index)
        val edited = original.copyOf()
        val offset = entryOffset(index)
        val ranges = mutableListOf<IntRange>()
        val fields = mutableListOf<String>()

        if (replacement.winWord != before.entry.rawWinWord) {
            require(replacement.winWord in 0L..1L) { "Changed Win flag word must be 0 clear or 1 set." }
            writeUInt32(edited, offset, replacement.winWord)
            ranges += offset until offset + 4
            fields += "Win flag"
        }
        if (replacement.battleSearcherWord != before.entry.rawBattleSearcherWord) {
            require(replacement.battleSearcherWord in 0L..1L) {
                "Changed battle-searcher flag word must be 0 clear or 1 set."
            }
            writeUInt32(edited, offset + 4, replacement.battleSearcherWord)
            ranges += offset + 4 until offset + 8
            fields += "battle-searcher flag"
        }
        require(ranges.isNotEmpty()) { "Change at least one saved flag before reviewing." }
        refreshDigest(edited)
        val after = read(edited, index)
        require(after.entry.rawWinWord == replacement.winWord &&
            after.entry.rawBattleSearcherWord == replacement.battleSearcherWord
        ) { "The requested battle-trainer status did not stage exactly." }
        verify(original, edited, index, after, ranges)
        return BdspBattleTrainerStatusCopyPlan(original, edited, before, after, ranges.toList(), fields.toList())
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        index: Int,
        expected: BdspBattleTrainerStatusEditRead,
        allowedRanges: List<IntRange>
    ) {
        val before = BdspBattleTrainerStatusReadOnly.read(original)
        val after = BdspBattleTrainerStatusReadOnly.read(edited)
        require(original.size == edited.size && before.version == after.version && before.revision == after.revision) {
            "The battle-trainer edit changed title identity, save revision, or file size."
        }
        require(after.entry(index).rawBytes == expected.entry.rawBytes) {
            "The reviewed battle-trainer entry did not reopen exactly."
        }
        require((0 until ENTRY_COUNT).all { trainer ->
            trainer == index || before.entry(trainer).rawBytes == after.entry(trainer).rawBytes
        }) { "A different battle-trainer entry changed." }
        require(original.indices.all { byteIndex ->
            allowedRanges.any { byteIndex in it } ||
                byteIndex in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[byteIndex] == edited[byteIndex]
        }) { "The battle-trainer edit changed an unrelated BDSP save byte." }
    }

    fun entryOffset(index: Int): Int {
        require(index in 0 until ENTRY_COUNT)
        return BASE_OFFSET + index * ENTRY_SIZE
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun writeUInt32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class BdspBattleTrainerStatusCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: BdspBattleTrainerStatusEditRead,
    val after: BdspBattleTrainerStatusEditRead,
    val allowedRanges: List<IntRange>,
    val changedFields: List<String>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = BdspBattleTrainerStatusEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Battle Trainer Status." }
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
            BdspBattleTrainerStatusEditing.verify(
                original, edited, before.entry.index, after, allowedRanges
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspBattleTrainerStatusEditing.verify(
                original, persisted, before.entry.index, after, allowedRanges
            )
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
