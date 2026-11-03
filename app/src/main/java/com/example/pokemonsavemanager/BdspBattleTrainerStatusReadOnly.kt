package com.example.pokemonsavemanager

import java.util.Collections

enum class BdspBattleTrainerFilter(val displayName: String) {
    ALL("All saved trainer entries"), WIN_SET("Win flag set"), SEARCHER_SET("Battle-searcher flag set"), UNKNOWN_WORDS("Unknown flag words")
}

class BdspBattleTrainerStatusEntry internal constructor(val index: Int, val rawWinWord: Long,
    val rawBattleSearcherWord: Long, val rawBytes: List<Int>) {
    val isWin: Boolean get() = rawWinWord == 1L
    val isBattleSearcher: Boolean get() = rawBattleSearcherWord == 1L
    val hasCanonicalWords: Boolean get() = rawWinWord in 0L..1L && rawBattleSearcherWord in 0L..1L
}

class BdspBattleTrainerStatusRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val entries: List<BdspBattleTrainerStatusEntry>, val rawStatusBytes: List<Int>) {
    val winFlagCount: Int get() = entries.count { it.isWin }
    val battleSearcherFlagCount: Int get() = entries.count { it.isBattleSearcher }
    val unknownEntryCount: Int get() = entries.count { !it.hasCanonicalWords }
    fun entry(index: Int): BdspBattleTrainerStatusEntry {
        require(index in entries.indices) { "The saved trainer index is outside 0..706." }
        return entries[index]
    }
    fun filtered(filter: BdspBattleTrainerFilter): List<BdspBattleTrainerStatusEntry> = Collections.unmodifiableList(entries.filter {
        when (filter) {
            BdspBattleTrainerFilter.ALL -> true
            BdspBattleTrainerFilter.WIN_SET -> it.isWin
            BdspBattleTrainerFilter.SEARCHER_SET -> it.isBattleSearcher
            BdspBattleTrainerFilter.UNKNOWN_WORDS -> !it.hasCanonicalWords
        }
    })
}

/** Saved flags only; no trainer catalogue, rematch eligibility, bulk state changes or persistence. */
object BdspBattleTrainerStatusReadOnly {
    fun read(source: ByteArray): BdspBattleTrainerStatusRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x7D3E0
        fun bytes(offset: Int, count: Int): List<Int> = Collections.unmodifiableList((0 until count).map { source[offset + it].toInt() and 255 })
        fun word(offset: Int): Long = (0..3).fold(0L) { n, i -> n or ((source[offset + i].toLong() and 255) shl (8 * i)) }
        val entries = (0 until 707).map { index ->
            val start = base + index * 8
            BdspBattleTrainerStatusEntry(index, word(start), word(start + 4), bytes(start, 8))
        }
        return BdspBattleTrainerStatusRead(checked.version, checked.revision, Collections.unmodifiableList(entries), bytes(base, 0x1618))
    }
}
