package com.example.pokemonsavemanager

import java.util.Collections

/** Raw saved size scalars; no conversion to absolute height/weight or record normalization. */
data class LetsGoDexSizeRead(val group: String, val heightScalar: Int, val weightScalar: Int, val flagByte: Int) {
    val isRecorded: Boolean get() = heightScalar != 0xFE || weightScalar != 0x7F
    val isFlagged: Boolean get() = flagByte == 1
}
data class LetsGoDexFormRead(val formId: Int, val sizeRecords: List<LetsGoDexSizeRead>)
data class LetsGoDexEntryRead(val speciesId: Int, val caught: Boolean, val seenVariants: List<Boolean>,
    val displayedVariants: List<Boolean>, val languages: List<String>, val forms: List<LetsGoDexFormRead>) {
    val seen: Boolean get() = seenVariants.any { it }
}
class LetsGoPokedexRead internal constructor(val version: LetsGoVersion, val nationalDexFlag: Boolean,
    val currentViewedSpecies: Int, val entries: List<LetsGoDexEntryRead>) {
    val seenCount: Int get() = entries.count { it.seen }
    val caughtCount: Int get() = entries.count { it.caught }
    val kantoSeenCount: Int get() = entries.count { it.speciesId <= 151 && it.seen }
    val kantoCaughtCount: Int get() = entries.count { it.speciesId <= 151 && it.caught }
    fun entry(speciesId: Int): LetsGoDexEntryRead = entries.firstOrNull { it.speciesId == speciesId }
        ?: throw IllegalArgumentException("This species is outside the supported Let's Go Pokédex list.")
}

/** Native flags and all 186 size-record mappings after complete 21-block validation. */
object LetsGoPokedexReadOnly {
    private const val DEX = 0x2A00
    private val languages = listOf("Japanese", "English", "French", "Italian", "German", "Spanish", "Korean",
        "Chinese (Simplified)", "Chinese (Traditional)")
    private val groups = listOf("Minimum height", "Maximum height", "Minimum weight", "Maximum weight")
    // Native Zukan7b size table order; these numeric forms are deliberately not named or conflated with flag-form bits.
    private val sizeForms = listOf(3 to 1, 6 to 1, 6 to 2, 9 to 1, 15 to 1, 18 to 1, 19 to 1,
        20 to 1, 26 to 1, 27 to 1, 28 to 1, 37 to 1, 38 to 1, 50 to 1, 51 to 1, 52 to 1,
        53 to 1, 65 to 1, 74 to 1, 75 to 1, 76 to 1, 80 to 1, 88 to 1, 89 to 1, 94 to 1,
        103 to 1, 105 to 1, 115 to 1, 127 to 1, 130 to 1, 142 to 1, 150 to 1, 150 to 2)

    fun read(source: ByteArray): LetsGoPokedexRead {
        val checked = LetsGoSaveValidation.inspect(source)
        require(checked is LetsGoValidation.Verified) { "A validated native Let's Go save is required." }
        require(u32(source, DEX) == 0x2F120F17L) { "The saved Let's Go Pokédex marker is not supported." }
        fun flag(offset: Int, bit: Int): Boolean = byte(source, DEX + offset + (bit ushr 3)) and (1 shl (bit and 7)) != 0
        fun sizes(index: Int): List<LetsGoDexSizeRead> = immutable(groups.mapIndexed { group, label ->
            val offset = DEX + 0xF78 + 6 * (index + group * 186)
            LetsGoDexSizeRead(label, byte(source, offset), byte(source, offset + 2), byte(source, offset + 1))
        })
        val species = (1..151).toList() + listOf(808, 809)
        val entries = species.map { id ->
            val bit = id - 1
            val baseSize = if (id <= 151) bit else 151 + id - 808
            val forms = listOf(LetsGoDexFormRead(0, sizes(baseSize))) + sizeForms.mapIndexedNotNull { index, pair ->
                if (pair.first == id) LetsGoDexFormRead(pair.second, sizes(153 + index)) else null
            }
            LetsGoDexEntryRead(id, flag(0x88, bit), immutable((0..3).map { flag(0xF0 + it * 0x8C, bit) }),
                immutable((0..3).map { flag(0xF0 + (it + 4) * 0x8C, bit) }),
                immutable(languages.filterIndexed { index, _ -> flag(0x550, bit * 9 + index) }), immutable(forms))
        }
        return LetsGoPokedexRead(checked.version, byte(source, DEX + 4) and 1 != 0,
            ((u32(source, DEX + 4) ushr 9) and 0x3FF).toInt(), immutable(entries))
    }
    private fun byte(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 255
    private fun u32(bytes: ByteArray, offset: Int): Long = (0..3).fold(0L) { n, i -> n or (byte(bytes, offset + i).toLong() shl (i * 8)) }
    private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
}
