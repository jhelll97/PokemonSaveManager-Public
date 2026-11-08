package com.example.pokemonsavemanager

/** Changes one native PK9 ribbon or mark ownership bit in a reviewed new Scarlet/Violet save copy. */
object ScarletVioletPokemonRibbonMarkEditing {
    private const val STORED_END = 0x148
    private val names = listOf(
        "Kalos Champion", "Generation 3 Champion", "Sinnoh Champion", "Best Friends", "Training",
        "Skillful Battler", "Expert Battler", "Effort", "Alert", "Shock", "Downcast", "Careless",
        "Relax", "Snooze", "Smile", "Gorgeous", "Royal", "Gorgeous Royal", "Artist", "Footprint",
        "Record", "Legend", "Country", "National", "Earth", "World", "Classic", "Premier", "Event",
        "Birthday", "Special", "Souvenir", "Wishing", "Battle Champion", "Regional Champion",
        "National Champion", "World Champion", "Contest Memory", "Battle Memory", "Hoenn Champion",
        "Contest Star", "Coolness Master", "Beauty Master", "Cuteness Master", "Cleverness Master",
        "Toughness Master", "Alola Champion", "Battle Royale", "Battle Tree Great", "Battle Tree Master",
        "Galar Champion", "Tower Master", "Master Rank", "Lunchtime Mark", "Sleepy-Time Mark", "Dusk Mark",
        "Dawn Mark", "Cloudy Mark", "Rainy Mark", "Stormy Mark", "Snowy Mark", "Blizzard Mark", "Dry Mark",
        "Sandstorm Mark", "Misty Mark", "Destiny Mark", "Fishing Mark", "Curry Mark", "Uncommon Mark",
        "Rare Mark", "Rowdy Mark", "Absent-Minded Mark", "Jittery Mark", "Excited Mark", "Charismatic Mark",
        "Calmness Mark", "Intense Mark", "Zoned-Out Mark", "Joyful Mark", "Angry Mark", "Smiley Mark",
        "Teary Mark", "Upbeat Mark", "Peeved Mark", "Intellectual Mark", "Ferocious Mark", "Crafty Mark",
        "Scowling Mark", "Kindly Mark", "Flustered Mark", "Pumped-Up Mark", "Zero-Energy Mark", "Prideful Mark",
        "Unsure Mark", "Humble Mark", "Thorny Mark", "Vigor Mark", "Slump Mark", "Hisui Ribbon",
        "Twinkling Star Ribbon", "Paldea Champion", "Jumbo Mark", "Mini Mark", "Itemfinder Mark", "Partner Mark",
        "Gourmand Mark", "Once-in-a-Lifetime Ribbon", "Alpha Mark", "Mightiest Mark", "Titan Mark", "Partner Ribbon"
    )
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        ScarletVioletPokemonNicknameEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = ScarletVioletPokemonNicknameEditing.sha256(bytes)

    fun name(index: Int): String =
        if (index in names.indices) names[index] else "Unsupported ($index)"

