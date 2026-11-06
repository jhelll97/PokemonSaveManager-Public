package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class BdspPokemonPartyMoveDirection(val label: String) {
    TO_PARTY("Move to Party"),
    TO_BOX("Move to PC Box")
}

data class BdspPokemonPartyMoveSelection(
    val pokemon: BdspPokemonRead,
    val direction: BdspPokemonPartyMoveDirection,
    val sourceArea: BdspPokemonArea,
    val sourceBox: Int,
    val sourcePosition: Int,
    val destinationBox: Int,
    val destinationPosition: Int,
    val partyCountBefore: Int,
    val partyCountAfter: Int
) {
    val sourceLocation: String get() = if (sourceArea == BdspPokemonArea.BOX)
        "Box $sourceBox • Slot $sourcePosition" else "Party $sourcePosition"
    val destinationLocation: String get() = if (direction == BdspPokemonPartyMoveDirection.TO_PARTY)
        "Party $destinationPosition" else "Box $destinationBox • Slot $destinationPosition"
}

/** Moves one exact native PB8 between BDSP party and PC storage in a reviewed new copy. */
object BdspPokemonPartyMoveEditing {
    private const val PARTY_OFFSET = 0x14098
    private const val BOX_OFFSET = 0x14EF4
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16
    private const val PARTY_CAPACITY = 6

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

