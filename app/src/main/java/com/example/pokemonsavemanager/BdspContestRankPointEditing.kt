package com.example.pokemonsavemanager

import java.security.MessageDigest

data class BdspContestRankPointEditRead(
    val version: BdspVersion,
    val revision: BdspSaveRevision,
    val rankPoints: Long
)

/** Reviewed editing for the documented native BDSP Contest Rank Point field. */
object BdspContestRankPointEditing {
    const val RANK_POINT_OFFSET = 0x7A324
    const val RANK_POINT_LENGTH = 4
    private const val PHOTO_OFFSET = 0x79C08
    private const val PHOTO_BYTES = 0x71C
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun read(source: ByteArray): BdspContestRankPointEditRead {
        val contest = BdspContestReadOnly.read(source)
        return BdspContestRankPointEditRead(contest.version, contest.revision, contest.rankPoints)
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        replacement: Long
    ): BdspContestRankPointCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(replacement in 0L..0xFFFF_FFFFL) {
            "Changed Contest Rank Points must fit the native UInt32 range 0 through 4,294,967,295."
        }
        val before = read(original)
        require(replacement != before.rankPoints) { "Change the saved Contest Rank Points before reviewing." }
        val edited = original.copyOf()
        writeUInt32(edited, RANK_POINT_OFFSET, replacement)
        refreshDigest(edited)
        val after = read(edited)
        require(after.rankPoints == replacement) { "The requested Contest Rank Points did not stage exactly." }
        verify(original, edited, after)
        return BdspContestRankPointCopyPlan(original, edited, before, after)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: BdspContestRankPointEditRead
    ) {
        val before = BdspContestReadOnly.read(original)
        val after = BdspContestReadOnly.read(edited)
        require(
            original.size == edited.size && before.version == after.version &&
                before.revision == after.revision
        ) { "The Contest Rank Point edit changed title identity, save revision, or file size." }
        require(after.rankPoints == expected.rankPoints) {
            "The reviewed Contest Rank Points did not reopen exactly."
        }
        require(before.photos.indices.all { before.photo(it).rawBytes == after.photo(it).rawBytes }) {
            "A native contest photo record changed."
        }
        require((0 until PHOTO_BYTES).all { relative ->
            original[PHOTO_OFFSET + relative] == edited[PHOTO_OFFSET + relative]
        }) { "Native contest photo data changed." }
        require(original.indices.all { index ->
            index in RANK_POINT_OFFSET until RANK_POINT_OFFSET + RANK_POINT_LENGTH ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[index] == edited[index]
        }) { "The Contest Rank Point edit changed an unrelated BDSP save byte." }
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
class BdspContestRankPointCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: BdspContestRankPointEditRead,
    val after: BdspContestRankPointEditRead
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = BdspContestRankPointEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Contest Records." }
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
            BdspContestRankPointEditing.verify(original, edited, after)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspContestRankPointEditing.verify(original, persisted, after)
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
