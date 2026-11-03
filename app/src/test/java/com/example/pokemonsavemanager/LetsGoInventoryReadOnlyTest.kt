package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class LetsGoInventoryReadOnlyTest {
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


    private val layouts = listOf(0x0000 to 60, 0x00F0 to 108, 0x02A0 to 200, 0x05C0 to 150,
        0x0818 to 50, 0x08E0 to 150, 0x0B38 to 150)
    private fun packed(id: Int, count: Int, fresh: Boolean = false): Long = id.toLong() or (count.toLong() shl 15) or (if (fresh) 0x40000000L else 0L)
    private fun populated(game: Int = 42): ByteArray = fixture(game).also { b ->
        for ((p, id) in listOf(0 to 17, 1 to 328, 2 to 1057, 3 to 51, 4 to 4, 5 to 55, 6 to 113))
            put32(b, layouts[p].first, packed(id, if (p == 1 || p == 6) 1 else 999, p == 0 || p == 4))
        val ends = listOf(903 to 1023, 387 to 2, 50 to 0, 849 to 999, 866 to 1, 659 to 999, 121 to 1)
        for ((p, item) in ends.withIndex()) put32(b, layouts[p].first + (layouts[p].second - 1) * 4, packed(item.first, item.second))
        put32(b, 4, packed(17, 1)) // Duplicate medicine ID stays in its own native slot.
        put32(b, 0x08E4, packed(656, 1))
        put32(b, 0x0B3C, packed(76, 12)); put32(b, 0x0B40, packed(113, 0))
        put32(b, 0x0B44, 0xFFFFFFFFL); put32(b, 0x0B48, 0x40000000L); put32(b, 0x0B4C, 0x80000000L)
        seal(b)
    }

    @Test fun bothTitlesExposeAllSevenNativePocketsAndFirstLastSlotBoundaries() {
        for (game in listOf(42, 43)) {
            val b = populated(game); val before = b.copyOf(); val bag = LetsGoInventoryReadOnly.read(b)
            if (game == 42) assertEquals("1e08b9560de8034f50fcf2bb376cba31dc945e1e6219aa566eabd2eaf50f87c4", MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it.toInt() and 255) })
            assertEquals(game, bag.version.gameId); assertEquals(7, bag.pockets.size); assertEquals(868, bag.pockets.sumOf { it.slots.size }); assertEquals(21, bag.recordedCount)
            assertEquals(listOf("Medicine Pocket", "TM Case", "Candy Jar", "Power-Up Pocket", "Catching Pocket", "Battle Pocket", "Items and Key Items"), bag.pockets.map { it.pocket.displayName })
            for ((i, p) in bag.pockets.withIndex()) {
                assertEquals(layouts[i].first, p.pocket.offset); assertEquals(layouts[i].second, p.slots.size)
                assertEquals((1..layouts[i].second).toList(), p.slots.map { it.slot }); assertFalse(p.slot(1).isEmpty); assertFalse(p.slot(p.slots.size).isEmpty)
            }
            assertEquals("Potion", bag.pocket(LetsGoInventoryPocket.MEDICINE).slot(1).itemName)
            assertEquals("TM01", bag.pocket(LetsGoInventoryPocket.TMS).slot(1).itemName); assertEquals("TM60", bag.pocket(LetsGoInventoryPocket.TMS).slot(108).itemName)
            assertEquals("Magmar Candy", bag.pocket(LetsGoInventoryPocket.CANDY).slot(1).itemName); assertEquals("PP Up", bag.pocket(LetsGoInventoryPocket.POWER_UP).slot(1).itemName)
            assertEquals("Gengarite", bag.pocket(LetsGoInventoryPocket.BATTLE).slot(2).itemName); assertEquals("Pokémon Box", bag.pocket(LetsGoInventoryPocket.ITEMS).slot(150).itemName)
            assertArrayEquals(before, b)
        }
    }

    @Test fun everyPackedBitMatchesTheNativeGetterMasksAndReservedBitsStayRaw() {
        for (bit in 0..31) {
            val b = fixture(); val raw = 1L shl bit; put32(b, 0, raw); seal(b)
            val e = LetsGoInventoryReadOnly.read(b).pocket(LetsGoInventoryPocket.MEDICINE).slot(1)
            assertEquals(raw, e.rawValue); assertEquals(if (bit in 0..10) 1 shl bit else 0, e.itemId)
            assertEquals(if (bit in 15..24) 1 shl (bit - 15) else 0, e.count); assertEquals(bit == 30, e.isNew)
            assertEquals(if (bit in 0..14) 1 shl bit else 0, e.rawItemField); assertEquals(if (bit in 15..29) 1 shl (bit - 15) else 0, e.rawCountField)
            assertEquals(if (bit in 11..14 || bit in 25..29 || bit == 31) raw else 0L, e.unknownBits)
        }
        val e = LetsGoInventoryReadOnly.read(populated()).pocket(LetsGoInventoryPocket.ITEMS).slot(4)
        assertEquals(0xFFFFFFFFL, e.rawValue); assertEquals(2047, e.itemId); assertEquals(1023, e.count); assertEquals(32767, e.rawItemField); assertEquals(32767, e.rawCountField)
        assertTrue(e.isNew); assertEquals(0xBE007800L, e.unknownBits); assertEquals("Item #2047", e.itemName); assertNull(e.referenceMaxCount); assertFalse(e.listedInPocket)
    }

    @Test fun nativeCatalogueNamesAndMixedPocketReferenceMaximaAreDescriptiveOnly() {
        val ids = listOf(1, 2, 3, 4, 12, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 38, 39, 40, 41, 50, 51, 53, 55, 56, 57, 58, 59, 60, 61, 62, 76, 77, 78, 79, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 101, 102, 103, 113, 115, 121, 122, 123, 124, 125, 126, 127, 128, 164, 166, 168, 328, 329, 330, 331, 332, 333, 334, 335, 336, 337, 338, 339, 340, 341, 342, 343, 344, 345, 346, 347, 348, 349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 361, 362, 363, 364, 365, 366, 367, 368, 369, 370, 371, 372, 373, 374, 375, 376, 377, 378, 379, 380, 381, 382, 383, 384, 385, 386, 387, 442, 571, 632, 651, 656, 659, 660, 661, 662, 663, 671, 672, 675, 676, 678, 679, 709, 760, 762, 770, 773, 795, 796, 849, 861, 862, 863, 864, 865, 866, 872, 873, 874, 875, 876, 877, 878, 885, 886, 887, 888, 889, 890, 891, 892, 893, 894, 895, 896, 900, 901, 902, 903, 960, 961, 962, 963, 964, 965, 966, 967, 968, 969, 970, 971, 972, 973, 974, 975, 976, 977, 978, 979, 980, 981, 982, 983, 984, 985, 986, 987, 988, 989, 990, 991, 992, 993, 994, 995, 996, 997, 998, 999, 1000, 1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011, 1012, 1013, 1014, 1015, 1016, 1017, 1018, 1019, 1020, 1021, 1022, 1023, 1024, 1025, 1026, 1027, 1028, 1029, 1030, 1031, 1032, 1033, 1034, 1035, 1036, 1037, 1038, 1039, 1040, 1041, 1042, 1043, 1044, 1045, 1046, 1047, 1048, 1049, 1050, 1051, 1052, 1053, 1054, 1055, 1056, 1057)
        val labels = ids.joinToString("\n") { "$it:${LetsGoItemNames.name(it)}" }
        assertEquals(280, ids.size); assertEquals("1a036087fc3478fcfc168002e60327d6f30a28263c3e567d784de19bf00e8156", MessageDigest.getInstance("SHA-256").digest(labels.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) })
        assertEquals("Empty", LetsGoItemNames.name(0)); assertEquals("Item #2047", LetsGoItemNames.name(2047))
        val bag = LetsGoInventoryReadOnly.read(populated())
        assertEquals(999, bag.pocket(LetsGoInventoryPocket.MEDICINE).slot(60).referenceMaxCount); assertEquals(1023, bag.pocket(LetsGoInventoryPocket.MEDICINE).slot(60).count)
        assertTrue(bag.pocket(LetsGoInventoryPocket.MEDICINE).slot(60).exceedsReferenceMax)
        assertEquals(1, bag.pocket(LetsGoInventoryPocket.TMS).slot(108).referenceMaxCount); assertTrue(bag.pocket(LetsGoInventoryPocket.TMS).slot(108).exceedsReferenceMax)
        assertEquals(999, bag.pocket(LetsGoInventoryPocket.BATTLE).slot(1).referenceMaxCount); assertEquals(1, bag.pocket(LetsGoInventoryPocket.BATTLE).slot(2).referenceMaxCount)
        assertTrue(bag.pocket(LetsGoInventoryPocket.BATTLE).slot(150).exceedsReferenceMax)
        assertEquals(1, bag.pocket(LetsGoInventoryPocket.ITEMS).slot(1).referenceMaxCount); assertEquals(999, bag.pocket(LetsGoInventoryPocket.ITEMS).slot(2).referenceMaxCount)
        val b = fixture(); put32(b, 0, packed(328, 1)); seal(b)
        val wrong = LetsGoInventoryReadOnly.read(b).pocket(LetsGoInventoryPocket.MEDICINE).slot(1)
        assertEquals("TM01", wrong.itemName); assertFalse(wrong.listedInPocket); assertNull(wrong.referenceMaxCount) // No repair, rejection or legality claim.
    }

    @Test fun emptyResidualDuplicateAndZeroCountSlotsKeepNativeOrderAndBounds() {
        val empty = LetsGoInventoryReadOnly.read(fixture()); assertEquals(0, empty.recordedCount); assertTrue(empty.pockets.all { it.recordedSlots.isEmpty() })
        val bag = LetsGoInventoryReadOnly.read(populated()); val medicine = bag.pocket(LetsGoInventoryPocket.MEDICINE)
        assertEquals(listOf(1, 2, 60), medicine.recordedSlots.map { it.slot }); assertEquals(listOf(17, 17, 903), medicine.recordedSlots.map { it.itemId })
        val p = bag.pocket(LetsGoInventoryPocket.ITEMS); assertEquals(listOf(1, 2, 3, 4, 5, 6, 150), p.recordedSlots.map { it.slot })
        assertEquals(0, p.slot(3).count); assertFalse(p.slot(3).isEmpty); assertEquals(0, p.slot(5).itemId); assertTrue(p.slot(5).isNew); assertFalse(p.slot(6).isNew)
        assertEquals(0x80000000L, p.slot(6).unknownBits); assertTrue(p.slot(7).isEmpty)
        for (pocket in bag.pockets) for (n in listOf(-1, 0, pocket.slots.size + 1, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { pocket.slot(n) }
    }

    @Test fun completeNativeBlockTitleFooterDescriptorAndAllTwentyOneCrcGatesPrecedeReads() {
        val good = populated(); val invalid = mutableListOf(ByteArray(1), good.copyOf(0x100001), good.copyOf().also { it[0xB8610] = 0 })
        for ((offset, _) in regions) invalid.add(good.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() })
        for (descriptor in listOf(0, 20)) invalid.add(good.copyOf().also { it[0xB8614 + descriptor * 8 + 4] = 255.toByte() })
        invalid.add(fixture(44)); invalid.add(good.copyOf().also { it[0x1004] = 0; seal(it) })
        for (b in invalid) {
            val before = b.copyOf(); assertThrows(IllegalArgumentException::class.java) { LetsGoInventoryReadOnly.read(b) }
            val bound = session(b); assertNull(bound.letsGoInventory); assertFalse(bound.supports(SwitchReadCapability.INVENTORY)); assertFalse(bound.profile.writesEnabled); assertArrayEquals(before, b)
        }
    }

    @Test fun snapshotAndEveryInventoryListAreDetachedAndUnmodifiable() {
        val b = populated(); val bound = session(b); val bag = bound.letsGoInventory!!; b.fill(0); bound.snapshot.bytesCopy().fill(0)
        assertEquals(21, bag.recordedCount); assertEquals(999, bag.pockets[0].slot(1).count); assertTrue(bag.pockets[0].slot(1).isNew)
        assertThrows(UnsupportedOperationException::class.java) { (bag.pockets as MutableList<LetsGoInventoryPocketRead>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.pockets[0].slots as MutableList<LetsGoInventorySlotRead>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.pockets[0].recordedSlots as MutableList<LetsGoInventorySlotRead>).clear() }
        assertEquals(868, bag.pockets.sumOf { it.slots.size }); assertEquals(21, bag.recordedCount)
    }

    @Test fun invalidTrainerStorageAndDexMetadataNeverHideAnIndependentInventory() {
        val b = populated(); b[0x1035] = 6; put16(b, 0x5A0E, 1001); put32(b, 0x2A00, 0); seal(b)
        val before = b.copyOf(); val bound = session(b)
        assertTrue(bound.isFormatVerified); assertNull(bound.letsGoTrainer); assertNull(bound.letsGoStorage); assertNull(bound.letsGoPokedex)
        assertNotNull(bound.trainerUnavailableReason); assertNotNull(bound.storageUnavailableReason); assertNotNull(bound.pokedexUnavailableReason)
        assertNotNull(bound.letsGoInventory); assertNull(bound.inventoryUnavailableReason); assertEquals(21, bound.letsGoInventory!!.recordedCount)
        assertTrue(bound.supports(SwitchReadCapability.INVENTORY)); assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertFalse(bound.supports(SwitchReadCapability.PARTY)); assertFalse(bound.supports(SwitchReadCapability.POKEDEX))
        assertFalse(bound.profile.writesEnabled); assertArrayEquals(before, b)
    }

    @Test fun bothNativeTitlesOverrideAllFamilyHintsAndNeverGrantOtherReadersOrWrites() {
        for (game in listOf(42, 43)) for (hint in SwitchGameFamily.values()) {
            val bound = session(populated(game), hint)
            assertEquals(SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, bound.profile.family); assertTrue(bound.isFormatVerified); assertTrue(bound.isReadOnly)
            assertNotNull(bound.letsGoTrainer); assertNotNull(bound.letsGoStorage); assertNotNull(bound.letsGoPokedex); assertNotNull(bound.letsGoInventory)
            assertNull(bound.swordShieldStorage); assertNull(bound.bdspStorage); assertNull(bound.legendsArceusStorage); assertNull(bound.scarletVioletStorage); assertNull(bound.legendsZaStorage)
            for (c in SwitchReadCapability.values()) assertEquals(c in setOf(SwitchReadCapability.SAVE_IDENTITY, SwitchReadCapability.TRAINER_PROFILE,
                SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS, SwitchReadCapability.POKEDEX, SwitchReadCapability.INVENTORY), bound.supports(c))
            assertFalse(bound.supports(SwitchReadCapability.POKEDEX_AND_PROGRESS)); assertFalse(bound.supports(SwitchReadCapability.PSE_BANK_READ_ONLY)); assertFalse(bound.profile.writesEnabled)
        }
        val unknown = session(byteArrayOf(42)); assertNull(unknown.letsGoInventory); assertFalse(unknown.supports(SwitchReadCapability.INVENTORY))
    }
}
