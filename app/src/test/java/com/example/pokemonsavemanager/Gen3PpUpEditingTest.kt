package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3PpUpEditingTest {
    private val basePp = mapOf(1 to 35, 45 to 40, 150 to 40, 166 to 1)

    private fun record(pid: Int, party: Boolean = false, egg: Boolean = false, ups: Int = 0x21,
        moves: List<Int> = listOf(1, 45, 150, 0), pp: List<Int> = listOf(20, 40, 0, 0)): ByteArray {
        val header = ByteArray(32) { (it + 7).toByte() }
        header[0x13] = (2 or (if (egg) 4 else 0)).toByte()
        val canonical = ByteArray(48) { (it * 13 + 3).toByte() }
        canonical[0] = 25
        canonical[1] = 0
        canonical[8] = ups.toByte()
        for (slot in 0 until 4) {
            canonical[12 + slot * 2] = moves[slot].toByte()
            canonical[13 + slot * 2] = (moves[slot] ushr 8).toByte()
            canonical[20 + slot] = pp[slot].toByte()
        }
        canonical[43] = ((canonical[43].toInt() and 0xBF) or (if (egg) 0x40 else 0)).toByte()
        val stored = Gen3Pk3Codec.encode(header, pid, 12345, 54321, canonical)
        return if (!party) stored else ByteArray(100) { (it * 7 + 1).toByte() }.also { stored.copyInto(it) }
    }

    private fun assertRejected(raw: ByteArray, values: List<Int> = listOf(0, 0, 0, 0),
        catalog: Map<Int, Int> = basePp) {
        val original = raw.copyOf()
        var rejected = false
        try { Gen3PpUpEditing.replace(raw, values, catalog) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertArrayEquals(original, raw)
    }

    @Test
    fun ppUpEditsPreserveMovesCurrentPpAndOtherFieldsInAllOrdersAndPartyRecords() {
        val values = listOf(3, 2, 1, 0)
        val pids = (0 until 24).toList() + listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -2, -1)
        for (pid in pids) for (party in listOf(false, true)) {
            val raw = record(pid, party)
            val original = raw.copyOf()
            val before = Gen3Pk3Codec.decode(raw.copyOfRange(0, 80)).canonicalSection
            val edited = Gen3PpUpEditing.replace(raw, values, basePp)
            val after = Gen3Pk3Codec.decode(edited.copyOfRange(0, 80)).canonicalSection
            assertEquals(values, Gen3PpUpEditing.read(edited, basePp))
            assertEquals(0x1B, after[8].toInt() and 255)
            assertTrue((0 until 48).all { it == 8 || before[it] == after[it] })
            assertTrue((0 until 32).all { it in 28..29 || raw[it] == edited[it] })
            assertArrayEquals(raw.copyOfRange(80, raw.size), edited.copyOfRange(80, edited.size))
            assertArrayEquals(original, raw)
        }
    }

    @Test
    fun everyPackedCountCombinationRoundTripsAndNoOpPreservesBytes() {
        for (packed in 0..255) for (party in listOf(false, true)) {
            val values = List(4) { (packed ushr (it * 2)) and 3 }
            val raw = record(23, party, ups = 0, moves = listOf(1, 45, 150, 1), pp = listOf(0, 0, 0, 0))
            val edited = Gen3PpUpEditing.replace(raw, values, basePp)
            assertEquals(values, Gen3PpUpEditing.read(edited, basePp))
            assertEquals(packed, Gen3Pk3Codec.decode(edited.copyOfRange(0, 80)).canonicalSection[8].toInt() and 255)
            assertArrayEquals(edited, Gen3PpUpEditing.replace(edited, values, basePp))
        }
    }

    @Test
    fun fixedWireFixtureChecksUnsignedPidAndExactEncryptedResult() {
        // Independently packed LE words, PID % 24 = 7 => A,G,M,E; checksum=sum(u16), XOR=PID^OID.
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val raw = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f202122b41925265257e14fc557cc4f477fcc4f4a57d16564139d11722f49dd84b33db1584fe97d6c1b9529681f992d3c2b45d9f0e77185")
        val expected = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f202122ae1925265257e14fc557cc4f477fcc4f4a57d16564139d11482f49dd84b33db1584fe97d6c1b9529681f992d3c2b45d9f0e77185")
        assertEquals(listOf(1, 0, 2, 0), Gen3PpUpEditing.read(raw, basePp))
        assertArrayEquals(expected, Gen3PpUpEditing.replace(raw, listOf(3, 2, 1, 0), basePp))
    }

    @Test
    fun unsafeReductionsAndInvalidCountsOrCatalogDataAreRejected() {
        val raw = record(17)
        for (bad in listOf(emptyList(), listOf(0, 0, 0), listOf(0, 0, 0, 0, 0),
            listOf(-1, 0, 0, 0), listOf(4, 0, 0, 0), listOf(0, 0, 0, 1))) assertRejected(raw, bad)
        assertRejected(raw, catalog = emptyMap())
        assertRejected(raw, catalog = basePp + (1 to 0))
        assertRejected(raw, catalog = basePp + (1 to 41))
        assertEquals(56, Gen3PpUpEditing.maximumPp(1, 3, basePp))
        assertEquals(64, Gen3PpUpEditing.maximumPp(45, 3, basePp))
        val full = record(7, ups = 12, pp = listOf(20, 64, 0, 0))
        assertEquals(listOf(0, 3, 0, 0), Gen3PpUpEditing.read(full, basePp))
        assertRejected(full, listOf(0, 2, 0, 0))
        assertRejected(record(7, ups = 8, pp = listOf(20, 64, 0, 0)))
    }

    @Test
    fun emptyAndOnePpMovesCannotReceivePpUps() {
        val sketch = record(7, ups = 0, moves = listOf(166, 0, 0, 0), pp = listOf(1, 0, 0, 0))
        assertEquals(listOf(0, 0, 0, 0), Gen3PpUpEditing.read(sketch, basePp))
        assertArrayEquals(sketch, Gen3PpUpEditing.replace(sketch, listOf(0, 0, 0, 0), basePp))
        assertRejected(sketch, listOf(1, 0, 0, 0))
        assertRejected(record(7, ups = 1, moves = listOf(166, 0, 0, 0), pp = listOf(1, 0, 0, 0)))
        assertRejected(record(7, pp = listOf(20, 40, 0, 1)))
    }

    @Test
    fun damagedEggEmptyFlagAndIncompleteRecordsAreRejected() {
        val valid = record(1)
        val invalid = listOf(record(1, egg = true),
            record(1, egg = true).also { it[0x13] = 2 }, record(1).also { it[0x13] = 6 },
            record(1).also { it[0x13] = 3 }, record(1).also { it[0x13] = 0 },
            record(1).also { it[79] = (it[79].toInt() xor 1).toByte() },
            record(1, moves = listOf(355, 45, 150, 0)),
            valid.copyOf(79), valid.copyOf(81), valid.copyOf(99), valid.copyOf(101))
        invalid.forEach { assertRejected(it) }
    }
}
