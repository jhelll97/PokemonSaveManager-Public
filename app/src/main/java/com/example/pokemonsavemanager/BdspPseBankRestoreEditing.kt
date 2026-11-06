package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Restores one checksum-valid native PB8 Bank record into a reviewed new BDSP save copy. */
object BdspPseBankRestoreEditing {
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

    fun inspectBankRecord(record: ByteArray): BdspPokemonRead {
        require(record.size == BdspPokemonReadOnly.RECORD_SIZE) {
            "A complete 344-byte native PB8 Bank record is required."
        }
        val pokemon = (BdspPokemonReadOnly.read(record) as? BdspSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The PSE Bank entry is not a checksum-valid native PB8 Pokémon.")
        require(!pokemon.isEgg) { "Egg PB8 records cannot be restored from PSE Bank." }
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

    fun stage(source: ByteArray, expectedSha256: String, bankRecord: ByteArray): BdspPseBankRestoreCopyPlan {
        val original = source.copyOf()
        val supplied = bankRecord.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = BdspStorageReadOnly.read(original)
        val pokemon = inspectBankRecord(supplied)
        val destination = (0 until 40).asSequence().flatMap { box -> before.box(box).asSequence() }
            .firstOrNull { it.read is BdspSlotRead.Empty }
            ?: throw IllegalArgumentException("All 1,200 native BDSP PC-box slots are occupied.")
        val recordOffset = BOX_OFFSET +
            (destination.box * 30 + destination.position - 1) * BdspPokemonReadOnly.RECORD_SIZE
        val edited = original.copyOf()
        supplied.copyInto(edited, recordOffset)
        refreshDigest(edited)
        verify(original, edited, supplied, destination.box + 1, destination.position, recordOffset)
        return BdspPseBankRestoreCopyPlan(
            original, edited, supplied, pokemon,
            destination.box + 1, destination.position, recordOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        bankRecord: ByteArray,
        box: Int,
        position: Int,
        recordOffset: Int
    ) {
        val before = BdspStorageReadOnly.read(original)
        val after = BdspStorageReadOnly.read(edited)
        require(original.size == edited.size && before.version == after.version &&
            before.revision == after.revision) {
            "The restored copy changed native title identity, revision, or size."
        }
        require(before.box(box - 1)[position - 1].read is BdspSlotRead.Empty) {
            "The reviewed native destination is no longer empty."
        }
        val reopened = after.box(box - 1)[position - 1].read as? BdspSlotRead.Pokemon
            ?: throw IllegalArgumentException("The restored PB8 did not reopen as a Pokémon.")
        require(reopened.value == inspectBankRecord(bankRecord)) {
            "The restored PB8 did not reopen exactly."
        }
        require(edited.copyOfRange(recordOffset, recordOffset + bankRecord.size)
            .contentEquals(bankRecord)) { "The native PSE Bank bytes were not preserved exactly." }
        require(original.indices.all { index ->
            index in recordOffset until recordOffset + bankRecord.size ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[index] == edited[index]
        }) { "An unrelated BDSP save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

class BdspPseBankRestoreCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    bankRecord: ByteArray,
    val pokemon: BdspPokemonRead,
    val box: Int,
    val position: Int,
    val recordOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val bankRecord = bankRecord.copyOf()
    val sourceSha256 = BdspPseBankRestoreEditing.sha256(this.original)
    val bankSha256 = BdspPseBankRestoreEditing.sha256(this.bankRecord)
    val editedSha256 = BdspPseBankRestoreEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        readBankRecord: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This PSE Bank restore review was already used. Reopen PSE Bank." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        fun requireBank() = require(readBankRecord().contentEquals(bankRecord)) {
            "The PSE Bank entry changed. Reopen PSE Bank."
        }
        requireSource()
        requireBank()
        require(readDestination().isEmpty()) {
            "Choose a new empty document; existing files cannot be replaced."
        }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) {
            "The recovery backup did not verify. Nothing was written."
        }
        requireSource()
        requireBank()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            BdspPseBankRestoreEditing.verify(
                original, edited, bankRecord, box, position, recordOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed PSE Bank restore."
            }
            BdspPseBankRestoreEditing.verify(
                original, persisted, bankRecord, box, position, recordOffset
            )
            requireSource()
            requireBank()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "PSE Bank restore verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
