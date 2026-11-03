package com.example.pokemonsavemanager

import java.util.Collections

class BdspHoneyTreeEntry internal constructor(val index: Int, val rawSpreadWord: Long,
    val minutes: Int, val tableId: Int, val rarityId: Int, val shakeId: Int, val rawBytes: List<Int>) {
    val isSpread: Boolean get() = rawSpreadWord == 1L
    val hasCanonicalSpreadWord: Boolean get() = rawSpreadWord in 0L..1L
}

class BdspHoneyTreesRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val lastUpdateMinutes: Long, val treeNumber: Int, val entries: List<BdspHoneyTreeEntry>,
    val rawHoneyBytes: List<Int>) {
    val spreadFlagCount: Int get() = entries.count { it.isSpread }
    val unknownSpreadWordCount: Int get() = entries.count { !it.hasCanonicalSpreadWord }
    fun entry(index: Int): BdspHoneyTreeEntry {
        require(index in entries.indices) { "The saved Honey Tree index is outside 0..20." }
        return entries[index]
    }
}

/** Saved Honey Tree fields only. No clock epoch, timer readiness, species inference or persistence. */
object BdspHoneyTreesReadOnly {
    fun read(source: ByteArray): BdspHoneyTreesRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x8F97C + 0x14
        fun bytes(offset: Int, count: Int): List<Int> = Collections.unmodifiableList((0 until count).map { source[offset + it].toInt() and 255 })
        fun word(offset: Int): Long = (0..3).fold(0L) { n, i -> n or ((source[offset + i].toLong() and 255) shl (8 * i)) }
        fun signedLong(offset: Int): Long = (0..7).fold(0L) { n, i -> n or ((source[offset + i].toLong() and 255) shl (8 * i)) }
        val entries = (0 until 21).map { index ->
            val start = base + 12 + index * 12
            BdspHoneyTreeEntry(index, word(start), word(start + 4).toInt(), source[start + 8].toInt() and 255,
                source[start + 9].toInt() and 255, source[start + 10].toInt() and 255, bytes(start, 12))
        }
        return BdspHoneyTreesRead(checked.version, checked.revision, signedLong(base), source[base + 8].toInt() and 255,
            Collections.unmodifiableList(entries), bytes(base, 0x108))
    }
}
