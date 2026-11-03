package com.example.pokemonsavemanager

import java.util.Collections

class BdspBerryPlotRecord internal constructor(val index: Int, val rawBytes: List<Int>)

class BdspBerryPlotsRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val lastUpdateMinutes: Long, val plots: List<BdspBerryPlotRecord>, val rawBerryBytes: List<Int>) {
    fun plot(index: Int): BdspBerryPlotRecord {
        require(index in plots.indices) { "The saved berry plot index is outside 0..127." }
        return plots[index]
    }
}

/** Verified record boundaries only; plot internals and update-minute clock epoch remain unclassified. */
object BdspBerryPlotsReadOnly {
    fun read(source: ByteArray): BdspBerryPlotsRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x94DA8
        fun raw(offset: Int, count: Int): List<Int> = Collections.unmodifiableList((0 until count).map { source[base + offset + it].toInt() and 255 })
        val plots = (0 until 128).map { index -> BdspBerryPlotRecord(index, raw(index * 16, 16)) }
        val update = (0..7).fold(0L) { n, i -> n or ((source[base + 0x800 + i].toLong() and 255) shl (8 * i)) }
        return BdspBerryPlotsRead(checked.version, checked.revision, update, Collections.unmodifiableList(plots), raw(0, 0x808))
    }
}
