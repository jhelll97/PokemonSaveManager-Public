package com.example.pokemonsavemanager

import java.time.Instant
import java.util.Collections

enum class BdspTimestampKind(val displayName: String) {
    START("Start"), LATEST_SAVE("Latest save"), PENALTY_TIMEOUT("Penalty timeout"), LAST_DAILY_EVENT("Last daily event")
}

class BdspSavedTimestamp internal constructor(val kind: BdspTimestampKind, val count: Long, val fileTimeTicks: Long) {
    /** Match DateTime.FromFileTimeUtc's range; zero is 1601-01-01, not an invented empty marker. */
    val utcTimestamp: String? get() = if (fileTimeTicks in 0L..2650467743999999999L)
        Instant.ofEpochSecond(fileTimeTicks / 10000000L - 11644473600L, (fileTimeTicks % 10000000L) * 100L).toString()
        else null
}

class BdspTimestampsRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val entries: List<BdspSavedTimestamp>, val rawSystemBytes: List<Int>) {
    fun entry(kind: BdspTimestampKind): BdspSavedTimestamp = entries[kind.ordinal]
}

/** Saved native values only: no clock comparison, timer repair, snapshot decoding or persistence. */
object BdspTimestampsReadOnly {
    fun read(source: ByteArray): BdspTimestampsRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x95DAC
        val entries = BdspTimestampKind.values().map { kind ->
            val offset = base + kind.ordinal * 12
            BdspSavedTimestamp(kind, u32(source, offset), i64(source, offset + 4))
        }
        return BdspTimestampsRead(checked.version, checked.revision, Collections.unmodifiableList(entries),
            Collections.unmodifiableList((0 until 0x138).map { source[base + it].toInt() and 255 }))
    }
    private fun u32(b: ByteArray, offset: Int): Long = (0..3).fold(0L) { n, i ->
        n or ((b[offset + i].toLong() and 255) shl (8 * i))
    }
    private fun i64(b: ByteArray, offset: Int): Long = (0..7).fold(0L) { n, i ->
        n or ((b[offset + i].toLong() and 255) shl (8 * i))
    }
}
