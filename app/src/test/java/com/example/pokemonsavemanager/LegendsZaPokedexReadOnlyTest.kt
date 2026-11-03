package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class LegendsZaPokedexReadOnlyTest {
    private fun put16(b: ByteArray, offset: Int, value: Int) { b[offset] = value.toByte(); b[offset + 1] = (value ushr 8).toByte() }
    private fun put32(b: ByteArray, offset: Int, value: Long) { repeat(4) { b[offset + it] = (value ushr (8 * it)).toByte() } }
    private fun label(b: ByteArray, text: String) { b.fill(0, 0x10, 0x2A); text.toByteArray(Charsets.UTF_16LE).copyInto(b, 0x10) }
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
        Block(0xE3E89BD1, data = ByteArray(0x78).also { it[0x04] = game.toByte(); it[5] = 1; it[7] = 2
            put32(it, 0, 0xFFFFFFFFL); label(it, "Zoë 雪♀"); it[0x5A] = 2; it[0x5B] = 29
            put32(it, 0x68, 0x3F400000L); put32(it, 0x70, 0xFFFFFFFDL)
            it[0x74] = 1; it[0x75] = 1; put16(it, 0x76, 2026) })
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


    private fun revision(marker: Int): Block = Block(0x0926555AL, 11, ByteArray(8).also { it[0] = marker.toByte() })
    private fun save(size: Int = sizes[0], marker: Int? = null, dex: ByteArray = ByteArray(0x27068),
        language: Int = 2, corruptParty: Boolean = false): ByteArray {
        val blocks = core(52).map { when (it.key) {
            0x2D87BE5CL -> it.copy(data = dex)
            0xE3E89BD1L -> it.copy(data = it.data.copyOf().also { b -> b[7] = language.toByte() })
            0x3AA1A9ADL -> it.copy(data = it.data.copyOf().also { b -> if (corruptParty) { b[0] = 1; b[4] = 1 } })
            else -> it
        } }
        return fixture(size = size, blocks = blocks + listOfNotNull(marker?.let { revision(it) }))
    }
    private fun populated(): ByteArray = ByteArray(0x27068).also { b ->
        val at = 152 * 0x84 // Lumiose #1 Chikorita; aligned species.
        put32(b, at, 0x80000000L); put32(b, at + 4, 1); put32(b, at + 12, 0xFFFFFFFFL); put16(b, at + 8, 65535)
        b[at + 10] = 255.toByte(); b[at + 11] = 255.toByte(); b[at + 16] = 255.toByte(); b[at + 17] = 128.toByte()
        b[at + 0x5A] = 255.toByte(); b[at + 0x5B] = 254.toByte(); b[at + 0x5C] = 128.toByte()
        put32(b, 153 * 0x84, 1) // Caught without seen is preserved.
        put32(b, 977 * 0x84, 1); put32(b, 977 * 0x84 + 4, 2) // National 1000 Gholdengo, internal 977.
        put32(b, 952 * 0x84 + 12, 0x80000000L); put16(b, 952 * 0x84 + 8, 1 shl 9); b[952 * 0x84 + 16] = 7 // National 978 Tatsugiri.
        put32(b, 807 * 0x84 + 4, 1) // Final native DexIndex 364.
        put32(b, 0, 0xFFFFFFFFL); put32(b, 906 * 0x84, 0xFFFFFFFFL) // Unused zero and non-Z-A species must not count.
    }
    private fun session(source: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LETS_GO_PIKACHU_EEVEE): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, source))
    private fun first(dex: LegendsZaPokedexRead): LegendsZaDexEntryRead = dex.section(LegendsZaDexSection.LUMIOSE)!!.entry(1)

    @Test fun allFourSizesAndOptionalRevisionsReadNativeFieldsWithoutMutatingTheSource() {
        for (size in sizes) for (marker in listOf(null, 0, 1, 2)) {
            val source = save(size, marker, populated()); val before = source.copyOf(); val dex = LegendsZaPokedexReadOnly.read(source)
            if (size == sizes[0] && marker == null) assertEquals("c17824abdce0ff5b81d7526e23927d17e4e66cf953aca44adbb00bf6632a26fc", MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it.toInt() and 255) })
            assertEquals(52, dex.version.gameId); assertEquals(marker, dex.revision?.marker)
            val e = first(dex); assertEquals(152, e.speciesId); assertEquals(0x80000000L, e.caughtForms); assertEquals(1L, e.seenForms); assertEquals(0xFFFFFFFFL, e.shinyForms)
            assertTrue(e.caught); assertTrue(e.seen); assertTrue(e.shinyRecorded); assertTrue(e.alphaSeen); assertTrue(e.newDisplay)
            assertEquals(65535, e.languageFlags); assertEquals(10, e.languages.size); assertEquals(255, e.genderFlags); assertEquals(255, e.megaFlags)
            assertEquals(128, e.rawAlphaFlag); assertEquals(255, e.rawNewFlag); assertEquals(255, e.displayForm); assertEquals(254, e.rawDisplayGender)
            assertEquals("Unknown", e.displayGender); assertEquals(128, e.rawDisplayShiny); assertTrue(e.displayShiny)
            val base = dex.section(LegendsZaDexSection.LUMIOSE)!!; assertEquals(232, base.entries.size); assertEquals(1, base.seenCount); assertEquals(2, base.caughtCount)
            assertTrue(base.entry(2).caught); assertFalse(base.entry(2).seen)
            val dlc = dex.section(LegendsZaDexSection.HYPERSPACE)
            if (marker == 1 || marker == 2) {
                assertNotNull(dlc); assertEquals(132, dlc!!.entries.size); assertEquals(2, dlc.seenCount); assertEquals(1, dlc.caughtCount)
                assertEquals(1000, dlc.entry(268).speciesId); assertTrue(dlc.entry(268).caught); assertTrue(dlc.entry(268).seen)
                assertEquals(978, dlc.entry(263).speciesId); assertEquals(0x80000000L, dlc.entry(263).shinyForms); assertEquals(listOf("Spanish (Latin America)"), dlc.entry(263).languages)
                assertEquals(807, dlc.entry(364).speciesId); assertTrue(dlc.entry(364).seen)
            } else assertNull(dlc)
            assertArrayEquals(before, source)
        }
    }

    @Test fun nativeDexOrderFiltersInheritedRowsAndSectionBoundsUseNativeNumbers() {
        val dex = LegendsZaPokedexReadOnly.read(save(marker = 1))
        val all = dex.sections.flatMap { it.entries }; assertEquals(364, all.size); assertEquals((1..364).toList(), all.map { it.number })
        assertEquals(364, all.map { it.speciesId }.toSet().size); assertTrue(all.all { it.speciesId <= 1000 }); assertFalse(all.any { it.speciesId == 906 })
        val map = all.joinToString(",") { it.speciesId.toString() }
        assertEquals("ff3a51dbcbeac050400dd7445bb17791960292c07ad22e50fca95854e71c1fa8", MessageDigest.getInstance("SHA-256").digest(map.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) })
        val base = dex.section(LegendsZaDexSection.LUMIOSE)!!; val dlc = dex.section(LegendsZaDexSection.HYPERSPACE)!!
        assertEquals(152, base.entry(1).speciesId); assertTrue(base.entries.all { it.speciesId <= 870 }); assertEquals(232, base.entries.last().number)
        for (n in listOf(Int.MIN_VALUE, -1, 0, 233, 364, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { base.entry(n) }
        for (n in listOf(-1, 0, 1, 232, 365, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { dlc.entry(n) }
    }

    @Test fun tenLanguagesAndTheDistinctZaDisplayGenderEnumAreReadExactly() {
        val names = listOf("Japanese", "English", "French", "Italian", "German", "Spanish", "Korean", "Chinese (Simplified)", "Chinese (Traditional)", "Spanish (Latin America)")
        for (bit in names.indices) {
            val b = ByteArray(0x27068); put16(b, 152 * 0x84 + 8, 1 shl bit)
            val e = first(LegendsZaPokedexReadOnly.read(save(dex = b))); assertEquals(listOf(names[bit]), e.languages); assertEquals(1 shl bit, e.languageFlags)
            assertFalse(e.caught); assertFalse(e.seen)
        }
        for ((raw, name) in listOf(0 to "Genderless", 1 to "Male", 2 to "Female", 3 to "Gendered, no display difference", 255 to "Unknown")) {
            val b = ByteArray(0x27068); b[152 * 0x84 + 0x5B] = raw.toByte(); put16(b, 152 * 0x84 + 8, 0x8000)
            val e = first(LegendsZaPokedexReadOnly.read(save(dex = b))); assertEquals(raw, e.rawDisplayGender); assertEquals(name, e.displayGender)
            assertEquals(0x8000, e.languageFlags); assertTrue(e.languages.isEmpty())
        }
    }

    @Test fun formShinyMegaAlphaAndDisplayFlagsRemainIndependentWithoutInventedRegistration() {
        val empty = first(LegendsZaPokedexReadOnly.read(save())); assertFalse(empty.caught); assertFalse(empty.seen); assertFalse(empty.shinyRecorded); assertFalse(empty.alphaSeen)
        for (offset in listOf(0, 4, 12, 16, 17, 10, 0x5C)) {
            val b = ByteArray(0x27068); b[152 * 0x84 + offset] = 128.toByte()
            val e = first(LegendsZaPokedexReadOnly.read(save(dex = b)))
            assertEquals(offset == 0, e.caught); assertEquals(offset == 4, e.seen); assertEquals(offset == 12, e.shinyRecorded)
            assertEquals(offset == 17, e.alphaSeen); assertEquals(offset == 10, e.newDisplay); assertEquals(offset == 0x5C, e.displayShiny)
            assertEquals(if (offset == 16) 128 else 0, e.megaFlags)
        }
    }

    @Test fun snapshotAndNestedMetadataListsAreDetachedAndUnmodifiable() {
        val b = populated(); val source = save(marker = 1, dex = b); val bound = session(source); val dex = bound.legendsZaPokedex!!
        source.fill(0); b.fill(0); bound.snapshot.bytesCopy().fill(0); assertEquals(0x80000000L, first(dex).caughtForms)
        assertThrows(UnsupportedOperationException::class.java) { (dex.sections as MutableList<LegendsZaDexSectionRead>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (dex.sections[0].entries as MutableList<LegendsZaDexEntryRead>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (first(dex).languages as MutableList<String>).clear() }
        assertEquals(2, dex.sections.size); assertEquals(232, dex.sections[0].entries.size); assertEquals(10, first(dex).languages.size)
    }

    @Test fun missingRevisionNeverAssumesDlcAndMalformedOrUnsupportedMarkersFailClosed() {
        for (size in sizes) for (marker in listOf(null, 0)) {
            val dex = LegendsZaPokedexReadOnly.read(save(size, marker, populated()))
            assertEquals(1, dex.sections.size); assertNull(dex.section(LegendsZaDexSection.HYPERSPACE))
        }
        val revisions = listOf(revision(3), revision(255), Block(0x0926555AL, 10, ByteArray(4)),
            Block(0x0926555AL, 11, ByteArray(8).also { it[1] = 1 }))
        for (rev in revisions) {
            val source = fixture(blocks = core(52) + rev)
            assertThrows(IllegalArgumentException::class.java) { LegendsZaPokedexReadOnly.read(source) }
            val bound = session(source); assertFalse(bound.isFormatVerified); assertNull(bound.legendsZaPokedex); assertFalse(bound.supports(SwitchReadCapability.POKEDEX))
        }
    }

    @Test fun fullContainerTitleHashCoreAndKeyOrderingGatesPrecedeDexExtraction() {
        val invalid = mutableListOf(ByteArray(1), ByteArray(sizes[0]), fixture(game = 51),
            fixture(blocks = core(52).filter { it.key != 0x2D87BE5CL }), fixture(blocks = core(52).reversed(), sort = false), fixture(blocks = core(52) + core(52).first()))
        for (length in listOf(0, 0x27067, 0x27069)) invalid.add(fixture(blocks = core(52).map { if (it.key == 0x2D87BE5CL) it.copy(data = ByteArray(length)) else it }))
        invalid.add(fixture(blocks = core(52).map { if (it.key == 0x2D87BE5CL) it.copy(type = 5) else it }))
        val good = save(marker = 1, dex = populated())
        for (offset in listOf(0, 126, 127, good.size - 33, good.size - 32, good.lastIndex)) invalid.add(good.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() })
        for (source in invalid) {
            val before = source.copyOf(); assertThrows(IllegalArgumentException::class.java) { LegendsZaPokedexReadOnly.read(source) }
            val bound = session(source); assertNull(bound.legendsZaPokedex); assertFalse(bound.supports(SwitchReadCapability.POKEDEX)); assertFalse(bound.profile.writesEnabled)
            assertArrayEquals(before, source)
        }
    }

    @Test fun nativeIdentityAndIndependentTrainerOrBadPartySlotsNeverGrantOtherReadersOrWrites() {
        for (hint in SwitchGameFamily.values()) {
            val bound = session(save(marker = 1, dex = populated()), hint)
            assertEquals(SwitchGameFamily.LEGENDS_ZA, bound.profile.family); assertTrue(bound.isFormatVerified); assertTrue(bound.isReadOnly)
            assertNotNull(bound.legendsZaTrainer); assertNotNull(bound.legendsZaStorage); assertNotNull(bound.legendsZaPokedex); assertNull(bound.pokedexUnavailableReason)
            assertNull(bound.letsGoPokedex); assertNull(bound.swordShieldPokedex); assertNull(bound.bdspPokedex); assertNull(bound.legendsArceusPokedex); assertNull(bound.scarletVioletPokedex)
            for (c in SwitchReadCapability.values()) assertEquals(c in setOf(SwitchReadCapability.SAVE_IDENTITY, SwitchReadCapability.TRAINER_PROFILE,
                SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS, SwitchReadCapability.POKEDEX), bound.supports(c))
            assertFalse(bound.profile.writesEnabled)
        }
        val bound = session(save(marker = 1, dex = populated(), language = 0, corruptParty = true))
        assertNull(bound.legendsZaTrainer); assertNotNull(bound.trainerUnavailableReason); assertNotNull(bound.legendsZaStorage)
        assertEquals(1, bound.legendsZaStorage!!.partyCount); assertTrue(bound.legendsZaStorage!!.party()[0].read is LegendsZaSlotRead.Invalid)
        assertNotNull(bound.legendsZaPokedex); assertNull(bound.pokedexUnavailableReason); assertTrue(bound.supports(SwitchReadCapability.POKEDEX))
        assertEquals(2, bound.legendsZaPokedex!!.sections[0].caughtCount); assertFalse(bound.supports(SwitchReadCapability.POKEDEX_AND_PROGRESS))
        assertFalse(bound.supports(SwitchReadCapability.PSE_BANK_READ_ONLY)); assertFalse(bound.profile.writesEnabled)
    }
}
