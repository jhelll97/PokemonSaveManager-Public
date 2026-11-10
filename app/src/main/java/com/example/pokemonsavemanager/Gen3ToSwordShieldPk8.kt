package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class Gen3ToSwordShieldSource(
    val species: Int,
    val nickname: String,
    val originalTrainer: String,
    val trainerId: Int,
    val secretId: Int,
    val personality: Long,
    val experience: Long,
    val heldItem: Int,
    val friendship: Int,
    val abilitySlot: Int,
    val moves: IntArray,
    val currentPp: IntArray,
    val ppUps: IntArray,
    val evs: IntArray,
    val contest: IntArray,
    val ivs: IntArray,
    val pokerus: Int,
    val metLocation: Int,
    val metLevel: Int,
    val ball: Int,
    val originalTrainerGender: Int,
    val originGame: Int,
    val language: Int,
    val markings: Int,
    val ribbonWord: Long,
    val shiny: Boolean
)

data class Gen3ToSwordShieldReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val effects: List<String>
)

/** Converts one exact checksum-valid 80-byte PK3 Bank record through a reviewed PK7 bridge. */
object Gen3ToSwordShieldPk8 {
    private const val PK7_SIZE = 0xE8

    fun inspectSource(record: ByteArray): Gen3ToSwordShieldSource {
        val decoded = Gen3Pk3Codec.decode(record)
        val header = decoded.header
        val data = decoded.canonicalSection
        val flags = byte(header, 0x13)
        require(flags and 0x01 == 0) { "Bad-Egg PK3 records are blocked." }
        require(flags and 0x02 != 0) { "The PK3 does not contain a present Pokémon." }
        val internalSpecies = u16(data, 0)
        val species = nationalSpecies(internalSpecies)
        require(species in 1..386) { "The PK3 species has no reviewed National Pokédex mapping." }
        val ivWord = u32(data, 40)
        require(ivWord and 0x40000000L == 0L && flags and 0x04 == 0) {
            "Egg PK3 records remain blocked from this PSE conversion."
        }
        val personality = u32(header, 0)
        val owner = u32(header, 4)
        val trainerId = (owner and 0xFFFFL).toInt()
        val secretId = ((owner ushr 16) and 0xFFFFL).toInt()
        val language = byte(header, 0x12)
        require(language == 2) {
            "This bounded route supports English PK3 text only; other glyph mappings remain blocked."
        }
        val nickname = text(header, 8, 10, "nickname")
        val originalTrainer = text(header, 0x14, 7, "Original Trainer name")
        require(originalTrainer.isNotBlank()) { "A blank PK3 Original Trainer name is not destination-valid." }
        val ppBonus = byte(data, 8)
        val moves = IntArray(4) { u16(data, 12 + it * 2) }
        val currentPp = IntArray(4) { byte(data, 20 + it) }
        val ppUps = IntArray(4) { (ppBonus ushr (it * 2)) and 3 }
        require((0..3).all { moves[it] != 0 || (currentPp[it] == 0 && ppUps[it] == 0) }) {
            "An empty PK3 move slot contains PP or PP-Up data."
        }
        val origins = u16(data, 38)
        val originGame = (origins ushr 7) and 15
        require(originGame in 1..5) { "Origin game ID $originGame is not a reviewed Generation 3 title." }
        val ball = (origins ushr 11) and 15
        require(ball in 0..12) { "Ball ID $ball has no reviewed PK3 -> PK8 mapping." }
        val shinyValue = trainerId xor secretId xor personality.toInt() xor (personality ushr 16).toInt()
        return Gen3ToSwordShieldSource(
            species = species,
            nickname = nickname,
            originalTrainer = originalTrainer,
            trainerId = trainerId,
            secretId = secretId,
            personality = personality,
            experience = u32(data, 4),
            heldItem = u16(data, 2),
            friendship = byte(data, 9),
            abilitySlot = ((ivWord ushr 31).toInt() and 1) + 1,
            moves = moves,
            currentPp = currentPp,
            ppUps = ppUps,
            evs = IntArray(6) { byte(data, 24 + it) },
            contest = IntArray(6) { byte(data, 30 + it) },
            ivs = IntArray(6) { ((ivWord ushr (it * 5)) and 31L).toInt() },
            pokerus = byte(data, 36),
            metLocation = byte(data, 37),
            metLevel = origins and 0x7F,
            ball = ball,
            originalTrainerGender = (origins ushr 15) and 1,
            originGame = originGame,
            language = language,
            markings = byte(header, 0x1B),
            ribbonWord = u32(data, 44),
            shiny = (shinyValue and 0xFFFF) < 8
        )
    }

