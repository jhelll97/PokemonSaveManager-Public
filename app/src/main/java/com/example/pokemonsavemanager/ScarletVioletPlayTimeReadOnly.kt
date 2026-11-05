package com.example.pokemonsavemanager

data class ScarletVioletPlayTimeRead(
    val version: ScarletVioletVersion,
    val hours: Int,
    val minutes: Int,
    val seconds: Int
) {
    val savedTimeText: String
        get() = "${hours.toString().padStart(4, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

/** Native recorded play time with no timer advance, wall-clock calculation, or normalization. */
object ScarletVioletPlayTimeReadOnly {
    fun read(source: ByteArray): ScarletVioletPlayTimeRead {
        val block = ScarletVioletSaveValidation.readPlayTimeBlock(source)
        val bytes = block.data
        require(bytes.size == 12) { "The native Scarlet/Violet Play Time object is truncated." }
        fun int32(offset: Int): Int = (0..3).fold(0) { value, index ->
            value or ((bytes[offset + index].toInt() and 255) shl (index * 8))
        }
        val hours = int32(0)
        val minutes = int32(4)
        val seconds = int32(8)
        require(hours in 0..65535 && minutes in 0..59 && seconds in 0..59) {
            "The native Scarlet/Violet Play Time values are outside their supported ranges."
        }
        return ScarletVioletPlayTimeRead(block.version, hours, minutes, seconds)
    }
}
