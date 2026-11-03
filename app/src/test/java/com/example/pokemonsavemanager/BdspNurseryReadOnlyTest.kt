package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspNurseryReadOnlyTest {
    private val nativeOrder = listOf("0123", "0132", "0213", "0312", "0231", "0321", "1023", "1032",
        "2013", "3012", "2031", "3021", "1203", "1302", "2103", "3102", "2301", "3201",
        "1230", "1320", "2130", "3120", "2310", "3210")
    private fun word(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }
    private fun uint(bytes: ByteArray, offset: Int, value: Long) { repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() } }
    private fun label(bytes: ByteArray, offset: Int, text: String) { text.toByteArray(Charsets.UTF_16LE).copyInto(bytes, offset) }
    private fun checksum(bytes: ByteArray) {
        var value = 0
        for (i in 8 until 0x148 step 2) value += (bytes[i].toInt() and 255) + ((bytes[i + 1].toInt() and 255) shl 8)
        word(bytes, 6, value and 65535)
    }
    private fun plain(ec: Long = 0xF003E001L, species: Int = 25): ByteArray = ByteArray(344).also { b ->
        uint(b, 0, ec); word(b, 8, species); word(b, 0x0A, 1589)
        word(b, 0x0C, 0xBEEF); word(b, 0x0E, 0xCAFE); uint(b, 0x10, 0xFEDCBA98)
        word(b, 0x14, 267); b[0x16] = 0; uint(b, 0x1C, (0x1234L shl 16) or (0x1234 xor 0xBEEF xor 0xCAFE).toLong())
        b[0x20] = 24; b[0x21] = 3; b[0x22] = 8; b[0x24] = 7
        listOf(252, 1, 2, 3, 0, 252).forEachIndexed { i, v -> b[0x26 + i] = v.toByte() }
        label(b, 0x58, "Éclair ♀"); label(b, 0xF8, "雪")
        repeat(4) { word(b, 0x72 + 2 * it, 800 + it); b[0x7A + it] = (10 + it).toByte(); b[0x7E + it] = it.toByte(); word(b, 0x82 + 2 * it, 500 + it) }
        word(b, 0x8A, 77); uint(b, 0x8C, listOf(31, 0, 1, 2, 3, 30).mapIndexed { i, v -> v.toLong() shl (5 * i) }.fold(0xC0000000L) { a, v -> a or v })
        listOf(255, 1, 2, 3, 4, 5).forEachIndexed { i, v -> b[0x2C + i] = v.toByte() }
        b[0x112] = 200.toByte(); b[0xC8] = 123; b[0xC4] = 1
        b[0x148] = 67; word(b, 0x14A, 188)
        repeat(5) { word(b, 0x14C + 2 * it, 100 + it) }
        checksum(b)
    }
    /** Test serializer maps canonical blocks back into native positions, then encrypts two independent streams. */
    private fun wire(plain: ByteArray): ByteArray {
        val output = plain.copyOf()
        val ec = (0..3).fold(0L) { a, i -> a or ((plain[i].toLong() and 255) shl (8 * i)) }
        val order = nativeOrder[((ec ushr 13).toInt() and 31) % 24]
        repeat(4) { canonical -> plain.copyInto(output, 8 + (order[canonical] - '0') * 80, 8 + canonical * 80, 8 + (canonical + 1) * 80) }
        for (range in listOf(8 until 0x148 step 2, 0x148 until 344 step 2)) {
            var seed = ec
            for (i in range) {
                seed = (seed * 1103515245L + 24691L) and 0xFFFFFFFFL
                output[i] = (output[i].toInt() xor (seed ushr 16).toInt()).toByte()
                output[i + 1] = (output[i + 1].toInt() xor (seed ushr 24).toInt()).toByte()
            }
        }
        return output
    }
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun fixture(layout: Int = 0, title: Int = 0, first: ByteArray = wire(plain()),
        second: ByteArray = wire(plain(species = 493)), flag: Long = 1,
        seed: ULong = 0xFEDCBA9876543210uL, steps: Long = 0xFFFFFFFFL): ByteArray = ByteArray(layouts[layout].first).also { b ->
        uint(b, 0, layouts[layout].second.toLong()); b[0x79BDF] = title.toByte()
        first.copyInto(b, 0x96080); second.copyInto(b, 0x961D8)
        uint(b, 0x96330, flag); repeat(8) { b[0x96334 + it] = (seed shr (8 * it)).toByte() }; uint(b, 0x9633C, steps)
        b[0x9607F] = 0xA5.toByte(); b[0x96340] = 0x5A; seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.SWORD_SHIELD): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesFourRevisionsExposeExactlyTwoIndependentParentsAndAllMetadataBits() {
        for (layout in layouts.indices) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val nursery = BdspNurseryReadOnly.read(b)
            assertEquals(title, nursery.version.romCode); assertEquals(layouts[layout].second.toLong(), nursery.revision.code)
            assertEquals(listOf(1, 2), nursery.parents.map { it.position }); assertEquals(2, nursery.decodedParentCount); assertEquals(0, nursery.unreadableParentCount)
            assertEquals(25, (nursery.parent(0).read as BdspSlotRead.Pokemon).value.species)
            assertEquals(493, (nursery.parent(1).read as BdspSlotRead.Pokemon).value.species)
            assertEquals("Éclair ♀", (nursery.parent(0).read as BdspSlotRead.Pokemon).value.nickname)
            assertEquals(1L, nursery.rawEggAvailableFlag); assertTrue(nursery.isEggAvailable); assertTrue(nursery.hasCanonicalEggFlag)
            assertEquals(0xFEDCBA9876543210uL, nursery.eggSeed); assertEquals("FEDCBA9876543210", nursery.eggSeedHex)
            assertEquals("18364758544493064720", nursery.eggSeed.toString()); assertEquals(-1, nursery.eggStepCount)
            assertEquals(0xFFFFFFFFL, nursery.rawEggStepCount); assertArrayEquals(before, b)
            assertEquals(0xA5, b[0x9607F].toInt() and 255); assertEquals(0x5A, b[0x96340].toInt() and 255)
        }
    }

    @Test fun completeFlagWordsUnsigned64SeedsAndSignedStepCountersNeverNormalize() {
        val flags = listOf(0L, 1L, 2L, 0x1000001L, 0x80000001L, 0xFFFFFFFFL)
        val seeds = listOf(0uL, 1uL, 0x7FFFFFFFFFFFFFFFuL, 0x8000000000000000uL, 0xFFFFFFFF80000000uL, 0xFFFFFFFFFFFFFFFFuL)
        val steps = listOf(0L, 1L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)
        for (flag in flags) for (seed in seeds) {
            val b = fixture(flag = flag, seed = seed, steps = steps[(flag % steps.size).toInt()]); val before = b.copyOf()
            val nursery = BdspNurseryReadOnly.read(b)
            assertEquals(flag, nursery.rawEggAvailableFlag); assertEquals(flag == 1L, nursery.isEggAvailable)
            assertEquals(flag in 0L..1L, nursery.hasCanonicalEggFlag); assertEquals(seed, nursery.eggSeed)
            assertEquals(seed.toString(16).uppercase().padStart(16, '0'), nursery.eggSeedHex)
            assertEquals(nursery.rawEggStepCount.toInt(), nursery.eggStepCount); assertArrayEquals(before, b)
        }
        for (word in steps) {
            val nursery = BdspNurseryReadOnly.read(fixture(steps = word)); assertEquals(word, nursery.rawEggStepCount); assertEquals(word.toInt(), nursery.eggStepCount)
        }
    }

    @Test fun allNativeShuffleSelectorsDecodeBothSlotsWithoutPartyCountDependencies() {
        for (selector in 0..31) for (high in listOf(0L, 0x80000000L)) {
            val a = wire(plain(ec = high or (selector.toLong() shl 13) or 1, species = 25))
            val b = wire(plain(ec = high or (((31 - selector).toLong()) shl 13) or 1, species = 493))
            val source = fixture(first = a, second = b); source[0x14098 + 6 * 344] = 255.toByte(); seal(source)
            val nursery = BdspNurseryReadOnly.read(source)
            val p = (nursery.parent(0).read as BdspSlotRead.Pokemon).value
            assertEquals(25, p.species); assertEquals("雪", p.originalTrainer); assertEquals(67, p.cachedLevel)
            assertEquals(493, (nursery.parent(1).read as BdspSlotRead.Pokemon).value.species)
            assertEquals(0xFEDCBA9876543210uL, nursery.eggSeed)
        }
    }

    @Test fun emptyResidualPlaintextUnsupportedAndCorruptParentsRemainIsolatedFromEggMetadata() {
        val good = wire(plain()); val bad = good.copyOf().also { it[80] = (it[80].toInt() xor 1).toByte() }
        for ((first, expected) in listOf(ByteArray(344) to 0, wire(plain(species = 0)) to 0,
            plain() to 1, wire(plain(species = 494)) to 1, bad to 1)) {
            val source = fixture(first = first); val before = source.copyOf(); val nursery = BdspNurseryReadOnly.read(source)
            if (expected == 0) assertTrue(nursery.parent(0).read is BdspSlotRead.Empty) else assertTrue(nursery.parent(0).read is BdspSlotRead.Invalid)
            assertTrue(nursery.parent(1).read is BdspSlotRead.Pokemon); assertEquals(1, nursery.decodedParentCount)
            assertEquals(expected, nursery.unreadableParentCount); assertTrue(nursery.isEggAvailable); assertEquals(-1, nursery.eggStepCount); assertArrayEquals(before, source)
        }
        val empty = BdspNurseryReadOnly.read(fixture(first = ByteArray(344), second = ByteArray(344), flag = 0))
        assertEquals(0, empty.decodedParentCount); assertEquals(0, empty.unreadableParentCount); assertFalse(empty.isEggAvailable)
        val unreadable = BdspNurseryReadOnly.read(fixture(first = bad, second = bad))
        assertEquals(0, unreadable.decodedParentCount); assertEquals(2, unreadable.unreadableParentCount); assertTrue(unreadable.isEggAvailable)
    }

    @Test fun existingWholeFileIntegrityRevisionTitleAndLengthGatesPrecedeAllNurseryData() {
        for (layout in layouts.indices) {
            val good = fixture(layout)
            for (o in setOf(0, 4, 0x9607F, 0x96080, 0x961D7, 0x961D8, 0x9632F, 0x96330, 0x96334, 0x9633F, 0x96340, 0x79BDF, 0xE9818, good.lastIndex)) {
                val bad = good.copyOf(); bad[o] = (bad[o].toInt() xor 1).toByte(); val before = bad.copyOf()
                assertThrows(IllegalArgumentException::class.java) { BdspNurseryReadOnly.read(bad) }
                assertNull(session(bad).bdspNursery); assertArrayEquals(before, bad)
            }
            val wrongTitle = good.copyOf(); wrongTitle[0x79BDF] = 2; seal(wrongTitle)
            val wrongRevision = good.copyOf(); uint(wrongRevision, 0, 0x35); seal(wrongRevision)
            for (bad in listOf(wrongTitle, wrongRevision, good.copyOf(good.size - 1), good.copyOf(good.size + 1)))
                assertThrows(IllegalArgumentException::class.java) { BdspNurseryReadOnly.read(bad) }
        }
        for (b in listOf(ByteArray(0), ByteArray(1), ByteArray(0xEF0A4))) assertThrows(IllegalArgumentException::class.java) { BdspNurseryReadOnly.read(b) }
    }

    @Test fun nativeIdentityIndependentViewsDetachedListsAndExistingWriteLocksArePreserved() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val source = fixture(title = title); uint(source, 0x79B78, 0); source[0x14098 + 6 * 344] = 7; uint(source, 0x7A328, 4); seal(source)
            val before = source.copyOf(); val bound = session(source, hint); val nursery = bound.bdspNursery!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family)
            assertTrue(bound.isFormatVerified); assertNull(bound.bdspNurseryUnavailableReason); assertNull(bound.bdspTrainer)
            assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex); assertNotNull(bound.bdspInventory); assertNotNull(bound.bdspUnderground)
            source.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy())
            assertEquals(2, nursery.decodedParentCount); assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
            assertThrows(UnsupportedOperationException::class.java) { (nursery.parents as MutableList<*>).clear() }
            val parent = (nursery.parent(0).read as BdspSlotRead.Pokemon).value
            assertThrows(UnsupportedOperationException::class.java) { (parent.moves as MutableList<*>).clear() }
            for (i in listOf(-1, 2, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { nursery.parent(i) }
        }
        val unrecognized = session(byteArrayOf(1)); assertNull(unrecognized.bdspNursery); assertNull(unrecognized.bdspNurseryUnavailableReason)
    }
}
