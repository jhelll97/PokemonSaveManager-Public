package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3PartyHpEditingTest {
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

    private fun party(count: Int = 2, pid: Int = 7, hp: Int = 20, maxHp: Int = 100): ByteArray =
        ByteArray(617) { (it * 17 + 9).toByte() }.also { raw ->
            for (slot in 0 until count) record(pid + slot, hp = hp, maxHp = maxHp).copyInto(raw, slot * 100)
        }

    private fun assertRejected(raw: ByteArray, count: Int = 2, slot: Int = 0, hp: Int = 7) {
        val original = raw.copyOf()
        var rejected = false
        try { Gen3PartyHpEditing.replace(raw, count, slot, hp) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertArrayEquals(original, raw)
    }

    @Test
    fun changesOnlyCurrentHpAcrossAllPartySizesSlotsAndBlockOrders() {
        val pids = (0 until 24).toList() + listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -2, -1)
        for (pid in pids) for (count in 1..6) for (slot in 0 until count) {
            val raw = party(count, pid)
            val original = raw.copyOf()
            val hp = if (count == 1) 7 else 0
            val edited = Gen3PartyHpEditing.replace(raw, count, slot, hp)
            val offset = slot * 100 + 86
            assertTrue(raw.indices.all { it in offset..offset + 1 || raw[it] == edited[it] })
            assertEquals(Gen3PartyHp(hp, 100),
                Gen3PartyHpEditing.read(edited.copyOfRange(slot * 100, (slot + 1) * 100)))
            assertArrayEquals(original, raw)
        }
    }

    @Test
    fun unsignedHpBoundsUseBothBytesAndPreserveMaximumHp() {
        for (hp in listOf(0, 1, 255, 256, 32768, 65535)) {
            val raw = party(maxHp = 65535)
            val edited = Gen3PartyHpEditing.replace(raw, 2, 0, hp)
            assertEquals(hp and 255, edited[86].toInt() and 255)
            assertEquals(hp ushr 8, edited[87].toInt() and 255)
            assertEquals(Gen3PartyHp(hp, 65535), Gen3PartyHpEditing.read(edited.copyOfRange(0, 100)))
            assertEquals(255, edited[88].toInt() and 255)
            assertEquals(255, edited[89].toInt() and 255)
        }
    }

    @Test
    fun lastUsablePokemonIsProtectedAndEggsNeverCountAsUsable() {
        val raw = party()
        record(8, hp = 0).copyInto(raw, 100)
        assertRejected(raw, hp = 0)
        assertRejected(party(1), count = 1, hp = 0)
        record(8, egg = true).copyInto(raw, 100)
        assertRejected(raw, hp = 0)
        assertRejected(raw, slot = 1)
        val edited = Gen3PartyHpEditing.replace(raw, 2, 0, 1)
        assertEquals(1, Gen3PartyHpEditing.read(edited.copyOfRange(0, 100)).current)
    }

    @Test
    fun faintedPokemonCanBeRevivedWithoutChangingStatusAndNoOpIsDetached() {
        val raw = party(hp = 0)
        record(status = 8, hp = 0).copyInto(raw)
        val edited = Gen3PartyHpEditing.replace(raw, 2, 0, 37)
        assertEquals(Gen3PartyHp(37, 100), Gen3PartyHpEditing.read(edited.copyOfRange(0, 100)))
        assertArrayEquals(raw.copyOfRange(80, 84), edited.copyOfRange(80, 84))
        val original = raw.copyOf()
        val noOp = Gen3PartyHpEditing.replace(raw, 2, 0, 0)
        assertArrayEquals(original, noOp)
        noOp[0] = (noOp[0].toInt() xor 1).toByte()
        assertArrayEquals(original, raw)
    }

    @Test
    fun independentWireFixtureKeepsStatusPk3AndOtherPartyBytesExact() {
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val member = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f20212213d12526ccfb758980b721b55443ed614a57d16564139d11382f49dd8447526e584fe97d6c1b9529681f992d3c2b45d9f0e77185" +
            "030000003200140064000102030405060708090a")
        val raw = party(1).also { member.copyInto(it) }
        val expected = raw.copyOf().also { it[86] = 7 }
        assertEquals(Gen3PartyHp(20, 100), Gen3PartyHpEditing.read(member))
        assertArrayEquals(expected, Gen3PartyHpEditing.replace(raw, 1, 0, 7))
    }

    @Test
    fun invalidSelectionsHpSourceAndOtherActiveRecordsRejectWithoutMutation() {
        val raw = party()
        assertRejected(raw.copyOf(599))
        for (count in listOf(0, 7)) assertRejected(raw, count = count)
        for (slot in listOf(-1, 2, Int.MAX_VALUE)) assertRejected(raw, slot = slot)
        for (hp in listOf(-1, 101, 65536, Int.MAX_VALUE)) assertRejected(raw, hp = hp)
        val bad = listOf(record(hp = 101), record(hp = 0, maxHp = 0), record(level = 0),
            record(level = 101), record(status = 0x1000), record(status = 24),
            record().also { it[0x13] = 3 }, record().also { it[0x13] = 0 },
            record().also { it[79] = (it[79].toInt() xor 1).toByte() },
            record(egg = true).also { it[0x13] = 2 }, record().also { it[0x13] = 6 })
        for (member in bad) {
            val source = party().also { member.copyInto(it, 100) }
            assertRejected(source)
        }
        var pcRejected = false
        try { Gen3PartyHpEditing.read(record().copyOf(80)) }
        catch (_: IllegalArgumentException) { pcRejected = true }
        assertTrue(pcRejected)
    }
}
