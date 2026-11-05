package com.example.pokemonsavemanager

/** Changes only one native PK8 species field in a reviewed new Sword/Shield save copy. */
object SwordShieldPokemonSpeciesEditing {
    private const val STORED_END = 0x148
    private const val SPECIES = 0x08
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    val supportedSpecies: List<Int> = SwordShieldDexSpecies.supportedSpecies

    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        SwordShieldPokemonNicknameEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = SwordShieldPokemonNicknameEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        requestedSpecies: Int
    ): SwordShieldSpeciesCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, box, position)
        val before = (SwordShieldPokemonReadOnly.read(target) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK8 is not checksum-valid.")
        require(!before.isEgg) { "Egg species editing is not supported for Sword/Shield." }
        require(before.form == 0) {
            "Species editing currently protects alternate-form PK8 records so their species and form cannot become mismatched."
        }
        require(!before.canGigantamax) {
            "Species editing protects Gigantamax-factor PK8 records so their species and native factor cannot become mismatched."
        }
        require(requestedSpecies in supportedSpecies) {
            "Choose a species present in Sword/Shield's native Galar, Isle of Armor, or Crown Tundra Pokédex tables."
        }
        require(requestedSpecies != before.species) { "Choose a different species before review." }
        val afterRecord = editRecord(target, requestedSpecies)
        val written = if (area == SwordShieldPokemonArea.BOX) {
            SwordShieldSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else SwordShieldSaveValidation.writePartyRecord(original, position, afterRecord)
        verify(original, written.bytes, area, box, position, written.absoluteOffset, requestedSpecies)
        return SwordShieldSpeciesCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset, before, requestedSpecies
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        species: Int
    ) {
        val beforeBlocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val afterBlocks = SwordShieldSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The species edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val beforePokemon = (SwordShieldPokemonReadOnly.read(beforeRecord) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PK8 no longer reopens as a Pokémon.")
        val afterPokemon = (SwordShieldPokemonReadOnly.read(afterRecord) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PK8 did not reopen as a Pokémon.")
        require(afterPokemon.species == species && afterPokemon.copy(species = beforePokemon.species) == beforePokemon) {
            "The persisted PK8 changed more than its species."
        }

        val relativeOffset = if (area == SwordShieldPokemonArea.BOX)
            ((box - 1) * 30 + position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
        else (position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
        val beforeTargetBlock = if (area == SwordShieldPokemonArea.BOX) beforeBlocks.boxes else beforeBlocks.party
        val afterTargetBlock = if (area == SwordShieldPokemonArea.BOX) afterBlocks.boxes else afterBlocks.party
        val beforeOtherBlock = if (area == SwordShieldPokemonArea.BOX) beforeBlocks.party else beforeBlocks.boxes
        val afterOtherBlock = if (area == SwordShieldPokemonArea.BOX) afterBlocks.party else afterBlocks.boxes
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) { "The unselected native Pokémon storage object changed." }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in SPECIES..SPECIES + 1 || beforePlain[offset] == afterPlain[offset]
        }) { "The PK8 edit exceeded the species and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
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
            require(box in 1..32 && position in 1..30) { "The selected native PC-box slot is outside storage." }
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

    private fun editRecord(record: ByteArray, species: Int): ByteArray {
        val plain = decryptAndUnshuffle(record)
        writeU16(plain, SPECIES, species)
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun decryptAndUnshuffle(record: ByteArray): ByteArray {
        require(record.size == SwordShieldPokemonReadOnly.RECORD_SIZE) { "Expected one complete native PK8 record." }
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
    private fun u32(bytes: ByteArray, offset: Int): Long = u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }
}

class SwordShieldSpeciesCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: SwordShieldPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: SwordShieldPokemonRead,
    val species: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldPokemonSpeciesEditing.sha256(this.original)
    val editedSha256 = SwordShieldPokemonSpeciesEditing.sha256(this.edited)
    private var consumed = false
    val location: String get() = if (area == SwordShieldPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This species review was already used. Reopen Pokémon Boxes." }
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
            SwordShieldPokemonSpeciesEditing.verify(
                original, edited, area, box, position, absoluteOffset, species
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed species edit." }
            SwordShieldPokemonSpeciesEditing.verify(
                original, persisted, area, box, position, absoluteOffset, species
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException((failure.message ?: "Species copy verification failed.") +
                if (removed) " The failed new copy was removed; the recovery backup is retained."
                else " The new copy could not be removed. Do not use it; the recovery backup is retained.", failure)
        }
    }
}
