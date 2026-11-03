package com.example.pokemonsavemanager

import java.util.Collections

class BdspStatueRecord internal constructor(val index: Int, val statueId: Int, val pedestalId: Int,
    val x: Int, val y: Int, val direction: Int, val rawBytes: List<Int>)

class BdspSecretBaseRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val talkPlayerDataId: Long, val talkPlayerCount: Long, val zoneId: Int, val x: Int, val y: Int,
    val direction: Int, val expansionStatus: Int, val goodCount: Int, val statues: List<BdspStatueRecord>,
    val rawEnableBytes: List<Int>, val rawBaseBytes: List<Int>) {
    fun statue(index: Int): BdspStatueRecord {
        require(index in statues.indices) { "The saved statue index is outside 0..29." }
        return statues[index]
    }
}

/** Saved layout metadata only; no enable-flag interpretation, statue effects, legality checks or writer. */
object BdspSecretBaseReadOnly {
    fun read(source: ByteArray): BdspSecretBaseRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x9A89C + 0x152C
        fun byte(offset: Int): Int = source[base + offset].toInt() and 255
        fun signedShort(offset: Int): Int = (byte(offset) or (byte(offset + 1) shl 8)).toShort().toInt()
        fun word(offset: Int): Long = (0..3).fold(0L) { n, i -> n or (byte(offset + i).toLong() shl (8 * i)) }
        fun raw(offset: Int, count: Int): List<Int> = Collections.unmodifiableList((0 until count).map { byte(offset + it) })
        val statues = (0 until 30).map { index ->
            val offset = 0x14 + index * 20
            BdspStatueRecord(index, word(offset).toInt(), word(offset + 4).toInt(), word(offset + 8).toInt(),
                word(offset + 12).toInt(), word(offset + 16).toInt(), raw(offset, 20))
        }
        return BdspSecretBaseRead(checked.version, checked.revision, word(0), word(4), signedShort(8), signedShort(10), signedShort(12),
            byte(14), byte(15), word(16).toInt(), Collections.unmodifiableList(statues), raw(0x26C, 4), raw(0, 0x270))
    }
}
