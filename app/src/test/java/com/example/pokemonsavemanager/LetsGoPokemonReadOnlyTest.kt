package com.example.pokemonsavemanager

import org.junit.Assert.*
import org.junit.Test

class LetsGoPokemonReadOnlyTest {
    private val orders = "0123 0132 0213 0312 0231 0321 1023 1032 2013 3012 2031 3021 1203 1302 2103 3102 2301 3201 1230 1320 2130 3120 2310 3210".split(" ")
    private val regions = listOf(0x00000 to 0x00D90, 0x00E00 to 0x00200, 0x01000 to 0x00168,
        0x01200 to 0x01800, 0x02A00 to 0x020E8, 0x04C00 to 0x00930, 0x05600 to 0x00004,
        0x05800 to 0x00130, 0x05A00 to 0x00012, 0x05C00 to 0x3F7A0, 0x45400 to 0x00008,
        0x45600 to 0x00E90, 0x46600 to 0x010A4, 0x47800 to 0x000F0, 0x47A00 to 0x06010,
        0x4DC00 to 0x00200, 0x4DE00 to 0x00098, 0x4E000 to 0x00068, 0x4E200 to 0x69780,
        0xB7A00 to 0x000B0, 0xB7C00 to 0x00940)
    private fun put16(b: ByteArray, i: Int, n: Int) { b[i] = n.toByte(); b[i + 1] = (n ushr 8).toByte() }
    private fun put32(b: ByteArray, i: Int, n: Long) { put16(b, i, n.toInt()); put16(b, i + 2, (n ushr 16).toInt()) }
    private fun word(b: ByteArray, i: Int): Int = (b[i].toInt() and 255) or ((b[i + 1].toInt() and 255) shl 8)
    private fun plain(ec: Long, species: Int = 25): ByteArray = ByteArray(260).also { b ->
        put32(b, 0, ec); put16(b, 8, species); put16(b, 0x0C, 65535); put16(b, 0x0E, 65535)
        put32(b, 0x10, 0xFFFFFFFFL); put32(b, 0x18, 0xFF00FF00L)
        b[0x1C] = 24; b[0x1D] = 64
        for (i in 0..5) b[0x24 + i] = (200 + i).toByte()
        "ミュウ".toByteArray(Charsets.UTF_16LE).copyInto(b, 0x40)
        "Zoë".toByteArray(Charsets.UTF_16LE).copyInto(b, 0xB0)
        for ((i, move) in listOf(1, 85, 0, 742).withIndex()) put16(b, 0x5A + i * 2, move)
        byteArrayOf(255.toByte(), 10, 0, 40).copyInto(b, 0x62)
        var ivs = 0x40000000L
        for ((i, iv) in listOf(31, 0, 1, 30, 15, 16).withIndex()) ivs = ivs or (iv.toLong() shl (i * 5))
        put32(b, 0x74, ivs); b[0xEC] = 100; put16(b, 0xF0, 65535); put16(b, 0xF2, 65535)
        var sum = 0; for (i in 8 until 232 step 2) sum += word(b, i)
        put16(b, 6, sum and 65535)
    }
    private fun encrypt(plain: ByteArray, ec: Long): ByteArray {
        val result = plain.copyOf()
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        for (canonical in 0..3) {
            val stored = order[canonical] - '0'
            plain.copyInto(result, 8 + stored * 56, 8 + canonical * 56, 8 + (canonical + 1) * 56)
        }
        for ((start, end) in listOf(8 to 232, 232 to 260)) {
            var state = ec
            for (offset in start until end step 2) {
                state = (state * 1103515245L + 24691L) and 0xFFFFFFFFL
                put16(result, offset, word(result, offset) xor (state ushr 16).toInt())
            }
        }
        return result
    }
    private fun crc(b: ByteArray, start: Int, len: Int): Int {
        var state = 0
        for (i in start until start + len) {
            state = state xor (b[i].toInt() and 255)
            repeat(8) { state = if (state and 1 == 0) state ushr 1 else (state ushr 1) xor 0xA001 }
        }
        return state
    }
    private fun seal(b: ByteArray) {
        put32(b, 0xB8610, 0x42454546L)
        for ((id, region) in regions.withIndex()) {
            val entry = 0xB8614 + id * 8
            put32(b, entry, region.second.toLong()); put16(b, entry + 4, id)
            put16(b, entry + 6, crc(b, region.first, region.second))
        }
    }
    private fun save(count: Int, party: List<Int> = List(6) { 1001 }, partner: Int = 1001): ByteArray =
        ByteArray(0x100000).also { b ->
            b[0x1004] = 42
            for (i in 0..5) put16(b, 0x5A00 + i * 2, party[i])
            put16(b, 0x5A0C, partner); put16(b, 0x5A0E, count)
            for (i in 0 until minOf(count, 1000)) encrypt(plain(i.toLong()), i.toLong()).copyInto(b, 0x5C00 + i * 260)
            seal(b)
        }

