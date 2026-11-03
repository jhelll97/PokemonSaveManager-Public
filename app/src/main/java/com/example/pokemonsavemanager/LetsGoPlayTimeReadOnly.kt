package com.example.pokemonsavemanager

import java.util.Collections

class LetsGoPlayTimeRead internal constructor(val version: LetsGoVersion, val hours: Int,
    val minutes: Int, val seconds: Int, val rawLastSavedWord: Long, val rawBlockBytes: List<Int>) {
    val hasCanonicalMinutesAndSeconds: Boolean get() = minutes in 0..59 && seconds in 0..59
    val savedTimeText: String get() = "${hours.toString().padStart(4, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

/** Native block 10 only after all 21 descriptors/CRCs and game ID pass. No timer or writer. */
object LetsGoPlayTimeReadOnly {
    fun read(source: ByteArray): LetsGoPlayTimeRead {
        val checked = LetsGoSaveValidation.inspect(source)
        require(checked is LetsGoValidation.Verified) { "A fully validated native Let's Go save is required." }
        val base = 0x45400
        val raw = Collections.unmodifiableList((0 until 8).map { source[base + it].toInt() and 255 })
        val lastSaved = (0..3).fold(0L) { n, i -> n or (raw[4 + i].toLong() shl (8 * i)) }
        return LetsGoPlayTimeRead(checked.version, raw[0] or (raw[1] shl 8), raw[2], raw[3], lastSaved, raw)
    }
}
