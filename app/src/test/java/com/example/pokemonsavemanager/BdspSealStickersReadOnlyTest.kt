package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspSealStickersReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0x93E0C
    private val words = listOf(0L, 1L, 2L, 255L, 256L, 257L, 0x10000L, 0x10001L,
        0x1000000L, 0x1000001L, 0x80000000L, 0xFFFFFFFFL)
    private fun put(b: ByteArray, o: Int, v: Long) { repeat(4) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put(b, 0, layouts[layout].second.toLong()); put(b, 0x79B78, 2); b[0x79BDF] = title.toByte()
        repeat(200) { i -> put(b, base + i * 12, words[i % words.size]); put(b, base + i * 12 + 4, (i - 100).toLong()); put(b, base + i * 12 + 8, (100 - i).toLong()) }
        b[base - 1] = 0xA5.toByte(); b[base + 0x960] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllRevisionsRetainEveryEntryAndExactNativeBoundaries() {
        for (layout in layouts.indices) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val read = BdspSealStickersReadOnly.read(b)
            assertEquals(title, read.version.romCode); assertEquals(layouts[layout].second.toLong(), read.revision.code)
            assertEquals(200, read.entries.size); assertEquals(2400, read.rawSealBytes.size)
            for (i in 0 until 200) {
                val entry = read.entry(i); assertSame(entry, read.entries[i]); assertEquals(i, entry.index)
                assertEquals(words[i % words.size], entry.rawObtainedWord); assertEquals(i - 100, entry.count); assertEquals(100 - i, entry.totalCount)
                assertEquals(before.copyOfRange(base + i * 12, base + (i + 1) * 12).map { it.toInt() and 255 }, entry.rawBytes)
            }
            assertEquals(before.copyOfRange(base, base + 0x960).map { it.toInt() and 255 }, read.rawSealBytes)
            assertEquals(0xA5, b[base - 1].toInt() and 255); assertEquals(0x5A, b[base + 0x960].toInt() and 255); assertArrayEquals(before, b)
            for (bad in listOf(-1, 200, Int.MIN_VALUE, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { read.entry(bad) }
        }
    }

    @Test fun obtainedUsesEntireWordAndPreservesUnknownFlagsWithNativeIndices() {
        for (word in words + (0 until 32).map { 1L shl it }) {
            val b = fixture(); repeat(200) { put(b, base + it * 12, word) }; seal(b); val before = b.copyOf(); val read = BdspSealStickersReadOnly.read(b)
            assertEquals(if (word == 1L) 200 else 0, read.obtainedFlagCount)
            for (entry in read.entries) { assertEquals(word, entry.rawObtainedWord); assertEquals(word == 1L, entry.isGet); assertEquals(word in 0L..1L, entry.hasCanonicalFlag) }
            assertEquals(if (word == 1L) (0 until 200).toList() else emptyList<Int>(), read.filtered(BdspSealStickerFilter.GET_SET).map { it.index })
            assertEquals(if (word > 1L) (0 until 200).toList() else emptyList<Int>(), read.filtered(BdspSealStickerFilter.UNKNOWN_FLAGS).map { it.index }); assertArrayEquals(before, b)
        }
    }

    @Test fun signedCountsAndFiltersDoNotClampDiscardOrRenumberUnlistedSeals() {
        for (value in listOf(Int.MIN_VALUE, -1, 0, 1, 99, 100, Int.MAX_VALUE)) {
            val b = fixture(); repeat(200) { put(b, base + it * 12 + 4, value.toLong()); put(b, base + it * 12 + 8, value.toLong()) }; seal(b)
            val read = BdspSealStickersReadOnly.read(b); assertTrue(read.entries.all { it.count == value && it.totalCount == value })
            assertEquals(if (value == 0) 0 else 200, read.filtered(BdspSealStickerFilter.NONZERO_COUNTS).size)
        }
        val read = BdspSealStickersReadOnly.read(fixture())
        assertEquals((0 until 200).toList(), read.filtered(BdspSealStickerFilter.ALL).map { it.index })
        assertEquals((0 until 200).filter { it != 100 }, read.filtered(BdspSealStickerFilter.NONZERO_COUNTS).map { it.index })
        assertEquals(listOf(0) + (97 until 200).toList(), read.filtered(BdspSealStickerFilter.UNLISTED).map { it.index })
        assertEquals("HEART_A", read.entry(1).nativeLabel); assertEquals("CHAMPION_A", read.entry(96).nativeLabel)
        for (id in listOf(-1, 0, 97, 199, 200, Int.MAX_VALUE)) assertNull(BdspSealStickerLabels.nativeLabel(id))
    }

    @Test fun wholeFileMd5ExactSizeRevisionAndNativeTitlePrecedeExtraction() {
        for (layout in layouts.indices) {
            val b = fixture(layout)
            for (o in setOf(0, 4, base - 1, base, base + 3, base + 4, base + 7, base + 8, base + 11, base + 0x95F, base + 0x960, 0xE9818, b.lastIndex)) {
                val bad = b.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspSealStickersReadOnly.read(bad) }; assertNull(session(bad).bdspSealStickers); assertArrayEquals(before, bad)
            }
            val title = b.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = b.copyOf(); put(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, b.copyOf(b.size - 1), b.copyOf(b.size + 1))) assertThrows(IllegalArgumentException::class.java) { BdspSealStickersReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4))) assertThrows(IllegalArgumentException::class.java) { BdspSealStickersReadOnly.read(bad) }
    }

    @Test fun independentSealsReaderDoesNotRelaxStrictTrainerPartyOrDexGates() {
        val cases = listOf(fixture().also { put(it, 0x79B78, 6) }, fixture().also { put(it, 0x79BD4, 1000000) },
            fixture().also { it[0x79BD8] = 2 }, fixture().also { it[0x79BDD] = 9 }, fixture().also { it[0x79BE0] = 2 },
            fixture().also { it[0x79BB4] = 0; it[0x79BB5] = 0xD8.toByte() })
        for (b in cases) {
            b[0x14098 + 6 * 344] = 7; put(b, 0x7A328, 4); seal(b); val before = b.copyOf(); val bound = session(b)
            assertTrue(bound.isFormatVerified); assertNull(bound.bdspTrainer); assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex); assertNotNull(bound.bdspSealStickers); assertNull(bound.bdspSealStickersUnavailableReason)
            assertEquals(200, bound.bdspSealStickers!!.entries.size); assertNotNull(bound.bdspUndergroundReturn); assertArrayEquals(before, b)
        }
    }

    @Test fun immutableDetachedEntriesUseNativeIdentityAndPreserveEarlierViewsAndWriteLocks() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); val before = b.copyOf(); val bound = session(b, hint); val read = bound.bdspSealStickers!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family); assertEquals(title, read.version.romCode)
            b.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy()); assertEquals(-100, read.entry(0).count)
            for (list in listOf(read.entries, read.rawSealBytes, read.entry(199).rawBytes, read.filtered(BdspSealStickerFilter.UNLISTED)))
                assertThrows(UnsupportedOperationException::class.java) { (list as MutableList<*>).clear() }
            assertNotNull(bound.bdspTrainer); assertNotNull(bound.bdspInventory); assertNotNull(bound.bdspPoffins); assertNotNull(bound.bdspUnderground)
            assertNotNull(bound.bdspUndergroundReturn); assertNotNull(bound.bdspUndergroundNpc); assertNotNull(bound.bdspSecretBase); assertNotNull(bound.bdspBerryPlots)
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspSealStickers); assertNull(other.bdspSealStickersUnavailableReason)
    }
}
