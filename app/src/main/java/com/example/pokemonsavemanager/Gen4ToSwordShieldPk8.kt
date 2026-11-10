package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class Gen4ToSwordShieldSource(
    val species: Int,
    val nickname: String,
    val originalTrainer: String,
    val trainerId: Int,
    val secretId: Int,
    val personality: Long,
    val experience: Long,
    val friendship: Int,
    val abilityId: Int,
    val abilitySlot: Int,
    val moves: IntArray,
    val currentPp: IntArray,
    val ppUps: IntArray,
    val evs: IntArray,
    val contest: IntArray,
    val ivs: IntArray,
    val pokerus: Int,
    val eggLocation: Int,
    val metLocation: Int,
    val metLevel: Int,
    val ball: Int,
    val originalTrainerGender: Int,
    val originGame: Int,
    val language: Int,
    val markings: Int,
    val fatefulEncounter: Boolean,
    val form: Int,
    val gender: Int,
    val eggYear: Int,
    val eggMonth: Int,
    val eggDay: Int,
    val metYear: Int,
    val metMonth: Int,
    val metDay: Int,
    val ribbonWordA: Long,
    val ribbonWordB: Long,
    val shiny: Boolean,
    val isNicknamed: Boolean
)

data class Gen4ToSwordShieldReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val effects: List<String>
)

/** Converts one exact checksum-valid 136-byte PK4 Bank record through a reviewed PK7 bridge. */
object Gen4ToSwordShieldPk8 {
    private const val PK7_SIZE = 0xE8
    private val originGames = setOf(1, 2, 3, 4, 5, 7, 8, 10, 11, 12, 15)
    private val blockPosition = intArrayOf(
        0,1,2,3, 0,1,3,2, 0,2,1,3, 0,3,1,2,
        0,2,3,1, 0,3,2,1, 1,0,2,3, 1,0,3,2,
        2,0,1,3, 3,0,1,2, 2,0,3,1, 3,0,2,1,
        1,2,0,3, 1,3,0,2, 2,1,0,3, 3,1,0,2,
        2,3,0,1, 3,2,0,1, 1,2,3,0, 1,3,2,0,
        2,1,3,0, 3,1,2,0, 2,3,1,0, 3,2,1,0,
        0,1,2,3, 0,1,3,2, 0,2,1,3, 0,3,1,2,
        0,2,3,1, 0,3,2,1, 1,0,2,3, 1,0,3,2
    )

