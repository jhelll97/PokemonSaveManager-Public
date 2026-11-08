package com.example.pokemonsavemanager

import java.util.Collections

data class ScarletVioletMoveRead(val id: Int, val pp: Int, val ppUps: Int)
data class ScarletVioletPokemonRead(
    val species: Int, val nickname: String, val form: Int, val gender: Int,
    val cachedLevel: Int, val experience: Long, val nature: Int, val statNature: Int,
    val currentHp: Int, val maximumHp: Int, val originalTrainer: String, val handlingTrainer: String,
    val tid: Int, val sid: Int, val pid: Long, val encryptionConstant: Long,
    val shiny: Boolean, val isEgg: Boolean, val fatefulEncounter: Boolean,
    val heldItem: Int, val ability: Int, val abilityNumber: Int,
    val originalTrainerFriendship: Int, val handlingTrainerFriendship: Int,
    val handlingTrainerGender: Int, val handlingTrainerLanguage: Int, val currentHandler: Int,
    val language: Int, val affixedRibbon: Int, val ribbonFlags: List<Boolean>,
    val moves: List<ScarletVioletMoveRead>, val ivs: List<Int>, val evs: List<Int>,
    val hyperTrainingFlags: Int,
    val relearnMoves: List<Int>, val cachedStats: List<Int>,
    val teraTypeOriginal: Int, val teraTypeOverride: Int, val scale: Int, val obedienceLevel: Int,
    val ball: Int = 0, val metLevel: Int = 0, val originalTrainerGender: Int = 0,
    val eggYear: Int = 0, val eggMonth: Int = 0, val eggDay: Int = 0, val eggLocation: Int = 0,
    val metYear: Int = 0, val metMonth: Int = 0, val metDay: Int = 0, val metLocation: Int = 0,
    val technicalMachineFlags: List<Boolean> = emptyList(),
    val pokerusDays: Int = 0, val pokerusStrain: Int = 0
)

sealed class ScarletVioletSlotRead {
    object Empty : ScarletVioletSlotRead()
    data class Invalid(val reason: String) : ScarletVioletSlotRead()
    data class Pokemon(val value: ScarletVioletPokemonRead) : ScarletVioletSlotRead()
}

/** Native save slots are encrypted full party-size PK9, including slots in boxes. */
object ScarletVioletPokemonReadOnly {
    const val RECORD_SIZE = 0x158
    private const val STORED_END = 0x148
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun read(record: ByteArray): ScarletVioletSlotRead {
        if (record.size != RECORD_SIZE) return ScarletVioletSlotRead.Invalid("Expected a complete 344-byte native PK9 slot.")
        if (record.all { it == 0.toByte() }) return ScarletVioletSlotRead.Empty
        if (u16(record, 4) != 0) return ScarletVioletSlotRead.Invalid("PK9 sanity field is not zero.")
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
        if ((checksum and 65535) != u16(record, 6)) return ScarletVioletSlotRead.Invalid("PK9 checksum failed.")
        val species = nationalSpecies(u16(plain, 8))
        // Valid encrypted blank slots may retain non-species metadata. Never expose residual data as a Pokémon.
        if (species == 0) return ScarletVioletSlotRead.Empty
        if (species !in 1..1025) return ScarletVioletSlotRead.Invalid("Unsupported Scarlet/Violet species ID $species.")
        val iv32 = u32(plain, 0x8C)
        val pid = u32(plain, 0x1C)
        val tid = u16(plain, 0x0C); val sid = u16(plain, 0x0E)
        val shiny = (tid xor sid xor (pid ushr 16).toInt() xor (pid.toInt() and 65535)) < 16
        return ScarletVioletSlotRead.Pokemon(ScarletVioletPokemonRead(
            species, text(plain, 0x58), byte(plain, 0x24), (byte(plain, 0x22) ushr 1) and 3,
            byte(plain, 0x148), u32(plain, 0x10), byte(plain, 0x20), byte(plain, 0x21),
            u16(plain, 0x8A), u16(plain, 0x14A), text(plain, 0xF8), text(plain, 0xA8), tid, sid, pid, ec,
            shiny, iv32 and 0x40000000L != 0L, byte(plain, 0x22) and 1 != 0,
            u16(plain, 0x0A), u16(plain, 0x14), byte(plain, 0x16) and 7,
            byte(plain, 0x112), byte(plain, 0xC8), byte(plain, 0xC2), byte(plain, 0xC3), byte(plain, 0xC4),
            byte(plain, 0xD5), byte(plain, 0xD4).let { if (it == 0xFF) -1 else it },
            immutable((0..110).map { ribbonFlag(plain, it) }),
            immutable((0..3).map { ScarletVioletMoveRead(u16(plain, 0x72 + it * 2), byte(plain, 0x7A + it), byte(plain, 0x7E + it)) }),
            immutable((0..5).map { ((iv32 ushr (it * 5)) and 31).toInt() }),
            immutable((0..5).map { byte(plain, 0x26 + it) }),
            byte(plain, 0x126),
            immutable((0..3).map { u16(plain, 0x82 + it * 2) }),
            immutable((0..4).map { u16(plain, 0x14C + it * 2) }),
            byte(plain, 0x94), byte(plain, 0x95), byte(plain, 0x4A), byte(plain, 0x11F),
            byte(plain, 0x124), byte(plain, 0x125) and 0x7F, byte(plain, 0x125) ushr 7,
            byte(plain, 0x119), byte(plain, 0x11A), byte(plain, 0x11B), u16(plain, 0x120),
            byte(plain, 0x11C), byte(plain, 0x11D), byte(plain, 0x11E), u16(plain, 0x122),
            immutable((0 until 230).map { technicalMachineFlag(plain, it) }),
            byte(plain, 0x32) and 0xF, byte(plain, 0x32) ushr 4
        ))
    }

