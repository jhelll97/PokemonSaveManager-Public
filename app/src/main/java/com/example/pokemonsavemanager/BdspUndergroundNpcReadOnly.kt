package com.example.pokemonsavemanager

import java.util.Collections

enum class BdspUndergroundNpcFilter(val displayName: String) {
    ALL("All saved entries"), NONZERO("Nonzero entries"), ZERO("Zero entries")
}

class BdspUndergroundNpcEntry internal constructor(val index: Int, val value: Int)

class BdspUndergroundNpcRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val entries: List<BdspUndergroundNpcEntry>, val rawNpcBytes: List<Int>) {
    val recordedEntryCount: Int get() = entries.count { it.value != 0 }
    fun entry(index: Int): BdspUndergroundNpcEntry {
        require(index in entries.indices) { "The saved NPC interaction index is outside 0..99." }
        return entries[index]
    }
    fun filtered(filter: BdspUndergroundNpcFilter): List<BdspUndergroundNpcEntry> = Collections.unmodifiableList(entries.filter {
        when (filter) {
            BdspUndergroundNpcFilter.ALL -> true
            BdspUndergroundNpcFilter.NONZERO -> it.value != 0
            BdspUndergroundNpcFilter.ZERO -> it.value == 0
        }
    })
}

/** Native nonzero-entry count only; duplicate bytes count separately, with no NPC identity or quest inference. */
object BdspUndergroundNpcReadOnly {
    fun read(source: ByteArray): BdspUndergroundNpcRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x9A89C + 0x273C
        val raw = Collections.unmodifiableList((0 until 100).map { source[base + it].toInt() and 255 })
        val entries = raw.mapIndexed { index, value -> BdspUndergroundNpcEntry(index, value) }
        return BdspUndergroundNpcRead(checked.version, checked.revision, Collections.unmodifiableList(entries), raw)
    }
}
