package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class LegendsZaDexEditMode { SEEN, CAUGHT }

/** Adds one native Z-A form registration while preserving Mega, Alpha, display and unknown fields. */
object LegendsZaPokedexEditing {
    private const val MAX_SAVE_SIZE = 0x309FB3
    private const val RECORD_SIZE = 0x84
    private val languages = listOf(
        "Japanese", "English", "French", "Italian", "German", "Spanish", "Korean",
        "Chinese (Simplified)", "Chinese (Traditional)", "Spanish (Latin America)"
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
            require(output.size() <= MAX_SAVE_SIZE) { "The document exceeds the supported Legends: Z-A save size." }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        section: LegendsZaDexSection,
        number: Int,
        form: Int,
        gender: Int,
        shiny: Boolean,
        mode: LegendsZaDexEditMode,
        languageIndex: Int? = null
    ): LegendsZaDexCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(form in 0..31) { "Native form ID must be 0 through 31." }
        require(gender in 0..2) { "Choose male, female, or genderless." }
        if (mode == LegendsZaDexEditMode.CAUGHT) require(languageIndex != null && languageIndex in languages.indices) {
            "Choose the language of the caught registration."
        }
        val beforeDex = LegendsZaPokedexReadOnly.read(original)
        val before = beforeDex.section(section)?.entry(number)
            ?: throw IllegalArgumentException("Reopen the native Pokédex entry from PSE.")
        val nativeSpecies = LegendsZaPokedexReadOnly.nativeSpeciesId(before.speciesId)
        val relativeOffset = nativeSpecies * RECORD_SIZE
        val block = LegendsZaSaveValidation.readDexBlock(original).data
        val record = block.copyOfRange(relativeOffset, relativeOffset + RECORD_SIZE)
        val formBit = 1L shl form
        val genderBit = 1 shl gender
        val touched = mutableListOf<IntRange>()
        if (mode == LegendsZaDexEditMode.CAUGHT) {
            writeU32(record, 0, before.caughtForms or formBit)
            writeU16(record, 8, before.languageFlags or (1 shl languageIndex!!))
            touched += 0..3
            touched += 8..9
        }
        writeU32(record, 4, before.seenForms or formBit)
        record[0xB] = (before.genderFlags or genderBit).toByte()
        touched += 4..7
        touched += 0xB..0xB
        if (shiny) {
            writeU32(record, 0xC, before.shinyForms or formBit)
            touched += 0xC..0xF
        }
        val write = LegendsZaSaveValidation.writeDexRecord(original, relativeOffset, record)
        require(!write.bytes.contentEquals(original)) { "That native registration is already present." }
        verify(original, write.bytes, section, number, form, gender, shiny, mode, languageIndex,
            write.absoluteOffset, touched)
        val after = LegendsZaPokedexReadOnly.read(write.bytes).section(section)!!.entry(number)
        return LegendsZaDexCopyPlan(original, write.bytes, section, number, form, gender, shiny, mode,
            languageIndex, before, after, write.absoluteOffset, touched)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        section: LegendsZaDexSection,
        number: Int,
        form: Int,
        gender: Int,
        shiny: Boolean,
        mode: LegendsZaDexEditMode,
        languageIndex: Int?,
        absoluteOffset: Int,
        touchedRanges: List<IntRange>
    ) {
        val beforeValidation = LegendsZaSaveValidation.inspect(original)
        val afterValidation = LegendsZaSaveValidation.inspect(edited)
        require(beforeValidation is LegendsZaValidation.Verified &&
            afterValidation is LegendsZaValidation.Verified &&
            beforeValidation.version == afterValidation.version &&
            beforeValidation.revision == afterValidation.revision && original.size == edited.size) {
            "The edited save changed title identity, revision, or size."
        }
        val beforeDex = LegendsZaPokedexReadOnly.read(original)
        val afterDex = LegendsZaPokedexReadOnly.read(edited)
        require(beforeDex.sections.map { it.section } == afterDex.sections.map { it.section }) {
            "The edited save changed native Pokédex section identity."
        }
        val target = beforeDex.section(section)?.entry(number)
            ?: throw IllegalArgumentException("The reviewed native Pokédex entry is unavailable.")
        val formBit = 1L shl form
        val genderBit = 1 shl gender
        fun expected(entry: LegendsZaDexEntryRead): LegendsZaDexEntryRead {
            val languageFlags = if (mode == LegendsZaDexEditMode.CAUGHT)
                entry.languageFlags or (1 shl languageIndex!!) else entry.languageFlags
            return entry.copy(
                caughtForms = if (mode == LegendsZaDexEditMode.CAUGHT) entry.caughtForms or formBit else entry.caughtForms,
                seenForms = entry.seenForms or formBit,
                shinyForms = if (shiny) entry.shinyForms or formBit else entry.shinyForms,
                languageFlags = languageFlags,
                languages = languages.filterIndexed { bit, _ -> languageFlags and (1 shl bit) != 0 },
                genderFlags = entry.genderFlags or genderBit
            )
        }
        require(beforeDex.sections.indices.all { sectionIndex ->
            val beforeEntries = beforeDex.sections[sectionIndex].entries
            val afterEntries = afterDex.sections[sectionIndex].entries
            beforeEntries.indices.all { index ->
                val beforeEntry = beforeEntries[index]
                afterEntries[index] == if (beforeEntry.speciesId == target.speciesId) expected(beforeEntry) else beforeEntry
            }
        }) { "An unrelated native Pokédex record changed." }
        val after = afterDex.section(section)!!.entry(number)
        require(after.seenForms and formBit != 0L && after.genderFlags and genderBit != 0 &&
            (!shiny || after.shinyForms and formBit != 0L) &&
            (mode != LegendsZaDexEditMode.CAUGHT || after.caughtForms and formBit != 0L)) {
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

class LegendsZaDexCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val section: LegendsZaDexSection,
    val number: Int,
    val form: Int,
    val gender: Int,
    val shiny: Boolean,
    val mode: LegendsZaDexEditMode,
    val languageIndex: Int?,
    val before: LegendsZaDexEntryRead,
    val after: LegendsZaDexEntryRead,
    val absoluteOffset: Int,
    touchedRanges: List<IntRange>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val touchedRanges = touchedRanges.toList()
    val allowedEncryptedRanges: List<IntRange> = this.touchedRanges.map {
        (absoluteOffset + it.first)..(absoluteOffset + it.last)
    }
    val sourceSha256: String = LegendsZaPokedexEditing.sha256(this.original)
    val editedSha256: String = LegendsZaPokedexEditing.sha256(this.edited)
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
            LegendsZaPokedexEditing.verify(original, edited, section, number, form, gender, shiny,
                mode, languageIndex, absoluteOffset, touchedRanges)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LegendsZaPokedexEditing.verify(original, persisted, section, number, form, gender, shiny,
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
