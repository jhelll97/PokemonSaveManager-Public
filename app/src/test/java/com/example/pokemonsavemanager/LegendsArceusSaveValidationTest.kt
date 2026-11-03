package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class LegendsArceusSaveValidationTest {
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

    @Test fun bothKnownNativeSizesAndSavedPlaIdentityVerifyWithoutMutatingSource() {
        for (game in listOf(47)) for (size in sizes) {
            val source = fixture(game, size); val before = source.copyOf()
            val result = LegendsArceusSaveValidation.inspect(source) as LegendsArceusValidation.Verified
            assertEquals(game, result.version.gameId); assertEquals(5, result.blockCount)
            if (size == sizes[0]) assertArrayEquals(
                hex("3ed26e155977a6ece13375ca9d70b336247756a45221586441852287237a4cc4"), source.copyOfRange(source.size - 32, source.size))
            assertArrayEquals(before, source)
        }
    }

    @Test fun nativeIdentityOverridesAllFamilyHintsWhileOtherReadersAndWritesStayLocked() {
        val source = fixture(47)
        for (family in SwitchGameFamily.values()) {
            val session = SwitchReadSession(SwitchReadSnapshot.capture(family, source))
            assertEquals(family, session.snapshot.family)
            assertEquals(SwitchGameFamily.LEGENDS_ARCEUS, session.profile.family)
            assertEquals("Pokémon Legends: Arceus", session.displayName)
            assertTrue(session.isFormatVerified); assertTrue(session.isReadOnly); assertFalse(session.profile.writesEnabled)
            assertNull(session.letsGoStorage); assertNull(session.swordShieldStorage)
            assertNull(session.bdspStorage); assertNull(session.swordShieldTrainer)
            for (capability in SwitchReadCapability.values())
                assertEquals(capability in setOf(SwitchReadCapability.SAVE_IDENTITY, SwitchReadCapability.PARTY,
                    SwitchReadCapability.PASTURES, SwitchReadCapability.POKEMON_DETAILS, SwitchReadCapability.POKEDEX), session.supports(capability))
        }
    }

    @Test fun payloadAndStoredHashCorruptionRejectAndUnknownSizesNeverClaimIdentity() {
        val good = fixture()
        for (index in listOf(0, 126, 127, 128, good.size - 33, good.size - 32, good.lastIndex)) {
            val bad = good.copyOf(); bad[index] = (bad[index].toInt() xor 1).toByte()
            assertTrue(LegendsArceusSaveValidation.inspect(bad) is LegendsArceusValidation.Invalid)
        }
        for (length in listOf(0, 1, 31, 32, sizes[0] - 1, sizes[0] + 1))
            assertTrue(LegendsArceusSaveValidation.inspect(ByteArray(length)) is LegendsArceusValidation.Unrecognized)
        assertTrue(LegendsArceusSaveValidation.inspect(ByteArray(sizes[0])) is LegendsArceusValidation.Invalid)
    }

    @Test fun validHashCannotHideInvalidTypesOversizeObjectsOrOverflowingArrays() {
        val cases = listOf(Block(1, 0), Block(1, 6), Block(1, 7), Block(1, 255),
            Block(1, declared = 0xFFFFFFFFL), Block(1, 5, declared = 0xFFFFFFFFL, subtype = 17),
            Block(1, 5, declared = 1, subtype = 4))
        for (bad in cases) assertTrue(LegendsArceusSaveValidation.inspect(fixture(blocks = core(47) + bad)) is LegendsArceusValidation.Invalid)
        // A huge final object is bounded against remaining payload before allocation or offset arithmetic.
        val huge = fixture(blocks = core(47) + Block(0xFFFFFFFEL, declared = 0x80000000L))
        assertTrue(LegendsArceusSaveValidation.inspect(huge) is LegendsArceusValidation.Invalid)
    }

    @Test fun allRequiredCoreBlockKeysSizesTypesAndTitleIdAreMandatory() {
        for (removed in core(47).indices) {
            assertTrue(LegendsArceusSaveValidation.inspect(fixture(blocks = core(47).filterIndexed { i, _ -> i != removed })) is LegendsArceusValidation.Invalid)
            val wrongSize = core(47).mapIndexed { i, b -> if (i == removed) b.copy(data = b.data + 0.toByte()) else b }
            assertTrue(LegendsArceusSaveValidation.inspect(fixture(blocks = wrongSize)) is LegendsArceusValidation.Invalid)
            val wrongType = core(47).mapIndexed { i, b -> if (i == removed) b.copy(type = 5) else b }
            assertTrue(LegendsArceusSaveValidation.inspect(fixture(blocks = wrongType)) is LegendsArceusValidation.Invalid)
        }
        for (game in listOf(0, 42, 43, 44, 45, 46, 48, 49, 255))
            assertTrue(LegendsArceusSaveValidation.inspect(fixture(game)) is LegendsArceusValidation.Invalid)
    }

    @Test fun duplicateUnsortedAndExcessiveKeyStreamsReject() {
        val duplicate = fixture(blocks = core(47) + Block(0x47E1CEAB, data = ByteArray(1)))
        assertTrue(LegendsArceusSaveValidation.inspect(duplicate) is LegendsArceusValidation.Invalid)
        assertTrue(LegendsArceusSaveValidation.inspect(fixture(blocks = core(47).reversed(), sort = false)) is LegendsArceusValidation.Invalid)
        val excessive = fixture(blocks = (0..65536).map { Block(it.toLong(), 1) } + core(47))
        val result = LegendsArceusSaveValidation.inspect(excessive) as LegendsArceusValidation.Invalid
        assertTrue(result.reason.contains("limit"))
    }

    @Test fun booleanObjectArrayAndAllPrimitiveKindsConsumeExactlyTheirNativeLengths() {
        val extras = mutableListOf<Block>(); var key = 1L
        for (type in listOf(1, 2, 3)) extras += Block(key++, type)
        extras += Block(key++, 4)
        for (type in 8..17) extras += Block(key++, type, ByteArray(width(type)) { 0xFF.toByte() })
        for (subtype in listOf(3, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17))
            extras += Block(key++, 5, ByteArray(width(subtype) * 3), subtype = subtype)
        val result = LegendsArceusSaveValidation.inspect(fixture(blocks = extras + core(47))) as LegendsArceusValidation.Verified
        assertEquals(extras.size + 5, result.blockCount)
    }

    @Test fun encryptedBytesResemblingLetsGoMarkersDoNotOverrideNativeLegendsArceusIdentity() {
        val bytes = fixture(47)
        // This location is in padding object's encrypted body, not any structural prefix.
        bytes[0xB86B0] = 19; bytes[0xB86B1] = 0
        val source = resign(bytes)
        val session = SwitchReadSession(SwitchReadSnapshot.capture(SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, source))
        assertEquals("Pokémon Legends: Arceus", session.displayName)
        assertTrue(session.legendsArceusValidation is LegendsArceusValidation.Verified)
        assertTrue(session.validation is LetsGoValidation.Unrecognized)
        source.fill(0); session.snapshot.bytesCopy().fill(0)
        assertEquals("Pokémon Legends: Arceus", session.displayName)
    }
}
