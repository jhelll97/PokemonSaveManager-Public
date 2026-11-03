package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class SwitchReadGameDetailsTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val keys = setOf("appearance", "playtime", "timestamps", "options", "menu", "poffins", "stickers", "capsules",
        "underground", "encounters", "honey", "berries", "secretbase", "npcs", "return", "activity", "tower", "trainers",
        "statistics", "additional", "nursery", "contest", "union", "groups")
    private fun put(b: ByteArray, o: Int, v: Long) { repeat(4) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also {
        put(it, 0, layouts[layout].second.toLong()); put(it, 0x79B78, 2); it[0x79BDF] = title.toByte(); seal(it)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllLayoutsExposeEachExistingActionExactlyOnceInSixTopics() {
        for (l in layouts.indices) for (title in 0..1) {
            val b = fixture(l, title); val before = b.copyOf(); val s = session(b); val groups = SwitchReadGameDetails.groups(s)
            assertTrue(s.isFormatVerified); assertEquals(title, s.bdspTrainer!!.version.romCode)
            assertEquals(listOf("Trainer and settings", "Items and decoration", "Exploration and Underground",
                "Battles and records", "Nursery and Contest", "Connection records"), groups.map { it.label })
            assertEquals(listOf(5, 3, 8, 4, 2, 2), groups.map { it.entries.size })
            val entries = groups.flatMap { it.entries }; assertEquals(24, entries.size); assertEquals(keys, entries.map { it.section }.toSet())
            assertEquals(24, entries.map { it.section }.distinct().size); assertTrue(entries.all { it.enabled && it.label.isNotBlank() })
            assertArrayEquals(before, b); assertFalse(s.profile.writesEnabled)
        }
    }

    @Test fun unverifiedHintsAndCorruptedNativeFilesNeverGetBdspGroups() {
        for (hint in SwitchGameFamily.values()) assertTrue(SwitchReadGameDetails.groups(session(byteArrayOf(1), hint)).isEmpty())
        val valid = fixture(); val corrupt = valid.copyOf().also { it[it.lastIndex] = 1 }
        val title = valid.copyOf().also { it[0x79BDF] = 2; seal(it) }
        val revision = valid.copyOf().also { put(it, 0, 0x35); seal(it) }
        for (b in listOf(corrupt, title, revision, valid.copyOf(valid.size - 1), valid.copyOf(valid.size + 1))) {
            val before = b.copyOf(); val s = session(b)
            assertFalse(s.isFormatVerified); assertTrue(SwitchReadGameDetails.groups(s).isEmpty()); assertArrayEquals(before, b)
        }
    }

    @Test fun independentDetailsStayReadableWithoutRelaxingStrictTrainerPartyAndDexGates() {
        val b = fixture(); put(b, 0x79BD4, 1000000); b[0x14098 + 6 * 344] = 7; put(b, 0x7A328, 4)
        put(b, 0x7E9F8, -1); put(b, 0x7E9FC, 0xFFFFFFFFL); seal(b); val before = b.copyOf(); val s = session(b)
        assertTrue(s.isFormatVerified); assertNull(s.bdspTrainer); assertNull(s.bdspStorage); assertNull(s.bdspPokedex)
        assertFalse(s.supports(SwitchReadCapability.TRAINER_PROFILE)); assertFalse(s.supports(SwitchReadCapability.PARTY)); assertFalse(s.supports(SwitchReadCapability.POKEDEX))
        val entries = SwitchReadGameDetails.groups(s).flatMap { it.entries }.associateBy { it.section }
        for (key in listOf("options", "menu", "playtime", "union", "groups")) assertTrue(entries.getValue(key).enabled)
        assertEquals(-1, s.bdspMenuSelection!!.item(0).menuId); assertEquals(0xFFFFFFFFL, s.bdspMenuSelection!!.item(0).rawNewWord)
        assertFalse(s.bdspMenuSelection!!.item(0).isNew); assertArrayEquals(before, b)
    }

    @Test fun legacyMissingAdditionalBlockRetainsItsExistingAbsenceViewWithoutInventingRecords() {
        for (l in layouts.indices) {
            val s = session(fixture(l)); val read = s.bdspAdditionalRecords!!
            val entry = SwitchReadGameDetails.groups(s).flatMap { it.entries }.single { it.section == "additional" }
            assertTrue(entry.enabled)
            if (l == 0) {
                assertFalse(read.hasBlock); assertTrue(read.records.isEmpty()); assertTrue(read.rawBlockBytes.isEmpty()); assertNotNull(read.absenceReason)
            } else { assertTrue(read.hasBlock); assertEquals(12, read.records.size); assertNull(read.absenceReason) }
        }
    }

    @Test fun nativeIdentityImmutableGroupsAndSnapshotsRemainReadOnlyForEveryHint() {
        for (hint in SwitchGameFamily.values()) {
            val b = fixture(); val before = b.copyOf(); val s = session(b, hint); val groups = SwitchReadGameDetails.groups(s)
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, s.profile.family); assertEquals(6, groups.size)
            b.fill(0); s.snapshot.bytesCopy().fill(0); assertArrayEquals(before, s.snapshot.bytesCopy()); assertEquals(groups, SwitchReadGameDetails.groups(s))
            assertThrows(UnsupportedOperationException::class.java) { (groups as MutableList<*>).clear() }
            for (group in groups) assertThrows(UnsupportedOperationException::class.java) { (group.entries as MutableList<*>).clear() }
            assertTrue(groups.flatMap { it.entries }.all { it.section in keys }); assertTrue(s.isReadOnly); assertFalse(s.profile.writesEnabled)
        }
    }
}
