package com.example.pokemonsavemanager

import java.util.Collections

class BdspTrainerAppearanceRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val regionCode: Int, val trainerViewId: Int, val bodyTypeId: Int, val fashionId: Int,
    val starterTypeId: Int, val rawDsPlayerFlag: Int, val followerIndex: Int,
    val gridX: Int, val gridY: Int, val rawHeightBits: Long, val rawRotationBits: Long,
    val rawStatusBytes: List<Int>) {
    val fashionName: String? get() = BdspFashionNames.name(fashionId)
    val isDsPlayer: Boolean get() = rawDsPlayerFlag == 1
    val hasCanonicalDsPlayerFlag: Boolean get() = rawDsPlayerFlag in 0..1
    val height: Float get() = Float.fromBits(rawHeightBits.toInt())
    val rotation: Float get() = Float.fromBits(rawRotationBits.toInt())
}

/** Saved outfit IDs only; this does not imply unlocked clothing or validity for the trainer. */
object BdspFashionNames {
    private val styles = listOf("Everyday", "Contest", "Pikachu Hoodie", "Platinum", "Overalls", "Eevee Jacket",
        "Gengar Jacket", "Cyber", "Summer", "Winter", "Spring", "Casual", "Leather Jacket")
    fun name(id: Int): String? = when (id) {
        in 0..12 -> "${styles[id]} (Male)"
        in 100..112 -> "${styles[id - 100]} (Female)"
        else -> null
    }
}

/** Independent native status inspection. Existing strict trainer text/money/flag rules stay unchanged. */
object BdspTrainerAppearanceReadOnly {
    fun read(source: ByteArray): BdspTrainerAppearanceRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x79BB4
        return BdspTrainerAppearanceRead(checked.version, checked.revision,
            byte(source, base + 0x28), byte(source, base + 0x2A), byte(source, base + 0x30), byte(source, base + 0x31),
            u32(source, base + 0x34).toInt(), byte(source, base + 0x38), u32(source, base + 0x3C).toInt(),
            u32(source, base + 0x40).toInt(), u32(source, base + 0x44).toInt(),
            u32(source, base + 0x48), u32(source, base + 0x4C),
            Collections.unmodifiableList((0 until 0x50).map { byte(source, base + it) }))
    }
    private fun byte(b: ByteArray, offset: Int): Int = b[offset].toInt() and 255
    private fun u32(b: ByteArray, offset: Int): Long = (0..3).fold(0L) { n, i ->
        n or ((b[offset + i].toLong() and 255) shl (8 * i))
    }
}
