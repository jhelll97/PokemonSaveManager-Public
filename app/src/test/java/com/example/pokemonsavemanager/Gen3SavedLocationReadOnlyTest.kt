package com.example.pokemonsavemanager

import org.junit.Assert.*
import org.junit.Test

class Gen3SavedLocationReadOnlyTest {
    @Test fun independentNativeWireFixtureDecodesOffsetsAndSignedFields() {
        val raw = ByteArray(0xF80) { 0x5A }
        byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0, 0x80.toByte()).copyInto(raw, 0)
        byteArrayOf(0xFF.toByte(), 0x7F, 0x80.toByte(), 0xA5.toByte(), 0x34, 0x12, 0xFF.toByte(), 0xFF.toByte()).copyInto(raw, 4)
        byteArrayOf(2, 3, 4, 0x5A, 0xFF.toByte(), 0x7F, 0, 0x80.toByte()).copyInto(raw, 0x1C)
        raw[0x32] = 0xCD.toByte(); raw[0x33] = 0xAB.toByte()
        val before = raw.copyOf()
        assertEquals(1, Gen3SavedLocationReadOnly.SECTION_ID)
        assertEquals(Gen3SavedLocation(-2, -32768, Gen3SavedWarp(-1, 127, -128, 4660, -1),
            Gen3SavedWarp(2, 3, 4, 32767, -32768), 0xABCD,
            Gen3SavedFieldState(0x5A5A, 0x5A, 0x5A, 0x5A),
            Gen3SavedWarp(0x5A, 0x5A, 0x5A, 0x5A5A, 0x5A5A),
            Gen3SavedWarp(0x5A, 0x5A, 0x5A, 0x5A5A, 0x5A5A),
            Gen3SavedWarp(0x5A, 0x5A, 0x5A, 0x5A5A, 0x5A5A)), Gen3SavedLocationReadOnly.read(raw))
        assertArrayEquals(before, raw)
    }

    @Test fun everyNativeMapAndWarpByteRetainsSignedMeaning() {
        val raw = ByteArray(0xF80)
        for (value in 0..255) {
            for (offset in listOf(4, 5, 6, 0x0C, 0x0D, 0x0E, 0x14, 0x15, 0x16, 0x1C, 0x1D, 0x1E, 0x24, 0x25, 0x26)) raw[offset] = value.toByte()
            val result = Gen3SavedLocationReadOnly.read(raw)
            val expected = value.toByte().toInt()
            assertEquals(expected, result.location.mapGroup); assertEquals(expected, result.location.mapNumber)
            assertEquals(expected, result.location.warpId); assertEquals(expected, result.lastHeal.mapGroup)
            assertEquals(expected, result.lastHeal.mapNumber); assertEquals(expected, result.lastHeal.warpId)
            assertEquals(expected, result.dynamicWarp.mapGroup); assertEquals(expected, result.dynamicWarp.mapNumber)
            assertEquals(expected, result.dynamicWarp.warpId)
            for (warp in listOf(result.warpAt0C, result.warpAt24)) {
                assertEquals(expected, warp.mapGroup); assertEquals(expected, warp.mapNumber)
                assertEquals(expected, warp.warpId)
            }
        }
    }

    @Test fun coordinatesAndUnsignedLayoutKeepBoundaryValues() {
        val raw = ByteArray(0xF80)
        for (wire in listOf(0, 1, 32767, 32768, 65534, 65535)) {
            for (offset in listOf(0, 2, 8, 10, 0x10, 0x12, 0x18, 0x1A, 0x20, 0x22, 0x28, 0x2A, 0x32)) {
                raw[offset] = wire.toByte(); raw[offset + 1] = (wire ushr 8).toByte()
            }
            val result = Gen3SavedLocationReadOnly.read(raw)
            val expected = wire.toShort().toInt()
            assertEquals(expected, result.playerX); assertEquals(expected, result.playerY)
            assertEquals(expected, result.location.x); assertEquals(expected, result.location.y)
            assertEquals(expected, result.lastHeal.x); assertEquals(expected, result.lastHeal.y)
            assertEquals(expected, result.dynamicWarp.x); assertEquals(expected, result.dynamicWarp.y)
            assertEquals(expected, result.warpAt0C.x); assertEquals(expected, result.warpAt0C.y)
            assertEquals(expected, result.warpAt24.x); assertEquals(expected, result.warpAt24.y)
            assertEquals(wire, result.mapLayoutId)
        }
    }

    @Test fun paddingAndUnrelatedBytesCannotChangeTheView() {
        val raw = ByteArray(0xF80)
        val expected = Gen3SavedLocationReadOnly.read(raw)
        for (offset in listOf(7, 0x0F, 0x17, 0x1F, 0x27, 0x34, 0xF7F)) raw[offset] = 0xFF.toByte()
        assertEquals(expected, Gen3SavedLocationReadOnly.read(raw))
    }

    @Test fun independentDynamicWarpWireKeepsSentinelsPaddingAndSignedCoordinates() {
        val raw = ByteArray(0xF80) { 0x5A }
        byteArrayOf(0xFF.toByte(), 0x80.toByte(), 0x7F, 0xCD.toByte(),
            0, 0x80.toByte(), 0xFF.toByte(), 0x7F).copyInto(raw, 0x14)
        val before = raw.copyOf()
        assertEquals(Gen3SavedWarp(-1, -128, 127, -32768, 32767),
            Gen3SavedLocationReadOnly.read(raw).dynamicWarp)
        assertArrayEquals(before, raw)
    }

    @Test fun dynamicRecordIsIsolatedFromEveryOtherDisplayedFieldAndDetached() {
        val raw = ByteArray(0xF80) { (it * 19).toByte() }
        val before = Gen3SavedLocationReadOnly.read(raw)
        byteArrayOf(3, 0, 0xFF.toByte(), 0xA5.toByte(), 0x34, 0x12, 0xFE.toByte(), 0xFF.toByte()).copyInto(raw, 0x14)
        val snapshot = raw.copyOf()
        val after = Gen3SavedLocationReadOnly.read(raw)
        assertEquals(Gen3SavedWarp(3, 0, -1, 4660, -2), after.dynamicWarp)
        assertEquals(before, after.copy(dynamicWarp = before.dynamicWarp))
        assertArrayEquals(snapshot, raw)
        raw.fill(0)
        assertEquals(Gen3SavedLocationReadOnly.read(snapshot), after)
    }

    @Test fun remainingWarpRecordsUseIndependentWireOffsetsAndSignedValues() {
        val raw = ByteArray(0xF80) { 0x5A }
        byteArrayOf(0x80.toByte(), 0xFF.toByte(), 0x7F, 1,
            0x34, 0x12, 0xFE.toByte(), 0xFF.toByte()).copyInto(raw, 0x0C)
        byteArrayOf(3, 0, 0xFF.toByte(), 0xA5.toByte(),
            0, 0x80.toByte(), 0xFF.toByte(), 0x7F).copyInto(raw, 0x24)
        val snapshot = raw.copyOf()
        val result = Gen3SavedLocationReadOnly.read(raw)
        assertEquals(Gen3SavedWarp(-128, -1, 127, 4660, -2), result.warpAt0C)
        assertEquals(Gen3SavedWarp(3, 0, -1, -32768, 32767), result.warpAt24)
        assertArrayEquals(snapshot, raw)
    }

    @Test fun changingEitherRemainingWarpCannotAffectOtherRecordsOrAliasTheInput() {
        for (offset in listOf(0x0C, 0x24)) {
            val raw = ByteArray(0xF80) { (it * 19).toByte() }
            val before = Gen3SavedLocationReadOnly.read(raw)
            byteArrayOf(1, 2, 3, 0xA5.toByte(), 4, 0, 0xFF.toByte(), 0xFF.toByte()).copyInto(raw, offset)
            val snapshot = raw.copyOf()
            val after = Gen3SavedLocationReadOnly.read(raw)
            if (offset == 0x0C) {
                assertEquals(Gen3SavedWarp(1, 2, 3, 4, -1), after.warpAt0C)
                assertEquals(before, after.copy(warpAt0C = before.warpAt0C))
            } else {
                assertEquals(Gen3SavedWarp(1, 2, 3, 4, -1), after.warpAt24)
                assertEquals(before, after.copy(warpAt24 = before.warpAt24))
            }
            assertArrayEquals(snapshot, raw)
            raw.fill(0)
            assertEquals(Gen3SavedLocationReadOnly.read(snapshot), after)
        }
    }

    @Test fun resultDoesNotAliasTheSourceAndReadNeverWrites() {
        val raw = ByteArray(0xF80) { (it * 31).toByte() }
        val before = raw.copyOf()
        val result = Gen3SavedLocationReadOnly.read(raw)
        assertArrayEquals(before, raw)
        raw.fill(0)
        assertEquals(Gen3SavedLocationReadOnly.read(before), result)
    }

    @Test fun wrongSectionLengthsRejectWithoutChangingInput() {
        for (size in listOf(0, 0xF7F, 0xF81)) {
            val raw = ByteArray(size) { 0x6A }
            val before = raw.copyOf()
            var rejected = false
            try { Gen3SavedLocationReadOnly.read(raw) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected); assertArrayEquals(before, raw)
        }
    }

    @Test fun savedFieldSettingsUseIndependentNativeWireBytes() {
        val raw = ByteArray(0xF80) { 0x4B }
        byteArrayOf(0x34, 0x12, 0xFF.toByte(), 0x80.toByte(), 0xFE.toByte()).copyInto(raw, 0x2C)
        val before = raw.copyOf()
        assertEquals(Gen3SavedFieldState(0x1234, 255, 128, 254), Gen3SavedLocationReadOnly.read(raw).fieldState)
        assertArrayEquals(before, raw)
    }

    @Test fun fieldByteValuesAreNeverSignedOrSilentlyClamped() {
        val raw = ByteArray(0xF80)
        for (value in 0..255) {
            raw[0x2E] = value.toByte()
            raw[0x2F] = (255 - value).toByte()
            raw[0x30] = (value xor 0x80).toByte()
            assertEquals(Gen3SavedFieldState(0, value, 255 - value, value xor 0x80),
                Gen3SavedLocationReadOnly.read(raw).fieldState)
        }
    }

    @Test fun musicRetainsTheEntireUnsignedWordRange() {
        val raw = ByteArray(0xF80)
        for (value in 0..65535) {
            raw[0x2C] = value.toByte()
            raw[0x2D] = (value ushr 8).toByte()
            assertEquals(value, Gen3SavedLocationReadOnly.read(raw).fieldState.musicId)
        }
    }

    @Test fun fieldSettingsDoNotAliasCoordinatesLayoutOrPadding() {
        val raw = ByteArray(0xF80) { (it * 19).toByte() }
        val before = Gen3SavedLocationReadOnly.read(raw)
        byteArrayOf(0, 0, 0, 0, 0).copyInto(raw, 0x2C)
        raw[0x31] = 0x7F
        val after = Gen3SavedLocationReadOnly.read(raw)
        assertEquals(before.playerX, after.playerX); assertEquals(before.playerY, after.playerY)
        assertEquals(before.location, after.location); assertEquals(before.lastHeal, after.lastHeal)
        assertEquals(before.dynamicWarp, after.dynamicWarp)
        assertEquals(before.warpAt0C, after.warpAt0C); assertEquals(before.warpAt24, after.warpAt24)
        assertEquals(before.mapLayoutId, after.mapLayoutId)
        assertEquals(Gen3SavedFieldState(0, 0, 0, 0), after.fieldState)
        raw[0x2C] = 1
        assertEquals(0, after.fieldState.musicId)
    }
}
