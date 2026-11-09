package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class LegendsZaPseBankDestination(val box: Int, val position: Int)

/** Restores one checksum-valid native PA9 Bank record into a reviewed new Legends: Z-A save copy. */
object LegendsZaPseBankRestoreEditing {
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

    fun inspectBankRecord(record: ByteArray): LegendsZaPokemonRead {
        require(record.size == LegendsZaPokemonReadOnly.RECORD_SIZE) {
            "A complete 344-byte native PA9 Bank record is required."
        }
        val pokemon = (LegendsZaPokemonReadOnly.read(record) as? LegendsZaSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The PSE Bank entry is not a checksum-valid native PA9 Pokémon.")
        require(!pokemon.isEgg) { "Egg PA9 records cannot be restored from PSE Bank." }
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

    fun emptyDestinations(source: ByteArray): List<LegendsZaPseBankDestination> {
        val storage = LegendsZaStorageReadOnly.read(source.copyOf())
        return (0 until 32).flatMap { box ->
            storage.box(box).filter { it.read is LegendsZaSlotRead.Empty }
                .map { LegendsZaPseBankDestination(box + 1, it.position) }
        }
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        bankRecord: ByteArray
    ): LegendsZaPseBankRestoreCopyPlan {
        val destination = emptyDestinations(source).firstOrNull()
            ?: throw IllegalArgumentException("All 960 native Legends: Z-A PC-box slots are occupied.")
        return stage(source, expectedSha256, bankRecord, destination.box, destination.position)
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        bankRecord: ByteArray,
        box: Int,
        position: Int
    ): LegendsZaPseBankRestoreCopyPlan {
        val original = source.copyOf()
        val supplied = bankRecord.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(box in 1..32 && position in 1..30) {
            "The selected Legends: Z-A PC-box destination is outside native storage."
        }
        val before = LegendsZaStorageReadOnly.read(original)
        require(before.box(box - 1)[position - 1].read is LegendsZaSlotRead.Empty) {
            "The selected Legends: Z-A destination is no longer empty. Choose another slot."
        }
        val pokemon = inspectBankRecord(supplied)
        val written = LegendsZaSaveValidation.writeBoxRecord(
            original, box, position, supplied
        )
        verify(
            original, written.bytes, supplied, box, position, written.absoluteOffset
        )
        return LegendsZaPseBankRestoreCopyPlan(
            original, written.bytes, supplied, pokemon, box, position, written.absoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        bankRecord: ByteArray,
        box: Int,
        position: Int,
        absoluteOffset: Int
    ) {
        val before = LegendsZaStorageReadOnly.read(original)
        val after = LegendsZaStorageReadOnly.read(edited)
        require(original.size == edited.size && before.version == after.version &&
            before.revision == after.revision) {
            "The restored copy changed native title/revision identity or size."
        }
        require(before.box(box - 1)[position - 1].read is LegendsZaSlotRead.Empty) {
            "The reviewed native destination is no longer empty."
        }
        val reopened = after.box(box - 1)[position - 1].read as? LegendsZaSlotRead.Pokemon
            ?: throw IllegalArgumentException("The restored PA9 did not reopen as a Pokémon.")
        require(reopened.value == inspectBankRecord(bankRecord)) {
            "The restored PA9 did not reopen exactly."
        }

        val beforeBlocks = LegendsZaSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsZaSaveValidation.readPokemonBlocks(edited)
        val relativeOffset = ((box - 1) * 30 + position - 1) * LegendsZaStorageReadOnly.BOX_STRIDE
        require(afterBlocks.boxes.copyOfRange(relativeOffset, relativeOffset + bankRecord.size)
            .contentEquals(bankRecord)) { "The native PSE Bank bytes were not preserved exactly." }
        require(beforeBlocks.party.contentEquals(afterBlocks.party)) {
            "Native party data changed during PSE Bank restore."
        }
        require(beforeBlocks.boxes.indices.all { index ->
            index in relativeOffset until relativeOffset + bankRecord.size ||
                beforeBlocks.boxes[index] == afterBlocks.boxes[index]
        }) { "An unrelated native PC-box byte changed." }

        val footerOffset = original.size - FOOTER_SIZE
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + bankRecord.size ||
                index in footerOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

class LegendsZaPseBankRestoreCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    bankRecord: ByteArray,
    val pokemon: LegendsZaPokemonRead,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val bankRecord = bankRecord.copyOf()
    val sourceSha256 = LegendsZaPseBankRestoreEditing.sha256(this.original)
    val bankSha256 = LegendsZaPseBankRestoreEditing.sha256(this.bankRecord)
    val editedSha256 = LegendsZaPseBankRestoreEditing.sha256(this.edited)
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
            LegendsZaPseBankRestoreEditing.verify(
                original, edited, bankRecord, box, position, absoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed PSE Bank restore."
            }
            LegendsZaPseBankRestoreEditing.verify(
                original, persisted, bankRecord, box, position, absoluteOffset
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
