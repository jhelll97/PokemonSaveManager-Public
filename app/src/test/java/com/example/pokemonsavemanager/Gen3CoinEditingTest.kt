package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3CoinEditingTest {
    private fun trainer(profile: Gen3CoinProfile, key: Long): ByteArray =
        ByteArray(0xF2C) { (it * 11 + 7).toByte() }.also { raw ->
            profile.keyOffset?.let { offset -> for (index in 0..3) raw[offset + index] = (key ushr (index * 8)).toByte() }
        }

    private fun section(profile: Gen3CoinProfile, key: Long, coins: Int): ByteArray =
        ByteArray(0xF80) { (it * 17 + 3).toByte() }.also { raw ->
            val stored = coins xor (if (profile.keyOffset == null) 0 else (key and 65535).toInt())
            raw[profile.coinOffset] = stored.toByte()
            raw[profile.coinOffset + 1] = (stored ushr 8).toByte()
        }

    @Test
    fun allProfilesRoundTripBoundaryBalancesWithUnsignedKeysAndExactScope() {
        for (profile in Gen3CoinProfile.values()) for (key in listOf(0L, 1L, 0x1234ABCDL, 0xFFFFFFFFL)) {
            val small = trainer(profile, key)
            val raw = section(profile, key, 1234)
            val smallBefore = small.copyOf(); val before = raw.copyOf()
            for (coins in listOf(0, 1, 255, 256, 9998, 9999)) {
                val edited = Gen3CoinEditing.replace(small, raw, profile, coins)
                assertEquals(coins, Gen3CoinEditing.read(small, edited, profile))
                assertTrue(raw.indices.all { it in profile.coinOffset..profile.coinOffset + 1 || raw[it] == edited[it] })
                assertArrayEquals(before, raw); assertArrayEquals(smallBefore, small)
            }
        }
    }

    @Test
    fun fixedNativeWireBytesUseCorrectOffsetsAndLowHalfOfEncryptionKey() {
        val cases = listOf(Triple(Gen3CoinProfile.RUBY_SAPPHIRE, 0x494, null),
            Triple(Gen3CoinProfile.EMERALD, 0x494, 0xAC), Triple(Gen3CoinProfile.FIRERED_LEAFGREEN, 0x294, 0xF20))
        for ((profile, offset, keyOffset) in cases) {
            val small = ByteArray(0xF2C)
            if (keyOffset != null) byteArrayOf(0xCD.toByte(), 0xAB.toByte(), 0x34, 0x12).copyInto(small, keyOffset)
            val raw = ByteArray(0xF80)
            // 0x04D2 (1234) XOR 0xABCD = 0xAF1F, stored little-endian.
            (if (keyOffset == null) byteArrayOf(0xD2.toByte(), 0x04) else byteArrayOf(0x1F, 0xAF.toByte())).copyInto(raw, offset)
            assertEquals(1234, Gen3CoinEditing.read(small, raw, profile))
            val expected = raw.copyOf()
            // 0x270F (9999) XOR 0xABCD = 0x8CC2.
            (if (keyOffset == null) byteArrayOf(0x0F, 0x27) else byteArrayOf(0xC2.toByte(), 0x8C.toByte())).copyInto(expected, offset)
            assertArrayEquals(expected, Gen3CoinEditing.replace(small, raw, profile, 9999))
            assertEquals(offset, profile.coinOffset); assertEquals(keyOffset, profile.keyOffset)
        }
    }

    @Test
    fun rubySapphireIgnoresUnrelatedTrainerBytesAndExpandedKeysUseTheirOwnOffsets() {
        for (profile in Gen3CoinProfile.values()) {
            val small = trainer(profile, 0xFFFFFFFFL)
            val raw = section(profile, 0xFFFFFFFFL, 4321)
            val modified = small.copyOf()
            val keyStart = profile.keyOffset
            for (index in modified.indices) {
                if (keyStart == null || index !in keyStart..keyStart + 1)
                    modified[index] = (modified[index].toInt() xor 255).toByte()
            }
            assertEquals(4321, Gen3CoinEditing.read(modified, raw, profile))
            assertArrayEquals(Gen3CoinEditing.replace(small, raw, profile, 9999),
                Gen3CoinEditing.replace(modified, raw, profile, 9999))
        }
    }

    @Test
    fun invalidBalancesAndMalformedLoadedValuesRejectWithoutMutation() {
        for (profile in Gen3CoinProfile.values()) {
            val small = trainer(profile, 0xFEDCBA98L)
            val raw = section(profile, 0xFEDCBA98L, 0)
            for (coins in listOf(-1, 10000, 65535, Int.MIN_VALUE, Int.MAX_VALUE)) {
                val before = raw.copyOf(); val smallBefore = small.copyOf()
                var rejected = false
                try { Gen3CoinEditing.replace(small, raw, profile, coins) } catch (_: IllegalArgumentException) { rejected = true }
                assertTrue(rejected); assertArrayEquals(before, raw); assertArrayEquals(smallBefore, small)
            }
            for (badValue in listOf(10000, 65535)) {
                val damaged = section(profile, 0xFEDCBA98L, badValue)
                val before = damaged.copyOf()
                var rejected = false
                try { Gen3CoinEditing.replace(small, damaged, profile, 0) } catch (_: IllegalArgumentException) { rejected = true }
                assertTrue(rejected); assertArrayEquals(before, damaged)
            }
        }
    }

    @Test
    fun incompleteOrOversizedSectionsRejectBeforeReadingOrWriting() {
        for (profile in Gen3CoinProfile.values()) {
            val small = trainer(profile, 0); val raw = section(profile, 0, 10)
            val pairs = listOf(small.copyOf(0xF2B) to raw, small.copyOf(0xF2D) to raw,
                small to raw.copyOf(0xF7F), small to raw.copyOf(0xF81), ByteArray(0) to ByteArray(0))
            for ((s0, s1) in pairs) {
                val before0 = s0.copyOf(); val before1 = s1.copyOf()
                var rejected = false
                try { Gen3CoinEditing.replace(s0, s1, profile, 20) } catch (_: IllegalArgumentException) { rejected = true }
                assertTrue(rejected); assertArrayEquals(before0, s0); assertArrayEquals(before1, s1)
            }
        }
    }

    @Test
    fun noOpReturnsDetachedExactSectionAndRetainsMoneyRegisteredItemAndInventory() {
        for (profile in Gen3CoinProfile.values()) {
            val small = trainer(profile, 0xF2345678L)
            val raw = section(profile, 0xF2345678L, 9999)
            val result = Gen3CoinEditing.replace(small, raw, profile, 9999)
            assertArrayEquals(raw, result)
            result[profile.coinOffset - 1] = (result[profile.coinOffset - 1].toInt() xor 1).toByte()
            assertTrue(raw[profile.coinOffset - 1] != result[profile.coinOffset - 1])
        }
    }
}
