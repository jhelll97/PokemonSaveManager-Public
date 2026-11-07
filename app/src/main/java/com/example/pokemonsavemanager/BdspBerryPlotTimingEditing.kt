package com.example.pokemonsavemanager

import java.security.MessageDigest

data class BdspBerryPlotTimingRead(
    val version: BdspVersion,
    val revision: BdspSaveRevision,
    val lastUpdateMinutes: Long
)

/** Reviewed edits for the documented signed timing field in the native BDSP berry-plot block. */
object BdspBerryPlotTimingEditing {
    const val UPDATE_MINUTES_OFFSET = 0x955A8
    const val UPDATE_MINUTES_LENGTH = 8
    private const val BERRY_OFFSET = 0x94DA8
    private const val PLOT_BYTES = 0x800
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun read(source: ByteArray): BdspBerryPlotTimingRead {
        val berries = BdspBerryPlotsReadOnly.read(source)
        return BdspBerryPlotTimingRead(berries.version, berries.revision, berries.lastUpdateMinutes)
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        replacementMinutes: Long
    ): BdspBerryPlotTimingCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = read(original)
        require(replacementMinutes != before.lastUpdateMinutes) {
            "Change the saved update minutes before reviewing."
        }
        val edited = original.copyOf()
        writeLong(edited, UPDATE_MINUTES_OFFSET, replacementMinutes)
        refreshDigest(edited)
        val after = read(edited)
        verify(original, edited, after)
        return BdspBerryPlotTimingCopyPlan(original, edited, before, after)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: BdspBerryPlotTimingRead
    ) {
        val before = BdspBerryPlotsReadOnly.read(original)
        val after = BdspBerryPlotsReadOnly.read(edited)
        require(
            original.size == edited.size && before.version == after.version &&
                before.revision == after.revision
        ) { "The berry timing edit changed title identity, save revision, or file size." }
        require(after.lastUpdateMinutes == expected.lastUpdateMinutes) {
            "The reviewed update-minutes value did not reopen exactly."
        }
        require((0 until PLOT_BYTES).all { relative ->
            original[BERRY_OFFSET + relative] == edited[BERRY_OFFSET + relative]
        }) { "A native berry-plot record changed." }
        require(original.indices.all { index ->
            index in UPDATE_MINUTES_OFFSET until UPDATE_MINUTES_OFFSET + UPDATE_MINUTES_LENGTH ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[index] == edited[index]
        }) { "The berry timing edit changed an unrelated BDSP save byte." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun writeLong(bytes: ByteArray, offset: Int, value: Long) {
        repeat(8) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class BdspBerryPlotTimingCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: BdspBerryPlotTimingRead,
    val after: BdspBerryPlotTimingRead
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = BdspBerryPlotTimingEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Berry Plot Records." }
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
            BdspBerryPlotTimingEditing.verify(original, edited, after)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspBerryPlotTimingEditing.verify(original, persisted, after)
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
