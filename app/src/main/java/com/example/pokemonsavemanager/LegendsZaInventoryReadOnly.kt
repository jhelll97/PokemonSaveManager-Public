package com.example.pokemonsavemanager

import java.util.Collections

enum class LegendsZaInventoryPocket(val displayName: String, val nativeIndex: Int) {
    MEDICINE("Medicine", 0), BALLS("Poké Balls", 1), BERRIES("Berries", 5), ITEMS("Other Items", 2),
    MACHINES("TMs", 6), MEGA_STONES("Mega Stones", 7), TREASURE("Treasures", 3), KEY_ITEMS("Key Items", 4)
}

data class LegendsZaInventorySlotRead(val itemId: Int, val count: Int, val rawCount: Long,
    val rawPouch: Long, val rawFlags: Long, val padding: Long, val pocket: LegendsZaInventoryPocket?,
    val megaShardMaxPatched: Boolean) {
    val itemName: String get() = LegendsZaItemNames.name(itemId)
    val isNew: Boolean get() = rawFlags and 1L != 0L
    val isFavorite: Boolean get() = rawFlags and 2L != 0L
    val isNewNotify: Boolean get() = rawFlags and 4L != 0L
    val isNewShop: Boolean get() = rawFlags and 8L != 0L
    val isHeld: Boolean get() = rawFlags and 16L != 0L
    val hasValidPouch: Boolean get() = rawPouch != 0xFFFFFFFFL
    // Retain every nondefault saved record; native unacquired slots use FFFFFFFF/0/0D/0.
    val isRecorded: Boolean get() = !(rawCount == 0L && padding == 0L &&
        ((rawPouch == 0xFFFFFFFFL && rawFlags in setOf(0L, 13L)) || (rawPouch == 0L && rawFlags == 0L)))
    val pouchMatchesCatalogue: Boolean get() = pocket?.nativeIndex?.toLong() == rawPouch
    val referenceMaxCount: Int? get() = when {
        pocket == null -> null
        itemId == 2612 -> 0
        itemId == 2619 -> count
        itemId == 2618 && megaShardMaxPatched -> 9999
        pocket in setOf(LegendsZaInventoryPocket.KEY_ITEMS, LegendsZaInventoryPocket.MACHINES, LegendsZaInventoryPocket.MEGA_STONES) -> 1
        else -> 999
    }
}

