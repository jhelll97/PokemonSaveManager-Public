package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class SwordShieldPokedexReadOnlyTest {
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

    private fun put32(b: ByteArray, offset: Int, value: Long) { repeat(4) { b[offset + it] = (value ushr (8 * it)).toByte() } }
    private val regionKeys = listOf(0x4716C404L, 0x3F936BA9L, 0x3C9366F0L)
    private val counts = listOf(400, 211, 210)
    private fun dexBytes(count: Int): ByteArray = ByteArray(count * 0x30).also { b ->
        b[0] = 1; b[15] = 0x80.toByte(); b[23] = 0x40; b[31] = 0x80.toByte()
        put32(b, 0x20, 0xFFFFFFFFL); put32(b, 0x24, 0xFFFFFFFFL); put32(b, 0x28, 0xFFFFFFFDL); put32(b, 0x2C, 0x80000000L)
        put32(b, (count - 1) * 0x30 + 0x20, 1L)
    }
    private fun save(game: Int = 44, size: Int = sizes[0], expansion: Int = 2): ByteArray {
        val blocks = core(game).map { if (it.key == regionKeys[0]) it.copy(data = dexBytes(400)) else it } +
            (1..expansion).map { index -> Block(regionKeys[index], data = dexBytes(counts[index])) }
        return fixture(game, size, blocks)
    }
    private fun session(source: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, source))

    @Test fun bothNativeTitlesAndEveryKnownSizeExposeThreeDetachedRegionalTables() {
        for (game in listOf(44, 45)) for (size in sizes) {
            val source = save(game, size); val before = source.copyOf(); val dex = SwordShieldPokedexReadOnly.read(source)
            if (game == 44 && size == sizes[0]) assertEquals("6e0d49c16e9a4728929cc8719f96e24bce955afce6cb25e76346dc5488ae792d", MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it.toInt() and 255) })
            assertEquals(game, dex.version.gameId); assertEquals(listOf(400, 211, 210), dex.regions.map { it.entries.size })
            for (table in dex.regions) { assertEquals(1, table.seenCount); assertEquals(2, table.caughtCount); assertFalse(table.entries.last().seen) }
            assertEquals(810, dex.regions[0].entry(1).speciesId); assertEquals(890, dex.regions[0].entry(400).speciesId)
            assertEquals(79, dex.regions[1].entry(1).speciesId); assertEquals(893, dex.regions[1].entry(211).speciesId)
            assertEquals(872, dex.regions[2].entry(1).speciesId); assertEquals(898, dex.regions[2].entry(210).speciesId)
            assertArrayEquals(before, source); source.fill(0); assertTrue(dex.regions[0].entry(1).caught)
        }
    }

    @Test fun rawHighBitFieldsAllNineLanguagesAndSpecialFormBitsPreserveExactValues() {
        val dex = SwordShieldPokedexReadOnly.read(save())
        for (region in dex.regions) {
            val e = region.entry(1)
            assertEquals(listOf(listOf(0), listOf(63), listOf(62), listOf(63)), e.seenBits)
            assertTrue(e.caught); assertTrue(e.caughtGigantamax); assertEquals(9, e.languages.size)
            assertEquals("Japanese", e.languages.first()); assertEquals("Chinese (Traditional)", e.languages.last())
            assertEquals(8191, e.displayedFormId); assertEquals(3, e.displayedGenderId); assertTrue(e.displayGigantamax); assertTrue(e.displayShiny)
            assertEquals(0xFFFFFFFFL, e.rawFlags); assertEquals(0xFFFFFFFFL, e.battledCount)
            assertTrue(e.secondaryGigantamaxFlag); assertEquals(0xFFFFFFFDL, e.rawExtra1); assertEquals(0x80000000L, e.rawExtra2)
        }
        val blocks = core(44).map { if (it.key == regionKeys[0]) it.copy(data = ByteArray(0x4B00).also { b ->
            put32(b, 0x20, 1); put32(b, 0x28, 2); b[7] = 0x80.toByte()
        }) else it }
        val e = SwordShieldPokedexReadOnly.read(fixture(blocks = blocks)).regions[0].entry(1)
        assertTrue(e.seen); assertTrue(e.caught); assertFalse(e.secondaryGigantamaxFlag); assertEquals(listOf(63), e.seenBits[0])
        assertEquals(0, e.displayedFormId); assertEquals(0, e.displayedGenderId); assertEquals(0L, e.battledCount); assertTrue(e.languages.isEmpty())
    }

    @Test fun nativeMappingIsNotNationalOrderAndSharedSpeciesKeepSeparateRegionalRecords() {
        val blocks = core(44) + Block(0x3F936BA9L, data = dexBytes(211).also { put32(it, 8 * 0x30 + 0x20, 1L) }) + Block(0x3C9366F0L, data = dexBytes(210))
        val dex = SwordShieldPokedexReadOnly.read(fixture(blocks = blocks))
        for (table in dex.regions) {
            assertEquals((1..table.region.entryCount).toList(), table.entries.map { it.number })
            assertEquals(table.region.entryCount, table.entries.map { it.speciesId }.toSet().size)
            for (e in table.entries) assertTrue(e.speciesId in 1..898)
        }
        val galar = dex.regions[0].entries.first { it.speciesId == 819 }; val armor = dex.regions[1].entry(9)
        assertFalse(galar.caught); assertTrue(armor.caught); assertEquals(galar.speciesId, armor.speciesId)
        assertNotEquals(galar.number, armor.number)
        for (region in SwordShieldDexRegion.values()) for (id in listOf(0, -1, region.entryCount + 1, Int.MAX_VALUE))
            assertThrows(IllegalArgumentException::class.java) { dex.region(region)!!.entry(id) }
    }

    @Test fun absentExpansionBlocksStayAbsentWithoutInventingEntitlementOrContent() {
        for (expansion in 0..2) {
            val dex = SwordShieldPokedexReadOnly.read(save(expansion = expansion))
            assertEquals(expansion + 1, dex.regions.size); assertNotNull(dex.region(SwordShieldDexRegion.GALAR))
            assertEquals(expansion >= 1, dex.region(SwordShieldDexRegion.ARMOR) != null)
            assertEquals(expansion >= 2, dex.region(SwordShieldDexRegion.CROWN) != null)
        }
    }

    @Test fun optionalRegionTypesLengthsAndOrphanAllocationDisableOnlyDex() {
        val malformed = listOf(Block(0x3F936BA9L, 1), Block(0x3F936BA9L, 5, ByteArray(0x2790)),
            Block(0x3F936BA9L, 4, ByteArray(0x278F)), Block(0x3F936BA9L, 4, ByteArray(0)),
            Block(0x3C9366F0L, 4, ByteArray(0x2760)), Block(0x3C9366F0L, 4, ByteArray(0x2761)))
        for (extra in malformed) {
            val source = fixture(blocks = core(44) + extra); val before = source.copyOf(); val bound = session(source)
            assertTrue(bound.isFormatVerified); assertNull(bound.swordShieldPokedex); assertNotNull(bound.pokedexUnavailableReason)
            assertNotNull(bound.swordShieldTrainer); assertNotNull(bound.swordShieldStorage)
            assertFalse(bound.supports(SwitchReadCapability.POKEDEX)); assertTrue(bound.supports(SwitchReadCapability.PARTY))
            assertTrue(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertFalse(bound.profile.writesEnabled); assertArrayEquals(before, source)
        }
    }

    @Test fun completeNativeHashCoreTypeTitleOrderAndResourceGatesPrecedeExtraction() {
        val good = save()
        for (offset in listOf(0, 126, 127, good.size - 33, good.size - 32, good.lastIndex)) {
            val damaged = good.copyOf(); damaged[offset] = (damaged[offset].toInt() xor 1).toByte()
            assertThrows(IllegalArgumentException::class.java) { SwordShieldPokedexReadOnly.read(damaged) }
        }
        val invalid = listOf(ByteArray(1), ByteArray(sizes[0]), fixture(game = 43),
            fixture(blocks = core(44).drop(1)), fixture(blocks = core(44).reversed(), sort = false),
            fixture(blocks = core(44) + Block(0x4716C404L, data = ByteArray(0x4B00))),
            fixture(blocks = core(44).map { if (it.key == regionKeys[0]) it.copy(type = 5) else it }),
            fixture(blocks = core(44) + Block(0xFFFFFFFEL, declared = 0xFFFFFFFFL)))
        for (source in invalid) assertThrows(IllegalArgumentException::class.java) { SwordShieldPokedexReadOnly.read(source) }
    }

    @Test fun allFamilyHintsAndImmutableNestedListsRemainReadOnlyAndIndependent() {
        for (hint in SwitchGameFamily.values()) {
            val source = save(45); val bound = session(source, hint); source.fill(0); bound.snapshot.bytesCopy().fill(0)
            val dex = bound.swordShieldPokedex!!; assertEquals(SwordShieldVersion.SHIELD, dex.version)
            assertEquals(SwitchGameFamily.SWORD_SHIELD, bound.profile.family); assertTrue(bound.isReadOnly)
            assertTrue(bound.supports(SwitchReadCapability.POKEDEX)); assertFalse(bound.supports(SwitchReadCapability.POKEDEX_AND_PROGRESS)); assertFalse(bound.profile.writesEnabled)
            assertNull(bound.letsGoPokedex); assertNull(bound.pokedexUnavailableReason)
            assertThrows(UnsupportedOperationException::class.java) { (dex.regions as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (dex.regions[0].entries as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (dex.regions[0].entry(1).languages as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (dex.regions[0].entry(1).seenBits as MutableList<*>).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (dex.regions[0].entry(1).seenBits[0] as MutableList<*>).clear() }
        }
    }

    @Test fun malformedPartyAndTrainerCannotHideIndependentlyValidDex() {
        val blocks = core(44).map { if (it.key == 0x2985FE5DL) it.copy(data = it.data.copyOf().also { b -> b[0x810] = 7 })
            else if (it.key == 0xF25C070EL) it.copy(data = it.data.copyOf().also { b -> b[0xA7] = 0 }) else it }
        val bound = session(fixture(blocks = blocks))
        assertNotNull(bound.swordShieldPokedex); assertNull(bound.swordShieldTrainer); assertNull(bound.swordShieldStorage)
        assertNotNull(bound.trainerUnavailableReason); assertNotNull(bound.storageUnavailableReason); assertNull(bound.pokedexUnavailableReason)
        assertTrue(bound.supports(SwitchReadCapability.POKEDEX)); assertFalse(bound.supports(SwitchReadCapability.PARTY))
        assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertTrue(bound.isFormatVerified)
    }
}
