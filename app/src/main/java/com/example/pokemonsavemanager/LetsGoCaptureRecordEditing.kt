package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class LetsGoCaptureRecordField(val displayName: String, val maximum: Long) {
    CAPTURED("Caught", 9_999L), TRANSFERRED("Transferred to Professor Oak", 999_999_999L)
}

/** Changes one native per-species capture-record counter and block 12's CRC in a reviewed new copy. */
object LetsGoCaptureRecordEditing {
    private const val BLOCK_ID = 12
    private const val DESCRIPTORS = 0xB8614
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

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        speciesId: Int,
        field: LetsGoCaptureRecordField,
        value: Long
    ): LetsGoCaptureRecordCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(value in 0L..field.maximum) { "${field.displayName} count must be 0–${field.maximum}." }
        val index = speciesIndex(speciesId)
        val beforeRecords = LetsGoCaptureRecordsReadOnly.read(original)
        val before = beforeRecords.entries[index]
        val beforeValue = if (field == LetsGoCaptureRecordField.CAPTURED) before.captured else before.transferred
        require(value != beforeValue) { "Choose a different ${field.displayName.lowercase()} count." }

        val edited = original.copyOf()
        val fieldOffset = if (field == LetsGoCaptureRecordField.CAPTURED)
            LetsGoCaptureRecordsReadOnly.CAPTURED_OFFSET else LetsGoCaptureRecordsReadOnly.TRANSFERRED_OFFSET
        val valueOffset = LetsGoCaptureRecordsReadOnly.BLOCK_OFFSET + fieldOffset + index * 4
        writeU32(edited, valueOffset, value)
        writeU16(edited, CRC_OFFSET, LetsGoSaveValidation.crc16(
            edited, LetsGoCaptureRecordsReadOnly.BLOCK_OFFSET, LetsGoCaptureRecordsReadOnly.BLOCK_LENGTH))
        verify(original, edited, speciesId, field, value, valueOffset)
        val after = LetsGoCaptureRecordsReadOnly.read(edited).entries[index]
        return LetsGoCaptureRecordCopyPlan(original, edited, speciesId, field, before, after, valueOffset)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        speciesId: Int,
        field: LetsGoCaptureRecordField,
        value: Long,
        valueOffset: Int
    ) {
        val before = LetsGoCaptureRecordsReadOnly.read(original)
        val after = LetsGoCaptureRecordsReadOnly.read(edited)
        val index = speciesIndex(speciesId)
        val afterValue = if (field == LetsGoCaptureRecordField.CAPTURED)
            after.entries[index].captured else after.entries[index].transferred
        require(before.entries[index].species == speciesId && afterValue == value) {
            "The staged ${field.displayName.lowercase()} count did not reopen exactly."
        }
        require(before.entries.indices.all { entryIndex ->
            val left = before.entries[entryIndex]
            val right = after.entries[entryIndex]
            left.species == right.species && when (field) {
                LetsGoCaptureRecordField.CAPTURED ->
                    left.transferred == right.transferred && (entryIndex == index || left.captured == right.captured)
                LetsGoCaptureRecordField.TRANSFERRED ->
                    left.captured == right.captured && (entryIndex == index || left.transferred == right.transferred)
            }
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
    val field: LetsGoCaptureRecordField,
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
            val value = if (field == LetsGoCaptureRecordField.CAPTURED) after.captured else after.transferred
            LetsGoCaptureRecordEditing.verify(original, edited, speciesId, field, value, valueOffset)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LetsGoCaptureRecordEditing.verify(original, persisted, speciesId, field, value, valueOffset)
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
