package com.example.pokemonsavemanager

import java.util.Collections

enum class BdspSealStickerFilter(val displayName: String) {
    ALL("All saved entries"), GET_SET("Obtained flag set"), NONZERO_COUNTS("Nonzero counts"),
    UNLISTED("Unlisted seal IDs"), UNKNOWN_FLAGS("Unknown flag words")
}

class BdspSealStickerEntry internal constructor(val index: Int, val rawObtainedWord: Long,
    val count: Int, val totalCount: Int, val rawBytes: List<Int>) {
    val isGet: Boolean get() = rawObtainedWord == 1L
    val hasCanonicalFlag: Boolean get() = rawObtainedWord in 0L..1L
    val nativeLabel: String? get() = BdspSealStickerLabels.nativeLabel(index)
}

class BdspSealStickersRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val entries: List<BdspSealStickerEntry>, val rawSealBytes: List<Int>) {
    val obtainedFlagCount: Int get() = entries.count { it.isGet }
    fun entry(index: Int): BdspSealStickerEntry {
        require(index in entries.indices) { "The saved seal-sticker index is outside 0..199." }
        return entries[index]
    }
    fun filtered(filter: BdspSealStickerFilter): List<BdspSealStickerEntry> = Collections.unmodifiableList(entries.filter {
        when (filter) {
            BdspSealStickerFilter.ALL -> true
            BdspSealStickerFilter.GET_SET -> it.isGet
            BdspSealStickerFilter.NONZERO_COUNTS -> it.count != 0 || it.totalCount != 0
            BdspSealStickerFilter.UNLISTED -> it.nativeLabel == null
            BdspSealStickerFilter.UNKNOWN_FLAGS -> !it.hasCanonicalFlag
        }
    })
}

/** Native counts/flags only; no unlock, cap normalization, capsule placement, repair or persistence. */
object BdspSealStickersReadOnly {
    fun read(source: ByteArray): BdspSealStickersRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x93E0C
        fun word(offset: Int): Long = (0..3).fold(0L) { n, i -> n or ((source[offset + i].toLong() and 255) shl (8 * i)) }
        fun raw(offset: Int, count: Int): List<Int> = Collections.unmodifiableList((0 until count).map { source[offset + it].toInt() and 255 })
        val entries = (0 until 200).map { index ->
            val offset = base + index * 12
            BdspSealStickerEntry(index, word(offset), word(offset + 4).toInt(), word(offset + 8).toInt(), raw(offset, 12))
        }
        return BdspSealStickersRead(checked.version, checked.revision, Collections.unmodifiableList(entries), raw(base, 0x960))
    }
}

/** Exact native Seal8b catalogue keys, not localized names; unlisted IDs remain visible. */
object BdspSealStickerLabels {
    private val keys = listOf(
        "UNKNOWN",
        "HEART_A",
        "HEART_B",
        "HEART_C",
        "HEART_D",
        "HEART_E",
        "HEART_F",
        "STAR_A",
        "STAR_B",
        "STAR_C",
        "STAR_D",
        "STAR_E",
        "STAR_F",
        "LINE_A",
        "LINE_B",
        "LINE_C",
        "LINE_D",
        "SMOKE_A",
        "SMOKE_B",
        "SMOKE_C",
        "SMOKE_D",
        "ELECTRIC_A",
        "ELECTRIC_B",
        "ELECTRIC_C",
        "ELECTRIC_D",
        "BUBBLE_A",
        "BUBBLE_B",
        "BUBBLE_C",
        "BUBBLE_D",
        "FIRE_A",
        "FIRE_B",
        "FIRE_C",
        "FIRE_D",
        "PARTY_A",
        "PARTY_B",
        "PARTY_C",
        "PARTY_D",
        "FLOWER_A",
        "FLOWER_B",
        "FLOWER_C",
        "FLOWER_D",
        "FLOWER_E",
        "FLOWER_F",
        "SONG_A",
        "SONG_B",
        "SONG_C",
        "SONG_D",
        "SONG_E",
        "SONG_F",
        "SONG_G",
        "DARK_A",
        "DARK_B",
        "DARK_C",
        "PRETTY_A",
        "PRETTY_B",
        "PRETTY_C",
        "COOL_A",
        "COOL_B",
        "COOL_C",
        "BURNING_A",
        "BURNING_B",
        "BURNING_C",
        "SKY_A",
        "SKY_B",
        "SKY_C",
        "ROCK_A",
        "ROCK_B",
        "ROCK_C",
        "LEAF_A",
        "LEAF_B",
        "LEAF_C",
        "SPARK_A",
        "SPARK_B",
        "SPARK_C",
        "DRESSER_A",
        "KAKKOYOSA_A",
        "KAKKOYOSA_B",
        "KAKKOYOSA_C",
        "KAKKOYOSA_D",
        "BEAUTY_A",
        "BEAUTY_B",
        "BEAUTY_C",
        "BEAUTY_D",
        "SMART_A",
        "SMART_B",
        "SMART_C",
        "SMART_D",
        "STRENGTH_A",
        "STRENGTH_B",
        "STRENGTH_C",
        "STRENGTH_D",
        "CUTE_A",
        "CUTE_B",
        "CUTE_C",
        "CUTE_D",
        "SHOWMASTER_A",
        "CHAMPION_A"
    )
    fun nativeLabel(index: Int): String? = if (index in 1 until keys.size) keys[index] else null
}

