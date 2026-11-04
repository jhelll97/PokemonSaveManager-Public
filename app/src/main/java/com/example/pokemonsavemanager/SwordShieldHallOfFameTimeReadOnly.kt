package com.example.pokemonsavemanager

import java.time.Instant

data class SwordShieldHallOfFameTimeRead(
    val version: SwordShieldVersion,
    val epochSeconds: Long,
    val rawHex: String,
    val utcTimestamp: String?
)

/** Exact read-only projection of PKHeX's documented Sword/Shield Hall of Fame time block. */
object SwordShieldHallOfFameTimeReadOnly {
    fun read(source: ByteArray): SwordShieldHallOfFameTimeRead {
        val block = SwordShieldSaveValidation.readHallOfFameTimeBlock(source)
        val seconds = int64LittleEndian(block.value)
        return SwordShieldHallOfFameTimeRead(
            block.version,
            seconds,
            block.value.reversedArray().joinToString("") { "%02X".format(it.toInt() and 0xFF) },
            runCatching { Instant.ofEpochSecond(seconds).toString() }.getOrNull()
        )
    }

    private fun int64LittleEndian(bytes: ByteArray): Long {
        require(bytes.size == 8) { "A native Sword/Shield Int64 Hall of Fame timestamp is truncated." }
        var result = 0L
        repeat(8) { result = result or ((bytes[it].toLong() and 0xFF) shl (it * 8)) }
        return result
    }
}
