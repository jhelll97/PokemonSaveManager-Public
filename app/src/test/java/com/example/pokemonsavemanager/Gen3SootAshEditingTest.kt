package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3SootAshEditingTest {
    private fun section(profile: Gen3SootAshProfile, ash: Int, seed: Int = 1): ByteArray =
        ByteArray(0xF80) { (it * 17 + seed).toByte() }.also {
            it[profile.sectionOffset] = ash.toByte(); it[profile.sectionOffset + 1] = (ash ushr 8).toByte()
        }

    @Test
    fun fixedNativeWireBytesUseSecondSaveBlock1SectionAndTitleOffsets() {
        assertEquals(2, Gen3SootAshEditing.SECTION_ID)
        for ((profile, offset) in listOf(Gen3SootAshProfile.RUBY_SAPPHIRE to 0x450,
            Gen3SootAshProfile.EMERALD to 0x4AC)) {
            val raw = ByteArray(0xF80)
            byteArrayOf(0xCD.toByte(), 0xAB.toByte(), 0xD2.toByte(), 0x04, 0xFE.toByte(), 0xDC.toByte()).copyInto(raw, offset - 2)
            assertEquals(1234, Gen3SootAshEditing.read(raw, profile))
            val expected = raw.copyOf()
            byteArrayOf(0x0F, 0x27).copyInto(expected, offset)
            assertArrayEquals(expected, Gen3SootAshEditing.replace(raw, profile, 9999))
            assertEquals(offset, profile.sectionOffset)
            assertEquals(offset + 0xF80, profile.saveBlockOffset)
        }
    }

    @Test
    fun boundaryCountsRoundTripWithExactScopeAndImmutableInputs() {
        for (profile in Gen3SootAshProfile.values()) for (seed in listOf(0, 1, 127, 128, 255)) {
            val raw = section(profile, 4321, seed)
            val before = raw.copyOf()
            for (ash in listOf(0, 1, 255, 256, 9998, 9999)) {
                val result = Gen3SootAshEditing.replace(raw, profile, ash)
                assertEquals(ash, Gen3SootAshEditing.read(result, profile))
                assertTrue(raw.indices.all { it in profile.sectionOffset..profile.sectionOffset + 1 || raw[it] == result[it] })
                assertArrayEquals(before, raw)
            }
        }
    }

    @Test
    fun neighbourVariablesAndOtherTitleLocationAreNotChanged() {
        for (profile in Gen3SootAshProfile.values()) {
            val raw = section(profile, 5000)
            val before = raw.copyOf()
            val result = Gen3SootAshEditing.replace(raw, profile, 9999)
            val start = profile.sectionOffset
            assertArrayEquals(before.copyOfRange(start - 8, start), result.copyOfRange(start - 8, start))
            assertArrayEquals(before.copyOfRange(start + 2, start + 10), result.copyOfRange(start + 2, start + 10))
            val other = if (profile == Gen3SootAshProfile.EMERALD) 0x450 else 0x4AC
            assertArrayEquals(before.copyOfRange(other, other + 2), result.copyOfRange(other, other + 2))
            assertArrayEquals(before, raw)
        }
    }

    @Test
    fun invalidTargetsRejectWithoutMutation() {
        for (profile in Gen3SootAshProfile.values()) {
            val raw = section(profile, 0)
            val before = raw.copyOf()
            for (ash in listOf(-1, 10000, 65535, Int.MIN_VALUE, Int.MAX_VALUE)) {
                var rejected = false
                try { Gen3SootAshEditing.replace(raw, profile, ash) } catch (_: IllegalArgumentException) { rejected = true }
                assertTrue(rejected); assertArrayEquals(before, raw)
            }
        }
    }

    @Test
    fun malformedSourceCounterAndWrongSectionLengthsRejectWithoutMutation() {
        for (profile in Gen3SootAshProfile.values()) {
            val cases = listOf(section(profile, 10000), section(profile, 65535),
                section(profile, 0).copyOf(0xF7F), section(profile, 0).copyOf(0xF81), ByteArray(0))
            for (raw in cases) {
                val before = raw.copyOf()
                var rejectedRead = false
                try { Gen3SootAshEditing.read(raw, profile) } catch (_: IllegalArgumentException) { rejectedRead = true }
                var rejectedWrite = false
                try { Gen3SootAshEditing.replace(raw, profile, 0) } catch (_: IllegalArgumentException) { rejectedWrite = true }
                assertTrue(rejectedRead && rejectedWrite); assertArrayEquals(before, raw)
            }
        }
    }

    @Test
    fun noOpReturnsDetachedExactSectionIncludingScriptVariables() {
        for (profile in Gen3SootAshProfile.values()) for (ash in listOf(0, 9999)) {
            val raw = section(profile, ash)
            val before = raw.copyOf()
            val result = Gen3SootAshEditing.replace(raw, profile, ash)
            assertArrayEquals(before, result)
            result[profile.sectionOffset - 1] = (result[profile.sectionOffset - 1].toInt() xor 1).toByte()
            assertArrayEquals(before, raw)
            assertTrue(result[profile.sectionOffset - 1] != raw[profile.sectionOffset - 1])
        }
    }
}
