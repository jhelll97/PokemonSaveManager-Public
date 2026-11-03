package com.example.pokemonsavemanager

import java.util.Collections

class BdspUndergroundReturnRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val returnZoneId: Int, val gridX: Int, val gridY: Int, val gridZ: Int, val zenmetsuZoneId: Int,
    val rawZenmetsuXBits: Long, val rawZenmetsuYBits: Long, val rawZenmetsuZBits: Long, val zenmetsuDirection: Int,
    val returnUgZoneId: Int, val rawReturnBytes: List<Int>, val rawUndergroundZoneBytes: List<Int>) {
    val zenmetsuX: Float get() = Float.fromBits(rawZenmetsuXBits.toInt())
    val zenmetsuY: Float get() = Float.fromBits(rawZenmetsuYBits.toInt())
    val zenmetsuZ: Float get() = Float.fromBits(rawZenmetsuZBits.toInt())
}

/** Saved return metadata only; no map interpretation, valid-destination claims, teleporting or writer. */
object BdspUndergroundReturnReadOnly {
    fun read(source: ByteArray): BdspUndergroundReturnRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x9A89C
        fun word(offset: Int): Long = (0..3).fold(0L) { n, i -> n or ((source[base + offset + i].toLong() and 255) shl (8 * i)) }
        fun raw(offset: Int, count: Int): List<Int> = Collections.unmodifiableList((0 until count).map { source[base + offset + it].toInt() and 255 })
        return BdspUndergroundReturnRead(checked.version, checked.revision, word(0).toInt(), word(4).toInt(), word(8).toInt(), word(12).toInt(),
            word(16).toInt(), word(20), word(24), word(28), word(32).toInt(), word(0x1528).toInt(), raw(0, 0x24), raw(0x1528, 4))
    }
}
