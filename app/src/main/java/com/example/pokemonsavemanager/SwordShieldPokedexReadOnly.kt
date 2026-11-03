package com.example.pokemonsavemanager

import java.util.Collections

enum class SwordShieldDexRegion(val displayName: String, val entryCount: Int) {
    GALAR("Galar", 400), ARMOR("Isle of Armor", 211), CROWN("Crown Tundra", 210)
}
data class SwordShieldDexEntryRead(val number: Int, val speciesId: Int, val seenBits: List<List<Int>>,
    val caught: Boolean, val caughtGigantamax: Boolean, val languages: List<String>,
    val displayedFormId: Int, val displayedGenderId: Int, val displayGigantamax: Boolean, val displayShiny: Boolean,
    val battledCount: Long, val secondaryGigantamaxFlag: Boolean, val rawFlags: Long, val rawExtra1: Long, val rawExtra2: Long) {
    val seen: Boolean get() = seenBits.any { it.isNotEmpty() }
}
data class SwordShieldDexRegionRead(val region: SwordShieldDexRegion, val entries: List<SwordShieldDexEntryRead>) {
    val seenCount: Int get() = entries.count { it.seen }
    val caughtCount: Int get() = entries.count { it.caught }
    fun entry(number: Int): SwordShieldDexEntryRead {
        require(number in 1..entries.size) { "Regional Pokédex entry is outside the saved table." }
        return entries[number - 1]
    }
}
class SwordShieldPokedexRead internal constructor(val version: SwordShieldVersion, val regions: List<SwordShieldDexRegionRead>) {
    fun region(value: SwordShieldDexRegion): SwordShieldDexRegionRead? = regions.firstOrNull { it.region == value }
}

/** Detached read-only native regional records; raw fields do not establish completion or legality. */
object SwordShieldPokedexReadOnly {
    private val languages = listOf("Japanese", "English", "French", "Italian", "German", "Spanish", "Korean",
        "Chinese (Simplified)", "Chinese (Traditional)")
    fun read(source: ByteArray): SwordShieldPokedexRead {
        val blocks = SwordShieldSaveValidation.readDexBlocks(source)
        // Native revision selection gates Crown behind Armor allocation. Do not guess a revision for an orphan block.
        require(blocks.crown == null || blocks.armor != null) { "The saved Crown Pokédex allocation has no preceding Armor block." }
        fun region(region: SwordShieldDexRegion, bytes: ByteArray): SwordShieldDexRegionRead {
            val entries = (1..region.entryCount).map { number ->
                val offset = (number - 1) * 0x30
                val flags = u32(bytes, offset + 0x20)
                val seen = (0..3).map { group -> immutable((0..63).filter { bit ->
                    val at = offset + group * 8 + bit / 8
                    byte(bytes, at) and (1 shl (bit % 8)) != 0
                }) }
                val extra1 = u32(bytes, offset + 0x28)
                SwordShieldDexEntryRead(number, SwordShieldDexSpecies.species(region, number), immutable(seen),
                    flags and 1L != 0L, flags and 2L != 0L,
                    immutable(languages.filterIndexed { index, _ -> flags and (1L shl (index + 2)) != 0L }),
                    ((flags ushr 15) and 0x1FFF).toInt(), ((flags ushr 29) and 3).toInt(),
                    flags and (1L shl 28) != 0L, flags and (1L shl 31) != 0L,
                    u32(bytes, offset + 0x24), extra1 and 1L != 0L, flags, extra1, u32(bytes, offset + 0x2C))
            }
            return SwordShieldDexRegionRead(region, immutable(entries))
        }
        val regions = mutableListOf(region(SwordShieldDexRegion.GALAR, blocks.galar))
        blocks.armor?.let { regions += region(SwordShieldDexRegion.ARMOR, it) }
        blocks.crown?.let { regions += region(SwordShieldDexRegion.CROWN, it) }
        return SwordShieldPokedexRead(blocks.version, immutable(regions))
    }
    private fun byte(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 255
    private fun u32(bytes: ByteArray, offset: Int): Long = (0..3).fold(0L) { n, i -> n or (byte(bytes, offset + i).toLong() shl (i * 8)) }
    private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
}
