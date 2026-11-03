package com.example.pokemonsavemanager

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Immutable metadata only; no source arrays, save handles, setters or persistence operations. */
data class BdspTrainerRead(
    val version: BdspVersion, val revision: BdspSaveRevision,
    val name: String, val rivalName: String, val gender: Int,
    val languageId: Int, val languageName: String,
    val id32: Long, val tid16: Int, val sid16: Int,
    val money: Long, val badgeCount: Int, val gameClear: Boolean
) {
    val displayTrainerId: String get() = (id32 % 1000000L).toString().padStart(6, '0')
}

/** Native fixed-offset metadata, gated by the complete revision/whole-file hash/title validation. */
object BdspTrainerReadOnly {
    private const val STATUS = 0x79BB4
    private const val CONFIG = 0x79B74
    private const val RIVAL = 0x55F4
    private val languages = mapOf(1 to "Japanese", 2 to "English", 3 to "French", 4 to "Italian",
        5 to "German", 7 to "Spanish", 8 to "Korean", 9 to "Chinese (Simplified)", 10 to "Chinese (Traditional)")

    fun read(source: ByteArray): BdspTrainerRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) { "A fully validated native BDSP save is required." }
        val male = byte(source, STATUS + 0x24)
        require(male in 0..1) { "The saved BDSP trainer gender flag is outside its native boolean range." }
        // BDSP stores a male boolean: 1 is male, 0 is female (opposite to generic gender IDs).
        val gender = if (male == 1) 0 else 1
        val language = u32(source, CONFIG + 4).toInt()
        val languageName = languages[language]
            ?: throw IllegalArgumentException("The saved trainer language is not supported by BDSP.")
        val money = u32(source, STATUS + 0x20)
        require(money <= 999999L) { "The saved BDSP money exceeds the native maximum." }
        val badges = byte(source, STATUS + 0x29)
        require(badges <= 8) { "The saved BDSP badge count exceeds eight." }
        val clear = byte(source, STATUS + 0x2C)
        require(clear in 0..1) { "The saved BDSP game-clear flag is outside its native boolean range." }
        return BdspTrainerRead(checked.version, checked.revision, name(source, STATUS, "trainer"),
            name(source, RIVAL, "rival"), gender, language, languageName,
            u32(source, STATUS + 0x1C), u16(source, STATUS + 0x1C), u16(source, STATUS + 0x1E),
            money, badges, clear == 1)
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
