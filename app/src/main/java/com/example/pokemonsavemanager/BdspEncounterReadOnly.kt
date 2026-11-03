package com.example.pokemonsavemanager

import java.util.Collections

class BdspRadarRecord internal constructor(val index: Int, val speciesId: Long, val chain: Long, val rawBytes: List<Int>)

class BdspRoamerRecord internal constructor(val index: Int, val zoneId: Int, val seed: ULong,
    val speciesId: Long, val hp: Long, val level: Int, val statusWord: Long, val encounterByte: Int, val rawBytes: List<Int>)

class BdspEncounterRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val walkCount: Int, val safariSeed: Long, val generateSeed: Long, val rawHillTalkWord: Long,
    val hillTable1: Int, val hillTable2: Int, val radarRecords: List<BdspRadarRecord>,
    val beforeZone: Int, val oldZone: Int, val roamers: List<BdspRoamerRecord>, val rawGenerateValidWord: Long,
    val sprayCount: Int, val sprayType: Int, val vsSeekerCharge: Int, val radarCharge: Int, val fluteType: Int,
    val rawEncounterBytes: List<Int>) {
    val isHillTalk: Boolean get() = rawHillTalkWord == 1L
    val isGenerateValid: Boolean get() = rawGenerateValidWord == 1L
    fun radarRecord(index: Int): BdspRadarRecord {
        require(index in radarRecords.indices) { "The saved radar record index is outside 0..2." }
        return radarRecords[index]
    }
    fun roamer(index: Int): BdspRoamerRecord {
        require(index in roamers.indices) { "The saved roamer index is outside 0..1." }
        return roamers[index]
    }
}

/** Native saved metadata only; no RNG advance, encounter prediction, state repair or persistence. */
object BdspEncounterReadOnly {
    fun read(source: ByteArray): BdspEncounterRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x8F97C
        fun byte(offset: Int): Int = source[base + offset].toInt() and 255
        fun u16(offset: Int): Int = byte(offset) or (byte(offset + 1) shl 8)
        fun u32(offset: Int): Long = (0..3).fold(0L) { n, i -> n or (byte(offset + i).toLong() shl (8 * i)) }
        fun u64(offset: Int): ULong = (0..7).fold(0uL) { n, i -> n or (byte(offset + i).toULong() shl (8 * i)) }
        fun raw(offset: Int, count: Int): List<Int> = Collections.unmodifiableList((0 until count).map { byte(offset + it) })
        val radar = (0 until 3).map { i ->
            val offset = 0x11C + i * 8
            BdspRadarRecord(i, u32(offset), u32(offset + 4), raw(offset, 8))
        }
        val roamers = (0 until 2).map { i ->
            val offset = 0x13C + i * 0x20
            BdspRoamerRecord(i, u32(offset).toInt(), u64(offset + 4), u32(offset + 0xC), u32(offset + 0x10),
                byte(offset + 0x14), u32(offset + 0x18), byte(offset + 0x1C), raw(offset, 0x20))
        }
        return BdspEncounterRead(checked.version, checked.revision, u32(0).toInt(), u32(4), u32(8), u32(0xC),
            u16(0x10), u16(0x12), Collections.unmodifiableList(radar), u32(0x134).toInt(), u32(0x138).toInt(),
            Collections.unmodifiableList(roamers), u32(0x17C), u16(0x180).toShort().toInt(), byte(0x182),
            byte(0x183), byte(0x184), byte(0x185), raw(0, 0x188))
    }
}
