package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Changes one native per-species caught counter and block 12's CRC in a reviewed new copy. */
object LetsGoCaptureRecordEditing {
    private const val BLOCK_ID = 12
    private const val DESCRIPTORS = 0xB8614
    private const val MAX_CAPTURED = 9_999L
    private const val CRC_OFFSET = DESCRIPTORS + BLOCK_ID * 8 + 6

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

    fun stage(source: ByteArray, expectedSha256: String, speciesId: Int, captured: Long): LetsGoCaptureRecordCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(captured in 0L..MAX_CAPTURED) { "Caught count must be 0–9,999." }
        val index = speciesIndex(speciesId)
        val beforeRecords = LetsGoCaptureRecordsReadOnly.read(original)
        val before = beforeRecords.entries[index]
        require(captured != before.captured) { "Choose a different caught count." }

        val edited = original.copyOf()
        val valueOffset = LetsGoCaptureRecordsReadOnly.BLOCK_OFFSET +
            LetsGoCaptureRecordsReadOnly.CAPTURED_OFFSET + index * 4
        writeU32(edited, valueOffset, captured)
        writeU16(edited, CRC_OFFSET, LetsGoSaveValidation.crc16(
            edited, LetsGoCaptureRecordsReadOnly.BLOCK_OFFSET, LetsGoCaptureRecordsReadOnly.BLOCK_LENGTH))
        verify(original, edited, speciesId, captured, valueOffset)
        val after = LetsGoCaptureRecordsReadOnly.read(edited).entries[index]
        return LetsGoCaptureRecordCopyPlan(original, edited, speciesId, before, after, valueOffset)
    }

    internal fun verify(original: ByteArray, edited: ByteArray, speciesId: Int, captured: Long, valueOffset: Int) {
        val before = LetsGoCaptureRecordsReadOnly.read(original)
        val after = LetsGoCaptureRecordsReadOnly.read(edited)
        val index = speciesIndex(speciesId)
        require(before.entries[index].species == speciesId && after.entries[index].captured == captured) {
            "The staged caught count did not reopen exactly."
        }
        require(before.entries.indices.all { entryIndex ->
            val left = before.entries[entryIndex]
            val right = after.entries[entryIndex]
            left.species == right.species && left.transferred == right.transferred &&
                (entryIndex == index || left.captured == right.captured)
        }) { "An unrelated species capture record changed." }
        require(before.totalCaptured == after.totalCaptured && before.totalTransferred == after.totalTransferred) {
            "A native total counter changed."
        }
        require(original.indices.all { byteIndex ->
            byteIndex in valueOffset until valueOffset + 4 ||
                byteIndex in CRC_OFFSET until CRC_OFFSET + 2 || original[byteIndex] == edited[byteIndex]
        }) { "An unrelated Let's Go save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun speciesIndex(speciesId: Int): Int = when (speciesId) {
        in 1..151 -> speciesId - 1
        808, 809 -> speciesId - 657
        else -> throw IllegalArgumentException("That species has no native Let's Go capture record.")
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }
}

class LetsGoCaptureRecordCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val speciesId: Int,
    val before: LetsGoCaptureRecordEntry,
    val after: LetsGoCaptureRecordEntry,
    val valueOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = LetsGoCaptureRecordEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Capture Records." }
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
            LetsGoCaptureRecordEditing.verify(original, edited, speciesId, after.captured, valueOffset)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LetsGoCaptureRecordEditing.verify(original, persisted, speciesId, after.captured, valueOffset)
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
