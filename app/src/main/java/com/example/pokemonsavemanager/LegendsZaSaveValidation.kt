package com.example.pokemonsavemanager

import java.security.MessageDigest

enum class LegendsZaVersion(val gameId: Int, val displayName: String) {
    ZA(52, "Pokémon Legends: Z-A")
}

enum class LegendsZaRevision(val marker: Int, val displayName: String) {
    BASE(0, "Base"), MEGA_DIMENSION(1, "Mega Dimension"), END_OF_LIFE(2, "End-of-life")
}

sealed class LegendsZaValidation {
    object Unrecognized : LegendsZaValidation()
    data class Invalid(val reason: String) : LegendsZaValidation()
    data class Verified(val version: LegendsZaVersion, val blockCount: Int, val revision: LegendsZaRevision?) : LegendsZaValidation()
}

internal data class LegendsZaDexBlock(val version: LegendsZaVersion, val revision: LegendsZaRevision?, val data: ByteArray)

internal data class LegendsZaPokemonBlocks(val version: LegendsZaVersion, val revision: LegendsZaRevision?, val boxes: ByteArray, val party: ByteArray)

internal data class LegendsZaTrainerBlocks(val version: LegendsZaVersion, val revision: LegendsZaRevision?, val status: ByteArray,
    val money: Long?, val royaleTicketPoints: Long?, val infiniteTicketPoints: Long?)

internal data class LegendsZaInventoryBlock(val version: LegendsZaVersion, val revision: LegendsZaRevision?,
    val data: ByteArray, val megaShardMaxPatched: Boolean)

internal data class LegendsZaInventoryWriteResult(val bytes: ByteArray, val absoluteOffset: Int)

internal data class LegendsZaDexWriteResult(val bytes: ByteArray, val absoluteOffset: Int)

/** Independent read-only container gate; pinned format evidence is in SWITCH_READ_ONLY_FORMATS.md. */
object LegendsZaSaveValidation {
    private val knownSizes = setOf(0x2F3284, 0x2F3289, 0x309FA6, 0x309FB3)
    private const val STATUS_KEY = 0xE3E89BD1L
    private const val REVISION_KEY = 0x0926555AL
    private val requiredObjects = mapOf(0x0D66012CL to 0x5FA00,
        0x3AA1A9ADL to 0xB40, 0x2D87BE5CL to 0x27068, STATUS_KEY to 0x78)
    private val intro = decodeHex("9ec99cd70ed33c44fb9303dceb39b42a1947e9634ba2334416bf82a2ba6355b63d9df24b5f7b6ab2621dc21b68e5c8b53a059000e8a8103de2ecf00cb2ed4f6d")
    private val outro = decodeHex("d6c01c598bc8b8cb46e153fc828c757513e045df32693c75f059f8d9a25fb217e08052dbea8973997579afcb2e8007e6f126e0030ae66ff641bf7e59c2ae55fd")
    private val pad = decodeHex("a092d10607db32a1ae01f5c51e844fe353ca37f4a7b04da018b7c297da5f532b75fa4816f8d48a6f6105f4e2fd04b5a30ffc4492cb32e61bb9b12e01b0565336d2d1503dde5b2e0e52fddf2f7bca6350a4675d2317c052e1a6307c2bb670365b2a276933f5637b363f269ba3ed7a5300a448b3509e14a052de7e102b1b776e")

    fun inspect(source: ByteArray): LegendsZaValidation = inspect(source, null)

    /** Return only detached core bodies after every native identity/integrity gate succeeds. */
    internal fun readPokemonBlocks(source: ByteArray): LegendsZaPokemonBlocks {
        val captured = mutableMapOf(0x0D66012CL to ByteArray(0), 0x3AA1A9ADL to ByteArray(0))
        val checked = inspect(source, captured)
        require(checked is LegendsZaValidation.Verified) { "A validated native Legends: Z-A save is required." }
        return LegendsZaPokemonBlocks(checked.version, checked.revision, captured.getValue(0x0D66012CL), captured.getValue(0x3AA1A9ADL))
    }