    fun inspectSource(record: ByteArray): Gen4ToSwordShieldSource {
        require(record.size == 136) { "The selected Gen 4 Bank source must be one exact 136-byte boxed PK4." }
        val decoded = decrypt(record)
        var checksum = 0
        for (offset in 8 until 136 step 2) checksum = (checksum + u16(decoded, offset)) and 0xFFFF
        require(checksum == u16(decoded, 6)) { "The source PK4 checksum is invalid." }
        val species = u16(decoded, 0x08)
        require(species in 1..493) { "The PK4 species is outside the reviewed Generation 4 range." }
        val personality = u32(decoded, 0)
        val trainerId = u16(decoded, 0x0C)
        val secretId = u16(decoded, 0x0E)
        val ivWord = u32(decoded, 0x38)
        require(ivWord and 0x40000000L == 0L) { "Egg PK4 records remain blocked from this PSE conversion." }
        require(u16(decoded, 0x0A) == 0) {
            "Held item #${u16(decoded, 0x0A)} has no verified Gen 4 -> Sword/Shield mapping. Remove it in the source game and deposit the Pokémon again."
        }
        val flags = byte(decoded, 0x40)
        val form = (flags ushr 3) and 0x1F
        require(form == 0) { "Only verified default-form PK4 records can enter Sword/Shield in this bounded route." }
        val language = byte(decoded, 0x17)
        require(language == 2) {
            "This bounded route supports English PK4 text only; other glyph mappings remain blocked."
        }
        val nickname = text(decoded, 0x48, 22, "nickname")
        val originalTrainer = text(decoded, 0x68, 16, "Original Trainer name")
        require(originalTrainer.isNotBlank()) { "A blank PK4 Original Trainer name is not destination-valid." }
        val abilityId = byte(decoded, 0x15)
        val abilitySlot = if (personality and 1L == 0L) 1 else 2
        require(abilityId in 1..123) { "Ability #$abilityId is outside the Generation 4 catalogue." }
        val moves = IntArray(4) { u16(decoded, 0x28 + it * 2) }
        val currentPp = IntArray(4) { byte(decoded, 0x30 + it) }
        val ppUps = IntArray(4) { byte(decoded, 0x34 + it) and 3 }
        require((0..3).all { moves[it] != 0 || (currentPp[it] == 0 && ppUps[it] == 0) }) {
            "An empty PK4 move slot contains PP or PP-Up data."
        }
        val originGame = byte(decoded, 0x5F)
        require(originGame in originGames) {
            "Origin game ID $originGame is not a documented native Generation 3-4 title."
        }
        val ball = byte(decoded, 0x86).takeIf { it != 0 } ?: byte(decoded, 0x83)
        require(ball in 1..25) { "Ball ID $ball has no reviewed Gen 4 -> Sword/Shield mapping." }
        val metLevel = byte(decoded, 0x84) and 0x7F
        require(metLevel in 1..100) { "The source met level is outside 1-100." }
        val eggDate = intArrayOf(byte(decoded, 0x78), byte(decoded, 0x79), byte(decoded, 0x7A))
        val metDate = intArrayOf(byte(decoded, 0x7B), byte(decoded, 0x7C), byte(decoded, 0x7D))
        require(date(eggDate[0], eggDate[1], eggDate[2])) { "The source egg date is invalid." }
        require(date(metDate[0], metDate[1], metDate[2])) { "The source met date is invalid." }
        val gender = when {
            flags and 4 != 0 -> 2
            flags and 2 != 0 -> 1
            else -> 0
        }
        val shinyValue = trainerId xor secretId xor personality.toInt() xor (personality ushr 16).toInt()
        return Gen4ToSwordShieldSource(
            species, nickname, originalTrainer, trainerId, secretId, personality,
            u32(decoded, 0x10), byte(decoded, 0x14), abilityId, abilitySlot,
            moves, currentPp, ppUps, IntArray(6) { byte(decoded, 0x18 + it) },
            IntArray(6) { byte(decoded, 0x1E + it) },
            IntArray(6) { ((ivWord ushr (it * 5)) and 31L).toInt() },
            byte(decoded, 0x82),
            u16(decoded, 0x44).takeIf { it != 0 } ?: u16(decoded, 0x7E),
            u16(decoded, 0x46).takeIf { it != 0 } ?: u16(decoded, 0x80),
            metLevel, ball, byte(decoded, 0x84) ushr 7, originGame, language,
            byte(decoded, 0x16), flags and 1 != 0, form, gender,
            eggDate[0], eggDate[1], eggDate[2], metDate[0], metDate[1], metDate[2],
            u32(decoded, 0x24), u32(decoded, 0x3C),
            shinyValue and 0xFFFF < 8, ivWord and 0x80000000L != 0L
        )
    }

