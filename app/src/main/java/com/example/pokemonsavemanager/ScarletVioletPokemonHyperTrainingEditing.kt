package com.example.pokemonsavemanager

/** Adds one legal native PK9 Hyper Training flag and rebuilds its affected cached battle values. */
object ScarletVioletPokemonHyperTrainingEditing {
    private const val STORED_END = 0x148
    private const val CURRENT_HP = 0x8A
    private const val HYPER_TRAIN_FLAGS = 0x126
    private const val MAXIMUM_HP = 0x14A
    private const val PARTY_VALUES_END = 0x156
    private val bitByStat = intArrayOf(0, 1, 2, 5, 3, 4)
    private val names = listOf("HP", "Attack", "Defense", "Speed", "Sp. Attack", "Sp. Defense")
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        ScarletVioletPokemonNicknameEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = ScarletVioletPokemonNicknameEditing.sha256(bytes)

    fun statName(index: Int): String = names.getOrElse(index) { "Unknown stat" }

    fun eligibleStats(pokemon: ScarletVioletPokemonRead): List<Int> {
        if (pokemon.isEgg || pokemon.cachedLevel !in 50..100 || pokemon.hyperTrainingFlags !in 0..0x3F)
            return emptyList()
        return names.indices.filter { index ->
            val iv = pokemon.ivs.getOrNull(index)
            iv != null && iv in 0..30 &&
                pokemon.hyperTrainingFlags and (1 shl bitByStat[index]) == 0
        }
    }

