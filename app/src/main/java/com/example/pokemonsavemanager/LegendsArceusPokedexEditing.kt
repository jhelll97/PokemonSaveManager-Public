package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Adds one native obtained registration without inventing research progress or task completion. */
object LegendsArceusPokedexEditing {
    private const val MAX_SAVE_SIZE = 0x13AD06

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
                "The document exceeds the supported Legends: Arceus save size."
            }
        }
        output.toByteArray()
    }

    fun stage(source: ByteArray, expectedSha256: String, number: Int): LegendsArceusDexCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(number in 1..242) { "The Hisui Pokédex number must be 1 through 242." }
        val beforeDex = LegendsArceusPokedexReadOnly.read(original)
        val before = beforeDex.entry(number)
        require(!before.obtained) { "This species already has a native obtained registration." }
        val write = LegendsArceusSaveValidation.writeDexObtainedCount(original, before.speciesId, 1)
        verify(original, write.bytes, number, write.absoluteOffset)
        val after = LegendsArceusPokedexReadOnly.read(write.bytes).entry(number)
        return LegendsArceusDexCopyPlan(original, write.bytes, number, before, after, write.absoluteOffset)
    }

    internal fun verify(original: ByteArray, edited: ByteArray, number: Int, absoluteOffset: Int) {
        val beforeValidation = LegendsArceusSaveValidation.inspect(original)
        val afterValidation = LegendsArceusSaveValidation.inspect(edited)
        require(beforeValidation is LegendsArceusValidation.Verified &&
            afterValidation is LegendsArceusValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The edited save changed title identity or size."
        }
        val beforeDex = LegendsArceusPokedexReadOnly.read(original)
        val afterDex = LegendsArceusPokedexReadOnly.read(edited)
        require(beforeDex.version == afterDex.version && beforeDex.rawGlobalFlags == afterDex.rawGlobalFlags &&
            beforeDex.totalResearchPoints == afterDex.totalResearchPoints &&
            beforeDex.updateCounter == afterDex.updateCounter && beforeDex.reportCounter == afterDex.reportCounter) {
            "Global Hisui research state changed."
        }
        val before = beforeDex.entry(number)
        val after = afterDex.entry(number)
        require(!before.obtained && after == before.copy(numObtained = 1)) {
            "The staged obtained registration did not reopen exactly."
        }
        require(beforeDex.entries.indices.all { index ->
            index == number - 1 || beforeDex.entries[index] == afterDex.entries[index]
        }) { "An unrelated Hisui research record changed." }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + 2 ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use export transaction to a new empty document. The selected source is never replaced. */
class LegendsArceusDexCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val number: Int,
    val before: LegendsArceusDexEntryRead,
    val after: LegendsArceusDexEntryRead,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LegendsArceusPokedexEditing.sha256(this.original)
    val editedSha256: String = LegendsArceusPokedexEditing.sha256(this.edited)
    private var consumed = false
    val beforeLabel: String get() = "Obtained ${before.numObtained}"
    val afterLabel: String get() = "Obtained ${after.numObtained}"

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
            LegendsArceusPokedexEditing.verify(original, edited, number, absoluteOffset)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LegendsArceusPokedexEditing.verify(original, persisted, number, absoluteOffset)
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
