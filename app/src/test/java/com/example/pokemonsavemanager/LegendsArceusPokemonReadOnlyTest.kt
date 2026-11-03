package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class LegendsArceusPokemonReadOnlyTest {
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
        for (i in 8 until 0x168 step 2) value += (bytes[i].toInt() and 255) + ((bytes[i + 1].toInt() and 255) shl 8)
        word(bytes, 6, value and 65535)
    }
    private fun plain(ec: Long = 0xF003E001L, species: Int = 905, stored: Boolean = false): ByteArray = ByteArray(if (stored) 360 else 376).also { b ->
        uint(b, 0, ec); word(b, 8, species)
        word(b, 0x0C, 0xBEEF); word(b, 0x0E, 0xCAFE); uint(b, 0x10, 0xFEDCBA98L)
        b[0x16] = 96; uint(b, 0x1C, (0x1234L shl 16) or (0x1234 xor 0xBEEF xor 0xCAFE).toLong())
        b[0x20] = 24; b[0x21] = 3; b[0x22] = 8; b[0x24] = 1
        label(b, 0x60, "Éclair ♀"); label(b, 0x110, "雪")
        repeat(4) { word(b, 0x54 + 2 * it, 800 + it); b[0x5C + it] = (10 + it).toByte(); word(b, 0x8A + 2 * it, 500 + it) }
        word(b, 0x92, 77); uint(b, 0x94, listOf(31, 0, 1, 2, 3, 30).mapIndexed { i, v -> v.toLong() shl (5 * i) }.fold(0xC0000000L) { a, v -> a or v })
        listOf(10, 1, 2, 3, 0, 10).forEachIndexed { i, v -> b[0xA4 + i] = v.toByte() }
        b[0x12A] = 200.toByte(); b[0xD8] = 123; b[0xD4] = 1; b[0x137] = 4
        if (!stored) { b[0x168] = 67; word(b, 0x16A, 188); repeat(5) { word(b, 0x16C + 2 * it, 100 + it) } }
        checksum(b)
    }
    /** Test serializer maps canonical blocks back into native positions, then encrypts two independent streams. */
    private fun wire(plain: ByteArray): ByteArray {
        val output = plain.copyOf()
        val ec = (0..3).fold(0L) { a, i -> a or ((plain[i].toLong() and 255) shl (8 * i)) }
        val order = nativeOrder[((ec ushr 13).toInt() and 31) % 24]
        repeat(4) { canonical -> plain.copyInto(output, 8 + (order[canonical] - '0') * 88, 8 + canonical * 88, 8 + (canonical + 1) * 88) }
        for (range in listOf(8 until 0x168 step 2, 0x168 until plain.size step 2)) {
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
    private fun core(game: Int, count: Int = 0, pastures: Map<Int, ByteArray> = emptyMap(), party: Map<Int, ByteArray> = emptyMap()): List<Block> = listOf(
        Block(0x47E1CEAB, data = ByteArray(0x54600).also { bytes -> pastures.forEach { (i, record) -> record.copyInto(bytes, i * 360) } }),
        Block(0x2985FE5D, data = ByteArray(0x8D4).also { bytes ->
            bytes[6 * 376] = count.toByte(); party.forEach { (i, record) -> record.copyInto(bytes, i * 376) }
        }),
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

    private fun save(count: Int = 0, size: Int = sizes[0], pastures: Map<Int, ByteArray> = emptyMap(),
        party: Map<Int, ByteArray> = emptyMap()): ByteArray = fixture(size = size, blocks = core(47, count, pastures, party))
    private fun session(source: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, source))

    @Test fun nativePa8OffsetsAlphaNobleTrainingAndDetachedMetadata() {
        for (stored in listOf(false, true)) {
            val input = wire(plain(stored = stored)); val before = input.copyOf()
            val p = (LegendsArceusPokemonReadOnly.read(input) as LegendsArceusSlotRead.Pokemon).value
            assertEquals(905, p.species); assertEquals("Enamorus", LegendsArceusSpeciesNames.name(p.species))
            assertEquals("Éclair ♀", p.nickname); assertEquals("雪", p.originalTrainer); assertEquals(1, p.form); assertEquals(2, p.gender)
            assertEquals(0xFEDCBA98L, p.experience); assertEquals(0xF003E001L, p.encryptionConstant)
            assertEquals(0xBEEF, p.tid); assertEquals(0xCAFE, p.sid); assertTrue(p.shiny); assertTrue(p.isEgg)
            assertTrue(p.isAlpha); assertTrue(p.isNoble); assertEquals(24, p.nature); assertEquals(3, p.statNature)
            assertEquals(77, p.currentHp); assertEquals(4, p.ball)
            assertEquals(200, p.originalTrainerFriendship); assertEquals(123, p.handlingTrainerFriendship); assertEquals(1, p.currentHandler)
            assertEquals(listOf(31, 0, 1, 2, 3, 30), p.ivs); assertEquals(listOf(10, 1, 2, 3, 0, 10), p.ganbaruValues)
            assertEquals((800..803).toList(), p.moves.map { it.id }); assertEquals((10..13).toList(), p.moves.map { it.pp })
            assertEquals((500..503).toList(), p.relearnMoves)
            if (stored) { assertNull(p.cachedLevel); assertNull(p.maximumHp); assertNull(p.cachedStats) }
            else { assertEquals(67, p.cachedLevel!!.toInt()); assertEquals(188, p.maximumHp!!.toInt()); assertEquals((100..104).toList(), p.cachedStats) }
            assertArrayEquals(before, input); input.fill(0); assertEquals(905, p.species)
            assertThrows(UnsupportedOperationException::class.java) { (p.ivs as MutableList<Int>)[0] = 0 }
            assertThrows(UnsupportedOperationException::class.java) { (p.ganbaruValues as MutableList<Int>)[0] = 0 }
            assertThrows(UnsupportedOperationException::class.java) { (p.moves as MutableList<LegendsArceusMoveRead>).clear() }
        }
        assertEquals("906a4e7762cf05b01a59a509d80316fde41c994483ba347c849d8019a1311b42", MessageDigest.getInstance("SHA-256").digest(wire(plain())).joinToString("") { "%02x".format(it.toInt() and 255) })
    }

    @Test fun everySelectorHighBitConstantAndBothNativeRecordWidths() {
        for (selector in 0..31) for (high in listOf(0L, 0x80000000L, 0xFFF00000L)) for (stored in listOf(false, true)) {
            val p = (LegendsArceusPokemonReadOnly.read(wire(plain(high or (selector.toLong() shl 13) or 1L, stored = stored))) as LegendsArceusSlotRead.Pokemon).value
            assertEquals(905, p.species); assertEquals("Éclair ♀", p.nickname); assertEquals("雪", p.originalTrainer)
            assertEquals(listOf(10, 1, 2, 3, 0, 10), p.ganbaruValues); assertTrue(p.isAlpha); assertTrue(p.isNoble)
            if (stored) assertNull(p.cachedLevel) else { assertEquals(67, p.cachedLevel!!.toInt()); assertEquals(188, p.maximumHp!!.toInt()) }
        }
    }

    @Test fun invalidPa8IsIsolatedWithNoPlaintextRepairOrPk8Fallback() {
        for (length in listOf(0, 8, 328, 344, 359, 361, 375, 377))
            assertTrue(LegendsArceusPokemonReadOnly.read(ByteArray(length)) is LegendsArceusSlotRead.Invalid)
        for (stored in listOf(false, true)) {
            val record = wire(plain(stored = stored))
            for (offset in listOf(4, 6, 8, 95, 183, 271, 359)) {
                val bad = record.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }
                assertTrue(LegendsArceusPokemonReadOnly.read(bad) is LegendsArceusSlotRead.Invalid)
            }
            for (species in listOf(906, 1000, 65535)) assertTrue(LegendsArceusPokemonReadOnly.read(wire(plain(species = species, stored = stored))) is LegendsArceusSlotRead.Invalid)
            assertTrue(LegendsArceusPokemonReadOnly.read(plain(stored = stored)) is LegendsArceusSlotRead.Invalid)
            assertTrue(LegendsArceusPokemonReadOnly.read(ByteArray(record.size)) is LegendsArceusSlotRead.Empty)
            assertTrue(LegendsArceusPokemonReadOnly.read(wire(plain(species = 0, stored = stored))) is LegendsArceusSlotRead.Empty)
        }
        val record = wire(plain()).also { it[360] = (it[360].toInt() xor 1).toByte() }
        assertTrue(LegendsArceusPokemonReadOnly.read(record) is LegendsArceusSlotRead.Pokemon)
    }

    @Test fun bothNativeSizesPreserveEveryPastureBoundaryAndHideResidualParty() {
        val stored = wire(plain(stored = true)); val team = wire(plain())
        for (size in sizes) {
            val source = save(2, size, mapOf(0 to stored, 29 to stored, 30 to stored, 959 to stored), mapOf(0 to team, 1 to team, 5 to team))
            val before = source.copyOf(); val result = LegendsArceusStorageReadOnly.read(source)
            assertEquals(2, result.partyCount); assertEquals(4, result.occupiedPastureSlots); assertEquals(0, result.unreadablePastureSlots)
            for ((pasture, slot) in listOf(0 to 0, 0 to 29, 1 to 0, 31 to 29))
                assertEquals(905, (result.pasture(pasture)[slot].read as LegendsArceusSlotRead.Pokemon).value.species)
            for (pasture in 0..31) assertEquals((1..30).toList(), result.pasture(pasture).map { it.position })
            assertEquals((1..6).toList(), result.party().map { it.position }); assertTrue(result.party()[5].read is LegendsArceusSlotRead.Empty)
            for (index in listOf(-1, 32)) assertThrows(IllegalArgumentException::class.java) { result.pasture(index) }
            assertThrows(UnsupportedOperationException::class.java) { (result.pasture(0) as MutableList<LegendsArceusPastureSlot>).clear() }
            assertArrayEquals(before, source)
        }
    }

    @Test fun badCountRetainsOnlyIdentityAndCorruptSlotsNeverSuppressNeighbors() {
        val stored = wire(plain(stored = true)); val team = wire(plain())
        val bad = stored.copyOf().also { it[100] = (it[100].toInt() xor 1).toByte() }
        val badParty = team.copyOf().also { it[100] = (it[100].toInt() xor 1).toByte() }
        val storage = LegendsArceusStorageReadOnly.read(save(3, pastures = mapOf(0 to bad, 1 to stored), party = mapOf(0 to badParty, 2 to team)))
        assertEquals(2, storage.occupiedPastureSlots); assertEquals(1, storage.unreadablePastureSlots)
        assertTrue(storage.pasture(0)[0].read is LegendsArceusSlotRead.Invalid); assertTrue(storage.pasture(0)[1].read is LegendsArceusSlotRead.Pokemon)
        assertTrue(storage.party()[0].read is LegendsArceusSlotRead.Invalid); assertTrue(storage.party()[1].read is LegendsArceusSlotRead.Invalid)
        assertTrue(storage.party()[2].read is LegendsArceusSlotRead.Pokemon)
        for (count in listOf(7, 255)) {
            val source = save(count); val bound = session(source)
            assertThrows(IllegalArgumentException::class.java) { LegendsArceusStorageReadOnly.read(source) }
            assertTrue(bound.supports(SwitchReadCapability.SAVE_IDENTITY)); assertNull(bound.legendsArceusStorage)
            assertNotNull(bound.storageUnavailableReason)
            for (capability in SwitchReadCapability.values()) if (capability !in setOf(SwitchReadCapability.SAVE_IDENTITY, SwitchReadCapability.POKEDEX)) assertFalse(bound.supports(capability))
            assertTrue(bound.supports(SwitchReadCapability.POKEDEX)); assertEquals(242, bound.legendsArceusPokedex!!.entries.size)
        }
    }

    @Test fun fullAndEmptyCapacitiesAndHintsNeverGrantWritesOrOtherReaders() {
        val empty = LegendsArceusStorageReadOnly.read(save())
        assertEquals(0, empty.partyCount); assertEquals(0, empty.occupiedPastureSlots); assertTrue(empty.party().all { it.read is LegendsArceusSlotRead.Empty })
        val stored = wire(plain(stored = true)); val team = wire(plain())
        val source = save(6, pastures = (0 until 960).associateWith { stored }, party = (0 until 6).associateWith { team })
        for (hint in SwitchGameFamily.values()) {
            val bound = session(source, hint); val storage = bound.legendsArceusStorage!!
            assertEquals(SwitchGameFamily.LEGENDS_ARCEUS, bound.profile.family); assertEquals(960, storage.occupiedPastureSlots)
            assertEquals(6, storage.partyCount); assertEquals(0, storage.unreadablePastureSlots)
            assertNull(bound.letsGoStorage); assertNull(bound.swordShieldStorage); assertNull(bound.bdspStorage); assertNull(bound.swordShieldTrainer)
            for (capability in SwitchReadCapability.values()) assertEquals(capability in setOf(SwitchReadCapability.SAVE_IDENTITY,
                SwitchReadCapability.PARTY, SwitchReadCapability.PASTURES, SwitchReadCapability.POKEMON_DETAILS, SwitchReadCapability.POKEDEX), bound.supports(capability))
            assertTrue(bound.isReadOnly); assertFalse(bound.profile.writesEnabled)
        }
        val bound = session(source); source.fill(0); bound.snapshot.bytesCopy().fill(0)
        assertEquals(960, bound.legendsArceusStorage!!.occupiedPastureSlots)
        assertEquals(905, (bound.legendsArceusStorage!!.pasture(31)[29].read as LegendsArceusSlotRead.Pokemon).value.species)
        assertEquals(listOf("Wyrdeer", "Kleavor", "Ursaluna", "Basculegion", "Sneasler", "Overqwil", "Enamorus"), (899..905).map { LegendsArceusSpeciesNames.name(it) })
    }

    @Test fun fullContainerGateIsMandatoryBeforeAnyExtractedMetadataEscapes() {
        val good = save(); val damaged = good.copyOf().also { it[123] = (it[123].toInt() xor 1).toByte() }
        for (source in listOf(ByteArray(1), damaged, fixture(game = 46), fixture(blocks = core(47).drop(1))))
            assertThrows(IllegalArgumentException::class.java) { LegendsArceusStorageReadOnly.read(source) }
    }
}
