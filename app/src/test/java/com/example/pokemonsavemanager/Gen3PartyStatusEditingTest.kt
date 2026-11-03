package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3PartyStatusEditingTest {
    private fun record(pid: Int = 7, status: Long = 0, hp: Int = 20, maxHp: Int = 100,
        level: Int = 50, egg: Boolean = false): ByteArray {
        val header = ByteArray(32) { (it + 7).toByte() }
        header[0x13] = (2 or (if (egg) 4 else 0)).toByte()
        val canonical = ByteArray(48) { (it * 13 + 3).toByte() }
        canonical[0] = 25
        canonical[1] = 0
        canonical[43] = ((canonical[43].toInt() and 0xBF) or (if (egg) 0x40 else 0)).toByte()
        val stored = Gen3Pk3Codec.encode(header, pid, 12345, 54321, canonical)
        return ByteArray(100) { (it * 7 + 1).toByte() }.also { raw ->
            stored.copyInto(raw)
            for (index in 0 until 4) raw[80 + index] = (status ushr (index * 8)).toByte()
            raw[84] = level.toByte()
            raw[86] = hp.toByte()
            raw[87] = (hp ushr 8).toByte()
            raw[88] = maxHp.toByte()
            raw[89] = (maxHp ushr 8).toByte()
        }
    }

    private fun assertRejected(raw: ByteArray,
        status: Gen3PartyStatus = Gen3PartyStatus(Gen3StatusCondition.BURN)) {
        val original = raw.copyOf()
        var rejected = false
        try { Gen3PartyStatusEditing.replace(raw, status) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertArrayEquals(original, raw)
    }

    @Test
    fun everyConditionPreservesPk3HpMailAndStatsAcrossAllBlockOrders() {
        val pids = (0 until 24).toList() + listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -2, -1)
        val statuses = listOf(Gen3PartyStatus(Gen3StatusCondition.HEALTHY),
            Gen3PartyStatus(Gen3StatusCondition.SLEEP, sleepTurns = 3),
            Gen3PartyStatus(Gen3StatusCondition.POISON), Gen3PartyStatus(Gen3StatusCondition.BURN),
            Gen3PartyStatus(Gen3StatusCondition.FREEZE), Gen3PartyStatus(Gen3StatusCondition.PARALYSIS),
            Gen3PartyStatus(Gen3StatusCondition.BAD_POISON, toxicCounter = 15))
        for (pid in pids) for (status in statuses) {
            val raw = record(pid, status = 8)
            val original = raw.copyOf()
            val edited = Gen3PartyStatusEditing.replace(raw, status)
            assertEquals(status, Gen3PartyStatusEditing.read(edited))
            assertTrue(raw.indices.all { it in 80..83 || raw[it] == edited[it] })
            assertArrayEquals(raw.copyOfRange(0, 80), edited.copyOfRange(0, 80))
            assertArrayEquals(raw.copyOfRange(84, 100), edited.copyOfRange(84, 100))
            assertArrayEquals(original, raw)
            assertArrayEquals(edited, Gen3PartyStatusEditing.replace(edited, status))
        }
    }

    @Test
    fun sleepAndBadPoisonCountersUseNativeBitPositions() {
        for (turns in 1..7) {
            val status = Gen3PartyStatus(Gen3StatusCondition.SLEEP, sleepTurns = turns)
            val edited = Gen3PartyStatusEditing.replace(record(), status)
            assertEquals(turns, edited[80].toInt() and 255)
            assertEquals(0, edited[81].toInt() and 255)
            assertEquals(status, Gen3PartyStatusEditing.read(edited))
        }
        for (counter in 0..15) {
            val status = Gen3PartyStatus(Gen3StatusCondition.BAD_POISON, toxicCounter = counter)
            val edited = Gen3PartyStatusEditing.replace(record(), status)
            assertEquals(128, edited[80].toInt() and 255)
            assertEquals(counter, edited[81].toInt() and 255)
            assertEquals(0, edited[82].toInt() and 255)
            assertEquals(0, edited[83].toInt() and 255)
            assertEquals(status, Gen3PartyStatusEditing.read(edited))
        }
    }

    @Test
    fun fixedWireFixtureKeepsEncryptedRecordAndAllOtherExtensionBytesExact() {
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        // Independent PID % 24 = 7 PK3 fixture; native party status is the following LE u32.
        val raw = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f20212213d12526ccfb758980b721b55443ed614a57d16564139d11382f49dd8447526e584fe97d6c1b9529681f992d3c2b45d9f0e77185" +
            "030000003200140064000102030405060708090a")
        val expected = raw.copyOf().also { it[80] = 0x80.toByte(); it[81] = 15 }
        assertEquals(Gen3PartyStatus(Gen3StatusCondition.SLEEP, sleepTurns = 3), Gen3PartyStatusEditing.read(raw))
        assertArrayEquals(expected, Gen3PartyStatusEditing.replace(raw,
            Gen3PartyStatus(Gen3StatusCondition.BAD_POISON, toxicCounter = 15)))
    }

    @Test
    fun reservedBitsConflictingConditionsAndOrphanCountersAreRejected() {
        for (status in listOf(0x1000L, 0x10000L, 0x80000000L, 0xFFFFFFFFL,
            8L or 16L, 3L or 64L, 128L or 8L, 0x100L, 0x110L)) {
            assertRejected(record(status = status))
        }
    }

    @Test
    fun invalidRequestedStatesAndNewConditionsOnFaintedPokemonAreRejected() {
        val raw = record()
        listOf(Gen3PartyStatus(Gen3StatusCondition.SLEEP),
            Gen3PartyStatus(Gen3StatusCondition.SLEEP, sleepTurns = 8),
            Gen3PartyStatus(Gen3StatusCondition.BURN, sleepTurns = 1),
            Gen3PartyStatus(Gen3StatusCondition.BAD_POISON, toxicCounter = -1),
            Gen3PartyStatus(Gen3StatusCondition.BAD_POISON, toxicCounter = 16),
            Gen3PartyStatus(Gen3StatusCondition.HEALTHY, toxicCounter = 1)).forEach { assertRejected(raw, it) }
        assertRejected(record(hp = 0))
        val fainted = record(status = 128L or (15L shl 8), hp = 0)
        val cured = Gen3PartyStatusEditing.replace(fainted, Gen3PartyStatus(Gen3StatusCondition.HEALTHY))
        assertEquals(Gen3PartyStatus(Gen3StatusCondition.HEALTHY), Gen3PartyStatusEditing.read(cured))
        assertTrue((80..83).all { cured[it] == 0.toByte() })
        assertArrayEquals(fainted.copyOfRange(84, 100), cured.copyOfRange(84, 100))
    }

    @Test
    fun pcRecordsEggsCorruptPk3AndInvalidPartyFieldsAreRejected() {
        val valid = record()
        val invalid = listOf(record(egg = true),
            record(egg = true).also { it[0x13] = 2 }, record().also { it[0x13] = 6 },
            record().also { it[0x13] = 3 }, record().also { it[0x13] = 0 },
            record().also { it[79] = (it[79].toInt() xor 1).toByte() },
            record(hp = 101), record(hp = 0, maxHp = 0), record(level = 0), record(level = 101),
            valid.copyOf(79), valid.copyOf(80), valid.copyOf(99), valid.copyOf(101))
        invalid.forEach { assertRejected(it) }
    }
}
