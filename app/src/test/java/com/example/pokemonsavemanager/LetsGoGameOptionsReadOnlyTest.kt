package com.example.pokemonsavemanager

import org.junit.Assert.*
import org.junit.Test

class LetsGoGameOptionsReadOnlyTest {
    private val regions = listOf(0x00000 to 0x00D90, 0x00E00 to 0x00200, 0x01000 to 0x00168,
        0x01200 to 0x01800, 0x02A00 to 0x020E8, 0x04C00 to 0x00930, 0x05600 to 0x00004,
        0x05800 to 0x00130, 0x05A00 to 0x00012, 0x05C00 to 0x3F7A0, 0x45400 to 0x00008,
        0x45600 to 0x00E90, 0x46600 to 0x010A4, 0x47800 to 0x000F0, 0x47A00 to 0x06010,
        0x4DC00 to 0x00200, 0x4DE00 to 0x00098, 0x4E000 to 0x00068, 0x4E200 to 0x69780,
        0xB7A00 to 0x000B0, 0xB7C00 to 0x00940)
    private val base = 0x45400
    private fun put16(b: ByteArray, o: Int, v: Int) { b[o] = v.toByte(); b[o + 1] = (v ushr 8).toByte() }
    private fun put32(b: ByteArray, o: Int, v: Long) { repeat(4) { b[o + it] = (v ushr (8 * it)).toByte() } }
    private fun crc(b: ByteArray, o: Int, count: Int): Int {
        var c = 0
        for (i in o until o + count) { c = c xor (b[i].toInt() and 255); repeat(8) { c = if (c and 1 == 0) c ushr 1 else (c ushr 1) xor 0xA001 } }
        return c
    }
    private fun seal(b: ByteArray) {
        put32(b, 0xB8610, 0x42454546L)
        regions.forEachIndexed { id, p ->
            val d = 0xB8614 + id * 8; put32(b, d, p.second.toLong()); put16(b, d + 4, id); put16(b, d + 6, crc(b, p.first, p.second))
        }
    }
    private fun fixture(game: Int = 42, hours: Int = 12345, minutes: Int = 59, seconds: Int = 58, last: Long = 0xFEDCBA98L): ByteArray =
        ByteArray(0x100000).also { b ->
            b[0x1004] = game.toByte(); b[0x1035] = 2; repeat(7) { put16(b, 0x5A00 + it * 2, 1001) }
            put32(b, 0x2A00, 0x2F120F17L) // Native Pokédex marker required by the earlier reader.
            put16(b, 0x5A0E, 0); put16(b, base, hours); b[base + 2] = minutes.toByte(); b[base + 3] = seconds.toByte(); put32(b, base + 4, last)
            b[base - 1] = 0xA5.toByte(); b[base + 8] = 0x5A; seal(b)
        }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    private val config = 0x5600
    private fun withConfig(word: Long, game: Int = 42): ByteArray = fixture(game).also { put32(it, config, word); seal(it) }

    @Test fun bothTitlesPreserveFourByteConfigurationAndSignedUnsignedViews() {
        for (game in listOf(42, 43)) for (word in listOf(0L, 0x2EL, 0x80000000L, 0xA5C3E17BL, 0xFFFFFFFFL)) {
            val b = withConfig(word, game); b[config - 1] = 0xA5.toByte(); b[config + 4] = 0x5A
            val before = b.copyOf(); val read = LetsGoGameOptionsReadOnly.read(b)
            assertEquals(game, read.version.gameId); assertEquals(word, read.rawWord); assertEquals(word.toInt(), read.signedConfigValue)
            assertEquals(word ushr 8, read.unknownUpperBits); assertEquals(before.copyOfRange(config, config + 4).map { it.toInt() and 255 }, read.rawBytes)
            assertArrayEquals(before, b); assertEquals(165, b[config - 1].toInt() and 255); assertEquals(90, b[config + 4].toInt() and 255)
        }
    }

