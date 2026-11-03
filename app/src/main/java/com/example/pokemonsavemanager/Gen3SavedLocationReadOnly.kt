package com.example.pokemonsavemanager

data class Gen3SavedWarp(val mapGroup: Int, val mapNumber: Int, val warpId: Int, val x: Int, val y: Int)
data class Gen3SavedFieldState(val musicId: Int, val weather: Int, val weatherCycleStage: Int, val flashLevel: Int)
data class Gen3SavedLocation(
    val playerX: Int, val playerY: Int, val location: Gen3SavedWarp,
    val lastHeal: Gen3SavedWarp, val mapLayoutId: Int, val fieldState: Gen3SavedFieldState,
    val dynamicWarp: Gen3SavedWarp, val warpAt0C: Gen3SavedWarp, val warpAt24: Gen3SavedWarp
)

/** Read-only SaveBlock1 fields shared by R/S/E and FR/LG; preserve signed native sentinels. */
object Gen3SavedLocationReadOnly {
    const val SECTION_ID = 1

    fun read(section1: ByteArray): Gen3SavedLocation {
        require(section1.size == 0xF80) { "Use a complete validated Gen 3 SaveBlock1 section." }
        fun u16(offset: Int): Int = (section1[offset].toInt() and 255) or
            ((section1[offset + 1].toInt() and 255) shl 8)
        fun s16(offset: Int): Int = u16(offset).toShort().toInt()
        fun warp(offset: Int): Gen3SavedWarp = Gen3SavedWarp(
            section1[offset].toInt(), section1[offset + 1].toInt(), section1[offset + 2].toInt(),
            s16(offset + 4), s16(offset + 6))
        return Gen3SavedLocation(s16(0), s16(2), warp(0x04), warp(0x1C), u16(0x32),
            Gen3SavedFieldState(u16(0x2C), section1[0x2E].toInt() and 255,
                section1[0x2F].toInt() and 255, section1[0x30].toInt() and 255),
            warp(0x14), warp(0x0C), warp(0x24))
    }
}
