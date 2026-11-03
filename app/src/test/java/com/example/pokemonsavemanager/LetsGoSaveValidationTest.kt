package com.example.pokemonsavemanager

import org.junit.Assert.*
import org.junit.Test

class LetsGoSaveValidationTest {
    // Independent synthetic wire fixture, with table-driven CRC-16/ARC.
    private val regions = listOf(
        0x00000 to 0x00D90, 0x00E00 to 0x00200, 0x01000 to 0x00168,
        0x01200 to 0x01800, 0x02A00 to 0x020E8, 0x04C00 to 0x00930,
        0x05600 to 0x00004, 0x05800 to 0x00130, 0x05A00 to 0x00012,
        0x05C00 to 0x3F7A0, 0x45400 to 0x00008, 0x45600 to 0x00E90,
        0x46600 to 0x010A4, 0x47800 to 0x000F0, 0x47A00 to 0x06010,
        0x4DC00 to 0x00200, 0x4DE00 to 0x00098, 0x4E000 to 0x00068,
        0x4E200 to 0x69780, 0xB7A00 to 0x000B0, 0xB7C00 to 0x00940
    )
    private val crcTable = IntArray(256) { value ->
        var remainder = value
        for (bit in 0..7) remainder = if ((remainder and 1) == 1)
            (remainder ushr 1) xor 0xA001 else remainder ushr 1
        remainder
    }
    private fun referenceCrc(bytes: ByteArray, offset: Int, length: Int): Int {
        var result = 0
        for (i in offset until offset + length)
            result = (result ushr 8) xor crcTable[(result xor bytes[i].toInt()) and 255]
        return result
    }
    private fun put16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }
    private fun put32(bytes: ByteArray, offset: Int, value: Int) {
        put16(bytes, offset, value); put16(bytes, offset + 2, value ushr 16)
    }
    private fun fixture(game: Int, seed: Int = 7): ByteArray {
        val raw = ByteArray(0x100000) { (it * 17 + seed).toByte() }
        raw[0x1004] = game.toByte()
        byteArrayOf(0x46, 0x45, 0x45, 0x42).copyInto(raw, 0xB8610)
        regions.forEachIndexed { id, (offset, length) ->
            val entry = 0xB8614 + id * 8
            put32(raw, entry, length); put16(raw, entry + 4, id)
            put16(raw, entry + 6, referenceCrc(raw, offset, length))
        }
        return raw
    }

    @Test fun bothTitlesRequireAllNativeBlocksAndPreserveTheCompleteInput() {
        for (version in LetsGoVersion.values()) for (seed in listOf(0, 128, 255)) {
            val raw = fixture(version.gameId, seed)
            val before = raw.copyOf()
            val result = LetsGoSaveValidation.inspect(raw) as LetsGoValidation.Verified
            assertEquals(version, result.version)
            assertEquals(21, result.checkedBlocks)
            assertArrayEquals(before, raw)
        }
    }

    @Test fun damageAtBothEndsOfEachChecksummedRegionIsRejected() {
        val raw = fixture(42)
        for ((offset, length) in regions) for (index in listOf(offset, offset + length - 1)) {
            val damaged = raw.copyOf()
            damaged[index] = (damaged[index].toInt() xor 1).toByte()
            val before = damaged.copyOf()
            assertTrue(LetsGoSaveValidation.inspect(damaged) is LetsGoValidation.Invalid)
            assertArrayEquals(before, damaged)
        }
    }

    @Test fun everyDescriptorIdLengthAndChecksumIsCheckedIncludingHighLengthBits() {
        val raw = fixture(43)
        for (id in regions.indices) for (field in listOf(0, 3, 4, 5, 6, 7)) {
            val damaged = raw.copyOf()
            val index = 0xB8614 + id * 8 + field
            damaged[index] = (damaged[index].toInt() xor 0x80).toByte()
            assertTrue("Descriptor $id field $field", LetsGoSaveValidation.inspect(damaged) is LetsGoValidation.Invalid)
        }
    }

    @Test fun unrelatedGameIdRejectsEvenWhenItsNativeCrcHasBeenUpdated() {
        for (game in listOf(0, 30, 44, 255)) {
            val raw = fixture(game)
            assertTrue(LetsGoSaveValidation.inspect(raw) is LetsGoValidation.Invalid)
        }
    }

    @Test fun completeSizeFooterAndDescriptorSignatureAreRequiredWithoutUnsafeOffsets() {
        val valid = fixture(42)
        for (length in listOf(0, 1, 0x1004, 0xB8610))
            assertTrue(LetsGoSaveValidation.inspect(valid.copyOf(length)) is LetsGoValidation.Unrecognized)
        for (length in listOf(0xB8800, 0xFFFFF, 0x100001))
            assertTrue(LetsGoSaveValidation.inspect(valid.copyOf(length)) is LetsGoValidation.Invalid)
        val badMagic = valid.copyOf().also { it[0xB8610] = 0 }
        assertTrue(LetsGoSaveValidation.inspect(badMagic) is LetsGoValidation.Invalid)
        assertTrue(LetsGoSaveValidation.inspect(ByteArray(0x100000)) is LetsGoValidation.Unrecognized)
    }

    @Test fun fixedCrcVectorUnsignedBytesSlicesAndBoundsMatchIndependentReference() {
        val vector = "123456789".toByteArray(Charsets.US_ASCII)
        assertEquals(0xBB3D, LetsGoSaveValidation.crc16(vector, 0, vector.size))
        val raw = ByteArray(1024) { it.toByte() }
        for (offset in listOf(0, 1, 127, 256)) for (length in listOf(0, 1, 255, 512))
            assertEquals(referenceCrc(raw, offset, length), LetsGoSaveValidation.crc16(raw, offset, length))
        for ((offset, length) in listOf(-1 to 1, 0 to -1, 1024 to 1, 1 to Int.MAX_VALUE))
            assertThrows(IllegalArgumentException::class.java) { LetsGoSaveValidation.crc16(raw, offset, length) }
    }

    @Test fun nativeIdentityOverridesEverySelectedFamilyHintWithoutEnablingOtherReadersOrWrites() {
        for (family in SwitchGameFamily.values()) {
            val session = SwitchReadSession(SwitchReadSnapshot.capture(family, fixture(43)))
            assertEquals(family, session.snapshot.family)
            assertEquals(SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, session.profile.family)
            assertEquals("Let's Go Eevee", session.displayName)
            assertTrue(session.isReadOnly)
            assertFalse(session.profile.writesEnabled)
            assertTrue(session.isFormatVerified)
            for (capability in SwitchReadCapability.values())
                assertEquals(capability in setOf(SwitchReadCapability.SAVE_IDENTITY, SwitchReadCapability.INVENTORY), session.supports(capability))
        }
    }

    @Test fun damagedAndUnknownSessionsNeverGainVerifiedCapabilitiesAndCopiesCannotAlterIdentity() {
        val valid = fixture(42)
        val session = SwitchReadSession(SwitchReadSnapshot.capture(SwitchGameFamily.SWORD_SHIELD, valid))
        valid[0x1004] = 44
        session.snapshot.bytesCopy().fill(0)
        assertEquals("Let's Go Pikachu", session.displayName)
        assertTrue(session.isFormatVerified)
        for (bytes in listOf(valid, ByteArray(0x100000), byteArrayOf(42))) {
            val unavailable = SwitchReadSession(SwitchReadSnapshot.capture(SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, bytes))
            assertFalse(unavailable.isFormatVerified)
            assertFalse(unavailable.supports(SwitchReadCapability.SAVE_IDENTITY))
        }
    }
}
