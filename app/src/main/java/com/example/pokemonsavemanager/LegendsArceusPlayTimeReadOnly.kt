package com.example.pokemonsavemanager

data class LegendsArceusPlayTimeRead(
    val version: LegendsArceusVersion,
    val hours: Int,
    val minutes: Int,
    val seconds: Int
) {
    val savedTimeText: String
        get() = "${hours.toString().padStart(4, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

/** Native recorded play time with no timer advance, wall-clock calculation, or normalization. */
object LegendsArceusPlayTimeReadOnly {
    fun read(source: ByteArray): LegendsArceusPlayTimeRead {
        val block = LegendsArceusSaveValidation.readPlayTimeBlock(source)
        val bytes = block.data
        require(bytes.size == 4) { "The native Legends: Arceus Play Time object is truncated." }
        val hours = (bytes[0].toInt() and 255) or ((bytes[1].toInt() and 255) shl 8)
        val minutes = bytes[2].toInt() and 255
        val seconds = bytes[3].toInt() and 255
        require(minutes in 0..59 && seconds in 0..59) {
            "The native Legends: Arceus Play Time minutes or seconds are outside 0–59."
        }
        return LegendsArceusPlayTimeRead(block.version, hours, minutes, seconds)
    }
}
