package com.example.pokemonsavemanager

/** Changes only the native PK9 egg-date triplet in a reviewed new Scarlet/Violet save copy. */
object ScarletVioletPokemonEggDateEditing {
    private const val STORED_END = 0x148
    private const val EGG_YEAR = 0x119
    private const val EGG_MONTH = 0x11A
    private const val EGG_DAY = 0x11B
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        ScarletVioletPokemonNicknameEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = ScarletVioletPokemonNicknameEditing.sha256(bytes)

    fun eggDate(pokemon: ScarletVioletPokemonRead): String? {
        val year = 2000 + pokemon.eggYear
        if (!isValidDate(year, pokemon.eggMonth, pokemon.eggDay)) return null
        return formatDate(year, pokemon.eggMonth, pokemon.eggDay)
    }

    fun parseDate(value: String): Triple<Int, Int, Int> {
        val match = Regex("^([0-9]{4})-([0-9]{2})-([0-9]{2})$").matchEntire(value.trim())
            ?: throw IllegalArgumentException("Egg Date must use YYYY-MM-DD.")
        val year = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val day = match.groupValues[3].toInt()
        require(year in 2000..2255 && isValidDate(year, month, day)) {
            "Enter a real Egg Date from 2000-01-01 through 2255-12-31."
        }
        return Triple(year, month, day)
    }

    private fun isValidDate(year: Int, month: Int, day: Int): Boolean {
        if (year !in 2000..2255 || month !in 1..12 || day < 1) return false
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val maximum = when (month) {
            2 -> if (leap) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return day <= maximum
    }

    private fun formatDate(year: Int, month: Int, day: Int): String =
        year.toString().padStart(4, '0') + "-" + month.toString().padStart(2, '0') +
            "-" + day.toString().padStart(2, '0')

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int,
        requestedDate: String
    ): ScarletVioletEggDateCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val (year, month, day) = parseDate(requestedDate)
        val blocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, box, position)
        val before = (ScarletVioletPokemonReadOnly.read(target) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK9 is not checksum-valid.")
        require(!before.fatefulEncounter) { "Fateful-event Egg Dates remain protected." }
        require(before.eggLocation != 0) { "A Pokémon without a native egg location must keep an empty Egg Date." }
        val savedDate = eggDate(before)
            ?: throw IllegalArgumentException("The saved Egg Date is unusual and remains protected.")
        val normalized = formatDate(year, month, day)
        require(normalized != savedDate) { "Enter a different Egg Date before review." }
        val afterRecord = editRecord(target, year, month, day)
        val written = if (area == ScarletVioletPokemonArea.BOX) {
            ScarletVioletSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else ScarletVioletSaveValidation.writePartyRecord(original, position, afterRecord)
        verify(original, written.bytes, area, box, position, written.absoluteOffset, year, month, day)
        return ScarletVioletEggDateCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset,
            before, savedDate, normalized, year, month, day
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        expectedYear: Int,
        expectedMonth: Int,
        expectedDay: Int
    ) {
        val beforeBlocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val afterBlocks = ScarletVioletSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The Egg Date edit changed title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val beforePokemon = (ScarletVioletPokemonReadOnly.read(beforeRecord) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PK9 no longer reopens as a Pokémon.")
        val afterPokemon = (ScarletVioletPokemonReadOnly.read(afterRecord) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PK9 did not reopen as a Pokémon.")
        require(afterPokemon.eggYear == expectedYear - 2000 &&
            afterPokemon.eggMonth == expectedMonth && afterPokemon.eggDay == expectedDay &&
            afterPokemon.copy(
                eggYear = beforePokemon.eggYear,
                eggMonth = beforePokemon.eggMonth,
                eggDay = beforePokemon.eggDay
            ) == beforePokemon) {
            "The persisted PK9 changed more than its Egg Date."
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
        require(byte(afterPlain, EGG_YEAR) == expectedYear - 2000 &&
            byte(afterPlain, EGG_MONTH) == expectedMonth && byte(afterPlain, EGG_DAY) == expectedDay) {
            "The Egg Date bytes do not match the reviewed value."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in EGG_YEAR..EGG_DAY || beforePlain[offset] == afterPlain[offset]
        }) { "The PK9 edit exceeded the Egg Date and checksum fields." }

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

    private fun editRecord(record: ByteArray, year: Int, month: Int, day: Int): ByteArray {
        val plain = decryptAndUnshuffle(record)
        plain[EGG_YEAR] = (year - 2000).toByte()
        plain[EGG_MONTH] = month.toByte()
        plain[EGG_DAY] = day.toByte()
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

class ScarletVioletEggDateCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: ScarletVioletPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: ScarletVioletPokemonRead,
    val beforeDate: String,
    val afterDate: String,
    private val year: Int,
    private val month: Int,
    private val day: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = ScarletVioletPokemonEggDateEditing.sha256(this.edited)
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
        check(!consumed) { "This Egg Date review was already used. Reopen Pokémon Boxes." }
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
            ScarletVioletPokemonEggDateEditing.verify(
                original, edited, area, box, position, absoluteOffset, year, month, day
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Egg Date edit."
            }
            ScarletVioletPokemonEggDateEditing.verify(
                original, persisted, area, box, position, absoluteOffset, year, month, day
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Egg Date copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
