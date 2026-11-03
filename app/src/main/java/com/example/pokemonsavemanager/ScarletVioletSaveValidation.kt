package com.example.pokemonsavemanager

import java.security.MessageDigest

enum class ScarletVioletVersion(val gameId: Int, val displayName: String) {
    SCARLET(50, "Pokémon Scarlet"),
    VIOLET(51, "Pokémon Violet")
}

sealed class ScarletVioletValidation {
    object Unrecognized : ScarletVioletValidation()
    data class Invalid(val reason: String) : ScarletVioletValidation()
    data class Verified(val version: ScarletVioletVersion, val blockCount: Int) : ScarletVioletValidation()
}

internal data class ScarletVioletDexBlocks(val version: ScarletVioletVersion, val original: ByteArray, val dlc: ByteArray?)

internal data class ScarletVioletPokemonBlocks(val version: ScarletVioletVersion, val boxes: ByteArray, val party: ByteArray)

internal data class ScarletVioletTrainerBlocks(val version: ScarletVioletVersion, val status: ByteArray,
    val money: Long?, val leaguePoints: Long?, val blueberryPoints: Long?)

internal data class ScarletVioletInventoryBlock(val version: ScarletVioletVersion, val data: ByteArray)

/** Independent read-only container gate; pinned format evidence is in SWITCH_READ_ONLY_FORMATS.md. */
object ScarletVioletSaveValidation {
    private val knownSizes = setOf(0x31626F, 0x31627C, 0x319DB3, 0x319DC0,
        0x319DC3, 0x319DD0, 0x31A2C0, 0x31A2CD, 0x31A2DD, 0x31A2D0,
        0x31CA6F, 0x31CA62, 0x31CA67, 0x31CA74, 0x31CF7C, 0x31CF6F, 0x31CF74, 0x31CF81)
    // Native optional-block tolerances from pinned SaveUtil, not arbitrary intake ranges.
    private val dlcBaseRanges = listOf(0x4329A0..(0x4329C9 + 100), 0x432EB6..(0x432ED6 + 100))
    private val dlcOffsets = listOf(0, 0xC8E, 0x83AD)
    internal fun isKnownSize(size: Int): Boolean = size in knownSizes || dlcBaseRanges.any { range ->
        dlcOffsets.any { offset -> size >= range.first + offset && size <= range.last + offset }
    }
    private const val STATUS_KEY = 0xE3E89BD1L
    private val requiredObjects = mapOf(0x0D66012CL to 0x53250,
        0x3AA1A9ADL to 0x814, 0x0DEAAEBDL to 0xBD24, STATUS_KEY to 0x68)
    private val intro = decodeHex("9ec99cd70ed33c44fb9303dceb39b42a1947e9634ba2334416bf82a2ba6355b63d9df24b5f7b6ab2621dc21b68e5c8b53a059000e8a8103de2ecf00cb2ed4f6d")
    private val outro = decodeHex("d6c01c598bc8b8cb46e153fc828c757513e045df32693c75f059f8d9a25fb217e08052dbea8973997579afcb2e8007e6f126e0030ae66ff641bf7e59c2ae55fd")
    private val pad = decodeHex("a092d10607db32a1ae01f5c51e844fe353ca37f4a7b04da018b7c297da5f532b75fa4816f8d48a6f6105f4e2fd04b5a30ffc4492cb32e61bb9b12e01b0565336d2d1503dde5b2e0e52fddf2f7bca6350a4675d2317c052e1a6307c2bb670365b2a276933f5637b363f269ba3ed7a5300a448b3509e14a052de7e102b1b776e")

    fun inspect(source: ByteArray): ScarletVioletValidation = inspect(source, null)

    /** Only return detached core bodies after the complete native identity gate succeeds. */
    internal fun readPokemonBlocks(source: ByteArray): ScarletVioletPokemonBlocks {
        val captured = mutableMapOf(0x0D66012CL to ByteArray(0), 0x3AA1A9ADL to ByteArray(0))
        val checked = inspect(source, captured)
        require(checked is ScarletVioletValidation.Verified) { "A validated native Scarlet/Violet save is required." }
        return ScarletVioletPokemonBlocks(checked.version, captured.getValue(0x0D66012CL), captured.getValue(0x3AA1A9ADL))
    }

    /** The nonempty DLC object supersedes the original block; malformed objects never fall back. */
    internal fun readDexBlocks(source: ByteArray): ScarletVioletDexBlocks {
        val captured = mutableMapOf(0x0DEAAEBDL to ByteArray(0), 0xF5D7C0E2L to ByteArray(0))
        val checked = inspect(source, captured)
        require(checked is ScarletVioletValidation.Verified) { (checked as? ScarletVioletValidation.Invalid)?.reason ?: "A validated native Scarlet/Violet Pokédex save is required." }
        return ScarletVioletDexBlocks(checked.version, captured.getValue(0x0DEAAEBDL), captured.getValue(0xF5D7C0E2L).takeIf { it.isNotEmpty() })
    }

