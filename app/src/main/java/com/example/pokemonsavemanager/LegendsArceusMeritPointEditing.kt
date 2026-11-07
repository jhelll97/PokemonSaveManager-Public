package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class LegendsArceusMeritPointEditRead(
    val version: LegendsArceusVersion,
    val current: Long?,
    val totalEarned: Long?
)

/** Reviewed editing for PKHeX's two documented native Legends: Arceus Merit Point records. */
object LegendsArceusMeritPointEditing {
    private const val MAX_SAVE_SIZE = 0x13AD06
    private const val FIELD_LENGTH = 4
    const val MAX_EDITED_VALUE = 999_999_999L

    fun read(source: ByteArray): LegendsArceusMeritPointEditRead {
        val trainer = LegendsArceusTrainerReadOnly.read(source)
        return LegendsArceusMeritPointEditRead(trainer.version, trainer.meritCurrent, trainer.meritTotal)
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
        requestedCurrent: Long?,
        requestedTotal: Long?
    ): LegendsArceusMeritPointCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = read(original)
        require(before.current != null || before.totalEarned != null) {
            "This save does not contain either native Merit Point record."
        }
        require(before.current != null || requestedCurrent == null) {
            "This save does not contain Current Merit Points."
        }
        require(before.totalEarned != null || requestedTotal == null) {
            "This save does not contain Total Merit Points earned."
        }
        val currentChanged = requestedCurrent != null && requestedCurrent != before.current
        val totalChanged = requestedTotal != null && requestedTotal != before.totalEarned
        if (currentChanged) require(requestedCurrent != null && requestedCurrent in 0L..MAX_EDITED_VALUE) {
            "Changed Current Merit Points must be between 0 and 999,999,999."
        }
        if (totalChanged) require(requestedTotal != null && requestedTotal in 0L..MAX_EDITED_VALUE) {
            "Changed Total Merit Points earned must be between 0 and 999,999,999."
        }
        require(currentChanged || totalChanged) { "Change at least one Merit Point value before reviewing." }
        val write = LegendsArceusSaveValidation.writeMeritPoints(
            original,
            requestedCurrent.takeIf { currentChanged },
            requestedTotal.takeIf { totalChanged }
        )
        val after = read(write.bytes)
        require(
            after.current == (if (currentChanged) requestedCurrent else before.current) &&
                after.totalEarned == (if (totalChanged) requestedTotal else before.totalEarned)
        ) { "The requested Merit Point values did not stage exactly." }
        verify(
            original, write.bytes, after, write.currentOffset, write.totalOffset,
            currentChanged, totalChanged
        )
        return LegendsArceusMeritPointCopyPlan(
            original, write.bytes, before, after, write.currentOffset, write.totalOffset,
            currentChanged, totalChanged
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: LegendsArceusMeritPointEditRead,
        currentOffset: Int,
        totalOffset: Int,
        currentChanged: Boolean,
        totalChanged: Boolean
    ) {
        val beforeValidation = LegendsArceusSaveValidation.inspect(original)
        val afterValidation = LegendsArceusSaveValidation.inspect(edited)
        require(
            beforeValidation is LegendsArceusValidation.Verified &&
                afterValidation is LegendsArceusValidation.Verified &&
                beforeValidation.version == afterValidation.version && original.size == edited.size
        ) { "The Merit Point edit changed title identity or save size." }
        val before = read(original)
        val after = read(edited)
        require(after == expected) { "The reviewed Merit Point values did not reopen exactly." }
        require(
            before.copy(
                current = if (currentChanged) expected.current else before.current,
                totalEarned = if (totalChanged) expected.totalEarned else before.totalEarned
            ) == after
        ) { "A different Legends: Arceus Trainer Data value changed." }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            (currentChanged && index in currentOffset until currentOffset + FIELD_LENGTH) ||
                (totalChanged && index in totalOffset until totalOffset + FIELD_LENGTH) ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "The Merit Point edit changed unrelated save data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class LegendsArceusMeritPointCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: LegendsArceusMeritPointEditRead,
    val after: LegendsArceusMeritPointEditRead,
    val currentOffset: Int,
    val totalOffset: Int,
    val currentChanged: Boolean,
    val totalChanged: Boolean
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = LegendsArceusMeritPointEditing.sha256(this.edited)
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
            LegendsArceusMeritPointEditing.verify(
                original, edited, after, currentOffset, totalOffset, currentChanged, totalChanged
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed Merit Point edit." }
            LegendsArceusMeritPointEditing.verify(
                original, persisted, after, currentOffset, totalOffset, currentChanged, totalChanged
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Merit Point copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
