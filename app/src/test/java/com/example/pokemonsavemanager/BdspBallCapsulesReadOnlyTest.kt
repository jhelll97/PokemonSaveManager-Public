package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspBallCapsulesReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base = 0x8FB84
    private fun put(b: ByteArray, o: Int, v: Long, size: Int = 4) { repeat(size) { b[o + it] = (v ushr (it * 8)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put(b, 0, layouts[layout].second.toLong()); put(b, 0x79B78, 2); b[0x79BDF] = title.toByte()
        b[base] = 0xFF.toByte(); b[base + 1] = 0xA5.toByte(); b[base + 2] = 0x5A; b[base + 3] = 0xFF.toByte()
        repeat(99) { i ->
            val o = base + 4 + i * 0xAC; put(b, o, 0xFFFFFFFFL - i); put(b, o + 4, 0x80000000L + i); put(b, o + 8, i.toLong())
            repeat(20) { j -> val s = o + 12 + j * 8
                put(b, s, (i * 20 + j).toLong(), 2); put(b, s + 2, (Short.MIN_VALUE + j).toLong(), 2)
                put(b, s + 4, (Short.MAX_VALUE - i).toLong(), 2); put(b, s + 6, (j - 10).toLong(), 2)
            }
        }
        b[base - 1] = 0xA6.toByte(); b[base + 0x4288] = 0x6A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllRevisionsRetain99Capsules20AffixesAndExactBlockBoundaries() {
        for (layout in layouts.indices) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val read = BdspBallCapsulesReadOnly.read(b)
            assertEquals(title, read.version.romCode); assertEquals(layouts[layout].second.toLong(), read.revision.code)
            assertEquals(99, read.capsules.size); assertEquals(listOf(255, 165, 90, 255), read.rawHeaderBytes)
            for (i in 0 until 99) {
                val o = base + 4 + i * 0xAC; val capsule = read.capsule(i); assertEquals(i, capsule.index); assertSame(capsule, read.capsules[i])
                assertEquals(0xFFFFFFFFL - i, capsule.speciesId); assertEquals(0x80000000L + i, capsule.encryptionConstant); assertEquals(i.toLong(), capsule.unknownWord)
                assertEquals(20, capsule.seals.size); assertEquals(before.copyOfRange(o, o + 0xAC).map { it.toInt() and 255 }, capsule.rawBytes)
                for (j in 0 until 20) {
                    val s = o + 12 + j * 8; val affix = capsule.seal(j); assertEquals(j, affix.index); assertSame(affix, capsule.seals[j])
                    assertEquals(i * 20 + j, affix.sealId); assertEquals(Short.MIN_VALUE + j, affix.x); assertEquals(Short.MAX_VALUE - i, affix.y); assertEquals(j - 10, affix.z)
                    assertEquals(before.copyOfRange(s, s + 8).map { it.toInt() and 255 }, affix.rawBytes)
                }
            }
            assertEquals(before.copyOfRange(base, base + 0x4288).map { it.toInt() and 255 }, read.rawHeaderBytes + read.capsules.flatMap { it.rawBytes })
            assertEquals(0xA6, b[base - 1].toInt() and 255); assertEquals(0x6A, b[base + 0x4288].toInt() and 255); assertArrayEquals(before, b)
            for (bad in listOf(-1, 99, Int.MIN_VALUE, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { read.capsule(bad) }
            for (bad in listOf(-1, 20, Int.MIN_VALUE, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { read.capsule(0).seal(bad) }
        }
    }

    @Test fun unsignedWordsIdsAndSigned16CoordinatesPreserveExtremesAndEveryBit() {
        for (word in listOf(0L, 1L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL) + (0 until 32).map { 1L shl it }) {
            val b = fixture(); for (o in listOf(4, 8, 12)) put(b, base + o, word); seal(b); val before = b.copyOf(); val capsule = BdspBallCapsulesReadOnly.read(b).capsule(0)
            assertEquals(listOf(word, word, word), listOf(capsule.speciesId, capsule.encryptionConstant, capsule.unknownWord)); assertArrayEquals(before, b)
        }
        for (id in listOf(0, 1, 96, 97, 255, 256, 32767, 32768, 65535) + (0 until 16).map { 1 shl it }) {
            val b = fixture(); put(b, base + 16, id.toLong(), 2); seal(b); val affix = BdspBallCapsulesReadOnly.read(b).capsule(0).seal(0)
            assertEquals(id, affix.sealId); assertEquals(BdspSealStickerLabels.nativeLabel(id), affix.nativeLabel)
        }
        for (bits in listOf(0, 1, 0x7FFF, 0x8000, 0xFFFF) + (0 until 16).map { 1 shl it }) {
            val b = fixture(); for (o in listOf(18, 20, 22)) put(b, base + o, bits.toLong(), 2); seal(b)
            val affix = BdspBallCapsulesReadOnly.read(b).capsule(0).seal(0); assertEquals(List(3) { bits.toShort().toInt() }, listOf(affix.x, affix.y, affix.z))
        }
    }

    @Test fun countByteDoesNotHideRecordsAndNonzeroFilterKeepsOriginalIndices() {
        for (count in listOf(0, 1, 98, 99, 100, 127, 128, 255)) {
            val b = fixture(); b[base] = count.toByte(); seal(b); val read = BdspBallCapsulesReadOnly.read(b)
            assertEquals(count, read.savedCapsuleCount); assertEquals(count <= 99, read.countWithinCapacity); assertEquals(99, read.capsules.size)
            assertEquals(listOf(count, 165, 90, 255), read.rawHeaderBytes); assertEquals((0 until 99).toList(), read.filtered(false).map { it.index })
        }
        val b = fixture(); b.fill(0, base + 4, base + 0x4288); seal(b)
        assertTrue(BdspBallCapsulesReadOnly.read(b).filtered(true).isEmpty())
        // Unknown header word and final affix byte each independently retain a record; not an active/unlock predicate.
        put(b, base + 4 + 17 * 0xAC + 8, 1); b[base + 0x4288 - 1] = 0x80.toByte(); seal(b)
        val read = BdspBallCapsulesReadOnly.read(b)
        assertEquals(listOf(17, 98), read.filtered(true).map { it.index }); assertEquals(Short.MIN_VALUE.toInt(), read.capsule(98).seal(19).z)
        assertEquals("HEART_A", BdspSealStickerLabels.nativeLabel(1)); assertEquals("CHAMPION_A", BdspSealStickerLabels.nativeLabel(96)); assertNull(read.capsule(0).seal(0).nativeLabel)
    }

    @Test fun wholeFileIntegrityExactLengthRevisionAndTitlePrecedeEveryNativeField() {
        for (layout in layouts.indices) {
            val b = fixture(layout)
            for (o in setOf(0, 4, base - 1, base, base + 1, base + 3, base + 4, base + 7, base + 8, base + 12, base + 16, base + 18, base + 20, base + 22, base + 0x4287, base + 0x4288, 0xE9818, b.lastIndex)) {
                val bad = b.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspBallCapsulesReadOnly.read(bad) }; assertNull(session(bad).bdspBallCapsules); assertArrayEquals(before, bad)
            }
            val title = b.copyOf(); title[0x79BDF] = 2; seal(title)
            val revision = b.copyOf(); put(revision, 0, 0x35); seal(revision)
            for (bad in listOf(title, revision, b.copyOf(b.size - 1), b.copyOf(b.size + 1))) assertThrows(IllegalArgumentException::class.java) { BdspBallCapsulesReadOnly.read(bad) }
        }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4))) assertThrows(IllegalArgumentException::class.java) { BdspBallCapsulesReadOnly.read(bad) }
    }

    @Test fun independentCapsuleReaderDoesNotRelaxTrainerPartyOrDexGates() {
        val cases = listOf(fixture().also { put(it, 0x79B78, 6) }, fixture().also { put(it, 0x79BD4, 1000000) },
            fixture().also { it[0x79BD8] = 2 }, fixture().also { it[0x79BDD] = 9 }, fixture().also { it[0x79BE0] = 2 },
            fixture().also { it[0x79BB4] = 0; it[0x79BB5] = 0xD8.toByte() })
        for (b in cases) {
            b[0x14098 + 6 * 344] = 7; put(b, 0x7A328, 4); seal(b); val before = b.copyOf(); val bound = session(b)
            assertTrue(bound.isFormatVerified); assertNull(bound.bdspTrainer); assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex); assertNotNull(bound.bdspBallCapsules); assertNull(bound.bdspBallCapsulesUnavailableReason)
            assertEquals(99, bound.bdspBallCapsules!!.capsules.size); assertNotNull(bound.bdspSealStickers); assertArrayEquals(before, b)
        }
    }

    @Test fun immutableDetachedNativeRecordsPreserveEarlierViewsSourceIdentityAndWriteLocks() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val b = fixture(title = title); val before = b.copyOf(); val bound = session(b, hint); val read = bound.bdspBallCapsules!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family); assertEquals(title, read.version.romCode)
            b.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy()); assertEquals(0xFFFFFFFFL, read.capsule(0).speciesId)
            for (list in listOf(read.capsules, read.rawHeaderBytes, read.capsule(98).seals, read.capsule(98).rawBytes, read.capsule(98).seal(19).rawBytes, read.filtered(true)))
                assertThrows(UnsupportedOperationException::class.java) { (list as MutableList<*>).clear() }
            assertNotNull(bound.bdspTrainer); assertNotNull(bound.bdspInventory); assertNotNull(bound.bdspPoffins); assertNotNull(bound.bdspUnderground)
            assertNotNull(bound.bdspSealStickers); assertNotNull(bound.bdspUndergroundReturn); assertNotNull(bound.bdspSecretBase); assertNotNull(bound.bdspBerryPlots)
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
        }
        val other = session(byteArrayOf(1)); assertNull(other.bdspBallCapsules); assertNull(other.bdspBallCapsulesUnavailableReason)
    }
}