class LegendsZaInventoryPocketRead internal constructor(val pocket: LegendsZaInventoryPocket,
    val unlocked: Boolean, val slots: List<LegendsZaInventorySlotRead>) {
    val recordedSlots: List<LegendsZaInventorySlotRead> = immutable(slots.filter { it.isRecorded })
}
class LegendsZaInventoryRead internal constructor(val version: LegendsZaVersion, val revision: LegendsZaRevision?,
    val megaShardMaxPatched: Boolean, val rawPouchFlags: Long, val tailBytes: List<Int>,
    val slots: List<LegendsZaInventorySlotRead>, val pockets: List<LegendsZaInventoryPocketRead>) {
    val recordedCount: Int get() = slots.count { it.isRecorded }
    val unlistedRecords: List<LegendsZaInventorySlotRead> = immutable(slots.filter { it.isRecorded && it.pocket == null })
    val additionalPocketFlag: Boolean get() = rawPouchFlags and 256L != 0L
    fun slot(itemId: Int): LegendsZaInventorySlotRead {
        require(itemId in slots.indices) { "The native inventory item index must be 0 through 2999." }
        return slots[itemId]
    }
}
object LegendsZaInventoryReadOnly {
    private val catalogue = mapOf(
        LegendsZaInventoryPocket.MEDICINE to setOf(17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 708, 2684),
        LegendsZaInventoryPocket.BALLS to setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 492, 493, 494, 495, 496, 497, 498, 499, 576, 851),
        LegendsZaInventoryPocket.BERRIES to setOf(149, 150, 151, 152, 153, 155, 156, 157, 158, 169, 170, 171, 172, 173, 174, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 686, 2651, 2652, 2653, 2654, 2655, 2656, 2657, 2658, 2659, 2660, 2661, 2662, 2663, 2664, 2665, 2666, 2667, 2668, 2669, 2670, 2671, 2672, 2673, 2674, 2675, 2676, 2677, 2678, 2679, 2680, 2681, 2682, 2683),
        LegendsZaInventoryPocket.ITEMS to setOf(45, 46, 47, 48, 49, 50, 52, 80, 81, 82, 83, 84, 85, 103, 107, 108, 109, 116, 117, 118, 119, 214, 217, 218, 221, 222, 230, 231, 232, 233, 234, 236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252, 253, 258, 259, 266, 267, 268, 270, 275, 289, 290, 291, 292, 293, 294, 296, 324, 534, 535, 537, 538, 540, 564, 565, 566, 567, 568, 569, 570, 639, 640, 646, 647, 710, 711, 795, 796, 849, 1124, 1125, 1126, 1127, 1128, 1231, 1232, 1233, 1234, 1235, 1236, 1237, 1238, 1239, 1240, 1241, 1242, 1243, 1244, 1245, 1246, 1247, 1248, 1249, 1250, 1251, 1582, 1592, 1691, 1861, 2344, 2401, 2558, 2618, 2137, 2619),
        LegendsZaInventoryPocket.MACHINES to setOf(328, 329, 330, 331, 332, 333, 334, 335, 336, 337, 338, 339, 340, 341, 342, 343, 344, 345, 346, 347, 348, 349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 361, 362, 363, 364, 365, 366, 367, 368, 369, 370, 371, 372, 373, 374, 375, 376, 377, 378, 379, 380, 381, 382, 383, 384, 385, 386, 387, 388, 389, 390, 391, 392, 393, 394, 395, 396, 397, 398, 399, 400, 401, 402, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415, 416, 417, 418, 419, 618, 619, 620, 690, 691, 692, 693, 2160, 2162, 2163, 2164, 2165, 2166, 2167, 2168, 2169, 2170, 2171, 2172, 2173, 2174, 2175, 2176, 2177, 2178, 2179, 2180, 2181, 2182, 2183, 2184, 2185, 2186, 2187, 2188, 2189, 2190, 2191, 2192, 2193, 2194, 2195, 2196, 2197, 2198, 2199, 2200, 2201, 2202, 2203, 2204, 2205, 2206, 2207, 2208, 2209, 2210, 2211, 2212, 2213, 2214, 2215, 2216, 2217, 2218, 2219, 2220, 2221),
        LegendsZaInventoryPocket.MEGA_STONES to setOf(656, 657, 658, 659, 660, 661, 662, 663, 664, 665, 666, 667, 668, 669, 670, 671, 672, 673, 674, 675, 676, 677, 678, 679, 680, 681, 682, 683, 684, 685, 752, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 767, 768, 769, 770, 2559, 2560, 2561, 2562, 2563, 2564, 2565, 2566, 2567, 2568, 2569, 2570, 2571, 2572, 2573, 2574, 2575, 2576, 2577, 2578, 2579, 2580, 2581, 2582, 2583, 2584, 2585, 2586, 2587, 2635, 2636, 2637, 2638, 2639, 2640, 2641, 2642, 2643, 2644, 2645, 2646, 2647, 2648, 2649, 2650),
        LegendsZaInventoryPocket.TREASURE to setOf(86, 88, 89, 92, 571, 581, 582),
        LegendsZaInventoryPocket.KEY_ITEMS to setOf(632, 700, 765, 847, 1278, 2588, 2589, 2590, 2591, 2592, 2595, 2596, 2597, 2598, 2599, 2600, 2601, 2602, 2603, 2604, 2605, 2606, 2607, 2608, 2609, 2610, 2611, 2612, 2613, 2620, 2621, 2622, 2623, 2624, 2625, 2626, 2627, 2628, 2629, 2630, 2631, 2632, 2633, 2634)
    )
    fun read(source: ByteArray): LegendsZaInventoryRead {
        val block = LegendsZaSaveValidation.readInventoryBlock(source)
        val flags = u32(block.data, 0xBB80)
        val slots = (0 until 3000).map { id ->
            val at = id * 16; val raw = u32(block.data, at + 4)
            LegendsZaInventorySlotRead(id, raw.toInt(), raw, u32(block.data, at),
                u32(block.data, at + 8), u32(block.data, at + 12),
                catalogue.entries.firstOrNull { id in it.value }?.key, block.megaShardMaxPatched)
        }
        return LegendsZaInventoryRead(block.version, block.revision, block.megaShardMaxPatched, flags,
            immutable(block.data.copyOfRange(0xBB80, 0xBC00).map { it.toInt() and 255 }), immutable(slots),
            immutable(LegendsZaInventoryPocket.values().map { p -> LegendsZaInventoryPocketRead(p,
                flags and (1L shl p.nativeIndex) != 0L, immutable(slots.filter { it.pocket == p })) }))
    }
    private fun u32(data: ByteArray, at: Int): Long =
        (0..3).fold(0L) { result, i -> result or ((data[at + i].toLong() and 255) shl (i * 8)) }
}
private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
