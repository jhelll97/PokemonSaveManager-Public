package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Gen 1-ordered Legends: Z-A Trainer Data edits backed by the native status object. */
object LegendsZaTrainerEditing {
    private const val MAX_SAVE_SIZE = 0x309FB3
    private const val NAME_SIZE = 26
    private const val TRAINER_ID_SIZE = 2
    private const val MONEY_SIZE = 4
    private const val PLAY_TIME_SIZE = 8

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
                "The document exceeds the supported Legends: Z-A save size."
            }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        requestedName: String,
        requestedTrainerId: Int,
        requestedMoney: Long?,
        requestedPlayHours: Int,
        requestedPlayMinutes: Int,
        requestedPlaySeconds: Int
    ): LegendsZaTrainerCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = LegendsZaTrainerReadOnly.read(original)
        val beforePlayTime = LegendsZaPlayTimeReadOnly.read(original)
        val name = if (requestedName == before.name) before.name else requestedName.trim()
        require(requestedTrainerId in 0..65535) { "Trainer ID must be between 0 and 65535." }
        require(before.money != null || requestedMoney == null) {
            "This save does not contain the native money counter."
        }
        require(requestedPlayHours in 0..65535) { "Play Time hours must be between 0 and 65535." }
        require(requestedPlayMinutes in 0..59) { "Play Time minutes must be between 0 and 59." }
        require(requestedPlaySeconds in 0..59) { "Play Time seconds must be between 0 and 59." }
        val nameChanged = name != before.name
        val trainerIdChanged = requestedTrainerId != before.tid16
        val moneyChanged = requestedMoney != null && requestedMoney != before.money
        val playTimeChanged = requestedPlayHours != beforePlayTime.hours ||
            requestedPlayMinutes != beforePlayTime.minutes || requestedPlaySeconds != beforePlayTime.seconds
        if (moneyChanged) require(requestedMoney != null && requestedMoney in 0L..9_999_999L) {
            "Money must be between 0 and 9,999,999."
        }
        require(nameChanged || trainerIdChanged || moneyChanged || playTimeChanged) {
            "Change the Trainer Name, Trainer ID, Money, or Play Time before reviewing."
        }
        val afterMoney = if (moneyChanged) requestedMoney else before.money
        val afterRawSeconds = if (playTimeChanged) {
            requestedPlayHours * 3_600.0 + requestedPlayMinutes * 60.0 +
                requestedPlaySeconds.toDouble() + beforePlayTime.fractionalSeconds
        } else beforePlayTime.rawSeconds
        val write = LegendsZaSaveValidation.writeTrainerFields(
            original,
            if (nameChanged) encodeName(name) else null,
            if (trainerIdChanged) requestedTrainerId else null,
            if (moneyChanged) requestedMoney else null,
            if (playTimeChanged) encodePlayTime(afterRawSeconds) else null
        )
        verify(
            original, write.bytes, name, requestedTrainerId, afterMoney, afterRawSeconds,
            requestedPlayHours, requestedPlayMinutes, requestedPlaySeconds, write.nameOffset,
            write.trainerIdOffset, write.moneyOffset, write.playTimeOffset,
            nameChanged, trainerIdChanged, moneyChanged, playTimeChanged
        )
        return LegendsZaTrainerCopyPlan(
            original, write.bytes, before.name, name, before.tid16, requestedTrainerId,
            before.money, afterMoney, beforePlayTime.hours, requestedPlayHours,
            beforePlayTime.minutes, requestedPlayMinutes, beforePlayTime.seconds, requestedPlaySeconds,
            afterRawSeconds, write.nameOffset, write.trainerIdOffset, write.moneyOffset, write.playTimeOffset,
            nameChanged, trainerIdChanged, moneyChanged, playTimeChanged
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expectedName: String,
        expectedTrainerId: Int,
        expectedMoney: Long?,
        expectedRawSeconds: Double,
        expectedPlayHours: Int,
        expectedPlayMinutes: Int,
        expectedPlaySeconds: Int,
        nameOffset: Int,
        trainerIdOffset: Int,
        moneyOffset: Int,
        playTimeOffset: Int,
        nameChanged: Boolean,
        trainerIdChanged: Boolean,
        moneyChanged: Boolean,
        playTimeChanged: Boolean
    ) {
        val beforeValidation = LegendsZaSaveValidation.inspect(original)
        val afterValidation = LegendsZaSaveValidation.inspect(edited)
        require(beforeValidation is LegendsZaValidation.Verified &&
            afterValidation is LegendsZaValidation.Verified &&
            beforeValidation.version == afterValidation.version &&
            beforeValidation.revision == afterValidation.revision && original.size == edited.size) {
            "The trainer edit changed title identity or save size."
        }
        val before = LegendsZaTrainerReadOnly.read(original)
        val after = LegendsZaTrainerReadOnly.read(edited)
        val beforePlayTime = LegendsZaPlayTimeReadOnly.read(original)
        val afterPlayTime = LegendsZaPlayTimeReadOnly.read(edited)
        val expectedId32 = (before.id32 and 0xFFFF0000L) or expectedTrainerId.toLong()
        require(
            after.name == expectedName && after.tid16 == expectedTrainerId && after.money == expectedMoney &&
                before.copy(name = expectedName, id32 = expectedId32, tid16 = expectedTrainerId,
                    money = expectedMoney) == after
        ) {
            "The Trainer Data edit did not reopen exactly."
        }
        require(
            afterPlayTime.version == beforePlayTime.version && afterPlayTime.revision == beforePlayTime.revision &&
                afterPlayTime.rawSeconds == expectedRawSeconds &&
                afterPlayTime.hours == expectedPlayHours && afterPlayTime.minutes == expectedPlayMinutes &&
                afterPlayTime.seconds == expectedPlaySeconds
        ) { "The Play Time edit did not reopen exactly after carrying forward its fractional second." }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            (nameChanged && index in nameOffset until nameOffset + NAME_SIZE) ||
                (trainerIdChanged && index in trainerIdOffset until trainerIdOffset + TRAINER_ID_SIZE) ||
                (moneyChanged && index in moneyOffset until moneyOffset + MONEY_SIZE) ||
                (playTimeChanged && index in playTimeOffset until playTimeOffset + PLAY_TIME_SIZE) ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "The Trainer Data edit changed unrelated save data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun encodePlayTime(rawSeconds: Double): ByteArray {
        require(rawSeconds.isFinite() && rawSeconds >= 0.0) { "Play Time cannot be encoded safely." }
        val bits = java.lang.Double.doubleToRawLongBits(rawSeconds)
        return ByteArray(PLAY_TIME_SIZE) { index -> (bits ushr (index * 8)).toByte() }
    }

    private fun encodeName(name: String): ByteArray {
        require(name.isNotEmpty()) { "Trainer name must contain 1–12 characters." }
        require(name.length <= 12) { "Trainer name must contain at most 12 UTF-16 code units." }
        require(name.none { Character.isISOControl(it) }) { "Trainer name cannot contain control characters." }
        val encoded = try {
            val buffer = Charsets.UTF_16LE.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(name))
            ByteArray(buffer.remaining()).also { buffer.get(it) }
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("Trainer name contains malformed Unicode.")
        }
        require(encoded.size <= 24) { "Trainer name must fit the native 12-unit field." }
        return ByteArray(NAME_SIZE).also { encoded.copyInto(it) }
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class LegendsZaTrainerCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val beforeName: String,
    val afterName: String,
    val beforeTrainerId: Int,
    val afterTrainerId: Int,
    val beforeMoney: Long?,
    val afterMoney: Long?,
    val beforePlayHours: Int,
    val afterPlayHours: Int,
    val beforePlayMinutes: Int,
    val afterPlayMinutes: Int,
    val beforePlaySeconds: Int,
    val afterPlaySeconds: Int,
    val afterRawSeconds: Double,
    val nameOffset: Int,
    val trainerIdOffset: Int,
    val moneyOffset: Int,
    val playTimeOffset: Int,
    val nameChanged: Boolean,
    val trainerIdChanged: Boolean,
    val moneyChanged: Boolean,
    val playTimeChanged: Boolean
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LegendsZaTrainerEditing.sha256(this.original)
    val editedSha256: String = LegendsZaTrainerEditing.sha256(this.edited)
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
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) { "The recovery backup did not verify. Nothing was written." }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LegendsZaTrainerEditing.verify(
                original, edited, afterName, afterTrainerId, afterMoney, afterRawSeconds,
                afterPlayHours, afterPlayMinutes, afterPlaySeconds, nameOffset, trainerIdOffset,
                moneyOffset, playTimeOffset, nameChanged, trainerIdChanged, moneyChanged, playTimeChanged
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LegendsZaTrainerEditing.verify(
                original, persisted, afterName, afterTrainerId, afterMoney, afterRawSeconds,
                afterPlayHours, afterPlayMinutes, afterPlaySeconds, nameOffset, trainerIdOffset,
                moneyOffset, playTimeOffset, nameChanged, trainerIdChanged, moneyChanged, playTimeChanged
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
