package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Restores one checksum-valid native PK9 Bank record into a reviewed new Scarlet/Violet save copy. */
object ScarletVioletPseBankRestoreEditing {
    private const val MAX_FILE_SIZE = 0x31CF81
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

    fun inspectBankRecord(record: ByteArray): ScarletVioletPokemonRead {
        require(record.size == ScarletVioletPokemonReadOnly.RECORD_SIZE) {
            "A complete 344-byte native PK9 Bank record is required."
        }
        val pokemon = (ScarletVioletPokemonReadOnly.read(record) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The PSE Bank entry is not a checksum-valid native PK9 Pokémon.")
        require(!pokemon.isEgg) { "Egg PK9 records cannot be restored from PSE Bank." }
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

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        bankRecord: ByteArray
    ): ScarletVioletPseBankRestoreCopyPlan {
        val original = source.copyOf()
        val supplied = bankRecord.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = ScarletVioletStorageReadOnly.read(original)
        val pokemon = inspectBankRecord(supplied)
        val destination = (0 until 32).asSequence().flatMap { box -> before.box(box).asSequence() }
            .firstOrNull { it.read is ScarletVioletSlotRead.Empty }
            ?: throw IllegalArgumentException("All 960 native Scarlet/Violet PC-box slots are occupied.")
        val written = ScarletVioletSaveValidation.writeBoxRecord(
            original,
            destination.box + 1,
            destination.position,
            supplied
        )
        verify(
            original, written.bytes, supplied,
            destination.box + 1, destination.position, written.absoluteOffset
        )
        return ScarletVioletPseBankRestoreCopyPlan(
            original, written.bytes, supplied, pokemon,
            destination.box + 1, destination.position, written.absoluteOffset
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
        val before = ScarletVioletStorageReadOnly.read(original)
        val after = ScarletVioletStorageReadOnly.read(edited)
        require(original.size == edited.size && before.version == after.version) {
            "The restored copy changed native title identity or size."
        }
        require(before.rideLegend == after.rideLegend) {
            "The native ride Pokémon changed during PSE Bank restore."
        }
        require(before.box(box - 1)[position - 1].read is ScarletVioletSlotRead.Empty) {
            "The reviewed native destination is no longer empty."
        }
        val reopened = after.box(box - 1)[position - 1].read as? ScarletVioletSlotRead.Pokemon
            ?: throw IllegalArgumentException("The restored PK9 did not reopen as a Pokémon.")
        require(reopened.value == inspectBankRecord(bankRecord)) {
            "The restored PK9 did not reopen exactly."
        }

        val beforeBlocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val afterBlocks = ScarletVioletSaveValidation.readPokemonBlocks(edited)
        val relativeOffset = ((box - 1) * 30 + position - 1) * ScarletVioletPokemonReadOnly.RECORD_SIZE
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
        }) { "An unrelated encrypted container byte changed, including ride Pokémon data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

class ScarletVioletPseBankRestoreCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    bankRecord: ByteArray,
    val pokemon: ScarletVioletPokemonRead,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val bankRecord = bankRecord.copyOf()
    val sourceSha256 = ScarletVioletPseBankRestoreEditing.sha256(this.original)
    val bankSha256 = ScarletVioletPseBankRestoreEditing.sha256(this.bankRecord)
    val editedSha256 = ScarletVioletPseBankRestoreEditing.sha256(this.edited)
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
            ScarletVioletPseBankRestoreEditing.verify(
                original, edited, bankRecord, box, position, absoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed PSE Bank restore."
            }
            ScarletVioletPseBankRestoreEditing.verify(
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
