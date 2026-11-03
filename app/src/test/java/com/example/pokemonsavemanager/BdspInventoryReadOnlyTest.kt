package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspInventoryReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val bagOffset = 0x0563C
    private fun put16(b: ByteArray, offset: Int, value: Int) { b[offset] = value.toByte(); b[offset + 1] = (value ushr 8).toByte() }
    private fun put32(b: ByteArray, offset: Int, value: Long) { repeat(4) { b[offset + it] = (value ushr (8 * it)).toByte() } }
    private fun seal(b: ByteArray) { b.fill(0, 0xE9818, 0xE9828); MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818) }
    private fun record(b: ByteArray, id: Int, count: Long, seen: Long = 0, favorite: Long = 0, order: Int = 1, alignment: Int = 0) {
        val o = bagOffset + id * 16; put32(b, o, count); put32(b, o + 4, seen); put32(b, o + 8, favorite)
        put16(b, o + 12, order); put16(b, o + 14, alignment)
    }
    private fun fixture(layout: Int = 0, title: Int = 0, populated: Boolean = true): ByteArray = ByteArray(layouts[layout].first).also { b ->
        put32(b, 0, layouts[layout].second.toLong()); b[0x79BDF] = title.toByte(); b[0x79BD8] = 1; put32(b, 0x79B78, 2)
        if (populated) {
            record(b, 45, Int.MAX_VALUE.toLong(), favorite = 1, order = 3, alignment = 65535)
            record(b, 46, 0xFFFFFFFFL, seen = 2, favorite = 2, order = 1)
            record(b, 47, 0, seen = 1, order = 1)
            record(b, 428, 2, favorite = 1, order = 2)
            record(b, 420, 999, seen = 1, favorite = 1, order = 65535)
            record(b, 427, 1000, seen = 0xFFFFFFFFL, favorite = 0xFFFFFFFFL, order = 0, alignment = 7)
            record(b, 17, 5); record(b, 4, 999); record(b, 55, 0x80000000L)
            record(b, 149, 1); record(b, 1808, 3)
            record(b, 0, 0, order = 0, alignment = 1)
            record(b, 2999, 0xFFFFFFFFL, favorite = 1, order = 65535, alignment = 0xABCD)
        }
        seal(b)
    }
    private fun session(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.SWORD_SHIELD): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, b))

    @Test fun bothTitlesAllFourRevisionsExposeAll3000IndicesEightPocketsAndNativeFields() {
        for (layout in layouts.indices) for (title in 0..1) {
            val b = fixture(layout, title); val before = b.copyOf(); val bag = BdspInventoryReadOnly.read(b)
            if (layout == 0 && title == 0) assertEquals("f3b24d10958d03161a29901f7498168f3551ad7d20d600fae7dc299d300b5a96", MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it.toInt() and 255) })
            assertEquals(title, bag.version.romCode); assertEquals(layouts[layout].second.toLong(), bag.revision.code)
            assertEquals((0 until 3000).toList(), bag.slots.map { it.itemId }); assertEquals(8, bag.pockets.size); assertEquals(472, bag.pockets.sumOf { it.slots.size })
            assertEquals(13, bag.recordedCount); assertEquals(listOf(0, 2999), bag.unlistedRecords.map { it.itemId })
            assertEquals(BdspInventoryPocket.TREASURE, bag.slot(1808).pocket); assertEquals("Mysterious Shard S", bag.slot(1808).itemName)
            assertEquals(0xFFFFFFFFL, bag.slot(2999).rawCount); assertEquals(-1, bag.slot(2999).count); assertEquals(0xABCD, bag.slot(2999).alignment)
            assertEquals(65535, bag.slot(2999).sortOrder); assertEquals("Item #2999", bag.slot(2999).itemName); assertArrayEquals(before, b)
        }
    }

    @Test fun fullWidthFlagEqualitySignedCountsAndAlignmentAreNeverNormalized() {
        val raw = listOf(0L, 1L, 2L, 0x1000001L, 0x80000001L, 0xFFFFFFFFL)
        val b = fixture(populated = false)
        for ((i, word) in raw.withIndex()) record(b, 17 + i, word, word, word, 65535 - i, 0x8000 + i)
        seal(b); val bag = BdspInventoryReadOnly.read(b)
        for ((i, word) in raw.withIndex()) {
            val e = bag.slot(17 + i); assertEquals(word.toInt(), e.count); assertEquals(word, e.rawCount)
            assertEquals(word, e.rawSeenFlag); assertEquals(word, e.rawFavoriteFlag)
            assertEquals(word == 0L, e.isNew); assertEquals(word == 1L, e.isFavorite)
            assertEquals(65535 - i, e.sortOrder); assertEquals(0x8000 + i, e.alignment); assertTrue(e.hasSortOrder); assertTrue(e.isRecorded)
        }
        val original = BdspInventoryReadOnly.read(fixture()); assertEquals(Int.MAX_VALUE, original.slot(45).count)
        assertEquals(Int.MIN_VALUE, original.slot(55).count); assertEquals(-1, original.slot(46).count)
        assertFalse(original.slot(46).isNew); assertFalse(original.slot(46).isFavorite); assertTrue(original.slot(46).hasUnusualMetadata)
    }

    @Test fun zeroRecordsResidualsDuplicateSortNumbersAndUnlistedIndicesRemainDistinct() {
        val empty = BdspInventoryReadOnly.read(fixture(populated = false))
        assertEquals(0, empty.recordedCount); assertTrue(empty.unlistedRecords.isEmpty()); assertTrue(empty.pockets.all { it.recordedSlots.isEmpty() })
        assertTrue(empty.slot(0).isNew); assertFalse(empty.slot(0).hasSortOrder); assertFalse(empty.slot(0).isRecorded)
        val bag = BdspInventoryReadOnly.read(fixture()); val other = bag.pocket(BdspInventoryPocket.ITEMS)
        assertEquals(listOf(46, 47, 45), other.recordedSlots.map { it.itemId }); assertEquals(listOf(1, 1, 3), other.recordedSlots.map { it.sortOrder })
        assertEquals(0, bag.slot(47).count); assertTrue(bag.slot(47).isRecorded)
        assertEquals(listOf(420, 427), bag.pocket(BdspInventoryPocket.MACHINES).recordedSlots.map { it.itemId })
        assertFalse(bag.slot(427).hasSortOrder); assertTrue(bag.slot(427).isRecorded)
        assertTrue(bag.slot(0).isRecorded); assertEquals(1, bag.slot(0).alignment); assertNull(bag.slot(0).pocket)
        for (id in listOf(-1, 3000, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { bag.slot(id) }
    }

    @Test fun everyNativeCatalogueNameAndBdspTmOverrideIsCorrectAndMaximaAreDescriptive() {
        val ids = (0 until 3000).filter { !BdspItemNames.name(it).startsWith("Item #") }
        val labels = ids.joinToString("\n") { "$it:${BdspItemNames.name(it)}" }
        assertEquals(472, ids.size); assertEquals("5ae01b36ae05e55cc30f5462bea2a8fa5b5318501fc07ca2c2ffb8ebb708e845", MessageDigest.getInstance("SHA-256").digest(labels.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) })
        assertEquals("TM92", BdspItemNames.name(419)); for (id in 420..427) assertEquals("TM${id - 420 + 93}", BdspItemNames.name(id))
        assertEquals("Item #618", BdspItemNames.name(618)); assertEquals("Item #0", BdspItemNames.name(0))
        val bag = BdspInventoryReadOnly.read(fixture()); assertEquals(1, bag.slot(428).referenceMaxCount); assertTrue(bag.slot(428).exceedsReferenceMax)
        assertEquals(999, bag.slot(420).referenceMaxCount); assertFalse(bag.slot(420).exceedsReferenceMax); assertTrue(bag.slot(427).exceedsReferenceMax)
        assertEquals(1000, bag.slot(427).count); assertNull(bag.slot(2999).referenceMaxCount)
    }

    @Test fun wholeFileMd5RevisionTitleAndEveryNativeSizeGatePrecedeBagReads() {
        for (layout in layouts.indices) {
            val good = fixture(layout)
            for (offset in setOf(0, 4, bagOffset - 1, bagOffset, bagOffset + 47999, bagOffset + 48000, 0x79BDF, 0xE9818, 0xE9827, good.lastIndex)) {
                val damaged = good.copyOf(); damaged[offset] = (damaged[offset].toInt() xor 1).toByte()
                val before = damaged.copyOf(); assertThrows(IllegalArgumentException::class.java) { BdspInventoryReadOnly.read(damaged) }
                val bound = session(damaged); assertNull(bound.bdspInventory); assertFalse(bound.supports(SwitchReadCapability.INVENTORY)); assertArrayEquals(before, damaged)
            }
            val badRevision = good.copyOf(); put32(badRevision, 0, 0x35); seal(badRevision)
            assertThrows(IllegalArgumentException::class.java) { BdspInventoryReadOnly.read(badRevision) }
            val badTitle = good.copyOf(); badTitle[0x79BDF] = 2; seal(badTitle)
            assertThrows(IllegalArgumentException::class.java) { BdspInventoryReadOnly.read(badTitle) }
            assertThrows(IllegalArgumentException::class.java) { BdspInventoryReadOnly.read(good.copyOf(good.size - 1)) }
        }
        for (b in listOf(ByteArray(1), ByteArray(0xEF0A4))) assertThrows(IllegalArgumentException::class.java) { BdspInventoryReadOnly.read(b) }
    }

    @Test fun snapshotFullArrayPocketAndRecordedListsAreDetachedAndUnmodifiable() {
        val source = fixture(); val before = source.copyOf(); val bound = session(source); val bag = bound.bdspInventory!!
        source.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy()); assertEquals(13, bag.recordedCount)
        assertThrows(UnsupportedOperationException::class.java) { (bag.slots as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.pockets as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.pockets[0].slots as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.pockets[0].recordedSlots as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.unlistedRecords as MutableList<*>).clear() }
        assertEquals(0, BdspInventoryReadOnly.read(fixture(populated = false)).recordedCount); assertEquals(13, bag.recordedCount)
    }

    @Test fun malformedTrainerPartyAndDexMetadataNeverHideAnIndependentBag() {
        val b = fixture(); put32(b, 0x79B78, 0); b[0x14098 + 6 * 344] = 7; put32(b, 0x7A328, 4); seal(b)
        val before = b.copyOf(); val bound = session(b)
        assertTrue(bound.isFormatVerified); assertNull(bound.bdspTrainer); assertNull(bound.bdspStorage); assertNull(bound.bdspPokedex)
        assertNotNull(bound.trainerUnavailableReason); assertNotNull(bound.storageUnavailableReason); assertNotNull(bound.pokedexUnavailableReason)
        assertNotNull(bound.bdspInventory); assertNull(bound.inventoryUnavailableReason); assertEquals(13, bound.bdspInventory!!.recordedCount)
        for (c in SwitchReadCapability.values()) assertEquals(c in setOf(SwitchReadCapability.SAVE_IDENTITY, SwitchReadCapability.INVENTORY), bound.supports(c))
        assertFalse(bound.profile.writesEnabled); assertArrayEquals(before, b)
    }

    @Test fun nativeTitlesOverrideEveryFamilyHintAndNeverGrantWritesOrOtherGameReaders() {
        for (title in 0..1) for (hint in SwitchGameFamily.values()) {
            val bound = session(fixture(title = title), hint); assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, bound.profile.family)
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled); assertNotNull(bound.bdspInventory); assertNull(bound.inventoryUnavailableReason)
            assertNull(bound.letsGoInventory); assertNull(bound.swordShieldInventory); assertNull(bound.letsGoStorage); assertNull(bound.swordShieldStorage)
            assertNull(bound.legendsArceusStorage); assertNull(bound.scarletVioletStorage); assertNull(bound.legendsZaStorage)
            for (c in SwitchReadCapability.values()) assertEquals(c in setOf(SwitchReadCapability.SAVE_IDENTITY, SwitchReadCapability.TRAINER_PROFILE,
                SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS, SwitchReadCapability.POKEDEX, SwitchReadCapability.INVENTORY), bound.supports(c))
        }
        val unknown = session(byteArrayOf(1)); assertNull(unknown.bdspInventory); assertFalse(unknown.supports(SwitchReadCapability.INVENTORY))
    }
}
