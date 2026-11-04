package com.example.pokemonsavemanager

/** Exact PKHeX-compatible Sword/Shield playtime projection from its native four-byte counter. */
data class SwordShieldPlayTimeRead(
    val version: SwordShieldVersion,
    val hours: Int,
    val minutes: Int,
    val seconds: Int
) {
    val formatted: String get() = "%04d:%02d:%02d".format(hours, minutes, seconds)
}

object SwordShieldPlayTimeReadOnly {
    fun read(source: ByteArray): SwordShieldPlayTimeRead {
        val block = SwordShieldSaveValidation.readPlayTimeBlock(source)
        val bytes = block.value
        require(bytes.size == 4) { "A native Sword/Shield playtime record is truncated." }
        val hours = u16(bytes, 0)
        val minutes = u8(bytes, 2)
        val seconds = u8(bytes, 3)
        require(minutes in 0..59 && seconds in 0..59) {
            "The saved Sword/Shield playtime minute or second field is outside its documented range."
        }
        return SwordShieldPlayTimeRead(block.version, hours, minutes, seconds)
    }

    private fun u8(bytes: ByteArray, offset: Int) = bytes[offset].toInt() and 0xFF
    private fun u16(bytes: ByteArray, offset: Int) = u8(bytes, offset) or (u8(bytes, offset + 1) shl 8)
}