    fun inspect(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int
    ): BdspPokemonPartyMoveSelection {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(BdspSaveValidation.inspect(original) is BdspValidation.Verified) {
            "A validated native BDSP save is required."
        }
        val count = partyCount(original)
        requirePartyRecords(original, count)
        val sourceRecord = record(original, area, box, position)
        val pokemon = inspectRecord(sourceRecord)
        return if (area == BdspPokemonArea.BOX) {
            require(count < PARTY_CAPACITY) { "The native BDSP party already contains six Pokémon." }
            BdspPokemonPartyMoveSelection(
                pokemon, BdspPokemonPartyMoveDirection.TO_PARTY,
                area, box, position, 0, count + 1, count, count + 1
            )
        } else {
            require(count > 1) { "At least one Pokémon must remain in the native BDSP party." }
            val storage = BdspStorageReadOnly.read(original)
            val destination = (0 until 40).asSequence()
                .flatMap { current -> storage.box(current).asSequence() }
                .firstOrNull { it.read is BdspSlotRead.Empty }
                ?: throw IllegalArgumentException("All 1,200 native BDSP PC-box slots are occupied.")
            BdspPokemonPartyMoveSelection(
                pokemon, BdspPokemonPartyMoveDirection.TO_BOX,
                area, 0, position, destination.box + 1, destination.position,
                count, count - 1
            )
        }
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: BdspPokemonArea,
        box: Int,
        position: Int
    ): BdspPokemonPartyMoveCopyPlan {
        val original = source.copyOf()
        val selection = inspect(original, expectedSha256, area, box, position)
        val sourceRecord = record(original, area, box, position)
        val edited = desiredBytes(original, selection, sourceRecord)
        refreshDigest(edited)
        verify(original, edited, selection)
        return BdspPokemonPartyMoveCopyPlan(original, edited, sourceRecord, selection)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        selection: BdspPokemonPartyMoveSelection
    ) {
        val beforeValidation = BdspSaveValidation.inspect(original)
        val afterValidation = BdspSaveValidation.inspect(edited)
        require(beforeValidation is BdspValidation.Verified &&
            afterValidation is BdspValidation.Verified &&
            beforeValidation.version == afterValidation.version &&
            beforeValidation.revision == afterValidation.revision &&
            original.size == edited.size) {
            "The moved copy changed native title identity, revision, size, or whole-file MD5."
        }
        require(partyCount(original) == selection.partyCountBefore &&
            partyCount(edited) == selection.partyCountAfter) {
            "The native party count did not reopen with the reviewed value."
        }
        requirePartyRecords(original, selection.partyCountBefore)
        requirePartyRecords(edited, selection.partyCountAfter)
        val sourceRecord = record(
            original, selection.sourceArea, selection.sourceBox, selection.sourcePosition
        )
        require(inspectRecord(sourceRecord) == selection.pokemon) {
            "The selected source PB8 changed before verification."
        }
        if (selection.direction == BdspPokemonPartyMoveDirection.TO_BOX) {
            require(BdspPokemonReadOnly.read(record(
                original, BdspPokemonArea.BOX,
                selection.destinationBox, selection.destinationPosition
            )) is BdspSlotRead.Empty) {
                "The reviewed native PC-box destination is no longer empty."
            }
        }
        val expected = desiredBytes(original, selection, sourceRecord)
        require(edited.indices.all { index ->
            index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH || edited[index] == expected[index]
        }) { "The native party and PC storage did not reopen in the reviewed state." }
        val destinationRecord = record(
            edited,
            if (selection.direction == BdspPokemonPartyMoveDirection.TO_PARTY)
                BdspPokemonArea.PARTY else BdspPokemonArea.BOX,
            selection.destinationBox,
            selection.destinationPosition
        )
        require(destinationRecord.contentEquals(sourceRecord) &&
            inspectRecord(destinationRecord) == selection.pokemon) {
            "The exact selected PB8 did not reopen at its reviewed destination."
        }
        BdspStorageReadOnly.read(edited)

        val size = BdspPokemonReadOnly.RECORD_SIZE
        val boxChangedOffset = if (selection.direction == BdspPokemonPartyMoveDirection.TO_PARTY)
            boxOffset(selection.sourceBox, selection.sourcePosition)
        else boxOffset(selection.destinationBox, selection.destinationPosition)
        val partyChangedStart = PARTY_OFFSET +
            (if (selection.direction == BdspPokemonPartyMoveDirection.TO_PARTY)
                selection.partyCountBefore else selection.sourcePosition - 1) * size
        val partyChangedEnd = PARTY_OFFSET + selection.partyCountBefore * size +
            if (selection.direction == BdspPokemonPartyMoveDirection.TO_PARTY) size else 0
        val countOffset = PARTY_OFFSET + PARTY_CAPACITY * size
        require(original.indices.all { index ->
            index in boxChangedOffset until boxChangedOffset + size ||
                index in partyChangedStart until partyChangedEnd ||
                index == countOffset ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH || original[index] == edited[index]
        }) { "An unrelated Pokémon slot, party byte, or BDSP save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun desiredBytes(
        original: ByteArray,
        selection: BdspPokemonPartyMoveSelection,
        sourceRecord: ByteArray
    ): ByteArray {
        val edited = original.copyOf()
        val size = BdspPokemonReadOnly.RECORD_SIZE
        if (selection.direction == BdspPokemonPartyMoveDirection.TO_PARTY) {
            ByteArray(size).copyInto(
                edited, boxOffset(selection.sourceBox, selection.sourcePosition)
            )
            sourceRecord.copyInto(edited, PARTY_OFFSET + selection.partyCountBefore * size)
        } else {
            sourceRecord.copyInto(
                edited, boxOffset(selection.destinationBox, selection.destinationPosition)
            )
            val sourceIndex = selection.sourcePosition - 1
            for (index in sourceIndex until selection.partyCountBefore - 1) {
                edited.copyInto(
                    edited,
                    PARTY_OFFSET + index * size,
                    PARTY_OFFSET + (index + 1) * size,
                    PARTY_OFFSET + (index + 2) * size
                )
            }
            ByteArray(size).copyInto(
                edited, PARTY_OFFSET + (selection.partyCountBefore - 1) * size
            )
        }
        edited[PARTY_OFFSET + PARTY_CAPACITY * size] = selection.partyCountAfter.toByte()
        return edited
    }

    private fun partyCount(bytes: ByteArray): Int {
        val count = bytes[PARTY_OFFSET + PARTY_CAPACITY * BdspPokemonReadOnly.RECORD_SIZE].toInt() and 255
        require(count in 1..PARTY_CAPACITY) { "The native BDSP party count must be one through six." }
        return count
    }

    private fun requirePartyRecords(bytes: ByteArray, count: Int) {
        require((1..count).all { position ->
            BdspPokemonReadOnly.read(partyRecordBytes(bytes, position)) is BdspSlotRead.Pokemon
        }) { "Every occupied native BDSP party record must pass its PB8 checksum before this move." }
    }

    private fun record(
        bytes: ByteArray,
        area: BdspPokemonArea,
        box: Int,
        position: Int
    ): ByteArray = if (area == BdspPokemonArea.BOX) {
        require(box in 1..40 && position in 1..30) {
            "The selected native BDSP PC-box slot is outside storage."
        }
        val offset = boxOffset(box, position)
        bytes.copyOfRange(offset, offset + BdspPokemonReadOnly.RECORD_SIZE)
    } else {
        require(position in 1..partyCount(bytes)) { "The selected native BDSP party slot is not occupied." }
        partyRecordBytes(bytes, position)
    }

    private fun partyRecordBytes(bytes: ByteArray, position: Int): ByteArray {
        require(position in 1..PARTY_CAPACITY) { "The native BDSP party slot is outside the six-slot party." }
        val offset = PARTY_OFFSET + (position - 1) * BdspPokemonReadOnly.RECORD_SIZE
        return bytes.copyOfRange(offset, offset + BdspPokemonReadOnly.RECORD_SIZE)
    }

    private fun boxOffset(box: Int, position: Int): Int =
        BOX_OFFSET + ((box - 1) * 30 + position - 1) * BdspPokemonReadOnly.RECORD_SIZE

    private fun inspectRecord(record: ByteArray): BdspPokemonRead {
        require(record.size == BdspPokemonReadOnly.RECORD_SIZE) {
            "A complete 344-byte native PB8 record is required."
        }
        val pokemon = (BdspPokemonReadOnly.read(record) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PB8 is not checksum-valid.")
        require(!pokemon.isEgg) { "Egg PB8 records cannot be moved between the party and PC Box." }
        require(pokemon.cachedLevel in 1..100 && pokemon.nature in 0..24 && pokemon.statNature in 0..24) {
            "The selected PB8 Level or nature data is outside BDSP's native limits."
        }
        require(pokemon.currentHandler in 0..1) { "The selected PB8 current-handler identity is invalid." }
        require(pokemon.ivs.all { it in 0..31 } && pokemon.hyperTrainingFlags and 0xC0 == 0) {
            "The selected PB8 IV or hyper-training data exceeds BDSP's native limits."
        }
        require(pokemon.evs.all { it in 0..252 } && pokemon.evs.sum() <= 510) {
            "The selected PB8 EV values exceed BDSP's native per-stat or total limit."
        }
        val maximumExperience = runCatching {
            BdspPokemonLevelEditing.maximumExperience(pokemon)
        }.getOrElse {
            throw IllegalArgumentException(
                "This PB8 species/form has no uniquely verified native BDSP profile."
            )
        }
        require(pokemon.experience <= maximumExperience) {
            "The selected PB8 experience exceeds the verified Level 100 limit for this species/form."
        }
        return pokemon
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

class BdspPokemonPartyMoveCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    val selection: BdspPokemonPartyMoveSelection
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    val sourceSha256 = BdspPokemonPartyMoveEditing.sha256(this.original)
    val sourceRecordSha256 = BdspPokemonPartyMoveEditing.sha256(this.sourceRecord)
    val editedSha256 = BdspPokemonPartyMoveEditing.sha256(this.edited)
    val boxChangedOffset: Int = if (selection.direction == BdspPokemonPartyMoveDirection.TO_PARTY)
        0x14EF4 + ((selection.sourceBox - 1) * 30 + selection.sourcePosition - 1) *
            BdspPokemonReadOnly.RECORD_SIZE
    else 0x14EF4 + ((selection.destinationBox - 1) * 30 + selection.destinationPosition - 1) *
        BdspPokemonReadOnly.RECORD_SIZE
    val partyChangedStart: Int = 0x14098 +
        (if (selection.direction == BdspPokemonPartyMoveDirection.TO_PARTY)
            selection.partyCountBefore else selection.sourcePosition - 1) *
            BdspPokemonReadOnly.RECORD_SIZE
    val partyChangedEndExclusive: Int = 0x14098 +
        selection.partyCountBefore * BdspPokemonReadOnly.RECORD_SIZE +
        if (selection.direction == BdspPokemonPartyMoveDirection.TO_PARTY)
            BdspPokemonReadOnly.RECORD_SIZE else 0
    val partyCountOffset: Int = 0x14098 + 6 * BdspPokemonReadOnly.RECORD_SIZE
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
            BdspPokemonPartyMoveEditing.verify(original, edited, selection)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed party move."
            }
            BdspPokemonPartyMoveEditing.verify(original, persisted, selection)
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
