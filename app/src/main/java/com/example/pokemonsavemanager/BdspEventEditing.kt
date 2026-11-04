package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Imports one supplied native fateful-event PB8 into the first empty BDSP PC-box slot. */
object BdspEventEditing {
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

    fun readEventRecord(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(512)
        while (true) {
            val remaining = BdspPokemonReadOnly.RECORD_SIZE + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= BdspPokemonReadOnly.RECORD_SIZE) {
                "Select one complete 344-byte native PB8 event record."
            }
        }
        output.toByteArray().also { record ->
            require(record.size == BdspPokemonReadOnly.RECORD_SIZE) {
                "Select one complete 344-byte native PB8 event record."
            }
        }
    }

    fun inspectEvent(record: ByteArray): BdspPokemonRead {
        val pokemon = (BdspPokemonReadOnly.read(record) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected file is not a checksum-valid native PB8 Pokémon.")
        require(!pokemon.isEgg && pokemon.fatefulEncounter) {
            "Only a non-Egg PB8 with its native Fateful Encounter flag can be imported from Events."
        }
        return pokemon
    }

    fun stage(source: ByteArray, expectedSha256: String, eventRecord: ByteArray): BdspEventCopyPlan {
        val original = source.copyOf()
        val supplied = eventRecord.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = BdspStorageReadOnly.read(original)
        val event = inspectEvent(supplied)
        val destination = (0 until 40).asSequence().flatMap { box -> before.box(box).asSequence() }
            .firstOrNull { it.read is BdspSlotRead.Empty }
            ?: throw IllegalArgumentException("All 1,200 native BDSP PC-box slots are occupied.")
        val recordOffset = BOX_OFFSET + (destination.box * 30 + destination.position - 1) * BdspPokemonReadOnly.RECORD_SIZE
        val edited = original.copyOf()
        supplied.copyInto(edited, recordOffset)
        refreshDigest(edited)
        verify(original, edited, supplied, destination.box + 1, destination.position, recordOffset)
        return BdspEventCopyPlan(original, edited, supplied, event,
            destination.box + 1, destination.position, recordOffset)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        eventRecord: ByteArray,
        box: Int,
        position: Int,
        recordOffset: Int
    ) {
        val before = BdspStorageReadOnly.read(original)
        val after = BdspStorageReadOnly.read(edited)
        require(original.size == edited.size && before.version == after.version && before.revision == after.revision) {
            "The event copy changed native title identity, revision, or size."
        }
        require(before.box(box - 1)[position - 1].read is BdspSlotRead.Empty) {
            "The reviewed native destination is no longer empty."
        }
        val reopened = after.box(box - 1)[position - 1].read as? BdspSlotRead.Pokemon
            ?: throw IllegalArgumentException("The imported event PB8 did not reopen as a Pokémon.")
        require(reopened.value == inspectEvent(eventRecord)) { "The imported event PB8 did not reopen exactly." }
        require(edited.copyOfRange(recordOffset, recordOffset + eventRecord.size).contentEquals(eventRecord)) {
            "The supplied native event bytes were not preserved exactly."
        }
        require(original.indices.all { index ->
            index in recordOffset until recordOffset + eventRecord.size ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH || original[index] == edited[index]
        }) { "An unrelated BDSP save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

class BdspEventCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    eventRecord: ByteArray,
    val event: BdspPokemonRead,
    val box: Int,
    val position: Int,
    val recordOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val eventRecord = eventRecord.copyOf()
    val sourceSha256 = BdspEventEditing.sha256(this.original)
    val editedSha256 = BdspEventEditing.sha256(this.edited)
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
            BdspEventEditing.verify(original, edited, eventRecord, box, position, recordOffset)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed event import." }
            BdspEventEditing.verify(original, persisted, eventRecord, box, position, recordOffset)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException((failure.message ?: "Event copy verification failed.") +
                if (removed) " The failed new copy was removed; the recovery backup is retained."
                else " The new copy could not be removed. Do not use it; the recovery backup is retained.", failure)
        }
    }
}
