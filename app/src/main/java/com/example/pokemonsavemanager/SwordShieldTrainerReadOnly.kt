package com.example.pokemonsavemanager

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Metadata only; no byte arrays, save handles, setters or persistence operations. */
data class SwordShieldTrainerRead(
    val version: SwordShieldVersion, val name: String, val gender: Int,
    val languageId: Int, val languageName: String, val jerseyNumber: String,
    val id32: Long, val tid16: Int, val sid16: Int, val money: Long, val watts: Long
) {
    val displayTrainerId: String get() = (id32 % 1000000L).toString().padStart(6, '0')
}

/** Reads only the required native status object after the full container gate passes. */
object SwordShieldTrainerReadOnly {
    private val languages = mapOf(1 to "Japanese", 2 to "English", 3 to "French", 4 to "Italian",
        5 to "German", 7 to "Spanish", 8 to "Korean", 9 to "Chinese (Simplified)", 10 to "Chinese (Traditional)")

    fun read(source: ByteArray): SwordShieldTrainerRead {
        val block = SwordShieldSaveValidation.readTrainerBlock(source)
        val bytes = block.status
        val gender = byte(bytes, 0xA5)
        require(gender in 0..1) { "The saved trainer gender is outside the known Sword/Shield range." }
        val language = byte(bytes, 0xA7)
        val languageName = languages[language]
            ?: throw IllegalArgumentException("The saved trainer language is not supported by Sword/Shield.")
        val watts = uint32(bytes, 0xD0)
        require(watts <= 9999999L) { "The saved Watt balance exceeds the native maximum." }
        require(block.money <= 9999999L) { "The saved money exceeds the native maximum." }
        val jersey = StringBuilder()
        var terminated = false
        for (offset in 1..3) {
            val value = byte(bytes, offset)
            if (value == 0) terminated = true
            else {
                require(!terminated && value in 48..57) { "The saved jersey number is not a bounded decimal string." }
                jersey.append(value.toChar())
            }
        }
        var length = 0
        while (length < 26 && uint16(bytes, 0xB0 + length) != 0) length += 2
        require(length <= 24) { "The saved trainer name has no terminator within its native 12-unit limit." }
        val name = try {
            Charsets.UTF_16LE.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, 0xB0, length)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("The saved trainer name contains malformed UTF-16.")
        }
        require(name.none { Character.isISOControl(it) }) { "The saved trainer name contains control characters." }
        return SwordShieldTrainerRead(block.version, name, gender, language, languageName, jersey.toString(),
            uint32(bytes, 0xA0), uint16(bytes, 0xA0), uint16(bytes, 0xA2), block.money, watts)
    }

    private fun byte(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 255
    private fun uint16(bytes: ByteArray, offset: Int): Int = byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun uint32(bytes: ByteArray, offset: Int): Long = uint16(bytes, offset).toLong() or (uint16(bytes, offset + 2).toLong() shl 16)
}
