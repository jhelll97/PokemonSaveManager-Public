package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Changes only one native PA9 Original Trainer name in a reviewed new Legends: Z-A save copy. */
object LegendsZaPokemonOriginalTrainerEditing {
    private const val MAX_FILE_SIZE = 0x309FB3
    private const val STORED_END = 0x148
    private const val ORIGINAL_TRAINER = 0xF8
    private const val ORIGINAL_TRAINER_LENGTH = 26
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val remaining = MAX_FILE_SIZE + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= MAX_FILE_SIZE) { "The document exceeds the supported Legends: Z-A save size." }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsZaPokemonArea,
        box: Int,
        position: Int,
        requestedOriginalTrainer: String
    ): LegendsZaOriginalTrainerCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = LegendsZaSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, box, position)
        val before = (LegendsZaPokemonReadOnly.read(target) as? LegendsZaSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA9 is not checksum-valid.")
        val originalTrainer = requestedOriginalTrainer.trim()
        val encoded = encodeOriginalTrainer(originalTrainer)
        require(originalTrainer != before.originalTrainer) {
            "Enter a different Original Trainer name before review."
        }
        val afterRecord = editRecord(target, encoded)
        val written = if (area == LegendsZaPokemonArea.BOX) {
            LegendsZaSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else LegendsZaSaveValidation.writePartyRecord(original, position, afterRecord)
        verify(original, written.bytes, area, box, position, written.absoluteOffset, originalTrainer)
        return LegendsZaOriginalTrainerCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset, before, originalTrainer
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: LegendsZaPokemonArea,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        originalTrainer: String
    ) {
        val beforeBlocks = LegendsZaSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsZaSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && beforeBlocks.revision == afterBlocks.revision &&
            original.size == edited.size) {
            "The Original Trainer edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val beforePokemon = (LegendsZaPokemonReadOnly.read(beforeRecord) as? LegendsZaSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PA9 no longer reopens as a Pokémon.")
        val afterPokemon = (LegendsZaPokemonReadOnly.read(afterRecord) as? LegendsZaSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PA9 did not reopen as a Pokémon.")
        require(afterPokemon.originalTrainer == originalTrainer &&
            afterPokemon.copy(originalTrainer = beforePokemon.originalTrainer) == beforePokemon) {
            "The persisted PA9 changed more than its Original Trainer name."
        }

        val relativeOffset = if (area == LegendsZaPokemonArea.BOX)
            ((box - 1) * 30 + position - 1) * LegendsZaStorageReadOnly.BOX_STRIDE
        else (position - 1) * LegendsZaStorageReadOnly.PARTY_STRIDE
        val beforeTargetBlock = if (area == LegendsZaPokemonArea.BOX) beforeBlocks.boxes else beforeBlocks.party
        val afterTargetBlock = if (area == LegendsZaPokemonArea.BOX) afterBlocks.boxes else afterBlocks.party
        val beforeOtherBlock = if (area == LegendsZaPokemonArea.BOX) beforeBlocks.party else beforeBlocks.boxes
        val afterOtherBlock = if (area == LegendsZaPokemonArea.BOX) afterBlocks.party else afterBlocks.boxes
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) { "The unselected native Pokémon storage object changed." }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + LegendsZaPokemonReadOnly.RECORD_SIZE ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(beforePlain.indices.all { offset ->
            offset in 6..7 ||
                offset in ORIGINAL_TRAINER until ORIGINAL_TRAINER + ORIGINAL_TRAINER_LENGTH ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PA9 edit exceeded the Original Trainer name and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + LegendsZaPokemonReadOnly.RECORD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun targetRecord(
        blocks: LegendsZaPokemonBlocks,
        area: LegendsZaPokemonArea,
        box: Int,
        position: Int
    ): ByteArray {
        val size = LegendsZaPokemonReadOnly.RECORD_SIZE
        val source: ByteArray
        val offset: Int
        if (area == LegendsZaPokemonArea.BOX) {
            require(box in 1..32 && position in 1..30) { "The selected native PC-box slot is outside storage." }
            source = blocks.boxes
            offset = ((box - 1) * 30 + position - 1) * LegendsZaStorageReadOnly.BOX_STRIDE
        } else {
            require(position in 1..6) { "The selected native party slot is outside the six-slot party." }
            source = blocks.party
            offset = (position - 1) * LegendsZaStorageReadOnly.PARTY_STRIDE
            require(LegendsZaStorageReadOnly.hasNativePresence(source, offset)) {
                "The selected native party slot is not occupied."
            }
        }
        return source.copyOfRange(offset, offset + size)
    }

    private fun encodeOriginalTrainer(value: String): ByteArray {
        require(value.isNotEmpty()) { "Enter an Original Trainer name." }
        require(value.length <= 12) {
            "Legends: Z-A Original Trainer names are limited to 12 UTF-16 code units."
        }
        require(value.none { Character.isISOControl(it) }) {
            "The Original Trainer name contains unsupported control characters."
        }
        val encoded = try {
            val buffer = Charsets.UTF_16LE.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value))
            ByteArray(buffer.remaining()).also { buffer.get(it) }
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("The Original Trainer name contains malformed Unicode.")
        }
        require(encoded.size <= 24) { "The Original Trainer name cannot fit the native PA9 field." }
        return ByteArray(ORIGINAL_TRAINER_LENGTH).also { encoded.copyInto(it) }
    }

    private fun editRecord(record: ByteArray, originalTrainer: ByteArray): ByteArray {
        val plain = decryptAndUnshuffle(record)
        originalTrainer.copyInto(plain, ORIGINAL_TRAINER)
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun decryptAndUnshuffle(record: ByteArray): ByteArray {
        require(record.size == LegendsZaPokemonReadOnly.RECORD_SIZE) { "Expected one complete native PA9 record." }
        val ec = u32(record, 0)
        val decoded = record.copyOf()
        xorWords(decoded, 8, STORED_END, ec)
        xorWords(decoded, STORED_END, LegendsZaPokemonReadOnly.RECORD_SIZE, ec)
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
        xorWords(shuffled, STORED_END, LegendsZaPokemonReadOnly.RECORD_SIZE, ec)
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

class LegendsZaOriginalTrainerCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: LegendsZaPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: LegendsZaPokemonRead,
    val originalTrainer: String
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = LegendsZaPokemonOriginalTrainerEditing.sha256(this.original)
    val editedSha256 = LegendsZaPokemonOriginalTrainerEditing.sha256(this.edited)
    private var consumed = false
    val recordSize: Int get() = LegendsZaPokemonReadOnly.RECORD_SIZE
    val location: String get() = if (area == LegendsZaPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This Original Trainer review was already used. Reopen Pokémon Boxes." }
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
            LegendsZaPokemonOriginalTrainerEditing.verify(
                original, edited, area, box, position, absoluteOffset, originalTrainer
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Original Trainer edit."
            }
            LegendsZaPokemonOriginalTrainerEditing.verify(
                original, persisted, area, box, position, absoluteOffset, originalTrainer
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException((failure.message ?: "Original Trainer copy verification failed.") +
                if (removed) " The failed new copy was removed; the recovery backup is retained."
                else " The new copy could not be removed. Do not use it; the recovery backup is retained.", failure)
        }
    }
}
