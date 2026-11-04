package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Imports one supplied native fateful-event PA8 into the first empty Legends: Arceus pasture slot. */
object LegendsArceusEventEditing {
    private const val MAX_FILE_SIZE = 0x13AD06

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
            require(output.size() <= MAX_FILE_SIZE) { "The document exceeds the supported Legends: Arceus save size." }
        }
        output.toByteArray()
    }

    fun readEventRecord(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(512)
        while (true) {
            val remaining = LegendsArceusPokemonReadOnly.STORED_SIZE + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= LegendsArceusPokemonReadOnly.STORED_SIZE) {
                "Select one complete 360-byte native PA8 event record."
            }
        }
        output.toByteArray().also { record ->
            require(record.size == LegendsArceusPokemonReadOnly.STORED_SIZE) {
                "Select one complete 360-byte native PA8 event record."
            }
        }
    }

    fun inspectEvent(record: ByteArray): LegendsArceusPokemonRead {
        val pokemon = (LegendsArceusPokemonReadOnly.read(record) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected file is not a checksum-valid native PA8 Pokémon.")
        require(!pokemon.isEgg && pokemon.fatefulEncounter) {
            "Only a non-Egg PA8 with its native Fateful Encounter flag can be imported from Events."
        }
        return pokemon
    }

    fun stage(source: ByteArray, expectedSha256: String, eventRecord: ByteArray): LegendsArceusEventCopyPlan {
        val original = source.copyOf()
        val supplied = eventRecord.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = LegendsArceusStorageReadOnly.read(original)
        val event = inspectEvent(supplied)
        val destination = (0 until 32).asSequence().flatMap { pasture -> before.pasture(pasture).asSequence() }
            .firstOrNull { it.read is LegendsArceusSlotRead.Empty }
            ?: throw IllegalArgumentException("All 960 native Legends: Arceus pasture slots are occupied.")
        val written = LegendsArceusSaveValidation.writePastureRecord(
            original,
            destination.pasture + 1,
            destination.position,
            supplied
        )
        verify(original, written.bytes, supplied, destination.pasture + 1, destination.position, written.absoluteOffset)
        return LegendsArceusEventCopyPlan(original, written.bytes, supplied, event,
            destination.pasture + 1, destination.position, written.absoluteOffset)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        eventRecord: ByteArray,
        pasture: Int,
        position: Int,
        absoluteOffset: Int
    ) {
        val before = LegendsArceusStorageReadOnly.read(original)
        val after = LegendsArceusStorageReadOnly.read(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The event copy changed native title identity or size."
        }
        require(before.pasture(pasture - 1)[position - 1].read is LegendsArceusSlotRead.Empty) {
            "The reviewed native destination is no longer empty."
        }
        val reopened = after.pasture(pasture - 1)[position - 1].read as? LegendsArceusSlotRead.Pokemon
            ?: throw IllegalArgumentException("The imported event PA8 did not reopen as a Pokémon.")
        require(reopened.value == inspectEvent(eventRecord)) { "The imported event PA8 did not reopen exactly." }

        val beforeBlocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsArceusSaveValidation.readPokemonBlocks(edited)
        val relativeOffset = ((pasture - 1) * 30 + position - 1) * LegendsArceusPokemonReadOnly.STORED_SIZE
        require(afterBlocks.pastures.copyOfRange(relativeOffset, relativeOffset + eventRecord.size)
            .contentEquals(eventRecord)) { "The supplied native event bytes were not preserved exactly." }
        require(beforeBlocks.party.contentEquals(afterBlocks.party)) { "Native party data changed during event import." }
        require(beforeBlocks.pastures.indices.all { index ->
            index in relativeOffset until relativeOffset + eventRecord.size ||
                beforeBlocks.pastures[index] == afterBlocks.pastures[index]
        }) { "An unrelated native pasture byte changed." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + eventRecord.size ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

class LegendsArceusEventCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    eventRecord: ByteArray,
    val event: LegendsArceusPokemonRead,
    val pasture: Int,
    val position: Int,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val eventRecord = eventRecord.copyOf()
    val sourceSha256 = LegendsArceusEventEditing.sha256(this.original)
    val editedSha256 = LegendsArceusEventEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This event review was already used. Reopen Events." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) { "The recovery backup did not verify. Nothing was written." }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LegendsArceusEventEditing.verify(original, edited, eventRecord, pasture, position, absoluteOffset)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed event import." }
            LegendsArceusEventEditing.verify(original, persisted, eventRecord, pasture, position, absoluteOffset)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException((failure.message ?: "Event copy verification failed.") +
                if (removed) " The failed new copy was removed; the recovery backup is retained."
                else " The new copy could not be removed. Do not use it; the recovery backup is retained.", failure)
        }
    }
}
