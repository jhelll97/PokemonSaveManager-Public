package com.example.pokemonsavemanager

import java.security.MessageDigest

data class BdspUndergroundNpcEditRead(
    val version: BdspVersion,
    val revision: BdspSaveRevision,
    val records: BdspUndergroundNpcRead,
    val entry: BdspUndergroundNpcEntry
)

/** Reviewed one-byte edits for the native BDSP Underground talked-NPC array. */
object BdspUndergroundNpcEditing {
    private const val NPC_OFFSET = 0x9CFD8
    private const val NPC_COUNT = 100
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun read(source: ByteArray, index: Int): BdspUndergroundNpcEditRead {
        val records = BdspUndergroundNpcReadOnly.read(source)
        return BdspUndergroundNpcEditRead(records.version, records.revision, records, records.entry(index))
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        index: Int,
        replacement: Int
    ): BdspUndergroundNpcCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(index in 0 until NPC_COUNT) { "NPC interaction index must be 0 through 99." }
        require(replacement in 0..255) { "The saved NPC interaction byte must be 0 through 255." }
        val before = read(original, index)
        require(replacement != before.entry.value) { "Change the saved NPC interaction byte before reviewing." }
        val edited = original.copyOf()
        edited[absoluteOffset(index)] = replacement.toByte()
        refreshDigest(edited)
        val after = read(edited, index)
        verify(original, edited, index, after)
        return BdspUndergroundNpcCopyPlan(original, edited, before, after)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        index: Int,
        expected: BdspUndergroundNpcEditRead
    ) {
        val before = BdspUndergroundNpcReadOnly.read(original)
        val after = BdspUndergroundNpcReadOnly.read(edited)
        require(
            original.size == edited.size && before.version == after.version &&
                before.revision == after.revision
        ) { "The Underground NPC edit changed title identity, save revision, or file size." }
        require(after.entry(index).value == expected.entry.value) {
            "The reviewed NPC interaction byte did not reopen exactly."
        }
        require(before.entries.indices.all { other ->
            other == index || before.entry(other).value == after.entry(other).value
        }) { "A different Underground NPC entry changed." }
        require(original.indices.all { byteIndex ->
            byteIndex == absoluteOffset(index) ||
                byteIndex in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[byteIndex] == edited[byteIndex]
        }) { "The Underground NPC edit changed an unrelated BDSP save byte." }
    }

    fun absoluteOffset(index: Int): Int {
        require(index in 0 until NPC_COUNT)
        return NPC_OFFSET + index
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class BdspUndergroundNpcCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: BdspUndergroundNpcEditRead,
    val after: BdspUndergroundNpcEditRead
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = BdspUndergroundNpcEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Underground NPC Records." }
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
            BdspUndergroundNpcEditing.verify(original, edited, before.entry.index, after)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspUndergroundNpcEditing.verify(original, persisted, before.entry.index, after)
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
