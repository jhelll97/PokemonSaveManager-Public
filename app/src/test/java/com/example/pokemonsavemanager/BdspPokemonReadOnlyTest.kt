package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspPokemonReadOnlyTest {
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
        word(b, 0x14, 267); b[0x16] = 0; uint(b, 0x1C, (0x1234L shl 16) or (0x1234 xor 0xBEEF xor 0xCAFE).toLong())
        b[0x20] = 24; b[0x21] = 3; b[0x22] = 8; b[0x24] = 7
        listOf(252, 1, 2, 3, 0, 252).forEachIndexed { i, v -> b[0x26 + i] = v.toByte() }
        label(b, 0x58, "Éclair ♀"); label(b, 0xF8, "雪")
        repeat(4) { word(b, 0x72 + 2 * it, 800 + it); b[0x7A + it] = (10 + it).toByte(); b[0x7E + it] = it.toByte(); word(b, 0x82 + 2 * it, 500 + it) }
        word(b, 0x8A, 77); uint(b, 0x8C, listOf(31, 0, 1, 2, 3, 30).mapIndexed { i, v -> v.toLong() shl (5 * i) }.fold(0xC0000000L) { a, v -> a or v })
        listOf(255, 1, 2, 3, 4, 5).forEachIndexed { i, v -> b[0x2C + i] = v.toByte() }
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
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private fun sign(source: ByteArray): ByteArray {
        val copy = source.copyOf(); copy.fill(0, 0xE9818, 0xE9828)
        MessageDigest.getInstance("MD5").digest(copy).copyInto(copy, 0xE9818)
        return copy
    }
    private fun fixture(layout: Int = 3, game: Int = 0, count: Int = 0,
        boxes: Map<Int, ByteArray> = emptyMap(), party: Map<Int, ByteArray> = emptyMap()): ByteArray {
        val bytes = ByteArray(layouts[layout].first); uint(bytes, 0, layouts[layout].second.toLong())
        bytes[0x79BDF] = game.toByte(); bytes[0x148A8] = count.toByte()
        boxes.forEach { (i, record) -> record.copyInto(bytes, 0x14EF4 + i * 344) }
        party.forEach { (i, record) -> record.copyInto(bytes, 0x14098 + i * 344) }
        return sign(bytes)
    }
    private fun session(bytes: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, bytes))

    @Test fun encryptedPb8FieldsUnsignedValuesContestAndFriendshipRemainReadOnly() {
        val input = wire(plain()); val before = input.copyOf()
        assertEquals("e22b62b900ca7d2c556147bd9787e3a73fb79b2d89508f2a8bfc1a1c4545bd84", MessageDigest.getInstance("SHA-256").digest(input).joinToString("") { "%02x".format(it.toInt() and 255) })
        val p = (BdspPokemonReadOnly.read(input) as BdspSlotRead.Pokemon).value
        assertEquals(25, p.species); assertEquals("Éclair ♀", p.nickname); assertEquals("雪", p.originalTrainer)
        assertEquals(7, p.form); assertEquals(2, p.gender); assertEquals(67, p.cachedLevel)
        assertEquals(0xFEDCBA98L, p.experience); assertEquals(0xF003E001L, p.encryptionConstant)
        assertEquals(0xBEEF, p.tid); assertEquals(0xCAFE, p.sid); assertTrue(p.shiny); assertTrue(p.isEgg)
        assertEquals(24, p.nature); assertEquals(3, p.statNature); assertEquals(77, p.currentHp); assertEquals(188, p.maximumHp)
        assertEquals(1589, p.heldItem); assertEquals(267, p.ability)
        assertEquals(200, p.originalTrainerFriendship); assertEquals(123, p.handlingTrainerFriendship); assertEquals(1, p.currentHandler)
        assertEquals(listOf(255, 1, 2, 3, 4, 5), p.contestValues)
        assertEquals(listOf(31, 0, 1, 2, 3, 30), p.ivs); assertEquals(listOf(252, 1, 2, 3, 0, 252), p.evs)
        assertEquals((800..803).toList(), p.moves.map { it.id }); assertEquals((10..13).toList(), p.moves.map { it.pp })
        assertEquals((0..3).toList(), p.moves.map { it.ppUps }); assertEquals((500..503).toList(), p.relearnMoves)
        assertEquals((100..104).toList(), p.cachedStats); assertArrayEquals(before, input)
        assertThrows(UnsupportedOperationException::class.java) { (p.contestValues as MutableList<Int>)[0] = 0 }
        assertThrows(UnsupportedOperationException::class.java) { (p.ivs as MutableList<Int>)[0] = 0 }
    }

    @Test fun allSelectorsAndHighBitConstantsUseTheNativeBlocksAndIndependentTailStream() {
        for (selector in 0..31) for (high in listOf(0L, 0x80000000L, 0xFFF00000L)) {
            val p = plain(high or (selector.toLong() shl 13) or 1L, 493)
            val read = (BdspPokemonReadOnly.read(wire(p)) as BdspSlotRead.Pokemon).value
            assertEquals(493, read.species); assertEquals("Éclair ♀", read.nickname); assertEquals("雪", read.originalTrainer)
            assertEquals(67, read.cachedLevel); assertEquals(188, read.maximumHp)
            assertEquals(listOf(255, 1, 2, 3, 4, 5), read.contestValues)
        }
    }

    @Test fun malformedPb8NeverFallsBackToPlaintextOrRepairAndEmptyRecordsStayEmpty() {
        for (length in listOf(0, 8, 328, 343, 345)) assertTrue(BdspPokemonReadOnly.read(ByteArray(length)) is BdspSlotRead.Invalid)
        val record = wire(plain())
        for (offset in listOf(4, 6, 8, 87, 167, 247, 327)) {
            val broken = record.copyOf(); broken[offset] = (broken[offset].toInt() xor 1).toByte()
            assertTrue(BdspPokemonReadOnly.read(broken) is BdspSlotRead.Invalid)
        }
        for (species in listOf(494, 808, 898, 65535)) assertTrue(BdspPokemonReadOnly.read(wire(plain(species = species))) is BdspSlotRead.Invalid)
        assertTrue(BdspPokemonReadOnly.read(plain()) is BdspSlotRead.Invalid)
        assertTrue(BdspPokemonReadOnly.read(ByteArray(344)) is BdspSlotRead.Empty)
        assertTrue(BdspPokemonReadOnly.read(wire(plain(species = 0))) is BdspSlotRead.Empty)
        // The record checksum excludes cached tail bytes; those values are explicitly labeled in UI.
        val tail = record.copyOf(); tail[0x148] = (tail[0x148].toInt() xor 1).toByte()
        assertTrue(BdspPokemonReadOnly.read(tail) is BdspSlotRead.Pokemon)
    }

    @Test fun bothTitlesAndEveryNativeLayoutPreserveBoxBoundariesAndHideResidualParty() {
        val record = wire(plain(species = 493))
        for (layout in layouts.indices) for (game in 0..1) {
            val source = fixture(layout, game, 2, mapOf(0 to record, 29 to record, 30 to record, 1199 to record),
                mapOf(0 to record, 1 to record, 5 to record))
            val before = source.copyOf(); val storage = BdspStorageReadOnly.read(source)
            assertEquals(game, storage.version.romCode); assertEquals(layouts[layout].first, storage.revision.fileSize)
            assertEquals(2, storage.partyCount); assertEquals(4, storage.occupiedBoxSlots); assertEquals(0, storage.unreadableBoxSlots)
            for ((box, slot) in listOf(0 to 0, 0 to 29, 1 to 0, 39 to 29))
                assertEquals(493, (storage.box(box)[slot].read as BdspSlotRead.Pokemon).value.species)
            assertEquals((1..6).toList(), storage.party().map { it.position }); assertTrue(storage.party()[5].read is BdspSlotRead.Empty)
            for (box in 0..39) assertEquals((1..30).toList(), storage.box(box).map { it.position })
            for (box in listOf(-1, 40)) assertThrows(IllegalArgumentException::class.java) { storage.box(box) }
            assertThrows(UnsupportedOperationException::class.java) { (storage.box(0) as MutableList<BdspStorageSlot>).clear() }
            assertArrayEquals(before, source)
        }
    }

    @Test fun corruptBoxAndPartyRecordsRemainIsolatedAndCountBoundsPreserveIdentity() {
        val good = wire(plain()); val bad = good.copyOf().also { it[80] = (it[80].toInt() xor 1).toByte() }
        val storage = BdspStorageReadOnly.read(fixture(count = 3, boxes = mapOf(0 to bad, 1 to good), party = mapOf(0 to bad, 2 to good)))
        assertEquals(2, storage.occupiedBoxSlots); assertEquals(1, storage.unreadableBoxSlots)
        assertTrue(storage.box(0)[0].read is BdspSlotRead.Invalid); assertTrue(storage.box(0)[1].read is BdspSlotRead.Pokemon)
        assertTrue(storage.party()[0].read is BdspSlotRead.Invalid); assertTrue(storage.party()[1].read is BdspSlotRead.Invalid)
        assertTrue(storage.party()[2].read is BdspSlotRead.Pokemon)
        for (count in listOf(7, 255)) {
            val source = fixture(count = count)
            assertThrows(IllegalArgumentException::class.java) { BdspStorageReadOnly.read(source) }
            val bound = session(source)
            assertTrue(bound.supports(SwitchReadCapability.SAVE_IDENTITY)); assertFalse(bound.supports(SwitchReadCapability.PARTY))
            assertFalse(bound.supports(SwitchReadCapability.PC_OR_BOXES)); assertFalse(bound.supports(SwitchReadCapability.POKEMON_DETAILS))
            assertNull(bound.bdspStorage); assertNotNull(bound.storageUnavailableReason)
        }
    }

    @Test fun emptyAndFullCapacitiesHintsAndSnapshotsNeverGrantWritesOrWrongGameReaders() {
        val empty = BdspStorageReadOnly.read(fixture())
        assertEquals(0, empty.partyCount); assertEquals(0, empty.occupiedBoxSlots)
        assertTrue(empty.party().all { it.read is BdspSlotRead.Empty })
        val record = wire(plain()); val source = fixture(game = 1, count = 6,
            boxes = (0 until 1200).associateWith { record }, party = (0 until 6).associateWith { record })
        for (hint in SwitchGameFamily.values()) {
            val bound = session(source, hint); val storage = bound.bdspStorage!!
            assertEquals("Pokémon Shining Pearl", bound.displayName); assertEquals(1200, storage.occupiedBoxSlots)
            assertEquals(6, storage.partyCount); assertEquals(0, storage.unreadableBoxSlots)
            assertNull(bound.letsGoStorage); assertNull(bound.swordShieldStorage); assertNull(bound.swordShieldTrainer)
            for (capability in SwitchReadCapability.values()) assertEquals(capability in setOf(SwitchReadCapability.SAVE_IDENTITY,
                SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS, SwitchReadCapability.POKEDEX, SwitchReadCapability.INVENTORY), bound.supports(capability))
            assertFalse(bound.profile.writesEnabled); assertTrue(bound.isReadOnly)
        }
        val bound = session(source); source.fill(0); bound.snapshot.bytesCopy().fill(0)
        assertEquals(1200, bound.bdspStorage!!.occupiedBoxSlots); assertEquals(25, (bound.bdspStorage!!.box(39)[29].read as BdspSlotRead.Pokemon).value.species)
    }

    @Test fun nativeSaveGateRemainsMandatoryForStorageIncludingEveryHashAndRevisionRule() {
        val good = fixture(); val damaged = good.copyOf().also { it[0xE9828] = 1 }
        val wrongTitle = good.copyOf().also { it[0x79BDF] = 2 }
        val wrongRevision = good.copyOf().also { uint(it, 0, 0x25) }
        for (source in listOf(ByteArray(1), damaged, sign(wrongTitle), sign(wrongRevision)))
            assertThrows(IllegalArgumentException::class.java) { BdspStorageReadOnly.read(source) }
    }
}
