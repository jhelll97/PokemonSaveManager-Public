package com.example.pokemonsavemanager

import java.util.Collections

/**
 * Native Let's Go CaptureRecords (PKHeX CaptureRecords.cs, pinned 0a7854f64e118ad228513f3cf4b84c308dac2fdc).
 * Read-only projection: raw bytes are retained and all values remain unsigned.
 */
data class LetsGoCaptureRecordEntry(
    val species: Int,
    val captured: Long,
    val transferred: Long
)

class LetsGoCaptureRecordsRead internal constructor(
    val rawBlockBytes: List<Int>,
    val entries: List<LetsGoCaptureRecordEntry>,
    val totalCaptured: Long,
    val totalTransferred: Long
) {
    val entryCount: Int get() = entries.size
}

/** Native block 12: 0x46600, length 0x10A4; no inferred legality/current-party state. */
object LetsGoCaptureRecordsReadOnly {
    internal const val BLOCK_OFFSET = 0x46600
    internal const val BLOCK_LENGTH = 0x10A4
    private const val ENTRY_COUNT = 153
    internal const val CAPTURED_OFFSET = 0x2A8
    internal const val TRANSFERRED_OFFSET = 0x50C
    internal const val TOTAL_CAPTURED_OFFSET = 0x794
    internal const val TOTAL_TRANSFERRED_OFFSET = 0x7A8

    fun read(source: ByteArray): LetsGoCaptureRecordsRead {
        require(LetsGoSaveValidation.inspect(source) is LetsGoValidation.Verified) {
            "A fully validated native Let's Go save is required."
        }
        require(source.size >= BLOCK_OFFSET + BLOCK_LENGTH) { "Native CaptureRecords block is truncated." }
        val block = source.copyOfRange(BLOCK_OFFSET, BLOCK_OFFSET + BLOCK_LENGTH)
        fun u32(offset: Int): Long =
            (block[offset].toLong() and 0xFF) or
                ((block[offset + 1].toLong() and 0xFF) shl 8) or
                ((block[offset + 2].toLong() and 0xFF) shl 16) or
                ((block[offset + 3].toLong() and 0xFF) shl 24)
        fun species(index: Int): Int = if (index < 150) index + 1 else index + 657
        val entries = (0 until ENTRY_COUNT).map { index ->
            LetsGoCaptureRecordEntry(species(index), u32(CAPTURED_OFFSET + index * 4), u32(TRANSFERRED_OFFSET + index * 4))
        }
        return LetsGoCaptureRecordsRead(
            Collections.unmodifiableList(block.map { it.toInt() and 0xFF }),
            Collections.unmodifiableList(entries),
            u32(TOTAL_CAPTURED_OFFSET),
            u32(TOTAL_TRANSFERRED_OFFSET)
        )
    }
}
