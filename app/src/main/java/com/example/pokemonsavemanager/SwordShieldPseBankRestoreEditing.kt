package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class SwordShieldPseBankDestination(val box: Int, val position: Int)

data class SwordShieldPseBankDexRegistration(
    val region: SwordShieldDexRegion,
    val number: Int,
    val variant: Int,
    val formIndex: Int,
    val languageIndex: Int,
    val absoluteOffset: Int
)

/** Restores one checksum-valid native PK8 Bank record into a reviewed new Sword/Shield save copy. */
object SwordShieldPseBankRestoreEditing {
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
            require(output.size() <= MAX_FILE_SIZE) {
                "The document exceeds the supported Sword/Shield save size."
            }
        }
        output.toByteArray()
    }

    fun readSaveBytes(input: InputStream): ByteArray = readDocumentBytes(input).also {
        require(SwordShieldSaveValidation.inspect(it) is SwordShieldValidation.Verified) {
            "A validated native Sword/Shield save is required."
        }
    }

    fun inspectBankRecord(record: ByteArray): SwordShieldPokemonRead {
        require(record.size == SwordShieldPokemonReadOnly.RECORD_SIZE) {
            "A complete 344-byte native PK8 Bank record is required."
        }
        val pokemon = (SwordShieldPokemonReadOnly.read(record) as? SwordShieldSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The PSE Bank entry is not a checksum-valid native PK8 Pokémon.")
        require(!pokemon.isEgg) { "Egg PK8 records cannot be restored from PSE Bank." }
        require(pokemon.cachedLevel in 1..100 && pokemon.nature in 0..24 && pokemon.statNature in 0..24) {
            "The PK8 Level or nature data is outside Sword/Shield's native limits."
        }
        require(pokemon.dynamaxLevel in 0..10) { "The PK8 Dynamax Level is outside Sword/Shield's native limit." }
        require(pokemon.evs.all { it in 0..252 } && pokemon.evs.sum() <= 510) {
            "The PK8 EV values exceed Sword/Shield's native per-stat or total limit."
        }
        require(runCatching { SwordShieldPokemonLevelEditing.maximumExperience(pokemon) }.isSuccess) {
            "This PK8 species/form has no uniquely verified native Sword/Shield profile, so it remains protected."
        }
        return pokemon
    }

    fun emptyDestinations(source: ByteArray): List<SwordShieldPseBankDestination> {
        val storage = SwordShieldStorageReadOnly.read(source.copyOf())
        return (0 until 32).flatMap { box ->
            storage.box(box).filter { it.read is SwordShieldSlotRead.Empty }
                .map { SwordShieldPseBankDestination(box + 1, it.position) }
        }
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        bankRecord: ByteArray
    ): SwordShieldPseBankRestoreCopyPlan {
        val destination = emptyDestinations(source).firstOrNull()
            ?: throw IllegalArgumentException("All 960 native Sword/Shield PC-box slots are occupied.")
        return stage(source, expectedSha256, bankRecord, destination.box, destination.position)
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        bankRecord: ByteArray,
        box: Int,
        position: Int,
        registerCaughtEnglish: Boolean = false,
        requireCaughtEnglishRecord: Boolean = true
    ): SwordShieldPseBankRestoreCopyPlan {
        val original = source.copyOf()
        val supplied = bankRecord.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(box in 1..32 && position in 1..30) {
            "The selected Sword/Shield PC-box destination is outside native storage."
        }
        val before = SwordShieldStorageReadOnly.read(original)
        require(before.box(box - 1)[position - 1].read is SwordShieldSlotRead.Empty) {
            "The selected Sword/Shield destination is no longer empty. Choose another slot."
        }
        val pokemon = inspectBankRecord(supplied)
        val written = SwordShieldSaveValidation.writeBoxRecord(
            original, box, position, supplied
        )
        val (edited, dexRegistrations) = if (registerCaughtEnglish) {
            registerCaughtEnglishDex(written.bytes, pokemon, requireCaughtEnglishRecord)
        } else written.bytes to emptyList()
        verify(
            original, edited, supplied, box, position, written.absoluteOffset,
            dexRegistrations
        )
        return SwordShieldPseBankRestoreCopyPlan(
            original, edited, supplied, pokemon,
            box, position, written.absoluteOffset, dexRegistrations
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        bankRecord: ByteArray,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        dexRegistrations: List<SwordShieldPseBankDexRegistration>
    ) {
        val before = SwordShieldStorageReadOnly.read(original)
        val after = SwordShieldStorageReadOnly.read(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The restored copy changed native title identity or size."
        }
        require(before.box(box - 1)[position - 1].read is SwordShieldSlotRead.Empty) {
            "The reviewed native destination is no longer empty."
        }
        val reopened = after.box(box - 1)[position - 1].read as? SwordShieldSlotRead.Pokemon
            ?: throw IllegalArgumentException("The restored PK8 did not reopen as a Pokémon.")
        require(reopened.value == inspectBankRecord(bankRecord)) {
            "The restored PK8 did not reopen exactly."
        }

        val beforeBlocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val afterBlocks = SwordShieldSaveValidation.readPokemonBlocks(edited)
        val relativeOffset = ((box - 1) * 30 + position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
        require(afterBlocks.boxes.copyOfRange(relativeOffset, relativeOffset + bankRecord.size)
            .contentEquals(bankRecord)) { "The native PSE Bank bytes were not preserved exactly." }
        require(beforeBlocks.party.contentEquals(afterBlocks.party)) {
            "Native party data changed during PSE Bank restore."
        }
        require(beforeBlocks.boxes.indices.all { index ->
            index in relativeOffset until relativeOffset + bankRecord.size ||
                beforeBlocks.boxes[index] == afterBlocks.boxes[index]
        }) { "An unrelated native PC-box byte changed." }

        if (dexRegistrations.isNotEmpty()) {
            val beforeDex = SwordShieldPokedexReadOnly.read(original)
            val afterDex = SwordShieldPokedexReadOnly.read(edited)
            require(beforeDex.version == afterDex.version) {
                "The destination Pokédex changed native title identity."
            }
            dexRegistrations.forEach { registration ->
                val beforeEntry = beforeDex.region(registration.region)?.entry(registration.number)
                    ?: error("The original regional Pokédex record is missing.")
                val afterEntry = afterDex.region(registration.region)?.entry(registration.number)
                    ?: error("The restored regional Pokédex record is missing.")
                require(beforeEntry.speciesId == reopened.value.species &&
                    afterEntry.speciesId == reopened.value.species) {
                    "A destination Pokédex record no longer matches the restored species."
                }
                require(registration.formIndex in afterEntry.seenBits[registration.variant] &&
                    afterEntry.caught &&
                    SwordShieldPokedexEditing.languages[registration.languageIndex] in afterEntry.languages) {
                    "The restored Pokémon did not reopen with its native caught registration."
                }
                require(beforeEntry.seenBits.indices.all { group ->
                    if (group == registration.variant) {
                        afterEntry.seenBits[group].toSet() ==
                            beforeEntry.seenBits[group].toSet() + registration.formIndex
                    } else afterEntry.seenBits[group] == beforeEntry.seenBits[group]
                }) { "An unrelated destination seen form changed." }
                require(afterEntry.languages.toSet() == beforeEntry.languages.toSet() +
                    SwordShieldPokedexEditing.languages[registration.languageIndex]) {
                    "An unrelated destination caught-language registration changed."
                }
                require(beforeEntry.displayedFormId == afterEntry.displayedFormId &&
                    beforeEntry.displayedGenderId == afterEntry.displayedGenderId &&
                    beforeEntry.displayGigantamax == afterEntry.displayGigantamax &&
                    beforeEntry.displayShiny == afterEntry.displayShiny &&
                    beforeEntry.caughtGigantamax == afterEntry.caughtGigantamax &&
                    beforeEntry.secondaryGigantamaxFlag == afterEntry.secondaryGigantamaxFlag &&
                    beforeEntry.battledCount == afterEntry.battledCount &&
                    beforeEntry.rawExtra1 == afterEntry.rawExtra1 &&
                    beforeEntry.rawExtra2 == afterEntry.rawExtra2
                ) { "Native destination display, Gigantamax, counter, or extra data changed." }
                val allowedFlagMask = 1L or (1L shl (registration.languageIndex + 2))
                require((beforeEntry.rawFlags xor afterEntry.rawFlags) and allowedFlagMask.inv() == 0L) {
                    "An unrelated native destination Pokédex flag changed."
                }
            }
        }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + bankRecord.size ||
                dexRegistrations.any {
                    index in it.absoluteOffset until it.absoluteOffset + 0x30
                } ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    private fun registerCaughtEnglishDex(
        source: ByteArray,
        pokemon: SwordShieldPokemonRead,
        requireAllocatedRecord: Boolean
    ): Pair<ByteArray, List<SwordShieldPseBankDexRegistration>> {
        require(pokemon.form == 0) {
            "Only the reviewed base form can receive the verified native Pokédex mapping."
        }
        val variant = pokemon.gender.coerceIn(0, 1) + if (pokemon.shiny) 2 else 0
        val formIndex = 0
        val languageIndex = 1
        val allocated = SwordShieldPokedexReadOnly.read(source).regions
        val matches = allocated.flatMap { region ->
            region.entries.filter { it.speciesId == pokemon.species }.map { region to it.number }
        }
        if (matches.isEmpty()) {
            require(!requireAllocatedRecord) {
                "This save has no allocated native Sword/Shield regional Pokédex record for the converted species."
            }
            return source.copyOf() to emptyList()
        }
        var edited = source.copyOf()
        val registrations = matches.map { (regionRead, number) ->
            val blocks = SwordShieldSaveValidation.readDexBlocks(edited)
            val table = when (regionRead.region) {
                SwordShieldDexRegion.GALAR -> blocks.galar
                SwordShieldDexRegion.ARMOR -> blocks.armor
                SwordShieldDexRegion.CROWN -> blocks.crown
            } ?: error("The matched regional Pokédex is no longer allocated.")
            val record = table.copyOfRange((number - 1) * 0x30, number * 0x30)
            val seenOffset = variant * 8 + formIndex / 8
            record[seenOffset] =
                (record[seenOffset].toInt() or (1 shl (formIndex and 7))).toByte()
            var flags = u32(record, 0x20)
            flags = flags or 1L or (1L shl (languageIndex + 2))
            writeU32(record, 0x20, flags)
            val written = SwordShieldSaveValidation.writeDexRecord(
                edited, regionRead.region, number, record
            )
            edited = written.bytes
            SwordShieldPseBankDexRegistration(
                regionRead.region, number, variant, formIndex, languageIndex,
                written.absoluteOffset
            )
        }
        return edited to registrations
    }

    private fun u32(bytes: ByteArray, offset: Int): Long = (0..3).fold(0L) { value, index ->
        value or ((bytes[offset + index].toLong() and 255) shl (index * 8))
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

class SwordShieldPseBankRestoreCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    bankRecord: ByteArray,
    val pokemon: SwordShieldPokemonRead,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    dexRegistrations: List<SwordShieldPseBankDexRegistration>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val bankRecord = bankRecord.copyOf()
    val dexRegistrations: List<SwordShieldPseBankDexRegistration> = dexRegistrations.toList()
    val sourceSha256 = SwordShieldPseBankRestoreEditing.sha256(this.original)
    val bankSha256 = SwordShieldPseBankRestoreEditing.sha256(this.bankRecord)
    val editedSha256 = SwordShieldPseBankRestoreEditing.sha256(this.edited)
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
            SwordShieldPseBankRestoreEditing.verify(
                original, edited, bankRecord, box, position, absoluteOffset,
                dexRegistrations
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed PSE Bank restore."
            }
            SwordShieldPseBankRestoreEditing.verify(
                original, persisted, bankRecord, box, position, absoluteOffset,
                dexRegistrations
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
