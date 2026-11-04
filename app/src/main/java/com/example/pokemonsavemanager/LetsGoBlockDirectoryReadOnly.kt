package com.example.pokemonsavemanager

data class LetsGoBlockDirectoryEntry(
    val id: Int,
    val offset: Int,
    val length: Int,
    val descriptorLength: Long,
    val descriptorId: Int,
    val storedCrc: Int,
    val calculatedCrc: Int
) {
    val endOffset: Int get() = offset + length - 1
    val descriptorMatches: Boolean get() = descriptorLength == length.toLong() && descriptorId == id
    val crcMatches: Boolean get() = storedCrc == calculatedCrc
}

data class LetsGoBlockDirectoryRead(
    val version: LetsGoVersion,
    val entries: List<LetsGoBlockDirectoryEntry>
) {
    val allDescriptorsMatch: Boolean get() = entries.all { it.descriptorMatches }
    val allCrcsMatch: Boolean get() = entries.all { it.crcMatches }
}

data class LetsGoNativeBlockExport(
    val version: LetsGoVersion,
    val entry: LetsGoBlockDirectoryEntry,
    val bytes: ByteArray
)

/** Exposes the native SAV_BEEF block directory only after the complete save passes validation. */
object LetsGoBlockDirectoryReadOnly {
    fun read(bytes: ByteArray): LetsGoBlockDirectoryRead {
        val verified = LetsGoSaveValidation.inspect(bytes) as? LetsGoValidation.Verified
            ?: throw IllegalArgumentException("A checksum-valid native Let's Go save is required.")
        val entries = LetsGoSaveValidation.blocks.mapIndexed { id, block ->
            val descriptor = LetsGoSaveValidation.DESCRIPTORS + id * 8
            LetsGoBlockDirectoryEntry(
                id = id,
                offset = block.offset,
                length = block.length,
                descriptorLength = u32(bytes, descriptor),
                descriptorId = u16(bytes, descriptor + 4),
                storedCrc = u16(bytes, descriptor + 6),
                calculatedCrc = LetsGoSaveValidation.crc16(bytes, block.offset, block.length)
            )
        }
        require(entries.all { it.descriptorMatches && it.crcMatches }) {
            "The native Let's Go block directory changed after validation."
        }
        return LetsGoBlockDirectoryRead(verified.version, entries)
    }

    /** Returns one exact native block after validating the complete directory and every block CRC. */
    fun export(bytes: ByteArray, blockId: Int): LetsGoNativeBlockExport {
        val read = read(bytes)
        val entry = read.entries.firstOrNull { it.id == blockId }
            ?: throw IllegalArgumentException("The selected native block ID is unavailable.")
        return LetsGoNativeBlockExport(
            version = read.version,
            entry = entry,
            bytes = bytes.copyOfRange(entry.offset, entry.offset + entry.length)
        )
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
}
