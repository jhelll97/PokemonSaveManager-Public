package com.example.pokemonsavemanager

import kotlin.math.min

data class LegendsZaLevelValues(
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

data class LegendsZaAttackDvValues(
    val attackIv: Int,
    val attack: Int
)

/** Rebuilds one PA9's native level-dependent values in a reviewed new Legends: Z-A save copy. */
object LegendsZaPokemonLevelEditing {
    private const val STORED_END = 0x148
    private const val EXPERIENCE = 0x10
    private const val IV32 = 0x8C
    private const val ATTACK_IV_SHIFT = 5
    private const val ATTACK_IV_MASK = 31L shl ATTACK_IV_SHIFT
    private const val CURRENT_HP = 0x8A
    private const val LEVEL = 0x148
    private const val PARTY_VALUES = 0x14A
    private const val PARTY_VALUES_END = 0x156
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        LegendsZaPokemonNicknameEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = LegendsZaPokemonNicknameEditing.sha256(bytes)

    internal fun maximumExperience(pokemon: LegendsZaPokemonRead): Long {
        require(pokemon.statNature in 0..24) {
            "The PA9 stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        return minimumExperience(100, resolvePersonal(pokemon).growth)
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsZaPokemonArea,
        box: Int,
        position: Int,
        requestedLevel: Int
    ): LegendsZaLevelCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(requestedLevel in 1..100) { "Level must be 1–100." }
        val blocks = LegendsZaSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, box, position)
        val before = (LegendsZaPokemonReadOnly.read(target) as? LegendsZaSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA9 is not checksum-valid.")
        require(!before.isEgg) { "Egg level editing is not supported for Legends: Z-A." }
        require(requestedLevel != before.cachedLevel) { "Enter a different level before review." }
        require(before.cachedLevel in 1..100) { "The saved PA9 Level is outside 1–100." }
        require(before.statNature in 0..24) {
            "The PA9 stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        val personal = resolvePersonal(before)
        require(levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedStats(before, calculate(before, personal, before.cachedLevel))) {
            "The saved Level-dependent stats do not match this PA9's verified species/form profile."
        }
        val after = calculate(before, personal, requestedLevel)
        val afterRecord = editRecord(target, after)
        val written = if (area == LegendsZaPokemonArea.BOX) {
            LegendsZaSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else LegendsZaSaveValidation.writePartyRecord(original, position, afterRecord)
        verify(original, written.bytes, area, box, position, written.absoluteOffset, after)
        return LegendsZaLevelCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset, before, after
        )
    }

    internal fun stageAttackDv(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsZaPokemonArea,
        box: Int,
        position: Int,
        requestedAttackIv: Int
    ): LegendsZaAttackDvCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(requestedAttackIv in 0..31) { "Attack IV must be between 0 and 31." }
        val blocks = LegendsZaSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, box, position)
        val before = (LegendsZaPokemonReadOnly.read(target) as? LegendsZaSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA9 is not checksum-valid.")
        require(!before.isEgg) { "Egg Attack DV / IV editing is not supported for Legends: Z-A." }
        require(requestedAttackIv != before.ivs[1]) { "Enter a different Attack IV before review." }
        require(before.cachedLevel in 1..100 && before.statNature in 0..24) {
            "The PA9 Level or stat nature is invalid, so derived stats cannot be rebuilt safely."
        }
        val personal = resolvePersonal(before)
        require(levelForExperience(before.experience, personal.growth) == before.cachedLevel) {
            "The saved Level and EXP do not agree with this Pokémon's verified growth group."
        }
        require(matchesCachedValues(before,
            calculate(before, personal, before.cachedLevel, before.experience))) {
            "The saved battle values do not match this PA9's verified species/form profile."
        }
        val requestedIvs = before.ivs.toMutableList().also { it[1] = requestedAttackIv }
        val recalculated = calculate(
            before.copy(ivs = requestedIvs), personal, before.cachedLevel, before.experience
        )
        val after = LegendsZaAttackDvValues(requestedAttackIv, recalculated.attack)
        val afterRecord = editAttackDvRecord(target, after)
        val written = if (area == LegendsZaPokemonArea.BOX) {
            LegendsZaSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else LegendsZaSaveValidation.writePartyRecord(original, position, afterRecord)
        verifyAttackDv(original, written.bytes, area, box, position, written.absoluteOffset, after)
        return LegendsZaAttackDvCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset, before, after
        )
    }

    internal fun stageExperience(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsZaPokemonArea,
        box: Int,
        position: Int,
        requestedExperience: Long
    ): LegendsZaExperienceCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = LegendsZaSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, box, position)
        val before = (LegendsZaPokemonReadOnly.read(target) as? LegendsZaSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA9 is not checksum-valid.")
        require(!before.isEgg) { "Egg experience editing is not supported for Legends: Z-A." }
        require(requestedExperience != before.experience) {
            "Enter a different Experience value before review."
        }
        require(before.statNature in 0..24) {
            "The PA9 stat nature is invalid, so derived stats cannot be rebuilt safely."
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
            "The saved Level-dependent values do not match this PA9's verified species/form profile."
        }
        val requestedLevel = levelForExperience(requestedExperience, personal.growth)
        val after = calculate(before, personal, requestedLevel, requestedExperience)
        val afterRecord = editRecord(target, after)
        val written = if (area == LegendsZaPokemonArea.BOX) {
            LegendsZaSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else LegendsZaSaveValidation.writePartyRecord(original, position, afterRecord)
        verify(original, written.bytes, area, box, position, written.absoluteOffset, after)
        return LegendsZaExperienceCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset, before, after,
            maximumExperience
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: LegendsZaPokemonArea,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        expected: LegendsZaLevelValues
    ) {
        val beforeBlocks = LegendsZaSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsZaSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && beforeBlocks.revision == afterBlocks.revision &&
            original.size == edited.size) {
            "The Level edit changed native title identity, revision, or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val before = (LegendsZaPokemonReadOnly.read(beforeRecord) as? LegendsZaSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PA9 no longer reopens as a Pokémon.")
        val after = (LegendsZaPokemonReadOnly.read(afterRecord) as? LegendsZaSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PA9 did not reopen as a Pokémon.")
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
            "The persisted PA9 changed outside its reviewed level-dependent values."
        }

        val relativeOffset = if (area == LegendsZaPokemonArea.BOX)
            ((box - 1) * 30 + position - 1) * LegendsZaStorageReadOnly.BOX_STRIDE
        else (position - 1) * LegendsZaStorageReadOnly.PARTY_STRIDE
        val beforeTargetBlock = if (area == LegendsZaPokemonArea.BOX) beforeBlocks.boxes else beforeBlocks.party
        val afterTargetBlock = if (area == LegendsZaPokemonArea.BOX) afterBlocks.boxes else afterBlocks.party
        val beforeOtherBlock = if (area == LegendsZaPokemonArea.BOX) beforeBlocks.party else beforeBlocks.boxes
        val afterOtherBlock = if (area == LegendsZaPokemonArea.BOX) afterBlocks.party else afterBlocks.boxes
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) {
            "The unselected native Pokémon storage object changed."
        }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + LegendsZaPokemonReadOnly.RECORD_SIZE ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in EXPERIENCE..EXPERIENCE + 3 ||
                offset in CURRENT_HP..CURRENT_HP + 1 || offset == LEVEL ||
                offset in PARTY_VALUES until PARTY_VALUES_END || beforePlain[offset] == afterPlain[offset]
        }) { "The PA9 edit exceeded EXP, Level, current HP, cached battle stats, and checksum." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + LegendsZaPokemonReadOnly.RECORD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    internal fun verifyAttackDv(
        original: ByteArray,
        edited: ByteArray,
        area: LegendsZaPokemonArea,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        expected: LegendsZaAttackDvValues
    ) {
        val beforeBlocks = LegendsZaSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsZaSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && beforeBlocks.revision == afterBlocks.revision &&
            original.size == edited.size) {
            "The Attack DV / IV edit changed native title identity, revision, or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val before = (LegendsZaPokemonReadOnly.read(beforeRecord) as? LegendsZaSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PA9 no longer reopens as a Pokémon.")
        val after = (LegendsZaPokemonReadOnly.read(afterRecord) as? LegendsZaSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PA9 did not reopen as a Pokémon.")
        require(after.ivs[1] == expected.attackIv &&
            after.ivs.indices.filter { it != 1 }.all { after.ivs[it] == before.ivs[it] } &&
            after.cachedStats[0] == expected.attack &&
            after.cachedStats.indices.filter { it != 0 }.all { after.cachedStats[it] == before.cachedStats[it] } &&
            after.copy(ivs = before.ivs, cachedStats = before.cachedStats) == before) {
            "The persisted PA9 changed outside Attack IV and its derived Attack value."
        }

        val relativeOffset = if (area == LegendsZaPokemonArea.BOX)
            ((box - 1) * 30 + position - 1) * LegendsZaStorageReadOnly.BOX_STRIDE
        else (position - 1) * LegendsZaStorageReadOnly.PARTY_STRIDE
        val beforeTargetBlock = if (area == LegendsZaPokemonArea.BOX) beforeBlocks.boxes else beforeBlocks.party
        val afterTargetBlock = if (area == LegendsZaPokemonArea.BOX) afterBlocks.boxes else afterBlocks.party
        val beforeOtherBlock = if (area == LegendsZaPokemonArea.BOX) beforeBlocks.party else beforeBlocks.boxes
        val afterOtherBlock = if (area == LegendsZaPokemonArea.BOX) afterBlocks.party else afterBlocks.boxes
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) {
            "The unselected native Pokémon storage object changed."
        }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + LegendsZaPokemonReadOnly.RECORD_SIZE ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot or the target slot footer changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        val preserveIvMask = 0xFFFFFFFFL xor ATTACK_IV_MASK
        require((u32(beforePlain, IV32) and preserveIvMask) ==
            (u32(afterPlain, IV32) and preserveIvMask)) {
            "A non-Attack IV or PA9 identity bit changed."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in IV32 until IV32 + 4 ||
                offset in 0x14C until 0x14E || beforePlain[offset] == afterPlain[offset]
        }) { "The PA9 edit exceeded Attack IV, derived Attack, and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + LegendsZaPokemonReadOnly.RECORD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    private fun resolvePersonal(pokemon: LegendsZaPokemonRead): LegendsZaPersonalData =
        LegendsZaPersonalCatalogue.profile(pokemon.species, pokemon.form)
            ?: throw IllegalArgumentException(
                "Verified Legends: Z-A personal data is unavailable for this species/form."
            )

    private fun matchesCachedStats(pokemon: LegendsZaPokemonRead, values: LegendsZaLevelValues): Boolean =
        pokemon.maximumHp == values.maximumHp &&
            pokemon.cachedStats == listOf(values.attack, values.defense, values.speed,
                values.specialAttack, values.specialDefense)

    private fun matchesCachedValues(
        pokemon: LegendsZaPokemonRead,
        values: LegendsZaLevelValues
    ): Boolean = matchesCachedStats(pokemon, values) && pokemon.currentHp == values.currentHp

    private fun calculate(
        before: LegendsZaPokemonRead,
        personal: LegendsZaPersonalData,
        level: Int,
        experience: Long = minimumExperience(level, personal.growth)
    ): LegendsZaLevelValues {
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
        return LegendsZaLevelValues(
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
            else -> error("Unsupported native Legends: Z-A growth group.")
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
        blocks: LegendsZaPokemonBlocks,
        area: LegendsZaPokemonArea,
        box: Int,
        position: Int
    ): ByteArray {
        val size = LegendsZaPokemonReadOnly.RECORD_SIZE
        val source: ByteArray
        val offset: Int
        if (area == LegendsZaPokemonArea.BOX) {
            require(box in 1..32 && position in 1..30) {
                "The selected native PC-box slot is outside storage."
            }
            source = blocks.boxes
            offset = ((box - 1) * 30 + position - 1) * LegendsZaStorageReadOnly.BOX_STRIDE
        } else {
            require(position in 1..6) { "The selected native party slot is outside the six-slot party." }
            source = blocks.party
            offset = (position - 1) * LegendsZaStorageReadOnly.PARTY_STRIDE
            require(LegendsZaStorageReadOnly.hasNativePresence(source, offset)) {
                "The selected native party slot is not occupied."
            }
        }
        return source.copyOfRange(offset, offset + size)
    }

    private fun editRecord(record: ByteArray, values: LegendsZaLevelValues): ByteArray {
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

    private fun editAttackDvRecord(record: ByteArray, values: LegendsZaAttackDvValues): ByteArray {
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

    private fun decryptAndUnshuffle(record: ByteArray): ByteArray {
        require(record.size == LegendsZaPokemonReadOnly.RECORD_SIZE) {
            "Expected one complete native PA9 record."
        }
        val ec = u32(record, 0)
        val decoded = record.copyOf()
        xorWords(decoded, 8, STORED_END, ec)
        xorWords(decoded, STORED_END, LegendsZaPokemonReadOnly.RECORD_SIZE, ec)
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
        xorWords(shuffled, STORED_END, LegendsZaPokemonReadOnly.RECORD_SIZE, ec)
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

/** Gen 1-ordered Experience action backed by the verified PA9 level-dependent write scope. */
object LegendsZaPokemonExperienceEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        LegendsZaPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = LegendsZaPokemonLevelEditing.sha256(bytes)

    fun maximumExperience(pokemon: LegendsZaPokemonRead): Long =
        LegendsZaPokemonLevelEditing.maximumExperience(pokemon)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsZaPokemonArea,
        box: Int,
        position: Int,
        requestedExperience: Long
    ): LegendsZaExperienceCopyPlan = LegendsZaPokemonLevelEditing.stageExperience(
        source, expectedSha256, area, box, position, requestedExperience
    )
}

/** Gen 1-ordered Attack DV action mapped to Legends: Z-A's native Attack IV. */
object LegendsZaPokemonAttackDvEditing {
    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        LegendsZaPokemonLevelEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = LegendsZaPokemonLevelEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsZaPokemonArea,
        box: Int,
        position: Int,
        requestedAttackIv: Int
    ): LegendsZaAttackDvCopyPlan = LegendsZaPokemonLevelEditing.stageAttackDv(
        source, expectedSha256, area, box, position, requestedAttackIv
    )
}

class LegendsZaAttackDvCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: LegendsZaPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: LegendsZaPokemonRead,
    val after: LegendsZaAttackDvValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = LegendsZaPokemonAttackDvEditing.sha256(this.original)
    val editedSha256 = LegendsZaPokemonAttackDvEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == LegendsZaPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

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
            LegendsZaPokemonLevelEditing.verifyAttackDv(
                original, edited, area, box, position, absoluteOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Attack DV / IV edit."
            }
            LegendsZaPokemonLevelEditing.verifyAttackDv(
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

class LegendsZaExperienceCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: LegendsZaPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: LegendsZaPokemonRead,
    val after: LegendsZaLevelValues,
    val maximumExperience: Long
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = LegendsZaPokemonExperienceEditing.sha256(this.original)
    val editedSha256 = LegendsZaPokemonExperienceEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == LegendsZaPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

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
            LegendsZaPokemonLevelEditing.verify(
                original, edited, area, box, position, absoluteOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Experience edit."
            }
            LegendsZaPokemonLevelEditing.verify(
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

class LegendsZaLevelCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: LegendsZaPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: LegendsZaPokemonRead,
    val after: LegendsZaLevelValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = LegendsZaPokemonLevelEditing.sha256(this.original)
    val editedSha256 = LegendsZaPokemonLevelEditing.sha256(this.edited)
    private var consumed = false
    val location: String
        get() = if (area == LegendsZaPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

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
            LegendsZaPokemonLevelEditing.verify(
                original, edited, area, box, position, absoluteOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Level edit."
            }
            LegendsZaPokemonLevelEditing.verify(
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