    /** Detach the required Pokédex object only after every native integrity/title/revision gate. */
    internal fun readDexBlock(source: ByteArray): LegendsZaDexBlock {
        val captured = mutableMapOf(0x2D87BE5CL to ByteArray(0))
        val checked = inspect(source, captured)
        require(checked is LegendsZaValidation.Verified) { "A validated native Legends: Z-A Pokédex save is required." }
        return LegendsZaDexBlock(checked.version, checked.revision, captured.getValue(0x2D87BE5CL))
    }

    /** Only requested trainer counters require native UInt32; missing counters remain absent. */
    internal fun readTrainerBlocks(source: ByteArray): LegendsZaTrainerBlocks {
        val uintKeys = setOf(0x4F35D0DDL, 0x9A730DE1L, 0x1D7EE369L)
        val captured = (uintKeys + STATUS_KEY).associateWith { ByteArray(0) }.toMutableMap()
        val checked = inspect(source, captured, uintKeys)
        require(checked is LegendsZaValidation.Verified) { (checked as? LegendsZaValidation.Invalid)?.reason ?: "A validated native Legends: Z-A trainer save is required." }
        fun optionalUint(key: Long): Long? = captured.getValue(key).takeIf { it.size == 4 }?.let { bytes ->
            (0..3).fold(0L) { value, index -> value or ((bytes[index].toLong() and 255) shl (index * 8)) }
        }
        return LegendsZaTrainerBlocks(checked.version, checked.revision, captured.getValue(STATUS_KEY),
            optionalUint(0x4F35D0DDL), optionalUint(0x9A730DE1L), optionalUint(0x1D7EE369L))
    }

    /** Return the native bag plus patch-key presence only after the complete container gate. */
    internal fun readInventoryBlock(source: ByteArray): LegendsZaInventoryBlock {
        val captured = mutableMapOf(0x21C9BD44L to ByteArray(0), 0x0ABC6547L to ByteArray(0))
        val checked = inspect(source, captured)
        require(checked is LegendsZaValidation.Verified) {
            (checked as? LegendsZaValidation.Invalid)?.reason ?: "A validated native Z-A save is required."
        }
        require(captured.getValue(0x21C9BD44L).size == 0xBC00) { "The native Z-A inventory block is missing." }
        return LegendsZaInventoryBlock(checked.version, checked.revision, captured.getValue(0x21C9BD44L),
            captured.getValue(0x0ABC6547L).isNotEmpty())
    }

