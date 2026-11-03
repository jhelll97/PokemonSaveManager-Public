package com.example.pokemonsavemanager

import java.util.Collections

enum class BdspBattleTowerMode(val nativeId: Int, val displayName: String) {
    SINGLES(0, "Singles"), DOUBLES(1, "Doubles"),
    MASTER_SINGLES(2, "Master Singles"), MASTER_DOUBLES(3, "Master Doubles");
    companion object {
        fun fromNative(value: Int): BdspBattleTowerMode? = values().firstOrNull { it.nativeId == value }
        fun label(value: Int): String = if (value == -1) "No entry" else fromNative(value)?.displayName ?: "Unknown mode ($value)"
    }
}

data class BdspBattleTowerRecordRead(val mode: BdspBattleTowerMode, val rawClearedFlag: Int,
    val rawSuspendedFlag: Long, val battleSeed: ULong, val rank: Long, val rankDownLosses: Long,
    val trainerSeeds: List<ULong>, val totalWins: Long, val winLoopCounter: Long, val lossCounter: Long,
    val previousStreak: Long, val currentStreak: Long, val rawBytes: List<Int>) {
    val isSuspended: Boolean get() = rawSuspendedFlag == 1L
    val hasCanonicalSuspendedFlag: Boolean get() = rawSuspendedFlag in 0L..1L
}

class BdspBattleTowerRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val maximumMasterRank: Int, val rawMaximumMasterRank: Long,
    val rawCurrentMode: Int, val rawPreviousMode: Int, val battlePoints: Long,
    val dailyChallengeCount: Long, val challengeCount: Long,
    val records: List<BdspBattleTowerRecordRead>, val rawBytes: List<Int>) {
    val currentMode: BdspBattleTowerMode? get() = BdspBattleTowerMode.fromNative(rawCurrentMode)
    val previousMode: BdspBattleTowerMode? get() = BdspBattleTowerMode.fromNative(rawPreviousMode)
    fun record(mode: BdspBattleTowerMode): BdspBattleTowerRecordRead = records[mode.nativeId]
}

/** Native full-file integrity gate first. No counter normalization, rank repair, RNG advance or writer. */
object BdspBattleTowerReadOnly {
    fun read(source: ByteArray): BdspBattleTowerRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x95BF4
        val records = BdspBattleTowerMode.values().map { mode ->
            val offset = base + 0x14 + mode.nativeId * 0x68
            BdspBattleTowerRecordRead(mode, source[offset].toInt() and 255, u32(source, offset + 4),
                u64(source, offset + 8), u32(source, offset + 0x10), u32(source, offset + 0x14),
                immutable((0..6).map { u64(source, offset + 0x1C + it * 8) }),
                u32(source, offset + 0x54), u32(source, offset + 0x58), u32(source, offset + 0x5C),
                u32(source, offset + 0x60), u32(source, offset + 0x64), raw(source, offset, 0x68))
        }
        val rank = u32(source, base)
        return BdspBattleTowerRead(checked.version, checked.revision, rank.toInt(), rank,
            u32(source, base + 4).toInt(), u32(source, base + 8).toInt(), u32(source, base + 12),
            u32(source, base + 16), u32(source, base + 0x1B4), immutable(records), raw(source, base, 0x1B8))
    }
    private fun u32(b: ByteArray, offset: Int): Long = (0..3).fold(0L) { n, i ->
        n or ((b[offset + i].toLong() and 255) shl (8 * i))
    }
    private fun u64(b: ByteArray, offset: Int): ULong = (0..7).fold(0uL) { n, i ->
        n or ((b[offset + i].toInt() and 255).toULong() shl (8 * i))
    }
    private fun raw(b: ByteArray, offset: Int, size: Int): List<Int> = immutable((0 until size).map { b[offset + it].toInt() and 255 })
    private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
}
