package com.example.pokemonsavemanager

import java.security.MessageDigest

enum class BdspSecretBaseEditField(val displayName: String, val width: Int) {
    TALK_PLAYER_DATA_ID("Talk-player data ID", 4),
    TALK_PLAYER_COUNT("Talk-player count", 4),
    ZONE_ID("Base zone ID", 2),
    BASE_X("Base position X", 2),
    BASE_Y("Base position Y", 2),
    BASE_DIRECTION("Base direction", 1),
    EXPANSION_STATUS("Expansion status", 1),
    GOOD_COUNT("Good count", 4),
    STATUE_ID("Statue ID", 4),
    PEDESTAL_ID("Pedestal ID", 4),
    STATUE_X("Statue X", 4),
    STATUE_Y("Statue Y", 4),
    STATUE_DIRECTION("Statue direction", 4)
}

class BdspSecretBaseReplacement(values: Map<BdspSecretBaseEditField, Long>) {
    private val values = values.toMap()
    init {
        require(BdspSecretBaseEditField.values().all { it in this.values }) {
            "Every Secret Base field must be supplied."
        }
    }
    fun value(field: BdspSecretBaseEditField): Long = values.getValue(field)
}

data class BdspSecretBaseEditRead(
    val version: BdspVersion,
    val revision: BdspSaveRevision,
    val base: BdspSecretBaseRead,
    val statue: BdspStatueRecord
)

/** Reviewed header and one-record edits for the documented native BDSP Secret Base structure. */
object BdspSecretBaseEditing {
    private const val BASE_OFFSET = 0x9BDC8
    private const val HEADER_SIZE = 0x14
    private const val RECORD_SIZE = 0x14
    private const val RECORD_COUNT = 30
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun read(source: ByteArray, statueIndex: Int): BdspSecretBaseEditRead {
        val base = BdspSecretBaseReadOnly.read(source)
        return BdspSecretBaseEditRead(base.version, base.revision, base, base.statue(statueIndex))
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        statueIndex: Int,
        replacement: BdspSecretBaseReplacement
    ): BdspSecretBaseCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(statueIndex in 0 until RECORD_COUNT) { "Statue index must be 0 through 29." }
        val before = read(original, statueIndex)
        val changedFields = BdspSecretBaseEditField.values().filter { field ->
            replacement.value(field) != value(before, field)
        }
        require(changedFields.isNotEmpty()) { "Change at least one Secret Base value before reviewing." }
        changedFields.forEach { field -> validate(field, replacement.value(field)) }

