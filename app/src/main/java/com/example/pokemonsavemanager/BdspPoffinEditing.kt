package com.example.pokemonsavemanager

import java.security.MessageDigest

enum class BdspPoffinEditField(val displayName: String, val relativeOffset: Int, val width: Int) {
    TYPE("Type ID", 0x00, 1),
    LEVEL("Level", 0x01, 1),
    SMOOTHNESS("Smoothness", 0x02, 1),
    NEW_FLAG("New flag", 0x04, 4),
    SPICY("Spicy", 0x08, 1),
    DRY("Dry", 0x09, 1),
    SWEET("Sweet", 0x0A, 1),
    BITTER("Bitter", 0x0B, 1),
    SOUR("Sour", 0x0C, 1)
}

data class BdspPoffinReplacement(
    val typeId: Int,
    val level: Int,
    val smoothness: Int,
    val rawNewFlag: Long,
    val flavors: List<Int>
) {
    fun value(field: BdspPoffinEditField): Long = when (field) {
        BdspPoffinEditField.TYPE -> typeId.toLong()
        BdspPoffinEditField.LEVEL -> level.toLong()
        BdspPoffinEditField.SMOOTHNESS -> smoothness.toLong()
        BdspPoffinEditField.NEW_FLAG -> rawNewFlag
        BdspPoffinEditField.SPICY -> flavors[0].toLong()
        BdspPoffinEditField.DRY -> flavors[1].toLong()
        BdspPoffinEditField.SWEET -> flavors[2].toLong()
        BdspPoffinEditField.BITTER -> flavors[3].toLong()
        BdspPoffinEditField.SOUR -> flavors[4].toLong()
    }
}

data class BdspPoffinEditRead(
    val version: BdspVersion,
    val revision: BdspSaveRevision,
    val slot: BdspPoffinSlotRead
)

/** Reviewed edits for one native 0x10-byte BDSP Poffin record. Reserved bytes stay exact. */
object BdspPoffinEditing {
    private const val CASE_OFFSET = 0x955B0
    private const val SLOT_SIZE = 0x10
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun read(source: ByteArray, slotIndex: Int): BdspPoffinEditRead {
        val poffins = BdspPoffinReadOnly.read(source)
        return BdspPoffinEditRead(poffins.version, poffins.revision, poffins.slot(slotIndex))
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        slotIndex: Int,
        replacement: BdspPoffinReplacement
    ): BdspPoffinCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(slotIndex in 0 until 100) { "Poffin slot index must be 0 through 99." }
        require(replacement.flavors.size == 5) { "All five native flavour values are required." }
        val before = read(original, slotIndex)
        val changedFields = BdspPoffinEditField.values().filter { field ->
            replacement.value(field) != value(before.slot, field)
        }
        require(changedFields.isNotEmpty()) { "Change at least one Poffin value before reviewing." }
        changedFields.forEach { field -> validate(field, replacement.value(field)) }

        val edited = original.copyOf()
        val base = slotOffset(slotIndex)
        changedFields.forEach { field ->
            val value = replacement.value(field)
            if (field.width == 1) edited[base + field.relativeOffset] = value.toByte()
            else writeU32(edited, base + field.relativeOffset, value)
        }
        refreshDigest(edited)
        val after = read(edited, slotIndex)
        verify(original, edited, slotIndex, after, changedFields)
        return BdspPoffinCopyPlan(original, edited, before, after, changedFields)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        slotIndex: Int,
        expected: BdspPoffinEditRead,
        changedFields: List<BdspPoffinEditField>
    ) {
        require(changedFields.isNotEmpty())
        val beforeCase = BdspPoffinReadOnly.read(original)
        val afterCase = BdspPoffinReadOnly.read(edited)
        require(
            original.size == edited.size && beforeCase.version == afterCase.version &&
                beforeCase.revision == afterCase.revision
        ) { "The Poffin edit changed title identity, save revision, or file size." }
        require(afterCase.slot(slotIndex) == expected.slot) { "The reviewed Poffin values did not reopen exactly." }
        require(beforeCase.cookingCount == afterCase.cookingCount && beforeCase.rawCookingCount == afterCase.rawCookingCount) {
            "The saved cooking count changed."
        }
        require(beforeCase.slots.indices.all { it == slotIndex || beforeCase.slots[it] == afterCase.slots[it] }) {
            "A different Poffin slot changed."
        }
        val base = slotOffset(slotIndex)
        require(original.indices.all { index ->
            changedFields.any { field -> index in base + field.relativeOffset until base + field.relativeOffset + field.width } ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH || original[index] == edited[index]
        }) { "The Poffin edit changed an unrelated BDSP save byte." }
    }

    fun slotOffset(slotIndex: Int): Int {
        require(slotIndex in 0 until 100)
        return CASE_OFFSET + slotIndex * SLOT_SIZE
    }

    fun value(slot: BdspPoffinSlotRead, field: BdspPoffinEditField): Long = when (field) {
        BdspPoffinEditField.TYPE -> slot.typeId.toLong()
        BdspPoffinEditField.LEVEL -> slot.level.toLong()
        BdspPoffinEditField.SMOOTHNESS -> slot.smoothness.toLong()
        BdspPoffinEditField.NEW_FLAG -> slot.rawNewFlag
        BdspPoffinEditField.SPICY -> slot.flavors[0].toLong()
        BdspPoffinEditField.DRY -> slot.flavors[1].toLong()
        BdspPoffinEditField.SWEET -> slot.flavors[2].toLong()
        BdspPoffinEditField.BITTER -> slot.flavors[3].toLong()
        BdspPoffinEditField.SOUR -> slot.flavors[4].toLong()
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun validate(field: BdspPoffinEditField, value: Long) {
        when (field) {
            BdspPoffinEditField.TYPE -> require(value in 0L..29L || value == 255L) {
                "A changed Poffin type must be 0 through 29, or 255 for an empty slot."
            }
            BdspPoffinEditField.NEW_FLAG -> require(value in 0L..1L) {
                "A changed New flag must be 0 or 1."
            }
            else -> require(value in 0L..255L) { "Changed ${field.displayName} must fit its native byte." }
        }
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class BdspPoffinCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: BdspPoffinEditRead,
    val after: BdspPoffinEditRead,
    val changedFields: List<BdspPoffinEditField>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = BdspPoffinEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen the Poffin slot." }
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
            BdspPoffinEditing.verify(original, edited, before.slot.index, after, changedFields)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspPoffinEditing.verify(original, persisted, before.slot.index, after, changedFields)
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
