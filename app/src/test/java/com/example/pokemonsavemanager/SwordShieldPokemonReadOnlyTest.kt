package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class SwordShieldPokemonReadOnlyTest {
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
        word(b, 0x14, 267); b[0x16] = 16; uint(b, 0x1C, (0x1234L shl 16) or (0x1234 xor 0xBEEF xor 0xCAFE).toLong())
        b[0x20] = 24; b[0x21] = 3; b[0x22] = 8; b[0x24] = 7
        listOf(252, 1, 2, 3, 0, 252).forEachIndexed { i, v -> b[0x26 + i] = v.toByte() }
        label(b, 0x58, "Éclair ♀"); label(b, 0xF8, "雪")
        repeat(4) { word(b, 0x72 + 2 * it, 800 + it); b[0x7A + it] = (10 + it).toByte(); b[0x7E + it] = it.toByte(); word(b, 0x82 + 2 * it, 500 + it) }
        word(b, 0x8A, 77); uint(b, 0x8C, listOf(31, 0, 1, 2, 3, 30).mapIndexed { i, v -> v.toLong() shl (5 * i) }.fold(0xC0000000L) { a, v -> a or v })
        b[0x90] = 10; b[0x148] = 67; word(b, 0x14A, 188)
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
    private class BlockStream(key: Long) {
        private var value = key; private var shift = 0
        init { repeat(java.lang.Long.bitCount(key)) { advance() } }
        private fun advance() { value = (value xor (value shl 2)) and 0xFFFFFFFFL; value = value xor (value ushr 15); value = (value xor (value shl 13)) and 0xFFFFFFFFL }
        fun next(): Int { val v = ((value ushr shift) and 255).toInt(); shift += 8; if (shift == 32) { shift = 0; advance() }; return v }
        fun word(): Long = (0..3).fold(0L) { a, i -> a or (next().toLong() shl (8 * i)) }
    }
    private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val intro = hex("9ec99cd70ed33c44fb9303dceb39b42a1947e9634ba2334416bf82a2ba6355b63d9df24b5f7b6ab2621dc21b68e5c8b53a059000e8a8103de2ecf00cb2ed4f6d")
    private val outro = hex("d6c01c598bc8b8cb46e153fc828c757513e045df32693c75f059f8d9a25fb217e08052dbea8973997579afcb2e8007e6f126e0030ae66ff641bf7e59c2ae55fd")
    private val pad = hex("a092d10607db32a1ae01f5c51e844fe353ca37f4a7b04da018b7c297da5f532b75fa4816f8d48a6f6105f4e2fd04b5a30ffc4492cb32e61bb9b12e01b0565336d2d1503dde5b2e0e52fddf2f7bca6350a4675d2317c052e1a6307c2bb670365b2a276933f5637b363f269ba3ed7a5300a448b3509e14a052de7e102b1b776e")
    private fun fixture(game: Int = 44, count: Int = 0, boxEntries: Map<Int, ByteArray> = emptyMap(), partyEntries: Map<Int, ByteArray> = emptyMap()): ByteArray {
        val boxes = ByteArray(0x50A00); val party = ByteArray(0x814)
        boxEntries.forEach { (i, record) -> record.copyInto(boxes, i * 344) }
        partyEntries.forEach { (i, record) -> record.copyInto(party, i * 344) }
        party[2064] = count.toByte()
        val blocks = linkedMapOf(0x0D66012CL to boxes, 0x2985FE5DL to party, 0x4716C404L to ByteArray(0x4B00),
            0xF25C070EL to ByteArray(0x110).also { it[0xA4] = game.toByte() })
        val out = ByteArrayOutputStream()
        fun block(key: Long, data: ByteArray) {
            val stream = BlockStream(key)
            val header = ByteArray(9); uint(header, 0, key); header[4] = (4 xor stream.next()).toByte()
            uint(header, 5, data.size.toLong() xor stream.word()); out.write(header)
            for (byte in data) out.write((byte.toInt() and 255) xor stream.next())
        }
        blocks.forEach { (key, data) -> block(key, data) }
        block(0xFFFFFFFFL, ByteArray(0x1716B3 - 32 - out.size() - 9))
        val payload = out.toByteArray()
        for (i in payload.indices) payload[i] = (payload[i].toInt() xor pad[i % pad.size].toInt()).toByte()
        val hash = MessageDigest.getInstance("SHA-256"); hash.update(intro); hash.update(payload); hash.update(outro)
        return payload + hash.digest()
    }

    @Test fun nativePk8FieldsAndIndependentFixedDigestRemainReadOnly() {
        val input = wire(plain()); val before = input.copyOf()
        // Fixed digest generated independently from the field table and pinned native permutation facts.
        assertEquals("a8947436f4887fbb803d5476663b756873c9278757144e55266ea77b11a1b36a", MessageDigest.getInstance("SHA-256").digest(input).joinToString("") { "%02x".format(it.toInt() and 255) })
        val p = (SwordShieldPokemonReadOnly.read(input) as SwordShieldSlotRead.Pokemon).value
        assertEquals(25, p.species); assertEquals("Éclair ♀", p.nickname); assertEquals("雪", p.originalTrainer)
        assertEquals(7, p.form); assertEquals(2, p.gender); assertEquals(67, p.cachedLevel)
        assertEquals(0xFEDCBA98L, p.experience); assertEquals(0xF003E001L, p.encryptionConstant)
        assertEquals(0xBEEF, p.tid); assertEquals(0xCAFE, p.sid); assertTrue(p.shiny); assertTrue(p.isEgg)
        assertEquals(24, p.nature); assertEquals(3, p.statNature); assertEquals(77, p.currentHp); assertEquals(188, p.maximumHp)
        assertEquals(1589, p.heldItem); assertEquals(267, p.ability); assertEquals(10, p.dynamaxLevel); assertTrue(p.canGigantamax)
        assertEquals(listOf(31, 0, 1, 2, 3, 30), p.ivs); assertEquals(listOf(252, 1, 2, 3, 0, 252), p.evs)
        assertEquals((800..803).toList(), p.moves.map { it.id }); assertEquals((10..13).toList(), p.moves.map { it.pp })
        assertEquals((0..3).toList(), p.moves.map { it.ppUps }); assertEquals((500..503).toList(), p.relearnMoves)
        assertEquals((100..104).toList(), p.cachedStats); assertArrayEquals(before, input)
        assertEquals("Pikachu", SwordShieldSpeciesNames.name(25)); assertEquals("Calyrex", SwordShieldSpeciesNames.name(898))
        assertThrows(UnsupportedOperationException::class.java) { (p.ivs as MutableList<Int>)[0] = 0 }
    }

    @Test fun allSelectorsAndUnsignedSeedsUseEightyByteBlocksAndIndependentTailStream() {
        for (selector in 0..31) for (high in listOf(0L, 0x80000000L, 0xFFF00000L)) {
            val p = plain(high or (selector.toLong() shl 13) or 1L)
            val read = (SwordShieldPokemonReadOnly.read(wire(p)) as SwordShieldSlotRead.Pokemon).value
            assertEquals("Éclair ♀", read.nickname); assertEquals(67, read.cachedLevel); assertEquals(188, read.maximumHp)
            assertEquals("雪", read.originalTrainer); assertEquals(listOf(31, 0, 1, 2, 3, 30), read.ivs)
        }
    }

    @Test fun badLengthSanityChecksumSpeciesAndPlaintextNeverFallBackToRepair() {
        for (size in listOf(0, 8, 328, 343, 345)) assertTrue(SwordShieldPokemonReadOnly.read(ByteArray(size)) is SwordShieldSlotRead.Invalid)
        val record = wire(plain())
        for (offset in listOf(4, 6, 8, 87, 167, 247, 327)) {
            val bad = record.copyOf(); bad[offset] = (bad[offset].toInt() xor 1).toByte()
            assertTrue(SwordShieldPokemonReadOnly.read(bad) is SwordShieldSlotRead.Invalid)
        }
        assertTrue(SwordShieldPokemonReadOnly.read(wire(plain(species = 899))) is SwordShieldSlotRead.Invalid)
        assertTrue(SwordShieldPokemonReadOnly.read(plain()) is SwordShieldSlotRead.Invalid)
        assertTrue(SwordShieldPokemonReadOnly.read(ByteArray(344)) is SwordShieldSlotRead.Empty)
        assertTrue(SwordShieldPokemonReadOnly.read(wire(plain(species = 0))) is SwordShieldSlotRead.Empty)
        // The tail is not covered by the PK8 checksum. Report cached values, never a validity/legality claim for stats.
        val changedTail = record.copyOf(); changedTail[0x148] = (changedTail[0x148].toInt() xor 1).toByte()
        assertTrue(SwordShieldPokemonReadOnly.read(changedTail) is SwordShieldSlotRead.Pokemon)
    }

    @Test fun bothTitlesPreserveNativeBoxAndPartyPositionsAndHideResidualPartyData() {
        for (game in listOf(44, 45)) {
            val source = fixture(game, 2, mapOf(0 to wire(plain()), 29 to wire(plain(species = 133)), 30 to wire(plain(species = 810)), 959 to wire(plain(species = 898))),
                mapOf(0 to wire(plain(species = 25)), 1 to wire(plain(species = 133)), 5 to wire(plain(species = 898))))
            val before = source.copyOf(); val storage = SwordShieldStorageReadOnly.read(source)
            assertEquals(game, storage.version.gameId); assertEquals(2, storage.partyCount); assertEquals(4, storage.occupiedBoxSlots)
            assertEquals(25, ((storage.box(0)[0].read as SwordShieldSlotRead.Pokemon).value.species))
            assertEquals(133, ((storage.box(0)[29].read as SwordShieldSlotRead.Pokemon).value.species))
            assertEquals(810, ((storage.box(1)[0].read as SwordShieldSlotRead.Pokemon).value.species))
            assertEquals(898, ((storage.box(31)[29].read as SwordShieldSlotRead.Pokemon).value.species))
            assertEquals((1..6).toList(), storage.party().map { it.position }); assertTrue(storage.party()[5].read is SwordShieldSlotRead.Empty)
            assertArrayEquals(before, source)
            for (box in 0..31) assertEquals((1..30).toList(), storage.box(box).map { it.position })
            for (box in listOf(-1, 32)) assertThrows(IllegalArgumentException::class.java) { storage.box(box) }
            assertThrows(UnsupportedOperationException::class.java) { (storage.box(0) as MutableList<SwordShieldStorageSlot>).clear() }
        }
    }

    @Test fun damagedPk8SlotsStayIsolatedAndCountedWithoutDiscardingOtherRecords() {
        val broken = wire(plain()).also { it[80] = (it[80].toInt() xor 1).toByte() }
        val storage = SwordShieldStorageReadOnly.read(fixture(count = 3, boxEntries = mapOf(0 to broken, 1 to wire(plain())),
            partyEntries = mapOf(0 to broken, 2 to wire(plain()))))
        assertEquals(2, storage.occupiedBoxSlots); assertEquals(1, storage.unreadableBoxSlots)
        assertTrue(storage.box(0)[0].read is SwordShieldSlotRead.Invalid); assertTrue(storage.box(0)[1].read is SwordShieldSlotRead.Pokemon)
        assertTrue(storage.party()[0].read is SwordShieldSlotRead.Invalid); assertTrue(storage.party()[1].read is SwordShieldSlotRead.Invalid)
        assertTrue(storage.party()[2].read is SwordShieldSlotRead.Pokemon)
    }

    @Test fun partyCountBoundsDoNotSuppressVerifiedIdentityOrGrantWrites() {
        for (count in listOf(7, 255)) {
            val source = fixture(count = count)
            assertThrows(IllegalArgumentException::class.java) { SwordShieldStorageReadOnly.read(source) }
            val session = SwitchReadSession(SwitchReadSnapshot.capture(SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, source))
            assertTrue(session.supports(SwitchReadCapability.SAVE_IDENTITY)); assertFalse(session.supports(SwitchReadCapability.PARTY))
            assertFalse(session.supports(SwitchReadCapability.PC_OR_BOXES)); assertFalse(session.supports(SwitchReadCapability.POKEMON_DETAILS))
            assertNotNull(session.storageUnavailableReason); assertFalse(session.profile.writesEnabled)
        }
        assertThrows(IllegalArgumentException::class.java) { SwordShieldStorageReadOnly.read(ByteArray(1)) }
        val damaged = fixture().also { it[127] = (it[127].toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) { SwordShieldStorageReadOnly.read(damaged) }
    }

    @Test fun emptyAndFullStorageAndPartyAreBoundedAndMetadataSurvivesInputMutation() {
        val empty = SwordShieldStorageReadOnly.read(fixture())
        assertEquals(0, empty.occupiedBoxSlots); assertEquals(0, empty.partyCount)
        assertTrue(empty.party().all { it.read is SwordShieldSlotRead.Empty })
        val record = wire(plain())
        val source = fixture(45, 6, (0 until 960).associateWith { record }, (0 until 6).associateWith { record })
        val session = SwitchReadSession(SwitchReadSnapshot.capture(SwitchGameFamily.LEGENDS_ZA, source))
        source.fill(0); session.snapshot.bytesCopy().fill(0)
        val storage = session.swordShieldStorage!!
        assertEquals(960, storage.occupiedBoxSlots); assertEquals(6, storage.partyCount)
        assertEquals(0, storage.unreadableBoxSlots); assertEquals("Pokémon Shield", session.displayName)
        assertTrue(storage.party().all { it.read is SwordShieldSlotRead.Pokemon }); assertNotNull(session.swordShieldStorage)
        assertTrue(session.supports(SwitchReadCapability.PARTY)); assertTrue(session.supports(SwitchReadCapability.PC_OR_BOXES))
        assertTrue(session.supports(SwitchReadCapability.POKEMON_DETAILS)); assertFalse(session.supports(SwitchReadCapability.PSE_BANK_READ_ONLY))
        assertFalse(session.profile.writesEnabled); assertTrue(session.isReadOnly)
    }
}