    fun review(
        record: ByteArray,
        destinationTrainer: SwordShieldTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): Gen4ToSwordShieldReview {
        val source = inspectSource(record)
        require(destinationTrainer.languageId == 2) {
            "This bounded route currently requires an English Sword/Shield destination trainer."
        }
        require(metadata.baseFriendship in 0..255 && metadata.genderRate in -1..8) {
            "The documented species friendship or gender mapping is invalid."
        }
        require(metadata.abilityId == source.abilityId && metadata.abilityNumber == source.abilitySlot) {
            "The documented Sword/Shield ability does not exactly match PK4 ability #${source.abilityId} in slot ${source.abilitySlot}."
        }
        require(gender(source.personality, metadata.genderRate) == source.gender) {
            "The source gender does not match the documented Sword/Shield species ratio."
        }
        val profile = SwordShieldPersonalCatalogue.profiles(source.species)
            .filter { it.isDefault }
            .distinctBy { listOf(it.hp, it.attack, it.defense, it.speed, it.specialAttack, it.specialDefense, it.growth) }
            .singleOrNull() ?: error("This species has no single verified default Sword/Shield profile.")
        require(source.experience <= minimumExperience(100, profile.growth)) {
            "The source EXP exceeds the reviewed level-100 value."
        }
        val normalizedPp = source.currentPp.copyOf()
        source.moves.forEachIndexed { index, move ->
            if (move != 0) {
                require(SwordShieldMoveCatalog.choice(move) != null) {
                    "Move #$move has no native Sword/Shield mapping."
                }
                normalizedPp[index] = minOf(
                    source.currentPp[index],
                    SwordShieldMoveCatalog.maximumPp(move, source.ppUps[index])
                )
            }
        }
        val mappedEvs = mappedEvs(source.evs)
        val modernPid = preserveShiny(source.personality, source.trainerId, source.secretId, source.shiny)
        val speciesName = SwordShieldSpeciesNames.name(source.species)
        require(speciesName.isNotBlank()) { "The Sword/Shield species name is unavailable." }
        val nickname = source.nickname.ifBlank { speciesName }
        val nicknamed = source.isNicknamed || (source.nickname.isNotBlank() &&
            !source.nickname.equals(speciesName, ignoreCase = true))
        val seed = MessageDigest.getInstance("SHA-256").apply {
            update(record)
            update(destinationTrainer.name.toByteArray(StandardCharsets.UTF_8))
            update(byteArrayOf(destinationTrainer.gender.toByte(), destinationTrainer.languageId.toByte()))
            update(intBytes(metadata.abilityId))
            update(intBytes(metadata.abilityNumber))
        }.digest()
        val pk = ByteArray(PK7_SIZE)
        w32(pk, 0, u32(seed, 0)); w16(pk, 4, 0); w16(pk, 8, source.species)
        w16(pk, 0x0A, 0); w16(pk, 0x0C, source.trainerId); w16(pk, 0x0E, source.secretId)
        w32(pk, 0x10, source.experience); pk[0x14] = source.abilityId.toByte()
        pk[0x15] = source.abilitySlot.toByte(); w32(pk, 0x18, modernPid)
        pk[0x1C] = (source.personality % 25L).toByte()
        pk[0x1D] = (((source.gender and 3) shl 1) or (if (source.fatefulEncounter) 1 else 0)).toByte()
        for (index in 0..5) {
            pk[0x1E + index] = mappedEvs[index].toByte()
            pk[0x24 + index] = source.contest[index].toByte()
        }
        pk[0x2B] = source.pokerus.toByte(); putText(pk, 0x40, nickname)
        for (index in 0..3) {
            w16(pk, 0x5A + index * 2, source.moves[index])
            if (source.moves[index] != 0) {
                pk[0x62 + index] = normalizedPp[index].toByte()
                pk[0x66 + index] = source.ppUps[index].toByte()
            }
        }
        var packedIvs = 0L
        for (index in 0..5) packedIvs = packedIvs or ((source.ivs[index].toLong() and 31L) shl (index * 5))
        if (nicknamed) packedIvs = packedIvs or 0x80000000L
        w32(pk, 0x74, packedIvs); putText(pk, 0x78, destinationTrainer.name)
        pk[0x92] = destinationTrainer.gender.toByte(); pk[0x93] = 1
        pk[0xA2] = source.friendship.toByte(); putText(pk, 0xB0, source.originalTrainer)
        pk[0xCA] = source.friendship.toByte()
        pk[0xD1] = source.eggYear.toByte(); pk[0xD2] = source.eggMonth.toByte(); pk[0xD3] = source.eggDay.toByte()
        pk[0xD4] = source.metYear.toByte(); pk[0xD5] = source.metMonth.toByte(); pk[0xD6] = source.metDay.toByte()
        w16(pk, 0xD8, 0); w16(pk, 0xDA, 30001)
        pk[0xDC] = source.ball.toByte()
        pk[0xDD] = (source.metLevel or (source.originalTrainerGender shl 7)).toByte()
        pk[0xDE] = 0; pk[0xDF] = source.originGame.toByte(); pk[0xE3] = 2
        var checksum = 0
        for (offset in 8 until PK7_SIZE step 2) checksum += u16(pk, offset)
        w16(pk, 6, checksum and 0xFFFF)
        val converted = Gen7ToSwordShieldPk8.review(pk)
        val downstream = converted.effects.filterNot {
            it.startsWith("Explicit Legalise:") || it.startsWith("The PK7 Bank entry")
        }
        val effects = buildList {
            add("Explicit Legalise: direct PSE Gen 4 PK4 -> deterministic in-memory PK7 bridge -> Sword/Shield PK8 conversion; not an official transfer, HOME move, trade, or verified provenance.")
            add("The exact selected checksum-valid 136-byte boxed PK4 remains the bound Bank source. Species #${source.species}, OT/TID/SID, nickname, exact EXP, friendship, IVs, contest values, Pokérus, source title, met level/date, ball, nature, gender, fateful flag and shiny state are retained.")
            add("Moves and PP-Up counts are retained. Current PP ${source.currentPp.joinToString("/")} maps to ${normalizedPp.joinToString("/")}; values above destination limits are explicitly capped.")
            add("Gen 4 EVs ${source.evs.joinToString("/")} map to destination EVs ${mappedEvs.joinToString("/")} by capping each stat at 252 and reducing the largest values until the total is 510.")
            add("The source holds no item; the destination held-item field remains empty.")
            add("Documented ability ${source.abilityId} retains native source slot ${source.abilitySlot}; encounter and moveset legality are not certified.")
            add(if (modernPid == source.personality) "PID 0x${source.personality.toString(16).uppercase().padStart(8, '0')} is retained." else "PID changes from 0x${source.personality.toString(16).uppercase().padStart(8, '0')} to 0x${modernPid.toString(16).uppercase().padStart(8, '0')} only to preserve the Generation 4 non-shiny state under the later-generation shiny threshold; this is an explicit PID/provenance Legalise choice.")
            add("A deterministic encryption constant is bound to the exact PK4, destination trainer and reviewed ability mapping. The destination trainer becomes current handling trainer.")
            add("Generation 4 egg location #${source.eggLocation} maps to 0 and met location #${source.metLocation} maps to the documented later-generation transfer marker 30001; source egg and met dates are retained.")
            add("Markings 0x${source.markings.toString(16).uppercase().padStart(2, '0')} and ribbon words 0x${source.ribbonWordA.toString(16).uppercase().padStart(8, '0')}/0x${source.ribbonWordB.toString(16).uppercase().padStart(8, '0')} are explicitly omitted because this bounded route has no exact PK8 mapping.")
            addAll(downstream)
            add("The PK4 Bank entry and Sword/Shield source remain unchanged. One reviewed empty native PK8 slot is written only to a new copy with caught registration, retained recovery, persisted reopen verification and two-sided rollback.")
        }
        return Gen4ToSwordShieldReview(converted.nativeRecord.copyOf(), source.species, source.nickname, effects)
    }

