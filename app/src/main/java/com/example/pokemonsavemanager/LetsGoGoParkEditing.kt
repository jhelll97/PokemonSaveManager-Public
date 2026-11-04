package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Clears one occupied native GP1 slot and recalculates only block 18's CRC. */
object LetsGoGoParkEditing {
    private const val BLOCK_ID = 18
    private const val DESCRIPTORS = 0xB8614
    private const val CRC_OFFSET = DESCRIPTORS + BLOCK_ID * 8 + 6
    private val BLANK_PREFIX = byteArrayOf(
        0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x80.toByte(), 0x3F, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF0.toByte(), 0x3F,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF0.toByte(), 0x3F, 0x00, 0x00, 0x80.toByte(), 0x3F, 0x00, 0x00, 0x80.toByte(), 0x3F,
        0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x85.toByte(), 0xEC.toByte(), 0x33, 0x01
    )

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        val max = LetsGoSaveValidation.FILE_SIZE
        while (true) {
            val remaining = max + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= max) { "The document exceeds the supported Let's Go save size." }
        }
        output.toByteArray()
    }

    fun stage(source: ByteArray, expectedSha256: String, index: Int): LetsGoGoParkClearCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(index in 0 until LetsGoGoParkReadOnly.RECORD_COUNT) { "GO Park slot is out of range." }
        val beforeRead = LetsGoGoParkReadOnly.read(original)
        val before = beforeRead.entries.firstOrNull { it.index == index }
            ?: throw IllegalArgumentException("That GO Park slot is already empty.")
        val recordOffset = LetsGoGoParkReadOnly.BLOCK_OFFSET + index * LetsGoGoParkReadOnly.RECORD_SIZE
        val edited = original.copyOf()
        blankRecord().copyInto(edited, recordOffset)
        writeU16(edited, CRC_OFFSET, LetsGoSaveValidation.crc16(
            edited, LetsGoGoParkReadOnly.BLOCK_OFFSET, LetsGoGoParkReadOnly.BLOCK_LENGTH))
        verify(original, edited, index, recordOffset)
        return LetsGoGoParkClearCopyPlan(original, edited, before, recordOffset)
    }

    internal fun verify(original: ByteArray, edited: ByteArray, index: Int, recordOffset: Int) {
        val before = LetsGoGoParkReadOnly.read(original)
        val after = LetsGoGoParkReadOnly.read(edited)
        require(before.entries.any { it.index == index } && after.entries.none { it.index == index }) {
            "The selected GO Park slot did not reopen as empty."
        }
        require(after.occupiedCount == before.occupiedCount - 1) { "An unexpected GO Park occupancy change occurred." }
        require(before.entries.filterNot { it.index == index } == after.entries) {
            "An unrelated GO Park record changed."
        }
        require(edited.copyOfRange(recordOffset, recordOffset + LetsGoGoParkReadOnly.RECORD_SIZE)
            .contentEquals(blankRecord())) { "The cleared slot is not the canonical native blank GP1 record." }
        require(original.indices.all { byteIndex ->
            byteIndex in recordOffset until recordOffset + LetsGoGoParkReadOnly.RECORD_SIZE ||
                byteIndex in CRC_OFFSET until CRC_OFFSET + 2 || original[byteIndex] == edited[byteIndex]
        }) { "An unrelated Let's Go save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun blankRecord(): ByteArray = ByteArray(LetsGoGoParkReadOnly.RECORD_SIZE).also {
        BLANK_PREFIX.copyInto(it)
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }
}

class LetsGoGoParkClearCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: LetsGoGoParkEntry,
    val recordOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = LetsGoGoParkEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen GO Park." }
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
            LetsGoGoParkEditing.verify(original, edited, before.index, recordOffset)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LetsGoGoParkEditing.verify(original, persisted, before.index, recordOffset)
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
