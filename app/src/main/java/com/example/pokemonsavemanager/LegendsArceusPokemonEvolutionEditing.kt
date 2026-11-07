package com.example.pokemonsavemanager

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

data class LegendsArceusEvolutionChoice(val species: Int, val method: String)

data class LegendsArceusEvolutionValues(
    val choice: LegendsArceusEvolutionChoice,
    val level: Int,
    val currentHp: Int,
    val maximumHp: Int,
    val cachedStats: List<Int>
)

/** Applies the shared Gen 1 evolution action to one native Legends: Arceus PA8. */
object LegendsArceusPokemonEvolutionEditing {
    private const val STORED_END = 0x168
    private const val SPECIES = 0x08
    private const val CURRENT_HP = 0x92
    private const val MAXIMUM_HP = 0x16A
    private const val PARTY_VALUES = 0x16A
    private const val PARTY_VALUES_END = 0x176
    private const val BLOCK_SIZE = 88
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun choices(species: Int): List<LegendsArceusEvolutionChoice> =
        LetsGoPokemonEvolutionEditing.choices(species).mapNotNull { choice ->
            choice.takeIf {
                it.species in LegendsArceusPokemonSpeciesEditing.supportedSpecies &&
                    LegendsArceusPersonalCatalogue.profiles(it.species, 0)
                        .distinctBy { profile -> profileKey(profile) }.size == 1
            }?.let { LegendsArceusEvolutionChoice(it.species, it.method) }
        }

    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        LegendsArceusPokemonNicknameEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = LegendsArceusPokemonNicknameEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        targetSpecies: Int
    ): LegendsArceusEvolutionCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, pasture, position)
        val before = (LegendsArceusPokemonReadOnly.read(target) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA8 is not checksum-valid.")
        require(!before.isEgg) { "Egg PA8 records cannot be evolved." }
        require(before.form == 0) {
            "Alternate-form PA8 records remain protected from base-form evolution."
        }
        require(!before.isNoble) { "Noble PA8 records remain protected from species evolution." }
        val choice = choices(before.species).singleOrNull { it.species == targetSpecies }
            ?: throw IllegalArgumentException("Choose one supported shared Gen 1 evolution of this Pokémon.")
        require(before.nature in 0..24 && before.statNature in 0..24 &&
            before.currentHandler in 0..1 && before.ivs.all { it in 0..31 } &&
            before.ganbaruValues.all { it in 0..10 } &&
            (before.hyperTrainingFlags and 0xC0) == 0) {
            "The selected PA8 contains values outside verified Legends: Arceus native limits."
        }
        val sourcePersonal = resolveSourcePersonal(before, area)
        val targetPersonal = resolveTargetPersonal(choice.species)
        val level = levelForExperience(before.experience, sourcePersonal.growth)
        require(before.experience in 0L..minimumExperience(100, sourcePersonal.growth) &&
            before.experience <= minimumExperience(100, targetPersonal.growth) &&
            levelForExperience(before.experience, targetPersonal.growth) == level &&
            (before.cachedLevel == null || before.cachedLevel == level)) {
            "The saved Level and EXP do not agree with both verified evolution profiles."
        }
        val current = calculate(before, sourcePersonal, before.species, level)
        require(matches(before, current, area)) {
            "The saved battle values do not match the current verified species profile."
        }
        val recalculated = calculate(before, targetPersonal, choice.species, level)
        val after = LegendsArceusEvolutionValues(
            choice, level, recalculated.currentHp, recalculated.maximumHp,
            recalculated.cachedStats
        )
        val afterRecord = editRecord(target, after)
        val written = if (area == LegendsArceusPokemonArea.PASTURE) {
            LegendsArceusSaveValidation.writePastureRecord(original, pasture, position, afterRecord)
        } else LegendsArceusSaveValidation.writePartyRecord(original, position, afterRecord)
        verify(original, written.bytes, area, pasture, position, written.absoluteOffset, after)
        return LegendsArceusEvolutionCopyPlan(
            original, written.bytes, area, pasture, position, written.absoluteOffset, before, after
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        absoluteOffset: Int,
        expected: LegendsArceusEvolutionValues
    ) {
        val beforeBlocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsArceusSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The evolved copy changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, pasture, position)
        val afterRecord = targetRecord(afterBlocks, area, pasture, position)
        val before = (LegendsArceusPokemonReadOnly.read(beforeRecord) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PA8 no longer reopens as a Pokémon.")
        val after = (LegendsArceusPokemonReadOnly.read(afterRecord) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The evolved PA8 did not reopen as a Pokémon.")
        val partyCacheMatches = if (area == LegendsArceusPokemonArea.PARTY) {
            after.cachedLevel == expected.level && after.maximumHp == expected.maximumHp &&
                after.cachedStats == expected.cachedStats
        } else after.cachedLevel == null && after.maximumHp == null && after.cachedStats == null
        require(after.species == expected.choice.species &&
            after.currentHp == expected.currentHp && partyCacheMatches && after.copy(
                species = before.species,
                cachedLevel = before.cachedLevel,
                currentHp = before.currentHp,
                maximumHp = before.maximumHp,
                cachedStats = before.cachedStats
            ) == before) {
            "The persisted PA8 changed outside its reviewed evolution values."
        }

        val recordSize = recordSize(area)
        val relativeOffset = if (area == LegendsArceusPokemonArea.PASTURE)
            ((pasture - 1) * 30 + position - 1) * recordSize else (position - 1) * recordSize
        val beforeTargetBlock = if (area == LegendsArceusPokemonArea.PASTURE) beforeBlocks.pastures else beforeBlocks.party
        val afterTargetBlock = if (area == LegendsArceusPokemonArea.PASTURE) afterBlocks.pastures else afterBlocks.party
        val beforeOtherBlock = if (area == LegendsArceusPokemonArea.PASTURE) beforeBlocks.party else beforeBlocks.pastures
        val afterOtherBlock = if (area == LegendsArceusPokemonArea.PASTURE) afterBlocks.party else afterBlocks.pastures
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) {
            "The unselected native Pokémon storage object changed during evolution."
        }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + recordSize ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed during evolution." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in SPECIES..SPECIES + 1 ||
                offset in CURRENT_HP..CURRENT_HP + 1 ||
                (area == LegendsArceusPokemonArea.PARTY && offset in PARTY_VALUES until PARTY_VALUES_END) ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PA8 evolution exceeded species, cached battle values, and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + recordSize ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted Legends: Arceus container byte changed." }
    }

    private fun resolveSourcePersonal(
        pokemon: LegendsArceusPokemonRead,
        area: LegendsArceusPokemonArea
    ): LegendsArceusPersonalData {
        val candidates = LegendsArceusPersonalCatalogue.profiles(pokemon.species, 0)
            .distinctBy { profileKey(it) }
        val level = candidates.map { levelForExperience(pokemon.experience, it.growth) }.distinct()
        require(level.size == 1) { "The PA8 species growth group is ambiguous." }
        val matching = if (area == LegendsArceusPokemonArea.PARTY) candidates.filter {
            matches(pokemon, calculate(pokemon, it, pokemon.species, level.single()), area)
        } else candidates
        return matching.singleOrNull() ?: throw IllegalArgumentException(
            "PSE could not uniquely verify this PA8's Legends: Arceus base-stat profile."
        )
    }

    private fun resolveTargetPersonal(species: Int): LegendsArceusPersonalData {
        val candidates = LegendsArceusPersonalCatalogue.profiles(species, 0)
            .distinctBy { profileKey(it) }
        return candidates.singleOrNull() ?: throw IllegalArgumentException(
            "The evolution target has no uniquely verified default Legends: Arceus profile."
        )
    }

    private fun profileKey(data: LegendsArceusPersonalData): List<Int> = listOf(
        data.hp, data.attack, data.defense, data.speed,
        data.specialAttack, data.specialDefense, data.growth
    )

    private fun matches(
        before: LegendsArceusPokemonRead,
        expected: LegendsArceusEvolutionValues,
        area: LegendsArceusPokemonArea
    ): Boolean = before.currentHp == expected.currentHp &&
        (area != LegendsArceusPokemonArea.PARTY ||
            before.maximumHp == expected.maximumHp && before.cachedStats == expected.cachedStats)

    private fun calculate(
        before: LegendsArceusPokemonRead,
        personal: LegendsArceusPersonalData,
        species: Int,
        level: Int
    ): LegendsArceusEvolutionValues {
        require(level in 1..100) { "The derived PA8 Level is outside 1–100." }
        val effectiveIv = IntArray(6) { index ->
            val hyperBit = intArrayOf(0, 1, 2, 5, 3, 4)[index]
            if (before.hyperTrainingFlags and (1 shl hyperBit) != 0) 31 else before.ivs[index]
        }
        val base = intArrayOf(
            personal.hp, personal.attack, personal.defense, personal.speed,
            personal.specialAttack, personal.specialDefense
        )
        fun ganbaru(index: Int): Int {
            val iv = effectiveIv[index]
            val bias = when { iv >= 31 -> 3; iv >= 26 -> 2; iv >= 20 -> 1; else -> 0 }
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
        return LegendsArceusEvolutionValues(
            LegendsArceusEvolutionChoice(species, "Verified profile"),
            level, currentHp, maximumHp, stats
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
            require(pasture in 1..32 && position in 1..30) {
                "The selected native pasture slot is outside storage."
            }
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

    private fun editRecord(record: ByteArray, values: LegendsArceusEvolutionValues): ByteArray {
        val plain = decryptAndUnshuffle(record)
        writeU16(plain, SPECIES, values.choice.species)
        writeU16(plain, CURRENT_HP, values.currentHp)
        if (record.size == LegendsArceusPokemonReadOnly.PARTY_SIZE) {
            writeU16(plain, MAXIMUM_HP, values.maximumHp)
            values.cachedStats.forEachIndexed { index, value ->
                writeU16(plain, MAXIMUM_HP + 2 + index * 2, value)
            }
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
    private fun u16(bytes: ByteArray, offset: Int): Int =
        byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long =
        u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
}

class LegendsArceusEvolutionCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: LegendsArceusPokemonArea,
    val pasture: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: LegendsArceusPokemonRead,
    val after: LegendsArceusEvolutionValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = LegendsArceusPokemonEvolutionEditing.sha256(this.original)
    val editedSha256 = LegendsArceusPokemonEvolutionEditing.sha256(this.edited)
    val location: String get() = if (area == LegendsArceusPokemonArea.PASTURE)
        "Pasture $pasture • Slot $position" else "Party $position"
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This evolution review was already used. Reopen Pokémon Boxes." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) {
            "Choose a new empty document; existing files cannot be replaced."
        }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) {
            "The recovery backup did not verify. Nothing was written."
        }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LegendsArceusPokemonEvolutionEditing.verify(
                original, edited, area, pasture, position, absoluteOffset, after
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed evolution."
            }
            LegendsArceusPokemonEvolutionEditing.verify(
                original, persisted, area, pasture, position, absoluteOffset, after
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Evolution copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
