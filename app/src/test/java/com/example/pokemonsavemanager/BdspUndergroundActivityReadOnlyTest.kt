package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspUndergroundActivityReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0xE8178
    private fun put(b: ByteArray, o: Int, v: Long, size: Int) { repeat(size) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put(b, 0, layouts[layout].second.toLong(), 4); put(b, 0x79B78, 2, 4); b[0x79BDF] = title.toByte()
        put(b, 0xE8178, 0x8000, 2); put(b, 0xE817A, 0x7FFF, 2)
        put(b, 0xE817C, 0x80000000L, 4); put(b, 0xE8180, 0x7FFFFFFFL, 4); put(b, 0xE8184, 0xFFFFFFFFL, 4)
        put(b, 0xE8188, Long.MIN_VALUE, 8); put(b, 0xE8190, Long.MAX_VALUE, 8)
        b[base - 1] = 0xA5.toByte(); b[base + 32] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))
    private fun counters(read: BdspUndergroundActivityRead): List<Int> = listOf(read.fossilDiggingPlayCount,
        read.statueTvBroadcastCount, read.secretBaseTvLikeCount, read.otherSecretBaseLikeCount, read.successfulLightStoneSearchCount)

    @Test fun bothTitlesAllRevisionsReadSignedFieldWidthsDistinctCountersAndExact32ByteBoundaries() {
        for (layout in layouts.indices) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val read = BdspUndergroundActivityReadOnly.read(b)
            assertEquals(title, read.version.romCode); assertEquals(layouts[layout].second.toLong(), read.revision.code)
            assertEquals(listOf(-32768, 32767, Int.MIN_VALUE, Int.MAX_VALUE, -1), counters(read))
            assertEquals(Long.MIN_VALUE, read.reserved1); assertEquals(Long.MAX_VALUE, read.reserved2)
            assertEquals(b.copyOfRange(base, base + 32).map { it.toInt() and 255 }, read.rawRecordBytes)
            assertEquals(0xA5, b[base - 1].toInt() and 255); assertEquals(0x5A, b[base + 32].toInt() and 255); assertArrayEquals(before, b)
        }
    }

    @Test fun signedCounterBoundariesReservedBitPatternsAndZeroStayExactWithoutClampingOrNormalization() {
        for (word in listOf(0L, 1L, 0x7FFFL, 0x8000L, 0xFFFFL)) {
            val b = fixture(); put(b, base, word, 2); put(b, base + 2, word, 2); seal(b)
            val read = BdspUndergroundActivityReadOnly.read(b)
            assertEquals(word.toShort().toInt(), read.fossilDiggingPlayCount); assertEquals(word.toShort().toInt(), read.statueTvBroadcastCount)
        }
        for (word in listOf(0L, 1L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
            val b = fixture(); for (offset in listOf(4, 8, 12)) put(b, base + offset, word, 4); seal(b)
            val read = BdspUndergroundActivityReadOnly.read(b)
            assertEquals(List(3) { word.toInt() }, counters(read).drop(2))
        }
        for (word in listOf(0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 0x0123456789ABCDEFL, -0x0123456789ABCDF0L)) {
            val b = fixture(); put(b, base + 16, word, 8); put(b, base + 24, word, 8); seal(b)
            val before = b.copyOf(); val read = BdspUndergroundActivityReadOnly.read(b)
            assertEquals(word, read.reserved1); assertEquals(word, read.reserved2); assertArrayEquals(before, b)
        }
        val b = fixture(); b.fill(0, base, base + 32); seal(b)
        val read = BdspUndergroundActivityReadOnly.read(b)
        assertEquals(List(5) { 0 }, counters(read)); assertEquals(0L, read.reserved1); assertEquals(0L, read.reserved2)
        assertEquals(List(32) { 0 }, read.rawRecordBytes)
    }

    @Test fun nativeIntegrityIdentityAndExactLengthGateEveryActivityRead() {
        for (layout in layouts.indices) {
            val source = fixture(layout)
            for (o in setOf(0, 4, base - 1, base, base + 1, base + 2, base + 4, base + 12, base + 16, base + 31, base + 32, 0xE9818, source.lastIndex)) {
                val bad = source.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspUndergroundActivityReadOnly.read(bad) }
                assertNull(session(bad).bdspUndergroundActivity); assertArrayEquals(before, bad)
            }
            val title = source.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = source.copyOf(); put(revision, 0, 0x35, 4); seal(revision)
            for (bad in listOf(title, revision, source.copyOf(source.size - 1), source.copyOf(source.size + 1)))
                assertThrows(IllegalArgumentException::class.java) { BdspUndergroundActivityReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4)))
            assertThrows(IllegalArgumentException::class.java) { BdspUndergroundActivityReadOnly.read(bad) }
    }

    @Test fun activityRemainsIndependentOfRejectedTrainerPartyAndDexAndDoesNotReenableCapabilities() {
        val badCases = listOf(fixture().also { put(it, 0x79B78, 6, 4) },
            fixture().also { put(it, 0x79BD4, 1000000, 4) }, fixture().also { it[0x79BD8] = 2 },
            fixture().also { it[0x79BDD] = 9 }, fixture().also { it[0x79BE0] = 2 },
            fixture().also { it[0x79BB4] = 0; it[0x79BB5] = 0xD8.toByte() })
        for (b in badCases) {
            b[0x14098 + 6 * 344] = 7; put(b, 0x7A328, 4, 4); seal(b)
            val before = b.copyOf(); val bound = session(b)
            assertTrue(bound.isFormatVerified); assertNull(bound.bdspTrainer); assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex); assertNotNull(bound.bdspUndergroundActivity)
            assertNull(bound.bdspUndergroundActivityUnavailableReason); assertEquals(-32768, bound.bdspUndergroundActivity!!.fossilDiggingPlayCount)
            assertNotNull(bound.bdspUnderground); assertNotNull(bound.bdspTimestamps); assertNotNull(bound.bdspPlayTime); assertNotNull(bound.bdspGameOptions)
            assertArrayEquals(before, b)
        }
    }

    @Test fun immutableDetachedSnapshotsUseNativeIdentityAndLeaveInventoryAndOtherSectionsIndependent() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); val before = b.copyOf(); val bound = session(b, hint); val read = bound.bdspUndergroundActivity!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family); assertEquals(title, read.version.romCode)
            b.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy())
            assertEquals(-32768, read.fossilDiggingPlayCount); assertEquals(Long.MIN_VALUE, read.reserved1); assertEquals(32, read.rawRecordBytes.size)
            assertThrows(UnsupportedOperationException::class.java) { (read.rawRecordBytes as MutableList<*>).clear() }
            assertNotNull(bound.bdspTrainer); assertNotNull(bound.bdspPlayTime); assertNotNull(bound.bdspTimestamps); assertNotNull(bound.bdspGameOptions)
            assertNotNull(bound.bdspPoffins); assertNotNull(bound.bdspNursery); assertNotNull(bound.bdspBattleTower); assertNotNull(bound.bdspUnderground)
            assertEquals(0, bound.bdspUnderground!!.slot(1).count) // Activity counts never become inventory quantities.
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspUndergroundActivity); assertNull(other.bdspUndergroundActivityUnavailableReason)
    }
}
