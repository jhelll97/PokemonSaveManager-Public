package com.example.pokemonsavemanager

enum class Gen3CoinProfile(val coinOffset: Int, val keyOffset: Int?) {
    RUBY_SAPPHIRE(0x494, null), EMERALD(0x494, 0xAC), FIRERED_LEAFGREEN(0x294, 0xF20)
}

/** Native Coin Case balance; encrypted profiles use the low 16 bits of their existing key. */
object Gen3CoinEditing {
    // Layouts: PKHeX.Core/Saves/Blocks/Gen3/SaveBlock3{Large,Small}{RS,E,FRLG}.cs
    // Encryption: pret/{pokeruby,pokeemerald,pokefirered}/src/coins.c, GetCoins/SetCoins.
    private fun readU16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 255) or ((data[offset + 1].toInt() and 255) shl 8)

    fun read(section0: ByteArray, section1: ByteArray, profile: Gen3CoinProfile): Int {
        require(section0.size == 0xF2C && section1.size == 0xF80) {
            "Use complete validated Gen 3 trainer and SaveBlock1 first sections."
        }
        val key = profile.keyOffset?.let { readU16(section0, it) } ?: 0
        val coins = readU16(section1, profile.coinOffset) xor key
        require(coins in 0..9999) { "The loaded Coin Case balance is invalid for this game profile." }
        return coins
    }

    fun replace(section0: ByteArray, section1: ByteArray, profile: Gen3CoinProfile, coins: Int): ByteArray {
        read(section0, section1, profile)
        require(coins in 0..9999) { "Choose a Coin Case balance from 0 to 9999." }
        val key = profile.keyOffset?.let { readU16(section0, it) } ?: 0
        val stored = coins xor key
        val result = section1.copyOf()
        result[profile.coinOffset] = stored.toByte()
        result[profile.coinOffset + 1] = (stored ushr 8).toByte()
        require(read(section0, result, profile) == coins && result.indices.all {
            it in profile.coinOffset..profile.coinOffset + 1 || result[it] == section1[it]
        }) { "The coin edit changed an unrelated save field." }
        return result
    }
}
