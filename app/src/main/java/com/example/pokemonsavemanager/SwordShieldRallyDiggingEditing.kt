package com.example.pokemonsavemanager

import java.io.InputStream
import java.security.MessageDigest

/** Reviewed editing for the four documented native Sword/Shield rally and digging records. */
object SwordShieldRallyDiggingEditing {
    private const val FIELD_SIZE = 4

    fun readDocumentBytes(input: InputStream): ByteArray = SwordShieldTrainerEditing.readDocumentBytes(input)

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        rotoRallyScore: Long,
        diggingDuoSkillStreak: Long,
        diggingDuoStaminaStreak: Long,
        diggingPaWatts: Long
    ): SwordShieldRallyDiggingCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = SwordShieldRallyDiggingReadOnly.read(original)
        val rallyChanged = rotoRallyScore != before.rotoRallyScore
        val skillChanged = diggingDuoSkillStreak != before.diggingDuoSkillStreak
        val staminaChanged = diggingDuoStaminaStreak != before.diggingDuoStaminaStreak
        val wattsChanged = diggingPaWatts != before.diggingPaWatts
        require(!rallyChanged || rotoRallyScore in 0L..99_999L) {
            "A changed Roto Rally best score must be between 0 and 99,999."
        }
        require(listOf(diggingDuoSkillStreak, diggingDuoStaminaStreak, diggingPaWatts).all {
            it in 0L..0xFFFF_FFFFL
        }) { "Digging records must fit their native unsigned 32-bit fields." }
        require(rallyChanged || skillChanged || staminaChanged || wattsChanged) {
            "Change at least one rally or digging record before reviewing."
        }
        val written = SwordShieldSaveValidation.writeRallyDiggingRecords(
            original, rotoRallyScore, diggingDuoSkillStreak, diggingDuoStaminaStreak, diggingPaWatts
        )
        verify(
            original, written.bytes, rotoRallyScore, diggingDuoSkillStreak,
            diggingDuoStaminaStreak, diggingPaWatts,
            written.rotoRallyScoreOffset, written.diggingDuoSkillStreakOffset,
            written.diggingDuoStaminaStreakOffset, written.diggingPaWattsOffset,
            rallyChanged, skillChanged, staminaChanged, wattsChanged
        )
        return SwordShieldRallyDiggingCopyPlan(
            original, written.bytes,
            before.rotoRallyScore, rotoRallyScore,
            before.diggingDuoSkillStreak, diggingDuoSkillStreak,
            before.diggingDuoStaminaStreak, diggingDuoStaminaStreak,
            before.diggingPaWatts, diggingPaWatts,
            written.rotoRallyScoreOffset, written.diggingDuoSkillStreakOffset,
            written.diggingDuoStaminaStreakOffset, written.diggingPaWattsOffset,
            rallyChanged, skillChanged, staminaChanged, wattsChanged
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expectedRotoRallyScore: Long,
        expectedDiggingDuoSkillStreak: Long,
        expectedDiggingDuoStaminaStreak: Long,
        expectedDiggingPaWatts: Long,
        rotoRallyScoreOffset: Int,
        diggingDuoSkillStreakOffset: Int,
        diggingDuoStaminaStreakOffset: Int,
        diggingPaWattsOffset: Int,
        rallyChanged: Boolean,
        skillChanged: Boolean,
        staminaChanged: Boolean,
        wattsChanged: Boolean
    ) {
        val before = SwordShieldRallyDiggingReadOnly.read(original)
        val after = SwordShieldRallyDiggingReadOnly.read(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The rally or digging edit changed title identity or save size."
        }
        require(
            after == SwordShieldRallyDiggingRead(
                before.version, expectedRotoRallyScore, expectedDiggingDuoSkillStreak,
                expectedDiggingDuoStaminaStreak, expectedDiggingPaWatts
            )
        ) { "The rally and digging records did not reopen exactly." }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            (rallyChanged && index in rotoRallyScoreOffset until rotoRallyScoreOffset + FIELD_SIZE) ||
                (skillChanged && index in diggingDuoSkillStreakOffset until diggingDuoSkillStreakOffset + FIELD_SIZE) ||
                (staminaChanged && index in diggingDuoStaminaStreakOffset until diggingDuoStaminaStreakOffset + FIELD_SIZE) ||
                (wattsChanged && index in diggingPaWattsOffset until diggingPaWattsOffset + FIELD_SIZE) ||
                index in hashOffset until original.size ||
                original[index] == edited[index]
        }) { "The rally or digging edit changed unrelated encrypted container data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class SwordShieldRallyDiggingCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val beforeRotoRallyScore: Long,
    val afterRotoRallyScore: Long,
    val beforeDiggingDuoSkillStreak: Long,
    val afterDiggingDuoSkillStreak: Long,
    val beforeDiggingDuoStaminaStreak: Long,
    val afterDiggingDuoStaminaStreak: Long,
    val beforeDiggingPaWatts: Long,
    val afterDiggingPaWatts: Long,
    val rotoRallyScoreOffset: Int,
    val diggingDuoSkillStreakOffset: Int,
    val diggingDuoStaminaStreakOffset: Int,
    val diggingPaWattsOffset: Int,
    val rallyChanged: Boolean,
    val skillChanged: Boolean,
    val staminaChanged: Boolean,
    val wattsChanged: Boolean
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = SwordShieldRallyDiggingEditing.sha256(this.original)
    val editedSha256 = SwordShieldRallyDiggingEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Rally & Digging Records." }
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

    private fun verifyPlan(candidate: ByteArray) = SwordShieldRallyDiggingEditing.verify(
        original, candidate, afterRotoRallyScore, afterDiggingDuoSkillStreak,
        afterDiggingDuoStaminaStreak, afterDiggingPaWatts,
        rotoRallyScoreOffset, diggingDuoSkillStreakOffset,
        diggingDuoStaminaStreakOffset, diggingPaWattsOffset,
        rallyChanged, skillChanged, staminaChanged, wattsChanged
    )
}
