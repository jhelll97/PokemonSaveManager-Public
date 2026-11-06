package com.example.pokemonsavemanager

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

data class LegendsArceusPersonalData(
    val isDefault: Boolean,
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val speed: Int,
    val specialAttack: Int,
    val specialDefense: Int,
    val growth: Int
)

/** PLA-native profiles layered over the pinned Generation 8 catalogue. */
object LegendsArceusPersonalCatalogue {
    private val added = mapOf(
        899 to listOf(LegendsArceusPersonalData(true, 103, 105, 72, 65, 105, 75, 0)),
        900 to listOf(LegendsArceusPersonalData(true, 70, 135, 95, 85, 45, 70, 0)),
        901 to listOf(LegendsArceusPersonalData(true, 130, 140, 105, 50, 45, 80, 0)),
        902 to listOf(
            LegendsArceusPersonalData(true, 120, 112, 65, 78, 80, 75, 0),
            LegendsArceusPersonalData(false, 120, 92, 65, 78, 100, 75, 0)
        ),
        903 to listOf(LegendsArceusPersonalData(true, 80, 130, 60, 120, 40, 80, 0)),
        904 to listOf(LegendsArceusPersonalData(true, 85, 115, 95, 85, 65, 65, 0)),
        905 to listOf(
            LegendsArceusPersonalData(true, 74, 115, 70, 106, 135, 80, 5),
            LegendsArceusPersonalData(false, 74, 115, 110, 46, 135, 100, 5)
        )
    )

    fun profiles(species: Int, form: Int): List<LegendsArceusPersonalData> {
        if (species !in LegendsArceusPokedexReadOnly.supportedSpecies || form !in 0..255) return emptyList()
        val source = added[species] ?: SwordShieldPersonalCatalogue.profiles(species).map {
            LegendsArceusPersonalData(
                it.isDefault, it.hp, it.attack, it.defense, it.speed,
                it.specialAttack, it.specialDefense, it.growth
            )
        }
        val matchingDefault = source.filter { it.isDefault == (form == 0) }
        return (if (matchingDefault.isNotEmpty()) matchingDefault else source).distinct()
    }
}

data class LegendsArceusLevelValues(
    val level: Int,
    val experience: Long,
    val currentHp: Int,
    val maximumHp: Int?,
    val cachedStats: List<Int>?
)

data class LegendsArceusAttackDvValues(
    val attackIv: Int,
    val attack: Int?
)

data class LegendsArceusDefenseDvValues(
    val defenseIv: Int,
    val defense: Int?
)

data class LegendsArceusSpeedDvValues(
    val speedIv: Int,
    val speed: Int?
)

data class LegendsArceusSpecialDvValues(
    val specialIv: Int,
    val specialAttack: Int?,
    val specialDefense: Int?
)

/** Rebuilds one PA8's native level-dependent values in a reviewed new Legends: Arceus save copy. */
object LegendsArceusPokemonLevelEditing {
    private const val STORED_END = 0x168
    private const val EXPERIENCE = 0x10
    private const val IV32 = 0x94
    private const val ATTACK_IV_SHIFT = 5
    private const val ATTACK_IV_MASK = 31L shl ATTACK_IV_SHIFT
    private const val DEFENSE_IV_SHIFT = 10
    private const val DEFENSE_IV_MASK = 31L shl DEFENSE_IV_SHIFT
    private const val SPEED_IV_SHIFT = 15
    private const val SPEED_IV_MASK = 31L shl SPEED_IV_SHIFT
    private const val SPECIAL_ATTACK_IV_SHIFT = 20
    private const val SPECIAL_ATTACK_IV_MASK = 31L shl SPECIAL_ATTACK_IV_SHIFT
    private const val SPECIAL_DEFENSE_IV_SHIFT = 25
    private const val SPECIAL_DEFENSE_IV_MASK = 31L shl SPECIAL_DEFENSE_IV_SHIFT
    private const val SPECIAL_IV_MASK = SPECIAL_ATTACK_IV_MASK or SPECIAL_DEFENSE_IV_MASK
    private const val CURRENT_HP = 0x92
    private const val LEVEL = 0x168
    private const val MAXIMUM_HP = 0x16A
    private const val PARTY_STATS = 0x16C
    private const val PARTY_END = 0x176
    private const val BLOCK_SIZE = 88
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        LegendsArceusPokemonNicknameEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = LegendsArceusPokemonNicknameEditing.sha256(bytes)

    fun currentLevel(pokemon: LegendsArceusPokemonRead): Int {
        val growth = resolveGrowth(pokemon)
        return levelForExperience(pokemon.experience, growth)
    }

