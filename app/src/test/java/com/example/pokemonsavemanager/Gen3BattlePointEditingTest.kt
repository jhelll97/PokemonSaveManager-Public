package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3BattlePointEditingTest {
    private fun section(points: Int, seed: Int = 1): ByteArray =
        ByteArray(0xF2C) { (it * 17 + seed).toByte() }.also {
            it[0xEB8] = points.toByte(); it[0xEB9] = (points ushr 8).toByte()
        }

    @Test
    fun fixedNativeWireFixtureSeparatesSpendableFromEarnedPoints() {
        val raw = ByteArray(0xF2C)
        byteArrayOf(0xD2.toByte(), 0x04, 0xFF.toByte(), 0xFF.toByte(), 0x78, 0x56, 0x34, 0x12).copyInto(raw, 0xEB8)
        assertEquals(0xEB8, Gen3BattlePointEditing.BALANCE_OFFSET)
        assertEquals(1234, Gen3BattlePointEditing.read(raw))
        val expected = raw.copyOf()
        byteArrayOf(0x0F, 0x27).copyInto(expected, 0xEB8)
        assertArrayEquals(expected, Gen3BattlePointEditing.replace(raw, 9999))
        assertEquals(255, expected[0xEBA].toInt() and 255)
        assertEquals(255, expected[0xEBB].toInt() and 255)
    }

    @Test
    fun boundaryBalancesRoundTripWithoutChangingOtherTrainerBytes() {
        for (seed in listOf(0, 1, 127, 128, 255)) {
            val raw = section(4321, seed)
            val before = raw.copyOf()
            for (points in listOf(0, 1, 255, 256, 9998, 9999)) {
                val result = Gen3BattlePointEditing.replace(raw, points)
                assertEquals(points, Gen3BattlePointEditing.read(result))
                assertTrue(raw.indices.all { it in 0xEB8..0xEB9 || raw[it] == result[it] })
                assertArrayEquals(before, raw)
            }
        }
    }

    @Test
    fun earnedPointsAndEncryptionKeyDoNotControlOrChangeSpendableBalance() {
        for (earned in listOf(0, 9999, 10000, 65535)) for (key in listOf(0L, 0x1234ABCDL, 0xFFFFFFFFL)) {
            val raw = section(5000)
            raw[0xEBA] = earned.toByte(); raw[0xEBB] = (earned ushr 8).toByte()
            for (index in 0..3) raw[0xAC + index] = (key ushr (index * 8)).toByte()
            val before = raw.copyOf()
            val result = Gen3BattlePointEditing.replace(raw, 9999)
            assertEquals(9999, Gen3BattlePointEditing.read(result))
            assertArrayEquals(before.copyOfRange(0xAC, 0xB0), result.copyOfRange(0xAC, 0xB0))
            assertArrayEquals(before.copyOfRange(0xEBA, 0xF2C), result.copyOfRange(0xEBA, 0xF2C))
            assertArrayEquals(before, raw)
        }
    }

    @Test
    fun invalidTargetsRejectWithoutMutation() {
        val raw = section(200)
        val before = raw.copyOf()
        for (points in listOf(-1, 10000, 65535, Int.MIN_VALUE, Int.MAX_VALUE)) {
            var rejected = false
            try { Gen3BattlePointEditing.replace(raw, points) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected); assertArrayEquals(before, raw)
        }
    }

    @Test
    fun damagedLoadedBalanceAndWrongSectionLengthsRejectWithoutMutation() {
        val cases = listOf(section(10000), section(65535), section(0).copyOf(0xF2B),
            section(0).copyOf(0xF2D), ByteArray(0))
        for (raw in cases) {
            val before = raw.copyOf()
            var rejectedRead = false
            try { Gen3BattlePointEditing.read(raw) } catch (_: IllegalArgumentException) { rejectedRead = true }
            var rejectedWrite = false
            try { Gen3BattlePointEditing.replace(raw, 0) } catch (_: IllegalArgumentException) { rejectedWrite = true }
            assertTrue(rejectedRead && rejectedWrite); assertArrayEquals(before, raw)
        }
    }

    @Test
    fun noOpReturnsDetachedExactSectionIncludingFrontierRecords() {
        for (points in listOf(0, 9999)) {
            val raw = section(points)
            val before = raw.copyOf()
            val result = Gen3BattlePointEditing.replace(raw, points)
            assertArrayEquals(before, result)
            result[0xEBA] = (result[0xEBA].toInt() xor 1).toByte()
            assertArrayEquals(before, raw)
            assertTrue(result[0xEBA] != raw[0xEBA])
        }
    }
}