    fun review(
        record: ByteArray,
        destinationTrainer: SwordShieldTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): Gen3ToSwordShieldReview {
        val source = inspectSource(record)
        require(destinationTrainer.languageId == 2) {
            "This bounded route currently requires an English Sword/Shield destination trainer."
        }
        require(metadata.baseFriendship in 0..255 && metadata.genderRate in -1..8) {
            "The documented species friendship or gender mapping is invalid."
        }
        require(metadata.abilityId in 1..255 && metadata.abilityNumber == source.abilitySlot) {
            "The documented Sword/Shield ability does not match PK3 ability slot ${source.abilitySlot}."
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
        val nature = (source.personality % 25L).toInt()
        val gender = gender(source.personality, metadata.genderRate)
        val speciesName = SwordShieldSpeciesNames.name(source.species)
        require(speciesName.isNotBlank()) { "The Sword/Shield species name is unavailable." }
        val nickname = source.nickname.ifBlank { speciesName }
        val nicknamed = source.nickname.isNotBlank() && !source.nickname.equals(speciesName, ignoreCase = true)
        val metLevel = source.metLevel.coerceAtLeast(1)
        val ball = if (source.ball == 0) 4 else source.ball
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
        w32(pk, 0x10, source.experience); pk[0x14] = metadata.abilityId.toByte()
        pk[0x15] = metadata.abilityNumber.toByte(); w32(pk, 0x18, modernPid)
        pk[0x1C] = nature.toByte(); pk[0x1D] = ((gender and 3) shl 1).toByte()
        for (index in 0..5) { pk[0x1E + index] = mappedEvs[index].toByte(); pk[0x24 + index] = source.contest[index].toByte() }
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
        pk[0xCA] = source.friendship.toByte(); w16(pk, 0xD8, 0); w16(pk, 0xDA, 0)
        pk[0xDC] = ball.toByte(); pk[0xDD] = (metLevel or (source.originalTrainerGender shl 7)).toByte()
        pk[0xDE] = 0; pk[0xDF] = source.originGame.toByte(); pk[0xE3] = 2
        var checksum = 0
        for (offset in 8 until PK7_SIZE step 2) checksum += u16(pk, offset)
        w16(pk, 6, checksum and 0xFFFF)
        val converted = Gen7ToSwordShieldPk8.review(pk)
        val downstream = converted.effects.filterNot {
            it.startsWith("Explicit Legalise:") || it.startsWith("The PK7 Bank entry")
        }
        val effects = buildList {
            add("Explicit Legalise: direct PSE Gen 3 PK3 -> deterministic in-memory PK7 bridge -> Sword/Shield PK8 conversion; not an official transfer, HOME move, trade, or verified provenance.")
            add("The exact selected checksum-valid 80-byte PK3 remains the bound Bank source. Species #${source.species}, OT/TID/SID, nickname, exact EXP, friendship, IVs, contest values, Pokérus, origin game, met level, ball, nature, gender and shiny state are retained.")
            add("Moves and PP-Up counts are retained. Current PP ${source.currentPp.joinToString("/")} maps to ${normalizedPp.joinToString("/")}; values above destination limits are explicitly capped.")
            add("Gen 3 EVs ${source.evs.joinToString("/")} map to destination EVs ${mappedEvs.joinToString("/")} by capping each stat at 252 and reducing the largest values until the total is 510.")
            add(if (source.heldItem == 0) "The source holds no item; the destination held-item field remains empty." else "Held item #${source.heldItem} is explicitly removed because Gen 3 and Sword/Shield item IDs are not interchangeable.")
            add("Documented ability ${metadata.abilityId} retains native source slot ${source.abilitySlot}; encounter and moveset legality are not certified.")
            add(if (modernPid == source.personality) "PID 0x${source.personality.toString(16).uppercase().padStart(8, '0')} is retained." else "PID changes from 0x${source.personality.toString(16).uppercase().padStart(8, '0')} to 0x${modernPid.toString(16).uppercase().padStart(8, '0')} only to preserve the Gen 3 non-shiny state under the later-generation shiny threshold; this is an explicit PID/provenance Legalise choice.")
            add("A deterministic encryption constant is bound to the exact PK3, destination trainer and reviewed ability mapping. The destination trainer becomes current handling trainer.")
            add("Source met-location byte ${source.metLocation}, markings 0x${source.markings.toString(16).uppercase().padStart(2, '0')} and ribbon/obedience word 0x${source.ribbonWord.toString(16).uppercase().padStart(8, '0')} are explicitly omitted because this bounded route has no exact PK8 mapping.")
            if (source.metLevel == 0) add("Unset Gen 3 met level 0 is normalized to destination met level 1.")
            if (source.ball == 0) add("Unset Gen 3 ball 0 is normalized to a Poké Ball.")
            addAll(downstream)
            add("The PK3 Bank entry and Sword/Shield source remain unchanged. One reviewed empty native PK8 slot is written only to a new copy with caught registration, retained recovery, persisted reopen verification and two-sided rollback.")
        }
        return Gen3ToSwordShieldReview(converted.nativeRecord.copyOf(), source.species, source.nickname, effects)
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

    private fun nationalSpecies(value: Int) = when { value in 1..251 -> value; value in 277..411 -> value - 25; else -> 0 }
    private fun text(bytes: ByteArray, offset: Int, length: Int, label: String): String {
        val out = StringBuilder()
        for (index in offset until offset + length) {
            val value = byte(bytes, index)
            if (value == 0xFF) break
            val character = when {
                value in 0xA1..0xAA -> ('0'.code + value - 0xA1).toChar()
                value in 0xBB..0xD4 -> ('A'.code + value - 0xBB).toChar()
                value in 0xD5..0xEE -> ('a'.code + value - 0xD5).toChar()
                value == 0x00 -> ' '; value == 0xAB -> '!'; value == 0xAC -> '?'
                value == 0xAD -> '.'; value == 0xAE -> '-'; value == 0xB4 -> '\''
                value == 0xB5 -> '♂'; value == 0xB6 -> '♀'; value == 0xB8 -> ','; value == 0xBA -> '/'
                else -> error("The Gen 3 $label contains unsupported byte 0x${value.toString(16).uppercase().padStart(2, '0')}.")
            }
            out.append(character)
        }
        return out.toString().trim()
    }
    private fun putText(bytes: ByteArray, offset: Int, value: String) { val encoded = value.toByteArray(StandardCharsets.UTF_16LE); require(encoded.size <= 24) { "Converted text exceeds 12 characters." }; encoded.copyInto(bytes, offset) }
    private fun intBytes(value: Int) = byteArrayOf(value.toByte(), (value ushr 8).toByte(), (value ushr 16).toByte(), (value ushr 24).toByte())
    private fun byte(bytes: ByteArray, offset: Int) = bytes[offset].toInt() and 255
    private fun u16(bytes: ByteArray, offset: Int) = byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun u32(bytes: ByteArray, offset: Int) = u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun w16(bytes: ByteArray, offset: Int, value: Int) { bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte() }
    private fun w32(bytes: ByteArray, offset: Int, value: Long) { w16(bytes, offset, value.toInt()); w16(bytes, offset + 2, (value ushr 16).toInt()) }
}
