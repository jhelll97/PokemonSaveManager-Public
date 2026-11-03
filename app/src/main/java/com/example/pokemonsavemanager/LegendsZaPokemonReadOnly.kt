package com.example.pokemonsavemanager

import java.util.Collections

data class LegendsZaMoveRead(val id: Int, val pp: Int, val ppUps: Int)
data class LegendsZaPokemonRead(
    val species: Int, val nickname: String, val form: Int, val gender: Int,
    val cachedLevel: Int, val experience: Long, val nature: Int, val statNature: Int,
    val currentHp: Int, val maximumHp: Int, val originalTrainer: String,
    val tid: Int, val sid: Int, val pid: Long, val encryptionConstant: Long,
    val shiny: Boolean, val isEgg: Boolean, val heldItem: Int, val ability: Int,
    val originalTrainerFriendship: Int, val handlingTrainerFriendship: Int, val currentHandler: Int,
    val moves: List<LegendsZaMoveRead>, val ivs: List<Int>, val evs: List<Int>,
    val relearnMoves: List<Int>, val cachedStats: List<Int>,
    val isAlpha: Boolean, val ball: Int, val scale: Int, val obedienceLevel: Int
)

sealed class LegendsZaSlotRead {
    object Empty : LegendsZaSlotRead()
    data class Invalid(val reason: String) : LegendsZaSlotRead()
    data class Pokemon(val value: LegendsZaPokemonRead) : LegendsZaSlotRead()
}

/** Native save slots are encrypted full party-size PA9, including slots in boxes. */
object LegendsZaPokemonReadOnly {
    const val RECORD_SIZE = 0x158
    private const val STORED_END = 0x148
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun read(record: ByteArray): LegendsZaSlotRead {
        if (record.size != RECORD_SIZE) return LegendsZaSlotRead.Invalid("Expected a complete 344-byte native PA9 slot.")
        if (record.all { it == 0.toByte() }) return LegendsZaSlotRead.Empty
        if (u16(record, 4) != 0) return LegendsZaSlotRead.Invalid("PA9 sanity field is not zero.")
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
        if ((checksum and 65535) != u16(record, 6)) return LegendsZaSlotRead.Invalid("PA9 checksum failed.")
        val species = nationalSpecies(u16(plain, 8))
        // Valid encrypted blank slots may retain non-species metadata. Never expose residual data as a Pokémon.
        if (species == 0) return LegendsZaSlotRead.Empty
        if (species !in 1..1000) return LegendsZaSlotRead.Invalid("Unsupported Legends: Z-A species ID $species.")
        val iv32 = u32(plain, 0x8C)
        val pid = u32(plain, 0x1C)
        val tid = u16(plain, 0x0C); val sid = u16(plain, 0x0E)
        val shiny = (tid xor sid xor (pid ushr 16).toInt() xor (pid.toInt() and 65535)) < 16
        return LegendsZaSlotRead.Pokemon(LegendsZaPokemonRead(
            species, text(plain, 0x58), byte(plain, 0x24), (byte(plain, 0x22) ushr 1) and 3,
            byte(plain, 0x148), u32(plain, 0x10), byte(plain, 0x20), byte(plain, 0x21),
            u16(plain, 0x8A), u16(plain, 0x14A), text(plain, 0xF8), tid, sid, pid, ec,
            shiny, iv32 and 0x40000000L != 0L, u16(plain, 0x0A), u16(plain, 0x14),
            byte(plain, 0x112), byte(plain, 0xC8), byte(plain, 0xC4),
            immutable((0..3).map { LegendsZaMoveRead(u16(plain, 0x72 + it * 2), byte(plain, 0x7A + it), byte(plain, 0x7E + it)) }),
            immutable((0..5).map { ((iv32 ushr (it * 5)) and 31).toInt() }),
            immutable((0..5).map { byte(plain, 0x26 + it) }),
            immutable((0..3).map { u16(plain, 0x82 + it * 2) }),
            immutable((0..4).map { u16(plain, 0x14C + it * 2) }),
            byte(plain, 0x23) != 0, byte(plain, 0x124), byte(plain, 0x4A), byte(plain, 0x11F)
        ))
    }

