package com.example.pokemonsavemanager

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Immutable metadata only; no source arrays, save handles or save-writing operations. */
data class LegendsArceusTrainerRead(
    val version: LegendsArceusVersion, val name: String, val gender: Int,
    val languageId: Int, val languageName: String, val id32: Long, val tid16: Int, val sid16: Int,
    val money: Long?, val meritCurrent: Long?, val meritTotal: Long?,
    val satchelUpgrades: Long?, val expeditionRank: Long?
) {
    val displayTrainerId: String get() = (id32 % 1000000L).toString().padStart(6, '0')
}

/** Native trainer status and optional typed counters, after the complete container gate. */
object LegendsArceusTrainerReadOnly {
    private val languages = mapOf(1 to "Japanese", 2 to "English", 3 to "French", 4 to "Italian",
        5 to "German", 7 to "Spanish", 8 to "Korean", 9 to "Chinese (Simplified)", 10 to "Chinese (Traditional)")

    fun read(source: ByteArray): LegendsArceusTrainerRead {
        val blocks = LegendsArceusSaveValidation.readTrainerBlocks(source)
        val status = blocks.status
        val gender = byte(status, 0x15)
        require(gender in 0..1) { "The saved Arceus trainer gender is outside the known range." }
        // Use saved status language IDs; do not substitute the separately shifted runtime language counter.
        val language = byte(status, 0x17)
        val languageName = languages[language]
            ?: throw IllegalArgumentException("The saved trainer language is not supported by Legends: Arceus.")
        blocks.money?.let { require(it <= 9999999L) { "The saved Arceus money exceeds the native maximum." } }
        return LegendsArceusTrainerRead(blocks.version, name(status), gender, language, languageName,
            u32(status, 0x10), u16(status, 0x10), u16(status, 0x12), blocks.money,
            blocks.meritCurrent, blocks.meritTotal, blocks.satchelUpgrades, blocks.expeditionRank)
    }

    private fun name(bytes: ByteArray): String {
        val offset = 0x20
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

