package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Duplicates one checksum-valid native PK8 into a reviewed new Sword/Shield save copy. */
object SwordShieldPokemonDuplicateEditing {
    private const val MAX_FILE_SIZE = 0x1876B1

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
            require(output.size() <= MAX_FILE_SIZE) {
                "The document exceeds the supported Sword/Shield save size."
            }
        }
        output.toByteArray()
    }

    fun readSaveBytes(input: InputStream): ByteArray = readDocumentBytes(input).also {
        require(SwordShieldSaveValidation.inspect(it) is SwordShieldValidation.Verified) {
            "A validated native Sword/Shield save is required."
        }
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int
    ): SwordShieldPokemonDuplicateCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val beforeBlocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val sourceRecord = targetRecord(beforeBlocks, area, box, position)
        val pokemon = inspectSourceRecord(sourceRecord)
        val storage = SwordShieldStorageReadOnly.read(original)
        val destination = (0 until 32).asSequence().flatMap { current -> storage.box(current).asSequence() }
            .firstOrNull { it.read is SwordShieldSlotRead.Empty }
            ?: throw IllegalArgumentException("All 960 native Sword/Shield PC-box slots are occupied.")
        val destinationBox = destination.box + 1
        val destinationPosition = destination.position
        require(area != SwordShieldPokemonArea.BOX || box != destinationBox || position != destinationPosition) {
            "The source Pokémon cannot also be the reviewed empty destination."
        }
        val written = SwordShieldSaveValidation.writeBoxRecord(
            original, destinationBox, destinationPosition, sourceRecord
        )
        verify(
            original, written.bytes, area, box, position,
            destinationBox, destinationPosition, written.absoluteOffset
        )
        return SwordShieldPokemonDuplicateCopyPlan(
            original, written.bytes, sourceRecord, pokemon, area, box, position,
            destinationBox, destinationPosition, written.absoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        sourceArea: SwordShieldPokemonArea,
        sourceBox: Int,
        sourcePosition: Int,
        destinationBox: Int,
        destinationPosition: Int,
        absoluteOffset: Int
    ) {
        val beforeBlocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val afterBlocks = SwordShieldSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The duplicated copy changed native title identity or save size."
        }
        val sourceRecord = targetRecord(beforeBlocks, sourceArea, sourceBox, sourcePosition)
        val sourcePokemon = inspectSourceRecord(sourceRecord)
        require(targetRecord(afterBlocks, sourceArea, sourceBox, sourcePosition).contentEquals(sourceRecord)) {
            "The selected source PK8 changed during duplication."
        }
        val destinationOffset = ((destinationBox - 1) * 30 + destinationPosition - 1) *
            SwordShieldPokemonReadOnly.RECORD_SIZE
        val beforeDestination = beforeBlocks.boxes.copyOfRange(
            destinationOffset, destinationOffset + SwordShieldPokemonReadOnly.RECORD_SIZE
        )
        require(SwordShieldPokemonReadOnly.read(beforeDestination) is SwordShieldSlotRead.Empty) {
            "The reviewed native destination was not empty in the source save."
        }
        val afterDestination = afterBlocks.boxes.copyOfRange(
            destinationOffset, destinationOffset + SwordShieldPokemonReadOnly.RECORD_SIZE
        )
        val duplicated = SwordShieldPokemonReadOnly.read(afterDestination) as? SwordShieldSlotRead.Pokemon
            ?: throw IllegalArgumentException("The duplicated PK8 did not reopen as a Pokémon.")
        require(afterDestination.contentEquals(sourceRecord) && duplicated.value == sourcePokemon) {
            "The duplicated PK8 did not preserve the exact native source record."
        }
        require(beforeBlocks.party.contentEquals(afterBlocks.party)) {
            "Native party data changed during Pokémon duplication."
        }
        require(beforeBlocks.boxes.indices.all { index ->
            index in destinationOffset until destinationOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                beforeBlocks.boxes[index] == afterBlocks.boxes[index]
        }) { "An existing native PC-box slot changed." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + SwordShieldPokemonReadOnly.RECORD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun inspectSourceRecord(record: ByteArray): SwordShieldPokemonRead {
        require(record.size == SwordShieldPokemonReadOnly.RECORD_SIZE) {
            "A complete 344-byte native PK8 record is required."
        }
        val pokemon = (SwordShieldPokemonReadOnly.read(record) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK8 is not checksum-valid.")
        require(!pokemon.isEgg) { "Egg PK8 records cannot be duplicated." }
        require(pokemon.cachedLevel in 1..100 && pokemon.nature in 0..24 && pokemon.statNature in 0..24) {
            "The PK8 Level or nature data is outside Sword/Shield's native limits."
        }
        require(pokemon.dynamaxLevel in 0..10) {
            "The PK8 Dynamax Level is outside Sword/Shield's native limit."
        }
        require(pokemon.evs.all { it in 0..252 } && pokemon.evs.sum() <= 510) {
            "The PK8 EV values exceed Sword/Shield's native per-stat or total limit."
        }
        require(runCatching { SwordShieldPokemonLevelEditing.maximumExperience(pokemon) }.isSuccess) {
            "This PK8 species/form has no uniquely verified native Sword/Shield profile, so it remains protected."
        }
        return pokemon
    }

    private fun targetRecord(
        blocks: SwordShieldPokemonBlocks,
        area: SwordShieldPokemonArea,
        box: Int,
        position: Int
    ): ByteArray {
        val size = SwordShieldPokemonReadOnly.RECORD_SIZE
        val source: ByteArray
        val offset: Int
        if (area == SwordShieldPokemonArea.BOX) {
            require(box in 1..32 && position in 1..30) {
                "The selected native PC-box slot is outside storage."
            }
            source = blocks.boxes
            offset = ((box - 1) * 30 + position - 1) * size
        } else {
            require(position in 1..6) { "The selected native party slot is outside the six-slot party." }
            val count = blocks.party[6 * size].toInt() and 255
            require(position <= count) { "The selected native party slot is not occupied." }
            source = blocks.party
            offset = (position - 1) * size
        }
        return source.copyOfRange(offset, offset + size)
    }
}

class SwordShieldPokemonDuplicateCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    val pokemon: SwordShieldPokemonRead,
    val sourceArea: SwordShieldPokemonArea,
    val sourceBox: Int,
    val sourcePosition: Int,
    val destinationBox: Int,
    val destinationPosition: Int,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    val sourceSha256 = SwordShieldPokemonDuplicateEditing.sha256(this.original)
    val recordSha256 = SwordShieldPokemonDuplicateEditing.sha256(this.sourceRecord)
    val editedSha256 = SwordShieldPokemonDuplicateEditing.sha256(this.edited)
    val sourceLocation: String get() = if (sourceArea == SwordShieldPokemonArea.BOX)
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
            SwordShieldPokemonDuplicateEditing.verify(
                original, edited, sourceArea, sourceBox, sourcePosition,
                destinationBox, destinationPosition, absoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Pokémon duplicate."
            }
            SwordShieldPokemonDuplicateEditing.verify(
                original, persisted, sourceArea, sourceBox, sourcePosition,
                destinationBox, destinationPosition, absoluteOffset
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
