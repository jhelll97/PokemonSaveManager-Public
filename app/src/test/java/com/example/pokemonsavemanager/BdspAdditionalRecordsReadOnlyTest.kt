package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspAdditionalRecordsReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0xE9828
    private fun put(b: ByteArray, o: Int, v: Long, count: Int = 4) { repeat(count) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun name(b: ByteArray, index: Int, text: String) {
        val offset = base + index * 48; b.fill(0, offset, offset + 26); text.toByteArray(Charsets.UTF_16LE).copyInto(b, offset)
    }
    private fun fixture(layout: Int = 1, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put(b, 0, layouts[layout].second.toLong()); put(b, 0x79B78, 2); b[0x79BDF] = title.toByte()
        if (layout != 0) {
            for (i in 0 until 12) {
                val o = base + i * 48; name(b, i, "Name$i"); b[o + 24] = 0xEF.toByte(); b[o + 25] = 0xBE.toByte()
                b[o + 26] = 0xA1.toByte(); b[o + 27] = 0xA2.toByte(); put(b, o + 28, 2)
                b[o + 32] = (i * 23).toByte(); b[o + 33] = 0xB1.toByte(); b[o + 34] = 0xB2.toByte(); b[o + 35] = 0xB3.toByte()
                put(b, o + 36, -i.toLong()); put(b, o + 40, 0xFFFF0000L + i); put(b, o + 44, 0xDEADBEEFL)
            }
            for (i in 0 until 384) b[base + 576 + i] = (i * 37 + 11).toByte()
            b[base + 960] = 0x5A
        }
        seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun revision10ReportsNativeAbsenceWithoutSynthesizingTwelveEmptyRecords() {
        for (title in 0..1) {
            val b = fixture(0, title); val before = b.copyOf(); val read = BdspAdditionalRecordsReadOnly.read(b)
            assertFalse(read.hasBlock); assertNotNull(read.absenceReason); assertEquals(title, read.version.romCode)
            assertTrue(read.records.isEmpty()); assertTrue(read.rawRankingBytes.isEmpty()); assertTrue(read.rawBlockBytes.isEmpty())
            assertThrows(IllegalArgumentException::class.java) { read.record(0) }
            val bound = session(b); assertNotNull(bound.bdspAdditionalRecords); assertFalse(bound.bdspAdditionalRecords!!.hasBlock)
            assertNotNull(bound.bdspRecordStatistics); assertArrayEquals(before, b)
        }
    }

    @Test fun bothTitlesExpandedRevisionsExposeAllTwelveNativeRecordsAndExactRankingAndBlockBytes() {
        for (layout in 1..3) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val read = BdspAdditionalRecordsReadOnly.read(b)
            assertTrue(read.hasBlock); assertNull(read.absenceReason); assertEquals(title, read.version.romCode)
            assertEquals(layouts[layout].second.toLong(), read.revision.code); assertEquals(12, read.records.size)
            for (i in 0 until 12) {
                val entry = read.record(i); assertEquals(i, entry.index); assertEquals("Name$i", entry.name); assertNull(entry.nameIssue)
                assertTrue(entry.hasNameTerminator); assertEquals(2, entry.languageId); assertEquals(i * 23, entry.genderByte)
                assertEquals(-i, entry.bodyTypeId); assertEquals(0xFFFF0000L + i, entry.id32); assertEquals(i, entry.tid16); assertEquals(65535, entry.sid16)
                assertEquals(b.copyOfRange(base + i * 48, base + (i + 1) * 48).map { it.toInt() and 255 }, entry.rawRecordBytes)
            }
            assertEquals(b.copyOfRange(base + 576, base + 960).map { it.toInt() and 255 }, read.rawRankingBytes)
            assertEquals(b.copyOfRange(base, base + 960).map { it.toInt() and 255 }, read.rawBlockBytes)
            assertEquals(0x5A, b[base + 960].toInt() and 255); assertArrayEquals(before, b)
        }
    }

    @Test fun namesPreserveNativeNullAndFullFieldSemanticsSpacesSurrogatesPrivateGlyphsAndTrash() {
        for (text in listOf("", "ABCDEFGHIJKL", "ABCDEFGHIJKLM", " A ", "  ", "Pokémon", "東京", "A😀B", "")) {
            val b = fixture(); name(b, 0, text); seal(b); val before = b.copyOf(); val entry = BdspAdditionalRecordsReadOnly.read(b).record(0)
            assertEquals(text, entry.name); assertNull(entry.nameIssue); assertEquals(text.length < 13, entry.hasNameTerminator); assertArrayEquals(before, b)
        }
        val b = fixture(); name(b, 0, "A"); put(b, base + 4, 0xD800, 2); seal(b)
        val before = b.copyOf(); val read = BdspAdditionalRecordsReadOnly.read(b)
        assertEquals("A", read.record(0).name); assertNull(read.record(0).nameIssue) // Bytes after first NULL remain trash, not part of the displayed name.
        assertEquals(0, read.record(0).rawRecordBytes[2]); assertEquals(0xD8, read.record(0).rawRecordBytes[5]); assertArrayEquals(before, b)
    }

    @Test fun malformedAndControlNamesAreIsolatedWithoutHidingNumericDataOtherSlotsOrRankingBytes() {
        for (units in listOf(listOf(0xD800, 0), listOf(0xDC00, 0), listOf(0xD800, 65, 0), listOf(65, 10, 0), listOf(127, 0))) {
            val b = fixture(); b.fill(0, base, base + 26); for ((i, unit) in units.withIndex()) put(b, base + i * 2, unit.toLong(), 2); seal(b)
            val before = b.copyOf(); val read = BdspAdditionalRecordsReadOnly.read(b); val entry = read.record(0)
            assertNull(entry.name); assertNotNull(entry.nameIssue); assertEquals(0xFFFF0000L, entry.id32); assertEquals(2, entry.languageId)
            assertEquals("Name1", read.record(1).name); assertEquals(12, read.records.size); assertEquals(384, read.rawRankingBytes.size); assertArrayEquals(before, b)
        }
        val b = fixture(); b.fill(0, base, base + 960); seal(b)
        val read = BdspAdditionalRecordsReadOnly.read(b); assertTrue(read.hasBlock); assertEquals(12, read.records.size)
        assertTrue(read.records.all { it.name == "" && !it.hasNonzeroRecordBytes }); assertEquals(List(384) { 0 }, read.rawRankingBytes)
    }

    @Test fun unsignedIdsSignedWordsAndAllGenderBytesRemainRawWithoutValidityOrOwnershipInference() {
        for (word in listOf(0L, 1L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
            val b = fixture(); put(b, base + 28, word); put(b, base + 36, word); put(b, base + 40, word); seal(b)
            val entry = BdspAdditionalRecordsReadOnly.read(b).record(0)
            assertEquals(word.toInt(), entry.languageId); assertEquals(word.toInt(), entry.bodyTypeId); assertEquals(word, entry.id32)
            assertEquals((word and 65535).toInt(), entry.tid16); assertEquals((word ushr 16).toInt(), entry.sid16)
        }
        for (value in 0..255) {
            val b = fixture(); b[base + 32] = value.toByte(); seal(b); val before = b.copyOf()
            assertEquals(value, BdspAdditionalRecordsReadOnly.read(b).record(0).genderByte); assertArrayEquals(before, b)
        }
    }

    @Test fun nativeIntegrityIdentityAndExactLengthPrecedePresenceAndEveryRecordRead() {
        for (layout in layouts.indices) {
            val source = fixture(layout)
            val offsets = mutableSetOf(0, 4, 0xE9818, source.lastIndex)
            if (layout > 0) offsets.addAll(listOf(base, base + 25, base + 47, base + 48, base + 575, base + 576, base + 959, base + 960))
            for (o in offsets) {
                val bad = source.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspAdditionalRecordsReadOnly.read(bad) }
                assertNull(session(bad).bdspAdditionalRecords); assertArrayEquals(before, bad)
            }
            val title = source.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = source.copyOf(); put(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, source.copyOf(source.size - 1), source.copyOf(source.size + 1)))
                assertThrows(IllegalArgumentException::class.java) { BdspAdditionalRecordsReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xE9BE8)))
            assertThrows(IllegalArgumentException::class.java) { BdspAdditionalRecordsReadOnly.read(bad) }
    }

    @Test fun immutableDetachedSnapshotsKeepStrictTrainerRejectionAndOtherSectionsIndependent() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); put(b, 0x79B78, 6); b[0x14098 + 6 * 344] = 7; put(b, 0x7A328, 4); seal(b)
            val before = b.copyOf(); val bound = session(b, hint); val read = bound.bdspAdditionalRecords!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family); assertEquals(title, read.version.romCode)
            assertNull(bound.bdspTrainer); assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex)
            b.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy()); assertEquals("Name11", read.record(11).name)
            assertThrows(UnsupportedOperationException::class.java) { (read.records as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.record(0).rawRecordBytes as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.rawRankingBytes as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.rawBlockBytes as MutableList<*>).clear() }
            for (index in listOf(-1, 12, Int.MIN_VALUE, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { read.record(index) }
            assertNotNull(bound.bdspRecordStatistics); assertNotNull(bound.bdspUndergroundActivity); assertNotNull(bound.bdspGameOptions)
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspAdditionalRecords); assertNull(other.bdspAdditionalRecordsUnavailableReason)
    }
}
