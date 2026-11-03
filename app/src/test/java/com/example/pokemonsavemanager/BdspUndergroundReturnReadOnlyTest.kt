package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspUndergroundReturnReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0x9A89C
    private val floatWords = listOf(0L, 0x80000000L, 1L, 0x80000001L, 0x007FFFFFL, 0x00800000L, 0x3F800000L,
        0x3F800001L, 0xBF800000L, 0x7F7FFFFFL, 0xFF7FFFFFL, 0x7F800000L, 0xFF800000L, 0x7FC01234L, 0xFFC05678L, 0x7F801234L, 0xFFFFFFFFL)
    private fun put(b: ByteArray, o: Int, v: Long) { repeat(4) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put(b, 0, layouts[layout].second.toLong()); put(b, 0x79B78, 2); b[0x79BDF] = title.toByte()
        for ((o, v) in mapOf(0 to Int.MIN_VALUE.toLong(), 4 to -1L, 8 to Int.MAX_VALUE.toLong(), 12 to 1L, 16 to -2L,
            20 to 0x80000000L, 24 to 0x7FC01234L, 28 to 0x7F800000L, 32 to Int.MIN_VALUE.toLong(), 0x1528 to Int.MAX_VALUE.toLong())) put(b, base + o, v)
        b[base - 1] = 0xA5.toByte(); b[base + 0x24] = 0x5A
        b[base + 0x1527] = 0xA6.toByte(); b[base + 0x152C] = 0x6A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllRevisionsExposeExactSignedFieldsFloatWordsAndTwoRawRegions() {
        for (layout in layouts.indices) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val read = BdspUndergroundReturnReadOnly.read(b)
            assertEquals(title, read.version.romCode); assertEquals(layouts[layout].second.toLong(), read.revision.code)
            assertEquals(Int.MIN_VALUE, read.returnZoneId); assertEquals(-1, read.gridX); assertEquals(Int.MAX_VALUE, read.gridY); assertEquals(1, read.gridZ)
            assertEquals(-2, read.zenmetsuZoneId); assertEquals(Int.MIN_VALUE, read.zenmetsuDirection); assertEquals(Int.MAX_VALUE, read.returnUgZoneId)
            assertEquals(0x80000000L, read.rawZenmetsuXBits); assertEquals(0x7FC01234L, read.rawZenmetsuYBits); assertEquals(0x7F800000L, read.rawZenmetsuZBits)
            assertEquals(Int.MIN_VALUE, read.zenmetsuX.toRawBits()); assertTrue(read.zenmetsuY.isNaN()); assertEquals(Float.POSITIVE_INFINITY, read.zenmetsuZ, 0f)
            assertEquals(36, read.rawReturnBytes.size); assertEquals(4, read.rawUndergroundZoneBytes.size)
            assertEquals(b.copyOfRange(base, base + 36).map { it.toInt() and 255 }, read.rawReturnBytes)
            assertEquals(b.copyOfRange(base + 0x1528, base + 0x152C).map { it.toInt() and 255 }, read.rawUndergroundZoneBytes)
            assertEquals(0xA5, b[base - 1].toInt() and 255); assertEquals(0x5A, b[base + 36].toInt() and 255)
            assertEquals(0xA6, b[base + 0x1527].toInt() and 255); assertEquals(0x6A, b[base + 0x152C].toInt() and 255); assertArrayEquals(before, b)
        }
    }

    @Test fun allSevenSignedIntegerFieldsPreserveExtremesWithoutClampingOrUnsignedDisplay() {
        for (value in listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)) {
            val b = fixture(); for (o in listOf(0, 4, 8, 12, 16, 32, 0x1528)) put(b, base + o, value.toLong()); seal(b)
            val before = b.copyOf(); val read = BdspUndergroundReturnReadOnly.read(b)
            assertEquals(List(7) { value }, listOf(read.returnZoneId, read.gridX, read.gridY, read.gridZ, read.zenmetsuZoneId, read.zenmetsuDirection, read.returnUgZoneId))
            assertArrayEquals(before, b)
        }
    }

    @Test fun float32SignedZerosSubnormalsInfinitiesAndNanPayloadsRetainOriginalRawBits() {
        for (word in floatWords + (0 until 32).map { 1L shl it }) {
            val b = fixture(); for (o in listOf(20, 24, 28)) put(b, base + o, word); seal(b)
            val before = b.copyOf(); val read = BdspUndergroundReturnReadOnly.read(b)
            assertEquals(List(3) { word }, listOf(read.rawZenmetsuXBits, read.rawZenmetsuYBits, read.rawZenmetsuZBits))
            val nan = (word and 0x7F800000L) == 0x7F800000L && (word and 0x007FFFFFL) != 0L
            for (value in listOf(read.zenmetsuX, read.zenmetsuY, read.zenmetsuZ)) {
                if (nan) assertTrue(value.isNaN()) else assertEquals(word.toInt(), value.toRawBits())
            }
            // Signaling NaNs may be quieted by the VM when materialized as Float; the saved word above remains authoritative.
            assertEquals(b.copyOfRange(base + 20, base + 32).map { it.toInt() and 255 }, read.rawReturnBytes.subList(20, 32)); assertArrayEquals(before, b)
        }
    }

    @Test fun fullNativeIntegrityIdentityAndExactLengthPrecedeEveryFieldRead() {
        for (layout in layouts.indices) {
            val source = fixture(layout)
            for (o in setOf(0, 4, base - 1, base, base + 4, base + 16, base + 20, base + 23, base + 24, base + 27, base + 28, base + 31, base + 32, base + 35, base + 36, base + 0x1527, base + 0x1528, base + 0x152B, base + 0x152C, 0xE9818, source.lastIndex)) {
                val bad = source.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspUndergroundReturnReadOnly.read(bad) }; assertNull(session(bad).bdspUndergroundReturn); assertArrayEquals(before, bad)
            }
            val title = source.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = source.copyOf(); put(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, source.copyOf(source.size - 1), source.copyOf(source.size + 1)))
                assertThrows(IllegalArgumentException::class.java) { BdspUndergroundReturnReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4))) assertThrows(IllegalArgumentException::class.java) { BdspUndergroundReturnReadOnly.read(bad) }
    }

    @Test fun independentReturnReaderDoesNotRelaxTrainerPartyOrDexGates() {
        val badCases = listOf(fixture().also { put(it, 0x79B78, 6) }, fixture().also { put(it, 0x79BD4, 1000000) },
            fixture().also { it[0x79BD8] = 2 }, fixture().also { it[0x79BDD] = 9 }, fixture().also { it[0x79BE0] = 2 },
            fixture().also { it[0x79BB4] = 0; it[0x79BB5] = 0xD8.toByte() })
        for (b in badCases) {
            b[0x14098 + 6 * 344] = 7; put(b, 0x7A328, 4); seal(b); val before = b.copyOf(); val bound = session(b)
            assertTrue(bound.isFormatVerified); assertNull(bound.bdspTrainer); assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex); assertNotNull(bound.bdspUndergroundReturn); assertNull(bound.bdspUndergroundReturnUnavailableReason)
            assertEquals(Int.MIN_VALUE, bound.bdspUndergroundReturn!!.returnZoneId); assertNotNull(bound.bdspUndergroundNpc); assertNotNull(bound.bdspSecretBase); assertArrayEquals(before, b)
        }
    }

    @Test fun detachedImmutableRawRegionsUseNativeIdentityAndPreserveEarlierViewsAndWritingLocks() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); val before = b.copyOf(); val bound = session(b, hint); val read = bound.bdspUndergroundReturn!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family); assertEquals(title, read.version.romCode)
            b.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy()); assertEquals(Int.MIN_VALUE, read.returnZoneId)
            assertEquals(0x7FC01234L, read.rawZenmetsuYBits)
            assertThrows(UnsupportedOperationException::class.java) { (read.rawReturnBytes as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.rawUndergroundZoneBytes as MutableList<*>).clear() }
            assertNotNull(bound.bdspTrainer); assertNotNull(bound.bdspUndergroundNpc); assertNotNull(bound.bdspSecretBase); assertNotNull(bound.bdspBerryPlots)
            assertNotNull(bound.bdspUnderground); assertNotNull(bound.bdspUndergroundActivity); assertNotNull(bound.bdspBattleTower)
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspUndergroundReturn); assertNull(other.bdspUndergroundReturnUnavailableReason)
    }
}
