package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3MetLocationEditingTest {
    private fun record(pid: Int, origin: Int = 3, location: Int = 16, party: Boolean = false,
        egg: Boolean = false, metLevel: Int = 30, ball: Int = 4): ByteArray {
        val header = ByteArray(32) { (it + 7).toByte() }
        header[0x13] = (2 or (if (egg) 4 else 0)).toByte()
        val canonical = ByteArray(48) { (it * 13 + 3).toByte() }
        canonical[0] = 25
        canonical[1] = 0
        canonical[37] = location.toByte()
        val word = metLevel or (origin shl 7) or (ball shl 11)
        canonical[38] = word.toByte()
        canonical[39] = (word ushr 8).toByte()
        canonical[43] = ((canonical[43].toInt() and 0xBF) or (if (egg) 0x40 else 0)).toByte()
        val stored = Gen3Pk3Codec.encode(header, pid, 12345, 54321, canonical)
        return if (!party) stored else ByteArray(100) { (it * 7 + 1).toByte() }.also { stored.copyInto(it) }
    }

    private fun assertRejected(raw: ByteArray, location: Int = 55) {
        val original = raw.copyOf()
        var rejected = false
        try { Gen3MetLocationEditing.replace(raw, location) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertArrayEquals(original, raw)
    }

    @Test
    fun titleCatalogsUseNativeIdsAndCorrectRegionalNames() {
        val sapphire = Gen3MetLocationEditing.choices(1)
        val ruby = Gen3MetLocationEditing.choices(2)
        val emerald = Gen3MetLocationEditing.choices(3)
        val fireRed = Gen3MetLocationEditing.choices(4)
        assertEquals((0..86).toList(), sapphire.map { it.id })
        assertEquals(sapphire.map { it.id }, ruby.map { it.id })
        assertEquals((0..86).toList() + (197..212).toList(), emerald.map { it.id })
        assertEquals((88..196).toList(), fireRed.map { it.id })
        assertEquals(fireRed, Gen3MetLocationEditing.choices(5))
        assertEquals("ROUTE 101", emerald.single { it.id == 16 }.name)
        assertEquals("BATTLE TOWER", ruby.single { it.id == 58 }.name)
        assertEquals("BATTLE FRONTIER", emerald.single { it.id == 58 }.name)
        assertEquals("MAGMA HIDEOUT", ruby.single { it.id == 66 }.name)
        assertEquals("AQUA HIDEOUT", sapphire.single { it.id == 66 }.name)
        assertEquals("PALLET TOWN", fireRed.first().name)
        assertEquals("CELADON DEPT.", fireRed.last().name)
        assertEquals("FARAWAY ISLAND", emerald.single { it.id == 201 }.name)
        assertTrue((sapphire + ruby + emerald + fireRed).all { it.name.isNotBlank() })
    }

    @Test
    fun changesOnlyMetLocationAcrossAllOrdersOriginsAndPartyRecords() {
        val pids = (0 until 24).toList() + listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -2, -1)
        for (pid in pids) for (origin in 1..5) for (party in listOf(false, true)) {
            val sourceLocation = if (origin >= 4) 88 else 16
            val target = when (origin) { 3 -> 212; 4, 5 -> 196; else -> 85 }
            val raw = record(pid, origin, sourceLocation, party)
            val original = raw.copyOf()
            val before = Gen3Pk3Codec.decode(raw.copyOfRange(0, 80)).canonicalSection
            val edited = Gen3MetLocationEditing.replace(raw, target)
            val after = Gen3Pk3Codec.decode(edited.copyOfRange(0, 80)).canonicalSection
            assertEquals(Gen3MetLocationMetadata(origin, target), Gen3MetLocationEditing.read(edited))
            assertTrue((0 until 48).all { it == 37 || before[it] == after[it] })
            assertTrue((0 until 32).all { it in 28..29 || raw[it] == edited[it] })
            assertArrayEquals(raw.copyOfRange(80, raw.size), edited.copyOfRange(80, edited.size))
            assertArrayEquals(original, raw)
        }
    }

    @Test
    fun independentWireFixtureChecksLocationByteAndUnsignedPid() {
        // Independently packed LE words, PID % 24 = 7 => A,G,M,E; checksum=sum(u16), XOR=PID^OID.
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val raw = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f20212213d12526ccfb758980b721b55443ed614a57d16564139d11382f49dd8447526e584fe97d6c1b9529681f992d3c2b45d9f0e77185")
        val expected = hex("674523f13412efbe0f10111213141516171819021b1c1d1e1f202122138a2526ccfb758980b721b55443ed614a57d16564139d11382f49dd849e526e584fe97d6c1b9529681f992d3c2b45d9f0e77185")
        assertEquals(Gen3MetLocationMetadata(3, 16), Gen3MetLocationEditing.read(raw))
        assertEquals(Gen3MetLocationMetadata(3, 201), Gen3MetLocationEditing.read(expected))
        assertArrayEquals(expected, Gen3MetLocationEditing.replace(raw, 201))
    }

    @Test
    fun unchangedLocationIsExactAndDoesNotMutateInput() {
        for (party in listOf(false, true)) {
            val raw = record(-1, party = party)
            val original = raw.copyOf()
            val result = Gen3MetLocationEditing.replace(raw, 16)
            assertArrayEquals(original, result)
            result[0] = (result[0].toInt() xor 1).toByte()
            assertArrayEquals(original, raw)
        }
    }

    @Test
    fun specialLocationsInvalidIdsAndWrongOriginRegionsAreProtected() {
        for (special in 253..255) {
            val raw = record(7, location = special)
            assertEquals(special, Gen3MetLocationEditing.read(raw).location)
            assertRejected(raw)
            assertRejected(record(7), special)
        }
        for (invalid in listOf(-1, 87, 88, 196, 213, 252, 256, Int.MAX_VALUE)) {
            assertRejected(record(7, origin = 1), invalid)
        }
        assertRejected(record(7, origin = 2), 201)
        assertRejected(record(7, origin = 4, location = 88), 16)
        assertRejected(record(7, origin = 5, location = 88), 201)
        assertRejected(record(7, origin = 15))
        assertRejected(record(7, origin = 0))
        assertRejected(record(7, location = 87))
        assertRejected(record(7, location = 252))
    }

    @Test
    fun eggsCorruptRecordsAndMalformedCaptureFieldsAreRejected() {
        val valid = record(1)
        val invalid = listOf(record(1, egg = true),
            record(1, egg = true).also { it[0x13] = 2 }, record(1).also { it[0x13] = 6 },
            record(1).also { it[0x13] = 3 }, record(1).also { it[0x13] = 0 },
            record(1).also { it[79] = (it[79].toInt() xor 1).toByte() },
            record(1, metLevel = 127), record(1, ball = 0), record(1, ball = 13),
            valid.copyOf(79), valid.copyOf(81), valid.copyOf(99), valid.copyOf(101))
        invalid.forEach { assertRejected(it) }
    }
}
