package com.example.pokemonsavemanager

import java.util.Collections

enum class LegendsZaDexSection(val displayName: String) { LUMIOSE("Lumiose"), HYPERSPACE("Hyperspace") }
/** Independent raw saved flags; no source arrays, save handles or write operations. */
data class LegendsZaDexEntryRead(val number: Int, val speciesId: Int, val caughtForms: Long, val seenForms: Long,
    val shinyForms: Long, val languageFlags: Int, val languages: List<String>, val rawNewFlag: Int,
    val genderFlags: Int, val megaFlags: Int, val rawAlphaFlag: Int, val displayForm: Int,
    val rawDisplayGender: Int, val rawDisplayShiny: Int) {
    val caught: Boolean get() = caughtForms != 0L
    val seen: Boolean get() = seenForms != 0L
    val shinyRecorded: Boolean get() = shinyForms != 0L
    val alphaSeen: Boolean get() = rawAlphaFlag != 0
    val newDisplay: Boolean get() = rawNewFlag != 0
    val displayShiny: Boolean get() = rawDisplayShiny != 0
    val displayGender: String get() = when (rawDisplayGender) {
        0 -> "Genderless"; 1 -> "Male"; 2 -> "Female"; 3 -> "Gendered, no display difference"; else -> "Unknown"
    }
}
class LegendsZaDexSectionRead internal constructor(val section: LegendsZaDexSection, val entries: List<LegendsZaDexEntryRead>) {
    val seenCount: Int get() = entries.count { it.seen }
    val caughtCount: Int get() = entries.count { it.caught }
    fun entry(number: Int): LegendsZaDexEntryRead = entries.firstOrNull { it.number == number }
        ?: throw IllegalArgumentException("The native Pokédex number is outside this section.")
}
class LegendsZaPokedexRead internal constructor(val version: LegendsZaVersion, val revision: LegendsZaRevision?,
    val sections: List<LegendsZaDexSectionRead>) {
    fun section(section: LegendsZaDexSection): LegendsZaDexSectionRead? = sections.firstOrNull { it.section == section }
}

/** Complete native gate, internal Gen 9 species indexing and revision-aware record access. */
object LegendsZaPokedexReadOnly {
    private val languages = listOf("Japanese", "English", "French", "Italian", "German", "Spanish", "Korean",
        "Chinese (Simplified)", "Chinese (Traditional)", "Spanish (Latin America)")
    private val internalSpecies = (1..1025).associateBy { LegendsZaPokemonReadOnly.nationalSpecies(it) }
    // Only present Z-A species <= its native DLC ceiling 1000; ignore inherited SV personal rows.
    // Form-aware native DexIndex order, verified 1..364. Lumiose ends at 232.
    private val speciesOrder = intArrayOf(
        152, 153, 154, 498, 499, 500, 158, 159, 160, 661, 662, 663, 659, 660, 664, 665, 666, 13,
        14, 15, 16, 17, 18, 179, 180, 181, 504, 505, 406, 315, 407, 129, 130, 688, 689, 120,
        121, 669, 670, 671, 672, 673, 677, 678, 667, 668, 674, 675, 568, 569, 702, 172, 25, 26,
        173, 35, 36, 167, 168, 23, 24, 63, 64, 65, 92, 93, 94, 543, 544, 545, 679, 680,
        681, 69, 70, 71, 511, 512, 513, 514, 515, 516, 307, 308, 309, 310, 280, 281, 282, 475,
        228, 229, 333, 334, 531, 682, 683, 684, 685, 133, 134, 135, 136, 196, 197, 470, 471, 700,
        427, 428, 353, 354, 582, 583, 584, 322, 323, 449, 450, 529, 530, 551, 552, 553, 66, 67,
        68, 443, 444, 445, 703, 302, 303, 359, 447, 448, 79, 80, 199, 318, 319, 602, 603, 604,
        147, 148, 149, 1, 2, 3, 4, 5, 6, 7, 8, 9, 618, 676, 686, 687, 690, 691,
        692, 693, 704, 705, 706, 225, 361, 362, 478, 459, 460, 712, 713, 123, 212, 127, 214, 587,
        701, 708, 709, 559, 560, 714, 715, 707, 607, 608, 609, 142, 696, 697, 698, 699, 95, 208,
        304, 305, 306, 694, 695, 710, 711, 246, 247, 248, 656, 657, 658, 870, 650, 651, 652, 227,
        653, 654, 655, 371, 372, 373, 115, 780, 374, 375, 376, 716, 717, 718, 719, 150, 56, 57,
        979, 52, 53, 863, 83, 865, 104, 105, 137, 233, 474, 951, 952, 957, 958, 959, 967, 969,
        970, 479, 971, 972, 769, 770, 352, 973, 615, 977, 978, 996, 997, 998, 999, 1000, 211, 904,
        252, 253, 254, 255, 256, 257, 258, 259, 260, 349, 350, 433, 358, 876, 509, 510, 517, 518,
        538, 539, 562, 563, 867, 767, 768, 827, 828, 852, 853, 778, 900, 877, 622, 623, 821, 822,
        823, 174, 39, 40, 926, 927, 396, 397, 398, 325, 326, 931, 739, 740, 932, 933, 934, 316,
        317, 41, 42, 169, 935, 936, 937, 942, 943, 848, 849, 944, 945, 335, 336, 439, 122, 866,
        590, 591, 485, 721, 638, 639, 640, 647, 648, 649, 720, 802, 808, 809, 491, 380, 381, 382,
        383, 384, 801, 807
    )
    fun read(source: ByteArray): LegendsZaPokedexRead {
        val block = LegendsZaSaveValidation.readDexBlock(source)
        val bytes = block.data
        val hasDlcRevision = block.revision == LegendsZaRevision.MEGA_DIMENSION || block.revision == LegendsZaRevision.END_OF_LIFE
        val sections = (if (hasDlcRevision) LegendsZaDexSection.values().toList() else listOf(LegendsZaDexSection.LUMIOSE)).map { section ->
            val numbers = if (section == LegendsZaDexSection.LUMIOSE) 1..232 else 233..364
            val entries = numbers.map { number ->
                val species = speciesOrder[number - 1]
                val at = internalSpecies.getValue(species) * 0x84 // Includes the native unused species-zero slot.
                val language = u16(bytes, at + 8)
                LegendsZaDexEntryRead(number, species, u32(bytes, at), u32(bytes, at + 4), u32(bytes, at + 0xC),
                    language, immutable(languages.filterIndexed { bit, _ -> language and (1 shl bit) != 0 }),
                    byte(bytes, at + 0xA), byte(bytes, at + 0xB), byte(bytes, at + 0x10), byte(bytes, at + 0x11),
                    byte(bytes, at + 0x5A), byte(bytes, at + 0x5B), byte(bytes, at + 0x5C))
            }
            LegendsZaDexSectionRead(section, immutable(entries))
        }
        return LegendsZaPokedexRead(block.version, block.revision, immutable(sections))
    }
    private fun byte(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 255
    private fun u16(bytes: ByteArray, offset: Int): Int = byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long = u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
}
