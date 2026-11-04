package com.example.pokemonsavemanager

import java.util.Collections

/** Stored-only PB7 summary. The native Day Care record has no party-stat tail. */
data class LetsGoDayCarePokemonRead(
    val species: Int, val form: Int, val gender: Int, val experience: Long, val nature: Int,
    val tid: Int, val sid: Int, val pid: Long, val encryptionConstant: Long
)

class LetsGoDayCareRead internal constructor(
    val version: LetsGoVersion, val isOccupied: Boolean, val rawOccupiedByte: Int, val stepCounter: Long,
    val rawBlockBytes: List<Int>, val rawHeaderBytes: List<Int>,
    val rawStoredBytes: List<Int>, val pokemon: LetsGoDayCarePokemonRead?, val recordUnavailableReason: String?
) {
    val isZeroStoredRecord: Boolean get() = rawStoredBytes.all { it == 0 }
}

/** Block 13: native occupied flag, step counter and one encrypted 232-byte stored PB7. */
object LetsGoDayCareReadOnly {
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun read(source: ByteArray): LetsGoDayCareRead {
        val checked = LetsGoSaveValidation.inspect(source)
        require(checked is LetsGoValidation.Verified) { "A fully validated native Let's Go save is required." }
        val block = source.copyOfRange(0x47800, 0x478F0)
        fun frozen(from: Int, end: Int): List<Int> = Collections.unmodifiableList(
            (from until end).map { block[it].toInt() and 255 })
        val stored = block.copyOfRange(8, 240)
        val rawOccupiedByte = block[0].toInt() and 255
        val stepCounter = u32(block, 4)
        val zero = stored.all { it == 0.toByte() }
        var reason: String? = null
        val pokemon = if (zero) null else try { decodeStored(stored) } catch (e: IllegalArgumentException) {
            reason = e.message; null
        }
        return LetsGoDayCareRead(checked.version, rawOccupiedByte != 0, rawOccupiedByte, stepCounter,
            frozen(0, 240), frozen(0, 8), frozen(8, 240), pokemon, reason)
    }

    private fun decodeStored(record: ByteArray): LetsGoDayCarePokemonRead {
        require(u16(record, 4) == 0) { "Stored PB7 sanity field is not zero." }
        val ec = u32(record, 0)
        val decrypted = record.copyOf()
        var state = ec
        for (offset in 8 until 232 step 2) {
            state = (state * 0x41C64E6DL + 0x6073L) and 0xFFFFFFFFL
            val word = u16(decrypted, offset) xor (state ushr 16).toInt()
            decrypted[offset] = word.toByte(); decrypted[offset + 1] = (word ushr 8).toByte()
        }
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val plain = decrypted.copyOf()
        for (canonical in 0..3) {
            val from = 8 + (order[canonical] - '0') * 56
            decrypted.copyInto(plain, 8 + canonical * 56, from, from + 56)
        }
        var checksum = 0
        for (offset in 8 until 232 step 2) checksum += u16(plain, offset)
        require((checksum and 65535) == u16(record, 6)) { "Stored PB7 checksum failed." }
        val species = u16(plain, 8)
        require(species in 1..151 || species == 808 || species == 809) {
            "Unsupported or empty Let's Go stored species ID $species."
        }
        return LetsGoDayCarePokemonRead(species, (plain[0x1D].toInt() and 255) ushr 3,
            ((plain[0x1D].toInt() and 255) ushr 1) and 3, u32(plain, 0x10), plain[0x1C].toInt() and 255,
            u16(plain, 0x0C), u16(plain, 0x0E), u32(plain, 0x18), ec)
    }

    private fun u16(b: ByteArray, o: Int): Int = (b[o].toInt() and 255) or ((b[o + 1].toInt() and 255) shl 8)
    private fun u32(b: ByteArray, o: Int): Long = u16(b, o).toLong() or (u16(b, o + 2).toLong() shl 16)
}
