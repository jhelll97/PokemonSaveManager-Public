package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3BerryPowderEditingTest {
    private fun section(profile: Gen3BerryPowderProfile, key: Long, powder: Long): ByteArray =
        ByteArray(0xF2C) { (it * 17 + 9).toByte() }.also { raw ->
            for (index in 0..3) {
                raw[profile.keyOffset + index] = (key ushr (index * 8)).toByte()
                raw[profile.powderOffset + index] = ((powder xor key) ushr (index * 8)).toByte()
            }
        }

    @Test
    fun allProfilesRoundTripUnsignedKeysAndFiveDigitAmountsWithExactScope() {
        for (profile in Gen3BerryPowderProfile.values())
            for (key in listOf(0L, 1L, 0xFFFFL, 0x80000000L, 0x1234ABCDL, 0xFFFFFFFFL)) {
                val raw = section(profile, key, 12345)
                val before = raw.copyOf()
                for (powder in listOf(0, 1, 65535, 65536, 99998, 99999)) {
                    val result = Gen3BerryPowderEditing.replace(raw, profile, powder)
                    assertEquals(powder, Gen3BerryPowderEditing.read(result, profile))
                    assertTrue(raw.indices.all { it in profile.powderOffset..profile.powderOffset + 3 || raw[it] == result[it] })
                    assertArrayEquals(before, raw)
                }
            }
    }

    @Test
    fun fixedNativeWireBytesUseFull32BitXorAndCorrectTitleOffsets() {
        for ((profile, offset, keyOffset) in listOf(Triple(Gen3BerryPowderProfile.EMERALD, 0x1F4, 0xAC),
            Triple(Gen3BerryPowderProfile.FIRERED_LEAFGREEN, 0xAF8, 0xF20))) {
            val raw = ByteArray(0xF2C)
            byteArrayOf(0xCD.toByte(), 0xAB.toByte(), 0x34, 0x12).copyInto(raw, keyOffset)
            // 12345 (0x3039) XOR 0x1234ABCD = 0x12349BF4.
            byteArrayOf(0xF4.toByte(), 0x9B.toByte(), 0x34, 0x12).copyInto(raw, offset)
            assertEquals(12345, Gen3BerryPowderEditing.read(raw, profile))
            val expected = raw.copyOf()
            // 99999 (0x1869F) XOR 0x1234ABCD = 0x12352D52.
            byteArrayOf(0x52, 0x2D, 0x35, 0x12).copyInto(expected, offset)
            assertArrayEquals(expected, Gen3BerryPowderEditing.replace(raw, profile, 99999))
            assertEquals(offset, profile.powderOffset); assertEquals(keyOffset, profile.keyOffset)
        }
    }

    @Test
    fun keyAndAdjacentBerryCrushRecordsRemainExact() {
        for (profile in Gen3BerryPowderProfile.values()) for (key in listOf(0x80000000L, 0xFFFFFFFFL)) {
            val raw = section(profile, key, 65536)
            val before = raw.copyOf()
            val result = Gen3BerryPowderEditing.replace(raw, profile, 99999)
            assertArrayEquals(before.copyOfRange(profile.keyOffset, profile.keyOffset + 4),
                result.copyOfRange(profile.keyOffset, profile.keyOffset + 4))
            assertArrayEquals(before.copyOfRange(profile.powderOffset - 8, profile.powderOffset),
                result.copyOfRange(profile.powderOffset - 8, profile.powderOffset))
            assertArrayEquals(before.copyOfRange(profile.powderOffset + 4, profile.powderOffset + 12),
                result.copyOfRange(profile.powderOffset + 4, profile.powderOffset + 12))
            assertEquals(99999, Gen3BerryPowderEditing.read(result, profile))
            assertArrayEquals(before, raw)
        }
    }

    @Test
    fun invalidTargetsAndUnsignedDamagedAmountsRejectWithoutMutation() {
        for (profile in Gen3BerryPowderProfile.values()) {
            val raw = section(profile, 0xFFFFFFFFL, 0)
            val before = raw.copyOf()
            for (powder in listOf(-1, 100000, Int.MIN_VALUE, Int.MAX_VALUE)) {
                var rejected = false
                try { Gen3BerryPowderEditing.replace(raw, profile, powder) } catch (_: IllegalArgumentException) { rejected = true }
                assertTrue(rejected); assertArrayEquals(before, raw)
            }
            for (bad in listOf(100000L, 0x80000000L, 0xFFFFFFFFL)) {
                val damaged = section(profile, 0x1234ABCDL, bad)
                val original = damaged.copyOf()
                var rejected = false
                try { Gen3BerryPowderEditing.replace(damaged, profile, 0) } catch (_: IllegalArgumentException) { rejected = true }
                assertTrue(rejected); assertArrayEquals(original, damaged)
            }
        }
    }

    @Test
    fun incompleteAndOversizedTrainerSectionsRejectWithoutMutation() {
        for (profile in Gen3BerryPowderProfile.values()) {
            val valid = section(profile, 0, 0)
            for (raw in listOf(valid.copyOf(0xF2B), valid.copyOf(0xF2D), ByteArray(0))) {
                val before = raw.copyOf()
                var rejected = false
                try { Gen3BerryPowderEditing.replace(raw, profile, 1) } catch (_: IllegalArgumentException) { rejected = true }
                assertTrue(rejected); assertArrayEquals(before, raw)
            }
        }
    }

    @Test
    fun noOpReturnsDetachedExactSectionIncludingEncryptionKey() {
        for (profile in Gen3BerryPowderProfile.values()) for (powder in listOf(0, 99999)) {
            val raw = section(profile, 0xF2345678L, powder.toLong())
            val before = raw.copyOf()
            val result = Gen3BerryPowderEditing.replace(raw, profile, powder)
            assertArrayEquals(before, result)
            result[profile.keyOffset] = (result[profile.keyOffset].toInt() xor 1).toByte()
            assertArrayEquals(before, raw)
            assertTrue(result[profile.keyOffset] != raw[profile.keyOffset])
        }
    }
}
