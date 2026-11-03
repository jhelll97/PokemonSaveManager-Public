package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class LegendsArceusPokedexReadOnlyTest {
    private fun put16(b: ByteArray, offset: Int, value: Int) { b[offset] = value.toByte(); b[offset + 1] = (value ushr 8).toByte() }
    private fun put32(b: ByteArray, offset: Int, value: Long) { repeat(4) { b[offset + it] = (value ushr (8 * it)).toByte() } }
    private fun label(b: ByteArray, text: String) { b.fill(0, 0x20, 0x3A); text.toByteArray(Charsets.UTF_16LE).copyInto(b, 0x20) }
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
        Block(0xF25C070E, data = ByteArray(0x80).also { it[0x14] = game.toByte(); it[0x15] = 1; it[0x17] = 2
            put32(it, 0x10, 0xFFFFFFFFL); label(it, "Zoë 雪♀") })
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


    private fun save(size: Int = sizes[0], dex: ByteArray = ByteArray(0x1E460),
        partyCount: Int = 0, trainerLanguage: Int = 2): ByteArray = fixture(size = size, blocks = core(47).map {
        when (it.key) {
            0x02168706L -> it.copy(data = dex)
            0x2985FE5DL -> it.copy(data = it.data.copyOf().also { b -> b[6 * 376] = partyCount.toByte() })
            0xF25C070EL -> it.copy(data = it.data.copyOf().also { b -> b[0x17] = trainerLanguage.toByte() })
            else -> it
        }
    })
    private fun session(source: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LETS_GO_PIKACHU_EEVEE): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, source))
    private fun populated(): ByteArray = ByteArray(0x1E460).also { b ->
        put32(b, 0, 0x80000041L); put16(b, 6, 65535); put16(b, 8, 32768); put32(b, 0xC, 0x80000000L)
        val at = 0x70 + 722 * 0x58
        put32(b, at, 0x8000007FL); put32(b, at + 4, 0xFFFFFFFFL)
        put16(b, at + 8, 65535); put16(b, at + 0xA, 60001); put16(b, at + 0xC, 32768); put16(b, at + 0xE, 65534)
        repeat(29) { put16(b, at + 0x10 + it * 2, 65535 - it) }
        b[at + 0x50] = 255.toByte()
        // Neighbor guards and a non-Hisui species must not be mistaken for this species or affect summaries.
        put16(b, 0x70 + 721 * 0x58 + 0xA, 123)
        put16(b, 0x70 + 723 * 0x58 + 8, 101)
        put32(b, 0x70 + 491 * 0x58, 4L); put16(b, 0x70 + 491 * 0x58 + 0xA, 1)
        put16(b, 0x70 + 905 * 0x58 + 0xA, 2)
    }

    @Test fun bothNativeSizesUseNationalSpeciesIndexAndRetainUnsignedAndSignedNativeValues() {
        for (size in sizes) {
            val source = save(size, populated()); val before = source.copyOf()
            val dex = LegendsArceusPokedexReadOnly.read(source); val e = dex.entry(1)
            if (size == sizes[0]) assertEquals("55014d46c0d60f52b48a2871f1d1b38100aaa4aa35a5555db06944371b7c0c8a", MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it.toInt() and 255) })
            assertEquals(47, dex.version.gameId); assertEquals(242, dex.entries.size)
            assertEquals(722, e.speciesId); assertEquals(0x8000007FL, e.rawFlags); assertEquals(0xFFFFFFFFL, e.rawReportedProgress)
            assertEquals(65535, e.rawResearchRate); assertEquals(60001, e.numObtained)
            assertEquals(32768, e.updateCounter); assertEquals(65534, e.lastReportCounter); assertEquals(255, e.selectedForm)
            assertEquals(0x80000041L, dex.rawGlobalFlags); assertTrue(dex.hisuiCompletedFlag); assertTrue(dex.hisuiPerfectFlag)
            assertEquals(Int.MIN_VALUE, dex.totalResearchPoints); assertEquals(65535, dex.updateCounter); assertEquals(32768, dex.reportCounter)
            assertEquals(3, dex.obtainedCount); assertEquals(1, dex.updatedCount); assertEquals(1, dex.reportedCount); assertEquals(2, dex.perfectCount)
            assertEquals(101, dex.entry(2).rawResearchRate); assertEquals(0, dex.entry(2).numObtained)
            assertEquals(905, dex.entry(234).speciesId); assertEquals(2, dex.entry(234).numObtained)
            assertEquals(491, dex.entry(242).speciesId); assertEquals(1, dex.entry(242).numObtained)
            assertArrayEquals(before, source)
        }
    }

    @Test fun formAwareHisuiOrderIsCompleteAndOnlyRegionalNumbersAreAccepted() {
        val dex = LegendsArceusPokedexReadOnly.read(save())
        assertEquals((1..242).toList(), dex.entries.map { it.number }); assertEquals(242, dex.entries.map { it.speciesId }.toSet().size)
        val map = dex.entries.joinToString(",") { it.speciesId.toString() }
        assertEquals("aeb4a0a7c71465696a821a3daf77eab296e157a46b101dc93568ccfddcb7a523", MessageDigest.getInstance("SHA-256").digest(map.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) })
        assertEquals(listOf(722, 723, 724, 155, 156, 157, 501, 502, 503), dex.entries.take(9).map { it.speciesId })
        assertEquals(58, dex.entry(150).speciesId) // Hisuian Growlithe: mapping searches alternate forms.
        assertFalse(dex.entries.any { it.speciesId == 721 })
        for (number in listOf(Int.MIN_VALUE, -1, 0, 243, 722, Int.MAX_VALUE))
            assertThrows(IllegalArgumentException::class.java) { dex.entry(number) }
    }

    @Test fun savedFlagsStayIndependentIncludingUnreportedHighRatesAndContradictions() {
        for (bit in 0..6) {
            val b = ByteArray(0x1E460); val at = 0x70 + 722 * 0x58
            put32(b, at, 1L shl bit); put16(b, at + 8, 100)
            val e = LegendsArceusPokedexReadOnly.read(save(dex = b)).entry(1)
            assertEquals((0..6).map { it == bit }, listOf(e.updated, e.reported, e.perfect, e.selectedGender1, e.selectedShiny, e.selectedAlpha, e.solitudeComplete))
            assertEquals(100, e.rawResearchRate); assertFalse(e.obtained); assertEquals(0, e.numObtained)
        }
        val b = ByteArray(0x1E460); put32(b, 0, 0xFFFFFFBEL)
        val dex = LegendsArceusPokedexReadOnly.read(save(dex = b))
        assertFalse(dex.hisuiCompletedFlag); assertFalse(dex.hisuiPerfectFlag); assertEquals(0xFFFFFFBEL, dex.rawGlobalFlags)
        assertEquals(0, dex.perfectCount) // Do not recompute entries from unrelated global flags.
    }

    @Test fun allKnownCounterOffsetsPreserveFullUInt16RangeWithoutInventedTaskIdentities() {
        val e = LegendsArceusPokedexReadOnly.read(save(dex = populated())).entry(1)
        assertEquals(29, e.counters.size); assertEquals((0 until 29).map { 65535 - it }, e.counters.map { it.value })
        assertEquals(listOf("Move-use slot 1", "Move-use slot 4", "Defeat-with-move-type slot 1", "Defeat-with-move-type slot 3", "Defeated", "Alpha caught", "Leapt from tussocks"),
            listOf(0, 3, 4, 6, 7, 9, 28).map { e.counters[it].label })
        assertTrue(e.counters.none { it.label.contains("complete", ignoreCase = true) })
    }

    @Test fun blankRecordsDoNotInventObtainmentReportsResearchOrStatistics() {
        val dex = LegendsArceusPokedexReadOnly.read(save())
        assertEquals(0, dex.obtainedCount); assertEquals(0, dex.updatedCount); assertEquals(0, dex.reportedCount); assertEquals(0, dex.perfectCount)
        assertEquals(0, dex.totalResearchPoints); assertFalse(dex.hisuiCompletedFlag); assertFalse(dex.hisuiPerfectFlag)
        assertTrue(dex.entries.all { it.rawFlags == 0L && it.rawResearchRate == 0 && it.rawReportedProgress == 0L && it.counters.all { c -> c.value == 0 } })
        val b = ByteArray(0x1E460); put32(b, 0xC, 0x7FFFFFFFL)
        assertEquals(Int.MAX_VALUE, LegendsArceusPokedexReadOnly.read(save(dex = b)).totalResearchPoints)
    }

    @Test fun metadataAndNestedListsAreDetachedAndUnmodifiable() {
        val b = populated(); val source = save(dex = b); val bound = session(source)
        val dex = bound.legendsArceusPokedex!!; source.fill(0); b.fill(0); bound.snapshot.bytesCopy().fill(0)
        assertEquals(60001, dex.entry(1).numObtained); assertEquals(65535, dex.entry(1).counters[0].value)
        assertThrows(UnsupportedOperationException::class.java) { (dex.entries as MutableList<LegendsArceusDexEntryRead>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (dex.entry(1).counters as MutableList<LegendsArceusDexCounterRead>)[0] = LegendsArceusDexCounterRead("bad", 0) }
        assertEquals(242, dex.entries.size); assertEquals(65535, dex.entry(1).counters[0].value)
    }

    @Test fun completeNativeHashTitleObjectLengthAndKeyOrderingGatesPrecedeDexAccess() {
        val invalid = mutableListOf(ByteArray(1), ByteArray(sizes[0]), fixture(game = 46),
            fixture(blocks = core(47).filter { it.key != 0x02168706L }),
            fixture(blocks = core(47).reversed(), sort = false), fixture(blocks = core(47) + core(47).first()))
        for (size in sizes) {
            val source = save(size)
            for (offset in listOf(0, 126, 127, source.size - 33, source.size - 32, source.lastIndex)) {
                invalid.add(source.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() })
            }
        }
        for (length in listOf(0, 0x1E45F, 0x1E461)) invalid.add(fixture(blocks = core(47).map { if (it.key == 0x02168706L) it.copy(data = ByteArray(length)) else it }))
        invalid.add(fixture(blocks = core(47).map { if (it.key == 0x02168706L) it.copy(type = 5) else it }))
        for (source in invalid) {
            val before = source.copyOf()
            assertThrows(IllegalArgumentException::class.java) { LegendsArceusPokedexReadOnly.read(source) }
            val bound = session(source); assertNull(bound.legendsArceusPokedex); assertFalse(bound.supports(SwitchReadCapability.POKEDEX))
            assertFalse(bound.profile.writesEnabled); assertArrayEquals(before, source)
        }
    }

    @Test fun nativeIdentityAndIndependentReadersNeverUnlockWrongGamesProgressOrWrites() {
        for (hint in SwitchGameFamily.values()) {
            val bound = session(save(dex = populated()), hint)
            assertEquals(SwitchGameFamily.LEGENDS_ARCEUS, bound.profile.family); assertTrue(bound.isFormatVerified); assertTrue(bound.isReadOnly)
            assertNotNull(bound.legendsArceusTrainer); assertNotNull(bound.legendsArceusStorage); assertNotNull(bound.legendsArceusPokedex)
            assertNull(bound.pokedexUnavailableReason); assertNull(bound.letsGoPokedex); assertNull(bound.swordShieldPokedex); assertNull(bound.bdspPokedex)
            for (c in SwitchReadCapability.values()) assertEquals(c in setOf(SwitchReadCapability.SAVE_IDENTITY,
                SwitchReadCapability.TRAINER_PROFILE, SwitchReadCapability.PARTY, SwitchReadCapability.PASTURES,
                SwitchReadCapability.POKEMON_DETAILS, SwitchReadCapability.POKEDEX), bound.supports(c))
            assertFalse(bound.profile.writesEnabled)
        }
        val bound = session(save(dex = populated(), partyCount = 7, trainerLanguage = 0))
        assertNull(bound.legendsArceusStorage); assertNotNull(bound.storageUnavailableReason)
        assertNull(bound.legendsArceusTrainer); assertNotNull(bound.trainerUnavailableReason)
        assertNotNull(bound.legendsArceusPokedex); assertNull(bound.pokedexUnavailableReason)
        assertTrue(bound.supports(SwitchReadCapability.POKEDEX)); assertEquals(3, bound.legendsArceusPokedex!!.obtainedCount)
        assertFalse(bound.supports(SwitchReadCapability.POKEDEX_AND_PROGRESS)); assertFalse(bound.supports(SwitchReadCapability.PSE_BANK_READ_ONLY))
        assertFalse(bound.profile.writesEnabled)
    }
}
