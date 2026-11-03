package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3MoveForgettingTest {
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

    private fun assertRejected(raw: ByteArray, slot: Int = 0, catalog: Map<Int, Int> = basePp) {
        val original = raw.copyOf()
        var rejected = false
        try { Gen3MoveForgetting.forget(raw, slot, catalog) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertArrayEquals(original, raw)
    }

    @Test
    fun forgettingEachMovePreservesRemainingSlotsAcrossAllOrdersAndPartyExtensions() {
        val pids = (0 until 24).toList() + listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -2, -1)
        for (pid in pids) for (party in listOf(false, true)) for (slot in 0 until 4) {
            val raw = record(pid, party)
            val original = raw.copyOf()
            val beforeSlots = Gen3MoveOrderEditing.read(raw, basePp)
            val expected = beforeSlots.filterIndexed { index, _ -> index != slot } + Gen3MoveSlot(0, 0, 0)
            val before = Gen3Pk3Codec.decode(raw.copyOfRange(0, 80)).canonicalSection
            val edited = Gen3MoveForgetting.forget(raw, slot, basePp)
            val after = Gen3Pk3Codec.decode(edited.copyOfRange(0, 80)).canonicalSection
            assertEquals(expected, Gen3MoveOrderEditing.read(edited, basePp))
            assertTrue((0 until 48).all { it == 8 || it in 12..23 || before[it] == after[it] })
            assertTrue((0 until 32).all { it in 28..29 || raw[it] == edited[it] })
            assertArrayEquals(raw.copyOfRange(80, raw.size), edited.copyOfRange(80, edited.size))
            assertArrayEquals(original, raw)
        }
    }

    @Test
    fun fixedWireFixtureChecksUnsignedPidAndExactMoveCompaction() {
        // Independently packed LE words, PID % 24 = 7 => A,G,M,E; checksum=sum(u16), XOR=PID^OID.
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val raw = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f202122663325265257e14fc5576a4f4717cc4e4a57d16564139d117e2f49dd84b33db1584fe97d6c1b9529681f992d3c2b45d9f0e77185")
        val expected = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f20212216f2252652575a4ff557cc4f4757cd4f4a57d16564139d115a2f49dd84b33db1584fe97d6c1b9529681f992d3c2b45d9f0e77185")
        assertEquals(listOf(Gen3MoveSlot(1, 20, 1), Gen3MoveSlot(150, 0, 2),
            Gen3MoveSlot(166, 1, 0), Gen3MoveSlot(0, 0, 0)), Gen3MoveOrderEditing.read(expected, basePp))
        assertArrayEquals(expected, Gen3MoveForgetting.forget(raw, 1, basePp))
    }

    @Test
    fun shorterMoveListsCompactAndClearEveryUnusedSlot() {
        for (party in listOf(false, true)) for (count in 2..3) for (removed in 0 until count) {
            val moves = listOf(1, 45, 150, 166).mapIndexed { index, move -> if (index < count) move else 0 }
            val pp = listOf(20, 64, 0, 1).mapIndexed { index, value -> if (index < count) value else 0 }
            val raw = record(23, party, ups = if (count == 2) 13 else 45, moves = moves, pp = pp)
            val before = Gen3MoveOrderEditing.read(raw, basePp).take(count)
            val edited = Gen3MoveForgetting.forget(raw, removed, basePp)
            val after = Gen3MoveOrderEditing.read(edited, basePp)
            assertEquals(before.filterIndexed { index, _ -> index != removed }, after.take(count - 1))
            assertTrue(after.drop(count - 1).all { it == Gen3MoveSlot(0, 0, 0) })
        }
        assertRejected(record(7, ups = 1, moves = listOf(1, 0, 0, 0), pp = listOf(20, 0, 0, 0)))
        assertRejected(record(7, ups = 0, moves = listOf(0, 0, 0, 0), pp = listOf(0, 0, 0, 0)))
    }

    @Test
    fun invalidSlotsGapsAndMissingMetadataAreRejectedWithoutMutation() {
        val raw = record(7)
        for (slot in listOf(-1, 4, Int.MAX_VALUE)) assertRejected(raw, slot)
        assertRejected(record(7, moves = listOf(1, 45, 150, 0), pp = listOf(20, 64, 0, 0)), 3)
        assertRejected(raw, catalog = emptyMap())
        assertRejected(record(7, ups = 0, moves = listOf(1, 0, 150, 166), pp = listOf(1, 0, 1, 1)))
    }

    @Test
    fun damagedEggAndInvalidPpRecordsAreRejected() {
        val valid = record(1)
        val invalid = listOf(record(1, egg = true),
            record(1, egg = true).also { it[0x13] = 2 }, record(1).also { it[0x13] = 6 },
            record(1).also { it[0x13] = 3 }, record(1).also { it[0x13] = 0 },
            record(1).also { it[79] = (it[79].toInt() xor 1).toByte() },
            record(1, pp = listOf(20, 65, 0, 1)),
            valid.copyOf(79), valid.copyOf(81), valid.copyOf(99), valid.copyOf(101))
        invalid.forEach { assertRejected(it) }
    }
}
