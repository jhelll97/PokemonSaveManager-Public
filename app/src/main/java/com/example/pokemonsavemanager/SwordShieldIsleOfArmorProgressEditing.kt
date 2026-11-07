package com.example.pokemonsavemanager

import java.io.InputStream
import java.security.MessageDigest

data class SwordShieldIsleOfArmorProgressEditRead(
    val version: SwordShieldVersion,
    val alolanDiglettFound: Long,
    val dojoDonationProgress: Long,
    val dojoDonationTotal: Long,
    val cramOMaticUses: Long,
    val restrictedSparringTypesCleared: Long
)

/** Reviewed editing for five documented native Isle of Armor progress records. */
object SwordShieldIsleOfArmorProgressEditing {
    private const val FIELD_SIZE = 4

    fun readDocumentBytes(input: InputStream): ByteArray = SwordShieldTrainerEditing.readDocumentBytes(input)

    fun read(source: ByteArray): SwordShieldIsleOfArmorProgressEditRead {
        val blocks = SwordShieldSaveValidation.readIsleOfArmorProgressBlocks(source)
        fun value(bytes: ByteArray): Long = bytes.foldIndexed(0L) { index, result, byte ->
            result or ((byte.toLong() and 0xFF) shl (index * 8))
        }
        return SwordShieldIsleOfArmorProgressEditRead(
            blocks.version,
            value(blocks.alolanDiglettFound),
            value(blocks.dojoDonationProgress),
            value(blocks.dojoDonationTotal),
            value(blocks.cramOMaticUses),
            value(blocks.restrictedSparringTypesCleared)
        )
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        alolanDiglettFound: Long,
        dojoDonationProgress: Long,
        dojoDonationTotal: Long,
        cramOMaticUses: Long,
        restrictedSparringTypesCleared: Long
    ): SwordShieldIsleOfArmorProgressCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val values = listOf(
            alolanDiglettFound, dojoDonationProgress, dojoDonationTotal,
            cramOMaticUses, restrictedSparringTypesCleared
        )
        require(values.all { it in 0L..0xFFFF_FFFFL }) {
            "Each Isle of Armor progress record must fit its native unsigned 32-bit field."
        }
        val before = read(original)
        val changes = listOf(
            alolanDiglettFound != before.alolanDiglettFound,
            dojoDonationProgress != before.dojoDonationProgress,
            dojoDonationTotal != before.dojoDonationTotal,
            cramOMaticUses != before.cramOMaticUses,
            restrictedSparringTypesCleared != before.restrictedSparringTypesCleared
        )
        require(!changes[1] || dojoDonationProgress in 0L..15L) {
            "A changed Master Dojo donation stage must be between 0 and 15."
        }
        require(changes.any { it }) { "Change at least one Isle of Armor record before reviewing." }
        val written = SwordShieldSaveValidation.writeIsleOfArmorProgress(
            original, alolanDiglettFound, dojoDonationProgress, dojoDonationTotal,
            cramOMaticUses, restrictedSparringTypesCleared
        )
        val offsets = listOf(
            written.alolanDiglettFoundOffset,
            written.dojoDonationProgressOffset,
            written.dojoDonationTotalOffset,
            written.cramOMaticUsesOffset,
            written.restrictedSparringTypesClearedOffset
        )
        val after = SwordShieldIsleOfArmorProgressEditRead(
            before.version, alolanDiglettFound, dojoDonationProgress, dojoDonationTotal,
            cramOMaticUses, restrictedSparringTypesCleared
        )
        verify(original, written.bytes, after, offsets, changes)
        return SwordShieldIsleOfArmorProgressCopyPlan(original, written.bytes, before, after, offsets, changes)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: SwordShieldIsleOfArmorProgressEditRead,
        offsets: List<Int>,
        changes: List<Boolean>
    ) {
        require(offsets.size == 5 && changes.size == 5)
        val before = read(original)
        val after = read(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The Isle of Armor edit changed title identity or save size."
        }
        require(after == expected) { "The Isle of Armor records did not reopen exactly." }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            offsets.indices.any { changes[it] && index in offsets[it] until offsets[it] + FIELD_SIZE } ||
                index in hashOffset until original.size ||
                original[index] == edited[index]
        }) { "The Isle of Armor edit changed unrelated encrypted container data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class SwordShieldIsleOfArmorProgressCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: SwordShieldIsleOfArmorProgressEditRead,
    val after: SwordShieldIsleOfArmorProgressEditRead,
    val offsets: List<Int>,
    val changes: List<Boolean>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldIsleOfArmorProgressEditing.sha256(this.original)
    val editedSha256 = SwordShieldIsleOfArmorProgressEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Isle of Armor Progress." }
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

    private fun verifyPlan(candidate: ByteArray) = SwordShieldIsleOfArmorProgressEditing.verify(
        original, candidate, after, offsets, changes
    )
}