        val edited = original.copyOf()
        changedFields.forEach { field ->
            writeLittleEndian(edited, absoluteOffset(statueIndex, field), field.width, replacement.value(field))
        }
        refreshDigest(edited)
        val after = read(edited, statueIndex)
        verify(original, edited, statueIndex, after, changedFields)
        return BdspSecretBaseCopyPlan(original, edited, before, after, changedFields)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        statueIndex: Int,
        expected: BdspSecretBaseEditRead,
        changedFields: List<BdspSecretBaseEditField>
    ) {
        require(changedFields.isNotEmpty())
        val before = BdspSecretBaseReadOnly.read(original)
        val after = BdspSecretBaseReadOnly.read(edited)
        require(
            original.size == edited.size && before.version == after.version &&
                before.revision == after.revision
        ) { "The Secret Base edit changed title identity, save revision, or file size." }
        val reopened = BdspSecretBaseEditRead(after.version, after.revision, after, after.statue(statueIndex))
        require(BdspSecretBaseEditField.values().all { field ->
            value(reopened, field) == value(expected, field)
        }) { "The reviewed Secret Base values did not reopen exactly." }
        require(before.statues.indices.all { index ->
            index == statueIndex || before.statue(index).rawBytes == after.statue(index).rawBytes
        }) { "A different Secret Base statue record changed." }
        require(before.rawBaseBytes.indices.all { relative ->
            val fileOffset = BASE_OFFSET + relative
            changedFields.any { field ->
                fileOffset in absoluteOffset(statueIndex, field) until
                    absoluteOffset(statueIndex, field) + field.width
            } || before.rawBaseBytes[relative] == after.rawBaseBytes[relative]
        }) { "A retained Secret Base field, enable word, or record byte changed." }
        require(original.indices.all { index ->
            changedFields.any { field ->
                index in absoluteOffset(statueIndex, field) until
                    absoluteOffset(statueIndex, field) + field.width
            } || index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[index] == edited[index]
        }) { "The Secret Base edit changed an unrelated BDSP save byte." }
    }

    fun absoluteOffset(statueIndex: Int, field: BdspSecretBaseEditField): Int {
        require(statueIndex in 0 until RECORD_COUNT)
        val record = BASE_OFFSET + HEADER_SIZE + statueIndex * RECORD_SIZE
        return when (field) {
            BdspSecretBaseEditField.TALK_PLAYER_DATA_ID -> BASE_OFFSET
            BdspSecretBaseEditField.TALK_PLAYER_COUNT -> BASE_OFFSET + 4
            BdspSecretBaseEditField.ZONE_ID -> BASE_OFFSET + 8
            BdspSecretBaseEditField.BASE_X -> BASE_OFFSET + 10
            BdspSecretBaseEditField.BASE_Y -> BASE_OFFSET + 12
            BdspSecretBaseEditField.BASE_DIRECTION -> BASE_OFFSET + 14
            BdspSecretBaseEditField.EXPANSION_STATUS -> BASE_OFFSET + 15
            BdspSecretBaseEditField.GOOD_COUNT -> BASE_OFFSET + 16
            BdspSecretBaseEditField.STATUE_ID -> record
            BdspSecretBaseEditField.PEDESTAL_ID -> record + 4
            BdspSecretBaseEditField.STATUE_X -> record + 8
            BdspSecretBaseEditField.STATUE_Y -> record + 12
            BdspSecretBaseEditField.STATUE_DIRECTION -> record + 16
        }
    }

    fun value(read: BdspSecretBaseEditRead, field: BdspSecretBaseEditField): Long = when (field) {
        BdspSecretBaseEditField.TALK_PLAYER_DATA_ID -> read.base.talkPlayerDataId
        BdspSecretBaseEditField.TALK_PLAYER_COUNT -> read.base.talkPlayerCount
        BdspSecretBaseEditField.ZONE_ID -> read.base.zoneId.toLong()
        BdspSecretBaseEditField.BASE_X -> read.base.x.toLong()
        BdspSecretBaseEditField.BASE_Y -> read.base.y.toLong()
        BdspSecretBaseEditField.BASE_DIRECTION -> read.base.direction.toLong()
        BdspSecretBaseEditField.EXPANSION_STATUS -> read.base.expansionStatus.toLong()
        BdspSecretBaseEditField.GOOD_COUNT -> read.base.goodCount.toLong()
        BdspSecretBaseEditField.STATUE_ID -> read.statue.statueId.toLong()
        BdspSecretBaseEditField.PEDESTAL_ID -> read.statue.pedestalId.toLong()
        BdspSecretBaseEditField.STATUE_X -> read.statue.x.toLong()
        BdspSecretBaseEditField.STATUE_Y -> read.statue.y.toLong()
        BdspSecretBaseEditField.STATUE_DIRECTION -> read.statue.direction.toLong()
    }

    fun rangeLabel(field: BdspSecretBaseEditField): String = when (field) {
        BdspSecretBaseEditField.TALK_PLAYER_DATA_ID,
        BdspSecretBaseEditField.TALK_PLAYER_COUNT -> "0–4294967295"
        BdspSecretBaseEditField.ZONE_ID,
        BdspSecretBaseEditField.BASE_X,
        BdspSecretBaseEditField.BASE_Y -> "-32768–32767"
        BdspSecretBaseEditField.BASE_DIRECTION,
        BdspSecretBaseEditField.EXPANSION_STATUS -> "0–255"
        else -> "-2147483648–2147483647"
    }

    fun isSigned(field: BdspSecretBaseEditField): Boolean = when (field) {
        BdspSecretBaseEditField.TALK_PLAYER_DATA_ID,
        BdspSecretBaseEditField.TALK_PLAYER_COUNT,
        BdspSecretBaseEditField.BASE_DIRECTION,
        BdspSecretBaseEditField.EXPANSION_STATUS -> false
        else -> true
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun validate(field: BdspSecretBaseEditField, value: Long) {
        val valid = when (field) {
            BdspSecretBaseEditField.TALK_PLAYER_DATA_ID,
            BdspSecretBaseEditField.TALK_PLAYER_COUNT -> value in 0L..0xFFFF_FFFFL
            BdspSecretBaseEditField.ZONE_ID,
            BdspSecretBaseEditField.BASE_X,
            BdspSecretBaseEditField.BASE_Y -> value in Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong()
            BdspSecretBaseEditField.BASE_DIRECTION,
            BdspSecretBaseEditField.EXPANSION_STATUS -> value in 0L..255L
            else -> value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
        }
        require(valid) { "Changed ${field.displayName} must be ${rangeLabel(field)}." }
    }

    private fun writeLittleEndian(bytes: ByteArray, offset: Int, width: Int, value: Long) {
        repeat(width) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class BdspSecretBaseCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: BdspSecretBaseEditRead,
    val after: BdspSecretBaseEditRead,
    val changedFields: List<BdspSecretBaseEditField>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = BdspSecretBaseEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen the Secret Base." }
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
            BdspSecretBaseEditing.verify(original, edited, before.statue.index, after, changedFields)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspSecretBaseEditing.verify(original, persisted, before.statue.index, after, changedFields)
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
