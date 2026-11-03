package com.example.pokemonsavemanager

import java.util.Collections

enum class BdspInventoryPocket(val displayName: String) {
    ITEMS("Other Items"), KEY_ITEMS("Key Items"), MACHINES("TMs"), MEDICINE("Medicine"),
    BERRIES("Berries"), BALLS("Poké Balls"), BATTLE("Battle Items"), TREASURE("Treasures")
}

/** One native 16-byte record. Its item ID comes from the array index, not saved bytes. */
data class BdspInventorySlotRead(val itemId: Int, val count: Int, val rawCount: Long,
    val rawSeenFlag: Long, val rawFavoriteFlag: Long, val sortOrder: Int, val alignment: Int,
    val pocket: BdspInventoryPocket?) {
    val itemName: String get() = BdspItemNames.name(itemId)
    val isNew: Boolean get() = rawSeenFlag == 0L
    val isFavorite: Boolean get() = rawFavoriteFlag == 1L
    val hasSortOrder: Boolean get() = sortOrder != 0
    val isRecorded: Boolean get() = rawCount != 0L || rawSeenFlag != 0L || rawFavoriteFlag != 0L || sortOrder != 0 || alignment != 0
    val isEmpty: Boolean get() = !isRecorded
    val referenceMaxCount: Int? get() = when (pocket) { null -> null; BdspInventoryPocket.KEY_ITEMS -> 1; else -> 999 }
    val exceedsReferenceMax: Boolean get() = referenceMaxCount?.let { count > it } ?: false
    val hasUnusualMetadata: Boolean get() = count < 0 || rawSeenFlag !in 0L..1L || rawFavoriteFlag !in 0L..1L || alignment != 0 || exceedsReferenceMax || pocket == null
}
class BdspInventoryPocketRead internal constructor(val pocket: BdspInventoryPocket, val slots: List<BdspInventorySlotRead>) {
    // Saved sort numbers are display metadata only. Ties retain item-ID order; zero-sort residuals stay visible last.
    val recordedSlots: List<BdspInventorySlotRead> get() = Collections.unmodifiableList(slots.filter { it.isRecorded }
        .sortedWith(compareBy<BdspInventorySlotRead> { if (it.hasSortOrder) it.sortOrder else Int.MAX_VALUE }.thenBy { it.itemId }))
    val recordedCount: Int get() = slots.count { it.isRecorded }
}
class BdspInventoryRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val slots: List<BdspInventorySlotRead>, val pockets: List<BdspInventoryPocketRead>) {
    val recordedCount: Int get() = slots.count { it.isRecorded }
    val unlistedRecords: List<BdspInventorySlotRead> get() = Collections.unmodifiableList(slots.filter { it.isRecorded && it.pocket == null })
    fun slot(itemId: Int): BdspInventorySlotRead {
        require(itemId in slots.indices) { "The native inventory item index is outside 0..2999." }
        return slots[itemId]
    }
    fun pocket(pocket: BdspInventoryPocket): BdspInventoryPocketRead = pockets.first { it.pocket == pocket }
}

/** Whole-file native MD5/revision/title validation always precedes any bag extraction. No writer. */
object BdspInventoryReadOnly {
    private val allowed = mapOf(
        BdspInventoryPocket.ITEMS to setOf(45, 46, 47, 48, 49, 50, 51, 52, 53, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 93, 94, 107, 108, 109, 110, 111, 112, 135, 136, 213, 214, 215, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252, 253, 254, 255, 256, 257, 258, 259, 260, 261, 262, 263, 264, 265, 266, 267, 268, 269, 270, 271, 272, 273, 274, 275, 276, 277, 278, 279, 280, 281, 282, 283, 284, 285, 286, 287, 288, 289, 290, 291, 292, 293, 294, 295, 296, 297, 298, 299, 300, 301, 302, 303, 304, 305, 306, 307, 308, 309, 310, 311, 312, 313, 314, 315, 316, 317, 318, 319, 320, 321, 322, 323, 324, 325, 326, 327, 537, 565, 566, 567, 568, 569, 570, 644, 645, 849, 1231, 1232, 1233, 1234, 1235, 1236, 1237, 1238, 1239, 1240, 1241, 1242, 1243, 1244, 1245, 1246, 1247, 1248, 1249, 1250, 1251, 1606),
        BdspInventoryPocket.KEY_ITEMS to setOf(428, 431, 432, 433, 438, 439, 440, 443, 445, 446, 447, 448, 449, 450, 451, 452, 453, 454, 455, 459, 460, 461, 462, 463, 464, 466, 467, 631, 632, 1267, 1278, 1822),
        BdspInventoryPocket.MACHINES to setOf(328, 329, 330, 331, 332, 333, 334, 335, 336, 337, 338, 339, 340, 341, 342, 343, 344, 345, 346, 347, 348, 349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 361, 362, 363, 364, 365, 366, 367, 368, 369, 370, 371, 372, 373, 374, 375, 376, 377, 378, 379, 380, 381, 382, 383, 384, 385, 386, 387, 388, 389, 390, 391, 392, 393, 394, 395, 396, 397, 398, 399, 400, 401, 402, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415, 416, 417, 418, 419, 420, 421, 422, 423, 424, 425, 426, 427),
        BdspInventoryPocket.MEDICINE to setOf(17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 54),
        BdspInventoryPocket.BERRIES to setOf(149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 686),
        BdspInventoryPocket.BALLS to setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 492, 493, 494, 495, 496, 497, 498, 499, 500, 576, 851),
        BdspInventoryPocket.BATTLE to setOf(55, 56, 57, 58, 59, 60, 61, 62, 63),
        BdspInventoryPocket.TREASURE to setOf(86, 87, 88, 89, 90, 91, 92, 99, 100, 101, 102, 103, 104, 105, 106, 795, 796, 1808, 1809, 1810, 1811, 1812, 1813, 1814, 1815, 1816, 1817, 1818, 1819, 1820, 1821)
    )
    fun read(source: ByteArray): BdspInventoryRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) { (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP inventory save is required." }
        val slots = (0 until 3000).map { id ->
            val offset = 0x0563C + id * 16
            val raw = u32(source, offset)
            BdspInventorySlotRead(id, raw.toInt(), raw, u32(source, offset + 4), u32(source, offset + 8),
                u16(source, offset + 12), u16(source, offset + 14), allowed.entries.firstOrNull { id in it.value }?.key)
        }
        val pockets = BdspInventoryPocket.values().map { p -> BdspInventoryPocketRead(p, immutable(slots.filter { it.pocket == p })) }
        return BdspInventoryRead(checked.version, checked.revision, immutable(slots), immutable(pockets))
    }
    fun allowedItemIds(pocket: BdspInventoryPocket): List<Int> = allowed.getValue(pocket).sorted()
    private fun u16(b: ByteArray, offset: Int): Int = (b[offset].toInt() and 255) or ((b[offset + 1].toInt() and 255) shl 8)
    private fun u32(b: ByteArray, offset: Int): Long = (0..3).fold(0L) { n, i -> n or ((b[offset + i].toLong() and 255) shl (8 * i)) }
    private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
}
