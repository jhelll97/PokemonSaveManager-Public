package com.example.pokemonsavemanager

import java.util.Collections

enum class ScarletVioletDexLayout(val displayName: String) { ORIGINAL("Original Paldea"), DLC("DLC-era form records") }
enum class ScarletVioletDexRegion(val displayName: String) { PALDEA("Paldea"), KITAKAMI("Kitakami"), BLUEBERRY("Blueberry") }
data class ScarletVioletDexDisplayRead(val region: ScarletVioletDexRegion, val formId: Long, val rawGender: Int, val rawShiny: Int)
/** Raw saved fields remain independent; no legality, unlock, reward or completion verdict. */
data class ScarletVioletDexEntryRead(val number: Int, val speciesId: Int, val rawState: Long?,
    val obtainedForms: Long?, val seenForms: Long, val heardForms: Long?, val checkedForms: Long?,
    val genderFlags: Int, val languageFlags: Int, val languages: List<String>, val rawShinyFlags: Int,
    val shinySeen: Boolean, val rawNewFlag: Int?, val rawGenderDifference: Int?, val displays: List<ScarletVioletDexDisplayRead>) {
    val known: Boolean get() = rawState?.let { it != 0L } ?: (heardForms != 0L)
    val seen: Boolean get() = rawState?.let { it >= 2L } ?: (seenForms != 0L)
    val caught: Boolean get() = rawState?.let { it >= 3L } ?: (obtainedForms != 0L)
    val newDisplay: Boolean get() = rawNewFlag?.let { it != 0 } ?: (checkedForms != heardForms)
}
class ScarletVioletDexRegionRead internal constructor(val region: ScarletVioletDexRegion, val entries: List<ScarletVioletDexEntryRead>) {
    val knownCount: Int get() = entries.count { it.known }
    val seenCount: Int get() = entries.count { it.seen }
    val caughtCount: Int get() = entries.count { it.caught }
    fun entry(number: Int): ScarletVioletDexEntryRead {
        require(number in 1..entries.size) { "The regional Pokédex number is outside this table." }
        return entries[number - 1]
    }
}
class ScarletVioletPokedexRead internal constructor(val version: ScarletVioletVersion, val layout: ScarletVioletDexLayout,
    val regions: List<ScarletVioletDexRegionRead>) {
    fun region(region: ScarletVioletDexRegion): ScarletVioletDexRegionRead? = regions.firstOrNull { it.region == region }
}

