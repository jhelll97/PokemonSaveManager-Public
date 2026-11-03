package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class SwitchReadDashboardTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private fun put(b: ByteArray, o: Int, value: Long) { repeat(4) { b[o + it] = (value ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also {
        put(it, 0, layouts[layout].second.toLong()); put(it, 0x79B78, 2); it[0x79BDF] = title.toByte(); seal(it)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ARCEUS): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun verifiedBdspRoutesAllFiveReadSectionsForBothTitlesAndAllLayouts() {
        for (l in layouts.indices) for (title in 0..1) {
            val b = fixture(l, title); val before = b.copyOf(); val s = session(b); val entries = SwitchReadDashboard.entries(s)
            assertTrue(s.isFormatVerified); assertEquals(title, s.bdspTrainer!!.version.romCode)
            assertEquals(listOf("trainer", "storage", "party", "inventory", "pokedex"), entries.map { it.section })
            assertEquals(listOf("Trainer Data", "Pokémon Boxes", "Party Pokémon", "Items", "Pokédex"), entries.map { it.label })
            assertTrue(entries.all { it.enabled }); assertEquals(5, entries.map { it.section }.distinct().size)
            assertArrayEquals(before, b); assertTrue(s.isReadOnly); assertFalse(s.profile.writesEnabled)
        }
    }

    @Test fun unverifiedFamilyHintsNeverEnableCardsAndStorageTerminologyStaysTitleSpecific() {
        for (family in SwitchGameFamily.values()) {
            val s = session(byteArrayOf(1), family); val entries = SwitchReadDashboard.entries(s)
            assertFalse(s.isFormatVerified); assertTrue(entries.none { it.enabled })
            val expected = when (family) {
                SwitchGameFamily.LEGENDS_ARCEUS -> "Pokémon Pastures"
                SwitchGameFamily.LETS_GO_PIKACHU_EEVEE -> "Pokémon Storage"
                else -> "Pokémon Boxes"
            }
            assertEquals(expected, entries.single { it.section == "storage" }.label)
            assertFalse(s.profile.writesEnabled)
        }
    }

    @Test fun strictReaderFailuresDisableOnlyTheirCardsWithoutPromotingPlannedCapabilities() {
        val b = fixture(); put(b, 0x79BD4, 1000000); b[0x14098 + 6 * 344] = 7; put(b, 0x7A328, 4); seal(b)
        val before = b.copyOf(); val s = session(b); val entries = SwitchReadDashboard.entries(s).associateBy { it.section }
        assertTrue(s.isFormatVerified); assertNull(s.bdspTrainer); assertNull(s.bdspStorage); assertNull(s.bdspPokedex)
        for (section in listOf("trainer", "storage", "party", "pokedex")) assertFalse(entries.getValue(section).enabled)
        assertNotNull(s.bdspInventory); assertTrue(entries.getValue("inventory").enabled)
        assertNotNull(s.bdspMenuSelection); assertNotNull(s.bdspRandomGroups); assertArrayEquals(before, b)
    }

    @Test fun corruptNativeFilesCannotExposeAnyReadCard() {
        val valid = fixture(); val damaged = valid.copyOf().also { it[it.lastIndex] = 1 }
        val title = valid.copyOf().also { it[0x79BDF] = 2; seal(it) }
        val revision = valid.copyOf().also { put(it, 0, 0x35); seal(it) }
        for (b in listOf(damaged, title, revision, valid.copyOf(valid.size - 1), valid.copyOf(valid.size + 1))) {
            val before = b.copyOf(); val s = session(b)
            assertFalse(s.isFormatVerified); assertTrue(SwitchReadDashboard.entries(s).none { it.enabled }); assertArrayEquals(before, b)
        }
    }

    @Test fun nativeIdentitySnapshotAndImmutableNavigationRemainReadOnly() {
        for (hint in SwitchGameFamily.values()) {
            val b = fixture(); val before = b.copyOf(); val s = session(b, hint); val entries = SwitchReadDashboard.entries(s)
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, s.profile.family)
            assertEquals("Pokémon Boxes", entries.single { it.section == "storage" }.label)
            b.fill(0); s.snapshot.bytesCopy().fill(0); assertArrayEquals(before, s.snapshot.bytesCopy())
            assertEquals(entries, SwitchReadDashboard.entries(s)); assertTrue(entries.all { it.enabled })
            assertThrows(UnsupportedOperationException::class.java) { (entries as MutableList<*>).clear() }
            assertFalse(entries.any { it.section in setOf("export", "replace", "edit", "convert", "bank", "events") })
            assertTrue(s.isReadOnly); assertFalse(s.profile.writesEnabled)
        }
    }
}
