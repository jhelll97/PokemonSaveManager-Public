package com.example.pokemonsavemanager

import org.junit.Assert.*
import org.junit.Test

class LetsGoPlayTimeReadOnlyTest {
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

    @Test fun bothTitlesKeepUnsignedHourRangeAndExactEightByteBoundaries() {
        for (game in listOf(42, 43)) for (hours in listOf(0, 1, 9999, 10000, 32767, 32768, 65535)) {
            val b = fixture(game, hours); val before = b.copyOf(); val read = LetsGoPlayTimeReadOnly.read(b)
            assertEquals(game, read.version.gameId); assertEquals(hours, read.hours); assertEquals(59, read.minutes); assertEquals(58, read.seconds)
            assertEquals(hours.toString().padStart(4, '0') + ":59:58", read.savedTimeText); assertTrue(read.hasCanonicalMinutesAndSeconds)
            assertEquals(0xFEDCBA98L, read.rawLastSavedWord); assertEquals(before.copyOfRange(base, base + 8).map { it.toInt() and 255 }, read.rawBlockBytes)
            assertEquals(165, b[base - 1].toInt() and 255); assertEquals(90, b[base + 8].toInt() and 255); assertArrayEquals(before, b)
        }
    }

    @Test fun anomalousTimerBytesAndAllLastSavedBitsRemainUnnormalized() {
        for ((m, s) in listOf(0 to 0, 59 to 59, 60 to 60, 255 to 255, 0 to 255, 128 to 1, 1 to 128, 59 to 60)) {
            val b = fixture(minutes = m, seconds = s); val read = LetsGoPlayTimeReadOnly.read(b)
            assertEquals(m, read.minutes); assertEquals(s, read.seconds); assertEquals(m in 0..59 && s in 0..59, read.hasCanonicalMinutesAndSeconds)
            assertEquals("12345:" + m.toString().padStart(2, '0') + ":" + s.toString().padStart(2, '0'), read.savedTimeText)
        }
        for (word in listOf(0L, 1L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL) + (0 until 32).map { 1L shl it }) {
            val b = fixture(last = word); val read = LetsGoPlayTimeReadOnly.read(b)
            assertEquals(word, read.rawLastSavedWord); assertEquals(12345, read.hours); assertEquals(59, read.minutes); assertEquals(58, read.seconds)
        }
    }

    @Test fun everyNativeBlockDescriptorAndCrcIsRequiredBeforePlaytimeRead() {
        val valid = fixture()
        for ((id, p) in regions.withIndex()) for (o in listOf(p.first, 0xB8614 + id * 8, 0xB8614 + id * 8 + 4, 0xB8614 + id * 8 + 6)) {
            val bad = valid.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
            assertThrows(IllegalArgumentException::class.java) { LetsGoPlayTimeReadOnly.read(bad) }; assertArrayEquals(before, bad)
        }
        for (o in base until base + 8) {
            val bad = valid.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte()
            assertThrows(IllegalArgumentException::class.java) { LetsGoPlayTimeReadOnly.read(bad) }; assertNull(session(bad).letsGoPlayTime)
        }
    }

    @Test fun titleMarkerExactLengthAndUnrecognizedDataNeverExposePlaytime() {
        val b = fixture(); val title = b.copyOf().also { it[0x1004] = 44; seal(it) }
        val footer = b.copyOf().also { it[0xB8610] = 0 }
        for (bad in listOf(title, footer, b.copyOf(b.size - 1), b.copyOf(b.size + 1), ByteArray(0), ByteArray(1), ByteArray(0x100000))) {
            val before = bad.copyOf(); assertThrows(IllegalArgumentException::class.java) { LetsGoPlayTimeReadOnly.read(bad) }
            if (bad.isEmpty()) assertThrows(IllegalArgumentException::class.java) { session(bad) }
            else assertNull(session(bad).letsGoPlayTime)
            assertArrayEquals(before, bad)
        }
    }

    @Test fun independentTimeViewDoesNotRelaxStrictTrainerValidation() {
        for ((o, v) in listOf(0x1005 to 2, 0x1035 to 6, 0x1038 to 0xD800)) {
            val b = fixture(); if (o == 0x1038) put16(b, o, v) else b[o] = v.toByte(); seal(b); val before = b.copyOf(); val s = session(b)
            assertTrue(s.isFormatVerified); assertNull(s.letsGoTrainer); assertFalse(s.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertNotNull(s.letsGoPlayTime); assertNull(s.letsGoPlayTimeUnavailableReason); assertEquals(12345, s.letsGoPlayTime!!.hours)
            assertTrue(s.supports(SwitchReadCapability.PARTY)); assertArrayEquals(before, b); assertFalse(s.profile.writesEnabled)
        }
    }

    @Test fun immutableDetachedDataUsesNativeIdentityAndPreservesEarlierReadersAndWriteLocks() {
        for (hint in SwitchGameFamily.values()) {
            val b = fixture(43); val before = b.copyOf(); val s = session(b, hint); val read = s.letsGoPlayTime!!
            assertEquals(SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, s.profile.family); assertEquals(43, read.version.gameId)
            b.fill(0); s.snapshot.bytesCopy().fill(0); assertArrayEquals(before, s.snapshot.bytesCopy()); assertEquals(12345, read.hours)
            assertThrows(UnsupportedOperationException::class.java) { (read.rawBlockBytes as MutableList<*>).clear() }
            assertNotNull(s.letsGoTrainer); assertNotNull(s.letsGoStorage); assertNotNull(s.letsGoInventory); assertNotNull(s.letsGoPokedex)
            assertTrue(s.isReadOnly); assertFalse(s.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.letsGoPlayTime); assertNull(other.letsGoPlayTimeUnavailableReason)
    }
}