/** Native internal-species addressing and authoritative DLC block selection after complete validation. */
object ScarletVioletPokedexReadOnly {
    private val languages = listOf("Japanese", "English", "French", "Italian", "German", "Spanish", "Korean",
        "Chinese (Simplified)", "Chinese (Traditional)")
    // Reuse the validated PK9 species permutation in reverse; dex records use internal, not national IDs.
    private val internalSpecies = (1..1025).associateBy { ScarletVioletPokemonReadOnly.nationalSpecies(it) }
    internal fun nativeSpeciesId(speciesId: Int): Int = internalSpecies[speciesId]
        ?: throw IllegalArgumentException("That species is outside the native Scarlet/Violet Pokédex table.")
    private val paldea = intArrayOf(
        906, 907, 908, 909, 910, 911, 912, 913, 914, 915, 916, 917, 918, 919, 920, 187, 188, 189, 661, 662,
        663, 921, 922, 923, 228, 229, 734, 735, 819, 820, 191, 192, 401, 402, 664, 665, 666, 415, 416, 821,
        822, 823, 440, 113, 242, 298, 183, 184, 283, 284, 418, 419, 194, 980, 54, 55, 833, 834, 174, 39,
        40, 280, 281, 282, 475, 96, 97, 92, 93, 94, 924, 925, 172, 25, 26, 926, 927, 287, 288, 289,
        761, 762, 763, 928, 929, 930, 438, 185, 744, 745, 837, 838, 839, 403, 404, 405, 396, 397, 398, 741,
        179, 180, 181, 548, 549, 285, 286, 840, 841, 842, 325, 326, 931, 200, 429, 296, 297, 739, 740, 757,
        758, 231, 232, 878, 879, 443, 444, 445, 932, 933, 934, 278, 279, 129, 130, 846, 847, 550, 316, 317,
        52, 53, 425, 426, 669, 670, 671, 50, 51, 324, 322, 323, 436, 437, 610, 611, 612, 56, 57, 979,
        307, 308, 447, 448, 935, 936, 937, 339, 340, 938, 939, 704, 705, 706, 453, 454, 940, 941, 133, 134,
        135, 136, 196, 197, 470, 471, 700, 206, 982, 585, 586, 203, 981, 88, 89, 942, 943, 848, 849, 702,
        417, 944, 945, 234, 590, 591, 100, 101, 81, 82, 462, 132, 58, 59, 216, 217, 335, 336, 333, 334,
        672, 673, 128, 667, 668, 434, 435, 570, 571, 215, 461, 198, 430, 574, 575, 576, 854, 855, 778, 707,
        876, 946, 947, 948, 949, 357, 753, 754, 950, 951, 952, 331, 332, 953, 954, 48, 49, 204, 205, 123,
        212, 214, 955, 956, 449, 450, 551, 552, 553, 843, 844, 749, 750, 636, 637, 371, 372, 373, 957, 958,
        959, 856, 857, 858, 859, 860, 861, 960, 961, 962, 963, 964, 965, 966, 967, 968, 302, 353, 354, 870,
        701, 442, 714, 715, 885, 886, 887, 969, 970, 479, 971, 972, 765, 766, 775, 246, 247, 248, 874, 875,
        871, 769, 770, 79, 80, 199, 422, 423, 90, 91, 211, 370, 456, 457, 779, 594, 690, 691, 692, 693,
        602, 603, 604, 747, 748, 973, 147, 148, 149, 872, 873, 459, 460, 225, 613, 614, 361, 362, 478, 615,
        974, 975, 712, 713, 627, 628, 624, 625, 983, 633, 634, 635, 976, 977, 978, 984, 985, 986, 987, 988,
        989, 990, 991, 992, 993, 994, 995, 996, 997, 998, 999, 1000, 1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008
    )
    private val kitakami = intArrayOf(
        167, 168, 193, 469, 194, 195, 261, 262, 313, 314, 341, 342, 540, 541, 542, 742, 743, 23, 24, 172,
        25, 26, 69, 70, 71, 161, 162, 396, 397, 398, 753, 754, 840, 841, 842, 1011, 37, 38, 60, 61,
        62, 186, 129, 130, 163, 164, 190, 424, 214, 220, 221, 473, 234, 273, 274, 275, 280, 281, 282, 475,
        401, 402, 417, 447, 448, 548, 549, 708, 709, 744, 745, 819, 820, 948, 949, 1012, 1013, 58, 59, 74,
        75, 76, 438, 185, 532, 533, 534, 714, 715, 846, 847, 856, 857, 858, 877, 968, 924, 925, 56, 57,
        979, 446, 143, 270, 271, 272, 299, 476, 403, 404, 405, 736, 737, 738, 741, 27, 28, 92, 93, 94,
        207, 472, 228, 229, 325, 326, 629, 630, 749, 750, 782, 783, 784, 962, 109, 110, 619, 620, 355, 356,
        477, 433, 358, 218, 219, 607, 608, 609, 283, 284, 173, 35, 36, 436, 437, 969, 970, 349, 350, 206,
        982, 339, 340, 443, 444, 445, 703, 757, 758, 215, 461, 361, 362, 478, 602, 603, 604, 704, 705, 706,
        580, 581, 833, 834, 845, 624, 625, 983, 778, 859, 860, 861, 876, 550, 902, 901, 1014, 1015, 1016, 1017
    )
    private val blueberry = intArrayOf(
        84, 85, 102, 103, 111, 112, 464, 48, 49, 239, 125, 466, 240, 126, 467, 440, 113, 242, 123, 212,
        900, 128, 522, 523, 203, 981, 551, 552, 553, 953, 954, 627, 628, 629, 630, 667, 668, 585, 586, 235,
        479, 868, 869, 328, 329, 330, 731, 732, 733, 72, 73, 116, 117, 230, 779, 546, 547, 764, 287, 288,
        289, 43, 44, 45, 182, 50, 51, 88, 89, 335, 336, 739, 740, 741, 79, 80, 199, 170, 171, 686,
        687, 370, 456, 457, 594, 324, 661, 662, 663, 751, 752, 236, 106, 107, 237, 74, 75, 76, 529, 530,
        574, 575, 576, 677, 678, 774, 408, 409, 410, 411, 572, 573, 227, 333, 334, 81, 82, 462, 311, 312,
        559, 560, 622, 623, 322, 323, 854, 855, 137, 233, 474, 595, 596, 602, 603, 604, 374, 375, 376, 610,
        611, 612, 86, 87, 131, 211, 904, 577, 578, 579, 209, 210, 613, 614, 27, 28, 37, 38, 459, 460,
        884, 1018, 1019, 1, 2, 3, 4, 5, 6, 7, 8, 9, 152, 153, 154, 155, 156, 157, 158, 159,
        160, 252, 253, 254, 255, 256, 257, 258, 259, 260, 387, 388, 389, 390, 391, 392, 393, 394, 395, 495,
        496, 497, 498, 499, 500, 501, 502, 503, 650, 651, 652, 653, 654, 655, 656, 657, 658, 722, 723, 724,
        725, 726, 727, 728, 729, 730, 810, 811, 812, 813, 814, 815, 816, 817, 818, 1020, 1021, 1023, 1022, 1024,
        1009, 1010, 1025
    )
    fun read(source: ByteArray): ScarletVioletPokedexRead {
        val blocks = ScarletVioletSaveValidation.readDexBlocks(source)
        val modern = blocks.dlc != null
        val bytes = blocks.dlc ?: blocks.original
        val layout = if (modern) ScarletVioletDexLayout.DLC else ScarletVioletDexLayout.ORIGINAL
        val maps = if (modern) listOf(paldea, kitakami, blueberry) else listOf(paldea)
        val regions = maps.mapIndexed { index, speciesList ->
            val region = ScarletVioletDexRegion.values()[index]
            val entries = speciesList.mapIndexed { i, species ->
                val at = internalSpecies.getValue(species) * (if (modern) 0x20 else 0x18)
                val language = u16(bytes, at + if (modern) 0x10 else 0xA)
                val displays = if (modern) ScarletVioletDexRegion.values().mapIndexed { n, area ->
                    val offset = at + 0x14 + n * 4
                    ScarletVioletDexDisplayRead(area, byte(bytes, offset).toLong(), byte(bytes, offset + 1), byte(bytes, offset + 2))
                } else listOf(ScarletVioletDexDisplayRead(ScarletVioletDexRegion.PALDEA, u32(bytes, at + 0x10), byte(bytes, at + 0x14), byte(bytes, at + 0x15)))
                ScarletVioletDexEntryRead(i + 1, species, if (modern) null else u32(bytes, at),
                    if (modern) u32(bytes, at) else null, u32(bytes, at + 4),
                    if (modern) u32(bytes, at + 8) else null, if (modern) u32(bytes, at + 0xC) else null,
                    if (modern) byte(bytes, at + 0x12) else u16(bytes, at + 8), language,
                    immutable(languages.filterIndexed { bit, _ -> language and (1 shl bit) != 0 }),
                    byte(bytes, at + if (modern) 0x13 else 0xC),
                    if (modern) byte(bytes, at + 0x13) and 2 != 0 else byte(bytes, at + 0xC) != 0,
                    if (modern) null else byte(bytes, at + 0xD), if (modern) null else byte(bytes, at + 0x16), immutable(displays))
            }
            ScarletVioletDexRegionRead(region, immutable(entries))
        }
        return ScarletVioletPokedexRead(blocks.version, layout, immutable(regions))
    }
    private fun byte(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 255
    private fun u16(bytes: ByteArray, offset: Int): Int = byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long = u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
}
