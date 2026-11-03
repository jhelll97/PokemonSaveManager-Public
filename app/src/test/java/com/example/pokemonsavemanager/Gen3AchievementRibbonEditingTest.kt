package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3AchievementRibbonEditingTest {
    private fun record(pid: Int, party: Boolean = false, egg: Boolean = false): ByteArray {
        val header = ByteArray(32) { (it + 7).toByte() }
        header[0x13] = (2 or (if (egg) 4 else 0)).toByte()
        val canonical = ByteArray(48) { (it * 13 + 3).toByte() }
        canonical[0] = 25
        canonical[1] = 0
        canonical[43] = ((canonical[43].toInt() and 0xBF) or (if (egg) 0x40 else 0)).toByte()
        canonical[44] = 0xD1.toByte() // Contest ranks 1,2,3,4,0.
        canonical[45] = 0x08
        canonical[46] = 0xA0.toByte() // Other ribbon flags.
        canonical[47] = 0xF3.toByte() // Other ribbons, reserved bits and fateful encounter.
        val stored = Gen3Pk3Codec.encode(header, pid, 12345, 54321, canonical)
        return if (!party) stored else ByteArray(100) { (it * 7 + 1).toByte() }.also { stored.copyInto(it) }
    }

    private fun ribbons(flags: Int) = Gen3AchievementRibbons((flags and 1) != 0,
        (flags and 2) != 0, (flags and 4) != 0, (flags and 8) != 0, (flags and 16) != 0)

    @Test
    fun allFlagCombinationsPreserveOtherBitsAcrossAllOrdersAndPartyExtensions() {
        val pids = (0 until 24).toList() + listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -2, -1)
        for (pid in pids) for (party in listOf(false, true)) for (flags in 0..31) {
            val raw = record(pid, party)
            val original = raw.copyOf()
            val before = Gen3Pk3Codec.decode(raw.copyOfRange(0, 80)).canonicalSection
            val values = ribbons(flags)
            val edited = Gen3AchievementRibbonEditing.replace(raw, values)
            val after = Gen3Pk3Codec.decode(edited.copyOfRange(0, 80)).canonicalSection
            assertEquals(values, Gen3AchievementRibbonEditing.read(edited))
            assertEquals(0x08 or ((flags and 1) shl 7), after[45].toInt() and 255)
            assertEquals(0xA0 or (flags ushr 1), after[46].toInt() and 255)
            assertTrue((0 until 48).all { it in 45..46 || before[it] == after[it] })
            assertTrue((0 until 32).all { it in 28..29 || raw[it] == edited[it] })
            assertArrayEquals(raw.copyOfRange(80, raw.size), edited.copyOfRange(80, edited.size))
            assertArrayEquals(original, raw)
            assertArrayEquals(raw, Gen3AchievementRibbonEditing.replace(edited, Gen3AchievementRibbons()))
        }
    }

    @Test
    fun independentFixedWireFixtureChecksUnsignedPidAndExactEncryptedBytes() {
        // Independently packed LE words, PID % 24 = 7 => A,G,M,E; checksum=sum(u16), XOR=PID^OID.
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val raw = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f2021223fcc2526ccfb758980b721b55443ed614a57d16564139d11382f49dd84b33db1584fe97d825f6cbc681f992d3c2b45d9f0e77185")
        val expected = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f202122494c2526ccfb758980b721b55443ed614a57d16564139d11382f49dd84b33db1584fe97d82df66bc681f992d3c2b45d9f0e77185")
        assertEquals(Gen3AchievementRibbons(), Gen3AchievementRibbonEditing.read(raw))
        val mixed = Gen3AchievementRibbons(champion = true, victory = true, effort = true)
        assertArrayEquals(expected, Gen3AchievementRibbonEditing.replace(raw, mixed))
        assertEquals(mixed, Gen3AchievementRibbonEditing.read(expected))
    }

    @Test
    fun noOpKeepsExactStoredAndPartyBytesForEveryFlagCombination() {
        for (party in listOf(false, true)) for (flags in 0..31) {
            val raw = Gen3AchievementRibbonEditing.replace(record(23, party), ribbons(flags))
            assertArrayEquals(raw, Gen3AchievementRibbonEditing.replace(raw, Gen3AchievementRibbonEditing.read(raw)))
        }
    }

    @Test
    fun achievementEditsAndContestEditsPreserveEachOthersFields() {
        val raw = record(-1, true)
        val contest = Gen3ContestRibbonRanks(4, 3, 2, 1, 4)
        val achievement = Gen3AchievementRibbons(true, false, true, false, true)
        val contestFirst = Gen3AchievementRibbonEditing.replace(
            Gen3ContestRibbonEditing.replace(raw, contest), achievement)
        val achievementFirst = Gen3ContestRibbonEditing.replace(
            Gen3AchievementRibbonEditing.replace(raw, achievement), contest)
        assertArrayEquals(contestFirst, achievementFirst)
        assertEquals(contest, Gen3ContestRibbonEditing.read(achievementFirst))
        assertEquals(achievement, Gen3AchievementRibbonEditing.read(contestFirst))
        assertArrayEquals(raw.copyOfRange(80, 100), contestFirst.copyOfRange(80, 100))
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
            val original = raw.copyOf()
            var readRejected = false
            var writeRejected = false
            try { Gen3AchievementRibbonEditing.read(raw) }
            catch (_: IllegalArgumentException) { readRejected = true }
            try { Gen3AchievementRibbonEditing.replace(raw, ribbons(31)) }
            catch (_: IllegalArgumentException) { writeRejected = true }
            assertTrue(readRejected && writeRejected)
            assertArrayEquals(original, raw)
        }
    }
}
