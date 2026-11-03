package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspRecordStatisticsReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0x8F3DC
    private fun put32(b: ByteArray, o: Int, v: Long) { repeat(4) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put32(b, 0, layouts[layout].second.toLong()); put32(b, 0x79B78, 2); b[0x79BDF] = title.toByte()
        for (set in 0 until 12) for (id in 0 until 30) put32(b, base + set * 120 + id * 4, 0x80000000L + set * 1000 + id)
        b[base - 1] = 0xA5.toByte(); b[base + 0x5A0] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllRevisionsReadAll360DistinctWordsInNativeSetOrderAndExactBoundaries() {
        for (layout in layouts.indices) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val read = BdspRecordStatisticsReadOnly.read(b)
            assertEquals(title, read.version.romCode); assertEquals(layouts[layout].second.toLong(), read.revision.code)
            assertEquals(12, read.sets.size); assertEquals(1440, read.rawRecordBytes.size)
            for (index in 0 until 12) {
                val set = read.set(index); assertEquals(index, set.index); assertEquals(30, set.entries.size); assertEquals(120, set.rawBytes.size)
                for (id in 0 until 30) {
                    val entry = set.entry(id); val expected = 0x80000000L + index * 1000 + id
                    assertEquals(id, entry.recordId); assertEquals(expected, entry.rawValue); assertEquals(expected.toInt(), entry.signedValue)
                    assertEquals(BdspRecordCatalogue.key(id), entry.referenceKey); assertEquals(BdspRecordCatalogue.referenceLimit(id), entry.referenceLimit)
                }
                assertEquals(b.copyOfRange(base + index * 120, base + (index + 1) * 120).map { it.toInt() and 255 }, set.rawBytes)
            }
            assertEquals(b.copyOfRange(base, base + 1440).map { it.toInt() and 255 }, read.rawRecordBytes)
            assertEquals(0xA5, b[base - 1].toInt() and 255); assertEquals(0x5A, b[base + 1440].toInt() and 255); assertArrayEquals(before, b)
        }
    }

    @Test fun rawUnsignedAndSignedValuesRemainExactIncludingAboveReferenceLimitsAndZero() {
        for (word in listOf(0L, 1L, 5L, 9999L, 1000000L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
            val b = fixture(); for (i in 0 until 360) put32(b, base + i * 4, word); seal(b)
            val before = b.copyOf(); val read = BdspRecordStatisticsReadOnly.read(b)
            for (set in read.sets) for (entry in set.entries) {
                assertEquals(word, entry.rawValue); assertEquals(word.toInt(), entry.signedValue)
            }
            assertEquals(5, read.set(0).entry(15).referenceLimit)
            assertEquals(word, read.set(0).entry(15).rawValue) // Preserve 1,000,000 even though the upstream getter would clamp it to 5.
            assertArrayEquals(before, b)
        }
    }

    @Test fun all30ReferenceKeysAndLimitsMatchPinnedCatalogueAndIndicesRejectWithoutFallback() {
        val catalogue = (0 until 30).joinToString("\n") { "$it:${BdspRecordCatalogue.key(it)}:${BdspRecordCatalogue.referenceLimit(it)}" }
        val hash = MessageDigest.getInstance("SHA-256").digest(catalogue.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals("ec0199162d3bf5a1ec79103cd53236d846f3f5a1e1b8eed4d6280a1017e1851f", hash)
        assertTrue((0 until 30).all { BdspRecordCatalogue.name(it).isNotBlank() })
        val b = fixture(); val before = b.copyOf(); val read = BdspRecordStatisticsReadOnly.read(b)
        for (index in listOf(-1, 12, Int.MIN_VALUE, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { read.set(index) }
        for (id in listOf(-1, 30, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { read.set(0).entry(id) }
            assertThrows(IllegalArgumentException::class.java) { BdspRecordCatalogue.key(id) }
            assertThrows(IllegalArgumentException::class.java) { BdspRecordCatalogue.name(id) }
            assertThrows(IllegalArgumentException::class.java) { BdspRecordCatalogue.referenceLimit(id) }
        }
        assertArrayEquals(before, b)
    }

    @Test fun nativeIntegrityIdentityAndExactLengthGateEveryRecordRead() {
        for (layout in layouts.indices) {
            val source = fixture(layout)
            for (o in setOf(0, 4, base - 1, base, base + 3, base + 116, base + 119, base + 120, base + 1436, base + 1439, base + 1440, 0xE9818, source.lastIndex)) {
                val bad = source.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspRecordStatisticsReadOnly.read(bad) }
                assertNull(session(bad).bdspRecordStatistics); assertArrayEquals(before, bad)
            }
            val title = source.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = source.copyOf(); put32(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, source.copyOf(source.size - 1), source.copyOf(source.size + 1)))
                assertThrows(IllegalArgumentException::class.java) { BdspRecordStatisticsReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4)))
            assertThrows(IllegalArgumentException::class.java) { BdspRecordStatisticsReadOnly.read(bad) }
    }

    @Test fun statisticsRemainIndependentOfRejectedTrainerPartyAndDexWithoutRegainingCapabilities() {
        val badCases = listOf(fixture().also { put32(it, 0x79B78, 6) },
            fixture().also { put32(it, 0x79BD4, 1000000) }, fixture().also { it[0x79BD8] = 2 },
            fixture().also { it[0x79BDD] = 9 }, fixture().also { it[0x79BE0] = 2 },
            fixture().also { it[0x79BB4] = 0; it[0x79BB5] = 0xD8.toByte() })
        for (b in badCases) {
            b[0x14098 + 6 * 344] = 7; put32(b, 0x7A328, 4); seal(b)
            val before = b.copyOf(); val bound = session(b)
            assertTrue(bound.isFormatVerified); assertNull(bound.bdspTrainer); assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex); assertNotNull(bound.bdspRecordStatistics)
            assertNull(bound.bdspRecordStatisticsUnavailableReason); assertEquals(0x80002B15L, bound.bdspRecordStatistics!!.set(11).entry(29).rawValue)
            assertNotNull(bound.bdspUndergroundActivity); assertNotNull(bound.bdspUnderground); assertNotNull(bound.bdspGameOptions)
            assertArrayEquals(before, b)
        }
    }

    @Test fun detachedImmutableSetsEntriesAndRawBytesUseNativeIdentityAndPreserveOtherReadOnlyViews() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); val before = b.copyOf(); val bound = session(b, hint); val read = bound.bdspRecordStatistics!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family); assertEquals(title, read.version.romCode)
            b.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy())
            assertEquals(0x80000000L, read.set(0).entry(0).rawValue); assertEquals(0x80002B15L, read.set(11).entry(29).rawValue)
            assertThrows(UnsupportedOperationException::class.java) { (read.sets as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.set(0).entries as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.set(0).rawBytes as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.rawRecordBytes as MutableList<*>).clear() }
            assertNotNull(bound.bdspTrainer); assertNotNull(bound.bdspPlayTime); assertNotNull(bound.bdspTimestamps); assertNotNull(bound.bdspGameOptions)
            assertNotNull(bound.bdspPoffins); assertNotNull(bound.bdspNursery); assertNotNull(bound.bdspBattleTower)
            assertNotNull(bound.bdspUndergroundActivity); assertNotNull(bound.bdspUnderground)
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspRecordStatistics); assertNull(other.bdspRecordStatisticsUnavailableReason)
    }
}
