package com.example.pokemonsavemanager

/** Changes only one native PA8 species field in a reviewed new Legends: Arceus save copy. */
object LegendsArceusPokemonSpeciesEditing {
    private const val STORED_END = 0x168
    private const val SPECIES = 0x08
    private const val BLOCK_SIZE = 88
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    val supportedSpecies: List<Int> = LegendsArceusPokedexReadOnly.supportedSpecies

    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        LegendsArceusPokemonNicknameEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = LegendsArceusPokemonNicknameEditing.sha256(bytes)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        requestedSpecies: Int
    ): LegendsArceusSpeciesCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, pasture, position)
        val before = (LegendsArceusPokemonReadOnly.read(target) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA8 is not checksum-valid.")
        require(!before.isEgg) { "Egg species editing is not supported for Legends: Arceus." }
        require(before.form == 0) {
            "Species editing protects alternate-form PA8 records so their species and form cannot become mismatched."
        }
        require(!before.isNoble) {
            "Species editing protects Noble PA8 records so their species and native Noble state cannot become mismatched."
        }
        require(requestedSpecies in supportedSpecies) {
            "Choose a species present in Legends: Arceus's native Hisui Pokédex table."
        }
        require(requestedSpecies != before.species) { "Choose a different species before review." }
        val afterRecord = editRecord(target, requestedSpecies)
        val written = if (area == LegendsArceusPokemonArea.PASTURE) {
            LegendsArceusSaveValidation.writePastureRecord(original, pasture, position, afterRecord)
        } else LegendsArceusSaveValidation.writePartyRecord(original, position, afterRecord)
        verify(original, written.bytes, area, pasture, position, written.absoluteOffset, requestedSpecies)
        return LegendsArceusSpeciesCopyPlan(
            original, written.bytes, area, pasture, position, written.absoluteOffset, before, requestedSpecies
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        absoluteOffset: Int,
        species: Int
    ) {
        val beforeBlocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsArceusSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The species edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, pasture, position)
        val afterRecord = targetRecord(afterBlocks, area, pasture, position)
        val beforePokemon = (LegendsArceusPokemonReadOnly.read(beforeRecord) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PA8 no longer reopens as a Pokémon.")
        val afterPokemon = (LegendsArceusPokemonReadOnly.read(afterRecord) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PA8 did not reopen as a Pokémon.")
        require(afterPokemon.species == species && afterPokemon.copy(species = beforePokemon.species) == beforePokemon) {
            "The persisted PA8 changed more than its species."
        }

        val recordSize = recordSize(area)
        val relativeOffset = if (area == LegendsArceusPokemonArea.PASTURE)
            ((pasture - 1) * 30 + position - 1) * recordSize else (position - 1) * recordSize
        val beforeTargetBlock = if (area == LegendsArceusPokemonArea.PASTURE) beforeBlocks.pastures else beforeBlocks.party
        val afterTargetBlock = if (area == LegendsArceusPokemonArea.PASTURE) afterBlocks.pastures else afterBlocks.party
        val beforeOtherBlock = if (area == LegendsArceusPokemonArea.PASTURE) beforeBlocks.party else beforeBlocks.pastures
        val afterOtherBlock = if (area == LegendsArceusPokemonArea.PASTURE) afterBlocks.party else afterBlocks.pastures
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) { "The unselected native Pokémon storage object changed." }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + recordSize || beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in SPECIES..SPECIES + 1 || beforePlain[offset] == afterPlain[offset]
        }) { "The PA8 edit exceeded the species and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + recordSize ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
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
            require(pasture in 1..32 && position in 1..30) { "The selected native pasture slot is outside storage." }
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

    private fun editRecord(record: ByteArray, species: Int): ByteArray {
        val plain = decryptAndUnshuffle(record)
        writeU16(plain, SPECIES, species)
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
    private fun u16(bytes: ByteArray, offset: Int): Int = byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long = u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }
}

class LegendsArceusSpeciesCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: LegendsArceusPokemonArea,
    val pasture: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: LegendsArceusPokemonRead,
    val species: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = LegendsArceusPokemonSpeciesEditing.sha256(this.original)
    val editedSha256 = LegendsArceusPokemonSpeciesEditing.sha256(this.edited)
    private var consumed = false
    val recordSize: Int get() = if (area == LegendsArceusPokemonArea.PASTURE)
        LegendsArceusPokemonReadOnly.STORED_SIZE else LegendsArceusPokemonReadOnly.PARTY_SIZE
    val location: String get() = if (area == LegendsArceusPokemonArea.PASTURE)
        "Pasture $pasture • Slot $position" else "Party $position"

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
            LegendsArceusPokemonSpeciesEditing.verify(
                original, edited, area, pasture, position, absoluteOffset, species
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed species edit." }
            LegendsArceusPokemonSpeciesEditing.verify(
                original, persisted, area, pasture, position, absoluteOffset, species
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
