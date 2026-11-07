package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class LegendsArceusSolitudeEditRead(
    val version: LegendsArceusVersion,
    val number: Int,
    val entry: LegendsArceusDexEntryRead
)

/** One-way reviewed completion of the native Path of Solitude flag. */
object LegendsArceusSolitudeEditing {
    private const val MAX_SAVE_SIZE = 0x13AD06
    private const val FIELD_LENGTH = 1
    private const val SOLITUDE_FLAG = 1L shl 6

    fun read(source: ByteArray, number: Int): LegendsArceusSolitudeEditRead {
        require(number in 1..242) { "The Hisui Pokédex number must be 1 through 242." }
        val dex = LegendsArceusPokedexReadOnly.read(source)
        return LegendsArceusSolitudeEditRead(dex.version, number, dex.entry(number))
    }

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

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        number: Int
    ): LegendsArceusSolitudeCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) {
            "The source changed. Reopen it from PSE."
        }
        val before = read(original, number)
        require(!before.entry.solitudeComplete) {
            "Path of Solitude is already marked complete for this species."
        }
        val write = LegendsArceusSaveValidation.writeDexSolitudeComplete(
            original, before.entry.speciesId
        )
        val after = read(write.bytes, number)
        require(after.entry.solitudeComplete) {
            "Path of Solitude completion did not stage exactly."
        }
        verify(original, write.bytes, before, after, write.absoluteOffset)
        return LegendsArceusSolitudeCopyPlan(
            original, write.bytes, before, after, write.absoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        before: LegendsArceusSolitudeEditRead,
        expected: LegendsArceusSolitudeEditRead,
        absoluteOffset: Int
    ) {
        val beforeValidation = LegendsArceusSaveValidation.inspect(original)
        val afterValidation = LegendsArceusSaveValidation.inspect(edited)
        require(
            beforeValidation is LegendsArceusValidation.Verified &&
                afterValidation is LegendsArceusValidation.Verified &&
                beforeValidation.version == afterValidation.version &&
                original.size == edited.size
        ) { "The Path of Solitude edit changed title identity or save size." }
        val reopened = read(edited, expected.number)
        require(reopened.entry == before.entry.copy(
            rawFlags = before.entry.rawFlags or SOLITUDE_FLAG
        )) { "A different field in the selected native research record changed." }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index == absoluteOffset ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "The Path of Solitude edit changed unrelated save data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class LegendsArceusSolitudeCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: LegendsArceusSolitudeEditRead,
    val after: LegendsArceusSolitudeEditRead,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = LegendsArceusSolitudeEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) {
            "This Path of Solitude review was already used. Reopen the Pokédex entry."
        }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) {
            "Choose a new empty document; existing files cannot be replaced."
        }
        require(retainBackup(original.copyOf()).contentEquals(original)) {
            "The recovery backup did not verify. Nothing was written."
        }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LegendsArceusSolitudeEditing.verify(
                original, edited, before, after, absoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Path of Solitude edit."
            }
            LegendsArceusSolitudeEditing.verify(
                original, persisted, before, after, absoluteOffset
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Path of Solitude copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
