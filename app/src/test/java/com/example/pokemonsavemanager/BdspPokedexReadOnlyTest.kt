package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspPokedexReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val dexOffset = 0x7A328
    private val forms = listOf(201 to 28, 351 to 4, 386 to 4, 412 to 3, 413 to 3, 414 to 3,
        421 to 2, 422 to 2, 423 to 2, 479 to 6, 487 to 2, 492 to 2, 493 to 18)
    private fun put32(b: ByteArray, offset: Int, value: Long) { repeat(4) { b[offset + it] = (value ushr (8 * it)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put32(b, 0, layouts[layout].second.toLong()); b[0x79BDF] = title.toByte(); b[0x79BD8] = 1; put32(b, 0x79B78, 2)
        put32(b, dexOffset, 3); put32(b, dexOffset + 492 * 4, 2)
        for (offset in listOf(0x7B4, 0xF68, 0x171C, 0x1ED0)) { put32(b, dexOffset + offset, 1); put32(b, dexOffset + offset + 492 * 4, 1) }
        put32(b, dexOffset + 0x28FC, 0xFFFFFFFFL); put32(b, dexOffset + 0x28FC + 492 * 4, 1L shl 8)
        put32(b, dexOffset + 0x30B0, 1); put32(b, dexOffset + 0x30B4, 1)
        for (group in 0..3) { put32(b, 0xCEA34 + group * 4, 0xFFFFFFFFL - group); put32(b, 0xCEA44 + group * 4, 0x80000000L + group) }
        seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.SWORD_SHIELD): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllFourRevisionsReadDetached493SpeciesAndRawFlags() {
        for (layout in layouts.indices) for (title in 0..1) {
            val source = fixture(layout, title); val before = source.copyOf(); val dex = BdspPokedexReadOnly.read(source)
            if (layout == 0 && title == 0) assertEquals("0fe5dfd42dc9dd72756205725412e3bdd905cb7becdfda31620072afc10029f5", MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it.toInt() and 255) })
            assertEquals(title, dex.version.romCode); assertEquals(layouts[layout].second.toLong(), dex.revision.code)
            assertEquals((1..493).toList(), dex.entries.map { it.speciesId }); assertEquals(2, dex.heardCount); assertEquals(2, dex.seenCount); assertEquals(1, dex.caughtCount)
            assertEquals(BdspDexState.CAUGHT, dex.entry(1).state); assertEquals(BdspDexState.SEEN, dex.entry(493).state)
            assertEquals(9, dex.entry(1).languages.size); assertEquals(listOf("Chinese (Traditional)"), dex.entry(493).languages)
            assertEquals(0xFFFFFFFFL, dex.entry(1).rawLanguageFlags); assertTrue(dex.regionalDex.value); assertTrue(dex.nationalDex.value)
            assertArrayEquals(before, source); source.fill(0); assertTrue(dex.entry(1).caught)
        }
    }

    @Test fun all493StateGenderAndLanguageRecordsHonorFourByteStrideAndByteBoundaries() {
        val b = fixture()
        for (index in 0 until 493) {
            put32(b, dexOffset + index * 4, (index % 4).toLong())
            for ((group, offset) in listOf(0x171C, 0x1ED0, 0x7B4, 0xF68).withIndex()) put32(b, dexOffset + offset + index * 4, if (group == index % 4) 1L else 0L)
            put32(b, dexOffset + 0x28FC + index * 4, 1L shl (index % 9))
        }
        seal(b); val dex = BdspPokedexReadOnly.read(b)
        val languages = listOf("Japanese", "English", "French", "Italian", "German", "Spanish", "Korean", "Chinese (Simplified)", "Chinese (Traditional)")
        for ((index, entry) in dex.entries.withIndex()) {
            assertEquals(index % 4, entry.state.ordinal); assertEquals((0..3).map { it == index % 4 }, entry.genderFlags.map { it.value })
            assertEquals(listOf(languages[index % 9]), entry.languages)
            assertEquals(index % 4 >= 1, entry.heard); assertEquals(index % 4 >= 2, entry.seen); assertEquals(index % 4 == 3, entry.caught)
        }
    }

    @Test fun allThirteenSpeciesFormArraysAndShinyOffsetsUseNativeAlignedWords() {
        val b = fixture(); var offset = 0x2684
        for ((species, count) in forms) {
            for (form in 0 until count) { put32(b, dexOffset + offset + form * 4, (form % 2).toLong()); put32(b, dexOffset + offset + (count + form) * 4, ((form + 1) % 2).toLong()) }
            offset += count * 8
        }
        assertEquals(0x28FC, offset); seal(b); val dex = BdspPokedexReadOnly.read(b)
        for ((species, count) in forms) {
            val entry = dex.entry(species); assertEquals(count, entry.forms.size)
            for (form in 0 until count) { assertEquals(form, entry.forms[form].formId); assertEquals(form % 2 == 1, entry.forms[form].normal.value); assertEquals(form % 2 == 0, entry.forms[form].shiny.value) }
        }
        assertEquals(79, dex.entries.sumOf { it.forms.size }); assertTrue(dex.entry(25).forms.isEmpty())
    }

    @Test fun fullWidthBooleanWordsUnknownBitsAndContradictoryStatesAreNotNormalized() {
        for (raw in listOf(0L, 1L, 2L, 0x1000001L, 0x80000001L, 0xFFFFFFFFL)) {
            val b = fixture(); put32(b, dexOffset, 0); put32(b, dexOffset + 0x171C, raw)
            put32(b, dexOffset + 0x2684, raw); put32(b, dexOffset + 0x30B0, raw); put32(b, dexOffset + 0x30B4, raw)
            seal(b); val before = b.copyOf(); val dex = BdspPokedexReadOnly.read(b)
            for (flag in listOf(dex.entry(1).genderFlags[0], dex.entry(201).forms[0].normal, dex.regionalDex, dex.nationalDex)) {
                assertEquals(raw, flag.rawValue); assertEquals(raw == 1L, flag.value)
            }
            assertEquals(BdspDexState.NONE, dex.entry(1).state); assertFalse(dex.entry(1).seen); assertEquals(9, dex.entry(1).languages.size)
            assertArrayEquals(before, b)
        }
    }

    @Test fun spindaPreservesAllEightUnsignedPatternEcsIncludingZeroWithoutInferringRegistration() {
        val b = fixture(); put32(b, 0xCEA34, 0); seal(b); val dex = BdspPokedexReadOnly.read(b); val spinda = dex.entry(327)
        assertEquals(BdspDexState.NONE, spinda.state); assertEquals(listOf("Male", "Female", "Shiny male", "Shiny female"), spinda.spinda.map { it.variant })
        for (group in 0..3) {
            assertEquals(if (group == 0) 0L else 0xFFFFFFFFL - group, spinda.spinda[group].seenEc)
            assertEquals(0x80000000L + group, spinda.spinda[group].caughtEc)
        }
        assertTrue(dex.entry(328).spinda.isEmpty())
    }

    @Test fun malformedStatesDisableOnlyDexAndNativeHashRevisionTitleSizeGatesAlwaysApply() {
        for (species in listOf(1, 493)) for (state in listOf(4L, 0x1000002L, 0x80000002L, 0xFFFFFFFFL)) {
            val b = fixture(); put32(b, dexOffset + (species - 1) * 4, state); seal(b); val before = b.copyOf(); val bound = session(b)
            assertTrue(bound.isFormatVerified); assertNull(bound.bdspPokedex); assertNotNull(bound.pokedexUnavailableReason)
            assertNotNull(bound.bdspTrainer); assertNotNull(bound.bdspStorage); assertFalse(bound.supports(SwitchReadCapability.POKEDEX))
            assertTrue(bound.supports(SwitchReadCapability.PARTY)); assertTrue(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertArrayEquals(before, b)
        }
        for (layout in layouts.indices) {
            val b = fixture(layout)
            for (offset in setOf(0, 4, dexOffset, dexOffset + 0x30B7, 0xCEA34, 0xE9818, b.lastIndex)) {
                val damaged = b.copyOf(); damaged[offset] = (damaged[offset].toInt() xor 1).toByte()
                assertThrows(IllegalArgumentException::class.java) { BdspPokedexReadOnly.read(damaged) }
            }
            val wrongRevision = b.copyOf(); put32(wrongRevision, 0, 0x35); seal(wrongRevision)
            assertThrows(IllegalArgumentException::class.java) { BdspPokedexReadOnly.read(wrongRevision) }
            val wrongTitle = b.copyOf(); wrongTitle[0x79BDF] = 2; seal(wrongTitle)
            assertThrows(IllegalArgumentException::class.java) { BdspPokedexReadOnly.read(wrongTitle) }
        }
        for (b in listOf(ByteArray(1), fixture().copyOf(fixture().size - 1))) assertThrows(IllegalArgumentException::class.java) { BdspPokedexReadOnly.read(b) }
    }

    @Test fun everyFamilyHintAndNestedListImmutabilityPreserveReadOnlyScopedAccess() {
        for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = 1); val bound = session(b, hint); b.fill(0); bound.snapshot.bytesCopy().fill(0)
            val dex = bound.bdspPokedex!!; assertEquals(BdspVersion.SHINING_PEARL, dex.version)
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family)
            assertTrue(bound.isReadOnly); assertTrue(bound.supports(SwitchReadCapability.POKEDEX)); assertFalse(bound.supports(SwitchReadCapability.POKEDEX_AND_PROGRESS))
            assertFalse(bound.profile.writesEnabled); assertNull(bound.letsGoPokedex); assertNull(bound.swordShieldPokedex); assertNull(bound.pokedexUnavailableReason)
            assertThrows(UnsupportedOperationException::class.java) { (dex.entries as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (dex.entry(1).genderFlags as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (dex.entry(1).languages as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (dex.entry(493).forms as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (dex.entry(327).spinda as MutableList<*>).clear() }
        }
        val dex = BdspPokedexReadOnly.read(fixture())
        for (id in listOf(-1, 0, 494, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { dex.entry(id) }
    }

    @Test fun malformedTrainerAndPartyCannotSuppressIndependentlyValidDex() {
        val b = fixture(); put32(b, 0x79B78, 0); b[0x14098 + 6 * 344] = 7; seal(b); val bound = session(b)
        assertNotNull(bound.bdspPokedex); assertNull(bound.bdspTrainer); assertNull(bound.bdspStorage)
        assertNotNull(bound.trainerUnavailableReason); assertNotNull(bound.storageUnavailableReason); assertNull(bound.pokedexUnavailableReason)
        assertTrue(bound.supports(SwitchReadCapability.POKEDEX)); assertFalse(bound.supports(SwitchReadCapability.PARTY))
        assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertTrue(bound.isFormatVerified)
    }
}