    @Test fun everyLowByteCombinesTextSpeedAnimationStyleAndSkippedLanguageIndex() {
        val seed = fixture(); val languageIds = listOf(0, 1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
        val languageNames = mapOf(1 to "Japanese", 2 to "English", 3 to "French", 4 to "Italian", 5 to "German", 7 to "Spanish",
            8 to "Korean", 9 to "Chinese (Simplified)", 10 to "Chinese (Traditional)")
        for (low in 0..255) {
            val b = seed.copyOf(); put32(b, config, 0xA5C3E100L or low.toLong())
            put16(b, 0xB8614 + 6 * 8 + 6, crc(b, config, 4)); val read = LetsGoGameOptionsReadOnly.read(b)
            assertEquals(low % 4, read.textSpeedId); assertEquals((low / 4) % 2, read.battleAnimationId)
            assertEquals((low / 4) % 2 == 0, read.animationsEnabled); assertEquals((low / 8) % 2, read.battleStyleId)
            assertEquals(low / 16, read.rawLanguageIndex); assertEquals(languageIds[low / 16], read.languageId)
            assertEquals(languageNames[languageIds[low / 16]], read.languageName); assertEquals(0xA5C3E1L, read.unknownUpperBits)
        }
    }

    @Test fun everyUnknownHighBitAndUnsignedBoundaryStaysExactWithoutAffectingLowFields() {
        for (word in listOf(0L, 1L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL) + (0 until 32).map { 1L shl it }) {
            val b = withConfig(word); val before = b.copyOf(); val read = LetsGoGameOptionsReadOnly.read(b)
            assertEquals(word, read.rawWord); assertEquals((0..3).map { ((word ushr (8 * it)) and 255).toInt() }, read.rawBytes)
            assertEquals(word ushr 8, read.unknownUpperBits); assertArrayEquals(before, b)
        }
        for (bit in 8 until 32) {
            val read = LetsGoGameOptionsReadOnly.read(withConfig((1L shl bit) or 0x2EL))
            assertEquals(2, read.textSpeedId); assertEquals(1, read.battleAnimationId); assertFalse(read.animationsEnabled)
            assertEquals(1, read.battleStyleId); assertEquals(2, read.languageId); assertEquals("English", read.languageName)
        }
    }

    @Test fun allNativeBlocksAndDescriptorsPrecedeOptionsAndEmptyInputFailsAtIntake() {
        val valid = withConfig(0xFFFFFFFFL)
        val offsets = regions.map { it.first } + (config until config + 4).toList() + listOf(0xB8614, 0xB8618, 0xB861A, 0xB8610)
        for (o in offsets) {
            val b = valid.copyOf(); b[o] = (b[o].toInt() xor 1).toByte(); val before = b.copyOf()
            assertThrows(IllegalArgumentException::class.java) { LetsGoGameOptionsReadOnly.read(b) }; assertNull(session(b).letsGoGameOptions); assertArrayEquals(before, b)
        }
        val title = valid.copyOf().also { it[0x1004] = 44; seal(it) }
        for (b in listOf(title, valid.copyOf(valid.size - 1), valid.copyOf(valid.size + 1), ByteArray(0), ByteArray(1))) {
            assertThrows(IllegalArgumentException::class.java) { LetsGoGameOptionsReadOnly.read(b) }
            if (b.isEmpty()) assertThrows(IllegalArgumentException::class.java) { session(b) } else assertNull(session(b).letsGoGameOptions)
        }
    }

    @Test fun configurationLanguageRemainsIndependentOfStrictTrainerMetadata() {
        for ((o, v) in listOf(0x1005 to 2, 0x1035 to 6, 0x1038 to 0xD800)) {
            val b = withConfig(0x6EL); if (o == 0x1038) put16(b, o, v) else b[o] = v.toByte(); seal(b)
            val before = b.copyOf(); val s = session(b)
            assertTrue(s.isFormatVerified); assertNull(s.letsGoTrainer); assertFalse(s.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertEquals(6, s.letsGoGameOptions!!.rawLanguageIndex); assertEquals(7, s.letsGoGameOptions!!.languageId)
            assertEquals("Spanish", s.letsGoGameOptions!!.languageName); assertNull(s.letsGoGameOptionsUnavailableReason)
            assertTrue(s.supports(SwitchReadCapability.PARTY)); assertNotNull(s.letsGoPlayTime); assertNotNull(s.letsGoLastSaved)
            assertArrayEquals(before, b); assertFalse(s.profile.writesEnabled)
        }
    }

    @Test fun immutableDetachedConfigurationKeepsNativeIdentityEarlierReadersAndWriteLocks() {
        for (hint in SwitchGameFamily.values()) {
            val b = withConfig(0xFFFFFFFFL, 43); val before = b.copyOf(); val s = session(b, hint); val read = s.letsGoGameOptions!!
            assertEquals(SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, s.profile.family); assertEquals(43, read.version.gameId)
            b.fill(0); s.snapshot.bytesCopy().fill(0); assertArrayEquals(before, s.snapshot.bytesCopy()); assertEquals(0xFFFFFFFFL, read.rawWord)
            assertEquals(16, read.languageId); assertNull(read.languageName)
            assertThrows(UnsupportedOperationException::class.java) { (read.rawBytes as MutableList<*>).clear() }
            assertNotNull(s.letsGoTrainer); assertNotNull(s.letsGoStorage); assertNotNull(s.letsGoInventory); assertNotNull(s.letsGoPokedex)
            assertNotNull(s.letsGoPlayTime); assertNotNull(s.letsGoLastSaved); assertTrue(s.isReadOnly); assertFalse(s.profile.writesEnabled)
        }
    }
}
