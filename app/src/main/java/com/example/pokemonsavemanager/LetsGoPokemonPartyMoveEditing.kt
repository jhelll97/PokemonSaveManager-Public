package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class LetsGoPokemonPartyMoveDirection(val label: String) {
    TO_PARTY("Move to Party"),
    TO_BOX("Move to PC Box")
}

data class LetsGoPokemonPartyMoveSelection(
    val sourceIndex: Int,
    val pokemon: LetsGoPokemonRead,
    val direction: LetsGoPokemonPartyMoveDirection,
    val partyBefore: List<Int>,
    val partyAfter: List<Int>
)

/** Changes only the six native party pointers in the shared Let's Go Pokémon pool. */
object LetsGoPokemonPartyMoveEditing {
    private const val STORAGE_META = 0x5A00
    private const val STORAGE_META_LENGTH = 0x12
    private const val PARTY_POINTERS = 0x5A00
    private const val PARTY_CAPACITY = 6
    private const val EMPTY_POINTER = 1001
    private const val FOOTER = 0xB8600
    private const val DESCRIPTORS = FOOTER + 0x14
    private const val META_BLOCK = 8

    fun readDocumentBytes(input: InputStream): ByteArray = readBounded(
        input, LetsGoSaveValidation.FILE_SIZE,
        "The document exceeds the supported Let's Go save size."
    )

    fun readSaveBytes(input: InputStream): ByteArray = readDocumentBytes(input).also {
        require(it.size == LetsGoSaveValidation.FILE_SIZE) { "Select one complete native Let's Go save." }
    }

    fun inspect(
        source: ByteArray,
        expectedSha256: String,
        sourceIndex: Int
    ): LetsGoPokemonPartyMoveSelection {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val storage = readVerifiedStorage(original)
        val partyBefore = compactParty(storage)
        val slot = protectedPokemon(storage, sourceIndex)
        val pokemon = (slot.read as LetsGoSlotRead.Pokemon).value
        val direction: LetsGoPokemonPartyMoveDirection
        val partyAfter: List<Int>
        when {
            slot.partyPositions.isEmpty() -> {
                require(partyBefore.size < PARTY_CAPACITY) { "The native party already contains six Pokémon." }
                direction = LetsGoPokemonPartyMoveDirection.TO_PARTY
                partyAfter = partyBefore + sourceIndex
            }
            else -> {
                require(slot.partyPositions.size == 1) { "The selected Pokémon has an ambiguous native party assignment." }
                require(partyBefore.size > 1) { "At least one Pokémon must remain in the native party." }
                direction = LetsGoPokemonPartyMoveDirection.TO_BOX
                partyAfter = partyBefore.filterNot { it == sourceIndex }
            }
        }
        return LetsGoPokemonPartyMoveSelection(
            sourceIndex, pokemon, direction, partyBefore.toList(), partyAfter.toList()
        )
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        sourceIndex: Int
    ): LetsGoPokemonPartyMoveCopyPlan {
        val original = source.copyOf()
        val selection = inspect(original, expectedSha256, sourceIndex)
        val sourceOffset = 0x5C00 + sourceIndex * LetsGoPokemonReadOnly.RECORD_SIZE
        val sourceRecord = original.copyOfRange(
            sourceOffset, sourceOffset + LetsGoPokemonReadOnly.RECORD_SIZE
        )
        val edited = original.copyOf()
        for (position in 0 until PARTY_CAPACITY) {
            writeWord(edited, PARTY_POINTERS + position * 2,
                selection.partyAfter.getOrNull(position) ?: EMPTY_POINTER)
        }
        writeBlockCrc(edited, META_BLOCK, STORAGE_META, STORAGE_META_LENGTH)
        verify(original, edited, selection)
        return LetsGoPokemonPartyMoveCopyPlan(
            original, edited, sourceRecord, selection, sourceOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: LetsGoPokemonPartyMoveSelection
    ) {
        val beforeValidation = LetsGoSaveValidation.inspect(original)
        val afterValidation = LetsGoSaveValidation.inspect(edited)
        require(beforeValidation is LetsGoValidation.Verified &&
            afterValidation is LetsGoValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The moved copy changed native title identity, size, or block integrity."
        }
        val before = LetsGoStorageReadOnly.read(original)
        val after = LetsGoStorageReadOnly.read(edited)
        require(before.count == after.count && before.partnerIndex == after.partnerIndex) {
            "The move changed the native storage count or partner pointer."
        }
        require(compactParty(before) == expected.partyBefore && compactParty(after) == expected.partyAfter) {
            "The persisted party did not reopen in the reviewed order."
        }
        val reopened = protectedPokemon(after, expected.sourceIndex)
        require((reopened.read as LetsGoSlotRead.Pokemon).value == expected.pokemon) {
            "The selected PB7 did not reopen unchanged."
        }
        require(after.slot(expected.sourceIndex).partyPositions == when (expected.direction) {
            LetsGoPokemonPartyMoveDirection.TO_PARTY -> listOf(expected.partyAfter.indexOf(expected.sourceIndex) + 1)
            LetsGoPokemonPartyMoveDirection.TO_BOX -> emptyList()
        }) { "The selected Pokémon did not reopen in its reviewed party or Box location." }
        require((0 until before.count).all { index ->
            val offset = 0x5C00 + index * LetsGoPokemonReadOnly.RECORD_SIZE
            original.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE)
                .contentEquals(edited.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE)) &&
                before.slot(index).partner == after.slot(index).partner
        }) { "A native PB7 record or partner assignment changed." }

        val metaCrc = descriptorCrcOffset(META_BLOCK)
        require(original.indices.all { offset ->
            offset in PARTY_POINTERS until PARTY_POINTERS + PARTY_CAPACITY * 2 ||
                offset in metaCrc until metaCrc + 2 || original[offset] == edited[offset]
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

    private fun protectedPokemon(storage: LetsGoStorageRead, index: Int): LetsGoStorageSlot {
        require(index in 0 until storage.count) { "The selected native storage record no longer exists." }
        val slot = storage.slot(index)
        val pokemon = (slot.read as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB7 is no longer checksum-valid.")
        require(!slot.partner && !pokemon.isPartnerForm) {
            "The partner Pokémon is save-bound and cannot be moved between the party and PC Box."
        }
        require(!pokemon.isEgg) { "Egg PB7 records cannot be moved." }
        require(LetsGoPersonalCatalogue.resolve(pokemon.species, pokemon.form) != null) {
            "The selected PB7 species/form has no verified native Let's Go profile."
        }
        return slot
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

    private fun writeWord(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun descriptorCrcOffset(block: Int): Int = DESCRIPTORS + block * 8 + 6
    private fun writeBlockCrc(bytes: ByteArray, block: Int, offset: Int, length: Int) {
        val value = LetsGoSaveValidation.crc16(bytes, offset, length)
        writeWord(bytes, descriptorCrcOffset(block), value)
    }
}

class LetsGoPokemonPartyMoveCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    val selection: LetsGoPokemonPartyMoveSelection,
    val sourceOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    val sourceSha256: String = LetsGoPokemonPartyMoveEditing.sha256(this.original)
    val sourceRecordSha256: String = LetsGoPokemonPartyMoveEditing.sha256(this.sourceRecord)
    val editedSha256: String = LetsGoPokemonPartyMoveEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This move review was already used. Reopen Pokémon Boxes." }
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
            LetsGoPokemonPartyMoveEditing.verify(original, edited, selection)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed party move."
            }
            LetsGoPokemonPartyMoveEditing.verify(original, persisted, selection)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Party move verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
