package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class ScarletVioletPseBankDestination(val box: Int, val position: Int)

internal data class ScarletVioletPseDexRegistration(
    val bytes: ByteArray,
    val region: ScarletVioletDexRegion,
    val number: Int,
    val form: Int,
    val gender: Int,
    val shiny: Boolean,
    val languageIndex: Int,
    val absoluteOffset: Int,
    val relativeOffset: Int,
    val touchedRanges: List<IntRange>
) {
    val absoluteRanges: List<IntRange> get() = touchedRanges.map {
        (absoluteOffset + it.first)..(absoluteOffset + it.last)
    }
}

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

    fun emptyDestinations(source: ByteArray): List<ScarletVioletPseBankDestination> {
        val storage = ScarletVioletStorageReadOnly.read(source.copyOf())
        return (0 until 32).flatMap { box ->
            storage.box(box).filter { it.read is ScarletVioletSlotRead.Empty }.map {
                ScarletVioletPseBankDestination(box + 1, it.position)
            }
        }
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        bankRecord: ByteArray,
        registerCaught: Boolean = false
    ): ScarletVioletPseBankRestoreCopyPlan {
        val destination = emptyDestinations(source).firstOrNull()
            ?: throw IllegalArgumentException("All 960 native Scarlet/Violet PC-box slots are occupied.")
        return stage(
            source, expectedSha256, bankRecord, destination.box, destination.position, registerCaught
        )
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        bankRecord: ByteArray,
        box: Int,
        position: Int,
        registerCaught: Boolean = false
    ): ScarletVioletPseBankRestoreCopyPlan {
        val original = source.copyOf()
        val supplied = bankRecord.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(box in 1..32 && position in 1..30) {
            "The selected Scarlet/Violet PC-box destination is outside native storage."
        }
        val before = ScarletVioletStorageReadOnly.read(original)
        require(before.box(box - 1)[position - 1].read is ScarletVioletSlotRead.Empty) {
            "The selected native destination is no longer empty. Choose another slot."
        }
        val pokemon = inspectBankRecord(supplied)
        val written = ScarletVioletSaveValidation.writeBoxRecord(
            original, box, position, supplied
        )
        val dexRegistration = if (registerCaught) {
            stageCaughtRegistration(written.bytes, pokemon)
        } else null
        val edited = dexRegistration?.bytes ?: written.bytes
        verify(
            original, edited, supplied, pokemon, box, position,
            written.absoluteOffset, dexRegistration
        )
        return ScarletVioletPseBankRestoreCopyPlan(
            original, edited, supplied, pokemon,
            box, position, written.absoluteOffset, dexRegistration
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        bankRecord: ByteArray,
        pokemon: ScarletVioletPokemonRead,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        dexRegistration: ScarletVioletPseDexRegistration?
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

        verifyDexRegistration(original, edited, pokemon, dexRegistration)

        val footerOffset = original.size - FOOTER_SIZE
        val dexRanges = dexRegistration?.absoluteRanges.orEmpty()
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + bankRecord.size ||
                dexRanges.any { index in it } ||
                index in footerOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed, including ride Pokémon data." }
    }

    private fun stageCaughtRegistration(
        source: ByteArray,
        pokemon: ScarletVioletPokemonRead
    ): ScarletVioletPseDexRegistration? {
        require(pokemon.form in 0..31 && pokemon.gender in 0..2) {
            "The converted PK9 form or gender is outside the reviewed Pokédex range."
        }
        val beforeDex = ScarletVioletPokedexReadOnly.read(source)
        val target = beforeDex.regions.asSequence().flatMap { region ->
            region.entries.asSequence().map { Triple(region.region, it.number, it) }
        }.firstOrNull { it.third.speciesId == pokemon.species }
            ?: throw IllegalArgumentException(
                "Species #${pokemon.species} is not present in this Scarlet/Violet save's native Pokédex table."
            )
        val modern = beforeDex.layout == ScarletVioletDexLayout.DLC
        val before = target.third
        if (!modern) require(before.rawState != null && before.rawState in 0L..3L) {
            "This original-layout entry contains an unknown raw state and remains locked."
        }
        val recordSize = if (modern) 0x20 else 0x18
        val relativeOffset = ScarletVioletPokedexReadOnly.nativeSpeciesId(pokemon.species) * recordSize
        val blocks = ScarletVioletSaveValidation.readDexBlocks(source)
        val block = if (modern) blocks.dlc!! else blocks.original
        val originalRecord = block.copyOfRange(relativeOffset, relativeOffset + recordSize)
        val record = originalRecord.copyOf()
        val formBit = 1L shl pokemon.form
        val genderBit = 1 shl pokemon.gender
        val languageIndex = 1
        val touched = mutableListOf<IntRange>()
        if (modern) {
            writeU32(record, 0, before.obtainedForms!! or formBit)
            writeU32(record, 4, before.seenForms or formBit)
            writeU32(record, 8, before.heardForms!! or formBit)
            writeU16(record, 0x10, before.languageFlags or (1 shl languageIndex))
            record[0x12] = (before.genderFlags or genderBit).toByte()
            touched += 0..3
            touched += 4..7
            touched += 8..11
            touched += 0x10..0x11
            touched += 0x12..0x12
            if (pokemon.shiny) {
                record[0x13] = (before.rawShinyFlags or 2).toByte()
                touched += 0x13..0x13
            }
        } else {
            writeU32(record, 0, 3)
            writeU32(record, 4, before.seenForms or formBit)
            writeU16(record, 8, before.genderFlags or genderBit)
            writeU16(record, 0xA, before.languageFlags or (1 shl languageIndex))
            touched += 0..3
            touched += 4..7
            touched += 8..9
            touched += 0xA..0xB
            if (pokemon.shiny) {
                record[0xC] = (before.rawShinyFlags or 1).toByte()
                touched += 0xC..0xC
            }
        }
        if (record.contentEquals(originalRecord)) return null
        val write = ScarletVioletSaveValidation.writeDexRecord(
            source, modern, relativeOffset, record
        )
        ScarletVioletPokedexEditing.verify(
            source, write.bytes, target.first, target.second, pokemon.form, pokemon.gender,
            pokemon.shiny, ScarletVioletDexEditMode.CAUGHT, languageIndex,
            write.absoluteOffset, touched
        )
        return ScarletVioletPseDexRegistration(
            write.bytes, target.first, target.second, pokemon.form, pokemon.gender,
            pokemon.shiny, languageIndex, write.absoluteOffset, relativeOffset, touched
        )
    }

    private fun verifyDexRegistration(
        original: ByteArray,
        edited: ByteArray,
        pokemon: ScarletVioletPokemonRead,
        registration: ScarletVioletPseDexRegistration?
    ) {
        val beforeBlocks = ScarletVioletSaveValidation.readDexBlocks(original)
        val afterBlocks = ScarletVioletSaveValidation.readDexBlocks(edited)
        if (registration == null) {
            val sameDlc = when {
                beforeBlocks.dlc == null && afterBlocks.dlc == null -> true
                beforeBlocks.dlc == null || afterBlocks.dlc == null -> false
                else -> beforeBlocks.dlc.contentEquals(afterBlocks.dlc)
            }
            require(beforeBlocks.original.contentEquals(afterBlocks.original) && sameDlc) {
                "Native Scarlet/Violet Pokédex data changed without a reviewed caught registration."
            }
            return
        }
        val beforeDex = ScarletVioletPokedexReadOnly.read(original)
        val afterDex = ScarletVioletPokedexReadOnly.read(edited)
        require(beforeDex.version == afterDex.version && beforeDex.layout == afterDex.layout &&
            beforeDex.regions.map { it.region } == afterDex.regions.map { it.region }) {
            "The restored copy changed native Pokédex layout or regional identity."
        }
        val target = beforeDex.region(registration.region)?.entry(registration.number)
            ?: throw IllegalArgumentException("The reviewed regional Pokédex entry is unavailable.")
        require(target.speciesId == pokemon.species) {
            "The reviewed Scarlet/Violet Pokédex species changed."
        }
        val formBit = 1L shl registration.form
        val genderBit = 1 shl registration.gender
        fun expected(entry: ScarletVioletDexEntryRead): ScarletVioletDexEntryRead {
            val languageFlags = entry.languageFlags or (1 shl registration.languageIndex)
            val shinyFlags = if (!registration.shiny) entry.rawShinyFlags else
                entry.rawShinyFlags or if (beforeDex.layout == ScarletVioletDexLayout.DLC) 2 else 1
            return if (beforeDex.layout == ScarletVioletDexLayout.DLC) entry.copy(
                obtainedForms = entry.obtainedForms!! or formBit,
                seenForms = entry.seenForms or formBit,
                heardForms = entry.heardForms!! or formBit,
                genderFlags = entry.genderFlags or genderBit,
                languageFlags = languageFlags,
                languages = ScarletVioletPokedexEditing.languageNames.filterIndexed { bit, _ ->
                    languageFlags and (1 shl bit) != 0
                },
                rawShinyFlags = shinyFlags
            ) else entry.copy(
                rawState = 3,
                seenForms = entry.seenForms or formBit,
                genderFlags = entry.genderFlags or genderBit,
                languageFlags = languageFlags,
                languages = ScarletVioletPokedexEditing.languageNames.filterIndexed { bit, _ ->
                    languageFlags and (1 shl bit) != 0
                },
                rawShinyFlags = shinyFlags
            )
        }
        require(beforeDex.regions.indices.all { regionIndex ->
            val beforeEntries = beforeDex.regions[regionIndex].entries
            val afterEntries = afterDex.regions[regionIndex].entries
            beforeEntries.indices.all { index ->
                val beforeEntry = beforeEntries[index]
                afterEntries[index] == if (beforeEntry.speciesId == pokemon.species)
                    expected(beforeEntry) else beforeEntry
            }
        }) { "An unrelated native Scarlet/Violet Pokédex record changed." }
        val afterTarget = afterDex.region(registration.region)!!.entry(registration.number)
        require(afterTarget.caught && afterTarget.seen &&
            afterTarget.seenForms and formBit != 0L &&
            afterTarget.genderFlags and genderBit != 0 &&
            (!registration.shiny || afterTarget.shinySeen)) {
            "The native Scarlet/Violet caught registration did not reopen exactly."
        }
        val beforeBlock = if (beforeDex.layout == ScarletVioletDexLayout.DLC)
            beforeBlocks.dlc!! else beforeBlocks.original
        val afterBlock = if (afterDex.layout == ScarletVioletDexLayout.DLC)
            afterBlocks.dlc!! else afterBlocks.original
        require(beforeBlock.indices.all { index ->
            registration.touchedRanges.any { range ->
                index in (registration.relativeOffset + range.first)..(registration.relativeOffset + range.last)
            } || beforeBlock[index] == afterBlock[index]
        }) { "An unrelated native Scarlet/Violet Pokédex byte changed." }
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
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
    val absoluteOffset: Int,
    dexRegistration: ScarletVioletPseDexRegistration?
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val bankRecord = bankRecord.copyOf()
    private val dexRegistration = dexRegistration?.copy(bytes = dexRegistration.bytes.copyOf())
    val sourceSha256 = ScarletVioletPseBankRestoreEditing.sha256(this.original)
    val bankSha256 = ScarletVioletPseBankRestoreEditing.sha256(this.bankRecord)
    val editedSha256 = ScarletVioletPseBankRestoreEditing.sha256(this.edited)
    val dexAbsoluteRanges: List<IntRange> = this.dexRegistration?.absoluteRanges.orEmpty()
    val addedCaughtRegistration: Boolean get() = this.dexRegistration != null
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
                original, edited, bankRecord, pokemon, box, position,
                absoluteOffset, dexRegistration
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed PSE Bank restore."
            }
            ScarletVioletPseBankRestoreEditing.verify(
                original, persisted, bankRecord, pokemon, box, position,
                absoluteOffset, dexRegistration
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
