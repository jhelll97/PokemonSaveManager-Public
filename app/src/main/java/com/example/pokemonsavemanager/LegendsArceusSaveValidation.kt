package com.example.pokemonsavemanager

import java.security.MessageDigest

enum class LegendsArceusVersion(val gameId: Int, val displayName: String) {
    ARCEUS(47, "Pokémon Legends: Arceus")
}

sealed class LegendsArceusValidation {
    object Unrecognized : LegendsArceusValidation()
    data class Invalid(val reason: String) : LegendsArceusValidation()
    data class Verified(val version: LegendsArceusVersion, val blockCount: Int) : LegendsArceusValidation()
}

internal data class LegendsArceusDexBlock(val version: LegendsArceusVersion, val data: ByteArray)

internal data class LegendsArceusPokemonBlocks(val version: LegendsArceusVersion, val pastures: ByteArray, val party: ByteArray)

internal data class LegendsArceusTrainerBlocks(val version: LegendsArceusVersion, val status: ByteArray,
    val money: Long?, val meritCurrent: Long?, val meritTotal: Long?, val satchelUpgrades: Long?, val expeditionRank: Long?)

internal data class LegendsArceusInventoryBlocks(val regular: ByteArray, val keyItems: ByteArray,
    val stored: ByteArray, val recipes: ByteArray, val favorites: ByteArray, val satchelUpgrades: Long)

/** Independent read-only container gate; pinned format evidence is in SWITCH_READ_ONLY_FORMATS.md. */
object LegendsArceusSaveValidation {
    private val knownSizes = setOf(0x136DDE, 0x13AD06)
    private const val STATUS_KEY = 0xF25C070EL
    private val requiredObjects = mapOf(0x47E1CEABL to 0x54600,
        0x2985FE5DL to 0x8D4, 0x02168706L to 0x1E460, STATUS_KEY to 0x80)
    private val intro = decodeHex("9ec99cd70ed33c44fb9303dceb39b42a1947e9634ba2334416bf82a2ba6355b63d9df24b5f7b6ab2621dc21b68e5c8b53a059000e8a8103de2ecf00cb2ed4f6d")
    private val outro = decodeHex("d6c01c598bc8b8cb46e153fc828c757513e045df32693c75f059f8d9a25fb217e08052dbea8973997579afcb2e8007e6f126e0030ae66ff641bf7e59c2ae55fd")
    private val pad = decodeHex("a092d10607db32a1ae01f5c51e844fe353ca37f4a7b04da018b7c297da5f532b75fa4816f8d48a6f6105f4e2fd04b5a30ffc4492cb32e61bb9b12e01b0565336d2d1503dde5b2e0e52fddf2f7bca6350a4675d2317c052e1a6307c2bb670365b2a276933f5637b363f269ba3ed7a5300a448b3509e14a052de7e102b1b776e")

    fun inspect(source: ByteArray): LegendsArceusValidation = inspect(source, null)

    /** Complete validation must finish before detached object bodies escape this parser. */
    internal fun readPokemonBlocks(source: ByteArray): LegendsArceusPokemonBlocks {
        val captured = mutableMapOf(0x47E1CEABL to ByteArray(0), 0x2985FE5DL to ByteArray(0))
        val checked = inspect(source, captured)
        require(checked is LegendsArceusValidation.Verified) { "A validated native Legends: Arceus save is required." }
        return LegendsArceusPokemonBlocks(checked.version, captured.getValue(0x47E1CEABL), captured.getValue(0x2985FE5DL))
    }

    /** Required native dex object is detached only after the entire container has passed. */
    internal fun readDexBlock(source: ByteArray): LegendsArceusDexBlock {
        val captured = mutableMapOf(0x02168706L to ByteArray(0))
        val checked = inspect(source, captured)
        require(checked is LegendsArceusValidation.Verified) { "A validated native Legends: Arceus Pokédex save is required." }
        return LegendsArceusDexBlock(checked.version, captured.getValue(0x02168706L))
    }

    /** Optional counters stay absent rather than becoming invented zero values. */
    internal fun readTrainerBlocks(source: ByteArray): LegendsArceusTrainerBlocks {
        val uintKeys = setOf(0x3279D927L, 0x9D5D1CA5L, 0xC25B0D5AL, 0x75CE2CF6L, 0x50FE632AL)
        val captured = (uintKeys + STATUS_KEY).associateWith { ByteArray(0) }.toMutableMap()
        val checked = inspect(source, captured, uintKeys)
        require(checked is LegendsArceusValidation.Verified) { (checked as? LegendsArceusValidation.Invalid)?.reason ?: "A validated native Legends: Arceus trainer save is required." }
        fun optionalUint(key: Long): Long? = captured.getValue(key).takeIf { it.size == 4 }?.let { bytes ->
            (0..3).fold(0L) { value, index -> value or ((bytes[index].toLong() and 255) shl (index * 8)) }
        }
        return LegendsArceusTrainerBlocks(checked.version, captured.getValue(STATUS_KEY),
            optionalUint(0x3279D927L), optionalUint(0x9D5D1CA5L), optionalUint(0xC25B0D5AL),
            optionalUint(0x75CE2CF6L), optionalUint(0x50FE632AL))
    }

