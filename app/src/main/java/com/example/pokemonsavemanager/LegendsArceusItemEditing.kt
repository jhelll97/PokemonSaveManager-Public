package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class LegendsArceusItemEditMode { QUANTITY, REMOVE, ADD }

/** Bounded PLA item edits. One decoded four-byte record and the native SHA-256 footer are allowed. */
object LegendsArceusItemEditing {
    private const val MAX_SAVE_SIZE = 0x13AD06

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
                "The document exceeds the supported Legends: Arceus save size."
            }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        pocketIndex: Int,
        slot: Int,
        mode: LegendsArceusItemEditMode,
        selectedItemId: Int? = null,
        selectedCount: Int? = null
    ): LegendsArceusItemCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val beforeInventory = LegendsArceusInventoryReadOnly.read(original)
        val pocket = beforeInventory.pockets.getOrNull(pocketIndex)
            ?: throw IllegalArgumentException("Reopen the inventory pocket from PSE.")
        val before = pocket.items.getOrNull(slot)
            ?: throw IllegalArgumentException("Reopen the item slot from PSE.")
        require(before.withinActiveCapacity) { "Slots beyond the active native capacity remain read-only." }
        val itemId: Int
        val count: Int
        when (mode) {
            LegendsArceusItemEditMode.QUANTITY -> {
                require(before.isRecorded && before.itemId > 0 && LegendsArceusItemNames.isCatalogued(before.itemId)) {
                    "Only a catalogued non-empty item can have its quantity changed."
                }
                itemId = before.itemId
                count = selectedCount ?: throw IllegalArgumentException("Enter a quantity.")
                require(count in 1..pocket.referenceMaxCount) {
                    "Quantity must be 1–${pocket.referenceMaxCount} for ${before.itemName}."
                }
                require(count != before.count) { "Choose a different quantity." }
            }
            LegendsArceusItemEditMode.REMOVE -> {
                require(before.isRecorded && before.itemId > 0 && LegendsArceusItemNames.isCatalogued(before.itemId)) {
                    "Only a catalogued non-empty item can be removed."
                }
                itemId = 0
                count = 0
            }
            LegendsArceusItemEditMode.ADD -> {
                require(pocket.allowsCataloguedAdd) { "Adding items is unavailable for this native pocket." }
                require(!before.isRecorded) { "The selected native slot is no longer empty." }
                itemId = selectedItemId ?: throw IllegalArgumentException("Choose an item.")
                require(LegendsArceusItemNames.isCatalogued(itemId)) {
                    "That item is not in the pinned Legends: Arceus catalogue."
                }
                require(pocket.recordedItems.none { it.itemId == itemId }) {
                    "${LegendsArceusItemNames.name(itemId)} is already recorded in this pocket."
                }
                count = selectedCount ?: throw IllegalArgumentException("Enter a quantity.")
                require(count in 1..pocket.referenceMaxCount) {
                    "Quantity must be 1–${pocket.referenceMaxCount} for ${LegendsArceusItemNames.name(itemId)}."
                }
            }
        }

        val write = LegendsArceusSaveValidation.writeInventoryRecord(
            original,
            pocket.blockKey,
            slot * LegendsArceusInventoryReadOnly.RECORD_BYTES,
            itemId,
            count
        )
        verify(original, write.bytes, pocketIndex, slot, mode,
            if (mode == LegendsArceusItemEditMode.REMOVE) null else itemId,
            if (mode == LegendsArceusItemEditMode.REMOVE) null else count,
            write.absoluteOffset)
        val after = LegendsArceusInventoryReadOnly.read(write.bytes).pockets[pocketIndex].items[slot]
        return LegendsArceusItemCopyPlan(
            original, write.bytes, pocketIndex, pocket.name, pocket.blockKey, slot, mode,
            before, after, write.absoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        pocketIndex: Int,
        slot: Int,
        mode: LegendsArceusItemEditMode,
        selectedItemId: Int?,
        selectedCount: Int?,
        absoluteOffset: Int
    ) {
        val beforeValidation = LegendsArceusSaveValidation.inspect(original)
        val afterValidation = LegendsArceusSaveValidation.inspect(edited)
        require(beforeValidation is LegendsArceusValidation.Verified &&
            afterValidation is LegendsArceusValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The edited save changed title identity or size."
        }
        val before = LegendsArceusInventoryReadOnly.read(original).pockets[pocketIndex].items[slot]
        val after = LegendsArceusInventoryReadOnly.read(edited).pockets[pocketIndex].items[slot]
        when (mode) {
            LegendsArceusItemEditMode.QUANTITY -> require(
                before.isRecorded && after.itemId == before.itemId && after.count == selectedCount &&
                    after.rawItemId == before.rawItemId && after.isFavorite == before.isFavorite
            ) { "The staged quantity change did not reopen exactly." }
            LegendsArceusItemEditMode.REMOVE -> require(before.isRecorded && !after.isRecorded) {
                "The staged removal did not reopen as an empty slot."
            }
            LegendsArceusItemEditMode.ADD -> require(
                !before.isRecorded && after.itemId == selectedItemId && after.count == selectedCount &&
                    LegendsArceusItemNames.isCatalogued(after.itemId)
            ) { "The staged added item did not reopen exactly." }
        }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + LegendsArceusInventoryReadOnly.RECORD_BYTES ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use export transaction to a new empty document. The selected source is never replaced. */
class LegendsArceusItemCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val pocketIndex: Int,
    val pocketName: String,
    val blockKey: UInt,
    val slot: Int,
    val mode: LegendsArceusItemEditMode,
    val before: LegendsArceusInventoryItem,
    val after: LegendsArceusInventoryItem,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LegendsArceusItemEditing.sha256(this.original)
    val editedSha256: String = LegendsArceusItemEditing.sha256(this.edited)
    private var consumed = false

    val beforeLabel: String get() = if (!before.isRecorded) "Empty slot" else "${before.itemName} ×${before.count}"
    val afterLabel: String get() = if (!after.isRecorded) "Empty slot" else "${after.itemName} ×${after.count}"

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
            LegendsArceusItemEditing.verify(
                original, edited, pocketIndex, slot, mode,
                if (after.isRecorded) after.itemId else null,
                if (after.isRecorded) after.count else null,
                absoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LegendsArceusItemEditing.verify(
                original, persisted, pocketIndex, slot, mode,
                if (after.isRecorded) after.itemId else null,
                if (after.isRecorded) after.count else null,
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
