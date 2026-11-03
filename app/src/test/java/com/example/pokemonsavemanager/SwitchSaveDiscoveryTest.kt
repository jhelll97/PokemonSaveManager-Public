package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class SwitchSaveDiscoveryTest {
    private val regions = listOf(0x00000 to 0x00D90, 0x00E00 to 0x00200, 0x01000 to 0x00168,
        0x01200 to 0x01800, 0x02A00 to 0x020E8, 0x04C00 to 0x00930, 0x05600 to 0x00004,
        0x05800 to 0x00130, 0x05A00 to 0x00012, 0x05C00 to 0x3F7A0, 0x45400 to 0x00008,
        0x45600 to 0x00E90, 0x46600 to 0x010A4, 0x47800 to 0x000F0, 0x47A00 to 0x06010,
        0x4DC00 to 0x00200, 0x4DE00 to 0x00098, 0x4E000 to 0x00068, 0x4E200 to 0x69780,
        0xB7A00 to 0x000B0, 0xB7C00 to 0x00940)
    private fun put16(b: ByteArray, i: Int, n: Int) { b[i] = n.toByte(); b[i + 1] = (n ushr 8).toByte() }
    private fun put32(b: ByteArray, i: Int, n: Long) { put16(b, i, n.toInt()); put16(b, i + 2, (n ushr 16).toInt()) }
    private fun word(b: ByteArray, i: Int): Int = (b[i].toInt() and 255) or ((b[i + 1].toInt() and 255) shl 8)
    private fun crc(b: ByteArray, start: Int, len: Int): Int {
        var state = 0
        for (i in start until start + len) {
            state = state xor (b[i].toInt() and 255)
            repeat(8) { state = if (state and 1 == 0) state ushr 1 else (state ushr 1) xor 0xA001 }
        }
        return state
    }
    private fun seal(b: ByteArray) {
        put32(b, 0xB8610, 0x42454546L)
        for ((id, region) in regions.withIndex()) {
            val entry = 0xB8614 + id * 8
            put32(b, entry, region.second.toLong()); put16(b, entry + 4, id)
            put16(b, entry + 6, crc(b, region.first, region.second))
        }
    }
    private val species = (1..151).toList() + listOf(808, 809)
    private fun flag(b: ByteArray, offset: Int, bit: Int) { val i = 0x2A00 + offset + bit / 8; b[i] = (b[i].toInt() or (1 shl (bit % 8))).toByte() }
    private fun fixture(game: Int = 42): ByteArray = ByteArray(0x100000).also { b ->
        b[0x1004] = game.toByte(); b[0x1005] = 1; b[0x1035] = 2
        repeat(7) { put16(b, 0x5A00 + it * 2, 1001) }
        put32(b, 0x2A00, 0x2F120F17L); put32(b, 0x2A04, 1L or (809L shl 9))
        for (group in 0..3) for (index in 0 until 186) {
            val o = 0x3978 + 6 * (index + group * 186); b[o] = 0xFE.toByte(); b[o + 2] = 0x7F
        }
        flag(b, 0x88, 0); flag(b, 0xF0, 0); flag(b, 0x550, 0)
        flag(b, 0x88, 807); flag(b, 0xF0 + 3 * 0x8C, 808); flag(b, 0xF0 + 7 * 0x8C, 808)
        flag(b, 0x550, 808 * 9 + 8); seal(b)
    }
    private fun snapshot(b: ByteArray, hint: SwitchGameFamily = SwitchGameFamily.LEGENDS_ZA): SwitchReadSnapshot = SwitchReadSnapshot.capture(hint, b)
    private val bdspLayouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private fun bdsp(layout: Int, title: Int): ByteArray = ByteArray(bdspLayouts[layout].first).also {
        put32(it, 0, bdspLayouts[layout].second.toLong()); it[0x79BDF] = title.toByte()
        MessageDigest.getInstance("MD5").digest(it).copyInto(it, 0xE9818)
    }

    @Test fun letsGoNativeIdentityOverridesEveryHintWithoutConfusingGen7With3ds() {
        for (game in listOf(42, 43)) for (hint in SwitchGameFamily.values()) {
            val bytes = fixture(game); val before = bytes.copyOf(); val captured = snapshot(bytes, hint)
            val identity = SwitchSaveDiscovery.inspect(captured)!!
            assertEquals(SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, identity.family); assertEquals(7, identity.family.generation)
            assertEquals(if (game == 42) "Let's Go Pikachu" else "Let's Go Eevee", identity.game)
            assertEquals(bytes.size, identity.byteCount); assertEquals(captured.sourceSha256, identity.sha256); assertArrayEquals(before, bytes)
            bytes.fill(0); captured.bytesCopy().fill(0); assertEquals(identity, SwitchSaveDiscovery.inspect(captured))
        }
    }

    @Test fun bdspTitlesAndAllFourRevisionsValidateWithoutRequiringTrainerOrPokemonMetadata() {
        for (layout in bdspLayouts.indices) for (title in 0..1) {
            val b = bdsp(layout, title); val before = b.copyOf(); val identity = SwitchSaveDiscovery.inspect(snapshot(b))!!
            assertEquals(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, identity.family); assertEquals(8, identity.family.generation)
            assertEquals(if (title == 0) "Pokémon Brilliant Diamond" else "Pokémon Shining Pearl", identity.game)
            assertEquals(b.size, identity.byteCount); assertArrayEquals(before, b)
        }
    }

    @Test fun sizeHashesCrcsAndNativeTitlesCannotBeBypassedByASelectedFamilyOrFileName() {
        for (b in listOf(byteArrayOf(42), ByteArray(0x100000), ByteArray(0xE9828), fixture().also { it[0] = 1 },
            fixture(41), bdsp(3, 2), bdsp(3, 0).also { it[it.lastIndex] = 1 })) {
            val before = b.copyOf()
            for (hint in SwitchGameFamily.values()) assertNull(SwitchSaveDiscovery.inspect(snapshot(b, hint)))
            assertArrayEquals(before, b)
        }
    }
}
