package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ScarletVioletSaveValidationTest {
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

    @Test fun allKnownNativeSizeFamiliesAndSavedTitleIdentityVerifyWithoutMutatingSource() {
        for (game in listOf(50, 51)) for (size in sizes) {
            val source = fixture(game, size); val before = source.copyOf()
            val result = ScarletVioletSaveValidation.inspect(source) as ScarletVioletValidation.Verified
            assertEquals(game, result.version.gameId); assertEquals(5, result.blockCount)
            if (size == sizes[0] && game == 50) assertArrayEquals(
                hex("2ea33add80a222e15187099cfc8f6e4be05dd76e1dc1084e0b84dac65c527c42"), source.copyOfRange(source.size - 32, source.size))
            assertArrayEquals(before, source)
        }
    }

    @Test fun nativeIdentityOverridesAllFamilyHintsWhileOtherReadersAndWritesStayLocked() {
        for (game in listOf(50, 51)) for (family in SwitchGameFamily.values()) {
            val source = fixture(game)
            val session = SwitchReadSession(SwitchReadSnapshot.capture(family, source))
            assertEquals(family, session.snapshot.family)
            assertEquals(SwitchGameFamily.SCARLET_VIOLET, session.profile.family)
            assertEquals(if (game == 50) "Pokémon Scarlet" else "Pokémon Violet", session.displayName)
            assertTrue(session.isFormatVerified); assertTrue(session.isReadOnly); assertFalse(session.profile.writesEnabled)
            assertNull(session.letsGoStorage); assertNull(session.swordShieldStorage)
            assertNull(session.bdspStorage); assertNull(session.swordShieldTrainer); assertNull(session.legendsArceusStorage)
            for (capability in SwitchReadCapability.values())
                assertEquals(capability in setOf(SwitchReadCapability.SAVE_IDENTITY, SwitchReadCapability.PARTY,
                    SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS, SwitchReadCapability.POKEDEX), session.supports(capability))
        }
    }

    @Test fun payloadAndStoredHashCorruptionRejectAndUnknownSizesNeverClaimIdentity() {
        val good = fixture()
        for (index in listOf(0, 126, 127, 128, good.size - 33, good.size - 32, good.lastIndex)) {
            val bad = good.copyOf(); bad[index] = (bad[index].toInt() xor 1).toByte()
            assertTrue(ScarletVioletSaveValidation.inspect(bad) is ScarletVioletValidation.Invalid)
        }
        for (length in listOf(0, 1, 31, 32, sizes[0] - 1, sizes[0] + 1))
            assertTrue(ScarletVioletSaveValidation.inspect(ByteArray(length)) is ScarletVioletValidation.Unrecognized)
        assertTrue(ScarletVioletSaveValidation.inspect(ByteArray(sizes[0])) is ScarletVioletValidation.Invalid)
    }

    @Test fun validHashCannotHideInvalidTypesOversizeObjectsOrOverflowingArrays() {
        val cases = listOf(Block(1, 0), Block(1, 6), Block(1, 7), Block(1, 255),
            Block(1, declared = 0xFFFFFFFFL), Block(1, 5, declared = 0xFFFFFFFFL, subtype = 17),
            Block(1, 5, declared = 1, subtype = 4))
        for (bad in cases) assertTrue(ScarletVioletSaveValidation.inspect(fixture(blocks = core(50) + bad)) is ScarletVioletValidation.Invalid)
        // A huge final object is bounded against remaining payload before allocation or offset arithmetic.
        val huge = fixture(blocks = core(50) + Block(0xFFFFFFFEL, declared = 0x80000000L))
        assertTrue(ScarletVioletSaveValidation.inspect(huge) is ScarletVioletValidation.Invalid)
    }

    @Test fun allRequiredCoreBlockKeysSizesTypesAndTitleIdAreMandatory() {
        for (removed in core(50).indices) {
            assertTrue(ScarletVioletSaveValidation.inspect(fixture(blocks = core(50).filterIndexed { i, _ -> i != removed })) is ScarletVioletValidation.Invalid)
            val wrongSize = core(50).mapIndexed { i, b -> if (i == removed) b.copy(data = b.data + 0.toByte()) else b }
            assertTrue(ScarletVioletSaveValidation.inspect(fixture(blocks = wrongSize)) is ScarletVioletValidation.Invalid)
            val wrongType = core(50).mapIndexed { i, b -> if (i == removed) b.copy(type = 5) else b }
            assertTrue(ScarletVioletSaveValidation.inspect(fixture(blocks = wrongType)) is ScarletVioletValidation.Invalid)
        }
        for (game in listOf(0, 42, 43, 44, 45, 46, 47, 48, 49, 52, 255))
            assertTrue(ScarletVioletSaveValidation.inspect(fixture(game)) is ScarletVioletValidation.Invalid)
    }

    @Test fun duplicateUnsortedAndExcessiveKeyStreamsReject() {
        val duplicate = fixture(blocks = core(50) + Block(0x0D66012C, data = ByteArray(1)))
        assertTrue(ScarletVioletSaveValidation.inspect(duplicate) is ScarletVioletValidation.Invalid)
        assertTrue(ScarletVioletSaveValidation.inspect(fixture(blocks = core(50).reversed(), sort = false)) is ScarletVioletValidation.Invalid)
        val excessive = fixture(blocks = (0..65536).map { Block(it.toLong(), 1) } + core(50))
        val result = ScarletVioletSaveValidation.inspect(excessive) as ScarletVioletValidation.Invalid
        assertTrue(result.reason.contains("limit"))
    }

    @Test fun booleanObjectArrayAndAllPrimitiveKindsConsumeExactlyTheirNativeLengths() {
        val extras = mutableListOf<Block>(); var key = 1L
        for (type in listOf(1, 2, 3)) extras += Block(key++, type)
        extras += Block(key++, 4)
        for (type in 8..17) extras += Block(key++, type, ByteArray(width(type)) { 0xFF.toByte() })
        for (subtype in listOf(3, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17))
            extras += Block(key++, 5, ByteArray(width(subtype) * 3), subtype = subtype)
        val result = ScarletVioletSaveValidation.inspect(fixture(blocks = extras + core(50))) as ScarletVioletValidation.Verified
        assertEquals(extras.size + 5, result.blockCount)
    }

    @Test fun encryptedBytesResemblingLetsGoMarkersDoNotOverrideNativeScarletVioletIdentity() {
        val bytes = fixture(50)
        // This location is in padding object's encrypted body, not any structural prefix.
        bytes[0xB86B0] = 19; bytes[0xB86B1] = 0
        val source = resign(bytes)
        val session = SwitchReadSession(SwitchReadSnapshot.capture(SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, source))
        assertEquals("Pokémon Scarlet", session.displayName)
        assertTrue(session.scarletVioletValidation is ScarletVioletValidation.Verified)
        assertTrue(session.validation is LetsGoValidation.Unrecognized)
        source.fill(0); session.snapshot.bytesCopy().fill(0)
        assertEquals("Pokémon Scarlet", session.displayName)
    }

    @Test fun nativeSizeRulesBoundEveryOptionalRangeAndDoNotInferARevisionFromLength() {
        for (size in discreteSizes) {
            assertTrue(ScarletVioletSaveValidation.isKnownSize(size))
            for (nearby in listOf(size - 1, size + 1)) if (nearby !in discreteSizes)
                assertFalse(ScarletVioletSaveValidation.isKnownSize(nearby))
        }
        for (range in ranges) {
            for (size in range) assertTrue(ScarletVioletSaveValidation.isKnownSize(size))
            for (size in listOf(range.first - 1, range.last + 1)) {
                assertFalse(ScarletVioletSaveValidation.isKnownSize(size))
                assertTrue(ScarletVioletSaveValidation.inspect(ByteArray(size)) is ScarletVioletValidation.Unrecognized)
            }
            assertTrue(ScarletVioletSaveValidation.inspect(ByteArray(range.first)) is ScarletVioletValidation.Invalid)
        }
        for (size in listOf(-1, 0, 32, 0x100000, 0x136DDE, 0x1876B1, 0x2F3284, 0x309FA6, 16 * 1024 * 1024, Int.MAX_VALUE))
            assertFalse(ScarletVioletSaveValidation.isKnownSize(size))
        val wrongOffset = core(50).map { block -> if (block.key == 0xE3E89BD1L)
            block.copy(data = block.data.copyOf().also { it[4] = 0; it[0x14] = 50 }) else block }
        assertTrue(ScarletVioletSaveValidation.inspect(fixture(blocks = wrongOffset)) is ScarletVioletValidation.Invalid)
        val bad = fixture().also { it[123] = (it[123].toInt() xor 1).toByte() }
        val bound = SwitchReadSession(SwitchReadSnapshot.capture(SwitchGameFamily.SCARLET_VIOLET, bad))
        assertTrue(bound.scarletVioletValidation is ScarletVioletValidation.Invalid)
        assertTrue(bound.validation is LetsGoValidation.Unrecognized)
        assertFalse(bound.isFormatVerified)
        for (capability in SwitchReadCapability.values()) assertFalse(bound.supports(capability))
    }

}
