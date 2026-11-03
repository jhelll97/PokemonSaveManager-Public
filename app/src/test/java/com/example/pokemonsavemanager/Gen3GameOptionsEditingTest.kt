package com.example.pokemonsavemanager

import org.junit.Assert.*
import org.junit.Test

class Gen3GameOptionsEditingTest {
    private val native = Gen3GameOptions(2, 2, 9, 1, 1, 1)
    private fun section(): ByteArray = ByteArray(0xF2C) { (it * 37 + 11).toByte() }.also {
        it[0x13] = 2; it[0x14] = 0x4A; it[0x15] = 0xFF.toByte()
    }
    private fun rejects(action: () -> Unit) {
        var rejected = false
        try { action() } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue("Invalid options must be rejected", rejected)
    }

    @Test fun fixedWireBytesDecodeAllSixFieldsWithTitleSpecificFrames() {
        assertEquals(0, Gen3GameOptionsEditing.SECTION_ID)
        for (profile in Gen3GameOptionsProfile.values()) {
            assertEquals(native, Gen3GameOptionsEditing.read(section(), profile))
        }
        assertEquals(20, Gen3GameOptionsProfile.HOENN.frameCount)
        assertEquals(10, Gen3GameOptionsProfile.FIRERED_LEAFGREEN.frameCount)
        assertEquals("Normal", Gen3GameOptionsProfile.HOENN.firstButtonLabel)
        assertEquals("Help", Gen3GameOptionsProfile.FIRERED_LEAFGREEN.firstButtonLabel)
    }

    @Test fun replacementHasIndependentExpectedBytesAndDetachedInput() {
        val source = section()
        val original = source.copyOf()
        val target = Gen3GameOptions(1, 1, 2, 0, 1, 0)
        val expected = original.copyOf().also { it[0x13] = 1; it[0x14] = 0x11; it[0x15] = 0xFA.toByte() }
        val result = Gen3GameOptionsEditing.replace(source, Gen3GameOptionsProfile.HOENN, target)
        assertArrayEquals(expected, result)
        assertArrayEquals(original, source)
        result[0] = (result[0].toInt() xor 1).toByte()
        assertArrayEquals(original, source)
    }

    @Test fun everyLegalFrameAndChoiceCombinationReopensWithExactByteScope() {
        for (profile in Gen3GameOptionsProfile.values()) for (frame in 0 until profile.frameCount) {
            for (button in 0..2) for (speed in 0..2) for (flags in 0..7) {
                val source = section()
                val target = Gen3GameOptions(button, speed, frame, flags and 1, (flags ushr 1) and 1, (flags ushr 2) and 1)
                val result = Gen3GameOptionsEditing.replace(source, profile, target)
                assertEquals(target, Gen3GameOptionsEditing.read(result, profile))
                for (i in source.indices) if (i !in 0x13..0x15) assertEquals(source[i], result[i])
            }
        }
    }

    @Test fun mapZoomAndEveryReservedBitPatternArePreserved() {
        for (high in 0..31) {
            val source = section().also { it[0x15] = ((high shl 3) or 7).toByte() }
            val result = Gen3GameOptionsEditing.replace(source, Gen3GameOptionsProfile.HOENN,
                Gen3GameOptions(0, 0, 0, 0, 0, 0))
            assertEquals(high shl 3, result[0x15].toInt() and 255)
            assertEquals((high shl 3) or 7, source[0x15].toInt() and 255)
        }
    }

    @Test fun kantoFrameLimitRejectsHoennOnlyFramesWithoutChangingSource() {
        val source = section()
        val before = source.copyOf()
        for (frame in 10..19) {
            assertEquals(frame, Gen3GameOptionsEditing.read(
                Gen3GameOptionsEditing.replace(source, Gen3GameOptionsProfile.HOENN, native.copy(frame = frame)),
                Gen3GameOptionsProfile.HOENN).frame)
            rejects { Gen3GameOptionsEditing.replace(source, Gen3GameOptionsProfile.FIRERED_LEAFGREEN, native.copy(frame = frame)) }
        }
        assertArrayEquals(before, source)
    }

