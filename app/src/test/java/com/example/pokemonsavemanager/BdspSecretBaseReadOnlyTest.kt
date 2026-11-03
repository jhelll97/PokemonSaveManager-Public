package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspSecretBaseReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0x9A89C + 0x152C
    private val signed = listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)
    private fun put(b: ByteArray, o: Int, v: Long, count: Int = 4) { repeat(count) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put(b, 0, layouts[layout].second.toLong()); put(b, 0x79B78, 2); b[0x79BDF] = title.toByte()
        put(b, base, 0xFFFFFFFFL); put(b, base + 4, 0x80000000L)
        put(b, base + 8, -32768, 2); put(b, base + 10, -1, 2); put(b, base + 12, 32767, 2)
        b[base + 14] = 0xFF.toByte(); b[base + 15] = 0xFE.toByte(); put(b, base + 16, Int.MIN_VALUE.toLong())
        for (index in 0 until 30) for (field in 0 until 5) put(b, base + 20 + index * 20 + field * 4, signed[(index + field) % 5].toLong())
        b[base + 0x26C] = 0xFF.toByte(); b[base + 0x26D] = 1; b[base + 0x26E] = 0xAB.toByte(); b[base + 0x26F] = 0x7F
        b[base - 1] = 0xA5.toByte(); b[base + 0x270] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllRevisionsPreserveHeaderAll30StatuesAndExactBaseBoundaries() {
        for (layout in layouts.indices) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val read = BdspSecretBaseReadOnly.read(b)
            assertEquals(title, read.version.romCode); assertEquals(layouts[layout].second.toLong(), read.revision.code)
            assertEquals(0xFFFFFFFFL, read.talkPlayerDataId); assertEquals(0x80000000L, read.talkPlayerCount)
            assertEquals(-32768, read.zoneId); assertEquals(-1, read.x); assertEquals(32767, read.y)
            assertEquals(255, read.direction); assertEquals(254, read.expansionStatus); assertEquals(Int.MIN_VALUE, read.goodCount)
            assertEquals(30, read.statues.size); assertEquals(624, read.rawBaseBytes.size); assertEquals(listOf(255, 1, 171, 127), read.rawEnableBytes)
            for (index in 0 until 30) {
                val statue = read.statue(index); assertEquals(index, statue.index)
                assertEquals((0 until 5).map { signed[(index + it) % 5] }, listOf(statue.statueId, statue.pedestalId, statue.x, statue.y, statue.direction))
                assertEquals(b.copyOfRange(base + 20 + index * 20, base + 40 + index * 20).map { it.toInt() and 255 }, statue.rawBytes)
            }
            assertEquals(b.copyOfRange(base, base + 624).map { it.toInt() and 255 }, read.rawBaseBytes)
            assertEquals(0xA5, b[base - 1].toInt() and 255); assertEquals(0x5A, b[base + 624].toInt() and 255); assertArrayEquals(before, b)
        }
    }

    @Test fun signedAndUnsignedValuesRemainExactWithoutCountBasedCroppingOrNormalization() {
        for (value in signed) {
            val b = fixture(); put(b, base + 16, value.toLong())
            for (index in 0 until 30) for (field in 0 until 5) put(b, base + 20 + index * 20 + field * 4, value.toLong()); seal(b)
            val before = b.copyOf(); val read = BdspSecretBaseReadOnly.read(b); assertEquals(value, read.goodCount); assertEquals(30, read.statues.size)
            for (statue in read.statues) assertEquals(List(5) { value }, listOf(statue.statueId, statue.pedestalId, statue.x, statue.y, statue.direction)); assertArrayEquals(before, b)
        }
        for (value in listOf(-32768, -1, 0, 1, 32767)) {
            val b = fixture(); for (o in listOf(8, 10, 12)) put(b, base + o, value.toLong(), 2); seal(b)
            val before = b.copyOf(); val read = BdspSecretBaseReadOnly.read(b)
            assertEquals(value, read.zoneId); assertEquals(value, read.x); assertEquals(value, read.y); assertArrayEquals(before, b)
        }
        for (word in listOf(0L, 1L, 2L, 257L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
            val b = fixture(); put(b, base, word); put(b, base + 4, word); seal(b)
            val before = b.copyOf(); val read = BdspSecretBaseReadOnly.read(b)
            assertEquals(word, read.talkPlayerDataId); assertEquals(word, read.talkPlayerCount); assertArrayEquals(before, b)
        }
    }

    @Test fun unknownHeaderBytesAndRawEnableDataAreRetainedAndAllZeroRecordsStayVisible() {
        for (value in 0..255) {
            val b = fixture(); b[base + 14] = value.toByte(); b[base + 15] = value.toByte(); b.fill(value.toByte(), base + 0x26C, base + 0x270); seal(b)
            val before = b.copyOf(); val read = BdspSecretBaseReadOnly.read(b)
            assertEquals(value, read.direction); assertEquals(value, read.expansionStatus); assertEquals(List(4) { value }, read.rawEnableBytes)
            assertEquals(30, read.statues.size); assertArrayEquals(before, b)
        }
        val b = fixture(); b.fill(0, base, base + 0x270); seal(b); val before = b.copyOf(); val read = BdspSecretBaseReadOnly.read(b)
        assertEquals(0, read.goodCount); assertEquals((0..29).toList(), read.statues.map { it.index })
        for (statue in read.statues) assertEquals(List(20) { 0 }, statue.rawBytes)
        assertEquals(List(624) { 0 }, read.rawBaseBytes); assertArrayEquals(before, b)
    }

    @Test fun nativeIntegrityIdentityAndExactLengthPrecedeExtractionAndInvalidIndicesReject() {
        for (layout in layouts.indices) {
            val source = fixture(layout)
            for (o in setOf(0, 4, base - 1, base, base + 4, base + 8, base + 13, base + 14, base + 15, base + 16, base + 20, base + 39, base + 40, base + 0x258, base + 0x26B, base + 0x26C, base + 0x26F, base + 0x270, 0xE9818, source.lastIndex)) {
                val bad = source.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspSecretBaseReadOnly.read(bad) }; assertNull(session(bad).bdspSecretBase); assertArrayEquals(before, bad)
            }
            val title = source.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = source.copyOf(); put(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, source.copyOf(source.size - 1), source.copyOf(source.size + 1)))
                assertThrows(IllegalArgumentException::class.java) { BdspSecretBaseReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4))) assertThrows(IllegalArgumentException::class.java) { BdspSecretBaseReadOnly.read(bad) }
        val read = BdspSecretBaseReadOnly.read(fixture())
        for (index in listOf(-1, 30, Int.MIN_VALUE, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { read.statue(index) }
    }

    @Test fun independentBaseReaderDoesNotRelaxTrainerPartyOrDexGates() {
        val badCases = listOf(fixture().also { put(it, 0x79B78, 6) }, fixture().also { put(it, 0x79BD4, 1000000) },
            fixture().also { it[0x79BD8] = 2 }, fixture().also { it[0x79BDD] = 9 }, fixture().also { it[0x79BE0] = 2 },
            fixture().also { it[0x79BB4] = 0; it[0x79BB5] = 0xD8.toByte() })
        for (b in badCases) {
            b[0x14098 + 6 * 344] = 7; put(b, 0x7A328, 4); seal(b); val before = b.copyOf(); val bound = session(b)
            assertTrue(bound.isFormatVerified); assertNull(bound.bdspTrainer); assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex); assertNotNull(bound.bdspSecretBase); assertNull(bound.bdspSecretBaseUnavailableReason)
            assertEquals(30, bound.bdspSecretBase!!.statues.size); assertNotNull(bound.bdspBerryPlots); assertNotNull(bound.bdspUnderground); assertArrayEquals(before, b)
        }
    }

    @Test fun detachedImmutableRecordsUseNativeIdentityAndPreserveEarlierViewsAndWritingLocks() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); val before = b.copyOf(); val bound = session(b, hint); val read = bound.bdspSecretBase!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family); assertEquals(title, read.version.romCode)
            b.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy()); assertEquals(Int.MIN_VALUE, read.goodCount)
            assertThrows(UnsupportedOperationException::class.java) { (read.statues as MutableList<*>).clear() }
            for (statue in read.statues) assertThrows(UnsupportedOperationException::class.java) { (statue.rawBytes as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.rawBaseBytes as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.rawEnableBytes as MutableList<*>).clear() }
            assertNotNull(bound.bdspTrainer); assertNotNull(bound.bdspBerryPlots); assertNotNull(bound.bdspEncounter); assertNotNull(bound.bdspHoneyTrees)
            assertNotNull(bound.bdspUnderground); assertNotNull(bound.bdspUndergroundActivity); assertNotNull(bound.bdspBattleTower)
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspSecretBase); assertNull(other.bdspSecretBaseUnavailableReason)
    }
}
