package com.example.pokemonsavemanager

import org.junit.Assert.*
import org.junit.Test

class LetsGoDayCareReadOnlyTest {
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

    private val dayCare = 0x47800
    private val orders = "0123 0132 0213 0312 0231 0321 1023 1032 2013 3012 2031 3021 1203 1302 2103 3102 2301 3201 1230 1320 2130 3120 2310 3210".split(" ")
    private fun word(b: ByteArray, o: Int): Int = (b[o].toInt() and 255) or ((b[o + 1].toInt() and 255) shl 8)
    private fun plain(ec: Long, species: Int = 25): ByteArray = ByteArray(232).also { b ->
        put32(b, 0, ec); put16(b, 8, species); put16(b, 0x0C, 65535); put16(b, 0x0E, 32768)
        put32(b, 0x10, 0xFFFFFFFFL); put32(b, 0x18, 0xFFFFFFFFL); b[0x1C] = 255.toByte(); b[0x1D] = 255.toByte()
        put16(b, 230, 0xA55A); var sum = 0
        for (o in 8 until 232 step 2) sum += word(b, o)
        put16(b, 6, sum and 65535)
    }
    private fun encrypted(ec: Long, species: Int = 25): ByteArray {
        val p = plain(ec, species); val b = p.copyOf(); val order = orders[((ec ushr 13).toInt() and 31) % 24]
        for (i in 0..3) p.copyInto(b, 8 + (order[i] - '0') * 56, 8 + i * 56, 8 + (i + 1) * 56)
        var state = ec
        for (o in 8 until 232 step 2) { state = (state * 1103515245L + 24691L) and 0xFFFFFFFFL; put16(b, o, word(b, o) xor (state ushr 16).toInt()) }
        return b
    }
    private fun resealDayCare(b: ByteArray) { put16(b, 0xB8614 + 13 * 8 + 6, crc(b, dayCare, 240)) }
    private fun withStored(record: ByteArray, game: Int = 42, prefix: Int = 0): ByteArray = fixture(game).also { b ->
        require(record.size == 232); repeat(8) { b[dayCare + it] = (prefix + it).toByte() }; record.copyInto(b, dayCare + 8); resealDayCare(b)
    }

    @Test fun bothTitlesReadStoredOnlySummaryAndPreserveExactBlockBoundaries() {
        for (game in listOf(42, 43)) for (ec in listOf(0L, 0x80000000L, 0xFFFFFFFFL)) {
            val b = withStored(encrypted(ec), game, 248); b[dayCare - 1] = 0xA5.toByte(); b[dayCare + 240] = 0x5A
            val before = b.copyOf(); val read = LetsGoDayCareReadOnly.read(b); val p = read.pokemon!!
            assertEquals(game, read.version.gameId); assertEquals(25, p.species); assertEquals(31, p.form); assertEquals(3, p.gender)
            assertEquals(0xFFFFFFFFL, p.experience); assertEquals(255, p.nature); assertEquals(65535, p.tid); assertEquals(32768, p.sid)
            assertEquals(0xFFFFFFFFL, p.pid); assertEquals(ec, p.encryptionConstant); assertNull(read.recordUnavailableReason)
            assertEquals((248..255).toList(), read.rawHeaderBytes)
            assertEquals(before.copyOfRange(dayCare + 8, dayCare + 240).map { it.toInt() and 255 }, read.rawStoredBytes)
            assertEquals(read.rawHeaderBytes + read.rawStoredBytes, read.rawBlockBytes)
            assertArrayEquals(before, b); assertEquals(165, b[dayCare - 1].toInt() and 255); assertEquals(90, b[dayCare + 240].toInt() and 255)
        }
    }

    @Test fun allSelectorsSpeciesAndFixedStoredVectorDecodeWithoutSyntheticPartyStats() {
        val seed = fixture()
        for (selector in 0..31) for (prefix in listOf(0L, 0x80000000L, 0xFFF00000L)) {
            val ec = prefix or (selector.toLong() shl 13) or 0x123L
            val b = seed.copyOf(); encrypted(ec).copyInto(b, dayCare + 8); resealDayCare(b)
            val p = LetsGoDayCareReadOnly.read(b).pokemon!!
            assertEquals(ec, p.encryptionConstant); assertEquals(25, p.species); assertEquals(0xFFFFFFFFL, p.experience)
        }
        for (species in (1..151).toList() + listOf(808, 809)) {
            val b = seed.copyOf(); encrypted(7, species).copyInto(b, dayCare + 8); resealDayCare(b)
            assertEquals(species, LetsGoDayCareReadOnly.read(b).pokemon!!.species)
        }
        val fixed = "ffffffff000002d1e58e3e1655e1aa43a1a2f00e9014fd9739ec100086a19202765e3e106f4f326ca2f8ceafe128acc24cfd45dc6641e8637591b27d8eacc546b4f0ffee9b91046e16ff4727c8d0552069535e8c716f5f93407a9eaf15c8a370dfe6c5ad0b0c973d205e7338ca74b42f81828f36c7a23f9de89815484703ca49508887047e218444230eaa8647aa13215e866e47f44da7e924172c6e7f964b1e4eff7912501e7e556b0ddf20e0c802520783d0d69fe1f2a130e8d70917451a1b359310ae51f882c8b0e8da4f77d24c1dc797ca74ba1cbf80ad56e117754ca2876fe301b5923bce6e".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertEquals(232, fixed.size); val p = LetsGoDayCareReadOnly.read(withStored(fixed)).pokemon!!
        assertEquals(25, p.species); assertEquals(8, p.form); assertEquals(0xFFFFFFFFL, p.experience)
        assertEquals(65535, p.tid); assertEquals(65535, p.sid); assertEquals(0xFF00FF00L, p.pid)
    }

