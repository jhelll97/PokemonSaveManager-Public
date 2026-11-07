package com.example.pokemonsavemanager

import java.security.MessageDigest

enum class BdspUndergroundActivityEditField(val displayName: String, val offset: Int, val width: Int) {
    FOSSIL_DIGGING_PLAYS("Fossil digging plays", 0x00, 2),
    STATUE_TV_BROADCASTS("Statue TV broadcasts", 0x02, 2),
    SECRET_BASE_TV_LIKES("Likes on Secret Base TV broadcasts", 0x04, 4),
    OTHER_SECRET_BASE_LIKES("Other Secret Base likes", 0x08, 4),
    SUCCESSFUL_LIGHT_STONE_SEARCHES("Successful Light Stone searches", 0x0C, 4)
}

class BdspUndergroundActivityReplacement(values: Map<BdspUndergroundActivityEditField, Long>) {
    private val values = values.toMap()
    init {
        require(BdspUndergroundActivityEditField.values().all { it in this.values }) {
            "Every Underground activity counter must be supplied."
        }
    }
    fun value(field: BdspUndergroundActivityEditField): Long = values.getValue(field)
}

data class BdspUndergroundActivityEditRead(
    val version: BdspVersion,
    val revision: BdspSaveRevision,
    val activity: BdspUndergroundActivityRead
)

/** Reviewed edits for the five documented counters in the native BDSP Underground activity record. */
object BdspUndergroundActivityEditing {
    private const val BASE_OFFSET = 0xE8178
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun read(source: ByteArray): BdspUndergroundActivityEditRead {
        val activity = BdspUndergroundActivityReadOnly.read(source)
        return BdspUndergroundActivityEditRead(activity.version, activity.revision, activity)
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        replacement: BdspUndergroundActivityReplacement
    ): BdspUndergroundActivityCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = read(original)
        val changedFields = BdspUndergroundActivityEditField.values().filter { field ->
            replacement.value(field) != value(before, field)
        }
        require(changedFields.isNotEmpty()) { "Change at least one Underground activity counter before reviewing." }
        changedFields.forEach { field -> validate(field, replacement.value(field)) }
        val edited = original.copyOf()
        changedFields.forEach { field ->
            writeSigned(edited, absoluteOffset(field), field.width, replacement.value(field))
        }
        refreshDigest(edited)
        val after = read(edited)
        verify(original, edited, after, changedFields)
        return BdspUndergroundActivityCopyPlan(original, edited, before, after, changedFields)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: BdspUndergroundActivityEditRead,
        changedFields: List<BdspUndergroundActivityEditField>
    ) {
        require(changedFields.isNotEmpty())
        val before = read(original)
        val after = read(edited)
        require(
            original.size == edited.size && before.version == after.version &&
                before.revision == after.revision
        ) { "The Underground activity edit changed title identity, save revision, or file size." }
        require(BdspUndergroundActivityEditField.values().all { field ->
            value(after, field) == value(expected, field)
        }) { "The reviewed Underground activity counters did not reopen exactly." }
        require(
            before.activity.reserved1 == after.activity.reserved1 &&
                before.activity.reserved2 == after.activity.reserved2
        ) { "A reserved Underground activity word changed." }
        require(original.indices.all { index ->
            changedFields.any { field ->
                index in absoluteOffset(field) until absoluteOffset(field) + field.width
            } || index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH || original[index] == edited[index]
        }) { "The Underground activity edit changed an unrelated BDSP save byte." }
    }

    fun value(read: BdspUndergroundActivityEditRead, field: BdspUndergroundActivityEditField): Long = when (field) {
        BdspUndergroundActivityEditField.FOSSIL_DIGGING_PLAYS -> read.activity.fossilDiggingPlayCount.toLong()
        BdspUndergroundActivityEditField.STATUE_TV_BROADCASTS -> read.activity.statueTvBroadcastCount.toLong()
        BdspUndergroundActivityEditField.SECRET_BASE_TV_LIKES -> read.activity.secretBaseTvLikeCount.toLong()
        BdspUndergroundActivityEditField.OTHER_SECRET_BASE_LIKES -> read.activity.otherSecretBaseLikeCount.toLong()
        BdspUndergroundActivityEditField.SUCCESSFUL_LIGHT_STONE_SEARCHES ->
            read.activity.successfulLightStoneSearchCount.toLong()
    }

    fun range(field: BdspUndergroundActivityEditField): LongRange =
        if (field.width == 2) Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong()
        else Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()

    fun absoluteOffset(field: BdspUndergroundActivityEditField): Int = BASE_OFFSET + field.offset

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun validate(field: BdspUndergroundActivityEditField, value: Long) {
        require(value in range(field)) {
            val allowed = range(field)
            "Changed ${field.displayName} must be ${allowed.first} through ${allowed.last}."
        }
    }

    private fun writeSigned(bytes: ByteArray, offset: Int, width: Int, value: Long) {
        repeat(width) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class BdspUndergroundActivityCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: BdspUndergroundActivityEditRead,
    val after: BdspUndergroundActivityEditRead,
    val changedFields: List<BdspUndergroundActivityEditField>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = BdspUndergroundActivityEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Underground Activity." }
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
            BdspUndergroundActivityEditing.verify(original, edited, after, changedFields)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspUndergroundActivityEditing.verify(original, persisted, after, changedFields)
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
