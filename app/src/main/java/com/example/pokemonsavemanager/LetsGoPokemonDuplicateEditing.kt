package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Duplicates one checksum-valid native PB7 into a reviewed new Let's Go save copy. */
object LetsGoPokemonDuplicateEditing {
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

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        sourceIndex: Int
    ): LetsGoPokemonDuplicateCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val validation = LetsGoSaveValidation.inspect(original)
        require(validation is LetsGoValidation.Verified) { "A validated native Let's Go save is required." }
        val before = LetsGoStorageReadOnly.read(original)
        require(sourceIndex in 0 until before.count) { "The selected native storage record no longer exists." }
        val sourceSlot = before.slot(sourceIndex)
        val pokemon = (sourceSlot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB7 is no longer checksum-valid.")
        require(!pokemon.isEgg) { "Egg PB7 records cannot be duplicated." }
        require(!pokemon.isPartnerForm && !sourceSlot.partner) {
            "Partner Pikachu and partner Eevee are save-bound and cannot be duplicated."
        }
        require(LetsGoPersonalCatalogue.resolve(pokemon.species, pokemon.form) != null) {
            "This PB7 species/form has no verified native Let's Go profile, so it remains protected."
        }

        val destinationIndex = before.count
        require(destinationIndex in 0 until 1000) { "The native Let's Go storage pool is full." }
        val sourceOffset = STORAGE_RECORDS + sourceIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        val destinationOffset = STORAGE_RECORDS + destinationIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        val sourceRecord = original.copyOfRange(
            sourceOffset, sourceOffset + LetsGoPokemonReadOnly.RECORD_SIZE
        )
        require(LetsGoPokemonReadOnly.read(sourceRecord) is LetsGoSlotRead.Pokemon) {
            "The selected PB7 record failed its stored-data checksum."
        }
        require(original.copyOfRange(
            destinationOffset, destinationOffset + LetsGoPokemonReadOnly.RECORD_SIZE
        ).all { it == 0.toByte() }) {
            "The next native storage record contains protected bytes, so PSE will not overwrite it."
        }

        val edited = original.copyOf()
        sourceRecord.copyInto(edited, destinationOffset)
        writeU16(edited, STORAGE_COUNT, destinationIndex + 1)
        writeBlockCrc(edited, META_BLOCK, STORAGE_META, STORAGE_META_LENGTH)
        writeBlockCrc(edited, RECORD_BLOCK, STORAGE_RECORDS, STORAGE_RECORDS_LENGTH)
        verify(original, edited, sourceIndex, destinationIndex)
        return LetsGoPokemonDuplicateCopyPlan(
            original, edited, sourceRecord, pokemon,
            sourceIndex, destinationIndex, destinationOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        sourceIndex: Int,
        destinationIndex: Int
    ) {
        val beforeValidation = LetsGoSaveValidation.inspect(original)
        val afterValidation = LetsGoSaveValidation.inspect(edited)
        require(beforeValidation is LetsGoValidation.Verified &&
            afterValidation is LetsGoValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The duplicated copy changed native title identity, size, or block integrity."
        }
        val before = LetsGoStorageReadOnly.read(original)
        val after = LetsGoStorageReadOnly.read(edited)
        require(sourceIndex in 0 until before.count && destinationIndex == before.count &&
            after.count == before.count + 1) {
            "The duplicate did not append exactly one native storage record."
        }
        val sourceOffset = STORAGE_RECORDS + sourceIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        val destinationOffset = STORAGE_RECORDS + destinationIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        val sourceRecord = original.copyOfRange(
            sourceOffset, sourceOffset + LetsGoPokemonReadOnly.RECORD_SIZE
        )
        require(original.copyOfRange(
            destinationOffset, destinationOffset + LetsGoPokemonReadOnly.RECORD_SIZE
        ).all { it == 0.toByte() }) {
            "The reviewed native destination was not empty in the source save."
        }
        require((0 until before.count).all { slot ->
            val start = STORAGE_RECORDS + slot * LetsGoPokemonReadOnly.RECORD_SIZE
            original.copyOfRange(start, start + LetsGoPokemonReadOnly.RECORD_SIZE)
                .contentEquals(edited.copyOfRange(start, start + LetsGoPokemonReadOnly.RECORD_SIZE)) &&
                before.slot(slot).partyPositions == after.slot(slot).partyPositions &&
                before.slot(slot).partner == after.slot(slot).partner
        }) { "An existing native Pokémon record, party pointer, or partner pointer changed." }

        val sourceSlot = before.slot(sourceIndex)
        val sourcePokemon = (sourceSlot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The source PB7 did not reopen as a Pokémon.")
        require(!sourcePokemon.isEgg && !sourcePokemon.isPartnerForm && !sourceSlot.partner &&
            LetsGoPersonalCatalogue.resolve(sourcePokemon.species, sourcePokemon.form) != null) {
            "The source PB7 no longer meets the reviewed duplication boundary."
        }
        val duplicatedSlot = after.slot(destinationIndex)
        val duplicated = duplicatedSlot.read as? LetsGoSlotRead.Pokemon
            ?: throw IllegalArgumentException("The duplicated PB7 did not reopen as a Pokémon.")
        require(duplicated.value == sourcePokemon && duplicatedSlot.partyPositions.isEmpty() &&
            !duplicatedSlot.partner) {
            "The duplicated PB7 did not reopen exactly as an unassigned storage Pokémon."
        }
        require(edited.copyOfRange(
            destinationOffset, destinationOffset + LetsGoPokemonReadOnly.RECORD_SIZE
        ).contentEquals(sourceRecord)) {
            "The exact native source PB7 bytes were not preserved in the duplicate."
        }

        val metaCrc = descriptorCrcOffset(META_BLOCK)
        val recordCrc = descriptorCrcOffset(RECORD_BLOCK)
        require(original.indices.all { offset ->
            offset in STORAGE_COUNT until STORAGE_COUNT + 2 ||
                offset in destinationOffset until destinationOffset + LetsGoPokemonReadOnly.RECORD_SIZE ||
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

class LetsGoPokemonDuplicateCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    val pokemon: LetsGoPokemonRead,
    val sourceIndex: Int,
    val destinationIndex: Int,
    val destinationOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    val sourceSha256: String = LetsGoPokemonDuplicateEditing.sha256(this.original)
    val recordSha256: String = LetsGoPokemonDuplicateEditing.sha256(this.sourceRecord)
    val editedSha256: String = LetsGoPokemonDuplicateEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This duplicate review was already used. Reopen Pokémon Boxes." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) {
            "Choose a new empty document; existing files cannot be replaced."
        }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) {
            "The recovery backup did not verify. Nothing was written."
        }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LetsGoPokemonDuplicateEditing.verify(
                original, edited, sourceIndex, destinationIndex
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Pokémon duplicate."
            }
            LetsGoPokemonDuplicateEditing.verify(
                original, persisted, sourceIndex, destinationIndex
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Pokémon duplication verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
