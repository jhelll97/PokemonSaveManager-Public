package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class LetsGoItemEditMode { QUANTITY, REMOVE, ADD }

/** Bounded Let's Go item edits. One native slot and block 0's CRC are the complete write allowlist. */
object LetsGoItemEditing {
    private const val INVENTORY_BLOCK_OFFSET = 0x0000
    private const val INVENTORY_BLOCK_LENGTH = 0x0D90
    private const val INVENTORY_CRC_OFFSET = 0xB861A
    private const val COUNT_MASK = 0x3FFL shl 15

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        val max = LetsGoSaveValidation.FILE_SIZE
        while (true) {
            val remaining = max + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= max) { "The document exceeds the supported Let's Go save size." }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        pocket: LetsGoInventoryPocket,
        slot: Int,
        mode: LetsGoItemEditMode,
        selectedItemId: Int? = null,
        selectedCount: Int? = null
    ): LetsGoItemCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val beforeInventory = LetsGoInventoryReadOnly.read(original)
        val before = beforeInventory.pocket(pocket).slot(slot)
        val offset = pocket.offset + (slot - 1) * 4
        val edited = original.copyOf()

        when (mode) {
            LetsGoItemEditMode.QUANTITY -> {
                require(!before.isEmpty && before.listedInPocket) {
                    "Only a catalogued non-empty item slot can have its quantity changed."
                }
                val maximum = before.referenceMaxCount
                    ?: throw IllegalArgumentException("This item's native stack maximum is unknown.")
                val count = selectedCount ?: throw IllegalArgumentException("Enter a quantity.")
                require(count in 1..maximum) { "Quantity must be 1–$maximum for ${before.itemName}." }
                require(count != before.count) { "Choose a different quantity." }
                writeU32(edited, offset, (before.rawValue and COUNT_MASK.inv()) or (count.toLong() shl 15))
            }
            LetsGoItemEditMode.REMOVE -> {
                require(!before.isEmpty && before.listedInPocket) {
                    "Only a catalogued non-empty item slot can be removed."
                }
                writeU32(edited, offset, 0L)
            }
            LetsGoItemEditMode.ADD -> {
                require(before.isEmpty) { "The selected native slot is no longer empty." }
                val itemId = selectedItemId ?: throw IllegalArgumentException("Choose an item.")
                require(itemId in LetsGoInventoryReadOnly.allowedItemIds(pocket)) {
                    "That item is not in this pocket's native catalogue."
                }
                require(beforeInventory.pocket(pocket).recordedSlots.none { it.itemId == itemId }) {
                    "${LetsGoItemNames.name(itemId)} is already recorded in this pocket."
                }
                val maximum = LetsGoInventoryReadOnly.referenceMaxCount(pocket, itemId)
                    ?: throw IllegalArgumentException("This item's native stack maximum is unknown.")
                val count = selectedCount ?: throw IllegalArgumentException("Enter a quantity.")
                require(count in 1..maximum) { "Quantity must be 1–$maximum for ${LetsGoItemNames.name(itemId)}." }
                val packed = (itemId.toLong() and 0x7FFL) or (count.toLong() shl 15) or 0x40000000L
                writeU32(edited, offset, packed)
            }
        }

        val crc = LetsGoSaveValidation.crc16(edited, INVENTORY_BLOCK_OFFSET, INVENTORY_BLOCK_LENGTH)
        writeU16(edited, INVENTORY_CRC_OFFSET, crc)
        verify(original, edited, pocket, slot, mode, selectedItemId, selectedCount)
        val after = LetsGoInventoryReadOnly.read(edited).pocket(pocket).slot(slot)
        return LetsGoItemCopyPlan(original, edited, pocket, slot, mode, before, after)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        pocket: LetsGoInventoryPocket,
        slot: Int,
        mode: LetsGoItemEditMode,
        selectedItemId: Int?,
        selectedCount: Int?
    ) {
        val beforeInventory = LetsGoInventoryReadOnly.read(original)
        val afterInventory = LetsGoInventoryReadOnly.read(edited)
        require(beforeInventory.version == afterInventory.version && original.size == edited.size) {
            "The edited save changed title identity or size."
        }
        val before = beforeInventory.pocket(pocket).slot(slot)
        val after = afterInventory.pocket(pocket).slot(slot)
        when (mode) {
            LetsGoItemEditMode.QUANTITY -> require(
                !before.isEmpty && after.itemId == before.itemId && after.count == selectedCount &&
                    (after.rawValue and COUNT_MASK.inv()) == (before.rawValue and COUNT_MASK.inv())
            ) { "The staged quantity change did not reopen exactly." }
            LetsGoItemEditMode.REMOVE -> require(!before.isEmpty && after.isEmpty) {
                "The staged removal did not reopen as an empty slot."
            }
            LetsGoItemEditMode.ADD -> require(
                before.isEmpty && after.itemId == selectedItemId && after.count == selectedCount &&
                    after.listedInPocket
            ) { "The staged added item did not reopen exactly." }
        }
        val slotOffset = pocket.offset + (slot - 1) * 4
        require(original.indices.all { index ->
            index in slotOffset until slotOffset + 4 ||
                index in INVENTORY_CRC_OFFSET until INVENTORY_CRC_OFFSET + 2 ||
                original[index] == edited[index]
        }) { "An unrelated save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }
}

/** Single-use export transaction to a newly created, empty document. The source is never replaced. */
class LetsGoItemCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val pocket: LetsGoInventoryPocket,
    val slot: Int,
    val mode: LetsGoItemEditMode,
    val before: LetsGoInventorySlotRead,
    val after: LetsGoInventorySlotRead
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LetsGoItemEditing.sha256(this.original)
    val editedSha256: String = LetsGoItemEditing.sha256(this.edited)
    val slotOffset: Int = pocket.offset + (slot - 1) * 4
    private var consumed = false

    val beforeLabel: String get() = if (before.isEmpty) "Empty slot" else "${before.itemName} ×${before.count}"
    val afterLabel: String get() = if (after.isEmpty) "Empty slot" else "${after.itemName} ×${after.count}"

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
            LetsGoItemEditing.verify(original, edited, pocket, slot, mode,
                if (after.isEmpty) null else after.itemId, if (after.isEmpty) null else after.count)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LetsGoItemEditing.verify(original, persisted, pocket, slot, mode,
                if (after.isEmpty) null else after.itemId, if (after.isEmpty) null else after.count)
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
