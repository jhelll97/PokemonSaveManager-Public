package com.example.pokemonsavemanager

data class LetsGoMasterTrainerTitlesRead(
    val version: LetsGoVersion,
    val unlockedTitleIds: List<Int>
) {
    val totalTitles: Int get() = LetsGoMasterTrainerTitlesReadOnly.TITLE_COUNT
    val unlockedCount: Int get() = unlockedTitleIds.size
    fun isUnlocked(id: Int): Boolean = id in unlockedTitleIds
}

/** Reads the 156 native title flags documented in EventWork block 3. */
object LetsGoMasterTrainerTitlesReadOnly {
    internal const val TITLE_COUNT = 156
    internal const val EVENT_WORK_OFFSET = 0x01200
    internal const val EVENT_WORK_LENGTH = 0x01800
    internal const val TITLE_FLAGS_OFFSET = EVENT_WORK_OFFSET + 0x1298

    fun read(bytes: ByteArray): LetsGoMasterTrainerTitlesRead {
        val verified = LetsGoSaveValidation.inspect(bytes) as? LetsGoValidation.Verified
            ?: throw IllegalArgumentException("A checksum-valid native Let's Go save is required.")
        val unlocked = (0 until TITLE_COUNT).filter { id ->
            val value = bytes[TITLE_FLAGS_OFFSET + (id ushr 3)].toInt() and 0xFF
            value and (1 shl (id and 7)) != 0
        }
        return LetsGoMasterTrainerTitlesRead(verified.version, unlocked)
    }

    fun titleLabel(id: Int): String = when (id) {
        0 -> "Trainer title"
        in 1..153 -> "Numbered title $id"
        154 -> "Grand title"
        155 -> "Battle title"
        else -> throw IllegalArgumentException("Master Trainer title ID is out of range.")
    }
}
