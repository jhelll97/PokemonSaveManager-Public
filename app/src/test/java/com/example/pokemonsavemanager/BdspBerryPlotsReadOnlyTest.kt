package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspBerryPlotsReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0x94DA8
    private fun put(b: ByteArray, o: Int, v: Long, count: Int = 4) { repeat(count) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put(b, 0, layouts[layout].second.toLong()); put(b, 0x79B78, 2); b[0x79BDF] = title.toByte()
        for (index in 0 until 128) for (byte in 0 until 16) b[base + index * 16 + byte] = (index * 17 + byte * 29 + layout + title).toByte()
        put(b, base + 0x800, Long.MIN_VALUE + 1, 8)
        b[base - 1] = 0xA5.toByte(); b[base + 0x808] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllRevisionsPreserveAll128RecordsAndExactBlockBoundaries() {
        for (layout in layouts.indices) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val read = BdspBerryPlotsReadOnly.read(b)
            assertEquals(title, read.version.romCode); assertEquals(layouts[layout].second.toLong(), read.revision.code)
            assertEquals(Long.MIN_VALUE + 1, read.lastUpdateMinutes); assertEquals(128, read.plots.size); assertEquals(2056, read.rawBerryBytes.size)
            for (index in 0 until 128) {
                val plot = read.plot(index); assertEquals(index, plot.index); assertEquals(16, plot.rawBytes.size)
                assertEquals((0 until 16).map { (index * 17 + it * 29 + layout + title) and 255 }, plot.rawBytes)
                assertEquals(b.copyOfRange(base + index * 16, base + (index + 1) * 16).map { it.toInt() and 255 }, plot.rawBytes)
            }
            assertEquals(b.copyOfRange(base, base + 2056).map { it.toInt() and 255 }, read.rawBerryBytes)
            assertEquals(0xA5, b[base - 1].toInt() and 255); assertEquals(0x5A, b[base + 2056].toInt() and 255); assertArrayEquals(before, b)
        }
    }

    @Test fun signedUpdateMinutesPreserveExtremesAndZeroWithoutClockConversionOrPlotOverlap() {
        for (update in listOf(Long.MIN_VALUE, Long.MIN_VALUE + 1, -1L, 0L, 1L, Long.MAX_VALUE)) {
            val b = fixture(); put(b, base + 0x800, update, 8); seal(b)
            val before = b.copyOf(); val read = BdspBerryPlotsReadOnly.read(b)
            assertEquals(update, read.lastUpdateMinutes)
            assertEquals(b.copyOfRange(base + 0x7F0, base + 0x800).map { it.toInt() and 255 }, read.plot(127).rawBytes)
            assertEquals(b.copyOfRange(base + 0x800, base + 0x808).map { it.toInt() and 255 }, read.rawBerryBytes.takeLast(8)); assertArrayEquals(before, b)
        }
    }

    @Test fun everyByteValueAndZeroFilledPlotsRemainVisibleWithoutFieldOrEmptyStateAssumptions() {
        for (value in 0..255) {
            val b = fixture(); b.fill(value.toByte(), base, base + 0x800); seal(b)
            val before = b.copyOf(); val read = BdspBerryPlotsReadOnly.read(b)
            assertEquals((0..127).toList(), read.plots.map { it.index })
            for (plot in read.plots) assertEquals(List(16) { value }, plot.rawBytes)
            assertEquals(Long.MIN_VALUE + 1, read.lastUpdateMinutes); assertArrayEquals(before, b)
        }
        val b = fixture(); b.fill(0, base, base + 0x808); seal(b)
        val before = b.copyOf(); val read = BdspBerryPlotsReadOnly.read(b)
        assertEquals(128, read.plots.size); assertEquals(0L, read.lastUpdateMinutes); assertEquals(List(2056) { 0 }, read.rawBerryBytes); assertArrayEquals(before, b)
    }

    @Test fun nativeIntegrityIdentityAndLengthPrecedeExtractionAndInvalidIndicesReject() {
        for (layout in layouts.indices) {
            val source = fixture(layout)
            val offsets = setOf(0, 4, base - 1, base, base + 15, base + 16, base + 0x7EF, base + 0x7F0, base + 0x7FF, base + 0x808, 0xE9818, source.lastIndex) + (0..7).map { base + 0x800 + it }
            for (o in offsets) {
                val bad = source.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspBerryPlotsReadOnly.read(bad) }; assertNull(session(bad).bdspBerryPlots); assertArrayEquals(before, bad)
            }
            val title = source.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = source.copyOf(); put(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, source.copyOf(source.size - 1), source.copyOf(source.size + 1)))
                assertThrows(IllegalArgumentException::class.java) { BdspBerryPlotsReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4))) assertThrows(IllegalArgumentException::class.java) { BdspBerryPlotsReadOnly.read(bad) }
        val read = BdspBerryPlotsReadOnly.read(fixture())
        for (index in listOf(-1, 128, Int.MIN_VALUE, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { read.plot(index) }
    }

    @Test fun independentBerryReaderDoesNotRelaxTrainerPartyOrDexGates() {
        val badCases = listOf(fixture().also { put(it, 0x79B78, 6) }, fixture().also { put(it, 0x79BD4, 1000000) },
            fixture().also { it[0x79BD8] = 2 }, fixture().also { it[0x79BDD] = 9 }, fixture().also { it[0x79BE0] = 2 },
            fixture().also { it[0x79BB4] = 0; it[0x79BB5] = 0xD8.toByte() })
        for (b in badCases) {
            b[0x14098 + 6 * 344] = 7; put(b, 0x7A328, 4); seal(b); val before = b.copyOf(); val bound = session(b)
            assertTrue(bound.isFormatVerified); assertNull(bound.bdspTrainer); assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex); assertNotNull(bound.bdspBerryPlots); assertNull(bound.bdspBerryPlotsUnavailableReason)
            assertEquals(128, bound.bdspBerryPlots!!.plots.size); assertNotNull(bound.bdspEncounter); assertNotNull(bound.bdspHoneyTrees); assertArrayEquals(before, b)
        }
    }

    @Test fun detachedImmutableRecordsUseNativeIdentityAndPreserveEarlierViewsAndWritingLocks() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); val before = b.copyOf(); val bound = session(b, hint); val read = bound.bdspBerryPlots!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family); assertEquals(title, read.version.romCode)
            b.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy())
            assertEquals(Long.MIN_VALUE + 1, read.lastUpdateMinutes); assertEquals(before.copyOfRange(base + 0x7F0, base + 0x800).map { it.toInt() and 255 }, read.plot(127).rawBytes)
            assertThrows(UnsupportedOperationException::class.java) { (read.plots as MutableList<*>).clear() }
            for (plot in read.plots) assertThrows(UnsupportedOperationException::class.java) { (plot.rawBytes as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.rawBerryBytes as MutableList<*>).clear() }
            assertNotNull(bound.bdspTrainer); assertNotNull(bound.bdspEncounter); assertNotNull(bound.bdspHoneyTrees); assertNotNull(bound.bdspBattleTrainerStatus)
            assertNotNull(bound.bdspAdditionalRecords); assertNotNull(bound.bdspRecordStatistics); assertNotNull(bound.bdspUndergroundActivity); assertNotNull(bound.bdspBattleTower)
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspBerryPlots); assertNull(other.bdspBerryPlotsUnavailableReason)
    }
}
