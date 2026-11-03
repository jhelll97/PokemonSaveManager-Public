package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3BoxPpRestorationTest {
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

    private fun pc(): ByteArray = ByteArray(33761) { (it * 17 + 9).toByte() }
    private fun offset(box: Int, slot: Int): Int = 4 + box * 30 * 80 + slot * 80

    private fun assertRejected(raw: ByteArray, box: Int = 0, slots: Collection<Int> = listOf(0),
        catalog: Map<Int, Int> = basePp) {
        val original = raw.copyOf()
        var rejected = false
        try { Gen3BoxPpRestoration.restore(raw, box, slots, catalog) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertArrayEquals(original, raw)
    }

    @Test
    fun everyBoxAndBlockOrderPreservesOtherSlotsAndPcMetadata() {
        val pids = (0 until 24).toList() + listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -2, -1)
        val slots = listOf(0, 15, 29)
        val expected = listOf(Gen3MoveSlot(1, 42, 1), Gen3MoveSlot(45, 64, 3),
            Gen3MoveSlot(150, 56, 2), Gen3MoveSlot(166, 1, 0))
        for (pid in pids) for (box in 0 until 14) {
            val raw = pc()
            slots.forEach { record(pid + it).copyInto(raw, offset(box, it)) }
            val original = raw.copyOf()
            val result = Gen3BoxPpRestoration.restore(raw, box, slots.reversed(), basePp)
            assertEquals(slots, result.changedSlots)
            val allowed = slots.flatMap { slot -> (0 until 80).map { offset(box, slot) + it } }.toSet()
            assertTrue(raw.indices.all { it in allowed || raw[it] == result.pc[it] })
            for (slot in slots) {
                val start = offset(box, slot)
                val before = raw.copyOfRange(start, start + 80)
                val after = result.pc.copyOfRange(start, start + 80)
                assertEquals(expected, Gen3MoveOrderEditing.read(after, basePp))
                val a = Gen3Pk3Codec.decode(before).canonicalSection
                val b = Gen3Pk3Codec.decode(after).canonicalSection
                assertTrue(a.indices.all { it in 20..23 || a[it] == b[it] })
                assertTrue((0 until 32).all { it in 28..29 || before[it] == after[it] })
            }
            assertArrayEquals(original, raw)
        }
    }

    @Test
    fun independentWireFixtureRestoresTheLastPcSlotExactly() {
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val before = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f202122663325265257e14fc5576a4f4717cc4e4a57d16564139d117e2f49dd84b33db1584fe97d6c1b9529681f992d3c2b45d9f0e77185")
        val after = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f202122b43325265257e14fc5576a4f7917f44e4a57d16564139d117e2f49dd84b33db1584fe97d6c1b9529681f992d3c2b45d9f0e77185")
        val raw = pc()
        before.copyInto(raw, 33524)
        val expected = raw.copyOf().also { after.copyInto(it, 33524) }
        val result = Gen3BoxPpRestoration.restore(raw, 13, listOf(29), basePp)
        assertEquals(listOf(29), result.changedSlots)
        assertArrayEquals(expected, result.pc)
    }

    @Test
    fun completeBoxSelectionAndAlreadyFullRecordsAreHandledTogether() {
        val raw = pc()
        for (slot in 0 until 30) record(slot).copyInto(raw, offset(7, slot))
        val full = Gen3PpRestoration.restore(record(15), basePp)
        full.copyInto(raw, offset(7, 15))
        val result = Gen3BoxPpRestoration.restore(raw, 7, (0 until 30).toList(), basePp)
        assertEquals((0 until 30).filter { it != 15 }, result.changedSlots)
        val original = result.pc.copyOf()
        val noOp = Gen3BoxPpRestoration.restore(result.pc, 7, (0 until 30).toList(), basePp)
        assertTrue(noOp.changedSlots.isEmpty())
        assertArrayEquals(original, noOp.pc)
        noOp.pc[0] = (noOp.pc[0].toInt() xor 1).toByte()
        assertArrayEquals(original, result.pc)
    }

    @Test
    fun invalidSelectionsEmptySlotsAndShortStorageAreRejected() {
        val raw = pc()
        record(7).copyInto(raw, 4)
        assertRejected(raw, -1)
        assertRejected(raw, 14)
        assertRejected(raw, slots = emptyList())
        assertRejected(raw, slots = listOf(0, 0))
        assertRejected(raw, slots = listOf(-1))
        assertRejected(raw, slots = listOf(30))
        assertRejected(raw.copyOf(33603))
        assertRejected(raw, catalog = emptyMap())
        raw.fill(0, offset(0, 1), offset(0, 1) + 80)
        assertRejected(raw, slots = listOf(0, 1))
    }

    @Test
    fun aLaterInvalidRecordRejectsTheCompleteSelectedBatch() {
        val invalid = listOf(record(8, egg = true),
            record(8).also { it[0x13] = 3 },
            record(8).also { it[79] = (it[79].toInt() xor 1).toByte() },
            record(8, pp = listOf(20, 65, 0, 1)),
            record(8, ups = 0x6D),
            record(8, ups = 0, moves = listOf(0, 0, 0, 0), pp = listOf(0, 0, 0, 0)))
        for (bad in invalid) {
            val raw = pc()
            record(7).copyInto(raw, offset(13, 0))
            bad.copyInto(raw, offset(13, 29))
            assertRejected(raw, 13, listOf(0, 29))
        }
    }

    @Test
    fun unselectedEggsAndMalformedPpStayExact() {
        val raw = pc()
        record(7).copyInto(raw, offset(0, 0))
        record(8, egg = true).copyInto(raw, offset(0, 1))
        record(9, pp = listOf(20, 65, 0, 1)).copyInto(raw, offset(0, 2))
        val original = raw.copyOf()
        val result = Gen3BoxPpRestoration.restore(raw, 0, listOf(0), basePp)
        assertEquals(listOf(0), result.changedSlots)
        assertArrayEquals(original.copyOfRange(84, raw.size), result.pc.copyOfRange(84, raw.size))
        assertArrayEquals(original, raw)
    }
}
