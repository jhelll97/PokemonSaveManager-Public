package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class ScarletVioletItemEditMode { QUANTITY, REMOVE, ADD }

/** Bounded SV item edits. One decoded item record and the native SHA-256 footer are allowed. */
object ScarletVioletItemEditing {
    private const val MAX_SAVE_SIZE = 0x31CF81
    private const val RECORD_SIZE = 16

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val remaining = MAX_SAVE_SIZE + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= MAX_SAVE_SIZE) {
                "The document exceeds the supported Scarlet/Violet save size."
            }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        pocket: ScarletVioletInventoryPocket,
        itemId: Int?,
        mode: ScarletVioletItemEditMode,
        selectedItemId: Int? = null,
        selectedCount: Int? = null
    ): ScarletVioletItemCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val beforeInventory = ScarletVioletInventoryReadOnly.read(original)
        val targetId = when (mode) {
            ScarletVioletItemEditMode.ADD -> selectedItemId
                ?: throw IllegalArgumentException("Choose an item.")
            else -> itemId ?: throw IllegalArgumentException("Reopen the item from PSE.")
        }
        require(targetId in ScarletVioletInventoryReadOnly.allowedItemIds(pocket)) {
            "That item is not in this pocket's native catalogue."
        }
        val before = beforeInventory.slot(targetId)
        val maximum = before.referenceMaxCount
            ?: throw IllegalArgumentException("This item's native stack maximum is unknown.")
        val record = when (mode) {
            ScarletVioletItemEditMode.QUANTITY -> {
                require(before.pocket == pocket && before.isEditable) {
                    "Only a catalogued item with normal native metadata can have its quantity changed."
                }
                val count = selectedCount ?: throw IllegalArgumentException("Enter a quantity.")
                require(count in 1..maximum) { "Quantity must be 1–$maximum for ${before.itemName}." }
                require(count.toLong() != before.rawCount) { "Choose a different quantity." }
                encode(before.rawPouch, count.toLong(), before.rawFlags, before.padding)
            }
            ScarletVioletItemEditMode.REMOVE -> {
                require(before.pocket == pocket && before.isEditable) {
                    "Only a catalogued item with normal native metadata can be removed."
                }
                ScarletVioletSaveValidation.readInventoryBlock(original).data.copyOfRange(0, RECORD_SIZE)
            }
            ScarletVioletItemEditMode.ADD -> {
                require(before.pocket == pocket && !before.isRecorded) {
                    "The selected native item record is no longer empty."
                }
                val count = selectedCount ?: throw IllegalArgumentException("Enter a quantity.")
                require(count in 1..maximum) { "Quantity must be 1–$maximum for ${before.itemName}." }
                encode(pocket.nativeIndex, count.toLong(), 0x5L, 0L)
            }
        }

        val write = ScarletVioletSaveValidation.writeInventoryRecord(original, targetId, record)
        verify(original, write.bytes, pocket, targetId, mode, selectedCount, write.absoluteOffset)
        val after = ScarletVioletInventoryReadOnly.read(write.bytes).slot(targetId)
        return ScarletVioletItemCopyPlan(
            original, write.bytes, pocket, targetId, mode, before, after, write.absoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        pocket: ScarletVioletInventoryPocket,
        itemId: Int,
        mode: ScarletVioletItemEditMode,
        selectedCount: Int?,
        absoluteOffset: Int
    ) {
        val beforeValidation = ScarletVioletSaveValidation.inspect(original)
        val afterValidation = ScarletVioletSaveValidation.inspect(edited)
        require(
            beforeValidation is ScarletVioletValidation.Verified &&
                afterValidation is ScarletVioletValidation.Verified &&
                beforeValidation.version == afterValidation.version && original.size == edited.size
        ) { "The edited save changed title identity or size." }
        val beforeInventory = ScarletVioletInventoryReadOnly.read(original)
        val afterInventory = ScarletVioletInventoryReadOnly.read(edited)
        val before = beforeInventory.slot(itemId)
        val after = afterInventory.slot(itemId)
        when (mode) {
            ScarletVioletItemEditMode.QUANTITY -> require(
                before.pocket == pocket && before.isEditable && after.pocket == pocket &&
                    after.rawCount == selectedCount?.toLong() && after.rawPouch == before.rawPouch &&
                    after.rawFlags == before.rawFlags && after.padding == before.padding && after.isEditable
            ) { "The staged quantity change did not reopen exactly." }
            ScarletVioletItemEditMode.REMOVE -> {
                val empty = afterInventory.slot(0)
                require(before.isEditable && !after.isRecorded &&
                    after.rawPouch == empty.rawPouch && after.rawCount == empty.rawCount &&
                    after.rawFlags == empty.rawFlags && after.padding == empty.padding
                ) { "The staged removal did not reopen as the native empty template." }
            }
            ScarletVioletItemEditMode.ADD -> require(
                !before.isRecorded && after.pocket == pocket && after.pouchMatchesCatalogue &&
                    after.rawCount == selectedCount?.toLong() && after.rawFlags == 0x5L &&
                    after.padding == 0L && after.isEditable
            ) { "The staged added item did not reopen exactly." }
        }
        val recordStart = absoluteOffset + if (mode == ScarletVioletItemEditMode.QUANTITY) 4 else 0
        val recordWidth = if (mode == ScarletVioletItemEditMode.QUANTITY) 4 else RECORD_SIZE
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in recordStart until recordStart + recordWidth ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun encode(pouch: Long, count: Long, flags: Long, padding: Long): ByteArray {
        val record = ByteArray(RECORD_SIZE)
        writeU32(record, 0, pouch)
        writeU32(record, 4, count)
        writeU32(record, 8, flags)
        writeU32(record, 12, padding)
        return record
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        require(value in 0L..0xFFFFFFFFL) { "A native Scarlet/Violet item word is out of range." }
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
    }
}

/** Single-use export transaction to a newly created empty document. */
class ScarletVioletItemCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val pocket: ScarletVioletInventoryPocket,
    val itemId: Int,
    val mode: ScarletVioletItemEditMode,
    val before: ScarletVioletInventorySlotRead,
    val after: ScarletVioletInventorySlotRead,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = ScarletVioletItemEditing.sha256(this.original)
    val editedSha256: String = ScarletVioletItemEditing.sha256(this.edited)
    private var consumed = false

    val beforeLabel: String get() = if (!before.isRecorded) "Not recorded" else "${before.itemName} ×${before.count}"
    val afterLabel: String get() = if (!after.isRecorded) "Not recorded" else "${after.itemName} ×${after.count}"

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
            ScarletVioletItemEditing.verify(
                original, edited, pocket, itemId, mode,
                if (after.isRecorded) after.count else null, absoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            ScarletVioletItemEditing.verify(
                original, persisted, pocket, itemId, mode,
                if (after.isRecorded) after.count else null, absoluteOffset
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
