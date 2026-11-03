package com.example.pokemonsavemanager

enum class Gen3SootAshProfile(val saveBlockOffset: Int) {
    RUBY_SAPPHIRE(0x13D0), EMERALD(0x142C);
    val sectionOffset: Int get() = saveBlockOffset - 0xF80
}

/** Hoenn's VAR_ASH_GATHER_COUNT (0x4048), in the second SaveBlock1 section (ID 2). */
object Gen3SootAshEditing {
    // pret/{pokeruby,pokeemerald}: global.h vars +0x1340/+0x139C; vars.h VAR_ASH_GATHER_COUNT.
    // src/field_tasks.c caps the unencrypted u16 counter at 9999.
    const val SECTION_ID = 2

    fun read(section2: ByteArray, profile: Gen3SootAshProfile): Int {
        require(section2.size == 0xF80) { "Use a complete validated Hoenn save section 2." }
        val start = profile.sectionOffset
        val ash = (section2[start].toInt() and 255) or ((section2[start + 1].toInt() and 255) shl 8)
        require(ash in 0..9999) { "The loaded Soot Sack ash count is invalid for this game profile." }
        return ash
    }

    fun replace(section2: ByteArray, profile: Gen3SootAshProfile, ash: Int): ByteArray {
        read(section2, profile)
        require(ash in 0..9999) { "Choose an ash count from 0 to 9999." }
        val start = profile.sectionOffset
        val result = section2.copyOf()
        result[start] = ash.toByte()
        result[start + 1] = (ash ushr 8).toByte()
        require(read(result, profile) == ash && result.indices.all {
            it in start..start + 1 || result[it] == section2[it]
        }) { "The ash edit changed a neighbouring variable or unrelated save field." }
        return result
    }
}
