package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ScarletVioletInventoryReadOnlyTest {
    private val intro = hex("9ec99cd70ed33c44fb9303dceb39b42a1947e9634ba2334416bf82a2ba6355b63d9df24b5f7b6ab2621dc21b68e5c8b53a059000e8a8103de2ecf00cb2ed4f6d")
    private val outro = hex("d6c01c598bc8b8cb46e153fc828c757513e045df32693c75f059f8d9a25fb217e08052dbea8973997579afcb2e8007e6f126e0030ae66ff641bf7e59c2ae55fd")
    private val pad = hex("a092d10607db32a1ae01f5c51e844fe353ca37f4a7b04da018b7c297da5f532b75fa4816f8d48a6f6105f4e2fd04b5a30ffc4492cb32e61bb9b12e01b0565336d2d1503dde5b2e0e52fddf2f7bca6350a4675d2317c052e1a6307c2bb670365b2a276933f5637b363f269ba3ed7a5300a448b3509e14a052de7e102b1b776e")
    private val discreteSizes = listOf(0x31626F, 0x31627C, 0x319DB3, 0x319DC0, 0x319DC3, 0x319DD0,
        0x31A2C0, 0x31A2CD, 0x31A2DD, 0x31A2D0, 0x31CA6F, 0x31CA62, 0x31CA67, 0x31CA74,
        0x31CF7C, 0x31CF6F, 0x31CF74, 0x31CF81)
    private val ranges = listOf(0x4329A0..(0x4329C9 + 100), 0x432EB6..(0x432ED6 + 100)).flatMap { range ->
        listOf(0, 0xC8E, 0x83AD).map { offset -> (range.first + offset)..(range.last + offset) }
    }
    private val sizes = discreteSizes + ranges.flatMap { listOf(it.first, it.last) }
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
        Block(0x0D66012C, data = ByteArray(0x53250)),
        Block(0x3AA1A9AD, data = ByteArray(0x814)),
        Block(0x0DEAAEBD, data = ByteArray(0xBD24)),
        Block(0xE3E89BD1, data = ByteArray(0x68).also { it[0x04] = game.toByte() })
    )
    private fun sign(payload: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(intro); md.update(payload); md.update(outro)
        return payload + md.digest()
    }
    private fun fixture(game: Int = 50, size: Int = sizes[0], blocks: List<Block> = core(game), sort: Boolean = true): ByteArray {
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

    private fun word(data: ByteArray, at: Int, value: Long) {
        repeat(4) { data[at + it] = (value ushr (it * 8)).toByte() }
    }
    private fun record(data: ByteArray, id: Int, pouch: Long = 0, count: Long = 0,
        flags: Long = 0, padding: Long = 0) {
        word(data, id * 16, pouch); word(data, id * 16 + 4, count)
        word(data, id * 16 + 8, flags); word(data, id * 16 + 12, padding)
    }
    private fun blocks(game: Int = 50, bag: ByteArray = ByteArray(0xBB80)): List<Block> =
        core(game) + Block(0x21C9BD44L, data = bag)
    private fun save(game: Int = 50, bag: ByteArray = ByteArray(0xBB80), size: Int = sizes[0]): ByteArray =
        fixture(game, size, blocks(game, bag))
    private fun session(source: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.SCARLET_VIOLET): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, source))

    @Test fun bothTitlesAndBaseDlcLayoutsExposeTenNativePockets() {
        val ids = listOf(17, 4, 55, 149, 45, 328, 86, 1888, 78, 1956)
        val raw = ByteArray(0xBB80)
        ids.forEachIndexed { index, id -> record(raw, id, index.toLong(), 5, 7) }
        for (game in listOf(50, 51)) for (size in listOf(sizes[0], discreteSizes.last(), ranges.first().first, ranges.last().last)) {
            val source = save(game, raw, size); val before = source.copyOf()
            val bag = ScarletVioletInventoryReadOnly.read(source)
            assertArrayEquals(before, source); assertEquals(game, bag.version.gameId)
            assertEquals(3000, bag.slots.size); assertEquals(10, bag.pockets.size); assertEquals(10, bag.recordedCount)
            for ((i, id) in ids.withIndex()) {
                assertEquals(listOf(id), bag.pockets[i].recordedSlots.map { it.itemId })
                assertEquals(5, bag.slot(id).count); assertTrue(bag.slot(id).pouchMatchesCatalogue)
                assertTrue(bag.slot(id).isNew); assertTrue(bag.slot(id).isFavorite); assertTrue(bag.slot(id).isObtained)
            }
            assertTrue(bag.unlistedRecords.isEmpty())
        }
    }

    @Test fun rawWordsSignedCountsMismatchedPouchAndUnlistedIndicesRemainVisible() {
        val raw = ByteArray(0xBB80).also {
            record(it, 0, padding = 1); record(it, 17, 7, 0x80000000L, 0x80000007L, 0xFFFFFFFFL)
            record(it, 328, 5, 0, 4); record(it, 2999, 10, 0xFFFFFFFFL, 8, 123)
        }
        val bag = ScarletVioletInventoryReadOnly.read(save(bag = raw))
        assertEquals(4, bag.recordedCount); assertEquals(listOf(0,2999), bag.unlistedRecords.map { it.itemId })
        val entry = bag.slot(17)
        assertEquals(Int.MIN_VALUE, entry.count); assertEquals(0x80000000L, entry.rawCount)
        assertEquals(7L, entry.rawPouch); assertFalse(entry.pouchMatchesCatalogue)
        assertEquals(ScarletVioletInventoryPocket.MEDICINE, entry.pocket)
        assertEquals(0x80000007L, entry.rawFlags); assertEquals(0xFFFFFFFFL, entry.padding)
        assertEquals(0, bag.slot(328).count); assertTrue(bag.slot(328).isObtained)
        assertFalse(bag.slot(328).isNew); assertFalse(bag.slot(328).isFavorite)
        assertEquals(-1, bag.slot(2999).count); assertEquals("Item #2999", bag.slot(2999).itemName)
    }

    @Test fun all128RecordBitsUseIndependentWordsAndIndexBasedItemIds() {
        val raw = ByteArray(0xBB80)
        for (bit in 0 until 128) raw[(1000 + bit) * 16 + bit / 8] = (1 shl (bit % 8)).toByte()
        val bag = ScarletVioletInventoryReadOnly.read(save(bag = raw))
        for (bit in 0 until 128) {
            val entry = bag.slot(1000 + bit); val expected = 1L shl (bit % 32)
            assertEquals(1000 + bit, entry.itemId)
            assertEquals(if (bit < 32) expected else 0L, entry.rawPouch)
            assertEquals(if (bit in 32..63) expected else 0L, entry.rawCount)
            assertEquals(if (bit in 32..63) expected.toInt() else 0, entry.count)
            assertEquals(if (bit in 64..95) expected else 0L, entry.rawFlags)
            assertEquals(if (bit >= 96) expected else 0L, entry.padding)
            assertEquals(bit == 64, entry.isNew); assertEquals(bit == 65, entry.isFavorite)
            assertEquals(bit == 66, entry.isObtained)
        }
        assertTrue(bag.slots.take(1000).all { !it.isRecorded })
        assertTrue(bag.slots.drop(1128).all { !it.isRecorded })
    }

    @Test fun earlySentinelInitializationAndHistoricalFlagsDoNotFabricateOwnedItems() {
        val raw = ByteArray(0xBB80)
        for (id in 0 until 3000) record(raw, id, 0xFFFFFFFFL)
        val empty = ScarletVioletInventoryReadOnly.read(save(bag = raw))
        assertEquals(0, empty.recordedCount); assertEquals(3000, empty.slots.size)
        assertTrue(empty.slots.all { it.rawPouch == 0xFFFFFFFFL && !it.isObtained })
        record(raw, 17, 0, 0, 4); record(raw, 2999, 0, 0, 0x80000000L)
        val bag = ScarletVioletInventoryReadOnly.read(save(bag = raw))
        assertEquals(2, bag.recordedCount); assertTrue(bag.slot(17).isObtained)
        assertEquals(0, bag.slot(17).count); assertFalse(bag.slot(2999).isObtained)
        assertEquals(listOf(2999), bag.unlistedRecords.map { it.itemId })
    }

    @Test fun missingWrongTypeAndWrongLengthBagDisableOnlyInventory() {
        val invalid = listOf(core(50)) + listOf(0, 0xBB7F, 0xBB81).map {
            core(50) + Block(0x21C9BD44L, data = ByteArray(it))
        } + listOf(core(50) + Block(0x21C9BD44L, type = 5, data = ByteArray(0xBB80)))
        for (blocks in invalid) {
            val source = fixture(blocks = blocks)
            assertTrue(ScarletVioletSaveValidation.inspect(source) is ScarletVioletValidation.Verified)
            assertThrows(IllegalArgumentException::class.java) { ScarletVioletInventoryReadOnly.read(source) }
            val bound = session(source); assertNull(bound.scarletVioletInventory)
            assertNotNull(bound.inventoryUnavailableReason); assertFalse(bound.supports(SwitchReadCapability.INVENTORY))
            assertTrue(bound.supports(SwitchReadCapability.PARTY)); assertTrue(bound.supports(SwitchReadCapability.POKEDEX))
        }
    }

    @Test fun completeGateRejectsCorruptionWrongTitleMissingCoreAndLateMalformedBlocks() {
        val source = save()
        for (offset in listOf(0, 126, 127, source.size - 33, source.size - 32, source.size - 1)) {
            val bad = source.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }
            assertThrows(IllegalArgumentException::class.java) { ScarletVioletInventoryReadOnly.read(bad) }
        }
        val invalid = listOf(
            fixture(game = 49, blocks = blocks(49)), fixture(blocks = blocks().filter { it.key != 0x0D66012CL }),
            fixture(blocks = blocks() + Block(0x21C9BD44L, data = ByteArray(0xBB80))),
            fixture(blocks = blocks().reversed(), sort = false),
            fixture(blocks = blocks() + Block(0xFFFFFFFEL, declared = 0xFFFFFFFFL)),
            ByteArray(0), ByteArray(0x31626F), ByteArray(0x31626E), ByteArray(0x43299F)
        )
        for (bad in invalid) assertThrows(IllegalArgumentException::class.java) { ScarletVioletInventoryReadOnly.read(bad) }
    }

    @Test fun inventoryIsIndependentOfTrainerAndPartyWhileHintsCannotGrantWrites() {
        for (game in listOf(50, 51)) {
            val blocks = blocks(game).map { b ->
                if (b.key == 0xE3E89BD1L) b.copy(data = b.data.copyOf().also { it[7] = 0 })
                else if (b.key == 0x3AA1A9ADL) b.copy(data = b.data.copyOf().also { it[6 * 344] = 7 }) else b
            }
            val source = fixture(game, blocks = blocks)
            for (hint in SwitchGameFamily.values()) {
                val bound = session(source, hint)
                assertEquals(SwitchGameFamily.SCARLET_VIOLET, bound.profile.family)
                assertNotNull(bound.scarletVioletInventory); assertNull(bound.scarletVioletTrainer)
                assertNull(bound.scarletVioletStorage); assertNotNull(bound.scarletVioletPokedex)
                assertTrue(bound.supports(SwitchReadCapability.INVENTORY)); assertFalse(bound.profile.writesEnabled)
                assertTrue(bound.isReadOnly); assertFalse(bound.supports(SwitchReadCapability.PSE_BANK_READ_ONLY))
                assertNull(bound.letsGoInventory); assertNull(bound.swordShieldInventory)
                assertNull(bound.bdspInventory); assertNull(bound.legendsArceusInventory)
            }
        }
    }

    @Test fun labelsPicnicMaximaSnapshotsAndReturnedListsPreserveTheirMeaning() {
        val raw = ByteArray(0xBB80).also { record(it, 17, count = 3, flags = 4) }
        val source = save(bag = raw); val before = source.copyOf(); val bound = session(source)
        val bag = bound.scarletVioletInventory!!; assertArrayEquals(before, source)
        source.fill(0); bound.snapshot.bytesCopy().fill(0); raw.fill(0)
        assertEquals(3, bag.slot(17).count)
        for ((id, label) in listOf(328 to "TM001", 419 to "TM092", 618 to "TM093", 693 to "TM099", 2160 to "TM100", 2289 to "TM229"))
            assertEquals(label, bag.slot(id).itemName)
        assertEquals("Scarlet Book (1)", bag.slot(1834).itemName)
        assertEquals("Scarlet Book (2)", bag.slot(2555).itemName)
        assertEquals(1, bag.slot(2311).referenceMaxCount); assertEquals(999, bag.slot(2334).referenceMaxCount)
        assertEquals(999, bag.slot(2548).referenceMaxCount); assertEquals(999, bag.slot(1888).referenceMaxCount)
        assertEquals(1, bag.slot(78).referenceMaxCount)
        assertThrows(UnsupportedOperationException::class.java) { (bag.slots as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.pockets as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.pockets[0].slots as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.pockets[0].recordedSlots as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.unlistedRecords as MutableList<*>).clear() }
        assertThrows(IllegalArgumentException::class.java) { bag.slot(-1) }
        assertThrows(IllegalArgumentException::class.java) { bag.slot(3000) }
    }
}
