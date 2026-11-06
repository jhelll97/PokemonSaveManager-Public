package com.example.pokemonsavemanager

import java.security.MessageDigest
import kotlin.math.min

data class BdspLevelValues(
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

data class BdspAttackDvValues(
    val attackIv: Int,
    val attack: Int
)

data class BdspDefenseDvValues(
    val defenseIv: Int,
    val defense: Int
)

data class BdspSpeedDvValues(
    val speedIv: Int,
    val speed: Int
)

data class BdspSpecialDvValues(
    val specialIv: Int,
    val specialAttack: Int,
    val specialDefense: Int
)

data class BdspHpStatExpValues(
    val hpEv: Int,
    val currentHp: Int,
    val maximumHp: Int
)

data class BdspAttackStatExpValues(
    val attackEv: Int,
    val attack: Int
)

/** Rebuilds one PB8's native level-dependent values in a reviewed new BDSP save copy. */
object BdspPokemonLevelEditing {
    private const val PARTY_OFFSET = 0x14098
    private const val BOX_OFFSET = 0x14EF4
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16
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
    private const val ATTACK_EV = 0x27
    private const val CURRENT_HP = 0x8A
    private const val LEVEL = 0x148
    private const val PARTY_VALUES = 0x14A
    private const val PARTY_VALUES_END = 0x156
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        BdspPokemonNicknameEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = BdspPokemonNicknameEditing.sha256(bytes)

    internal fun maximumExperience(pokemon: BdspPokemonRead): Long {
        require(pokemon.statNature in 0..24) {
            "The PB8 stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        return minimumExperience(100, resolvePersonal(pokemon).growth)
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        requestedLevel: Int
    ): BdspLevelCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val validation = BdspSaveValidation.inspect(original)
        require(validation is BdspValidation.Verified) { "A validated native BDSP save is required." }
        require(requestedLevel in 1..100) { "Level must be 1–100." }
        val recordOffset = targetOffset(original, area, box, position)
        val target = original.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val before = (BdspPokemonReadOnly.read(target) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB8 is not checksum-valid.")
        require(!before.isEgg) { "Egg level editing is not supported for BDSP." }
        require(requestedLevel != before.cachedLevel) { "Enter a different level before review." }
        require(before.cachedLevel in 1..100) { "The saved PB8 Level is outside 1–100." }
        require(before.statNature in 0..24) {
            "The PB8 stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        val personal = resolvePersonal(before)
        require(levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedStats(before, calculate(before, personal, before.cachedLevel))) {
            "The saved Level-dependent stats do not match this PB8's verified species/form profile."
        }
        val after = calculate(before, personal, requestedLevel)
        val afterRecord = editRecord(target, after)
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        refreshDigest(edited)
        verify(original, edited, area, box, position, recordOffset, after)
        return BdspLevelCopyPlan(
            original, edited, area, box, position, recordOffset, before, after
        )
    }

    internal fun stageAttackDv(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        requestedAttackIv: Int
    ): BdspAttackDvCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val validation = BdspSaveValidation.inspect(original)
        require(validation is BdspValidation.Verified) { "A validated native BDSP save is required." }
        require(requestedAttackIv in 0..31) { "Attack IV must be between 0 and 31." }
        val recordOffset = targetOffset(original, area, box, position)
        val target = original.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val before = (BdspPokemonReadOnly.read(target) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB8 is not checksum-valid.")
        require(!before.isEgg) { "Egg Attack DV / IV editing is not supported for BDSP." }
        require(requestedAttackIv != before.ivs[1]) { "Enter a different Attack IV before review." }
        require(before.cachedLevel in 1..100 && before.statNature in 0..24) {
            "The PB8 Level or stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        val personal = resolvePersonal(before)
        require(levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedValues(before,
            calculate(before, personal, before.cachedLevel, before.experience))) {
            "The saved battle values do not match this PB8's verified species/form profile."
        }
        val requestedIvs = before.ivs.toMutableList().also { it[1] = requestedAttackIv }
        val recalculated = calculate(
            before.copy(ivs = requestedIvs), personal, before.cachedLevel, before.experience
        )
        val after = BdspAttackDvValues(requestedAttackIv, recalculated.attack)
        val afterRecord = editAttackDvRecord(target, after)
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        refreshDigest(edited)
        verifyAttackDv(original, edited, area, box, position, recordOffset, after)
        return BdspAttackDvCopyPlan(
            original, edited, area, box, position, recordOffset, before, after
        )
    }

    internal fun stageDefenseDv(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        requestedDefenseIv: Int
    ): BdspDefenseDvCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val validation = BdspSaveValidation.inspect(original)
        require(validation is BdspValidation.Verified) { "A validated native BDSP save is required." }
        require(requestedDefenseIv in 0..31) { "Defense IV must be between 0 and 31." }
        val recordOffset = targetOffset(original, area, box, position)
        val target = original.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val before = (BdspPokemonReadOnly.read(target) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB8 is not checksum-valid.")
        require(!before.isEgg) { "Egg Defense DV / IV editing is not supported for BDSP." }
        require(requestedDefenseIv != before.ivs[2]) { "Enter a different Defense IV before review." }
        require(before.cachedLevel in 1..100 && before.statNature in 0..24) {
            "The PB8 Level or stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        val personal = resolvePersonal(before)
        require(levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedValues(before,
            calculate(before, personal, before.cachedLevel, before.experience))) {
            "The saved battle values do not match this PB8's verified species/form profile."
        }
        val requestedIvs = before.ivs.toMutableList().also { it[2] = requestedDefenseIv }
        val recalculated = calculate(
            before.copy(ivs = requestedIvs), personal, before.cachedLevel, before.experience
        )
        val after = BdspDefenseDvValues(requestedDefenseIv, recalculated.defense)
        val afterRecord = editDefenseDvRecord(target, after)
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        refreshDigest(edited)
        verifyDefenseDv(original, edited, area, box, position, recordOffset, after)
        return BdspDefenseDvCopyPlan(
            original, edited, area, box, position, recordOffset, before, after
        )
    }

    internal fun stageSpeedDv(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        requestedSpeedIv: Int
    ): BdspSpeedDvCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val validation = BdspSaveValidation.inspect(original)
        require(validation is BdspValidation.Verified) { "A validated native BDSP save is required." }
        require(requestedSpeedIv in 0..31) { "Speed IV must be between 0 and 31." }
        val recordOffset = targetOffset(original, area, box, position)
        val target = original.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val before = (BdspPokemonReadOnly.read(target) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB8 is not checksum-valid.")
        require(!before.isEgg) { "Egg Speed DV / IV editing is not supported for BDSP." }
        require(requestedSpeedIv != before.ivs[3]) { "Enter a different Speed IV before review." }
        require(before.cachedLevel in 1..100 && before.statNature in 0..24) {
            "The PB8 Level or stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        val personal = resolvePersonal(before)
        require(levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedValues(before,
            calculate(before, personal, before.cachedLevel, before.experience))) {
            "The saved battle values do not match this PB8's verified species/form profile."
        }
        val requestedIvs = before.ivs.toMutableList().also { it[3] = requestedSpeedIv }
        val recalculated = calculate(
            before.copy(ivs = requestedIvs), personal, before.cachedLevel, before.experience
        )
        val after = BdspSpeedDvValues(requestedSpeedIv, recalculated.speed)
        val afterRecord = editSpeedDvRecord(target, after)
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        refreshDigest(edited)
        verifySpeedDv(original, edited, area, box, position, recordOffset, after)
        return BdspSpeedDvCopyPlan(
            original, edited, area, box, position, recordOffset, before, after
        )
    }

    internal fun stageSpecialDv(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        requestedSpecialIv: Int
    ): BdspSpecialDvCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val validation = BdspSaveValidation.inspect(original)
        require(validation is BdspValidation.Verified) { "A validated native BDSP save is required." }
        require(requestedSpecialIv in 0..31) { "Special IV must be between 0 and 31." }
        val recordOffset = targetOffset(original, area, box, position)
        val target = original.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val before = (BdspPokemonReadOnly.read(target) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB8 is not checksum-valid.")
        require(!before.isEgg) { "Egg Special DV / IV editing is not supported for BDSP." }
        require(requestedSpecialIv != before.ivs[4] || requestedSpecialIv != before.ivs[5]) {
            "Enter a Special IV that differs from Special Attack or Special Defense before review."
        }
        require(before.cachedLevel in 1..100 && before.statNature in 0..24) {
            "The PB8 Level or stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        val personal = resolvePersonal(before)
        require(levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedValues(before,
            calculate(before, personal, before.cachedLevel, before.experience))) {
            "The saved battle values do not match this PB8's verified species/form profile."
        }
        val requestedIvs = before.ivs.toMutableList().also {
            it[4] = requestedSpecialIv
            it[5] = requestedSpecialIv
        }
        val recalculated = calculate(
            before.copy(ivs = requestedIvs), personal, before.cachedLevel, before.experience
        )
        val after = BdspSpecialDvValues(
            requestedSpecialIv, recalculated.specialAttack, recalculated.specialDefense
        )
        val afterRecord = editSpecialDvRecord(target, after)
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        refreshDigest(edited)
        verifySpecialDv(original, edited, area, box, position, recordOffset, after)
        return BdspSpecialDvCopyPlan(
            original, edited, area, box, position, recordOffset, before, after
        )
    }

    internal fun stageHpStatExp(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        requestedHpEv: Int
    ): BdspHpStatExpCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val validation = BdspSaveValidation.inspect(original)
        require(validation is BdspValidation.Verified) { "A validated native BDSP save is required." }
        require(requestedHpEv in 0..252) { "HP EV must be between 0 and 252." }
        val recordOffset = targetOffset(original, area, box, position)
        val target = original.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val before = (BdspPokemonReadOnly.read(target) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB8 is not checksum-valid.")
        require(!before.isEgg) { "Egg HP Stat Exp / EV editing is not supported for BDSP." }
        require(requestedHpEv != before.evs[0]) { "Enter a different HP EV before review." }
        require(requestedHpEv + before.evs.drop(1).sum() <= 510) {
            "The requested HP EV would exceed BDSP's native 510 total EV limit."
        }
        require(before.cachedLevel in 1..100 && before.statNature in 0..24) {
            "The PB8 Level or stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        val personal = resolvePersonal(before)
        require(levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedValues(before,
            calculate(before, personal, before.cachedLevel, before.experience))) {
            "The saved battle values do not match this PB8's verified species/form profile."
        }
        val requestedEvs = before.evs.toMutableList().also { it[0] = requestedHpEv }
        val recalculated = calculate(
            before.copy(evs = requestedEvs), personal, before.cachedLevel, before.experience
        )
        val after = BdspHpStatExpValues(
            requestedHpEv, recalculated.currentHp, recalculated.maximumHp
        )
        val afterRecord = editHpStatExpRecord(target, after)
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        refreshDigest(edited)
        verifyHpStatExp(original, edited, area, box, position, recordOffset, after)
        return BdspHpStatExpCopyPlan(
            original, edited, area, box, position, recordOffset, before, after
        )
    }

    internal fun stageAttackStatExp(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        requestedAttackEv: Int
    ): BdspAttackStatExpCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val validation = BdspSaveValidation.inspect(original)
        require(validation is BdspValidation.Verified) { "A validated native BDSP save is required." }
        require(requestedAttackEv in 0..252) { "Attack EV must be between 0 and 252." }
        val recordOffset = targetOffset(original, area, box, position)
        val target = original.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val before = (BdspPokemonReadOnly.read(target) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB8 is not checksum-valid.")
        require(!before.isEgg) { "Egg Attack Stat Exp / EV editing is not supported for BDSP." }
        require(requestedAttackEv != before.evs[1]) { "Enter a different Attack EV before review." }
        require(requestedAttackEv + before.evs.filterIndexed { index, _ -> index != 1 }.sum() <= 510) {
            "The requested Attack EV would exceed BDSP's native 510 total EV limit."
        }
        require(before.cachedLevel in 1..100 && before.statNature in 0..24) {
            "The PB8 Level or stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        val personal = resolvePersonal(before)
        require(levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedValues(before,
            calculate(before, personal, before.cachedLevel, before.experience))) {
            "The saved battle values do not match this PB8's verified species/form profile."
        }
        val requestedEvs = before.evs.toMutableList().also { it[1] = requestedAttackEv }
        val recalculated = calculate(
            before.copy(evs = requestedEvs), personal, before.cachedLevel, before.experience
        )
        val after = BdspAttackStatExpValues(requestedAttackEv, recalculated.attack)
        val afterRecord = editAttackStatExpRecord(target, after)
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        refreshDigest(edited)
        verifyAttackStatExp(original, edited, area, box, position, recordOffset, after)
        return BdspAttackStatExpCopyPlan(
            original, edited, area, box, position, recordOffset, before, after
        )
    }

    internal fun stageExperience(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        requestedExperience: Long
    ): BdspExperienceCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val validation = BdspSaveValidation.inspect(original)
        require(validation is BdspValidation.Verified) { "A validated native BDSP save is required." }
        val recordOffset = targetOffset(original, area, box, position)
        val target = original.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val before = (BdspPokemonReadOnly.read(target) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB8 is not checksum-valid.")
        require(!before.isEgg) { "Egg experience editing is not supported for BDSP." }
        require(requestedExperience != before.experience) { "Enter different experience before review." }
        require(before.statNature in 0..24) {
            "The PB8 stat nature is invalid, so derived stats cannot be rebuilt safely."
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
            "The saved Level-dependent values do not match this PB8's verified species/form profile."
        }
        val requestedLevel = levelForExperience(requestedExperience, personal.growth)
        val after = calculate(before, personal, requestedLevel, requestedExperience)
        val afterRecord = editRecord(target, after)
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        refreshDigest(edited)
        verify(original, edited, area, box, position, recordOffset, after)
        return BdspExperienceCopyPlan(
            original, edited, area, box, position, recordOffset, before, after, maximumExperience
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        recordOffset: Int,
        expected: BdspLevelValues
    ) {
        val beforeValidation = BdspSaveValidation.inspect(original)
        val afterValidation = BdspSaveValidation.inspect(edited)
        require(beforeValidation is BdspValidation.Verified && afterValidation is BdspValidation.Verified &&
            beforeValidation.version == afterValidation.version &&
            beforeValidation.revision == afterValidation.revision && original.size == edited.size) {
            "The Level edit changed native title identity, revision, size, or integrity."
        }
        require(recordOffset == targetOffset(original, area, box, position) &&
            recordOffset == targetOffset(edited, area, box, position)) {
            "The selected native Pokémon location changed."
        }
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val afterRecord = edited.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val before = (BdspPokemonReadOnly.read(beforeRecord) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PB8 no longer reopens as a Pokémon.")
        val after = (BdspPokemonReadOnly.read(afterRecord) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PB8 did not reopen as a Pokémon.")
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
            "The persisted PB8 changed outside its reviewed level-dependent values."
        }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in EXPERIENCE..EXPERIENCE + 3 ||
                offset in CURRENT_HP..CURRENT_HP + 1 || offset == LEVEL ||
                offset in PARTY_VALUES until PARTY_VALUES_END || beforePlain[offset] == afterPlain[offset]
        }) { "The PB8 edit exceeded EXP, Level, current HP, cached battle stats, and checksum." }

        require(original.indices.all { index ->
            index in recordOffset until recordOffset + BdspPokemonReadOnly.RECORD_SIZE ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH || original[index] == edited[index]
        }) { "An unrelated BDSP save byte changed." }
    }

    internal fun verifyAttackDv(
        original: ByteArray,
        edited: ByteArray,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        recordOffset: Int,
        expected: BdspAttackDvValues
    ) {
        val beforeValidation = BdspSaveValidation.inspect(original)
        val afterValidation = BdspSaveValidation.inspect(edited)
        require(beforeValidation is BdspValidation.Verified && afterValidation is BdspValidation.Verified &&
            beforeValidation.version == afterValidation.version &&
            beforeValidation.revision == afterValidation.revision && original.size == edited.size) {
            "The Attack DV / IV edit changed native title identity, revision, size, or integrity."
        }
        require(recordOffset == targetOffset(original, area, box, position) &&
            recordOffset == targetOffset(edited, area, box, position)) {
            "The selected native Pokémon location changed."
        }
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val afterRecord = edited.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val before = (BdspPokemonReadOnly.read(beforeRecord) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PB8 no longer reopens as a Pokémon.")
        val after = (BdspPokemonReadOnly.read(afterRecord) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PB8 did not reopen as a Pokémon.")
        require(after.ivs[1] == expected.attackIv &&
            after.ivs.indices.filter { it != 1 }.all { after.ivs[it] == before.ivs[it] } &&
            after.cachedStats[0] == expected.attack &&
            after.cachedStats.indices.filter { it != 0 }.all { after.cachedStats[it] == before.cachedStats[it] } &&
            after.copy(ivs = before.ivs, cachedStats = before.cachedStats) == before) {
            "The persisted PB8 changed outside Attack IV and its derived Attack value."
        }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        val preserveIvMask = 0xFFFFFFFFL xor ATTACK_IV_MASK
        require((u32(beforePlain, IV32) and preserveIvMask) ==
            (u32(afterPlain, IV32) and preserveIvMask)) {
            "A non-Attack IV or PB8 identity bit changed."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in IV32 until IV32 + 4 ||
                offset in 0x14C until 0x14E || beforePlain[offset] == afterPlain[offset]
        }) { "The PB8 edit exceeded Attack IV, derived Attack, and checksum fields." }

        require(original.indices.all { index ->
            index in recordOffset until recordOffset + BdspPokemonReadOnly.RECORD_SIZE ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH || original[index] == edited[index]
        }) { "An unrelated BDSP save byte changed." }
    }

    internal fun verifyDefenseDv(
        original: ByteArray,
        edited: ByteArray,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        recordOffset: Int,
        expected: BdspDefenseDvValues
    ) {
        val beforeValidation = BdspSaveValidation.inspect(original)
        val afterValidation = BdspSaveValidation.inspect(edited)
        require(beforeValidation is BdspValidation.Verified && afterValidation is BdspValidation.Verified &&
            beforeValidation.version == afterValidation.version &&
            beforeValidation.revision == afterValidation.revision && original.size == edited.size) {
            "The Defense DV / IV edit changed native title identity, revision, size, or integrity."
        }
        require(recordOffset == targetOffset(original, area, box, position) &&
            recordOffset == targetOffset(edited, area, box, position)) {
            "The selected native Pokémon location changed."
        }
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val afterRecord = edited.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val before = (BdspPokemonReadOnly.read(beforeRecord) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PB8 no longer reopens as a Pokémon.")
        val after = (BdspPokemonReadOnly.read(afterRecord) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PB8 did not reopen as a Pokémon.")
        require(after.ivs[2] == expected.defenseIv &&
            after.ivs.indices.filter { it != 2 }.all { after.ivs[it] == before.ivs[it] } &&
            after.cachedStats[1] == expected.defense &&
            after.cachedStats.indices.filter { it != 1 }.all { after.cachedStats[it] == before.cachedStats[it] } &&
            after.copy(ivs = before.ivs, cachedStats = before.cachedStats) == before) {
            "The persisted PB8 changed outside Defense IV and its derived Defense value."
        }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        val preserveIvMask = 0xFFFFFFFFL xor DEFENSE_IV_MASK
        require((u32(beforePlain, IV32) and preserveIvMask) ==
            (u32(afterPlain, IV32) and preserveIvMask)) {
            "A non-Defense IV or PB8 identity bit changed."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in IV32 until IV32 + 4 ||
                offset in 0x14E until 0x150 || beforePlain[offset] == afterPlain[offset]
        }) { "The PB8 edit exceeded Defense IV, derived Defense, and checksum fields." }

        require(original.indices.all { index ->
            index in recordOffset until recordOffset + BdspPokemonReadOnly.RECORD_SIZE ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH || original[index] == edited[index]
        }) { "An unrelated BDSP save byte changed." }
    }

    internal fun verifySpeedDv(
        original: ByteArray,
        edited: ByteArray,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        recordOffset: Int,
        expected: BdspSpeedDvValues
    ) {
        val beforeValidation = BdspSaveValidation.inspect(original)
        val afterValidation = BdspSaveValidation.inspect(edited)
        require(beforeValidation is BdspValidation.Verified && afterValidation is BdspValidation.Verified &&
            beforeValidation.version == afterValidation.version &&
            beforeValidation.revision == afterValidation.revision && original.size == edited.size) {
            "The Speed DV / IV edit changed native title identity, revision, size, or integrity."
        }
        require(recordOffset == targetOffset(original, area, box, position) &&
            recordOffset == targetOffset(edited, area, box, position)) {
            "The selected native Pokémon location changed."
        }
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val afterRecord = edited.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val before = (BdspPokemonReadOnly.read(beforeRecord) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PB8 no longer reopens as a Pokémon.")
        val after = (BdspPokemonReadOnly.read(afterRecord) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PB8 did not reopen as a Pokémon.")
        require(after.ivs[3] == expected.speedIv &&
            after.ivs.indices.filter { it != 3 }.all { after.ivs[it] == before.ivs[it] } &&
            after.cachedStats[2] == expected.speed &&
            after.cachedStats.indices.filter { it != 2 }.all { after.cachedStats[it] == before.cachedStats[it] } &&
            after.copy(ivs = before.ivs, cachedStats = before.cachedStats) == before) {
            "The persisted PB8 changed outside Speed IV and its derived Speed value."
        }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        val preserveIvMask = 0xFFFFFFFFL xor SPEED_IV_MASK
        require((u32(beforePlain, IV32) and preserveIvMask) ==
            (u32(afterPlain, IV32) and preserveIvMask)) {
            "A non-Speed IV or PB8 identity bit changed."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in IV32 until IV32 + 4 ||
                offset in 0x150 until 0x152 || beforePlain[offset] == afterPlain[offset]
        }) { "The PB8 edit exceeded Speed IV, derived Speed, and checksum fields." }

        require(original.indices.all { index ->
            index in recordOffset until recordOffset + BdspPokemonReadOnly.RECORD_SIZE ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH || original[index] == edited[index]
        }) { "An unrelated BDSP save byte changed." }
    }

    internal fun verifySpecialDv(
        original: ByteArray,
        edited: ByteArray,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        recordOffset: Int,
        expected: BdspSpecialDvValues
    ) {
        val beforeValidation = BdspSaveValidation.inspect(original)
        val afterValidation = BdspSaveValidation.inspect(edited)
        require(beforeValidation is BdspValidation.Verified && afterValidation is BdspValidation.Verified &&
            beforeValidation.version == afterValidation.version &&
            beforeValidation.revision == afterValidation.revision && original.size == edited.size) {
            "The Special DV / IV edit changed native title identity, revision, size, or integrity."
        }
        require(recordOffset == targetOffset(original, area, box, position) &&
            recordOffset == targetOffset(edited, area, box, position)) {
            "The selected native Pokémon location changed."
        }
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val afterRecord = edited.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val before = (BdspPokemonReadOnly.read(beforeRecord) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PB8 no longer reopens as a Pokémon.")
        val after = (BdspPokemonReadOnly.read(afterRecord) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PB8 did not reopen as a Pokémon.")
        require(after.ivs[4] == expected.specialIv && after.ivs[5] == expected.specialIv &&
            after.ivs.indices.filter { it != 4 && it != 5 }.all { after.ivs[it] == before.ivs[it] } &&
            after.cachedStats[3] == expected.specialAttack &&
            after.cachedStats[4] == expected.specialDefense &&
            after.cachedStats.indices.filter { it != 3 && it != 4 }
                .all { after.cachedStats[it] == before.cachedStats[it] } &&
            after.copy(ivs = before.ivs, cachedStats = before.cachedStats) == before) {
            "The persisted PB8 changed outside the paired Special IVs and their derived stats."
        }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        val preserveIvMask = 0xFFFFFFFFL xor SPECIAL_IV_MASK
        require((u32(beforePlain, IV32) and preserveIvMask) ==
            (u32(afterPlain, IV32) and preserveIvMask)) {
            "A non-Special IV or PB8 identity bit changed."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in IV32 until IV32 + 4 ||
                offset in 0x152 until 0x156 || beforePlain[offset] == afterPlain[offset]
        }) { "The PB8 edit exceeded paired Special IVs, derived Special stats, and checksum fields." }

        require(original.indices.all { index ->
            index in recordOffset until recordOffset + BdspPokemonReadOnly.RECORD_SIZE ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH || original[index] == edited[index]
        }) { "An unrelated BDSP save byte changed." }
    }

    internal fun verifyHpStatExp(
        original: ByteArray,
        edited: ByteArray,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        recordOffset: Int,
        expected: BdspHpStatExpValues
    ) {
        val beforeValidation = BdspSaveValidation.inspect(original)
        val afterValidation = BdspSaveValidation.inspect(edited)
        require(beforeValidation is BdspValidation.Verified && afterValidation is BdspValidation.Verified &&
            beforeValidation.version == afterValidation.version &&
            beforeValidation.revision == afterValidation.revision && original.size == edited.size) {
            "The HP Stat Exp edit changed native title identity, revision, size, or integrity."
        }
        require(recordOffset == targetOffset(original, area, box, position) &&
            recordOffset == targetOffset(edited, area, box, position)) {
            "The selected native Pokémon location changed."
        }
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val afterRecord = edited.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val before = (BdspPokemonReadOnly.read(beforeRecord) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PB8 no longer reopens as a Pokémon.")
        val after = (BdspPokemonReadOnly.read(afterRecord) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PB8 did not reopen as a Pokémon.")
        require(after.evs[0] == expected.hpEv &&
            after.evs.indices.filter { it != 0 }.all { after.evs[it] == before.evs[it] } &&
            after.currentHp == expected.currentHp && after.maximumHp == expected.maximumHp &&
            after.cachedStats == before.cachedStats &&
            after.copy(evs = before.evs, currentHp = before.currentHp, maximumHp = before.maximumHp) == before) {
            "The persisted PB8 changed outside HP EV and its derived current and maximum HP values."
        }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset == HP_EV ||
                offset in CURRENT_HP until CURRENT_HP + 2 ||
                offset in 0x14A until 0x14C || beforePlain[offset] == afterPlain[offset]
        }) { "The PB8 edit exceeded HP EV, derived current and maximum HP, and checksum fields." }

        require(original.indices.all { index ->
            index in recordOffset until recordOffset + BdspPokemonReadOnly.RECORD_SIZE ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH || original[index] == edited[index]
        }) { "An unrelated BDSP save byte changed." }
    }

    internal fun verifyAttackStatExp(
        original: ByteArray,
        edited: ByteArray,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        recordOffset: Int,
        expected: BdspAttackStatExpValues
    ) {
        val beforeValidation = BdspSaveValidation.inspect(original)
        val afterValidation = BdspSaveValidation.inspect(edited)
        require(beforeValidation is BdspValidation.Verified && afterValidation is BdspValidation.Verified &&
            beforeValidation.version == afterValidation.version &&
            beforeValidation.revision == afterValidation.revision && original.size == edited.size) {
            "The Attack Stat Exp edit changed native title identity, revision, size, or integrity."
        }
        require(recordOffset == targetOffset(original, area, box, position) &&
            recordOffset == targetOffset(edited, area, box, position)) {
            "The selected native Pokémon location changed."
        }
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val afterRecord = edited.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val before = (BdspPokemonReadOnly.read(beforeRecord) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PB8 no longer reopens as a Pokémon.")
        val after = (BdspPokemonReadOnly.read(afterRecord) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PB8 did not reopen as a Pokémon.")
        require(after.evs[1] == expected.attackEv &&
            after.evs.indices.filter { it != 1 }.all { after.evs[it] == before.evs[it] } &&
            after.cachedStats[0] == expected.attack &&
            after.cachedStats.indices.filter { it != 0 }.all { after.cachedStats[it] == before.cachedStats[it] } &&
            after.copy(evs = before.evs, cachedStats = before.cachedStats) == before) {
            "The persisted PB8 changed outside Attack EV and its derived Attack value."
        }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset == ATTACK_EV ||
                offset in 0x14C until 0x14E || beforePlain[offset] == afterPlain[offset]
        }) { "The PB8 edit exceeded Attack EV, derived Attack, and checksum fields." }

        require(original.indices.all { index ->
            index in recordOffset until recordOffset + BdspPokemonReadOnly.RECORD_SIZE ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH || original[index] == edited[index]
        }) { "An unrelated BDSP save byte changed." }
    }

    private fun resolvePersonal(pokemon: BdspPokemonRead): BdspPersonalData {
        val all = BdspPersonalCatalogue.profiles(pokemon.species, pokemon.form)
        require(all.isNotEmpty()) { "Verified Generation 8 personal data is unavailable for this species." }
        val growth = all.map { it.growth }.distinct()
        require(growth.size == 1) { "The species growth group is ambiguous." }
        val candidates = all.distinctBy {
                listOf(it.hp, it.attack, it.defense, it.speed, it.specialAttack, it.specialDefense, it.growth)
            }
        val matching = candidates.filter {
            matchesCachedStats(pokemon, calculate(pokemon, it, pokemon.cachedLevel))
        }
        return matching.singleOrNull() ?: throw IllegalArgumentException(
            "PSE could not uniquely verify this PB8 form's Generation 8 base stats from its native cached stats."
        )
    }

    private fun matchesCachedStats(pokemon: BdspPokemonRead, values: BdspLevelValues): Boolean =
        pokemon.maximumHp == values.maximumHp &&
            pokemon.cachedStats == listOf(values.attack, values.defense, values.speed,
                values.specialAttack, values.specialDefense)

    private fun matchesCachedValues(
        pokemon: BdspPokemonRead,
        values: BdspLevelValues
    ): Boolean = matchesCachedStats(pokemon, values) && pokemon.currentHp == values.currentHp

    private fun calculate(
        before: BdspPokemonRead,
        personal: BdspPersonalData,
        level: Int,
        experience: Long = minimumExperience(level, personal.growth)
    ): BdspLevelValues {
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
        return BdspLevelValues(
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
            else -> error("Unsupported native BDSP growth group.")
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

    private fun targetOffset(source: ByteArray, area: BdspPokemonArea, box: Int, position: Int): Int {
        val size = BdspPokemonReadOnly.RECORD_SIZE
        return if (area == BdspPokemonArea.BOX) {
            require(box in 1..40 && position in 1..30) {
                "The selected native PC-box slot is outside storage."
            }
            BOX_OFFSET + ((box - 1) * 30 + position - 1) * size
        } else {
            require(position in 1..6) { "The selected native party slot is outside the six-slot party." }
            val count = source[PARTY_OFFSET + 6 * size].toInt() and 255
            require(position <= count) { "The selected native party slot is not occupied." }
            PARTY_OFFSET + (position - 1) * size
        }
    }

    private fun editRecord(record: ByteArray, values: BdspLevelValues): ByteArray {
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

    private fun editAttackDvRecord(record: ByteArray, values: BdspAttackDvValues): ByteArray {
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

    private fun editDefenseDvRecord(record: ByteArray, values: BdspDefenseDvValues): ByteArray {
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

    private fun editSpeedDvRecord(record: ByteArray, values: BdspSpeedDvValues): ByteArray {
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

    private fun editSpecialDvRecord(record: ByteArray, values: BdspSpecialDvValues): ByteArray {
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

    private fun editHpStatExpRecord(record: ByteArray, values: BdspHpStatExpValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        plain[HP_EV] = values.hpEv.toByte()
        writeU16(plain, CURRENT_HP, values.currentHp)
        writeU16(plain, 0x14A, values.maximumHp)
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun editAttackStatExpRecord(record: ByteArray, values: BdspAttackStatExpValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        plain[ATTACK_EV] = values.attackEv.toByte()
        writeU16(plain, 0x14C, values.attack)
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun decryptAndUnshuffle(record: ByteArray): ByteArray {
        require(record.size == BdspPokemonReadOnly.RECORD_SIZE) {
            "Expected one complete native PB8 record."
        }
        val ec = u32(record, 0)
        val decoded = record.copyOf()
        xorWords(decoded, 8, STORED_END, ec)
        xorWords(decoded, STORED_END, BdspPokemonReadOnly.RECORD_SIZE, ec)
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
        xorWords(shuffled, STORED_END, BdspPokemonReadOnly.RECORD_SIZE, ec)
        return shuffled
    }

    private fun xorWords(bytes: ByteArray, from: Int, end: Int, seed: Long) {
        var state = seed
        for (offset in from until end step 2) {
            state = (state * 0x41C64E6DL + 0x6073L) and 0xFFFFFFFFL
            writeU16(bytes, offset, u16(bytes, offset) xor (state ushr 16).toInt())
        }
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
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

/** Gen 1-ordered Experience action backed by the verified PB8 level-dependent write scope. */
object BdspPokemonExperienceEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        BdspPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = BdspPokemonLevelEditing.sha256(bytes)

    fun maximumExperience(pokemon: BdspPokemonRead): Long =
        BdspPokemonLevelEditing.maximumExperience(pokemon)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        requestedExperience: Long
    ): BdspExperienceCopyPlan = BdspPokemonLevelEditing.stageExperience(
        source, expectedSha256, area, box, position, requestedExperience
    )
}

/** Gen 1-ordered Attack DV action mapped to BDSP's native Attack IV. */
object BdspPokemonAttackDvEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        BdspPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = BdspPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        requestedAttackIv: Int
    ): BdspAttackDvCopyPlan = BdspPokemonLevelEditing.stageAttackDv(
        source, expectedSha256, area, box, position, requestedAttackIv
    )
}

/** Gen 1-ordered Defense DV action mapped to BDSP's native Defense IV. */
object BdspPokemonDefenseDvEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        BdspPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = BdspPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        requestedDefenseIv: Int
    ): BdspDefenseDvCopyPlan = BdspPokemonLevelEditing.stageDefenseDv(
        source, expectedSha256, area, box, position, requestedDefenseIv
    )
}

/** Gen 1-ordered Speed DV action mapped to BDSP's native Speed IV. */
object BdspPokemonSpeedDvEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        BdspPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = BdspPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        requestedSpeedIv: Int
    ): BdspSpeedDvCopyPlan = BdspPokemonLevelEditing.stageSpeedDv(
        source, expectedSha256, area, box, position, requestedSpeedIv
    )
}

/** Gen 1 Special DV mapped to BDSP's paired Special Attack and Special Defense IVs. */
object BdspPokemonSpecialDvEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        BdspPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = BdspPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        requestedSpecialIv: Int
    ): BdspSpecialDvCopyPlan = BdspPokemonLevelEditing.stageSpecialDv(
        source, expectedSha256, area, box, position, requestedSpecialIv
    )
}

/** Gen 1 HP Stat Exp mapped to BDSP's native HP EV. */
object BdspPokemonHpStatExpEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        BdspPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = BdspPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        requestedHpEv: Int
    ): BdspHpStatExpCopyPlan = BdspPokemonLevelEditing.stageHpStatExp(
        source, expectedSha256, area, box, position, requestedHpEv
    )
}

/** Gen 1 Attack Stat Exp mapped to BDSP's native Attack EV. */
object BdspPokemonAttackStatExpEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        BdspPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = BdspPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        requestedAttackEv: Int
    ): BdspAttackStatExpCopyPlan = BdspPokemonLevelEditing.stageAttackStatExp(
        source, expectedSha256, area, box, position, requestedAttackEv
    )
}

class BdspDefenseDvCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: BdspPokemonArea,
    val box: Int,
    val position: Int,
    val recordOffset: Int,
    val before: BdspPokemonRead,
    val after: BdspDefenseDvValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = BdspPokemonDefenseDvEditing.sha256(this.original)
    val editedSha256 = BdspPokemonDefenseDvEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == BdspPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

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
            BdspPokemonLevelEditing.verifyDefenseDv(
                original, edited, area, box, position, recordOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Defense DV / IV edit."
            }
            BdspPokemonLevelEditing.verifyDefenseDv(
                original, persisted, area, box, position, recordOffset, after
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

class BdspSpeedDvCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: BdspPokemonArea,
    val box: Int,
    val position: Int,
    val recordOffset: Int,
    val before: BdspPokemonRead,
    val after: BdspSpeedDvValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = BdspPokemonSpeedDvEditing.sha256(this.original)
    val editedSha256 = BdspPokemonSpeedDvEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == BdspPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

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
            BdspPokemonLevelEditing.verifySpeedDv(
                original, edited, area, box, position, recordOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Speed DV / IV edit."
            }
            BdspPokemonLevelEditing.verifySpeedDv(
                original, persisted, area, box, position, recordOffset, after
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

class BdspSpecialDvCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: BdspPokemonArea,
    val box: Int,
    val position: Int,
    val recordOffset: Int,
    val before: BdspPokemonRead,
    val after: BdspSpecialDvValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = BdspPokemonSpecialDvEditing.sha256(this.original)
    val editedSha256 = BdspPokemonSpecialDvEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == BdspPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

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
            BdspPokemonLevelEditing.verifySpecialDv(
                original, edited, area, box, position, recordOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Special DV / IV edit."
            }
            BdspPokemonLevelEditing.verifySpecialDv(
                original, persisted, area, box, position, recordOffset, after
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

class BdspHpStatExpCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: BdspPokemonArea,
    val box: Int,
    val position: Int,
    val recordOffset: Int,
    val before: BdspPokemonRead,
    val after: BdspHpStatExpValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = BdspPokemonHpStatExpEditing.sha256(this.original)
    val editedSha256 = BdspPokemonHpStatExpEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == BdspPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

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
            BdspPokemonLevelEditing.verifyHpStatExp(
                original, edited, area, box, position, recordOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed HP Stat Exp edit."
            }
            BdspPokemonLevelEditing.verifyHpStatExp(
                original, persisted, area, box, position, recordOffset, after
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

class BdspAttackStatExpCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: BdspPokemonArea,
    val box: Int,
    val position: Int,
    val recordOffset: Int,
    val before: BdspPokemonRead,
    val after: BdspAttackStatExpValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = BdspPokemonAttackStatExpEditing.sha256(this.original)
    val editedSha256 = BdspPokemonAttackStatExpEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == BdspPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This Attack Stat Exp review was already used. Reopen Pokémon Boxes." }
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
            BdspPokemonLevelEditing.verifyAttackStatExp(
                original, edited, area, box, position, recordOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Attack Stat Exp edit."
            }
            BdspPokemonLevelEditing.verifyAttackStatExp(
                original, persisted, area, box, position, recordOffset, after
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Attack Stat Exp copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}

class BdspAttackDvCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: BdspPokemonArea,
    val box: Int,
    val position: Int,
    val recordOffset: Int,
    val before: BdspPokemonRead,
    val after: BdspAttackDvValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = BdspPokemonAttackDvEditing.sha256(this.original)
    val editedSha256 = BdspPokemonAttackDvEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == BdspPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

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
            BdspPokemonLevelEditing.verifyAttackDv(
                original, edited, area, box, position, recordOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Attack DV / IV edit."
            }
            BdspPokemonLevelEditing.verifyAttackDv(
                original, persisted, area, box, position, recordOffset, after
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

class BdspExperienceCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: BdspPokemonArea,
    val box: Int,
    val position: Int,
    val recordOffset: Int,
    val before: BdspPokemonRead,
    val after: BdspLevelValues,
    val maximumExperience: Long
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = BdspPokemonExperienceEditing.sha256(this.original)
    val editedSha256 = BdspPokemonExperienceEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == BdspPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

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
            BdspPokemonLevelEditing.verify(
                original, edited, area, box, position, recordOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Experience edit."
            }
            BdspPokemonLevelEditing.verify(
                original, persisted, area, box, position, recordOffset, after
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

class BdspLevelCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: BdspPokemonArea,
    val box: Int,
    val position: Int,
    val recordOffset: Int,
    val before: BdspPokemonRead,
    val after: BdspLevelValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = BdspPokemonLevelEditing.sha256(this.original)
    val editedSha256 = BdspPokemonLevelEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == BdspPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

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
            BdspPokemonLevelEditing.verify(
                original, edited, area, box, position, recordOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Level edit."
            }
            BdspPokemonLevelEditing.verify(
                original, persisted, area, box, position, recordOffset, after
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

