package com.example.pokemonsavemanager

import org.junit.Assert.*
import org.junit.Test

class Gen3RegisteredItemEditingTest {
    private fun section(offset: Int, id: Int): ByteArray =
        ByteArray(0xF80) { (it * 43 + 17).toByte() }.also {
            it[offset] = id.toByte(); it[offset + 1] = (id ushr 8).toByte()
        }

    @Test fun pinnedNativeOffsetsAndLittleEndianWireValues() {
        assertEquals(1, Gen3RegisteredItemEditing.SECTION_ID)
        assertEquals(0x496, Gen3RegisteredItemProfile.HOENN.sectionOffset)
        assertEquals(0x296, Gen3RegisteredItemProfile.FIRERED_LEAFGREEN.sectionOffset)
        for (profile in Gen3RegisteredItemProfile.values()) {
            val raw = ByteArray(0xF80).also {
                it[profile.sectionOffset] = 0x34; it[profile.sectionOffset + 1] = 0x12
            }
            assertEquals(0x1234, Gen3RegisteredItemEditing.read(raw, profile))
        }
    }

    @Test fun clearOnlyChangesTheTwoRegisteredItemBytesAndKeepsInputDetached() {
        for (profile in Gen3RegisteredItemProfile.values()) for (id in listOf(1, 255, 256, 0x1234, 65535)) {
            val raw = section(profile.sectionOffset, id)
            val before = raw.copyOf()
            val expected = before.copyOf().also {
                it[profile.sectionOffset] = 0; it[profile.sectionOffset + 1] = 0
            }
            val result = Gen3RegisteredItemEditing.clear(raw, profile)
            assertArrayEquals(expected, result)
            assertArrayEquals(before, raw)
            assertEquals(0, Gen3RegisteredItemEditing.read(result, profile))
            result[0] = (result[0].toInt() xor 1).toByte()
            assertArrayEquals(before, raw)
        }
    }

    @Test fun readingRetainsEveryNativeUnsignedItemIdWithoutInventingMeaning() {
        for (profile in Gen3RegisteredItemProfile.values()) {
            val raw = ByteArray(0xF80)
            for (id in 0..65535) {
                raw[profile.sectionOffset] = id.toByte()
                raw[profile.sectionOffset + 1] = (id ushr 8).toByte()
                assertEquals(id, Gen3RegisteredItemEditing.read(raw, profile))
            }
        }
    }

    @Test fun neighbouringMoneyCoinsAndBagDataAndOtherTitleOffsetStayIdentical() {
        for (profile in Gen3RegisteredItemProfile.values()) {
            val raw = section(profile.sectionOffset, 0xBEEF)
            val result = Gen3RegisteredItemEditing.clear(raw, profile)
            for (i in raw.indices) if (i !in profile.sectionOffset..profile.sectionOffset + 1) {
                assertEquals("Unexpected changed byte at " + i, raw[i], result[i])
            }
            val other = if (profile == Gen3RegisteredItemProfile.HOENN) 0x296 else 0x496
            assertEquals(raw[other], result[other]); assertEquals(raw[other + 1], result[other + 1])
        }
    }

    @Test fun noRegisteredItemAndRepeatedClearAreDetachedNoOps() {
        for (profile in Gen3RegisteredItemProfile.values()) {
            val raw = section(profile.sectionOffset, 0)
            val first = Gen3RegisteredItemEditing.clear(raw, profile)
            val second = Gen3RegisteredItemEditing.clear(first, profile)
            assertArrayEquals(raw, first); assertArrayEquals(raw, second)
            assertNotSame(raw, first); assertNotSame(first, second)
        }
    }

    @Test fun incompleteOrOversizedSectionsRejectWithoutMutation() {
        for (profile in Gen3RegisteredItemProfile.values()) for (size in listOf(0, 0xF7F, 0xF81)) {
            val raw = ByteArray(size) { 0xA5.toByte() }
            val before = raw.copyOf()
            var readRejected = false; var clearRejected = false
            try { Gen3RegisteredItemEditing.read(raw, profile) } catch (_: IllegalArgumentException) { readRejected = true }
            try { Gen3RegisteredItemEditing.clear(raw, profile) } catch (_: IllegalArgumentException) { clearRejected = true }
            assertTrue(readRejected && clearRejected); assertArrayEquals(before, raw)
        }
    }

    @Test fun commonShortcutNamesKeepTheirExactNativeIds() {
        for (profile in Gen3RegisteredItemProfile.values()) {
            assertEquals("No registered item", Gen3RegisteredItemEditing.itemLabel(0, profile))
            for ((id, name) in mapOf(261 to "Itemfinder", 262 to "Old Rod", 263 to "Good Rod", 264 to "Super Rod")) {
                assertEquals(name + " (#" + id + ")", Gen3RegisteredItemEditing.itemLabel(id, profile))
            }
        }
        for ((id, name) in mapOf(259 to "Mach Bike", 268 to "Wailmer Pail", 272 to "Acro Bike")) {
            assertEquals(name + " (#" + id + ")",
                Gen3RegisteredItemEditing.itemLabel(id, Gen3RegisteredItemProfile.HOENN))
        }
        assertEquals("Bicycle (#360)",
            Gen3RegisteredItemEditing.itemLabel(360, Gen3RegisteredItemProfile.FIRERED_LEAFGREEN))
    }

    @Test fun titleSpecificNamesNeverLeakIntoTheOtherTitleFamily() {
        for (id in listOf(259, 268, 272)) {
            assertEquals("Native item #" + id,
                Gen3RegisteredItemEditing.itemLabel(id, Gen3RegisteredItemProfile.FIRERED_LEAFGREEN))
        }
        assertEquals("Native item #360",
            Gen3RegisteredItemEditing.itemLabel(360, Gen3RegisteredItemProfile.HOENN))
    }

    @Test fun uncataloguedValuesStayNumericAndDoNotChangeTheStoredValue() {
        for (profile in Gen3RegisteredItemProfile.values()) for (id in listOf(1, 258, 265, 269, 292, 376, 65535)) {
            val raw = section(profile.sectionOffset, id)
            val before = raw.copyOf()
            assertEquals("Native item #" + id, Gen3RegisteredItemEditing.itemLabel(
                Gen3RegisteredItemEditing.read(raw, profile), profile))
            assertArrayEquals(before, raw)
            assertEquals(id, Gen3RegisteredItemEditing.read(raw, profile))
        }
    }

    @Test fun labelLookupRejectsIdsThatCannotExistInTheNativeWord() {
        for (profile in Gen3RegisteredItemProfile.values()) for (id in listOf(-1, 65536, Int.MIN_VALUE, Int.MAX_VALUE)) {
            var rejected = false
            try { Gen3RegisteredItemEditing.itemLabel(id, profile) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
    }
}
