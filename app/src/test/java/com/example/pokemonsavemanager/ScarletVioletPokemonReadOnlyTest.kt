package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ScarletVioletPokemonReadOnlyTest {
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
    private fun plain(ec: Long = 0xF003E001L, species: Int = 917): ByteArray = ByteArray(344).also { b ->
        uint(b, 0, ec); word(b, 8, species); word(b, 0x0A, 1589)
        word(b, 0x0C, 0xBEEF); word(b, 0x0E, 0xCAFE); uint(b, 0x10, 0xFEDCBA98)
        word(b, 0x14, 267); b[0x16] = 0; uint(b, 0x1C, (0x1234L shl 16) or (0x1234 xor 0xBEEF xor 0xCAFE).toLong())
        b[0x20] = 24; b[0x21] = 3; b[0x22] = 4; b[0x24] = 7
        listOf(252, 1, 2, 3, 0, 252).forEachIndexed { i, v -> b[0x26 + i] = v.toByte() }
        label(b, 0x58, "Éclair ♀"); label(b, 0xF8, "雪")
        repeat(4) { word(b, 0x72 + 2 * it, 800 + it); b[0x7A + it] = (10 + it).toByte(); b[0x7E + it] = it.toByte(); word(b, 0x82 + 2 * it, 500 + it) }
        word(b, 0x8A, 77); uint(b, 0x8C, listOf(31, 0, 1, 2, 3, 30).mapIndexed { i, v -> v.toLong() shl (5 * i) }.fold(0xC0000000L) { a, v -> a or v })
        b[0x94] = 17; b[0x95] = 99; b[0x4A] = 255.toByte(); b[0x11F] = 99
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
    private fun core(game: Int, count: Int = 0, boxes: Map<Int, ByteArray> = emptyMap(), party: Map<Int, ByteArray> = emptyMap()): List<Block> = listOf(
        Block(0x0D66012C, data = ByteArray(0x53250).also { bytes -> boxes.forEach { (i, record) -> record.copyInto(bytes, i * 344) } }),
        Block(0x3AA1A9AD, data = ByteArray(0x814).also { bytes ->
            bytes[6 * 344] = count.toByte(); party.forEach { (i, record) -> record.copyInto(bytes, i * 344) }
        }),
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

    private fun save(game: Int = 50, size: Int = sizes[0], count: Int = 0,
        boxes: Map<Int, ByteArray> = emptyMap(), party: Map<Int, ByteArray> = emptyMap()): ByteArray =
        fixture(game, size, core(game, count, boxes, party))
    private fun session(bytes: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, bytes))

    @Test fun encryptedPk9FieldsGenderSpeciesAndSavedTeraValuesAreDetached() {
        val input = wire(plain()); val before = input.copyOf()
        assertEquals("fc5621f49f785deebf976da73a02df443174cfcb4c3464f77acace5f3995d8fd", MessageDigest.getInstance("SHA-256").digest(input).joinToString("") { "%02x".format(it.toInt() and 255) })
        val p = (ScarletVioletPokemonReadOnly.read(input) as ScarletVioletSlotRead.Pokemon).value
        assertEquals(982, p.species); assertEquals("Dudunsparce", ScarletVioletSpeciesNames.name(p.species))
        assertEquals("Éclair ♀", p.nickname); assertEquals("雪", p.originalTrainer); assertEquals(7, p.form); assertEquals(2, p.gender)
        assertEquals(67, p.cachedLevel); assertEquals(0xFEDCBA98L, p.experience); assertEquals(0xF003E001L, p.encryptionConstant)
        assertEquals(0xBEEF, p.tid); assertEquals(0xCAFE, p.sid); assertTrue(p.shiny); assertTrue(p.isEgg)
        assertEquals(24, p.nature); assertEquals(3, p.statNature); assertEquals(77, p.currentHp); assertEquals(188, p.maximumHp)
        assertEquals(1589, p.heldItem); assertEquals(267, p.ability); assertEquals(200, p.originalTrainerFriendship)
        assertEquals(123, p.handlingTrainerFriendship); assertEquals(1, p.currentHandler)
        assertEquals(17, p.teraTypeOriginal); assertEquals(99, p.teraTypeOverride); assertEquals(255, p.scale); assertEquals(99, p.obedienceLevel)
        assertEquals(listOf(31, 0, 1, 2, 3, 30), p.ivs); assertEquals(listOf(252, 1, 2, 3, 0, 252), p.evs)
        assertEquals((800..803).toList(), p.moves.map { it.id }); assertEquals((10..13).toList(), p.moves.map { it.pp })
        assertEquals((0..3).toList(), p.moves.map { it.ppUps }); assertEquals((500..503).toList(), p.relearnMoves)
        assertEquals((100..104).toList(), p.cachedStats); assertArrayEquals(before, input); input.fill(0); assertEquals(982, p.species)
        assertThrows(UnsupportedOperationException::class.java) { (p.ivs as MutableList<Int>)[0] = 0 }
        assertThrows(UnsupportedOperationException::class.java) { (p.moves as MutableList<ScarletVioletMoveRead>).clear() }
    }

    @Test fun allSelectorsAndHighBitConstantsUseTheNativeBlocksAndIndependentTailStream() {
        for (selector in 0..31) for (high in listOf(0L, 0x80000000L, 0xFFF00000L)) {
            val p = plain(high or (selector.toLong() shl 13) or 1L)
            val read = (ScarletVioletPokemonReadOnly.read(wire(p)) as ScarletVioletSlotRead.Pokemon).value
            assertEquals(982, read.species); assertEquals("Éclair ♀", read.nickname); assertEquals("雪", read.originalTrainer)
            assertEquals(2, read.gender); assertEquals(67, read.cachedLevel); assertEquals(188, read.maximumHp)
            assertEquals(17, read.teraTypeOriginal); assertEquals(99, read.teraTypeOverride)
        }
    }

    @Test fun nativeSpeciesMappingAndNamesCoverTheFullSupportedIdRange() {
        val expected = listOf(982, 917, 918, 919, 920, 953, 954, 971, 972, 955, 956, 981, 960, 961, 977, 976, 963, 964, 928, 929, 930, 951, 952, 938, 939, 965, 966, 968, 924, 925, 974, 975, 996, 997, 998, 978, 967, 921, 922, 923, 940, 941, 962, 931, 973, 950, 932, 933, 934, 969, 970, 944, 945, 926, 927, 942, 943, 946, 947, 999, 1000, 984, 986, 1009, 989, 985, 987, 988, 1005, 990, 1010, 994, 992, 993, 995, 991, 1006, 1003, 1002, 1001, 1004, 1007, 1008, 957, 958, 959, 935, 936, 937, 948, 949, 983, 980, 979, 1017, 1011, 1019, 1014, 1015, 1016, 1020, 1021, 1023, 1022, 1024, 1025, 1018, 1012, 1013)
        for ((index, national) in expected.withIndex()) {
            val raw = 917 + index
            assertEquals(national, (ScarletVioletPokemonReadOnly.read(wire(plain(species = raw))) as ScarletVioletSlotRead.Pokemon).value.species)
        }
        assertEquals((917..1025).toSet(), expected.toSet())
        for (raw in listOf(1, 25, 898, 905, 906, 916))
            assertEquals(raw, (ScarletVioletPokemonReadOnly.read(wire(plain(species = raw))) as ScarletVioletSlotRead.Pokemon).value.species)
        assertEquals("Sprigatito", ScarletVioletSpeciesNames.name(906)); assertEquals("Miraidon", ScarletVioletSpeciesNames.name(1008))
        assertEquals("Pecharunt", ScarletVioletSpeciesNames.name(1025)); assertEquals("Enamorus", ScarletVioletSpeciesNames.name(905))
        for (gender in 0..2) {
            val p = plain().also { it[0x22] = (gender shl 1).toByte(); checksum(it) }
            assertEquals(gender, (ScarletVioletPokemonReadOnly.read(wire(p)) as ScarletVioletSlotRead.Pokemon).value.gender)
        }
    }

    @Test fun malformedPk9NeverFallsBackToPlaintextOrRepairAndEmptyRecordsStayEmpty() {
        for (length in listOf(0, 8, 328, 343, 345, 360, 376)) assertTrue(ScarletVioletPokemonReadOnly.read(ByteArray(length)) is ScarletVioletSlotRead.Invalid)
        val record = wire(plain())
        for (offset in listOf(4, 6, 8, 87, 167, 247, 327)) {
            val broken = record.copyOf(); broken[offset] = (broken[offset].toInt() xor 1).toByte()
            assertTrue(ScarletVioletPokemonReadOnly.read(broken) is ScarletVioletSlotRead.Invalid)
        }
        for (species in listOf(1026, 2000, 65535)) assertTrue(ScarletVioletPokemonReadOnly.read(wire(plain(species = species))) is ScarletVioletSlotRead.Invalid)
        assertTrue(ScarletVioletPokemonReadOnly.read(plain()) is ScarletVioletSlotRead.Invalid)
        assertTrue(ScarletVioletPokemonReadOnly.read(ByteArray(344)) is ScarletVioletSlotRead.Empty)
        assertTrue(ScarletVioletPokemonReadOnly.read(wire(plain(species = 0))) is ScarletVioletSlotRead.Empty)
        val tail = record.copyOf(); tail[0x148] = (tail[0x148].toInt() xor 1).toByte()
        assertTrue(ScarletVioletPokemonReadOnly.read(tail) is ScarletVioletSlotRead.Pokemon)
    }

    @Test fun bothTitlesAndSizeExtremesPreserveBoxRideAndPartyBoundaries() {
        val record = wire(plain())
        for (game in listOf(50, 51)) for (size in listOf(sizes.first(), sizes.last())) {
            val source = save(game, size, 2, mapOf(0 to record, 29 to record, 30 to record, 959 to record,
                960 to record, 961 to record, 989 to record), mapOf(0 to record, 1 to record, 5 to record))
            val before = source.copyOf(); val storage = ScarletVioletStorageReadOnly.read(source)
            assertEquals(game, storage.version.gameId); assertEquals(2, storage.partyCount)
            assertEquals(4, storage.occupiedBoxSlots); assertEquals(0, storage.unreadableBoxSlots)
            assertEquals(982, (storage.rideLegend as ScarletVioletSlotRead.Pokemon).value.species)
            for ((box, slot) in listOf(0 to 0, 0 to 29, 1 to 0, 31 to 29))
                assertEquals(982, (storage.box(box)[slot].read as ScarletVioletSlotRead.Pokemon).value.species)
            assertEquals((1..6).toList(), storage.party().map { it.position }); assertTrue(storage.party()[5].read is ScarletVioletSlotRead.Empty)
            for (box in 0..31) assertEquals((1..30).toList(), storage.box(box).map { it.position })
            for (box in listOf(-1, 32)) assertThrows(IllegalArgumentException::class.java) { storage.box(box) }
            assertThrows(UnsupportedOperationException::class.java) { (storage.box(0) as MutableList<ScarletVioletStorageSlot>).clear() }
            assertArrayEquals(before, source)
        }
    }

    @Test fun corruptBoxRideAndPartyRecordsStayIsolatedAndBadCountRetainsIdentityOnly() {
        val good = wire(plain()); val bad = good.copyOf().also { it[80] = (it[80].toInt() xor 1).toByte() }
        val storage = ScarletVioletStorageReadOnly.read(save(count = 3, boxes = mapOf(0 to bad, 1 to good, 960 to bad), party = mapOf(0 to bad, 2 to good)))
        assertEquals(2, storage.occupiedBoxSlots); assertEquals(1, storage.unreadableBoxSlots)
        assertTrue(storage.rideLegend is ScarletVioletSlotRead.Invalid)
        assertTrue(storage.box(0)[0].read is ScarletVioletSlotRead.Invalid); assertTrue(storage.box(0)[1].read is ScarletVioletSlotRead.Pokemon)
        assertTrue(storage.party()[0].read is ScarletVioletSlotRead.Invalid); assertTrue(storage.party()[1].read is ScarletVioletSlotRead.Invalid)
        assertTrue(storage.party()[2].read is ScarletVioletSlotRead.Pokemon)
        for (count in listOf(7, 255)) {
            val source = save(count = count); val bound = session(source)
            assertThrows(IllegalArgumentException::class.java) { ScarletVioletStorageReadOnly.read(source) }
            assertTrue(bound.supports(SwitchReadCapability.SAVE_IDENTITY)); assertNull(bound.scarletVioletStorage)
            assertNotNull(bound.storageUnavailableReason)
            for (capability in SwitchReadCapability.values()) if (capability !in setOf(SwitchReadCapability.SAVE_IDENTITY, SwitchReadCapability.POKEDEX)) assertFalse(bound.supports(capability))
            assertTrue(bound.supports(SwitchReadCapability.POKEDEX)); assertEquals(400, bound.scarletVioletPokedex!!.regions.single().entries.size)
        }
    }

    @Test fun emptyFullHintsAndSnapshotMutationNeverGrantWritesOrOtherReaders() {
        val empty = ScarletVioletStorageReadOnly.read(save())
        assertEquals(0, empty.partyCount); assertEquals(0, empty.occupiedBoxSlots); assertTrue(empty.party().all { it.read is ScarletVioletSlotRead.Empty })
        assertTrue(empty.rideLegend is ScarletVioletSlotRead.Empty)
        val record = wire(plain()); val source = save(game = 51, count = 6,
            boxes = (0..960).associateWith { record }, party = (0 until 6).associateWith { record })
        for (hint in SwitchGameFamily.values()) {
            val bound = session(source, hint); val storage = bound.scarletVioletStorage!!
            assertEquals("Pokémon Violet", bound.displayName); assertEquals(960, storage.occupiedBoxSlots)
            assertEquals(6, storage.partyCount); assertEquals(0, storage.unreadableBoxSlots)
            assertNull(bound.letsGoStorage); assertNull(bound.swordShieldStorage); assertNull(bound.legendsArceusStorage)
            assertNull(bound.bdspStorage); assertNull(bound.swordShieldTrainer)
            for (capability in SwitchReadCapability.values()) assertEquals(capability in setOf(SwitchReadCapability.SAVE_IDENTITY,
                SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS, SwitchReadCapability.POKEDEX), bound.supports(capability))
            assertFalse(bound.profile.writesEnabled); assertTrue(bound.isReadOnly)
        }
        val bound = session(source); source.fill(0); bound.snapshot.bytesCopy().fill(0)
        assertEquals(960, bound.scarletVioletStorage!!.occupiedBoxSlots)
        assertEquals(982, (bound.scarletVioletStorage!!.box(31)[29].read as ScarletVioletSlotRead.Pokemon).value.species)
        assertEquals(982, (bound.scarletVioletStorage!!.rideLegend as ScarletVioletSlotRead.Pokemon).value.species)
    }

    @Test fun fullNativeContainerGatePrecedesEveryMetadataExtraction() {
        val good = save(); val badHash = good.copyOf().also { it[123] = (it[123].toInt() xor 1).toByte() }
        for (source in listOf(ByteArray(1), badHash, fixture(game = 52), fixture(blocks = core(50).drop(1)),
            fixture(blocks = core(50).filter { it.key != 0xE3E89BD1L })))
            assertThrows(IllegalArgumentException::class.java) { ScarletVioletStorageReadOnly.read(source) }
    }
}
