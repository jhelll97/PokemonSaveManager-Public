package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class Gen1ToSwordShieldMetadata(
    val baseFriendship: Int,
    val genderRate: Int,
    val abilityId: Int,
    val abilityNumber: Int
)

data class Gen1ToSwordShieldSource(
    val species: Int,
    val nickname: String,
    val originalTrainer: String,
    val level: Int,
    val currentHp: Int,
    val status: Int,
    val type1: Int,
    val type2: Int,
    val catchRate: Int,
    val moves: IntArray,
    val currentPp: IntArray,
    val ppUps: IntArray,
    val experience: Long,
    val trainerId: Int,
    val statExperience: IntArray,
    val dvs: IntArray
)

data class Gen1ToSwordShieldReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val effects: List<String>
)

/**
 * Bounded direct adapter for a preserved PSE1 record.
 *
 * The adapter builds a deterministic, checksum-valid PK7 bridge in memory and then
 * uses the reviewed PK7 -> PK8 adapter. The PSE1 file remains the transaction source.
 */
object Gen1ToSwordShieldPk8 {
    private const val PSE1_SIZE = 63
    private const val HEADER = 8
    private const val MON_SIZE = 33
    private const val PK7_SIZE = 0xE8

    fun inspectSource(record: ByteArray): Gen1ToSwordShieldSource {
        require(record.size == PSE1_SIZE) { "A complete 63-byte PSE1 Bank record is required." }
        require(
            record[0] == 'P'.code.toByte() && record[1] == 'S'.code.toByte() &&
                record[2] == 'E'.code.toByte() && record[3] == '1'.code.toByte() &&
                byte(record, 4) == 1 && byte(record, 5) in 0..1
        ) { "The Gen 1 Bank header is invalid." }
        val internalSpecies = byte(record, HEADER)
        val species = internalToDex.getOrNull(internalSpecies)?.takeIf { it in 1..151 }
            ?: error("The Gen 1 internal species index has no National Pokédex mapping.")
        val level = byte(record, HEADER + 3)
        require(level in 1..100) { "The Gen 1 level is outside 1-100." }
        val moves = IntArray(4) { byte(record, HEADER + 8 + it) }
        require(moves.all { it in 0..165 }) { "A Gen 1 move ID is outside the native range." }
        val currentPp = IntArray(4) { byte(record, HEADER + 29 + it) and 0x3F }
        val ppUps = IntArray(4) { byte(record, HEADER + 29 + it) ushr 6 }
        require((0..3).all { moves[it] != 0 || (currentPp[it] == 0 && ppUps[it] == 0) }) {
            "An empty Gen 1 move slot contains PP or PP-Up data."
        }
        val nickname = gen1Text(record, HEADER + MON_SIZE + 11, 11, "nickname")
        val originalTrainer = gen1Text(record, HEADER + MON_SIZE, 11, "Original Trainer name")
        require(originalTrainer.isNotBlank()) { "A blank Gen 1 Original Trainer name is not destination-valid." }
        val attackDefense = byte(record, HEADER + 27)
        val speedSpecial = byte(record, HEADER + 28)
        val attack = attackDefense ushr 4
        val defense = attackDefense and 15
        val speed = speedSpecial ushr 4
        val special = speedSpecial and 15
        val hp = ((attack and 1) shl 3) or ((defense and 1) shl 2) or
            ((speed and 1) shl 1) or (special and 1)
        return Gen1ToSwordShieldSource(
            species, nickname, originalTrainer, level,
            u16be(record, HEADER + 1), byte(record, HEADER + 4),
            byte(record, HEADER + 5), byte(record, HEADER + 6),
            byte(record, HEADER + 7), moves, currentPp, ppUps,
            u24be(record, HEADER + 14), u16be(record, HEADER + 12),
            IntArray(5) { u16be(record, HEADER + 17 + it * 2) },
            intArrayOf(hp, attack, defense, speed, special)
        )
    }

