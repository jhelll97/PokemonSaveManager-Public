package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3CaptureEditingTest {
    private fun record(pid: Int, party: Boolean = false, egg: Boolean = false,
        origin: Int = 11, level: Int = 25, ball: Int = 4, gender: Int = 0): ByteArray {
        val header = ByteArray(32) { (it + 7).toByte() }
        header[0x13] = (2 or (if (egg) 4 else 0)).toByte()
        val canonical = ByteArray(48) { (it * 13 + 3).toByte() }
        canonical[0] = 25
        canonical[1] = 0
        val word = level or (origin shl 7) or (ball shl 11) or (gender shl 15)
        canonical[38] = word.toByte()
        canonical[39] = (word ushr 8).toByte()
        canonical[43] = ((canonical[43].toInt() and 0xBF) or (if (egg) 0x40 else 0)).toByte()
        val stored = Gen3Pk3Codec.encode(header, pid, 12345, 54321, canonical)
        return if (!party) stored else ByteArray(100) { (it * 7 + 1).toByte() }.also { stored.copyInto(it) }
    }

    private fun assertRejected(raw: ByteArray, values: Gen3CaptureMetadata = Gen3CaptureMetadata()) {
        val original = raw.copyOf()
        var rejected = false
        try { Gen3CaptureEditing.replace(raw, values) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertArrayEquals(original, raw)
    }

    @Test
    fun captureEditsPreserveOriginAndAllOtherFieldsInEveryOrderAndPartyExtension() {
        val values = Gen3CaptureMetadata(100, 12, 1)
        val pids = (0 until 24).toList() + listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -2, -1)
        for (pid in pids) for (party in listOf(false, true)) {
            val raw = record(pid, party)
            val original = raw.copyOf()
            val before = Gen3Pk3Codec.decode(raw.copyOfRange(0, 80)).canonicalSection
            val edited = Gen3CaptureEditing.replace(raw, values)
            val after = Gen3Pk3Codec.decode(edited.copyOfRange(0, 80)).canonicalSection
            assertEquals(values, Gen3CaptureEditing.read(edited))
            assertEquals(0xE4, after[38].toInt() and 255)
            assertEquals(0xE5, after[39].toInt() and 255)
            assertTrue((0 until 48).all { it in 38..39 || before[it] == after[it] })
            assertTrue((0 until 32).all { it in 28..29 || raw[it] == edited[it] })
            assertArrayEquals(raw.copyOfRange(80, raw.size), edited.copyOfRange(80, edited.size))
            assertArrayEquals(original, raw)
        }
    }

    @Test
    fun everyBallGenderAndLevelBoundaryPreservesEveryOriginCodeAndNoOpBytes() {
        assertEquals(listOf("Master Ball", "Ultra Ball", "Great Ball", "Poké Ball", "Safari Ball", "Net Ball",
            "Dive Ball", "Nest Ball", "Repeat Ball", "Timer Ball", "Luxury Ball", "Premier Ball"), Gen3CaptureEditing.ballNames)
        for (origin in 0..15) for (ball in 1..12) for (gender in 0..1) for (level in listOf(0, 1, 50, 100)) {
            val raw = record(7, origin = origin)
            val values = Gen3CaptureMetadata(level, ball, gender)
            val edited = Gen3CaptureEditing.replace(raw, values)
            val data = Gen3Pk3Codec.decode(edited).canonicalSection
            val word = (data[38].toInt() and 255) or ((data[39].toInt() and 255) shl 8)
            assertEquals(level or (origin shl 7) or (ball shl 11) or (gender shl 15), word)
            assertEquals(values, Gen3CaptureEditing.read(edited))
            assertArrayEquals(edited, Gen3CaptureEditing.replace(edited, values))
        }
        for (party in listOf(false, true)) {
            val raw = record(23, party)
            assertArrayEquals(raw, Gen3CaptureEditing.replace(raw, Gen3CaptureEditing.read(raw)))
        }
    }

    @Test
    fun fixedWireFixtureChecksUnsignedPidAndExactEncryptedResult() {
        // Independently packed LE words, PID % 24 = 7 => A,G,M,E; checksum=sum(u16), XOR=PID^OID.
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val raw = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f2021220ea92526ccfb758980b721b55443ed614a57d16564139d11382f49dd84b3556a584fe97d6c1b9529681f992d3c2b45d9f0e77185")
        val expected = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f20212259692526ccfb758980b721b55443ed614a57d16564139d11382f49dd84b328aa584fe97d6c1b9529681f992d3c2b45d9f0e77185")
        assertEquals(Gen3CaptureMetadata(25, 4, 0), Gen3CaptureEditing.read(raw))
        assertArrayEquals(expected, Gen3CaptureEditing.replace(raw, Gen3CaptureMetadata(100, 12, 1)))
    }

    @Test
    fun invalidRequestedAndStoredValuesCannotBeSilentlyRepaired() {
        val raw = record(17, true)
        for (values in listOf(Gen3CaptureMetadata(-1), Gen3CaptureMetadata(101),
            Gen3CaptureMetadata(Int.MAX_VALUE), Gen3CaptureMetadata(ball = 0),
            Gen3CaptureMetadata(ball = 13), Gen3CaptureMetadata(ball = -1),
            Gen3CaptureMetadata(otGender = -1), Gen3CaptureMetadata(otGender = 2))) assertRejected(raw, values)
        val malformed = (101..127).map { record(7, level = it) } + listOf(0, 13, 14, 15).map { record(7, ball = it) }
        malformed.forEach {
            var readRejected = false
            try { Gen3CaptureEditing.read(it) }
            catch (_: IllegalArgumentException) { readRejected = true }
            assertTrue(readRejected)
            assertRejected(it)
        }
    }

    @Test
    fun readAndReplaceRejectEggsBadEggsChecksumDamageAndIncompleteRecords() {
        val valid = record(1)
        val invalid = listOf(record(1, egg = true),
            record(1, egg = true).also { it[0x13] = 2 }, record(1).also { it[0x13] = 6 },
            record(1).also { it[0x13] = 3 }, record(1).also { it[0x13] = 0 },
            record(1).also { it[79] = (it[79].toInt() xor 1).toByte() },
            valid.copyOf(79), valid.copyOf(81), valid.copyOf(99), valid.copyOf(101))
        invalid.forEach {
            var readRejected = false
            try { Gen3CaptureEditing.read(it) }
            catch (_: IllegalArgumentException) { readRejected = true }
            assertTrue(readRejected)
            assertRejected(it)
        }
    }
}
