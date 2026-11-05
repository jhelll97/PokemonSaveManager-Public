package com.example.pokemonsavemanager

import kotlin.math.floor

data class LegendsZaPlayTimeRead(
    val version: LegendsZaVersion,
    val revision: LegendsZaRevision?,
    val rawSeconds: Double,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val fractionalSeconds: Double
) {
    val savedTimeText: String
        get() = "${hours.toString().padStart(4, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

/** Native elapsed play time with no timer advance, wall-clock calculation, or normalization. */
object LegendsZaPlayTimeReadOnly {
    fun read(source: ByteArray): LegendsZaPlayTimeRead {
        val block = LegendsZaSaveValidation.readPlayTimeBlock(source)
        val bytes = block.data
        require(bytes.size == 8) { "The native Legends: Z-A elapsed-seconds scalar is truncated." }
        var bits = 0L
        repeat(8) { index -> bits = bits or ((bytes[index].toLong() and 255L) shl (index * 8)) }
        val rawSeconds = java.lang.Double.longBitsToDouble(bits)
        require(rawSeconds.isFinite() && rawSeconds >= 0.0) {
            "The native Legends: Z-A elapsed-seconds value is invalid."
        }
        val wholeSeconds = floor(rawSeconds).toLong()
        require(wholeSeconds < 65_536L * 3_600L) {
            "The native Legends: Z-A Play Time exceeds the supported 16-bit hour range."
        }
        return LegendsZaPlayTimeRead(
            block.version,
            block.revision,
            rawSeconds,
            (wholeSeconds / 3_600L).toInt(),
            ((wholeSeconds % 3_600L) / 60L).toInt(),
            (wholeSeconds % 60L).toInt(),
            rawSeconds - wholeSeconds.toDouble()
        )
    }
}
