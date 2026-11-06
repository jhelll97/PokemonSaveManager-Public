package com.example.pokemonsavemanager

import kotlin.math.min

data class LetsGoLevelValues(
    val level: Int,
    val experience: Long,
    val currentHp: Int,
    val maximumHp: Int,
    val attack: Int,
    val defense: Int,
    val speed: Int,
    val specialAttack: Int,
    val specialDefense: Int,
    val combatPower: Int
)

data class LetsGoAttackDvValues(
    val attackIv: Int,
    val attack: Int,
    val combatPower: Int
)

data class LetsGoDefenseDvValues(
    val defenseIv: Int,
    val defense: Int,
    val combatPower: Int
)

data class LetsGoSpeedDvValues(
    val speedIv: Int,
    val speed: Int,
    val combatPower: Int
)

data class LetsGoSpecialDvValues(
    val specialIv: Int,
    val specialAttack: Int,
    val specialDefense: Int,
    val combatPower: Int
)

/** Rebuilds one PB7's native level-dependent values in a reviewed new Let's Go save copy. */
object LetsGoPokemonLevelEditing {
    private const val STORAGE_RECORDS = 0x5C00
    private const val STORAGE_RECORDS_LENGTH = 0x3F7A0
    private const val FOOTER = 0xB8600
    private const val DESCRIPTORS = FOOTER + 0x14
    private const val RECORD_BLOCK = 9
    private const val EXPERIENCE = 0x10
    private const val IV32 = 0x74
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
    private const val LEVEL = 0xEC
    private const val ATTACK_STAT = 0xF4
    private const val DEFENSE_STAT = 0xF6
    private const val SPEED_STAT = 0xF8
    private const val SPECIAL_ATTACK_STAT = 0xFA
    private const val SPECIAL_DEFENSE_STAT = 0xFC
    private const val COMBAT_POWER = 0xFE
    private const val PARTY_VALUES = 0xF0
    private const val PARTY_VALUES_END = 0x100
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        LetsGoPokemonNicknameEditing.readDocumentBytes(input)

    fun readSaveBytes(input: java.io.InputStream): ByteArray =
        LetsGoPokemonNicknameEditing.readSaveBytes(input)

    fun sha256(bytes: ByteArray): String = LetsGoPokemonNicknameEditing.sha256(bytes)

    internal fun maximumExperience(species: Int, form: Int): Long? =
        LetsGoPersonalCatalogue.resolve(species, form)?.let { minimumExperience(100, it.growth) }

