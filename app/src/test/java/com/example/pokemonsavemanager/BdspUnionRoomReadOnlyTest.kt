package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspUnionRoomReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0xCEA10
    private val flags = listOf(0L, 1L, 2L, 255L, 256L, 257L, 0x10000L, 0x10001L, 0x1000000L, 0x1000001L, 0x80000000L, 0xFFFFFFFFL)
    private val floatWords = listOf(0L, 0x80000000L, 1L, 0x80000001L, 0x007FFFFFL, 0x00800000L, 0x3F800000L, 0x3F800001L,
        0xBF800000L, 0x7F7FFFFFL, 0xFF7FFFFFL, 0x7F800000L, 0xFF800000L, 0x7FC01234L, 0xFFC05678L, 0x7F801234L, 0xFFFFFFFFL)
    private fun put(b: ByteArray, o: Int, v: Long) { repeat(4) { b[o + it] = (v ushr (8 * it)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put(b, 0, layouts[layout].second.toLong()); put(b, 0x79B78, 2); b[0x79BDF] = title.toByte()
        put(b, base, 257); put(b, base + 4, -1); put(b, base + 8, 0x7F801234L)
        b[base - 1] = 0xA5.toByte(); b[base + 12] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllRevisionsExposeAllThreeRawWordsAndExactTwelveByteBoundary() {
        for (l in layouts.indices) for (t in 0..1) {
            val b = fixture(l, t); val before = b.copyOf(); val read = BdspUnionRoomReadOnly.read(b)
            assertEquals(t, read.version.romCode); assertEquals(layouts[l].second.toLong(), read.revision.code)
            assertEquals(257L, read.rawInitTalkWord); assertFalse(read.isInitTalk); assertFalse(read.hasCanonicalTalkFlag)
            assertEquals(0xFFFFFFFFL, read.rawPenaltyCountWord); assertEquals(-1, read.penaltyCount)
            assertEquals(0x7F801234L, read.rawPenaltyTimeBits); assertTrue(read.penaltyTime.isNaN())
            assertEquals(before.copyOfRange(base, base + 12).map { it.toInt() and 255 }, read.rawBytes)
            assertEquals(0xA5, b[base - 1].toInt() and 255); assertEquals(0x5A, b[base + 12].toInt() and 255); assertArrayEquals(before, b)
        }
    }

    @Test fun talkFlagUsesCompleteWordAndSignedCountPreservesExtremesWithoutNormalization() {
        for (word in flags + (0 until 32).map { 1L shl it }) {
            val b = fixture(); put(b, base, word); seal(b); val before = b.copyOf(); val read = BdspUnionRoomReadOnly.read(b)
            assertEquals(word, read.rawInitTalkWord); assertEquals(word == 1L, read.isInitTalk); assertEquals(word in 0L..1L, read.hasCanonicalTalkFlag); assertArrayEquals(before, b)
        }
        for (value in listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)) {
            val b = fixture(); put(b, base + 4, value.toLong()); seal(b); val read = BdspUnionRoomReadOnly.read(b)
            assertEquals(value, read.penaltyCount); assertEquals(value.toLong() and 0xFFFFFFFFL, read.rawPenaltyCountWord)
            // Legacy SAV8BS.UnionRoomPenaltyTime reads this same +4 word as UInt32; it is not used as an elapsed/remaining time.
            assertEquals(0x7F801234L, read.rawPenaltyTimeBits)
        }
    }

    @Test fun penaltyFloatPreservesExactRawBitsIncludingSignedZerosInfinitiesAndNanPayloads() {
        for (word in floatWords + (0 until 32).map { 1L shl it }) {
            val b = fixture(); put(b, base + 8, word); seal(b); val before = b.copyOf(); val read = BdspUnionRoomReadOnly.read(b)
            assertEquals(word, read.rawPenaltyTimeBits)
            val nan = (word and 0x7F800000L) == 0x7F800000L && (word and 0x007FFFFFL) != 0L
            if (nan) assertTrue(read.penaltyTime.isNaN()) else assertEquals(word.toInt(), read.penaltyTime.toRawBits())
            // A VM may quiet a materialized signaling NaN; the original UInt32 word above is authoritative.
            assertEquals(before.copyOfRange(base + 8, base + 12).map { it.toInt() and 255 }, read.rawBytes.subList(8, 12)); assertArrayEquals(before, b)
        }
    }

    @Test fun nativeWholeFileIntegrityTitleRevisionAndLengthPrecedeEveryField() {
        for (l in layouts.indices) {
            val b = fixture(l)
            for (o in setOf(0, 4, base - 1, base, base + 3, base + 4, base + 7, base + 8, base + 11, base + 12, 0xE9818, b.lastIndex)) {
                val bad = b.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspUnionRoomReadOnly.read(bad) }; assertNull(session(bad).bdspUnionRoom); assertArrayEquals(before, bad)
            }
            val title = b.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = b.copyOf(); put(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, b.copyOf(b.size - 1), b.copyOf(b.size + 1))) assertThrows(IllegalArgumentException::class.java) { BdspUnionRoomReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4))) assertThrows(IllegalArgumentException::class.java) { BdspUnionRoomReadOnly.read(bad) }
    }

    @Test fun independentUnionReaderDoesNotRelaxStrictTrainerPartyAndDexGates() {
        val cases = listOf(fixture().also { put(it, 0x79B78, 6) }, fixture().also { put(it, 0x79BD4, 1000000) },
            fixture().also { it[0x79BD8] = 2 }, fixture().also { it[0x79BDD] = 9 }, fixture().also { it[0x79BE0] = 2 },
            fixture().also { it[0x79BB4] = 0; it[0x79BB5] = 0xD8.toByte() })
        for (b in cases) {
            b[0x14098 + 6 * 344] = 7; put(b, 0x7A328, 4); seal(b); val before = b.copyOf(); val s = session(b)
            assertTrue(s.isFormatVerified); assertNull(s.bdspTrainer); assertFalse(s.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertNull(s.bdspStorage); assertNull(s.bdspPokedex); assertNotNull(s.bdspUnionRoom); assertNull(s.bdspUnionRoomUnavailableReason)
            assertNotNull(s.bdspContestPhotoLanguages); assertNotNull(s.bdspContest); assertArrayEquals(before, b)
        }
    }

    @Test fun detachedImmutableUnionRecordPreservesNativeIdentityEarlierViewsAndWriteLocks() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); val before = b.copyOf(); val s = session(b, hint); val read = s.bdspUnionRoom!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, s.profile.family); assertEquals(title, read.version.romCode)
            b.fill(0); s.snapshot.bytesCopy().fill(0); assertArrayEquals(before, s.snapshot.bytesCopy()); assertEquals(-1, read.penaltyCount); assertEquals(0x7F801234L, read.rawPenaltyTimeBits)
            assertThrows(UnsupportedOperationException::class.java) { (read.rawBytes as MutableList<*>).clear() }
            assertNotNull(s.bdspTrainer); assertNotNull(s.bdspInventory); assertNotNull(s.bdspContest); assertNotNull(s.bdspContestPhotoLanguages); assertNotNull(s.bdspBallCapsules)
            assertTrue(s.isReadOnly); assertFalse(s.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspUnionRoom); assertNull(other.bdspUnionRoomUnavailableReason)
    }
}
