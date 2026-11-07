package com.example.pokemonsavemanager

import java.security.MessageDigest

enum class BdspSealStickerEditField(val displayName: String, val relativeOffset: Int) {
    OBTAINED("Obtained flag", 0x00),
    COUNT("Count", 0x04),
    TOTAL_COUNT("Total count", 0x08)
}

data class BdspSealStickerReplacement(
    val rawObtainedWord: Long,
    val count: Int,
    val totalCount: Int
) {
    fun value(field: BdspSealStickerEditField): Long = when (field) {
        BdspSealStickerEditField.OBTAINED -> rawObtainedWord
        BdspSealStickerEditField.COUNT -> count.toLong()
        BdspSealStickerEditField.TOTAL_COUNT -> totalCount.toLong()
    }
}

data class BdspSealStickerEditRead(
    val version: BdspVersion,
    val revision: BdspSaveRevision,
    val entry: BdspSealStickerEntry
)

/** Reviewed edits for one native 12-byte BDSP seal-sticker record. */
object BdspSealStickerEditing {
    private const val LIST_OFFSET = 0x93E0C
    private const val RECORD_SIZE = 0x0C
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun read(source: ByteArray, index: Int): BdspSealStickerEditRead {
        val seals = BdspSealStickersReadOnly.read(source)
        return BdspSealStickerEditRead(seals.version, seals.revision, seals.entry(index))
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        index: Int,
        replacement: BdspSealStickerReplacement
    ): BdspSealStickerCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(index in 0 until 200) { "Seal-sticker index must be 0 through 199." }
        val before = read(original, index)
        val changedFields = BdspSealStickerEditField.values().filter { field ->
            replacement.value(field) != value(before.entry, field)
        }
        require(changedFields.isNotEmpty()) { "Change at least one seal-sticker value before reviewing." }
        changedFields.forEach { field -> validate(field, replacement.value(field)) }

        val edited = original.copyOf()
        val base = recordOffset(index)
        changedFields.forEach { field -> writeU32(edited, base + field.relativeOffset, replacement.value(field)) }
        refreshDigest(edited)
        val after = read(edited, index)
        verify(original, edited, index, after, changedFields)
        return BdspSealStickerCopyPlan(original, edited, before, after, changedFields)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        index: Int,
        expected: BdspSealStickerEditRead,
        changedFields: List<BdspSealStickerEditField>
    ) {
        require(changedFields.isNotEmpty())
        val beforeList = BdspSealStickersReadOnly.read(original)
        val afterList = BdspSealStickersReadOnly.read(edited)
        require(
            original.size == edited.size && beforeList.version == afterList.version &&
                beforeList.revision == afterList.revision
        ) { "The seal-sticker edit changed title identity, save revision, or file size." }
        val reopened = afterList.entry(index)
        require(
            reopened.rawObtainedWord == expected.entry.rawObtainedWord && reopened.count == expected.entry.count &&
                reopened.totalCount == expected.entry.totalCount && reopened.rawBytes == expected.entry.rawBytes
        ) { "The reviewed seal-sticker values did not reopen exactly." }
        require(beforeList.entries.indices.all { it == index || beforeList.entry(it).rawBytes == afterList.entry(it).rawBytes }) {
            "A different seal-sticker record changed."
        }
        val base = recordOffset(index)
        require(original.indices.all { byteIndex ->
            changedFields.any { field -> byteIndex in base + field.relativeOffset until base + field.relativeOffset + 4 } ||
                byteIndex in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH || original[byteIndex] == edited[byteIndex]
        }) { "The seal-sticker edit changed an unrelated BDSP save byte." }
    }

    fun recordOffset(index: Int): Int {
        require(index in 0 until 200)
        return LIST_OFFSET + index * RECORD_SIZE
    }

    fun value(entry: BdspSealStickerEntry, field: BdspSealStickerEditField): Long = when (field) {
        BdspSealStickerEditField.OBTAINED -> entry.rawObtainedWord
        BdspSealStickerEditField.COUNT -> entry.count.toLong()
        BdspSealStickerEditField.TOTAL_COUNT -> entry.totalCount.toLong()
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun validate(field: BdspSealStickerEditField, value: Long) {
        when (field) {
            BdspSealStickerEditField.OBTAINED -> require(value in 0L..1L) {
                "A changed Obtained flag must be 0 or 1."
            }
            BdspSealStickerEditField.COUNT, BdspSealStickerEditField.TOTAL_COUNT -> require(value in 0L..99L) {
                "Changed ${field.displayName} must be 0 through the native reference maximum of 99."
            }
        }
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class BdspSealStickerCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: BdspSealStickerEditRead,
    val after: BdspSealStickerEditRead,
    val changedFields: List<BdspSealStickerEditField>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = BdspSealStickerEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen the seal-sticker entry." }
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
            BdspSealStickerEditing.verify(original, edited, before.entry.index, after, changedFields)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspSealStickerEditing.verify(original, persisted, before.entry.index, after, changedFields)
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
