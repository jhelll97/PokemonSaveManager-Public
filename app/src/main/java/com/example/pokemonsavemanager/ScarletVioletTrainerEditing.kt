package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Gen 1-ordered Scarlet/Violet Trainer Data edits backed by the native status object. */
object ScarletVioletTrainerEditing {
    private const val MAX_SAVE_SIZE = 0x31CF81
    private const val NAME_SIZE = 26
    private const val TRAINER_ID_SIZE = 2
    private const val MONEY_SIZE = 4

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
                "The document exceeds the supported Scarlet/Violet save size."
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
        requestedBadgeCount: Int
    ): ScarletVioletTrainerCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = ScarletVioletTrainerReadOnly.read(original)
        val name = if (requestedName == before.name) before.name else requestedName.trim()
        require(requestedTrainerId in 0..65535) { "Trainer ID must be between 0 and 65535." }
        require(before.money != null || requestedMoney == null) {
            "This save does not contain the native money counter."
        }
        require(requestedBadgeCount in 0..8) { "Badges must be between 0 and 8." }
        val nameChanged = name != before.name
        val trainerIdChanged = requestedTrainerId != before.tid16
        val moneyChanged = requestedMoney != null && requestedMoney != before.money
        val requestedBadgeMask = targetBadgeMask(before.badgeMask, requestedBadgeCount)
        val badgesChanged = requestedBadgeMask != before.badgeMask
        if (moneyChanged) require(requestedMoney != null && requestedMoney in 0L..9_999_999L) {
            "Money must be between 0 and 9,999,999."
        }
        require(nameChanged || trainerIdChanged || moneyChanged || badgesChanged) {
            "Change the Trainer Name, Trainer ID, Money, or Badges before reviewing."
        }
        val afterMoney = if (moneyChanged) requestedMoney else before.money
        val write = ScarletVioletSaveValidation.writeTrainerFields(
            original,
            if (nameChanged) encodeName(name) else null,
            if (trainerIdChanged) requestedTrainerId else null,
            if (moneyChanged) requestedMoney else null,
            if (badgesChanged) requestedBadgeMask else null
        )
        verify(
            original, write.bytes, name, requestedTrainerId, afterMoney, requestedBadgeMask,
            write.nameOffset, write.trainerIdOffset, write.moneyOffset, write.badgeOffsets,
            nameChanged, trainerIdChanged, moneyChanged, badgesChanged
        )
        return ScarletVioletTrainerCopyPlan(
            original, write.bytes, before.name, name, before.tid16, requestedTrainerId,
            before.money, afterMoney, before.badgeMask, requestedBadgeMask,
            write.nameOffset, write.trainerIdOffset, write.moneyOffset, write.badgeOffsets,
            nameChanged, trainerIdChanged, moneyChanged, badgesChanged
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expectedName: String,
        expectedTrainerId: Int,
        expectedMoney: Long?,
        expectedBadgeMask: Int,
        nameOffset: Int,
        trainerIdOffset: Int,
        moneyOffset: Int,
        badgeOffsets: List<Int>,
        nameChanged: Boolean,
        trainerIdChanged: Boolean,
        moneyChanged: Boolean,
        badgesChanged: Boolean
    ) {
        val beforeValidation = ScarletVioletSaveValidation.inspect(original)
        val afterValidation = ScarletVioletSaveValidation.inspect(edited)
        require(beforeValidation is ScarletVioletValidation.Verified &&
            afterValidation is ScarletVioletValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The trainer edit changed title identity or save size."
        }
        val before = ScarletVioletTrainerReadOnly.read(original)
        val after = ScarletVioletTrainerReadOnly.read(edited)
        val expectedId32 = (before.id32 and 0xFFFF0000L) or expectedTrainerId.toLong()
        require(
            after.name == expectedName && after.tid16 == expectedTrainerId &&
                after.money == expectedMoney && after.badgeMask == expectedBadgeMask &&
                before.copy(name = expectedName, id32 = expectedId32, tid16 = expectedTrainerId,
                    money = expectedMoney, badgeMask = expectedBadgeMask) == after
        ) {
            "The Trainer Data edit did not reopen exactly."
        }
        val hashOffset = original.size - 32
        val badgeOffsetSet = badgeOffsets.toSet()
        val expectedBadgeChanges = Integer.bitCount(before.badgeMask xor expectedBadgeMask)
        require(
            badgeOffsets.size == badgeOffsetSet.size && badgeOffsets.size == expectedBadgeChanges &&
                badgeOffsets.all { it in 0 until hashOffset && original[it] != edited[it] } &&
                badgesChanged == (expectedBadgeChanges != 0)
        ) {
            "The Gym Badge review does not match the exact native flag changes."
        }
        require(original.indices.all { index ->
            (nameChanged && index in nameOffset until nameOffset + NAME_SIZE) ||
                (trainerIdChanged && index in trainerIdOffset until trainerIdOffset + TRAINER_ID_SIZE) ||
                (moneyChanged && index in moneyOffset until moneyOffset + MONEY_SIZE) ||
                (badgesChanged && index in badgeOffsetSet) ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "The Trainer Data edit changed unrelated save data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun targetBadgeMask(currentMask: Int, requestedCount: Int): Int {
        var mask = currentMask and 0xFF
        var count = Integer.bitCount(mask)
        if (count < requestedCount) {
            for (index in 0..7) {
                if (count == requestedCount) break
                if (mask and (1 shl index) == 0) {
                    mask = mask or (1 shl index)
                    count++
                }
            }
        } else if (count > requestedCount) {
            for (index in 7 downTo 0) {
                if (count == requestedCount) break
                if (mask and (1 shl index) != 0) {
                    mask = mask and (1 shl index).inv()
                    count--
                }
            }
        }
        return mask
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
class ScarletVioletTrainerCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val beforeName: String,
    val afterName: String,
    val beforeTrainerId: Int,
    val afterTrainerId: Int,
    val beforeMoney: Long?,
    val afterMoney: Long?,
    val beforeBadgeMask: Int,
    val afterBadgeMask: Int,
    val nameOffset: Int,
    val trainerIdOffset: Int,
    val moneyOffset: Int,
    val badgeOffsets: List<Int>,
    val nameChanged: Boolean,
    val trainerIdChanged: Boolean,
    val moneyChanged: Boolean,
    val badgesChanged: Boolean
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256: String = ScarletVioletTrainerEditing.sha256(this.original)
    val editedSha256: String = ScarletVioletTrainerEditing.sha256(this.edited)
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
            ScarletVioletTrainerEditing.verify(
                original, edited, afterName, afterTrainerId, afterMoney, afterBadgeMask,
                nameOffset, trainerIdOffset, moneyOffset, badgeOffsets,
                nameChanged, trainerIdChanged, moneyChanged, badgesChanged
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            ScarletVioletTrainerEditing.verify(
                original, persisted, afterName, afterTrainerId, afterMoney, afterBadgeMask,
                nameOffset, trainerIdOffset, moneyOffset, badgeOffsets,
                nameChanged, trainerIdChanged, moneyChanged, badgesChanged
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
