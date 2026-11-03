package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ScarletVioletPokedexReadOnlyTest {
    private fun put16(b: ByteArray, offset: Int, value: Int) { b[offset] = value.toByte(); b[offset + 1] = (value ushr 8).toByte() }
    private fun put32(b: ByteArray, offset: Int, value: Long) { repeat(4) { b[offset + it] = (value ushr (8 * it)).toByte() } }
    private fun label(b: ByteArray, text: String) { b.fill(0, 0x10, 0x2A); text.toByteArray(Charsets.UTF_16LE).copyInto(b, 0x10) }
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
        Block(0xE3E89BD1, data = ByteArray(0x68).also { it[0x04] = game.toByte(); it[5] = 1; it[7] = 2
            put32(it, 0, 0xFFFFFFFFL); label(it, "Zoë 雪♀"); it[0x5A] = 2; it[0x5B] = 29 })
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


    private val dlcKey = 0xF5D7C0E2L
    private fun save(game: Int = 50, size: Int = sizes[0], original: ByteArray = ByteArray(0xBD24),
        dlc: Block? = null, partyCount: Int = 0, language: Int = 2): ByteArray {
        val blocks = core(game).map { when (it.key) {
            0x0DEAAEBDL -> it.copy(data = original)
            0x3AA1A9ADL -> it.copy(data = it.data.copyOf().also { b -> b[6 * 344] = partyCount.toByte() })
            0xE3E89BD1L -> it.copy(data = it.data.copyOf().also { b -> b[7] = language.toByte() })
            else -> it
        } }
        return fixture(game, size, blocks + listOfNotNull(dlc))
    }
    private fun original(): ByteArray = ByteArray(0xBD24).also { b ->
        val at = 906 * 0x18 // Sprigatito is aligned.
        put32(b, at, 3); put32(b, at + 4, 0x80000001L); put16(b, at + 8, 65535); put16(b, at + 10, 65535)
        b[at + 12] = 128.toByte(); b[at + 13] = 255.toByte(); put32(b, at + 16, 0xFFFFFFFFL)
        b[at + 20] = 255.toByte(); b[at + 21] = 128.toByte(); b[at + 22] = 255.toByte()
        put32(b, 917 * 0x18, 2) // National 982 (Dudunsparce), not National 917.
        put32(b, 999 * 0x18, 1) // National 1008 (Miraidon).
    }
    private fun modern(): ByteArray = ByteArray(0x8130).also { b ->
        val at = 917 * 0x20 // National 982 uses internal 917.
        put32(b, at, 0x80000000L); put32(b, at + 4, 2); put32(b, at + 8, 0); put32(b, at + 12, 0xFFFFFFFFL)
        put16(b, at + 16, 0x8001); b[at + 18] = 255.toByte(); b[at + 19] = 128.toByte()
        repeat(3) { n -> b[at + 20 + n * 4] = (255 - n).toByte(); b[at + 21 + n * 4] = (254 - n).toByte(); b[at + 22 + n * 4] = (128 - n).toByte() }
        put32(b, 1022 * 0x20, 1); put32(b, 1022 * 0x20 + 4, 1); put32(b, 1022 * 0x20 + 8, 1)
        b[1022 * 0x20 + 19] = 3 // National 1025 (Pecharunt).
    }
    private fun session(source: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LETS_GO_PIKACHU_EEVEE): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, source))
    private fun entry(dex: ScarletVioletPokedexRead, species: Int, region: ScarletVioletDexRegion = ScarletVioletDexRegion.PALDEA): ScarletVioletDexEntryRead =
        dex.region(region)!!.entries.first { it.speciesId == species }

    @Test fun originalBothTitlesPreserveNativeUnsignedStateFormLanguageAndDisplayValues() {
        for (game in listOf(50, 51)) {
            val source = save(game, original = original()); val before = source.copyOf(); val dex = ScarletVioletPokedexReadOnly.read(source)
            if (game == 50) assertEquals("20f674db3c0b45e8bcef33629c09ae93f8a95613bdd719dbc3f101f2731f44ab", MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it.toInt() and 255) })
            assertEquals(game, dex.version.gameId); assertEquals(ScarletVioletDexLayout.ORIGINAL, dex.layout); assertEquals(1, dex.regions.size)
            val e = entry(dex, 906); assertEquals(3L, e.rawState); assertTrue(e.known); assertTrue(e.seen); assertTrue(e.caught)
            assertEquals(0x80000001L, e.seenForms); assertNull(e.obtainedForms); assertNull(e.heardForms); assertNull(e.checkedForms)
            assertEquals(65535, e.genderFlags); assertEquals(65535, e.languageFlags); assertEquals(9, e.languages.size)
            assertEquals(128, e.rawShinyFlags); assertTrue(e.shinySeen); assertEquals(255, e.rawNewFlag); assertTrue(e.newDisplay)
            assertEquals(255, e.rawGenderDifference); assertEquals(0xFFFFFFFFL, e.displays.single().formId); assertEquals(255, e.displays.single().rawGender); assertEquals(128, e.displays.single().rawShiny)
            assertTrue(entry(dex, 982).seen); assertFalse(entry(dex, 917).seen); assertTrue(entry(dex, 1008).known); assertFalse(entry(dex, 1008).seen)
            assertEquals(3, dex.regions.single().knownCount); assertEquals(2, dex.regions.single().seenCount); assertEquals(1, dex.regions.single().caughtCount)
            assertNull(dex.region(ScarletVioletDexRegion.KITAKAMI)); assertNull(dex.region(ScarletVioletDexRegion.BLUEBERRY)); assertArrayEquals(before, source)
        }
    }

    @Test fun nonemptyDlcSupersedesConflictingOriginalAndUsesInternalSpeciesAndAllThreeDisplays() {
        for (game in listOf(50, 51)) for (size in listOf(sizes.first(), sizes.last())) {
            val source = save(game, size, original(), Block(dlcKey, data = modern())); val before = source.copyOf()
            val dex = ScarletVioletPokedexReadOnly.read(source); assertEquals(ScarletVioletDexLayout.DLC, dex.layout)
            assertEquals(3, dex.regions.size); assertFalse(entry(dex, 906).caught) // Never merge stale original state.
            val e = entry(dex, 982); assertNull(e.rawState); assertEquals(0x80000000L, e.obtainedForms); assertEquals(2L, e.seenForms)
            assertEquals(0L, e.heardForms); assertEquals(0xFFFFFFFFL, e.checkedForms); assertFalse(e.known); assertTrue(e.seen); assertTrue(e.caught); assertTrue(e.newDisplay)
            assertEquals(0x8001, e.languageFlags); assertEquals(listOf("Japanese"), e.languages); assertEquals(255, e.genderFlags)
            assertFalse(e.shinySeen); assertEquals(128, e.rawShinyFlags); assertNull(e.rawNewFlag); assertNull(e.rawGenderDifference)
            assertEquals(listOf(255L, 254L, 253L), e.displays.map { it.formId }); assertEquals(listOf(254, 253, 252), e.displays.map { it.rawGender })
            assertEquals(listOf(128, 127, 126), e.displays.map { it.rawShiny }); assertEquals(ScarletVioletDexRegion.values().toList(), e.displays.map { it.region })
            val last = dex.region(ScarletVioletDexRegion.BLUEBERRY)!!.entry(243); assertEquals(1025, last.speciesId)
            assertTrue(last.known); assertTrue(last.seen); assertTrue(last.caught); assertTrue(last.shinySeen); assertArrayEquals(before, source)
        }
    }

    @Test fun regionalOrdersHaveExactFormAwareMappingsAndGuardRegionalBounds() {
        val dex = ScarletVioletPokedexReadOnly.read(save(dlc = Block(dlcKey, data = ByteArray(0x8130))))
        val hashes = listOf("d69dffee2ec5368739dd637f3002cdb14bb492a51ed9af89b96a20da7e228706", "8aaa1621a8e32d851bf9cbee53ce25ea0c9063ca3693c1ba73cb4821bda910e0", "7be7bf1950142ad9bdff06c5e58acf377b577c767dae3a8a6d2d113d1cfd5daa")
        for ((i, region) in dex.regions.withIndex()) {
            assertEquals(listOf(400, 200, 243)[i], region.entries.size); assertEquals((1..region.entries.size).toList(), region.entries.map { it.number })
            assertEquals(region.entries.size, region.entries.map { it.speciesId }.toSet().size)
            val map = region.entries.joinToString(",") { it.speciesId.toString() }
            assertEquals(hashes[i], MessageDigest.getInstance("SHA-256").digest(map.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) })
            for (number in listOf(-1, 0, region.entries.size + 1, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { region.entry(number) }
        }
        assertEquals(906, dex.regions[0].entry(1).speciesId); assertEquals(1008, dex.regions[0].entry(400).speciesId)
        assertEquals(167, dex.regions[1].entry(1).speciesId); assertEquals(1017, dex.regions[1].entry(200).speciesId)
        assertEquals(84, dex.regions[2].entry(1).speciesId); assertEquals(1025, dex.regions[2].entry(243).speciesId)
    }

    @Test fun absentOrEmptyDlcFallsBackButMalformedDlcCannotMasqueradeAsOriginal() {
        for (dlc in listOf(null, Block(dlcKey))) {
            val dex = ScarletVioletPokedexReadOnly.read(save(original = original(), dlc = dlc))
            assertEquals(ScarletVioletDexLayout.ORIGINAL, dex.layout); assertTrue(entry(dex, 906).caught)
        }
        val wrong = listOf(Block(dlcKey, type = 1), Block(dlcKey, type = 10, data = ByteArray(4)),
            Block(dlcKey, type = 5, data = ByteArray(0x8130)), Block(dlcKey, data = ByteArray(1)),
            Block(dlcKey, data = ByteArray(0x812F)), Block(dlcKey, data = ByteArray(0x8131)))
        for (block in wrong) {
            val source = save(original = original(), dlc = block)
            assertTrue(ScarletVioletSaveValidation.inspect(source) is ScarletVioletValidation.Verified)
            assertThrows(IllegalArgumentException::class.java) { ScarletVioletPokedexReadOnly.read(source) }
            val bound = session(source); assertTrue(bound.isFormatVerified); assertNull(bound.scarletVioletPokedex); assertNotNull(bound.pokedexUnavailableReason)
            assertNotNull(bound.scarletVioletTrainer); assertNotNull(bound.scarletVioletStorage); assertTrue(bound.supports(SwitchReadCapability.PARTY))
            assertFalse(bound.supports(SwitchReadCapability.POKEDEX)); assertFalse(bound.profile.writesEnabled)
        }
    }

    @Test fun blankRecordsAndModelAvailabilityDoNotInventRegistrationOrRepairContradictions() {
        val blank = ScarletVioletPokedexReadOnly.read(save(dlc = Block(dlcKey, data = ByteArray(0x8130))))
        assertTrue(blank.regions.all { it.knownCount == 0 && it.seenCount == 0 && it.caughtCount == 0 })
        val b = ByteArray(0x8130); b[906 * 0x20 + 19] = 1
        var e = entry(ScarletVioletPokedexReadOnly.read(save(dlc = Block(dlcKey, data = b))), 906)
        assertFalse(e.shinySeen); assertFalse(e.seen); assertFalse(e.caught); assertFalse(e.known)
        put32(b, 906 * 0x20, 1); put32(b, 906 * 0x20 + 8, 2)
        e = entry(ScarletVioletPokedexReadOnly.read(save(dlc = Block(dlcKey, data = b))), 906)
        assertTrue(e.caught); assertTrue(e.known); assertFalse(e.seen) // Saved fields stay independent.
        val old = ByteArray(0xBD24); put32(old, 906 * 0x18, 0xFFFFFFFFL)
        e = entry(ScarletVioletPokedexReadOnly.read(save(original = old)), 906)
        assertEquals(0xFFFFFFFFL, e.rawState); assertTrue(e.known); assertTrue(e.seen); assertTrue(e.caught)
    }

    @Test fun snapshotsAndAllNestedMetadataListsHaveNoMutableAliases() {
        val data = modern(); val source = save(dlc = Block(dlcKey, data = data)); val bound = session(source)
        val dex = bound.scarletVioletPokedex!!; val e = entry(dex, 982); source.fill(0); data.fill(0); bound.snapshot.bytesCopy().fill(0)
        assertEquals(0x80000000L, e.obtainedForms); assertEquals(255L, e.displays[0].formId)
        assertThrows(UnsupportedOperationException::class.java) { (dex.regions as MutableList<ScarletVioletDexRegionRead>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (dex.regions[0].entries as MutableList<ScarletVioletDexEntryRead>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (e.languages as MutableList<String>).add("bad") }
        assertThrows(UnsupportedOperationException::class.java) { (e.displays as MutableList<ScarletVioletDexDisplayRead>).clear() }
        assertEquals(400, dex.regions[0].entries.size); assertEquals(listOf("Japanese"), e.languages)
    }

    @Test fun fullHashTitleCoreAndOrderingValidationPrecedesDexExtraction() {
        val invalid = mutableListOf(ByteArray(1), ByteArray(sizes[0]), fixture(game = 49),
            fixture(blocks = core(50).filter { it.key != 0x0DEAAEBDL }), fixture(blocks = core(50).reversed(), sort = false),
            fixture(blocks = core(50) + core(50).first()), fixture(blocks = core(50).map { if (it.key == 0x0DEAAEBDL) it.copy(data = ByteArray(0xBD23)) else it }))
        val good = save(dlc = Block(dlcKey, data = modern()))
        for (offset in listOf(0, 126, 127, good.size - 33, good.size - 32, good.lastIndex)) invalid.add(good.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() })
        for (source in invalid) {
            val before = source.copyOf(); assertThrows(IllegalArgumentException::class.java) { ScarletVioletPokedexReadOnly.read(source) }
            val bound = session(source); assertNull(bound.scarletVioletPokedex); assertFalse(bound.supports(SwitchReadCapability.POKEDEX)); assertFalse(bound.profile.writesEnabled)
            assertArrayEquals(before, source)
        }
    }

    @Test fun savedIdentityOverridesHintsAndDexIsIndependentOfTrainerPartyOrWrites() {
        for (game in listOf(50, 51)) for (hint in SwitchGameFamily.values()) {
            val source = save(game); val bound = session(source, hint)
            assertEquals(SwitchGameFamily.SCARLET_VIOLET, bound.profile.family)
            assertTrue(bound.isFormatVerified); assertTrue(bound.isReadOnly); assertNotNull(bound.scarletVioletTrainer); assertNotNull(bound.scarletVioletStorage); assertNotNull(bound.scarletVioletPokedex)
            assertNull(bound.letsGoPokedex); assertNull(bound.swordShieldPokedex); assertNull(bound.bdspPokedex); assertNull(bound.legendsArceusPokedex)
            for (c in SwitchReadCapability.values()) assertEquals(c in setOf(SwitchReadCapability.SAVE_IDENTITY, SwitchReadCapability.TRAINER_PROFILE,
                SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS, SwitchReadCapability.POKEDEX), bound.supports(c))
            assertFalse(bound.profile.writesEnabled); assertNull(bound.pokedexUnavailableReason)
        }
        val bound = session(save(dlc = Block(dlcKey, data = modern()), partyCount = 7, language = 0))
        assertNull(bound.scarletVioletTrainer); assertNotNull(bound.trainerUnavailableReason); assertNull(bound.scarletVioletStorage); assertNotNull(bound.storageUnavailableReason)
        assertNotNull(bound.scarletVioletPokedex); assertTrue(bound.supports(SwitchReadCapability.POKEDEX)); assertFalse(bound.supports(SwitchReadCapability.POKEDEX_AND_PROGRESS))
        assertFalse(bound.supports(SwitchReadCapability.PSE_BANK_READ_ONLY)); assertFalse(bound.profile.writesEnabled)
    }
}
