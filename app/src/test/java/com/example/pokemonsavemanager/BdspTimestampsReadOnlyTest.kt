package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspTimestampsReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0x95DAC
    private fun put32(b: ByteArray, o: Int, v: Long) { repeat(4) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun put64(b: ByteArray, o: Int, v: Long) { repeat(8) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put32(b, 0, layouts[layout].second.toLong()); put32(b, 0x79B78, 2); b[0x79BDF] = title.toByte()
        // Distinct adjacent records establish native ordering independently of enum names.
        put32(b, 0x95DAC, 0xFFFFFFFFL); put64(b, 0x95DB0, 0)
        put32(b, 0x95DB8, 0x80000000L); put64(b, 0x95DBC, 116444736000000000L)
        put32(b, 0x95DC4, 7); put64(b, 0x95DC8, 2650467743999999999L)
        put32(b, 0x95DD0, 0); put64(b, 0x95DD4, -1)
        for (i in 0x30 until 0x138) b[base + i] = (i * 37 + 11).toByte()
        b[base - 1] = 0xA5.toByte(); b[base + 0x138] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllRevisionsRetainFourDistinctRecordsAndEntire312ByteBlock() {
        for (layout in layouts.indices) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val read = BdspTimestampsReadOnly.read(b)
            assertEquals(title, read.version.romCode); assertEquals(layouts[layout].second.toLong(), read.revision.code)
            assertEquals(listOf("Start", "Latest save", "Penalty timeout", "Last daily event"), read.entries.map { it.kind.displayName })
            assertEquals(listOf(0xFFFFFFFFL, 0x80000000L, 7L, 0L), read.entries.map { it.count })
            assertEquals(listOf(0L, 116444736000000000L, 2650467743999999999L, -1L), read.entries.map { it.fileTimeTicks })
            assertEquals("1601-01-01T00:00:00Z", read.entry(BdspTimestampKind.START).utcTimestamp)
            assertEquals("1970-01-01T00:00:00Z", read.entry(BdspTimestampKind.LATEST_SAVE).utcTimestamp)
            assertEquals("9999-12-31T23:59:59.999999900Z", read.entry(BdspTimestampKind.PENALTY_TIMEOUT).utcTimestamp)
            assertNull(read.entry(BdspTimestampKind.LAST_DAILY_EVENT).utcTimestamp)
            assertEquals(b.copyOfRange(base, base + 0x138).map { it.toInt() and 255 }, read.rawSystemBytes)
            assertEquals(0xA5, b[base - 1].toInt() and 255); assertEquals(0x5A, b[base + 0x138].toInt() and 255)
            assertArrayEquals(before, b)
        }
    }

    @Test fun utcFileTimeBoundariesRetain100NanosecondsWithoutOverflowClampingOrSentinelGuessing() {
        val vectors = listOf(
            0L to "1601-01-01T00:00:00Z", 1L to "1601-01-01T00:00:00.000000100Z",
            9999999L to "1601-01-01T00:00:00.999999900Z", 10000000L to "1601-01-01T00:00:01Z",
            116444735999999999L to "1969-12-31T23:59:59.999999900Z", 116444736000000000L to "1970-01-01T00:00:00Z",
            116444736001234567L to "1970-01-01T00:00:00.123456700Z",
            2650467743999999998L to "9999-12-31T23:59:59.999999800Z", 2650467743999999999L to "9999-12-31T23:59:59.999999900Z",
            2650467744000000000L to null, Long.MAX_VALUE to null, -1L to null, Long.MIN_VALUE to null)
        for (kind in BdspTimestampKind.values()) for ((ticks, expected) in vectors) {
            val b = fixture(); put64(b, base + kind.ordinal * 12 + 4, ticks); seal(b)
            val before = b.copyOf(); val entry = BdspTimestampsReadOnly.read(b).entry(kind)
            assertEquals(ticks, entry.fileTimeTicks); assertEquals(expected, entry.utcTimestamp); assertArrayEquals(before, b)
        }
    }

    @Test fun invalidDatesNeverHideOtherEntriesAndCountsAreUnsignedSavedValues() {
        val b = fixture(); put64(b, 0x95DB0, Long.MIN_VALUE); put64(b, 0x95DC8, Long.MAX_VALUE); seal(b)
        val before = b.copyOf(); val read = session(b).bdspTimestamps!!
        assertEquals(4, read.entries.size); assertNull(read.entries[0].utcTimestamp); assertNull(read.entries[2].utcTimestamp)
        assertEquals("1970-01-01T00:00:00Z", read.entries[1].utcTimestamp); assertEquals(-1L, read.entries[3].fileTimeTicks)
        for (count in listOf(0L, 1L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
            for (o in listOf(0x95DAC, 0x95DB8, 0x95DC4, 0x95DD0)) put32(b, o, count)
            seal(b); assertEquals(List(4) { count }, BdspTimestampsReadOnly.read(b).entries.map { it.count })
        }
        assertEquals(before.copyOfRange(base + 0x30, base + 0x138).map { it.toInt() and 255 }, read.rawSystemBytes.subList(0x30, 0x138))
    }

    @Test fun nativeIntegrityIdentityAndExactLengthGateEveryReadIncludingCorruptionOutsideSystemBlock() {
        for (layout in layouts.indices) {
            val source = fixture(layout)
            for (o in setOf(0, 4, base - 1, base, base + 4, base + 47, base + 48, base + 0x34, base + 0x137, base + 0x138, 0xE9818, source.lastIndex)) {
                val bad = source.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspTimestampsReadOnly.read(bad) }
                assertNull(session(bad).bdspTimestamps); assertArrayEquals(before, bad)
            }
            val title = source.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = source.copyOf(); put32(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, source.copyOf(source.size - 1), source.copyOf(source.size + 1)))
                assertThrows(IllegalArgumentException::class.java) { BdspTimestampsReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4)))
            assertThrows(IllegalArgumentException::class.java) { BdspTimestampsReadOnly.read(bad) }
    }

    @Test fun timestampsCannotReenableStrictTrainerOrUnrelatedRejectedSections() {
        val b = fixture(); b[0x79BB4] = 0; b[0x79BB5] = 0xD8.toByte()
        b[0x14098 + 6 * 344] = 7; put32(b, 0x7A328, 4); seal(b)
        val before = b.copyOf(); val bound = session(b)
        assertTrue(bound.isFormatVerified); assertNull(bound.bdspTrainer); assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE))
        assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex)
        assertNotNull(bound.bdspTimestamps); assertNull(bound.bdspTimestampsUnavailableReason)
        assertNotNull(bound.bdspTrainerAppearance); assertNotNull(bound.bdspPoffins); assertNotNull(bound.bdspNursery)
        assertNotNull(bound.bdspBattleTower); assertNotNull(bound.bdspUnderground); assertArrayEquals(before, b)
    }

    @Test fun snapshotsAreDetachedImmutableAndUseNativeIdentityRatherThanPickerHint() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); val before = b.copyOf(); val bound = session(b, hint); val read = bound.bdspTimestamps!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family); assertEquals(title, read.version.romCode)
            b.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy())
            assertEquals(116444736000000000L, read.entry(BdspTimestampKind.LATEST_SAVE).fileTimeTicks)
            assertEquals(312, read.rawSystemBytes.size); assertNotNull(bound.bdspTrainer)
            assertThrows(UnsupportedOperationException::class.java) { (read.entries as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.rawSystemBytes as MutableList<*>).clear() }
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspTimestamps); assertNull(other.bdspTimestampsUnavailableReason)
    }
}
