package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ScarletVioletTrainerReadOnlyTest {
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

    private val keys = listOf(0x4F35D0DDL, 0xADB4FE17L, 0x66A33824L)
    private val defaults = listOf(0xFFFFFFFFL, 0xFFFFFFFEL, 9999999L)
    private fun scalar(key: Long, value: Long): Block = Block(key, 10, ByteArray(4).also { put32(it, 0, value) })
    private fun counterBlocks(): List<Block> = keys.zip(defaults).map { scalar(it.first, it.second) }
    private fun save(game: Int = 50, size: Int = sizes[0], counters: List<Block> = counterBlocks(),
        changeStatus: (ByteArray) -> Unit = {}): ByteArray {
        val blocks = core(game).map { if (it.key == 0xE3E89BD1L) it.copy(data = it.data.copyOf().also(changeStatus)) else it }
        return fixture(game, size, blocks + counters)
    }
    private fun session(source: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.SWORD_SHIELD): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, source))

    @Test fun bothTitlesNativeBaseAndExpandedContainersExposeDetachedTrainerMetadata() {
        for (game in listOf(50, 51)) for (size in listOf(sizes.first(), sizes.last())) {
            val source = save(game, size); val before = source.copyOf(); val trainer = ScarletVioletTrainerReadOnly.read(source)
            if (size == sizes[0] && game == 50) assertEquals("d0a2bff1642682ff909a05083e5f9f5f98aa1ee9175ec46afb4f452e975dd193", MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it.toInt() and 255) })
            assertEquals(game, trainer.version.gameId); assertEquals("Zoë 雪♀", trainer.name); assertEquals(1, trainer.gender)
            assertEquals(2, trainer.languageId); assertEquals("English", trainer.languageName)
            assertEquals(0xFFFFFFFFL, trainer.id32); assertEquals(65535, trainer.tid16); assertEquals(65535, trainer.sid16)
            assertEquals("967295", trainer.displayTrainerId); assertEquals(2, trainer.birthMonth); assertEquals(29, trainer.birthDay)
            assertEquals(defaults[0], trainer.money); assertEquals(defaults[1], trainer.leaguePoints); assertEquals(defaults[2], trainer.blueberryPoints)
            assertArrayEquals(before, source); source.fill(0); assertEquals("Zoë 雪♀", trainer.name)
        }
    }

    @Test fun statusLanguageGenderUnsignedIdsUnicodeAndBirthdayBoundariesAreHonored() {
        val languages = mapOf(1 to "Japanese", 2 to "English", 3 to "French", 4 to "Italian", 5 to "German",
            7 to "Spanish", 8 to "Korean", 9 to "Chinese (Simplified)", 10 to "Chinese (Traditional)")
        for ((language, name) in languages) for (gender in 0..1) {
            // Runtime language uses a different enum and must not override saved status IDs.
            val source = save(counters = counterBlocks() + Block(0x25839373L, 14, ByteArray(4))) { it[7] = language.toByte(); it[5] = gender.toByte() }
            val trainer = ScarletVioletTrainerReadOnly.read(source)
            assertEquals(language, trainer.languageId); assertEquals(name, trainer.languageName); assertEquals(gender, trainer.gender)
        }
        for ((id, display) in listOf(0L to "000000", 1L to "000001", 999999L to "999999", 1000000L to "000000", 0xFFFFFFFFL to "967295"))
            assertEquals(display, ScarletVioletTrainerReadOnly.read(save { put32(it, 0, id) }).displayTrainerId)
        for (name in listOf("", "ABCDEFGHIJKL", "雪".repeat(12), "A🚀B"))
            assertEquals(name, ScarletVioletTrainerReadOnly.read(save { label(it, name) }).name)
        for ((month, day) in listOf(0 to 0, 1 to 31, 2 to 29, 4 to 30, 12 to 31)) {
            val trainer = ScarletVioletTrainerReadOnly.read(save { it[0x5A] = month.toByte(); it[0x5B] = day.toByte() })
            assertEquals(month, trainer.birthMonth); assertEquals(day, trainer.birthDay)
        }
    }

    @Test fun malformedNamesStatusAndBirthdayDisableOnlyTrainerAccessWithoutRepair() {
        val badStatus: List<(ByteArray) -> Unit> = listOf<(ByteArray) -> Unit>(
            { b -> repeat(13) { put16(b, 0x10 + it * 2, 65) } },
            { b -> label(b, ""); put16(b, 0x10, 0xD800) }, { b -> label(b, ""); put16(b, 0x10, 0xDC00) },
            { b -> label(b, ""); put16(b, 0x10, 10) }, { b -> label(b, ""); put16(b, 0x10, 127) },
            { b -> b[5] = 2 }, { b -> b[5] = 255.toByte() },
            { b -> b[7] = 0 }, { b -> b[7] = 6 }, { b -> b[7] = 11 }, { b -> b[7] = 255.toByte() }
        ) + listOf(0 to 1, 1 to 0, 2 to 30, 4 to 31, 13 to 1, 255 to 255).map { (month, day) ->
            { b: ByteArray -> b[0x5A] = month.toByte(); b[0x5B] = day.toByte() }
        }
        for (change in badStatus) {
            val source = save(changeStatus = change); val before = source.copyOf(); val bound = session(source)
            assertTrue(bound.isFormatVerified); assertNull(bound.scarletVioletTrainer); assertNotNull(bound.trainerUnavailableReason)
            assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertTrue(bound.supports(SwitchReadCapability.PARTY))
            assertTrue(bound.supports(SwitchReadCapability.PC_OR_BOXES)); assertFalse(bound.profile.writesEnabled); assertArrayEquals(before, source)
        }
        assertEquals("A", ScarletVioletTrainerReadOnly.read(save { label(it, "A"); put16(it, 0x14, 0xD800) }).name)
    }

    @Test fun everyCurrencyPresenceCombinationKeepsMissingDistinctFromSavedZero() {
        for (mask in 0 until 8) {
            val currencies = keys.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.map { scalar(it, 0L) }
            val trainer = ScarletVioletTrainerReadOnly.read(save(counters = currencies))
            val actual = listOf(trainer.money, trainer.leaguePoints, trainer.blueberryPoints)
            for (index in keys.indices) assertEquals(if (mask and (1 shl index) != 0) 0L else null, actual[index])
        }
    }

    @Test fun currenciesRequireNativeUInt32AndDoNotTreatObjectsArraysOrSignedValuesAsAliases() {
        for (key in keys) for ((type, length) in listOf(1 to 0, 4 to 4, 5 to 4, 8 to 1, 11 to 8, 14 to 4, 16 to 4)) {
            val source = save(counters = listOf(Block(key, type, ByteArray(length))))
            assertTrue(ScarletVioletSaveValidation.inspect(source) is ScarletVioletValidation.Verified)
            assertThrows(IllegalArgumentException::class.java) { ScarletVioletTrainerReadOnly.read(source) }
            val bound = session(source); assertNull(bound.scarletVioletTrainer)
            assertTrue(bound.trainerUnavailableReason!!.contains("UInt32")); assertNotNull(bound.scarletVioletStorage)
            assertTrue(bound.supports(SwitchReadCapability.PC_OR_BOXES))
        }
    }

    @Test fun completeNativeHashTitleCoreAndOrderGatesPrecedeEveryTrainerExtraction() {
        for (size in listOf(sizes.first(), sizes.last())) {
            val source = save(size = size)
            for (offset in listOf(0, 126, 127, source.size - 33, source.size - 32, source.lastIndex)) {
                val damaged = source.copyOf(); damaged[offset] = (damaged[offset].toInt() xor 1).toByte()
                assertThrows(IllegalArgumentException::class.java) { ScarletVioletTrainerReadOnly.read(damaged) }
            }
        }
        val invalid = listOf(ByteArray(1), ByteArray(sizes[0]), fixture(game = 52),
            fixture(blocks = core(50).drop(1)), fixture(blocks = core(50).filter { it.key != 0xE3E89BD1L }),
            fixture(blocks = core(50).reversed(), sort = false), fixture(blocks = core(50) + Block(0xFFFFFFFFL, type = 1)))
        for (source in invalid) assertThrows(IllegalArgumentException::class.java) { ScarletVioletTrainerReadOnly.read(source) }
    }

    @Test fun nativeIdentityOverridesHintsAndDetachedSnapshotsCannotEnableOtherReadersOrWrites() {
        for (game in listOf(50, 51)) for (hint in SwitchGameFamily.values()) {
            val source = save(game); val bound = session(source, hint); source.fill(0); bound.snapshot.bytesCopy().fill(0)
            val trainer = bound.scarletVioletTrainer!!
            assertEquals(if (game == 50) "Pokémon Scarlet" else "Pokémon Violet", bound.displayName); assertEquals("Zoë 雪♀", trainer.name)
            assertEquals(SwitchGameFamily.SCARLET_VIOLET, bound.profile.family); assertTrue(bound.isReadOnly)
            assertNotNull(bound.scarletVioletStorage); assertNull(bound.trainerUnavailableReason); assertFalse(bound.profile.writesEnabled)
            assertNull(bound.legendsArceusTrainer); assertNull(bound.bdspTrainer); assertNull(bound.letsGoTrainer); assertNull(bound.swordShieldTrainer)
            assertNull(bound.legendsArceusStorage); assertNull(bound.bdspStorage); assertNull(bound.letsGoStorage); assertNull(bound.swordShieldStorage); assertNull(bound.legendsZaStorage)
            for (capability in SwitchReadCapability.values()) assertEquals(capability in setOf(SwitchReadCapability.SAVE_IDENTITY,
                SwitchReadCapability.TRAINER_PROFILE, SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES,
                SwitchReadCapability.POKEMON_DETAILS, SwitchReadCapability.POKEDEX), bound.supports(capability))
        }
    }

    @Test fun invalidPartyCountDoesNotHideIndependentlyValidTrainerMetadata() {
        val blocks = core(50).map { if (it.key == 0x3AA1A9ADL) it.copy(data = it.data.copyOf().also { b -> b[6 * 344] = 7 }) else it }
        val bound = session(fixture(blocks = blocks + counterBlocks()))
        assertTrue(bound.isFormatVerified); assertNull(bound.scarletVioletStorage); assertNotNull(bound.storageUnavailableReason)
        assertNotNull(bound.scarletVioletTrainer); assertNull(bound.trainerUnavailableReason)
        assertTrue(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertFalse(bound.supports(SwitchReadCapability.PARTY))
        assertEquals(9999999L, bound.scarletVioletTrainer!!.blueberryPoints)
    }
}