    private val nationalIds = intArrayOf(982, 917, 918, 919, 920, 953, 954, 971, 972, 955, 956, 981, 960, 961, 977, 976, 963, 964, 928, 929, 930, 951, 952, 938, 939, 965, 966, 968, 924, 925, 974, 975, 996, 997, 998, 978, 967, 921, 922, 923, 940, 941, 962, 931, 973, 950, 932, 933, 934, 969, 970, 944, 945, 926, 927, 942, 943, 946, 947, 999, 1000, 984, 986, 1009, 989, 985, 987, 988, 1005, 990, 1010, 994, 992, 993, 995, 991, 1006, 1003, 1002, 1001, 1004, 1007, 1008, 957, 958, 959, 935, 936, 937, 948, 949, 983, 980, 979, 1017, 1011, 1019, 1014, 1015, 1016, 1020, 1021, 1023, 1022, 1024, 1025, 1018, 1012, 1013)

    internal fun nationalSpecies(raw: Int): Int = nationalIds.getOrNull(raw - 917) ?: raw

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

data class LegendsZaStorageSlot(val box: Int, val position: Int, val read: LegendsZaSlotRead)
data class LegendsZaPartySlot(val position: Int, val read: LegendsZaSlotRead)

/** Native positions and immutable metadata only; no decrypted arrays are retained. */
class LegendsZaStorageRead internal constructor(
    val version: LegendsZaVersion, val revision: LegendsZaRevision?,
    private val boxes: List<LegendsZaSlotRead>, private val team: List<LegendsZaSlotRead>
) {
    val partyCount: Int get() = team.size
    val occupiedBoxSlots: Int get() = boxes.count { it !is LegendsZaSlotRead.Empty }
    val unreadableBoxSlots: Int get() = boxes.count { it is LegendsZaSlotRead.Invalid }
    fun box(index: Int): List<LegendsZaStorageSlot> {
        require(index in 0 until 32) { "Box index must be 0 through 31." }
        return Collections.unmodifiableList((0 until 30).map { LegendsZaStorageSlot(index, it + 1, boxes[index * 30 + it]) })
    }
    fun party(): List<LegendsZaPartySlot> = Collections.unmodifiableList((0 until 6).map {
        LegendsZaPartySlot(it + 1, team.getOrNull(it) ?: LegendsZaSlotRead.Empty)
    })
}

object LegendsZaStorageReadOnly {
    const val BOX_STRIDE = 0x198
    const val PARTY_STRIDE = 0x1E0

    /** Native EntityDetection rule: EC/nonzero first word, or the EC-zero species-word fallback. */
    internal fun hasNativePresence(bytes: ByteArray, offset: Int): Boolean {
        require(offset >= 0 && offset <= bytes.size - 10) { "A native presence header is truncated." }
        return (0..3).any { bytes[offset + it] != 0.toByte() } ||
            bytes[offset + 8] != 0.toByte() || bytes[offset + 9] != 0.toByte()
    }

    fun read(source: ByteArray): LegendsZaStorageRead {
        val blocks = LegendsZaSaveValidation.readPokemonBlocks(source)
        val size = LegendsZaPokemonReadOnly.RECORD_SIZE
        fun record(bytes: ByteArray, index: Int, stride: Int): LegendsZaSlotRead {
            val offset = index * stride
            return LegendsZaPokemonReadOnly.read(bytes.copyOfRange(offset, offset + size))
        }
        val boxes = (0 until 960).map { record(blocks.boxes, it, BOX_STRIDE) }
        // There is no trailing party-count byte. Stop at the first natively absent header.
        // Footer flags can be set on empty records; they are not occupancy/count markers.
        val count = (0 until 6).firstOrNull { !hasNativePresence(blocks.party, it * PARTY_STRIDE) } ?: 6
        val team = (0 until count).map { index ->
            when (val result = record(blocks.party, index, PARTY_STRIDE)) {
                LegendsZaSlotRead.Empty -> LegendsZaSlotRead.Invalid("Empty record within the native party prefix.")
                else -> result
            }
        }
        return LegendsZaStorageRead(blocks.version, blocks.revision, Collections.unmodifiableList(boxes), Collections.unmodifiableList(team))
    }
}