    /** Inventory-specific shapes add checks without changing existing reader gates. */
    internal fun readInventoryBlocks(source: ByteArray): LegendsArceusInventoryBlocks {
        val objects = mapOf(0x9FE2790AL to 0xA8C, 0x59A4D0C3L to 0x190,
            0x8E434F0DL to 0x2D0, 0xF5D9F4A5L to 0x118)
        val captured = (objects.keys + setOf(0x00EF4BAEL, 0x75CE2CF6L))
            .associateWith { ByteArray(0) }.toMutableMap()
        val checked = inspect(source, captured, setOf(0x75CE2CF6L), objects, mapOf(0x00EF4BAEL to 0x140))
        require(checked is LegendsArceusValidation.Verified) {
            (checked as? LegendsArceusValidation.Invalid)?.reason ?: "A validated native Arceus save is required."
        }
        require(objects.all { (key, size) -> captured.getValue(key).size == size } &&
            captured.getValue(0x00EF4BAEL).size == 0x140 && captured.getValue(0x75CE2CF6L).size == 4) {
            "Arceus inventory blocks or the saved satchel upgrade counter are missing."
        }
        val raw = captured.getValue(0x75CE2CF6L)
        val upgrades = (0..3).fold(0L) { value, i -> value or ((raw[i].toLong() and 255) shl (i * 8)) }
        return LegendsArceusInventoryBlocks(captured.getValue(0x9FE2790AL), captured.getValue(0x59A4D0C3L),
            captured.getValue(0x8E434F0DL), captured.getValue(0xF5D9F4A5L), captured.getValue(0x00EF4BAEL), upgrades)
    }

    private fun inspect(source: ByteArray, captured: MutableMap<Long, ByteArray>?, uintKeys: Set<Long> = emptySet(),
        objectSizes: Map<Long, Int> = emptyMap(), byteDataSizes: Map<Long, Int> = emptyMap()): LegendsArceusValidation {
        if (source.size !in knownSizes) return LegendsArceusValidation.Unrecognized
        val end = source.size - 32
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro); digest.update(source, 0, end); digest.update(outro)
        if (!MessageDigest.isEqual(digest.digest(), source.copyOfRange(end, source.size)))
            return LegendsArceusValidation.Invalid("The Legends: Arceus container hash failed.")
        // Only the detached payload is decrypted. The caller's bytes are never changed.
        val payload = source.copyOfRange(0, end)
        for (index in payload.indices) payload[index] = (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        return try { inspectBlocks(payload, captured, uintKeys, objectSizes, byteDataSizes) }
        catch (e: IllegalArgumentException) { LegendsArceusValidation.Invalid(e.message ?: "Malformed Legends: Arceus blocks.") }
    }

    private fun inspectBlocks(bytes: ByteArray, captured: MutableMap<Long, ByteArray>?, uintKeys: Set<Long>,
        objectSizes: Map<Long, Int>, byteDataSizes: Map<Long, Int>): LegendsArceusValidation {
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
            var arraySubtype: Int? = null
            val length: Long = when (type) {
                1, 2, 3 -> 0L
                4 -> cursor.uint32() xor stream.next32()
                5 -> {
                    val elements = cursor.uint32() xor stream.next32()
                    val subType = cursor.byte() xor stream.next()
                    arraySubtype = subType
                    elements * elementSize(subType)
                }
                else -> elementSize(type).toLong()
            }
            require(length <= cursor.remaining.toLong()) { "A native block length exceeds the remaining payload." }
            val expected = requiredObjects[key]
            if (expected != null) {
                require(type == 4 && length == expected.toLong()) { "A required Legends: Arceus block has the wrong type or size." }
                found += key
            }
            // Only trainer extraction requests these keys; identity/Pokémon gates retain their original behavior.
            if (key in uintKeys) require(type == 10 && length == 4L) { "A trainer counter must be a native UInt32." }
            objectSizes[key]?.let { expectedSize ->
                require(type == 4 && length == expectedSize.toLong()) { "An Arceus inventory pocket has the wrong type or size." }
            }
            byteDataSizes[key]?.let { expectedSize ->
                require((type == 4 || (type == 5 && arraySubtype == 8)) && length == expectedSize.toLong()) {
                    "Arceus favorite flags must be a byte body of the native size."
                }
            }
            val capture = captured?.containsKey(key) == true
            if (key == STATUS_KEY || capture) {
                val decoded = if (capture) ByteArray(length.toInt()) else null
                // Consume the same stream used by the unaligned type/length prefix.
                repeat(length.toInt()) { offset ->
                    val value = cursor.byte() xor stream.next()
                    if (key == STATUS_KEY && offset == 0x14) gameId = value
                    if (decoded != null) decoded[offset] = value.toByte()
                }
                if (decoded != null) captured?.put(key, decoded)
            } else cursor.skip(length.toInt())
        }
        require(found.size == requiredObjects.size) { "Required Legends: Arceus identity blocks are missing." }
        val version = LegendsArceusVersion.values().firstOrNull { it.gameId == gameId }
            ?: throw IllegalArgumentException("The saved title ID is not Legends: Arceus.")
        return LegendsArceusValidation.Verified(version, count)
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