    private val nationalIds = intArrayOf(982, 917, 918, 919, 920, 953, 954, 971, 972, 955, 956, 981, 960, 961, 977, 976, 963, 964, 928, 929, 930, 951, 952, 938, 939, 965, 966, 968, 924, 925, 974, 975, 996, 997, 998, 978, 967, 921, 922, 923, 940, 941, 962, 931, 973, 950, 932, 933, 934, 969, 970, 944, 945, 926, 927, 942, 943, 946, 947, 999, 1000, 984, 986, 1009, 989, 985, 987, 988, 1005, 990, 1010, 994, 992, 993, 995, 991, 1006, 1003, 1002, 1001, 1004, 1007, 1008, 957, 958, 959, 935, 936, 937, 948, 949, 983, 980, 979, 1017, 1011, 1019, 1014, 1015, 1016, 1020, 1021, 1023, 1022, 1024, 1025, 1018, 1012, 1013)

    internal fun nationalSpecies(raw: Int): Int = nationalIds.getOrNull(raw - 917) ?: raw

    private fun technicalMachineFlag(plain: ByteArray, index: Int): Boolean {
        val byteOffset = if (index < 200) 0x12F + (index ushr 3)
            else 0x4B + ((index - 200) ushr 3)
        val bit = if (index < 200) index and 7 else (index - 200) and 7
        return byte(plain, byteOffset) and (1 shl bit) != 0
    }