    @Test fun allInvalidTargetsRejectWithoutMutation() {
        val source = section()
        val before = source.copyOf()
        for (bad in listOf(-1, 3, 255, Int.MIN_VALUE, Int.MAX_VALUE)) {
            rejects { Gen3GameOptionsEditing.replace(source, Gen3GameOptionsProfile.HOENN, native.copy(buttonMode = bad)) }
            rejects { Gen3GameOptionsEditing.replace(source, Gen3GameOptionsProfile.HOENN, native.copy(textSpeed = bad)) }
        }
        for (bad in listOf(-1, 2, 255, Int.MAX_VALUE)) for (target in listOf(
            native.copy(sound = bad), native.copy(battleStyle = bad), native.copy(battleSceneOff = bad))) {
            rejects { Gen3GameOptionsEditing.replace(source, Gen3GameOptionsProfile.HOENN, target) }
        }
        for (bad in listOf(-1, 20, 31, Int.MAX_VALUE)) {
            rejects { Gen3GameOptionsEditing.replace(source, Gen3GameOptionsProfile.HOENN, native.copy(frame = bad)) }
        }
        assertArrayEquals(before, source)
    }

    @Test fun malformedLoadedValuesAndLengthsCannotBeSilentlyNormalized() {
        val invalid = listOf(section().also { it[0x13] = 3 }, section().also { it[0x14] = 7 },
            section().also { it[0x14] = 0xFA.toByte() }, section().copyOf(0xF2B),
            section().copyOf(0xF2D), ByteArray(0))
        for (source in invalid) for (profile in Gen3GameOptionsProfile.values()) {
            val before = source.copyOf()
            rejects { Gen3GameOptionsEditing.read(source, profile) }
            rejects { Gen3GameOptionsEditing.replace(source, profile, native) }
            assertArrayEquals(before, source)
        }
    }

    @Test fun noOpReturnsExactDetachedBytes() {
        for (profile in Gen3GameOptionsProfile.values()) {
            val source = section()
            val result = Gen3GameOptionsEditing.replace(source, profile, Gen3GameOptionsEditing.read(source, profile))
            assertArrayEquals(source, result)
            assertNotSame(source, result)
        }
    }

    @Test fun defaultsFollowNativeTitleRulesAndPreserveChosenHoennButtonMode() {
        for (profile in Gen3GameOptionsProfile.values()) for (button in 0..2) {
            val current = native.copy(buttonMode = button)
            val expectedButton = if (profile == Gen3GameOptionsProfile.HOENN) button else 0
            assertEquals(Gen3GameOptions(expectedButton, 1, 0, 0, 0, 0),
                Gen3GameOptionsEditing.defaultSelections(current, profile))
            assertEquals(native.copy(buttonMode = button), current)
        }
    }

    @Test fun applyingDefaultsPreservesEveryMapZoomAndReservedBitPattern() {
        for (profile in Gen3GameOptionsProfile.values()) for (high in 0..31) {
            val source = section().also { it[0x15] = ((high shl 3) or 7).toByte() }
            val before = source.copyOf()
            val loaded = Gen3GameOptionsEditing.read(source, profile)
            val defaults = Gen3GameOptionsEditing.defaultSelections(loaded, profile)
            val result = Gen3GameOptionsEditing.replace(source, profile, defaults)
            val expected = before.copyOf().also {
                it[0x13] = (if (profile == Gen3GameOptionsProfile.HOENN) 2 else 0).toByte()
                it[0x14] = 1
                it[0x15] = (high shl 3).toByte()
            }
            assertArrayEquals(expected, result)
            assertArrayEquals(before, source)
            assertEquals(defaults, Gen3GameOptionsEditing.read(result, profile))
            assertEquals(defaults, Gen3GameOptionsEditing.defaultSelections(defaults, profile))
        }
    }

    @Test fun defaultsCannotNormalizeAnInvalidCurrentSelection() {
        for (profile in Gen3GameOptionsProfile.values()) for (invalid in listOf(
            native.copy(buttonMode = 3), native.copy(textSpeed = 7),
            native.copy(frame = profile.frameCount), native.copy(sound = 2),
            native.copy(battleStyle = -1), native.copy(battleSceneOff = 2))) {
            rejects { Gen3GameOptionsEditing.defaultSelections(invalid, profile) }
        }
    }
}
