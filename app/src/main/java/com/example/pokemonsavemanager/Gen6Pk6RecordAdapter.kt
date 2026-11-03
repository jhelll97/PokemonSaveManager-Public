package com.example.pokemonsavemanager

/**
 * Standalone lossless PK6 record adapter extracted from the verified Gen 6
 * record handling in MainActivity. It deliberately does not perform save
 * placement, Legalise, or game-version conversion.
 */
data class Gen6Pk6Record(
    val encryptedBytes: ByteArray,
    val decryptedBytes: ByteArray,
    val species: Int,
    val encryptionConstant: Long,
    val tid: Int,
    val sid: Int,
    val pid: Long,
    val form: Int,
    val gender: Int,
    val nature: Int,
    val ability: Int,
    val abilityNumber: Int,
    val experience: Long,
    val moves: IntArray,
    val movePp: IntArray,
    val ivs: IntArray,
    val evs: IntArray,
    val nickname: String,
    val otName: String,
    val checksumValid: Boolean
) {
    init {
        require(encryptedBytes.size == Gen6Pk6Adapter.STORED_SIZE)
        require(decryptedBytes.size == Gen6Pk6Adapter.STORED_SIZE)
    }

    /** PK6 IV32 bit 30; eggs must not create destination Pokédex entries. */
    val isEgg: Boolean = (decryptedBytes[0x77].toInt() and 0x40) != 0
}

object Gen6Pk6Adapter {
    const val STORED_SIZE = 0xE8
    private const val BLOCK_SIZE = 56

    private val blockPosition = intArrayOf(
        0,1,2,3, 0,1,3,2, 0,2,1,3, 0,3,1,2,
        0,2,3,1, 0,3,2,1, 1,0,2,3, 1,0,3,2,
        2,0,1,3, 3,0,1,2, 2,0,3,1, 3,0,2,1,
        1,2,0,3, 1,3,0,2, 2,1,0,3, 3,1,0,2,
        2,3,0,1, 3,2,0,1, 1,2,3,0, 1,3,2,0,
        2,1,3,0, 3,1,2,0, 2,3,1,0, 3,2,1,0,
        0,1,2,3, 0,1,3,2, 0,2,1,3, 0,3,1,2,
        0,2,3,1, 0,3,2,1, 1,0,2,3, 1,0,3,2
    )
    private val blockPositionInvert = intArrayOf(
        0,1,2,4, 3,5,6,7, 12,18,13,19, 8,10,14,20,
        16,22,9,11, 15,21,17,23, 0,1,2,4, 3,5,6,7
    )

    fun decode(input: ByteArray): Gen6Pk6Record {
        require(input.size == STORED_SIZE) { "PK6 record must be exactly 0xE8 bytes." }
        val encrypted = input.copyOf()
        val plain = decryptIfNeeded(encrypted)
        val species = readU16(plain, 0x08)
        require(species in 1..721) { "PK6 species is outside the supported Gen 6 range." }
        val checksum = checksum(plain)
        require(checksum == readU16(plain, 0x06)) { "PK6 checksum is invalid." }
        val genderForm = plain[0x1D].toInt() and 0xFF
        val ivsValue = readU32(plain, 0x74)
        return Gen6Pk6Record(
            encryptedBytes = encrypted,
            decryptedBytes = plain.copyOf(),
            species = species,
            encryptionConstant = readU32(plain, 0),
            tid = readU16(plain, 0x0C),
            sid = readU16(plain, 0x0E),
            pid = readU32(plain, 0x18),
            form = genderForm ushr 3,
            gender = (genderForm ushr 1) and 3,
            nature = plain[0x1C].toInt() and 0xFF,
            ability = plain[0x14].toInt() and 0xFF,
            abilityNumber = plain[0x15].toInt() and 7,
            experience = readU32(plain, 0x10),
            moves = intArrayOf(readU16(plain, 0x5A), readU16(plain, 0x5C), readU16(plain, 0x5E), readU16(plain, 0x60)),
            movePp = intArrayOf(plain[0x62].toInt() and 0xFF, plain[0x63].toInt() and 0xFF, plain[0x64].toInt() and 0xFF, plain[0x65].toInt() and 0xFF),
            ivs = IntArray(6) { ((ivsValue ushr (it * 5)) and 0x1F).toInt() },
            evs = IntArray(6) { plain[0x1E + it].toInt() and 0xFF },
            nickname = readString(plain, 0x40, 26),
            otName = readString(plain, 0xB0, 26),
            checksumValid = true
        )
    }

    fun encode(record: Gen6Pk6Record): ByteArray {
        require(record.decryptedBytes.size == STORED_SIZE)
        val plain = record.decryptedBytes.copyOf()
        require(readU16(plain, 0x08) == record.species) { "PK6 record identity changed." }
        writeU16(plain, 0x06, checksum(plain))
        return encrypt(plain)
    }

    private fun checksum(data: ByteArray): Int {
        var sum = 0
        for (offset in 0x08 until STORED_SIZE step 2) sum = (sum + readU16(data, offset)) and 0xFFFF
        return sum
    }

    private fun decryptIfNeeded(input: ByteArray): ByteArray {
        val data = input.copyOf()
        val encrypted = readU16(data, 0xC8) != 0 || readU16(data, 0x58) != 0
        if (!encrypted) return data
        val ec = readU32(data, 0)
        crypt(data, 0x08, STORED_SIZE, ec)
        shuffle(data, 0x08, ((ec ushr 13) and 31).toInt())
        return data
    }

    private fun encrypt(input: ByteArray): ByteArray {
        val data = input.copyOf()
        val ec = readU32(data, 0)
        shuffle(data, 0x08, blockPositionInvert[((ec ushr 13) and 31).toInt()])
        crypt(data, 0x08, STORED_SIZE, ec)
        return data
    }

    private fun crypt(data: ByteArray, start: Int, end: Int, initialSeed: Long) {
        var seed = initialSeed
        var offset = start
        while (offset + 1 < end) {
            seed = (0x41C64E6DL * seed + 0x6073L) and 0xFFFFFFFFL
            val xor = ((seed ushr 16) and 0xFFFF).toInt()
            writeU16(data, offset, readU16(data, offset) xor xor)
            offset += 2
        }
    }

    private fun shuffle(data: ByteArray, start: Int, sv: Int) {
        if (sv == 0) return
        val base = sv * 4
        val slots = intArrayOf(0, 1, 2, 3)
        for (i in 0 until 3) {
            val desired = blockPosition[base + i]
            val j = slots.indexOf(desired)
            if (j == i) continue
            swap(data, start + i * BLOCK_SIZE, start + j * BLOCK_SIZE)
            val moved = slots[i]
            slots[i] = slots[j]
            slots[j] = moved
        }
    }

    private fun swap(data: ByteArray, first: Int, second: Int) {
        for (i in 0 until BLOCK_SIZE) {
            val value = data[first + i]
            data[first + i] = data[second + i]
            data[second + i] = value
        }
    }

    private fun readU16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun readU32(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFF) or
            ((data[offset + 1].toLong() and 0xFF) shl 8) or
            ((data[offset + 2].toLong() and 0xFF) shl 16) or
            ((data[offset + 3].toLong() and 0xFF) shl 24)

    private fun writeU16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun readString(data: ByteArray, offset: Int, length: Int): String {
        val out = StringBuilder()
        var cursor = offset
        while (cursor + 1 < offset + length) {
            val value = readU16(data, cursor)
            if (value == 0 || value == 0xFFFF) break
            out.append(if (value in 0xD800..0xDFFF) '�' else value.toChar())
            cursor += 2
        }
        return out.toString()
    }
}
