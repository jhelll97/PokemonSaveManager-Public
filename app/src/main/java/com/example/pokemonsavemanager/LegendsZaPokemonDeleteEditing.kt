package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class LegendsZaPokemonDeleteSelection(
    val pokemon: LegendsZaPokemonRead,
    val sourceArea: LegendsZaPokemonArea,
    val sourceBox: Int,
    val sourcePosition: Int,
    val partyCountBefore: Int,
    val partyCountAfter: Int
) {
    val sourceLocation: String get() = if (sourceArea == LegendsZaPokemonArea.BOX)
        "Box $sourceBox • Slot $sourcePosition" else "Party $sourcePosition"
}

/** Deletes one native PA9 from Legends: Z-A party or PC storage in a reviewed new copy. */
object LegendsZaPokemonDeleteEditing {
    private const val MAX_FILE_SIZE = 0x309FB3
    private const val PARTY_CAPACITY = 6

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

    fun inspect(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsZaPokemonArea,
        box: Int,
        position: Int
    ): LegendsZaPokemonDeleteSelection {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = LegendsZaSaveValidation.readPokemonBlocks(original)
        val count = partyCount(blocks)
        requirePartyRecords(blocks, count)
        val pokemon = inspectRecord(targetRecord(blocks, area, box, position))
        val after = if (area == LegendsZaPokemonArea.PARTY) {
            require(count > 1) { "At least one Pokémon must remain in the native Legends: Z-A party." }
            count - 1
        } else count
        return LegendsZaPokemonDeleteSelection(pokemon, area, box, position, count, after)
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsZaPokemonArea,
        box: Int,
        position: Int
    ): LegendsZaPokemonDeleteCopyPlan {
        val original = source.copyOf()
        val selection = inspect(original, expectedSha256, area, box, position)
        val before = LegendsZaSaveValidation.readPokemonBlocks(original)
        val sourceRecord = targetRecord(before, area, box, position)
        val boxes = before.boxes.copyOf()
        val party = before.party.copyOf()
        if (area == LegendsZaPokemonArea.BOX) {
            val start = boxOffset(box, position)
            boxes.fill(0, start, start + LegendsZaPokemonReadOnly.RECORD_SIZE)
        } else compactParty(party, selection)
        val written = LegendsZaSaveValidation.writePokemonBlocks(original, boxes, party)
        verify(
            original, written.bytes, selection,
            written.boxesAbsoluteOffset, written.partyAbsoluteOffset
        )
        return LegendsZaPokemonDeleteCopyPlan(
            original, written.bytes, sourceRecord, selection,
            written.boxesAbsoluteOffset, written.partyAbsoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        selection: LegendsZaPokemonDeleteSelection,
        boxesAbsoluteOffset: Int,
        partyAbsoluteOffset: Int
    ) {
        val beforeStorage = LegendsZaStorageReadOnly.read(original)
        val afterStorage = LegendsZaStorageReadOnly.read(edited)
        require(beforeStorage.version == afterStorage.version &&
            beforeStorage.revision == afterStorage.revision && original.size == edited.size) {
            "The deleted copy changed native title/revision identity or save size."
        }
        val before = LegendsZaSaveValidation.readPokemonBlocks(original)
        val after = LegendsZaSaveValidation.readPokemonBlocks(edited)
        require(partyCount(before) == selection.partyCountBefore &&
            partyCount(after) == selection.partyCountAfter) {
            "The native party count did not reopen with the reviewed value."
        }
        requirePartyRecords(before, selection.partyCountBefore)
        requirePartyRecords(after, selection.partyCountAfter)
        val sourceRecord = targetRecord(
            before, selection.sourceArea, selection.sourceBox, selection.sourcePosition
        )
        require(inspectRecord(sourceRecord) == selection.pokemon) {
            "The selected source PA9 changed before verification."
        }
        val size = LegendsZaPokemonReadOnly.RECORD_SIZE
        if (selection.sourceArea == LegendsZaPokemonArea.BOX) {
            val relativeOffset = boxOffset(selection.sourceBox, selection.sourcePosition)
            require(LegendsZaPokemonReadOnly.read(after.boxes.copyOfRange(
                relativeOffset, relativeOffset + size
            )) is LegendsZaSlotRead.Empty) {
                "The reviewed native PC-box slot did not reopen empty."
            }
            require(before.party.contentEquals(after.party)) {
                "Native party data changed during boxed Pokémon deletion."
            }
            require(before.boxes.indices.all { index ->
                index in relativeOffset until relativeOffset + size ||
                    before.boxes[index] == after.boxes[index]
            }) { "An unrelated native PC-box body or 64-byte slot footer changed." }
            val hashOffset = original.size - 32
            val absoluteOffset = boxesAbsoluteOffset + relativeOffset
            require(original.indices.all { index ->
                index in absoluteOffset until absoluteOffset + size ||
                    index in hashOffset until original.size || original[index] == edited[index]
            }) {
                "An unrelated encrypted Legends: Z-A save byte changed, including a native slot footer."
            }
        } else {
            require(before.boxes.contentEquals(after.boxes)) {
                "Native PC-box data changed during party Pokémon deletion."
            }
            val desired = before.party.copyOf().also { compactParty(it, selection) }
            require(after.party.contentEquals(desired)) {
                "The native party did not reopen in the reviewed compacted state."
            }
            val sourceIndex = selection.sourcePosition - 1
            val stride = LegendsZaStorageReadOnly.PARTY_STRIDE
            require((sourceIndex until selection.partyCountBefore - 1).all { oldIndex ->
                val oldStart = (oldIndex + 1) * stride
                val newStart = oldIndex * stride
                before.party.copyOfRange(oldStart, oldStart + size)
                    .contentEquals(after.party.copyOfRange(newStart, newStart + size))
            }) { "A retained native party PA9 body changed during compaction." }
            val clearedStart = (selection.partyCountBefore - 1) * stride
            require(after.party.copyOfRange(clearedStart, clearedStart + size).all { it == 0.toByte() }) {
                "The released native party PA9 body was not cleared."
            }
            require((0 until PARTY_CAPACITY).all { index ->
                val tailStart = index * stride + size
                before.party.copyOfRange(tailStart, (index + 1) * stride)
                    .contentEquals(after.party.copyOfRange(tailStart, (index + 1) * stride))
            }) { "A native 136-byte party tail changed during deletion." }
            val hashOffset = original.size - 32
            val changedBodies = (sourceIndex until selection.partyCountBefore).map {
                partyAbsoluteOffset + it * stride
            }
            require(original.indices.all { index ->
                changedBodies.any { start -> index in start until start + size } ||
                    index in hashOffset until original.size || original[index] == edited[index]
            }) {
                "An unrelated encrypted Legends: Z-A save byte changed, including a native party tail."
            }
        }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun compactParty(
        party: ByteArray,
        selection: LegendsZaPokemonDeleteSelection
    ) {
        require(selection.sourceArea == LegendsZaPokemonArea.PARTY) {
            "Party compaction requires a native party source."
        }
        val size = LegendsZaPokemonReadOnly.RECORD_SIZE
        val stride = LegendsZaStorageReadOnly.PARTY_STRIDE
        val sourceIndex = selection.sourcePosition - 1
        for (index in sourceIndex until selection.partyCountBefore - 1) {
            party.copyInto(
                party,
                index * stride,
                (index + 1) * stride,
                (index + 1) * stride + size
            )
        }
        party.fill(
            0,
            (selection.partyCountBefore - 1) * stride,
            (selection.partyCountBefore - 1) * stride + size
        )
    }

    private fun partyCount(blocks: LegendsZaPokemonBlocks): Int {
        val count = (0 until PARTY_CAPACITY).firstOrNull {
            !LegendsZaStorageReadOnly.hasNativePresence(
                blocks.party, it * LegendsZaStorageReadOnly.PARTY_STRIDE
            )
        } ?: PARTY_CAPACITY
        require(count in 1..PARTY_CAPACITY && (count until PARTY_CAPACITY).all {
            !LegendsZaStorageReadOnly.hasNativePresence(
                blocks.party, it * LegendsZaStorageReadOnly.PARTY_STRIDE
            )
        }) { "The native Legends: Z-A party must be one contiguous prefix of one through six Pokémon." }
        return count
    }

    private fun requirePartyRecords(blocks: LegendsZaPokemonBlocks, count: Int) {
        require((1..count).all { position ->
            LegendsZaPokemonReadOnly.read(partyRecordBytes(blocks, position)) is LegendsZaSlotRead.Pokemon
        }) { "Every occupied native Legends: Z-A party record must pass its PA9 checksum before deletion." }
    }

    private fun targetRecord(
        blocks: LegendsZaPokemonBlocks,
        area: LegendsZaPokemonArea,
        box: Int,
        position: Int
    ): ByteArray = if (area == LegendsZaPokemonArea.BOX) boxRecord(blocks, box, position)
        else partyRecord(blocks, position)

    private fun boxRecord(blocks: LegendsZaPokemonBlocks, box: Int, position: Int): ByteArray {
        require(box in 1..32 && position in 1..30) {
            "The selected native Legends: Z-A PC-box slot is outside storage."
        }
        val offset = boxOffset(box, position)
        return blocks.boxes.copyOfRange(offset, offset + LegendsZaPokemonReadOnly.RECORD_SIZE)
    }

    private fun partyRecord(blocks: LegendsZaPokemonBlocks, position: Int): ByteArray {
        val count = partyCount(blocks)
        require(position in 1..count) { "The selected native Legends: Z-A party slot is not occupied." }
        return partyRecordBytes(blocks, position)
    }

    private fun partyRecordBytes(blocks: LegendsZaPokemonBlocks, position: Int): ByteArray {
        require(position in 1..PARTY_CAPACITY) { "The native Legends: Z-A party slot is outside the six-slot party." }
        val offset = (position - 1) * LegendsZaStorageReadOnly.PARTY_STRIDE
        return blocks.party.copyOfRange(offset, offset + LegendsZaPokemonReadOnly.RECORD_SIZE)
    }

    private fun boxOffset(box: Int, position: Int): Int =
        ((box - 1) * 30 + position - 1) * LegendsZaStorageReadOnly.BOX_STRIDE

    private fun inspectRecord(record: ByteArray): LegendsZaPokemonRead {
        require(record.size == LegendsZaPokemonReadOnly.RECORD_SIZE) {
            "A complete 344-byte native PA9 record is required."
        }
        val pokemon = (LegendsZaPokemonReadOnly.read(record) as? LegendsZaSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA9 is not checksum-valid.")
        require(!pokemon.isEgg) { "Egg PA9 records remain protected from deletion." }
        require(pokemon.cachedLevel in 1..100 && pokemon.nature in 0..24 && pokemon.statNature in 0..24) {
            "The selected PA9 Level or nature data is outside Legends: Z-A native limits."
        }
        require(pokemon.currentHandler in 0..1) {
            "The selected PA9 current-handler identity is invalid."
        }
        require(pokemon.evs.all { it in 0..252 } && pokemon.evs.sum() <= 510) {
            "The selected PA9 EV values exceed Legends: Z-A's native per-stat or total limit."
        }
        require(pokemon.ivs.all { it in 0..31 }) {
            "The selected PA9 IV values exceed native limits."
        }
        require(pokemon.hyperTrainingFlags and 0xC0 == 0) {
            "The selected PA9 hyper-training flags contain unsupported bits."
        }
        val maximumExperience = runCatching {
            LegendsZaPokemonLevelEditing.maximumExperience(pokemon)
        }.getOrElse {
            throw IllegalArgumentException(
                "This PA9 species/form has no uniquely verified native Legends: Z-A profile."
            )
        }
        require(pokemon.experience <= maximumExperience) {
            "The selected PA9 experience exceeds the verified Level 100 limit for this species/form."
        }
        return pokemon
    }
}

class LegendsZaPokemonDeleteCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    sourceRecord: ByteArray,
    val selection: LegendsZaPokemonDeleteSelection,
    val boxesAbsoluteOffset: Int,
    val partyAbsoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val sourceRecord = sourceRecord.copyOf()
    val sourceSha256 = LegendsZaPokemonDeleteEditing.sha256(this.original)
    val sourceRecordSha256 = LegendsZaPokemonDeleteEditing.sha256(this.sourceRecord)
    val editedSha256 = LegendsZaPokemonDeleteEditing.sha256(this.edited)
    val changedAbsoluteBodyOffsets: List<Int> = if (
        selection.sourceArea == LegendsZaPokemonArea.BOX
    ) listOf(
        boxesAbsoluteOffset + ((selection.sourceBox - 1) * 30 + selection.sourcePosition - 1) *
            LegendsZaStorageReadOnly.BOX_STRIDE
    ) else (selection.sourcePosition - 1 until selection.partyCountBefore).map {
        partyAbsoluteOffset + it * LegendsZaStorageReadOnly.PARTY_STRIDE
    }
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
            LegendsZaPokemonDeleteEditing.verify(
                original, edited, selection, boxesAbsoluteOffset, partyAbsoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Pokémon deletion."
            }
            LegendsZaPokemonDeleteEditing.verify(
                original, persisted, selection, boxesAbsoluteOffset, partyAbsoluteOffset
            )
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
