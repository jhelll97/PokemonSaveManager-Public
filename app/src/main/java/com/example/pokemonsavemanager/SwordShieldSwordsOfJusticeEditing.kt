package com.example.pokemonsavemanager

import java.io.InputStream
import java.security.MessageDigest

data class SwordShieldSwordsOfJusticeEditRead(val version: SwordShieldVersion, val values: List<Long>)

/** Reviewed editing for the six documented native Crown Tundra Swords of Justice quest records. */
object SwordShieldSwordsOfJusticeEditing {
    private const val FIELD_SIZE = 4
    const val FIELD_COUNT = 6
    private val allowedValues = listOf(
        setOf(0L, 10L, 20L, 30L),
        setOf(0L, 1L, 2L, 4L, 7L, 8L),
        setOf(0L, 1L, 2L, 3L),
        setOf(0L, 1L, 2L, 3L),
        setOf(0L, 1L, 2L, 3L),
        setOf(0L, 2L, 3L)
    )

    fun readDocumentBytes(input: InputStream): ByteArray = SwordShieldTrainerEditing.readDocumentBytes(input)

    fun read(source: ByteArray): SwordShieldSwordsOfJusticeEditRead {
        val blocks = SwordShieldSaveValidation.readSwordsOfJusticeBlocks(source)
        fun value(bytes: ByteArray): Long = bytes.foldIndexed(0L) { index, result, byte ->
            result or ((byte.toLong() and 0xFF) shl (index * 8))
        }
        return SwordShieldSwordsOfJusticeEditRead(
            blocks.version,
            listOf(
                value(blocks.questStatus),
                value(blocks.questProgress),
                value(blocks.cobalionProgress),
                value(blocks.terrakionProgress),
                value(blocks.virizionProgress),
                value(blocks.keldeoProgress)
            )
        )
    }

    fun stage(source: ByteArray, expectedSha256: String, replacement: List<Long>): SwordShieldSwordsOfJusticeCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(replacement.size == FIELD_COUNT && replacement.all { it in 0L..0xFFFF_FFFFL }) {
            "Each Swords of Justice quest record must fit its native unsigned 32-bit field."
        }
        val before = read(original)
        val changes = replacement.indices.map { replacement[it] != before.values[it] }
        require(replacement.indices.all { !changes[it] || replacement[it] in allowedValues[it] }) {
            "Changed quest values must use PKHeX's documented native states. Unusual existing values may remain untouched."
        }
        require(changes.any { it }) { "Change at least one Swords of Justice quest record before reviewing." }
        val written = SwordShieldSaveValidation.writeSwordsOfJusticeRecords(original, replacement)
        val after = SwordShieldSwordsOfJusticeEditRead(before.version, replacement.toList())
        verify(original, written.bytes, after, written.offsets, changes)
        return SwordShieldSwordsOfJusticeCopyPlan(
            original, written.bytes, before, after, written.offsets.toList(), changes
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: SwordShieldSwordsOfJusticeEditRead,
        offsets: List<Int>,
        changes: List<Boolean>
    ) {
        require(offsets.size == FIELD_COUNT && changes.size == FIELD_COUNT)
        val before = read(original)
        val after = read(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The quest edit changed title identity or save size."
        }
        require(after == expected) { "The Swords of Justice quest records did not reopen exactly." }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            offsets.indices.any { changes[it] && index in offsets[it] until offsets[it] + FIELD_SIZE } ||
                index in hashOffset until original.size ||
                original[index] == edited[index]
        }) { "The quest edit changed unrelated encrypted container data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class SwordShieldSwordsOfJusticeCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: SwordShieldSwordsOfJusticeEditRead,
    val after: SwordShieldSwordsOfJusticeEditRead,
    val offsets: List<Int>,
    val changes: List<Boolean>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldSwordsOfJusticeEditing.sha256(this.original)
    val editedSha256 = SwordShieldSwordsOfJusticeEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Swords of Justice Quest." }
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

    private fun verifyPlan(candidate: ByteArray) = SwordShieldSwordsOfJusticeEditing.verify(
        original, candidate, after, offsets, changes
    )
}
