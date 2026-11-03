package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspHoneyTreesReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0x8F97C + 0x14
    private val flags = listOf(0L, 1L, 2L, 0x101L, 0xFFFFFFFFL)
    private val minutes = listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)
    private fun put(b: ByteArray, o: Int, v: Long, count: Int = 4) { repeat(count) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put(b, 0, layouts[layout].second.toLong()); put(b, 0x79B78, 2); b[0x79BDF] = title.toByte()
        put(b, base, Long.MIN_VALUE, 8); b[base + 8] = 0xFF.toByte()
        b[base + 9] = 0xA5.toByte(); b[base + 10] = 0x5A; b[base + 11] = 0xE7.toByte()
        for (i in 0 until 21) {
            val start = base + 12 + i * 12
            put(b, start, flags[i % 5]); put(b, start + 4, minutes[i % 5].toLong())
            b[start + 8] = (i * 13).toByte(); b[start + 9] = (255 - i).toByte(); b[start + 10] = (128 + i).toByte(); b[start + 11] = (0xC0 + i).toByte()
        }
        b[base - 1] = 0xA5.toByte(); b[base + 264] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllRevisionsPreserveHeadersEveryTreeAndNativeSectionBoundaries() {
        for (layout in layouts.indices) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val read = BdspHoneyTreesReadOnly.read(b)
            assertEquals(title, read.version.romCode); assertEquals(layouts[layout].second.toLong(), read.revision.code)
            assertEquals(Long.MIN_VALUE, read.lastUpdateMinutes); assertEquals(255, read.treeNumber)
            assertEquals(21, read.entries.size); assertEquals(264, read.rawHoneyBytes.size)
            for (i in 0 until 21) {
                val entry = read.entry(i); val start = base + 12 + i * 12
                assertEquals(i, entry.index); assertEquals(flags[i % 5], entry.rawSpreadWord); assertEquals(minutes[i % 5], entry.minutes)
                assertEquals(flags[i % 5] == 1L, entry.isSpread); assertEquals(i % 5 < 2, entry.hasCanonicalSpreadWord)
                assertEquals((i * 13) and 255, entry.tableId); assertEquals(255 - i, entry.rarityId); assertEquals(128 + i, entry.shakeId)
                assertEquals(b.copyOfRange(start, start + 12).map { it.toInt() and 255 }, entry.rawBytes)
            }
            assertEquals(4, read.spreadFlagCount); assertEquals(12, read.unknownSpreadWordCount)
            assertEquals(b.copyOfRange(base, base + 264).map { it.toInt() and 255 }, read.rawHoneyBytes)
            assertEquals(0xA5, b[base - 1].toInt() and 255); assertEquals(0x5A, b[base + 264].toInt() and 255); assertArrayEquals(before, b)
        }
    }

    @Test fun signedUpdateAndMinuteExtremesAndAllByteIdsRemainExactWithoutClockOrTimerNormalization() {
        for (update in listOf(Long.MIN_VALUE, Long.MIN_VALUE + 1, -1L, 0L, 1L, Long.MAX_VALUE)) {
            val b = fixture(); put(b, base, update, 8); seal(b)
            val before = b.copyOf(); assertEquals(update, BdspHoneyTreesReadOnly.read(b).lastUpdateMinutes); assertArrayEquals(before, b)
        }
        for (id in 0..255) {
            val b = fixture(); b[base + 8] = id.toByte()
            for (i in 0 until 21) { val start = base + 12 + i * 12; b[start + 8] = id.toByte(); b[start + 9] = id.toByte(); b[start + 10] = id.toByte() }
            seal(b); val before = b.copyOf(); val read = BdspHoneyTreesReadOnly.read(b)
            assertEquals(id, read.treeNumber)
            for (entry in read.entries) { assertEquals(id, entry.tableId); assertEquals(id, entry.rarityId); assertEquals(id, entry.shakeId) }
            assertArrayEquals(before, b)
        }
    }

    @Test fun spreadUsesCompleteUnsignedWordEqualityToOneAndRejectsOutOfRangeIndices() {
        for (word in listOf(0L, 1L, 2L, 3L, 255L, 256L, 257L, 0x01000000L, 0x01000001L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL) + (0 until 32).map { 1L shl it }) {
            val b = fixture(); for (i in 0 until 21) put(b, base + 12 + i * 12, word); seal(b)
            val before = b.copyOf(); val read = BdspHoneyTreesReadOnly.read(b)
            assertEquals(if (word == 1L) 21 else 0, read.spreadFlagCount)
            assertEquals(if (word in 0L..1L) 0 else 21, read.unknownSpreadWordCount)
            for (entry in read.entries) { assertEquals(word, entry.rawSpreadWord); assertEquals(word == 1L, entry.isSpread) }
            for (index in listOf(-1, 21, Int.MIN_VALUE, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { read.entry(index) }
            assertArrayEquals(before, b)
        }
    }

    @Test fun wholeFileIntegrityExactLengthRevisionAndNativeTitlePrecedeExtraction() {
        for (layout in layouts.indices) {
            val source = fixture(layout)
            for (o in setOf(0, 4, base - 1, base, base + 7, base + 8, base + 11, base + 12, base + 15, base + 16, base + 20, base + 251, base + 263, base + 264, 0xE9818, source.lastIndex)) {
                val bad = source.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspHoneyTreesReadOnly.read(bad) }
                assertNull(session(bad).bdspHoneyTrees); assertArrayEquals(before, bad)
            }
            val title = source.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = source.copyOf(); put(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, source.copyOf(source.size - 1), source.copyOf(source.size + 1)))
                assertThrows(IllegalArgumentException::class.java) { BdspHoneyTreesReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4)))
            assertThrows(IllegalArgumentException::class.java) { BdspHoneyTreesReadOnly.read(bad) }
    }

    @Test fun honeyViewsRemainIndependentOfStrictTrainerPartyAndDexCapabilityFailures() {
        val badCases = listOf(fixture().also { put(it, 0x79B78, 6) },
            fixture().also { put(it, 0x79BD4, 1000000) }, fixture().also { it[0x79BD8] = 2 },
            fixture().also { it[0x79BDD] = 9 }, fixture().also { it[0x79BE0] = 2 },
            fixture().also { it[0x79BB4] = 0; it[0x79BB5] = 0xD8.toByte() })
        for (b in badCases) {
            b[0x14098 + 6 * 344] = 7; put(b, 0x7A328, 4); seal(b)
            val before = b.copyOf(); val bound = session(b)
            assertTrue(bound.isFormatVerified); assertNull(bound.bdspTrainer); assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex); assertNotNull(bound.bdspHoneyTrees)
            assertNull(bound.bdspHoneyTreesUnavailableReason); assertEquals(21, bound.bdspHoneyTrees!!.entries.size)
            assertNotNull(bound.bdspBattleTrainerStatus); assertNotNull(bound.bdspAdditionalRecords); assertNotNull(bound.bdspRecordStatistics)
            assertArrayEquals(before, b)
        }
    }

    @Test fun immutableDetachedSnapshotsUseNativeIdentityAndKeepEarlierSectionsAndWritingLocks() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); val before = b.copyOf(); val bound = session(b, hint); val read = bound.bdspHoneyTrees!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family); assertEquals(title, read.version.romCode)
            b.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy())
            assertEquals(Long.MIN_VALUE, read.lastUpdateMinutes); assertEquals(Int.MIN_VALUE, read.entry(20).minutes)
            assertThrows(UnsupportedOperationException::class.java) { (read.entries as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.entry(0).rawBytes as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.rawHoneyBytes as MutableList<*>).clear() }
            assertNotNull(bound.bdspTrainer); assertNotNull(bound.bdspBattleTrainerStatus); assertNotNull(bound.bdspAdditionalRecords)
            assertNotNull(bound.bdspRecordStatistics); assertNotNull(bound.bdspUndergroundActivity); assertNotNull(bound.bdspGameOptions); assertNotNull(bound.bdspBattleTower)
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspHoneyTrees); assertNull(other.bdspHoneyTreesUnavailableReason)
    }
}
