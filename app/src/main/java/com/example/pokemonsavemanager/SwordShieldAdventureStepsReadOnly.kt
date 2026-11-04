package com.example.pokemonsavemanager

data class SwordShieldAdventureStepsRead(
    val version: SwordShieldVersion,
    val bikeBoostChargeSteps: Long,
    val bikeBoostChargeLimit: Long,
    val eggHatchCycleSteps: Int,
    val friendshipWalkSteps: Int
) {
    val bikeBoostStatus: String get() =
        if (bikeBoostChargeSteps == bikeBoostChargeLimit) "Fully charged" else "Counter does not match charge limit"

    fun cycleStatus(value: Int): String =
        if (value in 0..127) "Within documented 0–127 range" else "Outside documented 0–127 range"
}

/** Exact read-only projection of PKHeX's documented Sword/Shield adventure step records. */
object SwordShieldAdventureStepsReadOnly {
    fun read(source: ByteArray): SwordShieldAdventureStepsRead {
        val blocks = SwordShieldSaveValidation.readAdventureStepBlocks(source)
        return SwordShieldAdventureStepsRead(
            blocks.version,
            uint32(blocks.bikeBoostChargeSteps),
            uint32(blocks.bikeBoostChargeLimit),
            uint16(blocks.eggHatchCycleSteps),
            uint8(blocks.friendshipWalkSteps)
        )
    }

    private fun uint32(bytes: ByteArray): Long {
        require(bytes.size == 4) { "A native Sword/Shield UInt32 adventure step record is truncated." }
        return (bytes[0].toLong() and 0xFF) or
            ((bytes[1].toLong() and 0xFF) shl 8) or
            ((bytes[2].toLong() and 0xFF) shl 16) or
            ((bytes[3].toLong() and 0xFF) shl 24)
    }

    private fun uint16(bytes: ByteArray): Int {
        require(bytes.size == 2) { "A native Sword/Shield UInt16 adventure step record is truncated." }
        return (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
    }

    private fun uint8(bytes: ByteArray): Int {
        require(bytes.size == 1) { "A native Sword/Shield UInt8 adventure step record is truncated." }
        return bytes[0].toInt() and 0xFF
    }
}
