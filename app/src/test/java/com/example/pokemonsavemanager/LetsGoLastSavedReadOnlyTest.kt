package com.example.pokemonsavemanager

import org.junit.Assert.*
import org.junit.Test

class LetsGoLastSavedReadOnlyTest {
    private val regions = listOf(0x00000 to 0x00D90, 0x00E00 to 0x00200, 0x01000 to 0x00168,
        0x01200 to 0x01800, 0x02A00 to 0x020E8, 0x04C00 to 0x00930, 0x05600 to 0x00004,
        0x05800 to 0x00130, 0x05A00 to 0x00012, 0x05C00 to 0x3F7A0, 0x45400 to 0x00008,
        0x45600 to 0x00E90, 0x46600 to 0x010A4, 0x47800 to 0x000F0, 0x47A00 to 0x06010,
        0x4DC00 to 0x00200, 0x4DE00 to 0x00098, 0x4E000 to 0x00068, 0x4E200 to 0x69780,
        0xB7A00 to 0x000B0, 0xB7C00 to 0x00940)
    private val base = 0x45400
    private fun put16(b: ByteArray, o: Int, v: Int) { b[o] = v.toByte(); b[o + 1] = (v ushr 8).toByte() }
    private fun put32(b: ByteArray, o: Int, v: Long) { repeat(4) { b[o + it] = (v ushr (8 * it)).toByte() } }
    private fun crc(b: ByteArray, o: Int, count: Int): Int {
        var c = 0
        for (i in o until o + count) { c = c xor (b[i].toInt() and 255); repeat(8) { c = if (c and 1 == 0) c ushr 1 else (c ushr 1) xor 0xA001 } }
        return c
    }
    private fun seal(b: ByteArray) {
        put32(b, 0xB8610, 0x42454546L)
        regions.forEachIndexed { id, p ->
            val d = 0xB8614 + id * 8; put32(b, d, p.second.toLong()); put16(b, d + 4, id); put16(b, d + 6, crc(b, p.first, p.second))
        }
    }
    private fun fixture(game: Int = 42, hours: Int = 12345, minutes: Int = 59, seconds: Int = 58, last: Long = 0xFEDCBA98L): ByteArray =
        ByteArray(0x100000).also { b ->
            b[0x1004] = game.toByte(); b[0x1035] = 2; repeat(7) { put16(b, 0x5A00 + it * 2, 1001) }
            put32(b, 0x2A00, 0x2F120F17L) // Native Pokédex marker required by the earlier reader.
            put16(b, 0x5A0E, 0); put16(b, base, hours); b[base + 2] = minutes.toByte(); b[base + 3] = seconds.toByte(); put32(b, base + 4, last)
            b[base - 1] = 0xA5.toByte(); b[base + 8] = 0x5A; seal(b)
        }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    private fun packed(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        ((y - 1900).toLong() and 0xFFF) or (((m - 1).toLong() and 15) shl 12) or
            ((d.toLong() and 31) shl 16) or ((h.toLong() and 31) shl 21) or ((min.toLong() and 63) shl 26)
    private val format = java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", java.util.Locale.ROOT)

    @Test fun bothTitlesDecodePackedCalendarWithMinutePrecisionAndLeapCenturyRules() {
        val dates = listOf(listOf(1900, 1, 1, 0, 0), listOf(2000, 2, 29, 23, 59), listOf(2024, 2, 29, 12, 32),
            listOf(2100, 2, 28, 0, 1), listOf(5995, 12, 31, 23, 59))
        for (game in listOf(42, 43)) for ((y, m, d, h, min) in dates.map { it.toIntArray() }) {
            val word = packed(y, m, d, h, min); val b = fixture(game, last = word); val before = b.copyOf(); val read = LetsGoLastSavedReadOnly.read(b)
            assertEquals(game, read.version.gameId); assertEquals(word, read.rawWord); assertEquals(y - 1900, read.rawYear); assertEquals(m - 1, read.rawMonth)
            assertEquals(y, read.year); assertEquals(m, read.month); assertEquals(d, read.day); assertEquals(h, read.hour); assertEquals(min, read.minute)
            assertNull(read.unavailableReason); assertEquals(java.time.LocalDateTime.of(y, m, d, h, min).format(format), read.calendarText)
            assertEquals(before.copyOfRange(base + 4, base + 8).map { it.toInt() and 255 }, read.rawBytes); assertArrayEquals(before, b)
            assertEquals(12345, LetsGoPlayTimeReadOnly.read(b).hours)
        }
    }

    @Test fun everyWordBitAndHighBitRemainExactWithoutTreatingTheWordAsSeconds() {
        for (word in listOf(0L, 1L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL) + (0 until 32).map { 1L shl it }) {
            val b = fixture(last = word); val read = LetsGoLastSavedReadOnly.read(b); assertEquals(word, read.rawWord)
            assertEquals(1900 + (word and 0xFFF).toInt(), read.year); assertEquals(1 + ((word ushr 12) and 15).toInt(), read.month)
            assertEquals(((word ushr 16) and 31).toInt(), read.day); assertEquals(((word ushr 21) and 31).toInt(), read.hour); assertEquals(((word ushr 26) and 63).toInt(), read.minute)
            val oracle = runCatching { java.time.LocalDateTime.of(read.year, read.month, read.day, read.hour, read.minute) }.getOrNull()
            if (oracle == null) { assertNull(read.calendarText); assertNotNull(read.unavailableReason) }
            else { assertEquals(oracle.format(format), read.calendarText); assertNull(read.unavailableReason) }
            assertEquals((0..3).map { ((word ushr (8 * it)) and 255).toInt() }, read.rawBytes)
        }
    }

    @Test fun invalidAndZeroCalendarFieldsKeepRawDataAndDoNotDisableTheRecordedTimer() {
        val invalid = listOf(0L, packed(1900, 2, 29), packed(2100, 2, 29), packed(2000, 2, 30), packed(2026, 4, 31),
            packed(2026, 1, 0), packed(2026, 13, 1), packed(2026, 1, 1, 24), packed(2026, 1, 1, 0, 60))
        for (word in invalid) {
            val b = fixture(last = word); val before = b.copyOf(); val read = LetsGoLastSavedReadOnly.read(b); val s = session(b)
            assertEquals(word, read.rawWord); assertNull(read.calendarText); assertNotNull(read.unavailableReason)
            assertTrue(s.isFormatVerified); assertNotNull(s.letsGoLastSaved); assertNull(s.letsGoLastSavedUnavailableReason)
            assertEquals(12345, s.letsGoPlayTime!!.hours); assertNotNull(s.letsGoTrainer); assertArrayEquals(before, b)
        }
    }

    @Test fun fullNativeGatePrecedesCalendarReadsAndEmptyInputIsRejectedAtIntake() {
        val valid = fixture(last = packed(2024, 2, 29))
        val offsets = regions.map { it.first } + (base + 4 until base + 8).toList() + listOf(0xB8614, 0xB8614 + 4, 0xB8614 + 6, 0xB8610)
        for (o in offsets) {
            val b = valid.copyOf(); b[o] = (b[o].toInt() xor 1).toByte(); val before = b.copyOf()
            assertThrows(IllegalArgumentException::class.java) { LetsGoLastSavedReadOnly.read(b) }; assertNull(session(b).letsGoLastSaved); assertArrayEquals(before, b)
        }
        val title = valid.copyOf().also { it[0x1004] = 44; seal(it) }
        for (b in listOf(title, valid.copyOf(valid.size - 1), valid.copyOf(valid.size + 1), ByteArray(0), ByteArray(1))) {
            assertThrows(IllegalArgumentException::class.java) { LetsGoLastSavedReadOnly.read(b) }
            if (b.isEmpty()) assertThrows(IllegalArgumentException::class.java) { session(b) } else assertNull(session(b).letsGoLastSaved)
        }
    }

    @Test fun independentCalendarViewPreservesStrictTrainerChecks() {
        for ((o, v) in listOf(0x1005 to 2, 0x1035 to 6, 0x1038 to 0xD800)) {
            val b = fixture(last = packed(2024, 2, 29, 12, 32)); if (o == 0x1038) put16(b, o, v) else b[o] = v.toByte(); seal(b)
            val before = b.copyOf(); val s = session(b)
            assertTrue(s.isFormatVerified); assertNull(s.letsGoTrainer); assertFalse(s.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertEquals("2024-02-29 12:32", s.letsGoLastSaved!!.calendarText); assertEquals(12345, s.letsGoPlayTime!!.hours)
            assertTrue(s.supports(SwitchReadCapability.PARTY)); assertArrayEquals(before, b); assertFalse(s.profile.writesEnabled)
        }
    }

    @Test fun immutableRawDataAndNativeIdentitySurviveDetachedSnapshotCopies() {
        for (hint in SwitchGameFamily.values()) {
            val b = fixture(43, last = 0xFFFFFFFFL); val before = b.copyOf(); val s = session(b, hint); val read = s.letsGoLastSaved!!
            assertEquals(SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, s.profile.family); assertEquals(43, read.version.gameId)
            b.fill(0); s.snapshot.bytesCopy().fill(0); assertArrayEquals(before, s.snapshot.bytesCopy()); assertEquals(0xFFFFFFFFL, read.rawWord)
            assertNull(read.calendarText); assertNotNull(read.unavailableReason)
            assertThrows(UnsupportedOperationException::class.java) { (read.rawBytes as MutableList<*>).clear() }
            assertNotNull(s.letsGoPlayTime); assertNotNull(s.letsGoTrainer); assertNotNull(s.letsGoStorage); assertNotNull(s.letsGoInventory); assertNotNull(s.letsGoPokedex)
            assertTrue(s.isReadOnly); assertFalse(s.profile.writesEnabled)
        }
    }
}
