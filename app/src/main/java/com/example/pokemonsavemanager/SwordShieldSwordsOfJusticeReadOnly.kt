package com.example.pokemonsavemanager

data class SwordShieldSwordsOfJusticeRead(
    val version: SwordShieldVersion,
    val questStatus: Long,
    val questProgress: Long,
    val cobalionProgress: Long,
    val terrakionProgress: Long,
    val virizionProgress: Long,
    val keldeoProgress: Long
) {
    val questStatusLabel: String get() = when (questStatus) {
        0L -> "Cannot start"
        10L -> "Can start"
        20L -> "Camera highlights Cobalion footprints"
        30L -> "In progress"
        else -> "Outside documented values"
    }

    val questProgressLabel: String get() = when (questProgress) {
        0L -> "0 reported"
        1L -> "1 reported"
        2L -> "2 reported"
        4L -> "3 reported"
        7L -> "Keldeo caught"
        8L -> "Keldeo reported • Completed"
        else -> "Outside documented values"
    }

    fun swordsMemberLabel(value: Long): String = when (value) {
        0L -> "Footprints unreported"
        1L -> "Location revealed"
        2L -> "Caught"
        3L -> "Capture reported"
        else -> "Outside documented values"
    }

    val keldeoProgressLabel: String get() = when (keldeoProgress) {
        0L -> "Have to camp"
        2L -> "Appeared"
        3L -> "Caught"
        else -> "Outside documented values"
    }
}

/** Exact read-only projection of PKHeX's documented Crown Tundra Swords of Justice UInt32 records. */
object SwordShieldSwordsOfJusticeReadOnly {
    fun read(source: ByteArray): SwordShieldSwordsOfJusticeRead {
        val blocks = SwordShieldSaveValidation.readSwordsOfJusticeBlocks(source)
        return SwordShieldSwordsOfJusticeRead(
            blocks.version,
            uint32(blocks.questStatus),
            uint32(blocks.questProgress),
            uint32(blocks.cobalionProgress),
            uint32(blocks.terrakionProgress),
            uint32(blocks.virizionProgress),
            uint32(blocks.keldeoProgress)
        )
    }

    private fun uint32(bytes: ByteArray): Long {
        require(bytes.size == 4) { "A native Sword/Shield Swords of Justice quest record is truncated." }
        return (bytes[0].toLong() and 0xFF) or
            ((bytes[1].toLong() and 0xFF) shl 8) or
            ((bytes[2].toLong() and 0xFF) shl 16) or
            ((bytes[3].toLong() and 0xFF) shl 24)
    }
}
