package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3ContestEditingTest {
    private fun record(pid: Int, party: Boolean = false, egg: Boolean = false): ByteArray {
        val header = ByteArray(32) { (it + 7).toByte() }
        header[0x13] = (2 or (if (egg) 4 else 0)).toByte()
        val canonical = ByteArray(48) { (it * 13 + 3).toByte() }
        canonical[0] = 25
        canonical[1] = 0
        canonical[43] = ((canonical[43].toInt() and 0xBF) or (if (egg) 0x40 else 0)).toByte()
        val stored = Gen3Pk3Codec.encode(header, pid, 12345, 54321, canonical)
        return if (!party) stored else ByteArray(100) { (it * 7 + 1).toByte() }.also { stored.copyInto(it) }
    }

    @Test
    fun conditionEditsPreserveAllOtherFieldsAcrossEveryOrderAndPartyExtension() {
        val values = Gen3ContestState(0, 1, 127, 128, 254, 255)
        val pids = (0 until 24).toList() + listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -2, -1)
        for (pid in pids) for (party in listOf(false, true)) {
            val raw = record(pid, party)
            val original = raw.copyOf()
            val before = Gen3Pk3Codec.decode(raw.copyOfRange(0, 80))
            val edited = Gen3ContestEditing.replace(raw, values)
            val after = Gen3Pk3Codec.decode(edited.copyOfRange(0, 80))
            assertEquals(values, Gen3ContestEditing.read(edited))
            assertArrayEquals(byteArrayOf(0, 1, 127, -128, -2, -1), after.canonicalSection.copyOfRange(30, 36))
            assertTrue((0 until 48).all { it in 30..35 ||
                before.canonicalSection[it] == after.canonicalSection[it] })
            assertTrue((0 until 32).all { it in 28..29 || raw[it] == edited[it] })
            assertArrayEquals(raw.copyOfRange(80, raw.size), edited.copyOfRange(80, edited.size))
            assertArrayEquals(original, raw)
        }
    }

    @Test
    fun conditionReadAndNoOpReplacementKeepExactStoredAndPartyBytes() {
        for (party in listOf(false, true)) {
            val raw = record(23, party)
            val plain = Gen3Pk3Codec.decode(raw.copyOfRange(0, 80)).canonicalSection
            val values = Gen3ContestEditing.read(raw)
            assertEquals(Gen3ContestState(plain[30].toInt() and 255, plain[31].toInt() and 255,
                plain[32].toInt() and 255, plain[33].toInt() and 255,
                plain[34].toInt() and 255, plain[35].toInt() and 255), values)
            assertArrayEquals(raw, Gen3ContestEditing.replace(raw, values))
        }
    }

    @Test
    fun conditionEditsRejectEachOutOfRangeValueWithoutChangingTheRecord() {
        val raw = record(17, true)
        val before = raw.copyOf()
        val zero = Gen3ContestState()
        for (bad in listOf(-1, 256, Int.MAX_VALUE)) {
            val invalid = listOf(zero.copy(cool = bad), zero.copy(beauty = bad), zero.copy(cute = bad),
                zero.copy(smart = bad), zero.copy(tough = bad), zero.copy(sheen = bad))
            invalid.forEach { value ->
                var rejected = false
                try { Gen3ContestEditing.replace(raw, value) }
                catch (_: IllegalArgumentException) { rejected = true }
                assertTrue(rejected)
                assertArrayEquals(before, raw)
            }
        }
    }

    @Test
    fun conditionEditsRejectEggsBadEggsCorruptionAndIncompleteRecords() {
        val valid = record(1)
        val invalid = listOf(record(1, egg = true),
            record(1, egg = true).also { it[0x13] = 2 }, record(1).also { it[0x13] = 6 },
            record(1).also { it[0x13] = 3 },
            record(1).also { it[0x13] = 0 }, record(1).also { it[79] = (it[79].toInt() xor 1).toByte() },
            valid.copyOf(79), valid.copyOf(81), valid.copyOf(99), valid.copyOf(101))
        invalid.forEach { raw ->
            val before = raw.copyOf()
            var rejected = false
            try { Gen3ContestEditing.replace(raw, Gen3ContestState()) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertArrayEquals(before, raw)
        }
    }
}
