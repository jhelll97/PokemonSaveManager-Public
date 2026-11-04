package com.example.pokemonsavemanager

data class SwordShieldBattleTowerRead(
    val version: SwordShieldVersion,
    val singlesVictories: Long,
    val doublesVictories: Long,
    val singlesStreak: Int,
    val doublesStreak: Int
) {
    val victoriesWithinDocumentedRange: Boolean
        get() = singlesVictories <= 9_999_999L && doublesVictories <= 9_999_999L
    val streaksWithinDocumentedRange: Boolean
        get() = singlesStreak <= 300 && doublesStreak <= 300
}

/** Exact read-only projection of PKHeX's documented Sword/Shield Battle Tower scalar blocks. */
object SwordShieldBattleTowerReadOnly {
    fun read(source: ByteArray): SwordShieldBattleTowerRead {
        val blocks = SwordShieldSaveValidation.readBattleTowerBlocks(source)
        return SwordShieldBattleTowerRead(
            blocks.version,
            uint32(blocks.singlesVictories),
            uint32(blocks.doublesVictories),
            uint16(blocks.singlesStreak),
            uint16(blocks.doublesStreak)
        )
    }

    private fun uint16(bytes: ByteArray): Int =
        (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)

    private fun uint32(bytes: ByteArray): Long =
        (bytes[0].toLong() and 0xFF) or
            ((bytes[1].toLong() and 0xFF) shl 8) or
            ((bytes[2].toLong() and 0xFF) shl 16) or
            ((bytes[3].toLong() and 0xFF) shl 24)
}