    fun review(
        record: ByteArray,
        destinationTrainer: SwordShieldTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): Gen1ToSwordShieldReview {
        val source = inspectSource(record)
        require(destinationTrainer.languageId == 2) {
            "This bounded route currently requires an English Sword/Shield destination trainer."
        }
        require(metadata.baseFriendship in 0..255) { "The reviewed base friendship is invalid." }
        require(metadata.genderRate in -1..8) { "The reviewed gender ratio is invalid." }
        require(metadata.abilityId in 1..255 && metadata.abilityNumber in setOf(1, 2, 4)) {
            "The reviewed ability mapping cannot be represented in PK7."
        }
        val profile = SwordShieldPersonalCatalogue.profiles(source.species)
            .singleOrNull { it.isDefault }
            ?: error("This species has no unique default-form Sword/Shield profile.")
        source.moves.filter { it != 0 }.forEach {
            require(SwordShieldMoveCatalog.choice(it) != null) {
                "Move #" + it + " has no native Sword/Shield mapping."
            }
        }
        val bridge = buildPk7Bridge(record, source, destinationTrainer, metadata, profile.growth)
        val converted = Gen7ToSwordShieldPk8.review(bridge)
        val generatedExperience = minimumExperience(source.level, profile.growth)
        val nature = (source.experience % 25L).toInt()
        val shiny = wouldBeShiny(source.dvs)
        val bridgeEffects = converted.effects.filterNot {
            it.startsWith("Explicit Legalise:") || it.startsWith("The PK7 Bank entry")
        }
        val effects = buildList {
            add("Explicit Legalise: direct PSE Gen 1 PSE1 -> deterministic in-memory PK7 bridge -> Sword/Shield PK8 conversion; not an official transfer, HOME move, trade, or verified provenance.")
            add("The exact selected 63-byte PSE1 record remains the bound Bank source. Species #" +
                source.species + ", TID " + source.trainerId +
                ", OT text, nickname text, level, move IDs and PP-Up counts are retained.")
            add("Generation 1 current HP " + source.currentHp + ", status 0x" +
                source.status.toString(16).uppercase() + ", type bytes " +
                source.type1 + "/" + source.type2 + ", catch-rate byte " + source.catchRate +
                " and stat experience " + source.statExperience.joinToString("/") +
                " have no reviewed PK8 fields and are explicitly omitted; destination EVs are zero.")
            add("Generation 1 DVs " + source.dvs.joinToString("/") +
                " determine only the transferred shiny state (" +
                (if (shiny) "shiny" else "not shiny") +
                ") and gender. Deterministic destination IVs are generated with " +
                (if (source.species == 151) 5 else 3) + " perfect stats.")
            add("Source EXP " + source.experience + " is normalized to the level-" +
                source.level + " growth-floor value " + generatedExperience +
                "; nature " + nature + " is derived from source EXP modulo 25.")
            add("Current PP " + source.currentPp.joinToString("/") +
                " is normalized to Sword/Shield base PP with retained PP-Up counts " +
                source.ppUps.joinToString("/") + ". Unsupported Sword/Shield moves remain blocked.")
            add("SID is generated as 0. Encryption constant, PID and IVs are deterministic Legalise values bound to the PSE1 bytes, destination trainer and reviewed species metadata; PID does not certify origin.")
            add("Ability " + metadata.abilityId + " in slot " + metadata.abilityNumber +
                ", base friendship " + metadata.baseFriendship + " and gender ratio " +
                metadata.genderRate + " are the documented metadata accepted for this review.")
            add("The bridge uses generated Red origin ID 35, Poké Ball, Poké Transporter met location 30013, unset encounter date, English language and the destination trainer as handling trainer. The legacy PSE1 record does not identify Red, Blue or Yellow, so this is an explicit provenance Legalise choice.")
            addAll(bridgeEffects)
            add("The PSE1 Bank entry and Sword/Shield source remain unchanged. One reviewed empty native PK8 slot is written only to a new copy with caught registration, retained recovery and persisted reopen verification.")
        }
        return Gen1ToSwordShieldReview(
            converted.nativeRecord.copyOf(), source.species, source.nickname, effects
        )
    }

