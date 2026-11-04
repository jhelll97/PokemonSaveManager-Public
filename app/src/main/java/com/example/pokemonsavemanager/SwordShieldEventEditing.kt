package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Imports one supplied native fateful-event PK8 into the first empty Sword/Shield PC-box slot. */
object SwordShieldEventEditing {
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
            require(output.size() <= MAX_FILE_SIZE) { "The document exceeds the supported Sword/Shield save size." }
        }
        output.toByteArray()
    }

    fun readEventRecord(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(512)
        while (true) {
            val remaining = SwordShieldPokemonReadOnly.RECORD_SIZE + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= SwordShieldPokemonReadOnly.RECORD_SIZE) {
                "Select one complete 344-byte native PK8 event record."
            }
        }
        output.toByteArray().also { record ->
            require(record.size == SwordShieldPokemonReadOnly.RECORD_SIZE) {
                "Select one complete 344-byte native PK8 event record."
            }
        }
    }

    fun inspectEvent(record: ByteArray): SwordShieldPokemonRead {
        val pokemon = (SwordShieldPokemonReadOnly.read(record) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected file is not a checksum-valid native PK8 Pokémon.")
        require(!pokemon.isEgg && pokemon.fatefulEncounter) {
            "Only a non-Egg PK8 with its native Fateful Encounter flag can be imported from Events."
        }
        return pokemon
    }

    fun stage(source: ByteArray, expectedSha256: String, eventRecord: ByteArray): SwordShieldEventCopyPlan {
        val original = source.copyOf()
        val supplied = eventRecord.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = SwordShieldStorageReadOnly.read(original)
        val event = inspectEvent(supplied)
        val destination = (0 until 32).asSequence().flatMap { box -> before.box(box).asSequence() }
            .firstOrNull { it.read is SwordShieldSlotRead.Empty }
            ?: throw IllegalArgumentException("All 960 native Sword/Shield PC-box slots are occupied.")
        val written = SwordShieldSaveValidation.writeBoxRecord(
            original,
            destination.box + 1,
            destination.position,
            supplied
        )
        verify(original, written.bytes, supplied, destination.box + 1, destination.position, written.absoluteOffset)
        return SwordShieldEventCopyPlan(original, written.bytes, supplied, event,
            destination.box + 1, destination.position, written.absoluteOffset)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        eventRecord: ByteArray,
        box: Int,
        position: Int,
        absoluteOffset: Int
    ) {
        val before = SwordShieldStorageReadOnly.read(original)
        val after = SwordShieldStorageReadOnly.read(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The event copy changed native title identity or size."
        }
        require(before.box(box - 1)[position - 1].read is SwordShieldSlotRead.Empty) {
            "The reviewed native destination is no longer empty."
        }
        val reopened = after.box(box - 1)[position - 1].read as? SwordShieldSlotRead.Pokemon
            ?: throw IllegalArgumentException("The imported event PK8 did not reopen as a Pokémon.")
        require(reopened.value == inspectEvent(eventRecord)) { "The imported event PK8 did not reopen exactly." }

        val beforeBlocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val afterBlocks = SwordShieldSaveValidation.readPokemonBlocks(edited)
        val relativeOffset = ((box - 1) * 30 + position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
        require(afterBlocks.boxes.copyOfRange(relativeOffset, relativeOffset + eventRecord.size)
            .contentEquals(eventRecord)) { "The supplied native event bytes were not preserved exactly." }
        require(beforeBlocks.party.contentEquals(afterBlocks.party)) { "Native party data changed during event import." }
        require(beforeBlocks.boxes.indices.all { index ->
            index in relativeOffset until relativeOffset + eventRecord.size ||
                beforeBlocks.boxes[index] == afterBlocks.boxes[index]
        }) { "An unrelated native PC-box byte changed." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + eventRecord.size ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

class SwordShieldEventCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    eventRecord: ByteArray,
    val event: SwordShieldPokemonRead,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val eventRecord = eventRecord.copyOf()
    val sourceSha256 = SwordShieldEventEditing.sha256(this.original)
    val editedSha256 = SwordShieldEventEditing.sha256(this.edited)
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
            SwordShieldEventEditing.verify(original, edited, eventRecord, box, position, absoluteOffset)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed event import." }
            SwordShieldEventEditing.verify(original, persisted, eventRecord, box, position, absoluteOffset)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException((failure.message ?: "Event copy verification failed.") +
                if (removed) " The failed new copy was removed; the recovery backup is retained."
                else " The new copy could not be removed. Do not use it; the recovery backup is retained.", failure)
        }
    }
}
