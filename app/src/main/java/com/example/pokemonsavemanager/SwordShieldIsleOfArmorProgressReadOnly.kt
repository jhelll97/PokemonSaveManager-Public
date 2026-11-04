package com.example.pokemonsavemanager

data class SwordShieldIsleOfArmorProgressRead(
    val version: SwordShieldVersion,
    val alolanDiglettFound: Long,
    val dojoDonationProgress: Long,
    val dojoDonationTotal: Long,
    val cramOMaticUses: Long,
    val restrictedSparringTypesCleared: Long
) {
    val dojoDonationStatus: String get() = when {
        dojoDonationProgress == 15L -> "Completed"
        dojoDonationProgress in 0L..14L -> "Stage $dojoDonationProgress of 15"
        else -> "Outside documented 0–15 range"
    }
}

/** Exact read-only projection of PKHeX's documented Sword/Shield Isle of Armor UInt32 records. */
object SwordShieldIsleOfArmorProgressReadOnly {
    fun read(source: ByteArray): SwordShieldIsleOfArmorProgressRead {
        val blocks = SwordShieldSaveValidation.readIsleOfArmorProgressBlocks(source)
        return SwordShieldIsleOfArmorProgressRead(
            blocks.version,
            uint32(blocks.alolanDiglettFound),
            uint32(blocks.dojoDonationProgress),
            uint32(blocks.dojoDonationTotal),
            uint32(blocks.cramOMaticUses),
            uint32(blocks.restrictedSparringTypesCleared)
        )
    }

    private fun uint32(bytes: ByteArray): Long {
        require(bytes.size == 4) { "A native Sword/Shield Isle of Armor record is truncated." }
        return (bytes[0].toLong() and 0xFF) or
            ((bytes[1].toLong() and 0xFF) shl 8) or
            ((bytes[2].toLong() and 0xFF) shl 16) or
            ((bytes[3].toLong() and 0xFF) shl 24)
    }
}
