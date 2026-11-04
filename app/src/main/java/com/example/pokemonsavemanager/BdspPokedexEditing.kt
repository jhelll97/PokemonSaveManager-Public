package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class BdspDexEditMode { SEEN, CAUGHT }

/** Adds one native BDSP species registration while preserving form, Spinda, progression, and unknown raw data. */
object BdspPokedexEditing {
    private const val DEX = 0x7A328
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16
    private val variantOffsets = listOf(0x171C, 0x1ED0, 0x7B4, 0xF68)
    val languages: List<String> = listOf(
        "Japanese", "English", "French", "Italian", "German", "Spanish", "Korean",
        "Chinese (Simplified)", "Chinese (Traditional)"
    )

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        val max = BdspSaveRevision.values().maxOf { it.fileSize }
        while (true) {
            val remaining = max + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= max) { "The document exceeds the supported BDSP save size." }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        speciesId: Int,
        variant: Int,
        mode: BdspDexEditMode,
        languageIndex: Int? = null
    ): BdspDexCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(speciesId in 1..493) { "BDSP Pokédex species must be 1 through 493." }
        require(variant in variantOffsets.indices) { "Choose a native gender and shiny registration." }
        if (mode == BdspDexEditMode.CAUGHT) require(languageIndex != null && languageIndex in languages.indices) {
            "Choose the language of the caught registration."
        }
        val before = BdspPokedexReadOnly.read(original).entry(speciesId)
        require(before.genderFlags[variant].rawValue in 0L..1L) {
            "This registration contains an unknown raw value and remains locked."
        }
        val index = speciesId - 1
        val stateOffset = DEX + index * 4
        val variantOffset = DEX + variantOffsets[variant] + index * 4
        val languageOffset = DEX + 0x28FC + index * 4
        val edited = original.copyOf()
        val targetState = if (mode == BdspDexEditMode.CAUGHT) 3L else maxOf(before.state.ordinal, 2).toLong()
        writeU32(edited, stateOffset, targetState)
        writeU32(edited, variantOffset, 1L)
        if (mode == BdspDexEditMode.CAUGHT) writeU32(
            edited, languageOffset, before.rawLanguageFlags or (1L shl languageIndex!!)
        )
        require(!edited.contentEquals(original)) { "That native registration is already present." }
        refreshDigest(edited)
        val touched = setOf(stateOffset, variantOffset) + if (mode == BdspDexEditMode.CAUGHT) setOf(languageOffset) else emptySet()
        verify(original, edited, speciesId, variant, mode, languageIndex, touched)
        val after = BdspPokedexReadOnly.read(edited).entry(speciesId)
        return BdspDexCopyPlan(original, edited, speciesId, variant, mode, languageIndex, before, after, touched)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        speciesId: Int,
        variant: Int,
        mode: BdspDexEditMode,
        languageIndex: Int?,
        touchedOffsets: Set<Int>
    ) {
        val beforeDex = BdspPokedexReadOnly.read(original)
        val afterDex = BdspPokedexReadOnly.read(edited)
        require(original.size == edited.size && beforeDex.version == afterDex.version && beforeDex.revision == afterDex.revision) {
            "The edited save changed title identity, revision, or size."
        }
        val before = beforeDex.entry(speciesId)
        val after = afterDex.entry(speciesId)
        require(after.seen && after.genderFlags[variant].value) { "The staged seen registration did not reopen exactly." }
        require(before.genderFlags.indices.all { it == variant || before.genderFlags[it] == after.genderFlags[it] }) {
            "An unrelated gender or shiny registration changed."
        }
        require(before.forms == after.forms && before.spinda == after.spinda) {
            "Native form or Spinda pattern data changed."
        }
        if (mode == BdspDexEditMode.CAUGHT) {
            require(after.caught && languages[languageIndex!!] in after.languages) {
                "The staged caught registration did not reopen exactly."
            }
            require(after.rawLanguageFlags == (before.rawLanguageFlags or (1L shl languageIndex))) {
                "An unrelated language flag changed."
            }
        } else {
            val expectedState = if (before.state.ordinal >= 2) before.state else BdspDexState.SEEN
            require(after.state == expectedState && before.rawLanguageFlags == after.rawLanguageFlags) {
                "A seen-only registration changed caught or language data."
            }
        }
        require(beforeDex.regionalDex == afterDex.regionalDex && beforeDex.nationalDex == afterDex.nationalDex) {
            "Regional or National Pokédex progression flags changed."
        }
        require(original.indices.all { index ->
            touchedOffsets.any { index in it until it + 4 } || index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[index] == edited[index]
        }) { "An unrelated save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
    }
}

class BdspDexCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val speciesId: Int,
    val variant: Int,
    val mode: BdspDexEditMode,
    val languageIndex: Int?,
    val before: BdspDexEntryRead,
    val after: BdspDexEntryRead,
    touchedOffsets: Set<Int>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val touchedOffsets = touchedOffsets.toSet()
    val sourceSha256 = BdspPokedexEditing.sha256(this.original)
    val editedSha256 = BdspPokedexEditing.sha256(this.edited)
    val allowedOffsets = this.touchedOffsets.sorted()
    private var consumed = false
    val beforeLabel: String get() = before.state.displayName
    val afterLabel: String get() = after.state.displayName

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
        fun requireSource() = require(readSource().contentEquals(original)) { "The selected source changed. Reopen it from PSE." }
        requireSource(); require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) { "The recovery backup did not verify. Nothing was written." }
        requireSource(); require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            BdspPokedexEditing.verify(original, edited, speciesId, variant, mode, languageIndex, touchedOffsets)
            started = true; writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspPokedexEditing.verify(original, persisted, speciesId, variant, mode, languageIndex, touchedOffsets)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException((failure.message ?: "Copy verification failed.") +
                if (removed) " The failed new copy was removed; the recovery backup is retained."
                else " The new copy could not be removed. Do not use it; the recovery backup is retained.", failure)
        }
    }
}
