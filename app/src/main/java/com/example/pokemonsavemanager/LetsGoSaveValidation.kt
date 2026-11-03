package com.example.pokemonsavemanager

/** Native-format facts and sources are recorded in docs/SWITCH_READ_ONLY_FORMATS.md. */
enum class LetsGoVersion(val gameId: Int, val displayName: String) {
    PIKACHU(42, "Let's Go Pikachu"), EEVEE(43, "Let's Go Eevee")
}

sealed class LetsGoValidation {
    object Unrecognized : LetsGoValidation()
    data class Invalid(val reason: String) : LetsGoValidation()
    data class Verified(val version: LetsGoVersion, val checkedBlocks: Int) : LetsGoValidation()
}

/** Reads only native structure/CRCs and title ID. It never repairs or normalizes bytes. */
object LetsGoSaveValidation {
    const val FILE_SIZE = 0x100000
    private const val FOOTER = 0xB8600
    private const val MAGIC_OFFSET = FOOTER + 0x10
    private const val DESCRIPTORS = FOOTER + 0x14
    private const val STATUS_GAME = 0x1004
    private data class Block(val offset: Int, val length: Int)
    private val blocks = listOf(
        Block(0x00000, 0x00D90), Block(0x00E00, 0x00200),
        Block(0x01000, 0x00168), Block(0x01200, 0x01800),
        Block(0x02A00, 0x020E8), Block(0x04C00, 0x00930),
        Block(0x05600, 0x00004), Block(0x05800, 0x00130),
        Block(0x05A00, 0x00012), Block(0x05C00, 0x3F7A0),
        Block(0x45400, 0x00008), Block(0x45600, 0x00E90),
        Block(0x46600, 0x010A4), Block(0x47800, 0x000F0),
        Block(0x47A00, 0x06010), Block(0x4DC00, 0x00200),
        Block(0x4DE00, 0x00098), Block(0x4E000, 0x00068),
        Block(0x4E200, 0x69780), Block(0xB7A00, 0x000B0),
        Block(0xB7C00, 0x00940)
    )

    fun inspect(bytes: ByteArray): LetsGoValidation {
        // Identify a native candidate by its footer or descriptor signature, not its filename/selection.
        val magicPresent = bytes.size >= MAGIC_OFFSET + 4 && u32(bytes, MAGIC_OFFSET) == 0x42454546L
        val signature = DESCRIPTORS + 19 * 8 + 4
        val descriptorPresent = bytes.size >= signature + 2 && u16(bytes, signature) == 19
        if (!magicPresent && !descriptorPresent) return LetsGoValidation.Unrecognized
        if (bytes.size != FILE_SIZE) return LetsGoValidation.Invalid("Expected a complete 1 MiB extracted Let's Go save.")
        if (!magicPresent) return LetsGoValidation.Invalid("The Let's Go footer marker is damaged.")

        for ((id, block) in blocks.withIndex()) {
            val descriptor = DESCRIPTORS + id * 8
            if (u32(bytes, descriptor) != block.length.toLong() || u16(bytes, descriptor + 4) != id)
                return LetsGoValidation.Invalid("Block $id has an unexpected length or identity.")
            if (u16(bytes, descriptor + 6) != crc16(bytes, block.offset, block.length))
                return LetsGoValidation.Invalid("Block $id failed its native checksum.")
        }
        val version = LetsGoVersion.values().firstOrNull { it.gameId == (bytes[STATUS_GAME].toInt() and 0xFF) }
            ?: return LetsGoValidation.Invalid("The saved game ID is not Pikachu or Eevee.")
        return LetsGoValidation.Verified(version, blocks.size)
    }

    // Standard CRC-16/ARC: reflected polynomial 0xA001, initial 0, no final inversion.
    internal fun crc16(bytes: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        var crc = 0
        for (index in offset until offset + length) {
            crc = crc xor (bytes[index].toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc and 0xFFFF
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long =
        u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
}
