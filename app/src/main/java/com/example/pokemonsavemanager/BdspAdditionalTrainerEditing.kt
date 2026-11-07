package com.example.pokemonsavemanager

import java.security.MessageDigest

data class BdspAdditionalTrainerEditRead(
    val version: BdspVersion,
    val revision: BdspSaveRevision,
    val records: BdspAdditionalRecordsRead,
    val entry: BdspAdditionalTrainerRecordRead
)

data class BdspAdditionalTrainerReplacement(
    /** Null retains an unreadable native name field exactly. */
    val name: String?,
    val language: Int,
    val gender: Int,
    val bodyType: Int,
    val id32: Long
)

/** Reviewed edits for one native BDSP additional-trainer record. */
object BdspAdditionalTrainerEditing {
    private const val BASE_OFFSET = 0xE9828
    private const val RECORD_COUNT = 12
    private const val RECORD_SIZE = 0x30
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun read(source: ByteArray, index: Int): BdspAdditionalTrainerEditRead {
        require(index in 0 until RECORD_COUNT) { "The additional-trainer slot must be 0 through 11." }
        val records = BdspAdditionalRecordsReadOnly.read(source)
        require(records.hasBlock) {
            records.absenceReason ?: "This save does not contain native additional-trainer records."
        }
        return BdspAdditionalTrainerEditRead(records.version, records.revision, records, records.record(index))
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        index: Int,
        replacement: BdspAdditionalTrainerReplacement
    ): BdspAdditionalTrainerCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(replacement.gender in 0..255) { "Changed gender must fit the native byte range 0 through 255." }
        require(replacement.id32 in 0..0xFFFF_FFFFL) { "Changed ID32 must be 0 through 4,294,967,295." }
        val before = read(original, index)
        val edited = original.copyOf()
        val offset = recordOffset(index)
        val ranges = mutableListOf<IntRange>()
        val fields = mutableListOf<String>()

        if (replacement.name != null && replacement.name != before.entry.name) {
            validateName(replacement.name)
            writeName(edited, offset, replacement.name)
            ranges += offset until offset + 0x1A
            fields += "OT name"
        }
        if (replacement.language != before.entry.languageId) {
            writeInt(edited, offset + 0x1C, replacement.language)
            ranges += offset + 0x1C until offset + 0x20
            fields += "language"
        }
        if (replacement.gender != before.entry.genderByte) {
            edited[offset + 0x20] = replacement.gender.toByte()
            ranges += offset + 0x20..offset + 0x20
            fields += "gender"
        }
        if (replacement.bodyType != before.entry.bodyTypeId) {
            writeInt(edited, offset + 0x24, replacement.bodyType)
            ranges += offset + 0x24 until offset + 0x28
            fields += "body type"
        }
        if (replacement.id32 != before.entry.id32) {
            writeUInt(edited, offset + 0x28, replacement.id32)
            ranges += offset + 0x28 until offset + 0x2C
            fields += "ID32"
        }
        require(ranges.isNotEmpty()) { "Change at least one saved trainer field before reviewing." }
        refreshDigest(edited)
        val after = read(edited, index)
        verify(original, edited, index, after, ranges)
        return BdspAdditionalTrainerCopyPlan(original, edited, before, after, ranges.toList(), fields.toList())
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        index: Int,
        expected: BdspAdditionalTrainerEditRead,
        allowedRanges: List<IntRange>
    ) {
        val before = BdspAdditionalRecordsReadOnly.read(original)
        val after = BdspAdditionalRecordsReadOnly.read(edited)
        require(
            original.size == edited.size && before.version == after.version &&
                before.revision == after.revision && before.hasBlock && after.hasBlock
        ) { "The additional-trainer edit changed title identity, save revision, block presence, or file size." }
        require(after.record(index).rawRecordBytes == expected.entry.rawRecordBytes) {
            "The reviewed additional-trainer record did not reopen exactly."
        }
        require((0 until RECORD_COUNT).all { slot ->
            slot == index || before.record(slot).rawRecordBytes == after.record(slot).rawRecordBytes
        }) { "A different additional-trainer record changed." }
        require(before.rawRankingBytes == after.rawRankingBytes) { "The unclassified ranking region changed." }
        val offset = recordOffset(index)
        val reserved = listOf(
            offset + 0x1A..offset + 0x1B,
            offset + 0x21..offset + 0x23,
            offset + 0x2C..offset + 0x2F
        )
        require(reserved.all { range -> range.all { original[it] == edited[it] } }) {
            "A reserved byte in the selected trainer record changed."
        }
        require(original.indices.all { byteIndex ->
            allowedRanges.any { byteIndex in it } ||
                byteIndex in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[byteIndex] == edited[byteIndex]
        }) { "The additional-trainer edit changed an unrelated BDSP save byte." }
    }

    fun recordOffset(index: Int): Int {
        require(index in 0 until RECORD_COUNT)
        return BASE_OFFSET + index * RECORD_SIZE
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun validateName(value: String) {
        require(value.length <= 12) { "Changed OT name must use at most 12 UTF-16 code units." }
        require('\u0000' !in value) { "Changed OT name cannot contain a NULL code unit." }
        require(value.none { Character.isISOControl(it) }) { "Changed OT name cannot contain control characters." }
        var index = 0
        while (index < value.length) {
            val unit = value[index]
            when {
                Character.isHighSurrogate(unit) -> {
                    require(index + 1 < value.length && Character.isLowSurrogate(value[index + 1])) {
                        "Changed OT name contains malformed UTF-16."
                    }
                    index += 2
                }
                Character.isLowSurrogate(unit) -> throw IllegalArgumentException(
                    "Changed OT name contains malformed UTF-16."
                )
                else -> index++
            }
        }
    }

    private fun writeName(bytes: ByteArray, offset: Int, value: String) {
        val encoded = value.toByteArray(Charsets.UTF_16LE)
        require(encoded.size <= 24) { "Changed OT name is too long." }
        bytes.fill(0, offset, offset + 0x1A)
        encoded.copyInto(bytes, offset)
    }

    private fun writeInt(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }

    private fun writeUInt(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class BdspAdditionalTrainerCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: BdspAdditionalTrainerEditRead,
    val after: BdspAdditionalTrainerEditRead,
    val allowedRanges: List<IntRange>,
    val changedFields: List<String>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = BdspAdditionalTrainerEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Additional Record Trainers." }
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
            BdspAdditionalTrainerEditing.verify(
                original, edited, before.entry.index, after, allowedRanges
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspAdditionalTrainerEditing.verify(
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
