package com.example.pokemonsavemanager

enum class Gen3BerryPowderProfile(val powderOffset: Int, val keyOffset: Int) {
    EMERALD(0x1F4, 0xAC), FIRERED_LEAFGREEN(0xAF8, 0xF20)
}

/** Berry Powder uses a full unsigned 32-bit XOR key, unlike Game Corner Coins. */
object Gen3BerryPowderEditing {
    // pret/{pokeemerald,pokefirered}/src/berry_powder.c: XOR key and MAX_BERRY_POWDER 99999.
    // PKHeX.Core/Saves/Blocks/Gen3/SaveBlock3Small{E,FRLG}.cs: BerryPowder/SecurityKey offsets.
    private fun readU32(data: ByteArray, offset: Int): Long =
        (0..3).fold(0L) { value, index -> value or ((data[offset + index].toLong() and 255L) shl (index * 8)) }

    fun read(section0: ByteArray, profile: Gen3BerryPowderProfile): Int {
        require(section0.size == 0xF2C) { "Use a complete validated Gen 3 trainer section." }
        val powder = readU32(section0, profile.powderOffset) xor readU32(section0, profile.keyOffset)
        require(powder in 0L..99999L) { "The loaded Berry Powder amount is invalid for this game profile." }
        return powder.toInt()
    }

    fun replace(section0: ByteArray, profile: Gen3BerryPowderProfile, powder: Int): ByteArray {
        read(section0, profile)
        require(powder in 0..99999) { "Choose a Berry Powder amount from 0 to 99999." }
        val stored = powder.toLong() xor readU32(section0, profile.keyOffset)
        val result = section0.copyOf()
        for (index in 0..3) result[profile.powderOffset + index] = (stored ushr (index * 8)).toByte()
        require(read(result, profile) == powder && result.indices.all {
            it in profile.powderOffset..profile.powderOffset + 3 || result[it] == section0[it]
        }) { "The Berry Powder edit changed its encryption key or unrelated trainer data." }
        return result
    }
}
