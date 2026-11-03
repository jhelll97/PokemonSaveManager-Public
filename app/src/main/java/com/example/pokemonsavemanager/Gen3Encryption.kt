package com.example.pokemonsavemanager

/**
 * Gen 3 PK3 data-section encryption and four-block permutation.
 *
 * The canonical section order is Growth, Attacks, EVs and Misc. Callers must
 * checksum the canonical plaintext with Gen3Integrity before persistence.
 */
object Gen3Encryption {
    const val DATA_SECTION_LENGTH = 48
    private const val BLOCK_LENGTH = 12

    fun decrypt(
        encryptedSection: ByteArray,
        personality: Int,
        trainerId: Int,
        secretId: Int
    ): ByteArray {
        require(encryptedSection.size == DATA_SECTION_LENGTH) {
            "Gen 3 encrypted data must be exactly 48 bytes."
        }
        val key = encryptionKey(personality, trainerId, secretId)
        return xorWords(unshuffle(encryptedSection, blockOrder(personality)), key)
    }

    fun encrypt(
        canonicalSection: ByteArray,
        personality: Int,
        trainerId: Int,
        secretId: Int
    ): ByteArray {
        require(canonicalSection.size == DATA_SECTION_LENGTH) {
            "Gen 3 canonical data must be exactly 48 bytes."
        }
        val key = encryptionKey(personality, trainerId, secretId)
        return shuffle(xorWords(canonicalSection, key), blockOrder(personality))
    }

    private fun encryptionKey(personality: Int, trainerId: Int, secretId: Int): Int =
        personality xor (trainerId shl 16) xor (secretId and 0xFFFF)

    // GBA PK3 uses the complete unsigned 32-bit PID modulo 24, not a PID bit slice.
    private fun blockOrder(personality: Int): IntArray =
        BLOCK_ORDERS[((personality.toLong() and 0xFFFFFFFFL) % BLOCK_ORDERS.size).toInt()]

    private fun xorWords(source: ByteArray, key: Int): ByteArray {
        val result = source.copyOf()
        var offset = 0
        while (offset < result.size) {
            val value = readIntLittleEndian(result, offset) xor key
            writeIntLittleEndian(result, offset, value)
            offset += Int.SIZE_BYTES
        }
        return result
    }

    private fun shuffle(source: ByteArray, order: IntArray): ByteArray {
        val result = ByteArray(source.size)
        order.forEachIndexed { storedIndex, canonicalIndex ->
            source.copyInto(
                result,
                destinationOffset = storedIndex * BLOCK_LENGTH,
                startIndex = canonicalIndex * BLOCK_LENGTH,
                endIndex = (canonicalIndex + 1) * BLOCK_LENGTH
            )
        }
        return result
    }

    private fun unshuffle(source: ByteArray, order: IntArray): ByteArray {
        val result = ByteArray(source.size)
        order.forEachIndexed { storedIndex, canonicalIndex ->
            source.copyInto(
                result,
                destinationOffset = canonicalIndex * BLOCK_LENGTH,
                startIndex = storedIndex * BLOCK_LENGTH,
                endIndex = (storedIndex + 1) * BLOCK_LENGTH
            )
        }
        return result
    }

    private fun readIntLittleEndian(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF) or
            ((source[offset + 1].toInt() and 0xFF) shl 8) or
            ((source[offset + 2].toInt() and 0xFF) shl 16) or
            ((source[offset + 3].toInt() and 0xFF) shl 24)

    private fun writeIntLittleEndian(destination: ByteArray, offset: Int, value: Int) {
        destination[offset] = value.toByte()
        destination[offset + 1] = (value ushr 8).toByte()
        destination[offset + 2] = (value ushr 16).toByte()
        destination[offset + 3] = (value ushr 24).toByte()
    }

    private val BLOCK_ORDERS = arrayOf(
        intArrayOf(0, 1, 2, 3),
        intArrayOf(0, 1, 3, 2),
        intArrayOf(0, 2, 1, 3),
        intArrayOf(0, 2, 3, 1),
        intArrayOf(0, 3, 1, 2),
        intArrayOf(0, 3, 2, 1),
        intArrayOf(1, 0, 2, 3),
        intArrayOf(1, 0, 3, 2),
        intArrayOf(1, 2, 0, 3),
        intArrayOf(1, 2, 3, 0),
        intArrayOf(1, 3, 0, 2),
        intArrayOf(1, 3, 2, 0),
        intArrayOf(2, 0, 1, 3),
        intArrayOf(2, 0, 3, 1),
        intArrayOf(2, 1, 0, 3),
        intArrayOf(2, 1, 3, 0),
        intArrayOf(2, 3, 0, 1),
        intArrayOf(2, 3, 1, 0),
        intArrayOf(3, 0, 1, 2),
        intArrayOf(3, 0, 2, 1),
        intArrayOf(3, 1, 0, 2),
        intArrayOf(3, 1, 2, 0),
        intArrayOf(3, 2, 0, 1),
        intArrayOf(3, 2, 1, 0)
    )
}
