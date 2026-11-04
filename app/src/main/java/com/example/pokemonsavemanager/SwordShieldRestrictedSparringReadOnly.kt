package com.example.pokemonsavemanager

data class SwordShieldRestrictedSparringTypeRead(val typeName: String, val bestStreak: Long)

data class SwordShieldRestrictedSparringRead(
    val version: SwordShieldVersion,
    val types: List<SwordShieldRestrictedSparringTypeRead>
)

/** Exact read-only projection of PKHeX's documented Sword/Shield Restricted Sparring best streaks. */
object SwordShieldRestrictedSparringReadOnly {
    private val typeNames = listOf(
        "Normal", "Fire", "Water", "Electric", "Grass", "Ice", "Fighting", "Poison", "Ground",
        "Flying", "Psychic", "Bug", "Rock", "Ghost", "Dragon", "Dark", "Steel", "Fairy"
    )

    fun read(source: ByteArray): SwordShieldRestrictedSparringRead {
        val blocks = SwordShieldSaveValidation.readRestrictedSparringBlocks(source)
        require(blocks.streaks.size == typeNames.size) { "The native Restricted Sparring streak set is incomplete." }
        return SwordShieldRestrictedSparringRead(
            blocks.version,
            typeNames.indices.map { index ->
                SwordShieldRestrictedSparringTypeRead(typeNames[index], uint32(blocks.streaks[index]))
            }
        )
    }

    private fun uint32(bytes: ByteArray): Long {
        require(bytes.size == 4) { "A native Sword/Shield UInt32 Restricted Sparring streak is truncated." }
        return (bytes[0].toLong() and 0xFF) or
            ((bytes[1].toLong() and 0xFF) shl 8) or
            ((bytes[2].toLong() and 0xFF) shl 16) or
            ((bytes[3].toLong() and 0xFF) shl 24)
    }
}
