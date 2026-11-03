package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class LegendsArceusInventoryReadOnlyTest {
    private val intro = hex("9ec99cd70ed33c44fb9303dceb39b42a1947e9634ba2334416bf82a2ba6355b63d9df24b5f7b6ab2621dc21b68e5c8b53a059000e8a8103de2ecf00cb2ed4f6d")
    private val outro = hex("d6c01c598bc8b8cb46e153fc828c757513e045df32693c75f059f8d9a25fb217e08052dbea8973997579afcb2e8007e6f126e0030ae66ff641bf7e59c2ae55fd")
    private val pad = hex("a092d10607db32a1ae01f5c51e844fe353ca37f4a7b04da018b7c297da5f532b75fa4816f8d48a6f6105f4e2fd04b5a30ffc4492cb32e61bb9b12e01b0565336d2d1503dde5b2e0e52fddf2f7bca6350a4675d2317c052e1a6307c2bb670365b2a276933f5637b363f269ba3ed7a5300a448b3509e14a052de7e102b1b776e")
    private val sizes = listOf(0x136DDE, 0x13AD06)
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
        Block(0x47E1CEAB, data = ByteArray(0x54600)),
        Block(0x2985FE5D, data = ByteArray(0x8D4)),
        Block(0x02168706, data = ByteArray(0x1E460)),
        Block(0xF25C070E, data = ByteArray(0x80).also { it[0x14] = game.toByte() })
    )
    private fun sign(payload: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(intro); md.update(payload); md.update(outro)
        return payload + md.digest()
    }
    private fun fixture(game: Int = 47, size: Int = sizes[0], blocks: List<Block> = core(game), sort: Boolean = true): ByteArray {
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

    private fun p16(data: ByteArray, at: Int, value: Int) {
        data[at] = value.toByte(); data[at + 1] = (value ushr 8).toByte()
    }
    private fun item(data: ByteArray, slot: Int, id: Int, count: Int) {
        p16(data, slot * 4, id); p16(data, slot * 4 + 2, count)
    }
    private fun inventoryBlocks(upgrades: Long = 0, regular: ByteArray = ByteArray(0xA8C),
        keys: ByteArray = ByteArray(0x190), stored: ByteArray = ByteArray(0x2D0),
        recipes: ByteArray = ByteArray(0x118), favorites: ByteArray = ByteArray(0x140),
        favoriteType: Int = 5): List<Block> = core(47) + listOf(
        Block(0x9FE2790AL, data = regular), Block(0x59A4D0C3L, data = keys),
        Block(0x8E434F0DL, data = stored), Block(0xF5D9F4A5L, data = recipes),
        Block(0x00EF4BAEL, type = favoriteType, data = favorites),
        Block(0x75CE2CF6L, type = 10, data = ByteArray(4).also { b ->
            repeat(4) { b[it] = (upgrades ushr (it * 8)).toByte() }
        })
    )
    private fun read(blocks: List<Block>, size: Int = sizes[0]): LegendsArceusInventorySnapshot =
        LegendsArceusInventoryReadOnly.read(fixture(size = size, blocks = blocks))
    private fun session(source: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ARCEUS): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, source))

    @Test fun bothNativeSizesExposeFourEmptyPocketsAndAllPhysicalRecords() {
        for (size in sizes) {
            val bag = read(inventoryBlocks(), size)
            assertEquals(listOf("Satchel", "Key Items", "Item Storage", "Crafting Recipes"), bag.pockets.map { it.name })
            assertEquals(listOf(675, 100, 180, 70), bag.pockets.map { it.items.size })
            assertEquals(listOf(20, 100, 180, 70), bag.pockets.map { it.capacity })
            assertEquals(listOf(999, 1, 999, 1), bag.pockets.map { it.referenceMaxCount })
            assertEquals(0, bag.recordedItemCount); assertEquals(0L, bag.rawSatchelUpgrades)
            assertTrue(bag.favoriteItemIds.isEmpty())
            assertTrue(bag.pockets.all { p -> p.items.all { !it.isRecorded && !it.isFavorite } })
        }
    }

    @Test fun signedRecordsDuplicatesResidualSlotsAndOrphanFlagsRemainInspectable() {
        val regular = ByteArray(0xA8C).also {
            item(it, 0, 1710, 999); item(it, 1, 1710, 0); item(it, 19, 65535, 32768)
            item(it, 20, 0, 1); item(it, 674, 32768, 65535)
        }
        val keys = ByteArray(0x190).also { item(it, 99, 1828, 2) }
        val stored = ByteArray(0x2D0).also { item(it, 179, 1710, 32767) }
        val recipes = ByteArray(0x118).also { item(it, 69, 1640, 1) }
        val flags = ByteArray(0x140).also {
            it[1710 / 8] = (1 shl (1710 % 8)).toByte(); it[319] = 0x80.toByte()
        }
        for (size in sizes) {
            val source = fixture(size = size, blocks = inventoryBlocks(regular = regular, keys = keys,
                stored = stored, recipes = recipes, favorites = flags))
            val before = source.copyOf(); val bag = LegendsArceusInventoryReadOnly.read(source)
            assertArrayEquals(before, source)
            assertEquals(8, bag.recordedItemCount); assertEquals(listOf(1710, 2559), bag.favoriteItemIds)
            val p = bag.pockets[0]
            assertEquals(listOf(0, 1, 19, 20, 674), p.recordedItems.map { it.slot })
            assertEquals(2, p.residualCount)
            assertEquals(-1, p.items[19].itemId); assertEquals(-32768, p.items[19].count)
            assertEquals(65535, p.items[19].rawItemId); assertEquals(32768, p.items[19].rawCount)
            assertEquals(-32768, p.items[674].itemId); assertEquals(-1, p.items[674].count)
            assertFalse(p.items[19].isFavorite); assertFalse(p.items[674].isFavorite)
            assertTrue(p.items[0].isFavorite); assertTrue(p.items[1].isFavorite)
            assertTrue(bag.pockets[2].items[179].isFavorite)
            assertEquals("Item #-1", p.items[19].itemName)
            assertEquals("Poké Ball (LA)", p.items[0].itemName)
            assertEquals("Poké Ball (~)", bag.pockets[3].items[69].itemName)
        }
    }

    @Test fun savedUpgradeArithmeticAndBeyondCapacityRecordsAreNotNormalized() {
        val regular = ByteArray(0xA8C).also { item(it, 674, 17, 1) }
        for ((raw, expected) in listOf(0L to 20, 39L to 59, 655L to 675, 656L to 675,
            0xFFFFFFEBL to 675, 0xFFFFFFECL to 0, 0xFFFFFFFFL to 19)) {
            val bag = read(inventoryBlocks(upgrades = raw, regular = regular))
            assertEquals(raw, bag.rawSatchelUpgrades); assertEquals(expected, bag.pockets[0].capacity)
            assertEquals(675, bag.pockets[0].items.size); assertEquals(1, bag.recordedItemCount)
            assertEquals(expected == 675, bag.pockets[0].items[674].withinActiveCapacity)
        }
    }

    @Test fun everyRecordBitUsesFourByteStrideAndSignedInt16Fields() {
        for (bit in 0 until 32) {
            val regular = ByteArray(0xA8C)
            regular[674 * 4 + bit / 8] = (1 shl (bit % 8)).toByte()
            val bag = LegendsArceusInventoryReadOnly.decode(regular, ByteArray(400), ByteArray(720),
                ByteArray(280), ByteArray(320), 20, 0)
            val entry = bag.pockets[0].items.last()
            val expected = (1 shl (bit % 16)).toShort().toInt()
            assertEquals(if (bit < 16) expected else 0, entry.itemId)
            assertEquals(if (bit >= 16) expected else 0, entry.count)
            assertEquals(674, entry.slot); assertEquals(1, bag.recordedItemCount)
            assertTrue(bag.pockets[0].items.dropLast(1).all { !it.isRecorded })
        }
    }

    @Test fun missingOrMalformedInventoryBlocksDisableOnlyInventory() {
        val valid = inventoryBlocks()
        val keys = listOf(0x9FE2790AL, 0x59A4D0C3L, 0x8E434F0DL, 0xF5D9F4A5L, 0x00EF4BAEL, 0x75CE2CF6L)
        val invalid = keys.map { key -> valid.filter { it.key != key } } +
            keys.map { key -> valid.map { if (it.key == key) it.copy(type = 4, data = it.data + byteArrayOf(0)) else it } } +
            keys.map { key -> valid.map { if (it.key == key) it.copy(type = 5, subtype = 9) else it } }
        for (blocks in invalid) {
            val source = fixture(blocks = blocks)
            assertTrue(LegendsArceusSaveValidation.inspect(source) is LegendsArceusValidation.Verified)
            assertThrows(IllegalArgumentException::class.java) { LegendsArceusInventoryReadOnly.read(source) }
            val bound = session(source)
            assertNull(bound.legendsArceusInventory); assertNotNull(bound.inventoryUnavailableReason)
            assertFalse(bound.supports(SwitchReadCapability.INVENTORY))
            assertTrue(bound.supports(SwitchReadCapability.SAVE_IDENTITY))
            assertTrue(bound.supports(SwitchReadCapability.PARTY)); assertTrue(bound.supports(SwitchReadCapability.POKEDEX))
        }
        // Both native byte-body representations preserve identical favorite bits.
        val flags = ByteArray(320).also { it[0] = 1 }
        for (type in listOf(4, 5)) assertEquals(listOf(0), read(inventoryBlocks(favorites = flags, favoriteType = type)).favoriteItemIds)
    }

    @Test fun allHintsUseNativeIdentityAndInventorySurvivesInvalidTrainerOrPartyMetadata() {
        val blocks = inventoryBlocks().map {
            if (it.key == 0xF25C070EL) it.copy(data = it.data.copyOf().also { b -> b[0x17] = 0 })
            else if (it.key == 0x2985FE5DL) it.copy(data = it.data.copyOf().also { b -> b[6 * 376] = 7 }) else it
        }
        val source = fixture(blocks = blocks)
        for (hint in SwitchGameFamily.values()) {
            val bound = session(source, hint)
            assertEquals(SwitchGameFamily.LEGENDS_ARCEUS, bound.profile.family)
            assertNotNull(bound.legendsArceusInventory); assertNull(bound.legendsArceusTrainer)
            assertNull(bound.legendsArceusStorage); assertNotNull(bound.legendsArceusPokedex)
            assertTrue(bound.supports(SwitchReadCapability.INVENTORY)); assertTrue(bound.isReadOnly)
            assertFalse(bound.profile.writesEnabled); assertFalse(bound.supports(SwitchReadCapability.PSE_BANK_READ_ONLY))
            assertNull(bound.letsGoInventory); assertNull(bound.swordShieldInventory); assertNull(bound.bdspInventory)
        }
    }

    @Test fun completeContainerValidationRejectsCorruptionWrongIdentityAndLateMalformedBlocks() {
        for (size in sizes) {
            val source = fixture(size = size, blocks = inventoryBlocks())
            for (at in listOf(0, 126, 127, source.size - 33, source.size - 32, source.size - 1)) {
                val bad = source.copyOf().also { it[at] = (it[at].toInt() xor 1).toByte() }
                assertThrows(IllegalArgumentException::class.java) { LegendsArceusInventoryReadOnly.read(bad) }
            }
        }
        val valid = inventoryBlocks()
        val invalid = listOf(
            fixture(blocks = valid.map { if (it.key == 0xF25C070EL) it.copy(data = it.data.copyOf().also { b -> b[0x14] = 48 }) else it }),
            fixture(blocks = valid.filter { it.key != 0x47E1CEABL }),
            fixture(blocks = valid + valid.first()),
            fixture(blocks = valid.reversed(), sort = false),
            fixture(blocks = valid + Block(0xFFFFFFFEL, declared = 0xFFFFFFFFL)),
            ByteArray(0), ByteArray(sizes[0]), ByteArray(sizes[0] - 1), ByteArray(sizes[1] + 1)
        )
        for (bad in invalid) assertThrows(IllegalArgumentException::class.java) { LegendsArceusInventoryReadOnly.read(bad) }
    }

    @Test fun snapshotAndReturnedListsCannotAliasMutableInputs() {
        val source = fixture(blocks = inventoryBlocks(regular = ByteArray(2700).also { item(it, 0, 17, 3) },
            favorites = ByteArray(320).also { it[2] = 2 }))
        val before = source.copyOf(); val bound = session(source); val bag = bound.legendsArceusInventory!!
        assertArrayEquals(before, source); source.fill(0); bound.snapshot.bytesCopy().fill(0)
        assertEquals(17, bag.pockets[0].items[0].itemId); assertEquals(3, bag.pockets[0].items[0].count)
        assertTrue(bag.pockets[0].items[0].isFavorite)
        assertThrows(UnsupportedOperationException::class.java) { (bag.pockets as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.favoriteItemIds as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.pockets[0].items as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.pockets[0].recordedItems as MutableList<*>).clear() }
        assertEquals(1, bag.recordedItemCount)
    }
}

