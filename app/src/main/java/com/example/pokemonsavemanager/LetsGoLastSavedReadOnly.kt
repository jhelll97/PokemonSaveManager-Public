package com.example.pokemonsavemanager

import java.util.Collections

class LetsGoLastSavedRead internal constructor(val version: LetsGoVersion, val rawWord: Long, val rawBytes: List<Int>) {
    val rawYear: Int get() = (rawWord and 0xFFFL).toInt()
    val year: Int get() = 1900 + rawYear
    val rawMonth: Int get() = ((rawWord ushr 12) and 15L).toInt()
    val month: Int get() = rawMonth + 1
    val day: Int get() = ((rawWord ushr 16) and 31L).toInt()
    val hour: Int get() = ((rawWord ushr 21) and 31L).toInt()
    val minute: Int get() = ((rawWord ushr 26) and 63L).toInt()
    private val maxDay: Int get() = when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    val unavailableReason: String? get() = when {
        rawWord == 0L -> "Zero saved word encodes day 0; no valid saved calendar date."
        month !in 1..12 -> "Saved month is outside 1..12."
        day !in 1..maxDay -> "Saved day is not valid for this year and month."
        hour !in 0..23 -> "Saved hour is outside 0..23."
        minute !in 0..59 -> "Saved minute is outside 0..59."
        else -> null
    }
    val calendarText: String? get() = if (unavailableReason != null) null else
        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')} ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

/** Packed calendar components, not seconds since an epoch. No time-zone or clock conversion. */
object LetsGoLastSavedReadOnly {
    fun read(source: ByteArray): LetsGoLastSavedRead {
        val checked = LetsGoSaveValidation.inspect(source)
        require(checked is LetsGoValidation.Verified) { "A fully validated native Let's Go save is required." }
        val raw = Collections.unmodifiableList((0 until 4).map { source[0x45404 + it].toInt() and 255 })
        val word = (0..3).fold(0L) { n, i -> n or (raw[i].toLong() shl (8 * i)) }
        return LetsGoLastSavedRead(checked.version, word, raw)
    }
}