    @Test fun nativeEncryptedRecordReadsUnsignedValuesUnicodeAndPreservesInput() {
        val bytes = encrypt(plain(0xFFFFFFFFL), 0xFFFFFFFFL); val before = bytes.copyOf()
        val fixed = "ffffffff000002d1e58e3e1655e1aa43a1a2f00e9014fd9739ec100086a19202765e3e106f4f326ca2f8ceafe128acc24cfd45dc6641e8637591b27d8eacc546b4f0ffee9b91046e16ff4727c8d0552069535e8c716f5f93407a9eaf15c8a370dfe6c5ad0b0c973d205e7338ca74b42f81828f36c7a23f9de89815484703ca49508887047e218444230eaa8647aa13215e866e47f44da7e924172c6e7f964b1e4eff7912501e7e556b0ddf20e0c802520783d0d69fe1f2a130e8d70917451a1b359310ae51f882c8b0e8da4f77d24c1dc797ca74ba1cbf80ad56e117754ca2876fe301b5923bce6e3abedb2697d1aa435e5d0ff19014fd9739ec100086a19202765e3f10".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertArrayEquals(fixed, bytes)
        val p = (LetsGoPokemonReadOnly.read(bytes) as LetsGoSlotRead.Pokemon).value
        assertEquals(25, p.species); assertEquals("ミュウ", p.nickname); assertEquals("Zoë", p.originalTrainer)
        assertEquals(8, p.form); assertTrue(p.isPartnerForm); assertTrue(p.shiny); assertTrue(p.isEgg)
        assertEquals(0xFFFFFFFFL, p.experience); assertEquals(0xFFFFFFFFL, p.encryptionConstant)
        assertEquals(0xFF00FF00L, p.pid); assertEquals(65535, p.tid); assertEquals(65535, p.sid)
        assertEquals(100, p.cachedLevel); assertEquals(65535, p.currentHp); assertEquals(65535, p.maximumHp)
        assertEquals(listOf(31, 0, 1, 30, 15, 16), p.ivs)
        assertEquals(listOf(200, 201, 202, 203, 204, 205), p.awakened)
        assertEquals(listOf(1, 85, 0, 742), p.moves.map { it.id }); assertEquals(255, p.moves[0].pp)
        assertArrayEquals(before, bytes)
    }

    @Test fun all32ShuffleSelectorsAndUnsignedConstantsDecodeWithNoPlaintextGuess() {
        for (selector in 0..31) for (prefix in listOf(0L, 0x80000000L, 0xFFF00000L)) {
            val ec = prefix or (selector.toLong() shl 13) or 0x123L
            val result = LetsGoPokemonReadOnly.read(encrypt(plain(ec), ec)) as LetsGoSlotRead.Pokemon
            assertEquals(ec, result.value.encryptionConstant); assertEquals("ミュウ", result.value.nickname)
            assertEquals(25, result.value.species); assertEquals(100, result.value.cachedLevel)
        }
        assertTrue(LetsGoPokemonReadOnly.read(plain(0xFFFFFFFFL)) is LetsGoSlotRead.Invalid)
    }

    @Test fun emptyMalformedSanityChecksumAndUnsupportedSpeciesAreIsolated() {
        assertTrue(LetsGoPokemonReadOnly.read(ByteArray(260)) is LetsGoSlotRead.Empty)
        for (length in listOf(0, 232, 259, 261)) assertTrue(LetsGoPokemonReadOnly.read(ByteArray(length)) is LetsGoSlotRead.Invalid)
        val good = encrypt(plain(7), 7)
        for (index in listOf(4, 6, 8, 231)) {
            val bad = good.copyOf(); bad[index] = (bad[index].toInt() xor 1).toByte()
            assertTrue(LetsGoPokemonReadOnly.read(bad) is LetsGoSlotRead.Invalid)
        }
        assertTrue(LetsGoPokemonReadOnly.read(encrypt(plain(7, 999), 7)) is LetsGoSlotRead.Invalid)
    }

