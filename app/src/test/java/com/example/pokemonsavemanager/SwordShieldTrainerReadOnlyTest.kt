package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class SwordShieldTrainerReadOnlyTest {
    private fun word(b: ByteArray, offset: Int, value: Int) { b[offset] = value.toByte(); b[offset + 1] = (value ushr 8).toByte() }
    private fun uint(b: ByteArray, offset: Int, value: Long) { repeat(4) { b[offset + it] = (value ushr (it * 8)).toByte() } }
    private fun status(game: Int = 44, name: String = "Éclair 雪", gender: Int = 1, language: Int = 2,
        id: Long = 0xFFFFFFFFL, watts: Long = 9999999L, jersey: String = "007"): ByteArray = ByteArray(0x110).also { b ->
        jersey.toByteArray(Charsets.US_ASCII).copyInto(b, 1)
        uint(b, 0xA0, id); b[0xA4] = game.toByte(); b[0xA5] = gender.toByte(); b[0xA7] = language.toByte()
        name.toByteArray(Charsets.UTF_16LE).copyInto(b, 0xB0); uint(b, 0xD0, watts)
    }
    private class ReferenceStream(key: Long) {
        private var state = key; private var shift = 0
        init { repeat(java.lang.Long.bitCount(key)) { advance() } }
        private fun advance() { state = (state xor (state shl 2)) and 0xFFFFFFFFL; state = state xor (state ushr 15); state = (state xor (state shl 13)) and 0xFFFFFFFFL }
        fun next(): Int { val v = ((state ushr shift) and 255).toInt(); shift += 8; if (shift == 32) { shift = 0; advance() }; return v }
        fun word(): Long = (0..3).fold(0L) { a, i -> a or (next().toLong() shl (8 * i)) }
    }
    private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val intro = hex("9ec99cd70ed33c44fb9303dceb39b42a1947e9634ba2334416bf82a2ba6355b63d9df24b5f7b6ab2621dc21b68e5c8b53a059000e8a8103de2ecf00cb2ed4f6d")
    private val outro = hex("d6c01c598bc8b8cb46e153fc828c757513e045df32693c75f059f8d9a25fb217e08052dbea8973997579afcb2e8007e6f126e0030ae66ff641bf7e59c2ae55fd")
    private val pad = hex("a092d10607db32a1ae01f5c51e844fe353ca37f4a7b04da018b7c297da5f532b75fa4816f8d48a6f6105f4e2fd04b5a30ffc4492cb32e61bb9b12e01b0565336d2d1503dde5b2e0e52fddf2f7bca6350a4675d2317c052e1a6307c2bb670365b2a276933f5637b363f269ba3ed7a5300a448b3509e14a052de7e102b1b776e")
    private fun fixture(statusBytes: ByteArray = status(), partyCount: Int = 0): ByteArray {
        val out = ByteArrayOutputStream()
        fun block(key: Long, data: ByteArray) {
            val stream = ReferenceStream(key); val header = ByteArray(9)
            uint(header, 0, key); header[4] = (4 xor stream.next()).toByte(); uint(header, 5, data.size.toLong() xor stream.word())
            out.write(header); for (b in data) out.write((b.toInt() and 255) xor stream.next())
        }
        block(0x0D66012C, ByteArray(0x50A00)); block(0x2985FE5D, ByteArray(0x814).also { it[0x810] = partyCount.toByte() })
        block(0x4716C404, ByteArray(0x4B00)); block(0xF25C070E, statusBytes)
        block(0xFFFFFFFFL, ByteArray(0x1716B3 - 32 - out.size() - 9))
        val payload = out.toByteArray()
        for (i in payload.indices) payload[i] = (payload[i].toInt() xor pad[i % pad.size].toInt()).toByte()
        val hash = MessageDigest.getInstance("SHA-256"); hash.update(intro); hash.update(payload); hash.update(outro)
        return payload + hash.digest()
    }
    private fun session(bytes: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSession =
        SwitchReadSession(SwitchReadSnapshot.capture(hint, bytes))

    @Test fun nativeStatusFieldsForBothTitlesAreUnsignedReadOnlyAndHintIndependent() {
        for (game in listOf(44, 45)) {
            val source = fixture(status(game)); val before = source.copyOf()
            if (game == 44) assertArrayEquals(hex("4065aa45c2ea2767e46e0559d3c504689ac560bead17887bbc468f85d220ac77"),
                source.copyOfRange(source.size - 32, source.size))
            val profile = SwordShieldTrainerReadOnly.read(source)
            assertEquals(game, profile.version.gameId); assertEquals("Éclair 雪", profile.name)
            assertEquals(1, profile.gender); assertEquals(2, profile.languageId); assertEquals("English", profile.languageName)
            assertEquals("007", profile.jerseyNumber); assertEquals(0xFFFFFFFFL, profile.id32)
            assertEquals(65535, profile.tid16); assertEquals(65535, profile.sid16); assertEquals("967295", profile.displayTrainerId)
            assertEquals(9999999L, profile.watts); assertArrayEquals(before, source)
            for (hint in SwitchGameFamily.values()) {
                val bound = session(source, hint)
                assertEquals(SwitchGameFamily.SWORD_SHIELD, bound.profile.family)
                assertTrue(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertEquals(profile, bound.swordShieldTrainer)
                assertTrue(bound.supports(SwitchReadCapability.PARTY)); assertTrue(bound.supports(SwitchReadCapability.PC_OR_BOXES))
                assertFalse(bound.profile.writesEnabled); assertTrue(bound.isReadOnly)
                assertFalse(bound.supports(SwitchReadCapability.PSE_BANK_READ_ONLY))
            }
        }
    }

    @Test fun allNineNativeLanguagesAndBothTrainerGendersHaveExactLabels() {
        val labels = mapOf(1 to "Japanese", 2 to "English", 3 to "French", 4 to "Italian", 5 to "German",
            7 to "Spanish", 8 to "Korean", 9 to "Chinese (Simplified)", 10 to "Chinese (Traditional)")
        for ((language, label) in labels) {
            val trainer = SwordShieldTrainerReadOnly.read(fixture(status(language = language, gender = language % 2)))
            assertEquals(label, trainer.languageName); assertEquals(language, trainer.languageId); assertEquals(language % 2, trainer.gender)
        }
    }

    @Test fun nameJerseyAndIdBoundariesPreserveLeadingZerosAndIgnoreNameTrashAfterTerminator() {
        val cases = listOf(Triple("", "", 0L), Triple("ABCDEFGHIJKL", "999", 1L),
            Triple("雪😀", "1", 1000000L), Triple("Éclair", "00", 0x80000000L))
        val expected = listOf("000000", "000001", "000000", "483648")
        cases.forEachIndexed { i, (name, jersey, id) ->
            val block = status(name = name, jersey = jersey, id = id, watts = 0)
            if (i == 0) { block[0xB2] = 0; block[0xB3] = 0xD8.toByte() } // malformed unused trash after the terminator
            val trainer = SwordShieldTrainerReadOnly.read(fixture(block))
            assertEquals(name, trainer.name); assertEquals(jersey, trainer.jerseyNumber); assertEquals(id, trainer.id32)
            assertEquals(expected[i], trainer.displayTrainerId); assertEquals(0L, trainer.watts)
        }
    }

    @Test fun malformedTrainerFieldsDisableOnlyTheTrainerViewWithoutRepairingAnything() {
        val invalid = listOf(status(gender = 2), status(gender = 255), status(language = 0), status(language = 6),
            status(language = 11), status(language = 255), status(watts = 10000000), status(watts = 0xFFFFFFFFL),
            status(jersey = "-1"), status(jersey = "0A1"), status().also { it[1] = 0; it[2] = 49 },
            status(name = "Line\nBreak"), status(name = "x").also { word(it, 0xB0, 0xD800); word(it, 0xB2, 0) },
            status(name = "").also { for (i in 0..12) word(it, 0xB0 + i * 2, 65) })
        for (block in invalid) {
            val source = fixture(block); val before = source.copyOf()
            assertThrows(IllegalArgumentException::class.java) { SwordShieldTrainerReadOnly.read(source) }
            val bound = session(source)
            assertTrue(bound.supports(SwitchReadCapability.SAVE_IDENTITY)); assertFalse(bound.supports(SwitchReadCapability.TRAINER_PROFILE))
            assertNotNull(bound.trainerUnavailableReason); assertNull(bound.swordShieldTrainer)
            assertTrue(bound.supports(SwitchReadCapability.PARTY)); assertTrue(bound.supports(SwitchReadCapability.PC_OR_BOXES))
            assertArrayEquals(before, source)
        }
    }

    @Test fun partyCountFailureDoesNotSuppressValidTrainerAndSnapshotCopiesCannotMutateMetadata() {
        val source = fixture(status(45), partyCount = 255); val bound = session(source)
        val profile = bound.swordShieldTrainer!!
        source.fill(0); bound.snapshot.bytesCopy().fill(0)
        assertEquals("Éclair 雪", profile.name); assertEquals("007", bound.swordShieldTrainer!!.jerseyNumber)
        assertTrue(bound.supports(SwitchReadCapability.TRAINER_PROFILE)); assertFalse(bound.supports(SwitchReadCapability.PARTY))
        assertFalse(bound.supports(SwitchReadCapability.PC_OR_BOXES)); assertNotNull(bound.storageUnavailableReason)
        assertTrue(bound.supports(SwitchReadCapability.SAVE_IDENTITY)); assertTrue(bound.isReadOnly)
    }

    @Test fun fullContainerGateStillRejectsHashDamageUnknownSizesWrongTitleAndWrongStatusLength() {
        val valid = fixture(); val badHash = valid.copyOf().also { it[127] = (it[127].toInt() xor 1).toByte() }
        for (source in listOf(badHash, ByteArray(1), fixture(status(43)), fixture(status().copyOf(0x111))))
            assertThrows(IllegalArgumentException::class.java) { SwordShieldTrainerReadOnly.read(source) }
        val unknown = session(byteArrayOf(1))
        assertNull(unknown.swordShieldTrainer); assertFalse(unknown.supports(SwitchReadCapability.TRAINER_PROFILE))
    }
}