    @Test fun badInnerRecordsRemainRawAndDoNotDisableOtherNativeReaders() {
        val good = encrypted(7)
        val badRecords = listOf(4, 6, 8, 231).map { o -> good.copyOf().also { it[o] = (it[o].toInt() xor 1).toByte() } } +
            listOf(0, 152, 807, 810, 65535).map { encrypted(7, it) } + listOf(plain(0xFFFFFFFFL))
        for (record in badRecords) {
            val b = withStored(record); val before = b.copyOf(); val s = session(b); val read = s.letsGoDayCare!!
            assertTrue(s.isFormatVerified); assertNull(read.pokemon); assertNotNull(read.recordUnavailableReason); assertFalse(read.isZeroStoredRecord)
            assertEquals(record.map { it.toInt() and 255 }, read.rawStoredBytes); assertNull(s.letsGoDayCareUnavailableReason)
            assertNotNull(s.letsGoTrainer); assertNotNull(s.letsGoStorage); assertNotNull(s.letsGoPokedex); assertNotNull(s.letsGoGameOptions)
            assertFalse(s.profile.writesEnabled); assertArrayEquals(before, b)
        }
    }

    @Test fun zeroRecordAndEveryOpaquePrefixBitStaySeparateWithoutOccupancyInference() {
        val seed = withStored(ByteArray(232), prefix = 0); seed.fill(0, dayCare, dayCare + 8); resealDayCare(seed)
        for (bit in -1 until 64) {
            val b = seed.copyOf(); if (bit >= 0) b[dayCare + bit / 8] = (1 shl (bit % 8)).toByte(); resealDayCare(b)
            val before = b.copyOf(); val read = LetsGoDayCareReadOnly.read(b)
            assertTrue(read.isZeroStoredRecord); assertNull(read.pokemon); assertNull(read.recordUnavailableReason)
            assertEquals(before.copyOfRange(dayCare, dayCare + 8).map { it.toInt() and 255 }, read.rawHeaderBytes)
            assertEquals(List(232) { 0 }, read.rawStoredBytes); assertArrayEquals(before, b)
        }
    }

    @Test fun completeNativeIntegrityAndIdentityPrecedeDayCareIncludingEmptyIntakeRejection() {
        val valid = withStored(encrypted(7))
        for (o in regions.map { it.first } + listOf(dayCare + 8, dayCare + 239, 0xB8614, 0xB8618, 0xB861A, 0xB8610)) {
            val b = valid.copyOf(); b[o] = (b[o].toInt() xor 1).toByte(); val before = b.copyOf()
            assertThrows(IllegalArgumentException::class.java) { LetsGoDayCareReadOnly.read(b) }; assertNull(session(b).letsGoDayCare)
            assertArrayEquals(before, b)
        }
        val title = valid.copyOf().also { it[0x1004] = 44; seal(it) }
        for (b in listOf(title, valid.copyOf(valid.size - 1), valid.copyOf(valid.size + 1), ByteArray(0), ByteArray(1))) {
            assertThrows(IllegalArgumentException::class.java) { LetsGoDayCareReadOnly.read(b) }
            if (b.isEmpty()) assertThrows(IllegalArgumentException::class.java) { session(b) } else assertNull(session(b).letsGoDayCare)
        }
    }

    @Test fun detachedImmutableSessionIgnoresFamilyHintsAndKeepsStrictReadersIndependent() {
        for (hint in SwitchGameFamily.values()) {
            val b = withStored(encrypted(0xFFFFFFFFL), 43); val before = b.copyOf(); val s = session(b, hint); val read = s.letsGoDayCare!!
            assertEquals(SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, s.profile.family); assertEquals(43, read.version.gameId)
            b.fill(0); s.snapshot.bytesCopy().fill(0); assertArrayEquals(before, s.snapshot.bytesCopy())
            assertEquals(0xFFFFFFFFL, read.pokemon!!.encryptionConstant)
            for (raw in listOf(read.rawHeaderBytes, read.rawStoredBytes, read.rawBlockBytes))
                assertThrows(UnsupportedOperationException::class.java) { (raw as MutableList<*>).clear() }
            assertNotNull(s.letsGoTrainer); assertNotNull(s.letsGoStorage); assertNotNull(s.letsGoInventory); assertNotNull(s.letsGoPokedex)
            assertNotNull(s.letsGoPlayTime); assertNotNull(s.letsGoLastSaved); assertNotNull(s.letsGoGameOptions)
            assertTrue(s.isReadOnly); assertFalse(s.profile.writesEnabled)
        }
        for ((o, v) in listOf(0x1005 to 2, 0x1035 to 6, 0x1038 to 0xD800, 0x5A0E to 1001)) {
            val b = withStored(encrypted(7)); if (o >= 0x1038) put16(b, o, v) else b[o] = v.toByte(); seal(b)
            val s = session(b); assertNotNull(s.letsGoDayCare!!.pokemon)
            if (o == 0x5A0E) assertNull(s.letsGoStorage) else { assertNull(s.letsGoTrainer); assertFalse(s.supports(SwitchReadCapability.TRAINER_PROFILE)) }
            assertTrue(s.isFormatVerified); assertFalse(s.profile.writesEnabled)
        }
    }
}
