package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Changes only one native PB8 Trainer ID in a reviewed new BDSP save copy. */
object BdspPokemonTrainerIdEditing {
    private const val PARTY_OFFSET = 0x14098
    private const val BOX_OFFSET = 0x14EF4
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16
    private const val STORED_END = 0x148
    private const val TRAINER_ID = 0x0C
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        val maximum = BdspSaveRevision.values().maxOf { it.fileSize }
        while (true) {
            val remaining = maximum + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= maximum) { "The document exceeds the supported BDSP save size." }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        requestedTrainerId: Int
    ): BdspTrainerIdCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val validation = BdspSaveValidation.inspect(original)
        require(validation is BdspValidation.Verified) { "A validated native BDSP save is required." }
        val recordOffset = targetOffset(original, area, box, position)
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val before = (BdspPokemonReadOnly.read(beforeRecord) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB8 is not checksum-valid.")
        val trainerId = normalizeTrainerId(requestedTrainerId)
        require(trainerId != before.tid) { "Enter a different Trainer ID before review." }
        val afterRecord = editRecord(beforeRecord, trainerId)
        val afterPokemon = (BdspPokemonReadOnly.read(afterRecord) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PB8 did not pass its native checksum.")
        val edited = original.copyOf()
        afterRecord.copyInto(edited, recordOffset)
        refreshDigest(edited)
        verify(original, edited, area, box, position, recordOffset, trainerId)
        return BdspTrainerIdCopyPlan(
            original, edited, area, box, position, recordOffset, before, trainerId, afterPokemon.shiny
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: BdspPokemonArea,
        box: Int,
        position: Int,
        recordOffset: Int,
        trainerId: Int
    ) {
        val beforeValidation = BdspSaveValidation.inspect(original)
        val afterValidation = BdspSaveValidation.inspect(edited)
        require(beforeValidation is BdspValidation.Verified && afterValidation is BdspValidation.Verified &&
            beforeValidation.version == afterValidation.version && beforeValidation.revision == afterValidation.revision &&
            original.size == edited.size) {
            "The Trainer ID edit changed native title identity, revision, or save size."
        }
        require(recordOffset == targetOffset(original, area, box, position) &&
            recordOffset == targetOffset(edited, area, box, position)) {
            "The selected native Pokémon location changed."
        }
        val beforeRecord = original.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val afterRecord = edited.copyOfRange(recordOffset, recordOffset + BdspPokemonReadOnly.RECORD_SIZE)
        val beforePokemon = (BdspPokemonReadOnly.read(beforeRecord) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PB8 no longer reopens as a Pokémon.")
        val afterPokemon = (BdspPokemonReadOnly.read(afterRecord) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PB8 did not reopen as a Pokémon.")
        require(afterPokemon.tid == trainerId &&
            afterPokemon.copy(tid = beforePokemon.tid, shiny = beforePokemon.shiny) == beforePokemon) {
            "The persisted PB8 changed more than its Trainer ID."
        }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in TRAINER_ID until TRAINER_ID + 2 ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PB8 edit exceeded the Trainer ID and checksum fields." }
        require(original.indices.all { index ->
            index in recordOffset until recordOffset + BdspPokemonReadOnly.RECORD_SIZE ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH || original[index] == edited[index]
        }) { "An unrelated BDSP save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun targetOffset(source: ByteArray, area: BdspPokemonArea, box: Int, position: Int): Int {
        val size = BdspPokemonReadOnly.RECORD_SIZE
        return if (area == BdspPokemonArea.BOX) {
            require(box in 1..40 && position in 1..30) { "The selected native PC-box slot is outside storage." }
            BOX_OFFSET + ((box - 1) * 30 + position - 1) * size
        } else {
            require(position in 1..6) { "The selected native party slot is outside the six-slot party." }
            val count = source[PARTY_OFFSET + 6 * size].toInt() and 255
            require(position <= count) { "The selected native party slot is not occupied." }
            PARTY_OFFSET + (position - 1) * size
        }
    }

    private fun normalizeTrainerId(value: Int): Int {
        require(value in 0..65535) { "Trainer ID must be between 0 and 65535." }
        return value
    }

    private fun editRecord(record: ByteArray, trainerId: Int): ByteArray {
        val plain = decryptAndUnshuffle(record)
        writeU16(plain, TRAINER_ID, trainerId)
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun decryptAndUnshuffle(record: ByteArray): ByteArray {
        require(record.size == BdspPokemonReadOnly.RECORD_SIZE) { "Expected one complete native PB8 record." }
        val ec = u32(record, 0)
        val decoded = record.copyOf()
        xorWords(decoded, 8, STORED_END, ec)
        xorWords(decoded, STORED_END, BdspPokemonReadOnly.RECORD_SIZE, ec)
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
        xorWords(shuffled, STORED_END, BdspPokemonReadOnly.RECORD_SIZE, ec)
        return shuffled
    }

    private fun xorWords(bytes: ByteArray, from: Int, end: Int, seed: Long) {
        var state = seed
        for (offset in from until end step 2) {
            state = (state * 0x41C64E6DL + 0x6073L) and 0xFFFFFFFFL
            writeU16(bytes, offset, u16(bytes, offset) xor (state ushr 16).toInt())
        }
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
    private fun byte(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 255
    private fun u16(bytes: ByteArray, offset: Int): Int = byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long = u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }
}

class BdspTrainerIdCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: BdspPokemonArea,
    val box: Int,
    val position: Int,
    val recordOffset: Int,
    val before: BdspPokemonRead,
    val trainerId: Int,
    val afterShiny: Boolean
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = BdspPokemonTrainerIdEditing.sha256(this.original)
    val editedSha256 = BdspPokemonTrainerIdEditing.sha256(this.edited)
    private var consumed = false
    val location: String get() = if (area == BdspPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This Trainer ID review was already used. Reopen Pokémon Boxes." }
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
            BdspPokemonTrainerIdEditing.verify(original, edited, area, box, position, recordOffset, trainerId)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed Trainer ID edit." }
            BdspPokemonTrainerIdEditing.verify(original, persisted, area, box, position, recordOffset, trainerId)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException((failure.message ?: "Trainer ID copy verification failed.") +
                if (removed) " The failed new copy was removed; the recovery backup is retained."
                else " The new copy could not be removed. Do not use it; the recovery backup is retained.", failure)
        }
    }
}
