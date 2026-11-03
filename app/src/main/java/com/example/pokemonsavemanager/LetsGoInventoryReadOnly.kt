package com.example.pokemonsavemanager

import java.util.Collections

enum class LetsGoInventoryPocket(val displayName: String, val offset: Int, val capacity: Int) {
    MEDICINE("Medicine Pocket", 0x0000, 60), TMS("TM Case", 0x00F0, 108),
    CANDY("Candy Jar", 0x02A0, 200), POWER_UP("Power-Up Pocket", 0x05C0, 150),
    CATCHING("Catching Pocket", 0x0818, 50), BATTLE("Battle Pocket", 0x08E0, 150),
    ITEMS("Items and Key Items", 0x0B38, 150)
}
/** Preserve the full word and unused bits; do not rewrite or normalize unusual records. */
data class LetsGoInventorySlotRead(val slot: Int, val rawValue: Long, val itemId: Int, val count: Int,
    val isNew: Boolean, val rawItemField: Int, val rawCountField: Int, val unknownBits: Long,
    val listedInPocket: Boolean, val referenceMaxCount: Int?) {
    val itemName: String get() = LetsGoItemNames.name(itemId)
    val isEmpty: Boolean get() = rawValue == 0L
    val exceedsReferenceMax: Boolean get() = referenceMaxCount?.let { count > it } ?: false
}
class LetsGoInventoryPocketRead internal constructor(val pocket: LetsGoInventoryPocket, val slots: List<LetsGoInventorySlotRead>) {
    val recordedSlots: List<LetsGoInventorySlotRead> get() = Collections.unmodifiableList(slots.filter { !it.isEmpty })
    val recordedCount: Int get() = slots.count { !it.isEmpty }
    fun slot(number: Int): LetsGoInventorySlotRead {
        require(number in 1..slots.size) { "The native inventory slot is outside this pocket." }
        return slots[number - 1]
    }
}
class LetsGoInventoryRead internal constructor(val version: LetsGoVersion, val pockets: List<LetsGoInventoryPocketRead>) {
    val recordedCount: Int get() = pockets.sumOf { it.recordedCount }
    fun pocket(pocket: LetsGoInventoryPocket): LetsGoInventoryPocketRead = pockets.first { it.pocket == pocket }
}

/** Native 0xD90 inventory block after the complete 21-block/title/CRC validation gate. */
object LetsGoInventoryReadOnly {
    private val allowed = mapOf(
        LetsGoInventoryPocket.MEDICINE to setOf(17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 38, 39, 40, 41, 709, 903),
        LetsGoInventoryPocket.TMS to setOf(328, 329, 330, 331, 332, 333, 334, 335, 336, 337, 338, 339, 340, 341, 342, 343, 344, 345, 346, 347, 348, 349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 361, 362, 363, 364, 365, 366, 367, 368, 369, 370, 371, 372, 373, 374, 375, 376, 377, 378, 379, 380, 381, 382, 383, 384, 385, 386, 387),
        LetsGoInventoryPocket.CANDY to setOf(50, 960, 961, 962, 963, 964, 965, 966, 967, 968, 969, 970, 971, 972, 973, 974, 975, 976, 977, 978, 979, 980, 981, 982, 983, 984, 985, 986, 987, 988, 989, 990, 991, 992, 993, 994, 995, 996, 997, 998, 999, 1000, 1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011, 1012, 1013, 1014, 1015, 1016, 1017, 1018, 1019, 1020, 1021, 1022, 1023, 1024, 1025, 1026, 1027, 1028, 1029, 1030, 1031, 1032, 1033, 1034, 1035, 1036, 1037, 1038, 1039, 1040, 1041, 1042, 1043, 1044, 1045, 1046, 1047, 1048, 1049, 1050, 1051, 1052, 1053, 1054, 1055, 1056, 1057),
        LetsGoInventoryPocket.POWER_UP to setOf(51, 53, 81, 82, 83, 84, 85, 849),
        LetsGoInventoryPocket.CATCHING to setOf(1, 2, 3, 4, 12, 164, 166, 168, 861, 862, 863, 864, 865, 866),
        LetsGoInventoryPocket.BATTLE to setOf(55, 56, 57, 58, 59, 60, 61, 62, 656, 659, 660, 661, 662, 663, 671, 672, 675, 676, 678, 679, 760, 762, 770, 773),
        LetsGoInventoryPocket.ITEMS to setOf(76, 77, 78, 79, 86, 87, 88, 89, 90, 91, 92, 93, 101, 102, 103, 113, 115, 121, 122, 123, 124, 125, 126, 127, 128, 442, 571, 632, 651, 795, 796, 872, 873, 874, 875, 876, 877, 878, 885, 886, 887, 888, 889, 890, 891, 892, 893, 894, 895, 896, 900, 901, 902)
    )
    private val keyItems = setOf(113, 115, 121, 122, 123, 124, 125, 126, 127, 128, 442, 632, 651, 872, 873, 874, 875, 876, 877, 878, 885, 886, 887, 888, 889, 890, 891, 892, 893, 894, 895, 896)

    fun allowedItemIds(pocket: LetsGoInventoryPocket): List<Int> =
        allowed.getValue(pocket).sorted()

    fun referenceMaxCount(pocket: LetsGoInventoryPocket, itemId: Int): Int? {
        if (itemId !in allowed.getValue(pocket)) return null
        return when {
            pocket == LetsGoInventoryPocket.TMS -> 1
            pocket == LetsGoInventoryPocket.BATTLE && itemId > 100 -> 1
            pocket == LetsGoInventoryPocket.ITEMS && itemId in keyItems -> 1
            else -> 999
        }
    }

    fun read(source: ByteArray): LetsGoInventoryRead {
        val checked = LetsGoSaveValidation.inspect(source)
        require(checked is LetsGoValidation.Verified) { "A validated native Let's Go inventory save is required." }
        val pockets = LetsGoInventoryPocket.values().map { pocket ->
            val slots = (0 until pocket.capacity).map { i ->
                val raw = u32(source, pocket.offset + i * 4)
                // Use native getter masks, not the broader 15-bit field comments or writer caps.
                val item = (raw and 0x7FF).toInt()
                val count = ((raw ushr 15) and 0x3FF).toInt()
                val listed = item in allowed.getValue(pocket)
                val maximum = referenceMaxCount(pocket, item)
                LetsGoInventorySlotRead(i + 1, raw, item, count, raw and 0x40000000L != 0L,
                    (raw and 0x7FFF).toInt(), ((raw ushr 15) and 0x7FFF).toInt(), raw and 0xBE007800L, listed, maximum)
            }
            LetsGoInventoryPocketRead(pocket, immutable(slots))
        }
        return LetsGoInventoryRead(checked.version, immutable(pockets))
    }
    private fun u32(bytes: ByteArray, offset: Int): Long = (0..3).fold(0L) { n, i -> n or ((bytes[offset + i].toLong() and 255) shl (8 * i)) }
    private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
}
