package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class LegendsArceusExpeditionRankEditRead(
    val version: LegendsArceusVersion,
    val rank: Long
)

/** Reviewed editing for PKHeX's documented Galaxy Expedition Team rank UInt32. */
object LegendsArceusExpeditionRankEditing {
    private const val MAX_SAVE_SIZE = 0x13AD06
    private const val FIELD_LENGTH = 4

    fun read(source: ByteArray): LegendsArceusExpeditionRankEditRead {
        val trainer = LegendsArceusTrainerReadOnly.read(source)
        return LegendsArceusExpeditionRankEditRead(
            trainer.version,
            trainer.expeditionRank ?: error("This save does not contain the Expedition Team rank.")
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
        replacement: Long
    ): LegendsArceusExpeditionRankCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(replacement in 0L..10L) { "Changed Expedition Team rank must be between 0 and 10." }
        val before = read(original)
        require(replacement != before.rank) { "Change the Expedition Team rank before reviewing." }
        val write = LegendsArceusSaveValidation.writeExpeditionRank(original, replacement)
        val after = read(write.bytes)
        require(after.rank == replacement) { "The requested Expedition Team rank did not stage exactly." }
        verify(original, write.bytes, after, write.rankOffset)
        return LegendsArceusExpeditionRankCopyPlan(original, write.bytes, before, after, write.rankOffset)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: LegendsArceusExpeditionRankEditRead,
        rankOffset: Int
    ) {
        val beforeValidation = LegendsArceusSaveValidation.inspect(original)
        val afterValidation = LegendsArceusSaveValidation.inspect(edited)
        require(
            beforeValidation is LegendsArceusValidation.Verified &&
                afterValidation is LegendsArceusValidation.Verified &&
                beforeValidation.version == afterValidation.version && original.size == edited.size
        ) { "The Expedition Team rank edit changed title identity or save size." }
        val before = LegendsArceusTrainerReadOnly.read(original)
        val after = LegendsArceusTrainerReadOnly.read(edited)
        require(after.expeditionRank == expected.rank) {
            "The reviewed Expedition Team rank did not reopen exactly."
        }
        require(before.copy(expeditionRank = expected.rank) == after) {
            "A different Legends: Arceus Trainer Data value changed."
        }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in rankOffset until rankOffset + FIELD_LENGTH ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "The Expedition Team rank edit changed unrelated save data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class LegendsArceusExpeditionRankCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: LegendsArceusExpeditionRankEditRead,
    val after: LegendsArceusExpeditionRankEditRead,
    val rankOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = LegendsArceusExpeditionRankEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Trainer Data." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        require(retainBackup(original.copyOf()).contentEquals(original)) {
            "The recovery backup did not verify. Nothing was written."
        }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LegendsArceusExpeditionRankEditing.verify(original, edited, after, rankOffset)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Expedition Team rank edit."
            }
            LegendsArceusExpeditionRankEditing.verify(original, persisted, after, rankOffset)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Expedition Team rank copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
