package com.example.pokemonsavemanager

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Immutable saved metadata only; source arrays and persistence operations are never exposed. */
data class LegendsZaTrainerRead(
    val version: LegendsZaVersion, val revision: LegendsZaRevision?, val name: String, val gender: Int,
    val languageId: Int, val languageName: String, val id32: Long, val tid16: Int, val sid16: Int,
    val birthMonth: Int, val birthDay: Int, val money: Long?, val royaleTicketPoints: Long?, val infiniteTicketPoints: Long?,
    val playerHp: Long, val megaGaugePercent: Float, val wasBirthdayGiven: Boolean,
    val observedBirthday: Boolean, val observedBirthdayYear: Int
) {
    val displayTrainerId: String get() = (id32 % 1000000L).toString().padStart(6, '0')
}

/** Native trainer status and optional typed currencies after the complete container gate. */
object LegendsZaTrainerReadOnly {
    private val languages = mapOf(1 to "Japanese", 2 to "English", 3 to "French", 4 to "Italian",
        5 to "German", 7 to "Spanish", 8 to "Korean", 9 to "Chinese (Simplified)", 10 to "Chinese (Traditional)",
        11 to "Spanish (Latin America)")

    fun read(source: ByteArray): LegendsZaTrainerRead {
        val blocks = LegendsZaSaveValidation.readTrainerBlocks(source)
        val status = blocks.status
        val gender = byte(status, 5)
        require(gender in 0..1) { "The saved Legends: Z-A trainer gender is outside the known range." }
        // Z-A adds Latin American Spanish (11); never reuse the SV nine-language whitelist.
        val language = byte(status, 7)
        val languageName = languages[language]
            ?: throw IllegalArgumentException("The saved trainer language is not supported by Legends: Z-A.")
        val month = byte(status, 0x5A); val day = byte(status, 0x5B)
        val days = listOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        require((month == 0 && day == 0) || (month in 1..12 && day in 1..days[month - 1])) {
            "The saved trainer birthday is neither unset nor a valid month/day pair."
        }
        val gauge = java.lang.Float.intBitsToFloat(u32(status, 0x68).toInt())
        require(gauge.isFinite()) { "The saved trainer Mega gauge is not a finite value." }
        return LegendsZaTrainerRead(blocks.version, blocks.revision, name(status), gender, language, languageName,
            u32(status, 0), u16(status, 0), u16(status, 2), month, day,
            blocks.money, blocks.royaleTicketPoints, blocks.infiniteTicketPoints,
            u32(status, 0x70), gauge, byte(status, 0x74) != 0, byte(status, 0x75) != 0, u16(status, 0x76))
    }

    private fun name(bytes: ByteArray): String {
        val offset = 0x10
        var length = 0
        while (length < 26 && u16(bytes, offset + length) != 0) length += 2
        require(length <= 24) { "The saved trainer name has no terminator within its native 12-unit limit." }
        val text = try {
            Charsets.UTF_16LE.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, length)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("The saved trainer name contains malformed UTF-16.")
        }
        require(text.none { Character.isISOControl(it) }) { "The saved trainer name contains control characters." }
        return text
    }
    private fun byte(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 255
    private fun u16(bytes: ByteArray, offset: Int): Int = byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long = u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
}
