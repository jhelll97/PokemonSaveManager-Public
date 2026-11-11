package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets

data class LegendsZaToLetsGoReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val destinationVersion: LetsGoVersion,
    val effects: List<String>
)

/** Converts one reviewed native Legends: Z-A PA9 Bank record into a native Let's Go PB7. */
object LegendsZaToLetsGoPb7 {
    private const val PA9_SIZE = 0x158
    private const val PA9_STORED_END = 0x148
    private const val PB7_SIZE = 0x104
    private const val PB7_STORED_END = 0xE8
    private val orders = listOf("0123","0132","0213","0312","0231","0321","1023","1032",
        "2013","3012","2031","3021","1203","1302","2103","3102","2301","3201",
        "1230","1320","2130","3120","2310","3210")
    private val reviewedOrigins = setOf(1,2,3,4,5,7,8,10,11,12,15,20,21,22,23,24,25,26,27,
        30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,47,48,49,50,51)

    fun review(record: ByteArray, destinationVersion: LetsGoVersion): LegendsZaToLetsGoReview {
        val source = LegendsZaPseBankRestoreEditing.inspectBankRecord(record)
        val s = plainPa9(record)
        val species = source.species
        val form = source.form
        val heldItem = source.heldItem
        val ability = source.ability
        val abilityNumber = byte(s, 0x16) and 7
        val markings = u16(s, 0x18)
        val nature = source.nature
        val statNature = source.statNature
        val sourceFlags = byte(s, 0x22)
        val gender = source.gender
        val experience = source.experience
        val iv32 = u32(s, 0x8C)
        val hyper = source.hyperTrainingFlags
        val language = byte(s, 0xD5)
        val sourceOrigin = byte(s, 0xCE)
        val sourceBall = source.ball
        val destinationBall = if (sourceBall == 27) 4 else sourceBall
        val metLevel = byte(s, 0x125) and 0x7F
        val currentHandler = source.currentHandler
        val sourceScale = source.scale
        val sourceAlpha = source.isAlpha
        val heightScalar = byte(s, 0x48)
        val weightScalar = byte(s, 0x49)
        val obedience = source.obedienceLevel

        require(form == 0) {
            "Legends: Z-A form $form has no reviewed native Let's Go mapping in this bounded route."
        }
        val personal = LetsGoPersonalCatalogue.resolve(species, 0)
            ?: throw IllegalArgumentException(
                "Species #$species is not available in the reviewed Let's Go destination catalogue."
            )
        require(!source.isEgg) { "Egg PA9 records remain blocked from this PSE conversion." }
        require(heldItem == 0) {
            "Held item #$heldItem has no reviewed Legends: Z-A -> Let's Go mapping. Remove it in the source game first."
        }
        require(nature in 0..24 && statNature in 0..24) {
            "The source nature data is outside the native range."
        }
        require(ability in 1..233 && abilityNumber in setOf(1, 2, 4)) {
            "The source ability ID or slot is outside the reviewed PB7 storage range."
        }
        require(gender in 0..2) { "The source gender field is outside the reviewed PB7 range." }
        require(language == 2) {
            "This bounded route supports English PA9 text only; other glyph translations remain blocked."
        }
        require(sourceOrigin in reviewedOrigins) {
            "Origin game ID $sourceOrigin has no reviewed PA9 -> PB7 mapping."
        }
        require(sourceBall in 1..27) {
            "Ball ID $sourceBall is outside the reviewed Legends: Z-A range."
        }
        require(metLevel in 1..100) { "The source met level is outside 1-100." }
        require(obedience in 0..100) { "The source obedience level is outside 0-100." }
        require(date(byte(s,0x119), byte(s,0x11A), byte(s,0x11B))) {
            "The source egg date is invalid."
        }
        require(date(byte(s,0x11C), byte(s,0x11D), byte(s,0x11E))) {
            "The source met date is invalid."
        }
        require(hyper and 0xC0 == 0) {
            "The source Hyper Training field contains unsupported bits."
        }
        require(currentHandler in 0..1) {
            "The source current-handler field is outside the reviewed PB7 range."
        }

        val evs = source.evs.toIntArray()
        require(evs.all { it in 0..252 } && evs.sum() <= 510) {
            "The source EVs exceed native per-stat or total limits."
        }
        val ivs = source.ivs.toIntArray()
        val moves = source.moves.map { it.id }.toIntArray()
        val pp = source.moves.map { it.pp }.toIntArray()
        val ppUps = source.moves.map { it.ppUps }.toIntArray()
        var emptyMoveSeen = false
        val usedMoves = mutableSetOf<Int>()
        for (index in 0..3) {
            val move = moves[index]
            if (move == 0) {
                emptyMoveSeen = true
                require(pp[index] == 0 && ppUps[index] == 0) {
                    "Empty move slot ${index + 1} contains PP or PP-Up data."
                }
            } else {
                require(!emptyMoveSeen) { "Let's Go move slots must be consecutive." }
                require(usedMoves.add(move)) { "Let's Go move slots must be unique." }
                require(ppUps[index] in 0..3) { "Move #$move has an invalid PP-Up count." }
                require(LetsGoMoveCatalog.choice(move) != null) {
                    "Move #$move is not usable in Let's Go Pikachu/Eevee."
                }
                require(pp[index] in 0..LetsGoMoveCatalog.maximumPp(move, ppUps[index])) {
                    "Move #$move has PP outside its Let's Go limit."
                }
            }
        }
        source.relearnMoves.filter { it != 0 }.forEach {
            require(LetsGoMoveCatalog.choice(it) != null) {
                "Relearn move #$it is not usable in Let's Go Pikachu/Eevee."
            }
        }

        val nickname = text(s, 0x58, "nickname")
        val handlingTrainer = text(s, 0xA8, "Handling Trainer name")
        val originalTrainer = text(s, 0xF8, "Original Trainer name")
        require(experience <= minimumExperience(100, personal.growth)) {
            "The source EXP exceeds the reviewed Let's Go Level 100 value."
        }
        val level = levelForExperience(experience, personal.growth)
        val friendship = if (currentHandler == 0) source.originalTrainerFriendship
            else source.handlingTrainerFriendship
        val effectiveIvs = IntArray(6) { index ->
            val bit = intArrayOf(0, 1, 2, 5, 3, 4)[index]
            if (hyper and (1 shl bit) != 0) 31 else ivs[index]
        }
        val base = intArrayOf(personal.hp, personal.attack, personal.defense, personal.speed,
            personal.specialAttack, personal.specialDefense)
        val scalar = (((friendship / 255.0f / 10.0f) + 1.0f) * 100.0f).toInt()
        fun raw(index: Int): Int = (effectiveIvs[index] + 2 * base[index]) * level / 100
        fun adjusted(index: Int, value: Int): Int {
            val up = nature / 5
            val down = nature % 5
            if (up == down) return value
            return when (index) {
                up -> value * 110 / 100
                down -> value * 90 / 100
                else -> value
            }
        }
        val maximumHp = raw(0) + 10 + level
        val attack = scalar * adjusted(0, raw(1) + 5) / 100
        val defense = scalar * adjusted(1, raw(2) + 5) / 100
        val speed = scalar * adjusted(4, raw(3) + 5) / 100
        val specialAttack = scalar * adjusted(2, raw(4) + 5) / 100
        val specialDefense = scalar * adjusted(3, raw(5) + 5) / 100
        val baseCpSum = raw(0) + 10 + level + attack + defense + speed + specialAttack + specialDefense
        val combatPower = minOf(10000, (baseCpSum * 6f * level / 100f).toInt())

        val o = ByteArray(PB7_SIZE)
        w32(o, 0, source.encryptionConstant)
        w16(o, 8, species)
        copy(s, 0x0C, o, 0x0C, 8)
        o[0x14] = ability.toByte()
        o[0x15] = abilityNumber.toByte()
        w16(o, 0x16, markings and 0x0FFF)
        w32(o, 0x18, source.pid)
        o[0x1C] = nature.toByte()
        o[0x1D] = ((sourceFlags and 1) or (gender shl 1)).toByte()
        copy(s, 0x26, o, 0x1E, 6)
        o[0x2B] = s[0x32]
        putText(o, 0x40, nickname)
        copy(s, 0x72, o, 0x5A, 16)
        copy(s, 0x82, o, 0x6A, 8)
        w32(o, 0x74, iv32)
        putText(o, 0x78, handlingTrainer)
        o[0x92] = s[0xC2]
        o[0x93] = s[0xC4]
        o[0xA2] = s[0xC8]
        putText(o, 0xB0, originalTrainer)
        o[0xCA] = s[0x112]
        copy(s, 0x119, o, 0xD1, 6)
        copy(s, 0x120, o, 0xD8, 4)
        o[0xDC] = destinationBall.toByte()
        o[0xDD] = s[0x125]
        o[0xDE] = s[0x126]
        o[0xDF] = destinationVersion.gameId.toByte()
        o[0xE3] = language.toByte()
        o[0xEC] = level.toByte()
        w16(o, 0xF0, maximumHp)
        w16(o, 0xF2, maximumHp)
        w16(o, 0xF4, attack)
        w16(o, 0xF6, defense)
        w16(o, 0xF8, speed)
        w16(o, 0xFA, specialAttack)
        w16(o, 0xFC, specialDefense)
        w16(o, 0xFE, combatPower)
        var checksum = 0
        for (offset in 8 until PB7_STORED_END step 2) checksum += u16(o, offset)
        w16(o, 6, checksum and 0xFFFF)

        val native = encryptPb7(o)
        val reopened = (LetsGoPokemonReadOnly.read(native) as? LetsGoSlotRead.Pokemon)?.value
            ?: error("The converted PB7 did not reopen with a valid native checksum.")
        val reopenedPlain = plainPb7(native)
        require(reopened.species == species && reopened.form == 0 && reopened.gender == gender &&
            reopened.fatefulEncounter == source.fatefulEncounter && reopened.pid == source.pid &&
            reopened.experience == experience && reopened.nature == nature &&
            reopened.cachedLevel == level && reopened.currentHp == maximumHp &&
            reopened.maximumHp == maximumHp && reopened.attack == attack &&
            reopened.defense == defense && reopened.speed == speed &&
            reopened.specialAttack == specialAttack && reopened.specialDefense == specialDefense &&
            reopened.combatPower == combatPower && reopened.moves.map { it.id } == moves.toList() &&
            reopened.moves.map { it.pp } == pp.toList() &&
            reopened.moves.map { it.ppUps } == ppUps.toList() && reopened.ivs == source.ivs &&
            reopened.awakened.all { it == 0 } && reopened.currentHandler == currentHandler &&
            reopened.currentFriendship == friendship && byte(reopenedPlain, 0x14) == ability &&
            (byte(reopenedPlain, 0x15) and 7) == abilityNumber &&
            (0..5).map { byte(reopenedPlain, 0x1E + it) } == source.evs &&
            byte(reopenedPlain, 0xDF) == destinationVersion.gameId &&
            byte(reopenedPlain, 0xDC) == destinationBall && byte(reopenedPlain, 0xE3) == language) {
            "The converted PB7 did not reopen with the reviewed identity, values, stats, moves, origin, Poké Ball, and title defaults."
        }
        LetsGoPseBankRestoreEditing.inspectBankRecord(native)

        val ribbons = s.copyOfRange(0x34, 0x48)
        val tracker = s.copyOfRange(0x127, 0x12F)
        val moveRecords = s.copyOfRange(0x12F, 0x148) + s.copyOfRange(0x4B, 0x4F)
        val sourceStatus = u32(s, 0x90)
        val sourceBattleVersion = byte(s, 0xCF)
        val sourceFormArgument = u32(s, 0xD0)
        val sourceAffixed = byte(s, 0xD4)
        val contest = s.copyOfRange(0x2C, 0x32)
        val ballEffect = if (sourceBall == destinationBall)
            "Ball ID $sourceBall is retained as destination-valid Let's Go storage data."
        else
            "Strange Ball ID $sourceBall becomes Poké Ball #$destinationBall because Let's Go has no destination-valid Strange Ball field; this is a disclosed Legalise mapping."
        val statNatureEffect = if (statNature == nature)
            "Source stat nature equals nature $nature; PB7 stores that one native nature value."
        else
            "Source stat nature $statNature is omitted because PB7 has only one nature field; PB7 uses retained original nature $nature for rebuilt stats. This changes the source mint effect by explicit Legalise choice."
        val effects = listOf(
            "Explicit Legalise: PSE Legends: Z-A PA9 -> ${destinationVersion.displayName} PB7 conversion; this backward cross-generation route is not an official HOME transfer, in-game trade, encounter record, or verified provenance.",
            "Species #$species and exact default form 0 map to one verified Let's Go personal profile. PID, EC, TID/SID, EXP, IVs, gender, shiny state, Original Trainer and Handling Trainer friendship, Pokérus, dates, locations, met level, OT gender, fateful flag, Hyper Training, moves, PP, PP Ups and relearn moves are retained.",
            "PA9 EVs ${source.evs.joinToString("/")} are retained in PB7's six hidden EV bytes; all six gameplay Awakening Values become 0. PA9 contest values ${contest.joinToString(" ") { "%02X".format(it) }} are omitted because PB7 uses those offsets for Awakening Values.",
            statNatureEffect,
            "Nickname, OT text, HT text, English language and current-handler state are retained as UTF-16 text; source text trash bytes are normalized.",
            "Held item remains empty. Ability ID $ability and slot $abilityNumber are retained as PB7 storage data; Let's Go encounter, ability and moveset legality are not certified.",
            "Source origin game ID $sourceOrigin becomes the explicit selected destination origin ${destinationVersion.displayName} ID ${destinationVersion.gameId}; PSE does not claim an official transfer or provenance.",
            ballEffect,
            "Source Alpha flag $sourceAlpha, Height Scalar $heightScalar, Weight Scalar $weightScalar and display Scale $sourceScale have no reviewed PB7 mapping and are cleared. No Alpha, size, Mega or temporary battle-form state is invented.",
            "PA9 Obedience Level $obedience has no PB7 equivalent and is omitted. Current HP is rebuilt full at $maximumHp; Level $level, five battle stats and CP $combatPower are rebuilt from retained EXP/IVs/nature/friendship, zero Awakening Values and the verified Let's Go profile. Retained met level is $metLevel.",
            if (markings and 0xF000 == 0) "All 12 supported marking bits are retained." else "High marking bits 0x${(markings and 0xF000).toString(16).uppercase()} are cleared; low 12 bits are retained.",
            if (ribbons.all { it == 0.toByte() } && sourceAffixed == 0xFF) "No Legends: Z-A ribbon, mark or affixed-ribbon data requires omission." else "Ribbon/mark bytes ${ribbons.joinToString(" ") { "%02X".format(it) }} and affixed-ribbon byte $sourceAffixed are cleared; this bounded adapter has no reviewed PA9 -> PB7 ribbon mapping.",
            "PA9 Handling Trainer language, handling memories, OT memories, geography, sociability, fullness/enjoyment, status 0x${sourceStatus.toString(16).uppercase()}, Battle Version $sourceBattleVersion, Form Argument $sourceFormArgument, Mega metadata, Tera metadata and other PA9-only bytes remain PB7 defaults.",
            "PA9 move-record and Technical Record bytes ${moveRecords.joinToString(" ") { "%02X".format(it) }} are cleared.",
            if (tracker.all { it == 0.toByte() }) "No source HOME tracker bytes require omission." else "Source HOME tracker bytes ${tracker.joinToString(" ") { "%02X".format(it) }} are cleared; PSE does not invent or claim an official HOME identity.",
            "The PA9 Bank entry and Let's Go source save remain unchanged. PSE writes exactly the next unused native PB7 storage record in a new copy, updates the native storage count and block CRCs, retains a recovery backup, reopens the persisted destination, removes a failed copy when possible, and re-verifies both unchanged sources."
        )
        return LegendsZaToLetsGoReview(native, species, nickname, destinationVersion, effects)
    }

