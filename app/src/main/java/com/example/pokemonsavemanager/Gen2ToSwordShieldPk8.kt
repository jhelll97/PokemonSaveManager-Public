package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class Gen2ToSwordShieldSource(
    val sourceGame: Int,
    val legacyGoldFallback: Boolean,
    val species: Int,
    val nickname: String,
    val originalTrainer: String,
    val level: Int,
    val heldItem: Int,
    val moves: IntArray,
    val currentPp: IntArray,
    val ppUps: IntArray,
    val experience: Long,
    val trainerId: Int,
    val statExperience: IntArray,
    val happiness: Int,
    val pokerus: Int,
    val caughtData: Int,
    val dvs: IntArray,
    val shiny: Boolean
)

data class Gen2ToSwordShieldReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val effects: List<String>
)

data class Gen2ToPk7Bridge(
    val source: Gen2ToSwordShieldSource,
    val pk7: ByteArray,
    val ivs: IntArray,
    val evs: IntArray
)

data class Gen2ToLegendsArceusBridge(
    val source: Gen2ToSwordShieldSource,
    val pk7: ByteArray,
    val ivs: IntArray,
    val evs: IntArray,
    val normalizedPp: IntArray
)

/**
 * Bounded adapter for one preserved 62-byte PSE2 record.
 *
 * It creates a deterministic, checksum-valid PK7 bridge in memory and passes that
 * bridge through the reviewed PK7 -> PK8 adapter. The PSE2 file remains the source.
 */
object Gen2ToSwordShieldPk8 {
    private const val PSE2_SIZE = 62
    private const val HEADER = 8
    private const val PK7_SIZE = 0xE8
    private val mailItems = setOf(0x9E, 0xB5, 0xB6, 0xB7, 0xB8, 0xB9, 0xBA, 0xBB, 0xBC, 0xBD)

    fun inspectSource(record: ByteArray): Gen2ToSwordShieldSource {
        require(record.size == PSE2_SIZE) { "A complete 62-byte PSE2 Bank record is required." }
        require(
            record[0] == 'P'.code.toByte() && record[1] == 'S'.code.toByte() &&
                record[2] == 'E'.code.toByte() && record[3] == '2'.code.toByte() &&
                byte(record, 4) == 1 && byte(record, 5) in 1..2 &&
                byte(record, 6) in 0..1
        ) { "The Gen 2 Bank header is invalid." }
        val storedGame = byte(record, 7)
        require(storedGame in setOf(0, 39, 40, 41)) {
            "The Gen 2 source-title marker is invalid."
        }
        require(storedGame !in 39..40 || byte(record, 5) == 1) {
            "The Gold/Silver title marker conflicts with the PSE2 profile."
        }
        require(storedGame != 41 || byte(record, 5) == 2) {
            "The Crystal title marker conflicts with the PSE2 profile."
        }
        val sourceGame = if (storedGame in 39..41) storedGame
            else if (byte(record, 5) == 2) 41 else 39
        val species = byte(record, HEADER)
        require(species in 1..251) { "The Gen 2 species is outside 1-251." }
        val heldItem = byte(record, HEADER + 1)
        require(heldItem !in mailItems) {
            "Held Mail and its message state cannot be represented safely in Sword/Shield."
        }
        val moves = IntArray(4) { byte(record, HEADER + 2 + it) }
        require(moves.all { it in 0..251 }) { "A move is outside the Gen 2 catalogue." }
        val currentPp = IntArray(4) { byte(record, HEADER + 23 + it) and 0x3F }
        val ppUps = IntArray(4) { byte(record, HEADER + 23 + it) ushr 6 }
        require((0..3).all {
            moves[it] != 0 || (currentPp[it] == 0 && ppUps[it] == 0)
        }) { "An empty Gen 2 move slot contains PP or PP-Up data." }
        val level = byte(record, HEADER + 31)
        require(level in 1..100) { "The Gen 2 level is outside 1-100." }
        val nickname = gen2Text(record, HEADER + 43, 11, "nickname")
        val originalTrainer = gen2Text(record, HEADER + 32, 11, "Original Trainer name")
        require(originalTrainer.isNotBlank()) {
            "A blank Gen 2 Original Trainer name is not destination-valid."
        }
        val attackDefense = byte(record, HEADER + 21)
        val speedSpecial = byte(record, HEADER + 22)
        val attack = attackDefense ushr 4
        val defense = attackDefense and 15
        val speed = speedSpecial ushr 4
        val special = speedSpecial and 15
        val hp = ((attack and 1) shl 3) or ((defense and 1) shl 2) or
            ((speed and 1) shl 1) or (special and 1)
        val dvs = intArrayOf(hp, attack, defense, speed, special)
        val shiny = defense == 10 && speed == 10 && special == 10 &&
            attack in setOf(2, 3, 6, 7, 10, 11, 14, 15)
        return Gen2ToSwordShieldSource(
            sourceGame = sourceGame,
            legacyGoldFallback = storedGame == 0 && byte(record, 5) == 1,
            species = species,
            nickname = nickname,
            originalTrainer = originalTrainer,
            level = level,
            heldItem = heldItem,
            moves = moves,
            currentPp = currentPp,
            ppUps = ppUps,
            experience = u24be(record, HEADER + 8),
            trainerId = u16be(record, HEADER + 6),
            statExperience = IntArray(5) { u16be(record, HEADER + 11 + it * 2) },
            happiness = byte(record, HEADER + 27),
            pokerus = byte(record, HEADER + 28),
            caughtData = u16be(record, HEADER + 29),
            dvs = dvs,
            shiny = shiny
        )
    }

