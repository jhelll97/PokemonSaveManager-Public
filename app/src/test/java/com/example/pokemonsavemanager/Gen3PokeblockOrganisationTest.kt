package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3PokeblockOrganisationTest {
    private fun section(layout: Gen3PokeblockLayout): ByteArray =
        ByteArray(0xF80) { (it * 13 + 5).toByte() }.also { raw ->
            raw.fill(0, layout.caseOffset, layout.caseOffset + 320)
            for (slot in 0 until 40) raw[layout.caseOffset + slot * 8 + 7] = (slot + 128).toByte()
        }

    private fun put(raw: ByteArray, layout: Gen3PokeblockLayout, slot: Int, entry: Gen3PokeblockEntry) {
        entry.values().forEachIndexed { index, value -> raw[layout.caseOffset + slot * 8 + index] = value.toByte() }
    }

    private fun assertPreserved(raw: ByteArray, result: ByteArray, layout: Gen3PokeblockLayout) {
        assertTrue(raw.indices.all { index ->
            (index in layout.caseOffset until layout.caseOffset + 320 && (index - layout.caseOffset) % 8 < 7) ||
                raw[index] == result[index]
        })
        assertEquals(Gen3PokeblockEditing.read(raw, layout).groupingBy { it }.eachCount(),
            Gen3PokeblockEditing.read(result, layout).groupingBy { it }.eachCount())
    }

    @Test
    fun compactClosesGapsWhileRetainingBlockAndDuplicateOrder() {
        for (layout in Gen3PokeblockLayout.values()) {
            val raw = section(layout)
            val first = Gen3PokeblockEntry(14, 255, 1, 2, 3, 4, 255)
            val last = Gen3PokeblockEntry(1, 10, 20, 30, 40, 50, 99)
            put(raw, layout, 2, first); put(raw, layout, 20, first); put(raw, layout, 39, last)
            val original = raw.copyOf()
            val order = Gen3PokeblockEditing.organisationOrder(raw, layout, Gen3PokeblockOrganisation.COMPACT)
            assertEquals(listOf(2, 20, 39), order.take(3))
            val result = Gen3PokeblockEditing.reorder(raw, layout, order)
            assertEquals(listOf(first, first, last), Gen3PokeblockEditing.read(result, layout).take(3))
            assertTrue(Gen3PokeblockEditing.read(result, layout).drop(3).all { it.color == 0 })
            assertPreserved(raw, result, layout)
            assertArrayEquals(original, raw)
        }
    }

    @Test
    fun levelSortUsesAllFiveUnsignedFlavoursWithStableTiesAndEmptyTail() {
        val layout = Gen3PokeblockLayout.EMERALD
        val raw = section(layout)
        put(raw, layout, 1, Gen3PokeblockEntry(1, spicy = 128, feel = 255))
        put(raw, layout, 5, Gen3PokeblockEntry(2, sour = 255))
        put(raw, layout, 9, Gen3PokeblockEntry(3, dry = 128))
        put(raw, layout, 12, Gen3PokeblockEntry(4, sweet = 0))
        val order = Gen3PokeblockEditing.organisationOrder(raw, layout, Gen3PokeblockOrganisation.LEVEL_DESCENDING)
        assertEquals(listOf(5, 1, 9, 12), order.take(4))
        assertPreserved(raw, Gen3PokeblockEditing.reorder(raw, layout, order), layout)
    }

    @Test
    fun colourSortIsStableAcrossFullCasesAndPreservesEveryField() {
        for (layout in Gen3PokeblockLayout.values()) {
            val raw = section(layout)
            for (slot in 0 until 40) put(raw, layout, slot,
                Gen3PokeblockEntry(14 - slot % 14, slot, 255 - slot, 128, 0, 255, slot + 100))
            val order = Gen3PokeblockEditing.organisationOrder(raw, layout, Gen3PokeblockOrganisation.COLOUR)
            val expected = (0 until 40).sortedWith(compareBy<Int> { 14 - it % 14 }.thenBy { it })
            assertEquals(expected, order)
            assertPreserved(raw, Gen3PokeblockEditing.reorder(raw, layout, order), layout)
        }
    }

    @Test
    fun fixedWireReorderKeepsAlignmentAtPhysicalSlot() {
        val raw = section(Gen3PokeblockLayout.RUBY_SAPPHIRE)
        byteArrayOf(1, 10, 20, 30, 40, 50, 99, 165.toByte()).copyInto(raw, 0x7F8)
        byteArrayOf(14, 255.toByte(), 2, 3, 4, 5, 255.toByte(), 90).copyInto(raw, 0x800)
        val expected = raw.copyOf()
        raw.copyOfRange(0x800, 0x807).copyInto(expected, 0x7F8)
        raw.copyOfRange(0x7F8, 0x7FF).copyInto(expected, 0x800)
        val order = listOf(1, 0) + (2 until 40)
        assertArrayEquals(expected, Gen3PokeblockEditing.reorder(raw, Gen3PokeblockLayout.RUBY_SAPPHIRE, order))
        assertEquals(165, expected[0x7FF].toInt() and 255)
        assertEquals(90, expected[0x807].toInt() and 255)
    }

    @Test
    fun invalidPermutationsAndDamagedCasesRejectWithoutMutation() {
        val layout = Gen3PokeblockLayout.EMERALD
        val valid = section(layout)
        val identity = (0 until 40).toList()
        val orders = listOf(identity.drop(1), identity + 0, List(40) { 0 },
            identity.toMutableList().also { it[39] = -1 }, identity.toMutableList().also { it[39] = 40 })
        for (order in orders) {
            val original = valid.copyOf()
            var rejected = false
            try { Gen3PokeblockEditing.reorder(valid, layout, order) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected); assertArrayEquals(original, valid)
        }
        for (raw in listOf(valid.copyOf(0xF7F), valid.copyOf(0xF81),
            valid.copyOf().also { it[0x848 + 39 * 8] = 15 },
            valid.copyOf().also { it[0x848 + 39 * 8 + 1] = 1 })) {
            val original = raw.copyOf()
            var rejected = false
            try { Gen3PokeblockEditing.organisationOrder(raw, layout, Gen3PokeblockOrganisation.COMPACT) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected); assertArrayEquals(original, raw)
        }
    }

    @Test
    fun allModesAreIdempotentAndNoOpsAreDetachedIncludingEmptyCase() {
        for (layout in Gen3PokeblockLayout.values()) for (mode in Gen3PokeblockOrganisation.values()) {
            val raw = section(layout)
            val result = Gen3PokeblockEditing.reorder(raw, layout, Gen3PokeblockEditing.organisationOrder(raw, layout, mode))
            assertArrayEquals(raw, result)
            result[0] = (result[0].toInt() xor 1).toByte()
            assertTrue(raw[0] != result[0])
            for (slot in 0 until 40 step 3) put(raw, layout, slot, Gen3PokeblockEntry(14 - slot % 14, sour = slot))
            val once = Gen3PokeblockEditing.reorder(raw, layout, Gen3PokeblockEditing.organisationOrder(raw, layout, mode))
            val twice = Gen3PokeblockEditing.reorder(once, layout, Gen3PokeblockEditing.organisationOrder(once, layout, mode))
            assertArrayEquals(once, twice)
            assertPreserved(raw, once, layout)
        }
    }
}
