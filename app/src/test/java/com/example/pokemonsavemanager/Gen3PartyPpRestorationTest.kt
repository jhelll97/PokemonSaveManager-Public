package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3PartyPpRestorationTest {
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

    private fun party(count: Int = 6, pid: Int = 7): ByteArray =
        ByteArray(617) { (it * 17 + 9).toByte() }.also { records ->
            for (slot in 0 until count) record(pid + slot, party = true).copyInto(records, slot * 100)
        }

    private fun assertRejected(records: ByteArray, count: Int = 6,
        slots: Collection<Int> = listOf(0), catalog: Map<Int, Int> = basePp) {
        val original = records.copyOf()
        var rejected = false
        try { Gen3PartyPpRestoration.restore(records, count, slots, catalog) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertArrayEquals(original, records)
    }

    @Test
    fun batchesPreserveUnselectedInactiveAndExtensionBytesAcrossPartyCountsAndOrders() {
        val pids = (0 until 24).toList() + listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -2, -1)
        val expected = listOf(Gen3MoveSlot(1, 42, 1), Gen3MoveSlot(45, 64, 3),
            Gen3MoveSlot(150, 56, 2), Gen3MoveSlot(166, 1, 0))
        for (pid in pids) for (count in 1..6) {
            for (slots in listOf((0 until count).toList(), listOf(0, count - 1).distinct())) {
                val raw = party(count, pid)
                val original = raw.copyOf()
                val result = Gen3PartyPpRestoration.restore(raw, count, slots.reversed(), basePp)
                assertEquals(slots.sorted(), result.changedSlots)
                assertTrue(raw.indices.all { index ->
                    (index / 100 in slots && index % 100 < 80) || raw[index] == result.partyRecords[index]
                })
                for (slot in slots) {
                    val before = raw.copyOfRange(slot * 100, (slot + 1) * 100)
                    val after = result.partyRecords.copyOfRange(slot * 100, (slot + 1) * 100)
                    assertEquals(expected, Gen3MoveOrderEditing.read(after, basePp))
                    val a = Gen3Pk3Codec.decode(before.copyOfRange(0, 80)).canonicalSection
                    val b = Gen3Pk3Codec.decode(after.copyOfRange(0, 80)).canonicalSection
                    assertTrue(a.indices.all { it in 20..23 || a[it] == b[it] })
                    assertTrue((0 until 32).all { it in 28..29 || before[it] == after[it] })
                }
                assertArrayEquals(original, raw)
            }
        }
    }

    @Test
    fun independentWireFixtureRestoresOneSelectedSlotOnly() {
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val before = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f202122663325265257e14fc5576a4f4717cc4e4a57d16564139d117e2f49dd84b33db1584fe97d6c1b9529681f992d3c2b45d9f0e77185")
        val after = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f202122b43325265257e14fc5576a4f7917f44e4a57d16564139d117e2f49dd84b33db1584fe97d6c1b9529681f992d3c2b45d9f0e77185")
        val raw = party()
        before.copyInto(raw, 200)
        val expected = raw.copyOf().also { after.copyInto(it, 200) }
        val result = Gen3PartyPpRestoration.restore(raw, 6, listOf(2), basePp)
        assertEquals(listOf(2), result.changedSlots)
        assertArrayEquals(expected, result.partyRecords)
    }

    @Test
    fun fullPpSelectionsProduceNoChangesAndReturnIndependentBuffers() {
        val raw = party(3)
        Gen3PpRestoration.restore(raw.copyOfRange(0, 100), basePp).copyInto(raw, 0)
        val mixed = Gen3PartyPpRestoration.restore(raw, 3, listOf(2, 0), basePp)
        assertEquals(listOf(2), mixed.changedSlots)
        val original = mixed.partyRecords.copyOf()
        val noOp = Gen3PartyPpRestoration.restore(mixed.partyRecords, 3, listOf(0, 2), basePp)
        assertTrue(noOp.changedSlots.isEmpty())
        assertArrayEquals(original, noOp.partyRecords)
        noOp.partyRecords[0] = (noOp.partyRecords[0].toInt() xor 1).toByte()
        assertArrayEquals(original, mixed.partyRecords)
    }

    @Test
    fun invalidCountsStorageAndSelectionsAreRejectedWithoutMutation() {
        val raw = party()
        for (count in listOf(0, 7, Int.MAX_VALUE)) assertRejected(raw, count)
        assertRejected(raw.copyOf(599))
        assertRejected(raw, slots = emptyList())
        assertRejected(raw, slots = listOf(0, 0))
        assertRejected(raw, slots = listOf(-1))
        assertRejected(raw, slots = listOf(6))
        assertRejected(raw, count = 3, slots = listOf(3))
        assertRejected(raw, catalog = emptyMap())
    }

    @Test
    fun aLaterInvalidSelectedRecordRejectsTheEntireBatch() {
        val invalid = listOf(
            record(8, party = true, egg = true),
            record(8, party = true).also { it[0x13] = 3 },
            record(8, party = true).also { it[79] = (it[79].toInt() xor 1).toByte() },
            record(8, party = true, pp = listOf(20, 65, 0, 1)),
            record(8, party = true, ups = 0x6D),
            record(8, party = true, ups = 0, moves = listOf(0, 0, 0, 0), pp = listOf(0, 0, 0, 0))
        )
        for (bad in invalid) {
            val raw = party(3)
            bad.copyInto(raw, 100)
            assertRejected(raw, 3, listOf(0, 1, 2))
        }
    }

    @Test
    fun unselectedEggAndUnselectedMalformedPpRemainExact() {
        val raw = party(4)
        record(8, party = true, egg = true).copyInto(raw, 100)
        record(10, party = true, pp = listOf(20, 65, 0, 1)).copyInto(raw, 300)
        val original = raw.copyOf()
        val result = Gen3PartyPpRestoration.restore(raw, 4, listOf(2, 0), basePp)
        assertEquals(listOf(0, 2), result.changedSlots)
        assertArrayEquals(original.copyOfRange(100, 200), result.partyRecords.copyOfRange(100, 200))
        assertArrayEquals(original.copyOfRange(300, raw.size), result.partyRecords.copyOfRange(300, raw.size))
        assertArrayEquals(original, raw)
    }
}
