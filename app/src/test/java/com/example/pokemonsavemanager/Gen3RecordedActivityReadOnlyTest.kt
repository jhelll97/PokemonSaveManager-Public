package com.example.pokemonsavemanager

import org.junit.Assert.*
import org.junit.Test

class Gen3RecordedActivityReadOnlyTest {
    private fun put(bytes: ByteArray, offset: Int, value: Long) {
        for (i in 0..3) bytes[offset + i] = (value ushr (8 * i)).toByte()
    }

    @Test fun pinnedLayoutsAndIndependentWireFixturesDecodeSteps() {
        assertEquals(2, Gen3RecordedActivityReadOnly.SECTION_ID)
        assertEquals(0x5C0, Gen3ActivityProfile.RUBY_SAPPHIRE.sectionOffset)
        assertEquals(0x61C, Gen3ActivityProfile.EMERALD.sectionOffset)
        assertEquals(0x280, Gen3ActivityProfile.FIRERED_LEAFGREEN.sectionOffset)
        assertEquals(0xAC, Gen3ActivityProfile.EMERALD.keyOffset)
        assertEquals(0xF20, Gen3ActivityProfile.FIRERED_LEAFGREEN.keyOffset)
        for (profile in Gen3ActivityProfile.values()) {
            val trainer = ByteArray(0xF2C) { 0xFF.toByte() }
            val raw = ByteArray(0xF80)
            val wire = if (profile == Gen3ActivityProfile.RUBY_SAPPHIRE)
                byteArrayOf(0x78, 0x56, 0x34, 0x12) else
                byteArrayOf(0x87.toByte(), 0xA9.toByte(), 0xCB.toByte(), 0xED.toByte())
            wire.copyInto(raw, profile.sectionOffset + 20)
            assertEquals(0x12345678L, Gen3RecordedActivityReadOnly.read(trainer, raw, profile).single { it.id == 5 }.value)
        }
    }

