package com.example.pokemonsavemanager

import java.util.Collections

class BdspPlayTimeRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val hours: Int, val minutes: Int, val seconds: Int, val rawPlayTimeBytes: List<Int>) {
    val hasCanonicalMinutesAndSeconds: Boolean get() = minutes in 0..59 && seconds in 0..59
    /** Preserve hours above four digits and anomalous byte values; never carry or truncate them. */
    val savedTimeText: String get() = "${hours.toString().padStart(4, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

/** Native recorded playtime projection. No wall-clock calculation, timer advance, or normalization. */
object BdspPlayTimeReadOnly {
    fun read(source: ByteArray): BdspPlayTimeRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x79C04
        val bytes = (0 until 4).map { source[base + it].toInt() and 255 }
        return BdspPlayTimeRead(checked.version, checked.revision, bytes[0] or (bytes[1] shl 8), bytes[2], bytes[3],
            Collections.unmodifiableList(bytes))
    }
}
