package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspGameOptionsReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0x79B74
    private val wordOffsets = listOf(0, 4, 0x0C, 0x10, 0x14, 0x18, 0x28, 0x2C, 0x34)
    private val flagOffsets = listOf(8, 0x1C, 0x20, 0x24, 0x30, 0x38)
    private fun put32(b: ByteArray, o: Int, v: Long) { repeat(4) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put32(b, 0, layouts[layout].second.toLong()); b[0x79BDF] = title.toByte()
        for (i in 0 until 64) b[base + i] = (i * 37 + 11).toByte()
        for ((offset, word) in listOf(0 to 3L, 4 to 2L, 0x0C to 0x80000000L, 0x10 to 1L, 0x14 to 1L,
            0x18 to 1L, 0x28 to 0L, 0x2C to 1L, 0x34 to 0L)) put32(b, base + offset, word)
        for ((offset, word) in flagOffsets.zip(listOf(0x11223301L, 0xAABBCC00L, 0x101L, 2L, 0xFFFFFF01L, 0x100L))) put32(b, base + offset, word)
        b[base + 0x3C] = 255.toByte(); b[base + 0x3D] = 128.toByte(); b[base + 0x3E] = 0; b[base + 0x3F] = 0xE7.toByte()
        b[base - 1] = 0xA5.toByte(); b[base + 64] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))
    private fun words(read: BdspGameOptionsRead): List<Long> = listOf(read.textSpeedId, read.languageId.toLong(), read.windowTypeId.toLong(),
        read.moveAnimationsId.toLong(), read.battleStyleId.toLong(), read.partyBoxId.toLong(), read.cameraVerticalId.toLong(), read.cameraHorizontalId.toLong(), read.inputModeId.toLong())

    @Test fun bothTitlesAllRevisionsExposeEverySettingAndExact64ByteBoundaries() {
        for (layout in layouts.indices) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val read = BdspGameOptionsReadOnly.read(b)
            assertEquals(title, read.version.romCode); assertEquals(layouts[layout].second.toLong(), read.revision.code)
            assertEquals(listOf(3L, 2L, Int.MIN_VALUE.toLong(), 1L, 1L, 1L, 0L, 1L, 0L), words(read))
            assertEquals("Instant", BdspGameOptionNames.textSpeed(read.textSpeedId)); assertEquals("English", BdspGameOptionNames.language(read.languageId))
            assertEquals("Off", BdspGameOptionNames.moveAnimations(read.moveAnimationsId)); assertEquals("Set", BdspGameOptionNames.battleStyle(read.battleStyleId))
            assertEquals("Send to Box", BdspGameOptionNames.partyBox(read.partyBoxId)); assertEquals("Normal", BdspGameOptionNames.camera(read.cameraVerticalId))
            assertEquals("Reverse", BdspGameOptionNames.camera(read.cameraHorizontalId)); assertEquals("Easy", BdspGameOptionNames.inputMode(read.inputModeId))
            assertEquals(listOf(1, 0, 1, 2, 1, 0), read.flags.map { it.rawByte })
            assertEquals(listOf(true, false, true, false, true, false), read.flags.map { it.nativeEnabled })
            assertEquals(0x101L, read.flag(BdspGameOptionFlag.GYRO_SENSOR).rawWord); assertTrue(read.flag(BdspGameOptionFlag.GYRO_SENSOR).nativeEnabled)
            assertEquals(listOf(255, 128, 0), listOf(read.bgmVolume, read.soundEffectsVolume, read.voiceVolume))
            assertEquals(b.copyOfRange(base, base + 64).map { it.toInt() and 255 }, read.rawConfigBytes); assertEquals(0xE7, read.rawConfigBytes[63])
            assertEquals(0xA5, b[base - 1].toInt() and 255); assertEquals(0x5A, b[base + 64].toInt() and 255); assertArrayEquals(before, b)
        }
    }

    @Test fun signedEnumsUnknownValuesAndKnownMappingsNeverDefaultClampOrBorrowOtherGameRules() {
        for (word in listOf(0L, 1L, 2L, 3L, 4L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
            val b = fixture(); for (offset in wordOffsets) put32(b, base + offset, word); seal(b)
            val before = b.copyOf(); assertEquals(listOf(word) + List(8) { word.toInt().toLong() }, words(BdspGameOptionsReadOnly.read(b))); assertArrayEquals(before, b)
        }
        assertEquals(listOf("Slow", "Normal", "Fast", "Instant"), (0L..3L).map { BdspGameOptionNames.textSpeed(it) })
        assertEquals(listOf("On", "Off"), (0..1).map { BdspGameOptionNames.moveAnimations(it) })
        assertEquals(listOf("Switch", "Set"), (0..1).map { BdspGameOptionNames.battleStyle(it) })
        assertEquals(listOf("Select", "Send to Box"), (0..1).map { BdspGameOptionNames.partyBox(it) })
        assertEquals(listOf("Normal", "Reverse"), (0..1).map { BdspGameOptionNames.camera(it) })
        assertEquals(listOf("Easy", "Normal"), (0..1).map { BdspGameOptionNames.inputMode(it) })
        for (id in listOf(-1L, 4L, 0x80000000L, 0xFFFFFFFFL)) assertNull(BdspGameOptionNames.textSpeed(id))
        for (id in listOf(-1, 2, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertNull(BdspGameOptionNames.moveAnimations(id)); assertNull(BdspGameOptionNames.battleStyle(id))
            assertNull(BdspGameOptionNames.partyBox(id)); assertNull(BdspGameOptionNames.camera(id)); assertNull(BdspGameOptionNames.inputMode(id))
        }
        assertEquals(listOf("Japanese", "English", "French", "Italian", "German", "Spanish", "Korean", "Chinese (Simplified)", "Chinese (Traditional)"),
            listOf(1, 2, 3, 4, 5, 7, 8, 9, 10).map { BdspGameOptionNames.language(it) })
        for (id in listOf(-1, 0, 6, 11, Int.MIN_VALUE, Int.MAX_VALUE)) assertNull(BdspGameOptionNames.language(id))
    }

    @Test fun allFlagAndVolumeBytesKeepNativeFirstByteSemanticsAndIgnoredUpperSlotBytes() {
        for (value in 0..255) {
            val b = fixture(); val word = 0xA1B2C300L or value.toLong()
            for (offset in flagOffsets) put32(b, base + offset, word)
            b[base + 0x3C] = value.toByte(); b[base + 0x3D] = (255 - value).toByte(); b[base + 0x3E] = value.toByte(); seal(b)
            val before = b.copyOf(); val read = BdspGameOptionsReadOnly.read(b)
            for (flag in read.flags) {
                assertEquals(word, flag.rawWord); assertEquals(value, flag.rawByte)
                assertEquals(value == 1, flag.nativeEnabled); assertEquals(value in 0..1, flag.hasCanonicalByte)
            }
            assertEquals(value, read.bgmVolume); assertEquals(255 - value, read.soundEffectsVolume); assertEquals(value, read.voiceVolume)
            assertArrayEquals(before, b)
        }
    }

    @Test fun nativeIntegrityIdentityAndExactLengthPrecedeEveryOptionRead() {
        for (layout in layouts.indices) {
            val source = fixture(layout)
            for (o in setOf(0, 4, base - 1, base, base + 4, base + 8, base + 0x1D, base + 0x3C, base + 63, base + 64, 0xE9818, source.lastIndex)) {
                val bad = source.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspGameOptionsReadOnly.read(bad) }
                assertNull(session(bad).bdspGameOptions); assertArrayEquals(before, bad)
            }
            val title = source.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = source.copyOf(); put32(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, source.copyOf(source.size - 1), source.copyOf(source.size + 1)))
                assertThrows(IllegalArgumentException::class.java) { BdspGameOptionsReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4)))
            assertThrows(IllegalArgumentException::class.java) { BdspGameOptionsReadOnly.read(bad) }
    }

    @Test fun optionsCannotReenableStrictTrainerAndRemainIndependentOfRejectedPartyAndDexSections() {
        val badCases = listOf(fixture().also { put32(it, base + 4, 6) },
            fixture().also { put32(it, 0x79BD4, 1000000) }, fixture().also { it[0x79BD8] = 2 },
            fixture().also { it[0x79BDD] = 9 }, fixture().also { it[0x79BE0] = 2 },
            fixture().also { it[0x79BB4] = 0; it[0x79BB5] = 0xD8.toByte() })
        for (b in badCases) {
            b[0x14098 + 6 * 344] = 7; put32(b, 0x7A328, 4); seal(b)
            val before = b.copyOf(); val bound = session(b)
            assertTrue(bound.isFormatVerified); assertNull(bound.bdspTrainer); assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex); assertNotNull(bound.bdspGameOptions)
            assertNull(bound.bdspGameOptionsUnavailableReason); assertEquals(Int.MIN_VALUE, bound.bdspGameOptions!!.windowTypeId)
            assertNotNull(bound.bdspTimestamps); assertNotNull(bound.bdspPlayTime); assertNotNull(bound.bdspTrainerAppearance)
            assertArrayEquals(before, b)
        }
    }

    @Test fun detachedImmutableSnapshotsUseNativeIdentityAndPreserveEveryEarlierReadOnlySection() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); val before = b.copyOf(); val bound = session(b, hint); val read = bound.bdspGameOptions!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family); assertEquals(title, read.version.romCode)
            b.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy())
            assertEquals(3L, read.textSpeedId); assertEquals(64, read.rawConfigBytes.size); assertEquals(0xE7, read.rawConfigBytes[63])
            assertThrows(UnsupportedOperationException::class.java) { (read.flags as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.rawConfigBytes as MutableList<*>).clear() }
            assertNotNull(bound.bdspTrainer); assertNotNull(bound.bdspPlayTime); assertNotNull(bound.bdspTimestamps); assertNotNull(bound.bdspTrainerAppearance)
            assertNotNull(bound.bdspPoffins); assertNotNull(bound.bdspNursery); assertNotNull(bound.bdspBattleTower); assertNotNull(bound.bdspUnderground)
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspGameOptions); assertNull(other.bdspGameOptionsUnavailableReason)
    }
}
