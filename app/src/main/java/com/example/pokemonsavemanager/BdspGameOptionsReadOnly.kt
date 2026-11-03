package com.example.pokemonsavemanager

import java.util.Collections

enum class BdspGameOptionFlag(val displayName: String, internal val offset: Int) {
    KANJI("Kanji script", 0x08), NICKNAME_REGISTRATION("Nickname registration", 0x1C),
    GYRO_SENSOR("Gyro sensor", 0x20), FOSSIL_CAMERA_SHAKE("Fossil camera shake", 0x24),
    AUTO_REPORT("Auto-report", 0x30), SHOW_NICKNAMES("Show nicknames", 0x38)
}

class BdspSavedOptionFlag internal constructor(val kind: BdspGameOptionFlag, val rawWord: Long) {
    val rawByte: Int get() = (rawWord and 255).toInt()
    // Native ConfigSave8b tests Data[offset] == 1, not the complete 32-bit slot.
    val nativeEnabled: Boolean get() = rawByte == 1
    val hasCanonicalByte: Boolean get() = rawByte in 0..1
}

class BdspGameOptionsRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val textSpeedId: Long, val languageId: Int, val windowTypeId: Int, val moveAnimationsId: Int,
    val battleStyleId: Int, val partyBoxId: Int, val cameraVerticalId: Int, val cameraHorizontalId: Int,
    val inputModeId: Int, val flags: List<BdspSavedOptionFlag>, val bgmVolume: Int,
    val soundEffectsVolume: Int, val voiceVolume: Int, val rawConfigBytes: List<Int>) {
    fun flag(kind: BdspGameOptionFlag): BdspSavedOptionFlag = flags[kind.ordinal]
}

/** Labels describe stored IDs, not menu availability or legality. Unknown values have no fallback. */
object BdspGameOptionNames {
    fun textSpeed(id: Long): String? = when (id) { 0L -> "Slow"; 1L -> "Normal"; 2L -> "Fast"; 3L -> "Instant"; else -> null }
    fun moveAnimations(id: Int): String? = when (id) { 0 -> "On"; 1 -> "Off"; else -> null }
    fun battleStyle(id: Int): String? = when (id) { 0 -> "Switch"; 1 -> "Set"; else -> null }
    fun partyBox(id: Int): String? = when (id) { 0 -> "Select"; 1 -> "Send to Box"; else -> null }
    fun camera(id: Int): String? = when (id) { 0 -> "Normal"; 1 -> "Reverse"; else -> null }
    fun inputMode(id: Int): String? = when (id) { 0 -> "Easy"; 1 -> "Normal"; else -> null }
    fun language(id: Int): String? = when (id) {
        1 -> "Japanese"; 2 -> "English"; 3 -> "French"; 4 -> "Italian"; 5 -> "German"; 7 -> "Spanish"
        8 -> "Korean"; 9 -> "Chinese (Simplified)"; 10 -> "Chinese (Traditional)"; else -> null
    }
}

/** Independent read-only configuration inspection; strict trainer/language validation stays intact. */
object BdspGameOptionsReadOnly {
    fun read(source: ByteArray): BdspGameOptionsRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x79B74
        fun word(offset: Int): Long = (0..3).fold(0L) { n, i ->
            n or ((source[base + offset + i].toLong() and 255) shl (8 * i))
        }
        val flags = BdspGameOptionFlag.values().map { BdspSavedOptionFlag(it, word(it.offset)) }
        return BdspGameOptionsRead(checked.version, checked.revision,
            word(0), word(4).toInt(), word(0x0C).toInt(), word(0x10).toInt(), word(0x14).toInt(),
            word(0x18).toInt(), word(0x28).toInt(), word(0x2C).toInt(), word(0x34).toInt(),
            Collections.unmodifiableList(flags), source[base + 0x3C].toInt() and 255,
            source[base + 0x3D].toInt() and 255, source[base + 0x3E].toInt() and 255,
            Collections.unmodifiableList((0 until 0x40).map { source[base + it].toInt() and 255 }))
    }
}