    @Test fun completeUnsignedValuesAndFullWidthKeysRemainExact() {
        for (profile in Gen3ActivityProfile.values()) for (key in listOf(0L, 0xFFFFFFFFL, 0x80000000L, 0x12345678L)) {
            val trainer = ByteArray(0xF2C)
            profile.keyOffset?.let { put(trainer, it, key) }
            for (value in listOf(0L, 1L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
                val raw = ByteArray(0xF80)
                put(raw, profile.sectionOffset + 20, value xor (if (profile.keyOffset == null) 0L else key))
                assertEquals(value, Gen3RecordedActivityReadOnly.read(trainer, raw, profile).single { it.id == 5 }.value)
            }
        }
    }

    @Test fun exactCounterIdsAndOrderDoNotInventAdditionalStatistics() {
        for (profile in Gen3ActivityProfile.values()) {
            val counters = Gen3RecordedActivityReadOnly.read(ByteArray(0xF2C), ByteArray(0xF80), profile)
            val expected = if (profile == Gen3ActivityProfile.EMERALD)
                listOf(0, 5, 7, 8, 9, 11, 12, 13, 14, 15, 18, 19, 21) else
                listOf(0, 5, 7, 8, 9, 11, 13, 14, 15, 18, 19, 21)
            assertEquals(expected, counters.map { it.id })
            assertEquals(expected.size, counters.map { it.label }.distinct().size)
            assertTrue(counters.all { it.label.isNotBlank() && it.value == 0L })
            assertEquals(profile == Gen3ActivityProfile.EMERALD, counters.any { it.label == "Fishing encounters" })
        }
    }

    @Test fun fieldUsesDecodeFixedNativeOffsetsAndRemainSeparate() {
        for ((profile, cutOffset, smashOffset) in listOf(
            Triple(Gen3ActivityProfile.RUBY_SAPPHIRE, 0x608, 0x60C),
            Triple(Gen3ActivityProfile.EMERALD, 0x664, 0x668),
            Triple(Gen3ActivityProfile.FIRERED_LEAFGREEN, 0x2C8, 0x2CC))) {
            val trainer = ByteArray(0xF2C) { 0xFF.toByte() }
            val raw = ByteArray(0xF80)
            val before = Gen3RecordedActivityReadOnly.read(trainer, raw, profile)
            val cutWire = if (profile == Gen3ActivityProfile.RUBY_SAPPHIRE)
                byteArrayOf(0x78, 0x56, 0x34, 0x12) else
                byteArrayOf(0x87.toByte(), 0xA9.toByte(), 0xCB.toByte(), 0xED.toByte())
            val smashWire = if (profile == Gen3ActivityProfile.RUBY_SAPPHIRE)
                byteArrayOf(0x21, 0x43, 0x65, 0x87.toByte()) else
                byteArrayOf(0xDE.toByte(), 0xBC.toByte(), 0x9A.toByte(), 0x78)
            cutWire.copyInto(raw, cutOffset); smashWire.copyInto(raw, smashOffset)
            val after = Gen3RecordedActivityReadOnly.read(trainer, raw, profile)
            assertEquals(Gen3ActivityCounter(18, "Cut field uses", 0x12345678L), after.single { it.id == 18 })
            assertEquals(Gen3ActivityCounter(19, "Rock Smash field uses", 0x87654321L), after.single { it.id == 19 })
            assertEquals(before.filter { it.id != 18 && it.id != 19 }, after.filter { it.id != 18 && it.id != 19 })
        }
    }

    @Test fun fieldUsesKeepFullUnsignedValuesWithZeroAndHighBitKeys() {
        for (profile in Gen3ActivityProfile.values()) for (key in listOf(0L, 0xFFFFFFFFL, 0x80000000L, 0x12345678L)) {
            val trainer = ByteArray(0xF2C)
            profile.keyOffset?.let { put(trainer, it, key) }
            val effectiveKey = if (profile.keyOffset == null) 0L else key
            for (value in listOf(0L, 1L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
                val raw = ByteArray(0xF80)
                val otherValue = value xor 0xFFFFFFFFL
                put(raw, profile.sectionOffset + 4 * 18, value xor effectiveKey)
                put(raw, profile.sectionOffset + 4 * 19, otherValue xor effectiveKey)
                val snapshot = raw.copyOf()
                val counters = Gen3RecordedActivityReadOnly.read(trainer, raw, profile)
                assertEquals(value, counters.single { it.id == 18 }.value)
                assertEquals(otherValue, counters.single { it.id == 19 }.value)
                assertArrayEquals(snapshot, raw)
            }
        }
    }

    @Test fun emeraldFishingUsesIndependentWireFixtureWithoutChangingCaptureCounter() {
        val trainer = ByteArray(0xF2C)
        byteArrayOf(0x78, 0x56, 0x34, 0x12).copyInto(trainer, 0xAC)
        val raw = ByteArray(0xF80)
        // Fixed native offsets: 0x159C - 0xF80 + 4 * stat ID.
        byteArrayOf(0x77, 0x56, 0x34, 0x92.toByte()).copyInto(raw, 0x64C)
        byteArrayOf(0x7D, 0x56, 0x34, 0x12).copyInto(raw, 0x648)
        val counters = Gen3RecordedActivityReadOnly.read(trainer, raw, Gen3ActivityProfile.EMERALD)
        assertEquals(0x8000000FL, counters.single { it.id == 12 }.value)
        assertEquals("Fishing encounters", counters.single { it.id == 12 }.label)
        assertEquals(5L, counters.single { it.id == 11 }.value)
    }

    @Test fun emeraldFishingPreservesUnsignedBoundariesAndFullKey() {
        for (key in listOf(0L, 0xFFFFFFFFL, 0x80000000L, 0x12345678L)) {
            val trainer = ByteArray(0xF2C)
            put(trainer, 0xAC, key)
            for (value in listOf(0L, 1L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
                val raw = ByteArray(0xF80)
                put(raw, 0x64C, value xor key)
                assertEquals(value, Gen3RecordedActivityReadOnly.read(trainer, raw,
                    Gen3ActivityProfile.EMERALD).single { it.id == 12 }.value)
            }
        }
    }

    @Test fun profileKeyAndStatisticOffsetsAreIsolated() {
        for (profile in Gen3ActivityProfile.values()) {
            val trainer = ByteArray(0xF2C)
            put(trainer, 0xAC, 0xAABBCCDDL); put(trainer, 0xF20, 0x11223344L)
            val raw = ByteArray(0xF80)
            val expectedKey = when (profile) {
                Gen3ActivityProfile.RUBY_SAPPHIRE -> 0L
                Gen3ActivityProfile.EMERALD -> 0xAABBCCDDL
                Gen3ActivityProfile.FIRERED_LEAFGREEN -> 0x11223344L
            }
            put(raw, profile.sectionOffset + 4 * 21, 0xCAFEBABEL)
            assertEquals(0xCAFEBABEL xor expectedKey,
                Gen3RecordedActivityReadOnly.read(trainer, raw, profile).single { it.id == 21 }.value)
        }
    }

    @Test fun readingNeverMutatesInputsAndResultsAreDetached() {
        for (profile in Gen3ActivityProfile.values()) {
            val trainer = ByteArray(0xF2C) { (it * 31).toByte() }
            val raw = ByteArray(0xF80) { (it * 17).toByte() }
            val t = trainer.copyOf(); val s = raw.copyOf()
            val counters = Gen3RecordedActivityReadOnly.read(trainer, raw, profile)
            assertArrayEquals(t, trainer); assertArrayEquals(s, raw)
            trainer.fill(0); raw.fill(0)
            assertEquals(Gen3RecordedActivityReadOnly.read(t, s, profile), counters)
        }
    }

    @Test fun malformedSectionsRejectWithoutMutation() {
        for (profile in Gen3ActivityProfile.values()) for ((tSize, sSize) in listOf(0 to 0, 0xF2B to 0xF80,
            0xF2D to 0xF80, 0xF2C to 0xF7F, 0xF2C to 0xF81)) {
            val trainer = ByteArray(tSize) { 0x5A }; val raw = ByteArray(sSize) { 0x6A }
            val t = trainer.copyOf(); val s = raw.copyOf()
            var rejected = false
            try { Gen3RecordedActivityReadOnly.read(trainer, raw, profile) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected); assertArrayEquals(t, trainer); assertArrayEquals(s, raw)
        }
    }
}
