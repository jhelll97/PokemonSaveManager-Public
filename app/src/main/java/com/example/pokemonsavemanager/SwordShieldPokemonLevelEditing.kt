package com.example.pokemonsavemanager

import kotlin.math.min

data class SwordShieldLevelValues(
    val level: Int,
    val experience: Long,
    val currentHp: Int,
    val maximumHp: Int,
    val attack: Int,
    val defense: Int,
    val speed: Int,
    val specialAttack: Int,
    val specialDefense: Int
)

data class SwordShieldAttackDvValues(
    val attackIv: Int,
    val attack: Int
)

data class SwordShieldDefenseDvValues(
    val defenseIv: Int,
    val defense: Int
)

data class SwordShieldSpeedDvValues(
    val speedIv: Int,
    val speed: Int
)

data class SwordShieldSpecialDvValues(
    val specialIv: Int,
    val specialAttack: Int,
    val specialDefense: Int
)

data class SwordShieldHpStatExpValues(
    val hpEv: Int,
    val currentHp: Int,
    val maximumHp: Int
)

/** Rebuilds one PK8's native level-dependent values in a reviewed new Sword/Shield save copy. */
object SwordShieldPokemonLevelEditing {
    private const val STORED_END = 0x148
    private const val EXPERIENCE = 0x10
    private const val IV32 = 0x8C
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
    private const val HP_EV = 0x26
    private const val CURRENT_HP = 0x8A
    private const val LEVEL = 0x148
    private const val MAXIMUM_HP = 0x14A
    private const val PARTY_VALUES = 0x14A
    private const val PARTY_VALUES_END = 0x156
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        SwordShieldPokemonNicknameEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = SwordShieldPokemonNicknameEditing.sha256(bytes)

