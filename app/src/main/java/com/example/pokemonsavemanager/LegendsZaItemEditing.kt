package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class LegendsZaItemEditMode { QUANTITY, REMOVE, ADD }

/** Bounded Z-A item edits. One decoded item record and the native SHA-256 footer are allowed. */
object LegendsZaItemEditing {
    private const val MAX_SAVE_SIZE = 0x309FB3
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
                "The document exceeds the supported Legends: Z-A save size."
            }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        pocket: LegendsZaInventoryPocket,
        itemId: Int?,
        mode: LegendsZaItemEditMode,
        selectedItemId: Int? = null,
        selectedCount: Int? = null
    ): LegendsZaItemCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val beforeInventory = LegendsZaInventoryReadOnly.read(original)
        val targetId = when (mode) {
            LegendsZaItemEditMode.ADD -> selectedItemId
                ?: throw IllegalArgumentException("Choose an item.")
            else -> itemId ?: throw IllegalArgumentException("Reopen the item from PSE.")
        }
        require(targetId in LegendsZaInventoryReadOnly.allowedItemIds(pocket)) {
            "That item is not in this pocket's native catalogue."
        }
        require(beforeInventory.pockets.first { it.pocket == pocket }.unlocked) {
            "This native item pocket is still locked in the selected save."
        }
        val before = beforeInventory.slot(targetId)
        val maximum = before.referenceMaxCount
            ?: throw IllegalArgumentException("This item's native stack maximum is unknown.")
        val record = when (mode) {
            LegendsZaItemEditMode.QUANTITY -> {
                require(before.pocket == pocket && before.isEditable) {
                    "Only a catalogued item with normal native metadata can have its quantity changed."
                }
                val count = selectedCount ?: throw IllegalArgumentException("Enter a quantity.")
                require(count in 1..maximum) { "Quantity must be 1–$maximum for ${before.itemName}." }
                require(count.toLong() != before.rawCount) { "Choose a different quantity." }
                encode(before.rawPouch, count.toLong(), before.rawFlags, before.padding)
            }
            LegendsZaItemEditMode.REMOVE -> {
                require(before.pocket == pocket && before.isEditable) {
                    "Only a catalogued item with normal native metadata can be removed."
                }
                LegendsZaSaveValidation.readInventoryBlock(original).data.copyOfRange(0, RECORD_SIZE)
            }
            LegendsZaItemEditMode.ADD -> {
                require(before.pocket == pocket && !before.isRecorded) {
                    "The selected native item record is no longer empty."
                }
                val count = selectedCount ?: throw IllegalArgumentException("Enter a quantity.")
                require(count in 1..maximum) { "Quantity must be 1–$maximum for ${before.itemName}." }
                encode(pocket.nativeIndex.toLong(), count.toLong(), 0x9L, 0L)
            }
        }

        val write = LegendsZaSaveValidation.writeInventoryRecord(original, targetId, record)
        verify(original, write.bytes, pocket, targetId, mode, selectedCount, write.absoluteOffset)
        val after = LegendsZaInventoryReadOnly.read(write.bytes).slot(targetId)
        return LegendsZaItemCopyPlan(
            original, write.bytes, pocket, targetId, mode, before, after, write.absoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        pocket: LegendsZaInventoryPocket,
        itemId: Int,
        mode: LegendsZaItemEditMode,
        selectedCount: Int?,
        absoluteOffset: Int
    ) {
        val beforeValidation = LegendsZaSaveValidation.inspect(original)
        val afterValidation = LegendsZaSaveValidation.inspect(edited)
        require(
            beforeValidation is LegendsZaValidation.Verified &&
                afterValidation is LegendsZaValidation.Verified &&
                beforeValidation.version == afterValidation.version &&
                beforeValidation.revision == afterValidation.revision && original.size == edited.size
        ) { "The edited save changed title identity, revision, or size." }
        val beforeInventory = LegendsZaInventoryReadOnly.read(original)
        val afterInventory = LegendsZaInventoryReadOnly.read(edited)
        val before = beforeInventory.slot(itemId)
        val after = afterInventory.slot(itemId)
        require(beforeInventory.pockets.first { it.pocket == pocket }.unlocked &&
            afterInventory.pockets.first { it.pocket == pocket }.unlocked) {
            "The edited save changed the native pocket access state."
        }
        when (mode) {
            LegendsZaItemEditMode.QUANTITY -> require(
                before.pocket == pocket && before.isEditable && after.pocket == pocket &&
                    after.rawCount == selectedCount?.toLong() && after.rawPouch == before.rawPouch &&
                    after.rawFlags == before.rawFlags && after.padding == before.padding && after.isEditable
            ) { "The staged quantity change did not reopen exactly." }
            LegendsZaItemEditMode.REMOVE -> {
                val empty = afterInventory.slot(0)
                require(before.pocket == pocket && before.isEditable && !after.isRecorded &&
                    after.rawPouch == empty.rawPouch && after.rawCount == empty.rawCount &&
                    after.rawFlags == empty.rawFlags && after.padding == empty.padding
                ) { "The staged removal did not reopen as the native empty template." }
            }
            LegendsZaItemEditMode.ADD -> require(
                !before.isRecorded && after.pocket == pocket && after.pouchMatchesCatalogue &&
                    after.rawCount == selectedCount?.toLong() && after.rawFlags == 0x9L &&
                    after.padding == 0L && after.isEditable
            ) { "The staged added item did not reopen exactly." }
        }
        val recordStart = absoluteOffset + if (mode == LegendsZaItemEditMode.QUANTITY) 4 else 0
        val recordWidth = if (mode == LegendsZaItemEditMode.QUANTITY) 4 else RECORD_SIZE
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
        require(value in 0L..0xFFFFFFFFL) { "A native Legends: Z-A item word is out of range." }
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
    }
}

/** Single-use export transaction to a newly created empty document. */
class LegendsZaItemCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val pocket: LegendsZaInventoryPocket,
    val itemId: Int,
    val mode: LegendsZaItemEditMode,
    val before: LegendsZaInventorySlotRead,
    val after: LegendsZaInventorySlotRead,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LegendsZaItemEditing.sha256(this.original)
    val editedSha256: String = LegendsZaItemEditing.sha256(this.edited)
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
            LegendsZaItemEditing.verify(
                original, edited, pocket, itemId, mode,
                if (after.isRecorded) after.count else null, absoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LegendsZaItemEditing.verify(
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
