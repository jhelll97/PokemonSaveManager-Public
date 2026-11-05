package com.example.pokemonsavemanager

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Immutable metadata only; source bytes and save-writing operations are never exposed. */
data class LetsGoTrainerRead(
    val version: LetsGoVersion, val name: String, val gender: Int,
    val languageId: Int, val languageName: String,
    val id32: Long, val tid16: Int, val sid16: Int,
    val rivalName: String, val money: Long,
    val badgeMask: Int,
    val trainerClassIndex: Int, val overworldGenderId: Int,
    val starterChoiceId: Int, val starterGenderId: Int
) {
    val displayTrainerId: String get() = (id32 % 1000000L).toString().padStart(6, '0')
    val badgeCount: Int get() = Integer.bitCount(badgeMask)
}

/** Reads fixed native status, misc and Gym-clear event fields after every native descriptor and CRC passes. */
object LetsGoTrainerReadOnly {
    private const val STATUS = 0x1000
    private const val MISC = 0x4C00
    private const val EVENT_WORK = 0x1200
    private const val EVENT_FLAG_OFFSET = EVENT_WORK + 1000 * 4
    private const val FIRST_BADGE_FLAG = 0x08C
    private val languages = mapOf(1 to "Japanese", 2 to "English", 3 to "French", 4 to "Italian",
        5 to "German", 7 to "Spanish", 8 to "Korean", 9 to "Chinese (Simplified)", 10 to "Chinese (Traditional)")

    fun read(source: ByteArray): LetsGoTrainerRead {
        val checked = LetsGoSaveValidation.inspect(source)
        require(checked is LetsGoValidation.Verified) { "A fully validated native Let's Go save is required." }
        val gender = byte(source, STATUS + 5)
        require(gender in 0..1) { "The saved trainer gender is outside the known Let's Go range." }
        val language = byte(source, STATUS + 0x35)
        val languageName = languages[language]
            ?: throw IllegalArgumentException("The saved trainer language is not supported by Let's Go.")
        return LetsGoTrainerRead(checked.version, name(source, STATUS + 0x38, "trainer"), gender,
            language, languageName, u32(source, STATUS), u16(source, STATUS), u16(source, STATUS + 2),
            name(source, MISC + 0x200, "rival"), u32(source, MISC + 4),
            badgeMask(source),
            byte(source, STATUS + 0x76), byte(source, STATUS + 0x108),
            byte(source, STATUS + 0xB8), byte(source, STATUS + 0xB9))
    }

    private fun badgeMask(bytes: ByteArray): Int = (0 until 8).fold(0) { mask, index ->
        val flag = FIRST_BADGE_FLAG + index
        val value = byte(bytes, EVENT_FLAG_OFFSET + (flag ushr 3))
        if (value and (1 shl (flag and 7)) != 0) mask or (1 shl index) else mask
    }

    private fun name(bytes: ByteArray, offset: Int, field: String): String {
        var length = 0
        while (length < 26 && u16(bytes, offset + length) != 0) length += 2
        require(length <= 24) { "The saved $field name has no terminator within its native 12-unit limit." }
        val text = try {
            Charsets.UTF_16LE.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, length)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("The saved $field name contains malformed UTF-16.")
        }
        require(text.none { Character.isISOControl(it) }) { "The saved $field name contains control characters." }
        return text
    }
    private fun byte(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 255
    private fun u16(bytes: ByteArray, offset: Int): Int = byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long = u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
}