    internal fun maximumExperience(pokemon: SwordShieldPokemonRead): Long {
        require(pokemon.statNature in 0..24) {
            "The PK8 stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        return minimumExperience(100, resolvePersonal(pokemon).growth)
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        requestedLevel: Int
    ): SwordShieldLevelCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(requestedLevel in 1..100) { "Level must be 1–100." }
        val blocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, box, position)
        val before = (SwordShieldPokemonReadOnly.read(target) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK8 is not checksum-valid.")
        require(!before.isEgg) { "Egg level editing is not supported for Sword/Shield." }
        require(requestedLevel != before.cachedLevel) { "Enter a different level before review." }
        require(before.cachedLevel in 1..100) { "The saved PK8 Level is outside 1–100." }
        require(before.statNature in 0..24) {
            "The PK8 stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        val personal = resolvePersonal(before)
        require(levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedStats(before, calculate(before, personal, before.cachedLevel))) {
            "The saved Level-dependent stats do not match this PK8's verified species/form profile."
        }
        val after = calculate(before, personal, requestedLevel)
        val afterRecord = editRecord(target, after)
        val written = if (area == SwordShieldPokemonArea.BOX) {
            SwordShieldSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else SwordShieldSaveValidation.writePartyRecord(original, position, afterRecord)
        verify(original, written.bytes, area, box, position, written.absoluteOffset, after)
        return SwordShieldLevelCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset, before, after
        )
    }

    internal fun stageAttackDv(
        source: ByteArray,
        expectedSha256: String,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        requestedAttackIv: Int
    ): SwordShieldAttackDvCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(requestedAttackIv in 0..31) { "Attack IV must be between 0 and 31." }
        val blocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, box, position)
        val before = (SwordShieldPokemonReadOnly.read(target) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK8 is not checksum-valid.")
        require(!before.isEgg) { "Egg Attack DV / IV editing is not supported for Sword/Shield." }
        require(requestedAttackIv != before.ivs[1]) { "Enter a different Attack IV before review." }
        require(before.cachedLevel in 1..100 && before.statNature in 0..24) {
            "The PK8 Level or stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        val personal = resolvePersonal(before)
        require(levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedValues(before,
            calculate(before, personal, before.cachedLevel, before.experience))) {
            "The saved battle values do not match this PK8's verified species/form profile."
        }
        val requestedIvs = before.ivs.toMutableList().also { it[1] = requestedAttackIv }
        val recalculated = calculate(
            before.copy(ivs = requestedIvs), personal, before.cachedLevel, before.experience
        )
        val after = SwordShieldAttackDvValues(requestedAttackIv, recalculated.attack)
        val afterRecord = editAttackDvRecord(target, after)
        val written = if (area == SwordShieldPokemonArea.BOX) {
            SwordShieldSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else SwordShieldSaveValidation.writePartyRecord(original, position, afterRecord)
        verifyAttackDv(original, written.bytes, area, box, position, written.absoluteOffset, after)
        return SwordShieldAttackDvCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset, before, after
        )
    }

    internal fun stageDefenseDv(
        source: ByteArray,
        expectedSha256: String,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        requestedDefenseIv: Int
    ): SwordShieldDefenseDvCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(requestedDefenseIv in 0..31) { "Defense IV must be between 0 and 31." }
        val blocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, box, position)
        val before = (SwordShieldPokemonReadOnly.read(target) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK8 is not checksum-valid.")
        require(!before.isEgg) { "Egg Defense DV / IV editing is not supported for Sword/Shield." }
        require(requestedDefenseIv != before.ivs[2]) { "Enter a different Defense IV before review." }
        require(before.cachedLevel in 1..100 && before.statNature in 0..24) {
            "The PK8 Level or stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        val personal = resolvePersonal(before)
        require(levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedValues(before,
            calculate(before, personal, before.cachedLevel, before.experience))) {
            "The saved battle values do not match this PK8's verified species/form profile."
        }
        val requestedIvs = before.ivs.toMutableList().also { it[2] = requestedDefenseIv }
        val recalculated = calculate(
            before.copy(ivs = requestedIvs), personal, before.cachedLevel, before.experience
        )
        val after = SwordShieldDefenseDvValues(requestedDefenseIv, recalculated.defense)
        val afterRecord = editDefenseDvRecord(target, after)
        val written = if (area == SwordShieldPokemonArea.BOX) {
            SwordShieldSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else SwordShieldSaveValidation.writePartyRecord(original, position, afterRecord)
        verifyDefenseDv(original, written.bytes, area, box, position, written.absoluteOffset, after)
        return SwordShieldDefenseDvCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset, before, after
        )
    }

    internal fun stageSpeedDv(
        source: ByteArray,
        expectedSha256: String,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        requestedSpeedIv: Int
    ): SwordShieldSpeedDvCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(requestedSpeedIv in 0..31) { "Speed IV must be between 0 and 31." }
        val blocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, box, position)
        val before = (SwordShieldPokemonReadOnly.read(target) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK8 is not checksum-valid.")
        require(!before.isEgg) { "Egg Speed DV / IV editing is not supported for Sword/Shield." }
        require(requestedSpeedIv != before.ivs[3]) { "Enter a different Speed IV before review." }
        require(before.cachedLevel in 1..100 && before.statNature in 0..24) {
            "The PK8 Level or stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        val personal = resolvePersonal(before)
        require(levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedValues(before,
            calculate(before, personal, before.cachedLevel, before.experience))) {
            "The saved battle values do not match this PK8's verified species/form profile."
        }
        val requestedIvs = before.ivs.toMutableList().also { it[3] = requestedSpeedIv }
        val recalculated = calculate(
            before.copy(ivs = requestedIvs), personal, before.cachedLevel, before.experience
        )
        val after = SwordShieldSpeedDvValues(requestedSpeedIv, recalculated.speed)
        val afterRecord = editSpeedDvRecord(target, after)
        val written = if (area == SwordShieldPokemonArea.BOX) {
            SwordShieldSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else SwordShieldSaveValidation.writePartyRecord(original, position, afterRecord)
        verifySpeedDv(original, written.bytes, area, box, position, written.absoluteOffset, after)
        return SwordShieldSpeedDvCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset, before, after
        )
    }

    internal fun stageSpecialDv(
        source: ByteArray,
        expectedSha256: String,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        requestedSpecialIv: Int
    ): SwordShieldSpecialDvCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(requestedSpecialIv in 0..31) { "Special IV must be between 0 and 31." }
        val blocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, box, position)
        val before = (SwordShieldPokemonReadOnly.read(target) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK8 is not checksum-valid.")
        require(!before.isEgg) { "Egg Special DV / IV editing is not supported for Sword/Shield." }
        require(requestedSpecialIv != before.ivs[4] || requestedSpecialIv != before.ivs[5]) {
            "Enter a Special IV that differs from Special Attack or Special Defense before review."
        }
        require(before.cachedLevel in 1..100 && before.statNature in 0..24) {
            "The PK8 Level or stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        val personal = resolvePersonal(before)
        require(levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedValues(before,
            calculate(before, personal, before.cachedLevel, before.experience))) {
            "The saved battle values do not match this PK8's verified species/form profile."
        }
        val requestedIvs = before.ivs.toMutableList().also {
            it[4] = requestedSpecialIv
            it[5] = requestedSpecialIv
        }
        val recalculated = calculate(
            before.copy(ivs = requestedIvs), personal, before.cachedLevel, before.experience
        )
        val after = SwordShieldSpecialDvValues(
            requestedSpecialIv, recalculated.specialAttack, recalculated.specialDefense
        )
        val afterRecord = editSpecialDvRecord(target, after)
        val written = if (area == SwordShieldPokemonArea.BOX) {
            SwordShieldSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else SwordShieldSaveValidation.writePartyRecord(original, position, afterRecord)
        verifySpecialDv(original, written.bytes, area, box, position, written.absoluteOffset, after)
        return SwordShieldSpecialDvCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset, before, after
        )
    }

    internal fun stageHpStatExp(
        source: ByteArray,
        expectedSha256: String,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        requestedHpEv: Int
    ): SwordShieldHpStatExpCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(requestedHpEv in 0..252) { "HP EV must be between 0 and 252." }
        val blocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, box, position)
        val before = (SwordShieldPokemonReadOnly.read(target) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK8 is not checksum-valid.")
        require(!before.isEgg) { "Egg HP Stat Exp / EV editing is not supported for Sword/Shield." }
        require(requestedHpEv != before.evs[0]) { "Enter a different HP EV before review." }
        require(requestedHpEv + before.evs.drop(1).sum() <= 510) {
            "The requested HP EV would exceed Sword/Shield's native 510 total EV limit."
        }
        require(before.cachedLevel in 1..100 && before.statNature in 0..24) {
            "The PK8 Level or stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        val personal = resolvePersonal(before)
        require(levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedValues(before,
            calculate(before, personal, before.cachedLevel, before.experience))) {
            "The saved battle values do not match this PK8's verified species/form profile."
        }
        val requestedEvs = before.evs.toMutableList().also { it[0] = requestedHpEv }
        val recalculated = calculate(
            before.copy(evs = requestedEvs), personal, before.cachedLevel, before.experience
        )
        val after = SwordShieldHpStatExpValues(
            requestedHpEv, recalculated.currentHp, recalculated.maximumHp
        )
        val afterRecord = editHpStatExpRecord(target, after)
        val written = if (area == SwordShieldPokemonArea.BOX) {
            SwordShieldSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else SwordShieldSaveValidation.writePartyRecord(original, position, afterRecord)
        verifyHpStatExp(original, written.bytes, area, box, position, written.absoluteOffset, after)
        return SwordShieldHpStatExpCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset, before, after
        )
    }

    internal fun stageExperience(
        source: ByteArray,
        expectedSha256: String,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        requestedExperience: Long
    ): SwordShieldExperienceCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, box, position)
        val before = (SwordShieldPokemonReadOnly.read(target) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK8 is not checksum-valid.")
        require(!before.isEgg) { "Egg experience editing is not supported for Sword/Shield." }
        require(requestedExperience != before.experience) { "Enter different experience before review." }
        require(before.statNature in 0..24) {
            "The PK8 stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        val personal = resolvePersonal(before)
        val maximumExperience = minimumExperience(100, personal.growth)
        require(requestedExperience in 0L..maximumExperience) {
            "Experience must be between 0 and $maximumExperience for this Pokémon's native growth group."
        }
        require(before.experience in 0L..maximumExperience && before.cachedLevel in 1..100 &&
            levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedValues(before,
            calculate(before, personal, before.cachedLevel, before.experience))) {
            "The saved Level-dependent values do not match this PK8's verified species/form profile."
        }
        val requestedLevel = levelForExperience(requestedExperience, personal.growth)
        val after = calculate(before, personal, requestedLevel, requestedExperience)
        val afterRecord = editRecord(target, after)
        val written = if (area == SwordShieldPokemonArea.BOX) {
            SwordShieldSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else SwordShieldSaveValidation.writePartyRecord(original, position, afterRecord)
        verify(original, written.bytes, area, box, position, written.absoluteOffset, after)
        return SwordShieldExperienceCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset, before, after,
            maximumExperience
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        expected: SwordShieldLevelValues
    ) {
        val beforeBlocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val afterBlocks = SwordShieldSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The Level edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val before = (SwordShieldPokemonReadOnly.read(beforeRecord) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PK8 no longer reopens as a Pokémon.")
        val after = (SwordShieldPokemonReadOnly.read(afterRecord) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PK8 did not reopen as a Pokémon.")
        require(after.cachedLevel == expected.level && after.experience == expected.experience &&
            after.currentHp == expected.currentHp && after.maximumHp == expected.maximumHp &&
            after.cachedStats == listOf(expected.attack, expected.defense, expected.speed,
                expected.specialAttack, expected.specialDefense) &&
            after.copy(
                cachedLevel = before.cachedLevel,
                experience = before.experience,
                currentHp = before.currentHp,
                maximumHp = before.maximumHp,
                cachedStats = before.cachedStats
            ) == before) {
            "The persisted PK8 changed outside its reviewed level-dependent values."
        }

        val relativeOffset = if (area == SwordShieldPokemonArea.BOX)
            ((box - 1) * 30 + position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
        else (position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
        val beforeTargetBlock = if (area == SwordShieldPokemonArea.BOX) beforeBlocks.boxes else beforeBlocks.party
        val afterTargetBlock = if (area == SwordShieldPokemonArea.BOX) afterBlocks.boxes else afterBlocks.party
        val beforeOtherBlock = if (area == SwordShieldPokemonArea.BOX) beforeBlocks.party else beforeBlocks.boxes
        val afterOtherBlock = if (area == SwordShieldPokemonArea.BOX) afterBlocks.party else afterBlocks.boxes
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) {
            "The unselected native Pokémon storage object changed."
        }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in EXPERIENCE..EXPERIENCE + 3 ||
                offset in CURRENT_HP..CURRENT_HP + 1 || offset == LEVEL ||
                offset in PARTY_VALUES until PARTY_VALUES_END || beforePlain[offset] == afterPlain[offset]
        }) { "The PK8 edit exceeded EXP, Level, current HP, cached battle stats, and checksum." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    internal fun verifyAttackDv(
        original: ByteArray,
        edited: ByteArray,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        expected: SwordShieldAttackDvValues
    ) {
        val beforeBlocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val afterBlocks = SwordShieldSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The Attack DV / IV edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val before = (SwordShieldPokemonReadOnly.read(beforeRecord) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PK8 no longer reopens as a Pokémon.")
        val after = (SwordShieldPokemonReadOnly.read(afterRecord) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PK8 did not reopen as a Pokémon.")
        require(after.ivs[1] == expected.attackIv &&
            after.ivs.indices.filter { it != 1 }.all { after.ivs[it] == before.ivs[it] } &&
            after.cachedStats[0] == expected.attack &&
            after.cachedStats.indices.filter { it != 0 }.all { after.cachedStats[it] == before.cachedStats[it] } &&
            after.copy(ivs = before.ivs, cachedStats = before.cachedStats) == before) {
            "The persisted PK8 changed outside Attack IV and its derived Attack value."
        }

        val relativeOffset = if (area == SwordShieldPokemonArea.BOX)
            ((box - 1) * 30 + position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
        else (position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
        val beforeTargetBlock = if (area == SwordShieldPokemonArea.BOX) beforeBlocks.boxes else beforeBlocks.party
        val afterTargetBlock = if (area == SwordShieldPokemonArea.BOX) afterBlocks.boxes else afterBlocks.party
        val beforeOtherBlock = if (area == SwordShieldPokemonArea.BOX) beforeBlocks.party else beforeBlocks.boxes
        val afterOtherBlock = if (area == SwordShieldPokemonArea.BOX) afterBlocks.party else afterBlocks.boxes
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) {
            "The unselected native Pokémon storage object changed."
        }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        val preserveIvMask = 0xFFFFFFFFL xor ATTACK_IV_MASK
        require((u32(beforePlain, IV32) and preserveIvMask) ==
            (u32(afterPlain, IV32) and preserveIvMask)) {
            "A non-Attack IV or PK8 identity bit changed."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in IV32 until IV32 + 4 ||
                offset in 0x14C until 0x14E || beforePlain[offset] == afterPlain[offset]
        }) { "The PK8 edit exceeded Attack IV, derived Attack, and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    internal fun verifyDefenseDv(
        original: ByteArray,
        edited: ByteArray,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        expected: SwordShieldDefenseDvValues
    ) {
        val beforeBlocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val afterBlocks = SwordShieldSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The Defense DV / IV edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val before = (SwordShieldPokemonReadOnly.read(beforeRecord) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PK8 no longer reopens as a Pokémon.")
        val after = (SwordShieldPokemonReadOnly.read(afterRecord) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PK8 did not reopen as a Pokémon.")
        require(after.ivs[2] == expected.defenseIv &&
            after.ivs.indices.filter { it != 2 }.all { after.ivs[it] == before.ivs[it] } &&
            after.cachedStats[1] == expected.defense &&
            after.cachedStats.indices.filter { it != 1 }.all { after.cachedStats[it] == before.cachedStats[it] } &&
            after.copy(ivs = before.ivs, cachedStats = before.cachedStats) == before) {
            "The persisted PK8 changed outside Defense IV and its derived Defense value."
        }

        val relativeOffset = if (area == SwordShieldPokemonArea.BOX)
            ((box - 1) * 30 + position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
        else (position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
        val beforeTargetBlock = if (area == SwordShieldPokemonArea.BOX) beforeBlocks.boxes else beforeBlocks.party
        val afterTargetBlock = if (area == SwordShieldPokemonArea.BOX) afterBlocks.boxes else afterBlocks.party
        val beforeOtherBlock = if (area == SwordShieldPokemonArea.BOX) beforeBlocks.party else beforeBlocks.boxes
        val afterOtherBlock = if (area == SwordShieldPokemonArea.BOX) afterBlocks.party else afterBlocks.boxes
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) {
            "The unselected native Pokémon storage object changed."
        }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        val preserveIvMask = 0xFFFFFFFFL xor DEFENSE_IV_MASK
        require((u32(beforePlain, IV32) and preserveIvMask) ==
            (u32(afterPlain, IV32) and preserveIvMask)) {
            "A non-Defense IV or PK8 identity bit changed."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in IV32 until IV32 + 4 ||
                offset in 0x14E until 0x150 || beforePlain[offset] == afterPlain[offset]
        }) { "The PK8 edit exceeded Defense IV, derived Defense, and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    internal fun verifySpeedDv(
        original: ByteArray,
        edited: ByteArray,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        expected: SwordShieldSpeedDvValues
    ) {
        val beforeBlocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val afterBlocks = SwordShieldSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The Speed DV / IV edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val before = (SwordShieldPokemonReadOnly.read(beforeRecord) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PK8 no longer reopens as a Pokémon.")
        val after = (SwordShieldPokemonReadOnly.read(afterRecord) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PK8 did not reopen as a Pokémon.")
        require(after.ivs[3] == expected.speedIv &&
            after.ivs.indices.filter { it != 3 }.all { after.ivs[it] == before.ivs[it] } &&
            after.cachedStats[2] == expected.speed &&
            after.cachedStats.indices.filter { it != 2 }.all { after.cachedStats[it] == before.cachedStats[it] } &&
            after.copy(ivs = before.ivs, cachedStats = before.cachedStats) == before) {
            "The persisted PK8 changed outside Speed IV and its derived Speed value."
        }

        val relativeOffset = if (area == SwordShieldPokemonArea.BOX)
            ((box - 1) * 30 + position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
        else (position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
        val beforeTargetBlock = if (area == SwordShieldPokemonArea.BOX) beforeBlocks.boxes else beforeBlocks.party
        val afterTargetBlock = if (area == SwordShieldPokemonArea.BOX) afterBlocks.boxes else afterBlocks.party
        val beforeOtherBlock = if (area == SwordShieldPokemonArea.BOX) beforeBlocks.party else beforeBlocks.boxes
        val afterOtherBlock = if (area == SwordShieldPokemonArea.BOX) afterBlocks.party else afterBlocks.boxes
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) {
            "The unselected native Pokémon storage object changed."
        }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        val preserveIvMask = 0xFFFFFFFFL xor SPEED_IV_MASK
        require((u32(beforePlain, IV32) and preserveIvMask) ==
            (u32(afterPlain, IV32) and preserveIvMask)) {
            "A non-Speed IV or PK8 identity bit changed."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in IV32 until IV32 + 4 ||
                offset in 0x150 until 0x152 || beforePlain[offset] == afterPlain[offset]
        }) { "The PK8 edit exceeded Speed IV, derived Speed, and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    internal fun verifySpecialDv(
        original: ByteArray,
        edited: ByteArray,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        expected: SwordShieldSpecialDvValues
    ) {
        val beforeBlocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val afterBlocks = SwordShieldSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The Special DV / IV edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val before = (SwordShieldPokemonReadOnly.read(beforeRecord) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PK8 no longer reopens as a Pokémon.")
        val after = (SwordShieldPokemonReadOnly.read(afterRecord) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PK8 did not reopen as a Pokémon.")
        require(after.ivs[4] == expected.specialIv && after.ivs[5] == expected.specialIv &&
            after.ivs.indices.filter { it != 4 && it != 5 }.all { after.ivs[it] == before.ivs[it] } &&
            after.cachedStats[3] == expected.specialAttack &&
            after.cachedStats[4] == expected.specialDefense &&
            after.cachedStats.indices.filter { it != 3 && it != 4 }
                .all { after.cachedStats[it] == before.cachedStats[it] } &&
            after.copy(ivs = before.ivs, cachedStats = before.cachedStats) == before) {
            "The persisted PK8 changed outside the paired Special IVs and their derived stats."
        }

        val relativeOffset = if (area == SwordShieldPokemonArea.BOX)
            ((box - 1) * 30 + position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
        else (position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
        val beforeTargetBlock = if (area == SwordShieldPokemonArea.BOX) beforeBlocks.boxes else beforeBlocks.party
        val afterTargetBlock = if (area == SwordShieldPokemonArea.BOX) afterBlocks.boxes else afterBlocks.party
        val beforeOtherBlock = if (area == SwordShieldPokemonArea.BOX) beforeBlocks.party else beforeBlocks.boxes
        val afterOtherBlock = if (area == SwordShieldPokemonArea.BOX) afterBlocks.party else afterBlocks.boxes
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) {
            "The unselected native Pokémon storage object changed."
        }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        val preserveIvMask = 0xFFFFFFFFL xor SPECIAL_IV_MASK
        require((u32(beforePlain, IV32) and preserveIvMask) ==
            (u32(afterPlain, IV32) and preserveIvMask)) {
            "A non-Special IV or PK8 identity bit changed."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in IV32 until IV32 + 4 ||
                offset in 0x152 until 0x156 || beforePlain[offset] == afterPlain[offset]
        }) { "The PK8 edit exceeded paired Special IVs, derived Special stats, and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    internal fun verifyHpStatExp(
        original: ByteArray,
        edited: ByteArray,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        expected: SwordShieldHpStatExpValues
    ) {
        val beforeBlocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val afterBlocks = SwordShieldSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The HP Stat Exp edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val before = (SwordShieldPokemonReadOnly.read(beforeRecord) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PK8 no longer reopens as a Pokémon.")
        val after = (SwordShieldPokemonReadOnly.read(afterRecord) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PK8 did not reopen as a Pokémon.")
        require(after.evs[0] == expected.hpEv &&
            after.evs.indices.filter { it != 0 }.all { after.evs[it] == before.evs[it] } &&
            after.currentHp == expected.currentHp && after.maximumHp == expected.maximumHp &&
            after.cachedStats == before.cachedStats &&
            after.copy(evs = before.evs, currentHp = before.currentHp, maximumHp = before.maximumHp) == before) {
            "The persisted PK8 changed outside HP EV and its derived current and maximum HP values."
        }

        val relativeOffset = if (area == SwordShieldPokemonArea.BOX)
            ((box - 1) * 30 + position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
        else (position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
        val beforeTargetBlock = if (area == SwordShieldPokemonArea.BOX) beforeBlocks.boxes else beforeBlocks.party
        val afterTargetBlock = if (area == SwordShieldPokemonArea.BOX) afterBlocks.boxes else afterBlocks.party
        val beforeOtherBlock = if (area == SwordShieldPokemonArea.BOX) beforeBlocks.party else beforeBlocks.boxes
        val afterOtherBlock = if (area == SwordShieldPokemonArea.BOX) afterBlocks.party else afterBlocks.boxes
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) {
            "The unselected native Pokémon storage object changed."
        }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset == HP_EV ||
                offset in CURRENT_HP until CURRENT_HP + 2 ||
                offset in MAXIMUM_HP until MAXIMUM_HP + 2 ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PK8 edit exceeded HP EV, derived current and maximum HP, and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    private fun resolvePersonal(pokemon: SwordShieldPokemonRead): SwordShieldPersonalData {
        val all = SwordShieldPersonalCatalogue.profiles(pokemon.species)
        require(all.isNotEmpty()) { "Verified Generation 8 personal data is unavailable for this species." }
        val growth = all.map { it.growth }.distinct()
        require(growth.size == 1) { "The species growth group is ambiguous." }
        val candidates = (if (pokemon.form == 0) all.filter { it.isDefault } else all)
            .distinctBy {
                listOf(it.hp, it.attack, it.defense, it.speed, it.specialAttack, it.specialDefense, it.growth)
            }
        val matching = candidates.filter {
            matchesCachedStats(pokemon, calculate(pokemon, it, pokemon.cachedLevel))
        }
        return matching.singleOrNull() ?: throw IllegalArgumentException(
            "PSE could not uniquely verify this PK8 form's Generation 8 base stats from its native cached stats."
        )
    }

    private fun matchesCachedStats(pokemon: SwordShieldPokemonRead, values: SwordShieldLevelValues): Boolean =
        pokemon.maximumHp == values.maximumHp &&
            pokemon.cachedStats == listOf(values.attack, values.defense, values.speed,
                values.specialAttack, values.specialDefense)

    private fun matchesCachedValues(
        pokemon: SwordShieldPokemonRead,
        values: SwordShieldLevelValues
    ): Boolean = matchesCachedStats(pokemon, values) && pokemon.currentHp == values.currentHp

    private fun calculate(
        before: SwordShieldPokemonRead,
        personal: SwordShieldPersonalData,
        level: Int,
        experience: Long = minimumExperience(level, personal.growth)
    ): SwordShieldLevelValues {
        val effectiveIv = IntArray(6) { index ->
            val hyperBit = intArrayOf(0, 1, 2, 5, 3, 4)[index]
            if (before.hyperTrainingFlags and (1 shl hyperBit) != 0) 31 else before.ivs[index]
        }
        val base = intArrayOf(personal.hp, personal.attack, personal.defense, personal.speed,
            personal.specialAttack, personal.specialDefense)
        fun core(index: Int): Int =
            (2 * base[index] + effectiveIv[index] + before.evs[index] / 4) * level / 100
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
        val maximumHp = if (before.species == 292) 1 else core(0) + level + 10
        val attack = nature(0, core(1) + 5)
        val defense = nature(1, core(2) + 5)
        val speed = nature(4, core(3) + 5)
        val specialAttack = nature(2, core(4) + 5)
        val specialDefense = nature(3, core(5) + 5)
        val currentHp = when {
            before.currentHp == 0 -> 0
            before.maximumHp > 0 && before.currentHp >= before.maximumHp -> maximumHp
            else -> min(before.currentHp, maximumHp)
        }
        return SwordShieldLevelValues(
            level, experience, currentHp, maximumHp,
            attack, defense, speed, specialAttack, specialDefense
        )
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
            else -> error("Unsupported native Sword/Shield growth group.")
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
        blocks: SwordShieldPokemonBlocks,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int
    ): ByteArray {
        val size = SwordShieldPokemonReadOnly.RECORD_SIZE
        val source: ByteArray
        val offset: Int
        if (area == SwordShieldPokemonArea.BOX) {
            require(box in 1..32 && position in 1..30) {
                "The selected native PC-box slot is outside storage."
            }
            source = blocks.boxes
            offset = ((box - 1) * 30 + position - 1) * size
        } else {
            require(position in 1..6) { "The selected native party slot is outside the six-slot party." }
            val count = blocks.party[6 * size].toInt() and 255
            require(position <= count) { "The selected native party slot is not occupied." }
            source = blocks.party
            offset = (position - 1) * size
        }
        return source.copyOfRange(offset, offset + size)
    }

    private fun editRecord(record: ByteArray, values: SwordShieldLevelValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        writeU32(plain, EXPERIENCE, values.experience)
        writeU16(plain, CURRENT_HP, values.currentHp)
        plain[LEVEL] = values.level.toByte()
        writeU16(plain, 0x14A, values.maximumHp)
        writeU16(plain, 0x14C, values.attack)
        writeU16(plain, 0x14E, values.defense)
        writeU16(plain, 0x150, values.speed)
        writeU16(plain, 0x152, values.specialAttack)
        writeU16(plain, 0x154, values.specialDefense)
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun editAttackDvRecord(record: ByteArray, values: SwordShieldAttackDvValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        val iv32 = (u32(plain, IV32) and (0xFFFFFFFFL xor ATTACK_IV_MASK)) or
            (values.attackIv.toLong() shl ATTACK_IV_SHIFT)
        writeU32(plain, IV32, iv32)
        writeU16(plain, 0x14C, values.attack)
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun editDefenseDvRecord(record: ByteArray, values: SwordShieldDefenseDvValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        val iv32 = (u32(plain, IV32) and (0xFFFFFFFFL xor DEFENSE_IV_MASK)) or
            (values.defenseIv.toLong() shl DEFENSE_IV_SHIFT)
        writeU32(plain, IV32, iv32)
        writeU16(plain, 0x14E, values.defense)
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun editSpeedDvRecord(record: ByteArray, values: SwordShieldSpeedDvValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        val iv32 = (u32(plain, IV32) and (0xFFFFFFFFL xor SPEED_IV_MASK)) or
            (values.speedIv.toLong() shl SPEED_IV_SHIFT)
        writeU32(plain, IV32, iv32)
        writeU16(plain, 0x150, values.speed)
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun editSpecialDvRecord(record: ByteArray, values: SwordShieldSpecialDvValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        val iv32 = (u32(plain, IV32) and (0xFFFFFFFFL xor SPECIAL_IV_MASK)) or
            (values.specialIv.toLong() shl SPECIAL_ATTACK_IV_SHIFT) or
            (values.specialIv.toLong() shl SPECIAL_DEFENSE_IV_SHIFT)
        writeU32(plain, IV32, iv32)
        writeU16(plain, 0x152, values.specialAttack)
        writeU16(plain, 0x154, values.specialDefense)
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun editHpStatExpRecord(record: ByteArray, values: SwordShieldHpStatExpValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        plain[HP_EV] = values.hpEv.toByte()
        writeU16(plain, CURRENT_HP, values.currentHp)
        writeU16(plain, MAXIMUM_HP, values.maximumHp)
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun decryptAndUnshuffle(record: ByteArray): ByteArray {
        require(record.size == SwordShieldPokemonReadOnly.RECORD_SIZE) {
            "Expected one complete native PK8 record."
        }
        val ec = u32(record, 0)
        val decoded = record.copyOf()
        xorWords(decoded, 8, STORED_END, ec)
        xorWords(decoded, STORED_END, SwordShieldPokemonReadOnly.RECORD_SIZE, ec)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val plain = decoded.copyOf()
        for (block in 0..3) {
            val from = 8 + (order[block] - '0') * 80
            decoded.copyInto(plain, 8 + block * 80, from, from + 80)
        }
        return plain
    }

    private fun shuffleAndEncrypt(plain: ByteArray): ByteArray {
        val ec = u32(plain, 0)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val shuffled = plain.copyOf()
        for (block in 0..3) {
            val destination = 8 + (order[block] - '0') * 80
            plain.copyInto(shuffled, destination, 8 + block * 80, 8 + (block + 1) * 80)
        }
        xorWords(shuffled, 8, STORED_END, ec)
        xorWords(shuffled, STORED_END, SwordShieldPokemonReadOnly.RECORD_SIZE, ec)
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
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        writeU16(bytes, offset, value.toInt())
        writeU16(bytes, offset + 2, (value ushr 16).toInt())
    }
}

/** Gen 1-ordered Experience action backed by the verified PK8 level-dependent write scope. */
object SwordShieldPokemonExperienceEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        SwordShieldPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = SwordShieldPokemonLevelEditing.sha256(bytes)

    fun maximumExperience(pokemon: SwordShieldPokemonRead): Long =
        SwordShieldPokemonLevelEditing.maximumExperience(pokemon)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        requestedExperience: Long
    ): SwordShieldExperienceCopyPlan = SwordShieldPokemonLevelEditing.stageExperience(
        source, expectedSha256, area, box, position, requestedExperience
    )
}

/** Gen 1-ordered Attack DV action mapped to Sword/Shield's native Attack IV. */
object SwordShieldPokemonAttackDvEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        SwordShieldPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = SwordShieldPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        requestedAttackIv: Int
    ): SwordShieldAttackDvCopyPlan = SwordShieldPokemonLevelEditing.stageAttackDv(
        source, expectedSha256, area, box, position, requestedAttackIv
    )
}

/** Gen 1-ordered Defense DV action mapped to Sword/Shield's native Defense IV. */
object SwordShieldPokemonDefenseDvEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        SwordShieldPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = SwordShieldPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        requestedDefenseIv: Int
    ): SwordShieldDefenseDvCopyPlan = SwordShieldPokemonLevelEditing.stageDefenseDv(
        source, expectedSha256, area, box, position, requestedDefenseIv
    )
}

/** Gen 1-ordered Speed DV action mapped to Sword/Shield's native Speed IV. */
object SwordShieldPokemonSpeedDvEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        SwordShieldPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = SwordShieldPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        requestedSpeedIv: Int
    ): SwordShieldSpeedDvCopyPlan = SwordShieldPokemonLevelEditing.stageSpeedDv(
        source, expectedSha256, area, box, position, requestedSpeedIv
    )
}

/** Gen 1 Special DV mapped to Sword/Shield's paired Special Attack and Special Defense IVs. */
object SwordShieldPokemonSpecialDvEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        SwordShieldPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = SwordShieldPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        requestedSpecialIv: Int
    ): SwordShieldSpecialDvCopyPlan = SwordShieldPokemonLevelEditing.stageSpecialDv(
        source, expectedSha256, area, box, position, requestedSpecialIv
    )
}

/** Gen 1 HP Stat Exp mapped to Sword/Shield's native HP EV. */
object SwordShieldPokemonHpStatExpEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        SwordShieldPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = SwordShieldPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        requestedHpEv: Int
    ): SwordShieldHpStatExpCopyPlan = SwordShieldPokemonLevelEditing.stageHpStatExp(
        source, expectedSha256, area, box, position, requestedHpEv
    )
}

class SwordShieldAttackDvCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: SwordShieldPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: SwordShieldPokemonRead,
    val after: SwordShieldAttackDvValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldPokemonAttackDvEditing.sha256(this.original)
    val editedSha256 = SwordShieldPokemonAttackDvEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == SwordShieldPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

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
            SwordShieldPokemonLevelEditing.verifyAttackDv(
                original, edited, area, box, position, absoluteOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Attack DV / IV edit."
            }
            SwordShieldPokemonLevelEditing.verifyAttackDv(
                original, persisted, area, box, position, absoluteOffset, after
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

class SwordShieldDefenseDvCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: SwordShieldPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: SwordShieldPokemonRead,
    val after: SwordShieldDefenseDvValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldPokemonDefenseDvEditing.sha256(this.original)
    val editedSha256 = SwordShieldPokemonDefenseDvEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == SwordShieldPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

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
            SwordShieldPokemonLevelEditing.verifyDefenseDv(
                original, edited, area, box, position, absoluteOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Defense DV / IV edit."
            }
            SwordShieldPokemonLevelEditing.verifyDefenseDv(
                original, persisted, area, box, position, absoluteOffset, after
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

class SwordShieldSpeedDvCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: SwordShieldPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: SwordShieldPokemonRead,
    val after: SwordShieldSpeedDvValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldPokemonSpeedDvEditing.sha256(this.original)
    val editedSha256 = SwordShieldPokemonSpeedDvEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == SwordShieldPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

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
            SwordShieldPokemonLevelEditing.verifySpeedDv(
                original, edited, area, box, position, absoluteOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Speed DV / IV edit."
            }
            SwordShieldPokemonLevelEditing.verifySpeedDv(
                original, persisted, area, box, position, absoluteOffset, after
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

class SwordShieldSpecialDvCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: SwordShieldPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: SwordShieldPokemonRead,
    val after: SwordShieldSpecialDvValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldPokemonSpecialDvEditing.sha256(this.original)
    val editedSha256 = SwordShieldPokemonSpecialDvEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == SwordShieldPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

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
            SwordShieldPokemonLevelEditing.verifySpecialDv(
                original, edited, area, box, position, absoluteOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Special DV / IV edit."
            }
            SwordShieldPokemonLevelEditing.verifySpecialDv(
                original, persisted, area, box, position, absoluteOffset, after
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

class SwordShieldHpStatExpCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: SwordShieldPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: SwordShieldPokemonRead,
    val after: SwordShieldHpStatExpValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldPokemonHpStatExpEditing.sha256(this.original)
    val editedSha256 = SwordShieldPokemonHpStatExpEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == SwordShieldPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This HP Stat Exp review was already used. Reopen Pokémon Boxes." }
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
            SwordShieldPokemonLevelEditing.verifyHpStatExp(
                original, edited, area, box, position, absoluteOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed HP Stat Exp edit."
            }
            SwordShieldPokemonLevelEditing.verifyHpStatExp(
                original, persisted, area, box, position, absoluteOffset, after
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "HP Stat Exp copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}

class SwordShieldExperienceCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: SwordShieldPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: SwordShieldPokemonRead,
    val after: SwordShieldLevelValues,
    val maximumExperience: Long
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldPokemonExperienceEditing.sha256(this.original)
    val editedSha256 = SwordShieldPokemonExperienceEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == SwordShieldPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

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
            SwordShieldPokemonLevelEditing.verify(
                original, edited, area, box, position, absoluteOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Experience edit."
            }
            SwordShieldPokemonLevelEditing.verify(
                original, persisted, area, box, position, absoluteOffset, after
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

class SwordShieldLevelCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: SwordShieldPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: SwordShieldPokemonRead,
    val after: SwordShieldLevelValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldPokemonLevelEditing.sha256(this.original)
    val editedSha256 = SwordShieldPokemonLevelEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == SwordShieldPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

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
            SwordShieldPokemonLevelEditing.verify(
                original, edited, area, box, position, absoluteOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Level edit."
            }
            SwordShieldPokemonLevelEditing.verify(
                original, persisted, area, box, position, absoluteOffset, after
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