    @Test fun sparsePartyPointersAndIndependentPartnerReferenceTheSameNativePool() {
        val bytes = save(3, listOf(2, 1001, 0, 1001, 1001, 1001), 1); val before = bytes.copyOf()
        val storage = LetsGoStorageReadOnly.read(bytes)
        assertEquals(listOf(2, null, 0, null, null, null), storage.party().map { it.storage?.index })
        assertEquals(1, storage.partnerIndex); assertTrue(storage.slot(1).partner)
        assertEquals(listOf(1), storage.slot(2).partyPositions); assertEquals(listOf(3), storage.slot(0).partyPositions)
        assertEquals(25, storage.page(0).size); assertTrue(storage.slot(3).read is LetsGoSlotRead.Empty)
        assertArrayEquals(before, bytes)
    }

    @Test fun outOfPoolDuplicateAndInvalidSentinelPointersRejectAfterValidNativeChecksums() {
        for ((offset, value) in listOf(0x5A00 to 3, 0x5A00 to 1000, 0x5A00 to 65535,
            0x5A0C to 3, 0x5A0E to 1001)) {
            val b = save(3); put16(b, offset, value); seal(b)
            assertThrows(IllegalArgumentException::class.java) { LetsGoStorageReadOnly.read(b) }
        }
        val duplicate = save(3, listOf(0, 0, 1001, 1001, 1001, 1001))
        assertThrows(IllegalArgumentException::class.java) { LetsGoStorageReadOnly.read(duplicate) }
        assertTrue(LetsGoStorageReadOnly.read(save(1, listOf(0, 1001, 1001, 1001, 1001, 1001), 0)).slot(0).partner)
    }

    @Test fun outerChecksumDamageBlocksBrowsingAndBadInnerRecordsStayPerSlot() {
        val badOuter = save(2); badOuter[0x5C08] = (badOuter[0x5C08].toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) { LetsGoStorageReadOnly.read(badOuter) }
        val b = save(3)
        b[0x5C08] = (b[0x5C08].toInt() xor 1).toByte()
        b.fill(0, 0x5C00 + 260, 0x5C00 + 520); seal(b)
        val storage = LetsGoStorageReadOnly.read(b)
        assertTrue(storage.slot(0).read is LetsGoSlotRead.Invalid)
        assertTrue(storage.slot(1).read is LetsGoSlotRead.Invalid)
        assertTrue(storage.slot(2).read is LetsGoSlotRead.Pokemon)
        val session = SwitchReadSession(SwitchReadSnapshot.capture(SwitchGameFamily.SWORD_SHIELD, b))
        assertTrue(session.supports(SwitchReadCapability.PARTY)); assertTrue(session.isReadOnly)
        assertFalse(session.supports(SwitchReadCapability.TRAINER_PROFILE))
    }

    @Test fun emptyAndFullPoolBoundaryPagesNeverLeakTrailingResidualData() {
        val empty = LetsGoStorageReadOnly.read(save(0))
        assertEquals(0, empty.count); assertTrue(empty.page(39).all { it.read is LetsGoSlotRead.Empty })
        val bytes = save(1000); val before = bytes.copyOf()
        val full = LetsGoStorageReadOnly.read(bytes)
        assertEquals(1000, full.count); assertEquals(999, full.page(39).last().index)
        assertTrue(full.page(39).last().read is LetsGoSlotRead.Pokemon)
        assertThrows(IllegalArgumentException::class.java) { full.page(40) }
        assertThrows(IllegalArgumentException::class.java) { full.slot(-1) }
        assertThrows(IllegalArgumentException::class.java) { full.slot(1000) }
        assertArrayEquals(before, bytes)
    }

    @Test fun sourceCopiesAndExposedListsCannotChangeReadSessionOrNativeIdentity() {
        val input = save(1); val session = SwitchReadSession(SwitchReadSnapshot.capture(SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, input))
        input.fill(0); session.snapshot.bytesCopy().fill(0)
        val p = (session.letsGoStorage!!.slot(0).read as LetsGoSlotRead.Pokemon).value
        assertEquals("ミュウ", p.nickname); assertTrue(session.isFormatVerified)
        assertThrows(UnsupportedOperationException::class.java) { (p.ivs as MutableList<Int>)[0] = 0 }
        assertThrows(UnsupportedOperationException::class.java) { (p.moves as MutableList<LetsGoMoveRead>).clear() }
        assertTrue(session.supports(SwitchReadCapability.POKEMON_DETAILS))
        assertTrue(session.supports(SwitchReadCapability.PC_OR_BOXES)); assertFalse(session.profile.writesEnabled)
        assertEquals("Pikachu", LetsGoSpeciesNames.name(25)); assertEquals("Eevee", LetsGoSpeciesNames.name(133))
        assertEquals("Meltan", LetsGoSpeciesNames.name(808)); assertEquals("Melmetal", LetsGoSpeciesNames.name(809))
    }
}
