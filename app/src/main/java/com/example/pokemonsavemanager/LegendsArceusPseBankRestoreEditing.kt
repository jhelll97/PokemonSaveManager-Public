package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class LegendsArceusPseBankDestination(val pasture: Int, val position: Int)

/** Restores one checksum-valid PA8 Bank record into a reviewed new Legends: Arceus save copy. */
object LegendsArceusPseBankRestoreEditing {
    private const val MAX_FILE_SIZE = 0x13AD06
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
                "The document exceeds the supported Legends: Arceus save size."
            }
        }
        output.toByteArray()
    }

    fun readSaveBytes(input: InputStream): ByteArray = readDocumentBytes(input).also {
        require(LegendsArceusSaveValidation.inspect(it) is LegendsArceusValidation.Verified) {
            "A validated native Legends: Arceus save is required."
        }
        LegendsArceusStorageReadOnly.read(it)
    }

    fun inspectBankRecord(record: ByteArray): LegendsArceusPokemonRead {
        require(record.size == LegendsArceusPokemonReadOnly.STORED_SIZE ||
            record.size == LegendsArceusPokemonReadOnly.PARTY_SIZE) {
            "A complete 360-byte stored or 376-byte party PA8 Bank record is required."
        }
        val pokemon = (LegendsArceusPokemonReadOnly.read(record) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The PSE Bank entry is not a checksum-valid native PA8 Pokémon.")
        require(!pokemon.isEgg) { "Egg PA8 records cannot be restored from PSE Bank." }
        require(!pokemon.isNoble) { "Noble PA8 records have no legal native pasture equivalent." }
        require(pokemon.nature in 0..24 && pokemon.statNature in 0..24) {
            "The PA8 nature data is outside Legends: Arceus native limits."
        }
        require(pokemon.currentHandler in 0..1) { "The PA8 current-handler identity is invalid." }
        require(pokemon.ivs.all { it in 0..31 }) { "The PA8 IV values exceed native limits." }
        require(pokemon.ganbaruValues.all { it in 0..10 }) {
            "The PA8 Ganbaru Values exceed native limits."
        }
        require(pokemon.hyperTrainingFlags and 0xC0 == 0) {
            "The PA8 hyper-training flags contain unsupported bits."
        }
        val maximumExperience = runCatching {
            LegendsArceusPokemonLevelEditing.maximumExperience(pokemon)
        }.getOrElse {
            throw IllegalArgumentException(
                "This PA8 species/form has no uniquely verified native Legends: Arceus profile, so it remains protected."
            )
        }
        require(pokemon.experience <= maximumExperience) {
            "The PA8 experience exceeds the verified Level 100 limit for this species/form."
        }
        require(pokemon.cachedLevel == null || pokemon.cachedLevel in 1..100) {
            "The party-format PA8 cached Level is outside native limits."
        }
        return pokemon
    }

    fun emptyDestinations(source: ByteArray): List<LegendsArceusPseBankDestination> {
        val storage = LegendsArceusStorageReadOnly.read(source.copyOf())
        return (0 until 32).flatMap { pasture ->
            storage.pasture(pasture).filter { it.read is LegendsArceusSlotRead.Empty }
                .map { LegendsArceusPseBankDestination(pasture + 1, it.position) }
        }
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        bankRecord: ByteArray,
        registerObtained: Boolean = false
    ): LegendsArceusPseBankRestoreCopyPlan {
        val destination = emptyDestinations(source).firstOrNull()
            ?: throw IllegalArgumentException("All 960 native Legends: Arceus pasture slots are occupied.")
        return stage(
            source, expectedSha256, bankRecord, destination.pasture, destination.position, registerObtained
        )
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        bankRecord: ByteArray,
        pasture: Int,
        position: Int,
        registerObtained: Boolean = false
    ): LegendsArceusPseBankRestoreCopyPlan {
        val original = source.copyOf()
        val supplied = bankRecord.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(pasture in 1..32 && position in 1..30) {
            "The selected Legends: Arceus pasture destination is outside native storage."
        }
        val before = LegendsArceusStorageReadOnly.read(original)
        require(before.pasture(pasture - 1)[position - 1].read is LegendsArceusSlotRead.Empty) {
            "The selected Legends: Arceus destination is no longer empty. Choose another slot."
        }
        val pokemon = inspectBankRecord(supplied)
        val storedRecord = supplied.copyOfRange(0, LegendsArceusPokemonReadOnly.STORED_SIZE)
        inspectBankRecord(storedRecord)
        val written = LegendsArceusSaveValidation.writePastureRecord(
            original, pasture, position, storedRecord
        )
        val beforeDexEntry = if (registerObtained) {
            LegendsArceusPokedexReadOnly.read(original).entries
                .singleOrNull { it.speciesId == pokemon.species }
                ?: throw IllegalArgumentException(
                    "Species #${pokemon.species} has no reviewed native Hisui Pokédex record."
                )
        } else null
        val dexWritten = if (beforeDexEntry?.numObtained == 0) {
            LegendsArceusSaveValidation.writeDexObtainedCount(
                written.bytes, pokemon.species, 1
            )
        } else null
        val edited = dexWritten?.bytes ?: written.bytes
        verify(
            original, edited, supplied, storedRecord, pokemon.species,
            pasture, position, written.absoluteOffset, dexWritten?.absoluteOffset
        )
        return LegendsArceusPseBankRestoreCopyPlan(
            original, edited, supplied, storedRecord, pokemon,
            pasture, position, written.absoluteOffset, dexWritten?.absoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        bankRecord: ByteArray,
        storedRecord: ByteArray,
        pokemonSpecies: Int,
        pasture: Int,
        position: Int,
        absoluteOffset: Int,
        dexAbsoluteOffset: Int?
    ) {
        val before = LegendsArceusStorageReadOnly.read(original)
        val after = LegendsArceusStorageReadOnly.read(edited)
        require(original.size == edited.size && before.version == after.version) {
            "The restored copy changed native title identity or size."
        }
        require(before.pasture(pasture - 1)[position - 1].read is LegendsArceusSlotRead.Empty) {
            "The reviewed native pasture destination is no longer empty."
        }
        val reopened = after.pasture(pasture - 1)[position - 1].read as? LegendsArceusSlotRead.Pokemon
            ?: throw IllegalArgumentException("The restored PA8 did not reopen as a Pokémon.")
        require(reopened.value == inspectBankRecord(storedRecord)) {
            "The restored PA8 did not reopen exactly."
        }
        require(inspectBankRecord(bankRecord).species == reopened.value.species) {
            "The PSE Bank identity changed during restore verification."
        }

        val beforeBlocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsArceusSaveValidation.readPokemonBlocks(edited)
        val relativeOffset = ((pasture - 1) * 30 + position - 1) * LegendsArceusPokemonReadOnly.STORED_SIZE
        require(afterBlocks.pastures.copyOfRange(relativeOffset, relativeOffset + storedRecord.size)
            .contentEquals(storedRecord)) { "The native PA8 stored core was not preserved exactly." }
        require(beforeBlocks.party.contentEquals(afterBlocks.party)) {
            "Native party data changed during PSE Bank restore."
        }
        require(beforeBlocks.pastures.indices.all { index ->
            index in relativeOffset until relativeOffset + storedRecord.size ||
                beforeBlocks.pastures[index] == afterBlocks.pastures[index]
        }) { "An unrelated native pasture byte changed." }

        val beforeDexBlock = LegendsArceusSaveValidation.readDexBlock(original)
        val afterDexBlock = LegendsArceusSaveValidation.readDexBlock(edited)
        if (dexAbsoluteOffset == null) {
            require(beforeDexBlock.data.contentEquals(afterDexBlock.data)) {
                "Native Hisui Pokédex data changed without a reviewed obtained registration."
            }
        } else {
            val beforeDex = LegendsArceusPokedexReadOnly.read(original)
            val afterDex = LegendsArceusPokedexReadOnly.read(edited)
            val beforeEntry = beforeDex.entries.single { it.speciesId == pokemonSpecies }
            val afterEntry = afterDex.entries.single { it.speciesId == pokemonSpecies }
            require(beforeEntry.numObtained == 0 &&
                afterEntry == beforeEntry.copy(numObtained = 1)) {
                "The native Hisui obtained registration did not reopen exactly."
            }
            require(beforeDex.rawGlobalFlags == afterDex.rawGlobalFlags &&
                beforeDex.totalResearchPoints == afterDex.totalResearchPoints &&
                beforeDex.updateCounter == afterDex.updateCounter &&
                beforeDex.reportCounter == afterDex.reportCounter) {
                "Global Hisui research state changed."
            }
            require(beforeDex.entries.indices.all { index ->
                beforeDex.entries[index].speciesId == pokemonSpecies ||
                    beforeDex.entries[index] == afterDex.entries[index]
            }) { "An unrelated Hisui research record changed." }
            val dexRelativeOffset = 0x70 + pokemonSpecies * 0x58 + 0xA
            require(beforeDexBlock.data.indices.all { index ->
                index in dexRelativeOffset until dexRelativeOffset + 2 ||
                    beforeDexBlock.data[index] == afterDexBlock.data[index]
            }) { "An unrelated native Hisui Pokédex byte changed." }
        }

        val footerOffset = original.size - FOOTER_SIZE
        val dexRange = dexAbsoluteOffset?.let { it until it + 2 }
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + storedRecord.size ||
                dexRange?.contains(index) == true ||
                index in footerOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

class LegendsArceusPseBankRestoreCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    bankRecord: ByteArray,
    storedRecord: ByteArray,
    val pokemon: LegendsArceusPokemonRead,
    val pasture: Int,
    val position: Int,
    val absoluteOffset: Int,
    val dexAbsoluteOffset: Int?
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val bankRecord = bankRecord.copyOf()
    private val storedRecord = storedRecord.copyOf()
    val sourceSha256 = LegendsArceusPseBankRestoreEditing.sha256(this.original)
    val bankSha256 = LegendsArceusPseBankRestoreEditing.sha256(this.bankRecord)
    val editedSha256 = LegendsArceusPseBankRestoreEditing.sha256(this.edited)
    val partyTailBytes = this.bankRecord.size - this.storedRecord.size
    val addedObtainedRegistration: Boolean get() = dexAbsoluteOffset != null
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
            LegendsArceusPseBankRestoreEditing.verify(
                original, edited, bankRecord, storedRecord, pokemon.species,
                pasture, position, absoluteOffset, dexAbsoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed PSE Bank restore."
            }
            LegendsArceusPseBankRestoreEditing.verify(
                original, persisted, bankRecord, storedRecord, pokemon.species,
                pasture, position, absoluteOffset, dexAbsoluteOffset
            )
            requireSource()
            requireBank()
        } catch (failure: Exception) {
            if (started) runCatching { discardDestination() }
            val destinationEmpty = runCatching { readDestination().isEmpty() }.getOrDefault(false)
            val recoveryStatus = when {
                destinationEmpty && started -> " The failed new copy was cleared and verified empty; the recovery backup is retained."
                destinationEmpty -> " The new copy remained empty and was independently verified; the recovery backup is retained."
                else -> " The new copy could not be verified empty. Do not use it; the recovery backup is retained."
            }
            throw IllegalStateException(
                (failure.message ?: "PSE Bank restore verification failed.") +
                    recoveryStatus,
                failure
            )
        }
    }
}
