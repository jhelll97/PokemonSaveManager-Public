package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class BdspPseBankDestination(val box: Int, val position: Int)

data class BdspPseBankDexRegistration(
    val speciesId: Int,
    val variants: List<Int>,
    val languageIndex: Int,
    val touchedOffsets: List<Int>
)

/** Restores one checksum-valid native PB8 Bank record into a reviewed new BDSP save copy. */
object BdspPseBankRestoreEditing {
    private const val BOX_OFFSET = 0x14EF4
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16
    private const val DEX_OFFSET = 0x7A328
    private val dexVariantOffsets = listOf(0x7B4, 0xF68, 0x171C, 0x1ED0)
    private val dexFormCounts = listOf(
        201 to 28, 351 to 4, 386 to 4, 412 to 3, 413 to 3, 414 to 3,
        421 to 2, 422 to 2, 423 to 2, 479 to 6, 487 to 2, 492 to 2, 493 to 18
    )

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

    fun emptyDestinations(source: ByteArray): List<BdspPseBankDestination> {
        val storage = BdspStorageReadOnly.read(source.copyOf())
        return (0 until 40).flatMap { box ->
            storage.box(box).filter { it.read is BdspSlotRead.Empty }
                .map { BdspPseBankDestination(box + 1, it.position) }
        }
    }

