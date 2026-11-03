package com.example.pokemonsavemanager

/** Gen 3 PK3 data-section checksum support for save adapters. */
object Gen3Integrity {
    const val DATA_SECTION_LENGTH = 48
    const val CHECKSUM_MASK = 0xFFFF

    fun checksum(dataSection: ByteArray): Int {
        require(dataSection.size == DATA_SECTION_LENGTH) {
            "Gen 3 data section must be exactly 48 bytes."
        }
        var sum = 0
        var index = 0
        while (index < dataSection.size) {
            val low = dataSection[index].toInt() and 0xFF
            val high = dataSection[index + 1].toInt() and 0xFF
            sum = (sum + (low or (high shl 8))) and CHECKSUM_MASK
            index += 2
        }
        return sum
    }

    fun isValid(dataSection: ByteArray, storedChecksum: Int): Boolean =
        checksum(dataSection) == (storedChecksum and CHECKSUM_MASK)

    fun requireValid(dataSection: ByteArray, storedChecksum: Int): ByteArray {
        require(isValid(dataSection, storedChecksum)) {
            "Gen 3 checksum verification failed."
        }
        return dataSection.copyOf()
    }
}