    fun options(): IntArray = names.indices.toList().toIntArray()

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int,
        requestedIndex: Int,
        requestedOwned: Boolean
    ): ScarletVioletRibbonMarkCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(requestedIndex in names.indices) { "Choose a supported Scarlet/Violet ribbon or mark." }
        val blocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, box, position)
        val before = (ScarletVioletPokemonReadOnly.read(target) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK9 is not checksum-valid.")
        require(!before.isEgg) { "Egg ribbon and mark ownership remains protected." }
        require(!before.fatefulEncounter) {
            "Fateful-event ribbon and mark ownership remains protected because distribution identity is not inferred."
        }
        require(ScarletVioletPokemonAffixedTitleEditing.isValid(before)) {
            "Clear or repair the unsupported Active Title before editing ribbon or mark ownership."
        }
        val beforeOwned = before.ribbonFlags[requestedIndex]
        require(beforeOwned != requestedOwned) { "Choose a ribbon or mark ownership state that changes this PK9." }
        require(requestedOwned || before.affixedRibbon != requestedIndex) {
            "Clear this ribbon or mark from Active Title before removing its ownership bit."
        }
        val afterRecord = editRecord(target, requestedIndex, requestedOwned)
        val written = if (area == ScarletVioletPokemonArea.BOX) {
            ScarletVioletSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else ScarletVioletSaveValidation.writePartyRecord(original, position, afterRecord)
        verify(
            original, written.bytes, area, box, position, written.absoluteOffset,
            requestedIndex, requestedOwned
        )
        return ScarletVioletRibbonMarkCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset,
            before, requestedIndex, beforeOwned, requestedOwned,
            ribbonByteOffset(requestedIndex), ribbonMask(requestedIndex)
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        ribbonIndex: Int,
        owned: Boolean
    ) {
        require(ribbonIndex in names.indices) { "The reviewed ribbon or mark index is unsupported." }
        val beforeBlocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val afterBlocks = ScarletVioletSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The ribbon or mark edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val beforePokemon = (ScarletVioletPokemonReadOnly.read(beforeRecord) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PK9 no longer reopens as a Pokémon.")
        val afterPokemon = (ScarletVioletPokemonReadOnly.read(afterRecord) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PK9 did not reopen as a Pokémon.")
        require(!beforePokemon.isEgg && !beforePokemon.fatefulEncounter) {
            "The reviewed PK9 is no longer eligible for ribbon or mark ownership editing."
        }
        require(ScarletVioletPokemonAffixedTitleEditing.isValid(beforePokemon)) {
            "The reviewed PK9 no longer has a valid Active Title."
        }
        require(owned || beforePokemon.affixedRibbon != ribbonIndex) {
            "The reviewed ribbon or mark is still the Active Title and cannot be removed."
        }
        val restoredFlags = afterPokemon.ribbonFlags.toMutableList().also {
            it[ribbonIndex] = beforePokemon.ribbonFlags[ribbonIndex]
        }
        require(afterPokemon.ribbonFlags[ribbonIndex] == owned &&
            afterPokemon.copy(ribbonFlags = restoredFlags) == beforePokemon) {
            "The persisted PK9 changed more than the selected ribbon or mark ownership bit."
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
        val byteOffset = ribbonByteOffset(ribbonIndex)
        val mask = ribbonMask(ribbonIndex)
        require(((byte(afterPlain, byteOffset) and mask) != 0) == owned) {
            "The selected ribbon or mark ownership bit was not written exactly."
        }
        require((byte(beforePlain, byteOffset) xor byte(afterPlain, byteOffset)) == mask) {
            "The ribbon or mark edit changed more than the selected ownership bit."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset == byteOffset || beforePlain[offset] == afterPlain[offset]
        }) { "The PK9 edit exceeded the selected ribbon or mark bit and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + ScarletVioletPokemonReadOnly.RECORD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    private fun ribbonByteOffset(index: Int): Int =
        if (index < 64) 0x34 + (index ushr 3) else 0x40 + ((index - 64) ushr 3)

    private fun ribbonMask(index: Int): Int =
        1 shl (if (index < 64) (index and 7) else ((index - 64) and 7))

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

    private fun editRecord(record: ByteArray, ribbonIndex: Int, owned: Boolean): ByteArray {
        val plain = decryptAndUnshuffle(record)
        val offset = ribbonByteOffset(ribbonIndex)
        val mask = ribbonMask(ribbonIndex)
        val current = byte(plain, offset)
        plain[offset] = (if (owned) current or mask else current and mask.inv()).toByte()
        var checksum = 0
        for (checksumOffset in 8 until STORED_END step 2) checksum += u16(plain, checksumOffset)
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

class ScarletVioletRibbonMarkCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: ScarletVioletPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: ScarletVioletPokemonRead,
    val ribbonIndex: Int,
    val beforeOwned: Boolean,
    val owned: Boolean,
    val byteOffset: Int,
    val bitMask: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = ScarletVioletPokemonRibbonMarkEditing.sha256(this.edited)
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
        check(!consumed) { "This ribbon or mark review was already used. Reopen Pokémon Boxes." }
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
            ScarletVioletPokemonRibbonMarkEditing.verify(
                original, edited, area, box, position, absoluteOffset, ribbonIndex, owned
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed ribbon or mark edit."
            }
            ScarletVioletPokemonRibbonMarkEditing.verify(
                original, persisted, area, box, position, absoluteOffset, ribbonIndex, owned
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Ribbon or mark copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