    private fun plainPa9(record: ByteArray): ByteArray {
        require(record.size == PA9_SIZE) { "A complete 344-byte native PA9 record is required." }
        require(u16(record, 4) == 0) { "The PA9 sanity field is not zero." }
        val ec = u32(record, 0)
        val decoded = record.copyOf()
        xor(decoded, 8, PA9_STORED_END, ec)
        xor(decoded, PA9_STORED_END, PA9_SIZE, ec)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val plain = decoded.copyOf()
        for (block in 0..3) {
            val from = 8 + (order[block] - '0') * 0x50
            decoded.copyInto(plain, 8 + block * 0x50, from, from + 0x50)
        }
        var checksum = 0
        for (offset in 8 until PA9_STORED_END step 2) checksum += u16(plain, offset)
        require((checksum and 0xFFFF) == u16(record, 6)) { "The PA9 checksum failed." }
        return plain
    }

    private fun plainPb7(record: ByteArray): ByteArray {
        require(record.size == PB7_SIZE) { "A complete 260-byte native PB7 record is required." }
        val ec = u32(record, 0)
        val decoded = record.copyOf()
        xor(decoded, 8, PB7_STORED_END, ec)
        xor(decoded, PB7_STORED_END, PB7_SIZE, ec)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val plain = decoded.copyOf()
        for (block in 0..3) {
            val from = 8 + (order[block] - '0') * 0x38
            decoded.copyInto(plain, 8 + block * 0x38, from, from + 0x38)
        }
        var checksum = 0
        for (offset in 8 until PB7_STORED_END step 2) checksum += u16(plain, offset)
        require((checksum and 0xFFFF) == u16(record, 6)) { "The converted PB7 checksum failed." }
        return plain
    }