    fun summary(flags: Int): String {
        if (flags !in 0..0x3F) return "Unusual flags 0x${flags.toString(16).uppercase()}"
        val trained = names.indices.filter { flags and (1 shl bitByStat[it]) != 0 }.map(::statName)
        return if (trained.isEmpty()) "None" else trained.joinToString(", ")
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int,
        requestedStat: Int
    ): ScarletVioletHyperTrainingCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(requestedStat in names.indices) { "Choose one supported battle stat." }
        val blocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, box, position)
        val before = (ScarletVioletPokemonReadOnly.read(target) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK9 is not checksum-valid.")
        require(!before.isEgg) { "Egg Hyper Training is not supported." }
        require(before.hyperTrainingFlags in 0..0x3F) {
            "The saved Hyper Training byte has unsupported bits and remains unchanged."
        }
        require(before.cachedLevel >= 50) {
            "Scarlet/Violet Hyper Training requires Level 50 or higher."
        }
        require(before.ivs[requestedStat] in 0..30) {
            "${statName(requestedStat)} already has a perfect native IV and cannot be Hyper Trained."
        }
        val requestedBit = 1 shl bitByStat[requestedStat]
        require(before.hyperTrainingFlags and requestedBit == 0) {
            "${statName(requestedStat)} is already Hyper Trained."
        }
        val afterFlags = before.hyperTrainingFlags or requestedBit
        val afterValues = ScarletVioletPokemonLevelEditing.verifiedValuesForHyperTraining(before, afterFlags)
        val afterRecord = editRecord(target, afterFlags, afterValues)
        val written = if (area == ScarletVioletPokemonArea.BOX) {
            ScarletVioletSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else ScarletVioletSaveValidation.writePartyRecord(original, position, afterRecord)
        verify(
            original, written.bytes, area, box, position, written.absoluteOffset,
            requestedStat, afterFlags, afterValues
        )
        return ScarletVioletHyperTrainingCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset,
            before, requestedStat, afterFlags, afterValues
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        expectedStat: Int,
        expectedFlags: Int,
        expectedValues: ScarletVioletLevelValues
    ) {
        val beforeBlocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val afterBlocks = ScarletVioletSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The Hyper Training edit changed title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val beforePokemon = (ScarletVioletPokemonReadOnly.read(beforeRecord) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PK9 no longer reopens as a Pokémon.")
        val afterPokemon = (ScarletVioletPokemonReadOnly.read(afterRecord) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PK9 did not reopen as a Pokémon.")
        require(afterPokemon.hyperTrainingFlags == expectedFlags &&
            afterPokemon.currentHp == expectedValues.currentHp &&
            afterPokemon.maximumHp == expectedValues.maximumHp &&
            afterPokemon.cachedStats == listOf(
                expectedValues.attack, expectedValues.defense, expectedValues.speed,
                expectedValues.specialAttack, expectedValues.specialDefense
            ) &&
            afterPokemon.copy(
                hyperTrainingFlags = beforePokemon.hyperTrainingFlags,
                currentHp = beforePokemon.currentHp,
                maximumHp = beforePokemon.maximumHp,
                cachedStats = beforePokemon.cachedStats
            ) == beforePokemon) {
            "The persisted PK9 changed more than one Hyper Training flag and its derived battle values."
        }
        val bit = 1 shl bitByStat[expectedStat]
        require(expectedFlags == (beforePokemon.hyperTrainingFlags or bit)) {
            "The requested Hyper Training flag was not added exactly."
        }

        val relativeOffset = if (area == ScarletVioletPokemonArea.BOX)
            ((box - 1) * 30 + position - 1) * ScarletVioletPokemonReadOnly.RECORD_SIZE
        else (position - 1) * ScarletVioletPokemonReadOnly.RECORD_SIZE
        val beforeTargetBlock = if (area == ScarletVioletPokemonArea.BOX) beforeBlocks.boxes else beforeBlocks.party
        val afterTargetBlock = if (area == ScarletVioletPokemonArea.BOX) afterBlocks.boxes else afterBlocks.party
        val beforeOtherBlock = if (area == ScarletVioletPokemonArea.BOX) beforeBlocks.party else beforeBlocks.boxes
        val afterOtherBlock = if (area == ScarletVioletPokemonArea.BOX) afterBlocks.party else afterBlocks.boxes
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) {
            "The unselected native Pokémon storage object changed."
        }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + ScarletVioletPokemonReadOnly.RECORD_SIZE ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(byte(afterPlain, HYPER_TRAIN_FLAGS) == expectedFlags) {
            "The native Hyper Training flags were not persisted exactly."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in CURRENT_HP..CURRENT_HP + 1 ||
                offset == HYPER_TRAIN_FLAGS || offset in MAXIMUM_HP until PARTY_VALUES_END ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PK9 edit exceeded Hyper Training, derived battle values and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + ScarletVioletPokemonReadOnly.RECORD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    private fun targetRecord(
        blocks: ScarletVioletPokemonBlocks,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int
    ): ByteArray {
        val size = ScarletVioletPokemonReadOnly.RECORD_SIZE
        val source: ByteArray
        val offset: Int
        if (area == ScarletVioletPokemonArea.BOX) {
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

    private fun editRecord(
        record: ByteArray,
        flags: Int,
        values: ScarletVioletLevelValues
    ): ByteArray {
        val plain = decryptAndUnshuffle(record)
        plain[HYPER_TRAIN_FLAGS] = flags.toByte()
        writeU16(plain, CURRENT_HP, values.currentHp)
        writeU16(plain, MAXIMUM_HP, values.maximumHp)
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
        require(record.size == ScarletVioletPokemonReadOnly.RECORD_SIZE) {
            "Expected one complete native PK9 record."
        }
        val ec = u32(record, 0)
        val decoded = record.copyOf()
        xorWords(decoded, 8, STORED_END, ec)
        xorWords(decoded, STORED_END, ScarletVioletPokemonReadOnly.RECORD_SIZE, ec)
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
        xorWords(shuffled, STORED_END, ScarletVioletPokemonReadOnly.RECORD_SIZE, ec)
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
}

class ScarletVioletHyperTrainingCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: ScarletVioletPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: ScarletVioletPokemonRead,
    val requestedStat: Int,
    val afterFlags: Int,
    val afterValues: ScarletVioletLevelValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = ScarletVioletPokemonHyperTrainingEditing.sha256(this.edited)
    private var consumed = false
    val location: String get() = if (area == ScarletVioletPokemonArea.BOX)
        "Box $box • Slot $position" else "Party $position"

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This Hyper Training review was already used. Reopen Pokémon Boxes." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) {
            "Choose a new empty document; existing files cannot be replaced."
        }
        require(retainBackup(original.copyOf()).contentEquals(original)) {
            "The recovery backup did not verify. Nothing was written."
        }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            ScarletVioletPokemonHyperTrainingEditing.verify(
                original, edited, area, box, position, absoluteOffset,
                requestedStat, afterFlags, afterValues
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Hyper Training edit."
            }
            ScarletVioletPokemonHyperTrainingEditing.verify(
                original, persisted, area, box, position, absoluteOffset,
                requestedStat, afterFlags, afterValues
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Hyper Training copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