    fun stage(source: ByteArray, expectedSha256: String, storageIndex: Int, requestedLevel: Int): LetsGoLevelCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(LetsGoSaveValidation.inspect(original) is LetsGoValidation.Verified) {
            "A validated native Let's Go save is required."
        }
        require(requestedLevel in 1..100) { "Level must be 1–100." }
        val storage = LetsGoStorageReadOnly.read(original)
        require(storageIndex in 0 until storage.count) { "The selected native Pokémon record no longer exists." }
        val before = (storage.slot(storageIndex).read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB7 is not checksum-valid.")
        require(!before.isEgg) { "Egg level editing is not supported for Let's Go." }
        require(requestedLevel != before.cachedLevel) { "Enter a different level before review." }
        val personal = LetsGoPersonalCatalogue.resolve(before.species, before.form)
            ?: throw IllegalArgumentException(
                "This PB7 form has no verified native Let's Go stat entry, so Level remains protected."
            )
        require(before.nature in 0..24 && before.currentHandler in 0..1) {
            "The PB7 nature or current-handler identity is invalid, so derived stats cannot be rebuilt safely."
        }
        require(before.cachedLevel in 1..100 &&
            levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        val after = calculate(before, personal, requestedLevel)

        val recordOffset = STORAGE_RECORDS + storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        val afterRecord = editRecord(beforeRecord, after)
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        writeU16(edited, descriptorCrcOffset(RECORD_BLOCK),
            LetsGoSaveValidation.crc16(edited, STORAGE_RECORDS, STORAGE_RECORDS_LENGTH))
        verify(original, edited, storageIndex, after)
        return LetsGoLevelCopyPlan(original, edited, storageIndex, recordOffset, before, after)
    }

    internal fun stageExperience(
        source: ByteArray,
        expectedSha256: String,
        storageIndex: Int,
        requestedExperience: Long
    ): LetsGoExperienceCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(LetsGoSaveValidation.inspect(original) is LetsGoValidation.Verified) {
            "A validated native Let's Go save is required."
        }
        val storage = LetsGoStorageReadOnly.read(original)
        require(storageIndex in 0 until storage.count) { "The selected native Pokémon record no longer exists." }
        val before = (storage.slot(storageIndex).read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB7 is not checksum-valid.")
        require(!before.isEgg) { "Egg experience editing is not supported for Let's Go." }
        require(requestedExperience != before.experience) { "Enter different experience before review." }
        val personal = LetsGoPersonalCatalogue.resolve(before.species, before.form)
            ?: throw IllegalArgumentException(
                "This PB7 form has no verified native Let's Go stat entry, so Experience remains protected."
            )
        require(before.nature in 0..24 && before.currentHandler in 0..1) {
            "The PB7 nature or current-handler identity is invalid, so derived stats cannot be rebuilt safely."
        }
        val maximumExperience = minimumExperience(100, personal.growth)
        require(requestedExperience in 0L..maximumExperience) {
            "Experience must be between 0 and $maximumExperience for this Pokémon's native growth group."
        }
        require(before.experience in 0L..maximumExperience && before.cachedLevel in 1..100 &&
            levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedValues(before, calculate(before, personal, before.cachedLevel, before.experience))) {
            "The saved Level-dependent values do not match this PB7's verified species/form profile."
        }
        val requestedLevel = levelForExperience(requestedExperience, personal.growth)
        val after = calculate(before, personal, requestedLevel, requestedExperience)

        val recordOffset = STORAGE_RECORDS + storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        val afterRecord = editRecord(beforeRecord, after)
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        writeU16(edited, descriptorCrcOffset(RECORD_BLOCK),
            LetsGoSaveValidation.crc16(edited, STORAGE_RECORDS, STORAGE_RECORDS_LENGTH))
        verify(original, edited, storageIndex, after)
        return LetsGoExperienceCopyPlan(original, edited, storageIndex, recordOffset, before, after,
            maximumExperience)
    }

    internal fun stageAttackDv(
        source: ByteArray,
        expectedSha256: String,
        storageIndex: Int,
        requestedAttackIv: Int
    ): LetsGoAttackDvCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(LetsGoSaveValidation.inspect(original) is LetsGoValidation.Verified) {
            "A validated native Let's Go save is required."
        }
        require(requestedAttackIv in 0..31) { "Attack IV must be between 0 and 31." }
        val storage = LetsGoStorageReadOnly.read(original)
        require(storageIndex in 0 until storage.count) { "The selected native Pokémon record no longer exists." }
        val before = (storage.slot(storageIndex).read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB7 is not checksum-valid.")
        require(!before.isEgg) { "Egg Attack DV / IV editing is not supported for Let's Go." }
        require(requestedAttackIv != before.ivs[1]) { "Enter a different Attack IV before review." }
        val personal = LetsGoPersonalCatalogue.resolve(before.species, before.form)
            ?: throw IllegalArgumentException(
                "This PB7 form has no verified native Let's Go stat entry, so Attack DV / IV remains protected."
            )
        require(before.nature in 0..24 && before.currentHandler in 0..1) {
            "The PB7 nature or current-handler identity is invalid, so derived stats cannot be rebuilt safely."
        }
        require(before.cachedLevel in 1..100 &&
            levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedValues(before, calculate(before, personal, before.cachedLevel, before.experience))) {
            "The saved battle values do not match this PB7's verified species/form profile."
        }
        val requestedIvs = before.ivs.toMutableList().also { it[1] = requestedAttackIv }
        val recalculated = calculate(
            before.copy(ivs = requestedIvs), personal, before.cachedLevel, before.experience
        )
        val after = LetsGoAttackDvValues(requestedAttackIv, recalculated.attack, recalculated.combatPower)

        val recordOffset = STORAGE_RECORDS + storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        val afterRecord = editAttackDvRecord(beforeRecord, after)
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        writeU16(edited, descriptorCrcOffset(RECORD_BLOCK),
            LetsGoSaveValidation.crc16(edited, STORAGE_RECORDS, STORAGE_RECORDS_LENGTH))
        verifyAttackDv(original, edited, storageIndex, after)
        return LetsGoAttackDvCopyPlan(original, edited, storageIndex, recordOffset, before, after)
    }

    internal fun stageDefenseDv(
        source: ByteArray,
        expectedSha256: String,
        storageIndex: Int,
        requestedDefenseIv: Int
    ): LetsGoDefenseDvCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(LetsGoSaveValidation.inspect(original) is LetsGoValidation.Verified) {
            "A validated native Let's Go save is required."
        }
        require(requestedDefenseIv in 0..31) { "Defense IV must be between 0 and 31." }
        val storage = LetsGoStorageReadOnly.read(original)
        require(storageIndex in 0 until storage.count) { "The selected native Pokémon record no longer exists." }
        val before = (storage.slot(storageIndex).read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB7 is not checksum-valid.")
        require(!before.isEgg) { "Egg Defense DV / IV editing is not supported for Let's Go." }
        require(requestedDefenseIv != before.ivs[2]) { "Enter a different Defense IV before review." }
        val personal = LetsGoPersonalCatalogue.resolve(before.species, before.form)
            ?: throw IllegalArgumentException(
                "This PB7 form has no verified native Let's Go stat entry, so Defense DV / IV remains protected."
            )
        require(before.nature in 0..24 && before.currentHandler in 0..1) {
            "The PB7 nature or current-handler identity is invalid, so derived stats cannot be rebuilt safely."
        }
        require(before.cachedLevel in 1..100 &&
            levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedValues(before, calculate(before, personal, before.cachedLevel, before.experience))) {
            "The saved battle values do not match this PB7's verified species/form profile."
        }
        val requestedIvs = before.ivs.toMutableList().also { it[2] = requestedDefenseIv }
        val recalculated = calculate(
            before.copy(ivs = requestedIvs), personal, before.cachedLevel, before.experience
        )
        val after = LetsGoDefenseDvValues(
            requestedDefenseIv, recalculated.defense, recalculated.combatPower
        )

        val recordOffset = STORAGE_RECORDS + storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        val afterRecord = editDefenseDvRecord(beforeRecord, after)
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        writeU16(edited, descriptorCrcOffset(RECORD_BLOCK),
            LetsGoSaveValidation.crc16(edited, STORAGE_RECORDS, STORAGE_RECORDS_LENGTH))
        verifyDefenseDv(original, edited, storageIndex, after)
        return LetsGoDefenseDvCopyPlan(original, edited, storageIndex, recordOffset, before, after)
    }

    internal fun stageSpeedDv(
        source: ByteArray,
        expectedSha256: String,
        storageIndex: Int,
        requestedSpeedIv: Int
    ): LetsGoSpeedDvCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(LetsGoSaveValidation.inspect(original) is LetsGoValidation.Verified) {
            "A validated native Let's Go save is required."
        }
        require(requestedSpeedIv in 0..31) { "Speed IV must be between 0 and 31." }
        val storage = LetsGoStorageReadOnly.read(original)
        require(storageIndex in 0 until storage.count) { "The selected native Pokémon record no longer exists." }
        val before = (storage.slot(storageIndex).read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB7 is not checksum-valid.")
        require(!before.isEgg) { "Egg Speed DV / IV editing is not supported for Let's Go." }
        require(requestedSpeedIv != before.ivs[3]) { "Enter a different Speed IV before review." }
        val personal = LetsGoPersonalCatalogue.resolve(before.species, before.form)
            ?: throw IllegalArgumentException(
                "This PB7 form has no verified native Let's Go stat entry, so Speed DV / IV remains protected."
            )
        require(before.nature in 0..24 && before.currentHandler in 0..1) {
            "The PB7 nature or current-handler identity is invalid, so derived stats cannot be rebuilt safely."
        }
        require(before.cachedLevel in 1..100 &&
            levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedValues(before, calculate(before, personal, before.cachedLevel, before.experience))) {
            "The saved battle values do not match this PB7's verified species/form profile."
        }
        val requestedIvs = before.ivs.toMutableList().also { it[3] = requestedSpeedIv }
        val recalculated = calculate(
            before.copy(ivs = requestedIvs), personal, before.cachedLevel, before.experience
        )
        val after = LetsGoSpeedDvValues(requestedSpeedIv, recalculated.speed, recalculated.combatPower)

        val recordOffset = STORAGE_RECORDS + storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        val afterRecord = editSpeedDvRecord(beforeRecord, after)
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        writeU16(edited, descriptorCrcOffset(RECORD_BLOCK),
            LetsGoSaveValidation.crc16(edited, STORAGE_RECORDS, STORAGE_RECORDS_LENGTH))
        verifySpeedDv(original, edited, storageIndex, after)
        return LetsGoSpeedDvCopyPlan(original, edited, storageIndex, recordOffset, before, after)
    }

    internal fun stageSpecialDv(
        source: ByteArray,
        expectedSha256: String,
        storageIndex: Int,
        requestedSpecialIv: Int
    ): LetsGoSpecialDvCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(LetsGoSaveValidation.inspect(original) is LetsGoValidation.Verified) {
            "A validated native Let's Go save is required."
        }
        require(requestedSpecialIv in 0..31) { "Special IV must be between 0 and 31." }
        val storage = LetsGoStorageReadOnly.read(original)
        require(storageIndex in 0 until storage.count) { "The selected native Pokémon record no longer exists." }
        val before = (storage.slot(storageIndex).read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB7 is not checksum-valid.")
        require(!before.isEgg) { "Egg Special DV / IV editing is not supported for Let's Go." }
        require(requestedSpecialIv != before.ivs[4] || requestedSpecialIv != before.ivs[5]) {
            "Enter a Special IV that differs from Special Attack or Special Defense before review."
        }
        val personal = LetsGoPersonalCatalogue.resolve(before.species, before.form)
            ?: throw IllegalArgumentException(
                "This PB7 form has no verified native Let's Go stat entry, so Special DV / IV remains protected."
            )
        require(before.nature in 0..24 && before.currentHandler in 0..1) {
            "The PB7 nature or current-handler identity is invalid, so derived stats cannot be rebuilt safely."
        }
        require(before.cachedLevel in 1..100 &&
            levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedValues(before, calculate(before, personal, before.cachedLevel, before.experience))) {
            "The saved battle values do not match this PB7's verified species/form profile."
        }
        val requestedIvs = before.ivs.toMutableList().also {
            it[4] = requestedSpecialIv
            it[5] = requestedSpecialIv
        }
        val recalculated = calculate(
            before.copy(ivs = requestedIvs), personal, before.cachedLevel, before.experience
        )
        val after = LetsGoSpecialDvValues(
            requestedSpecialIv, recalculated.specialAttack, recalculated.specialDefense,
            recalculated.combatPower
        )

        val recordOffset = STORAGE_RECORDS + storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        val afterRecord = editSpecialDvRecord(beforeRecord, after)
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        writeU16(edited, descriptorCrcOffset(RECORD_BLOCK),
            LetsGoSaveValidation.crc16(edited, STORAGE_RECORDS, STORAGE_RECORDS_LENGTH))
        verifySpecialDv(original, edited, storageIndex, after)
        return LetsGoSpecialDvCopyPlan(original, edited, storageIndex, recordOffset, before, after)
    }

    internal fun verifyAttackDv(
        original: ByteArray,
        edited: ByteArray,
        storageIndex: Int,
        expected: LetsGoAttackDvValues
    ) {
        val beforeValidation = LetsGoSaveValidation.inspect(original)
        val afterValidation = LetsGoSaveValidation.inspect(edited)
        require(beforeValidation is LetsGoValidation.Verified && afterValidation is LetsGoValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The Attack DV / IV edit changed native title identity, size, or block integrity."
        }
        val beforeStorage = LetsGoStorageReadOnly.read(original)
        val afterStorage = LetsGoStorageReadOnly.read(edited)
        require(beforeStorage.count == afterStorage.count && storageIndex in 0 until beforeStorage.count) {
            "The native stored-list identity changed."
        }
        val recordOffset = STORAGE_RECORDS + storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        require((0 until beforeStorage.count).all { index ->
            index == storageIndex || run {
                val offset = STORAGE_RECORDS + index * LetsGoPokemonReadOnly.RECORD_SIZE
                original.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE)
                    .contentEquals(edited.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE))
            }
        }) { "Another native Pokémon record changed." }

