package com.example.pokemonsavemanager

import java.util.Collections

data class BdspMoveRead(val id: Int, val pp: Int, val ppUps: Int)
data class BdspPokemonRead(
    val species: Int, val nickname: String, val form: Int, val gender: Int,
    val cachedLevel: Int, val experience: Long, val nature: Int, val statNature: Int,
    val currentHp: Int, val maximumHp: Int, val originalTrainer: String,
    val tid: Int, val sid: Int, val pid: Long, val encryptionConstant: Long,
    val shiny: Boolean, val isEgg: Boolean, val fatefulEncounter: Boolean,
    val heldItem: Int, val ability: Int,
    val originalTrainerFriendship: Int, val handlingTrainerFriendship: Int, val currentHandler: Int,
    val moves: List<BdspMoveRead>, val ivs: List<Int>, val evs: List<Int>,
    val relearnMoves: List<Int>, val cachedStats: List<Int>, val contestValues: List<Int>
)

sealed class BdspSlotRead {
    object Empty : BdspSlotRead()
    data class Invalid(val reason: String) : BdspSlotRead()
    data class Pokemon(val value: BdspPokemonRead) : BdspSlotRead()
}

/** Native save slots are encrypted full party-size PB8, including slots in boxes. */
object BdspPokemonReadOnly {
    const val RECORD_SIZE = 0x158
    private const val STORED_END = 0x148
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun read(record: ByteArray): BdspSlotRead {
        if (record.size != RECORD_SIZE) return BdspSlotRead.Invalid("Expected a complete 344-byte native PB8 slot.")
        if (record.all { it == 0.toByte() }) return BdspSlotRead.Empty
        if (u16(record, 4) != 0) return BdspSlotRead.Invalid("PB8 sanity field is not zero.")
        val ec = u32(record, 0)
        val decoded = record.copyOf()
        xorWords(decoded, 8, STORED_END, ec)
        xorWords(decoded, STORED_END, RECORD_SIZE, ec)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val plain = decoded.copyOf()
        for (block in 0..3) {
            val from = 8 + (order[block] - '0') * 80
            decoded.copyInto(plain, 8 + block * 80, from, from + 80)
        }
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        if ((checksum and 65535) != u16(record, 6)) return BdspSlotRead.Invalid("PB8 checksum failed.")
        val species = u16(plain, 8)
        // Valid encrypted blank slots may retain non-species metadata. Never expose residual data as a Pokémon.
        if (species == 0) return BdspSlotRead.Empty
        if (species !in 1..493) return BdspSlotRead.Invalid("Unsupported BDSP species ID $species.")
        val iv32 = u32(plain, 0x8C)
        val pid = u32(plain, 0x1C)
        val tid = u16(plain, 0x0C); val sid = u16(plain, 0x0E)
        val shiny = (tid xor sid xor (pid ushr 16).toInt() xor (pid.toInt() and 65535)) < 16
        return BdspSlotRead.Pokemon(BdspPokemonRead(
            species, text(plain, 0x58), byte(plain, 0x24), (byte(plain, 0x22) ushr 2) and 3,
            byte(plain, 0x148), u32(plain, 0x10), byte(plain, 0x20), byte(plain, 0x21),
            u16(plain, 0x8A), u16(plain, 0x14A), text(plain, 0xF8), tid, sid, pid, ec,
            shiny, iv32 and 0x40000000L != 0L, byte(plain, 0x22) and 1 != 0,
            u16(plain, 0x0A), u16(plain, 0x14),
            byte(plain, 0x112), byte(plain, 0xC8), byte(plain, 0xC4),
            immutable((0..3).map { BdspMoveRead(u16(plain, 0x72 + it * 2), byte(plain, 0x7A + it), byte(plain, 0x7E + it)) }),
            immutable((0..5).map { ((iv32 ushr (it * 5)) and 31).toInt() }),
            immutable((0..5).map { byte(plain, 0x26 + it) }),
            immutable((0..3).map { u16(plain, 0x82 + it * 2) }),
            immutable((0..4).map { u16(plain, 0x14C + it * 2) }),
            immutable((0..5).map { byte(plain, 0x2C + it) })
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

data class BdspStorageSlot(val box: Int, val position: Int, val read: BdspSlotRead)
data class BdspPartySlot(val position: Int, val read: BdspSlotRead)

/** Native positions and immutable metadata only; no decrypted arrays are retained. */
class BdspStorageRead internal constructor(
    val version: BdspVersion, val revision: BdspSaveRevision,
    private val boxes: List<BdspSlotRead>, private val team: List<BdspSlotRead>
) {
    val partyCount: Int get() = team.size
    val occupiedBoxSlots: Int get() = boxes.count { it !is BdspSlotRead.Empty }
    val unreadableBoxSlots: Int get() = boxes.count { it is BdspSlotRead.Invalid }
    fun box(index: Int): List<BdspStorageSlot> {
        require(index in 0 until 40) { "Box index must be 0 through 39." }
        return Collections.unmodifiableList((0 until 30).map { BdspStorageSlot(index, it + 1, boxes[index * 30 + it]) })
    }
    fun party(): List<BdspPartySlot> = Collections.unmodifiableList((0 until 6).map {
        BdspPartySlot(it + 1, team.getOrNull(it) ?: BdspSlotRead.Empty)
    })
}

object BdspStorageReadOnly {
    private const val PARTY_OFFSET = 0x14098
    private const val BOX_OFFSET = 0x14EF4

    fun read(source: ByteArray): BdspStorageRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) { "A validated native BDSP save is required." }
        val size = BdspPokemonReadOnly.RECORD_SIZE
        val count = source[PARTY_OFFSET + 6 * size].toInt() and 255
        require(count in 0..6) { "The native BDSP party count exceeds six slots." }
        fun record(base: Int, index: Int): BdspSlotRead {
            val offset = base + index * size
            return BdspPokemonReadOnly.read(source.copyOfRange(offset, offset + size))
        }
        val boxes = (0 until 1200).map { record(BOX_OFFSET, it) }
        val team = (0 until count).map { index ->
            when (val result = record(PARTY_OFFSET, index)) {
                BdspSlotRead.Empty -> BdspSlotRead.Invalid("Empty record within the native party count.")
                else -> result
            }
        }
        return BdspStorageRead(checked.version, checked.revision, Collections.unmodifiableList(boxes), Collections.unmodifiableList(team))
    }
}
