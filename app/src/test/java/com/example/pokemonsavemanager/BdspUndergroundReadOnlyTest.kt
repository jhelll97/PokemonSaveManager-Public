package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspUndergroundReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private fun put32(b: ByteArray, o: Int, v: Long) { repeat(4) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun record(b: ByteArray, id: Int, count: Long, hide: Long = 0, favorite: Long = 0) {
        val o = 0x111BC + id * 12; put32(b, o, count); put32(b, o + 4, hide); put32(b, o + 8, favorite)
    }
    private fun fixture(layout: Int = 0, title: Int = 0, populated: Boolean = true): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put32(b, 0, layouts[layout].second.toLong()); b[0x79BDF] = title.toByte()
        if (populated) {
            record(b, 0, 0, favorite = 2); record(b, 1, 999, 1, 1); record(b, 10, 0x80000000L, 2, 2)
            record(b, 11, 0xFFFFFFFFL, 0x1000001L, 0x80000001L); record(b, 62, 0, 1)
            record(b, 370, 100, 1); record(b, 399, 1); record(b, 400, 999)
            record(b, 492, 99, favorite = 1); record(b, 924, 100); record(b, 925, 1)
            record(b, 939, 1); record(b, 940, 7); record(b, 998, Int.MAX_VALUE.toLong(), 0xFFFFFFFFL, 1)
        }
        seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesFourRevisionsPreserveAll999IndicesAndBoundaryWords() {
        for (layout in layouts.indices) for (title in 0..1) {
            val source = fixture(layout, title); val before = source.copyOf(); val ug = BdspUndergroundReadOnly.read(source)
            assertEquals(title, ug.version.romCode); assertEquals(layouts[layout].second.toLong(), ug.revision.code)
            assertEquals((0 until 999).toList(), ug.slots.map { it.undergroundId }); assertEquals(14, ug.recordedCount)
            assertEquals(listOf(0, 62, 940, 998), ug.entries(null).map { it.undergroundId })
            assertEquals(Int.MAX_VALUE, ug.slot(998).count); assertEquals(0xFFFFFFFFL, ug.slot(998).rawHideNewFlag)
            assertEquals(Int.MIN_VALUE, ug.slot(10).count); assertEquals(-1, ug.slot(11).count)
            assertEquals(0xFFFFFFFFL, ug.slot(11).rawCount); assertArrayEquals(before, source)
        }
    }

    @Test fun fullWordBooleansUseEqualityNotBitTestsAndNeverNormalizeResiduals() {
        val words = listOf(0L, 1L, 2L, 0x1000001L, 0x80000001L, 0xFFFFFFFFL)
        val b = fixture(populated = false)
        for ((i, word) in words.withIndex()) record(b, i + 1, word, word, word)
        seal(b); val before = b.copyOf(); val ug = BdspUndergroundReadOnly.read(b)
        for ((i, word) in words.withIndex()) {
            val e = ug.slot(i + 1); assertEquals(word.toInt(), e.count); assertEquals(word, e.rawCount)
            assertEquals(word, e.rawHideNewFlag); assertEquals(word, e.rawFavoriteFlag)
            assertEquals(word != 1L, e.isNew); assertEquals(word == 1L, e.isFavorite); assertEquals(word != 0L, e.isRecorded)
        }
        val residual = BdspUndergroundReadOnly.read(fixture()).slot(62)
        assertEquals(0, residual.count); assertTrue(residual.isRecorded); assertFalse(residual.isNew); assertNull(residual.catalogue)
        assertArrayEquals(before, b)
    }

    @Test fun catalogueUsesUndergroundNamespaceCompleteMappingsAndReadableSpecialStatues() {
        val ug = BdspUndergroundReadOnly.read(fixture(populated = false))
        assertEquals(631, ug.slots.count { it.catalogue != null })
        assertEquals(listOf(10, 152, 35, 434), BdspUndergroundKind.values().map { ug.entries(it, true).size })
        assertEquals(368, ug.entries(null, true).size)
        val labels = ug.slots.mapNotNull { it.catalogue }.joinToString("\n") {
            "${it.undergroundId}:${it.kind.name}:${it.nativeReferenceId}:${it.name}"
        }
        assertEquals("51f3e9464f7411ae32f5db1060e0cb89d2379dd5348d3134b747dc7871aabd27", MessageDigest.getInstance("SHA-256").digest(labels.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) })
        assertEquals("Red Sphere S", ug.slot(1).name); assertEquals(28, ug.slot(11).catalogue!!.nativeReferenceId)
        assertEquals("Digger Drill", ug.slot(399).name); assertEquals(1810, ug.slot(399).catalogue!!.nativeReferenceId)
        assertEquals("TM01", ug.slot(400).name); assertEquals(328, ug.slot(400).catalogue!!.nativeReferenceId)
        assertEquals("TM93", ug.slot(925).name); assertEquals(420, ug.slot(925).catalogue!!.nativeReferenceId)
        assertEquals("TM100", ug.slot(932).name); assertEquals(427, ug.slot(932).catalogue!!.nativeReferenceId)
        assertEquals("Square Pedestal XL", ug.slot(933).name); assertEquals(30, ug.slot(933).catalogue!!.nativeReferenceId)
        assertEquals("Giratina Origin Statue", ug.slot(939).name); assertEquals(434, ug.slot(939).catalogue!!.nativeReferenceId)
        assertTrue(ug.slot(924).name.endsWith("★")); assertEquals("Underground item #998", ug.slot(998).name)
        for (id in listOf(-1, 999, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { ug.slot(id) }
    }

    @Test fun emptyViewsAndReferenceLimitsDoNotClampSavedValues() {
        val empty = BdspUndergroundReadOnly.read(fixture(populated = false))
        assertEquals(0, empty.recordedCount); assertTrue(empty.entries(null).isEmpty())
        for (kind in BdspUndergroundKind.values()) assertTrue(empty.entries(kind).isEmpty())
        val ug = BdspUndergroundReadOnly.read(fixture())
        assertEquals(999, ug.slot(1).referenceMaxCount); assertFalse(ug.slot(1).hasUnusualMetadata)
        assertEquals(99, ug.slot(370).referenceMaxCount); assertTrue(ug.slot(370).hasUnusualMetadata); assertEquals(100, ug.slot(370).count)
        assertFalse(ug.slot(492).hasUnusualMetadata); assertTrue(ug.slot(924).hasUnusualMetadata); assertEquals(100, ug.slot(924).count)
        assertNull(ug.slot(998).referenceMaxCount); assertTrue(ug.slot(998).hasUnusualMetadata)
    }

    @Test fun wholeFileIntegrityNativeTitleRevisionAndLengthsPrecedeUndergroundReads() {
        for (layout in layouts.indices) {
            val good = fixture(layout)
            for (o in setOf(0, 4, 0x111BB, 0x111BC, 0x1408F, 0x14090, 0x79BDF, 0xE9818, 0xE9827, good.lastIndex)) {
                val bad = good.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspUndergroundReadOnly.read(bad) }
                assertNull(session(bad).bdspUnderground); assertArrayEquals(before, bad)
            }
            val wrongTitle = good.copyOf(); wrongTitle[0x79BDF] = 2; seal(wrongTitle)
            val wrongRevision = good.copyOf(); put32(wrongRevision, 0, 0x35); seal(wrongRevision)
            for (bad in listOf(wrongTitle, wrongRevision, good.copyOf(good.size - 1), good.copyOf(good.size + 1)))
                assertThrows(IllegalArgumentException::class.java) { BdspUndergroundReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4))) {
            assertThrows(IllegalArgumentException::class.java) { BdspUndergroundReadOnly.read(bad) }; assertNull(session(if (bad.isEmpty()) byteArrayOf(0) else bad).bdspUnderground)
        }
    }

    @Test fun detachedImmutableSnapshotsAndListsSurviveSourceChanges() {
        val source = fixture(); val before = source.copyOf(); val bound = session(source); val ug = bound.bdspUnderground!!
        source.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy())
        assertEquals(14, ug.recordedCount)
        assertThrows(UnsupportedOperationException::class.java) { (ug.slots as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (ug.entries(BdspUndergroundKind.SPHERE) as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (ug.entries(null, true) as MutableList<*>).clear() }
        assertEquals(0, BdspUndergroundReadOnly.read(fixture(populated = false)).recordedCount); assertEquals(14, ug.recordedCount)
    }

    @Test fun nativeIdentityOverridesHintsAndIndependentReadersDoNotHideUnderground() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); put32(b, 0x79B78, 0); b[0x14098 + 6 * 344] = 7; put32(b, 0x7A328, 4); seal(b)
            val before = b.copyOf(); val bound = session(b, hint)
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family)
            assertTrue(bound.isFormatVerified); assertNotNull(bound.bdspUnderground); assertNull(bound.bdspUndergroundUnavailableReason)
            assertEquals(14, bound.bdspUnderground!!.recordedCount); assertNotNull(bound.bdspInventory)
            assertNull(bound.bdspTrainer); assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex)
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled); assertArrayEquals(before, b)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspUnderground); assertNull(other.bdspUndergroundUnavailableReason)
    }
}
