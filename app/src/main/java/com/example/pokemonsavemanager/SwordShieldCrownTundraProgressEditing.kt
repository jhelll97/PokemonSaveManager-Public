package com.example.pokemonsavemanager

import java.io.InputStream
import java.security.MessageDigest

data class SwordShieldCrownTundraProgressEditRead(val version: SwordShieldVersion, val values: List<Long>)

/** Reviewed editing for ten documented native Crown Tundra progress records. */
object SwordShieldCrownTundraProgressEditing {
    private const val FIELD_SIZE = 4
    const val FIELD_COUNT = 10

    fun readDocumentBytes(input: InputStream): ByteArray = SwordShieldTrainerEditing.readDocumentBytes(input)

    fun read(source: ByteArray): SwordShieldCrownTundraProgressEditRead {
        val blocks = SwordShieldSaveValidation.readCrownTundraProgressBlocks(source)
        fun value(bytes: ByteArray): Long = bytes.foldIndexed(0L) { index, result, byte ->
            result or ((byte.toLong() and 0xFF) shl (index * 8))
        }
        return SwordShieldCrownTundraProgressEditRead(
            blocks.version,
            listOf(
                value(blocks.regiPattern),
                value(blocks.cobalionFootprints),
                value(blocks.terrakionFootprints),
                value(blocks.virizionFootprints),
                value(blocks.notedSpecies1),
                value(blocks.notedSpecies2),
                value(blocks.notedSpecies3),
                value(blocks.endlessStreak),
                value(blocks.disconnectStreak),
                value(blocks.peoniaSpeciesHint)
            )
        )
    }

    fun stage(source: ByteArray, expectedSha256: String, replacement: List<Long>): SwordShieldCrownTundraProgressCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(replacement.size == FIELD_COUNT && replacement.all { it in 0L..0xFFFF_FFFFL }) {
            "Each Crown Tundra progress record must fit its native unsigned 32-bit field."
        }
        val before = read(original)
        val changes = replacement.indices.map { replacement[it] != before.values[it] }
        require(!changes[0] || replacement[0] in 0L..2L) {
            "A changed Split-Decision Ruins pattern must be 0 not chosen, 1 electric, or 2 dragon."
        }
        require((1..3).all { !changes[it] || replacement[it] in 0L..100L }) {
            "Changed Swords of Justice footprint percentages must be between 0 and 100."
        }
        require(listOf(4, 5, 6, 9).all { !changes[it] || replacement[it] in 0L..898L }) {
            "Changed Max Lair and Peonia species IDs must be 0 for empty or between 1 and 898."
        }
        require(changes.any { it }) { "Change at least one Crown Tundra record before reviewing." }
        val written = SwordShieldSaveValidation.writeCrownTundraProgress(original, replacement)
        val after = SwordShieldCrownTundraProgressEditRead(before.version, replacement.toList())
        verify(original, written.bytes, after, written.offsets, changes)
        return SwordShieldCrownTundraProgressCopyPlan(
            original, written.bytes, before, after, written.offsets.toList(), changes
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: SwordShieldCrownTundraProgressEditRead,
        offsets: List<Int>,
        changes: List<Boolean>
    ) {
        require(offsets.size == FIELD_COUNT && changes.size == FIELD_COUNT)
        val before = read(original)
        val after = read(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The Crown Tundra edit changed title identity or save size."
        }
        require(after == expected) { "The Crown Tundra records did not reopen exactly." }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            offsets.indices.any { changes[it] && index in offsets[it] until offsets[it] + FIELD_SIZE } ||
                index in hashOffset until original.size ||
                original[index] == edited[index]
        }) { "The Crown Tundra edit changed unrelated encrypted container data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class SwordShieldCrownTundraProgressCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: SwordShieldCrownTundraProgressEditRead,
    val after: SwordShieldCrownTundraProgressEditRead,
    val offsets: List<Int>,
    val changes: List<Boolean>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldCrownTundraProgressEditing.sha256(this.original)
    val editedSha256 = SwordShieldCrownTundraProgressEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Crown Tundra Progress." }
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

    private fun verifyPlan(candidate: ByteArray) = SwordShieldCrownTundraProgressEditing.verify(
        original, candidate, after, offsets, changes
    )
}
