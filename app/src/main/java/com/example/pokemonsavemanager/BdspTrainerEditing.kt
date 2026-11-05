package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Gen 1-ordered BDSP Trainer Data edits backed by native fixed-offset fields. */
object BdspTrainerEditing {
    private const val NAME_OFFSET = 0x79BB4
    private const val NAME_SIZE = 26
    private const val TRAINER_ID_OFFSET = 0x79BD0
    private const val TRAINER_ID_SIZE = 2
    private const val MONEY_OFFSET = 0x79BD4
    private const val MONEY_SIZE = 4
    private const val BADGE_OFFSET = 0x79BDD
    private const val BADGE_SIZE = 1
    private const val PLAY_TIME_OFFSET = 0x79C04
    private const val PLAY_TIME_SIZE = 4
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        val max = BdspSaveRevision.values().maxOf { revision -> revision.fileSize }
        while (true) {
            val remaining = max + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= max) { "The document exceeds the supported BDSP save size." }
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
    ): BdspTrainerCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = BdspTrainerReadOnly.read(original)
        val name = if (requestedName == before.name) before.name else requestedName.trim()
        require(requestedTrainerId in 0..65535) { "Trainer ID must be between 0 and 65535." }
        require(requestedMoney in 0L..999_999L) { "Money must be between 0 and 999,999." }
        require(requestedBadgeCount in 0..8) { "Badges must be between 0 and 8." }
        require(requestedPlayHours in 0..65535) { "Play Time hours must be between 0 and 65535." }
        require(requestedPlayMinutes in 0..59) { "Play Time minutes must be between 0 and 59." }
        require(requestedPlaySeconds in 0..59) { "Play Time seconds must be between 0 and 59." }
        val beforePlayTime = BdspPlayTimeReadOnly.read(original)
        val nameChanged = name != before.name
        val trainerIdChanged = requestedTrainerId != before.tid16
        val moneyChanged = requestedMoney != before.money
        val badgesChanged = requestedBadgeCount != before.badgeCount
        val playTimeChanged = requestedPlayHours != beforePlayTime.hours ||
            requestedPlayMinutes != beforePlayTime.minutes || requestedPlaySeconds != beforePlayTime.seconds
        require(nameChanged || trainerIdChanged || moneyChanged || badgesChanged || playTimeChanged) {
            "Change the Trainer Name, Trainer ID, Money, Badges, or Play Time before reviewing."
        }
        val edited = original.copyOf()
        if (nameChanged) encodeName(name).copyInto(edited, NAME_OFFSET)
        if (trainerIdChanged) {
            edited[TRAINER_ID_OFFSET] = requestedTrainerId.toByte()
            edited[TRAINER_ID_OFFSET + 1] = (requestedTrainerId ushr 8).toByte()
        }
        if (moneyChanged) writeU32(edited, MONEY_OFFSET, requestedMoney)
        if (badgesChanged) edited[BADGE_OFFSET] = requestedBadgeCount.toByte()
        if (playTimeChanged) {
            edited[PLAY_TIME_OFFSET] = requestedPlayHours.toByte()
            edited[PLAY_TIME_OFFSET + 1] = (requestedPlayHours ushr 8).toByte()
            edited[PLAY_TIME_OFFSET + 2] = requestedPlayMinutes.toByte()
            edited[PLAY_TIME_OFFSET + 3] = requestedPlaySeconds.toByte()
        }
        refreshDigest(edited)
        verify(original, edited, name, requestedTrainerId, requestedMoney, requestedBadgeCount,
            requestedPlayHours, requestedPlayMinutes, requestedPlaySeconds,
            nameChanged, trainerIdChanged, moneyChanged, badgesChanged, playTimeChanged)
        return BdspTrainerCopyPlan(
            original, edited, before.name, name, before.tid16, requestedTrainerId,
            before.money, requestedMoney, before.badgeCount, requestedBadgeCount,
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
        expectedBadgeCount: Int,
        expectedPlayHours: Int,
        expectedPlayMinutes: Int,
        expectedPlaySeconds: Int,
        nameChanged: Boolean,
        trainerIdChanged: Boolean,
        moneyChanged: Boolean,
        badgesChanged: Boolean,
        playTimeChanged: Boolean
    ) {
        val before = BdspTrainerReadOnly.read(original)
        val after = BdspTrainerReadOnly.read(edited)
        val beforePlayTime = BdspPlayTimeReadOnly.read(original)
        val afterPlayTime = BdspPlayTimeReadOnly.read(edited)
        require(
            original.size == edited.size && before.version == after.version && before.revision == after.revision
        ) { "The trainer edit changed title identity, revision, or save size." }
        val expectedId32 = (before.id32 and 0xFFFF0000L) or expectedTrainerId.toLong()
        require(
            after.name == expectedName && after.tid16 == expectedTrainerId &&
                after.money == expectedMoney && after.badgeCount == expectedBadgeCount &&
                before.copy(name = expectedName, id32 = expectedId32, tid16 = expectedTrainerId,
                    money = expectedMoney, badgeCount = expectedBadgeCount) == after
        ) {
            "The Trainer Data edit did not reopen exactly."
        }
        require(
            afterPlayTime.version == beforePlayTime.version && afterPlayTime.revision == beforePlayTime.revision &&
                afterPlayTime.hours == expectedPlayHours && afterPlayTime.minutes == expectedPlayMinutes &&
                afterPlayTime.seconds == expectedPlaySeconds
        ) { "The Play Time edit did not reopen exactly." }
        require(original.indices.all { index ->
            (nameChanged && index in NAME_OFFSET until NAME_OFFSET + NAME_SIZE) ||
                (trainerIdChanged && index in TRAINER_ID_OFFSET until TRAINER_ID_OFFSET + TRAINER_ID_SIZE) ||
                (moneyChanged && index in MONEY_OFFSET until MONEY_OFFSET + MONEY_SIZE) ||
                (badgesChanged && index in BADGE_OFFSET until BADGE_OFFSET + BADGE_SIZE) ||
                (playTimeChanged && index in PLAY_TIME_OFFSET until PLAY_TIME_OFFSET + PLAY_TIME_SIZE) ||
                index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[index] == edited[index]
        }) { "The Trainer Data edit changed unrelated save data." }
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

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        for (index in 0..3) bytes[offset + index] = (value ushr (index * 8)).toByte()
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class BdspTrainerCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val beforeName: String,
    val afterName: String,
    val beforeTrainerId: Int,
    val afterTrainerId: Int,
    val beforeMoney: Long,
    val afterMoney: Long,
    val beforeBadgeCount: Int,
    val afterBadgeCount: Int,
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
    val sourceSha256: String = BdspTrainerEditing.sha256(this.original)
    val editedSha256: String = BdspTrainerEditing.sha256(this.edited)
    val nameOffset: Int = 0x79BB4
    val trainerIdOffset: Int = 0x79BD0
    val moneyOffset: Int = 0x79BD4
    val badgeOffset: Int = 0x79BDD
    val playTimeOffset: Int = 0x79C04
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
            BdspTrainerEditing.verify(
                original, edited, afterName, afterTrainerId, afterMoney, afterBadgeCount,
                afterPlayHours, afterPlayMinutes, afterPlaySeconds,
                nameChanged, trainerIdChanged, moneyChanged, badgesChanged, playTimeChanged
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspTrainerEditing.verify(
                original, persisted, afterName, afterTrainerId, afterMoney, afterBadgeCount,
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
