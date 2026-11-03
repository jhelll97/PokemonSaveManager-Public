package com.example.pokemonsavemanager

import java.util.Collections

class BdspMenuItemRead internal constructor(val index: Int, val menuId: Int,
    val rawNewWord: Long, val rawBytes: List<Int>) {
    val isNew: Boolean get() = rawNewWord == 1L
    val hasCanonicalNewWord: Boolean get() = rawNewWord in 0L..1L
}

class BdspMenuSelectionRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val items: List<BdspMenuItemRead>, val lastSelectedMenuId: Int, val rawBytes: List<Int>) {
    fun item(index: Int): BdspMenuItemRead {
        require(index in items.indices) { "The saved menu index is outside 0..7." }
        return items[index]
    }
}

/** Native MenuSelect8b records. IDs and noncanonical flags remain exact; no action or unlock inference. */
object BdspMenuSelectionReadOnly {
    fun read(source: ByteArray): BdspMenuSelectionRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x7E9F8
        fun raw(o: Int, count: Int): List<Int> = Collections.unmodifiableList((0 until count).map { source[o + it].toInt() and 255 })
        fun word(o: Int): Long = (0..3).fold(0L) { n, i -> n or ((source[o + i].toLong() and 255) shl (8 * i)) }
        val items = (0 until 8).map { index ->
            val o = base + index * 8
            BdspMenuItemRead(index, word(o).toInt(), word(o + 4), raw(o, 8))
        }
        return BdspMenuSelectionRead(checked.version, checked.revision, Collections.unmodifiableList(items),
            word(base + 0x40).toInt(), raw(base, 0x44))
    }
}
