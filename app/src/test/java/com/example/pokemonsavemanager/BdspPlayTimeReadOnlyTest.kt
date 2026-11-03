package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspPlayTimeReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0x79C04
    private fun put32(b: ByteArray, o: Int, v: Long) { repeat(4) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put32(b, 0, layouts[layout].second.toLong()); put32(b, 0x79B78, 2); b[0x79BDF] = title.toByte()
        b[0x79C04] = 0xDC.toByte(); b[0x79C05] = 0xFE.toByte(); b[0x79C06] = 59; b[0x79C07] = 58
        b[base - 1] = 0xA5.toByte(); b[base + 4] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllRevisionsUseUnsignedLittleEndianHoursAndExactFourByteBoundaries() {
        for (layout in layouts.indices) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val read = BdspPlayTimeReadOnly.read(b)
            assertEquals(title, read.version.romCode); assertEquals(layouts[layout].second.toLong(), read.revision.code)
            assertEquals(65244, read.hours); assertEquals(59, read.minutes); assertEquals(58, read.seconds)
            assertTrue(read.hasCanonicalMinutesAndSeconds); assertEquals("65244:59:58", read.savedTimeText)
            assertEquals(listOf(0xDC, 0xFE, 59, 58), read.rawPlayTimeBytes)
            assertEquals(0xA5, b[base - 1].toInt() and 255); assertEquals(0x5A, b[base + 4].toInt() and 255)
            assertArrayEquals(before, b)
        }
    }

    @Test fun allByteValuesHourExtremaAndNoncanonicalComponentsStayExactWithoutCarryingOrTruncation() {
        for (value in 0..255) {
            val b = fixture(); b[base + 2] = value.toByte(); b[base + 3] = (255 - value).toByte(); seal(b)
            val before = b.copyOf(); val read = BdspPlayTimeReadOnly.read(b)
            assertEquals(value, read.minutes); assertEquals(255 - value, read.seconds)
            assertFalse(read.hasCanonicalMinutesAndSeconds); assertArrayEquals(before, b)
        }
        for (hours in listOf(0, 1, 255, 256, 9999, 10000, 32768, 65535)) {
            val b = fixture(); b[base] = hours.toByte(); b[base + 1] = (hours ushr 8).toByte(); b[base + 2] = 0; b[base + 3] = 0; seal(b)
            val read = BdspPlayTimeReadOnly.read(b)
            assertEquals(hours, read.hours); assertTrue(read.hasCanonicalMinutesAndSeconds)
            assertEquals(hours.toString().padStart(4, '0') + ":00:00", read.savedTimeText)
        }
        for ((minutes, seconds) in listOf(0 to 0, 59 to 59, 60 to 0, 0 to 60, 255 to 0, 0 to 255, 255 to 255)) {
            val b = fixture(); b[base] = 0xFF.toByte(); b[base + 1] = 0xFF.toByte()
            b[base + 2] = minutes.toByte(); b[base + 3] = seconds.toByte(); seal(b)
            val read = BdspPlayTimeReadOnly.read(b)
            assertEquals(65535, read.hours); assertEquals(minutes, read.minutes); assertEquals(seconds, read.seconds)
            assertEquals(minutes < 60 && seconds < 60, read.hasCanonicalMinutesAndSeconds)
            if (minutes == 255 && seconds == 255) assertEquals("65535:255:255", read.savedTimeText)
        }
    }

    @Test fun nativeIntegrityIdentityAndExactLengthPrecedeEveryReadIncludingOutsideBlockCorruption() {
        for (layout in layouts.indices) {
            val source = fixture(layout)
            for (o in setOf(0, 4, base - 1, base, base + 1, base + 2, base + 3, base + 4, 0x95DAC, 0xE9818, source.lastIndex)) {
                val bad = source.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspPlayTimeReadOnly.read(bad) }
                assertNull(session(bad).bdspPlayTime); assertArrayEquals(before, bad)
            }
            val title = source.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = source.copyOf(); put32(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, source.copyOf(source.size - 1), source.copyOf(source.size + 1)))
                assertThrows(IllegalArgumentException::class.java) { BdspPlayTimeReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4)))
            assertThrows(IllegalArgumentException::class.java) { BdspPlayTimeReadOnly.read(bad) }
    }

    @Test fun playtimeDoesNotRelaxStrictTrainerOrDependOnOtherSectionsOrSavedDates() {
        val badCases = listOf(
            fixture().also { put32(it, 0x79BD4, 1000000) }, fixture().also { it[0x79BD8] = 2 },
            fixture().also { it[0x79BDD] = 9 }, fixture().also { it[0x79BE0] = 2 },
            fixture().also { put32(it, 0x79B78, 6) }, fixture().also { it[0x79BB4] = 0; it[0x79BB5] = 0xD8.toByte() })
        for (b in badCases) {
            b[0x14098 + 6 * 344] = 7; put32(b, 0x7A328, 4); b.fill(0xFF.toByte(), 0x95DAC, 0x95DDC); seal(b)
            val before = b.copyOf(); val bound = session(b)
            assertTrue(bound.isFormatVerified); assertNull(bound.bdspTrainer); assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex); assertNotNull(bound.bdspTimestamps)
            assertTrue(bound.bdspTimestamps!!.entries.all { it.utcTimestamp == null })
            assertNotNull(bound.bdspPlayTime); assertNull(bound.bdspPlayTimeUnavailableReason)
            assertEquals("65244:59:58", bound.bdspPlayTime!!.savedTimeText); assertArrayEquals(before, b)
        }
        val b = fixture(); b.fill(0, base, base + 4); seal(b)
        assertEquals("0000:00:00", session(b).bdspPlayTime!!.savedTimeText)
    }

    @Test fun immutableDetachedSnapshotsUseNativeIdentityAndKeepEarlierReadOnlyViewsAvailable() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); val before = b.copyOf(); val bound = session(b, hint); val read = bound.bdspPlayTime!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family); assertEquals(title, read.version.romCode)
            b.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy())
            assertEquals("65244:59:58", read.savedTimeText); assertEquals(listOf(0xDC, 0xFE, 59, 58), read.rawPlayTimeBytes)
            assertThrows(UnsupportedOperationException::class.java) { (read.rawPlayTimeBytes as MutableList<*>).clear() }
            assertNotNull(bound.bdspTrainer); assertNotNull(bound.bdspTimestamps); assertNotNull(bound.bdspTrainerAppearance)
            assertNotNull(bound.bdspPoffins); assertNotNull(bound.bdspNursery); assertNotNull(bound.bdspBattleTower); assertNotNull(bound.bdspUnderground)
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspPlayTime); assertNull(other.bdspPlayTimeUnavailableReason)
    }
}
