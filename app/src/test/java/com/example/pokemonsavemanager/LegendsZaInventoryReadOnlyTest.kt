package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class LegendsZaInventoryReadOnlyTest {
    private val intro = hex("9ec99cd70ed33c44fb9303dceb39b42a1947e9634ba2334416bf82a2ba6355b63d9df24b5f7b6ab2621dc21b68e5c8b53a059000e8a8103de2ecf00cb2ed4f6d")
    private val outro = hex("d6c01c598bc8b8cb46e153fc828c757513e045df32693c75f059f8d9a25fb217e08052dbea8973997579afcb2e8007e6f126e0030ae66ff641bf7e59c2ae55fd")
    private val pad = hex("a092d10607db32a1ae01f5c51e844fe353ca37f4a7b04da018b7c297da5f532b75fa4816f8d48a6f6105f4e2fd04b5a30ffc4492cb32e61bb9b12e01b0565336d2d1503dde5b2e0e52fddf2f7bca6350a4675d2317c052e1a6307c2bb670365b2a276933f5637b363f269ba3ed7a5300a448b3509e14a052de7e102b1b776e")
    private val sizes = listOf(0x2F3284, 0x2F3289, 0x309FA6, 0x309FB3)
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
        Block(0x0D66012C, data = ByteArray(0x5FA00)),
        Block(0x3AA1A9AD, data = ByteArray(0xB40)),
        Block(0x2D87BE5C, data = ByteArray(0x27068)),
        Block(0xE3E89BD1, data = ByteArray(0x78).also { it[0x04] = game.toByte() })
    )
    private fun sign(payload: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(intro); md.update(payload); md.update(outro)
        return payload + md.digest()
    }
    private fun fixture(game: Int = 52, size: Int = sizes[0], blocks: List<Block> = core(game), sort: Boolean = true): ByteArray {
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

    private fun word(data: ByteArray, at: Int, value: Long) { repeat(4) { data[at + it] = (value ushr (it * 8)).toByte() } }
    private fun record(data: ByteArray, id: Int, pouch: Long = 0, count: Long = 0, flags: Long = 0, pad: Long = 0) {
        word(data, id * 16, pouch); word(data, id * 16 + 4, count)
        word(data, id * 16 + 8, flags); word(data, id * 16 + 12, pad)
    }
    private fun blocks(bag: ByteArray = ByteArray(0xBC00), revision: Int? = null, patched: Boolean = false): List<Block> =
        core(52) + Block(0x21C9BD44L, data = bag) +
            (if (revision == null) emptyList() else listOf(Block(0x0926555AL, type = 11,
                data = ByteArray(8).also { it[0] = revision.toByte() }))) +
            (if (patched) listOf(Block(0x0ABC6547L, type = 1)) else emptyList())
    private fun save(bag: ByteArray = ByteArray(0xBC00), revision: Int? = null, patched: Boolean = false,
        size: Int = sizes[0]): ByteArray = fixture(size = size, blocks = blocks(bag, revision, patched))
    private fun session(source: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, source))

    @Test fun allNativeSizesAndRevisionMarkersExposeEightPocketsAndFullTail() {
        val raw = ByteArray(0xBC00); val ids = listOf(17,4,149,45,328,656,86,632)
        val native = listOf(0,1,5,2,6,7,3,4)
        ids.forEachIndexed { i, id -> record(raw, id, native[i].toLong(), 3, 31) }
        word(raw, 0xBB80, 0x800001FFL); raw[0xBBFF] = 0xFE.toByte()
        for (size in sizes) for (revision in listOf(null,0,1,2)) {
            val source = save(raw, revision, size = size); val before = source.copyOf()
            val bag = LegendsZaInventoryReadOnly.read(source)
            assertArrayEquals(before, source); assertEquals(52, bag.version.gameId)
            assertEquals(revision, bag.revision?.marker); assertEquals(3000, bag.slots.size)
            assertEquals(8, bag.pockets.size); assertEquals(8, bag.recordedCount)
            assertEquals(native, bag.pockets.map { it.pocket.nativeIndex })
            for ((i,id) in ids.withIndex()) {
                assertEquals(listOf(id), bag.pockets[i].recordedSlots.map { it.itemId })
                assertTrue(bag.pockets[i].unlocked); assertTrue(bag.slot(id).pouchMatchesCatalogue)
                assertTrue(bag.slot(id).isNew); assertTrue(bag.slot(id).isFavorite)
                assertTrue(bag.slot(id).isNewNotify); assertTrue(bag.slot(id).isNewShop); assertTrue(bag.slot(id).isHeld)
            }
            assertTrue(bag.additionalPocketFlag); assertEquals(0x800001FFL, bag.rawPouchFlags)
            assertEquals(128, bag.tailBytes.size); assertEquals(254, bag.tailBytes.last())
            assertFalse(bag.megaShardMaxPatched)
        }
    }

    @Test fun shardLimitUsesPatchKeyPresenceRatherThanRevisionOrValue() {
        for (revision in listOf(null,0,1,2)) {
            val ordinary = LegendsZaInventoryReadOnly.read(save(revision = revision))
            val patched = LegendsZaInventoryReadOnly.read(save(revision = revision, patched = true))
            assertEquals(999, ordinary.slot(2618).referenceMaxCount)
            assertEquals(9999, patched.slot(2618).referenceMaxCount)
        }
        for (patch in listOf(Block(0x0ABC6547L, type = 4), Block(0x0ABC6547L, type = 10, data = ByteArray(4)))) {
            val bag = LegendsZaInventoryReadOnly.read(fixture(blocks = blocks() + patch))
            assertTrue(bag.megaShardMaxPatched); assertEquals(9999, bag.slot(2618).referenceMaxCount)
        }
        val raw = ByteArray(0xBC00).also { record(it,2619,2,0xFFFFFFFFL); record(it,2612,4,5) }
        val bag = LegendsZaInventoryReadOnly.read(save(raw))
        assertEquals(-1, bag.slot(2619).referenceMaxCount); assertEquals(0, bag.slot(2612).referenceMaxCount)
        assertEquals(5, bag.slot(2612).count); assertEquals(1, bag.slot(328).referenceMaxCount)
        assertEquals(1, bag.slot(656).referenceMaxCount)
    }

    @Test fun rawSignedValuesDefaultTemplatesAndUnknownMetadataArePreserved() {
        val raw = ByteArray(0xBC00)
        for (id in 0 until 3000) record(raw,id,0xFFFFFFFFL,0,13)
        assertEquals(0, LegendsZaInventoryReadOnly.read(save(raw)).recordedCount)
        record(raw,0,pad=1); record(raw,17,7,0x80000000L,0x8000001FL,0xFFFFFFFFL)
        record(raw,328,6,0,1); record(raw,2999,10,0xFFFFFFFFL,0x20,123)
        val bag = LegendsZaInventoryReadOnly.read(save(raw)); val entry=bag.slot(17)
        assertEquals(4,bag.recordedCount); assertEquals(listOf(0,2999),bag.unlistedRecords.map { it.itemId })
        assertEquals(Int.MIN_VALUE,entry.count); assertEquals(0x80000000L,entry.rawCount)
        assertEquals(0x8000001FL,entry.rawFlags); assertEquals(0xFFFFFFFFL,entry.padding)
        assertFalse(entry.pouchMatchesCatalogue); assertTrue(entry.hasValidPouch)
        assertFalse(bag.slot(18).hasValidPouch); assertTrue(bag.slot(18).isNewNotify)
        assertTrue(bag.slot(18).isNewShop); assertFalse(bag.slot(18).isRecorded)
        assertEquals(-1,bag.slot(2999).count); assertEquals("Item #2999",bag.slot(2999).itemName)
    }

    @Test fun allRecordBitsAndPocketUnlockBitsHaveNativeMeaning() {
        val raw = ByteArray(0xBC00)
        for (bit in 0 until 128) raw[(1000+bit)*16+bit/8]=(1 shl (bit%8)).toByte()
        val bag = LegendsZaInventoryReadOnly.read(save(raw))
        for (bit in 0 until 128) {
            val entry=bag.slot(1000+bit); val value=1L shl (bit%32)
            assertEquals(if(bit<32)value else 0L,entry.rawPouch)
            assertEquals(if(bit in 32..63)value else 0L,entry.rawCount)
            assertEquals(if(bit in 32..63)value.toInt() else 0,entry.count)
            assertEquals(if(bit in 64..95)value else 0L,entry.rawFlags)
            assertEquals(if(bit>=96)value else 0L,entry.padding)
            assertEquals(bit==64,entry.isNew); assertEquals(bit==65,entry.isFavorite)
            assertEquals(bit==66,entry.isNewNotify); assertEquals(bit==67,entry.isNewShop); assertEquals(bit==68,entry.isHeld)
        }
        for(bit in 0..8) {
            val flags=ByteArray(0xBC00).also { word(it,0xBB80,1L shl bit) }
            val result=LegendsZaInventoryReadOnly.read(save(flags))
            assertEquals(bit==8,result.additionalPocketFlag)
            for(p in result.pockets) assertEquals(p.pocket.nativeIndex==bit,p.unlocked)
        }
    }

    @Test fun bagShapeFailuresLeaveIndependentReadersAvailable() {
        val cases=listOf(core(52)) + listOf(0,0xBBFF,0xBC01).map { core(52)+Block(0x21C9BD44L,data=ByteArray(it)) } +
            listOf(core(52)+Block(0x21C9BD44L,type=5,data=ByteArray(0xBC00)))
        for(parts in cases) {
            val source=fixture(blocks=parts)
            assertTrue(LegendsZaSaveValidation.inspect(source) is LegendsZaValidation.Verified)
            assertThrows(IllegalArgumentException::class.java) { LegendsZaInventoryReadOnly.read(source) }
            val bound=session(source); assertNull(bound.legendsZaInventory); assertNotNull(bound.inventoryUnavailableReason)
            assertFalse(bound.supports(SwitchReadCapability.INVENTORY)); assertTrue(bound.supports(SwitchReadCapability.PARTY))
        }
    }

    @Test fun fullContainerGateRejectsCorruptionBadRevisionsAndLateMalformedBlocks() {
        val source=save()
        for(at in listOf(0,126,127,source.size-33,source.size-32,source.size-1)) {
            val bad=source.copyOf().also { it[at]=(it[at].toInt() xor 1).toByte() }
            assertThrows(IllegalArgumentException::class.java) { LegendsZaInventoryReadOnly.read(bad) }
        }
        val cases=listOf(fixture(blocks=core(51)+Block(0x21C9BD44L,data=ByteArray(0xBC00))),
            fixture(blocks=blocks().filter { it.key!=0x0D66012CL }),
            fixture(blocks=blocks()+Block(0x21C9BD44L,data=ByteArray(0xBC00))),
            fixture(blocks=blocks().reversed(),sort=false),
            fixture(blocks=blocks()+Block(0xFFFFFFFEL,declared=0xFFFFFFFFL)),
            save(revision=3), fixture(blocks=blocks()+Block(0x0926555AL,type=10,data=ByteArray(4))),
            ByteArray(0),ByteArray(sizes[0]),ByteArray(sizes[0]-1))
        for(bad in cases) assertThrows(IllegalArgumentException::class.java) { LegendsZaInventoryReadOnly.read(bad) }
    }

    @Test fun hintsAndInvalidTrainerPartyCannotEnableWritesOrSuppressInventory() {
        val parts=blocks().map { b ->
            if(b.key==0xE3E89BD1L)b.copy(data=b.data.copyOf().also { it[7]=0 })
            else if(b.key==0x3AA1A9ADL)b.copy(data=b.data.copyOf().also { it[0]=1 }) else b
        }
        val source=fixture(blocks=parts)
        for(hint in SwitchGameFamily.values()) {
            val bound=session(source,hint); assertEquals(SwitchGameFamily.LEGENDS_ZA,bound.profile.family)
            assertNotNull(bound.legendsZaInventory); assertNull(bound.legendsZaTrainer); assertNotNull(bound.legendsZaStorage)
            assertEquals(1,bound.legendsZaStorage!!.partyCount)
            assertTrue(bound.supports(SwitchReadCapability.INVENTORY)); assertFalse(bound.profile.writesEnabled)
            assertTrue(bound.isReadOnly); assertFalse(bound.supports(SwitchReadCapability.PSE_BANK_READ_ONLY))
            assertNull(bound.letsGoInventory);assertNull(bound.swordShieldInventory);assertNull(bound.bdspInventory)
            assertNull(bound.legendsArceusInventory);assertNull(bound.scarletVioletInventory)
        }
    }

    @Test fun labelsSnapshotsAndListsStayDetachedFromMutableSources() {
        val raw=ByteArray(0xBC00).also { record(it,17,count=3,flags=2);it[0xBBFF]=123 }
        val source=save(raw);val before=source.copyOf();val bound=session(source);val bag=bound.legendsZaInventory!!
        assertArrayEquals(before,source);source.fill(0);bound.snapshot.bytesCopy().fill(0);raw.fill(0)
        assertEquals(3,bag.slot(17).count);assertEquals(123,bag.tailBytes.last())
        assertEquals("TM006",bag.slot(331).itemName);assertEquals("TM037",bag.slot(2160).itemName)
        assertEquals("Seed of Mastery (ZA)",bag.slot(2558).itemName)
        assertTrue(bag.slot(2620).itemName.endsWith(" (1)"));assertTrue(bag.slot(2622).itemName.endsWith(" (3)"))
        assertThrows(UnsupportedOperationException::class.java) { (bag.slots as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.pockets as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.tailBytes as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.pockets[0].recordedSlots as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (bag.unlistedRecords as MutableList<*>).clear() }
        assertThrows(IllegalArgumentException::class.java) { bag.slot(-1) }
        assertThrows(IllegalArgumentException::class.java) { bag.slot(3000) }
    }
}
