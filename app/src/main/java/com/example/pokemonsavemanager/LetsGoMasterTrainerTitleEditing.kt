package com.example.pokemonsavemanager

import java.security.MessageDigest

data class LetsGoMasterTrainerTitleEditRead(
    val version: LetsGoVersion,
    val titleId: Int,
    val titleLabel: String,
    val unlocked: Boolean
)

/** Sets one selected native Master Trainer title flag and only block 3's CRC. */
object LetsGoMasterTrainerTitleEditing {
    private const val CRC_OFFSET = LetsGoSaveValidation.DESCRIPTORS + 3 * 8 + 6

    fun read(source: ByteArray, titleId: Int): LetsGoMasterTrainerTitleEditRead {
        require(titleId in 0 until LetsGoMasterTrainerTitlesReadOnly.TITLE_COUNT) {
            "Choose a native Master Trainer title from 0 to 155."
        }
        val titles = LetsGoMasterTrainerTitlesReadOnly.read(source)
        return LetsGoMasterTrainerTitleEditRead(
            titles.version, titleId, LetsGoMasterTrainerTitlesReadOnly.titleLabel(titleId), titles.isUnlocked(titleId)
        )
    }

    fun stage(source: ByteArray, expectedSha256: String, titleId: Int): LetsGoMasterTrainerTitleCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = read(original, titleId)
        require(!before.unlocked) { "${before.titleLabel} is already unlocked." }
        val edited = original.copyOf()
        val flagOffset = LetsGoMasterTrainerTitlesReadOnly.TITLE_FLAGS_OFFSET + (titleId ushr 3)
        edited[flagOffset] = (edited[flagOffset].toInt() or (1 shl (titleId and 7))).toByte()
        writeU16(edited, CRC_OFFSET, LetsGoSaveValidation.crc16(
            edited,
            LetsGoMasterTrainerTitlesReadOnly.EVENT_WORK_OFFSET,
            LetsGoMasterTrainerTitlesReadOnly.EVENT_WORK_LENGTH
        ))
        verify(original, edited, titleId)
        return LetsGoMasterTrainerTitleCopyPlan(original, edited, titleId, before, read(edited, titleId))
    }

    internal fun verify(original: ByteArray, edited: ByteArray, titleId: Int) {
        val before = LetsGoMasterTrainerTitlesReadOnly.read(original)
        val after = LetsGoMasterTrainerTitlesReadOnly.read(edited)
        require(!before.isUnlocked(titleId) && after.isUnlocked(titleId)) {
            "The selected title did not reopen as unlocked."
        }
        require((0 until before.totalTitles).all { id -> id == titleId || before.isUnlocked(id) == after.isUnlocked(id) }) {
            "A different Master Trainer title flag changed."
        }
        val flagOffset = LetsGoMasterTrainerTitlesReadOnly.TITLE_FLAGS_OFFSET + (titleId ushr 3)
        val expectedFlagByte = original[flagOffset].toInt() or (1 shl (titleId and 7))
        require((edited[flagOffset].toInt() and 255) == (expectedFlagByte and 255)) {
            "The selected native title flag byte changed outside its reviewed bit."
        }
        require(original.indices.all { index ->
            index == flagOffset || index in CRC_OFFSET until CRC_OFFSET + 2 || original[index] == edited[index]
        }) { "An unrelated Let's Go save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
}

class LetsGoMasterTrainerTitleCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val titleId: Int,
    val before: LetsGoMasterTrainerTitleEditRead,
    val after: LetsGoMasterTrainerTitleEditRead
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = LetsGoMasterTrainerTitleEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Master Trainer Titles." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        require(retainBackup(original.copyOf()).contentEquals(original)) {
            "The recovery backup did not verify. Nothing was written."
        }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LetsGoMasterTrainerTitleEditing.verify(original, edited, titleId)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LetsGoMasterTrainerTitleEditing.verify(original, persisted, titleId)
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
