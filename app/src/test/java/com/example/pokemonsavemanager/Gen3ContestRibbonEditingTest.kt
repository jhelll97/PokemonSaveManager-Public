package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3ContestRibbonEditingTest {
    private fun record(pid: Int, party: Boolean = false, egg: Boolean = false): ByteArray {
        val header = ByteArray(32) { (it + 7).toByte() }
        header[0x13] = (2 or (if (egg) 4 else 0)).toByte()
        val canonical = ByteArray(48) { (it * 13 + 3).toByte() }
        canonical[0] = 25
        canonical[1] = 0
        canonical[43] = ((canonical[43].toInt() and 0xBF) or (if (egg) 0x40 else 0)).toByte()
        canonical[44] = 0xD1.toByte() // 1, 2, 3, 4, 0 at shifts 0, 3, 6, 9, 12.
        canonical[45] = 0x88.toByte() // Preserve the Champion ribbon at bit 15.
        val stored = Gen3Pk3Codec.encode(header, pid, 12345, 54321, canonical)
        return if (!party) stored else ByteArray(100) { (it * 7 + 1).toByte() }.also { stored.copyInto(it) }
    }

    private fun assertRejected(raw: ByteArray, ranks: Gen3ContestRibbonRanks = Gen3ContestRibbonRanks()) {
        val original = raw.copyOf()
        var rejected = false
        try { Gen3ContestRibbonEditing.replace(raw, ranks) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertArrayEquals(original, raw)
    }

    @Test
    fun ribbonEditsPreserveEveryOtherFieldInAllOrdersAndPartyRecords() {
        val ranks = Gen3ContestRibbonRanks(4, 0, 2, 1, 3)
        val pids = (0 until 24).toList() + listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -2, -1)
        for (pid in pids) for (party in listOf(false, true)) {
            val raw = record(pid, party)
            val original = raw.copyOf()
            val before = Gen3Pk3Codec.decode(raw.copyOfRange(0, 80)).canonicalSection
            val edited = Gen3ContestRibbonEditing.replace(raw, ranks)
            val after = Gen3Pk3Codec.decode(edited.copyOfRange(0, 80)).canonicalSection
            assertEquals(ranks, Gen3ContestRibbonEditing.read(edited))
            assertEquals(0x84, after[44].toInt() and 255)
            assertEquals(0xB2, after[45].toInt() and 255)
            assertTrue((0 until 48).all { it in 44..45 || before[it] == after[it] })
            assertTrue((0 until 32).all { it in 28..29 || raw[it] == edited[it] })
            assertArrayEquals(raw.copyOfRange(80, raw.size), edited.copyOfRange(80, edited.size))
            assertArrayEquals(original, raw)
        }
    }

    @Test
    fun eachRankUsesOnlyItsThreeBitsAndPreservesChampionInBothStates() {
        for (champion in listOf(false, true)) for (category in 0 until 5) for (rank in 0..4) {
            val raw = record(7)
            val decoded = Gen3Pk3Codec.decode(raw)
            val plain = decoded.canonicalSection.copyOf()
            plain[44] = 0
            plain[45] = (if (champion) 0x80 else 0).toByte()
            val zero = Gen3Pk3Codec.encode(decoded.header, decoded.personality,
                decoded.trainerId, decoded.secretId, plain)
            val values = MutableList(5) { 0 }.also { it[category] = rank }
            val ranks = Gen3ContestRibbonRanks(values[0], values[1], values[2], values[3], values[4])
            val edited = Gen3ContestRibbonEditing.replace(zero, ranks)
            val data = Gen3Pk3Codec.decode(edited).canonicalSection
            val word = (data[44].toInt() and 255) or ((data[45].toInt() and 255) shl 8)
            assertEquals((rank shl (category * 3)) or (if (champion) 0x8000 else 0), word)
            assertEquals(ranks, Gen3ContestRibbonEditing.read(edited))
        }
    }

    @Test
    fun fixedWireFixtureChecksHighUnsignedPidAndExactEncryptedResult() {
        // Independently packed LE words, PID % 24 = 7 => A,G,M,E; checksum=sum(u16), XOR=PID^OID.
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val raw = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f202122f8be2526ccfb758980b721b55443ed614a57d16564139d11382f49dd84b33db1584fe97d82df9529681f992d3c2b45d9f0e77185")
        val expected = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f202122abe82526ccfb758980b721b55443ed614a57d16564139d11382f49dd84b33db1584fe97dd7e59529681f992d3c2b45d9f0e77185")
        assertEquals(Gen3ContestRibbonRanks(1, 2, 3, 4, 0), Gen3ContestRibbonEditing.read(raw))
        assertArrayEquals(expected, Gen3ContestRibbonEditing.replace(raw, Gen3ContestRibbonRanks(4, 0, 2, 1, 3)))
    }

    @Test
    fun noOpKeepsExactStoredAndPartyBytes() {
        for (party in listOf(false, true)) {
            val raw = record(23, party)
            val ranks = Gen3ContestRibbonEditing.read(raw)
            assertEquals(Gen3ContestRibbonRanks(1, 2, 3, 4, 0), ranks)
            assertArrayEquals(raw, Gen3ContestRibbonEditing.replace(raw, ranks))
        }
    }

    @Test
    fun rejectsInvalidRequestedAndStoredRanksWithoutRepairingOrMutating() {
        val raw = record(17, true)
        for (category in 0 until 5) {
            for (bad in listOf(-1, 5, 7, 8, Int.MAX_VALUE)) {
                val values = MutableList(5) { 0 }.also { it[category] = bad }
                assertRejected(raw, Gen3ContestRibbonRanks(values[0], values[1], values[2], values[3], values[4]))
            }
            for (bad in 5..7) {
                val decoded = Gen3Pk3Codec.decode(raw.copyOfRange(0, 80))
                val canonical = decoded.canonicalSection.copyOf()
                val word = bad shl (category * 3)
                canonical[44] = word.toByte()
                canonical[45] = (word ushr 8).toByte()
                val malformed = Gen3Pk3Codec.encode(decoded.header, decoded.personality,
                    decoded.trainerId, decoded.secretId, canonical)
                assertRejected(malformed)
            }
        }
    }

    @Test
    fun rejectsEggsBadEggsChecksumDamageAndIncompleteRecords() {
        val valid = record(1)
        val invalid = listOf(record(1, egg = true),
            record(1, egg = true).also { it[0x13] = 2 }, record(1).also { it[0x13] = 6 },
            record(1).also { it[0x13] = 3 }, record(1).also { it[0x13] = 0 },
            record(1).also { it[79] = (it[79].toInt() xor 1).toByte() },
            valid.copyOf(79), valid.copyOf(81), valid.copyOf(99), valid.copyOf(101))
        invalid.forEach { assertRejected(it) }
    }
}
