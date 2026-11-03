package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class SwordShieldItemEditMode { QUANTITY, REMOVE, ADD }

/** One native Sword/Shield inventory word and the required container hash are the complete write allowlist. */
object SwordShieldItemEditing {
    private const val MAX_FILE_SIZE = 0x1876B1
    private const val COUNT_MASK = 0x3FFL shl 15

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val remaining = MAX_FILE_SIZE + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= MAX_FILE_SIZE) { "The document exceeds the supported Sword/Shield save size." }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        pocket: SwordShieldInventoryPocket,
        slot: Int,
        mode: SwordShieldItemEditMode,
        selectedItemId: Int? = null,
        selectedCount: Int? = null
    ): SwordShieldItemCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val beforeInventory = SwordShieldInventoryReadOnly.read(original)
        val before = beforeInventory.pocket(pocket).slot(slot)
        val rawValue = when (mode) {
            SwordShieldItemEditMode.QUANTITY -> {
                require(!before.isEmpty && before.listedInPocket) {
                    "Only a catalogued non-empty item slot can have its quantity changed."
                }
                val maximum = before.referenceMaxCount
                    ?: throw IllegalArgumentException("This item's native stack maximum is unknown.")
                val count = selectedCount ?: throw IllegalArgumentException("Enter a quantity.")
                require(count in 1..maximum) { "Quantity must be 1–$maximum for ${before.itemName}." }
                require(count != before.count) { "Choose a different quantity." }
                (before.rawValue and COUNT_MASK.inv()) or (count.toLong() shl 15)
            }
            SwordShieldItemEditMode.REMOVE -> {
                require(!before.isEmpty && before.listedInPocket) {
                    "Only a catalogued non-empty item slot can be removed."
                }
                0L
            }
            SwordShieldItemEditMode.ADD -> {
                require(before.isEmpty) { "The selected native slot is no longer empty." }
                val itemId = selectedItemId ?: throw IllegalArgumentException("Choose an item.")
                require(itemId in SwordShieldInventoryReadOnly.allowedItemIds(pocket)) {
                    "That item is not in this pocket's native catalogue."
                }
                require(beforeInventory.pocket(pocket).recordedSlots.none { it.itemId == itemId }) {
                    "${SwordShieldItemNames.name(itemId)} is already recorded in this pocket."
                }
                val maximum = SwordShieldInventoryReadOnly.referenceMaxCount(pocket, itemId)
                    ?: throw IllegalArgumentException("This item's native stack maximum is unknown.")
                val count = selectedCount ?: throw IllegalArgumentException("Enter a quantity.")
                require(count in 1..maximum) { "Quantity must be 1–$maximum for ${SwordShieldItemNames.name(itemId)}." }
                (itemId.toLong() and 0x7FFL) or (count.toLong() shl 15) or 0x40000000L
            }
        }
        val written = SwordShieldSaveValidation.writeInventoryWord(
            original,
            pocket.offset + (slot - 1) * 4,
            rawValue
        )
        verify(original, written.bytes, pocket, slot, mode, selectedItemId, selectedCount, written.absoluteOffset)
        val after = SwordShieldInventoryReadOnly.read(written.bytes).pocket(pocket).slot(slot)
        return SwordShieldItemCopyPlan(
            original,
            written.bytes,
            pocket,
            slot,
            mode,
            before,
            after,
            written.absoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        pocket: SwordShieldInventoryPocket,
        slot: Int,
        mode: SwordShieldItemEditMode,
        selectedItemId: Int?,
        selectedCount: Int?,
        absoluteOffset: Int
    ) {
        val beforeInventory = SwordShieldInventoryReadOnly.read(original)
        val afterInventory = SwordShieldInventoryReadOnly.read(edited)
        require(beforeInventory.version == afterInventory.version && original.size == edited.size) {
            "The edited save changed title identity or size."
        }
        val before = beforeInventory.pocket(pocket).slot(slot)
        val after = afterInventory.pocket(pocket).slot(slot)
        when (mode) {
            SwordShieldItemEditMode.QUANTITY -> require(
                !before.isEmpty && after.itemId == before.itemId && after.count == selectedCount &&
                    (after.rawValue and COUNT_MASK.inv()) == (before.rawValue and COUNT_MASK.inv())
            ) { "The staged quantity change did not reopen exactly." }
            SwordShieldItemEditMode.REMOVE -> require(!before.isEmpty && after.isEmpty) {
                "The staged removal did not reopen as an empty slot."
            }
            SwordShieldItemEditMode.ADD -> require(
                before.isEmpty && after.itemId == selectedItemId && after.count == selectedCount &&
                    after.listedInPocket
            ) { "The staged added item did not reopen exactly." }
        }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + 4 ||
                index in hashOffset until original.size ||
                original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use export transaction to a new empty document. The selected source is never replaced. */
class SwordShieldItemCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val pocket: SwordShieldInventoryPocket,
    val slot: Int,
    val mode: SwordShieldItemEditMode,
    val before: SwordShieldInventorySlotRead,
    val after: SwordShieldInventorySlotRead,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldItemEditing.sha256(this.original)
    val editedSha256 = SwordShieldItemEditing.sha256(this.edited)
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
            SwordShieldItemEditing.verify(
                original,
                edited,
                pocket,
                slot,
                mode,
                if (after.isEmpty) null else after.itemId,
                if (after.isEmpty) null else after.count,
                absoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            SwordShieldItemEditing.verify(
                original,
                persisted,
                pocket,
                slot,
                mode,
                if (after.isEmpty) null else after.itemId,
                if (after.isEmpty) null else after.count,
                absoluteOffset
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