    private fun buildPk7Bridge(
        record: ByteArray,
        source: Gen1ToSwordShieldSource,
        trainer: SwordShieldTrainerRead,
        metadata: Gen1ToSwordShieldMetadata,
        growth: Int
    ): ByteArray {
        val seed = MessageDigest.getInstance("SHA-256").apply {
            update(record)
            update(trainer.name.toByteArray(StandardCharsets.UTF_8))
            update(byteArrayOf(trainer.gender.toByte(), trainer.languageId.toByte()))
            update(intBytes(metadata.baseFriendship))
            update(intBytes(metadata.genderRate))
            update(intBytes(metadata.abilityId))
            update(intBytes(metadata.abilityNumber))
        }.digest()
        val ec = u32(seed, 0)
        val shiny = wouldBeShiny(source.dvs)
        val pid = forceShiny(u32(seed, 4), source.trainerId, shiny)
        val ivs = deterministicIvs(seed, if (source.species == 151) 5 else 3)
        val nature = (source.experience % 25L).toInt()
        val gender = transferGender(source.dvs[1], metadata.genderRate)
        val speciesName = SwordShieldSpeciesNames.name(source.species)
        val nickname = source.nickname.ifBlank { speciesName }
        val customNickname = source.nickname.isNotBlank() &&
            !source.nickname.equals(speciesName, ignoreCase = true)
        val pk = ByteArray(PK7_SIZE)
        w32(pk, 0x00, ec)
        w16(pk, 0x04, 0)
        w16(pk, 0x08, source.species)
        w16(pk, 0x0A, 0)
        w16(pk, 0x0C, source.trainerId)
        w16(pk, 0x0E, 0)
        w32(pk, 0x10, minimumExperience(source.level, growth))
        pk[0x14] = metadata.abilityId.toByte()
        pk[0x15] = metadata.abilityNumber.toByte()
        w16(pk, 0x16, 0)
        w32(pk, 0x18, pid)
        pk[0x1C] = nature.toByte()
        pk[0x1D] = (((gender and 3) shl 1) or
            if (source.species == 151) 1 else 0).toByte()
        putText(pk, 0x40, nickname)
        for (index in 0..3) {
            val move = source.moves[index]
            w16(pk, 0x5A + index * 2, move)
            if (move != 0) {
                pk[0x62 + index] =
                    SwordShieldMoveCatalog.maximumPp(move, source.ppUps[index]).toByte()
                pk[0x66 + index] = source.ppUps[index].toByte()
            }
        }
        var packedIvs = 0L
        for (index in 0..5) {
            packedIvs = packedIvs or ((ivs[index].toLong() and 31L) shl (index * 5))
        }
        if (customNickname) packedIvs = packedIvs or 0x80000000L
        w32(pk, 0x74, packedIvs)
        putText(pk, 0x78, trainer.name)
        pk[0x92] = trainer.gender.toByte()
        pk[0x93] = 1
        pk[0xA2] = metadata.baseFriendship.toByte()
        putText(pk, 0xB0, source.originalTrainer)
        pk[0xCA] = metadata.baseFriendship.toByte()
        w16(pk, 0xD8, 0)
        w16(pk, 0xDA, 30013)
        pk[0xDC] = 4
        pk[0xDD] = source.level.toByte()
        pk[0xDE] = 0
        pk[0xDF] = 35
        pk[0xE3] = 2
        var checksum = 0
        for (offset in 8 until PK7_SIZE step 2) checksum += u16(pk, offset)
        w16(pk, 6, checksum and 0xFFFF)
        return pk
    }

    private fun deterministicIvs(seed: ByteArray, flawless: Int): IntArray {
        val values = IntArray(6) { byte(seed, 16 + it) and 31 }
        val order = (0..5).sortedWith(compareBy<Int>({ byte(seed, 8 + it) }, { it }))
        order.take(flawless.coerceIn(0, 6)).forEach { values[it] = 31 }
        return values
    }

    private fun transferGender(attackDv: Int, genderRate: Int): Int = when {
        genderRate < 0 -> 2
        genderRate == 0 -> 0
        genderRate >= 8 -> 1
        attackDv < (genderRate * 2).coerceIn(0, 16) -> 1
        else -> 0
    }

    private fun wouldBeShiny(dvs: IntArray): Boolean =
        dvs.size >= 5 && dvs[2] == 10 && dvs[3] == 10 && dvs[4] == 10 &&
            dvs[1] in setOf(2, 3, 6, 7, 10, 11, 14, 15)

    private fun forceShiny(input: Long, tid: Int, wanted: Boolean): Long {
        var pid = input and 0xFFFFFFFFL
        val low = (pid and 0xFFFF).toInt()
        var high = ((pid ushr 16) and 0xFFFF).toInt()
        val current = (tid xor low xor high) < 16
        if (wanted && !current) {
            high = (tid xor low) and 0xFFFF
            pid = (low.toLong() and 0xFFFFL) or ((high.toLong() and 0xFFFFL) shl 16)
        } else if (!wanted && current) {
            pid = pid xor 0x10000000L
        }
        return pid and 0xFFFFFFFFL
    }

