package com.example.pokemonsavemanager

import java.util.Collections

class LetsGoGameOptionsRead internal constructor(val version: LetsGoVersion, val rawWord: Long, val rawBytes: List<Int>) {
    val signedConfigValue: Int get() = rawWord.toInt()
    val textSpeedId: Int get() = (rawWord and 3L).toInt()
    val battleAnimationId: Int get() = ((rawWord ushr 2) and 1L).toInt()
    val animationsEnabled: Boolean get() = battleAnimationId == 0
    val battleStyleId: Int get() = ((rawWord ushr 3) and 1L).toInt()
    val rawLanguageIndex: Int get() = ((rawWord ushr 4) and 15L).toInt()
    val languageId: Int get() = if (rawLanguageIndex >= 6) rawLanguageIndex + 1 else rawLanguageIndex
    val languageName: String? get() = when (languageId) {
        1 -> "Japanese"; 2 -> "English"; 3 -> "French"; 4 -> "Italian"; 5 -> "German"; 7 -> "Spanish"
        8 -> "Korean"; 9 -> "Chinese (Simplified)"; 10 -> "Chinese (Traditional)"; else -> null
    }
    val unknownUpperBits: Long get() = rawWord ushr 8
}

/** Native four-byte configuration only; text-speed choices and upper-bit meanings are not inferred. */
object LetsGoGameOptionsReadOnly {
    fun read(source: ByteArray): LetsGoGameOptionsRead {
        val checked = LetsGoSaveValidation.inspect(source)
        require(checked is LetsGoValidation.Verified) { "A fully validated native Let's Go save is required." }
        val raw = Collections.unmodifiableList((0 until 4).map { source[0x5600 + it].toInt() and 255 })
        val word = (0..3).fold(0L) { n, i -> n or (raw[i].toLong() shl (8 * i)) }
        return LetsGoGameOptionsRead(checked.version, word, raw)
    }
}
