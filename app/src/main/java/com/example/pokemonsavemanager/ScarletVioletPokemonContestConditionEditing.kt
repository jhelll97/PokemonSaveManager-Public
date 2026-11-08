package com.example.pokemonsavemanager

/** Changes only the six native PK9 contest-condition bytes in a reviewed new Scarlet/Violet save copy. */
object ScarletVioletPokemonContestConditionEditing {
    private const val STORED_END = 0x148
    private const val CONTEST_START = 0x2C
    private const val CONTEST_COUNT = 6
    private val labels = listOf("Cool", "Beauty", "Cute", "Smart", "Tough", "Sheen")
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        ScarletVioletPokemonNicknameEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = ScarletVioletPokemonNicknameEditing.sha256(bytes)

    fun label(index: Int): String = labels.getOrElse(index) { "Value ${index + 1}" }

    fun isValid(pokemon: ScarletVioletPokemonRead): Boolean =
        pokemon.contestStats.size == CONTEST_COUNT && pokemon.contestStats.all { it in 0..255 }

    fun describe(stats: List<Int>): String =
        labels.indices.joinToString(" • ") { "${labels[it]} ${stats.getOrElse(it) { -1 }}" }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int,
        requestedStats: List<Int>
    ): ScarletVioletContestConditionCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(requestedStats.size == CONTEST_COUNT && requestedStats.all { it in 0..255 }) {
            "Each contest-condition value must be 0 through 255."
        }
        val blocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val targetRecord = targetRecord(blocks, area, box, position)
        val before = (ScarletVioletPokemonReadOnly.read(targetRecord) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK9 is not checksum-valid.")
        require(!before.isEgg) { "Egg contest-condition data remains protected." }
        require(!before.fatefulEncounter) {
            "Fateful-event contest-condition data remains protected because distribution history is not inferred."
        }
        require(isValid(before)) { "The saved contest-condition data is unsupported and cannot be changed safely." }
        require(requestedStats != before.contestStats) {
            "Change at least one contest-condition value before review."
        }
        val afterRecord = editRecord(targetRecord, requestedStats)
        val written = if (area == ScarletVioletPokemonArea.BOX) {
            ScarletVioletSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else ScarletVioletSaveValidation.writePartyRecord(original, position, afterRecord)
        verify(
            original, written.bytes, area, box, position, written.absoluteOffset, requestedStats
        )
        return ScarletVioletContestConditionCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset, before, requestedStats
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        expectedStats: List<Int>
    ) {
        require(expectedStats.size == CONTEST_COUNT && expectedStats.all { it in 0..255 }) {
            "The reviewed contest-condition values are unsupported."
        }
        val beforeBlocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val afterBlocks = ScarletVioletSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The contest-condition edit changed title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val beforePokemon = (ScarletVioletPokemonReadOnly.read(beforeRecord) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PK9 no longer reopens as a Pokémon.")
        val afterPokemon = (ScarletVioletPokemonReadOnly.read(afterRecord) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PK9 did not reopen as a Pokémon.")
        require(!beforePokemon.isEgg && !beforePokemon.fatefulEncounter && isValid(beforePokemon)) {
            "The reviewed PK9 is no longer eligible for contest-condition editing."
        }
        require(afterPokemon.contestStats == expectedStats &&
            afterPokemon.copy(contestStats = beforePokemon.contestStats) == beforePokemon) {
            "The persisted PK9 changed more than its contest-condition values."
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
        require((0 until CONTEST_COUNT).all { index ->
            byte(afterPlain, CONTEST_START + index) == expectedStats[index]
        }) { "The native contest-condition bytes were not written exactly." }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in CONTEST_START until CONTEST_START + CONTEST_COUNT ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PK9 edit exceeded contest-condition and checksum fields." }

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



    private fun editRecord(record: ByteArray, stats: List<Int>): ByteArray {
        val plain = decryptAndUnshuffle(record)
        for (index in 0 until CONTEST_COUNT) plain[CONTEST_START + index] = stats[index].toByte()
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

class ScarletVioletContestConditionCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: ScarletVioletPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: ScarletVioletPokemonRead,
    stats: List<Int>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val stats: List<Int> = stats.toList()
    val editedSha256: String = ScarletVioletPokemonContestConditionEditing.sha256(this.edited)
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
        check(!consumed) { "This contest-condition review was already used. Reopen Pokémon Boxes." }
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
            ScarletVioletPokemonContestConditionEditing.verify(
                original, edited, area, box, position, absoluteOffset, stats
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed contest-condition edit."
            }
            ScarletVioletPokemonContestConditionEditing.verify(
                original, persisted, area, box, position, absoluteOffset, stats
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Contest-condition copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
