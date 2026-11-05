package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Gen 1-ordered Trainer Data edits for the native Let's Go status block. */
object LetsGoTrainerEditing {
    private const val STATUS_OFFSET = 0x1000
    private const val STATUS_LENGTH = 0x168
    private const val NAME_OFFSET = STATUS_OFFSET + 0x38
    private const val NAME_SIZE = 26
    private const val TRAINER_ID_OFFSET = STATUS_OFFSET
    private const val TRAINER_ID_SIZE = 2
    private const val MISC_OFFSET = 0x4C00
    private const val MISC_LENGTH = 0x930
    private const val MONEY_OFFSET = MISC_OFFSET + 4
    private const val MONEY_SIZE = 4
    private const val MAX_MONEY = 9_999_999L
    private const val EVENT_WORK_OFFSET = 0x1200
    private const val EVENT_WORK_LENGTH = 0x1800
    private const val EVENT_FLAG_OFFSET = EVENT_WORK_OFFSET + 1000 * 4
    private const val FIRST_BADGE_FLAG = 0x08C
    private const val BADGE_FLAG_COUNT = 8
    private const val PLAY_TIME_OFFSET = 0x45400
    private const val PLAY_TIME_LENGTH = 8
    private const val PLAY_TIME_SIZE = 4
    private const val STATUS_CRC_OFFSET = LetsGoSaveValidation.DESCRIPTORS + (2 * 8) + 6
    private const val EVENT_WORK_CRC_OFFSET = LetsGoSaveValidation.DESCRIPTORS + (3 * 8) + 6
    private const val MISC_CRC_OFFSET = LetsGoSaveValidation.DESCRIPTORS + (5 * 8) + 6
    private const val PLAY_TIME_CRC_OFFSET = LetsGoSaveValidation.DESCRIPTORS + (10 * 8) + 6

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val remaining = LetsGoSaveValidation.FILE_SIZE + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= LetsGoSaveValidation.FILE_SIZE) {
                "The document exceeds the supported Let's Go save size."
            }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        requestedName: String,
        requestedTrainerId: Int,
        requestedMoney: Long,
        requestedBadgeCount: Int,
        requestedPlayHours: Int,
        requestedPlayMinutes: Int,
        requestedPlaySeconds: Int
    ): LetsGoTrainerCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = LetsGoTrainerReadOnly.read(original)
        val name = if (requestedName == before.name) before.name else requestedName.trim()
        require(requestedTrainerId in 0..65535) { "Trainer ID must be between 0 and 65535." }
        require(requestedMoney in 0L..MAX_MONEY) { "Money must be between 0 and 9,999,999." }
        require(requestedBadgeCount in 0..8) { "Badges must be between 0 and 8." }
        require(requestedPlayHours in 0..65535) { "Play Time hours must be between 0 and 65535." }
        require(requestedPlayMinutes in 0..59) { "Play Time minutes must be between 0 and 59." }
        require(requestedPlaySeconds in 0..59) { "Play Time seconds must be between 0 and 59." }
        val beforePlayTime = LetsGoPlayTimeReadOnly.read(original)
        val requestedBadgeMask = if (requestedBadgeCount == before.badgeCount) before.badgeMask
            else (1 shl requestedBadgeCount) - 1
        val nameChanged = name != before.name
        val trainerIdChanged = requestedTrainerId != before.tid16
        val moneyChanged = requestedMoney != before.money
        val badgesChanged = requestedBadgeMask != before.badgeMask
        val playTimeChanged = requestedPlayHours != beforePlayTime.hours ||
            requestedPlayMinutes != beforePlayTime.minutes || requestedPlaySeconds != beforePlayTime.seconds
        require(nameChanged || trainerIdChanged || moneyChanged || badgesChanged || playTimeChanged) {
            "Change the Trainer Name, Trainer ID, Money, Badges, or Play Time before reviewing."
        }

        val edited = original.copyOf()
        if (nameChanged) encodeName(name).copyInto(edited, NAME_OFFSET)
        if (trainerIdChanged) writeU16(edited, TRAINER_ID_OFFSET, requestedTrainerId)
        if (nameChanged || trainerIdChanged) {
            writeU16(edited, STATUS_CRC_OFFSET, LetsGoSaveValidation.crc16(edited, STATUS_OFFSET, STATUS_LENGTH))
        }
        if (moneyChanged) {
            writeU32(edited, MONEY_OFFSET, requestedMoney)
            writeU16(edited, MISC_CRC_OFFSET, LetsGoSaveValidation.crc16(edited, MISC_OFFSET, MISC_LENGTH))
        }
        if (badgesChanged) {
            writeBadgeMask(edited, requestedBadgeMask)
            writeU16(edited, EVENT_WORK_CRC_OFFSET,
                LetsGoSaveValidation.crc16(edited, EVENT_WORK_OFFSET, EVENT_WORK_LENGTH))
        }
        if (playTimeChanged) {
            writeU16(edited, PLAY_TIME_OFFSET, requestedPlayHours)
            edited[PLAY_TIME_OFFSET + 2] = requestedPlayMinutes.toByte()
            edited[PLAY_TIME_OFFSET + 3] = requestedPlaySeconds.toByte()
            writeU16(edited, PLAY_TIME_CRC_OFFSET,
                LetsGoSaveValidation.crc16(edited, PLAY_TIME_OFFSET, PLAY_TIME_LENGTH))
        }
        verify(
            original, edited, name, requestedTrainerId, requestedMoney, requestedBadgeMask,
            requestedPlayHours, requestedPlayMinutes, requestedPlaySeconds,
            nameChanged, trainerIdChanged, moneyChanged, badgesChanged, playTimeChanged
        )
        return LetsGoTrainerCopyPlan(
            original, edited, before.name, name, before.tid16, requestedTrainerId,
            before.money, requestedMoney, before.badgeMask, requestedBadgeMask,
            beforePlayTime.hours, requestedPlayHours, beforePlayTime.minutes, requestedPlayMinutes,
            beforePlayTime.seconds, requestedPlaySeconds,
            nameChanged, trainerIdChanged, moneyChanged, badgesChanged, playTimeChanged
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expectedName: String,
        expectedTrainerId: Int,
        expectedMoney: Long,
        expectedBadgeMask: Int,
        expectedPlayHours: Int,
        expectedPlayMinutes: Int,
        expectedPlaySeconds: Int,
        nameChanged: Boolean,
        trainerIdChanged: Boolean,
        moneyChanged: Boolean,
        badgesChanged: Boolean,
        playTimeChanged: Boolean
    ) {
        val before = LetsGoTrainerReadOnly.read(original)
        val after = LetsGoTrainerReadOnly.read(edited)
        val beforePlayTime = LetsGoPlayTimeReadOnly.read(original)
        val afterPlayTime = LetsGoPlayTimeReadOnly.read(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The trainer edit changed title identity or save size."
        }
        val expectedId32 = (before.id32 and 0xFFFF0000L) or expectedTrainerId.toLong()
        require(
            after.name == expectedName && after.tid16 == expectedTrainerId &&
                after.money == expectedMoney && after.badgeMask == expectedBadgeMask &&
                before.copy(
                    name = expectedName, id32 = expectedId32, tid16 = expectedTrainerId,
                    money = expectedMoney, badgeMask = expectedBadgeMask
                ) == after
        ) {
            "The Trainer Data edit did not reopen exactly."
        }
        require(
            afterPlayTime.hours == expectedPlayHours && afterPlayTime.minutes == expectedPlayMinutes &&
                afterPlayTime.seconds == expectedPlaySeconds &&
                beforePlayTime.version == afterPlayTime.version &&
                beforePlayTime.rawLastSavedWord == afterPlayTime.rawLastSavedWord
        ) { "The Play Time edit did not reopen exactly." }
        require(original.indices.all { index ->
            (nameChanged && index in NAME_OFFSET until NAME_OFFSET + NAME_SIZE) ||
                (trainerIdChanged && index in TRAINER_ID_OFFSET until TRAINER_ID_OFFSET + TRAINER_ID_SIZE) ||
                (moneyChanged && index in MONEY_OFFSET until MONEY_OFFSET + MONEY_SIZE) ||
                (badgesChanged && badgeByteChangeAllowed(original, edited, index)) ||
                (playTimeChanged && index in PLAY_TIME_OFFSET until PLAY_TIME_OFFSET + PLAY_TIME_SIZE) ||
                ((nameChanged || trainerIdChanged) && index in STATUS_CRC_OFFSET until STATUS_CRC_OFFSET + 2) ||
                (badgesChanged && index in EVENT_WORK_CRC_OFFSET until EVENT_WORK_CRC_OFFSET + 2) ||
                (moneyChanged && index in MISC_CRC_OFFSET until MISC_CRC_OFFSET + 2) ||
                (playTimeChanged && index in PLAY_TIME_CRC_OFFSET until PLAY_TIME_CRC_OFFSET + 2) ||
                original[index] == edited[index]
        }) { "The Trainer Data edit changed unrelated save data." }
    }

    private fun writeBadgeMask(bytes: ByteArray, mask: Int) {
        repeat(BADGE_FLAG_COUNT) { index ->
            val flag = FIRST_BADGE_FLAG + index
            val offset = EVENT_FLAG_OFFSET + (flag ushr 3)
            val bit = 1 shl (flag and 7)
            val value = bytes[offset].toInt() and 0xFF
            bytes[offset] = (if (mask and (1 shl index) != 0) value or bit else value and bit.inv()).toByte()
        }
    }

    private fun badgeByteChangeAllowed(original: ByteArray, edited: ByteArray, offset: Int): Boolean {
        val first = EVENT_FLAG_OFFSET + (FIRST_BADGE_FLAG ushr 3)
        val last = EVENT_FLAG_OFFSET + ((FIRST_BADGE_FLAG + BADGE_FLAG_COUNT - 1) ushr 3)
        if (offset !in first..last) return false
        var allowedBits = 0
        repeat(BADGE_FLAG_COUNT) { index ->
            val flag = FIRST_BADGE_FLAG + index
            if (EVENT_FLAG_OFFSET + (flag ushr 3) == offset) allowedBits = allowedBits or (1 shl (flag and 7))
        }
        return ((original[offset].toInt() xor edited[offset].toInt()) and allowedBits.inv() and 0xFF) == 0
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

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

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class LetsGoTrainerCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val beforeName: String,
    val afterName: String,
    val beforeTrainerId: Int,
    val afterTrainerId: Int,
    val beforeMoney: Long,
    val afterMoney: Long,
    val beforeBadgeMask: Int,
    val afterBadgeMask: Int,
    val beforePlayHours: Int,
    val afterPlayHours: Int,
    val beforePlayMinutes: Int,
    val afterPlayMinutes: Int,
    val beforePlaySeconds: Int,
    val afterPlaySeconds: Int,
    val nameChanged: Boolean,
    val trainerIdChanged: Boolean,
    val moneyChanged: Boolean,
    val badgesChanged: Boolean,
    val playTimeChanged: Boolean
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = LetsGoTrainerEditing.sha256(this.original)
    val editedSha256: String = LetsGoTrainerEditing.sha256(this.edited)
    val nameOffset: Int = 0x1038
    val trainerIdOffset: Int = 0x1000
    val moneyOffset: Int = 0x4C04
    val badgeFlagOffset: Int = 0x21B1
    val playTimeOffset: Int = 0x45400
    val beforeBadgeCount: Int = Integer.bitCount(beforeBadgeMask)
    val afterBadgeCount: Int = Integer.bitCount(afterBadgeMask)
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
            LetsGoTrainerEditing.verify(
                original, edited, afterName, afterTrainerId, afterMoney, afterBadgeMask,
                afterPlayHours, afterPlayMinutes, afterPlaySeconds,
                nameChanged, trainerIdChanged, moneyChanged, badgesChanged, playTimeChanged
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LetsGoTrainerEditing.verify(
                original, persisted, afterName, afterTrainerId, afterMoney, afterBadgeMask,
                afterPlayHours, afterPlayMinutes, afterPlaySeconds,
                nameChanged, trainerIdChanged, moneyChanged, badgesChanged, playTimeChanged
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
