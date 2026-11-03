package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3PpRestorationTest {
    private val basePp = mapOf(1 to 35, 45 to 40, 150 to 40, 166 to 1)

    private fun record(pid: Int, party: Boolean = false, egg: Boolean = false, ups: Int = 0x2D,
        moves: List<Int> = listOf(1, 45, 150, 166), pp: List<Int> = listOf(20, 64, 0, 1)): ByteArray {
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

    private fun assertRejected(raw: ByteArray, catalog: Map<Int, Int> = basePp) {
        val original = raw.copyOf()
        var rejected = false
        try { Gen3PpRestoration.restore(raw, catalog) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertArrayEquals(original, raw)
    }

    @Test
    fun restoresOnlyPpAcrossAllBlockOrdersUnsignedPidsAndPartyExtensions() {
        val pids = (0 until 24).toList() + listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -2, -1)
        for (pid in pids) for (party in listOf(false, true)) {
            val raw = record(pid, party)
            val original = raw.copyOf()
            val before = Gen3Pk3Codec.decode(raw.copyOfRange(0, 80)).canonicalSection
            val edited = Gen3PpRestoration.restore(raw, basePp)
            val after = Gen3Pk3Codec.decode(edited.copyOfRange(0, 80)).canonicalSection
            assertEquals(listOf(Gen3MoveSlot(1, 42, 1), Gen3MoveSlot(45, 64, 3),
                Gen3MoveSlot(150, 56, 2), Gen3MoveSlot(166, 1, 0)),
                Gen3MoveOrderEditing.read(edited, basePp))
            assertTrue((0 until 48).all { it in 20..23 || before[it] == after[it] })
            assertTrue((0 until 32).all { it in 28..29 || raw[it] == edited[it] })
            assertArrayEquals(raw.copyOfRange(80, raw.size), edited.copyOfRange(80, edited.size))
            assertArrayEquals(original, raw)
            assertArrayEquals(edited, Gen3PpRestoration.restore(edited, basePp))
        }
    }

    @Test
    fun independentWireFixtureChecksUnsignedPidAndMaximumPp() {
        // Independently packed LE words, PID % 24 = 7 => A,G,M,E; checksum=sum(u16), XOR=PID^OID.
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val raw = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f202122663325265257e14fc5576a4f4717cc4e4a57d16564139d117e2f49dd84b33db1584fe97d6c1b9529681f992d3c2b45d9f0e77185")
        val expected = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f202122b43325265257e14fc5576a4f7917f44e4a57d16564139d117e2f49dd84b33db1584fe97d6c1b9529681f992d3c2b45d9f0e77185")
        assertArrayEquals(expected, Gen3PpRestoration.restore(raw, basePp))
    }

    @Test
    fun shortMoveListsKeepEmptySlotsAndEveryPpUpCount() {
        for (party in listOf(false, true)) for (count in 1..4) for (ups in 0..3) {
            val moves = List(4) { if (it < count) 45 else 0 }
            val pp = List(4) { if (it < count) 1 else 0 }
            val packed = (0 until count).fold(0) { bits, slot -> bits or (ups shl (slot * 2)) }
            val raw = record(23, party, ups = packed, moves = moves, pp = pp)
            val edited = Gen3PpRestoration.restore(raw, basePp)
            val slots = Gen3MoveOrderEditing.read(edited, basePp)
            assertEquals(List(count) { Gen3MoveSlot(45, 40 + 8 * ups, ups) }, slots.take(count))
            assertTrue(slots.drop(count).all { it == Gen3MoveSlot(0, 0, 0) })
            assertArrayEquals(edited, Gen3PpRestoration.restore(edited, basePp))
        }
        val sketch = record(7, ups = 0, moves = listOf(166, 0, 0, 0), pp = listOf(0, 0, 0, 0))
        assertEquals(Gen3MoveSlot(166, 1, 0),
            Gen3MoveOrderEditing.read(Gen3PpRestoration.restore(sketch, basePp), basePp).first())
    }

    @Test
    fun rejectsInvalidMetadataGapsAndDamagedPpWithoutRepairingInput() {
        val raw = record(7)
        assertRejected(raw, emptyMap())
        assertRejected(raw, basePp + (45 to 0))
        assertRejected(raw, basePp + (45 to 41))
        assertRejected(record(7, ups = 0x6D)) // Sketch cannot receive PP Ups.
        assertRejected(record(7, pp = listOf(20, 65, 0, 1)))
        assertRejected(record(7, moves = listOf(355, 45, 150, 166)))
        assertRejected(record(7, ups = 0, moves = listOf(1, 0, 150, 166), pp = listOf(1, 0, 1, 1)))
        assertRejected(record(7, ups = 0, moves = listOf(1, 0, 0, 0), pp = listOf(1, 1, 0, 0)))
        assertRejected(record(7, ups = 0, moves = listOf(0, 0, 0, 0), pp = listOf(0, 0, 0, 0)))
    }

    @Test
    fun eggsBadEggsCorruptionAndUnsupportedLengthsAreRejected() {
        val valid = record(1)
        val invalid = listOf(record(1, egg = true),
            record(1, egg = true).also { it[0x13] = 2 }, record(1).also { it[0x13] = 6 },
            record(1).also { it[0x13] = 3 }, record(1).also { it[0x13] = 0 },
            record(1).also { it[79] = (it[79].toInt() xor 1).toByte() },
            valid.copyOf(79), valid.copyOf(81), valid.copyOf(99), valid.copyOf(101))
        invalid.forEach { assertRejected(it) }
    }
}