    /** Replaces one decoded 16-byte item record and rebuilds native container integrity. */
    internal fun writeInventoryRecord(
        source: ByteArray,
        itemId: Int,
        decodedRecord: ByteArray
    ): LegendsZaInventoryWriteResult {
        readInventoryBlock(source)
        require(itemId in 1 until 3000) { "The native Legends: Z-A item index must be 1 through 2999." }
        require(decodedRecord.size == 16) { "A native Legends: Z-A item record must be 16 bytes." }
        val relativeOffset = itemId * 16
        val end = source.size - 32
        val payload = source.copyOfRange(0, end)
        for (index in payload.indices) payload[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()

        val cursor = Cursor(payload)
        var absoluteOffset = -1
        while (cursor.remaining > 0) {
            val key = cursor.uint32()
            val stream = KeyStream(key)
            val type = cursor.byte() xor stream.next()
            val length = when (type) {
                1, 2, 3 -> 0L
                4 -> cursor.uint32() xor stream.next32()
                5 -> {
                    val elements = cursor.uint32() xor stream.next32()
                    val subType = cursor.byte() xor stream.next()
                    elements * elementSize(subType).toLong()
                }
                else -> elementSize(type).toLong()
            }
            require(length <= cursor.remaining.toLong()) { "A native block length exceeds the remaining payload." }
            val blockStart = payload.size - cursor.remaining
            if (key == 0x21C9BD44L) {
                require(type == 4 && length == 0xBC00L) {
                    "The Legends: Z-A inventory block has the wrong native type or size."
                }
                repeat(relativeOffset) { stream.next() }
                repeat(decodedRecord.size) { byteIndex ->
                    payload[blockStart + relativeOffset + byteIndex] =
                        (decodedRecord[byteIndex].toInt() xor stream.next()).toByte()
                }
                absoluteOffset = blockStart + relativeOffset
            }
            cursor.skip(length.toInt())
        }
        require(absoluteOffset >= 0) { "The Legends: Z-A inventory block is missing." }

        val edited = source.copyOf()
        for (index in payload.indices) edited[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro)
        digest.update(edited, 0, end)
        digest.update(outro)
        digest.digest().copyInto(edited, end)
        readInventoryBlock(edited)
        return LegendsZaInventoryWriteResult(edited, absoluteOffset)
    }

    /** Replaces one decoded native Pokédex record and rebuilds container integrity. */
    internal fun writeDexRecord(
        source: ByteArray,
        relativeOffset: Int,
        decodedRecord: ByteArray
    ): LegendsZaDexWriteResult {
        readDexBlock(source)
        require(decodedRecord.size == 0x84) { "A native Legends: Z-A Pokédex record must be 0x84 bytes." }
        require(relativeOffset >= 0 && relativeOffset + decodedRecord.size <= 0x27068 &&
            relativeOffset % decodedRecord.size == 0) {
            "The selected Legends: Z-A Pokédex record is outside its native block."
        }
        val end = source.size - 32
        val payload = source.copyOfRange(0, end)
        for (index in payload.indices) payload[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()

        val cursor = Cursor(payload)
        var absoluteOffset = -1
        while (cursor.remaining > 0) {
            val key = cursor.uint32()
            val stream = KeyStream(key)
            val type = cursor.byte() xor stream.next()
            val length = when (type) {
                1, 2, 3 -> 0L
                4 -> cursor.uint32() xor stream.next32()
                5 -> {
                    val elements = cursor.uint32() xor stream.next32()
                    val subType = cursor.byte() xor stream.next()
                    elements * elementSize(subType).toLong()
                }
                else -> elementSize(type).toLong()
            }
            require(length <= cursor.remaining.toLong()) { "A native block length exceeds the remaining payload." }
            val blockStart = payload.size - cursor.remaining
            if (key == 0x2D87BE5CL) {
                require(type == 4 && length == 0x27068L) {
                    "The Legends: Z-A Pokédex block has the wrong native type or size."
                }
                repeat(relativeOffset) { stream.next() }
                repeat(decodedRecord.size) { byteIndex ->
                    payload[blockStart + relativeOffset + byteIndex] =
                        (decodedRecord[byteIndex].toInt() xor stream.next()).toByte()
                }
                absoluteOffset = blockStart + relativeOffset
            }
            cursor.skip(length.toInt())
        }
        require(absoluteOffset >= 0) { "The Legends: Z-A Pokédex block is missing." }

        val edited = source.copyOf()
        for (index in payload.indices) edited[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro)
        digest.update(edited, 0, end)
        digest.update(outro)
        digest.digest().copyInto(edited, end)
        readDexBlock(edited)
        return LegendsZaDexWriteResult(edited, absoluteOffset)
    }

    private fun inspect(source: ByteArray, captured: MutableMap<Long, ByteArray>?, uintKeys: Set<Long> = emptySet()): LegendsZaValidation {
        if (source.size !in knownSizes) return LegendsZaValidation.Unrecognized
        val end = source.size - 32
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro); digest.update(source, 0, end); digest.update(outro)
        if (!MessageDigest.isEqual(digest.digest(), source.copyOfRange(end, source.size)))
            return LegendsZaValidation.Invalid("The Legends: Z-A container hash failed.")
        // Only the detached payload is decrypted. The caller's bytes are never changed.
        val payload = source.copyOfRange(0, end)
        for (index in payload.indices) payload[index] = (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        return try { inspectBlocks(payload, captured, uintKeys) }
        catch (e: IllegalArgumentException) { LegendsZaValidation.Invalid(e.message ?: "Malformed Legends: Z-A blocks.") }
    }

    private fun inspectBlocks(bytes: ByteArray, captured: MutableMap<Long, ByteArray>?, uintKeys: Set<Long>): LegendsZaValidation {
        val cursor = Cursor(bytes)
        val found = mutableSetOf<Long>()
        var previous = -1L
        var count = 0
        var gameId: Int? = null
        var revision: LegendsZaRevision? = null
        while (cursor.remaining > 0) {
            require(++count <= 65536) { "The block count exceeds the read-only parser limit." }
            val key = cursor.uint32()
            require(key > previous) { "Native block keys are duplicated or out of order." }
            previous = key
            val stream = KeyStream(key)
            val type = cursor.byte() xor stream.next()
            val length: Long = when (type) {
                1, 2, 3 -> 0L
                4 -> cursor.uint32() xor stream.next32()
                5 -> {
                    val elements = cursor.uint32() xor stream.next32()
                    val subType = cursor.byte() xor stream.next()
                    elements * elementSize(subType)
                }
                else -> elementSize(type).toLong()
            }
            require(length <= cursor.remaining.toLong()) { "A native block length exceeds the remaining payload." }
            val expected = requiredObjects[key]
            if (expected != null) {
                require(type == 4 && length == expected.toLong()) { "A required Legends: Z-A block has the wrong type or size." }
                found += key
            }
            // Trainer-only constraints preserve generic identity and Pokémon reader behavior.
            if (key in uintKeys) require(type == 10 && length == 4L) { "A trainer currency counter must be a native UInt32." }
            if (key == 0x21C9BD44L && captured?.containsKey(key) == true)
                require(type == 4 && length == 0xBC00L) { "The Z-A inventory object has the wrong native type or size." }
            // The reference uses HasBlock: its presence, not its value or save revision.
            if (key == 0x0ABC6547L && captured?.containsKey(key) == true) captured?.put(key, byteArrayOf(1))
            val capture = key != 0x0ABC6547L && captured?.containsKey(key) == true
            if (key == REVISION_KEY) {
                require(type == 11 && length == 8L) { "The Z-A revision marker must be a native UInt64." }
                val marker = cursor.byte() xor stream.next()
                repeat(7) { require((cursor.byte() xor stream.next()) == 0) { "Unsupported Z-A revision marker." } }
                revision = LegendsZaRevision.values().firstOrNull { it.marker == marker }
                    ?: throw IllegalArgumentException("Unsupported Z-A revision marker.")
            } else if (key == STATUS_KEY || capture) {
                val decoded = if (capture) ByteArray(length.toInt()) else null
                // Consume the same stream used by the unaligned type/length prefix.
                repeat(length.toInt()) { offset ->
                    val value = cursor.byte() xor stream.next()
                    if (key == STATUS_KEY && offset == 0x04) gameId = value
                    if (decoded != null) decoded[offset] = value.toByte()
                }
                if (decoded != null) captured?.put(key, decoded)
            } else cursor.skip(length.toInt())
        }
        require(found.size == requiredObjects.size) { "Required Legends: Z-A identity blocks are missing." }
        val version = LegendsZaVersion.values().firstOrNull { it.gameId == gameId }
            ?: throw IllegalArgumentException("The saved title ID is not Legends: Z-A.")
        return LegendsZaValidation.Verified(version, count, revision)
    }

    private fun elementSize(type: Int): Int = when (type) {
        3, 8, 12 -> 1
        9, 13 -> 2
        10, 14, 16 -> 4
        11, 15, 17 -> 8
        else -> throw IllegalArgumentException("Unsupported native block type $type.")
    }

    private class Cursor(private val bytes: ByteArray) {
        private var position = 0
        val remaining: Int get() = bytes.size - position
        fun byte(): Int {
            require(remaining >= 1) { "The native block stream is truncated." }
            return bytes[position++].toInt() and 255
        }
        fun uint32(): Long {
            require(remaining >= 4) { "A native block prefix is truncated." }
            var result = 0L
            repeat(4) { result = result or (byte().toLong() shl (it * 8)) }
            return result
        }
        fun skip(length: Int) {
            require(length >= 0 && length <= remaining) { "The native block payload is truncated." }
            position += length
        }
    }

    private class KeyStream(seed: Long) {
        private var state = seed.toInt()
        private var index = 0
        init { repeat(Integer.bitCount(state)) { advance() } }
        private fun advance() {
            state = state xor (state shl 2)
            state = state xor (state ushr 15)
            state = state xor (state shl 13)
        }
        fun next(): Int {
            val value = (state ushr (index * 8)) and 255
            if (++index == 4) { index = 0; advance() }
            return value
        }
        fun next32(): Long {
            var result = 0L
            repeat(4) { result = result or (next().toLong() shl (it * 8)) }
            return result
        }
    }

    private fun decodeHex(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

