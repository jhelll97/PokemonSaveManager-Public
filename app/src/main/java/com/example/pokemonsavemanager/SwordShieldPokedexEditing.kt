package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class SwordShieldDexEditMode { SEEN, CAUGHT }

/** Adds one native regional registration while preserving forms, display metadata, counters, and Gigantamax flags. */
object SwordShieldPokedexEditing {
    private const val MAX_FILE_SIZE = 0x1876B1
    val languages: List<String> = listOf(
        "Japanese", "English", "French", "Italian", "German", "Spanish", "Korean",
        "Chinese (Simplified)", "Chinese (Traditional)"
    )

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
            require(output.size() <= MAX_FILE_SIZE) { "The document exceeds the supported Sword/Shield save size." }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        region: SwordShieldDexRegion,
        number: Int,
        variant: Int,
        formIndex: Int,
        mode: SwordShieldDexEditMode,
        languageIndex: Int? = null
    ): SwordShieldDexCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(number in 1..region.entryCount) { "The regional Pokédex number is outside the native table." }
        require(variant in 0..3) { "Choose a native seen variant." }
        require(formIndex in 0..61) { "Form index must be 0–61. Special native indices 62 and 63 stay locked." }
        if (mode == SwordShieldDexEditMode.CAUGHT) require(languageIndex != null && languageIndex in languages.indices) {
            "Choose the language of the caught registration."
        }

        val dex = SwordShieldPokedexReadOnly.read(original)
        val before = dex.region(region)?.entry(number)
            ?: throw IllegalArgumentException("The selected regional Pokédex is not allocated in this save.")
        val blocks = SwordShieldSaveValidation.readDexBlocks(original)
        val table = when (region) {
            SwordShieldDexRegion.GALAR -> blocks.galar
            SwordShieldDexRegion.ARMOR -> blocks.armor
            SwordShieldDexRegion.CROWN -> blocks.crown
        } ?: throw IllegalArgumentException("The selected regional Pokédex is not allocated in this save.")
        val record = table.copyOfRange((number - 1) * 0x30, number * 0x30)
        val seenOffset = variant * 8 + formIndex / 8
        record[seenOffset] = (record[seenOffset].toInt() or (1 shl (formIndex and 7))).toByte()
        if (mode == SwordShieldDexEditMode.CAUGHT) {
            var flags = u32(record, 0x20)
            flags = flags or 1L or (1L shl (languageIndex!! + 2))
            writeU32(record, 0x20, flags)
        }
        val unchanged = table.copyOfRange((number - 1) * 0x30, number * 0x30)
        require(!record.contentEquals(unchanged)) { "That native registration is already present." }

        val written = SwordShieldSaveValidation.writeDexRecord(original, region, number, record)
        verify(original, written.bytes, region, number, variant, formIndex, mode, languageIndex, written.absoluteOffset)
        val after = SwordShieldPokedexReadOnly.read(written.bytes).region(region)!!.entry(number)
        return SwordShieldDexCopyPlan(original, written.bytes, region, number, variant, formIndex, mode,
            languageIndex, before, after, written.absoluteOffset)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        region: SwordShieldDexRegion,
        number: Int,
        variant: Int,
        formIndex: Int,
        mode: SwordShieldDexEditMode,
        languageIndex: Int?,
        absoluteOffset: Int
    ) {
        val beforeDex = SwordShieldPokedexReadOnly.read(original)
        val afterDex = SwordShieldPokedexReadOnly.read(edited)
        require(beforeDex.version == afterDex.version && original.size == edited.size) {
            "The edited save changed title identity or size."
        }
        val before = beforeDex.region(region)?.entry(number) ?: error("The original regional record is missing.")
        val after = afterDex.region(region)?.entry(number) ?: error("The edited regional record is missing.")
        require(formIndex in after.seenBits[variant]) { "The staged seen registration did not reopen exactly." }
        require(before.seenBits.indices.all { group ->
            if (group == variant) after.seenBits[group].toSet() == before.seenBits[group].toSet() + formIndex
            else after.seenBits[group] == before.seenBits[group]
        }) { "An unrelated seen form changed." }
        require(before.displayedFormId == after.displayedFormId && before.displayedGenderId == after.displayedGenderId &&
            before.displayGigantamax == after.displayGigantamax && before.displayShiny == after.displayShiny &&
            before.caughtGigantamax == after.caughtGigantamax && before.secondaryGigantamaxFlag == after.secondaryGigantamaxFlag &&
            before.battledCount == after.battledCount && before.rawExtra1 == after.rawExtra1 && before.rawExtra2 == after.rawExtra2
        ) { "Native display, Gigantamax, counter, or extra data changed." }
        if (mode == SwordShieldDexEditMode.CAUGHT) {
            require(after.caught && languages[languageIndex!!] in after.languages) {
                "The staged caught registration did not reopen exactly."
            }
            require(after.languages.toSet() == before.languages.toSet() + languages[languageIndex]) {
                "An unrelated caught-language registration changed."
            }
        } else require(before.caught == after.caught && before.languages == after.languages) {
            "A seen-only registration changed caught or language data."
        }
        val allowedFlagMask = if (mode == SwordShieldDexEditMode.CAUGHT)
            1L or (1L shl (languageIndex!! + 2)) else 0L
        require((before.rawFlags xor after.rawFlags) and allowedFlagMask.inv() == 0L) {
            "An unrelated native Pokédex flag changed."
        }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + 0x30 || index in hashOffset until original.size ||
                original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun u32(bytes: ByteArray, offset: Int): Long = (0..3).fold(0L) { value, index ->
        value or ((bytes[offset + index].toLong() and 255) shl (index * 8))
    }
    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
    }
}

class SwordShieldDexCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val region: SwordShieldDexRegion,
    val number: Int,
    val variant: Int,
    val formIndex: Int,
    val mode: SwordShieldDexEditMode,
    val languageIndex: Int?,
    val before: SwordShieldDexEntryRead,
    val after: SwordShieldDexEntryRead,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldPokedexEditing.sha256(this.original)
    val editedSha256 = SwordShieldPokedexEditing.sha256(this.edited)
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
        fun requireSource() = require(readSource().contentEquals(original)) { "The selected source changed. Reopen it from PSE." }
        requireSource()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) { "The recovery backup did not verify. Nothing was written." }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            SwordShieldPokedexEditing.verify(original, edited, region, number, variant, formIndex, mode,
                languageIndex, absoluteOffset)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            SwordShieldPokedexEditing.verify(original, persisted, region, number, variant, formIndex, mode,
                languageIndex, absoluteOffset)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException((failure.message ?: "Copy verification failed.") +
                if (removed) " The failed new copy was removed; the recovery backup is retained."
                else " The new copy could not be removed. Do not use it; the recovery backup is retained.", failure)
        }
    }
}
