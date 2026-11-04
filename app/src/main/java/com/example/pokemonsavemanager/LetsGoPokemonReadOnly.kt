package com.example.pokemonsavemanager

import java.util.Collections

data class LetsGoMoveRead(val id: Int, val pp: Int)
data class LetsGoPokemonRead(
    val species: Int, val nickname: String, val form: Int, val gender: Int,
    val cachedLevel: Int, val experience: Long, val nature: Int,
    val currentHp: Int, val maximumHp: Int, val originalTrainer: String,
    val tid: Int, val sid: Int, val pid: Long, val encryptionConstant: Long,
    val shiny: Boolean, val isEgg: Boolean, val fatefulEncounter: Boolean, val isPartnerForm: Boolean,
    val moves: List<LetsGoMoveRead>, val ivs: List<Int>, val awakened: List<Int>
)

sealed class LetsGoSlotRead {
    object Empty : LetsGoSlotRead()
    data class Invalid(val reason: String) : LetsGoSlotRead()
    data class Pokemon(val value: LetsGoPokemonRead) : LetsGoSlotRead()
}

/** PB7 native save records are always encrypted, 260 bytes each. No plaintext fallback. */
object LetsGoPokemonReadOnly {
    const val RECORD_SIZE = 260
    // The native four-block order selected by encryption-constant bits 13..17.
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun read(record: ByteArray): LetsGoSlotRead {
        if (record.size != RECORD_SIZE) return LetsGoSlotRead.Invalid("Expected a complete 260-byte PB7 record.")
        if (record.all { it == 0.toByte() }) return LetsGoSlotRead.Empty
        if (u16(record, 4) != 0) return LetsGoSlotRead.Invalid("PB7 sanity field is not zero.")
        val ec = u32(record, 0)
        val decrypted = record.copyOf()
        xorWords(decrypted, 8, 0xE8, ec)
        xorWords(decrypted, 0xE8, RECORD_SIZE, ec)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val plain = decrypted.copyOf()
        for (block in 0..3) {
            val from = 8 + (order[block] - '0') * 56
            decrypted.copyInto(plain, 8 + block * 56, from, from + 56)
        }
        var checksum = 0
        for (offset in 8 until 0xE8 step 2) checksum += u16(plain, offset)
        if ((checksum and 0xFFFF) != u16(record, 6)) return LetsGoSlotRead.Invalid("PB7 checksum failed.")
        val species = u16(plain, 8)
        if (species !in 1..151 && species != 808 && species != 809)
            return LetsGoSlotRead.Invalid("Unsupported or empty Let's Go species ID $species.")
        val form = (plain[0x1D].toInt() and 255) ushr 3
        val iv32 = u32(plain, 0x74)
        val pid = u32(plain, 0x18)
        val tid = u16(plain, 0x0C)
        val sid = u16(plain, 0x0E)
        val shinyXor = tid xor sid xor (pid ushr 16).toInt() xor (pid.toInt() and 0xFFFF)
        return LetsGoSlotRead.Pokemon(LetsGoPokemonRead(
            species, text(plain, 0x40), form, ((plain[0x1D].toInt() and 255) ushr 1) and 3,
            plain[0xEC].toInt() and 255, u32(plain, 0x10), plain[0x1C].toInt() and 255,
            u16(plain, 0xF0), u16(plain, 0xF2), text(plain, 0xB0), tid, sid, pid, ec,
            shinyXor < 16, iv32 and 0x40000000L != 0L, plain[0x1D].toInt() and 1 != 0,
            (species == 25 && form == 8) || (species == 133 && form == 1),
            Collections.unmodifiableList((0..3).map { LetsGoMoveRead(u16(plain, 0x5A + it * 2), plain[0x62 + it].toInt() and 255) }),
            Collections.unmodifiableList((0..5).map { ((iv32 ushr (it * 5)) and 31).toInt() }),
            Collections.unmodifiableList((0..5).map { plain[0x24 + it].toInt() and 255 })
        ))
    }

    private fun xorWords(bytes: ByteArray, from: Int, endExclusive: Int, seed: Long) {
        var state = seed
        for (offset in from until endExclusive step 2) {
            state = (state * 0x41C64E6DL + 0x6073L) and 0xFFFFFFFFL
            val word = u16(bytes, offset) xor (state ushr 16).toInt()
            bytes[offset] = word.toByte()
            bytes[offset + 1] = (word ushr 8).toByte()
        }
    }
    private fun text(bytes: ByteArray, offset: Int): String {
        var length = 0
        while (length < 26 && u16(bytes, offset + length) != 0) length += 2
        return String(bytes, offset, length, Charsets.UTF_16LE)
    }
    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long =
        u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
}

data class LetsGoStorageSlot(val index: Int, val read: LetsGoSlotRead, val partyPositions: List<Int>, val partner: Boolean)
data class LetsGoPartySlot(val position: Int, val storage: LetsGoStorageSlot?)

class LetsGoStorageRead internal constructor(
    private val records: List<LetsGoSlotRead>,
    private val pointers: List<Int?>,
    val partnerIndex: Int?
) {
    val count: Int get() = records.size
    fun slot(index: Int): LetsGoStorageSlot {
        require(index in 0 until 1000)
        return LetsGoStorageSlot(index, records.getOrNull(index) ?: LetsGoSlotRead.Empty,
            Collections.unmodifiableList((0..5).filter { pointers[it] == index }.map { it + 1 }), partnerIndex == index)
    }
    fun page(index: Int): List<LetsGoStorageSlot> {
        require(index in 0 until 40)
        return Collections.unmodifiableList((index * 25 until (index + 1) * 25).map { slot(it) })
    }
    fun party(): List<LetsGoPartySlot> = Collections.unmodifiableList((0..5).map {
        LetsGoPartySlot(it + 1, pointers[it]?.let { index -> slot(index) })
    })
}

/** Validates native pointers and reads the shared pool without compacting or changing it. */
object LetsGoStorageReadOnly {
    fun read(bytes: ByteArray): LetsGoStorageRead {
        require(LetsGoSaveValidation.inspect(bytes) is LetsGoValidation.Verified) { "A validated native Let's Go save is required." }
        fun word(offset: Int): Int = (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
        val count = word(0x5A0E)
        require(count in 0..1000) { "The native storage count exceeds 1,000 slots." }
        val pointers = (0..6).map { index ->
            val value = word(0x5A00 + index * 2)
            if (value == 1001) null else {
                require(value in 0 until count) { "Team/partner pointer ${index + 1} is outside the stored pool." }
                value
            }
        }
        val occupied = pointers.take(6).filterNotNull()
        require(occupied.toSet().size == occupied.size) { "Duplicate native party pointers." }
        val records = (0 until count).map { index ->
            val offset = 0x5C00 + index * LetsGoPokemonReadOnly.RECORD_SIZE
            when (val result = LetsGoPokemonReadOnly.read(bytes.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE))) {
                LetsGoSlotRead.Empty -> LetsGoSlotRead.Invalid("Empty record inside the stored list.")
                else -> result
            }
        }
        return LetsGoStorageRead(Collections.unmodifiableList(records), Collections.unmodifiableList(pointers.take(6)), pointers[6])
    }
}