    private fun decrypt(record: ByteArray): ByteArray {
        val decoded = record.copyOf()
        crypt(decoded, 8, 136, u16(decoded, 6).toLong())
        unshuffle(decoded)
        return decoded
    }

    private fun crypt(bytes: ByteArray, start: Int, end: Int, seedIn: Long) {
        var seed = seedIn and 0xFFFFFFFFL
        var offset = start
        while (offset + 1 < end) {
            seed = (0x41C64E6DL * seed + 0x6073L) and 0xFFFFFFFFL
            w16(bytes, offset, u16(bytes, offset) xor ((seed ushr 16).toInt() and 0xFFFF))
            offset += 2
        }
    }

    private fun unshuffle(bytes: ByteArray) {
        val sv = (u32(bytes, 0).toInt() ushr 13) and 31
        if (sv == 0) return
        val blockAt = intArrayOf(0, 1, 2, 3)
        val slotOf = intArrayOf(0, 1, 2, 3)
        for (slot in 0 until 3) {
            val desired = blockPosition[sv * 4 + slot]
            val other = slotOf[desired]
            if (other == slot) continue
            for (index in 0 until 32) {
                val a = 8 + slot * 32 + index
                val b = 8 + other * 32 + index
                val temporary = bytes[a]; bytes[a] = bytes[b]; bytes[b] = temporary
            }
            val moved = blockAt[slot]
            blockAt[other] = moved
            slotOf[moved] = other
        }
    }