    /** Optional counters stay absent rather than becoming invented zero values. */
    internal fun readTrainerBlocks(source: ByteArray): ScarletVioletTrainerBlocks {
        val uintKeys = setOf(0x4F35D0DDL, 0xADB4FE17L, 0x66A33824L)
        val captured = (uintKeys + STATUS_KEY).associateWith { ByteArray(0) }.toMutableMap()
        val checked = inspect(source, captured, uintKeys)
        require(checked is ScarletVioletValidation.Verified) { (checked as? ScarletVioletValidation.Invalid)?.reason ?: "A validated native Scarlet/Violet trainer save is required." }
        fun optionalUint(key: Long): Long? = captured.getValue(key).takeIf { it.size == 4 }?.let { bytes ->
            (0..3).fold(0L) { value, index -> value or ((bytes[index].toLong() and 255) shl (index * 8)) }
        }
        return ScarletVioletTrainerBlocks(checked.version, captured.getValue(STATUS_KEY),
            optionalUint(0x4F35D0DDL), optionalUint(0xADB4FE17L), optionalUint(0x66A33824L))
    }

    /** Fixed native bag shape is checked only for inventory extraction. */
    internal fun readInventoryBlock(source: ByteArray): ScarletVioletInventoryBlock {
        val captured = mutableMapOf(0x21C9BD44L to ByteArray(0))
        val checked = inspect(source, captured)
        require(checked is ScarletVioletValidation.Verified) {
            (checked as? ScarletVioletValidation.Invalid)?.reason ?: "A validated native Scarlet/Violet save is required."
        }
        require(captured.getValue(0x21C9BD44L).size == 0xBB80) { "The native Scarlet/Violet inventory block is missing." }
        return ScarletVioletInventoryBlock(checked.version, captured.getValue(0x21C9BD44L))
    }

    private fun inspect(source: ByteArray, captured: MutableMap<Long, ByteArray>?, uintKeys: Set<Long> = emptySet()): ScarletVioletValidation {
        if (!isKnownSize(source.size)) return ScarletVioletValidation.Unrecognized
        val end = source.size - 32
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro); digest.update(source, 0, end); digest.update(outro)
        if (!MessageDigest.isEqual(digest.digest(), source.copyOfRange(end, source.size)))
            return ScarletVioletValidation.Invalid("The Scarlet/Violet container hash failed.")
        // Only the detached payload is decrypted. The caller's bytes are never changed.
        val payload = source.copyOfRange(0, end)
        for (index in payload.indices) payload[index] = (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        return try { inspectBlocks(payload, captured, uintKeys) }
        catch (e: IllegalArgumentException) { ScarletVioletValidation.Invalid(e.message ?: "Malformed Scarlet/Violet blocks.") }
    }

    private fun inspectBlocks(bytes: ByteArray, captured: MutableMap<Long, ByteArray>?, uintKeys: Set<Long>): ScarletVioletValidation {
        val cursor = Cursor(bytes)
        val found = mutableSetOf<Long>()
        var previous = -1L
        var count = 0
        var gameId: Int? = null
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
                require(type == 4 && length == expected.toLong()) { "A required Scarlet/Violet block has the wrong type or size." }
                found += key
            }
            // Only trainer extraction requests these keys; existing identity/Pokémon gates retain their behavior.
            if (key in uintKeys) require(type == 10 && length == 4L) { "A trainer currency counter must be a native UInt32." }
            // Dex extraction alone requests this optional object. Keep identity/storage/trainer behavior unchanged.
            if (key == 0xF5D7C0E2L && captured?.containsKey(key) == true)
                require(type == 4 && (length == 0L || length == 0x8130L)) { "The DLC Pokédex object has the wrong native type or size." }
            if (key == 0x21C9BD44L && captured?.containsKey(key) == true)
                require(type == 4 && length == 0xBB80L) { "The Scarlet/Violet inventory object has the wrong native type or size." }
            val capture = captured?.containsKey(key) == true
            if (key == STATUS_KEY || capture) {
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
        require(found.size == requiredObjects.size) { "Required Scarlet/Violet identity blocks are missing." }
        val version = ScarletVioletVersion.values().firstOrNull { it.gameId == gameId }
            ?: throw IllegalArgumentException("The saved title ID is not Scarlet/Violet.")
        return ScarletVioletValidation.Verified(version, count)
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
