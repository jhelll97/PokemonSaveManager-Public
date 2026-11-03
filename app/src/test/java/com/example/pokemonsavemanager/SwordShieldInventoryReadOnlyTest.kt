package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class SwordShieldInventoryReadOnlyTest {
    private val intro = hex("9ec99cd70ed33c44fb9303dceb39b42a1947e9634ba2334416bf82a2ba6355b63d9df24b5f7b6ab2621dc21b68e5c8b53a059000e8a8103de2ecf00cb2ed4f6d")
    private val outro = hex("d6c01c598bc8b8cb46e153fc828c757513e045df32693c75f059f8d9a25fb217e08052dbea8973997579afcb2e8007e6f126e0030ae66ff641bf7e59c2ae55fd")
    private val pad = hex("a092d10607db32a1ae01f5c51e844fe353ca37f4a7b04da018b7c297da5f532b75fa4816f8d48a6f6105f4e2fd04b5a30ffc4492cb32e61bb9b12e01b0565336d2d1503dde5b2e0e52fddf2f7bca6350a4675d2317c052e1a6307c2bb670365b2a276933f5637b363f269ba3ed7a5300a448b3509e14a052de7e102b1b776e")
    private val sizes = listOf(0x1716B3, 0x17195E, 0x180B19, 0x180AD0, 0x1876B1, 0x187693, 0x187668, 0x18764A)
    private data class Block(val key: Long, val type: Int = 4, val data: ByteArray = byteArrayOf(),
        val declared: Long? = null, val subtype: Int = 8)
    private class ReferenceStream(seed: Long) {
        private var value = seed
        private var shift = 0
        init { repeat(java.lang.Long.bitCount(seed)) { advance() } }
        private fun advance() {
            value = (value xor (value shl 2)) and 0xFFFFFFFFL
            value = value xor (value ushr 15)
            value = (value xor (value shl 13)) and 0xFFFFFFFFL
        }
        fun next(): Int {
            val result = ((value ushr shift) and 255).toInt()
            shift += 8
            if (shift == 32) { shift = 0; advance() }
            return result
        }
        fun word(): Long = (0..3).fold(0L) { result, i -> result or (next().toLong() shl (8 * i)) }
    }
    private fun uint(out: ByteArrayOutputStream, value: Long) { repeat(4) { out.write((value ushr (8 * it)).toInt() and 255) } }
    private fun width(type: Int): Int = when (type) { 3, 8, 12 -> 1; 9, 13 -> 2; 10, 14, 16 -> 4; else -> 8 }
    private fun encode(block: Block): ByteArray {
        val output = ByteArrayOutputStream(); uint(output, block.key)
        val stream = ReferenceStream(block.key)
        output.write(block.type xor stream.next())
        if (block.type == 4) uint(output, (block.declared ?: block.data.size.toLong()) xor stream.word())
        if (block.type == 5) {
            uint(output, (block.declared ?: (block.data.size / width(block.subtype)).toLong()) xor stream.word())
            output.write(block.subtype xor stream.next())
        }
        for (byte in block.data) output.write((byte.toInt() and 255) xor stream.next())
        return output.toByteArray()
    }
    private fun core(game: Int): List<Block> = listOf(
        Block(0x0D66012C, data = ByteArray(0x50A00)),
        Block(0x2985FE5D, data = ByteArray(0x814)),
        Block(0x4716C404, data = ByteArray(0x4B00)),
        Block(0xF25C070E, data = ByteArray(0x110).also { it[0xA4] = game.toByte(); it[0xA5] = 1; it[0xA7] = 2 })
    )
    private fun sign(payload: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(intro); md.update(payload); md.update(outro)
        return payload + md.digest()
    }
    private fun fixture(game: Int = 44, size: Int = sizes[0], blocks: List<Block> = core(game), sort: Boolean = true): ByteArray {
        val stream = ByteArrayOutputStream()
        for (b in if (sort) blocks.sortedBy { it.key } else blocks) stream.write(encode(b))
        val paddingLength = size - 32 - stream.size() - 9
        require(paddingLength >= 0)
        stream.write(encode(Block(0xFFFFFFFFL, data = ByteArray(paddingLength))))
        val payload = stream.toByteArray()
        for (i in payload.indices) payload[i] = (payload[i].toInt() xor pad[i % pad.size].toInt()).toByte()
        return sign(payload)
    }
    private fun resign(bytes: ByteArray): ByteArray = sign(bytes.copyOf(bytes.size - 32))
    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private val itemKey = 0x1177C2C4L
    private val layouts = listOf(0x0000 to 60, 0x00F0 to 30, 0x0168 to 20, 0x01B8 to 80,
        0x02F8 to 550, 0x0B90 to 210, 0x0ED8 to 100, 0x1068 to 100, 0x11F8 to 64)
    private val firstIds = listOf(17, 4, 55, 149, 45, 328, 86, 1084, 78)
    private fun put32(b: ByteArray, offset: Int, value: Long) { repeat(4) { b[offset + it] = (value ushr (8 * it)).toByte() } }
    private fun packed(id: Int, count: Int, fresh: Boolean = false, favorite: Boolean = false): Long =
        id.toLong() or (count.toLong() shl 15) or (if (fresh) 0x40000000L else 0L) or (if (favorite) 0x80000000L else 0L)
    private fun bagBytes(): ByteArray = ByteArray(0x12F8).also { b ->
        for ((index, layout) in layouts.withIndex()) {
            put32(b, layout.first, packed(firstIds[index], 999, true, true))
            put32(b, layout.first + 4, packed(firstIds[index], 0))
            put32(b, layout.first + (layout.second - 1) * 4, packed(firstIds[index], 1023))
        }
        put32(b, 0x0B90 + 8, packed(1130, 999)); put32(b, 0x0B90 + 12, packed(1229, 1023))
        put32(b, 0x0B90 + 16, packed(1230, 2)); put32(b, 0x0B90 + 20, packed(693, 2))
        put32(b, 0x02F8 + 8, packed(2047, 0)); put32(b, 0x02F8 + 12, 0x40000000L)
        put32(b, 0x02F8 + 16, 0x80000000L); put32(b, 0x02F8 + 20, 0x3E007800L)
    }
    private fun save(game: Int = 44, size: Int = sizes[0], items: ByteArray = bagBytes()): ByteArray =
        fixture(game, size, core(game) + Block(itemKey, data = items))
    private fun session(source: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, source))

    @Test fun bothTitlesAndAllEightNativeSizesExposeEveryPocketAndBoundary() {
        for (game in listOf(44, 45)) for (size in sizes) {
            val source = save(game, size); val before = source.copyOf(); val bag = SwordShieldInventoryReadOnly.read(source)
            if (game == 44 && size == sizes[0]) assertEquals("d358666ee2647b2a88f3c4cac1e8917772619f8690a4a7dd69cda258de1bbec7", MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it.toInt() and 255) })
            assertEquals(game, bag.version.gameId); assertEquals(9, bag.pockets.size); assertEquals(1214, bag.pockets.sumOf { it.slots.size }); assertEquals(35, bag.recordedCount)
            assertEquals(listOf("Medicine", "Poké Balls", "Battle Items", "Berries", "Other Items", "TMs and TRs", "Treasures", "Curry Ingredients", "Key Items"), bag.pockets.map { it.pocket.displayName })
            for ((i, p) in bag.pockets.withIndex()) {
                assertEquals(layouts[i].first, p.pocket.offset); assertEquals(layouts[i].second, p.slots.size)
                assertEquals((1..layouts[i].second).toList(), p.slots.map { it.slot })
                assertEquals(firstIds[i], p.slot(1).itemId); assertEquals(firstIds[i], p.slot(p.slots.size).itemId)
                assertEquals(999, p.slot(1).count); assertEquals(1023, p.slot(p.slots.size).count)
                assertTrue(p.slot(1).isNew); assertTrue(p.slot(1).isFavorite)
            }
            assertEquals("Potion", bag.pockets[0].slot(1).itemName); assertEquals("TM01", bag.pockets[5].slot(1).itemName)
            assertEquals("TR00", bag.pockets[5].slot(3).itemName); assertEquals("TR99", bag.pockets[5].slot(4).itemName)
            assertEquals("TM00", bag.pockets[5].slot(5).itemName); assertEquals("TM99", bag.pockets[5].slot(6).itemName)
            assertEquals("Sausages", bag.pockets[7].slot(1).itemName); assertArrayEquals(before, source)
        }
    }

    @Test fun everyPackedBitUsesGetterMasksAndBothNativeFlagsWithoutDroppingUnknownBits() {
        val items = ByteArray(0x12F8)
        for (bit in 0..31) put32(items, bit * 4, 1L shl bit)
        put32(items, 32 * 4, 0xFFFFFFFFL)
        val pocket = SwordShieldInventoryReadOnly.read(save(items = items)).pockets[0]
        for (bit in 0..31) {
            val raw = 1L shl bit; val e = pocket.slot(bit + 1)
            assertEquals(raw, e.rawValue); assertEquals(if (bit in 0..10) 1 shl bit else 0, e.itemId)
            assertEquals(if (bit in 15..24) 1 shl (bit - 15) else 0, e.count)
            assertEquals(bit == 30, e.isNew); assertEquals(bit == 31, e.isFavorite)
            assertEquals(if (bit in 0..14) 1 shl bit else 0, e.rawItemField)
            assertEquals(if (bit in 15..29) 1 shl (bit - 15) else 0, e.rawCountField)
            assertEquals(if (bit in 11..14 || bit in 25..29) raw else 0L, e.unknownBits)
        }
        val e = pocket.slot(33); assertEquals(0xFFFFFFFFL, e.rawValue); assertEquals(2047, e.itemId); assertEquals(1023, e.count)
        assertEquals(32767, e.rawItemField); assertEquals(32767, e.rawCountField); assertTrue(e.isNew); assertTrue(e.isFavorite)
        assertEquals(0x3E007800L, e.unknownBits); assertEquals("Item #2047", e.itemName); assertFalse(e.listedInPocket); assertNull(e.referenceMaxCount)
    }

    @Test fun nativeNamesAndTmTrKeyItemReferenceMaximaNeverClampOrRejectSavedCounts() {
        val ids = (1..2047).filter { !SwordShieldItemNames.name(it).startsWith("Item #") }
        val labels = ids.joinToString("\n") { "$it:${SwordShieldItemNames.name(it)}" }
        assertEquals(964, ids.size); assertEquals("63cb9885305a599ea49a9579040f0376872609fa206ba519eda134c82efe7978", MessageDigest.getInstance("SHA-256").digest(labels.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) })
        assertEquals("Empty", SwordShieldItemNames.name(0)); assertEquals("Item #2047", SwordShieldItemNames.name(2047))
        val bag = SwordShieldInventoryReadOnly.read(save()); val machines = bag.pockets[5]
        assertEquals(1, machines.slot(1).referenceMaxCount); assertTrue(machines.slot(1).exceedsReferenceMax)
        assertEquals(999, machines.slot(3).referenceMaxCount); assertFalse(machines.slot(3).exceedsReferenceMax)
        assertEquals(999, machines.slot(4).referenceMaxCount); assertTrue(machines.slot(4).exceedsReferenceMax)
        assertEquals(1, machines.slot(5).referenceMaxCount); assertEquals(1, machines.slot(6).referenceMaxCount)
        assertEquals(1, bag.pockets[8].slot(1).referenceMaxCount); assertEquals(999, bag.pockets[7].slot(1).referenceMaxCount)
        val wrong = SwordShieldInventoryReadOnly.read(save(items = ByteArray(0x12F8).also { put32(it, 0, packed(328, 1023)) })).pockets[0].slot(1)
        assertEquals("TM01", wrong.itemName); assertFalse(wrong.listedInPocket); assertNull(wrong.referenceMaxCount); assertEquals(1023, wrong.count)
    }

    @Test fun emptyDuplicateZeroQuantityAndFlagsOnlyRecordsPreserveNativeOrder() {
        val empty = SwordShieldInventoryReadOnly.read(save(items = ByteArray(0x12F8)))
        assertEquals(0, empty.recordedCount); assertTrue(empty.pockets.all { it.recordedSlots.isEmpty() })
        val bag = SwordShieldInventoryReadOnly.read(save()); val medicine = bag.pockets[0]
        assertEquals(listOf(1, 2, 60), medicine.recordedSlots.map { it.slot }); assertEquals(listOf(17, 17, 17), medicine.recordedSlots.map { it.itemId })
        val p = bag.pockets[4]; assertEquals(listOf(1, 2, 3, 4, 5, 6, 550), p.recordedSlots.map { it.slot })
        assertEquals(2047, p.slot(3).itemId); assertEquals(0, p.slot(3).count); assertFalse(p.slot(3).isEmpty)
        assertEquals(0, p.slot(4).itemId); assertTrue(p.slot(4).isNew); assertTrue(p.slot(5).isFavorite)
        assertEquals(0x3E007800L, p.slot(6).unknownBits); assertTrue(p.slot(7).isEmpty)
        for (table in bag.pockets) for (slot in listOf(-1, 0, table.slots.size + 1, Int.MAX_VALUE))
            assertThrows(IllegalArgumentException::class.java) { table.slot(slot) }
    }

    @Test fun missingOrWrongInventoryObjectDisablesOnlyInventoryAndNeverWeakensIdentity() {
        val malformed = listOf<Block?>(null, Block(itemKey, 1), Block(itemKey, 5, ByteArray(0x12F8)),
            Block(itemKey, 4, ByteArray(0)), Block(itemKey, 4, ByteArray(0x12F7)), Block(itemKey, 4, ByteArray(0x12F9)))
        for (block in malformed) {
            val source = fixture(blocks = core(44) + listOfNotNull(block)); val before = source.copyOf(); val bound = session(source)
            assertTrue(bound.isFormatVerified); assertNull(bound.swordShieldInventory); assertNotNull(bound.inventoryUnavailableReason)
            assertNotNull(bound.swordShieldTrainer); assertNotNull(bound.swordShieldStorage); assertNotNull(bound.swordShieldPokedex)
            assertFalse(bound.supports(SwitchReadCapability.INVENTORY)); assertTrue(bound.supports(SwitchReadCapability.PARTY))
            assertTrue(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertTrue(bound.supports(SwitchReadCapability.POKEDEX))
            assertThrows(IllegalArgumentException::class.java) { SwordShieldInventoryReadOnly.read(source) }; assertArrayEquals(before, source)
        }
    }

    @Test fun fullHashTitleCoreTypeOrderLengthAndSizeGatesPrecedeInventoryExtraction() {
        val good = save()
        for (offset in listOf(0, 126, 127, good.size - 33, good.size - 32, good.lastIndex)) {
            val b = good.copyOf(); b[offset] = (b[offset].toInt() xor 1).toByte()
            assertThrows(IllegalArgumentException::class.java) { SwordShieldInventoryReadOnly.read(b) }
        }
        val invalid = listOf(ByteArray(1), ByteArray(sizes[0]), save(game = 43),
            fixture(blocks = core(44).drop(1) + Block(itemKey, data = bagBytes())),
            fixture(blocks = (core(44) + Block(itemKey, data = bagBytes())).reversed(), sort = false),
            fixture(blocks = core(44) + Block(itemKey, data = bagBytes()) + Block(itemKey, data = bagBytes())),
            fixture(blocks = core(44).map { if (it.key == 0x2985FE5DL) it.copy(type = 5) else it } + Block(itemKey, data = bagBytes())),
            fixture(blocks = core(44) + Block(itemKey, declared = 0xFFFFFFFFL)))
        for (b in invalid) {
            val before = b.copyOf(); assertThrows(IllegalArgumentException::class.java) { SwordShieldInventoryReadOnly.read(b) }
            val bound = session(b); assertFalse(bound.isFormatVerified); assertNull(bound.swordShieldInventory)
            assertFalse(bound.supports(SwitchReadCapability.INVENTORY)); assertFalse(bound.profile.writesEnabled); assertArrayEquals(before, b)
        }
    }

    @Test fun immutableListsAndSnapshotCopiesAreIndependentOfTheSourceAndEachOther() {
        val source = save(); val before = source.copyOf(); val bound = session(source); val bag = bound.swordShieldInventory!!
        source.fill(0); bound.snapshot.bytesCopy().fill(0); assertArrayEquals(before, bound.snapshot.bytesCopy())
        assertEquals(35, bag.recordedCount); assertEquals(999, bag.pockets[0].slot(1).count); assertTrue(bag.pockets[0].slot(1).isFavorite)
        assertThrows(UnsupportedOperationException::class.java) { (bag.pockets as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.pockets[0].slots as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.pockets[0].recordedSlots as MutableList<*>).clear() }
        val later = SwordShieldInventoryReadOnly.read(save(items = ByteArray(0x12F8))); assertEquals(0, later.recordedCount); assertEquals(35, bag.recordedCount)
    }

    @Test fun badTrainerPartyOrOptionalDexMetadataCannotHideAValidBag() {
        val blocks = core(44).map { if (it.key == 0x2985FE5DL) it.copy(data = it.data.copyOf().also { b -> b[0x810] = 7 })
            else if (it.key == 0xF25C070EL) it.copy(data = it.data.copyOf().also { b -> b[0xA7] = 0 }) else it } +
            Block(itemKey, data = bagBytes()) + Block(0x3F936BA9L, data = ByteArray(0))
        val source = fixture(blocks = blocks); val before = source.copyOf(); val bound = session(source)
        assertTrue(bound.isFormatVerified); assertNull(bound.swordShieldTrainer); assertNull(bound.swordShieldStorage); assertNull(bound.swordShieldPokedex)
        assertNotNull(bound.trainerUnavailableReason); assertNotNull(bound.storageUnavailableReason); assertNotNull(bound.pokedexUnavailableReason)
        assertNotNull(bound.swordShieldInventory); assertNull(bound.inventoryUnavailableReason); assertEquals(35, bound.swordShieldInventory!!.recordedCount)
        assertTrue(bound.supports(SwitchReadCapability.INVENTORY)); assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE))
        assertFalse(bound.supports(SwitchReadCapability.PARTY)); assertFalse(bound.supports(SwitchReadCapability.POKEDEX)); assertArrayEquals(before, source)
    }

    @Test fun bothTitlesOverrideEveryFamilyHintWithoutGrantingWritingOrOtherGameReaders() {
        for (game in listOf(44, 45)) for (hint in SwitchGameFamily.values()) {
            val bound = session(save(game), hint); assertEquals(SwitchGameFamily.SWORD_SHIELD, bound.profile.family)
            assertTrue(bound.isFormatVerified); assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
            assertNotNull(bound.swordShieldInventory); assertNull(bound.letsGoInventory); assertNull(bound.inventoryUnavailableReason)
            assertNull(bound.letsGoStorage); assertNull(bound.bdspStorage); assertNull(bound.legendsArceusStorage); assertNull(bound.scarletVioletStorage); assertNull(bound.legendsZaStorage)
            for (c in SwitchReadCapability.values()) assertEquals(c in setOf(SwitchReadCapability.SAVE_IDENTITY, SwitchReadCapability.TRAINER_PROFILE,
                SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS, SwitchReadCapability.POKEDEX, SwitchReadCapability.INVENTORY), bound.supports(c))
        }
        val unknown = session(byteArrayOf(44)); assertNull(unknown.swordShieldInventory); assertFalse(unknown.supports(SwitchReadCapability.INVENTORY))
    }
}