    fun maximumExperience(pokemon: LegendsArceusPokemonRead): Long =
        minimumExperience(100, resolveGrowth(pokemon))

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        requestedLevel: Int
    ): LegendsArceusLevelCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(LegendsArceusSaveValidation.inspect(original) is LegendsArceusValidation.Verified) {
            "A validated native Legends: Arceus save is required."
        }
        require(requestedLevel in 1..100) { "Level must be 1–100." }
        val blocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, pasture, position)
        val before = (LegendsArceusPokemonReadOnly.read(target) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA8 is not checksum-valid.")
        require(!before.isEgg) { "Egg Level editing is not supported for Legends: Arceus." }
        val growth = resolveGrowth(before)
        val beforeLevel = levelForExperience(before.experience, growth)
        require(before.cachedLevel == null || before.cachedLevel == beforeLevel) {
            "The saved party Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(requestedLevel != beforeLevel) { "Enter a different Level before review." }
        val after = if (area == LegendsArceusPokemonArea.PARTY) {
            require(before.statNature in 0..24) {
                "The PA8 stat nature is invalid, so derived stats cannot be rebuilt safely."
            }
            val personal = resolvePartyPersonal(before, beforeLevel)
            require(matchesCachedStats(before, calculateParty(before, personal, beforeLevel))) {
                "The saved Level-dependent stats do not match this PA8's verified species/form profile."
            }
            calculateParty(before, personal, requestedLevel).copy(
                experience = minimumExperience(requestedLevel, growth)
            )
        } else LegendsArceusLevelValues(
            requestedLevel, minimumExperience(requestedLevel, growth), before.currentHp, null, null
        )
        val afterRecord = editRecord(target, after)
        val written = if (area == LegendsArceusPokemonArea.PASTURE) {
            LegendsArceusSaveValidation.writePastureRecord(original, pasture, position, afterRecord)
        } else LegendsArceusSaveValidation.writePartyRecord(original, position, afterRecord)
        verify(original, written.bytes, area, pasture, position, written.absoluteOffset, after)
        return LegendsArceusLevelCopyPlan(
            original, written.bytes, area, pasture, position, written.absoluteOffset,
            before, beforeLevel, after
        )
    }

    internal fun stageAttackDv(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        requestedAttackIv: Int
    ): LegendsArceusAttackDvCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(LegendsArceusSaveValidation.inspect(original) is LegendsArceusValidation.Verified) {
            "A validated native Legends: Arceus save is required."
        }
        require(requestedAttackIv in 0..31) { "Attack IV must be between 0 and 31." }
        val blocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, pasture, position)
        val before = (LegendsArceusPokemonReadOnly.read(target) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA8 is not checksum-valid.")
        require(!before.isEgg) { "Egg Attack DV / IV editing is not supported for Legends: Arceus." }
        require(requestedAttackIv != before.ivs[1]) { "Enter a different Attack IV before review." }
        val attack = if (area == LegendsArceusPokemonArea.PARTY) {
            val growth = resolveGrowth(before)
            val beforeLevel = levelForExperience(before.experience, growth)
            require(before.cachedLevel == beforeLevel && before.statNature in 0..24) {
                "The party PA8 Level, EXP, or stat nature is invalid, so Attack cannot be rebuilt safely."
            }
            val personal = resolvePartyPersonal(before, beforeLevel)
            require(matchesCachedValues(before, calculateParty(before, personal, beforeLevel))) {
                "The saved battle values do not match this PA8's verified species/form profile."
            }
            val requestedIvs = before.ivs.toMutableList().also { it[1] = requestedAttackIv }
            requireNotNull(calculateParty(
                before.copy(ivs = requestedIvs), personal, beforeLevel
            ).cachedStats)[0]
        } else null
        val after = LegendsArceusAttackDvValues(requestedAttackIv, attack)
        val afterRecord = editAttackDvRecord(target, after)
        val written = if (area == LegendsArceusPokemonArea.PASTURE) {
            LegendsArceusSaveValidation.writePastureRecord(original, pasture, position, afterRecord)
        } else LegendsArceusSaveValidation.writePartyRecord(original, position, afterRecord)
        verifyAttackDv(original, written.bytes, area, pasture, position, written.absoluteOffset, after)
        return LegendsArceusAttackDvCopyPlan(
            original, written.bytes, area, pasture, position, written.absoluteOffset, before, after
        )
    }

    internal fun stageDefenseDv(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        requestedDefenseIv: Int
    ): LegendsArceusDefenseDvCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(LegendsArceusSaveValidation.inspect(original) is LegendsArceusValidation.Verified) {
            "A validated native Legends: Arceus save is required."
        }
        require(requestedDefenseIv in 0..31) { "Defense IV must be between 0 and 31." }
        val blocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, pasture, position)
        val before = (LegendsArceusPokemonReadOnly.read(target) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA8 is not checksum-valid.")
        require(!before.isEgg) { "Egg Defense DV / IV editing is not supported for Legends: Arceus." }
        require(requestedDefenseIv != before.ivs[2]) { "Enter a different Defense IV before review." }
        val defense = if (area == LegendsArceusPokemonArea.PARTY) {
            val growth = resolveGrowth(before)
            val beforeLevel = levelForExperience(before.experience, growth)
            require(before.cachedLevel == beforeLevel && before.statNature in 0..24) {
                "The party PA8 Level, EXP, or stat nature is invalid, so Defense cannot be rebuilt safely."
            }
            val personal = resolvePartyPersonal(before, beforeLevel)
            require(matchesCachedValues(before, calculateParty(before, personal, beforeLevel))) {
                "The saved battle values do not match this PA8's verified species/form profile."
            }
            val requestedIvs = before.ivs.toMutableList().also { it[2] = requestedDefenseIv }
            requireNotNull(calculateParty(
                before.copy(ivs = requestedIvs), personal, beforeLevel
            ).cachedStats)[1]
        } else null
        val after = LegendsArceusDefenseDvValues(requestedDefenseIv, defense)
        val afterRecord = editDefenseDvRecord(target, after)
        val written = if (area == LegendsArceusPokemonArea.PASTURE) {
            LegendsArceusSaveValidation.writePastureRecord(original, pasture, position, afterRecord)
        } else LegendsArceusSaveValidation.writePartyRecord(original, position, afterRecord)
        verifyDefenseDv(original, written.bytes, area, pasture, position, written.absoluteOffset, after)
        return LegendsArceusDefenseDvCopyPlan(
            original, written.bytes, area, pasture, position, written.absoluteOffset, before, after
        )
    }

    internal fun stageSpeedDv(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        requestedSpeedIv: Int
    ): LegendsArceusSpeedDvCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(LegendsArceusSaveValidation.inspect(original) is LegendsArceusValidation.Verified) {
            "A validated native Legends: Arceus save is required."
        }
        require(requestedSpeedIv in 0..31) { "Speed IV must be between 0 and 31." }
        val blocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, pasture, position)
        val before = (LegendsArceusPokemonReadOnly.read(target) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA8 is not checksum-valid.")
        require(!before.isEgg) { "Egg Speed DV / IV editing is not supported for Legends: Arceus." }
        require(requestedSpeedIv != before.ivs[3]) { "Enter a different Speed IV before review." }
        val speed = if (area == LegendsArceusPokemonArea.PARTY) {
            val growth = resolveGrowth(before)
            val beforeLevel = levelForExperience(before.experience, growth)
            require(before.cachedLevel == beforeLevel && before.statNature in 0..24) {
                "The party PA8 Level, EXP, or stat nature is invalid, so Speed cannot be rebuilt safely."
            }
            val personal = resolvePartyPersonal(before, beforeLevel)
            require(matchesCachedValues(before, calculateParty(before, personal, beforeLevel))) {
                "The saved battle values do not match this PA8's verified species/form profile."
            }
            val requestedIvs = before.ivs.toMutableList().also { it[3] = requestedSpeedIv }
            requireNotNull(calculateParty(
                before.copy(ivs = requestedIvs), personal, beforeLevel
            ).cachedStats)[2]
        } else null
        val after = LegendsArceusSpeedDvValues(requestedSpeedIv, speed)
        val afterRecord = editSpeedDvRecord(target, after)
        val written = if (area == LegendsArceusPokemonArea.PASTURE) {
            LegendsArceusSaveValidation.writePastureRecord(original, pasture, position, afterRecord)
        } else LegendsArceusSaveValidation.writePartyRecord(original, position, afterRecord)
        verifySpeedDv(original, written.bytes, area, pasture, position, written.absoluteOffset, after)
        return LegendsArceusSpeedDvCopyPlan(
            original, written.bytes, area, pasture, position, written.absoluteOffset, before, after
        )
    }


    internal fun stageSpecialDv(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        requestedSpecialIv: Int
    ): LegendsArceusSpecialDvCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(LegendsArceusSaveValidation.inspect(original) is LegendsArceusValidation.Verified) {
            "A validated native Legends: Arceus save is required."
        }
        require(requestedSpecialIv in 0..31) { "Special IV must be between 0 and 31." }
        val blocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, pasture, position)
        val before = (LegendsArceusPokemonReadOnly.read(target) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA8 is not checksum-valid.")
        require(!before.isEgg) { "Egg Special DV / IV editing is not supported for Legends: Arceus." }
        require(requestedSpecialIv != before.ivs[4] || requestedSpecialIv != before.ivs[5]) {
            "Enter a Special IV that differs from Special Attack or Special Defense before review."
        }
        val specialStats = if (area == LegendsArceusPokemonArea.PARTY) {
            val growth = resolveGrowth(before)
            val beforeLevel = levelForExperience(before.experience, growth)
            require(before.cachedLevel == beforeLevel && before.statNature in 0..24) {
                "The party PA8 Level, EXP, or stat nature is invalid, so Special stats cannot be rebuilt safely."
            }
            val personal = resolvePartyPersonal(before, beforeLevel)
            require(matchesCachedValues(before, calculateParty(before, personal, beforeLevel))) {
                "The saved battle values do not match this PA8's verified species/form profile."
            }
            val requestedIvs = before.ivs.toMutableList().also {
                it[4] = requestedSpecialIv
                it[5] = requestedSpecialIv
            }
            val stats = requireNotNull(calculateParty(
                before.copy(ivs = requestedIvs), personal, beforeLevel
            ).cachedStats)
            stats[3] to stats[4]
        } else null
        val after = LegendsArceusSpecialDvValues(
            requestedSpecialIv, specialStats?.first, specialStats?.second
        )
        val afterRecord = editSpecialDvRecord(target, after)
        val written = if (area == LegendsArceusPokemonArea.PASTURE) {
            LegendsArceusSaveValidation.writePastureRecord(original, pasture, position, afterRecord)
        } else LegendsArceusSaveValidation.writePartyRecord(original, position, afterRecord)
        verifySpecialDv(original, written.bytes, area, pasture, position, written.absoluteOffset, after)
        return LegendsArceusSpecialDvCopyPlan(
            original, written.bytes, area, pasture, position, written.absoluteOffset, before, after
        )
    }

    internal fun stageExperience(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        requestedExperience: Long
    ): LegendsArceusExperienceCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(LegendsArceusSaveValidation.inspect(original) is LegendsArceusValidation.Verified) {
            "A validated native Legends: Arceus save is required."
        }
        val blocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, pasture, position)
        val before = (LegendsArceusPokemonReadOnly.read(target) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA8 is not checksum-valid.")
        require(!before.isEgg) { "Egg Experience editing is not supported for Legends: Arceus." }
        require(requestedExperience != before.experience) { "Enter different Experience before review." }
        val growth = resolveGrowth(before)
        val maximumExperience = minimumExperience(100, growth)
        require(requestedExperience in 0L..maximumExperience) {
            "Experience must be between 0 and $maximumExperience for this Pokémon's native growth group."
        }
        val beforeLevel = levelForExperience(before.experience, growth)
        require(before.experience in 0L..maximumExperience &&
            (before.cachedLevel == null || before.cachedLevel == beforeLevel)) {
            "The saved party Level and EXP do not agree with this Pokémon's verified growth group."
        }
        val requestedLevel = levelForExperience(requestedExperience, growth)
        val after = if (area == LegendsArceusPokemonArea.PARTY) {
            require(before.statNature in 0..24) {
                "The PA8 stat nature is invalid, so derived stats cannot be rebuilt safely."
            }
            val personal = resolvePartyPersonal(before, beforeLevel)
            require(matchesCachedValues(before, calculateParty(before, personal, beforeLevel))) {
                "The saved Level-dependent values do not match this PA8's verified species/form profile."
            }
            calculateParty(before, personal, requestedLevel).copy(experience = requestedExperience)
        } else LegendsArceusLevelValues(
            requestedLevel, requestedExperience, before.currentHp, null, null
        )
        val afterRecord = editRecord(target, after)
        val written = if (area == LegendsArceusPokemonArea.PASTURE) {
            LegendsArceusSaveValidation.writePastureRecord(original, pasture, position, afterRecord)
        } else LegendsArceusSaveValidation.writePartyRecord(original, position, afterRecord)
        verify(original, written.bytes, area, pasture, position, written.absoluteOffset, after)
        return LegendsArceusExperienceCopyPlan(
            original, written.bytes, area, pasture, position, written.absoluteOffset,
            before, beforeLevel, after, maximumExperience
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        absoluteOffset: Int,
        expected: LegendsArceusLevelValues
    ) {
        val beforeBlocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsArceusSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The Level edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, pasture, position)
        val afterRecord = targetRecord(afterBlocks, area, pasture, position)
        val before = (LegendsArceusPokemonReadOnly.read(beforeRecord) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PA8 no longer reopens as a Pokémon.")
        val after = (LegendsArceusPokemonReadOnly.read(afterRecord) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PA8 did not reopen as a Pokémon.")
        require(after.experience == expected.experience && after.currentHp == expected.currentHp &&
            after.cachedLevel == expected.maximumHp?.let { expected.level } &&
            after.maximumHp == expected.maximumHp && after.cachedStats == expected.cachedStats &&
            after.copy(
                cachedLevel = before.cachedLevel,
                experience = before.experience,
                currentHp = before.currentHp,
                maximumHp = before.maximumHp,
                cachedStats = before.cachedStats
            ) == before) {
            "The persisted PA8 changed outside its reviewed Level-dependent values."
        }

        val recordSize = recordSize(area)
        val relativeOffset = if (area == LegendsArceusPokemonArea.PASTURE)
            ((pasture - 1) * 30 + position - 1) * recordSize else (position - 1) * recordSize
        val beforeTargetBlock = if (area == LegendsArceusPokemonArea.PASTURE) beforeBlocks.pastures else beforeBlocks.party
        val afterTargetBlock = if (area == LegendsArceusPokemonArea.PASTURE) afterBlocks.pastures else afterBlocks.party
        val beforeOtherBlock = if (area == LegendsArceusPokemonArea.PASTURE) beforeBlocks.party else beforeBlocks.pastures
        val afterOtherBlock = if (area == LegendsArceusPokemonArea.PASTURE) afterBlocks.party else afterBlocks.pastures
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) { "The unselected native Pokémon storage object changed." }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + recordSize || beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in EXPERIENCE..EXPERIENCE + 3 ||
                offset in CURRENT_HP..CURRENT_HP + 1 ||
                (area == LegendsArceusPokemonArea.PARTY &&
                    (offset == LEVEL || offset in MAXIMUM_HP until PARTY_END)) ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PA8 edit exceeded EXP, Level, current HP, cached battle stats, and checksum." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + recordSize ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    internal fun verifyAttackDv(
        original: ByteArray,
        edited: ByteArray,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        absoluteOffset: Int,
        expected: LegendsArceusAttackDvValues
    ) {
        val beforeBlocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsArceusSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The Attack DV / IV edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, pasture, position)
        val afterRecord = targetRecord(afterBlocks, area, pasture, position)
        val before = (LegendsArceusPokemonReadOnly.read(beforeRecord) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PA8 no longer reopens as a Pokémon.")
        val after = (LegendsArceusPokemonReadOnly.read(afterRecord) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PA8 did not reopen as a Pokémon.")
        val beforeStats = before.cachedStats
        val afterStats = after.cachedStats
        val cachedAttackMatches = if (expected.attack == null) {
            beforeStats == null && afterStats == null
        } else {
            beforeStats != null && afterStats != null && afterStats[0] == expected.attack &&
                (1..4).all { afterStats[it] == beforeStats[it] }
        }
        require(after.ivs[1] == expected.attackIv && cachedAttackMatches &&
            after.ivs.indices.filter { it != 1 }.all { after.ivs[it] == before.ivs[it] } &&
            after.copy(ivs = before.ivs, cachedStats = before.cachedStats) == before) {
            "The persisted PA8 changed outside Attack IV and its optional derived Attack value."
        }

        val recordSize = recordSize(area)
        val relativeOffset = if (area == LegendsArceusPokemonArea.PASTURE)
            ((pasture - 1) * 30 + position - 1) * recordSize else (position - 1) * recordSize
        val beforeTargetBlock = if (area == LegendsArceusPokemonArea.PASTURE) beforeBlocks.pastures else beforeBlocks.party
        val afterTargetBlock = if (area == LegendsArceusPokemonArea.PASTURE) afterBlocks.pastures else afterBlocks.party
        val beforeOtherBlock = if (area == LegendsArceusPokemonArea.PASTURE) beforeBlocks.party else beforeBlocks.pastures
        val afterOtherBlock = if (area == LegendsArceusPokemonArea.PASTURE) afterBlocks.party else afterBlocks.pastures
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) {
            "The unselected native Pokémon storage object changed."
        }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + recordSize ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        val preserveIvMask = 0xFFFFFFFFL xor ATTACK_IV_MASK
        require((u32(beforePlain, IV32) and preserveIvMask) ==
            (u32(afterPlain, IV32) and preserveIvMask)) {
            "A non-Attack IV or PA8 identity bit changed."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in IV32 until IV32 + 4 ||
                (area == LegendsArceusPokemonArea.PARTY && offset in PARTY_STATS until PARTY_STATS + 2) ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PA8 edit exceeded Attack IV, optional derived Attack, and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + recordSize ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    internal fun verifyDefenseDv(
        original: ByteArray,
        edited: ByteArray,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        absoluteOffset: Int,
        expected: LegendsArceusDefenseDvValues
    ) {
        val beforeBlocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsArceusSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The Defense DV / IV edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, pasture, position)
        val afterRecord = targetRecord(afterBlocks, area, pasture, position)
        val before = (LegendsArceusPokemonReadOnly.read(beforeRecord) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PA8 no longer reopens as a Pokémon.")
        val after = (LegendsArceusPokemonReadOnly.read(afterRecord) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PA8 did not reopen as a Pokémon.")
        val beforeStats = before.cachedStats
        val afterStats = after.cachedStats
        val cachedDefenseMatches = if (expected.defense == null) {
            beforeStats == null && afterStats == null
        } else {
            beforeStats != null && afterStats != null && afterStats[1] == expected.defense &&
                afterStats.indices.filter { it != 1 }.all { afterStats[it] == beforeStats[it] }
        }
        require(after.ivs[2] == expected.defenseIv && cachedDefenseMatches &&
            after.ivs.indices.filter { it != 2 }.all { after.ivs[it] == before.ivs[it] } &&
            after.copy(ivs = before.ivs, cachedStats = before.cachedStats) == before) {
            "The persisted PA8 changed outside Defense IV and its optional derived Defense value."
        }

        val recordSize = recordSize(area)
        val relativeOffset = if (area == LegendsArceusPokemonArea.PASTURE)
            ((pasture - 1) * 30 + position - 1) * recordSize else (position - 1) * recordSize
        val beforeTargetBlock = if (area == LegendsArceusPokemonArea.PASTURE) beforeBlocks.pastures else beforeBlocks.party
        val afterTargetBlock = if (area == LegendsArceusPokemonArea.PASTURE) afterBlocks.pastures else afterBlocks.party
        val beforeOtherBlock = if (area == LegendsArceusPokemonArea.PASTURE) beforeBlocks.party else beforeBlocks.pastures
        val afterOtherBlock = if (area == LegendsArceusPokemonArea.PASTURE) afterBlocks.party else afterBlocks.pastures
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) {
            "The unselected native Pokémon storage object changed."
        }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + recordSize ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        val preserveIvMask = 0xFFFFFFFFL xor DEFENSE_IV_MASK
        require((u32(beforePlain, IV32) and preserveIvMask) ==
            (u32(afterPlain, IV32) and preserveIvMask)) {
            "A non-Defense IV or PA8 identity bit changed."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in IV32 until IV32 + 4 ||
                (area == LegendsArceusPokemonArea.PARTY && offset in PARTY_STATS + 2 until PARTY_STATS + 4) ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PA8 edit exceeded Defense IV, optional derived Defense, and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + recordSize ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    internal fun verifySpeedDv(
        original: ByteArray,
        edited: ByteArray,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        absoluteOffset: Int,
        expected: LegendsArceusSpeedDvValues
    ) {
        val beforeBlocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsArceusSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The Speed DV / IV edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, pasture, position)
        val afterRecord = targetRecord(afterBlocks, area, pasture, position)
        val before = (LegendsArceusPokemonReadOnly.read(beforeRecord) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PA8 no longer reopens as a Pokémon.")
        val after = (LegendsArceusPokemonReadOnly.read(afterRecord) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PA8 did not reopen as a Pokémon.")
        val beforeStats = before.cachedStats
        val afterStats = after.cachedStats
        val cachedSpeedMatches = if (expected.speed == null) {
            beforeStats == null && afterStats == null
        } else {
            beforeStats != null && afterStats != null && afterStats[2] == expected.speed &&
                afterStats.indices.filter { it != 2 }.all { afterStats[it] == beforeStats[it] }
        }
        require(after.ivs[3] == expected.speedIv && cachedSpeedMatches &&
            after.ivs.indices.filter { it != 3 }.all { after.ivs[it] == before.ivs[it] } &&
            after.copy(ivs = before.ivs, cachedStats = before.cachedStats) == before) {
            "The persisted PA8 changed outside Speed IV and its optional derived Speed value."
        }

        val recordSize = recordSize(area)
        val relativeOffset = if (area == LegendsArceusPokemonArea.PASTURE)
            ((pasture - 1) * 30 + position - 1) * recordSize else (position - 1) * recordSize
        val beforeTargetBlock = if (area == LegendsArceusPokemonArea.PASTURE) beforeBlocks.pastures else beforeBlocks.party
        val afterTargetBlock = if (area == LegendsArceusPokemonArea.PASTURE) afterBlocks.pastures else afterBlocks.party
        val beforeOtherBlock = if (area == LegendsArceusPokemonArea.PASTURE) beforeBlocks.party else beforeBlocks.pastures
        val afterOtherBlock = if (area == LegendsArceusPokemonArea.PASTURE) afterBlocks.party else afterBlocks.pastures
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) {
            "The unselected native Pokémon storage object changed."
        }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + recordSize ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        val preserveIvMask = 0xFFFFFFFFL xor SPEED_IV_MASK
        require((u32(beforePlain, IV32) and preserveIvMask) ==
            (u32(afterPlain, IV32) and preserveIvMask)) {
            "A non-Speed IV or PA8 identity bit changed."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in IV32 until IV32 + 4 ||
                (area == LegendsArceusPokemonArea.PARTY && offset in PARTY_STATS + 4 until PARTY_STATS + 6) ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PA8 edit exceeded Speed IV, optional derived Speed, and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + recordSize ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }


    internal fun verifySpecialDv(
        original: ByteArray,
        edited: ByteArray,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        absoluteOffset: Int,
        expected: LegendsArceusSpecialDvValues
    ) {
        val beforeBlocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsArceusSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The Special DV / IV edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, pasture, position)
        val afterRecord = targetRecord(afterBlocks, area, pasture, position)
        val before = (LegendsArceusPokemonReadOnly.read(beforeRecord) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PA8 no longer reopens as a Pokémon.")
        val after = (LegendsArceusPokemonReadOnly.read(afterRecord) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PA8 did not reopen as a Pokémon.")
        val beforeStats = before.cachedStats
        val afterStats = after.cachedStats
        val cachedSpecialMatches = if (expected.specialAttack == null && expected.specialDefense == null) {
            beforeStats == null && afterStats == null
        } else {
            beforeStats != null && afterStats != null &&
                afterStats[3] == expected.specialAttack && afterStats[4] == expected.specialDefense &&
                (0..2).all { afterStats[it] == beforeStats[it] }
        }
        require(after.ivs[4] == expected.specialIv && after.ivs[5] == expected.specialIv &&
            cachedSpecialMatches &&
            after.ivs.indices.filter { it != 4 && it != 5 }.all { after.ivs[it] == before.ivs[it] } &&
            after.copy(ivs = before.ivs, cachedStats = before.cachedStats) == before) {
            "The persisted PA8 changed outside paired Special IVs and their optional derived stats."
        }

        val recordSize = recordSize(area)
        val relativeOffset = if (area == LegendsArceusPokemonArea.PASTURE)
            ((pasture - 1) * 30 + position - 1) * recordSize else (position - 1) * recordSize
        val beforeTargetBlock = if (area == LegendsArceusPokemonArea.PASTURE) beforeBlocks.pastures else beforeBlocks.party
        val afterTargetBlock = if (area == LegendsArceusPokemonArea.PASTURE) afterBlocks.pastures else afterBlocks.party
        val beforeOtherBlock = if (area == LegendsArceusPokemonArea.PASTURE) beforeBlocks.party else beforeBlocks.pastures
        val afterOtherBlock = if (area == LegendsArceusPokemonArea.PASTURE) afterBlocks.party else afterBlocks.pastures
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) {
            "The unselected native Pokémon storage object changed."
        }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + recordSize ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        val preserveIvMask = 0xFFFFFFFFL xor SPECIAL_IV_MASK
        require((u32(beforePlain, IV32) and preserveIvMask) ==
            (u32(afterPlain, IV32) and preserveIvMask)) {
            "A non-Special IV or PA8 identity bit changed."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in IV32 until IV32 + 4 ||
                (area == LegendsArceusPokemonArea.PARTY && offset in PARTY_STATS + 6 until PARTY_STATS + 10) ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PA8 edit exceeded paired Special IVs, optional derived Special stats, and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + recordSize ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    private fun resolveGrowth(pokemon: LegendsArceusPokemonRead): Int {
        val growth = LegendsArceusPersonalCatalogue.profiles(pokemon.species, pokemon.form)
            .map { it.growth }.distinct()
        return growth.singleOrNull() ?: throw IllegalArgumentException(
            "PSE could not verify one native growth group for this PA8 species/form."
        )
    }

    private fun resolvePartyPersonal(
        pokemon: LegendsArceusPokemonRead,
        level: Int
    ): LegendsArceusPersonalData {
        val candidates = LegendsArceusPersonalCatalogue.profiles(pokemon.species, pokemon.form)
            .distinctBy { listOf(it.hp, it.attack, it.defense, it.speed, it.specialAttack, it.specialDefense, it.growth) }
        val matching = candidates.filter { matchesCachedStats(pokemon, calculateParty(pokemon, it, level)) }
        return matching.singleOrNull() ?: throw IllegalArgumentException(
            "PSE could not uniquely verify this PA8 form's Legends: Arceus base stats from its native cached stats."
        )
    }

    private fun matchesCachedStats(
        pokemon: LegendsArceusPokemonRead,
        values: LegendsArceusLevelValues
    ): Boolean = pokemon.maximumHp == values.maximumHp && pokemon.cachedStats == values.cachedStats

    private fun matchesCachedValues(
        pokemon: LegendsArceusPokemonRead,
        values: LegendsArceusLevelValues
    ): Boolean = matchesCachedStats(pokemon, values) && pokemon.currentHp == values.currentHp

    private fun calculateParty(
        before: LegendsArceusPokemonRead,
        personal: LegendsArceusPersonalData,
        level: Int
    ): LegendsArceusLevelValues {
        val base = intArrayOf(personal.hp, personal.attack, personal.defense, personal.speed,
            personal.specialAttack, personal.specialDefense)
        val hyperBits = intArrayOf(0, 1, 2, 5, 3, 4)
        val effectiveIv = IntArray(6) { index ->
            if (before.hyperTrainingFlags and (1 shl hyperBits[index]) != 0) 31 else before.ivs[index]
        }
        fun ganbaru(index: Int): Int {
            val iv = effectiveIv[index]
            val bias = when {
                iv >= 31 -> 3
                iv >= 26 -> 2
                iv >= 20 -> 1
                else -> 0
            }
            val multiplier = intArrayOf(0, 2, 3, 4, 7, 8, 9, 14, 15, 16, 25)[
                min(before.ganbaruValues[index] + bias, 10)
            ]
            val step = (sqrt(base[index].toDouble()) * multiplier).toFloat()
            return floor(((step + level) / 2.5f + 0.5f).toDouble()).toInt()
        }
        fun nature(index: Int, value: Int): Int {
            val up = before.statNature / 5
            val down = before.statNature % 5
            if (up == down) return value
            return when (index) {
                up -> value * 110 / 100
                down -> value * 90 / 100
                else -> value
            }
        }
        fun regular(index: Int, natureIndex: Int): Int {
            val initial = ((((level / 50.0f) + 1.0f) * base[index]) / 1.5f).toInt()
            return ganbaru(index) + nature(natureIndex, initial)
        }
        val maximumHp = ganbaru(0) + ((((level / 100.0f) + 1.0f) * base[0]) + level).toInt()
        val stats = listOf(
            regular(1, 0), regular(2, 1), regular(3, 4), regular(4, 2), regular(5, 3)
        )
        val currentHp = when {
            before.currentHp == 0 -> 0
            before.maximumHp != null && before.currentHp >= before.maximumHp -> maximumHp
            else -> min(before.currentHp, maximumHp)
        }
        return LegendsArceusLevelValues(level, before.experience, currentHp, maximumHp, stats)
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
            else -> error("Unsupported native Legends: Arceus growth group.")
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

    private fun targetRecord(
        blocks: LegendsArceusPokemonBlocks,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int
    ): ByteArray {
        val size = recordSize(area)
        val source: ByteArray
        val offset: Int
        if (area == LegendsArceusPokemonArea.PASTURE) {
            require(pasture in 1..32 && position in 1..30) { "The selected native pasture slot is outside storage." }
            source = blocks.pastures
            offset = ((pasture - 1) * 30 + position - 1) * size
        } else {
            require(position in 1..6) { "The selected native party slot is outside the six-slot party." }
            val count = blocks.party[6 * size].toInt() and 255
            require(position <= count) { "The selected native party slot is not occupied." }
            source = blocks.party
            offset = (position - 1) * size
        }
        return source.copyOfRange(offset, offset + size)
    }

    private fun recordSize(area: LegendsArceusPokemonArea): Int =
        if (area == LegendsArceusPokemonArea.PASTURE) LegendsArceusPokemonReadOnly.STORED_SIZE
        else LegendsArceusPokemonReadOnly.PARTY_SIZE

    private fun editRecord(record: ByteArray, values: LegendsArceusLevelValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        writeU32(plain, EXPERIENCE, values.experience)
        writeU16(plain, CURRENT_HP, values.currentHp)
        if (record.size == LegendsArceusPokemonReadOnly.PARTY_SIZE) {
            val stats = requireNotNull(values.cachedStats)
            plain[LEVEL] = values.level.toByte()
            writeU16(plain, MAXIMUM_HP, requireNotNull(values.maximumHp))
            stats.forEachIndexed { index, value -> writeU16(plain, PARTY_STATS + index * 2, value) }
        }
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun editAttackDvRecord(record: ByteArray, values: LegendsArceusAttackDvValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        val iv32 = (u32(plain, IV32) and (0xFFFFFFFFL xor ATTACK_IV_MASK)) or
            (values.attackIv.toLong() shl ATTACK_IV_SHIFT)
        writeU32(plain, IV32, iv32)
        values.attack?.let {
            require(record.size == LegendsArceusPokemonReadOnly.PARTY_SIZE) {
                "Only a party PA8 can store a cached Attack stat."
            }
            writeU16(plain, PARTY_STATS, it)
        }
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun editDefenseDvRecord(record: ByteArray, values: LegendsArceusDefenseDvValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        val iv32 = (u32(plain, IV32) and (0xFFFFFFFFL xor DEFENSE_IV_MASK)) or
            (values.defenseIv.toLong() shl DEFENSE_IV_SHIFT)
        writeU32(plain, IV32, iv32)
        values.defense?.let {
            require(record.size == LegendsArceusPokemonReadOnly.PARTY_SIZE) {
                "Only a party PA8 can store a cached Defense stat."
            }
            writeU16(plain, PARTY_STATS + 2, it)
        }
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun editSpeedDvRecord(record: ByteArray, values: LegendsArceusSpeedDvValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        val iv32 = (u32(plain, IV32) and (0xFFFFFFFFL xor SPEED_IV_MASK)) or
            (values.speedIv.toLong() shl SPEED_IV_SHIFT)
        writeU32(plain, IV32, iv32)
        values.speed?.let {
            require(record.size == LegendsArceusPokemonReadOnly.PARTY_SIZE) {
                "Only a party PA8 can store a cached Speed stat."
            }
            writeU16(plain, PARTY_STATS + 4, it)
        }
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }


    private fun editSpecialDvRecord(record: ByteArray, values: LegendsArceusSpecialDvValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        val iv32 = (u32(plain, IV32) and (0xFFFFFFFFL xor SPECIAL_IV_MASK)) or
            (values.specialIv.toLong() shl SPECIAL_ATTACK_IV_SHIFT) or
            (values.specialIv.toLong() shl SPECIAL_DEFENSE_IV_SHIFT)
        writeU32(plain, IV32, iv32)
        require((values.specialAttack == null) == (values.specialDefense == null)) {
            "Both cached Special stats must be present together."
        }
        values.specialAttack?.let {
            require(record.size == LegendsArceusPokemonReadOnly.PARTY_SIZE) {
                "Only a party PA8 can store cached Special stats."
            }
            writeU16(plain, PARTY_STATS + 6, it)
            writeU16(plain, PARTY_STATS + 8, requireNotNull(values.specialDefense))
        }
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun decryptAndUnshuffle(record: ByteArray): ByteArray {
        require(record.size == LegendsArceusPokemonReadOnly.STORED_SIZE ||
            record.size == LegendsArceusPokemonReadOnly.PARTY_SIZE) {
            "Expected one complete native stored or party PA8 record."
        }
        val ec = u32(record, 0)
        val decoded = record.copyOf()
        xorWords(decoded, 8, STORED_END, ec)
        if (record.size == LegendsArceusPokemonReadOnly.PARTY_SIZE)
            xorWords(decoded, STORED_END, LegendsArceusPokemonReadOnly.PARTY_SIZE, ec)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val plain = decoded.copyOf()
        for (block in 0..3) {
            val from = 8 + (order[block] - '0') * BLOCK_SIZE
            decoded.copyInto(plain, 8 + block * BLOCK_SIZE, from, from + BLOCK_SIZE)
        }
        return plain
    }

    private fun shuffleAndEncrypt(plain: ByteArray): ByteArray {
        val ec = u32(plain, 0)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val shuffled = plain.copyOf()
        for (block in 0..3) {
            val destination = 8 + (order[block] - '0') * BLOCK_SIZE
            plain.copyInto(shuffled, destination, 8 + block * BLOCK_SIZE, 8 + (block + 1) * BLOCK_SIZE)
        }
        xorWords(shuffled, 8, STORED_END, ec)
        if (plain.size == LegendsArceusPokemonReadOnly.PARTY_SIZE)
            xorWords(shuffled, STORED_END, LegendsArceusPokemonReadOnly.PARTY_SIZE, ec)
        return shuffled
    }

    private fun xorWords(bytes: ByteArray, from: Int, end: Int, seed: Long) {
        var state = seed
        for (offset in from until end step 2) {
            state = (state * 0x41C64E6DL + 0x6073L) and 0xFFFFFFFFL
            writeU16(bytes, offset, u16(bytes, offset) xor (state ushr 16).toInt())
        }
    }

    private fun byte(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 255
    private fun u16(bytes: ByteArray, offset: Int): Int = byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long =
        u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }
    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        writeU16(bytes, offset, value.toInt()); writeU16(bytes, offset + 2, (value ushr 16).toInt())
    }
}

/** Gen 1-ordered Experience action backed by the verified PA8 level-dependent write scope. */
object LegendsArceusPokemonExperienceEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        LegendsArceusPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = LegendsArceusPokemonLevelEditing.sha256(bytes)

    fun currentLevel(pokemon: LegendsArceusPokemonRead): Int =
        LegendsArceusPokemonLevelEditing.currentLevel(pokemon)

    fun maximumExperience(pokemon: LegendsArceusPokemonRead): Long =
        LegendsArceusPokemonLevelEditing.maximumExperience(pokemon)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        requestedExperience: Long
    ): LegendsArceusExperienceCopyPlan = LegendsArceusPokemonLevelEditing.stageExperience(
        source, expectedSha256, area, pasture, position, requestedExperience
    )
}

/** Gen 1-ordered Attack DV action mapped to Legends: Arceus's native Attack IV. */
object LegendsArceusPokemonAttackDvEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        LegendsArceusPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = LegendsArceusPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        requestedAttackIv: Int
    ): LegendsArceusAttackDvCopyPlan = LegendsArceusPokemonLevelEditing.stageAttackDv(
        source, expectedSha256, area, pasture, position, requestedAttackIv
    )
}

/** Gen 1-ordered Defense DV action mapped to Legends: Arceus's native Defense IV. */
object LegendsArceusPokemonDefenseDvEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        LegendsArceusPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = LegendsArceusPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        requestedDefenseIv: Int
    ): LegendsArceusDefenseDvCopyPlan = LegendsArceusPokemonLevelEditing.stageDefenseDv(
        source, expectedSha256, area, pasture, position, requestedDefenseIv
    )
}

/** Gen 1-ordered Speed DV action mapped to Legends: Arceus's native Speed IV. */
object LegendsArceusPokemonSpeedDvEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        LegendsArceusPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = LegendsArceusPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        requestedSpeedIv: Int
    ): LegendsArceusSpeedDvCopyPlan = LegendsArceusPokemonLevelEditing.stageSpeedDv(
        source, expectedSha256, area, pasture, position, requestedSpeedIv
    )
}


/** Gen 1 Special DV mapped to Legends: Arceus's paired Special Attack and Special Defense IVs. */
object LegendsArceusPokemonSpecialDvEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        LegendsArceusPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = LegendsArceusPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        requestedSpecialIv: Int
    ): LegendsArceusSpecialDvCopyPlan = LegendsArceusPokemonLevelEditing.stageSpecialDv(
        source, expectedSha256, area, pasture, position, requestedSpecialIv
    )
}

class LegendsArceusDefenseDvCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: LegendsArceusPokemonArea,
    val pasture: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: LegendsArceusPokemonRead,
    val after: LegendsArceusDefenseDvValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = LegendsArceusPokemonDefenseDvEditing.sha256(this.original)
    val editedSha256 = LegendsArceusPokemonDefenseDvEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == LegendsArceusPokemonArea.PASTURE)
            "Pasture $pasture • Slot $position" else "Party $position"
    val recordSize: Int
        get() = if (area == LegendsArceusPokemonArea.PASTURE)
            LegendsArceusPokemonReadOnly.STORED_SIZE else LegendsArceusPokemonReadOnly.PARTY_SIZE

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This Defense DV / IV review was already used. Reopen Pokémon Boxes." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) { "The recovery backup did not verify. Nothing was written." }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LegendsArceusPokemonLevelEditing.verifyDefenseDv(
                original, edited, area, pasture, position, absoluteOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Defense DV / IV edit."
            }
            LegendsArceusPokemonLevelEditing.verifyDefenseDv(
                original, persisted, area, pasture, position, absoluteOffset, after
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Defense DV / IV copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}

class LegendsArceusSpeedDvCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: LegendsArceusPokemonArea,
    val pasture: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: LegendsArceusPokemonRead,
    val after: LegendsArceusSpeedDvValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = LegendsArceusPokemonSpeedDvEditing.sha256(this.original)
    val editedSha256 = LegendsArceusPokemonSpeedDvEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == LegendsArceusPokemonArea.PASTURE)
            "Pasture $pasture • Slot $position" else "Party $position"
    val recordSize: Int
        get() = if (area == LegendsArceusPokemonArea.PASTURE)
            LegendsArceusPokemonReadOnly.STORED_SIZE else LegendsArceusPokemonReadOnly.PARTY_SIZE

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This Speed DV / IV review was already used. Reopen Pokémon Boxes." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) { "The recovery backup did not verify. Nothing was written." }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LegendsArceusPokemonLevelEditing.verifySpeedDv(
                original, edited, area, pasture, position, absoluteOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Speed DV / IV edit."
            }
            LegendsArceusPokemonLevelEditing.verifySpeedDv(
                original, persisted, area, pasture, position, absoluteOffset, after
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Speed DV / IV copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}


class LegendsArceusSpecialDvCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: LegendsArceusPokemonArea,
    val pasture: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: LegendsArceusPokemonRead,
    val after: LegendsArceusSpecialDvValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = LegendsArceusPokemonSpecialDvEditing.sha256(this.original)
    val editedSha256 = LegendsArceusPokemonSpecialDvEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == LegendsArceusPokemonArea.PASTURE)
            "Pasture $pasture • Slot $position" else "Party $position"
    val recordSize: Int
        get() = if (area == LegendsArceusPokemonArea.PASTURE)
            LegendsArceusPokemonReadOnly.STORED_SIZE else LegendsArceusPokemonReadOnly.PARTY_SIZE

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This Special DV / IV review was already used. Reopen Pokémon Boxes." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) { "The recovery backup did not verify. Nothing was written." }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LegendsArceusPokemonLevelEditing.verifySpecialDv(
                original, edited, area, pasture, position, absoluteOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Special DV / IV edit."
            }
            LegendsArceusPokemonLevelEditing.verifySpecialDv(
                original, persisted, area, pasture, position, absoluteOffset, after
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Special DV / IV copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}

class LegendsArceusAttackDvCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: LegendsArceusPokemonArea,
    val pasture: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: LegendsArceusPokemonRead,
    val after: LegendsArceusAttackDvValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = LegendsArceusPokemonAttackDvEditing.sha256(this.original)
    val editedSha256 = LegendsArceusPokemonAttackDvEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == LegendsArceusPokemonArea.PASTURE)
            "Pasture $pasture • Slot $position" else "Party $position"
    val recordSize: Int
        get() = if (area == LegendsArceusPokemonArea.PASTURE)
            LegendsArceusPokemonReadOnly.STORED_SIZE else LegendsArceusPokemonReadOnly.PARTY_SIZE

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This Attack DV / IV review was already used. Reopen Pokémon Boxes." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) { "The recovery backup did not verify. Nothing was written." }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LegendsArceusPokemonLevelEditing.verifyAttackDv(
                original, edited, area, pasture, position, absoluteOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Attack DV / IV edit."
            }
            LegendsArceusPokemonLevelEditing.verifyAttackDv(
                original, persisted, area, pasture, position, absoluteOffset, after
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Attack DV / IV copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}

class LegendsArceusExperienceCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: LegendsArceusPokemonArea,
    val pasture: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: LegendsArceusPokemonRead,
    val beforeLevel: Int,
    val after: LegendsArceusLevelValues,
    val maximumExperience: Long
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = LegendsArceusPokemonExperienceEditing.sha256(this.original)
    val editedSha256 = LegendsArceusPokemonExperienceEditing.sha256(this.edited)
    private var consumed = false
    val recordSize: Int get() = if (area == LegendsArceusPokemonArea.PASTURE)
        LegendsArceusPokemonReadOnly.STORED_SIZE else LegendsArceusPokemonReadOnly.PARTY_SIZE
    val location: String get() = if (area == LegendsArceusPokemonArea.PASTURE)
        "Pasture $pasture • Slot $position" else "Party $position"

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This Experience review was already used. Reopen Pokémon Boxes." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) { "The recovery backup did not verify. Nothing was written." }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LegendsArceusPokemonLevelEditing.verify(
                original, edited, area, pasture, position, absoluteOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Experience edit."
            }
            LegendsArceusPokemonLevelEditing.verify(
                original, persisted, area, pasture, position, absoluteOffset, after
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Experience copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}

class LegendsArceusLevelCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: LegendsArceusPokemonArea,
    val pasture: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: LegendsArceusPokemonRead,
    val beforeLevel: Int,
    val after: LegendsArceusLevelValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = LegendsArceusPokemonLevelEditing.sha256(this.original)
    val editedSha256 = LegendsArceusPokemonLevelEditing.sha256(this.edited)
    private var consumed = false
    val recordSize: Int get() = if (area == LegendsArceusPokemonArea.PASTURE)
        LegendsArceusPokemonReadOnly.STORED_SIZE else LegendsArceusPokemonReadOnly.PARTY_SIZE
    val location: String get() = if (area == LegendsArceusPokemonArea.PASTURE)
        "Pasture $pasture • Slot $position" else "Party $position"

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This Level review was already used. Reopen Pokémon Boxes." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) { "The recovery backup did not verify. Nothing was written." }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LegendsArceusPokemonLevelEditing.verify(
                original, edited, area, pasture, position, absoluteOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed Level edit." }
            LegendsArceusPokemonLevelEditing.verify(
                original, persisted, area, pasture, position, absoluteOffset, after
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Level copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
