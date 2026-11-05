package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Changes only Move 1 and its current PP in one reviewed native PB7 copy. */
object LetsGoPokemonMove1Editing {
    private const val STORAGE_RECORDS = 0x5C00
    private const val STORAGE_RECORDS_LENGTH = 0x3F7A0
    private const val FOOTER = 0xB8600
    private const val DESCRIPTORS = FOOTER + 0x14
    private const val RECORD_BLOCK = 9
    private const val MOVE_1 = 0x5A
    private const val MOVE_1_PP = 0x62
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: InputStream): ByteArray = readBounded(
        input, LetsGoSaveValidation.FILE_SIZE, "The document exceeds the supported Let's Go save size."
    )

    fun readSaveBytes(input: InputStream): ByteArray = readDocumentBytes(input).also {
        require(it.size == LetsGoSaveValidation.FILE_SIZE) { "Select one complete native Let's Go save." }
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        storageIndex: Int,
        requestedMoveId: Int,
        requestedPp: Int
    ): LetsGoMove1CopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val validation = LetsGoSaveValidation.inspect(original)
        require(validation is LetsGoValidation.Verified) { "A validated native Let's Go save is required." }
        val storage = LetsGoStorageReadOnly.read(original)
        require(storageIndex in 0 until storage.count) { "The selected native Pokémon record no longer exists." }
        val before = storage.slot(storageIndex).read as? LetsGoSlotRead.Pokemon
            ?: throw IllegalArgumentException("The selected native PB7 is not checksum-valid.")
        validateMoves(before.value.moves)
        val move = LetsGoMoveCatalog.choice(requestedMoveId)
            ?: throw IllegalArgumentException("Move #$requestedMoveId is not available in Let's Go.")
        require(before.value.moves.drop(1).none { it.id == move.id }) {
            "${move.name} already appears in another move slot. Let's Go move slots must be unique."
        }
        val ppUps = before.value.moves.first().ppUps
        val maximumPp = LetsGoMoveCatalog.maximumPp(move.id, ppUps)
        require(requestedPp in 0..maximumPp) {
            "Current PP must be between 0 and $maximumPp for ${move.name} with $ppUps PP Ups."
        }
        val beforeMove = before.value.moves.first()
        require(beforeMove.id != move.id || beforeMove.pp != requestedPp) {
            "Choose a different Move 1 or current PP value before review."
        }

        val recordOffset = STORAGE_RECORDS + storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        val afterRecord = editRecord(beforeRecord, move.id, requestedPp)
        require(LetsGoPokemonReadOnly.read(afterRecord) is LetsGoSlotRead.Pokemon) {
            "The edited PB7 did not pass its native checksum."
        }
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        writeU16(edited, descriptorCrcOffset(RECORD_BLOCK),
            LetsGoSaveValidation.crc16(edited, STORAGE_RECORDS, STORAGE_RECORDS_LENGTH))
        verify(original, edited, storageIndex, move.id, requestedPp)
        return LetsGoMove1CopyPlan(
            original, edited, storageIndex, recordOffset, before.value, move, requestedPp, ppUps, maximumPp
        )
    }

    internal fun verify(original: ByteArray, edited: ByteArray, storageIndex: Int, moveId: Int, pp: Int) {
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
        validateMoves(beforePokemon.moves)
        validateMoves(afterPokemon.moves)
        val beforeMove = beforePokemon.moves.first()
        val afterMove = afterPokemon.moves.first()
        require(afterMove == LetsGoMoveRead(moveId, pp, beforeMove.ppUps) &&
            afterPokemon.moves.drop(1) == beforePokemon.moves.drop(1) &&
            afterPokemon.copy(moves = beforePokemon.moves) == beforePokemon) {
            "The persisted PB7 changed more than Move 1 and its current PP."
        }

        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        val afterRecord = edited.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in MOVE_1 until MOVE_1 + 2 || offset == MOVE_1_PP ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PB7 edit exceeded Move 1, its current PP, and checksum fields." }

        val crcOffset = descriptorCrcOffset(RECORD_BLOCK)
        require(original.indices.all { offset ->
            offset in recordOffset until recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE ||
                offset in crcOffset until crcOffset + 2 || original[offset] == edited[offset]
        }) { "An unrelated Let's Go save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun validateMoves(moves: List<LetsGoMoveRead>) {
        require(moves.size == 4) { "Expected four native PB7 move slots." }
        var foundEmpty = false
        val seen = mutableSetOf<Int>()
        moves.forEachIndexed { index, move ->
            require(move.ppUps in 0..3) { "Move ${index + 1} has an invalid PP Up count." }
            if (move.id == 0) {
                foundEmpty = true
                require(move.pp == 0 && move.ppUps == 0) { "An empty move slot retains PP data." }
            } else {
                require(!foundEmpty) { "Active moves must be consecutive from Move 1." }
                require(seen.add(move.id)) { "Duplicate native moves are not editable." }
                val maximum = LetsGoMoveCatalog.maximumPp(move.id, move.ppUps)
                require(move.pp in 0..maximum) {
                    "Move ${index + 1} current PP exceeds its Let's Go maximum."
                }
            }
        }
    }

    private fun editRecord(record: ByteArray, moveId: Int, pp: Int): ByteArray {
        val plain = decryptAndUnshuffle(record)
        writeU16(plain, MOVE_1, moveId)
        plain[MOVE_1_PP] = pp.toByte()
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

    private fun readBounded(input: InputStream, maximum: Int, message: String): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val remaining = maximum + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= maximum) { message }
        }
        output.toByteArray()
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

class LetsGoMove1CopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val storageIndex: Int,
    val recordOffset: Int,
    val before: LetsGoPokemonRead,
    val move: LetsGoMoveChoice,
    val pp: Int,
    val ppUps: Int,
    val maximumPp: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LetsGoPokemonMove1Editing.sha256(this.original)
    val editedSha256: String = LetsGoPokemonMove1Editing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This Move 1 review was already used. Reopen Pokémon Boxes." }
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
            LetsGoPokemonMove1Editing.verify(original, edited, storageIndex, move.id, pp)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed Move 1 edit." }
            LetsGoPokemonMove1Editing.verify(original, persisted, storageIndex, move.id, pp)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Move 1 copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
