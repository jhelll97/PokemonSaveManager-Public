package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class BdspItemEditMode { QUANTITY, REMOVE, ADD }

/** Bounded BDSP item edits. One native item record and the whole-file MD5 are the complete write allowlist. */
object BdspItemEditing {
    private const val INVENTORY_OFFSET = 0x0563C
    private const val RECORD_SIZE = 16
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        val max = BdspSaveRevision.values().maxOf { revision -> revision.fileSize }
        while (true) {
            val remaining = max + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= max) { "The document exceeds the supported BDSP save size." }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        pocket: BdspInventoryPocket,
        itemId: Int?,
        mode: BdspItemEditMode,
        selectedItemId: Int? = null,
        selectedCount: Int? = null
    ): BdspItemCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val beforeInventory = BdspInventoryReadOnly.read(original)
        val targetId = when (mode) {
            BdspItemEditMode.ADD -> selectedItemId
                ?: throw IllegalArgumentException("Choose an item.")
            else -> itemId ?: throw IllegalArgumentException("Reopen the item from PSE.")
        }
        require(targetId in BdspInventoryReadOnly.allowedItemIds(pocket)) {
            "That item is not in this pocket's native catalogue."
        }
        val before = beforeInventory.slot(targetId)
        val maximum = before.referenceMaxCount
            ?: throw IllegalArgumentException("This item's native stack maximum is unknown.")
        val recordOffset = INVENTORY_OFFSET + targetId * RECORD_SIZE
        val edited = original.copyOf()

        when (mode) {
            BdspItemEditMode.QUANTITY -> {
                require(before.pocket == pocket && before.isRecorded && before.count > 0) {
                    "Only a catalogued non-empty item can have its quantity changed."
                }
                val count = selectedCount ?: throw IllegalArgumentException("Enter a quantity.")
                require(count in 1..maximum) { "Quantity must be 1–$maximum for ${before.itemName}." }
                require(count != before.count) { "Choose a different quantity." }
                writeU32(edited, recordOffset, count.toLong())
            }
            BdspItemEditMode.REMOVE -> {
                require(before.pocket == pocket && before.isRecorded && before.count > 0) {
                    "Only a catalogued non-empty item can be removed."
                }
                edited.fill(0, recordOffset, recordOffset + RECORD_SIZE)
            }
            BdspItemEditMode.ADD -> {
                require(before.pocket == pocket && before.isEmpty) {
                    "The selected native item record is no longer empty."
                }
                val count = selectedCount ?: throw IllegalArgumentException("Enter a quantity.")
                require(count in 1..maximum) { "Quantity must be 1–$maximum for ${before.itemName}." }
                val highestSort = beforeInventory.pocket(pocket).recordedSlots.maxOfOrNull { it.sortOrder } ?: 0
                require(highestSort < 0xFFFF) { "This pocket has no free native sort number." }
                writeU32(edited, recordOffset, count.toLong())
                writeU32(edited, recordOffset + 4, 0L)
                writeU32(edited, recordOffset + 8, 0L)
                writeU16(edited, recordOffset + 12, highestSort + 1)
                writeU16(edited, recordOffset + 14, 0)
            }
        }

        refreshDigest(edited)
        verify(original, edited, pocket, targetId, mode, selectedCount)
        val after = BdspInventoryReadOnly.read(edited).slot(targetId)
        return BdspItemCopyPlan(original, edited, pocket, targetId, mode, before, after, recordOffset)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        pocket: BdspInventoryPocket,
        itemId: Int,
        mode: BdspItemEditMode,
        selectedCount: Int?
    ) {
        val beforeInventory = BdspInventoryReadOnly.read(original)
        val afterInventory = BdspInventoryReadOnly.read(edited)
        require(
            original.size == edited.size && beforeInventory.version == afterInventory.version &&
                beforeInventory.revision == afterInventory.revision
        ) { "The edited save changed title identity, revision, or size." }
        val before = beforeInventory.slot(itemId)
        val after = afterInventory.slot(itemId)
        when (mode) {
            BdspItemEditMode.QUANTITY -> require(
                before.pocket == pocket && after.pocket == pocket && after.count == selectedCount &&
                    after.rawSeenFlag == before.rawSeenFlag && after.rawFavoriteFlag == before.rawFavoriteFlag &&
                    after.sortOrder == before.sortOrder && after.alignment == before.alignment
            ) { "The staged quantity change did not reopen exactly." }
            BdspItemEditMode.REMOVE -> require(before.isRecorded && after.isEmpty) {
                "The staged removal did not reopen as an empty native record."
            }
            BdspItemEditMode.ADD -> require(
                before.isEmpty && after.pocket == pocket && after.count == selectedCount &&
                    after.rawSeenFlag == 0L && after.rawFavoriteFlag == 0L &&
                    after.sortOrder > 0 && after.alignment == 0
            ) { "The staged added item did not reopen exactly." }
        }
        val recordOffset = INVENTORY_OFFSET + itemId * RECORD_SIZE
        val recordWidth = if (mode == BdspItemEditMode.QUANTITY) 4 else RECORD_SIZE
        require(original.indices.all { index ->
            index in recordOffset until recordOffset + recordWidth ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[index] == edited[index]
        }) { "An unrelated save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }
}

/** Single-use export transaction to a newly created empty document. The selected source is never replaced. */
class BdspItemCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val pocket: BdspInventoryPocket,
    val itemId: Int,
    val mode: BdspItemEditMode,
    val before: BdspInventorySlotRead,
    val after: BdspInventorySlotRead,
    val recordOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = BdspItemEditing.sha256(this.original)
    val editedSha256: String = BdspItemEditing.sha256(this.edited)
    private var consumed = false

    val beforeLabel: String get() = if (before.isEmpty) "Not recorded" else "${before.itemName} ×${before.count}"
    val afterLabel: String get() = if (after.isEmpty) "Not recorded" else "${after.itemName} ×${after.count}"

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen the item editor." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) { "The recovery backup did not verify. Nothing was written." }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            BdspItemEditing.verify(original, edited, pocket, itemId, mode, if (after.isEmpty) null else after.count)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspItemEditing.verify(original, persisted, pocket, itemId, mode, if (after.isEmpty) null else after.count)
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
