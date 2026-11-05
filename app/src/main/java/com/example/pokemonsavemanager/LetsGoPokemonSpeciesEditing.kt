package com.example.pokemonsavemanager

/** Changes only one native PB7 species field in a reviewed new Let's Go save copy. */
object LetsGoPokemonSpeciesEditing {
    private const val STORAGE_RECORDS = 0x5C00
    private const val STORAGE_RECORDS_LENGTH = 0x3F7A0
    private const val FOOTER = 0xB8600
    private const val DESCRIPTORS = FOOTER + 0x14
    private const val RECORD_BLOCK = 9
    private const val SPECIES = 0x08
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    val supportedSpecies: List<Int> = (1..151).toList() + listOf(808, 809)

    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        LetsGoPokemonNicknameEditing.readDocumentBytes(input)

    fun readSaveBytes(input: java.io.InputStream): ByteArray =
        LetsGoPokemonNicknameEditing.readSaveBytes(input)

    fun sha256(bytes: ByteArray): String = LetsGoPokemonNicknameEditing.sha256(bytes)

    fun stage(source: ByteArray, expectedSha256: String, storageIndex: Int, requestedSpecies: Int): LetsGoSpeciesCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(LetsGoSaveValidation.inspect(original) is LetsGoValidation.Verified) {
            "A validated native Let's Go save is required."
        }
        val storage = LetsGoStorageReadOnly.read(original)
        require(storageIndex in 0 until storage.count) { "The selected native Pokémon record no longer exists." }
        val slot = storage.slot(storageIndex)
        val before = slot.read as? LetsGoSlotRead.Pokemon
            ?: throw IllegalArgumentException("The selected native PB7 is not checksum-valid.")
        require(!slot.partner && !before.value.isPartnerForm) {
            "The partner Pokémon's species is tied to its native starter form and remains protected."
        }
        require(!before.value.isEgg) { "Egg species editing is not supported for Let's Go." }
        require(before.value.form == 0) {
            "Species editing currently protects alternate-form PB7 records so their species and form cannot become mismatched."
        }
        require(requestedSpecies in supportedSpecies) { "Choose a species native to Let's Go." }
        require(requestedSpecies != before.value.species) { "Choose a different species before review." }

        val recordOffset = STORAGE_RECORDS + storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        val afterRecord = editRecord(beforeRecord, requestedSpecies)
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        writeU16(edited, descriptorCrcOffset(RECORD_BLOCK),
            LetsGoSaveValidation.crc16(edited, STORAGE_RECORDS, STORAGE_RECORDS_LENGTH))
        verify(original, edited, storageIndex, requestedSpecies)
        return LetsGoSpeciesCopyPlan(original, edited, storageIndex, recordOffset, before.value, requestedSpecies)
    }

    internal fun verify(original: ByteArray, edited: ByteArray, storageIndex: Int, species: Int) {
        val beforeValidation = LetsGoSaveValidation.inspect(original)
        val afterValidation = LetsGoSaveValidation.inspect(edited)
        require(beforeValidation is LetsGoValidation.Verified && afterValidation is LetsGoValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The edited copy changed native title identity, size, or block integrity."
        }
        val beforeStorage = LetsGoStorageReadOnly.read(original)
        val afterStorage = LetsGoStorageReadOnly.read(edited)
        require(beforeStorage.count == afterStorage.count && storageIndex in 0 until beforeStorage.count) {
            "The native stored-list identity changed."
        }
        val recordOffset = STORAGE_RECORDS + storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        require((0 until beforeStorage.count).all { index ->
            index == storageIndex || run {
                val offset = STORAGE_RECORDS + index * LetsGoPokemonReadOnly.RECORD_SIZE
                original.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE)
                    .contentEquals(edited.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE))
            }
        }) { "Another native Pokémon record changed." }

        val beforeSlot = beforeStorage.slot(storageIndex)
        val afterSlot = afterStorage.slot(storageIndex)
        require(beforeSlot.partyPositions == afterSlot.partyPositions && beforeSlot.partner == afterSlot.partner) {
            "The selected Pokémon's party or partner identity changed."
        }
        val beforePokemon = (beforeSlot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PB7 no longer reopens as a Pokémon.")
        val afterPokemon = (afterSlot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PB7 did not reopen as a Pokémon.")
        require(afterPokemon.species == species && afterPokemon.copy(species = beforePokemon.species) == beforePokemon) {
            "The persisted PB7 changed more than its species."
        }

        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        val afterRecord = edited.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in SPECIES..SPECIES + 1 || beforePlain[offset] == afterPlain[offset]
        }) { "The PB7 edit exceeded the species and checksum fields." }

        val crcOffset = descriptorCrcOffset(RECORD_BLOCK)
        require(original.indices.all { offset ->
            offset in recordOffset until recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE ||
                offset in crcOffset until crcOffset + 2 || original[offset] == edited[offset]
        }) { "An unrelated Let's Go save byte changed." }
    }

    private fun editRecord(record: ByteArray, species: Int): ByteArray {
        val plain = decryptAndUnshuffle(record)
        writeU16(plain, SPECIES, species)
        var checksum = 0
        for (offset in 8 until 0xE8 step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun decryptAndUnshuffle(record: ByteArray): ByteArray {
        require(record.size == LetsGoPokemonReadOnly.RECORD_SIZE) { "Expected one complete native PB7 record." }
        val ec = u32(record, 0)
        val decrypted = record.copyOf()
        xorWords(decrypted, 8, 0xE8, ec)
        xorWords(decrypted, 0xE8, LetsGoPokemonReadOnly.RECORD_SIZE, ec)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val plain = decrypted.copyOf()
        for (block in 0..3) {
            val from = 8 + (order[block] - '0') * 56
            decrypted.copyInto(plain, 8 + block * 56, from, from + 56)
        }
        return plain
    }

    private fun shuffleAndEncrypt(plain: ByteArray): ByteArray {
        val ec = u32(plain, 0)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val shuffled = plain.copyOf()
        for (block in 0..3) {
            val destination = 8 + (order[block] - '0') * 56
            plain.copyInto(shuffled, destination, 8 + block * 56, 8 + (block + 1) * 56)
        }
        xorWords(shuffled, 8, 0xE8, ec)
        xorWords(shuffled, 0xE8, LetsGoPokemonReadOnly.RECORD_SIZE, ec)
        return shuffled
    }

    private fun xorWords(bytes: ByteArray, from: Int, endExclusive: Int, seed: Long) {
        var state = seed
        for (offset in from until endExclusive step 2) {
            state = (state * 0x41C64E6DL + 0x6073L) and 0xFFFFFFFFL
            writeU16(bytes, offset, u16(bytes, offset) xor (state ushr 16).toInt())
        }
    }

    private fun descriptorCrcOffset(block: Int): Int = DESCRIPTORS + block * 8 + 6
    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long =
        u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }
}

class LetsGoSpeciesCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val storageIndex: Int,
    val recordOffset: Int,
    val before: LetsGoPokemonRead,
    val species: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LetsGoPokemonSpeciesEditing.sha256(this.original)
    val editedSha256: String = LetsGoPokemonSpeciesEditing.sha256(this.edited)
    private var consumed = false

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
            LetsGoPokemonSpeciesEditing.verify(original, edited, storageIndex, species)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed species edit." }
            LetsGoPokemonSpeciesEditing.verify(original, persisted, storageIndex, species)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Species copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
