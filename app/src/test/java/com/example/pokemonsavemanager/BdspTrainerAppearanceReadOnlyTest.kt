package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspTrainerAppearanceReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0x79BB4
    private fun put32(b: ByteArray, o: Int, v: Long) { repeat(4) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put32(b, 0, layouts[layout].second.toLong()); put32(b, 0x79B78, 2); put32(b, base + 0x20, 999999)
        b[base + 0x24] = 1; b[base + 0x28] = 255.toByte(); b[base + 0x29] = 8; b[base + 0x2A] = 254.toByte()
        b[base + 0x2B] = title.toByte(); b[base + 0x2C] = 1; b[base + 0x30] = 255.toByte(); b[base + 0x31] = 112
        b[base + 0x32] = 0xF1.toByte(); b[base + 0x33] = 0xF2.toByte()
        put32(b, base + 0x34, 0x80000000L); b[base + 0x38] = 255.toByte()
        b[base + 0x39] = 0xB1.toByte(); b[base + 0x3A] = 0xB2.toByte(); b[base + 0x3B] = 0xB3.toByte()
        put32(b, base + 0x3C, 0xFFFFFFFFL); put32(b, base + 0x40, 0x80000000L); put32(b, base + 0x44, 0x7FFFFFFFL)
        put32(b, base + 0x48, 0xC1480000L); put32(b, base + 0x4C, 0x43B40000L)
        b[base - 1] = 0xA5.toByte(); b[base + 0x50] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesFourRevisionsExposeAllNativeFieldsAndExactStatusBoundaries() {
        for (layout in layouts.indices) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val appearance = BdspTrainerAppearanceReadOnly.read(b)
            assertEquals(title, appearance.version.romCode); assertEquals(layouts[layout].second.toLong(), appearance.revision.code)
            assertEquals(255, appearance.regionCode); assertEquals(254, appearance.trainerViewId); assertEquals(255, appearance.bodyTypeId)
            assertEquals(112, appearance.fashionId); assertEquals("Leather Jacket (Female)", appearance.fashionName)
            assertEquals(Int.MIN_VALUE, appearance.starterTypeId); assertEquals(255, appearance.rawDsPlayerFlag)
            assertFalse(appearance.isDsPlayer); assertFalse(appearance.hasCanonicalDsPlayerFlag); assertEquals(-1, appearance.followerIndex)
            assertEquals(Int.MIN_VALUE, appearance.gridX); assertEquals(Int.MAX_VALUE, appearance.gridY)
            assertEquals(-12.5f, appearance.height, 0f); assertEquals(360f, appearance.rotation, 0f)
            assertEquals(0xC1480000L, appearance.rawHeightBits); assertEquals(0x43B40000L, appearance.rawRotationBits)
            assertEquals(b.copyOfRange(base, base + 0x50).map { it.toInt() and 255 }, appearance.rawStatusBytes)
            assertEquals(listOf(0xF1, 0xF2), appearance.rawStatusBytes.subList(0x32, 0x34)); assertEquals(listOf(0xB1, 0xB2, 0xB3), appearance.rawStatusBytes.subList(0x39, 0x3C))
            assertArrayEquals(before, b); assertEquals(0xA5, b[base - 1].toInt() and 255); assertEquals(0x5A, b[base + 0x50].toInt() and 255)
            // Saved clothing IDs are independent of the trainer gender; do not normalize an apparent mismatch.
            assertEquals(0, BdspTrainerReadOnly.read(b).gender)
        }
    }

    @Test fun all256AppearanceAndFlagBytesRetainUnknownValuesAnd26NativeFashionNames() {
        val fashionLabels = (0..255).mapNotNull { id -> BdspFashionNames.name(id)?.let { "$id:$it" } }.joinToString("\n")
        assertEquals("a642d9e9e0bf397570be99f2ad98b3947f7ebac79516116dfb08606df67aeeb5", MessageDigest.getInstance("SHA-256").digest(fashionLabels.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) })
        for (value in 0..255) {
            val b = fixture(); for (offset in listOf(0x28, 0x2A, 0x30, 0x31, 0x38)) b[base + offset] = value.toByte(); seal(b)
            val before = b.copyOf(); val appearance = BdspTrainerAppearanceReadOnly.read(b)
            assertEquals(value, appearance.regionCode); assertEquals(value, appearance.trainerViewId); assertEquals(value, appearance.bodyTypeId); assertEquals(value, appearance.fashionId)
            assertEquals(value, appearance.rawDsPlayerFlag); assertEquals(value == 1, appearance.isDsPlayer); assertEquals(value in 0..1, appearance.hasCanonicalDsPlayerFlag)
            assertEquals(value in 0..12 || value in 100..112, appearance.fashionName != null); assertArrayEquals(before, b)
        }
        assertEquals("Everyday (Male)", BdspFashionNames.name(0)); assertEquals("Everyday (Female)", BdspFashionNames.name(100))
        assertNull(BdspFashionNames.name(13)); assertNull(BdspFashionNames.name(99)); assertNull(BdspFashionNames.name(255))
    }

    @Test fun signedWordsAndEveryFloatBoundaryKeepRawBitsWithoutNormalization() {
        for (word in listOf(0L, 1L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
            val b = fixture(); for (offset in listOf(0x34, 0x3C, 0x40, 0x44)) put32(b, base + offset, word); seal(b)
            val appearance = BdspTrainerAppearanceReadOnly.read(b)
            assertEquals(List(4) { word.toInt() }, listOf(appearance.starterTypeId, appearance.followerIndex, appearance.gridX, appearance.gridY))
        }
        val words = listOf(0L, 0x80000000L, 1L, 0x80000001L, 0x3F800000L, 0xBF800000L, 0x7F7FFFFFL, 0xFF7FFFFFL,
            0x7F800000L, 0xFF800000L, 0x7FC01234L, 0x7F801234L, 0xFFC00001L)
        for (word in words) {
            val b = fixture(); put32(b, base + 0x48, word); put32(b, base + 0x4C, word); seal(b)
            val before = b.copyOf(); val appearance = BdspTrainerAppearanceReadOnly.read(b)
            assertEquals(word, appearance.rawHeightBits); assertEquals(word, appearance.rawRotationBits)
            val nonFinite = word and 0x7F800000L == 0x7F800000L
            assertEquals(!nonFinite, appearance.height.isFinite()); assertEquals(!nonFinite, appearance.rotation.isFinite())
            if (!nonFinite) { assertEquals(word.toInt(), appearance.height.toRawBits()); assertEquals(word.toInt(), appearance.rotation.toRawBits()) }
            else if (word and 0x7FFFFFL == 0L) assertTrue(appearance.height.isInfinite()) else assertTrue(appearance.height.isNaN())
            assertArrayEquals(before, b)
        }
    }

    @Test fun strictTrainerValidationRemainsIndependentAndCannotBeBypassedByThisMetadataView() {
        val badCases = listOf(
            fixture().also { b -> put32(b, base + 0x20, 1000000) },
            fixture().also { b -> b[base + 0x24] = 2 },
            fixture().also { b -> b[base + 0x29] = 9 },
            fixture().also { b -> b[base + 0x2C] = 2 },
            fixture().also { b -> put32(b, 0x79B78, 6) },
            fixture().also { b -> b[base] = 0; b[base + 1] = 0xD8.toByte() }
        )
        for (b in badCases) {
            seal(b); val before = b.copyOf(); val bound = session(b)
            assertTrue(bound.isFormatVerified); assertNull(bound.bdspTrainer); assertNotNull(bound.trainerUnavailableReason)
            assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertThrows(IllegalArgumentException::class.java) { BdspTrainerReadOnly.read(b) }
            assertNotNull(bound.bdspTrainerAppearance); assertNull(bound.bdspTrainerAppearanceUnavailableReason)
            assertEquals(112, bound.bdspTrainerAppearance!!.fashionId); assertEquals(0xC1480000L, bound.bdspTrainerAppearance!!.rawHeightBits); assertArrayEquals(before, b)
        }
    }

    @Test fun wholeFileIntegrityTitleRevisionAndExactLengthsPrecedeEveryAppearanceRead() {
        for (layout in layouts.indices) {
            val source = fixture(layout)
            for (o in setOf(0, 4, base - 1, base, base + 0x28, base + 0x31, base + 0x3C, base + 0x48, base + 0x4F, base + 0x50, 0xE9818, source.lastIndex)) {
                val bad = source.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspTrainerAppearanceReadOnly.read(bad) }; assertNull(session(bad).bdspTrainerAppearance); assertArrayEquals(before, bad)
            }
            val title = source.copyOf(); title[base + 0x2B] = 2; seal(title)
            val revision = source.copyOf(); put32(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, source.copyOf(source.size - 1), source.copyOf(source.size + 1)))
                assertThrows(IllegalArgumentException::class.java) { BdspTrainerAppearanceReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4))) assertThrows(IllegalArgumentException::class.java) { BdspTrainerAppearanceReadOnly.read(bad) }
    }

    @Test fun immutableSnapshotsNativeIdentityAndOtherReadOnlySectionsRemainIndependent() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val source = fixture(title = title); source[0x14098 + 6 * 344] = 7; put32(source, 0x7A328, 4); seal(source)
            val before = source.copyOf(); val bound = session(source, hint); val appearance = bound.bdspTrainerAppearance!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family); assertTrue(bound.isFormatVerified)
            assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex); assertNotNull(bound.bdspTrainer); assertNotNull(bound.bdspPoffins)
            assertNotNull(bound.bdspNursery); assertNotNull(bound.bdspBattleTower); assertNotNull(bound.bdspUnderground); assertNull(bound.bdspTrainerAppearanceUnavailableReason)
            source.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy())
            assertEquals(112, appearance.fashionId); assertEquals(80, appearance.rawStatusBytes.size)
            assertThrows(UnsupportedOperationException::class.java) { (appearance.rawStatusBytes as MutableList<*>).clear() }
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspTrainerAppearance); assertNull(other.bdspTrainerAppearanceUnavailableReason)
    }
}
