package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Duplicates one checksum-valid native PK9 into a reviewed new Scarlet/Violet save copy. */
object ScarletVioletPokemonDuplicateEditing {
    private const val MAX_FILE_SIZE = 0x43B2E7
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
                "The document exceeds the supported Scarlet/Violet save size."
            }
        }
        output.toByteArray()
    }

    fun readSaveBytes(input: InputStream): ByteArray = readDocumentBytes(input).also {
        require(ScarletVioletSaveValidation.inspect(it) is ScarletVioletValidation.Verified) {
            "A validated native Scarlet/Violet save is required."
        }
        ScarletVioletStorageReadOnly.read(it)
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int
    ): ScarletVioletPokemonDuplicateCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val beforeBlocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val sourceRecord = targetRecord(beforeBlocks, area, box, position)
        val pokemon = inspectSourceRecord(sourceRecord)
        val storage = ScarletVioletStorageReadOnly.read(original)
        val destination = (0 until 32).asSequence().flatMap { current -> storage.box(current).asSequence() }
            .firstOrNull { it.read is ScarletVioletSlotRead.Empty }
            ?: throw IllegalArgumentException("All 960 native Scarlet/Violet PC-box slots are occupied.")
        val destinationBox = destination.box + 1
        val destinationPosition = destination.position
        require(area != ScarletVioletPokemonArea.BOX ||
            box != destinationBox || position != destinationPosition) {
            "The source Pokémon cannot also be the reviewed empty destination."
        }
        val written = ScarletVioletSaveValidation.writeBoxRecord(
            original, destinationBox, destinationPosition, sourceRecord
        )
        verify(
            original, written.bytes, area, box, position, sourceRecord,
            destinationBox, destinationPosition, written.absoluteOffset
        )
        return ScarletVioletPokemonDuplicateCopyPlan(
            original, written.bytes, sourceRecord, pokemon, area, box, position,
            destinationBox, destinationPosition, written.absoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        sourceArea: ScarletVioletPokemonArea,
        sourceBox: Int,
        sourcePosition: Int,
        sourceRecord: ByteArray,
        destinationBox: Int,
        destinationPosition: Int,
        absoluteOffset: Int
    ) {
        val before = ScarletVioletStorageReadOnly.read(original)
        val after = ScarletVioletStorageReadOnly.read(edited)
        require(original.size == edited.size && before.version == after.version) {
            "The duplicated copy changed native title identity or size."
        }
        require(before.rideLegend == after.rideLegend) {
            "The native ride Pokémon changed during Pokémon duplication."
        }
        val beforeBlocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val afterBlocks = ScarletVioletSaveValidation.readPokemonBlocks(edited)
        val currentSource = targetRecord(beforeBlocks, sourceArea, sourceBox, sourcePosition)
        require(currentSource.contentEquals(sourceRecord) &&
            targetRecord(afterBlocks, sourceArea, sourceBox, sourcePosition).contentEquals(sourceRecord)) {
            "The selected source PK9 changed during duplication."
        }
        val sourcePokemon = inspectSourceRecord(sourceRecord)
        require(before.box(destinationBox - 1)[destinationPosition - 1].read
            is ScarletVioletSlotRead.Empty) {
            "The reviewed native PC-box destination was not empty in the source save."
        }
        val reopened = after.box(destinationBox - 1)[destinationPosition - 1].read
            as? ScarletVioletSlotRead.Pokemon
            ?: throw IllegalArgumentException("The duplicated PK9 did not reopen as a Pokémon.")
        require(reopened.value == sourcePokemon) {
            "The duplicated PK9 did not reopen as the exact native source record."
        }

        val relativeOffset = ((destinationBox - 1) * 30 + destinationPosition - 1) *
            ScarletVioletPokemonReadOnly.RECORD_SIZE
        require(afterBlocks.boxes.copyOfRange(
            relativeOffset, relativeOffset + ScarletVioletPokemonReadOnly.RECORD_SIZE
        ).contentEquals(sourceRecord)) {
            "The exact native PK9 bytes were not preserved in the duplicate."
        }
        require(beforeBlocks.party.contentEquals(afterBlocks.party)) {
            "Native party data changed during Pokémon duplication."
        }
        require(beforeBlocks.boxes.indices.all { index ->
            index in relativeOffset until relativeOffset + ScarletVioletPokemonReadOnly.RECORD_SIZE ||
                beforeBlocks.boxes[index] == afterBlocks.boxes[index]
        }) { "An existing native PC-box slot changed." }

        val footerOffset = original.size - FOOTER_SIZE
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + ScarletVioletPokemonReadOnly.RECORD_SIZE ||
                index in footerOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed, including ride Pokémon data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun inspectSourceRecord(record: ByteArray): ScarletVioletPokemonRead {
        require(record.size == ScarletVioletPokemonReadOnly.RECORD_SIZE) {
            "A complete 344-byte native PK9 record is required."
        }
        val pokemon = (ScarletVioletPokemonReadOnly.read(record) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK9 is not checksum-valid.")
        require(!pokemon.isEgg) { "Egg PK9 records cannot be duplicated." }
        require(pokemon.cachedLevel in 1..100 && pokemon.nature in 0..24 && pokemon.statNature in 0..24) {
            "The PK9 Level or nature data is outside Scarlet/Violet native limits."
        }
        require(pokemon.currentHandler in 0..1) { "The PK9 current-handler identity is invalid." }
        require(pokemon.ivs.all { it in 0..31 }) { "The PK9 IV values exceed native limits." }
        require(pokemon.evs.all { it in 0..252 } && pokemon.evs.sum() <= 510) {
            "The PK9 EV values exceed native per-stat or total limits."
        }
        require(pokemon.hyperTrainingFlags and 0xC0 == 0) {
            "The PK9 hyper-training flags contain unsupported bits."
        }
        val maximumExperience = runCatching {
            ScarletVioletPokemonLevelEditing.maximumExperience(pokemon)
        }.getOrElse {
            throw IllegalArgumentException(
                "This PK9 species/form has no uniquely verified native Scarlet/Violet profile, so it remains protected."
            )
        }
        require(pokemon.experience <= maximumExperience) {
            "The PK9 experience exceeds the verified Level 100 limit for this species/form."
        }
        return pokemon
    }

    private fun targetRecord(
        blocks: ScarletVioletPokemonBlocks,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int
    ): ByteArray {
        val size = ScarletVioletPokemonReadOnly.RECORD_SIZE
        val source: ByteArray
        val offset: Int
        if (area == ScarletVioletPokemonArea.BOX) {
            require(box in 1..32 && position in 1..30) {
                "The selected native PC-box slot is outside storage."
            }
            source = blocks.boxes
            offset = ((box - 1) * 30 + position - 1) * size
        } else {
            require(position in 1..6) { "The selected native party slot is outside the six-slot party." }
            val count = blocks.party[6 * size].toInt() and 255
            require(count in 0..6 && position <= count) {
                "The selected native party slot is not occupied."
            }
            source = blocks.party
            offset = (position - 1) * size
        }
        return source.copyOfRange(offset, offset + size)
    }
}

class ScarletVioletPokemonDuplicateCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    val pokemon: ScarletVioletPokemonRead,
    val sourceArea: ScarletVioletPokemonArea,
    val sourceBox: Int,
    val sourcePosition: Int,
    val destinationBox: Int,
    val destinationPosition: Int,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    val sourceSha256 = ScarletVioletPokemonDuplicateEditing.sha256(this.original)
    val recordSha256 = ScarletVioletPokemonDuplicateEditing.sha256(this.sourceRecord)
    val editedSha256 = ScarletVioletPokemonDuplicateEditing.sha256(this.edited)
    val sourceLocation: String get() = if (sourceArea == ScarletVioletPokemonArea.BOX)
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
            ScarletVioletPokemonDuplicateEditing.verify(
                original, edited, sourceArea, sourceBox, sourcePosition, sourceRecord,
                destinationBox, destinationPosition, absoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Pokémon duplicate."
            }
            ScarletVioletPokemonDuplicateEditing.verify(
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
