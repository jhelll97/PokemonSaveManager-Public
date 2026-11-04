package com.example.pokemonsavemanager

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

data class SwordShieldLeagueCardPokemon(
    val slot: Int,
    val species: Int,
    val form: Int,
    val gender: Int,
    val shiny: Int,
    val encryptionConstant: Long,
    val unknown: Long,
    val formArgument: Int
)

data class SwordShieldLeagueCardRead(
    val version: SwordShieldVersion,
    val trainerName: String,
    val languageId: Int,
    val trainerId: Long,
    val pokedexOwned: Int,
    val shinyPokemonFound: Int,
    val displayedGame: Int,
    val starter: Int,
    val curryTypesOwned: Int,
    val rotoRallyScore: Int,
    val caughtPokemon: Int,
    val galarDexComplete: Int,
    val armorDexComplete: Int,
    val crownDexComplete: Int,
    val gender: Int,
    val cardNumberBytes: ByteArray,
    val startedYear: Int,
    val startedMonth: Int,
    val startedDay: Int,
    val printedTimestamp: Long,
    val team: List<SwordShieldLeagueCardPokemon>
) {
    val displayedGameName: String? get() = when (displayedGame) {
        0 -> "Pokémon Sword"
        1 -> "Pokémon Shield"
        else -> null
    }
    val starterName: String? get() = when (starter) {
        0 -> "Grookey"
        1 -> "Scorbunny"
        2 -> "Sobble"
        else -> null
    }
    val cardNumberDisplay: String get() = cardNumberBytes.joinToString(" ") { byte ->
        val value = byte.toInt() and 0xFF
        if (value in 0x20..0x7E) "${value.toChar()} (0x${value.toString(16).uppercase().padStart(2, '0')})"
        else "0x${value.toString(16).uppercase().padStart(2, '0')}"
    }
}

/** Exact read-only projection of PKHeX TrainerCard8 at pinned commit 0660d1e1f4924b8478daec73281bb8071a82ad19. */
object SwordShieldLeagueCardReadOnly {
    private const val TEAM_OFFSET = 0xC8
    private const val TEAM_RECORD_SIZE = 0x1C

    fun read(source: ByteArray): SwordShieldLeagueCardRead {
        val block = SwordShieldSaveValidation.readLeagueCardBlock(source)
        val bytes = block.data
        require(bytes.size == 0x1D0) { "The native Sword/Shield League Card object is truncated." }
        val team = List(6) { index ->
            val offset = TEAM_OFFSET + index * TEAM_RECORD_SIZE
            SwordShieldLeagueCardPokemon(
                index + 1,
                uint16(bytes, offset),
                byte(bytes, offset + 0x04),
                byte(bytes, offset + 0x08),
                byte(bytes, offset + 0x0C),
                uint32(bytes, offset + 0x10),
                uint32(bytes, offset + 0x14),
                int32(bytes, offset + 0x18)
            )
        }
        return SwordShieldLeagueCardRead(
            block.version,
            trainerName(bytes),
            byte(bytes, 0x1B),
            uint32(bytes, 0x1C),
            uint16(bytes, 0x20),
            uint16(bytes, 0x22),
            byte(bytes, 0x24),
            byte(bytes, 0x25),
            uint16(bytes, 0x26),
            int32(bytes, 0x28),
            int32(bytes, 0x2C),
            byte(bytes, 0x30),
            byte(bytes, 0x1B4),
            byte(bytes, 0x1B5),
            byte(bytes, 0x38),
            bytes.copyOfRange(0x39, 0x3C),
            uint16(bytes, 0x170),
            byte(bytes, 0x172),
            byte(bytes, 0x173),
            uint32(bytes, 0x1A8),
            team
        )
    }

    private fun trainerName(bytes: ByteArray): String {
        var length = 0
        while (length < 0x1A && uint16(bytes, length) != 0) length += 2
        require(length <= 0x18) { "The saved League Card trainer name has no native terminator." }
        val name = try {
            Charsets.UTF_16LE.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, 0, length)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("The saved League Card trainer name contains malformed UTF-16.")
        }
        require(name.none { Character.isISOControl(it) }) {
            "The saved League Card trainer name contains control characters."
        }
        return name
    }

    private fun byte(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xFF
    private fun uint16(bytes: ByteArray, offset: Int): Int =
        byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun uint32(bytes: ByteArray, offset: Int): Long =
        uint16(bytes, offset).toLong() or (uint16(bytes, offset + 2).toLong() shl 16)
    private fun int32(bytes: ByteArray, offset: Int): Int = uint32(bytes, offset).toInt()
}
