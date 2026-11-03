package com.example.pokemonsavemanager

import java.util.Collections

data class BdspRecordStatisticRead(val recordId: Int, val referenceKey: String, val displayName: String,
    val referenceLimit: Int, val rawValue: Long) {
    val signedValue: Int get() = rawValue.toInt()
}

class BdspRecordSetRead internal constructor(val index: Int, val entries: List<BdspRecordStatisticRead>, val rawBytes: List<Int>) {
    fun entry(recordId: Int): BdspRecordStatisticRead {
        require(recordId in entries.indices) { "The record ID is outside 0..29." }
        return entries[recordId]
    }
}

class BdspRecordStatisticsRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val sets: List<BdspRecordSetRead>, val rawRecordBytes: List<Int>) {
    fun set(index: Int): BdspRecordSetRead {
        require(index in sets.indices) { "The record set is outside 0..11." }
        return sets[index]
    }
}

object BdspRecordCatalogue {
    private val keys = listOf("CLEAR_TIME", "DENDOU_CNT", "CAPTURE_POKE", "FISHING_SUCCESS", "TAMAGO_HATCHING", "BEAT_DOWN_POKE",
        "RENSHOU_SINGLE", "RENSHOU_SINGLE_NOW", "RENSHOU_DOUBLE", "RENSHOU_DOUBLE_NOW", "RENSHOU_MASTER_SINGLE", "RENSHOU_MASTER_SINGLE_NOW",
        "RENSHOU_MASTER_DOUBLE", "RENSHOU_MASTER_DOUBLE_NOW", "BTL_TOWER_AVERAGE", "CONTEST_STYLE_RANK", "CONTEST_BEATIFUL_RANK",
        "CONTEST_CUTE_RANK", "CONTEST_CLEVER_RANK", "CONTEST_STRONG_RANK", "CONTEST_PLAY_SINGLE", "CONTEST_PLAY_LOCAL", "CONTEST_PLAY_NETWORK",
        "CONTEST_WIN_SINGLE", "CONTEST_WIN_LOCAL", "CONTEST_WIN_NETWORK", "CONTEST_RATE_SINGLE", "CONTEST_RATE_LOCAL", "CONTEST_RATE_NETWORK", "CONTEST_GET_RIBBON")
    private val names = listOf("Clear-time value", "Hall of Fame entries", "Pokémon caught", "Successful fishing", "Eggs hatched", "Pokémon defeated",
        "Singles streak value", "Current Singles streak", "Doubles streak value", "Current Doubles streak", "Master Singles streak value", "Current Master Singles streak",
        "Master Doubles streak value", "Current Master Doubles streak", "Battle Tower average value", "Style Contest rank", "Beautiful Contest rank",
        "Cute Contest rank", "Clever Contest rank", "Strong Contest rank", "Single Contest plays", "Local Contest plays", "Network Contest plays",
        "Single Contest wins", "Local Contest wins", "Network Contest wins", "Single Contest rate value", "Local Contest rate value", "Network Contest rate value", "Contest ribbon value")
    private val limits = listOf(Int.MAX_VALUE, 9999, 999999, 999999, 999999, 999999, 9999, 9999, 9999, 9999, 9999, 9999, 9999, 9999,
        7, 5, 5, 5, 5, 5, 9999, 9999, 9999, 9999, 9999, 9999, 100, 100, 100, 65536)
    fun key(recordId: Int): String { require(recordId in keys.indices); return keys[recordId] }
    fun name(recordId: Int): String { require(recordId in names.indices); return names[recordId] }
    fun referenceLimit(recordId: Int): Int { require(recordId in limits.indices); return limits[recordId] }
}

/** Read every stored word directly. Do not call the upstream clamping getter or infer chronology. */
object BdspRecordStatisticsReadOnly {
    fun read(source: ByteArray): BdspRecordStatisticsRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x8F3DC
        val sets = (0 until 12).map { setIndex ->
            val start = base + setIndex * 120
            val entries = (0 until 30).map { id ->
                val raw = (0..3).fold(0L) { n, i -> n or ((source[start + id * 4 + i].toLong() and 255) shl (8 * i)) }
                BdspRecordStatisticRead(id, BdspRecordCatalogue.key(id), BdspRecordCatalogue.name(id), BdspRecordCatalogue.referenceLimit(id), raw)
            }
            BdspRecordSetRead(setIndex, Collections.unmodifiableList(entries),
                Collections.unmodifiableList((0 until 120).map { source[start + it].toInt() and 255 }))
        }
        return BdspRecordStatisticsRead(checked.version, checked.revision, Collections.unmodifiableList(sets),
            Collections.unmodifiableList((0 until 0x5A0).map { source[base + it].toInt() and 255 }))
    }
}
