package com.example.pokemonsavemanager

import java.security.MessageDigest

enum class BdspUndergroundReturnEditField(
    val displayName: String,
    val relativeOffset: Int,
    val rawFloatWord: Boolean = false
) {
    RETURN_ZONE_ID("Return zone ID", 0x00),
    RETURN_GRID_X("Return grid X", 0x04),
    RETURN_GRID_Y("Return grid Y", 0x08),
    RETURN_GRID_Z("Return grid Z", 0x0C),
    ZENMETSU_ZONE_ID("Zenmetsu zone ID", 0x10),
    ZENMETSU_POSITION_X("Zenmetsu position X bits", 0x14, true),
    ZENMETSU_POSITION_Y("Zenmetsu position Y bits", 0x18, true),
    ZENMETSU_POSITION_Z("Zenmetsu position Z bits", 0x1C, true),
    ZENMETSU_DIRECTION("Zenmetsu direction", 0x20),
    UNDERGROUND_RETURN_ZONE_ID("Underground return zone ID", 0x1528)
}

class BdspUndergroundReturnReplacement(values: Map<BdspUndergroundReturnEditField, Long>) {
    private val values = values.toMap()
    init {
        require(BdspUndergroundReturnEditField.values().all { it in this.values }) {
            "Every Underground return field must be supplied."
        }
    }
    fun value(field: BdspUndergroundReturnEditField): Long = values.getValue(field)
}

data class BdspUndergroundReturnEditRead(
    val version: BdspVersion,
    val revision: BdspSaveRevision,
    val saved: BdspUndergroundReturnRead
)

/** Reviewed edits for the documented scalar fields in the native BDSP Underground return structure. */
object BdspUndergroundReturnEditing {
    private const val BASE_OFFSET = 0x9A89C
    private const val FIELD_LENGTH = 4
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun read(source: ByteArray): BdspUndergroundReturnEditRead {
        val saved = BdspUndergroundReturnReadOnly.read(source)
        return BdspUndergroundReturnEditRead(saved.version, saved.revision, saved)
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        replacement: BdspUndergroundReturnReplacement
    ): BdspUndergroundReturnCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = read(original)
        val changedFields = BdspUndergroundReturnEditField.values().filter { field ->
            replacement.value(field) != value(before, field)
        }
        require(changedFields.isNotEmpty()) { "Change at least one Underground return value before reviewing." }
        changedFields.forEach { field -> validate(field, replacement.value(field)) }

        val edited = original.copyOf()
        changedFields.forEach { field ->
            writeWord(edited, absoluteOffset(field), replacement.value(field))
        }
        refreshDigest(edited)
        val after = read(edited)
        verify(original, edited, after, changedFields)
        return BdspUndergroundReturnCopyPlan(original, edited, before, after, changedFields)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: BdspUndergroundReturnEditRead,
        changedFields: List<BdspUndergroundReturnEditField>
    ) {
        require(changedFields.isNotEmpty())
        val before = read(original)
        val after = read(edited)
        require(
            original.size == edited.size && before.version == after.version &&
                before.revision == after.revision
        ) { "The Underground return edit changed title identity, save revision, or file size." }
        require(BdspUndergroundReturnEditField.values().all { field ->
            value(after, field) == value(expected, field)
        }) { "The reviewed Underground return values did not reopen exactly." }
        require(original.indices.all { index ->
            changedFields.any { field ->
                index in absoluteOffset(field) until absoluteOffset(field) + FIELD_LENGTH
            } || index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH || original[index] == edited[index]
        }) { "The Underground return edit changed an unrelated BDSP save byte." }
    }

    fun value(read: BdspUndergroundReturnEditRead, field: BdspUndergroundReturnEditField): Long = when (field) {
        BdspUndergroundReturnEditField.RETURN_ZONE_ID -> read.saved.returnZoneId.toLong()
        BdspUndergroundReturnEditField.RETURN_GRID_X -> read.saved.gridX.toLong()
        BdspUndergroundReturnEditField.RETURN_GRID_Y -> read.saved.gridY.toLong()
        BdspUndergroundReturnEditField.RETURN_GRID_Z -> read.saved.gridZ.toLong()
        BdspUndergroundReturnEditField.ZENMETSU_ZONE_ID -> read.saved.zenmetsuZoneId.toLong()
        BdspUndergroundReturnEditField.ZENMETSU_POSITION_X -> read.saved.rawZenmetsuXBits
        BdspUndergroundReturnEditField.ZENMETSU_POSITION_Y -> read.saved.rawZenmetsuYBits
        BdspUndergroundReturnEditField.ZENMETSU_POSITION_Z -> read.saved.rawZenmetsuZBits
        BdspUndergroundReturnEditField.ZENMETSU_DIRECTION -> read.saved.zenmetsuDirection.toLong()
        BdspUndergroundReturnEditField.UNDERGROUND_RETURN_ZONE_ID -> read.saved.returnUgZoneId.toLong()
    }

    fun displayValue(read: BdspUndergroundReturnEditRead, field: BdspUndergroundReturnEditField): String {
        val saved = value(read, field)
        return if (field.rawFloatWord) {
            "0x${saved.toString(16).uppercase().padStart(8, '0')} • ${Float.fromBits(saved.toInt())}"
        } else saved.toString()
    }

    fun absoluteOffset(field: BdspUndergroundReturnEditField): Int = BASE_OFFSET + field.relativeOffset

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun validate(field: BdspUndergroundReturnEditField, value: Long) {
        val valid = if (field.rawFloatWord) value in 0L..0xFFFF_FFFFL
        else value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
        require(valid) {
            if (field.rawFloatWord) "Changed ${field.displayName} must be an 8-digit 32-bit hexadecimal word."
            else "Changed ${field.displayName} must be -2147483648 through 2147483647."
        }
    }

    private fun writeWord(bytes: ByteArray, offset: Int, value: Long) {
        repeat(FIELD_LENGTH) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class BdspUndergroundReturnCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: BdspUndergroundReturnEditRead,
    val after: BdspUndergroundReturnEditRead,
    val changedFields: List<BdspUndergroundReturnEditField>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = BdspUndergroundReturnEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Underground Return Data." }
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
            BdspUndergroundReturnEditing.verify(original, edited, after, changedFields)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspUndergroundReturnEditing.verify(original, persisted, after, changedFields)
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
