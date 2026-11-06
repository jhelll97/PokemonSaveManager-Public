package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Duplicates one checksum-valid native PB8 into a reviewed new BDSP save copy. */
object BdspPokemonDuplicateEditing {
    private const val PARTY_OFFSET = 0x14098
    private const val BOX_OFFSET = 0x14EF4
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

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

    fun readSaveBytes(input: InputStream): ByteArray = readDocumentBytes(input).also {
        BdspStorageReadOnly.read(it)
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int
    ): BdspPokemonDuplicateCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val validation = BdspSaveValidation.inspect(original)
        require(validation is BdspValidation.Verified) { "A validated native BDSP save is required." }
        val sourceOffset = targetOffset(original, area, box, position)
        val sourceRecord = original.copyOfRange(
            sourceOffset, sourceOffset + BdspPokemonReadOnly.RECORD_SIZE
        )
        val pokemon = inspectSourceRecord(sourceRecord)
        val storage = BdspStorageReadOnly.read(original)
        val destination = (0 until 40).asSequence().flatMap { current -> storage.box(current).asSequence() }
            .firstOrNull { it.read is BdspSlotRead.Empty }
            ?: throw IllegalArgumentException("All 1,200 native BDSP PC-box slots are occupied.")
        val destinationBox = destination.box + 1
        val destinationPosition = destination.position
        val destinationOffset = BOX_OFFSET +
            (destination.box * 30 + destination.position - 1) * BdspPokemonReadOnly.RECORD_SIZE
        require(sourceOffset != destinationOffset) {
            "The source Pokémon cannot also be the reviewed empty destination."
        }
        val edited = original.copyOf()
        sourceRecord.copyInto(edited, destinationOffset)
        refreshDigest(edited)
        verify(
            original, edited, area, box, position,
            destinationBox, destinationPosition, destinationOffset
        )
        return BdspPokemonDuplicateCopyPlan(
            original, edited, sourceRecord, pokemon, area, box, position,
            destinationBox, destinationPosition, destinationOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        sourceArea: BdspPokemonArea,
        sourceBox: Int,
        sourcePosition: Int,
        destinationBox: Int,
        destinationPosition: Int,
        destinationOffset: Int
    ) {
        val beforeValidation = BdspSaveValidation.inspect(original)
        val afterValidation = BdspSaveValidation.inspect(edited)
        require(beforeValidation is BdspValidation.Verified &&
            afterValidation is BdspValidation.Verified &&
            beforeValidation.version == afterValidation.version &&
            beforeValidation.revision == afterValidation.revision &&
            original.size == edited.size) {
            "The duplicated copy changed native title identity, revision, or save size."
        }
        val sourceOffset = targetOffset(original, sourceArea, sourceBox, sourcePosition)
        require(sourceOffset == targetOffset(edited, sourceArea, sourceBox, sourcePosition)) {
            "The selected native Pokémon location changed."
        }
        val expectedDestinationOffset = BOX_OFFSET +
            ((destinationBox - 1) * 30 + destinationPosition - 1) * BdspPokemonReadOnly.RECORD_SIZE
        require(destinationOffset == expectedDestinationOffset && sourceOffset != destinationOffset) {
            "The reviewed native duplication locations changed."
        }
        val sourceRecord = original.copyOfRange(
            sourceOffset, sourceOffset + BdspPokemonReadOnly.RECORD_SIZE
        )
        val sourcePokemon = inspectSourceRecord(sourceRecord)
        require(edited.copyOfRange(
            sourceOffset, sourceOffset + BdspPokemonReadOnly.RECORD_SIZE
        ).contentEquals(sourceRecord)) {
            "The selected source PB8 changed during duplication."
        }
        val before = BdspStorageReadOnly.read(original)
        val after = BdspStorageReadOnly.read(edited)
        require(before.box(destinationBox - 1)[destinationPosition - 1].read is BdspSlotRead.Empty) {
            "The reviewed native destination was not empty in the source save."
        }
        val reopened = after.box(destinationBox - 1)[destinationPosition - 1].read as? BdspSlotRead.Pokemon
            ?: throw IllegalArgumentException("The duplicated PB8 did not reopen as a Pokémon.")
        val duplicatedRecord = edited.copyOfRange(
            destinationOffset, destinationOffset + BdspPokemonReadOnly.RECORD_SIZE
        )
        require(duplicatedRecord.contentEquals(sourceRecord) && reopened.value == sourcePokemon) {
            "The duplicated PB8 did not preserve the exact native source record."
        }
        require(original.indices.all { index ->
            index in destinationOffset until destinationOffset + BdspPokemonReadOnly.RECORD_SIZE ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[index] == edited[index]
        }) { "An existing Pokémon slot or unrelated BDSP save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun inspectSourceRecord(record: ByteArray): BdspPokemonRead {
        require(record.size == BdspPokemonReadOnly.RECORD_SIZE) {
            "A complete 344-byte native PB8 record is required."
        }
        val pokemon = (BdspPokemonReadOnly.read(record) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB8 is not checksum-valid.")
        require(!pokemon.isEgg) { "Egg PB8 records cannot be duplicated." }
        require(pokemon.cachedLevel in 1..100 && pokemon.nature in 0..24 && pokemon.statNature in 0..24) {
            "The PB8 Level or nature data is outside BDSP's native limits."
        }
        require(pokemon.currentHandler in 0..1) { "The PB8 current-handler identity is invalid." }
        require(pokemon.evs.all { it in 0..252 } && pokemon.evs.sum() <= 510) {
            "The PB8 EV values exceed BDSP's native per-stat or total limit."
        }
        require(runCatching { BdspPokemonLevelEditing.maximumExperience(pokemon) }.isSuccess) {
            "This PB8 species/form has no uniquely verified native BDSP profile, so it remains protected."
        }
        return pokemon
    }

    private fun targetOffset(source: ByteArray, area: BdspPokemonArea, box: Int, position: Int): Int {
        val size = BdspPokemonReadOnly.RECORD_SIZE
        return if (area == BdspPokemonArea.BOX) {
            require(box in 1..40 && position in 1..30) {
                "The selected native PC-box slot is outside storage."
            }
            BOX_OFFSET + ((box - 1) * 30 + position - 1) * size
        } else {
            require(position in 1..6) { "The selected native party slot is outside the six-slot party." }
            val count = source[PARTY_OFFSET + 6 * size].toInt() and 255
            require(position <= count) { "The selected native party slot is not occupied." }
            PARTY_OFFSET + (position - 1) * size
        }
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

class BdspPokemonDuplicateCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    val pokemon: BdspPokemonRead,
    val sourceArea: BdspPokemonArea,
    val sourceBox: Int,
    val sourcePosition: Int,
    val destinationBox: Int,
    val destinationPosition: Int,
    val destinationOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    val sourceSha256 = BdspPokemonDuplicateEditing.sha256(this.original)
    val recordSha256 = BdspPokemonDuplicateEditing.sha256(this.sourceRecord)
    val editedSha256 = BdspPokemonDuplicateEditing.sha256(this.edited)
    val sourceLocation: String get() = if (sourceArea == BdspPokemonArea.BOX)
        "Box $sourceBox • Slot $sourcePosition" else "Party $sourcePosition"
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
            BdspPokemonDuplicateEditing.verify(
                original, edited, sourceArea, sourceBox, sourcePosition,
                destinationBox, destinationPosition, destinationOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Pokémon duplicate."
            }
            BdspPokemonDuplicateEditing.verify(
                original, persisted, sourceArea, sourceBox, sourcePosition,
                destinationBox, destinationPosition, destinationOffset
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
