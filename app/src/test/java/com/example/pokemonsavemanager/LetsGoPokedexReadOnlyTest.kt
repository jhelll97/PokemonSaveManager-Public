package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class LetsGoPokedexReadOnlyTest {
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
    private val species = (1..151).toList() + listOf(808, 809)
    private fun flag(b: ByteArray, offset: Int, bit: Int) { val i = 0x2A00 + offset + bit / 8; b[i] = (b[i].toInt() or (1 shl (bit % 8))).toByte() }
    private fun fixture(game: Int = 42): ByteArray = ByteArray(0x100000).also { b ->
        b[0x1004] = game.toByte(); b[0x1005] = 1; b[0x1035] = 2
        repeat(7) { put16(b, 0x5A00 + it * 2, 1001) }
        put32(b, 0x2A00, 0x2F120F17L); put32(b, 0x2A04, 1L or (809L shl 9))
        for (group in 0..3) for (index in 0 until 186) {
            val o = 0x3978 + 6 * (index + group * 186); b[o] = 0xFE.toByte(); b[o + 2] = 0x7F
        }
        flag(b, 0x88, 0); flag(b, 0xF0, 0); flag(b, 0x550, 0)
        flag(b, 0x88, 807); flag(b, 0xF0 + 3 * 0x8C, 808); flag(b, 0xF0 + 7 * 0x8C, 808)
        flag(b, 0x550, 808 * 9 + 8); seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.SWORD_SHIELD): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesReadNativeSpeciesFlagsLanguagesAndDetachedCounts() {
        for (game in listOf(42, 43)) {
            val b = fixture(game); val before = b.copyOf(); val dex = LetsGoPokedexReadOnly.read(b)
            if (game == 42) assertEquals("d0dd4a571294ce961574d1f4439b0d8e6a9f2fe7e717e4d68a53ec5d685b686d", MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it.toInt() and 255) })
            assertEquals(game, dex.version.gameId); assertEquals(species, dex.entries.map { it.speciesId })
            assertEquals(2, dex.seenCount); assertEquals(2, dex.caughtCount); assertEquals(1, dex.kantoSeenCount); assertEquals(1, dex.kantoCaughtCount)
            assertTrue(dex.nationalDexFlag); assertEquals(809, dex.currentViewedSpecies)
            assertEquals(listOf("Japanese"), dex.entry(1).languages); assertEquals(listOf("Chinese (Traditional)"), dex.entry(809).languages)
            assertFalse(dex.entry(808).seen); assertTrue(dex.entry(808).caught)
            assertTrue(dex.entry(809).seen); assertFalse(dex.entry(809).caught); assertTrue(dex.entry(809).displayedVariants[3])
            assertArrayEquals(before, b); b.fill(0); assertTrue(dex.entry(1).caught)
        }
    }

    @Test fun allSupportedSpeciesAndEveryVariantLanguageCrossNativeByteBoundaries() {
        val b = fixture(); b.fill(0, 0x2A88, 0x2A00 + 0xF78)
        for ((index, id) in species.withIndex()) {
            flag(b, 0x88, id - 1); flag(b, 0xF0 + (index % 4) * 0x8C, id - 1)
            flag(b, 0xF0 + (4 + (3 - index % 4)) * 0x8C, id - 1); flag(b, 0x550, (id - 1) * 9 + index % 9)
        }
        seal(b); val dex = LetsGoPokedexReadOnly.read(b)
        assertEquals(153, dex.seenCount); assertEquals(153, dex.caughtCount)
        val languageNames = listOf("Japanese", "English", "French", "Italian", "German", "Spanish", "Korean", "Chinese (Simplified)", "Chinese (Traditional)")
        for ((i, id) in species.withIndex()) {
            val entry = dex.entry(id); assertEquals((0..3).map { it == i % 4 }, entry.seenVariants)
            assertEquals((0..3).map { it == 3 - i % 4 }, entry.displayedVariants); assertEquals(listOf(languageNames[i % 9]), entry.languages)
        }
    }

    @Test fun completeNativeSizeTableMapsBaseMeltanMelmetalAndThirtyThreeFormsWithoutAliasing() {
        val forms = listOf(3 to 1, 6 to 1, 6 to 2, 9 to 1, 15 to 1, 18 to 1, 19 to 1, 20 to 1, 26 to 1,
            27 to 1, 28 to 1, 37 to 1, 38 to 1, 50 to 1, 51 to 1, 52 to 1, 53 to 1, 65 to 1,
            74 to 1, 75 to 1, 76 to 1, 80 to 1, 88 to 1, 89 to 1, 94 to 1, 103 to 1, 105 to 1,
            115 to 1, 127 to 1, 130 to 1, 142 to 1, 150 to 1, 150 to 2)
        val b = fixture()
        for (group in 0..3) for (index in 0 until 186) {
            val o = 0x3978 + 6 * (index + group * 186); b[o] = index.toByte(); b[o + 1] = (group + 1).toByte()
            b[o + 2] = (255 - index).toByte(); b[o + 3] = 99; b[o + 4] = 88; b[o + 5] = 77
        }
        seal(b); val dex = LetsGoPokedexReadOnly.read(b)
        val mapping = species.map { it to 0 } + forms
        for ((index, pair) in mapping.withIndex()) for (group in 0..3) {
            val record = dex.entry(pair.first).forms.first { it.formId == pair.second }.sizeRecords[group]
            assertEquals(index, record.heightScalar); assertEquals(255 - index, record.weightScalar)
            assertEquals(group + 1, record.flagByte); assertEquals(group == 0, record.isFlagged); assertTrue(record.isRecorded)
        }
        assertEquals(186, dex.entries.sumOf { it.forms.size })
        assertEquals(listOf(0, 1, 2), dex.entry(6).forms.map { it.formId }); assertEquals(listOf(0), dex.entry(25).forms.map { it.formId })
    }

    @Test fun unsetSentinelRawFlagSemanticsAndContradictoryFlagsRemainUnchanged() {
        val b = fixture(); val o = 0x3978; b[o + 1] = 255.toByte()
        // Changing only one sentinel scalar makes the native record present.
        b[o + 186 * 6] = 0xFD.toByte(); b[o + 2 * 186 * 6 + 2] = 0x7E
        put32(b, 0x2A04, 0xFFFFFFFFL); seal(b); val before = b.copyOf(); val dex = LetsGoPokedexReadOnly.read(b)
        val records = dex.entry(1).forms[0].sizeRecords
        assertFalse(records[0].isRecorded); assertEquals(255, records[0].flagByte); assertFalse(records[0].isFlagged)
        assertTrue(records[1].isRecorded); assertTrue(records[2].isRecorded); assertFalse(records[3].isRecorded)
        assertTrue(dex.entry(808).caught); assertFalse(dex.entry(808).seen); assertEquals(1023, dex.currentViewedSpecies)
        assertArrayEquals(before, b)
    }

    @Test fun invalidDexMarkerDisablesOnlyPokedexWhileNativeIdentityTrainerAndStorageStayAvailable() {
        for (magic in listOf(0L, 0x2F120F16L, 0xFFFFFFFFL)) {
            val b = fixture(); put32(b, 0x2A00, magic); seal(b); val before = b.copyOf(); val bound = session(b)
            assertTrue(bound.isFormatVerified); assertNull(bound.letsGoPokedex); assertNotNull(bound.pokedexUnavailableReason)
            assertNotNull(bound.letsGoTrainer); assertNotNull(bound.letsGoStorage)
            assertFalse(bound.supports(SwitchReadCapability.POKEDEX)); assertFalse(bound.profile.writesEnabled)
            assertTrue(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertTrue(bound.supports(SwitchReadCapability.PARTY)); assertArrayEquals(before, b)
        }
    }

    @Test fun everyNativeBlockChecksumDescriptorTitleAndSizeGatePrecedesDexExtraction() {
        val good = fixture()
        for ((i, region) in regions.withIndex()) {
            val damaged = good.copyOf(); damaged[region.first] = (damaged[region.first].toInt() xor 1).toByte()
            assertThrows(IllegalArgumentException::class.java) { LetsGoPokedexReadOnly.read(damaged) }
            val descriptor = good.copyOf(); descriptor[0xB8614 + i * 8 + 4] = 255.toByte()
            assertThrows(IllegalArgumentException::class.java) { LetsGoPokedexReadOnly.read(descriptor) }
        }
        for (b in listOf(ByteArray(1), good.copyOf(good.size - 1), good + byteArrayOf(0), fixture(44), good.copyOf().also { it[0xB8610] = 0 }))
            assertThrows(IllegalArgumentException::class.java) { LetsGoPokedexReadOnly.read(b) }
    }

    @Test fun everyHintUsesNativeIdentityAndReturnedNestedListsCannotBeMutated() {
        for (hint in SwitchGameFamily.values()) {
            val b = fixture(43); val bound = session(b, hint); b.fill(0); bound.snapshot.bytesCopy().fill(0)
            val dex = bound.letsGoPokedex!!; assertEquals(LetsGoVersion.EEVEE, dex.version)
            assertEquals(SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, bound.profile.family); assertTrue(bound.isReadOnly)
            assertTrue(bound.supports(SwitchReadCapability.POKEDEX)); assertFalse(bound.supports(SwitchReadCapability.POKEDEX_AND_PROGRESS))
            assertNull(bound.pokedexUnavailableReason); assertFalse(bound.profile.writesEnabled)
            assertThrows(UnsupportedOperationException::class.java) { (dex.entries as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (dex.entry(1).seenVariants as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (dex.entry(1).displayedVariants as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (dex.entry(1).languages as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (dex.entry(1).forms as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (dex.entry(1).forms[0].sizeRecords as MutableList<*>).clear() }
        }
        val dex = LetsGoPokedexReadOnly.read(fixture())
        for (id in listOf(-1, 0, 152, 807, 810, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { dex.entry(id) }
    }

    @Test fun malformedTrainerAndPartyReferencesDoNotHideIndependentPokedex() {
        val b = fixture(); b[0x1035] = 0; put16(b, 0x5A00, 1002); seal(b); val bound = session(b)
        assertNotNull(bound.letsGoPokedex); assertNull(bound.letsGoTrainer); assertNull(bound.letsGoStorage)
        assertNotNull(bound.trainerUnavailableReason); assertNotNull(bound.storageUnavailableReason); assertNull(bound.pokedexUnavailableReason)
        assertTrue(bound.supports(SwitchReadCapability.POKEDEX)); assertFalse(bound.supports(SwitchReadCapability.PARTY))
        assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertTrue(bound.isFormatVerified)
    }
}
