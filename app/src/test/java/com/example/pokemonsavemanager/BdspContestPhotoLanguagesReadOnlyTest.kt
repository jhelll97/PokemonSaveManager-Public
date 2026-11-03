package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspContestPhotoLanguagesReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0xCEA1C
    private fun put(b: ByteArray, offset: Int, value: Long) { repeat(4) { b[offset + it] = (value ushr (8 * it)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put(b, 0, layouts[layout].second.toLong()); put(b, 0x79B78, 2); b[0x79BDF] = title.toByte()
        for ((i, id) in listOf(1, 2, 6, 128, 255).withIndex()) b[base + i] = id.toByte()
        for (i in 5 until 24) b[base + i] = (i * 37).toByte()
        b[base - 1] = 0xA5.toByte(); b[base + 24] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllRevisionsKeepFiveNativeCategoryBytesAndExactRawBoundaries() {
        for (l in layouts.indices) for (t in 0..1) {
            val b = fixture(l, t); val before = b.copyOf(); val read = BdspContestPhotoLanguagesReadOnly.read(b)
            assertEquals(t, read.version.romCode); assertEquals(layouts[l].second.toLong(), read.revision.code)
            assertEquals(listOf(1, 2, 6, 128, 255), read.entries.map { it.languageId })
            assertEquals(listOf("Style", "Beautiful", "Cute", "Clever", "Strong"), read.entries.map { it.category.nativeName })
            for (i in 0 until 5) { assertEquals(i, read.entry(i).index); assertSame(read.entries[i], read.entry(i)); assertEquals(BdspContestPhotoCategory.values()[i], read.entry(i).category) }
            assertEquals(before.copyOfRange(base, base + 24).map { it.toInt() and 255 }, read.rawBytes)
            assertEquals(before.copyOfRange(base + 5, base + 24).map { it.toInt() and 255 }, read.reservedBytes)
            assertEquals(0xA5, b[base - 1].toInt() and 255); assertEquals(0x5A, b[base + 24].toInt() and 255); assertArrayEquals(before, b)
        }
    }

    @Test fun all256LanguageByteValuesAreRetainedWithoutTrainerLanguageNormalization() {
        for (id in 0..255) {
            val b = fixture(); b.fill(id.toByte(), base, base + 5); seal(b); val before = b.copyOf()
            val read = BdspContestPhotoLanguagesReadOnly.read(b)
            assertEquals(List(5) { id }, read.entries.map { it.languageId }); assertEquals(5, read.entries.size)
            assertEquals(before.copyOfRange(base + 5, base + 24).map { it.toInt() and 255 }, read.reservedBytes); assertArrayEquals(before, b)
        }
    }

    @Test fun reservedBytesStayOpaqueAndInvalidIndicesAreRejected() {
        for (v in listOf(0, 1, 127, 128, 255)) {
            val b = fixture(); b.fill(v.toByte(), base + 5, base + 24); seal(b); val read = BdspContestPhotoLanguagesReadOnly.read(b)
            assertEquals(List(19) { v }, read.reservedBytes); assertEquals(listOf(1, 2, 6, 128, 255), read.entries.map { it.languageId })
            for (i in listOf(-1, 5, Int.MIN_VALUE, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { read.entry(i) }
        }
    }

    @Test fun nativeWholeFileIntegrityTitleRevisionAndExactLengthPrecedeByteExtraction() {
        for (l in layouts.indices) {
            val b = fixture(l)
            for (o in setOf(0, 4, base - 1, base, base + 4, base + 5, base + 23, base + 24, 0xE9818, b.lastIndex)) {
                val bad = b.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspContestPhotoLanguagesReadOnly.read(bad) }
                assertNull(session(bad).bdspContestPhotoLanguages); assertArrayEquals(before, bad)
            }
            val title = b.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = b.copyOf(); put(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, b.copyOf(b.size - 1), b.copyOf(b.size + 1))) assertThrows(IllegalArgumentException::class.java) { BdspContestPhotoLanguagesReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4))) assertThrows(IllegalArgumentException::class.java) { BdspContestPhotoLanguagesReadOnly.read(bad) }
    }

    @Test fun independentPhotoLanguageReaderLeavesStrictTrainerPartyAndDexGatesIntact() {
        val cases = listOf(fixture().also { put(it, 0x79B78, 6) }, fixture().also { put(it, 0x79BD4, 1000000) },
            fixture().also { it[0x79BD8] = 2 }, fixture().also { it[0x79BDD] = 9 }, fixture().also { it[0x79BE0] = 2 },
            fixture().also { it[0x79BB4] = 0; it[0x79BB5] = 0xD8.toByte() })
        for (b in cases) {
            b[0x14098 + 6 * 344] = 7; put(b, 0x7A328, 4); seal(b); val before = b.copyOf(); val s = session(b)
            assertTrue(s.isFormatVerified); assertNull(s.bdspTrainer); assertFalse(s.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertNull(s.bdspStorage); assertNull(s.bdspPokedex); assertNotNull(s.bdspContestPhotoLanguages); assertNull(s.bdspContestPhotoLanguagesUnavailableReason)
            assertNotNull(s.bdspContest); assertNotNull(s.bdspBallCapsules); assertArrayEquals(before, b)
        }
    }

    @Test fun detachedImmutableLanguageViewsPreserveNativeIdentityEarlierViewsAndWriteLocks() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); val before = b.copyOf(); val s = session(b, hint); val read = s.bdspContestPhotoLanguages!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, s.profile.family); assertEquals(title, read.version.romCode)
            b.fill(0); s.snapshot.bytesCopy().fill(0); assertArrayEquals(before, s.snapshot.bytesCopy()); assertEquals(255, read.entry(4).languageId)
            for (list in listOf(read.entries, read.rawBytes, read.reservedBytes)) assertThrows(UnsupportedOperationException::class.java) { (list as MutableList<*>).clear() }
            assertNotNull(s.bdspTrainer); assertNotNull(s.bdspContest); assertNotNull(s.bdspInventory); assertNotNull(s.bdspSealStickers); assertNotNull(s.bdspBallCapsules)
            assertTrue(s.isReadOnly); assertFalse(s.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspContestPhotoLanguages); assertNull(other.bdspContestPhotoLanguagesUnavailableReason)
    }
}
