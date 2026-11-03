package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class LegendsArceusTrainerReadOnlyTest {
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

    private val keys = listOf(0x3279D927L, 0x9D5D1CA5L, 0xC25B0D5AL, 0x75CE2CF6L, 0x50FE632AL)
    private val defaults = listOf(9999999L, 0xFFFFFFFFL, 0xFFFFFFFEL, 39L, 10L)
    private fun scalar(key: Long, value: Long): Block = Block(key, 10, ByteArray(4).also { put32(it, 0, value) })
    private fun counterBlocks(): List<Block> = keys.zip(defaults).map { scalar(it.first, it.second) }
    private fun save(size: Int = sizes[0], counters: List<Block> = counterBlocks(),
        changeStatus: (ByteArray) -> Unit = {}): ByteArray {
        val blocks = core(47).map { if (it.key == 0xF25C070EL) it.copy(data = it.data.copyOf().also(changeStatus)) else it }
        return fixture(size = size, blocks = blocks + counters)
    }
    private fun session(source: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.SWORD_SHIELD): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, source))

    @Test fun bothNativeSizesExposeUnsignedStatusAndTypedCountersWithoutMutatingSource() {
        for (size in sizes) {
            val source = save(size); val before = source.copyOf(); val trainer = LegendsArceusTrainerReadOnly.read(source)
            if (size == sizes[0]) assertEquals("5e354d955d020d7701d89d58e093dee884f184d87d4e7f79d054019194e76471", MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it.toInt() and 255) })
            assertEquals(47, trainer.version.gameId); assertEquals("Zoë 雪♀", trainer.name); assertEquals(1, trainer.gender)
            assertEquals(2, trainer.languageId); assertEquals("English", trainer.languageName)
            assertEquals(0xFFFFFFFFL, trainer.id32); assertEquals(65535, trainer.tid16); assertEquals(65535, trainer.sid16)
            assertEquals("967295", trainer.displayTrainerId); assertEquals(9999999L, trainer.money)
            assertEquals(0xFFFFFFFFL, trainer.meritCurrent); assertEquals(0xFFFFFFFEL, trainer.meritTotal)
            assertEquals(39L, trainer.satchelUpgrades); assertEquals(10L, trainer.expeditionRank)
            assertArrayEquals(before, source); source.fill(0); assertEquals("Zoë 雪♀", trainer.name)
        }
    }

    @Test fun nativeStatusLanguagesGenderIdsAndUnicodeLimitsAreHonored() {
        val languages = mapOf(1 to "Japanese", 2 to "English", 3 to "French", 4 to "Italian", 5 to "German",
            7 to "Spanish", 8 to "Korean", 9 to "Chinese (Simplified)", 10 to "Chinese (Traditional)")
        for ((language, name) in languages) for (gender in 0..1) {
            val source = save(counters = counterBlocks() + scalar(0x0BFDEBA1L, 1L)) { it[0x17] = language.toByte(); it[0x15] = gender.toByte() }
            val trainer = LegendsArceusTrainerReadOnly.read(source)
            assertEquals(language, trainer.languageId); assertEquals(name, trainer.languageName); assertEquals(gender, trainer.gender)
        }
        for ((id, display) in listOf(0L to "000000", 1L to "000001", 999999L to "999999", 1000000L to "000000", 0xFFFFFFFFL to "967295"))
            assertEquals(display, LegendsArceusTrainerReadOnly.read(save { put32(it, 0x10, id) }).displayTrainerId)
        for (name in listOf("", "ABCDEFGHIJKL", "雪".repeat(12), "A🚀B"))
            assertEquals(name, LegendsArceusTrainerReadOnly.read(save { label(it, name) }).name)
    }

    @Test fun malformedNamesGenderLanguageAndBoundedCountersDisableOnlyTrainerAccess() {
        val badStatus: List<(ByteArray) -> Unit> = listOf(
            { b -> repeat(13) { put16(b, 0x20 + it * 2, 65) } },
            { b -> label(b, ""); put16(b, 0x20, 0xD800) },
            { b -> label(b, ""); put16(b, 0x20, 0xDC00) },
            { b -> label(b, ""); put16(b, 0x20, 10) },
            { b -> label(b, ""); put16(b, 0x20, 127) },
            { b -> b[0x15] = 2 }, { b -> b[0x15] = 255.toByte() },
            { b -> b[0x17] = 0 }, { b -> b[0x17] = 6 }, { b -> b[0x17] = 11 }, { b -> b[0x17] = 255.toByte() }
        )
        val bad = badStatus.map { save(changeStatus = it) } + listOf(
            save(counters = listOf(scalar(keys[0], 10000000L))), save(counters = listOf(scalar(keys[0], 0xFFFFFFFFL))),
            save(counters = listOf(scalar(keys[3], 40L))), save(counters = listOf(scalar(keys[3], 0xFFFFFFFFL))),
            save(counters = listOf(scalar(keys[4], 11L))), save(counters = listOf(scalar(keys[4], 0xFFFFFFFFL)))
        )
        for (source in bad) {
            val before = source.copyOf(); val bound = session(source)
            assertTrue(bound.isFormatVerified); assertNull(bound.legendsArceusTrainer); assertNotNull(bound.trainerUnavailableReason)
            assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertTrue(bound.supports(SwitchReadCapability.PARTY))
            assertTrue(bound.supports(SwitchReadCapability.PASTURES)); assertFalse(bound.profile.writesEnabled); assertArrayEquals(before, source)
        }
        val trash = LegendsArceusTrainerReadOnly.read(save { label(it, "A"); put16(it, 0x24, 0xD800) })
        assertEquals("A", trash.name)
    }

    @Test fun allOptionalPresenceCombinationsKeepMissingDistinctFromSavedZero() {
        for (mask in 0 until 32) {
            val counters = keys.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.map { scalar(it, 0L) }
            val trainer = LegendsArceusTrainerReadOnly.read(save(counters = counters))
            val actual = listOf(trainer.money, trainer.meritCurrent, trainer.meritTotal, trainer.satchelUpgrades, trainer.expeditionRank)
            for (index in keys.indices) assertEquals(if (mask and (1 shl index) != 0) 0L else null, actual[index])
        }
    }

    @Test fun optionalCountersRequireExactNativeUInt32RatherThanObjectsArraysOrSignedAliases() {
        for (key in keys) for ((type, length) in listOf(1 to 0, 4 to 4, 5 to 4, 8 to 1, 11 to 8, 14 to 4, 16 to 4)) {
            val source = save(counters = listOf(Block(key, type, ByteArray(length))))
            assertTrue(LegendsArceusSaveValidation.inspect(source) is LegendsArceusValidation.Verified)
            assertThrows(IllegalArgumentException::class.java) { LegendsArceusTrainerReadOnly.read(source) }
            val bound = session(source); assertNull(bound.legendsArceusTrainer)
            assertTrue(bound.trainerUnavailableReason!!.contains("UInt32")); assertNotNull(bound.legendsArceusStorage)
            assertTrue(bound.supports(SwitchReadCapability.PASTURES))
        }
    }

    @Test fun completeNativeHashTitleCoreAndOrderingGatesPrecedeTrainerExtraction() {
        for (size in sizes) {
            val source = save(size)
            for (offset in listOf(0, 126, 127, source.size - 33, source.size - 32, source.lastIndex)) {
                val damaged = source.copyOf(); damaged[offset] = (damaged[offset].toInt() xor 1).toByte()
                assertThrows(IllegalArgumentException::class.java) { LegendsArceusTrainerReadOnly.read(damaged) }
            }
        }
        val invalid = listOf(ByteArray(1), ByteArray(sizes[0]), fixture(game = 46),
            fixture(blocks = core(47).drop(1)), fixture(blocks = core(47).filter { it.key != 0xF25C070EL }),
            fixture(blocks = core(47).reversed(), sort = false),
            fixture(blocks = core(47) + Block(0xFFFFFFFFL, type = 1)))
        for (source in invalid) assertThrows(IllegalArgumentException::class.java) { LegendsArceusTrainerReadOnly.read(source) }
    }

    @Test fun nativeIdentityOverridesAllHintsAndSnapshotsCannotGrantOtherReadersOrWrites() {
        for (hint in SwitchGameFamily.values()) {
            val source = save(); val bound = session(source, hint); source.fill(0); bound.snapshot.bytesCopy().fill(0)
            val trainer = bound.legendsArceusTrainer!!
            assertEquals("Pokémon Legends: Arceus", bound.displayName); assertEquals("Zoë 雪♀", trainer.name)
            assertEquals(SwitchGameFamily.LEGENDS_ARCEUS, bound.profile.family); assertTrue(bound.isReadOnly)
            assertNotNull(bound.legendsArceusStorage); assertNull(bound.trainerUnavailableReason); assertFalse(bound.profile.writesEnabled)
            assertNull(bound.bdspTrainer); assertNull(bound.letsGoTrainer); assertNull(bound.swordShieldTrainer)
            assertNull(bound.bdspStorage); assertNull(bound.letsGoStorage); assertNull(bound.swordShieldStorage)
            assertNull(bound.scarletVioletStorage); assertNull(bound.legendsZaStorage)
            for (capability in SwitchReadCapability.values()) assertEquals(capability in setOf(SwitchReadCapability.SAVE_IDENTITY,
                SwitchReadCapability.TRAINER_PROFILE, SwitchReadCapability.PARTY, SwitchReadCapability.PASTURES,
                SwitchReadCapability.POKEMON_DETAILS, SwitchReadCapability.POKEDEX), bound.supports(capability))
        }
    }

    @Test fun invalidPartyCountDoesNotHideAnIndependentlyValidTrainerProfile() {
        val blocks = core(47).map { if (it.key == 0x2985FE5DL) it.copy(data = it.data.copyOf().also { b -> b[6 * 376] = 7 }) else it }
        val bound = session(fixture(blocks = blocks + counterBlocks()))
        assertTrue(bound.isFormatVerified); assertNull(bound.legendsArceusStorage); assertNotNull(bound.storageUnavailableReason)
        assertNotNull(bound.legendsArceusTrainer); assertNull(bound.trainerUnavailableReason)
        assertTrue(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertFalse(bound.supports(SwitchReadCapability.PARTY))
        assertEquals(10L, bound.legendsArceusTrainer!!.expeditionRank)
    }
}
