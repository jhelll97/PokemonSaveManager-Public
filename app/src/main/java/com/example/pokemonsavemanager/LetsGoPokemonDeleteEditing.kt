package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class LetsGoPokemonDeleteSelection(
    val sourceIndex: Int,
    val pokemon: LetsGoPokemonRead,
    val storageCountBefore: Int,
    val partyBefore: List<Int>,
    val partyAfter: List<Int>,
    val partnerBefore: Int?,
    val partnerAfter: Int?
)

/** Removes one native PB7 and compacts the shared Let's Go Pokémon pool in a reviewed new copy. */
object LetsGoPokemonDeleteEditing {
    private const val STORAGE_META = 0x5A00
    private const val STORAGE_META_LENGTH = 0x12
    private const val PARTY_POINTERS = 0x5A00
    private const val PARTNER_POINTER = 0x5A0C
    private const val STORAGE_COUNT = 0x5A0E
    private const val STORAGE_RECORDS = 0x5C00
    private const val STORAGE_RECORDS_LENGTH = 0x3F7A0
    private const val PARTY_CAPACITY = 6
    private const val EMPTY_POINTER = 1001
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

    fun inspect(source: ByteArray, expectedSha256: String, sourceIndex: Int): LetsGoPokemonDeleteSelection {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val storage = readVerifiedStorage(original)
        require(sourceIndex in 0 until storage.count) { "The selected native storage record no longer exists." }
        val slot = storage.slot(sourceIndex)
        val pokemon = (slot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB7 is no longer checksum-valid.")
        require(!slot.partner && !pokemon.isPartnerForm) {
            "The save-bound partner Pokémon cannot be deleted."
        }
        require(!pokemon.isEgg) { "Egg PB7 records remain protected from deletion." }
        require(LetsGoPersonalCatalogue.resolve(pokemon.species, pokemon.form) != null) {
            "This PB7 species/form has no verified native Let's Go profile, so it remains protected."
        }
        val partyBefore = compactParty(storage)
        require(slot.partyPositions.size <= 1) { "The selected Pokémon has an ambiguous native party assignment." }
        if (slot.partyPositions.isNotEmpty()) {
            require(partyBefore.size > 1) { "At least one Pokémon must remain in the native party." }
        }
        val partyAfter = partyBefore.filterNot { it == sourceIndex }
            .map { if (it > sourceIndex) it - 1 else it }
        val partnerBefore = storage.partnerIndex
        val partnerAfter = partnerBefore?.let { if (it > sourceIndex) it - 1 else it }
        return LetsGoPokemonDeleteSelection(
            sourceIndex, pokemon, storage.count, partyBefore, partyAfter, partnerBefore, partnerAfter
        )
    }

    fun stage(source: ByteArray, expectedSha256: String, sourceIndex: Int): LetsGoPokemonDeleteCopyPlan {
        val original = source.copyOf()
        val selection = inspect(original, expectedSha256, sourceIndex)
        val sourceOffset = recordOffset(sourceIndex)
        val oldTailOffset = recordOffset(selection.storageCountBefore - 1)
        val removedRecord = original.copyOfRange(
            sourceOffset, sourceOffset + LetsGoPokemonReadOnly.RECORD_SIZE
        )
        val edited = original.copyOf()
        if (sourceIndex + 1 < selection.storageCountBefore) {
            original.copyInto(
                edited,
                sourceOffset,
                sourceOffset + LetsGoPokemonReadOnly.RECORD_SIZE,
                oldTailOffset + LetsGoPokemonReadOnly.RECORD_SIZE
            )
        }
        edited.fill(0, oldTailOffset, oldTailOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
        selection.partyAfter.forEachIndexed { position, index ->
            writeWord(edited, PARTY_POINTERS + position * 2, index)
        }
        for (position in selection.partyAfter.size until PARTY_CAPACITY) {
            writeWord(edited, PARTY_POINTERS + position * 2, EMPTY_POINTER)
        }
        writeWord(edited, PARTNER_POINTER, selection.partnerAfter ?: EMPTY_POINTER)
        writeWord(edited, STORAGE_COUNT, selection.storageCountBefore - 1)
        writeBlockCrc(edited, META_BLOCK, STORAGE_META, STORAGE_META_LENGTH)
        writeBlockCrc(edited, RECORD_BLOCK, STORAGE_RECORDS, STORAGE_RECORDS_LENGTH)
        verify(original, edited, selection)
        return LetsGoPokemonDeleteCopyPlan(
            original, edited, removedRecord, selection, sourceOffset, oldTailOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: LetsGoPokemonDeleteSelection
    ) {
        val beforeValidation = LetsGoSaveValidation.inspect(original)
        val afterValidation = LetsGoSaveValidation.inspect(edited)
        require(beforeValidation is LetsGoValidation.Verified &&
            afterValidation is LetsGoValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The deleted copy changed native title identity, size, or block integrity."
        }
        val before = LetsGoStorageReadOnly.read(original)
        val after = LetsGoStorageReadOnly.read(edited)
        require(before.count == expected.storageCountBefore && after.count == before.count - 1) {
            "The persisted storage count did not decrease by exactly one."
        }
        require(compactParty(before) == expected.partyBefore && compactParty(after) == expected.partyAfter &&
            before.partnerIndex == expected.partnerBefore && after.partnerIndex == expected.partnerAfter) {
            "The compacted party or partner pointers did not reopen in the reviewed positions."
        }
        val selectedBefore = before.slot(expected.sourceIndex)
        require((selectedBefore.read as? LetsGoSlotRead.Pokemon)?.value == expected.pokemon &&
            !selectedBefore.partner && !expected.pokemon.isPartnerForm && !expected.pokemon.isEgg) {
            "The selected PB7 no longer meets the reviewed deletion boundary."
        }
        require((0 until before.count).filter { it != expected.sourceIndex }.all { oldIndex ->
            val newIndex = if (oldIndex > expected.sourceIndex) oldIndex - 1 else oldIndex
            val oldOffset = recordOffset(oldIndex)
            val newOffset = recordOffset(newIndex)
            original.copyOfRange(oldOffset, oldOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
                .contentEquals(edited.copyOfRange(newOffset, newOffset + LetsGoPokemonReadOnly.RECORD_SIZE))
        }) { "A retained native PB7 changed while the storage pool was compacted." }
        val oldTailOffset = recordOffset(before.count - 1)
        require(edited.copyOfRange(
            oldTailOffset, oldTailOffset + LetsGoPokemonReadOnly.RECORD_SIZE
        ).all { it == 0.toByte() }) {
            "The released native tail record was not cleared."
        }
        val sourceOffset = recordOffset(expected.sourceIndex)
        val metaCrc = descriptorCrcOffset(META_BLOCK)
        val recordCrc = descriptorCrcOffset(RECORD_BLOCK)
        require(original.indices.all { offset ->
            offset in PARTY_POINTERS until STORAGE_COUNT + 2 ||
                offset in sourceOffset until oldTailOffset + LetsGoPokemonReadOnly.RECORD_SIZE ||
                offset in metaCrc until metaCrc + 2 || offset in recordCrc until recordCrc + 2 ||
                original[offset] == edited[offset]
        }) { "An unrelated Let's Go save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun readVerifiedStorage(bytes: ByteArray): LetsGoStorageRead {
        require(LetsGoSaveValidation.inspect(bytes) is LetsGoValidation.Verified) {
            "A validated native Let's Go save is required."
        }
        return LetsGoStorageReadOnly.read(bytes)
    }

    private fun compactParty(storage: LetsGoStorageRead): List<Int> {
        val raw = storage.party().map { it.storage?.index }
        val firstEmpty = raw.indexOfFirst { it == null }.let { if (it < 0) raw.size else it }
        require(raw.drop(firstEmpty).all { it == null }) {
            "The native party has a non-contiguous pointer layout and remains read-only."
        }
        return raw.take(firstEmpty).filterNotNull()
    }

    private fun recordOffset(index: Int): Int =
        STORAGE_RECORDS + index * LetsGoPokemonReadOnly.RECORD_SIZE

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
        writeWord(bytes, descriptorCrcOffset(block), LetsGoSaveValidation.crc16(bytes, offset, length))
    private fun writeWord(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
}

class LetsGoPokemonDeleteCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    removedRecord: ByteArray,
    val selection: LetsGoPokemonDeleteSelection,
    val sourceOffset: Int,
    val oldTailOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val removedRecord = removedRecord.copyOf()
    val sourceSha256: String = LetsGoPokemonDeleteEditing.sha256(this.original)
    val removedRecordSha256: String = LetsGoPokemonDeleteEditing.sha256(this.removedRecord)
    val editedSha256: String = LetsGoPokemonDeleteEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This delete review was already used. Reopen Pokémon Boxes." }
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
            LetsGoPokemonDeleteEditing.verify(original, edited, selection)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Pokémon deletion."
            }
            LetsGoPokemonDeleteEditing.verify(original, persisted, selection)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Pokémon deletion verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
