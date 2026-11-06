package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Restores one checksum-valid native PB7 Bank record into a reviewed new Let's Go save copy. */
object LetsGoPseBankRestoreEditing {
    private const val STORAGE_META = 0x5A00
    private const val STORAGE_META_LENGTH = 0x12
    private const val STORAGE_COUNT = 0x5A0E
    private const val STORAGE_RECORDS = 0x5C00
    private const val STORAGE_RECORDS_LENGTH = 0x3F7A0
    private const val FOOTER = 0xB8600
    private const val DESCRIPTORS = FOOTER + 0x14
    private const val META_BLOCK = 8
    private const val RECORD_BLOCK = 9

    fun readDocumentBytes(input: InputStream): ByteArray = readBounded(
        input, LetsGoSaveValidation.FILE_SIZE,
        "The document exceeds the supported Let's Go save size."
    )

    fun readSaveBytes(input: InputStream): ByteArray = readDocumentBytes(input).also {
        require(it.size == LetsGoSaveValidation.FILE_SIZE) { "Select one complete native Let's Go save." }
    }

    fun inspectBankRecord(record: ByteArray): LetsGoPokemonRead {
        require(record.size == LetsGoPokemonReadOnly.RECORD_SIZE) {
            "A complete 260-byte native PB7 Bank record is required."
        }
        val pokemon = (LetsGoPokemonReadOnly.read(record) as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The PSE Bank entry is not a checksum-valid native PB7 Pokémon.")
        require(!pokemon.isEgg) { "Egg PB7 records cannot be restored from PSE Bank." }
        require(!pokemon.isPartnerForm) {
            "Partner Pikachu and partner Eevee are save-bound and cannot be restored from PSE Bank."
        }
        require(LetsGoPersonalCatalogue.resolve(pokemon.species, pokemon.form) != null) {
            "This PB7 species/form has no verified native Let's Go profile, so it remains protected."
        }
        return pokemon
    }

    fun stage(source: ByteArray, expectedSha256: String, bankRecord: ByteArray): LetsGoPseBankRestoreCopyPlan {
        val original = source.copyOf()
        val supplied = bankRecord.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val validation = LetsGoSaveValidation.inspect(original)
        require(validation is LetsGoValidation.Verified) { "A validated native Let's Go save is required." }
        val beforeStorage = LetsGoStorageReadOnly.read(original)
        val pokemon = inspectBankRecord(supplied)
        val index = beforeStorage.count
        require(index in 0 until 1000) { "The native Let's Go storage pool is full." }
        val recordOffset = STORAGE_RECORDS + index * LetsGoPokemonReadOnly.RECORD_SIZE
        require(original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
            .all { it == 0.toByte() }) {
            "The next native storage record contains protected bytes, so PSE will not overwrite it."
        }

        val edited = original.copyOf()
        supplied.copyInto(edited, recordOffset)
        writeU16(edited, STORAGE_COUNT, index + 1)
        writeBlockCrc(edited, META_BLOCK, STORAGE_META, STORAGE_META_LENGTH)
        writeBlockCrc(edited, RECORD_BLOCK, STORAGE_RECORDS, STORAGE_RECORDS_LENGTH)
        verify(original, edited, supplied, index)
        return LetsGoPseBankRestoreCopyPlan(original, edited, supplied, pokemon, index, recordOffset)
    }

    internal fun verify(original: ByteArray, edited: ByteArray, bankRecord: ByteArray, index: Int) {
        val beforeValidation = LetsGoSaveValidation.inspect(original)
        val afterValidation = LetsGoSaveValidation.inspect(edited)
        require(beforeValidation is LetsGoValidation.Verified && afterValidation is LetsGoValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The restored copy changed native title identity, size, or block integrity."
        }
        val before = LetsGoStorageReadOnly.read(original)
        val after = LetsGoStorageReadOnly.read(edited)
        require(index == before.count && after.count == before.count + 1) {
            "The restore did not append exactly one native storage record."
        }
        val recordOffset = STORAGE_RECORDS + index * LetsGoPokemonReadOnly.RECORD_SIZE
        require(original.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
            .all { it == 0.toByte() }) {
            "The reviewed native destination was not empty in the source save."
        }
        require((0 until before.count).all { slot ->
            val start = STORAGE_RECORDS + slot * LetsGoPokemonReadOnly.RECORD_SIZE
            original.copyOfRange(start, start + LetsGoPokemonReadOnly.RECORD_SIZE)
                .contentEquals(edited.copyOfRange(start, start + LetsGoPokemonReadOnly.RECORD_SIZE)) &&
                before.slot(slot).partyPositions == after.slot(slot).partyPositions &&
                before.slot(slot).partner == after.slot(slot).partner
        }) { "An existing native Pokémon record, party pointer, or partner pointer changed." }
        val insertedSlot = after.slot(index)
        val inserted = insertedSlot.read as? LetsGoSlotRead.Pokemon
            ?: throw IllegalArgumentException("The restored PB7 did not reopen as a Pokémon.")
        val expected = inspectBankRecord(bankRecord)
        require(inserted.value == expected && insertedSlot.partyPositions.isEmpty() && !insertedSlot.partner) {
            "The restored PB7 did not reopen exactly as an unassigned storage Pokémon."
        }
        require(edited.copyOfRange(recordOffset, recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
            .contentEquals(bankRecord)) { "The native PSE Bank bytes were not preserved exactly." }
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

class LetsGoPseBankRestoreCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    bankRecord: ByteArray,
    val pokemon: LetsGoPokemonRead,
    val storageIndex: Int,
    val recordOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val bankRecord = bankRecord.copyOf()
    val sourceSha256: String = LetsGoPseBankRestoreEditing.sha256(this.original)
    val bankSha256: String = LetsGoPseBankRestoreEditing.sha256(this.bankRecord)
    val editedSha256: String = LetsGoPseBankRestoreEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        readBankRecord: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This PSE Bank restore review was already used. Reopen PSE Bank." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        fun requireBank() = require(readBankRecord().contentEquals(bankRecord)) {
            "The PSE Bank entry changed. Reopen PSE Bank."
        }
        requireSource()
        requireBank()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) { "The recovery backup did not verify. Nothing was written." }
        requireSource()
        requireBank()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LetsGoPseBankRestoreEditing.verify(original, edited, bankRecord, storageIndex)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed PSE Bank restore."
            }
            LetsGoPseBankRestoreEditing.verify(original, persisted, bankRecord, storageIndex)
            requireSource()
            requireBank()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "PSE Bank restore verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
