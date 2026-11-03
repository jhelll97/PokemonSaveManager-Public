package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspTrainerReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val status = 0x79BB4
    private val languageOffset = 0x79B78
    private val rivalOffset = 0x55F4
    private fun put16(b: ByteArray, offset: Int, value: Int) { b[offset] = value.toByte(); b[offset + 1] = (value ushr 8).toByte() }
    private fun put32(b: ByteArray, offset: Int, value: Long) { repeat(4) { b[offset + it] = (value ushr (8 * it)).toByte() } }
    private fun label(b: ByteArray, offset: Int, text: String) {
        b.fill(0, offset, offset + 26); text.toByteArray(Charsets.UTF_16LE).copyInto(b, offset)
    }
    private fun seal(b: ByteArray) {
        b.fill(0, 0xE9818, 0xE9828)
        MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818)
    }
    private fun fixture(layout: Int = 3, title: Int = 0, male: Int = 1, language: Long = 2L,
        id: Long = 0xFFFFFFFFL): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put32(b, 0, layouts[layout].second.toLong()); b[status + 0x2B] = title.toByte()
        label(b, status, "Zoë 雪♀"); label(b, rivalOffset, "ライバル")
        put32(b, status + 0x1C, id); put32(b, status + 0x20, 999999L)
        b[status + 0x24] = male.toByte(); put32(b, languageOffset, language)
        b[status + 0x29] = 8; b[status + 0x2C] = 1; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.SWORD_SHIELD): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))
    private fun changed(offset: Int, value: Long, width: Int = 1): ByteArray = fixture().also {
        if (width == 4) put32(it, offset, value) else it[offset] = value.toByte(); seal(it)
    }

    @Test fun bothTitlesAllRevisionsExposeNativeFieldsWithoutMutatingSource() {
        for (layout in layouts.indices) for (title in 0..1) {
            val source = fixture(layout, title); val before = source.copyOf(); val trainer = BdspTrainerReadOnly.read(source)
            if (layout == 0 && title == 0) assertEquals("cb23cea4d8786db8da6e63858d6392623bbe2bbc9e986302d5ad46d319019f22", MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it.toInt() and 255) })
            assertEquals(title, trainer.version.romCode); assertEquals(layouts[layout].second.toLong(), trainer.revision.code)
            assertEquals("Zoë 雪♀", trainer.name); assertEquals("ライバル", trainer.rivalName)
            assertEquals(0, trainer.gender); assertEquals(2, trainer.languageId); assertEquals("English", trainer.languageName)
            assertEquals(0xFFFFFFFFL, trainer.id32); assertEquals(65535, trainer.tid16); assertEquals(65535, trainer.sid16)
            assertEquals("967295", trainer.displayTrainerId); assertEquals(999999L, trainer.money)
            assertEquals(8, trainer.badgeCount); assertTrue(trainer.gameClear); assertArrayEquals(before, source)
            source.fill(0); assertEquals("Zoë 雪♀", trainer.name)
        }
    }

    @Test fun nativeMaleBooleanFullWidthLanguagesUnsignedIdsAndNameLimitsAreHonored() {
        val languages = mapOf(1 to "Japanese", 2 to "English", 3 to "French", 4 to "Italian", 5 to "German",
            7 to "Spanish", 8 to "Korean", 9 to "Chinese (Simplified)", 10 to "Chinese (Traditional)")
        for ((language, name) in languages) for (male in 0..1) {
            val trainer = BdspTrainerReadOnly.read(fixture(title = 1, male = male, language = language.toLong()))
            assertEquals(if (male == 1) 0 else 1, trainer.gender); assertEquals(language, trainer.languageId); assertEquals(name, trainer.languageName)
        }
        for ((id, display) in listOf(0L to "000000", 1L to "000001", 999999L to "999999", 1000000L to "000000", 0xFFFFFFFFL to "967295"))
            assertEquals(display, BdspTrainerReadOnly.read(fixture(id = id)).displayTrainerId)
        for (name in listOf("", "ABCDEFGHIJKL", "雪".repeat(12), "A🚀B")) {
            val source = fixture(); label(source, status, name); label(source, rivalOffset, name); seal(source)
            val trainer = BdspTrainerReadOnly.read(source); assertEquals(name, trainer.name); assertEquals(name, trainer.rivalName)
        }
    }

    @Test fun malformedNamesNeverBecomeReplacementTextAndPostNullBytesStayHidden() {
        for (offset in listOf(status, rivalOffset)) {
            val bad = listOf(
                fixture().also { b -> repeat(13) { put16(b, offset + it * 2, 65) } },
                fixture().also { b -> b.fill(0, offset, offset + 26); put16(b, offset, 0xD800) },
                fixture().also { b -> b.fill(0, offset, offset + 26); put16(b, offset, 0xDC00) },
                fixture().also { b -> b.fill(0, offset, offset + 26); put16(b, offset, 10) },
                fixture().also { b -> b.fill(0, offset, offset + 26); put16(b, offset, 127) }
            )
            for (source in bad) {
                seal(source); assertTrue(BdspSaveValidation.inspect(source) is BdspValidation.Verified)
                assertThrows(IllegalArgumentException::class.java) { BdspTrainerReadOnly.read(source) }
                val bound = session(source); assertNull(bound.bdspTrainer); assertNotNull(bound.trainerUnavailableReason)
                assertTrue(bound.supports(SwitchReadCapability.PARTY))
            }
            val trash = fixture(); label(trash, offset, "A"); put16(trash, offset + 4, 0xD800); seal(trash)
            val trainer = BdspTrainerReadOnly.read(trash)
            assertEquals("A", if (offset == status) trainer.name else trainer.rivalName)
        }
    }

    @Test fun malformedTrainerFieldsDisableOnlyTrainerCapabilityWithoutClamping() {
        val cases = listOf(
            Triple(status + 0x24, 2L, 1), Triple(status + 0x24, 255L, 1),
            Triple(status + 0x20, 1000000L, 4), Triple(status + 0x20, 0xFFFFFFFFL, 4),
            Triple(status + 0x29, 9L, 1), Triple(status + 0x29, 255L, 1),
            Triple(status + 0x2C, 2L, 1), Triple(status + 0x2C, 255L, 1),
            Triple(languageOffset, 0L, 4), Triple(languageOffset, 6L, 4), Triple(languageOffset, 11L, 4),
            Triple(languageOffset, 0x1000002L, 4), Triple(languageOffset, 0x80000002L, 4), Triple(languageOffset, 0xFFFFFFFFL, 4)
        )
        for ((offset, value, width) in cases) {
            val source = changed(offset, value, width); val before = source.copyOf(); val bound = session(source)
            assertTrue(bound.isFormatVerified); assertNull(bound.bdspTrainer); assertNotNull(bound.trainerUnavailableReason)
            assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertTrue(bound.supports(SwitchReadCapability.PARTY))
            assertTrue(bound.supports(SwitchReadCapability.PC_OR_BOXES)); assertFalse(bound.profile.writesEnabled)
            assertArrayEquals(before, source)
        }
    }

    @Test fun exactRevisionHashAndTitleGatesPrecedeEveryTrainerRead() {
        for (layout in layouts.indices) {
            val good = fixture(layout)
            for (offset in setOf(0, 4, status, 0xE9817, 0xE9818, 0xE9827, good.lastIndex)) {
                val damaged = good.copyOf(); damaged[offset] = (damaged[offset].toInt() xor 1).toByte()
                assertThrows(IllegalArgumentException::class.java) { BdspTrainerReadOnly.read(damaged) }
            }
            val revision = good.copyOf(); put32(revision, 0, 0x35); seal(revision)
            assertThrows(IllegalArgumentException::class.java) { BdspTrainerReadOnly.read(revision) }
            val title = good.copyOf(); title[status + 0x2B] = 2; seal(title)
            assertThrows(IllegalArgumentException::class.java) { BdspTrainerReadOnly.read(title) }
            assertThrows(IllegalArgumentException::class.java) { BdspTrainerReadOnly.read(good.copyOf(good.size - 1)) }
        }
        for (source in listOf(ByteArray(1), ByteArray(0xEF0A4)))
            assertThrows(IllegalArgumentException::class.java) { BdspTrainerReadOnly.read(source) }
    }

    @Test fun nativeTitleOverridesAllHintsAndDetachedSnapshotsNeverGrantOtherReadersOrWrites() {
        for (hint in SwitchGameFamily.values()) {
            val source = fixture(title = 1); val bound = session(source, hint); source.fill(0); bound.snapshot.bytesCopy().fill(0)
            val trainer = bound.bdspTrainer!!
            assertEquals("Pokémon Shining Pearl", bound.displayName); assertEquals("Zoë 雪♀", trainer.name)
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family)
            assertNotNull(bound.bdspStorage); assertNull(bound.trainerUnavailableReason); assertTrue(bound.isReadOnly)
            assertNull(bound.letsGoTrainer); assertNull(bound.swordShieldTrainer); assertNull(bound.letsGoStorage)
            assertNull(bound.swordShieldStorage); assertNull(bound.legendsArceusStorage); assertNull(bound.scarletVioletStorage); assertNull(bound.legendsZaStorage)
            // This native fixture has a valid, empty Pokédex; earlier source mutations cannot invalidate its detached snapshot.
            val pokedex = bound.bdspPokedex!!
            assertNull(bound.pokedexUnavailableReason); assertEquals(493, pokedex.entries.size)
            assertEquals(0, pokedex.heardCount); assertEquals(0, pokedex.seenCount); assertEquals(0, pokedex.caughtCount)
            assertNull(bound.letsGoPokedex); assertNull(bound.swordShieldPokedex)
            assertFalse(bound.profile.writesEnabled)
            for (capability in SwitchReadCapability.values()) assertEquals(capability in setOf(SwitchReadCapability.SAVE_IDENTITY,
                SwitchReadCapability.TRAINER_PROFILE, SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES,
                SwitchReadCapability.POKEMON_DETAILS, SwitchReadCapability.POKEDEX, SwitchReadCapability.INVENTORY), bound.supports(capability))
        }
    }

    @Test fun invalidNativePartyCountCannotHideAnIndependentlyValidTrainer() {
        val source = fixture(); source[0x14098 + 6 * 344] = 7; seal(source)
        val bound = session(source)
        assertTrue(bound.isFormatVerified); assertNull(bound.bdspStorage); assertNotNull(bound.storageUnavailableReason)
        assertNotNull(bound.bdspTrainer); assertNull(bound.trainerUnavailableReason)
        assertTrue(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertFalse(bound.supports(SwitchReadCapability.PARTY))
        assertEquals(8, bound.bdspTrainer!!.badgeCount)
    }

    @Test fun nativeMoneyBadgeAndClearBoundariesRemainExactSavedValues() {
        for (money in listOf(0L, 999999L)) for (badges in 0..8) for (clear in 0..1) {
            val source = fixture(); put32(source, status + 0x20, money); source[status + 0x29] = badges.toByte(); source[status + 0x2C] = clear.toByte(); seal(source)
            val before = source.copyOf(); val trainer = BdspTrainerReadOnly.read(source)
            assertEquals(money, trainer.money); assertEquals(badges, trainer.badgeCount); assertEquals(clear == 1, trainer.gameClear)
            assertArrayEquals(before, source)
        }
    }
}
