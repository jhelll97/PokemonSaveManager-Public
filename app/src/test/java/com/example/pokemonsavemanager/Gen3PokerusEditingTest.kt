package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3PokerusEditingTest {
    private fun record(pid: Int, party: Boolean = false, egg: Boolean = false, pokerus: Int = 0x32): ByteArray {
        val header = ByteArray(32) { (it + 7).toByte() }
        header[0x13] = (2 or (if (egg) 4 else 0)).toByte()
        val canonical = ByteArray(48) { (it * 13 + 3).toByte() }
        canonical[0] = 25
        canonical[1] = 0
        canonical[36] = pokerus.toByte()
        canonical[43] = ((canonical[43].toInt() and 0xBF) or (if (egg) 0x40 else 0)).toByte()
        val stored = Gen3Pk3Codec.encode(header, pid, 12345, 54321, canonical)
        return if (!party) stored else ByteArray(100) { (it * 7 + 1).toByte() }.also { stored.copyInto(it) }
    }

    private fun assertRejected(raw: ByteArray, state: Gen3PokerusState = Gen3PokerusState()) {
        val original = raw.copyOf()
        var rejected = false
        try { Gen3PokerusEditing.replace(raw, state) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertArrayEquals(original, raw)
    }

    @Test
    fun editsPreserveAllOtherFieldsAcrossEveryOrderAndPartyExtension() {
        val state = Gen3PokerusState(7, 4)
        val pids = (0 until 24).toList() + listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -2, -1)
        for (pid in pids) for (party in listOf(false, true)) {
            val raw = record(pid, party)
            val original = raw.copyOf()
            val before = Gen3Pk3Codec.decode(raw.copyOfRange(0, 80)).canonicalSection
            val edited = Gen3PokerusEditing.replace(raw, state)
            val after = Gen3Pk3Codec.decode(edited.copyOfRange(0, 80)).canonicalSection
            assertEquals(state, Gen3PokerusEditing.read(edited))
            assertEquals(0x74, after[36].toInt() and 255)
            assertTrue((0 until 48).all { it == 36 || before[it] == after[it] })
            assertTrue((0 until 32).all { it in 28..29 || raw[it] == edited[it] })
            assertArrayEquals(raw.copyOfRange(80, raw.size), edited.copyOfRange(80, edited.size))
            assertArrayEquals(original, raw)
        }
    }

    @Test
    fun everyValidStrainDayStateRoundTripsAndNoOpPreservesExactBytes() {
        for (strain in 0..15) {
            val maximum = if (strain == 0) 0 else (strain and 3) + 1
            for (days in 0..maximum) for (party in listOf(false, true)) {
                val state = Gen3PokerusState(strain, days)
                val raw = record(23, party, pokerus = (strain shl 4) or days)
                assertEquals(state, Gen3PokerusEditing.read(raw))
                assertEquals(when { strain == 0 -> "Uninfected"; days == 0 -> "Cured"; else -> "Infectious" }, state.status())
                assertArrayEquals(raw, Gen3PokerusEditing.replace(raw, state))
                assertEquals(state, Gen3PokerusEditing.read(Gen3PokerusEditing.replace(record(23, party), state)))
            }
        }
    }

    @Test
    fun invalidRequestedStatesAndMalformedStoredStatesCannotBeSilentlyNormalized() {
        val raw = record(17, true)
        for (state in listOf(Gen3PokerusState(-1, 0), Gen3PokerusState(16, 0),
            Gen3PokerusState(Int.MAX_VALUE, 0), Gen3PokerusState(1, -1),
            Gen3PokerusState(1, 5), Gen3PokerusState(1, Int.MAX_VALUE))) assertRejected(raw, state)
        for (strain in 0..15) {
            val maximum = if (strain == 0) 0 else (strain and 3) + 1
            assertRejected(raw, Gen3PokerusState(strain, maximum + 1))
            for (days in (maximum + 1)..15) {
                val malformed = record(7, pokerus = (strain shl 4) or days)
                var readRejected = false
                try { Gen3PokerusEditing.read(malformed) }
                catch (_: IllegalArgumentException) { readRejected = true }
                assertTrue(readRejected)
                assertRejected(malformed)
            }
        }
    }

    @Test
    fun fixedWireFixtureChecksUnsignedPidAndExactEncryptedResult() {
        // Independently packed LE words, PID % 24 = 7 => A,G,M,E; checksum=sum(u16), XOR=PID^OID.
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val raw = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f202122c1812526ccfb758980b721b55443ed614a57d16564139d11382f49dd61b33db1584fe97d6c1b9529681f992d3c2b45d9f0e77185")
        val expected = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f20212203822526ccfb758980b721b55443ed614a57d16564139d11382f49dd27b33db1584fe97d6c1b9529681f992d3c2b45d9f0e77185")
        assertEquals(Gen3PokerusState(3, 2), Gen3PokerusEditing.read(raw))
        assertArrayEquals(expected, Gen3PokerusEditing.replace(raw, Gen3PokerusState(7, 4)))
    }

    @Test
    fun readAndReplaceRejectEggsBadEggsChecksumDamageAndIncompleteRecords() {
        val valid = record(1)
        val invalid = listOf(record(1, egg = true),
            record(1, egg = true).also { it[0x13] = 2 }, record(1).also { it[0x13] = 6 },
            record(1).also { it[0x13] = 3 }, record(1).also { it[0x13] = 0 },
            record(1).also { it[79] = (it[79].toInt() xor 1).toByte() },
            valid.copyOf(79), valid.copyOf(81), valid.copyOf(99), valid.copyOf(101))
        invalid.forEach { raw ->
            var readRejected = false
            try { Gen3PokerusEditing.read(raw) }
            catch (_: IllegalArgumentException) { readRejected = true }
            assertTrue(readRejected)
            assertRejected(raw)
        }
    }
}
