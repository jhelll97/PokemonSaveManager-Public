package com.example.pokemonsavemanager

/** Changes only the native all-species caught total and block 12's CRC in a reviewed new copy. */
object LetsGoCaptureTotalEditing {
    private const val BLOCK_ID = 12
    private const val DESCRIPTORS = 0xB8614
    private const val CRC_OFFSET = DESCRIPTORS + BLOCK_ID * 8 + 6
    const val MAX_TOTAL = 999_999_999L

    fun stage(source: ByteArray, expectedSha256: String, totalCaptured: Long): LetsGoCaptureTotalCopyPlan {
        val original = source.copyOf()
        require(LetsGoCaptureRecordEditing.sha256(original) == expectedSha256) {
            "The source changed. Reopen it from PSE."
        }
        require(totalCaptured in 0L..MAX_TOTAL) { "Total caught must be 0–999,999,999." }
        val before = LetsGoCaptureRecordsReadOnly.read(original)
        require(totalCaptured != before.totalCaptured) { "Choose a different total caught value." }

        val edited = original.copyOf()
        val valueOffset = LetsGoCaptureRecordsReadOnly.BLOCK_OFFSET +
            LetsGoCaptureRecordsReadOnly.TOTAL_CAPTURED_OFFSET
        writeU32(edited, valueOffset, totalCaptured)
        writeU16(edited, CRC_OFFSET, LetsGoSaveValidation.crc16(
            edited, LetsGoCaptureRecordsReadOnly.BLOCK_OFFSET, LetsGoCaptureRecordsReadOnly.BLOCK_LENGTH))
        verify(original, edited, totalCaptured, valueOffset)
        return LetsGoCaptureTotalCopyPlan(original, edited, before.totalCaptured, totalCaptured, valueOffset)
    }

    internal fun verify(original: ByteArray, edited: ByteArray, totalCaptured: Long, valueOffset: Int) {
        val before = LetsGoCaptureRecordsReadOnly.read(original)
        val after = LetsGoCaptureRecordsReadOnly.read(edited)
        require(after.totalCaptured == totalCaptured && before.totalTransferred == after.totalTransferred) {
            "The staged total caught value did not reopen exactly."
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
            LetsGoCaptureTotalEditing.verify(original, edited, after, valueOffset)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LetsGoCaptureTotalEditing.verify(original, persisted, after, valueOffset)
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
