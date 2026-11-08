package com.example.pokemonsavemanager

/** Changes only the six native PK9 Effort Value bytes in a reviewed new Scarlet/Violet save copy. */
object ScarletVioletPokemonEffortValueEditing {
    private const val STORED_END = 0x148
    private const val EV_START = 0x26
    private const val EV_COUNT = 6
    private const val MAX_PER_STAT = 252
    private const val MAX_TOTAL = 510
    private val labels = listOf("HP", "Attack", "Defense", "Speed", "Special Attack", "Special Defense")
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        ScarletVioletPokemonNicknameEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = ScarletVioletPokemonNicknameEditing.sha256(bytes)

    fun label(index: Int): String = labels.getOrElse(index) { "Value ${index + 1}" }

    fun isReadable(pokemon: ScarletVioletPokemonRead): Boolean =
        pokemon.evs.size == EV_COUNT && pokemon.evs.all { it in 0..255 }

    fun isValid(pokemon: ScarletVioletPokemonRead): Boolean =
        isReadable(pokemon) && pokemon.evs.all { it in 0..MAX_PER_STAT } &&
            pokemon.evs.sum() <= MAX_TOTAL

    fun describe(evs: List<Int>): String =
        labels.indices.joinToString(" • ") { "${labels[it]} ${evs.getOrElse(it) { -1 }}" } +
            " • Total ${evs.sum()}/$MAX_TOTAL"

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int,
        requestedEvs: List<Int>
    ): ScarletVioletEffortValueCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(requestedEvs.size == EV_COUNT && requestedEvs.all { it in 0..MAX_PER_STAT }) {
            "Each Effort Value must be 0 through 252."
        }
        require(requestedEvs.sum() <= MAX_TOTAL) {
            "The six Effort Values must total 510 or less."
        }
        val blocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val targetRecord = targetRecord(blocks, area, box, position)
        val before = (ScarletVioletPokemonReadOnly.read(targetRecord) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK9 is not checksum-valid.")
        require(!before.isEgg) { "Egg effort-value data remains protected." }
        require(!before.fatefulEncounter) {
            "Fateful-event effort-value data remains protected because distribution history is not inferred."
        }
        require(isReadable(before)) { "The six saved Effort Value bytes could not be read exactly." }
        require(requestedEvs != before.evs) {
            "Change at least one Effort Value before review."
        }
        val afterRecord = editRecord(targetRecord, requestedEvs)
        val written = if (area == ScarletVioletPokemonArea.BOX) {
            ScarletVioletSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else ScarletVioletSaveValidation.writePartyRecord(original, position, afterRecord)
        verify(
            original, written.bytes, area, box, position, written.absoluteOffset, requestedEvs
        )
        return ScarletVioletEffortValueCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset, before, requestedEvs
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        expectedEvs: List<Int>
    ) {
        require(expectedEvs.size == EV_COUNT && expectedEvs.all { it in 0..MAX_PER_STAT } &&
            expectedEvs.sum() <= MAX_TOTAL) {
            "The reviewed Effort Values exceed the Scarlet/Violet per-stat or total limit."
        }
        val beforeBlocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val afterBlocks = ScarletVioletSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The effort-value edit changed title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val beforePokemon = (ScarletVioletPokemonReadOnly.read(beforeRecord) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PK9 no longer reopens as a Pokémon.")
        val afterPokemon = (ScarletVioletPokemonReadOnly.read(afterRecord) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PK9 did not reopen as a Pokémon.")
        require(!beforePokemon.isEgg && !beforePokemon.fatefulEncounter && isReadable(beforePokemon)) {
            "The reviewed PK9 is no longer eligible for Effort Value editing."
        }
        require(isValid(afterPokemon) && afterPokemon.evs == expectedEvs &&
            afterPokemon.copy(evs = beforePokemon.evs) == beforePokemon) {
            "The persisted PK9 changed more than its Effort Values."
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
        require((0 until EV_COUNT).all { index ->
            byte(afterPlain, EV_START + index) == expectedEvs[index]
        }) { "The native Effort Value bytes were not written exactly." }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in EV_START until EV_START + EV_COUNT ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PK9 edit exceeded Effort Value and checksum fields." }

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



    private fun editRecord(record: ByteArray, evs: List<Int>): ByteArray {
        val plain = decryptAndUnshuffle(record)
        for (index in 0 until EV_COUNT) plain[EV_START + index] = evs[index].toByte()
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

class ScarletVioletEffortValueCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: ScarletVioletPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: ScarletVioletPokemonRead,
    evs: List<Int>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val evs: List<Int> = evs.toList()
    val editedSha256: String = ScarletVioletPokemonEffortValueEditing.sha256(this.edited)
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
        check(!consumed) { "This effort-value review was already used. Reopen Pokémon Boxes." }
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
            ScarletVioletPokemonEffortValueEditing.verify(
                original, edited, area, box, position, absoluteOffset, evs
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed effort-value edit."
            }
            ScarletVioletPokemonEffortValueEditing.verify(
                original, persisted, area, box, position, absoluteOffset, evs
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Effort Value copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
