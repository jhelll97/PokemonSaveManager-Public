package com.example.pokemonsavemanager

import java.util.Collections

data class LegendsArceusMoveRead(val id: Int, val pp: Int)
data class LegendsArceusPokemonRead(
    val species: Int, val nickname: String, val form: Int, val gender: Int,
    val cachedLevel: Int?, val experience: Long, val nature: Int, val statNature: Int,
    val currentHp: Int, val maximumHp: Int?, val originalTrainer: String,
    val tid: Int, val sid: Int, val pid: Long, val encryptionConstant: Long,
    val shiny: Boolean, val isEgg: Boolean, val isAlpha: Boolean, val isNoble: Boolean,
    val ball: Int, val originalTrainerFriendship: Int, val handlingTrainerFriendship: Int,
    val currentHandler: Int, val moves: List<LegendsArceusMoveRead>, val ivs: List<Int>,
    val ganbaruValues: List<Int>, val relearnMoves: List<Int>, val cachedStats: List<Int>?
)

sealed class LegendsArceusSlotRead {
    object Empty : LegendsArceusSlotRead()
    data class Invalid(val reason: String) : LegendsArceusSlotRead()
    data class Pokemon(val value: LegendsArceusPokemonRead) : LegendsArceusSlotRead()
}

/** Native PA8 only: 360-byte pasture records and 376-byte party records. No plaintext fallback. */
object LegendsArceusPokemonReadOnly {
    const val STORED_SIZE = 0x168
    const val PARTY_SIZE = 0x178
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun read(record: ByteArray): LegendsArceusSlotRead {
        if (record.size != STORED_SIZE && record.size != PARTY_SIZE)
            return LegendsArceusSlotRead.Invalid("Expected a complete 360-byte stored or 376-byte party PA8 record.")
        if (record.all { it == 0.toByte() }) return LegendsArceusSlotRead.Empty
        if (u16(record, 4) != 0) return LegendsArceusSlotRead.Invalid("PA8 sanity field is not zero.")
        val ec = u32(record, 0)
        val decoded = record.copyOf()
        xorWords(decoded, 8, STORED_SIZE, ec)
        if (record.size == PARTY_SIZE) xorWords(decoded, STORED_SIZE, PARTY_SIZE, ec)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val plain = decoded.copyOf()
        for (block in 0..3) {
            val from = 8 + (order[block] - '0') * 88
            decoded.copyInto(plain, 8 + block * 88, from, from + 88)
        }
        var checksum = 0
        for (offset in 8 until STORED_SIZE step 2) checksum += u16(plain, offset)
        if ((checksum and 65535) != u16(record, 6)) return LegendsArceusSlotRead.Invalid("PA8 checksum failed.")
        val species = u16(plain, 8)
        if (species == 0) return LegendsArceusSlotRead.Empty
        if (species !in 1..905) return LegendsArceusSlotRead.Invalid("Unsupported Legends: Arceus species ID $species.")
        val iv32 = u32(plain, 0x94)
        val pid = u32(plain, 0x1C)
        val tid = u16(plain, 0x0C); val sid = u16(plain, 0x0E)
        val shiny = (tid xor sid xor (pid ushr 16).toInt() xor (pid.toInt() and 65535)) < 16
        val hasTail = record.size == PARTY_SIZE
        return LegendsArceusSlotRead.Pokemon(LegendsArceusPokemonRead(
            species, text(plain, 0x60), byte(plain, 0x24), (byte(plain, 0x22) ushr 2) and 3,
            if (hasTail) byte(plain, 0x168) else null, u32(plain, 0x10), byte(plain, 0x20), byte(plain, 0x21),
            u16(plain, 0x92), if (hasTail) u16(plain, 0x16A) else null, text(plain, 0x110), tid, sid, pid, ec,
            shiny, iv32 and 0x40000000L != 0L, byte(plain, 0x16) and 32 != 0, byte(plain, 0x16) and 64 != 0,
            byte(plain, 0x137), byte(plain, 0x12A), byte(plain, 0xD8), byte(plain, 0xD4),
            immutable((0..3).map { LegendsArceusMoveRead(u16(plain, 0x54 + it * 2), byte(plain, 0x5C + it)) }),
            immutable((0..5).map { ((iv32 ushr (it * 5)) and 31).toInt() }),
            immutable((0..5).map { byte(plain, 0xA4 + it) }),
            immutable((0..3).map { u16(plain, 0x8A + it * 2) }),
            if (hasTail) immutable((0..4).map { u16(plain, 0x16C + it * 2) }) else null
        ))
    }

