package com.example.pokemonsavemanager

import java.util.Collections

enum class BdspUndergroundKind(val displayName: String, val referenceLabel: String) {
    SPHERE("Spheres", "Sphere ID"), ITEM("Treasures and TMs", "Normal bag item ID"),
    PEDESTAL("Pedestals", "Pedestal ID"), STATUE("Statues", "Statue ID")
}

data class BdspUndergroundCatalogueEntry(val undergroundId: Int, val name: String,
    val kind: BdspUndergroundKind, val nativeReferenceId: Int)

/** Three independent native words; item identity is the Underground array index. */
data class BdspUndergroundSlotRead(val undergroundId: Int, val count: Int, val rawCount: Long,
    val rawHideNewFlag: Long, val rawFavoriteFlag: Long, val catalogue: BdspUndergroundCatalogueEntry?) {
    val name: String get() = catalogue?.name ?: "Underground item #$undergroundId"
    val kind: BdspUndergroundKind? get() = catalogue?.kind
    // Native booleans compare the complete UInt32 word to 1, not merely bit zero.
    val isNew: Boolean get() = rawHideNewFlag != 1L
    val isFavorite: Boolean get() = rawFavoriteFlag == 1L
    val isRecorded: Boolean get() = rawCount != 0L || rawHideNewFlag != 0L || rawFavoriteFlag != 0L
    val referenceMaxCount: Int? get() = when (kind) {
        BdspUndergroundKind.STATUE, BdspUndergroundKind.PEDESTAL -> 99
        BdspUndergroundKind.ITEM, BdspUndergroundKind.SPHERE -> 999
        null -> null
    }
    val hasUnusualMetadata: Boolean get() = count < 0 || rawHideNewFlag !in 0L..1L ||
        rawFavoriteFlag !in 0L..1L || (referenceMaxCount?.let { count > it } ?: false) ||
        (catalogue == null && isRecorded)
}

class BdspUndergroundRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val slots: List<BdspUndergroundSlotRead>) {
    val recordedCount: Int get() = slots.count { it.isRecorded }
    /** Native index order; null selects unlisted indices. No values are repaired or merged. */
    fun entries(kind: BdspUndergroundKind?, includeEmpty: Boolean = false): List<BdspUndergroundSlotRead> =
        Collections.unmodifiableList(slots.filter { it.kind == kind && (includeEmpty || it.isRecorded) })
    fun slot(undergroundId: Int): BdspUndergroundSlotRead {
        require(undergroundId in slots.indices) { "The Underground index is outside 0..998." }
        return slots[undergroundId]
    }
}

/** Existing native size/revision/title/whole-file MD5 validation always runs before extraction. */
object BdspUndergroundReadOnly {
    fun read(source: ByteArray): BdspUndergroundRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val slots = (0 until 999).map { id ->
            val offset = 0x111BC + id * 12
            val raw = u32(source, offset)
            BdspUndergroundSlotRead(id, raw.toInt(), raw, u32(source, offset + 4),
                u32(source, offset + 8), BdspUndergroundCatalogue.entry(id))
        }
        return BdspUndergroundRead(checked.version, checked.revision,
            Collections.unmodifiableList(ArrayList(slots)))
    }
    private fun u32(b: ByteArray, offset: Int): Long = (0..3).fold(0L) { n, i ->
        n or ((b[offset + i].toLong() and 255) shl (8 * i))
    }
}
