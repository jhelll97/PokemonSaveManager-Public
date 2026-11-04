package com.example.pokemonsavemanager

import java.util.Collections

data class SwordShieldDaycareSlotRead(
    val slot: Int,
    val rawOccupiedByte: Int,
    val pokemon: SwordShieldDaycarePokemonRead
) {
    val isOccupied: Boolean get() = rawOccupiedByte == 1
    val hasCanonicalOccupiedByte: Boolean get() = rawOccupiedByte in 0..1
}

data class SwordShieldDaycarePokemonSummary(
    val species: Int,
    val nickname: String,
    val form: Int,
    val gender: Int,
    val shiny: Boolean,
    val isEgg: Boolean
)

sealed class SwordShieldDaycarePokemonRead {
    object Empty : SwordShieldDaycarePokemonRead()
    data class Invalid(val reason: String) : SwordShieldDaycarePokemonRead()
    data class Pokemon(val value: SwordShieldDaycarePokemonSummary) : SwordShieldDaycarePokemonRead()
}

data class SwordShieldNurseryRead(
    val nursery: Int,
    val slots: List<SwordShieldDaycareSlotRead>,
    val unknownPrefixWord: Int,
    val unknownValueWord: Long,
    val rngSeed: ULong,
    val unknownTailBytes: List<Int>
)

data class SwordShieldDaycareRead(
    val version: SwordShieldVersion,
    val nurseries: List<SwordShieldNurseryRead>
)

/** Exact read-only projection of PKHeX's documented two-nursery Sword/Shield object. */
object SwordShieldDaycareReadOnly {
    private const val RECORD_SIZE = 0x148
    private const val SLOT_SIZE = 1 + RECORD_SIZE
    private const val METADATA_SIZE = 0x26
    private const val NURSERY_SIZE = (2 * SLOT_SIZE) + METADATA_SIZE
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun read(source: ByteArray): SwordShieldDaycareRead {
        val block = SwordShieldSaveValidation.readDaycareBlock(source)
        require(block.data.size == NURSERY_SIZE * 2) { "The native Sword/Shield nursery object is truncated." }
        val nurseries = (0 until 2).map { index ->
            val base = index * NURSERY_SIZE
            val slots = (0 until 2).map { slot ->
                val offset = base + slot * SLOT_SIZE
                val record = block.data.copyOfRange(offset + 1, offset + 1 + RECORD_SIZE)
                SwordShieldDaycareSlotRead(
                    slot + 1,
                    byte(block.data, offset),
                    readPokemon(record)
                )
            }
            val metadata = base + 2 * SLOT_SIZE
            SwordShieldNurseryRead(
                index + 1,
                immutable(slots),
                uint16(block.data, metadata),
                uint32(block.data, metadata + 2),
                uint64(block.data, metadata + 6),
                immutable((metadata + 14 until metadata + METADATA_SIZE).map { byte(block.data, it) })
            )
        }
        return SwordShieldDaycareRead(block.version, immutable(nurseries))
    }

    private fun readPokemon(record: ByteArray): SwordShieldDaycarePokemonRead {
        if (record.size != RECORD_SIZE)
            return SwordShieldDaycarePokemonRead.Invalid("Expected a complete 328-byte stored PK8 record.")
        if (record.all { it == 0.toByte() }) return SwordShieldDaycarePokemonRead.Empty
        if (uint16(record, 4) != 0)
            return SwordShieldDaycarePokemonRead.Invalid("Stored PK8 sanity field is not zero.")
        val ec = uint32(record, 0)
        val decoded = record.copyOf()
        xorWords(decoded, 8, RECORD_SIZE, ec)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val plain = decoded.copyOf()
        for (block in 0..3) {
            val from = 8 + (order[block] - '0') * 80
            decoded.copyInto(plain, 8 + block * 80, from, from + 80)
        }
        var checksum = 0
        for (offset in 8 until RECORD_SIZE step 2) checksum += uint16(plain, offset)
        if ((checksum and 0xFFFF) != uint16(record, 6))
            return SwordShieldDaycarePokemonRead.Invalid("Stored PK8 checksum failed.")
        val species = uint16(plain, 8)
        if (species == 0) return SwordShieldDaycarePokemonRead.Empty
        if (species !in 1..898)
            return SwordShieldDaycarePokemonRead.Invalid("Unsupported Sword/Shield species ID $species.")
        val pid = uint32(plain, 0x1C)
        val tid = uint16(plain, 0x0C)
        val sid = uint16(plain, 0x0E)
        val shiny = (tid xor sid xor (pid ushr 16).toInt() xor (pid.toInt() and 0xFFFF)) < 16
        val iv32 = uint32(plain, 0x8C)
        return SwordShieldDaycarePokemonRead.Pokemon(SwordShieldDaycarePokemonSummary(
            species,
            text(plain, 0x58),
            byte(plain, 0x24),
            (byte(plain, 0x22) ushr 2) and 3,
            shiny,
            iv32 and 0x40000000L != 0L
        ))
    }

    private fun xorWords(bytes: ByteArray, from: Int, end: Int, seed: Long) {
        var state = seed
        for (offset in from until end step 2) {
            state = (state * 0x41C64E6DL + 0x6073L) and 0xFFFFFFFFL
            val word = uint16(bytes, offset) xor (state ushr 16).toInt()
            bytes[offset] = word.toByte()
            bytes[offset + 1] = (word ushr 8).toByte()
        }
    }

    private fun text(bytes: ByteArray, offset: Int): String {
        var length = 0
        while (length < 26 && uint16(bytes, offset + length) != 0) length += 2
        return String(bytes, offset, length, Charsets.UTF_16LE)
    }

    private fun byte(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xFF
    private fun uint16(bytes: ByteArray, offset: Int): Int =
        byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun uint32(bytes: ByteArray, offset: Int): Long =
        uint16(bytes, offset).toLong() or (uint16(bytes, offset + 2).toLong() shl 16)
    private fun uint64(bytes: ByteArray, offset: Int): ULong = (0 until 8).fold(0uL) { value, index ->
        value or (byte(bytes, offset + index).toULong() shl (index * 8))
    }
    private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(values)
}
