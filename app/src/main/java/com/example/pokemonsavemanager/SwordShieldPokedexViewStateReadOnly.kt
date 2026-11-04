package com.example.pokemonsavemanager

data class SwordShieldPokedexViewStateRead(
    val version: SwordShieldVersion,
    val currentDex: Long,
    val galarEntry: Int,
    val armorEntry: Int,
    val crownEntry: Int
) {
    val currentDexLabel: String get() = when (currentDex) {
        0L -> "Galar Pokédex"
        1L -> "Isle of Armor Pokédex"
        2L -> "Crown Tundra Pokédex"
        else -> "Unrecognized native selector"
    }

    fun speciesLabel(value: Int): String =
        if (value in 1..898) "$value • ${SwordShieldSpeciesNames.name(value)}" else value.toString()
}

/** Exact read-only projection of PKHeX's documented Sword/Shield Pokédex view state. */
object SwordShieldPokedexViewStateReadOnly {
    fun read(source: ByteArray): SwordShieldPokedexViewStateRead {
        val blocks = SwordShieldSaveValidation.readPokedexViewStateBlocks(source)
        return SwordShieldPokedexViewStateRead(
            blocks.version,
            uint32(blocks.currentDex),
            uint16(blocks.galarEntry),
            uint16(blocks.armorEntry),
            uint16(blocks.crownEntry)
        )
    }

    private fun uint32(bytes: ByteArray): Long {
        require(bytes.size == 4) { "A native Sword/Shield UInt32 Pokédex selector is truncated." }
        return (bytes[0].toLong() and 0xFF) or
            ((bytes[1].toLong() and 0xFF) shl 8) or
            ((bytes[2].toLong() and 0xFF) shl 16) or
            ((bytes[3].toLong() and 0xFF) shl 24)
    }

    private fun uint16(bytes: ByteArray): Int {
        require(bytes.size == 2) { "A native Sword/Shield UInt16 Pokédex entry is truncated." }
        return (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
    }
}
