package com.example.pokemonsavemanager

import java.util.Collections

class BdspUndergroundActivityRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val fossilDiggingPlayCount: Int, val statueTvBroadcastCount: Int, val secretBaseTvLikeCount: Int,
    val otherSecretBaseLikeCount: Int, val successfulLightStoneSearchCount: Int,
    val reserved1: Long, val reserved2: Long, val rawRecordBytes: List<Int>)

/** Native signed saved counters. No clamping, inventory/base inference, reserved-field decoding or writing. */
object BdspUndergroundActivityReadOnly {
    fun read(source: ByteArray): BdspUndergroundActivityRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0xE8178
        fun word(offset: Int, size: Int): Long = (0 until size).fold(0L) { n, i ->
            n or ((source[base + offset + i].toLong() and 255) shl (8 * i))
        }
        return BdspUndergroundActivityRead(checked.version, checked.revision,
            word(0, 2).toShort().toInt(), word(2, 2).toShort().toInt(), word(4, 4).toInt(),
            word(8, 4).toInt(), word(0x0C, 4).toInt(), word(0x10, 8), word(0x18, 8),
            Collections.unmodifiableList((0 until 0x20).map { source[base + it].toInt() and 255 }))
    }
}
