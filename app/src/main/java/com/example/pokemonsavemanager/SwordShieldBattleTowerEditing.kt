package com.example.pokemonsavemanager

import java.io.InputStream
import java.security.MessageDigest

/** Reviewed editing for the four documented native Sword/Shield Battle Tower record scalars. */
object SwordShieldBattleTowerEditing {
    private const val VICTORIES_SIZE = 4
    private const val STREAK_SIZE = 2

    fun readDocumentBytes(input: InputStream): ByteArray = SwordShieldTrainerEditing.readDocumentBytes(input)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        singlesVictories: Long,
        doublesVictories: Long,
        singlesStreak: Int,
        doublesStreak: Int
    ): SwordShieldBattleTowerCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = SwordShieldBattleTowerReadOnly.read(original)
        val singlesVictoriesChanged = singlesVictories != before.singlesVictories
        val doublesVictoriesChanged = doublesVictories != before.doublesVictories
        val singlesStreakChanged = singlesStreak != before.singlesStreak
        val doublesStreakChanged = doublesStreak != before.doublesStreak
        require(!singlesVictoriesChanged || singlesVictories in 0L..9_999_999L) {
            "A changed Singles victory total must be between 0 and 9,999,999."
        }
        require(!doublesVictoriesChanged || doublesVictories in 0L..9_999_999L) {
            "A changed Doubles victory total must be between 0 and 9,999,999."
        }
        require(!singlesStreakChanged || singlesStreak in 0..300) {
            "A changed Singles streak must be between 0 and 300."
        }
        require(!doublesStreakChanged || doublesStreak in 0..300) {
            "A changed Doubles streak must be between 0 and 300."
        }
        require(singlesVictoriesChanged || doublesVictoriesChanged || singlesStreakChanged || doublesStreakChanged) {
            "Change at least one Battle Tower record before reviewing."
        }
        val written = SwordShieldSaveValidation.writeBattleTowerRecords(
            original, singlesVictories, doublesVictories, singlesStreak, doublesStreak
        )
        verify(
            original, written.bytes, singlesVictories, doublesVictories, singlesStreak, doublesStreak,
            written.singlesVictoriesOffset, written.doublesVictoriesOffset,
            written.singlesStreakOffset, written.doublesStreakOffset,
            singlesVictoriesChanged, doublesVictoriesChanged, singlesStreakChanged, doublesStreakChanged
        )
        return SwordShieldBattleTowerCopyPlan(
            original, written.bytes,
            before.singlesVictories, singlesVictories,
            before.doublesVictories, doublesVictories,
            before.singlesStreak, singlesStreak,
            before.doublesStreak, doublesStreak,
            written.singlesVictoriesOffset, written.doublesVictoriesOffset,
            written.singlesStreakOffset, written.doublesStreakOffset,
            singlesVictoriesChanged, doublesVictoriesChanged, singlesStreakChanged, doublesStreakChanged
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expectedSinglesVictories: Long,
        expectedDoublesVictories: Long,
        expectedSinglesStreak: Int,
        expectedDoublesStreak: Int,
        singlesVictoriesOffset: Int,
        doublesVictoriesOffset: Int,
        singlesStreakOffset: Int,
        doublesStreakOffset: Int,
        singlesVictoriesChanged: Boolean,
        doublesVictoriesChanged: Boolean,
        singlesStreakChanged: Boolean,
        doublesStreakChanged: Boolean
    ) {
        val before = SwordShieldBattleTowerReadOnly.read(original)
        val after = SwordShieldBattleTowerReadOnly.read(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The Battle Tower edit changed title identity or save size."
        }
        require(
            after == SwordShieldBattleTowerRead(
                before.version, expectedSinglesVictories, expectedDoublesVictories,
                expectedSinglesStreak, expectedDoublesStreak
            )
        ) { "The Battle Tower records did not reopen exactly." }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            (singlesVictoriesChanged && index in singlesVictoriesOffset until singlesVictoriesOffset + VICTORIES_SIZE) ||
                (doublesVictoriesChanged && index in doublesVictoriesOffset until doublesVictoriesOffset + VICTORIES_SIZE) ||
                (singlesStreakChanged && index in singlesStreakOffset until singlesStreakOffset + STREAK_SIZE) ||
                (doublesStreakChanged && index in doublesStreakOffset until doublesStreakOffset + STREAK_SIZE) ||
                index in hashOffset until original.size ||
                original[index] == edited[index]
        }) { "The Battle Tower edit changed unrelated encrypted container data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class SwordShieldBattleTowerCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val beforeSinglesVictories: Long,
    val afterSinglesVictories: Long,
    val beforeDoublesVictories: Long,
    val afterDoublesVictories: Long,
    val beforeSinglesStreak: Int,
    val afterSinglesStreak: Int,
    val beforeDoublesStreak: Int,
    val afterDoublesStreak: Int,
    val singlesVictoriesOffset: Int,
    val doublesVictoriesOffset: Int,
    val singlesStreakOffset: Int,
    val doublesStreakOffset: Int,
    val singlesVictoriesChanged: Boolean,
    val doublesVictoriesChanged: Boolean,
    val singlesStreakChanged: Boolean,
    val doublesStreakChanged: Boolean
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldBattleTowerEditing.sha256(this.original)
    val editedSha256 = SwordShieldBattleTowerEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Battle Tower Records." }
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
            SwordShieldBattleTowerEditing.verify(
                original, edited, afterSinglesVictories, afterDoublesVictories,
                afterSinglesStreak, afterDoublesStreak,
                singlesVictoriesOffset, doublesVictoriesOffset, singlesStreakOffset, doublesStreakOffset,
                singlesVictoriesChanged, doublesVictoriesChanged, singlesStreakChanged, doublesStreakChanged
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            SwordShieldBattleTowerEditing.verify(
                original, persisted, afterSinglesVictories, afterDoublesVictories,
                afterSinglesStreak, afterDoublesStreak,
                singlesVictoriesOffset, doublesVictoriesOffset, singlesStreakOffset, doublesStreakOffset,
                singlesVictoriesChanged, doublesVictoriesChanged, singlesStreakChanged, doublesStreakChanged
            )
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
