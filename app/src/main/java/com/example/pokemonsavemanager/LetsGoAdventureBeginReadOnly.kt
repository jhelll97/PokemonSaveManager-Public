package com.example.pokemonsavemanager

import java.time.Instant
import java.util.Collections

class LetsGoAdventureBeginRead internal constructor(
    val version: LetsGoVersion,
    val secondsSinceEpoch: ULong,
    val rawBytes: List<Int>,
    val utcTimestamp: String?,
    val unavailableReason: String?
)

/**
 * Native PlayerGeoLocation block 7 timestamp from PKHeX PlayerGeoLocation7b at pinned commit 0a7854f.
 * The eight-byte field is an unsigned little-endian count of seconds from 1970-01-01 00:00:00 UTC.
 */
object LetsGoAdventureBeginReadOnly {
    private const val FIELD_OFFSET = 0x5800 + 0x48

    fun read(source: ByteArray): LetsGoAdventureBeginRead {
        val checked = LetsGoSaveValidation.inspect(source)
        require(checked is LetsGoValidation.Verified) { "A fully validated native Let's Go save is required." }
        val raw = Collections.unmodifiableList((0 until 8).map { source[FIELD_OFFSET + it].toInt() and 255 })
        val seconds = raw.foldIndexed(0uL) { index, value, byte ->
            value or (byte.toULong() shl (8 * index))
        }
        val timestamp = if (seconds <= Long.MAX_VALUE.toULong())
            runCatching { Instant.ofEpochSecond(seconds.toLong()).toString() }.getOrNull()
        else null
        val reason = if (timestamp != null) null else
            "The saved unsigned epoch count is outside java.time.Instant's supported range."
        return LetsGoAdventureBeginRead(checked.version, seconds, raw, timestamp, reason)
    }
}
