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

/** Rebuilds one PK8's native level-dependent values in a reviewed new Sword/Shield save copy. */
object SwordShieldPokemonLevelEditing {
    private const val STORED_END = 0x148
    private const val EXPERIENCE = 0x10
    private const val CURRENT_HP = 0x8A
    private const val LEVEL = 0x148
    private const val PARTY_VALUES = 0x14A
    private const val PARTY_VALUES_END = 0x156
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        SwordShieldPokemonNicknameEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = SwordShieldPokemonNicknameEditing.sha256(bytes)

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

    private fun calculate(
        before: SwordShieldPokemonRead,
        personal: SwordShieldPersonalData,
        level: Int
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
            level, minimumExperience(level, personal.growth), currentHp, maximumHp,
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
