package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspBattleTowerReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0x95BF4
    private fun put32(b: ByteArray, o: Int, v: Long) { repeat(4) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun put64(b: ByteArray, o: Int, v: ULong) { repeat(8) { b[o + it] = (v shr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0, populated: Boolean = true): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put32(b, 0, layouts[layout].second.toLong()); b[0x79BDF] = title.toByte()
        if (populated) {
            put32(b, base, 0xFFFFFFFFL); put32(b, base + 4, 3); put32(b, base + 8, 0xFFFFFFFFL)
            put32(b, base + 12, 0xF0000000L); put32(b, base + 16, 0x80000001L); put32(b, base + 0x1B4, 0xFFFFFFFFL)
            repeat(4) { i ->
                val o = base + 0x14 + i * 0x68
                b.fill((0x20 + i).toByte(), o, o + 0x68); b[o] = (0xF0 + i).toByte()
                put32(b, o + 4, listOf(0L, 1L, 2L, 0x1000001L)[i]); put64(b, o + 8, 0xFEDCBA9876543210uL + i.toULong())
                put32(b, o + 0x10, 0xF0000000L + i); put32(b, o + 0x14, (i * 7).toLong())
                repeat(7) { j -> put64(b, o + 0x1C + j * 8, 0x8000000000000000uL + (i * 16 + j).toULong()) }
                put32(b, o + 0x54, 0x80000000L + i); put32(b, o + 0x58, 0xFFFFFFFFL - i); put32(b, o + 0x5C, (17 + i).toLong())
                put32(b, o + 0x60, (50 + i).toLong()); put32(b, o + 0x64, (8 + i).toLong())
            }
        }
        b[base - 1] = 0xA5.toByte(); b[base + 0x1B8] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllFourRevisionsDecodeEveryModeAndExactHeaderAndRecordBoundaries() {
        for (layout in layouts.indices) for (title in 0..1) {
            val source = fixture(layout, title); val before = source.copyOf(); val tower = BdspBattleTowerReadOnly.read(source)
            assertEquals(title, tower.version.romCode); assertEquals(layouts[layout].second.toLong(), tower.revision.code)
            assertEquals(-1, tower.maximumMasterRank); assertEquals(0xFFFFFFFFL, tower.rawMaximumMasterRank)
            assertEquals(BdspBattleTowerMode.MASTER_DOUBLES, tower.currentMode); assertNull(tower.previousMode)
            assertEquals(3, tower.rawCurrentMode); assertEquals(-1, tower.rawPreviousMode)
            assertEquals(0xF0000000L, tower.battlePoints); assertEquals(0x80000001L, tower.dailyChallengeCount); assertEquals(0xFFFFFFFFL, tower.challengeCount)
            assertEquals(BdspBattleTowerMode.values().toList(), tower.records.map { it.mode })
            assertEquals(source.copyOfRange(base, base + 0x1B8).map { it.toInt() and 255 }, tower.rawBytes)
            for (mode in BdspBattleTowerMode.values()) {
                val i = mode.nativeId; val record = tower.record(mode); val o = base + 0x14 + i * 0x68
                assertEquals(0xF0 + i, record.rawClearedFlag); assertEquals(0xFEDCBA9876543210uL + i.toULong(), record.battleSeed)
                assertEquals(0xF0000000L + i, record.rank); assertEquals((i * 7).toLong(), record.rankDownLosses)
                assertEquals((0..6).map { 0x8000000000000000uL + (i * 16 + it).toULong() }, record.trainerSeeds)
                assertEquals(0x80000000L + i, record.totalWins); assertEquals(0xFFFFFFFFL - i, record.winLoopCounter)
                assertEquals((17 + i).toLong(), record.lossCounter); assertEquals((50 + i).toLong(), record.previousStreak); assertEquals((8 + i).toLong(), record.currentStreak)
                assertEquals(source.copyOfRange(o, o + 0x68).map { it.toInt() and 255 }, record.rawBytes)
                assertEquals(List(3) { 0x20 + i }, record.rawBytes.subList(1, 4)); assertEquals(List(4) { 0x20 + i }, record.rawBytes.subList(0x18, 0x1C))
            }
            assertArrayEquals(before, source); assertEquals(0xA5, source[base - 1].toInt() and 255); assertEquals(0x5A, source[base + 0x1B8].toInt() and 255)
        }
    }

    @Test fun suspendedUsesCompleteWordEqualityClearedByteAndUnsignedSeedsNeverNormalize() {
        val words = listOf(0L, 1L, 2L, 0x1000001L, 0x80000001L, 0xFFFFFFFFL)
        val seeds = listOf(0uL, 1uL, 0x7FFFFFFFFFFFFFFFuL, 0x8000000000000000uL, 0xFFFFFFFF80000000uL, 0xFFFFFFFFFFFFFFFFuL)
        for (word in words) for (seed in seeds) {
            val b = fixture(); repeat(4) { i ->
                val o = base + 0x14 + i * 0x68; put32(b, o + 4, word); put64(b, o + 8, seed)
                repeat(7) { j -> put64(b, o + 0x1C + j * 8, seed) }
            }; seal(b); val before = b.copyOf(); val tower = BdspBattleTowerReadOnly.read(b)
            for (record in tower.records) {
                assertEquals(word, record.rawSuspendedFlag); assertEquals(word == 1L, record.isSuspended)
                assertEquals(word in 0L..1L, record.hasCanonicalSuspendedFlag); assertEquals(seed, record.battleSeed)
                assertEquals(List(7) { seed }, record.trainerSeeds); assertEquals(0xF0 + record.mode.nativeId, record.rawClearedFlag)
            }; assertArrayEquals(before, b)
        }
    }

    @Test fun signedModeIdsNoEntryAndUnknownValuesNeverHideAnyOfTheFourRecords() {
        assertEquals(listOf("Singles", "Doubles", "Master Singles", "Master Doubles"), BdspBattleTowerMode.values().map { it.displayName })
        for (word in listOf(0L, 1L, 2L, 3L, 4L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
            val b = fixture(); put32(b, base + 4, word); put32(b, base + 8, word); seal(b)
            val tower = BdspBattleTowerReadOnly.read(b); assertEquals(word.toInt(), tower.rawCurrentMode); assertEquals(word.toInt(), tower.rawPreviousMode)
            assertEquals(BdspBattleTowerMode.values().firstOrNull { it.nativeId == word.toInt() }, tower.currentMode)
            assertEquals(tower.currentMode, tower.previousMode); assertEquals(4, tower.records.size); assertEquals(8L, tower.records[0].currentStreak)
        }
        assertEquals("No entry", BdspBattleTowerMode.label(-1)); assertEquals("Unknown mode (4)", BdspBattleTowerMode.label(4))
    }

    @Test fun unsignedCounterBoundariesAndEmptyRecordsRemainLiteralWithoutDerivingStreakMaxima() {
        for (word in listOf(0L, 1L, 9999L, 10000L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
            val b = fixture(populated = false)
            for (o in listOf(0, 12, 16, 0x1B4)) put32(b, base + o, word)
            repeat(4) { i -> for (o in listOf(0x10, 0x14, 0x54, 0x58, 0x5C, 0x60, 0x64)) put32(b, base + 0x14 + i * 0x68 + o, word) }
            seal(b); val tower = BdspBattleTowerReadOnly.read(b)
            assertEquals(word.toInt(), tower.maximumMasterRank); assertEquals(word, tower.rawMaximumMasterRank)
            assertEquals(word, tower.battlePoints); assertEquals(word, tower.dailyChallengeCount); assertEquals(word, tower.challengeCount)
            for (record in tower.records) assertEquals(List(7) { word }, listOf(record.rank, record.rankDownLosses, record.totalWins, record.winLoopCounter, record.lossCounter, record.previousStreak, record.currentStreak))
        }
        val zero = BdspBattleTowerReadOnly.read(fixture(populated = false)); assertEquals(4, zero.records.size)
        assertTrue(zero.records.all { it.rank == 0L && it.currentStreak == 0L && it.rawBytes.all { b -> b == 0 } })
        val b = fixture(); put32(b, base + 0x14 + 0x60, 1); put32(b, base + 0x14 + 0x64, 999); seal(b)
        val record = BdspBattleTowerReadOnly.read(b).records[0]; assertEquals(1L, record.previousStreak); assertEquals(999L, record.currentStreak)
    }

    @Test fun existingNativeWholeFileGateRejectsDamageIdentityMismatchAndWrongLengths() {
        for (layout in layouts.indices) {
            val source = fixture(layout)
            for (o in setOf(0, 4, base - 1, base, base + 0x13, base + 0x14, base + 0x1B3, base + 0x1B4, base + 0x1B7, base + 0x1B8, 0x79BDF, 0xE9818, source.lastIndex)) {
                val bad = source.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspBattleTowerReadOnly.read(bad) }; assertNull(session(bad).bdspBattleTower); assertArrayEquals(before, bad)
            }
            val title = source.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = source.copyOf(); put32(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, source.copyOf(source.size - 1), source.copyOf(source.size + 1)))
                assertThrows(IllegalArgumentException::class.java) { BdspBattleTowerReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4))) assertThrows(IllegalArgumentException::class.java) { BdspBattleTowerReadOnly.read(bad) }
    }

    @Test fun independentSessionReaderImmutableListsAndNativeIdentityPreserveAllWriteLocks() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val source = fixture(title = title); put32(source, 0x79B78, 0); source[0x14098 + 6 * 344] = 7; put32(source, 0x7A328, 4); seal(source)
            val before = source.copyOf(); val bound = session(source, hint); val tower = bound.bdspBattleTower!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family); assertTrue(bound.isFormatVerified)
            assertNull(bound.bdspTrainer); assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex); assertNull(bound.bdspBattleTowerUnavailableReason)
            assertNotNull(bound.bdspInventory); assertNotNull(bound.bdspNursery); assertNotNull(bound.bdspUnderground)
            source.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy()); assertEquals(4, tower.records.size)
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
            for (list in listOf(tower.records, tower.rawBytes, tower.records[0].trainerSeeds, tower.records[0].rawBytes))
                assertThrows(UnsupportedOperationException::class.java) { (list as MutableList<*>).clear() }
            assertEquals(0xF0000000L, tower.battlePoints)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspBattleTower); assertNull(other.bdspBattleTowerUnavailableReason)
    }
}
