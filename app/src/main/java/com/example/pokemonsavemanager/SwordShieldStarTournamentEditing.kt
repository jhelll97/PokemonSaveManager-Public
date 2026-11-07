package com.example.pokemonsavemanager

import java.io.InputStream
import java.security.MessageDigest

data class SwordShieldStarTournamentEditRead(val version: SwordShieldVersion, val values: List<Long>)

/** Reviewed editing for the 21 documented native Galarian Star Tournament victory records. */
object SwordShieldStarTournamentEditing {
    private const val FIELD_SIZE = 4
    const val FIELD_COUNT = 21

    fun readDocumentBytes(input: InputStream): ByteArray = SwordShieldTrainerEditing.readDocumentBytes(input)

    fun read(source: ByteArray): SwordShieldStarTournamentEditRead {
        val blocks = SwordShieldSaveValidation.readStarTournamentBlocks(source)
        fun value(bytes: ByteArray): Long = bytes.foldIndexed(0L) { index, result, byte ->
            result or ((byte.toLong() and 0xFF) shl (index * 8))
        }
        return SwordShieldStarTournamentEditRead(
            blocks.version,
            listOf(value(blocks.totalVictories)) + blocks.partnerVictories.map(::value)
        )
    }

    fun stage(source: ByteArray, expectedSha256: String, replacement: List<Long>): SwordShieldStarTournamentCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(replacement.size == FIELD_COUNT && replacement.all { it in 0L..0xFFFF_FFFFL }) {
            "Each tournament victory record must fit its native unsigned 32-bit field."
        }
        val before = read(original)
        val changes = replacement.indices.map { replacement[it] != before.values[it] }
        require(changes.any { it }) { "Change at least one tournament victory record before reviewing." }
        val written = SwordShieldSaveValidation.writeStarTournamentRecords(original, replacement)
        val after = SwordShieldStarTournamentEditRead(before.version, replacement.toList())
        verify(original, written.bytes, after, written.offsets, changes)
        return SwordShieldStarTournamentCopyPlan(
            original, written.bytes, before, after, written.offsets.toList(), changes
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: SwordShieldStarTournamentEditRead,
        offsets: List<Int>,
        changes: List<Boolean>
    ) {
        require(offsets.size == FIELD_COUNT && changes.size == FIELD_COUNT)
        val before = read(original)
        val after = read(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The tournament edit changed title identity or save size."
        }
        require(after == expected) { "The tournament victory records did not reopen exactly." }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            offsets.indices.any { changes[it] && index in offsets[it] until offsets[it] + FIELD_SIZE } ||
                index in hashOffset until original.size ||
                original[index] == edited[index]
        }) { "The tournament edit changed unrelated encrypted container data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class SwordShieldStarTournamentCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: SwordShieldStarTournamentEditRead,
    val after: SwordShieldStarTournamentEditRead,
    val offsets: List<Int>,
    val changes: List<Boolean>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldStarTournamentEditing.sha256(this.original)
    val editedSha256 = SwordShieldStarTournamentEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Galarian Star Tournament." }
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
            verifyPlan(edited)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            verifyPlan(persisted)
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

    private fun verifyPlan(candidate: ByteArray) = SwordShieldStarTournamentEditing.verify(
        original, candidate, after, offsets, changes
    )
}
