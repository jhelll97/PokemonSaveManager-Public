package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspRandomGroupsReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0x9476C
    private fun put(b: ByteArray, o: Int, v: Long, count: Int = 4) { repeat(count) { b[o + it] = (v ushr (8 * it)).toByte() } }
    private fun name(b: ByteArray, o: Int, count: Int, text: String) { b.fill(0, o, o + count); text.toByteArray(Charsets.UTF_16LE).copyInto(b, o) }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put(b, 0, layouts[layout].second.toLong()); put(b, 0x79B78, 2); b[0x79BDF] = title.toByte()
        repeat(12) { i ->
            val o = base + i * 0x84; name(b, o, 32, "G$i"); name(b, o + 32, 64, "P$i")
            b[o + 0x60] = (i % 2).toByte(); b[o + 0x61] = i.toByte(); b[o + 0x62] = 0xFF.toByte(); b[o + 0x63] = 0x80.toByte()
            put(b, o + 0x64, (i - 6).toLong()); put(b, o + 0x68, (ULong.MAX_VALUE - i.toULong()).toLong(), 8)
            put(b, o + 0x70, Long.MIN_VALUE + i, 8); put(b, o + 0x78, Long.MIN_VALUE + i, 8); put(b, o + 0x80, (Int.MIN_VALUE + i).toLong())
        }
        b[base - 1] = 0xA5.toByte(); b[base + 0x630] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllRevisionsKeepTwelveComputedLayoutRecordsAndExactBoundaries() {
        for (l in layouts.indices) for (t in 0..1) {
            val b = fixture(l, t); val before = b.copyOf(); val read = BdspRandomGroupsReadOnly.read(b)
            assertEquals(t, read.version.romCode); assertEquals(layouts[l].second.toLong(), read.revision.code); assertEquals(12, read.records.size)
            for (i in 0 until 12) {
                val o = base + i * 0x84; val r = read.record(i); assertEquals(i, r.index); assertSame(r, read.records[i])
                assertEquals("G$i", r.groupName.text); assertEquals("P$i", r.playerName.text); assertNull(r.groupName.unavailableReason); assertNull(r.playerName.unavailableReason)
                assertEquals(i % 2, r.rawMaleByte); assertEquals(i % 2 == 1, r.male); assertEquals(listOf(i, 255, 128), r.alignmentBytes)
                assertEquals(i - 6, r.regionCode); assertEquals(Int.MIN_VALUE + i, r.userId); assertEquals(ULong.MAX_VALUE - i.toULong(), r.seed)
                assertEquals((Long.MIN_VALUE + i).toULong(), r.random); assertEquals(Long.MIN_VALUE + i, r.ticks)
                assertEquals(before.copyOfRange(o, o + 0x84).map { it.toInt() and 255 }, r.rawBytes)
                assertEquals(before.copyOfRange(o, o + 32).map { it.toInt() and 255 }, r.groupName.rawBytes)
                assertEquals(before.copyOfRange(o + 32, o + 96).map { it.toInt() and 255 }, r.playerName.rawBytes)
            }
            assertEquals(before.copyOfRange(base, base + 0x630).map { it.toInt() and 255 }, read.rawBytes)
            assertEquals(read.rawBytes, read.records.flatMap { it.rawBytes }); assertArrayEquals(before, b)
            assertEquals(165, b[base - 1].toInt() and 255); assertEquals(90, b[base + 0x630].toInt() and 255)
            for (index in listOf(-1, 12, Int.MIN_VALUE, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { read.record(index) }
        }
    }

    @Test fun unsigned64SignedFieldsAndByteBooleanPreserveAllBitsAndAlignment() {
        val words = listOf(0uL, 1uL, Long.MAX_VALUE.toULong(), 1uL shl 63, ULong.MAX_VALUE) + (0 until 64).map { 1uL shl it }
        for (word in words) {
            val b = fixture(); for (o in listOf(0x68, 0x70, 0x78)) put(b, base + o, word.toLong(), 8); seal(b); val before = b.copyOf()
            val r = BdspRandomGroupsReadOnly.read(b).record(0); assertEquals(word, r.seed); assertEquals(word, r.random); assertEquals(word.toLong(), r.ticks); assertArrayEquals(before, b)
        }
        for (value in listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)) {
            val b = fixture(); for (o in listOf(0x64, 0x80)) put(b, base + o, value.toLong()); seal(b)
            val r = BdspRandomGroupsReadOnly.read(b).record(0); assertEquals(value, r.regionCode); assertEquals(value, r.userId)
        }
        for (value in 0..255) {
            val b = fixture(); b[base + 0x60] = value.toByte(); seal(b); val r = BdspRandomGroupsReadOnly.read(b).record(0)
            assertEquals(value, r.rawMaleByte); assertEquals(value == 1, r.male); assertEquals(value in 0..1, r.hasCanonicalMaleByte); assertEquals(listOf(0, 255, 128), r.alignmentBytes)
        }
    }

    @Test fun fullWidthUnicodeAndMalformedNamesAreHandledIndividuallyWithoutLosingRawData() {
        val full = fixture(); name(full, base, 32, "G".repeat(16)); name(full, base + 32, 64, "P".repeat(32)); seal(full)
        val r = BdspRandomGroupsReadOnly.read(full).record(0); assertEquals("G".repeat(16), r.groupName.text); assertEquals("P".repeat(32), r.playerName.text)
        val unicode = fixture(); name(unicode, base, 32, "é雪😀"); name(unicode, base + 32, 64, "\uE08E"); seal(unicode)
        val u = BdspRandomGroupsReadOnly.read(unicode).record(0); assertEquals("é雪😀", u.groupName.text); assertEquals("\uE08E", u.playerName.text)
        val trailing = fixture(); name(trailing, base, 32, "A"); put(trailing, base + 4, 0xD800, 2); seal(trailing)
        val tr = BdspRandomGroupsReadOnly.read(trailing).record(0); assertEquals("A", tr.groupName.text); assertEquals(0xD8, tr.groupName.rawBytes[5])
        for ((offset, count) in listOf(0 to 32, 32 to 64)) for (units in listOf(listOf(0xD800), listOf(0xDC00), listOf(0xD800, 0x41), listOf(10))) {
            val b = fixture(); b.fill(0, base + offset, base + offset + count); units.forEachIndexed { i, v -> put(b, base + offset + i * 2, v.toLong(), 2) }; seal(b); val before = b.copyOf()
            val read = BdspRandomGroupsReadOnly.read(b); val first = read.record(0); val bad = if (offset == 0) first.groupName else first.playerName
            assertNull(bad.text); assertNotNull(bad.unavailableReason); assertEquals(before.copyOfRange(base + offset, base + offset + count).map { it.toInt() and 255 }, bad.rawBytes)
            assertEquals(if (offset == 0) "P0" else "G0", if (offset == 0) first.playerName.text else first.groupName.text)
            assertEquals("G11", read.record(11).groupName.text); assertEquals(ULong.MAX_VALUE, first.seed); assertEquals(Long.MIN_VALUE, first.ticks); assertArrayEquals(before, b)
        }
        val zero = fixture(); zero.fill(0, base, base + 0x630); seal(zero)
        assertTrue(BdspRandomGroupsReadOnly.read(zero).records.all { it.groupName.text == "" && it.playerName.text == "" })
    }

    @Test fun nativeIntegrityIdentityRevisionAndExactLengthPrecedeAllRecordReads() {
        for (l in layouts.indices) {
            val b = fixture(l)
            for (o in setOf(0, 4, base - 1, base, base + 31, base + 32, base + 95, base + 0x60, base + 0x63, base + 0x64, base + 0x68,
                base + 0x70, base + 0x78, base + 0x80, base + 0x83, base + 0x62F, base + 0x630, 0xE9818, b.lastIndex)) {
                val bad = b.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspRandomGroupsReadOnly.read(bad) }; assertNull(session(bad).bdspRandomGroups); assertArrayEquals(before, bad)
            }
            val title = b.copyOf(); title[0x79BDF] = 2; seal(title); val revision = b.copyOf(); put(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, b.copyOf(b.size - 1), b.copyOf(b.size + 1))) assertThrows(IllegalArgumentException::class.java) { BdspRandomGroupsReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4))) assertThrows(IllegalArgumentException::class.java) { BdspRandomGroupsReadOnly.read(bad) }
    }

    @Test fun independentGroupsReaderDoesNotRelaxStrictTrainerPartyAndDexGates() {
        val cases = listOf(fixture().also { put(it, 0x79B78, 6) }, fixture().also { put(it, 0x79BD4, 1000000) },
            fixture().also { it[0x79BD8] = 2 }, fixture().also { it[0x79BDD] = 9 }, fixture().also { it[0x79BE0] = 2 },
            fixture().also { it[0x79BB4] = 0; it[0x79BB5] = 0xD8.toByte() })
        for (b in cases) {
            b[0x14098 + 6 * 344] = 7; put(b, 0x7A328, 4); seal(b); val before = b.copyOf(); val s = session(b)
            assertTrue(s.isFormatVerified); assertNull(s.bdspTrainer); assertFalse(s.supports(SwitchReadCapability.TRAINER_PROFILE)); assertNull(s.bdspStorage); assertNull(s.bdspPokedex)
            assertNotNull(s.bdspRandomGroups); assertNull(s.bdspRandomGroupsUnavailableReason); assertNotNull(s.bdspUnionRoom); assertArrayEquals(before, b)
        }
    }

    @Test fun detachedImmutableRecordsPreserveNativeIdentityEarlierViewsAndWriteLocks() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); val before = b.copyOf(); val s = session(b, hint); val read = s.bdspRandomGroups!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, s.profile.family); assertEquals(title, read.version.romCode)
            b.fill(0); s.snapshot.bytesCopy().fill(0); assertArrayEquals(before, s.snapshot.bytesCopy()); assertEquals("G0", read.record(0).groupName.text); assertEquals(ULong.MAX_VALUE, read.record(0).seed)
            for (list in listOf(read.records, read.rawBytes, read.record(0).rawBytes, read.record(0).alignmentBytes, read.record(0).groupName.rawBytes, read.record(0).playerName.rawBytes))
                assertThrows(UnsupportedOperationException::class.java) { (list as MutableList<*>).clear() }
            assertNotNull(s.bdspTrainer); assertNotNull(s.bdspInventory); assertNotNull(s.bdspUnionRoom); assertNotNull(s.bdspContestPhotoLanguages); assertTrue(s.isReadOnly); assertFalse(s.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspRandomGroups); assertNull(other.bdspRandomGroupsUnavailableReason)
    }
}
