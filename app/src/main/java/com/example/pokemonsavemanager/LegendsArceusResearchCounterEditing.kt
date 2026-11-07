package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class LegendsArceusResearchCounterEditRead(
    val version: LegendsArceusVersion,
    val number: Int,
    val speciesId: Int,
    val counterIndex: Int,
    val counter: LegendsArceusDexCounterRead,
    val entry: LegendsArceusDexEntryRead
)

/** Reviewed editing for one native UInt16 Legends: Arceus research counter. */
object LegendsArceusResearchCounterEditing {
    private const val MAX_SAVE_SIZE = 0x13AD06
    private const val COUNTER_COUNT = 29
    private const val REFERENCE_MAX = 60_000
    private const val FIELD_LENGTH = 2

    fun read(
        source: ByteArray,
        number: Int,
        counterIndex: Int
    ): LegendsArceusResearchCounterEditRead {
        require(number in 1..242) { "The Hisui Pokédex number must be 1 through 242." }
        require(counterIndex in 0 until COUNTER_COUNT) {
            "The native research-counter slot must be 1 through 29."
        }
        val dex = LegendsArceusPokedexReadOnly.read(source)
        val entry = dex.entry(number)
        require(entry.counters.size == COUNTER_COUNT) {
            "The native research record does not contain 29 counters."
        }
        return LegendsArceusResearchCounterEditRead(
            dex.version, number, entry.speciesId, counterIndex,
            entry.counters[counterIndex], entry
        )
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
        number: Int,
        counterIndex: Int,
        replacement: Int
    ): LegendsArceusResearchCounterCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) {
            "The source changed. Reopen it from PSE."
        }
        require(replacement in 0..REFERENCE_MAX) {
            "Changed research counters must be between 0 and 60,000."
        }
        val before = read(original, number, counterIndex)
        require(replacement != before.counter.value) {
            "Change the selected research counter before reviewing."
        }
        val write = LegendsArceusSaveValidation.writeDexResearchCounter(
            original, before.speciesId, counterIndex, replacement
        )
        val after = read(write.bytes, number, counterIndex)
        require(after.counter.value == replacement) {
            "The requested research counter did not stage exactly."
        }
        verify(original, write.bytes, before, after, write.absoluteOffset)
        return LegendsArceusResearchCounterCopyPlan(
            original, write.bytes, before, after, write.absoluteOffset
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        before: LegendsArceusResearchCounterEditRead,
        expected: LegendsArceusResearchCounterEditRead,
        absoluteOffset: Int
    ) {
        val beforeValidation = LegendsArceusSaveValidation.inspect(original)
        val afterValidation = LegendsArceusSaveValidation.inspect(edited)
        require(
            beforeValidation is LegendsArceusValidation.Verified &&
                afterValidation is LegendsArceusValidation.Verified &&
                beforeValidation.version == afterValidation.version &&
                original.size == edited.size
        ) { "The research-counter edit changed title identity or save size." }
        val reopened = read(edited, expected.number, expected.counterIndex)
        require(reopened.counter.value == expected.counter.value) {
            "The reviewed research counter did not reopen exactly."
        }
        val expectedCounters = before.entry.counters.toMutableList().apply {
            this[before.counterIndex] = this[before.counterIndex].copy(
                value = expected.counter.value
            )
        }
        require(reopened.entry == before.entry.copy(counters = expectedCounters)) {
            "A different field in the selected native research record changed."
        }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + FIELD_LENGTH ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "The research-counter edit changed unrelated save data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class LegendsArceusResearchCounterCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: LegendsArceusResearchCounterEditRead,
    val after: LegendsArceusResearchCounterEditRead,
    val absoluteOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = LegendsArceusResearchCounterEditing.sha256(this.edited)
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
            "This research-counter review was already used. Reopen the Pokédex entry."
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
            LegendsArceusResearchCounterEditing.verify(
                original, edited, before, after, absoluteOffset
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed research-counter edit."
            }
            LegendsArceusResearchCounterEditing.verify(
                original, persisted, before, after, absoluteOffset
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Research-counter copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
