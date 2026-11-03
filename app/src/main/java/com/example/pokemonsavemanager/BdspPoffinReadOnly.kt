package com.example.pokemonsavemanager

import java.util.Collections

data class BdspPoffinSlotRead(val index: Int, val typeId: Int, val level: Int, val smoothness: Int,
    val rawNewFlag: Long, val flavors: List<Int>, val rawBytes: List<Int>) {
    val position: Int get() = index + 1
    val isEmpty: Boolean get() = typeId == 255
    val isKnownType: Boolean get() = typeId in 0..29
    val name: String get() = BdspPoffinNames.name(typeId)
    val isNew: Boolean get() = rawNewFlag == 1L
    val hasCanonicalNewFlag: Boolean get() = rawNewFlag in 0L..1L
}

class BdspPoffinCaseRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val slots: List<BdspPoffinSlotRead>, val cookingCount: Int, val rawCookingCount: Long,
    val rawBytes: List<Int>) {
    val knownPoffinCount: Int get() = slots.count { it.isKnownType }
    val unknownTypeCount: Int get() = slots.count { !it.isKnownType && !it.isEmpty }
    val emptyCount: Int get() = slots.count { it.isEmpty }
    fun entries(includeEmpty: Boolean = false): List<BdspPoffinSlotRead> =
        Collections.unmodifiableList(slots.filter { includeEmpty || !it.isEmpty })
    fun slot(index: Int): BdspPoffinSlotRead {
        require(index in slots.indices) { "Poffin slot index must be 0 through 99." }
        return slots[index]
    }
}

/** Native type IDs are zero based; only FF means empty, even if other saved bytes remain. */
object BdspPoffinNames {
    private val names = listOf(
        "Spicy Poffin", "Spicy-Dry Poffin", "Spicy-Sweet Poffin", "Spicy-Bitter Poffin", "Spicy-Sour Poffin",
        "Dry Poffin", "Dry-Spicy Poffin", "Dry-Sweet Poffin", "Dry-Bitter Poffin", "Dry-Sour Poffin",
        "Sweet Poffin", "Sweet-Spicy Poffin", "Sweet-Dry Poffin", "Sweet-Bitter Poffin", "Sweet-Sour Poffin",
        "Bitter Poffin", "Bitter-Spicy Poffin", "Bitter-Dry Poffin", "Bitter-Sweet Poffin", "Bitter-Sour Poffin",
        "Sour Poffin", "Sour-Spicy Poffin", "Sour-Dry Poffin", "Sour-Sweet Poffin", "Sour-Bitter Poffin",
        "Rich Poffin", "Overripe Poffin", "Foul Poffin", "Mild Poffin", "Super Mild Poffin"
    )
    fun name(typeId: Int): String = if (typeId == 255) "Empty slot" else names.getOrNull(typeId) ?: "Unknown Poffin type #$typeId"
}

/** Whole-file size/revision/title/MD5 checks always precede extraction. No sorting, cleanup or writer. */
object BdspPoffinReadOnly {
    fun read(source: ByteArray): BdspPoffinCaseRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x955B0
        val slots = (0 until 100).map { index ->
            val offset = base + index * 16
            BdspPoffinSlotRead(index, byte(source, offset), byte(source, offset + 1), byte(source, offset + 2),
                u32(source, offset + 4), immutable((0..4).map { byte(source, offset + 8 + it) }), raw(source, offset, 16))
        }
        val count = u32(source, base + 0x640)
        return BdspPoffinCaseRead(checked.version, checked.revision, immutable(slots), count.toInt(), count, raw(source, base, 0x644))
    }
    private fun byte(b: ByteArray, offset: Int): Int = b[offset].toInt() and 255
    private fun u32(b: ByteArray, offset: Int): Long = (0..3).fold(0L) { n, i ->
        n or ((b[offset + i].toLong() and 255) shl (8 * i))
    }
    private fun raw(b: ByteArray, offset: Int, size: Int): List<Int> = immutable((0 until size).map { byte(b, offset + it) })
    private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
}
