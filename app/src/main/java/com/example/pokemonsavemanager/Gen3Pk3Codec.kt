package com.example.pokemonsavemanager

/**
 * Safe Gen 3 PK3 record boundary for adapters and persistence.
 *
 * This codec preserves the first 32 header bytes, derives the checksum from
 * canonical plaintext data, and encrypts the 48-byte data section on encode.
 */
object Gen3Pk3Codec {
    const val RECORD_LENGTH = 80
    const val HEADER_LENGTH = 32

    fun decode(record: ByteArray): Gen3Pk3DecodedRecord {
        require(record.size == RECORD_LENGTH) {
            "Gen 3 PK3 records must be exactly 80 bytes."
        }

        val personality = readIntLittleEndian(record, 0)
        val originalTrainerId = readIntLittleEndian(record, 4)
        val trainerId = (originalTrainerId ushr 16) and 0xFFFF
        val secretId = originalTrainerId and 0xFFFF
        val storedChecksum = readUnsignedShortLittleEndian(record, 28)
        val encryptedSection = record.copyOfRange(HEADER_LENGTH, RECORD_LENGTH)
        val canonicalSection = Gen3Encryption.decrypt(
            encryptedSection = encryptedSection,
            personality = personality,
            trainerId = trainerId,
            secretId = secretId
        )
        require(Gen3Integrity.isValid(canonicalSection, storedChecksum)) {
            "Gen 3 PK3 checksum verification failed."
        }

        return Gen3Pk3DecodedRecord(
            header = record.copyOfRange(0, HEADER_LENGTH),
            personality = personality,
            trainerId = trainerId,
            secretId = secretId,
            storedChecksum = storedChecksum,
            canonicalSection = canonicalSection,
            encryptedSection = encryptedSection
        )
    }

    fun encode(
        header: ByteArray,
        personality: Int,
        trainerId: Int,
        secretId: Int,
        canonicalSection: ByteArray
    ): ByteArray {
        require(header.size == HEADER_LENGTH) {
            "Gen 3 PK3 headers must be exactly 32 bytes."
        }
        require(trainerId in 0..65535) {
            "trainerId must be a 16-bit unsigned value."
        }
        require(secretId in 0..65535) {
            "secretId must be a 16-bit unsigned value."
        }
        require(canonicalSection.size == Gen3Integrity.DATA_SECTION_LENGTH) {
            "Gen 3 canonical data must be exactly 48 bytes."
        }

        val result = ByteArray(RECORD_LENGTH)
        header.copyInto(result, endIndex = HEADER_LENGTH)
        writeIntLittleEndian(result, 0, personality)
        writeIntLittleEndian(result, 4, (trainerId shl 16) or secretId)

        val checksum = Gen3Integrity.checksum(canonicalSection)
        writeUnsignedShortLittleEndian(result, 28, checksum)
        val encryptedSection = Gen3Encryption.encrypt(
            canonicalSection = canonicalSection,
            personality = personality,
            trainerId = trainerId,
            secretId = secretId
        )
        encryptedSection.copyInto(result, destinationOffset = HEADER_LENGTH)
        return result
    }

    private fun readIntLittleEndian(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF) or
            ((source[offset + 1].toInt() and 0xFF) shl 8) or
            ((source[offset + 2].toInt() and 0xFF) shl 16) or
            ((source[offset + 3].toInt() and 0xFF) shl 24)

    private fun readUnsignedShortLittleEndian(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF) or
            ((source[offset + 1].toInt() and 0xFF) shl 8)

    private fun writeIntLittleEndian(destination: ByteArray, offset: Int, value: Int) {
        destination[offset] = value.toByte()
        destination[offset + 1] = (value ushr 8).toByte()
        destination[offset + 2] = (value ushr 16).toByte()
        destination[offset + 3] = (value ushr 24).toByte()
    }

    private fun writeUnsignedShortLittleEndian(
        destination: ByteArray,
        offset: Int,
        value: Int
    ) {
        destination[offset] = value.toByte()
        destination[offset + 1] = (value ushr 8).toByte()
    }
}

data class Gen3Pk3DecodedRecord(
    val header: ByteArray,
    val personality: Int,
    val trainerId: Int,
    val secretId: Int,
    val storedChecksum: Int,
    val canonicalSection: ByteArray,
    val encryptedSection: ByteArray
)
