package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Imports one supplied native fateful-event PB7 into the next unused Let's Go storage record. */
object LetsGoEventEditing {
    private const val STORAGE_META = 0x5A00
    private const val STORAGE_META_LENGTH = 0x12
    private const val STORAGE_COUNT = 0x5A0E
    private const val STORAGE_RECORDS = 0x5C00
    private const val STORAGE_RECORDS_LENGTH = 0x3F7A0
    private const val FOOTER = 0xB8600
    private const val DESCRIPTORS = FOOTER + 0x14
    private const val META_BLOCK = 8
    private const val RECORD_BLOCK = 9

    fun readDocumentBytes(input: InputStream): ByteArray = readBounded(input, LetsGoSaveValidation.FILE_SIZE,
        "The document exceeds the supported Let's Go save size.")

    fun readSaveBytes(input: InputStream): ByteArray = readDocumentBytes(input).also {
        require(it.size == LetsGoSaveValidation.FILE_SIZE) { "Select one complete native Let's Go save." }
    }

    fun readEventRecord(input: InputStream): ByteArray = readBounded(input, LetsGoPokemonReadOnly.RECORD_SIZE,
        "Select one complete 260-byte native PB7 event record.").also {
        require(it.size == LetsGoPokemonReadOnly.RECORD_SIZE) {
            "Select one complete 260-byte native PB7 event record."
        }
    }

    fun inspectEvent(record: ByteArray): LetsGoPokemonRead {
        val pokemon = (LetsGoPokemonReadOnly.read(record) as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected file is not a checksum-valid native PB7 Pokémon.")
        require(!pokemon.isEgg && !pokemon.isPartnerForm && pokemon.fatefulEncounter) {
            "Only a non-Egg, non-partner PB7 with its native Fateful Encounter flag can be imported from Events."
        }
        return pokemon
    }

    fun stage(source: ByteArray, expectedSha256: String, eventRecord: ByteArray): LetsGoEventCopyPlan {
        val original = source.copyOf()
        val supplied = eventRecord.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val validation = LetsGoSaveValidation.inspect(original)
        require(validation is LetsGoValidation.Verified) { "A validated native Let's Go save is required." }
        val beforeStorage = LetsGoStorageReadOnly.read(original)
        val event = inspectEvent(supplied)
        val index = beforeStorage.count
        require(index in 0 until 1000) { "The native Let's Go storage pool is full." }
        val recordOffset = STORAGE_RECORDS + index * LetsGoPokemonReadOnly.RECORD_SIZE
        require(original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
            .all { it == 0.toByte() }) { "The next native storage record is not empty." }

        val edited = original.copyOf()
        supplied.copyInto(edited, recordOffset)
        writeU16(edited, STORAGE_COUNT, index + 1)
        writeBlockCrc(edited, META_BLOCK, STORAGE_META, STORAGE_META_LENGTH)
        writeBlockCrc(edited, RECORD_BLOCK, STORAGE_RECORDS, STORAGE_RECORDS_LENGTH)
        verify(original, edited, supplied, index)
        return LetsGoEventCopyPlan(original, edited, supplied, event, index, recordOffset)
    }

    internal fun verify(original: ByteArray, edited: ByteArray, eventRecord: ByteArray, index: Int) {
        val beforeValidation = LetsGoSaveValidation.inspect(original)
        val afterValidation = LetsGoSaveValidation.inspect(edited)
        require(beforeValidation is LetsGoValidation.Verified && afterValidation is LetsGoValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The edited copy changed native title identity, size, or block integrity."
        }
        val before = LetsGoStorageReadOnly.read(original)
        val after = LetsGoStorageReadOnly.read(edited)
        require(index == before.count && after.count == before.count + 1) {
            "The staged event did not append exactly one native storage record."
        }
        require((0 until before.count).all { slot ->
            val start = STORAGE_RECORDS + slot * LetsGoPokemonReadOnly.RECORD_SIZE
            original.copyOfRange(start, start + LetsGoPokemonReadOnly.RECORD_SIZE)
                .contentEquals(edited.copyOfRange(start, start + LetsGoPokemonReadOnly.RECORD_SIZE))
        }) { "An existing native Pokémon record changed." }
        val inserted = after.slot(index).read as? LetsGoSlotRead.Pokemon
            ?: throw IllegalArgumentException("The appended event PB7 did not reopen as a Pokémon.")
        val expected = inspectEvent(eventRecord)
        require(inserted.value == expected) { "The appended event PB7 did not reopen exactly." }
        val recordOffset = STORAGE_RECORDS + index * LetsGoPokemonReadOnly.RECORD_SIZE
        require(edited.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
            .contentEquals(eventRecord)) { "The supplied native event bytes were not preserved exactly." }
        val metaCrc = descriptorCrcOffset(META_BLOCK)
        val recordCrc = descriptorCrcOffset(RECORD_BLOCK)
        require(original.indices.all { offset ->
            offset in STORAGE_COUNT until STORAGE_COUNT + 2 ||
                offset in recordOffset until recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE ||
                offset in metaCrc until metaCrc + 2 || offset in recordCrc until recordCrc + 2 ||
                original[offset] == edited[offset]
        }) { "An unrelated Let's Go save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

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
    private fun writeBlockCrc(bytes: ByteArray, block: Int, offset: Int, length: Int) =
        writeU16(bytes, descriptorCrcOffset(block), LetsGoSaveValidation.crc16(bytes, offset, length))
    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
}

class LetsGoEventCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    eventRecord: ByteArray,
    val event: LetsGoPokemonRead,
    val storageIndex: Int,
    val recordOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val eventRecord = eventRecord.copyOf()
    val sourceSha256: String = LetsGoEventEditing.sha256(this.original)
    val editedSha256: String = LetsGoEventEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This event review was already used. Reopen Events." }
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
            LetsGoEventEditing.verify(original, edited, eventRecord, storageIndex)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed event import." }
            LetsGoEventEditing.verify(original, persisted, eventRecord, storageIndex)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Event copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
