package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspPoffinReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0x955B0
    private fun put32(b: ByteArray, o: Int, v: Long) { repeat(4) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun record(b: ByteArray, index: Int, type: Int, level: Int = 0, smooth: Int = 0, flag: Long = 0) {
        val o = base + index * 16; b[o] = type.toByte(); b[o + 1] = level.toByte(); b[o + 2] = smooth.toByte(); b[o + 3] = (0x80 + index).toByte()
        put32(b, o + 4, flag); repeat(5) { b[o + 8 + it] = ((index + it) * 31).toByte() }
        b[o + 13] = 255.toByte(); b[o + 14] = (0x40 + index).toByte(); b[o + 15] = 0xAA.toByte()
    }
    private fun fixture(layout: Int = 0, title: Int = 0, populated: Boolean = true): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put32(b, 0, layouts[layout].second.toLong()); b[0x79BDF] = title.toByte(); repeat(100) { b[base + it * 16] = 255.toByte() }
        if (populated) {
            val flags = listOf(0L, 1L, 2L, 0x1000001L, 0x80000001L, 0xFFFFFFFFL)
            repeat(30) { record(b, it, it, (it * 7) and 255, 255 - it, flags[it % flags.size]) }
            record(b, 30, 30, 255, 255, 1); record(b, 31, 254, 0, 1, 2)
            record(b, 32, 255, 255, 255, 1); record(b, 99, 255, 1, 5, 0xFFFFFFFFL)
        }
        put32(b, base + 0x640, 0xFFFFFFFFL); b[base - 1] = 0xA5.toByte(); b[base + 0x644] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesFourRevisionsPreserve100NativeSlotsCookingCountFlavorsAndEveryRawByte() {
        for (layout in layouts.indices) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val poffins = BdspPoffinReadOnly.read(b)
            assertEquals(title, poffins.version.romCode); assertEquals(layouts[layout].second.toLong(), poffins.revision.code)
            assertEquals((0 until 100).toList(), poffins.slots.map { it.index }); assertEquals((1..100).toList(), poffins.slots.map { it.position })
            assertEquals(30, poffins.knownPoffinCount); assertEquals(2, poffins.unknownTypeCount); assertEquals(68, poffins.emptyCount)
            assertEquals((0..31).toList(), poffins.entries().map { it.index }); assertEquals(100, poffins.entries(true).size)
            assertEquals(-1, poffins.cookingCount); assertEquals(0xFFFFFFFFL, poffins.rawCookingCount)
            assertEquals(b.copyOfRange(base, base + 0x644).map { it.toInt() and 255 }, poffins.rawBytes)
            for (i in 0..29) {
                val slot = poffins.slot(i); assertEquals(i, slot.typeId); assertEquals((i * 7) and 255, slot.level); assertEquals(255 - i, slot.smoothness)
                assertEquals((0..4).map { ((i + it) * 31) and 255 }, slot.flavors)
                assertEquals(b.copyOfRange(base + i * 16, base + (i + 1) * 16).map { it.toInt() and 255 }, slot.rawBytes)
                assertEquals(0x80 + i, slot.rawBytes[3]); assertEquals(listOf(255, 0x40 + i, 0xAA), slot.rawBytes.subList(13, 16))
            }
            assertTrue(poffins.slot(99).isEmpty); assertEquals(1, poffins.slot(99).level); assertEquals(0xFFFFFFFFL, poffins.slot(99).rawNewFlag)
            assertArrayEquals(before, b); assertEquals(0xA5, b[base - 1].toInt() and 255); assertEquals(0x5A, b[base + 0x644].toInt() and 255)
        }
    }

    @Test fun all256TypeIdsUseZeroBasedNamesOnlyFfMeansEmptyAndUnknownsNeverFallBackToEmpty() {
        val names = (0..29).joinToString("\n") { "$it:${BdspPoffinNames.name(it)}" }
        assertEquals("6d8339ed2c9c32d1d00d27bd8a308639031db1c69e4672ab5f829f3b8634edfb", MessageDigest.getInstance("SHA-256").digest(names.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) })
        assertEquals("Spicy Poffin", BdspPoffinNames.name(0)); assertEquals("Super Mild Poffin", BdspPoffinNames.name(29))
        for (first in listOf(0, 100, 200)) {
            val b = fixture(populated = false); val count = minOf(100, 256 - first)
            repeat(count) { b[base + it * 16] = (first + it).toByte() }; seal(b)
            val poffins = BdspPoffinReadOnly.read(b)
            repeat(count) { i ->
                val type = first + i; val slot = poffins.slot(i)
                assertEquals(type, slot.typeId); assertEquals(type == 255, slot.isEmpty); assertEquals(type in 0..29, slot.isKnownType)
                if (type in 30..254) assertEquals("Unknown Poffin type #$type", slot.name)
            }
        }
        val zero = fixture(populated = false); zero.fill(0, base, base + 0x640); seal(zero)
        val allSpicy = BdspPoffinReadOnly.read(zero); assertEquals(100, allSpicy.knownPoffinCount); assertEquals(0, allSpicy.emptyCount)
        assertTrue(allSpicy.slots.all { it.name == "Spicy Poffin" && it.level == 0 })
        val empty = BdspPoffinReadOnly.read(fixture(populated = false)); assertEquals(100, empty.emptyCount); assertTrue(empty.entries().isEmpty())
        assertEquals("Empty slot", empty.slot(0).name); assertEquals(100, empty.entries(true).size)
    }

    @Test fun fullWordNewFlagsByteExtremaAndSignedCookingWordsNeverNormalizeOrSort() {
        for (word in listOf(0L, 1L, 2L, 0x1000001L, 0x80000001L, 0xFFFFFFFFL)) {
            val b = fixture(populated = false)
            record(b, 0, 29, 255, 255, word); record(b, 1, 255, 255, 255, word); record(b, 2, 0, 0, 0, word)
            seal(b); val before = b.copyOf(); val poffins = BdspPoffinReadOnly.read(b)
            assertEquals(listOf(0, 2), poffins.entries().map { it.index }); assertEquals(listOf(29, 255, 0), poffins.slots.take(3).map { it.typeId })
            for (i in 0..2) {
                val slot = poffins.slot(i); assertEquals(word, slot.rawNewFlag); assertEquals(word == 1L, slot.isNew); assertEquals(word in 0L..1L, slot.hasCanonicalNewFlag)
            }
            assertEquals(255, poffins.slot(0).level); assertEquals(255, poffins.slot(0).smoothness); assertTrue(poffins.slot(1).isEmpty); assertEquals(255, poffins.slot(1).level)
            assertArrayEquals(before, b)
        }
        for (word in listOf(0L, 1L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
            val b = fixture(); put32(b, base + 0x640, word); seal(b); val poffins = BdspPoffinReadOnly.read(b)
            assertEquals(word.toInt(), poffins.cookingCount); assertEquals(word, poffins.rawCookingCount)
        }
    }

    @Test fun existingWholeFileGateRejectsCorruptionTitleRevisionAndLengthBeforeReadingTheCase() {
        for (layout in layouts.indices) {
            val source = fixture(layout)
            for (o in setOf(0, 4, base - 1, base, base + 15, base + 99 * 16, base + 0x63F, base + 0x640, base + 0x643, base + 0x644, 0x79BDF, 0xE9818, source.lastIndex)) {
                val bad = source.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspPoffinReadOnly.read(bad) }; assertNull(session(bad).bdspPoffins); assertArrayEquals(before, bad)
            }
            val title = source.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = source.copyOf(); put32(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, source.copyOf(source.size - 1), source.copyOf(source.size + 1)))
                assertThrows(IllegalArgumentException::class.java) { BdspPoffinReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4))) assertThrows(IllegalArgumentException::class.java) { BdspPoffinReadOnly.read(bad) }
    }

    @Test fun detachedSnapshotsAllSlotListsFlavorsAndRawBytesAreImmutableAndIndicesAreBounded() {
        val source = fixture(); val before = source.copyOf(); val bound = session(source); val poffins = bound.bdspPoffins!!
        source.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy()); assertEquals(30, poffins.knownPoffinCount)
        for (list in listOf(poffins.slots, poffins.entries(), poffins.entries(true), poffins.rawBytes, poffins.slot(0).flavors, poffins.slot(0).rawBytes))
            assertThrows(UnsupportedOperationException::class.java) { (list as MutableList<*>).clear() }
        for (i in listOf(-1, 100, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { poffins.slot(i) }
        assertEquals(100, BdspPoffinReadOnly.read(fixture(populated = false)).emptyCount); assertEquals(68, poffins.emptyCount)
    }

    @Test fun nativeIdentityIndependentSectionsAndWriteLocksSurviveMalformedTrainerPartyAndDex() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val source = fixture(title = title); put32(source, 0x79B78, 0); source[0x14098 + 6 * 344] = 7; put32(source, 0x7A328, 4); seal(source)
            val before = source.copyOf(); val bound = session(source, hint)
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family); assertTrue(bound.isFormatVerified)
            assertNull(bound.bdspTrainer); assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex)
            assertNotNull(bound.bdspPoffins); assertNull(bound.bdspPoffinsUnavailableReason); assertEquals(30, bound.bdspPoffins!!.knownPoffinCount)
            assertNotNull(bound.bdspInventory); assertNotNull(bound.bdspUnderground); assertNotNull(bound.bdspNursery); assertNotNull(bound.bdspBattleTower)
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled); assertArrayEquals(before, source)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspPoffins); assertNull(other.bdspPoffinsUnavailableReason)
    }
}
