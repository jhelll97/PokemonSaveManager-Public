package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3PokeblockEditingTest {
    private fun section(layout: Gen3PokeblockLayout): ByteArray =
        ByteArray(0xF80) { (it * 17 + 9).toByte() }.also { raw ->
            raw.fill(0, layout.caseOffset, layout.caseOffset + 320)
            for (slot in 0 until 40) raw[layout.caseOffset + slot * 8 + 7] = (slot + 100).toByte()
        }

    private fun assertRejected(raw: ByteArray, layout: Gen3PokeblockLayout, slot: Int = 0,
        entry: Gen3PokeblockEntry = Gen3PokeblockEntry(1, 20, 0, 0, 0, 0, 10)) {
        val original = raw.copyOf()
        var rejected = false
        try { Gen3PokeblockEditing.replace(raw, layout, slot, entry) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertArrayEquals(original, raw)
    }

    @Test
    fun allFortySlotsInBothLayoutsPreserveAlignmentAndUnrelatedBytes() {
        val entry = Gen3PokeblockEntry(14, 255, 128, 64, 32, 16, 255)
        for (layout in Gen3PokeblockLayout.values()) for (slot in 0 until 40) {
            val raw = section(layout)
            val original = raw.copyOf()
            val start = layout.caseOffset + slot * 8
            val edited = Gen3PokeblockEditing.replace(raw, layout, slot, entry)
            assertEquals(entry, Gen3PokeblockEditing.read(edited, layout)[slot])
            assertTrue(raw.indices.all { it in start..start + 6 || raw[it] == edited[it] })
            assertEquals(raw[start + 7], edited[start + 7])
            assertArrayEquals(original, raw)
        }
    }

    @Test
    fun independentEmeraldWireFixtureUsesEightByteStrideAndSevenFields() {
        val raw = section(Gen3PokeblockLayout.EMERALD)
        // Native slot 0 at SaveBlock1 +0x848: colour, five flavours, feel, unused alignment.
        val wire = byteArrayOf(1, 255.toByte(), 0, 128.toByte(), 64, 32, 99, 165.toByte())
        wire.copyInto(raw, 0x848)
        assertEquals(Gen3PokeblockEntry(1, 255, 0, 128, 64, 32, 99),
            Gen3PokeblockEditing.read(raw, Gen3PokeblockLayout.EMERALD)[0])
        val entry = Gen3PokeblockEntry(14, 1, 2, 3, 4, 5, 255)
        val expected = raw.copyOf().also { byteArrayOf(14, 1, 2, 3, 4, 5, 255.toByte()).copyInto(it, 0x848) }
        assertArrayEquals(expected, Gen3PokeblockEditing.replace(raw, Gen3PokeblockLayout.EMERALD, 0, entry))
        assertEquals(165, expected[0x84F].toInt() and 255)
    }

    @Test
    fun clearingSlotZerosOnlyItsGameplayFieldsAndDoesNotCompactCase() {
        for (layout in Gen3PokeblockLayout.values()) {
            val raw = section(layout)
            for (slot in listOf(0, 20, 39)) {
                Gen3PokeblockEntry(12, 2, 2, 2, 0, 0, 25).values().forEachIndexed { index, value ->
                    raw[layout.caseOffset + slot * 8 + index] = value.toByte()
                }
            }
            val start = layout.caseOffset + 20 * 8
            val edited = Gen3PokeblockEditing.replace(raw, layout, 20, Gen3PokeblockEntry())
            assertTrue((start..start + 6).all { edited[it] == 0.toByte() })
            assertTrue(raw.indices.all { it in start..start + 6 || raw[it] == edited[it] })
            val entries = Gen3PokeblockEditing.read(edited, layout)
            assertEquals(12, entries[0].color)
            assertEquals(0, entries[20].color)
            assertEquals(12, entries[39].color)
        }
    }

    @Test
    fun nativeColourIdsUnsignedFlavoursAndFeelRoundTripWithoutClamping() {
        assertEquals(0x7F8, Gen3PokeblockLayout.RUBY_SAPPHIRE.caseOffset)
        assertEquals(0x848, Gen3PokeblockLayout.EMERALD.caseOffset)
        assertEquals(15, Gen3PokeblockEditing.colorNames.size)
        assertEquals("Gold", Gen3PokeblockEditing.colorNames[14])
        for (color in 1..14) for (value in listOf(0, 1, 99, 128, 255)) {
            val raw = section(Gen3PokeblockLayout.RUBY_SAPPHIRE)
            val entry = Gen3PokeblockEntry(color, value, value, value, value, value, value)
            val edited = Gen3PokeblockEditing.replace(raw, Gen3PokeblockLayout.RUBY_SAPPHIRE, 39, entry)
            assertEquals(entry, Gen3PokeblockEditing.read(edited, Gen3PokeblockLayout.RUBY_SAPPHIRE)[39])
        }
    }

    @Test
    fun invalidValuesSlotsAndMalformedSourceCaseRejectWithoutMutation() {
        val layout = Gen3PokeblockLayout.EMERALD
        val raw = section(layout)
        for (slot in listOf(-1, 40, Int.MAX_VALUE)) assertRejected(raw, layout, slot)
        for (entry in listOf(Gen3PokeblockEntry(-1), Gen3PokeblockEntry(15),
            Gen3PokeblockEntry(0, spicy = 1), Gen3PokeblockEntry(1, dry = -1),
            Gen3PokeblockEntry(1, sour = 256), Gen3PokeblockEntry(1, feel = 256))) {
            assertRejected(raw, layout, entry = entry)
        }
        assertRejected(raw.copyOf(0xF7F), layout)
        assertRejected(raw.copyOf(0xF81), layout)
        assertRejected(raw.copyOf().also { it[0x848 + 39 * 8] = 15 }, layout)
        assertRejected(raw.copyOf().also { it[0x848 + 39 * 8 + 1] = 10 }, layout)
    }

    @Test
    fun unchangedSlotReturnsDetachedExactSectionIncludingUnusedBytes() {
        for (layout in Gen3PokeblockLayout.values()) {
            val raw = section(layout)
            val original = raw.copyOf()
            val edited = Gen3PokeblockEditing.replace(raw, layout, 39, Gen3PokeblockEntry())
            assertArrayEquals(original, edited)
            edited[0] = (edited[0].toInt() xor 1).toByte()
            assertArrayEquals(original, raw)
            assertEquals(40, Gen3PokeblockEditing.read(raw, layout).size)
        }
    }
}
