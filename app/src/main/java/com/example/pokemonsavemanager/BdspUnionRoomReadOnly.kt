package com.example.pokemonsavemanager

import java.util.Collections

class BdspUnionRoomRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val rawInitTalkWord: Long, val rawPenaltyCountWord: Long, val rawPenaltyTimeBits: Long, val rawBytes: List<Int>) {
    val isInitTalk: Boolean get() = rawInitTalkWord == 1L
    val hasCanonicalTalkFlag: Boolean get() = rawInitTalkWord in 0L..1L
    val penaltyCount: Int get() = rawPenaltyCountWord.toInt()
    val penaltyTime: Float get() = Float.fromBits(rawPenaltyTimeBits.toInt())
}

/** Direct native block fields; saved raw words remain authoritative where legacy labels differ. */
object BdspUnionRoomReadOnly {
    fun read(source: ByteArray): BdspUnionRoomRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0xCEA10
        fun word(offset: Int): Long = (0..3).fold(0L) { n, i -> n or ((source[base + offset + i].toLong() and 255) shl (8 * i)) }
        val raw = Collections.unmodifiableList((0 until 12).map { source[base + it].toInt() and 255 })
        return BdspUnionRoomRead(checked.version, checked.revision, word(0), word(4), word(8), raw)
    }
}
