package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspSaveValidationTest {
    private val layouts = listOf(Triple(0xE9828, 0x25, "1.0"), Triple(0xEDC20, 0x2C, "1.1"),
        Triple(0xEED8C, 0x32, "1.2"), Triple(0xEF0A4, 0x34, "1.3"))
    private fun uint(bytes: ByteArray, offset: Int, value: Long) { repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() } }
    private fun sign(bytes: ByteArray): ByteArray {
        val copy = bytes.copyOf(); copy.fill(0, 0xE9818, 0xE9828)
        val digest = MessageDigest.getInstance("MD5").digest(copy)
        digest.copyInto(copy, 0xE9818)
        return copy
    }
    private fun fixture(size: Int = layouts[3].first, revision: Int = layouts[3].second, game: Int = 0): ByteArray {
        // Nonzero whole-file pattern makes omissions of post-hash expansion bytes observable.
        val bytes = ByteArray(size) { ((it * 17 + 23) and 255).toByte() }
        uint(bytes, 0, revision.toLong()); bytes[0x79BDF] = game.toByte()
        return sign(bytes)
    }
    private fun session(bytes: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.SCARLET_VIOLET): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, bytes))
    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun bothTitlesAndAllFourExactLayoutPairsValidateWithoutSourceMutation() {
        val fixed = listOf("9162902749d796b1895c7c184371caad", "3b947ea1bf41b3c32e1a533123001a29", "4ae5ce5c2508047dc4d5bd099cc559e9", "d4acfbca0d332d51bcdd7cc955f75082")
        for ((i, layout) in layouts.withIndex()) for (game in 0..1) {
            val source = fixture(layout.first, layout.second, game); val before = source.copyOf()
            val result = BdspSaveValidation.inspect(source) as BdspValidation.Verified
            assertEquals(game, result.version.romCode); assertEquals(layout.third, result.revision.displayName)
            assertEquals(layout.first, result.revision.fileSize); assertEquals(layout.second.toLong(), result.revision.code)
            assertArrayEquals(before, source)
            if (game == 0) assertArrayEquals(hex(fixed[i]), source.copyOfRange(0xE9818, 0xE9828))
        }
    }

    @Test fun identityOverridesEveryFamilyHintButMalformedPartyCountNeverGrantsContentOrWrites() {
        // The patterned identity fixture has a native party count of 63, outside 0..6.
        val source = fixture(game = 1)
        for (hint in SwitchGameFamily.values()) {
            val bound = session(source, hint)
            assertEquals(hint, bound.snapshot.family); assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family)
            assertEquals("Pokémon Shining Pearl", bound.displayName); assertTrue(bound.isReadOnly); assertTrue(bound.isFormatVerified)
            assertTrue(bound.validation is LetsGoValidation.Unrecognized); assertTrue(bound.swordShieldValidation is SwordShieldValidation.Unrecognized)
            assertNull(bound.letsGoStorage); assertNull(bound.swordShieldStorage); assertNull(bound.swordShieldTrainer)
            assertNull(bound.bdspStorage); assertNotNull(bound.storageUnavailableReason)
            for (capability in SwitchReadCapability.values()) assertEquals(capability in setOf(SwitchReadCapability.SAVE_IDENTITY, SwitchReadCapability.INVENTORY), bound.supports(capability))
            assertFalse(bound.profile.writesEnabled)
        }
    }

    @Test fun checksumIncludesTheZeroedMiddleFieldAndEveryPostHashExpansionByte() {
        for (layout in layouts) {
            val source = fixture(layout.first, layout.second)
            val offsets = mutableSetOf(0, 3, 4, 0x79BDF, 0xB86B0, 0xE9817, 0xE9818, 0xE9827, source.lastIndex)
            if (source.size > 0xE9828) offsets += 0xE9828
            for (offset in offsets) {
                val damaged = source.copyOf(); damaged[offset] = (damaged[offset].toInt() xor 1).toByte()
                val before = damaged.copyOf()
                assertTrue(BdspSaveValidation.inspect(damaged) is BdspValidation.Invalid)
                assertArrayEquals(before, damaged)
            }
            val wrongDigest = source.copyOf().also { it.fill(0, 0xE9818, 0xE9828) }
            // Hashing without zeroing the native field is not the native checksum rule.
            MessageDigest.getInstance("MD5").digest(source).copyInto(wrongDigest, 0xE9818)
            assertTrue(BdspSaveValidation.inspect(wrongDigest) is BdspValidation.Invalid)
        }
    }

    @Test fun allMismatchedRevisionLengthPairsAndUnknownRevisionBitsRejectEvenWithValidHash() {
        for (size in layouts.map { it.first }) for (revision in layouts.map { it.second }) {
            if (layouts.any { it.first == size && it.second == revision }) continue
            assertTrue(BdspSaveValidation.inspect(fixture(size, revision)) is BdspValidation.Invalid)
        }
        for (revision in listOf(0L, 1L, 0x35L, 0x1000034L, 0x80000034L, 0xFFFFFFFFL)) {
            val source = fixture(); uint(source, 0, revision)
            assertTrue(BdspSaveValidation.inspect(sign(source)) is BdspValidation.Invalid)
        }
    }

    @Test fun unsupportedTitleCodesRejectAndUnknownLengthsNeverClaimBdspIdentity() {
        for (game in listOf(2, 44, 45, 48, 49, 255)) assertTrue(BdspSaveValidation.inspect(fixture(game = game)) is BdspValidation.Invalid)
        val lengths = listOf(0, 1, 3, 4, 16) + layouts.flatMap { listOf(it.first - 1, it.first + 1) }
        for (length in lengths) {
            val source = ByteArray(length)
            if (length >= 4) uint(source, 0, 0x34)
            assertTrue(BdspSaveValidation.inspect(source) is BdspValidation.Unrecognized)
        }
        assertFalse(session(byteArrayOf(0x34, 0, 0, 0)).isFormatVerified)
    }

    @Test fun validBdspPayloadResemblingLetsGoFooterDoesNotMisrouteAndCopiesRemainDetached() {
        val raw = fixture()
        // Incidental bytes at an unrelated game's marker are not format evidence.
        raw[0xB86B0] = 19; raw[0xB86B1] = 0
        val bytes = sign(raw); val bound = session(bytes, SwitchGameFamily.LETS_GO_PIKACHU_EEVEE)
        assertTrue(bound.bdspValidation is BdspValidation.Verified); assertTrue(bound.validation is LetsGoValidation.Unrecognized)
        assertEquals("Pokémon Brilliant Diamond", bound.displayName)
        bytes.fill(0); bound.snapshot.bytesCopy().fill(0)
        assertEquals("Pokémon Brilliant Diamond", bound.displayName); assertTrue(bound.supports(SwitchReadCapability.SAVE_IDENTITY))
    }
}