    private fun xorWords(bytes: ByteArray, from: Int, end: Int, seed: Long) {
        var state = seed
        for (offset in from until end step 2) {
            state = (state * 0x41C64E6DL + 0x6073L) and 0xFFFFFFFFL
            val word = u16(bytes, offset) xor (state ushr 16).toInt()
            bytes[offset] = word.toByte(); bytes[offset + 1] = (word ushr 8).toByte()
        }
    }
    private fun text(bytes: ByteArray, offset: Int): String {
        var length = 0
        while (length < 26 && u16(bytes, offset + length) != 0) length += 2
        return String(bytes, offset, length, Charsets.UTF_16LE)
    }
    private fun byte(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 255
    private fun u16(bytes: ByteArray, offset: Int): Int = byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long = u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(values)
}

object LegendsArceusSpeciesNames {
    private val added = listOf("Wyrdeer", "Kleavor", "Ursaluna", "Basculegion", "Sneasler", "Overqwil", "Enamorus")
    fun name(species: Int): String = if (species in 899..905) added[species - 899] else SwordShieldSpeciesNames.name(species)
}

data class LegendsArceusPastureSlot(val pasture: Int, val position: Int, val read: LegendsArceusSlotRead)
data class LegendsArceusPartySlot(val position: Int, val read: LegendsArceusSlotRead)

/** Only immutable metadata escapes. Detached block and record arrays are not retained. */
class LegendsArceusStorageRead internal constructor(
    val version: LegendsArceusVersion,
    private val pastures: List<LegendsArceusSlotRead>, private val team: List<LegendsArceusSlotRead>
) {
    val partyCount: Int get() = team.size
    val occupiedPastureSlots: Int get() = pastures.count { it !is LegendsArceusSlotRead.Empty }
    val unreadablePastureSlots: Int get() = pastures.count { it is LegendsArceusSlotRead.Invalid }
    fun pasture(index: Int): List<LegendsArceusPastureSlot> {
        require(index in 0 until 32) { "Pasture index must be 0 through 31." }
        return Collections.unmodifiableList((0 until 30).map { LegendsArceusPastureSlot(index, it + 1, pastures[index * 30 + it]) })
    }
    fun party(): List<LegendsArceusPartySlot> = Collections.unmodifiableList((0 until 6).map {
        LegendsArceusPartySlot(it + 1, team.getOrNull(it) ?: LegendsArceusSlotRead.Empty)
    })
}

object LegendsArceusStorageReadOnly {
    fun read(source: ByteArray): LegendsArceusStorageRead {
        val blocks = LegendsArceusSaveValidation.readPokemonBlocks(source)
        val count = blocks.party[6 * LegendsArceusPokemonReadOnly.PARTY_SIZE].toInt() and 255
        require(count in 0..6) { "The native Legends: Arceus party count exceeds six slots." }
        fun record(bytes: ByteArray, index: Int, size: Int): LegendsArceusSlotRead =
            LegendsArceusPokemonReadOnly.read(bytes.copyOfRange(index * size, (index + 1) * size))
        val pastures = (0 until 960).map { record(blocks.pastures, it, LegendsArceusPokemonReadOnly.STORED_SIZE) }
        val team = (0 until count).map { index ->
            when (val result = record(blocks.party, index, LegendsArceusPokemonReadOnly.PARTY_SIZE)) {
                LegendsArceusSlotRead.Empty -> LegendsArceusSlotRead.Invalid("Empty record within the native party count.")
                else -> result
            }
        }
        return LegendsArceusStorageRead(blocks.version, Collections.unmodifiableList(pastures), Collections.unmodifiableList(team))
    }
}
