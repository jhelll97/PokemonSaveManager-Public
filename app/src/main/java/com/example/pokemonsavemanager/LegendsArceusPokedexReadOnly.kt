package com.example.pokemonsavemanager

import java.util.Collections

/** Saved counters only. Slot labels are native indices, not inferred task/move identities. */
data class LegendsArceusDexCounterRead(val label: String, val value: Int)
data class LegendsArceusDexEntryRead(val number: Int, val speciesId: Int, val rawFlags: Long,
    val rawReportedProgress: Long, val rawResearchRate: Int, val numObtained: Int,
    val updateCounter: Int, val lastReportCounter: Int, val selectedForm: Int,
    val counters: List<LegendsArceusDexCounterRead>) {
    val updated: Boolean get() = rawFlags and 1L != 0L
    val reported: Boolean get() = rawFlags and 2L != 0L
    val perfect: Boolean get() = rawFlags and 4L != 0L
    val selectedGender1: Boolean get() = rawFlags and 8L != 0L
    val selectedShiny: Boolean get() = rawFlags and 16L != 0L
    val selectedAlpha: Boolean get() = rawFlags and 32L != 0L
    val solitudeComplete: Boolean get() = rawFlags and 64L != 0L
    val obtained: Boolean get() = numObtained > 0
}
class LegendsArceusPokedexRead internal constructor(val version: LegendsArceusVersion,
    val rawGlobalFlags: Long, val totalResearchPoints: Int, val updateCounter: Int, val reportCounter: Int,
    val entries: List<LegendsArceusDexEntryRead>) {
    val hisuiCompletedFlag: Boolean get() = rawGlobalFlags and 1L != 0L
    val hisuiPerfectFlag: Boolean get() = rawGlobalFlags and 64L != 0L
    val updatedCount: Int get() = entries.count { it.updated }
    val obtainedCount: Int get() = entries.count { it.obtained }
    val reportedCount: Int get() = entries.count { it.reported }
    val perfectCount: Int get() = entries.count { it.perfect }
    fun entry(number: Int): LegendsArceusDexEntryRead {
        require(number in 1..242) { "The Hisui Pokédex number must be 1 through 242." }
        return entries[number - 1]
    }
}

/** Research records after the complete native title/hash/block gate. No task completion is inferred. */
object LegendsArceusPokedexReadOnly {
    // Derived from the pinned personal_la form-aware DexIndexHisui table; evidence in format notes.
    private val hisuiSpecies = intArrayOf(
        722, 723, 724, 155, 156, 157, 501, 502, 503, 399, 400, 396, 397, 398, 403, 404, 405, 265,
        266, 267, 268, 269, 77, 78, 133, 134, 135, 136, 196, 197, 470, 471, 700, 41, 42, 169,
        425, 426, 401, 402, 418, 419, 412, 413, 414, 74, 75, 76, 234, 899, 446, 143, 46, 47,
        172, 25, 26, 63, 64, 65, 390, 391, 392, 427, 428, 420, 421, 54, 55, 415, 416, 123,
        900, 212, 214, 439, 122, 190, 424, 129, 130, 422, 423, 211, 904, 440, 113, 242, 406, 315,
        407, 455, 548, 549, 114, 465, 339, 340, 453, 454, 280, 281, 282, 475, 193, 469, 449, 450,
        417, 434, 435, 216, 217, 901, 704, 705, 706, 95, 208, 111, 112, 464, 438, 185, 108, 463,
        175, 176, 468, 387, 388, 389, 137, 233, 474, 92, 93, 94, 442, 198, 430, 201, 363, 364,
        365, 223, 224, 451, 452, 58, 59, 431, 432, 66, 67, 68, 441, 355, 356, 477, 393, 394,
        395, 458, 226, 550, 902, 37, 38, 72, 73, 456, 457, 240, 126, 467, 81, 82, 462, 436,
        437, 239, 125, 466, 207, 472, 443, 444, 445, 299, 476, 100, 101, 479, 433, 358, 200, 429,
        173, 35, 36, 215, 903, 461, 361, 362, 478, 408, 409, 410, 411, 220, 221, 473, 712, 713,
        459, 460, 570, 571, 627, 628, 447, 448, 480, 481, 482, 485, 486, 488, 641, 642, 645, 905,
        483, 484, 487, 493, 489, 490, 492, 491
    )
    val supportedSpecies: List<Int> = hisuiSpecies.toList()
    private val counterLabels = listOf("Defeated", "Evolved", "Alpha caught", "Large caught", "Small caught",
        "Heavy caught", "Light caught", "Caught at task time", "Caught sleeping", "Caught in air",
        "Caught without being spotted", "Given food", "Stunned with items", "Scared", "Lured",
        "Strong-style moves used", "Agile-style moves used", "Leapt from trees", "Leapt from leaves",
        "Leapt from snow", "Leapt from ore", "Leapt from tussocks")
    fun read(source: ByteArray): LegendsArceusPokedexRead {
        val block = LegendsArceusSaveValidation.readDexBlock(source)
        val bytes = block.data
        val entries = hisuiSpecies.mapIndexed { index, species ->
            // The native array includes species zero; do not use (species - 1) or regional index here.
            val at = 0x70 + species * 0x58
            val counters = (0 until 4).map { LegendsArceusDexCounterRead("Move-use slot ${it + 1}", u16(bytes, at + 0x10 + it * 2)) } +
                (0 until 3).map { LegendsArceusDexCounterRead("Defeat-with-move-type slot ${it + 1}", u16(bytes, at + 0x18 + it * 2)) } +
                counterLabels.mapIndexed { slot, label -> LegendsArceusDexCounterRead(label, u16(bytes, at + 0x1E + slot * 2)) }
            LegendsArceusDexEntryRead(index + 1, species, u32(bytes, at), u32(bytes, at + 4),
                u16(bytes, at + 8), u16(bytes, at + 0xA), u16(bytes, at + 0xC), u16(bytes, at + 0xE),
                bytes[at + 0x50].toInt() and 255, immutable(counters))
        }
        return LegendsArceusPokedexRead(block.version, u32(bytes, 0), u32(bytes, 0xC).toInt(),
            u16(bytes, 6), u16(bytes, 8), immutable(entries))
    }
    private fun u16(bytes: ByteArray, offset: Int): Int = (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long = u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
}
