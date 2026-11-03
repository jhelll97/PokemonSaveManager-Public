package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspMenuSelectionReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0x7E9F8
    private fun put(b: ByteArray, o: Int, v: Long) { repeat(4) { b[o + it] = (v ushr (8 * it)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put(b, 0, layouts[layout].second.toLong()); put(b, 0x79B78, 2); b[0x79BDF] = title.toByte()
        repeat(8) { i -> put(b, base + i * 8, (i - 4).toLong()); put(b, base + i * 8 + 4, (i % 2).toLong()) }
        put(b, base + 0x40, -123456789); b[base - 1] = 0xA5.toByte(); b[base + 0x44] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllRevisionsKeepEveryNativeSlotAndSeparateLastSelectedId() {
        for (l in layouts.indices) for (t in 0..1) {
            val b = fixture(l, t); val before = b.copyOf(); val read = BdspMenuSelectionReadOnly.read(b)
            assertEquals(t, read.version.romCode); assertEquals(layouts[l].second.toLong(), read.revision.code)
            assertEquals(8, read.items.size); assertEquals(-123456789, read.lastSelectedMenuId)
            for (i in 0 until 8) {
                val item = read.item(i); val o = base + i * 8
                assertSame(item, read.items[i]); assertEquals(i, item.index); assertEquals(i - 4, item.menuId)
                assertEquals((i % 2).toLong(), item.rawNewWord); assertEquals(i % 2 == 1, item.isNew); assertTrue(item.hasCanonicalNewWord)
                assertEquals(before.copyOfRange(o, o + 8).map { it.toInt() and 255 }, item.rawBytes)
            }
            assertEquals(before.copyOfRange(base, base + 0x44).map { it.toInt() and 255 }, read.rawBytes)
            assertEquals(read.rawBytes.take(64), read.items.flatMap { it.rawBytes }); assertArrayEquals(before, b)
            assertEquals(165, b[base - 1].toInt() and 255); assertEquals(90, b[base + 0x44].toInt() and 255)
            for (index in listOf(-1, 8, Int.MIN_VALUE, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { read.item(index) }
        }
    }

    @Test fun fullWordBooleanDoesNotCollapseHighBitsOrUnknownFlags() {
        val words = listOf(0L, 1L, 2L, 0x100L, 0x101L, 0x10001L, 0x1000001L, 0x7FFFFFFFL, 0x80000000L, 0x80000001L, 0xFFFFFFFFL) +
            (0 until 32).map { 1L shl it }
        for (word in words) {
            val b = fixture(); repeat(8) { put(b, base + it * 8 + 4, word) }; seal(b); val before = b.copyOf()
            val read = BdspMenuSelectionReadOnly.read(b)
            for (item in read.items) {
                assertEquals(word, item.rawNewWord); assertEquals(word == 1L, item.isNew); assertEquals(word in 0L..1L, item.hasCanonicalNewWord)
                assertEquals(item.index - 4, item.menuId)
            }
            assertEquals(-123456789, read.lastSelectedMenuId); assertArrayEquals(before, b)
        }
    }

    @Test fun signedUnknownDuplicateAndZeroIdsRemainInNativeOrderWithoutMatchRequirements() {
        for (id in listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)) {
            val b = fixture(); repeat(8) { put(b, base + it * 8, id.toLong()) }; put(b, base + 0x40, id.toLong()); seal(b)
            val read = BdspMenuSelectionReadOnly.read(b); assertEquals(id, read.lastSelectedMenuId)
            assertEquals((0 until 8).toList(), read.items.map { it.index }); assertTrue(read.items.all { it.menuId == id })
        }
        val b = fixture(); b.fill(0, base, base + 0x44); put(b, base + 0x40, Int.MIN_VALUE.toLong()); seal(b)
        val read = BdspMenuSelectionReadOnly.read(b); assertEquals(8, read.items.size); assertTrue(read.items.all { it.menuId == 0 && !it.isNew })
        assertEquals(Int.MIN_VALUE, read.lastSelectedMenuId); assertFalse(read.items.any { it.menuId == read.lastSelectedMenuId })
    }

    @Test fun exactNativeLengthRevisionTitleAndWholeFileMd5GateAllReads() {
        for (l in layouts.indices) {
            val b = fixture(l)
            for (o in setOf(0, 4, base - 1, base, base + 3, base + 4, base + 7, base + 56, base + 63, base + 0x40, base + 0x43, base + 0x44, 0xE9818, b.lastIndex)) {
                val bad = b.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspMenuSelectionReadOnly.read(bad) }; assertNull(session(bad).bdspMenuSelection); assertArrayEquals(before, bad)
            }
            val title = b.copyOf(); title[0x79BDF] = 2; seal(title); val revision = b.copyOf(); put(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, b.copyOf(b.size - 1), b.copyOf(b.size + 1))) {
                assertThrows(IllegalArgumentException::class.java) { BdspMenuSelectionReadOnly.read(bad) }; assertNull(session(bad).bdspMenuSelection)
            }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4))) assertThrows(IllegalArgumentException::class.java) { BdspMenuSelectionReadOnly.read(bad) }
    }

    @Test fun independentMenuReaderLeavesStrictTrainerPartyAndDexRejectionsIntact() {
        val cases = listOf(fixture().also { put(it, 0x79B78, 6) }, fixture().also { put(it, 0x79BD4, 1000000) },
            fixture().also { it[0x79BD8] = 2 }, fixture().also { it[0x79BDD] = 9 }, fixture().also { it[0x79BE0] = 2 },
            fixture().also { it[0x79BB4] = 0; it[0x79BB5] = 0xD8.toByte() })
        for (b in cases) {
            b[0x14098 + 6 * 344] = 7; put(b, 0x7A328, 4); seal(b); val before = b.copyOf(); val s = session(b)
            assertTrue(s.isFormatVerified); assertNull(s.bdspTrainer); assertFalse(s.supports(SwitchReadCapability.TRAINER_PROFILE)); assertNull(s.bdspStorage); assertNull(s.bdspPokedex)
            assertNotNull(s.bdspMenuSelection); assertNull(s.bdspMenuSelectionUnavailableReason); assertEquals(-123456789, s.bdspMenuSelection!!.lastSelectedMenuId); assertArrayEquals(before, b)
        }
    }

    @Test fun immutableDetachedDataKeepsNativeIdentityEarlierReadersAndWriteLocks() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); val before = b.copyOf(); val s = session(b, hint); val read = s.bdspMenuSelection!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, s.profile.family); assertEquals(title, read.version.romCode)
            b.fill(0); s.snapshot.bytesCopy().fill(0); assertArrayEquals(before, s.snapshot.bytesCopy()); assertEquals(-4, read.item(0).menuId); assertEquals(-123456789, read.lastSelectedMenuId)
            for (list in listOf(read.items, read.rawBytes, read.item(0).rawBytes))
                assertThrows(UnsupportedOperationException::class.java) { (list as MutableList<*>).clear() }
            assertNotNull(s.bdspTrainer); assertNotNull(s.bdspInventory); assertNotNull(s.bdspGameOptions); assertNotNull(s.bdspRandomGroups); assertNotNull(s.bdspUnionRoom)
            assertTrue(s.isReadOnly); assertFalse(s.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspMenuSelection); assertNull(other.bdspMenuSelectionUnavailableReason)
    }
}
