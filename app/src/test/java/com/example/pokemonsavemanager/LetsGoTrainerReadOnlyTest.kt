package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class LetsGoTrainerReadOnlyTest {
    private val regions = listOf(0x00000 to 0x00D90, 0x00E00 to 0x00200, 0x01000 to 0x00168,
        0x01200 to 0x01800, 0x02A00 to 0x020E8, 0x04C00 to 0x00930, 0x05600 to 0x00004,
        0x05800 to 0x00130, 0x05A00 to 0x00012, 0x05C00 to 0x3F7A0, 0x45400 to 0x00008,
        0x45600 to 0x00E90, 0x46600 to 0x010A4, 0x47800 to 0x000F0, 0x47A00 to 0x06010,
        0x4DC00 to 0x00200, 0x4DE00 to 0x00098, 0x4E000 to 0x00068, 0x4E200 to 0x69780,
        0xB7A00 to 0x000B0, 0xB7C00 to 0x00940)
    private fun put16(b: ByteArray, i: Int, n: Int) { b[i] = n.toByte(); b[i + 1] = (n ushr 8).toByte() }
    private fun put32(b: ByteArray, i: Int, n: Long) { put16(b, i, n.toInt()); put16(b, i + 2, (n ushr 16).toInt()) }
    private fun word(b: ByteArray, i: Int): Int = (b[i].toInt() and 255) or ((b[i + 1].toInt() and 255) shl 8)
    private fun crc(b: ByteArray, start: Int, len: Int): Int {
        var state = 0
        for (i in start until start + len) {
            state = state xor (b[i].toInt() and 255)
            repeat(8) { state = if (state and 1 == 0) state ushr 1 else (state ushr 1) xor 0xA001 }
        }
        return state
    }
    private fun seal(b: ByteArray) {
        put32(b, 0xB8610, 0x42454546L)
        for ((id, region) in regions.withIndex()) {
            val entry = 0xB8614 + id * 8
            put32(b, entry, region.second.toLong()); put16(b, entry + 4, id)
            put16(b, entry + 6, crc(b, region.first, region.second))
        }
    }
    private fun label(b: ByteArray, offset: Int, value: String) {
        b.fill(0, offset, offset + 26); value.toByteArray(Charsets.UTF_16LE).copyInto(b, offset)
    }
    private fun fixture(game: Int = 42, gender: Int = 1, language: Int = 2, id: Long = 0xFFFFFFFFL): ByteArray =
        ByteArray(0x100000).also { b ->
            put32(b, 0x1000, id); b[0x1004] = game.toByte(); b[0x1005] = gender.toByte(); b[0x1035] = language.toByte()
            label(b, 0x1038, "Zoë 雪♀"); label(b, 0x4E00, "ライバル")
            put32(b, 0x4C04, 0xFEDCBA98L); b[0x10B8] = 2; b[0x10B9] = 1
            repeat(7) { put16(b, 0x5A00 + it * 2, 1001) }
            put16(b, 0x5A0E, 0); seal(b)
        }
    private fun session(source: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.SWORD_SHIELD): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, source))
    private fun changed(offset: Int, value: Int): ByteArray = fixture().also { it[offset] = value.toByte(); seal(it) }

    @Test fun bothNativeTitlesExposeUnsignedUnicodeFieldsWithoutSourceAliases() {
        for (game in listOf(42, 43)) {
            val source = fixture(game); val before = source.copyOf(); val trainer = LetsGoTrainerReadOnly.read(source)
            if (game == 42) assertEquals("53999f770ac4fed1d85eaf3714a321fe11a8b6967e9047f72a02fac4b495b5ad", MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it.toInt() and 255) })
            assertEquals(game, trainer.version.gameId); assertEquals("Zoë 雪♀", trainer.name); assertEquals("ライバル", trainer.rivalName)
            assertEquals(1, trainer.gender); assertEquals(2, trainer.languageId); assertEquals("English", trainer.languageName)
            assertEquals(0xFFFFFFFFL, trainer.id32); assertEquals(65535, trainer.tid16); assertEquals(65535, trainer.sid16)
            assertEquals("967295", trainer.displayTrainerId); assertEquals(0xFEDCBA98L, trainer.money)
            assertEquals(2, trainer.starterChoiceId); assertEquals(1, trainer.starterGenderId)
            assertArrayEquals(before, source); source.fill(0); assertEquals("Zoë 雪♀", trainer.name)
        }
    }

    @Test fun allNativeLanguagesGenderAndIdBoundariesRemainReadable() {
        val languages = mapOf(1 to "Japanese", 2 to "English", 3 to "French", 4 to "Italian", 5 to "German",
            7 to "Spanish", 8 to "Korean", 9 to "Chinese (Simplified)", 10 to "Chinese (Traditional)")
        for ((language, name) in languages) for (gender in 0..1) {
            val trainer = LetsGoTrainerReadOnly.read(fixture(43, gender, language))
            assertEquals(language, trainer.languageId); assertEquals(name, trainer.languageName); assertEquals(gender, trainer.gender)
        }
        for ((id, display) in listOf(0L to "000000", 1L to "000001", 999999L to "999999", 1000000L to "000000", 0xFFFFFFFFL to "967295"))
            assertEquals(display, LetsGoTrainerReadOnly.read(fixture(id = id)).displayTrainerId)
        for (name in listOf("", "ABCDEFGHIJKL", "雪".repeat(12), "A🚀B")) {
            val b = fixture(); label(b, 0x1038, name); label(b, 0x4E00, name); seal(b)
            val trainer = LetsGoTrainerReadOnly.read(b); assertEquals(name, trainer.name); assertEquals(name, trainer.rivalName)
        }
    }

    @Test fun malformedNamesNeverBecomeReplacementTextOrLeakedTrailingBytes() {
        for (offset in listOf(0x1038, 0x4E00)) {
            val bad = listOf(
                fixture().also { b -> repeat(13) { put16(b, offset + it * 2, 65) } },
                fixture().also { b -> b.fill(0, offset, offset + 26); put16(b, offset, 0xD800) },
                fixture().also { b -> b.fill(0, offset, offset + 26); put16(b, offset, 0xDC00) },
                fixture().also { b -> b.fill(0, offset, offset + 26); put16(b, offset, 10) },
                fixture().also { b -> b.fill(0, offset, offset + 26); put16(b, offset, 127) }
            )
            for (b in bad) { seal(b); assertTrue(LetsGoSaveValidation.inspect(b) is LetsGoValidation.Verified)
                assertThrows(IllegalArgumentException::class.java) { LetsGoTrainerReadOnly.read(b) }
                val bound = session(b); assertNull(bound.letsGoTrainer); assertNotNull(bound.trainerUnavailableReason)
                assertTrue(bound.supports(SwitchReadCapability.PARTY))
            }
            val trash = fixture(); label(trash, offset, "A"); put16(trash, offset + 4, 0xD800); seal(trash)
            val trainer = LetsGoTrainerReadOnly.read(trash)
            assertEquals("A", if (offset == 0x1038) trainer.name else trainer.rivalName)
        }
    }

    @Test fun invalidTrainerMetadataDisablesOnlyTrainerAccessWithAReason() {
        val bad = listOf(0x1005 to 2, 0x1005 to 255, 0x1035 to 0, 0x1035 to 6, 0x1035 to 11, 0x1035 to 255)
        for ((offset, value) in bad) {
            val b = changed(offset, value); val before = b.copyOf(); val bound = session(b)
            assertTrue(bound.isFormatVerified); assertNull(bound.letsGoTrainer); assertNotNull(bound.trainerUnavailableReason)
            assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertTrue(bound.supports(SwitchReadCapability.PARTY))
            assertTrue(bound.supports(SwitchReadCapability.PC_OR_BOXES)); assertNotNull(bound.letsGoStorage)
            assertFalse(bound.profile.writesEnabled); assertArrayEquals(before, b)
        }
    }

    @Test fun everyNativeBlockAndDescriptorMustValidateBeforeTrainerFieldsAreRead() {
        val good = fixture()
        for ((offset, _) in regions) {
            val damaged = good.copyOf(); damaged[offset] = (damaged[offset].toInt() xor 1).toByte()
            assertThrows(IllegalArgumentException::class.java) { LetsGoTrainerReadOnly.read(damaged) }
        }
        for (offset in listOf(0xB8610, 0xB8614, 0xB8618, 0xB861A, 0xB8614 + 20 * 8 + 6)) {
            val damaged = good.copyOf(); damaged[offset] = (damaged[offset].toInt() xor 1).toByte()
            assertThrows(IllegalArgumentException::class.java) { LetsGoTrainerReadOnly.read(damaged) }
        }
        for (source in listOf(ByteArray(1), ByteArray(0x100000), good.copyOf(0xFFFFF), fixture(game = 44)))
            assertThrows(IllegalArgumentException::class.java) { LetsGoTrainerReadOnly.read(source) }
    }

    @Test fun detectedTitleOverridesEveryHintAndSnapshotsCannotEnableWritesOrOtherReaders() {
        for (hint in SwitchGameFamily.values()) {
            val source = fixture(43); val bound = session(source, hint); source.fill(0); bound.snapshot.bytesCopy().fill(0)
            val trainer = bound.letsGoTrainer!!
            assertEquals("Let's Go Eevee", bound.displayName); assertEquals(SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, bound.profile.family)
            assertEquals("Zoë 雪♀", trainer.name); assertEquals(0xFFFFFFFFL, trainer.id32); assertTrue(bound.isReadOnly)
            assertNotNull(bound.letsGoStorage); assertNull(bound.trainerUnavailableReason); assertNull(bound.swordShieldTrainer)
            assertNull(bound.swordShieldStorage); assertNull(bound.bdspStorage); assertNull(bound.legendsArceusStorage)
            assertNull(bound.scarletVioletStorage); assertNull(bound.legendsZaStorage); assertFalse(bound.profile.writesEnabled)
            for (capability in SwitchReadCapability.values()) assertEquals(capability in setOf(SwitchReadCapability.SAVE_IDENTITY,
                SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS,
                SwitchReadCapability.TRAINER_PROFILE, SwitchReadCapability.INVENTORY), bound.supports(capability))
        }
    }

    @Test fun malformedStoragePointersDoNotBlockAnIndependentlyValidTrainerProfile() {
        val b = fixture(); put16(b, 0x5A0E, 1001); seal(b)
        val bound = session(b)
        assertTrue(bound.isFormatVerified); assertNull(bound.letsGoStorage); assertNotNull(bound.storageUnavailableReason)
        assertNotNull(bound.letsGoTrainer); assertNull(bound.trainerUnavailableReason)
        assertTrue(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertFalse(bound.supports(SwitchReadCapability.PARTY))
        assertEquals("Zoë 雪♀", bound.letsGoTrainer!!.name)
    }

    @Test fun rawPartnerAndMoneyMetadataAreNeverSilentlyNormalizedOrUsedAsPokemonRecords() {
        for (choice in listOf(0, 1, 2, 255)) for (gender in listOf(0, 1, 2, 255)) {
            val b = fixture(); b[0x10B8] = choice.toByte(); b[0x10B9] = gender.toByte(); put32(b, 0x4C04, 0xFFFFFFFFL); seal(b)
            val before = b.copyOf(); val trainer = LetsGoTrainerReadOnly.read(b)
            assertEquals(choice, trainer.starterChoiceId); assertEquals(gender, trainer.starterGenderId)
            assertEquals(0xFFFFFFFFL, trainer.money); assertArrayEquals(before, b)
        }
    }
}
