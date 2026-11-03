package com.example.pokemonsavemanager

/** Immutable Gen 3 trainer-data editing with format-safe bounds. */
object Gen3TrainerEditing {
    fun rename(trainer: Gen3TrainerState, name: String): Gen3TrainerState {
        require(name.length in 1..7) {
            "Gen 3 trainer names must contain between 1 and 7 characters."
        }
        return trainer.copy(name = name)
    }

    fun setTrainerIds(
        trainer: Gen3TrainerState,
        trainerId: Int,
        secretId: Int
    ): Gen3TrainerState {
        require(trainerId in 0..65535) { "trainerId must be a 16-bit unsigned value." }
        require(secretId in 0..65535) { "secretId must be a 16-bit unsigned value." }
        return trainer.copy(trainerId = trainerId, secretId = secretId)
    }

    fun setMoney(trainer: Gen3TrainerState, money: Int): Gen3TrainerState {
        require(money in 0..999999) { "money must be between 0 and 999999." }
        return trainer.copy(money = money)
    }

    fun setGender(trainer: Gen3TrainerState, gender: Int): Gen3TrainerState {
        require(gender in 0..1) { "gender must be 0 for male or 1 for female." }
        return trainer.copy(gender = gender)
    }

    fun changedFields(
        original: Gen3TrainerState,
        edited: Gen3TrainerState
    ): List<String> {
        val changed = mutableListOf<String>()
        if (original.name != edited.name) changed += "Trainer name"
        if (original.trainerId != edited.trainerId) changed += "Trainer ID"
        if (original.secretId != edited.secretId) changed += "Secret ID"
        if (original.gender != edited.gender) changed += "Player gender"
        if (original.playedHours != edited.playedHours ||
            original.playedMinutes != edited.playedMinutes ||
            original.playedSeconds != edited.playedSeconds ||
            original.playedFrames != edited.playedFrames) {
            changed += "Play time"
        }
        return changed
    }

    fun changeSummary(
        original: Gen3TrainerState,
        edited: Gen3TrainerState
    ): List<String> {
        val changes = mutableListOf<String>()
        if (original.name != edited.name) {
            changes += "Trainer name: ${original.name} → ${edited.name}"
        }
        if (original.trainerId != edited.trainerId) {
            changes += "Trainer ID: ${original.trainerId} → ${edited.trainerId}"
        }
        if (original.secretId != edited.secretId) {
            changes += "Secret ID: ${original.secretId} → ${edited.secretId}"
        }
        if (original.gender != edited.gender) {
            changes += "Player gender: ${genderLabel(original.gender)} → ${genderLabel(edited.gender)}"
        }
        if (original.playedHours != edited.playedHours ||
            original.playedMinutes != edited.playedMinutes ||
            original.playedSeconds != edited.playedSeconds ||
            original.playedFrames != edited.playedFrames) {
            changes += "Play time: ${playTimeLabel(original)} → ${playTimeLabel(edited)}"
        }
        return changes
    }

    fun isReviewedForSave(
        original: Gen3TrainerState,
        edited: Gen3TrainerState,
        reviewedSummary: List<String>,
        acknowledged: Boolean
    ): Boolean {
        val currentSummary = changeSummary(original, edited)
        return acknowledged &&
            currentSummary.isNotEmpty() &&
            reviewedSummary == currentSummary
    }

    private fun genderLabel(gender: Int): String =
        if (gender == 1) "Female" else "Male"

    private fun playTimeLabel(trainer: Gen3TrainerState): String =
        "${trainer.playedHours}:" +
            trainer.playedMinutes.toString().padStart(2, '0') + ":" +
            trainer.playedSeconds.toString().padStart(2, '0') + " + " +
            trainer.playedFrames.toString().padStart(2, '0') + " frames"

    fun restoreLoadedValues(loaded: Gen3TrainerState): Gen3TrainerState =
        loaded.copy()

    fun resetPlayTime(trainer: Gen3TrainerState): Gen3TrainerState =
        trainer.copy(
            playedHours = 0,
            playedMinutes = 0,
            playedSeconds = 0,
            playedFrames = 0
        )

    fun setPlayTime(
        trainer: Gen3TrainerState,
        hours: Int,
        minutes: Int,
        seconds: Int,
        frames: Int
    ): Gen3TrainerState {
        require(hours in 0..999) { "hours must be between 0 and 999." }
        require(minutes in 0..59) { "minutes must be between 0 and 59." }
        require(seconds in 0..59) { "seconds must be between 0 and 59." }
        require(frames in 0..59) { "frames must be between 0 and 59." }
        return trainer.copy(
            playedHours = hours,
            playedMinutes = minutes,
            playedSeconds = seconds,
            playedFrames = frames
        )
    }
}
