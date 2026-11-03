package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspEncounterReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0x8F97C
    private fun put(b: ByteArray, o: Int, v: Long, count: Int = 4) { repeat(count) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put(b, 0, layouts[layout].second.toLong()); put(b, 0x79B78, 2); b[0x79BDF] = title.toByte()
        put(b, base, Int.MIN_VALUE.toLong()); put(b, base + 4, 0xFFFFFFFFL); put(b, base + 8, 0x80000001L)
        put(b, base + 0xC, 0x101); put(b, base + 0x10, 65535, 2); put(b, base + 0x12, 32768, 2)
        for (i in 0 until 264) b[base + 0x14 + i] = (i * 19).toByte()
        for (i in 0 until 3) { put(b, base + 0x11C + i * 8, 0xFFFFFFFFL - i); put(b, base + 0x120 + i * 8, 0x80000000L + i) }
        put(b, base + 0x134, Int.MIN_VALUE.toLong()); put(b, base + 0x138, -1)
        for (i in 0 until 2) {
            val o = base + 0x13C + i * 32; b.fill((0xA0 + i).toByte(), o, o + 32)
            put(b, o, if (i == 0) -1 else Int.MAX_VALUE.toLong())
            put(b, o + 4, if (i == 0) -1 else Long.MIN_VALUE + 1, 8)
            put(b, o + 12, 0xFFFFFFFFL - i); put(b, o + 16, 0xFFFFFFFFL - i)
            b[o + 20] = (255 - i).toByte(); put(b, o + 24, 0x80000000L + i); b[o + 28] = (254 - i).toByte()
        }
        put(b, base + 0x17C, 1); put(b, base + 0x180, -32768, 2)
        b[base + 0x182] = 0xFF.toByte(); b[base + 0x183] = 200.toByte(); b[base + 0x184] = 201.toByte(); b[base + 0x185] = 254.toByte()
        b[base + 0x186] = 0xAB.toByte(); b[base + 0x187] = 0xCD.toByte()
        b[base - 1] = 0xA5.toByte(); b[base + 392] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllRevisionsExposeExactNativeMetadataAndEveryAlignmentByte() {
        for (layout in layouts.indices) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val read = BdspEncounterReadOnly.read(b)
            assertEquals(title, read.version.romCode); assertEquals(layouts[layout].second.toLong(), read.revision.code)
            assertEquals(Int.MIN_VALUE, read.walkCount); assertEquals(0xFFFFFFFFL, read.safariSeed); assertEquals(0x80000001L, read.generateSeed)
            assertEquals(257L, read.rawHillTalkWord); assertFalse(read.isHillTalk); assertEquals(65535, read.hillTable1); assertEquals(32768, read.hillTable2)
            assertEquals(Int.MIN_VALUE, read.beforeZone); assertEquals(-1, read.oldZone); assertEquals(3, read.radarRecords.size); assertEquals(2, read.roamers.size)
            for (i in 0 until 3) {
                val radar = read.radarRecord(i); assertEquals(i, radar.index); assertEquals(0xFFFFFFFFL - i, radar.speciesId); assertEquals(0x80000000L + i, radar.chain)
                assertEquals(b.copyOfRange(base + 0x11C + i * 8, base + 0x124 + i * 8).map { it.toInt() and 255 }, radar.rawBytes)
            }
            for (i in 0 until 2) {
                val roamer = read.roamer(i); val o = base + 0x13C + i * 32
                assertEquals(i, roamer.index); assertEquals(if (i == 0) -1 else Int.MAX_VALUE, roamer.zoneId)
                assertEquals(if (i == 0) "18446744073709551615" else "9223372036854775809", roamer.seed.toString())
                assertEquals(0xFFFFFFFFL - i, roamer.speciesId); assertEquals(0xFFFFFFFFL - i, roamer.hp); assertEquals(255 - i, roamer.level)
                assertEquals(0x80000000L + i, roamer.statusWord); assertEquals(254 - i, roamer.encounterByte)
                assertEquals(b.copyOfRange(o, o + 32).map { it.toInt() and 255 }, roamer.rawBytes)
                for (pad in listOf(21, 22, 23, 29, 30, 31)) assertEquals(0xA0 + i, roamer.rawBytes[pad])
            }
            assertEquals(1L, read.rawGenerateValidWord); assertTrue(read.isGenerateValid); assertEquals(-32768, read.sprayCount)
            assertEquals(255, read.sprayType); assertEquals(200, read.vsSeekerCharge); assertEquals(201, read.radarCharge); assertEquals(254, read.fluteType)
            assertEquals(392, read.rawEncounterBytes.size); assertEquals(b.copyOfRange(base, base + 392).map { it.toInt() and 255 }, read.rawEncounterBytes)
            assertEquals(0xA5, b[base - 1].toInt() and 255); assertEquals(0x5A, b[base + 392].toInt() and 255); assertArrayEquals(before, b)
        }
    }

    @Test fun fullWidthUnsignedSeedsSignedCountsZonesAndAllByteValuesRemainExact() {
        for (seed in listOf(0uL, 1uL, Long.MAX_VALUE.toULong(), Long.MAX_VALUE.toULong() + 1uL, ULong.MAX_VALUE - 1uL, ULong.MAX_VALUE)) {
            val b = fixture(); for (i in 0 until 2) put(b, base + 0x140 + i * 32, seed.toLong(), 8); seal(b)
            val before = b.copyOf(); val read = BdspEncounterReadOnly.read(b)
            for (roamer in read.roamers) assertEquals(seed.toString(), roamer.seed.toString()); assertArrayEquals(before, b)
        }
        for (value in listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)) {
            val b = fixture(); for (o in listOf(0, 0x134, 0x138, 0x13C, 0x15C)) put(b, base + o, value.toLong()); seal(b)
            val before = b.copyOf(); val read = BdspEncounterReadOnly.read(b)
            assertEquals(value, read.walkCount); assertEquals(value, read.beforeZone); assertEquals(value, read.oldZone)
            for (roamer in read.roamers) assertEquals(value, roamer.zoneId); assertArrayEquals(before, b)
        }
        for (value in listOf(-32768, -1, 0, 1, 32767)) {
            val b = fixture(); put(b, base + 0x180, value.toLong(), 2); seal(b)
            val before = b.copyOf(); assertEquals(value, BdspEncounterReadOnly.read(b).sprayCount); assertArrayEquals(before, b)
        }
        for (value in 0..255) {
            val b = fixture(); for (o in listOf(0x150, 0x158, 0x170, 0x178, 0x182, 0x183, 0x184, 0x185)) b[base + o] = value.toByte(); seal(b)
            val before = b.copyOf(); val read = BdspEncounterReadOnly.read(b)
            for (roamer in read.roamers) { assertEquals(value, roamer.level); assertEquals(value, roamer.encounterByte) }
            assertEquals(value, read.sprayType); assertEquals(value, read.vsSeekerCharge); assertEquals(value, read.radarCharge); assertEquals(value, read.fluteType); assertArrayEquals(before, b)
        }
    }

    @Test fun wholeWordFlagsAndUnsignedWordsNeverBecomeLowByteBooleansOrClampedValues() {
        for (word in listOf(0L, 1L, 2L, 3L, 255L, 256L, 257L, 0x01000000L, 0x01000001L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL) + (0 until 32).map { 1L shl it }) {
            val b = fixture(); for (o in listOf(4, 8, 0xC, 0x11C, 0x120, 0x124, 0x128, 0x12C, 0x130, 0x148, 0x14C, 0x154, 0x168, 0x16C, 0x174, 0x17C)) put(b, base + o, word); seal(b)
            val before = b.copyOf(); val read = BdspEncounterReadOnly.read(b)
            assertEquals(word, read.rawHillTalkWord); assertEquals(word, read.rawGenerateValidWord); assertEquals(word == 1L, read.isHillTalk); assertEquals(word == 1L, read.isGenerateValid)
            assertEquals(word, read.safariSeed); assertEquals(word, read.generateSeed)
            for (radar in read.radarRecords) { assertEquals(word, radar.speciesId); assertEquals(word, radar.chain) }
            for (roamer in read.roamers) { assertEquals(word, roamer.speciesId); assertEquals(word, roamer.hp); assertEquals(word, roamer.statusWord) }
            assertArrayEquals(before, b)
        }
    }

    @Test fun fullNativeIntegrityAndIdentityPrecedeExtractionAndIndicesRejectWithoutFallback() {
        for (layout in layouts.indices) {
            val source = fixture(layout)
            for (o in setOf(0, 4, base - 1, base, base + 0xC, base + 0x14, base + 0x11B, base + 0x11C, base + 0x134, base + 0x13C, base + 0x140, base + 0x15B, base + 0x15C, base + 0x17B, base + 0x17C, base + 0x180, base + 0x187, base + 0x188, 0xE9818, source.lastIndex)) {
                val bad = source.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspEncounterReadOnly.read(bad) }; assertNull(session(bad).bdspEncounter); assertArrayEquals(before, bad)
            }
            val title = source.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = source.copyOf(); put(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, source.copyOf(source.size - 1), source.copyOf(source.size + 1)))
                assertThrows(IllegalArgumentException::class.java) { BdspEncounterReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4))) assertThrows(IllegalArgumentException::class.java) { BdspEncounterReadOnly.read(bad) }
        val read = BdspEncounterReadOnly.read(fixture())
        for (index in listOf(-1, 2, Int.MIN_VALUE, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { read.roamer(index) }
        for (index in listOf(-1, 3, Int.MIN_VALUE, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { read.radarRecord(index) }
    }

    @Test fun independentEncounterReaderDoesNotRelaxTrainerPartyOrDexGates() {
        val badCases = listOf(fixture().also { put(it, 0x79B78, 6) }, fixture().also { put(it, 0x79BD4, 1000000) },
            fixture().also { it[0x79BD8] = 2 }, fixture().also { it[0x79BDD] = 9 }, fixture().also { it[0x79BE0] = 2 },
            fixture().also { it[0x79BB4] = 0; it[0x79BB5] = 0xD8.toByte() })
        for (b in badCases) {
            b[0x14098 + 6 * 344] = 7; put(b, 0x7A328, 4); seal(b); val before = b.copyOf(); val bound = session(b)
            assertTrue(bound.isFormatVerified); assertNull(bound.bdspTrainer); assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex); assertNotNull(bound.bdspEncounter); assertNull(bound.bdspEncounterUnavailableReason)
            assertNotNull(bound.bdspHoneyTrees); assertNotNull(bound.bdspBattleTrainerStatus); assertArrayEquals(before, b)
        }
    }

    @Test fun detachedImmutableRecordsUseNativeIdentityAndPreserveHoneyTreesAndEarlierViews() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); val before = b.copyOf(); val bound = session(b, hint); val read = bound.bdspEncounter!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family); assertEquals(title, read.version.romCode)
            b.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy())
            assertEquals("18446744073709551615", read.roamer(0).seed.toString()); assertEquals(200, read.vsSeekerCharge)
            assertThrows(UnsupportedOperationException::class.java) { (read.radarRecords as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.roamers as MutableList<*>).clear() }
            for (radar in read.radarRecords) assertThrows(UnsupportedOperationException::class.java) { (radar.rawBytes as MutableList<*>).clear() }
            for (roamer in read.roamers) assertThrows(UnsupportedOperationException::class.java) { (roamer.rawBytes as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (read.rawEncounterBytes as MutableList<*>).clear() }
            assertEquals(read.rawEncounterBytes.subList(0x14, 0x11C), bound.bdspHoneyTrees!!.rawHoneyBytes)
            assertNotNull(bound.bdspTrainer); assertNotNull(bound.bdspBattleTrainerStatus); assertNotNull(bound.bdspAdditionalRecords)
            assertNotNull(bound.bdspRecordStatistics); assertNotNull(bound.bdspUndergroundActivity); assertNotNull(bound.bdspBattleTower)
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspEncounter); assertNull(other.bdspEncounterUnavailableReason)
    }
}
