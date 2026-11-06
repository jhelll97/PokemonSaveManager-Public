package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Duplicates one checksum-valid native PA9 into a reviewed new Legends: Z-A save copy. */
object LegendsZaPokemonDuplicateEditing {
    private const val MAX_FILE_SIZE = 0x309FB3
    private const val FOOTER_SIZE = 32

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
                "The document exceeds the supported Legends: Z-A save size."
            }
        }
        output.toByteArray()
    }

    fun readSaveBytes(input: InputStream): ByteArray = readDocumentBytes(input).also {
        require(LegendsZaSaveValidation.inspect(it) is LegendsZaValidation.Verified) {
            "A validated native Legends: Z-A save is required."
        }
        LegendsZaStorageReadOnly.read(it)
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsZaPokemonArea,
        box: Int,
        position: Int
    ): LegendsZaPokemonDuplicateCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val beforeBlocks = LegendsZaSaveValidation.readPokemonBlocks(original)
        val sourceRecord = targetRecord(beforeBlocks, area, box, position)
        val pokemon = inspectSourceRecord(sourceRecord)
        val storage = LegendsZaStorageReadOnly.read(original)
        val destination = (0 until 32).asSequence().flatMap { current -> storage.box(current).asSequence() }
            .firstOrNull { it.read is LegendsZaSlotRead.Empty }
            ?: throw IllegalArgumentException("All 960 native Legends: Z-A PC-box slots are occupied.")
        val destinationBox = destination.box + 1
        val destinationPosition = destination.position
        require(area != LegendsZaPokemonArea.BOX ||
            box != destinationBox || position != destinationPosition) {
            "The source Pokémon cannot also be the reviewed empty destination."
        }
        val written = LegendsZaSaveValidation.writeBoxRecord(
            original, destinationBox, destinationPosition, sourceRecord
        )
        verify(
            original, written.bytes, area, box, position, sourceRecord,
            destinationBox, destinationPosition, written.absoluteOffset
        )
        return LegendsZaPokemonDuplicateCopyPlan(
            original, written.bytes, sourceRecord, pokemon, area, box, position,
            destinationBox, destinationPosition, written.absoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        sourceArea: LegendsZaPokemonArea,
        sourceBox: Int,
        sourcePosition: Int,
        sourceRecord: ByteArray,
        destinationBox: Int,
        destinationPosition: Int,
        absoluteOffset: Int
    ) {
        val before = LegendsZaStorageReadOnly.read(original)
        val after = LegendsZaStorageReadOnly.read(edited)
        require(original.size == edited.size && before.version == after.version &&
            before.revision == after.revision) {
            "The duplicated copy changed native title/revision identity or size."
        }
        val beforeBlocks = LegendsZaSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsZaSaveValidation.readPokemonBlocks(edited)
        val currentSource = targetRecord(beforeBlocks, sourceArea, sourceBox, sourcePosition)
        require(currentSource.contentEquals(sourceRecord) &&
            targetRecord(afterBlocks, sourceArea, sourceBox, sourcePosition).contentEquals(sourceRecord)) {
            "The selected source PA9 changed during duplication."
        }
        val sourcePokemon = inspectSourceRecord(sourceRecord)
        require(before.box(destinationBox - 1)[destinationPosition - 1].read
            is LegendsZaSlotRead.Empty) {
            "The reviewed native PC-box destination was not empty in the source save."
        }
        val reopened = after.box(destinationBox - 1)[destinationPosition - 1].read
            as? LegendsZaSlotRead.Pokemon
            ?: throw IllegalArgumentException("The duplicated PA9 did not reopen as a Pokémon.")
        require(reopened.value == sourcePokemon) {
            "The duplicated PA9 did not reopen as the exact native source record."
        }

        val relativeOffset = ((destinationBox - 1) * 30 + destinationPosition - 1) *
            LegendsZaStorageReadOnly.BOX_STRIDE
        require(afterBlocks.boxes.copyOfRange(
            relativeOffset, relativeOffset + LegendsZaPokemonReadOnly.RECORD_SIZE
        ).contentEquals(sourceRecord)) {
            "The exact native PA9 bytes were not preserved in the duplicate."
        }
        require(beforeBlocks.party.contentEquals(afterBlocks.party)) {
            "Native party data changed during Pokémon duplication."
        }
        require(beforeBlocks.boxes.indices.all { index ->
            index in relativeOffset until relativeOffset + LegendsZaPokemonReadOnly.RECORD_SIZE ||
                beforeBlocks.boxes[index] == afterBlocks.boxes[index]
        }) { "An existing native PC-box slot or the destination slot footer changed." }

        val footerOffset = original.size - FOOTER_SIZE
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + LegendsZaPokemonReadOnly.RECORD_SIZE ||
                index in footerOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun inspectSourceRecord(record: ByteArray): LegendsZaPokemonRead {
        require(record.size == LegendsZaPokemonReadOnly.RECORD_SIZE) {
            "A complete 344-byte native PA9 record is required."
        }
        val pokemon = (LegendsZaPokemonReadOnly.read(record) as? LegendsZaSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA9 is not checksum-valid.")
        require(!pokemon.isEgg) { "Egg PA9 records cannot be duplicated." }
        require(pokemon.cachedLevel in 1..100 && pokemon.nature in 0..24 && pokemon.statNature in 0..24) {
            "The PA9 Level or nature data is outside Legends: Z-A native limits."
        }
        require(pokemon.currentHandler in 0..1) { "The PA9 current-handler identity is invalid." }
        require(pokemon.ivs.all { it in 0..31 }) { "The PA9 IV values exceed native limits." }
        require(pokemon.evs.all { it in 0..252 } && pokemon.evs.sum() <= 510) {
            "The PA9 EV values exceed native per-stat or total limits."
        }
        require(pokemon.hyperTrainingFlags and 0xC0 == 0) {
            "The PA9 hyper-training flags contain unsupported bits."
        }
        val maximumExperience = runCatching {
            LegendsZaPokemonLevelEditing.maximumExperience(pokemon)
        }.getOrElse {
            throw IllegalArgumentException(
                "This PA9 species/form has no uniquely verified native Legends: Z-A profile, so it remains protected."
            )
        }
        require(pokemon.experience <= maximumExperience) {
            "The PA9 experience exceeds the verified Level 100 limit for this species/form."
        }
        return pokemon
    }

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
            require(box in 1..32 && position in 1..30) {
                "The selected native PC-box slot is outside storage."
            }
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
}

class LegendsZaPokemonDuplicateCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    val pokemon: LegendsZaPokemonRead,
    val sourceArea: LegendsZaPokemonArea,
    val sourceBox: Int,
    val sourcePosition: Int,
    val destinationBox: Int,
    val destinationPosition: Int,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    val sourceSha256 = LegendsZaPokemonDuplicateEditing.sha256(this.original)
    val recordSha256 = LegendsZaPokemonDuplicateEditing.sha256(this.sourceRecord)
    val editedSha256 = LegendsZaPokemonDuplicateEditing.sha256(this.edited)
    val sourceLocation: String get() = if (sourceArea == LegendsZaPokemonArea.BOX)
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
            LegendsZaPokemonDuplicateEditing.verify(
                original, edited, sourceArea, sourceBox, sourcePosition, sourceRecord,
                destinationBox, destinationPosition, absoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Pokémon duplicate."
            }
            LegendsZaPokemonDuplicateEditing.verify(
                original, persisted, sourceArea, sourceBox, sourcePosition, sourceRecord,
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
