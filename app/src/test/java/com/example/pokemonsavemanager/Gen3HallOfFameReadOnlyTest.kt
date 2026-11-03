package com.example.pokemonsavemanager

import org.junit.Assert.*
import org.junit.Test

class Gen3HallOfFameReadOnlyTest {
    private fun put(bytes: ByteArray, offset: Int, value: Long) {
        for (i in 0..3) bytes[offset + i] = (value ushr (8 * i)).toByte()
    }

    private fun read(profile: Gen3ActivityProfile, count: Long, time: Long, key: Long = 0L): Gen3HallOfFameRecord {
        val trainer = ByteArray(0xF2C)
        profile.keyOffset?.let { put(trainer, it, key) }
        val effectiveKey = if (profile.keyOffset == null) 0L else key
        val stats = ByteArray(0xF80)
        put(stats, profile.sectionOffset + 4, time xor effectiveKey)
        put(stats, profile.sectionOffset + 40, count xor effectiveKey)
        return Gen3RecordedActivityReadOnly.readHallOfFame(trainer, stats, profile)
    }

    @Test fun independentWireFixturesDecodePackedTimeAndUnsignedCountForEveryProfile() {
        for ((profile, timeOffset, countOffset) in listOf(
            Triple(Gen3ActivityProfile.RUBY_SAPPHIRE, 0x5C4, 0x5E8),
            Triple(Gen3ActivityProfile.EMERALD, 0x620, 0x644),
            Triple(Gen3ActivityProfile.FIRERED_LEAFGREEN, 0x284, 0x2A8))) {
            val trainer = ByteArray(0xF2C) { 0xFF.toByte() }
            val stats = ByteArray(0xF80)
            val time = if (profile == Gen3ActivityProfile.RUBY_SAPPHIRE)
                byteArrayOf(0x3B, 0x22, 0x23, 0x01) else
                byteArrayOf(0xC4.toByte(), 0xDD.toByte(), 0xDC.toByte(), 0xFE.toByte())
            val count = if (profile == Gen3ActivityProfile.RUBY_SAPPHIRE)
                byteArrayOf(0x01, 0, 0, 0x80.toByte()) else
                byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F)
            time.copyInto(stats, timeOffset); count.copyInto(stats, countOffset)
            val result = Gen3RecordedActivityReadOnly.readHallOfFame(trainer, stats, profile)
            assertEquals(0x80000001L, result.entryCount)
            assertEquals(0x0123223BL, result.rawTime)
            assertEquals(291, result.hours); assertEquals(34, result.minutes); assertEquals(59, result.seconds)
            assertTrue(result.hasRecordedEntry); assertTrue(result.timeFieldsValid)
        }
    }

    @Test fun absentEntryDoesNotBecomePresentFromNonzeroPackedTime() {
        for (profile in Gen3ActivityProfile.values()) {
            val absent = read(profile, 0L, 0x12343B3BL, 0x12345678L)
            assertFalse(absent.hasRecordedEntry)
            assertEquals(0x12343B3BL, absent.rawTime)
            val zeroTime = read(profile, 1L, 0L, 0x80000000L)
            assertTrue(zeroTime.hasRecordedEntry); assertTrue(zeroTime.timeFieldsValid)
            assertEquals(0, zeroTime.hours); assertEquals(0, zeroTime.minutes); assertEquals(0, zeroTime.seconds)
        }
    }

    @Test fun invalidMinuteOrSecondAndOversizedHoursArePreservedWithoutClamping() {
        for (profile in Gen3ActivityProfile.values()) {
            for (packed in listOf(0x00013C00L, 0x0001003CL, 0xFFFFFFFFL)) {
                val result = read(profile, 1L, packed)
                assertFalse(result.timeFieldsValid); assertEquals(packed, result.rawTime)
            }
            val maximumHours = read(profile, 1L, 0xFFFF3B3BL)
            assertTrue(maximumHours.timeFieldsValid)
            assertEquals(65535, maximumHours.hours)
            assertEquals(59, maximumHours.minutes); assertEquals(59, maximumHours.seconds)
        }
    }

    @Test fun fullWidthKeyAndCounterBoundariesRemainExact() {
        for (profile in Gen3ActivityProfile.values()) for (key in listOf(0L, 0xFFFFFFFFL, 0x80000000L, 0x12345678L)) {
            for (value in listOf(0L, 1L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
                val result = read(profile, value, value xor 0xFFFFFFFFL, key)
                assertEquals(value, result.entryCount)
                assertEquals(value xor 0xFFFFFFFFL, result.rawTime)
            }
        }
    }

    @Test fun readsDoNotMutateInputOrAffectExistingCounterSnapshot() {
        for (profile in Gen3ActivityProfile.values()) {
            val trainer = ByteArray(0xF2C) { (it * 31).toByte() }
            val stats = ByteArray(0xF80) { (it * 17).toByte() }
            val t = trainer.copyOf(); val s = stats.copyOf()
            val counters = Gen3RecordedActivityReadOnly.read(trainer, stats, profile)
            val result = Gen3RecordedActivityReadOnly.readHallOfFame(trainer, stats, profile)
            assertArrayEquals(t, trainer); assertArrayEquals(s, stats)
            assertEquals(counters, Gen3RecordedActivityReadOnly.read(trainer, stats, profile))
            trainer.fill(0); stats.fill(0)
            assertEquals(result, Gen3RecordedActivityReadOnly.readHallOfFame(t, s, profile))
        }
    }

    @Test fun incompleteOrOversizedSectionsRejectWithoutMutation() {
        for (profile in Gen3ActivityProfile.values()) for ((tSize, sSize) in listOf(
            0 to 0, 0xF2B to 0xF80, 0xF2D to 0xF80, 0xF2C to 0xF7F, 0xF2C to 0xF81)) {
            val trainer = ByteArray(tSize) { 0x5A }; val stats = ByteArray(sSize) { 0x6A }
            val t = trainer.copyOf(); val s = stats.copyOf()
            var rejected = false
            try { Gen3RecordedActivityReadOnly.readHallOfFame(trainer, stats, profile) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected); assertArrayEquals(t, trainer); assertArrayEquals(s, stats)
        }
    }
}
