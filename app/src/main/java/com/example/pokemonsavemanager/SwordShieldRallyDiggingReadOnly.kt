package com.example.pokemonsavemanager

data class SwordShieldRallyDiggingRead(
    val version: SwordShieldVersion,
    val rotoRallyScore: Long,
    val diggingDuoSkillStreak: Long,
    val diggingDuoStaminaStreak: Long,
    val diggingPaWatts: Long
) {
    val rotoRallyWithinDocumentedRange: Boolean get() = rotoRallyScore <= 99_999L
}

/** Exact read-only projection of PKHeX's documented Sword/Shield rally and digging UInt32 records. */
object SwordShieldRallyDiggingReadOnly {
    fun read(source: ByteArray): SwordShieldRallyDiggingRead {
        val blocks = SwordShieldSaveValidation.readRallyDiggingBlocks(source)
        return SwordShieldRallyDiggingRead(
            blocks.version,
            uint32(blocks.rotoRallyScore),
            uint32(blocks.diggingDuoSkillStreak),
            uint32(blocks.diggingDuoStaminaStreak),
            uint32(blocks.diggingPaWatts)
        )
    }

    private fun uint32(bytes: ByteArray): Long {
        require(bytes.size == 4) { "A native Sword/Shield UInt32 record is truncated." }
        return (bytes[0].toLong() and 0xFF) or
            ((bytes[1].toLong() and 0xFF) shl 8) or
            ((bytes[2].toLong() and 0xFF) shl 16) or
            ((bytes[3].toLong() and 0xFF) shl 24)
    }
}