    private fun text(bytes: ByteArray, offset: Int, length: Int, label: String): String {
        val out = StringBuilder()
        var index = 0
        while (index + 1 < length) {
            val value = u16(bytes, offset + index)
            if (value == 0 || value == 0xFFFF) break
            val character = when {
                value in 0x121..0x12A -> ('0'.code + value - 0x121).toChar()
                value in 0x12B..0x144 -> ('A'.code + value - 0x12B).toChar()
                value in 0x145..0x15E -> ('a'.code + value - 0x145).toChar()
                value == 0x1DE -> ' '; value == 0x1AC -> '!'; value == 0x1AD -> '?'
                value == 0x1AE -> ','; value == 0x1AF -> '.'; value == 0x1B1 -> '/'
                value == 0x1B3 -> '\''; value == 0x1BA -> '('; value == 0x1BB -> ')'
                value == 0x1BD -> '♂'; value == 0x1BE -> '♀'; value == 0x1BF -> '+'
                value == 0x1C0 -> '-'; value == 0x1C1 -> '*'; value == 0x1C3 -> '='
                value == 0x1C4 -> '&'; value == 0x1C6 -> ':'; value == 0x1C7 -> ';'
                value == 0x1D0 -> '@'; value == 0x1D2 -> '%'; value == 0x1E9 -> '_'
                else -> error("The Gen 4 $label contains unsupported glyph 0x${value.toString(16).uppercase().padStart(4, '0')}.")
            }
            out.append(character)
            index += 2
        }
        return out.toString().trim()
    }

    private fun mappedEvs(source: IntArray): IntArray {
        val result = IntArray(6) { source[it].coerceIn(0, 252) }
        while (result.sum() > 510) {
            val index = result.indices.maxByOrNull { result[it] } ?: 0
            result[index]--
        }
        return result
    }

    private fun preserveShiny(pid: Long, tid: Int, sid: Int, shiny: Boolean): Long {
        val value = pid and 0xFFFFFFFFL
        val modern = ((tid xor sid xor value.toInt() xor (value ushr 16).toInt()) and 0xFFFF) < 16
        return if (modern == shiny) value else value xor 0x10000000L
    }

    private fun gender(pid: Long, rate: Int): Int = when {
        rate < 0 -> 2
        rate == 0 -> 0
        rate >= 8 -> 1
        (pid.toInt() and 0xFF) < rate * 32 -> 1
        else -> 0
    }

    private fun date(year: Int, month: Int, day: Int): Boolean {
        if (year == 0 && month == 0 && day == 0) return true
        if (month !in 1..12 || day < 1) return false
        val fullYear = 2000 + year
        val days = when (month) {
            2 -> if (fullYear % 400 == 0 || (fullYear % 4 == 0 && fullYear % 100 != 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return day <= days
    }

    private fun minimumExperience(level: Int, growth: Int): Long {
        if (level <= 1) return 0
        val n = level.toLong(); val cube = n * n * n
        return when (growth) {
            0 -> cube
            1 -> when { level <= 50 -> cube * (100 - n) / 50; level <= 68 -> cube * (150 - n) / 100; level <= 98 -> { val r = n % 3; cube * (1274 + r * r - 9 * r - 20 * (n / 3)) / 1000 }; else -> cube * (160 - n) / 100 }
            2 -> when { level <= 15 -> cube * (24 + (n + 1) / 3) / 50; level <= 35 -> cube * (14 + n) / 50; else -> cube * (32 + n / 2) / 50 }
            3 -> maxOf(0L, 6 * cube / 5 - 15 * n * n + 100 * n - 140)
            4 -> 4 * cube / 5
            5 -> 5 * cube / 4
            else -> error("The Sword/Shield growth group is unsupported.")
        }
    }

    private fun putText(bytes: ByteArray, offset: Int, value: String) {
        val encoded = value.toByteArray(StandardCharsets.UTF_16LE)
        require(encoded.size <= 24) { "Converted text exceeds 12 characters." }
        encoded.copyInto(bytes, offset)
    }
    private fun intBytes(value: Int) = byteArrayOf(value.toByte(), (value ushr 8).toByte(), (value ushr 16).toByte(), (value ushr 24).toByte())
    private fun byte(bytes: ByteArray, offset: Int) = bytes[offset].toInt() and 255
    private fun u16(bytes: ByteArray, offset: Int) = byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun u32(bytes: ByteArray, offset: Int) = u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun w16(bytes: ByteArray, offset: Int, value: Int) { bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte() }
    private fun w32(bytes: ByteArray, offset: Int, value: Long) { w16(bytes, offset, value.toInt()); w16(bytes, offset + 2, (value ushr 16).toInt()) }
}
