package com.example.pokemonsavemanager

data class LetsGoPlayerLocationRead internal constructor(
    val version: LetsGoVersion,
    val x: Float, val z: Float, val y: Float,
    val scaleX: Float, val scaleZ: Float, val scaleY: Float,
    val rotationX: Float, val rotationZ: Float, val rotationY: Float, val rotationW: Float,
    val mapId: ULong,
    val positionBits: List<Long>, val scaleBits: List<Long>, val rotationBits: List<Long>,
    val mapBytes: List<Int>
) {
    val allTransformsFinite: Boolean get() = listOf(
        x, z, y, scaleX, scaleZ, scaleY, rotationX, rotationZ, rotationY, rotationW
    ).all { it.isFinite() }
}

/** Native block 14 coordinates from PKHeX Coordinates7b at pinned commit 0a7854f. */
object LetsGoPlayerLocationReadOnly {
    private const val BLOCK = 0x47A00

    fun read(source: ByteArray): LetsGoPlayerLocationRead {
        val checked = LetsGoSaveValidation.inspect(source)
        require(checked is LetsGoValidation.Verified) { "A fully validated native Let's Go save is required." }
        fun u32(relative: Int): Long = (0..3).fold(0L) { value, index ->
            value or ((source[BLOCK + relative + index].toInt() and 255).toLong() shl (8 * index))
        }
        fun f32(relative: Int): Float = Float.fromBits(u32(relative).toInt())
        fun bits(vararg relative: Int): List<Long> = relative.map(::u32)
        val mapOffset = BLOCK + 0x6000
        val mapBytes = (0 until 8).map { source[mapOffset + it].toInt() and 255 }
        val mapId = mapBytes.foldIndexed(0uL) { index, value, byte ->
            value or (byte.toULong() shl (8 * index))
        }
        return LetsGoPlayerLocationRead(
            checked.version,
            f32(0x10), f32(0x14), f32(0x18),
            f32(0x20), f32(0x24), f32(0x28),
            f32(0x30), f32(0x34), f32(0x38), f32(0x3C),
            mapId,
            bits(0x10, 0x14, 0x18), bits(0x20, 0x24, 0x28),
            bits(0x30, 0x34, 0x38, 0x3C), mapBytes
        )
    }
}
