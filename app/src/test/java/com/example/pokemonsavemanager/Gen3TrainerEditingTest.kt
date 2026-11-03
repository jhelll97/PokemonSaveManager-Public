package com.example.pokemonsavemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Gen3TrainerEditingTest {
    @Test
    fun setPlayTimeIncludesFrameCounter() {
        val edited = Gen3TrainerEditing.setPlayTime(
            Gen3TrainerState(name = "RED"),
            hours = 123,
            minutes = 45,
            seconds = 56,
            frames = 59
        )

        assertEquals(123, edited.playedHours)
        assertEquals(45, edited.playedMinutes)
        assertEquals(56, edited.playedSeconds)
        assertEquals(59, edited.playedFrames)
    }

    @Test
    fun resetPlayTimePreservesTrainerIdentity() {
        val original = Gen3TrainerState(
            name = "RED",
            trainerId = 1234,
            secretId = 5678,
            gender = 1,
            playedHours = 999,
            playedMinutes = 59,
            playedSeconds = 58,
            playedFrames = 57
        )

        val reset = Gen3TrainerEditing.resetPlayTime(original)

        assertEquals("RED", reset.name)
        assertEquals(1234, reset.trainerId)
        assertEquals(5678, reset.secretId)
        assertEquals(1, reset.gender)
        assertEquals(0, reset.playedHours)
        assertEquals(0, reset.playedMinutes)
        assertEquals(0, reset.playedSeconds)
        assertEquals(0, reset.playedFrames)
    }

    @Test
    fun changedFieldsReportsOnlyEditedGroups() {
        val original = Gen3TrainerState(
            name = "RED",
            trainerId = 1234,
            secretId = 5678,
            gender = 0,
            playedHours = 10,
            playedMinutes = 20,
            playedSeconds = 30,
            playedFrames = 40
        )
        val edited = original.copy(name = "BLUE", secretId = 8765, playedFrames = 41)

        assertEquals(
            listOf("Trainer name", "Secret ID", "Play time"),
            Gen3TrainerEditing.changedFields(original, edited)
        )
        assertEquals(emptyList<String>(), Gen3TrainerEditing.changedFields(original, original))
    }

    @Test
    fun changeSummaryShowsExactBeforeAndAfterValues() {
        val original = Gen3TrainerState(
            name = "RED",
            trainerId = 1234,
            secretId = 5678,
            gender = 0,
            playedHours = 10,
            playedMinutes = 20,
            playedSeconds = 30,
            playedFrames = 40
        )
        val edited = original.copy(
            name = "BLUE",
            trainerId = 4321,
            gender = 1,
            playedHours = 11,
            playedFrames = 41
        )

        assertEquals(
            listOf(
                "Trainer name: RED → BLUE",
                "Trainer ID: 1234 → 4321",
                "Player gender: Male → Female",
                "Play time: 10:20:30 + 40 frames → 11:20:30 + 41 frames"
            ),
            Gen3TrainerEditing.changeSummary(original, edited)
        )
        assertEquals(emptyList<String>(), Gen3TrainerEditing.changeSummary(original, original))
    }

    @Test
    fun restoreLoadedValuesReturnsCompleteLoadedState() {
        val loaded = Gen3TrainerState(
            name = "MAY",
            trainerId = 2222,
            secretId = 3333,
            money = 4444,
            gender = 1,
            playedHours = 55,
            playedMinutes = 44,
            playedSeconds = 33,
            playedFrames = 22
        )

        assertEquals(loaded, Gen3TrainerEditing.restoreLoadedValues(loaded))
    }

    @Test
    fun setPlayTimeRejectsOutOfRangeFrames() {
        try {
            Gen3TrainerEditing.setPlayTime(
                Gen3TrainerState(name = "RED"),
                hours = 0,
                minutes = 0,
                seconds = 0,
                frames = 60
            )
            fail("Expected out-of-range frames to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertEquals("frames must be between 0 and 59.", expected.message)
        }
    }
    @Test
    fun saveReviewMustBeAcknowledgedAndMatchCurrentChanges() {
        val original = Gen3TrainerState(name = "RED", trainerId = 1234)
        val reviewedEdit = original.copy(name = "BLUE")
        val reviewedSummary = Gen3TrainerEditing.changeSummary(original, reviewedEdit)

        assertFalse(
            Gen3TrainerEditing.isReviewedForSave(
                original,
                reviewedEdit,
                emptyList(),
                acknowledged = true
            )
        )
        assertFalse(
            Gen3TrainerEditing.isReviewedForSave(
                original,
                reviewedEdit,
                reviewedSummary,
                acknowledged = false
            )
        )
        assertFalse(
            Gen3TrainerEditing.isReviewedForSave(
                original,
                reviewedEdit.copy(trainerId = 4321),
                reviewedSummary,
                acknowledged = true
            )
        )
        assertTrue(
            Gen3TrainerEditing.isReviewedForSave(
                original,
                reviewedEdit,
                reviewedSummary,
                acknowledged = true
            )
        )
    }


}
