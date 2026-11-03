package com.example.pokemonsavemanager

import java.util.Collections

enum class BdspContestPhotoCategory(val nativeName: String) {
    STYLE("Style"), BEAUTIFUL("Beautiful"), CUTE("Cute"), CLEVER("Clever"), STRONG("Strong")
}

class BdspContestPhotoLanguageRead internal constructor(val index: Int,
    val category: BdspContestPhotoCategory, val languageId: Int)

class BdspContestPhotoLanguagesRead internal constructor(val version: BdspVersion,
    val revision: BdspSaveRevision, val entries: List<BdspContestPhotoLanguageRead>,
    val rawBytes: List<Int>, val reservedBytes: List<Int>) {
    fun entry(index: Int): BdspContestPhotoLanguageRead {
        require(index in entries.indices) { "The saved contest photo language index is outside 0..4." }
        return entries[index]
    }
}

/** Five native language bytes only; no trainer-language gate, normalization or photo-content decoding. */
object BdspContestPhotoLanguagesReadOnly {
    fun read(source: ByteArray): BdspContestPhotoLanguagesRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0xCEA1C
        fun raw(offset: Int, count: Int): List<Int> = Collections.unmodifiableList((0 until count).map { source[offset + it].toInt() and 255 })
        val categories = BdspContestPhotoCategory.values()
        val entries = categories.mapIndexed { index, category ->
            BdspContestPhotoLanguageRead(index, category, source[base + index].toInt() and 255)
        }
        return BdspContestPhotoLanguagesRead(checked.version, checked.revision,
            Collections.unmodifiableList(entries), raw(base, 0x18), raw(base + 5, 0x13))
    }
}