    private fun ribbonFlag(plain: ByteArray, index: Int): Boolean {
        val byteOffset = if (index < 64) 0x34 + (index ushr 3)
            else 0x40 + ((index - 64) ushr 3)
        return byte(plain, byteOffset) and (1 shl (index and 7)) != 0
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

data class ScarletVioletStorageSlot(val box: Int, val position: Int, val read: ScarletVioletSlotRead)
data class ScarletVioletPartySlot(val position: Int, val read: ScarletVioletSlotRead)

/** Native positions and immutable metadata only; no decrypted arrays are retained. */
class ScarletVioletStorageRead internal constructor(
    val version: ScarletVioletVersion, val rideLegend: ScarletVioletSlotRead,
    private val boxes: List<ScarletVioletSlotRead>, private val team: List<ScarletVioletSlotRead>
) {
    val partyCount: Int get() = team.size
    val occupiedBoxSlots: Int get() = boxes.count { it !is ScarletVioletSlotRead.Empty }
    val unreadableBoxSlots: Int get() = boxes.count { it is ScarletVioletSlotRead.Invalid }
    fun box(index: Int): List<ScarletVioletStorageSlot> {
        require(index in 0 until 32) { "Box index must be 0 through 31." }
        return Collections.unmodifiableList((0 until 30).map { ScarletVioletStorageSlot(index, it + 1, boxes[index * 30 + it]) })
    }
    fun party(): List<ScarletVioletPartySlot> = Collections.unmodifiableList((0 until 6).map {
        ScarletVioletPartySlot(it + 1, team.getOrNull(it) ?: ScarletVioletSlotRead.Empty)
    })
}

object ScarletVioletStorageReadOnly {
    fun read(source: ByteArray): ScarletVioletStorageRead {
        val blocks = ScarletVioletSaveValidation.readPokemonBlocks(source)
        val size = ScarletVioletPokemonReadOnly.RECORD_SIZE
        val count = blocks.party[6 * size].toInt() and 255
        require(count in 0..6) { "The native Scarlet/Violet party count exceeds six slots." }
        fun record(bytes: ByteArray, index: Int): ScarletVioletSlotRead =
            ScarletVioletPokemonReadOnly.read(bytes.copyOfRange(index * size, (index + 1) * size))
        val boxes = (0 until 960).map { record(blocks.boxes, it) }
        // The ride legend is a dedicated record after the 32 normal boxes, not box 33.
        val ride = record(blocks.boxes, 960)
        val team = (0 until count).map { index ->
            when (val result = record(blocks.party, index)) {
                ScarletVioletSlotRead.Empty -> ScarletVioletSlotRead.Invalid("Empty record within the native party count.")
                else -> result
            }
        }
        return ScarletVioletStorageRead(blocks.version, ride, Collections.unmodifiableList(boxes), Collections.unmodifiableList(team))
    }
}

object ScarletVioletSpeciesNames {
    private val added = listOf("Sprigatito", "Floragato", "Meowscarada", "Fuecoco", "Crocalor", "Skeledirge", "Quaxly", "Quaxwell", "Quaquaval", "Lechonk", "Oinkologne", "Tarountula", "Spidops", "Nymble", "Lokix", "Pawmi", "Pawmo", "Pawmot", "Tandemaus", "Maushold", "Fidough", "Dachsbun", "Smoliv", "Dolliv", "Arboliva", "Squawkabilly", "Nacli", "Naclstack", "Garganacl", "Charcadet", "Armarouge", "Ceruledge", "Tadbulb", "Bellibolt", "Wattrel", "Kilowattrel", "Maschiff", "Mabosstiff", "Shroodle", "Grafaiai", "Bramblin", "Brambleghast", "Toedscool", "Toedscruel", "Klawf", "Capsakid", "Scovillain", "Rellor", "Rabsca", "Flittle", "Espathra", "Tinkatink", "Tinkatuff", "Tinkaton", "Wiglett", "Wugtrio", "Bombirdier", "Finizen", "Palafin", "Varoom", "Revavroom", "Cyclizar", "Orthworm", "Glimmet", "Glimmora", "Greavard", "Houndstone", "Flamigo", "Cetoddle", "Cetitan", "Veluza", "Dondozo", "Tatsugiri", "Annihilape", "Clodsire", "Farigiraf", "Dudunsparce", "Kingambit", "Great Tusk", "Scream Tail", "Brute Bonnet", "Flutter Mane", "Slither Wing", "Sandy Shocks", "Iron Treads", "Iron Bundle", "Iron Hands", "Iron Jugulis", "Iron Moth", "Iron Thorns", "Frigibax", "Arctibax", "Baxcalibur", "Gimmighoul", "Gholdengo", "Wo-Chien", "Chien-Pao", "Ting-Lu", "Chi-Yu", "Roaring Moon", "Iron Valiant", "Koraidon", "Miraidon", "Walking Wake", "Iron Leaves", "Dipplin", "Poltchageist", "Sinistcha", "Okidogi", "Munkidori", "Fezandipiti", "Ogerpon", "Archaludon", "Hydrapple", "Gouging Fire", "Raging Bolt", "Iron Boulder", "Iron Crown", "Terapagos", "Pecharunt")
    fun name(species: Int): String = if (species in 906..1025) added[species - 906] else LegendsArceusSpeciesNames.name(species)
}
