package com.example.pokemonsavemanager

import java.io.InputStream
import java.security.MessageDigest

data class SwordShieldAdventureStepEditRead(val version: SwordShieldVersion, val values: List<Long>)

/** Reviewed editing for the four documented native Sword/Shield adventure-step counters. */
object SwordShieldAdventureStepEditing {
    const val FIELD_COUNT = 4
    private val fieldSizes = listOf(4, 4, 2, 1)

    fun readDocumentBytes(input: InputStream): ByteArray = SwordShieldTrainerEditing.readDocumentBytes(input)

    fun read(source: ByteArray): SwordShieldAdventureStepEditRead {
        val blocks = SwordShieldSaveValidation.readAdventureStepBlocks(source)
        fun value(bytes: ByteArray): Long = bytes.foldIndexed(0L) { index, result, byte ->
            result or ((byte.toLong() and 0xFF) shl (index * 8))
        }
        return SwordShieldAdventureStepEditRead(
            blocks.version,
            listOf(
                value(blocks.bikeBoostChargeSteps),
                value(blocks.bikeBoostChargeLimit),
                value(blocks.eggHatchCycleSteps),
                value(blocks.friendshipWalkSteps)
            )
        )
    }

    fun stage(source: ByteArray, expectedSha256: String, replacement: List<Long>): SwordShieldAdventureStepCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(replacement.size == FIELD_COUNT &&
            replacement[0] in 0L..0xFFFF_FFFFL && replacement[1] in 0L..0xFFFF_FFFFL &&
            replacement[2] in 0L..0xFFFFL && replacement[3] in 0L..0xFFL
        ) {
            "Adventure counters must fit their native UInt32, UInt32, UInt16, and UInt8 fields."
        }
        val before = read(original)
        val changes = replacement.indices.map { replacement[it] != before.values[it] }
        require(!changes[2] || replacement[2] in 0L..127L) {
            "A changed Egg hatch-cycle counter must use PKHeX's documented 0–127 range."
        }
        require(!changes[3] || replacement[3] in 0L..127L) {
            "A changed friendship-walk counter must use PKHeX's documented 0–127 range."
        }
        require(changes.any { it }) { "Change at least one Adventure Step Counter before reviewing." }
        val written = SwordShieldSaveValidation.writeAdventureStepRecords(original, replacement)
        val after = SwordShieldAdventureStepEditRead(before.version, replacement.toList())
        verify(original, written.bytes, after, written.offsets, changes)
        return SwordShieldAdventureStepCopyPlan(
            original, written.bytes, before, after, written.offsets.toList(), changes
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: SwordShieldAdventureStepEditRead,
        offsets: List<Int>,
        changes: List<Boolean>
    ) {
        require(offsets.size == FIELD_COUNT && changes.size == FIELD_COUNT)
        val before = read(original)
        val after = read(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The adventure-step edit changed title identity or save size."
        }
        require(after == expected) { "The Adventure Step Counters did not reopen exactly." }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            offsets.indices.any { changes[it] && index in offsets[it] until offsets[it] + fieldSizes[it] } ||
                index in hashOffset until original.size ||
                original[index] == edited[index]
        }) { "The adventure-step edit changed unrelated encrypted container data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class SwordShieldAdventureStepCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: SwordShieldAdventureStepEditRead,
    val after: SwordShieldAdventureStepEditRead,
    val offsets: List<Int>,
    val changes: List<Boolean>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldAdventureStepEditing.sha256(this.original)
    val editedSha256 = SwordShieldAdventureStepEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Adventure Step Counters." }
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

    private fun verifyPlan(candidate: ByteArray) = SwordShieldAdventureStepEditing.verify(
        original, candidate, after, offsets, changes
    )
}
