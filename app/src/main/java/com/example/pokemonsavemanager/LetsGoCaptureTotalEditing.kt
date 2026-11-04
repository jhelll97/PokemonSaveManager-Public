package com.example.pokemonsavemanager

enum class LetsGoCaptureTotalField(val displayName: String, val offset: Int) {
    CAPTURED("Total caught", LetsGoCaptureRecordsReadOnly.TOTAL_CAPTURED_OFFSET),
    TRANSFERRED("Total transferred to Professor Oak", LetsGoCaptureRecordsReadOnly.TOTAL_TRANSFERRED_OFFSET)
}

/** Changes one native all-species total and block 12's CRC in a reviewed new copy. */
object LetsGoCaptureTotalEditing {
    private const val BLOCK_ID = 12
    private const val DESCRIPTORS = 0xB8614
    private const val CRC_OFFSET = DESCRIPTORS + BLOCK_ID * 8 + 6
    const val MAX_TOTAL = 999_999_999L

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        field: LetsGoCaptureTotalField,
        value: Long
    ): LetsGoCaptureTotalCopyPlan {
        val original = source.copyOf()
        require(LetsGoCaptureRecordEditing.sha256(original) == expectedSha256) {
            "The source changed. Reopen it from PSE."
        }
        require(value in 0L..MAX_TOTAL) { "${field.displayName} must be 0–999,999,999." }
        val before = LetsGoCaptureRecordsReadOnly.read(original)
        val beforeValue = if (field == LetsGoCaptureTotalField.CAPTURED)
            before.totalCaptured else before.totalTransferred
        require(value != beforeValue) { "Choose a different ${field.displayName.lowercase()} value." }

        val edited = original.copyOf()
        val valueOffset = LetsGoCaptureRecordsReadOnly.BLOCK_OFFSET + field.offset
        writeU32(edited, valueOffset, value)
        writeU16(edited, CRC_OFFSET, LetsGoSaveValidation.crc16(
            edited, LetsGoCaptureRecordsReadOnly.BLOCK_OFFSET, LetsGoCaptureRecordsReadOnly.BLOCK_LENGTH))
        verify(original, edited, field, value, valueOffset)
        return LetsGoCaptureTotalCopyPlan(original, edited, field, beforeValue, value, valueOffset)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        field: LetsGoCaptureTotalField,
        value: Long,
        valueOffset: Int
    ) {
        val before = LetsGoCaptureRecordsReadOnly.read(original)
        val after = LetsGoCaptureRecordsReadOnly.read(edited)
        val selectedAfter = if (field == LetsGoCaptureTotalField.CAPTURED)
            after.totalCaptured else after.totalTransferred
        val otherUnchanged = if (field == LetsGoCaptureTotalField.CAPTURED)
            before.totalTransferred == after.totalTransferred else before.totalCaptured == after.totalCaptured
        require(selectedAfter == value && otherUnchanged) {
            "The staged ${field.displayName.lowercase()} value did not reopen exactly."
        }
        require(before.entries == after.entries) { "A per-species capture record changed." }
        require(original.indices.all { index ->
            index in valueOffset until valueOffset + 4 ||
                index in CRC_OFFSET until CRC_OFFSET + 2 || original[index] == edited[index]
        }) { "An unrelated Let's Go save byte changed." }
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }
}

class LetsGoCaptureTotalCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val field: LetsGoCaptureTotalField,
    val before: Long,
    val after: Long,
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
            LetsGoCaptureTotalEditing.verify(original, edited, field, after, valueOffset)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LetsGoCaptureTotalEditing.verify(original, persisted, field, after, valueOffset)
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