        val beforeSlot = beforeStorage.slot(storageIndex)
        val afterSlot = afterStorage.slot(storageIndex)
        require(beforeSlot.partyPositions == afterSlot.partyPositions && beforeSlot.partner == afterSlot.partner) {
            "The selected Pokémon's party or partner identity changed."
        }
        val before = (beforeSlot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PB7 no longer reopens as a Pokémon.")
        val after = (afterSlot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PB7 did not reopen as a Pokémon.")
        require(after.ivs[1] == expected.attackIv &&
            after.ivs.indices.filter { it != 1 }.all { after.ivs[it] == before.ivs[it] } &&
            after.attack == expected.attack && after.combatPower == expected.combatPower &&
            after.copy(ivs = before.ivs, attack = before.attack, combatPower = before.combatPower) == before) {
            "The persisted PB7 changed outside Attack IV and its derived Attack and CP values."
        }

        val beforePlain = decryptAndUnshuffle(
            original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE))
        val afterPlain = decryptAndUnshuffle(
            edited.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE))
        val preserveIvMask = 0xFFFFFFFFL xor ATTACK_IV_MASK
        require((u32(beforePlain, IV32) and preserveIvMask) ==
            (u32(afterPlain, IV32) and preserveIvMask)) {
            "A non-Attack IV or PB7 identity bit changed."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in IV32 until IV32 + 4 ||
                offset in ATTACK_STAT until ATTACK_STAT + 2 ||
                offset in COMBAT_POWER until COMBAT_POWER + 2 ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PB7 edit exceeded Attack IV, derived Attack and CP, and checksum fields." }

        val crcOffset = descriptorCrcOffset(RECORD_BLOCK)
        require(original.indices.all { offset ->
            offset in recordOffset until recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE ||
                offset in crcOffset until crcOffset + 2 || original[offset] == edited[offset]
        }) { "An unrelated Let's Go save byte changed." }
    }