    private fun encryptPb7(plain: ByteArray): ByteArray {
        val ec = u32(plain, 0)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val shuffled = plain.copyOf()
        for (block in 0..3) {
            val destination = 8 + (order[block] - '0') * 0x38
            plain.copyInto(shuffled, destination, 8 + block * 0x38, 8 + (block + 1) * 0x38)
        }
        xor(shuffled, 8, PB7_STORED_END, ec)
        xor(shuffled, PB7_STORED_END, PB7_SIZE, ec)
        return shuffled
    }

    private fun text(bytes: ByteArray, offset: Int, label: String): String {
        var end = offset
        while (end < offset + 26 && u16(bytes, end) != 0) end += 2
        val value = String(bytes, offset, end - offset, StandardCharsets.UTF_16LE)
        require(value.length <= 12 && value.all {
            it.code !in 0xD800..0xDFFF && it.code >= 0x20 && it.code !in 0xE000..0xF8FF
        }) { "The $label contains an unmapped glyph." }
        return value
    }

    private fun putText(bytes: ByteArray, offset: Int, value: String) {
        val encoded = value.toByteArray(StandardCharsets.UTF_16LE)
        require(encoded.size <= 24) { "Converted text exceeds 12 characters." }
        encoded.copyInto(bytes, offset)
    }