    private fun minimumExperience(level: Int, growth: Int): Long {
        if (level <= 1) return 0
        val n = level.toLong()
        val cube = n * n * n
        return when (growth) {
            0 -> cube
            1 -> when {
                level <= 50 -> cube * (100 - n) / 50
                level <= 68 -> cube * (150 - n) / 100
                level <= 98 -> {
                    val remainder = n % 3
                    cube * (1274 + remainder * remainder - 9 * remainder -
                        20 * (n / 3)) / 1000
                }
                else -> cube * (160 - n) / 100
            }
            2 -> when {
                level <= 15 -> cube * (24 + (n + 1) / 3) / 50
                level <= 35 -> cube * (14 + n) / 50
                else -> cube * (32 + n / 2) / 50
            }
            3 -> maxOf(0L, 6 * cube / 5 - 15 * n * n + 100 * n - 140)
            4 -> 4 * cube / 5
            5 -> 5 * cube / 4
            else -> error("The Sword/Shield growth group is unsupported.")
        }
    }

    private fun gen1Text(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        label: String
    ): String {
        val text = StringBuilder()
        for (index in offset until offset + length) {
            val value = byte(bytes, index)
            if (value == 0x50) break
            val character = when (value) {
                in 0x80..0x99 -> ('A'.code + value - 0x80).toChar()
                in 0xA0..0xB9 -> ('a'.code + value - 0xA0).toChar()
                in 0xF6..0xFF -> ('0'.code + value - 0xF6).toChar()
                0x7F -> ' '
                0xE0 -> '\''
                0xE3 -> '-'
                0xE6 -> '?'
                0xE7 -> '!'
                0xF2 -> '.'
                else -> error("The Gen 1 " + label + " contains unsupported byte 0x" +
                    value.toString(16).padStart(2, '0').uppercase() + ".")
            }
            text.append(character)
        }
        return text.toString().trim()
    }

    private fun putText(bytes: ByteArray, offset: Int, value: String) {
        val encoded = value.toByteArray(StandardCharsets.UTF_16LE)
        require(encoded.size <= 24) { "Converted text exceeds 12 characters." }
        encoded.copyInto(bytes, offset)
    }

    private fun intBytes(value: Int) = byteArrayOf(
        value.toByte(), (value ushr 8).toByte(),
        (value ushr 16).toByte(), (value ushr 24).toByte()
    )

    private fun byte(bytes: ByteArray, offset: Int) = bytes[offset].toInt() and 255
    private fun u16(bytes: ByteArray, offset: Int) =
        byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun u16be(bytes: ByteArray, offset: Int) =
        (byte(bytes, offset) shl 8) or byte(bytes, offset + 1)
    private fun u24be(bytes: ByteArray, offset: Int) =
        (byte(bytes, offset).toLong() shl 16) or
            (byte(bytes, offset + 1).toLong() shl 8) or
            byte(bytes, offset + 2).toLong()
    private fun u32(bytes: ByteArray, offset: Int) =
        u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun w16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
    private fun w32(bytes: ByteArray, offset: Int, value: Long) {
        w16(bytes, offset, value.toInt())
        w16(bytes, offset + 2, (value ushr 16).toInt())
    }

    private val internalToDex = intArrayOf(
        0,112,115,32,35,21,100,34,80,2,103,108,102,88,94,29,31,104,111,131,
        59,151,130,90,72,92,123,120,9,127,114,0,0,58,95,22,16,79,64,75,113,
        67,122,106,107,24,47,54,96,76,0,126,0,125,82,109,0,56,86,50,128,0,0,
        0,83,48,149,0,0,0,84,60,124,146,144,145,132,52,98,0,0,0,37,38,25,26,
        0,0,147,148,140,141,116,117,0,0,27,28,138,139,39,40,133,136,135,134,
        66,41,23,46,61,62,13,14,15,0,85,57,51,49,87,0,0,10,11,12,68,0,55,97,
        42,150,143,129,0,0,89,0,99,91,0,101,36,110,53,105,0,93,63,65,17,18,
        121,1,3,73,0,118,119,0,0,0,0,77,78,19,20,33,30,74,137,142,0,81,0,0,
        4,7,5,8,6,0,0,0,0,43,44,45,69,70,71
    )
}
