package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspUndergroundNpcReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0x9A89C + 0x273C
    private val values = listOf(0, 1, 2, 255, 2)
    private fun put(b: ByteArray, o: Int, v: Long) { repeat(4) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put(b, 0, layouts[layout].second.toLong()); put(b, 0x79B78, 2); b[0x79BDF] = title.toByte()
        for (index in 0 until 100) b[base + index] = values[index % 5].toByte()
        b[base - 1] = 0xA5.toByte(); b[base + 100] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllRevisionsPreserveAll100EntriesAndExactNativeBoundaries() {
        for (layout in layouts.indices) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val read = BdspUndergroundNpcReadOnly.read(b)
            assertEquals(title, read.version.romCode); assertEquals(layouts[layout].second.toLong(), read.revision.code)
            assertEquals(100, read.entries.size); assertEquals(100, read.rawNpcBytes.size); assertEquals(80, read.recordedEntryCount)
            for (index in 0 until 100) { val entry = read.entry(index); assertEquals(index, entry.index); assertEquals(values[index % 5], entry.value) }
            assertEquals(b.copyOfRange(base, base + 100).map { it.toInt() and 255 }, read.rawNpcBytes)
            assertEquals(0xA5, b[base - 1].toInt() and 255); assertEquals(0x5A, b[base + 100].toInt() and 255); assertArrayEquals(before, b)
        }
    }

    @Test fun allByteValuesCountAsNonzeroWithoutBooleanNormalizationOrDeduplicatingRepeatedValues() {
        for (value in 0..255) {
            val b = fixture(); b.fill(value.toByte(), base, base + 100); seal(b)
            val before = b.copyOf(); val read = BdspUndergroundNpcReadOnly.read(b)
            assertEquals(if (value == 0) 0 else 100, read.recordedEntryCount)
            assertEquals(List(100) { value }, read.rawNpcBytes); assertEquals(List(100) { value }, read.entries.map { it.value })
            assertEquals(if (value == 0) 0 else 100, read.filtered(BdspUndergroundNpcFilter.NONZERO).size)
            assertEquals(if (value == 0) 100 else 0, read.filtered(BdspUndergroundNpcFilter.ZERO).size); assertArrayEquals(before, b)
        }
    }

    @Test fun filtersKeepNativeIndicesOrderAndRepeatedValuesWithoutChangingTheSource() {
        val b = fixture(); b.fill(0, base, base + 100)
        for ((index, value) in mapOf(0 to 1, 1 to 1, 49 to 2, 99 to 255)) b[base + index] = value.toByte(); seal(b)
        val before = b.copyOf(); val read = BdspUndergroundNpcReadOnly.read(b)
        assertEquals(4, read.recordedEntryCount)
        assertEquals((0..99).toList(), read.filtered(BdspUndergroundNpcFilter.ALL).map { it.index })
        assertEquals(listOf(0, 1, 49, 99), read.filtered(BdspUndergroundNpcFilter.NONZERO).map { it.index })
        assertEquals(listOf(1, 1, 2, 255), read.filtered(BdspUndergroundNpcFilter.NONZERO).map { it.value })
        assertEquals((0..99).filter { it !in listOf(0, 1, 49, 99) }, read.filtered(BdspUndergroundNpcFilter.ZERO).map { it.index })
        assertArrayEquals(before, b)
    }

    @Test fun nativeIntegrityIdentityAndLengthPrecedeExtractionAndInvalidIndicesReject() {
        for (layout in layouts.indices) {
            val source = fixture(layout)
            for (o in setOf(0, 4, base - 1, base, base + 1, base + 19, base + 20, base + 98, base + 99, base + 100, 0xE9818, source.lastIndex)) {
                val bad = source.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspUndergroundNpcReadOnly.read(bad) }; assertNull(session(bad).bdspUndergroundNpc); assertArrayEquals(before, bad)
            }
            val title = source.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = source.copyOf(); put(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, source.copyOf(source.size - 1), source.copyOf(source.size + 1)))
                assertThrows(IllegalArgumentException::class.java) { BdspUndergroundNpcReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4))) assertThrows(IllegalArgumentException::class.java) { BdspUndergroundNpcReadOnly.read(bad) }
        val read = BdspUndergroundNpcReadOnly.read(fixture())
        for (index in listOf(-1, 100, Int.MIN_VALUE, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { read.entry(index) }
    }

    @Test fun independentNpcReaderDoesNotRelaxTrainerPartyOrDexGates() {
        val badCases = listOf(fixture().also { put(it, 0x79B78, 6) }, fixture().also { put(it, 0x79BD4, 1000000) },
            fixture().also { it[0x79BD8] = 2 }, fixture().also { it[0x79BDD] = 9 }, fixture().also { it[0x79BE0] = 2 },
            fixture().also { it[0x79BB4] = 0; it[0x79BB5] = 0xD8.toByte() })
        for (b in badCases) {
            b[0x14098 + 6 * 344] = 7; put(b, 0x7A328, 4); seal(b); val before = b.copyOf(); val bound = session(b)
            assertTrue(bound.isFormatVerified); assertNull(bound.bdspTrainer); assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex); assertNotNull(bound.bdspUndergroundNpc); assertNull(bound.bdspUndergroundNpcUnavailableReason)
            assertEquals(80, bound.bdspUndergroundNpc!!.recordedEntryCount); assertNotNull(bound.bdspSecretBase); assertNotNull(bound.bdspUnderground); assertArrayEquals(before, b)
        }
    }

    @Test fun detachedImmutableRecordsUseNativeIdentityAndPreserveEarlierViewsAndWritingLocks() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); val before = b.copyOf(); val bound = session(b, hint); val read = bound.bdspUndergroundNpc!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family); assertEquals(title, read.version.romCode)
            b.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy()); assertEquals(80, read.recordedEntryCount)
            assertEquals(2, read.entry(99).value); assertEquals(255, read.entry(98).value)
            assertThrows(UnsupportedOperationException::class.java) { (read.entries as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.rawNpcBytes as MutableList<*>).clear() }
            for (filter in BdspUndergroundNpcFilter.values()) assertThrows(UnsupportedOperationException::class.java) { (read.filtered(filter) as MutableList<*>).clear() }
            assertNotNull(bound.bdspTrainer); assertNotNull(bound.bdspSecretBase); assertNotNull(bound.bdspBerryPlots); assertNotNull(bound.bdspEncounter)
            assertNotNull(bound.bdspUnderground); assertNotNull(bound.bdspUndergroundActivity); assertNotNull(bound.bdspBattleTower)
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspUndergroundNpc); assertNull(other.bdspUndergroundNpcUnavailableReason)
    }
}