    private fun date(year: Int, month: Int, day: Int): Boolean =
        if (year == 0 && month == 0 && day == 0) true
        else year in 0..99 && month in 1..12 && day in 1..31 &&
            runCatching { java.time.LocalDate.of(2000 + year, month, day) }.isSuccess

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
                    cube * (1274 + remainder * remainder - 9 * remainder - 20 * (n / 3)) / 1000
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
            else -> error("Unsupported growth group.")
        }
    }

    private fun levelForExperience(experience: Long, growth: Int): Int {
        var level = 1
        for (candidate in 2..100) {
            if (experience < minimumExperience(candidate, growth)) break
            level = candidate
        }
        return level
    }

    private fun xor(bytes: ByteArray, from: Int, end: Int, seed: Long) {
        var state = seed
        for (offset in from until end step 2) {
            state = (state * 0x41C64E6DL + 0x6073L) and 0xFFFFFFFFL
            w16(bytes, offset, u16(bytes, offset) xor (state ushr 16).toInt())
        }
    }

    private fun copy(source: ByteArray, sourceOffset: Int, destination: ByteArray,
                     destinationOffset: Int, length: Int) =
        source.copyInto(destination, destinationOffset, sourceOffset, sourceOffset + length)
    private fun byte(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 255
    private fun u16(bytes: ByteArray, offset: Int): Int =
        byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long =
        u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun w16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
    private fun w32(bytes: ByteArray, offset: Int, value: Long) {
        w16(bytes, offset, value.toInt())
        w16(bytes, offset + 2, (value ushr 16).toInt())
    }
}
