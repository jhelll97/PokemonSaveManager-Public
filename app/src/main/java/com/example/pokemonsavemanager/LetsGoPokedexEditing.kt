package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class LetsGoDexEditMode { SEEN, CAUGHT }

/** Adds one native Let's Go Pokédex registration while preserving every existing registration and size record. */
object LetsGoPokedexEditing {
    private const val DEX = 0x2A00
    private const val DEX_BLOCK_LENGTH = 0x20E8
    private const val DEX_CRC_OFFSET = 0xB863A
    val languages: List<String> = listOf(
        "Japanese", "English", "French", "Italian", "German", "Spanish", "Korean",
        "Chinese (Simplified)", "Chinese (Traditional)"
    )

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        val max = LetsGoSaveValidation.FILE_SIZE
        while (true) {
            val remaining = max + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= max) { "The document exceeds the supported Let's Go save size." }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        speciesId: Int,
        variant: Int,
        mode: LetsGoDexEditMode,
        languageIndex: Int? = null
    ): LetsGoDexCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(speciesId in 1..151 || speciesId in 808..809) { "That species is outside the native Let's Go Pokédex." }
        require(variant in 0..3) { "Choose a native seen variant." }
        if (mode == LetsGoDexEditMode.CAUGHT) require(languageIndex != null && languageIndex in languages.indices) {
            "Choose the language of the caught registration."
        }

        val before = LetsGoPokedexReadOnly.read(original).entry(speciesId)
        val edited = original.copyOf()
        val bit = speciesId - 1
        val seenOffset = DEX + 0xF0 + variant * 0x8C + (bit ushr 3)
        val displayedOffset = DEX + 0xF0 + (variant + 4) * 0x8C + (bit ushr 3)
        val touched = linkedSetOf(seenOffset, displayedOffset)
        setBit(edited, seenOffset, bit and 7)
        setBit(edited, displayedOffset, bit and 7)

        if (mode == LetsGoDexEditMode.CAUGHT) {
            val caughtOffset = DEX + 0x88 + (bit ushr 3)
            val languageBit = bit * 9 + languageIndex!!
            val languageOffset = DEX + 0x550 + (languageBit ushr 3)
            setBit(edited, caughtOffset, bit and 7)
            setBit(edited, languageOffset, languageBit and 7)
            touched += caughtOffset
            touched += languageOffset
        }
        require(!edited.contentEquals(original)) { "That native registration is already present." }

        writeU16(edited, DEX_CRC_OFFSET, LetsGoSaveValidation.crc16(edited, DEX, DEX_BLOCK_LENGTH))
        verify(original, edited, speciesId, variant, mode, languageIndex, touched)
        val after = LetsGoPokedexReadOnly.read(edited).entry(speciesId)
        return LetsGoDexCopyPlan(original, edited, speciesId, variant, mode, languageIndex, before, after, touched)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        speciesId: Int,
        variant: Int,
        mode: LetsGoDexEditMode,
        languageIndex: Int?,
        touchedOffsets: Set<Int>
    ) {
        val beforeDex = LetsGoPokedexReadOnly.read(original)
        val afterDex = LetsGoPokedexReadOnly.read(edited)
        require(beforeDex.version == afterDex.version && original.size == edited.size) {
            "The edited save changed title identity or size."
        }
        val before = beforeDex.entry(speciesId)
        val after = afterDex.entry(speciesId)
        require(after.seenVariants[variant] && after.displayedVariants[variant]) {
            "The staged seen registration did not reopen exactly."
        }
        require(before.seenVariants.indices.all { it == variant || before.seenVariants[it] == after.seenVariants[it] }) {
            "An unrelated seen variant changed."
        }
        require(before.displayedVariants.indices.all { it == variant || before.displayedVariants[it] == after.displayedVariants[it] }) {
            "An unrelated displayed variant changed."
        }
        if (mode == LetsGoDexEditMode.CAUGHT) {
            require(after.caught && languages[languageIndex!!] in after.languages) {
                "The staged caught registration did not reopen exactly."
            }
        } else require(before.caught == after.caught && before.languages == after.languages) {
            "A seen-only registration changed caught or language data."
        }
        require(original.indices.all { index ->
            index in touchedOffsets || index in DEX_CRC_OFFSET until DEX_CRC_OFFSET + 2 || original[index] == edited[index]
        }) { "An unrelated save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun setBit(bytes: ByteArray, offset: Int, bit: Int) {
        bytes[offset] = (bytes[offset].toInt() or (1 shl bit)).toByte()
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
}

class LetsGoDexCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val speciesId: Int,
    val variant: Int,
    val mode: LetsGoDexEditMode,
    val languageIndex: Int?,
    val before: LetsGoDexEntryRead,
    val after: LetsGoDexEntryRead,
    touchedOffsets: Set<Int>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val touchedOffsets = touchedOffsets.toSet()
    val sourceSha256: String = LetsGoPokedexEditing.sha256(this.original)
    val editedSha256: String = LetsGoPokedexEditing.sha256(this.edited)
    val allowedOffsets: List<Int> = this.touchedOffsets.sorted()
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
            LetsGoPokedexEditing.verify(original, edited, speciesId, variant, mode, languageIndex, touchedOffsets)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LetsGoPokedexEditing.verify(original, persisted, speciesId, variant, mode, languageIndex, touchedOffsets)
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