    fun stage(source: ByteArray, expectedSha256: String, bankRecord: ByteArray): BdspPseBankRestoreCopyPlan {
        val destination = emptyDestinations(source).firstOrNull()
            ?: throw IllegalArgumentException("All 1,200 native BDSP PC-box slots are occupied.")
        return stage(source, expectedSha256, bankRecord, destination.box, destination.position)
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        bankRecord: ByteArray,
        box: Int,
        position: Int,
        registerCaughtEnglish: Boolean = false
    ): BdspPseBankRestoreCopyPlan {
        val original = source.copyOf()
        val supplied = bankRecord.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(box in 1..40 && position in 1..30) {
            "The selected BDSP PC-box destination is outside native storage."
        }
        val before = BdspStorageReadOnly.read(original)
        require(before.box(box - 1)[position - 1].read is BdspSlotRead.Empty) {
            "The selected BDSP destination is no longer empty. Choose another slot."
        }
        val pokemon = inspectBankRecord(supplied)
        val recordOffset = BOX_OFFSET +
            ((box - 1) * 30 + position - 1) * BdspPokemonReadOnly.RECORD_SIZE
        val edited = original.copyOf()
        supplied.copyInto(edited, recordOffset)
        val dexRegistration = if (registerCaughtEnglish) {
            registerCaughtEnglishDex(original, edited, pokemon)
        } else null
        refreshDigest(edited)
        verify(
            original, edited, supplied, box, position, recordOffset,
            dexRegistration
        )
        return BdspPseBankRestoreCopyPlan(
            original, edited, supplied, pokemon, box, position, recordOffset,
            dexRegistration
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        bankRecord: ByteArray,
        box: Int,
        position: Int,
        recordOffset: Int,
        dexRegistration: BdspPseBankDexRegistration?
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
        dexRegistration?.let { registration ->
            val beforeDex = BdspPokedexReadOnly.read(original)
            val afterDex = BdspPokedexReadOnly.read(edited)
            require(beforeDex.version == afterDex.version &&
                beforeDex.revision == afterDex.revision) {
                "The destination Pokédex changed native title identity or revision."
            }
            val beforeEntry = beforeDex.entry(registration.speciesId)
            val afterEntry = afterDex.entry(registration.speciesId)
            require(afterEntry.caught &&
                BdspPokedexEditing.languages[registration.languageIndex] in afterEntry.languages) {
                "The restored Pokémon did not reopen with its native caught registration."
            }
            require(afterEntry.rawLanguageFlags ==
                (beforeEntry.rawLanguageFlags or (1L shl registration.languageIndex))) {
                "An unrelated destination language flag changed."
            }
            require(beforeEntry.genderFlags.indices.all { variant ->
                if (variant in registration.variants) {
                    afterEntry.genderFlags[variant].rawValue == 1L
                } else beforeEntry.genderFlags[variant] == afterEntry.genderFlags[variant]
            }) { "An unrelated destination gender or shiny registration changed." }
            if (beforeEntry.forms.isNotEmpty()) {
                require(beforeEntry.forms.indices.all { form ->
                    val beforeForm = beforeEntry.forms[form]
                    val afterForm = afterEntry.forms[form]
                    if (form == 0 && reopened.value.shiny) {
                        afterForm.shiny.rawValue == 1L && beforeForm.normal == afterForm.normal
                    } else if (form == 0) {
                        afterForm.normal.rawValue == 1L && beforeForm.shiny == afterForm.shiny
                    } else beforeForm == afterForm
                }) { "An unrelated destination form registration changed." }
            } else require(afterEntry.forms.isEmpty()) {
                "The destination gained an unsupported form registration."
            }
            if (registration.speciesId == 327) {
                val patternVariant = (reopened.value.gender and 1) +
                    if (reopened.value.shiny) 2 else 0
                require(beforeEntry.spinda.indices.all { variant ->
                    val beforePattern = beforeEntry.spinda[variant]
                    val afterPattern = afterEntry.spinda[variant]
                    if (variant != patternVariant) beforePattern == afterPattern
                    else {
                        val expectedSeen = if (beforeEntry.state.ordinal < BdspDexState.SEEN.ordinal)
                            reopened.value.encryptionConstant else beforePattern.seenEc
                        val expectedCaught = if (beforeEntry.state.ordinal < BdspDexState.CAUGHT.ordinal)
                            reopened.value.encryptionConstant else beforePattern.caughtEc
                        afterPattern.seenEc == expectedSeen && afterPattern.caughtEc == expectedCaught
                    }
                }) { "The destination Spinda pattern registration did not reopen exactly." }
            } else require(beforeEntry.spinda == afterEntry.spinda) {
                "Unrelated destination Spinda pattern data changed."
            }
            require(beforeDex.regionalDex == afterDex.regionalDex &&
                beforeDex.nationalDex == afterDex.nationalDex) {
                "Regional or National Pokédex progression flags changed."
            }
        }
        require(original.indices.all { index ->
            index in recordOffset until recordOffset + bankRecord.size ||
                dexRegistration?.touchedOffsets?.any { index in it until it + 4 } == true ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[index] == edited[index]
        }) { "An unrelated BDSP save byte changed." }
    }

    private fun registerCaughtEnglishDex(
        original: ByteArray,
        edited: ByteArray,
        pokemon: BdspPokemonRead
    ): BdspPseBankDexRegistration {
        require(pokemon.species in 1..493 && pokemon.form == 0) {
            "Only a reviewed default-form BDSP species can receive this native Pokédex registration."
        }
        val beforeDex = BdspPokedexReadOnly.read(original)
        val before = beforeDex.entry(pokemon.species)
        val shinyOffset = if (pokemon.shiny) 2 else 0
        val variants = when (pokemon.gender) {
            0 -> listOf(shinyOffset)
            1 -> listOf(shinyOffset + 1)
            2 -> listOf(shinyOffset, shinyOffset + 1)
            else -> throw IllegalArgumentException("The converted gender has no native BDSP Pokédex mapping.")
        }
        variants.forEach { variant ->
            require(before.genderFlags[variant].rawValue in 0L..1L) {
                "This destination gender/shiny registration contains an unknown raw value."
            }
        }

        val index = pokemon.species - 1
        val stateOffset = DEX_OFFSET + index * 4
        val languageOffset = DEX_OFFSET + 0x28FC + index * 4
        val touched = mutableSetOf(stateOffset, languageOffset)
        writeU32(edited, stateOffset, BdspDexState.CAUGHT.ordinal.toLong())
        variants.forEach { variant ->
            val variantOffset = DEX_OFFSET + dexVariantOffsets[variant] + index * 4
            writeU32(edited, variantOffset, 1L)
            touched += variantOffset
        }
        writeU32(edited, languageOffset, before.rawLanguageFlags or (1L shl 1))

        var nextFormOffset = 493 * 4 * 5
        dexFormCounts.forEach { (species, count) ->
            if (species == pokemon.species) {
                val formOffset = DEX_OFFSET + nextFormOffset +
                    if (pokemon.shiny) count * 4 else 0
                require(u32(edited, formOffset) in 0L..1L) {
                    "This destination base-form registration contains an unknown raw value."
                }
                writeU32(edited, formOffset, 1L)
                touched += formOffset
            }
            nextFormOffset += count * 4 * 2
        }

        if (pokemon.species == 327) {
            val patternVariant = (pokemon.gender and 1) + shinyOffset
            if (before.state.ordinal < BdspDexState.SEEN.ordinal) {
                val seenOffset = 0xCEA34 + patternVariant * 4
                writeU32(edited, seenOffset, pokemon.encryptionConstant)
                touched += seenOffset
            }
            if (before.state.ordinal < BdspDexState.CAUGHT.ordinal) {
                val caughtOffset = 0xCEA44 + patternVariant * 4
                writeU32(edited, caughtOffset, pokemon.encryptionConstant)
                touched += caughtOffset
            }
        }
        return BdspPseBankDexRegistration(
            pokemon.species, variants, 1, touched.sorted()
        )
    }

    private fun u32(bytes: ByteArray, offset: Int): Long = (0..3).fold(0L) { value, index ->
        value or ((bytes[offset + index].toLong() and 255) shl (index * 8))
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
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
    val recordOffset: Int,
    val dexRegistration: BdspPseBankDexRegistration?
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
                original, edited, bankRecord, box, position, recordOffset,
                dexRegistration
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed PSE Bank restore."
            }
            BdspPseBankRestoreEditing.verify(
                original, persisted, bankRecord, box, position, recordOffset,
                dexRegistration
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