    fun review(
        record: ByteArray,
        destinationTrainer: SwordShieldTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): Gen2ToSwordShieldReview {
        val source = inspectSource(record)
        val pk7 = buildPk7Bridge(record, source, destinationTrainer, metadata)
        val converted = Gen7ToSwordShieldPk8.review(pk7)
        val downstream = converted.effects.filterNot {
            it.startsWith("Explicit Legalise:") || it.startsWith("The PK7 Bank entry")
        }
        val game = when (source.sourceGame) {
            39 -> "Gold"
            40 -> "Silver"
            41 -> "Crystal"
            else -> "Generation 2"
        }
        val evs = mappedEvs(source.statExperience)
        val ivs = deterministicIvs(record, source, destinationTrainer, metadata)
        val effects = buildList {
            add("Explicit Legalise: direct PSE Gen 2 PSE2 -> deterministic in-memory PK7 bridge -> Sword/Shield PK8 conversion; not an official transfer, HOME move, trade, or verified provenance.")
            add("The exact selected 62-byte PSE2 record remains the bound Bank source. Species #" +
                source.species + ", TID " + source.trainerId +
                ", OT text, nickname text, exact EXP " + source.experience +
                ", level " + source.level + ", move IDs, current PP and PP-Up counts are retained.")
            add("Generation 2 happiness " + source.happiness +
                " and Pokérus byte 0x" + source.pokerus.toString(16).uppercase().padStart(2, '0') +
                " are retained in the bridge and native PK8.")
            if (source.heldItem == 0) {
                add("The source holds no item; the destination held-item field remains empty.")
            } else {
                add("Held item #" + source.heldItem +
                    " is explicitly removed because Gen 2 and Sword/Shield item IDs are not interchangeable. Mail records remain blocked.")
            }
            add("Gen 2 Stat Exp " + source.statExperience.joinToString("/") +
                " maps to destination EVs " + evs.joinToString("/") +
                " by division by 257, splitting the single Special budget across SpA/SpD and reducing the largest values only when needed to cap the total at 510.")
            add("Gen 2 DVs " + source.dvs.joinToString("/") +
                " seed deterministic destination IVs " + ivs.joinToString("/") +
                ", including " + (if (source.species == 251) 5 else 3) +
                " required flawless transfer values. The source shiny state (" + source.shiny +
                ") and DV-derived gender are retained through the generated PID and gender field.")
            add("Nature is source EXP modulo 25. SID becomes 0. Encryption constant, PID and IVs are deterministic Legalise values bound to the exact PSE2 bytes, destination trainer and reviewed species metadata; PID does not certify origin.")
            add("Ability " + metadata.abilityId + " in slot " + metadata.abilityNumber +
                " and gender ratio " + metadata.genderRate +
                " are the documented metadata accepted for this review. Source happiness is used instead of the species base-friendship value.")
            add("Origin is retained as " + game + " (#" + source.sourceGame +
                "), with a Poké Ball, PSE Johto met marker 30017, source level and unset encounter date. " +
                if (source.legacyGoldFallback)
                    "This legacy PSE2 record identifies Gold/Silver but cannot distinguish the pair, so Gold is the disclosed fallback."
                else "The source-title marker is exact.")
            if (source.caughtData == 0) {
                add("The source has no packed caught-data value.")
            } else {
                add("Packed Gen 2 caught data 0x" +
                    source.caughtData.toString(16).uppercase().padStart(4, '0') +
                    " is explicitly omitted because it has no exact PK8 field mapping.")
            }
            add("The validated destination trainer becomes current handling trainer; OT gender defaults to male because the PSE2 record does not encode it. Geographic, ribbon, memory, relearn, marking and Hyper Training fields remain empty.")
            addAll(downstream)
            add("The PSE2 Bank entry and Sword/Shield source remain unchanged. One reviewed empty native PK8 slot is written only to a new copy with caught registration, retained recovery, persisted reopen verification and two-sided rollback.")
        }
        return Gen2ToSwordShieldReview(
            converted.nativeRecord.copyOf(), source.species, source.nickname, effects
        )
    }