    internal fun verifyDefenseDv(
        original: ByteArray,
        edited: ByteArray,
        storageIndex: Int,
        expected: LetsGoDefenseDvValues
    ) {
        val beforeValidation = LetsGoSaveValidation.inspect(original)
        val afterValidation = LetsGoSaveValidation.inspect(edited)
        require(beforeValidation is LetsGoValidation.Verified && afterValidation is LetsGoValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The Defense DV / IV edit changed native title identity, size, or block integrity."
        }
        val beforeStorage = LetsGoStorageReadOnly.read(original)
        val afterStorage = LetsGoStorageReadOnly.read(edited)
        require(beforeStorage.count == afterStorage.count && storageIndex in 0 until beforeStorage.count) {
            "The native stored-list identity changed."
        }
        val recordOffset = STORAGE_RECORDS + storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        require((0 until beforeStorage.count).all { index ->
            index == storageIndex || run {
                val offset = STORAGE_RECORDS + index * LetsGoPokemonReadOnly.RECORD_SIZE
                original.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE)
                    .contentEquals(edited.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE))
            }
        }) { "Another native Pokémon record changed." }

        val beforeSlot = beforeStorage.slot(storageIndex)
        val afterSlot = afterStorage.slot(storageIndex)
        require(beforeSlot.partyPositions == afterSlot.partyPositions && beforeSlot.partner == afterSlot.partner) {
            "The selected Pokémon's party or partner identity changed."
        }
        val before = (beforeSlot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PB7 no longer reopens as a Pokémon.")
        val after = (afterSlot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PB7 did not reopen as a Pokémon.")
        require(after.ivs[2] == expected.defenseIv &&
            after.ivs.indices.filter { it != 2 }.all { after.ivs[it] == before.ivs[it] } &&
            after.defense == expected.defense && after.combatPower == expected.combatPower &&
            after.copy(ivs = before.ivs, defense = before.defense, combatPower = before.combatPower) == before) {
            "The persisted PB7 changed outside Defense IV and its derived Defense and CP values."
        }

        val beforePlain = decryptAndUnshuffle(
            original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE))
        val afterPlain = decryptAndUnshuffle(
            edited.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE))
        val preserveIvMask = 0xFFFFFFFFL xor DEFENSE_IV_MASK
        require((u32(beforePlain, IV32) and preserveIvMask) ==
            (u32(afterPlain, IV32) and preserveIvMask)) {
            "A non-Defense IV or PB7 identity bit changed."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in IV32 until IV32 + 4 ||
                offset in DEFENSE_STAT until DEFENSE_STAT + 2 ||
                offset in COMBAT_POWER until COMBAT_POWER + 2 ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PB7 edit exceeded Defense IV, derived Defense and CP, and checksum fields." }

        val crcOffset = descriptorCrcOffset(RECORD_BLOCK)
        require(original.indices.all { offset ->
            offset in recordOffset until recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE ||
                offset in crcOffset until crcOffset + 2 || original[offset] == edited[offset]
        }) { "An unrelated Let's Go save byte changed." }
    }

    internal fun verifySpeedDv(
        original: ByteArray,
        edited: ByteArray,
        storageIndex: Int,
        expected: LetsGoSpeedDvValues
    ) {
        val beforeValidation = LetsGoSaveValidation.inspect(original)
        val afterValidation = LetsGoSaveValidation.inspect(edited)
        require(beforeValidation is LetsGoValidation.Verified && afterValidation is LetsGoValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The Speed DV / IV edit changed native title identity, size, or block integrity."
        }
        val beforeStorage = LetsGoStorageReadOnly.read(original)
        val afterStorage = LetsGoStorageReadOnly.read(edited)
        require(beforeStorage.count == afterStorage.count && storageIndex in 0 until beforeStorage.count) {
            "The native stored-list identity changed."
        }
        val recordOffset = STORAGE_RECORDS + storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        require((0 until beforeStorage.count).all { index ->
            index == storageIndex || run {
                val offset = STORAGE_RECORDS + index * LetsGoPokemonReadOnly.RECORD_SIZE
                original.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE)
                    .contentEquals(edited.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE))
            }
        }) { "Another native Pokémon record changed." }

        val beforeSlot = beforeStorage.slot(storageIndex)
        val afterSlot = afterStorage.slot(storageIndex)
        require(beforeSlot.partyPositions == afterSlot.partyPositions && beforeSlot.partner == afterSlot.partner) {
            "The selected Pokémon's party or partner identity changed."
        }
        val before = (beforeSlot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PB7 no longer reopens as a Pokémon.")
        val after = (afterSlot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PB7 did not reopen as a Pokémon.")
        require(after.ivs[3] == expected.speedIv &&
            after.ivs.indices.filter { it != 3 }.all { after.ivs[it] == before.ivs[it] } &&
            after.speed == expected.speed && after.combatPower == expected.combatPower &&
            after.copy(ivs = before.ivs, speed = before.speed, combatPower = before.combatPower) == before) {
            "The persisted PB7 changed outside Speed IV and its derived Speed and CP values."
        }

        val beforePlain = decryptAndUnshuffle(
            original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE))
        val afterPlain = decryptAndUnshuffle(
            edited.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE))
        val preserveIvMask = 0xFFFFFFFFL xor SPEED_IV_MASK
        require((u32(beforePlain, IV32) and preserveIvMask) ==
            (u32(afterPlain, IV32) and preserveIvMask)) {
            "A non-Speed IV or PB7 identity bit changed."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in IV32 until IV32 + 4 ||
                offset in SPEED_STAT until SPEED_STAT + 2 ||
                offset in COMBAT_POWER until COMBAT_POWER + 2 ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PB7 edit exceeded Speed IV, derived Speed and CP, and checksum fields." }

        val crcOffset = descriptorCrcOffset(RECORD_BLOCK)
        require(original.indices.all { offset ->
            offset in recordOffset until recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE ||
                offset in crcOffset until crcOffset + 2 || original[offset] == edited[offset]
        }) { "An unrelated Let's Go save byte changed." }
    }

    internal fun verifySpecialDv(
        original: ByteArray,
        edited: ByteArray,
        storageIndex: Int,
        expected: LetsGoSpecialDvValues
    ) {
        val beforeValidation = LetsGoSaveValidation.inspect(original)
        val afterValidation = LetsGoSaveValidation.inspect(edited)
        require(beforeValidation is LetsGoValidation.Verified && afterValidation is LetsGoValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The Special DV / IV edit changed native title identity, size, or block integrity."
        }
        val beforeStorage = LetsGoStorageReadOnly.read(original)
        val afterStorage = LetsGoStorageReadOnly.read(edited)
        require(beforeStorage.count == afterStorage.count && storageIndex in 0 until beforeStorage.count) {
            "The native stored-list identity changed."
        }
        val recordOffset = STORAGE_RECORDS + storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        require((0 until beforeStorage.count).all { index ->
            index == storageIndex || run {
                val offset = STORAGE_RECORDS + index * LetsGoPokemonReadOnly.RECORD_SIZE
                original.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE)
                    .contentEquals(edited.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE))
            }
        }) { "Another native Pokémon record changed." }

        val beforeSlot = beforeStorage.slot(storageIndex)
        val afterSlot = afterStorage.slot(storageIndex)
        require(beforeSlot.partyPositions == afterSlot.partyPositions && beforeSlot.partner == afterSlot.partner) {
            "The selected Pokémon's party or partner identity changed."
        }
        val before = (beforeSlot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PB7 no longer reopens as a Pokémon.")
        val after = (afterSlot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PB7 did not reopen as a Pokémon.")
        require(after.ivs[4] == expected.specialIv && after.ivs[5] == expected.specialIv &&
            after.ivs.indices.filter { it != 4 && it != 5 }.all { after.ivs[it] == before.ivs[it] } &&
            after.specialAttack == expected.specialAttack &&
            after.specialDefense == expected.specialDefense &&
            after.combatPower == expected.combatPower &&
            after.copy(ivs = before.ivs, specialAttack = before.specialAttack,
                specialDefense = before.specialDefense, combatPower = before.combatPower) == before) {
            "The persisted PB7 changed outside the paired Special IVs and their derived stats and CP."
        }

        val beforePlain = decryptAndUnshuffle(
            original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE))
        val afterPlain = decryptAndUnshuffle(
            edited.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE))
        val preserveIvMask = 0xFFFFFFFFL xor SPECIAL_IV_MASK
        require((u32(beforePlain, IV32) and preserveIvMask) ==
            (u32(afterPlain, IV32) and preserveIvMask)) {
            "A non-Special IV or PB7 identity bit changed."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in IV32 until IV32 + 4 ||
                offset in SPECIAL_ATTACK_STAT until SPECIAL_ATTACK_STAT + 2 ||
                offset in SPECIAL_DEFENSE_STAT until SPECIAL_DEFENSE_STAT + 2 ||
                offset in COMBAT_POWER until COMBAT_POWER + 2 ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PB7 edit exceeded paired Special IVs, derived Special stats and CP, and checksum fields." }

        val crcOffset = descriptorCrcOffset(RECORD_BLOCK)
        require(original.indices.all { offset ->
            offset in recordOffset until recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE ||
                offset in crcOffset until crcOffset + 2 || original[offset] == edited[offset]
        }) { "An unrelated Let's Go save byte changed." }
    }

    internal fun verify(original: ByteArray, edited: ByteArray, storageIndex: Int, expected: LetsGoLevelValues) {
        val beforeValidation = LetsGoSaveValidation.inspect(original)
        val afterValidation = LetsGoSaveValidation.inspect(edited)
        require(beforeValidation is LetsGoValidation.Verified && afterValidation is LetsGoValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The edited copy changed native title identity, size, or block integrity."
        }
        val beforeStorage = LetsGoStorageReadOnly.read(original)
        val afterStorage = LetsGoStorageReadOnly.read(edited)
        require(beforeStorage.count == afterStorage.count && storageIndex in 0 until beforeStorage.count) {
            "The native stored-list identity changed."
        }
        val recordOffset = STORAGE_RECORDS + storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        require((0 until beforeStorage.count).all { index ->
            index == storageIndex || run {
                val offset = STORAGE_RECORDS + index * LetsGoPokemonReadOnly.RECORD_SIZE
                original.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE)
                    .contentEquals(edited.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE))
            }
        }) { "Another native Pokémon record changed." }

        val beforeSlot = beforeStorage.slot(storageIndex)
        val afterSlot = afterStorage.slot(storageIndex)
        require(beforeSlot.partyPositions == afterSlot.partyPositions && beforeSlot.partner == afterSlot.partner) {
            "The selected Pokémon's party or partner identity changed."
        }
        val before = (beforeSlot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PB7 no longer reopens as a Pokémon.")
        val after = (afterSlot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PB7 did not reopen as a Pokémon.")
        require(after.cachedLevel == expected.level && after.experience == expected.experience &&
            after.currentHp == expected.currentHp && after.maximumHp == expected.maximumHp &&
            after.attack == expected.attack && after.defense == expected.defense &&
            after.speed == expected.speed && after.specialAttack == expected.specialAttack &&
            after.specialDefense == expected.specialDefense && after.combatPower == expected.combatPower &&
            after.copy(
                cachedLevel = before.cachedLevel,
                experience = before.experience,
                currentHp = before.currentHp,
                maximumHp = before.maximumHp,
                attack = before.attack,
                defense = before.defense,
                speed = before.speed,
                specialAttack = before.specialAttack,
                specialDefense = before.specialDefense,
                combatPower = before.combatPower
            ) == before) {
            "The persisted PB7 changed outside its reviewed level-dependent values."
        }

        val beforePlain = decryptAndUnshuffle(
            original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE))
        val afterPlain = decryptAndUnshuffle(
            edited.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE))
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in EXPERIENCE..EXPERIENCE + 3 || offset == LEVEL ||
                offset in PARTY_VALUES until PARTY_VALUES_END || beforePlain[offset] == afterPlain[offset]
        }) { "The PB7 edit exceeded EXP, Level, cached battle values, CP, and checksum." }

        val crcOffset = descriptorCrcOffset(RECORD_BLOCK)
        require(original.indices.all { offset ->
            offset in recordOffset until recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE ||
                offset in crcOffset until crcOffset + 2 || original[offset] == edited[offset]
        }) { "An unrelated Let's Go save byte changed." }
    }

    private fun matchesCachedValues(before: LetsGoPokemonRead, expected: LetsGoLevelValues): Boolean =
        before.currentHp == expected.currentHp && before.maximumHp == expected.maximumHp &&
            before.attack == expected.attack && before.defense == expected.defense &&
            before.speed == expected.speed && before.specialAttack == expected.specialAttack &&
            before.specialDefense == expected.specialDefense && before.combatPower == expected.combatPower

    private fun calculate(
        before: LetsGoPokemonRead,
        personal: LetsGoPersonalData,
        level: Int,
        experience: Long = minimumExperience(level, personal.growth)
    ): LetsGoLevelValues {
        val effectiveIv = IntArray(6) { index ->
            val hyperBit = intArrayOf(0, 1, 2, 5, 3, 4)[index]
            if (before.hyperTrainingFlags and (1 shl hyperBit) != 0) 31 else before.ivs[index]
        }
        val base = intArrayOf(personal.hp, personal.attack, personal.defense, personal.speed,
            personal.specialAttack, personal.specialDefense)
        val scalar = (((before.currentFriendship / 255.0f / 10.0f) + 1.0f) * 100.0f).toInt()
        fun raw(index: Int): Int = (effectiveIv[index] + 2 * base[index]) * level / 100
        fun nature(index: Int, value: Int): Int {
            if (before.nature !in 0..24) return value
            val up = before.nature / 5
            val down = before.nature % 5
            if (up == down) return value
            return when (index) {
                up -> value * 110 / 100
                down -> value * 90 / 100
                else -> value
            }
        }
        val maximumHp = before.awakened[0] + raw(0) + 10 + level
        fun battle(index: Int, natureIndex: Int): Int =
            before.awakened[index] + scalar * nature(natureIndex, raw(index) + 5) / 100
        val attack = battle(1, 0)
        val defense = battle(2, 1)
        val speed = battle(3, 4)
        val specialAttack = battle(4, 2)
        val specialDefense = battle(5, 3)
        val baseCpSum = raw(0) + 10 + level +
            nature(0, raw(1) + 5) * scalar / 100 +
            nature(1, raw(2) + 5) * scalar / 100 +
            nature(4, raw(3) + 5) * scalar / 100 +
            nature(2, raw(4) + 5) * scalar / 100 +
            nature(3, raw(5) + 5) * scalar / 100
        val baseCp = (baseCpSum * 6f * level / 100f).toInt()
        val awakeCp = (before.awakened.sum() * (level * 4f / 100f + 2f)).toInt()
        val combatPower = min(10000, baseCp + awakeCp)
        val currentHp = when {
            before.currentHp == 0 -> 0
            before.maximumHp > 0 && before.currentHp >= before.maximumHp -> maximumHp
            else -> min(before.currentHp, maximumHp)
        }
        return LetsGoLevelValues(
            level, experience, currentHp, maximumHp,
            attack, defense, speed, specialAttack, specialDefense, combatPower
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
            else -> error("Unsupported native Let's Go growth group.")
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

    private fun editRecord(record: ByteArray, values: LetsGoLevelValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        writeU32(plain, EXPERIENCE, values.experience)
        plain[LEVEL] = values.level.toByte()
        writeU16(plain, 0xF0, values.currentHp)
        writeU16(plain, 0xF2, values.maximumHp)
        writeU16(plain, 0xF4, values.attack)
        writeU16(plain, 0xF6, values.defense)
        writeU16(plain, 0xF8, values.speed)
        writeU16(plain, 0xFA, values.specialAttack)
        writeU16(plain, 0xFC, values.specialDefense)
        writeU16(plain, 0xFE, values.combatPower)
        var checksum = 0
        for (offset in 8 until 0xE8 step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun editAttackDvRecord(record: ByteArray, values: LetsGoAttackDvValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        val iv32 = (u32(plain, IV32) and (0xFFFFFFFFL xor ATTACK_IV_MASK)) or
            (values.attackIv.toLong() shl ATTACK_IV_SHIFT)
        writeU32(plain, IV32, iv32)
        writeU16(plain, ATTACK_STAT, values.attack)
        writeU16(plain, COMBAT_POWER, values.combatPower)
        var checksum = 0
        for (offset in 8 until 0xE8 step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun editDefenseDvRecord(record: ByteArray, values: LetsGoDefenseDvValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        val iv32 = (u32(plain, IV32) and (0xFFFFFFFFL xor DEFENSE_IV_MASK)) or
            (values.defenseIv.toLong() shl DEFENSE_IV_SHIFT)
        writeU32(plain, IV32, iv32)
        writeU16(plain, DEFENSE_STAT, values.defense)
        writeU16(plain, COMBAT_POWER, values.combatPower)
        var checksum = 0
        for (offset in 8 until 0xE8 step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun editSpeedDvRecord(record: ByteArray, values: LetsGoSpeedDvValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        val iv32 = (u32(plain, IV32) and (0xFFFFFFFFL xor SPEED_IV_MASK)) or
            (values.speedIv.toLong() shl SPEED_IV_SHIFT)
        writeU32(plain, IV32, iv32)
        writeU16(plain, SPEED_STAT, values.speed)
        writeU16(plain, COMBAT_POWER, values.combatPower)
        var checksum = 0
        for (offset in 8 until 0xE8 step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun editSpecialDvRecord(record: ByteArray, values: LetsGoSpecialDvValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        val iv32 = (u32(plain, IV32) and (0xFFFFFFFFL xor SPECIAL_IV_MASK)) or
            (values.specialIv.toLong() shl SPECIAL_ATTACK_IV_SHIFT) or
            (values.specialIv.toLong() shl SPECIAL_DEFENSE_IV_SHIFT)
        writeU32(plain, IV32, iv32)
        writeU16(plain, SPECIAL_ATTACK_STAT, values.specialAttack)
        writeU16(plain, SPECIAL_DEFENSE_STAT, values.specialDefense)
        writeU16(plain, COMBAT_POWER, values.combatPower)
        var checksum = 0
        for (offset in 8 until 0xE8 step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun decryptAndUnshuffle(record: ByteArray): ByteArray {
        require(record.size == LetsGoPokemonReadOnly.RECORD_SIZE) { "Expected one complete native PB7 record." }
        val ec = u32(record, 0)
        val decrypted = record.copyOf()
        xorWords(decrypted, 8, 0xE8, ec)
        xorWords(decrypted, 0xE8, LetsGoPokemonReadOnly.RECORD_SIZE, ec)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val plain = decrypted.copyOf()
        for (block in 0..3) {
            val from = 8 + (order[block] - '0') * 56
            decrypted.copyInto(plain, 8 + block * 56, from, from + 56)
        }
        return plain
    }

    private fun shuffleAndEncrypt(plain: ByteArray): ByteArray {
        val ec = u32(plain, 0)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val shuffled = plain.copyOf()
        for (block in 0..3) {
            val destination = 8 + (order[block] - '0') * 56
            plain.copyInto(shuffled, destination, 8 + block * 56, 8 + (block + 1) * 56)
        }
        xorWords(shuffled, 8, 0xE8, ec)
        xorWords(shuffled, 0xE8, LetsGoPokemonReadOnly.RECORD_SIZE, ec)
        return shuffled
    }

    private fun xorWords(bytes: ByteArray, from: Int, endExclusive: Int, seed: Long) {
        var state = seed
        for (offset in from until endExclusive step 2) {
            state = (state * 0x41C64E6DL + 0x6073L) and 0xFFFFFFFFL
            writeU16(bytes, offset, u16(bytes, offset) xor (state ushr 16).toInt())
        }
    }

    private fun descriptorCrcOffset(block: Int): Int = DESCRIPTORS + block * 8 + 6
    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
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

/** Gen 1-ordered Experience action backed by the same verified PB7 level-dependent write scope. */
object LetsGoPokemonExperienceEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        LetsGoPokemonLevelEditing.readDocumentBytes(input)

    fun readSaveBytes(input: java.io.InputStream): ByteArray =
        LetsGoPokemonLevelEditing.readSaveBytes(input)

    fun sha256(bytes: ByteArray): String = LetsGoPokemonLevelEditing.sha256(bytes)

    fun maximumExperience(species: Int, form: Int): Long? =
        LetsGoPokemonLevelEditing.maximumExperience(species, form)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        storageIndex: Int,
        requestedExperience: Long
    ): LetsGoExperienceCopyPlan = LetsGoPokemonLevelEditing.stageExperience(
        source, expectedSha256, storageIndex, requestedExperience
    )
}

/** Gen 1-ordered Attack DV action mapped to Let's Go's native Attack IV. */
object LetsGoPokemonAttackDvEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        LetsGoPokemonLevelEditing.readDocumentBytes(input)

    fun readSaveBytes(input: java.io.InputStream): ByteArray =
        LetsGoPokemonLevelEditing.readSaveBytes(input)

    fun sha256(bytes: ByteArray): String = LetsGoPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        storageIndex: Int,
        requestedAttackIv: Int
    ): LetsGoAttackDvCopyPlan = LetsGoPokemonLevelEditing.stageAttackDv(
        source, expectedSha256, storageIndex, requestedAttackIv
    )
}

/** Gen 1-ordered Defense DV action mapped to Let's Go's native Defense IV. */
object LetsGoPokemonDefenseDvEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        LetsGoPokemonLevelEditing.readDocumentBytes(input)

    fun readSaveBytes(input: java.io.InputStream): ByteArray =
        LetsGoPokemonLevelEditing.readSaveBytes(input)

    fun sha256(bytes: ByteArray): String = LetsGoPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        storageIndex: Int,
        requestedDefenseIv: Int
    ): LetsGoDefenseDvCopyPlan = LetsGoPokemonLevelEditing.stageDefenseDv(
        source, expectedSha256, storageIndex, requestedDefenseIv
    )
}

/** Gen 1-ordered Speed DV action mapped to Let's Go's native Speed IV. */
object LetsGoPokemonSpeedDvEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        LetsGoPokemonLevelEditing.readDocumentBytes(input)

    fun readSaveBytes(input: java.io.InputStream): ByteArray =
        LetsGoPokemonLevelEditing.readSaveBytes(input)

    fun sha256(bytes: ByteArray): String = LetsGoPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        storageIndex: Int,
        requestedSpeedIv: Int
    ): LetsGoSpeedDvCopyPlan = LetsGoPokemonLevelEditing.stageSpeedDv(
        source, expectedSha256, storageIndex, requestedSpeedIv
    )
}

/** Gen 1 Special DV mapped to Let's Go's paired Special Attack and Special Defense IVs. */
object LetsGoPokemonSpecialDvEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        LetsGoPokemonLevelEditing.readDocumentBytes(input)

    fun readSaveBytes(input: java.io.InputStream): ByteArray =
        LetsGoPokemonLevelEditing.readSaveBytes(input)

    fun sha256(bytes: ByteArray): String = LetsGoPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        storageIndex: Int,
        requestedSpecialIv: Int
    ): LetsGoSpecialDvCopyPlan = LetsGoPokemonLevelEditing.stageSpecialDv(
        source, expectedSha256, storageIndex, requestedSpecialIv
    )
}

class LetsGoExperienceCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val storageIndex: Int,
    val recordOffset: Int,
    val before: LetsGoPokemonRead,
    val after: LetsGoLevelValues,
    val maximumExperience: Long
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LetsGoPokemonExperienceEditing.sha256(this.original)
    val editedSha256: String = LetsGoPokemonExperienceEditing.sha256(this.edited)
    private var consumed = false

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
            LetsGoPokemonLevelEditing.verify(original, edited, storageIndex, after)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Experience edit."
            }
            LetsGoPokemonLevelEditing.verify(original, persisted, storageIndex, after)
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

class LetsGoLevelCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val storageIndex: Int,
    val recordOffset: Int,
    val before: LetsGoPokemonRead,
    val after: LetsGoLevelValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LetsGoPokemonLevelEditing.sha256(this.original)
    val editedSha256: String = LetsGoPokemonLevelEditing.sha256(this.edited)
    private var consumed = false

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
            LetsGoPokemonLevelEditing.verify(original, edited, storageIndex, after)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed Level edit." }
            LetsGoPokemonLevelEditing.verify(original, persisted, storageIndex, after)
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

class LetsGoAttackDvCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val storageIndex: Int,
    val recordOffset: Int,
    val before: LetsGoPokemonRead,
    val after: LetsGoAttackDvValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LetsGoPokemonAttackDvEditing.sha256(this.original)
    val editedSha256: String = LetsGoPokemonAttackDvEditing.sha256(this.edited)
    private var consumed = false

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
            LetsGoPokemonLevelEditing.verifyAttackDv(original, edited, storageIndex, after)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Attack DV / IV edit."
            }
            LetsGoPokemonLevelEditing.verifyAttackDv(original, persisted, storageIndex, after)
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

class LetsGoDefenseDvCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val storageIndex: Int,
    val recordOffset: Int,
    val before: LetsGoPokemonRead,
    val after: LetsGoDefenseDvValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LetsGoPokemonDefenseDvEditing.sha256(this.original)
    val editedSha256: String = LetsGoPokemonDefenseDvEditing.sha256(this.edited)
    private var consumed = false

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
            LetsGoPokemonLevelEditing.verifyDefenseDv(original, edited, storageIndex, after)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Defense DV / IV edit."
            }
            LetsGoPokemonLevelEditing.verifyDefenseDv(original, persisted, storageIndex, after)
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

class LetsGoSpeedDvCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val storageIndex: Int,
    val recordOffset: Int,
    val before: LetsGoPokemonRead,
    val after: LetsGoSpeedDvValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LetsGoPokemonSpeedDvEditing.sha256(this.original)
    val editedSha256: String = LetsGoPokemonSpeedDvEditing.sha256(this.edited)
    private var consumed = false

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
            LetsGoPokemonLevelEditing.verifySpeedDv(original, edited, storageIndex, after)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Speed DV / IV edit."
            }
            LetsGoPokemonLevelEditing.verifySpeedDv(original, persisted, storageIndex, after)
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

class LetsGoSpecialDvCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val storageIndex: Int,
    val recordOffset: Int,
    val before: LetsGoPokemonRead,
    val after: LetsGoSpecialDvValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LetsGoPokemonSpecialDvEditing.sha256(this.original)
    val editedSha256: String = LetsGoPokemonSpecialDvEditing.sha256(this.edited)
    private var consumed = false

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
            LetsGoPokemonLevelEditing.verifySpecialDv(original, edited, storageIndex, after)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Special DV / IV edit."
            }
            LetsGoPokemonLevelEditing.verifySpecialDv(original, persisted, storageIndex, after)
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

