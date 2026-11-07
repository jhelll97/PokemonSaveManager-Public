package com.example.pokemonsavemanager

import java.security.MessageDigest

enum class BdspUndergroundItemEditField(val displayName: String, val relativeOffset: Int) {
    QUANTITY("Quantity", 0x00),
    HIDE_NEW("New state", 0x04),
    FAVORITE("Favorite", 0x08)
}

data class BdspUndergroundItemReplacement(
    val quantity: Int,
    val rawHideNewFlag: Long,
    val rawFavoriteFlag: Long
) {
    fun value(field: BdspUndergroundItemEditField): Long = when (field) {
        BdspUndergroundItemEditField.QUANTITY -> quantity.toLong()
        BdspUndergroundItemEditField.HIDE_NEW -> rawHideNewFlag
        BdspUndergroundItemEditField.FAVORITE -> rawFavoriteFlag
    }
}

data class BdspUndergroundItemEditRead(
    val version: BdspVersion,
    val revision: BdspSaveRevision,
    val entry: BdspUndergroundSlotRead
)

/** Reviewed edits for one native 12-byte BDSP Grand Underground inventory record. */
object BdspUndergroundItemEditing {
    private const val LIST_OFFSET = 0x111BC
    private const val RECORD_SIZE = 12
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun read(source: ByteArray, undergroundId: Int): BdspUndergroundItemEditRead {
        val underground = BdspUndergroundReadOnly.read(source)
        return BdspUndergroundItemEditRead(
            underground.version,
            underground.revision,
            underground.slot(undergroundId)
        )
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        undergroundId: Int,
        replacement: BdspUndergroundItemReplacement
    ): BdspUndergroundItemCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(undergroundId in 0 until 999) { "Underground ID must be 0 through 998." }
        val before = read(original, undergroundId)
        val maximum = before.entry.referenceMaxCount
        require(before.entry.catalogue != null && maximum != null) {
            "This unlisted Underground record has no verified editing limit."
        }
        val changedFields = BdspUndergroundItemEditField.values().filter { field ->
            replacement.value(field) != value(before.entry, field)
        }
        require(changedFields.isNotEmpty()) { "Change at least one Underground item value before reviewing." }
        changedFields.forEach { field -> validate(field, replacement.value(field), maximum) }

        val edited = original.copyOf()
        val base = recordOffset(undergroundId)
        changedFields.forEach { field ->
            writeU32(edited, base + field.relativeOffset, replacement.value(field))
        }
        refreshDigest(edited)
        val after = read(edited, undergroundId)
        verify(original, edited, undergroundId, after, changedFields)
        return BdspUndergroundItemCopyPlan(original, edited, before, after, changedFields)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        undergroundId: Int,
        expected: BdspUndergroundItemEditRead,
        changedFields: List<BdspUndergroundItemEditField>
    ) {
        require(changedFields.isNotEmpty())
        val beforeList = BdspUndergroundReadOnly.read(original)
        val afterList = BdspUndergroundReadOnly.read(edited)
        require(
            original.size == edited.size && beforeList.version == afterList.version &&
                beforeList.revision == afterList.revision
        ) { "The Grand Underground edit changed title identity, save revision, or file size." }
        val reopened = afterList.slot(undergroundId)
        require(
            reopened.rawCount == expected.entry.rawCount &&
                reopened.rawHideNewFlag == expected.entry.rawHideNewFlag &&
                reopened.rawFavoriteFlag == expected.entry.rawFavoriteFlag
        ) { "The reviewed Underground item values did not reopen exactly." }
        require(beforeList.slots.indices.all { index ->
            if (index == undergroundId) true else {
                val before = beforeList.slot(index)
                val after = afterList.slot(index)
                before.rawCount == after.rawCount &&
                    before.rawHideNewFlag == after.rawHideNewFlag &&
                    before.rawFavoriteFlag == after.rawFavoriteFlag
            }
        }) { "A different Grand Underground inventory record changed." }
        val base = recordOffset(undergroundId)
        require(original.indices.all { byteIndex ->
            changedFields.any { field ->
                byteIndex in base + field.relativeOffset until base + field.relativeOffset + 4
            } || byteIndex in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[byteIndex] == edited[byteIndex]
        }) { "The Grand Underground edit changed an unrelated BDSP save byte." }
    }

    fun recordOffset(undergroundId: Int): Int {
        require(undergroundId in 0 until 999)
        return LIST_OFFSET + undergroundId * RECORD_SIZE
    }

    fun value(entry: BdspUndergroundSlotRead, field: BdspUndergroundItemEditField): Long = when (field) {
        BdspUndergroundItemEditField.QUANTITY -> entry.count.toLong()
        BdspUndergroundItemEditField.HIDE_NEW -> entry.rawHideNewFlag
        BdspUndergroundItemEditField.FAVORITE -> entry.rawFavoriteFlag
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun validate(field: BdspUndergroundItemEditField, value: Long, maximum: Int) {
        when (field) {
            BdspUndergroundItemEditField.QUANTITY -> require(value in 0L..maximum.toLong()) {
                "A changed Quantity must be 0 through the reference maximum of $maximum."
            }
            BdspUndergroundItemEditField.HIDE_NEW,
            BdspUndergroundItemEditField.FAVORITE -> require(value in 0L..1L) {
                "A changed ${field.displayName} value must be 0 or 1."
            }
        }
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        val raw = value and 0xFFFF_FFFFL
        repeat(4) { bytes[offset + it] = (raw ushr (8 * it)).toByte() }
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class BdspUndergroundItemCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: BdspUndergroundItemEditRead,
    val after: BdspUndergroundItemEditRead,
    val changedFields: List<BdspUndergroundItemEditField>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = BdspUndergroundItemEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen the Underground item." }
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
            BdspUndergroundItemEditing.verify(
                original, edited, before.entry.undergroundId, after, changedFields
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspUndergroundItemEditing.verify(
                original, persisted, before.entry.undergroundId, after, changedFields
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