    internal fun buildBdspBridge(
        record: ByteArray,
        trainer: BdspTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): Gen2ToPk7Bridge {
        val source = inspectSource(record)
        require(trainer.languageId == 2) {
            "This bounded route currently requires an English BDSP destination trainer."
        }
        require(metadata.baseFriendship in 0..255) { "The reviewed base friendship is invalid." }
        require(metadata.genderRate in -1..8) { "The reviewed gender ratio is invalid." }
        require(metadata.abilityId in 1..255 && metadata.abilityNumber in setOf(1, 2, 4)) {
            "The reviewed ability mapping cannot be represented in PK7."
        }
        val profile = BdspPersonalCatalogue.profiles(source.species, 0)
            .distinctBy {
                listOf(
                    it.hp, it.attack, it.defense, it.speed,
                    it.specialAttack, it.specialDefense, it.growth
                )
            }
            .singleOrNull()
            ?: error("This species has no single verified default BDSP profile.")
        require(level(source.experience, profile.growth) == source.level) {
            "The Gen 2 level byte does not agree with exact EXP under the BDSP species growth group."
        }
        require(source.experience <= minimumExperience(100, profile.growth)) {
            "The source EXP exceeds the reviewed BDSP level-100 value."
        }
        source.moves.forEachIndexed { index, move ->
            if (move != 0) {
                require(BdspMoveCatalog.choice(move) != null) {
                    "Move #$move has no native BDSP mapping."
                }
                val maximum = BdspMoveCatalog.maximumPp(move, source.ppUps[index])
                require(source.currentPp[index] in 0..maximum) {
                    "Move #$move current PP cannot be retained within its BDSP limit."
                }
            }
        }
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
        val pid = forceShiny(u32(seed, 4), source.trainerId, source.shiny)
        val ivs = deterministicIvs(seed, source)
        val evs = mappedEvs(source.statExperience)
        val nature = (source.experience % 25L).toInt()
        val gender = transferGender(source.dvs[1], metadata.genderRate)
        val speciesName = SwordShieldSpeciesNames.name(source.species)
        require(speciesName.isNotBlank()) { "The BDSP species name is unavailable." }
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
        w32(pk, 0x10, source.experience)
        pk[0x14] = metadata.abilityId.toByte()
        pk[0x15] = metadata.abilityNumber.toByte()
        w16(pk, 0x16, 0)
        w32(pk, 0x18, pid)
        pk[0x1C] = nature.toByte()
        pk[0x1D] = (((gender and 3) shl 1) or
            if (source.species == 251) 1 else 0).toByte()
        for (index in evs.indices) pk[0x1E + index] = evs[index].toByte()
        pk[0x2B] = source.pokerus.toByte()
        putText(pk, 0x40, nickname)
        for (index in 0..3) {
            val move = source.moves[index]
            w16(pk, 0x5A + index * 2, move)
            if (move != 0) {
                pk[0x62 + index] = source.currentPp[index].toByte()
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
        pk[0xA2] = source.happiness.toByte()
        putText(pk, 0xB0, source.originalTrainer)
        pk[0xCA] = source.happiness.toByte()
        w16(pk, 0xD8, 0)
        w16(pk, 0xDA, 30017)
        pk[0xDC] = 4
        pk[0xDD] = source.level.toByte()
        pk[0xDE] = 0
        pk[0xDF] = source.sourceGame.toByte()
        pk[0xE3] = 2
        var checksum = 0
        for (offset in 8 until PK7_SIZE step 2) checksum += u16(pk, offset)
        w16(pk, 6, checksum and 0xFFFF)
        return Gen2ToPk7Bridge(source, pk, ivs, evs)
    }


    internal fun buildLegendsArceusBridge(
        record: ByteArray,
        trainer: LegendsArceusTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): Gen2ToLegendsArceusBridge {
        val source = inspectSource(record)
        require(trainer.languageId == 2) {
            "This bounded route currently requires an English Legends: Arceus destination trainer."
        }
        require(metadata.baseFriendship in 0..255) { "The reviewed base friendship is invalid." }
        require(metadata.genderRate in -1..8) { "The reviewed gender ratio is invalid." }
        require(metadata.abilityId in 1..255 && metadata.abilityNumber in setOf(1, 2, 4)) {
            "The reviewed ability mapping cannot be represented in PK7."
        }
        require(source.species in LegendsArceusPokedexReadOnly.supportedSpecies) {
            "Species #${source.species} is not available in the reviewed Legends: Arceus destination catalogue."
        }
        val profile = LegendsArceusPersonalCatalogue.profiles(source.species, 0)
            .distinctBy {
                listOf(
                    it.hp, it.attack, it.defense, it.speed,
                    it.specialAttack, it.specialDefense, it.growth
                )
            }
            .singleOrNull()
            ?: error("This species has no single verified default Legends: Arceus profile.")
        require(level(source.experience, profile.growth) == source.level) {
            "The Gen 2 level byte does not agree with exact EXP under the Legends: Arceus species growth group."
        }
        require(source.experience <= minimumExperience(100, profile.growth)) {
            "The source EXP exceeds the reviewed Legends: Arceus level-100 value."
        }
        val normalizedPp = IntArray(4)
        source.moves.forEachIndexed { index, move ->
            if (move != 0) {
                require(LegendsArceusMoveCatalog.choice(move) != null) {
                    "Move #$move has no native Legends: Arceus mapping."
                }
                normalizedPp[index] = LegendsArceusMoveCatalog.maximumPp(move)
            }
        }
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
        val pid = forceShiny(u32(seed, 4), source.trainerId, source.shiny)
        val ivs = deterministicIvs(seed, source)
        val evs = mappedEvs(source.statExperience)
        val nature = (source.experience % 25L).toInt()
        val gender = transferGender(source.dvs[1], metadata.genderRate)
        val speciesName = LegendsArceusSpeciesNames.name(source.species)
        require(speciesName.isNotBlank()) {
            "The Legends: Arceus species name is unavailable."
        }
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
        w32(pk, 0x10, source.experience)
        pk[0x14] = metadata.abilityId.toByte()
        pk[0x15] = metadata.abilityNumber.toByte()
        w16(pk, 0x16, 0)
        w32(pk, 0x18, pid)
        pk[0x1C] = nature.toByte()
        pk[0x1D] = (((gender and 3) shl 1) or
            if (source.species == 251) 1 else 0).toByte()
        for (index in evs.indices) pk[0x1E + index] = evs[index].toByte()
        pk[0x2B] = source.pokerus.toByte()
        putText(pk, 0x40, nickname)
        for (index in 0..3) {
            val move = source.moves[index]
            w16(pk, 0x5A + index * 2, move)
            if (move != 0) {
                pk[0x62 + index] = normalizedPp[index].toByte()
                pk[0x66 + index] = 0
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
        pk[0xA2] = source.happiness.toByte()
        putText(pk, 0xB0, source.originalTrainer)
        pk[0xCA] = source.happiness.toByte()
        w16(pk, 0xD8, 0)
        w16(pk, 0xDA, 30017)
        pk[0xDC] = 4
        pk[0xDD] = source.level.toByte()
        pk[0xDE] = 0
        pk[0xDF] = source.sourceGame.toByte()
        pk[0xE3] = 2
        var checksum = 0
        for (offset in 8 until PK7_SIZE step 2) checksum += u16(pk, offset)
        w16(pk, 6, checksum and 0xFFFF)
        return Gen2ToLegendsArceusBridge(
            source, pk, ivs, evs, normalizedPp
        )
    }

    private fun buildPk7Bridge(
        record: ByteArray,
        source: Gen2ToSwordShieldSource,
        trainer: SwordShieldTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): ByteArray {
        require(trainer.languageId == 2) {
            "This bounded route currently requires an English Sword/Shield destination trainer."
        }
        require(metadata.baseFriendship in 0..255) { "The reviewed base friendship is invalid." }
        require(metadata.genderRate in -1..8) { "The reviewed gender ratio is invalid." }
        require(metadata.abilityId in 1..255 && metadata.abilityNumber in setOf(1, 2, 4)) {
            "The reviewed ability mapping cannot be represented in PK7."
        }
        val profile = SwordShieldPersonalCatalogue.profiles(source.species)
            .filter { it.isDefault }
            .distinctBy {
                listOf(
                    it.hp, it.attack, it.defense, it.speed,
                    it.specialAttack, it.specialDefense, it.growth
                )
            }
            .singleOrNull()
            ?: error("This species has no single verified default Sword/Shield profile.")
        require(level(source.experience, profile.growth) == source.level) {
            "The Gen 2 level byte does not agree with exact EXP under the destination species growth group."
        }
        require(source.experience <= minimumExperience(100, profile.growth)) {
            "The source EXP exceeds the reviewed level-100 value."
        }
        source.moves.forEachIndexed { index, move ->
            if (move != 0) {
                require(SwordShieldMoveCatalog.choice(move) != null) {
                    "Move #$move has no native Sword/Shield mapping."
                }
                val maximum = SwordShieldMoveCatalog.maximumPp(move, source.ppUps[index])
                require(source.currentPp[index] in 0..maximum) {
                    "Move #$move current PP cannot be retained within its Sword/Shield limit."
                }
            }
        }
        val seed = seed(record, trainer, metadata)
        val ec = u32(seed, 0)
        val pid = forceShiny(u32(seed, 4), source.trainerId, source.shiny)
        val ivs = deterministicIvs(record, source, trainer, metadata)
        val nature = (source.experience % 25L).toInt()
        val gender = transferGender(source.dvs[1], metadata.genderRate)
        val speciesName = SwordShieldSpeciesNames.name(source.species)
        require(speciesName.isNotBlank()) { "The Sword/Shield species name is unavailable." }
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
        w32(pk, 0x10, source.experience)
        pk[0x14] = metadata.abilityId.toByte()
        pk[0x15] = metadata.abilityNumber.toByte()
        w16(pk, 0x16, 0)
        w32(pk, 0x18, pid)
        pk[0x1C] = nature.toByte()
        pk[0x1D] = (((gender and 3) shl 1) or
            if (source.species == 251) 1 else 0).toByte()
        val evs = mappedEvs(source.statExperience)
        for (index in evs.indices) pk[0x1E + index] = evs[index].toByte()
        pk[0x2B] = source.pokerus.toByte()
        putText(pk, 0x40, nickname)
        for (index in 0..3) {
            val move = source.moves[index]
            w16(pk, 0x5A + index * 2, move)
            if (move != 0) {
                pk[0x62 + index] = source.currentPp[index].toByte()
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
        pk[0xA2] = source.happiness.toByte()
        putText(pk, 0xB0, source.originalTrainer)
        pk[0xCA] = source.happiness.toByte()
        w16(pk, 0xD8, 0)
        w16(pk, 0xDA, 30017)
        pk[0xDC] = 4
        pk[0xDD] = source.level.toByte()
        pk[0xDE] = 0
        pk[0xDF] = source.sourceGame.toByte()
        pk[0xE3] = 2
        var checksum = 0
        for (offset in 8 until PK7_SIZE step 2) checksum += u16(pk, offset)
        w16(pk, 6, checksum and 0xFFFF)
        return pk
    }

    private fun seed(
        record: ByteArray,
        trainer: SwordShieldTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): ByteArray = MessageDigest.getInstance("SHA-256").apply {
        update(record)
        update(trainer.name.toByteArray(StandardCharsets.UTF_8))
        update(byteArrayOf(trainer.gender.toByte(), trainer.languageId.toByte()))
        update(intBytes(metadata.baseFriendship))
        update(intBytes(metadata.genderRate))
        update(intBytes(metadata.abilityId))
        update(intBytes(metadata.abilityNumber))
    }.digest()

    private fun deterministicIvs(
        record: ByteArray,
        source: Gen2ToSwordShieldSource,
        trainer: SwordShieldTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): IntArray = deterministicIvs(seed(record, trainer, metadata), source)

    private fun deterministicIvs(
        seed: ByteArray,
        source: Gen2ToSwordShieldSource
    ): IntArray {
        fun expanded(dv: Int) = ((dv.coerceIn(0, 15) shl 1) or 1).coerceIn(0, 31)
        val values = intArrayOf(
            expanded(source.dvs[0]), expanded(source.dvs[1]), expanded(source.dvs[2]),
            expanded(source.dvs[3]), expanded(source.dvs[4]), expanded(source.dvs[4])
        )
        val order = (0..5).sortedWith(compareBy<Int>({ byte(seed, 8 + it) }, { it }))
        order.take(if (source.species == 251) 5 else 3).forEach { values[it] = 31 }
        return values
    }

    private fun mappedEvs(statExperience: IntArray): IntArray {
        require(statExperience.size == 5) { "Five Gen 2 stat-experience values are required." }
        val special = (statExperience[4] / 257).coerceIn(0, 255)
        val evs = intArrayOf(
            (statExperience[0] / 257).coerceIn(0, 255),
            (statExperience[1] / 257).coerceIn(0, 255),
            (statExperience[2] / 257).coerceIn(0, 255),
            (statExperience[3] / 257).coerceIn(0, 255),
            (special + 1) / 2,
            special / 2
        )
        var total = evs.sum()
        while (total > 510) {
            val index = evs.indices.maxByOrNull { evs[it] } ?: 0
            if (evs[index] == 0) break
            evs[index]--
            total--
        }
        return evs
    }

    private fun transferGender(attackDv: Int, genderRate: Int): Int = when {
        genderRate < 0 -> 2
        genderRate == 0 -> 0
        genderRate >= 8 -> 1
        attackDv < (genderRate * 2).coerceIn(0, 16) -> 1
        else -> 0
    }

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

    private fun level(experience: Long, growth: Int): Int {
        var result = 1
        for (candidate in 2..100) {
            if (experience < minimumExperience(candidate, growth)) break
            result = candidate
        }
        return result
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

    private fun gen2Text(bytes: ByteArray, offset: Int, length: Int, label: String): String {
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
                else -> error("The Gen 2 " + label + " contains unsupported byte 0x" +
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
}
