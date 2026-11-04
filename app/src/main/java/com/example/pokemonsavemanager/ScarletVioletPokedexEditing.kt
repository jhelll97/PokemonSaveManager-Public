package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class ScarletVioletDexEditMode { SEEN, CAUGHT }

/** Adds one native SV form registration while preserving display selections and unknown bits. */
object ScarletVioletPokedexEditing {
    private const val MAX_SAVE_SIZE = 0x31CF81
    private val languages = listOf(
        "Japanese", "English", "French", "Italian", "German", "Spanish", "Korean",
        "Chinese (Simplified)", "Chinese (Traditional)"
    )
    val languageNames: List<String> get() = languages

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val remaining = MAX_SAVE_SIZE + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= MAX_SAVE_SIZE) {
                "The document exceeds the supported Scarlet/Violet save size."
            }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        region: ScarletVioletDexRegion,
        number: Int,
        form: Int,
        gender: Int,
        shiny: Boolean,
        mode: ScarletVioletDexEditMode,
        languageIndex: Int? = null
    ): ScarletVioletDexCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(form in 0..31) { "Native form ID must be 0 through 31." }
        require(gender in 0..2) { "Choose male, female, or genderless." }
        if (mode == ScarletVioletDexEditMode.CAUGHT) require(languageIndex != null && languageIndex in languages.indices) {
            "Choose the language of the caught registration."
        }
        val beforeDex = ScarletVioletPokedexReadOnly.read(original)
        val before = beforeDex.region(region)?.entry(number)
            ?: throw IllegalArgumentException("Reopen the regional Pokédex entry from PSE.")
        val modern = beforeDex.layout == ScarletVioletDexLayout.DLC
        if (!modern) require(before.rawState != null && before.rawState in 0L..3L) {
            "This original-layout entry contains an unknown raw state and remains locked."
        }
        val recordSize = if (modern) 0x20 else 0x18
        val nativeSpecies = ScarletVioletPokedexReadOnly.nativeSpeciesId(before.speciesId)
        val relativeOffset = nativeSpecies * recordSize
        val blocks = ScarletVioletSaveValidation.readDexBlocks(original)
        val block = if (modern) blocks.dlc!! else blocks.original
        val record = block.copyOfRange(relativeOffset, relativeOffset + recordSize)
        val formBit = 1L shl form
        val genderBit = 1 shl gender
        val touched = mutableListOf<IntRange>()
        if (modern) {
            if (mode == ScarletVioletDexEditMode.CAUGHT) {
                writeU32(record, 0, before.obtainedForms!! or formBit)
                touched += 0..3
            }
            writeU32(record, 4, before.seenForms or formBit)
            writeU32(record, 8, before.heardForms!! or formBit)
            touched += 4..7
            touched += 8..11
            if (mode == ScarletVioletDexEditMode.CAUGHT) {
                writeU16(record, 0x10, before.languageFlags or (1 shl languageIndex!!))
                touched += 0x10..0x11
            }
            record[0x12] = (before.genderFlags or genderBit).toByte()
            touched += 0x12..0x12
            if (shiny) {
                record[0x13] = (before.rawShinyFlags or 2).toByte()
                touched += 0x13..0x13
            }
        } else {
            val state = if (mode == ScarletVioletDexEditMode.CAUGHT) 3L else maxOf(before.rawState!!, 2L)
            writeU32(record, 0, state)
            writeU32(record, 4, before.seenForms or formBit)
            writeU16(record, 8, before.genderFlags or genderBit)
            touched += 0..3
            touched += 4..7
            touched += 8..9
            if (mode == ScarletVioletDexEditMode.CAUGHT) {
                writeU16(record, 0xA, before.languageFlags or (1 shl languageIndex!!))
                touched += 0xA..0xB
            }
            if (shiny) {
                record[0xC] = (before.rawShinyFlags or 1).toByte()
                touched += 0xC..0xC
            }
        }
        val write = ScarletVioletSaveValidation.writeDexRecord(original, modern, relativeOffset, record)
        require(!write.bytes.contentEquals(original)) { "That native registration is already present." }
        verify(original, write.bytes, region, number, form, gender, shiny, mode, languageIndex,
            write.absoluteOffset, touched)
        val after = ScarletVioletPokedexReadOnly.read(write.bytes).region(region)!!.entry(number)
        return ScarletVioletDexCopyPlan(original, write.bytes, region, number, form, gender, shiny, mode,
            languageIndex, before, after, write.absoluteOffset, touched)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        region: ScarletVioletDexRegion,
        number: Int,
        form: Int,
        gender: Int,
        shiny: Boolean,
        mode: ScarletVioletDexEditMode,
        languageIndex: Int?,
        absoluteOffset: Int,
        touchedRanges: List<IntRange>
    ) {
        val beforeValidation = ScarletVioletSaveValidation.inspect(original)
        val afterValidation = ScarletVioletSaveValidation.inspect(edited)
        require(beforeValidation is ScarletVioletValidation.Verified &&
            afterValidation is ScarletVioletValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The edited save changed title identity or size."
        }
        val beforeDex = ScarletVioletPokedexReadOnly.read(original)
        val afterDex = ScarletVioletPokedexReadOnly.read(edited)
        require(beforeDex.version == afterDex.version && beforeDex.layout == afterDex.layout &&
            beforeDex.regions.map { it.region } == afterDex.regions.map { it.region }) {
            "The edited save changed Pokédex layout or regional identity."
        }
        val target = beforeDex.region(region)?.entry(number)
            ?: throw IllegalArgumentException("The reviewed regional entry is unavailable.")
        val formBit = 1L shl form
        val genderBit = 1 shl gender
        fun expected(entry: ScarletVioletDexEntryRead): ScarletVioletDexEntryRead {
            val languageFlags = if (mode == ScarletVioletDexEditMode.CAUGHT)
                entry.languageFlags or (1 shl languageIndex!!) else entry.languageFlags
            val shinyFlags = if (!shiny) entry.rawShinyFlags else
                entry.rawShinyFlags or if (beforeDex.layout == ScarletVioletDexLayout.DLC) 2 else 1
            return if (beforeDex.layout == ScarletVioletDexLayout.DLC) entry.copy(
                obtainedForms = if (mode == ScarletVioletDexEditMode.CAUGHT) entry.obtainedForms!! or formBit else entry.obtainedForms,
                seenForms = entry.seenForms or formBit,
                heardForms = entry.heardForms!! or formBit,
                genderFlags = entry.genderFlags or genderBit,
                languageFlags = languageFlags,
                languages = languages.filterIndexed { bit, _ -> languageFlags and (1 shl bit) != 0 },
                rawShinyFlags = shinyFlags
            ) else entry.copy(
                rawState = if (mode == ScarletVioletDexEditMode.CAUGHT) 3L else maxOf(entry.rawState!!, 2L),
                seenForms = entry.seenForms or formBit,
                genderFlags = entry.genderFlags or genderBit,
                languageFlags = languageFlags,
                languages = languages.filterIndexed { bit, _ -> languageFlags and (1 shl bit) != 0 },
                rawShinyFlags = shinyFlags
            )
        }
        require(beforeDex.regions.indices.all { regionIndex ->
            val beforeEntries = beforeDex.regions[regionIndex].entries
            val afterEntries = afterDex.regions[regionIndex].entries
            beforeEntries.indices.all { index ->
                val beforeEntry = beforeEntries[index]
                afterEntries[index] == if (beforeEntry.speciesId == target.speciesId) expected(beforeEntry) else beforeEntry
            }
        }) { "An unrelated native Pokédex record changed." }
        val after = afterDex.region(region)!!.entry(number)
        require(after.seen && after.seenForms and formBit != 0L && after.genderFlags and genderBit != 0 &&
            (!shiny || after.shinySeen) && (mode != ScarletVioletDexEditMode.CAUGHT || after.caught)) {
            "The staged registration did not reopen exactly."
        }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            touchedRanges.any { range -> index in (absoluteOffset + range.first)..(absoluteOffset + range.last) } ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        require(value in 0..0xFFFF)
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        require(value in 0L..0xFFFFFFFFL)
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
    }
}

class ScarletVioletDexCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val region: ScarletVioletDexRegion,
    val number: Int,
    val form: Int,
    val gender: Int,
    val shiny: Boolean,
    val mode: ScarletVioletDexEditMode,
    val languageIndex: Int?,
    val before: ScarletVioletDexEntryRead,
    val after: ScarletVioletDexEntryRead,
    val absoluteOffset: Int,
    touchedRanges: List<IntRange>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val touchedRanges = touchedRanges.toList()
    val allowedEncryptedRanges: List<IntRange> = this.touchedRanges.map {
        (absoluteOffset + it.first)..(absoluteOffset + it.last)
    }
    val sourceSha256: String = ScarletVioletPokedexEditing.sha256(this.original)
    val editedSha256: String = ScarletVioletPokedexEditing.sha256(this.edited)
    private var consumed = false
    val beforeLabel: String get() = "Seen ${before.seen} • Caught ${before.caught}"
    val afterLabel: String get() = "Seen ${after.seen} • Caught ${after.caught}"

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen the Pokédex editor." }
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
            ScarletVioletPokedexEditing.verify(original, edited, region, number, form, gender, shiny,
                mode, languageIndex, absoluteOffset, touchedRanges)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            ScarletVioletPokedexEditing.verify(original, persisted, region, number, form, gender, shiny,
                mode, languageIndex, absoluteOffset, touchedRanges)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
