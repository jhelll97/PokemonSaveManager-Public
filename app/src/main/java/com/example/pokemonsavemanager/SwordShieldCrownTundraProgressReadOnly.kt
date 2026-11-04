package com.example.pokemonsavemanager

data class SwordShieldCrownTundraProgressRead(
    val version: SwordShieldVersion,
    val regiPattern: Long,
    val cobalionFootprints: Long,
    val terrakionFootprints: Long,
    val virizionFootprints: Long,
    val notedSpecies: List<Long>,
    val endlessStreak: Long,
    val disconnectStreak: Long,
    val peoniaSpeciesHint: Long
) {
    val regiPatternStatus: String get() = when (regiPattern) {
        0L -> "Not chosen"
        1L -> "Electric pattern • Regieleki"
        2L -> "Dragon pattern • Regidrago"
        else -> "Outside documented 0–2 range"
    }

    fun footprintStatus(value: Long): String =
        if (value in 0L..100L) "$value%" else "Outside documented 0–100 range"

    val disconnectStatus: String get() =
        if (disconnectStreak == 3L) "Dynite Ore fee threshold" else "Exact native value"
}

/** Exact read-only projection of PKHeX's documented Sword/Shield Crown Tundra UInt32 records. */
object SwordShieldCrownTundraProgressReadOnly {
    fun read(source: ByteArray): SwordShieldCrownTundraProgressRead {
        val blocks = SwordShieldSaveValidation.readCrownTundraProgressBlocks(source)
        return SwordShieldCrownTundraProgressRead(
            blocks.version,
            uint32(blocks.regiPattern),
            uint32(blocks.cobalionFootprints),
            uint32(blocks.terrakionFootprints),
            uint32(blocks.virizionFootprints),
            listOf(uint32(blocks.notedSpecies1), uint32(blocks.notedSpecies2), uint32(blocks.notedSpecies3)),
            uint32(blocks.endlessStreak),
            uint32(blocks.disconnectStreak),
            uint32(blocks.peoniaSpeciesHint)
        )
    }

    private fun uint32(bytes: ByteArray): Long {
        require(bytes.size == 4) { "A native Sword/Shield Crown Tundra record is truncated." }
        return (bytes[0].toLong() and 0xFF) or
            ((bytes[1].toLong() and 0xFF) shl 8) or
            ((bytes[2].toLong() and 0xFF) shl 16) or
            ((bytes[3].toLong() and 0xFF) shl 24)
    }
}
