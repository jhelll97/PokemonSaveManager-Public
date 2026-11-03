package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspBattleTrainerStatusReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0x7D3E0
    private val wins = listOf(0L, 1L, 2L, 0x101L, 0xFFFFFFFFL)
    private val searchers = listOf(1L, 0L, 0x100L, 0x01000001L, 0xFFFFFFFFL)
    private fun put(b: ByteArray, o: Int, v: Long) { repeat(4) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put(b, 0, layouts[layout].second.toLong()); put(b, 0x79B78, 2); b[0x79BDF] = title.toByte()
        for (i in 0 until 707) { put(b, base + i * 8, wins[i % 5]); put(b, base + i * 8 + 4, searchers[i % 5]) }
        b[base - 1] = 0xA5.toByte(); b[base + 0x1618] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllRevisionsExpose707EntriesFullUnsignedWordsAndExactBlockBoundaries() {
        for (layout in layouts.indices) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val read = BdspBattleTrainerStatusReadOnly.read(b)
            assertEquals(title, read.version.romCode); assertEquals(layouts[layout].second.toLong(), read.revision.code)
            assertEquals(707, read.entries.size); assertEquals(5656, read.rawStatusBytes.size)
            for (i in 0 until 707) {
                val entry = read.entry(i); assertEquals(i, entry.index); assertEquals(wins[i % 5], entry.rawWinWord); assertEquals(searchers[i % 5], entry.rawBattleSearcherWord)
                assertEquals(wins[i % 5] == 1L, entry.isWin); assertEquals(searchers[i % 5] == 1L, entry.isBattleSearcher)
                assertEquals(i % 5 < 2, entry.hasCanonicalWords)
                assertEquals(b.copyOfRange(base + i * 8, base + (i + 1) * 8).map { it.toInt() and 255 }, entry.rawBytes)
            }
            assertEquals(142, read.winFlagCount); assertEquals(142, read.battleSearcherFlagCount); assertEquals(423, read.unknownEntryCount)
            assertEquals(b.copyOfRange(base, base + 5656).map { it.toInt() and 255 }, read.rawStatusBytes)
            assertEquals(0xA5, b[base - 1].toInt() and 255); assertEquals(0x5A, b[base + 5656].toInt() and 255); assertArrayEquals(before, b)
        }
    }

    @Test fun fullWordEqualityNeverBecomesLowByteLowBitNonzeroOrSignedBooleanLogic() {
        for (word in listOf(0L, 1L, 2L, 3L, 255L, 256L, 257L, 0x01000000L, 0x01000001L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
            val b = fixture(); for (i in 0 until 707) { put(b, base + i * 8, word); put(b, base + i * 8 + 4, word) }; seal(b)
            val before = b.copyOf(); val read = BdspBattleTrainerStatusReadOnly.read(b)
            assertEquals(if (word == 1L) 707 else 0, read.winFlagCount); assertEquals(if (word == 1L) 707 else 0, read.battleSearcherFlagCount)
            assertEquals(if (word in 0L..1L) 0 else 707, read.unknownEntryCount)
            assertEquals(word, read.entry(706).rawWinWord); assertEquals(word, read.entry(706).rawBattleSearcherWord); assertArrayEquals(before, b)
        }
        for (bit in 0 until 32) {
            val b = fixture(); val word = 1L shl bit; put(b, base, word); put(b, base + 4, word); seal(b)
            val before = b.copyOf(); val entry = BdspBattleTrainerStatusReadOnly.read(b).entry(0)
            assertEquals(word, entry.rawWinWord); assertEquals(word, entry.rawBattleSearcherWord)
            assertEquals(bit == 0, entry.isWin); assertEquals(bit == 0, entry.isBattleSearcher); assertArrayEquals(before, b)
        }
    }

    @Test fun filtersPreserveOriginalIndicesAndUnknownWordsCanOverlapASetFlagWithoutBecomingClear() {
        val b = fixture(); b.fill(0, base, base + 5656)
        for ((i, pair) in mapOf(0 to (1L to 0L), 1 to (0L to 1L), 2 to (2L to 1L), 3 to (1L to 256L), 706 to (1L to 1L))) {
            put(b, base + i * 8, pair.first); put(b, base + i * 8 + 4, pair.second)
        }
        seal(b); val before = b.copyOf(); val read = BdspBattleTrainerStatusReadOnly.read(b)
        assertEquals((0..706).toList(), read.filtered(BdspBattleTrainerFilter.ALL).map { it.index })
        assertEquals(listOf(0, 3, 706), read.filtered(BdspBattleTrainerFilter.WIN_SET).map { it.index })
        assertEquals(listOf(1, 2, 706), read.filtered(BdspBattleTrainerFilter.SEARCHER_SET).map { it.index })
        assertEquals(listOf(2, 3), read.filtered(BdspBattleTrainerFilter.UNKNOWN_WORDS).map { it.index })
        assertEquals(3, read.winFlagCount); assertEquals(3, read.battleSearcherFlagCount); assertEquals(2, read.unknownEntryCount)
        for (index in listOf(-1, 707, Int.MIN_VALUE, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { read.entry(index) }
        assertArrayEquals(before, b)
    }

    @Test fun nativeIntegrityIdentityAndExactLengthPrecedeEveryTrainerFlagRead() {
        for (layout in layouts.indices) {
            val source = fixture(layout)
            for (o in setOf(0, 4, base - 1, base, base + 3, base + 4, base + 7, base + 8, base + 5648, base + 5655, base + 5656, 0xE9818, source.lastIndex)) {
                val bad = source.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspBattleTrainerStatusReadOnly.read(bad) }
                assertNull(session(bad).bdspBattleTrainerStatus); assertArrayEquals(before, bad)
            }
            val title = source.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = source.copyOf(); put(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, source.copyOf(source.size - 1), source.copyOf(source.size + 1)))
                assertThrows(IllegalArgumentException::class.java) { BdspBattleTrainerStatusReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4)))
            assertThrows(IllegalArgumentException::class.java) { BdspBattleTrainerStatusReadOnly.read(bad) }
    }

    @Test fun trainerFlagsDoNotReenableRejectedTrainerProfilePartyOrDexAndKeepOtherReadersIndependent() {
        val badCases = listOf(fixture().also { put(it, 0x79B78, 6) },
            fixture().also { put(it, 0x79BD4, 1000000) }, fixture().also { it[0x79BD8] = 2 },
            fixture().also { it[0x79BDD] = 9 }, fixture().also { it[0x79BE0] = 2 },
            fixture().also { it[0x79BB4] = 0; it[0x79BB5] = 0xD8.toByte() })
        for (b in badCases) {
            b[0x14098 + 6 * 344] = 7; put(b, 0x7A328, 4); seal(b)
            val before = b.copyOf(); val bound = session(b)
            assertTrue(bound.isFormatVerified); assertNull(bound.bdspTrainer); assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex); assertNotNull(bound.bdspBattleTrainerStatus)
            assertNull(bound.bdspBattleTrainerStatusUnavailableReason); assertEquals(707, bound.bdspBattleTrainerStatus!!.entries.size)
            assertNotNull(bound.bdspAdditionalRecords); assertNotNull(bound.bdspRecordStatistics); assertNotNull(bound.bdspUndergroundActivity)
            assertArrayEquals(before, b)
        }
    }

    @Test fun detachedImmutableListsUseNativeIdentityAndNeverAlterOriginalWordsOrEarlierSections() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); val before = b.copyOf(); val bound = session(b, hint); val read = bound.bdspBattleTrainerStatus!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family); assertEquals(title, read.version.romCode)
            b.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy())
            assertEquals(1L, read.entry(706).rawWinWord); assertEquals(0L, read.entry(706).rawBattleSearcherWord)
            assertThrows(UnsupportedOperationException::class.java) { (read.entries as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.entry(0).rawBytes as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.rawStatusBytes as MutableList<*>).clear() }
            for (filter in BdspBattleTrainerFilter.values()) assertThrows(UnsupportedOperationException::class.java) { (read.filtered(filter) as MutableList<*>).clear() }
            assertNotNull(bound.bdspTrainer); assertNotNull(bound.bdspAdditionalRecords); assertNotNull(bound.bdspRecordStatistics)
            assertNotNull(bound.bdspUndergroundActivity); assertNotNull(bound.bdspGameOptions); assertNotNull(bound.bdspBattleTower)
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspBattleTrainerStatus); assertNull(other.bdspBattleTrainerStatusUnavailableReason)
    }
}
