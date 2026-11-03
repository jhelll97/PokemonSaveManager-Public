package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class LegendsZaSaveValidationTest {
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

    @Test fun allFourNativeSizesAndSavedZaIdentityVerifyWithoutMutatingSource() {
        for (game in listOf(52)) for (size in sizes) {
            val source = fixture(game, size); val before = source.copyOf()
            val result = LegendsZaSaveValidation.inspect(source) as LegendsZaValidation.Verified
            assertEquals(game, result.version.gameId); assertEquals(5, result.blockCount); assertNull(result.revision)
            if (size == sizes[0]) assertArrayEquals(
                hex("9f10fcdc277f30c24d69f0d220f24726d94ad347d40560ea2fc0dd44433bee6b"), source.copyOfRange(source.size - 32, source.size))
            assertArrayEquals(before, source)
        }
    }

    @Test fun nativeIdentityOverridesAllFamilyHintsWhileOtherReadersAndWritesStayLocked() {
        val source = fixture(52)
        for (family in SwitchGameFamily.values()) {
            val session = SwitchReadSession(SwitchReadSnapshot.capture(family, source))
            assertEquals(family, session.snapshot.family)
            assertEquals(SwitchGameFamily.LEGENDS_ZA, session.profile.family)
            assertEquals("Pokémon Legends: Z-A", session.displayName)
            assertTrue(session.isFormatVerified); assertTrue(session.isReadOnly); assertFalse(session.profile.writesEnabled)
            assertNull(session.letsGoStorage); assertNull(session.swordShieldStorage)
            assertNull(session.bdspStorage); assertNull(session.swordShieldTrainer)
            assertNull(session.legendsArceusStorage); assertNull(session.scarletVioletStorage)
            for (capability in SwitchReadCapability.values())
                assertEquals(capability in setOf(SwitchReadCapability.SAVE_IDENTITY, SwitchReadCapability.PARTY,
                    SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS, SwitchReadCapability.POKEDEX), session.supports(capability))
        }
    }

    @Test fun payloadAndStoredHashCorruptionRejectAndUnknownSizesNeverClaimIdentity() {
        val good = fixture()
        for (index in listOf(0, 126, 127, 128, good.size - 33, good.size - 32, good.lastIndex)) {
            val bad = good.copyOf(); bad[index] = (bad[index].toInt() xor 1).toByte()
            assertTrue(LegendsZaSaveValidation.inspect(bad) is LegendsZaValidation.Invalid)
        }
        for (length in listOf(0, 1, 31, 32, sizes[0] - 1, sizes[0] + 1))
            assertTrue(LegendsZaSaveValidation.inspect(ByteArray(length)) is LegendsZaValidation.Unrecognized)
        assertTrue(LegendsZaSaveValidation.inspect(ByteArray(sizes[0])) is LegendsZaValidation.Invalid)
    }

    @Test fun validHashCannotHideInvalidTypesOversizeObjectsOrOverflowingArrays() {
        val cases = listOf(Block(1, 0), Block(1, 6), Block(1, 7), Block(1, 255),
            Block(1, declared = 0xFFFFFFFFL), Block(1, 5, declared = 0xFFFFFFFFL, subtype = 17),
            Block(1, 5, declared = 1, subtype = 4))
        for (bad in cases) assertTrue(LegendsZaSaveValidation.inspect(fixture(blocks = core(52) + bad)) is LegendsZaValidation.Invalid)
        // A huge final object is bounded against remaining payload before allocation or offset arithmetic.
        val huge = fixture(blocks = core(52) + Block(0xFFFFFFFEL, declared = 0x80000000L))
        assertTrue(LegendsZaSaveValidation.inspect(huge) is LegendsZaValidation.Invalid)
    }

    @Test fun allRequiredCoreBlockKeysSizesTypesAndTitleIdAreMandatory() {
        for (removed in core(52).indices) {
            assertTrue(LegendsZaSaveValidation.inspect(fixture(blocks = core(52).filterIndexed { i, _ -> i != removed })) is LegendsZaValidation.Invalid)
            val wrongSize = core(52).mapIndexed { i, b -> if (i == removed) b.copy(data = b.data + 0.toByte()) else b }
            assertTrue(LegendsZaSaveValidation.inspect(fixture(blocks = wrongSize)) is LegendsZaValidation.Invalid)
            val wrongType = core(52).mapIndexed { i, b -> if (i == removed) b.copy(type = 5) else b }
            assertTrue(LegendsZaSaveValidation.inspect(fixture(blocks = wrongType)) is LegendsZaValidation.Invalid)
        }
        for (game in listOf(0, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 53, 255))
            assertTrue(LegendsZaSaveValidation.inspect(fixture(game)) is LegendsZaValidation.Invalid)
    }

    @Test fun duplicateUnsortedAndExcessiveKeyStreamsReject() {
        val duplicate = fixture(blocks = core(52) + Block(0x0D66012C, data = ByteArray(1)))
        assertTrue(LegendsZaSaveValidation.inspect(duplicate) is LegendsZaValidation.Invalid)
        assertTrue(LegendsZaSaveValidation.inspect(fixture(blocks = core(52).reversed(), sort = false)) is LegendsZaValidation.Invalid)
        val excessive = fixture(blocks = (0..65536).map { Block(it.toLong(), 1) } + core(52))
        val result = LegendsZaSaveValidation.inspect(excessive) as LegendsZaValidation.Invalid
        assertTrue(result.reason.contains("limit"))
    }

    @Test fun booleanObjectArrayAndAllPrimitiveKindsConsumeExactlyTheirNativeLengths() {
        val extras = mutableListOf<Block>(); var key = 1L
        for (type in listOf(1, 2, 3)) extras += Block(key++, type)
        extras += Block(key++, 4)
        for (type in 8..17) extras += Block(key++, type, ByteArray(width(type)) { 0xFF.toByte() })
        for (subtype in listOf(3, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17))
            extras += Block(key++, 5, ByteArray(width(subtype) * 3), subtype = subtype)
        val result = LegendsZaSaveValidation.inspect(fixture(blocks = extras + core(52))) as LegendsZaValidation.Verified
        assertEquals(extras.size + 5, result.blockCount)
    }

    @Test fun encryptedBytesResemblingLetsGoMarkersDoNotOverrideNativeLegendsZaIdentity() {
        val bytes = fixture(52)
        // This location is in padding object's encrypted body, not any structural prefix.
        bytes[0xB86B0] = 19; bytes[0xB86B1] = 0
        val source = resign(bytes)
        val session = SwitchReadSession(SwitchReadSnapshot.capture(SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, source))
        assertEquals("Pokémon Legends: Z-A", session.displayName)
        assertTrue(session.legendsZaValidation is LegendsZaValidation.Verified)
        assertTrue(session.validation is LetsGoValidation.Unrecognized)
        source.fill(0); session.snapshot.bytesCopy().fill(0)
        assertEquals("Pokémon Legends: Z-A", session.displayName)
    }

    @Test fun optionalNativeRevisionMarkersAreCheckedWithoutInferringRevisionFromFileSize() {
        for (size in sizes) for (marker in 0..2) {
            val block = Block(0x0926555AL, 11, ByteArray(8).also { it[0] = marker.toByte() })
            val source = fixture(size = size, blocks = core(52) + block); val before = source.copyOf()
            val checked = LegendsZaSaveValidation.inspect(source) as LegendsZaValidation.Verified
            assertEquals(marker, checked.revision!!.marker); assertEquals(6, checked.blockCount)
            assertArrayEquals(before, source)
        }
        for (type in listOf(1, 4, 5, 10, 15, 17)) {
            val bytes = when (type) { 1 -> ByteArray(0); 10 -> ByteArray(4); else -> ByteArray(8) }
            assertTrue(LegendsZaSaveValidation.inspect(fixture(blocks = core(52) + Block(0x0926555AL, type, bytes))) is LegendsZaValidation.Invalid)
        }
        for (marker in listOf(3, 255)) assertTrue(LegendsZaSaveValidation.inspect(fixture(blocks = core(52) +
            Block(0x0926555AL, 11, ByteArray(8).also { it[0] = marker.toByte() }))) is LegendsZaValidation.Invalid)
        for (index in 1..7) assertTrue(LegendsZaSaveValidation.inspect(fixture(blocks = core(52) +
            Block(0x0926555AL, 11, ByteArray(8).also { it[index] = 1 }))) is LegendsZaValidation.Invalid)
        val wrongOffset = core(52).map { block -> if (block.key == 0xE3E89BD1L)
            block.copy(data = block.data.copyOf().also { it[4] = 0; it[0x14] = 52 }) else block }
        assertTrue(LegendsZaSaveValidation.inspect(fixture(blocks = wrongOffset)) is LegendsZaValidation.Invalid)
        val bad = fixture().also { it[123] = (it[123].toInt() xor 1).toByte() }
        val bound = SwitchReadSession(SwitchReadSnapshot.capture(SwitchGameFamily.LEGENDS_ZA, bad))
        assertTrue(bound.legendsZaValidation is LegendsZaValidation.Invalid)
        assertTrue(bound.validation is LetsGoValidation.Unrecognized); assertFalse(bound.isFormatVerified)
        for (capability in SwitchReadCapability.values()) assertFalse(bound.supports(capability))
    }

}
