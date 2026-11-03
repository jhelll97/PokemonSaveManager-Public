package com.example.pokemonsavemanager

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class SwitchReadOnlyCatalogTest {
    @Test fun plannedFamiliesAndImplementedReadersAreDistinctFromPerFileValidity() {
        val profiles = SwitchReadOnlyCatalog.profiles
        assertEquals(6, profiles.size)
        assertEquals(SwitchGameFamily.values().toSet(), profiles.map { it.family }.toSet())
        for (profile in profiles) {
            assertFalse(profile.writesEnabled)
            val expected = if (profile.family == SwitchGameFamily.LETS_GO_PIKACHU_EEVEE)
                setOf(SwitchReadCapability.SAVE_IDENTITY, SwitchReadCapability.PARTY,
                    SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS, SwitchReadCapability.TRAINER_PROFILE, SwitchReadCapability.POKEDEX, SwitchReadCapability.INVENTORY)
            else if (profile.family == SwitchGameFamily.SWORD_SHIELD) setOf(SwitchReadCapability.SAVE_IDENTITY,
                SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS,
                SwitchReadCapability.TRAINER_PROFILE, SwitchReadCapability.POKEDEX, SwitchReadCapability.INVENTORY)
            else if (profile.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL) setOf(SwitchReadCapability.SAVE_IDENTITY,
                SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS,
                SwitchReadCapability.TRAINER_PROFILE, SwitchReadCapability.POKEDEX, SwitchReadCapability.INVENTORY)
            else if (profile.family == SwitchGameFamily.LEGENDS_ARCEUS) setOf(SwitchReadCapability.SAVE_IDENTITY,
                SwitchReadCapability.PARTY, SwitchReadCapability.PASTURES, SwitchReadCapability.POKEMON_DETAILS,
                SwitchReadCapability.TRAINER_PROFILE, SwitchReadCapability.POKEDEX, SwitchReadCapability.INVENTORY)
            else if (profile.family == SwitchGameFamily.SCARLET_VIOLET) setOf(SwitchReadCapability.SAVE_IDENTITY,
                SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS,
                SwitchReadCapability.TRAINER_PROFILE, SwitchReadCapability.POKEDEX, SwitchReadCapability.INVENTORY)
            else if (profile.family == SwitchGameFamily.LEGENDS_ZA) setOf(SwitchReadCapability.SAVE_IDENTITY,
                SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS,
                SwitchReadCapability.TRAINER_PROFILE, SwitchReadCapability.POKEDEX, SwitchReadCapability.INVENTORY)
            else emptySet()
            assertEquals(expected, profile.capabilities)
            assertTrue(profile.plannedCapabilities.contains(SwitchReadCapability.PARTY))
            for (capability in SwitchReadCapability.values()) assertEquals(capability in expected, profile.supports(capability))
        }
        assertTrue(SwitchReadOnlyCatalog.profileFor(SwitchGameFamily.LEGENDS_ARCEUS)
            .plannedCapabilities.contains(SwitchReadCapability.PASTURES))
    }

    @Test fun snapshotHasFixedHashAndNoMutableAliasesInEitherDirection() {
        val input = byteArrayOf(1, 2, 3, 4)
        val snapshot = SwitchReadSnapshot.capture(SwitchGameFamily.SWORD_SHIELD, input)
        input.fill(99)
        val returned = snapshot.bytesCopy()
        returned.fill(88)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), snapshot.bytesCopy())
        assertEquals("9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a", snapshot.sourceSha256)
        assertEquals(4, snapshot.byteCount)
    }

    @Test fun sessionBindsSelectedFamilyAndNeverClaimsFormatValidity() {
        for (family in SwitchGameFamily.values()) {
            val session = SwitchReadSession(SwitchReadSnapshot.capture(family, byteArrayOf(42)))
            assertEquals(family, session.profile.family)
            assertTrue(session.isReadOnly)
            assertFalse(session.isFormatVerified)
            assertFalse(session.supports(SwitchReadCapability.SAVE_IDENTITY))
            assertFalse(session.supports(SwitchReadCapability.PARTY))
        }
    }

    @Test fun unknownSizeAndShortReadsDoNotDependOnAvailableOrFilename() {
        var closed = false
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) {
            override fun available(): Int = 0
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                super.read(bytes, offset, minOf(1, length))
            override fun close() { closed = true; super.close() }
        }
        val snapshot = SwitchReadSnapshot.readFrom(SwitchGameFamily.LEGENDS_ZA, input)
        assertTrue(closed)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), snapshot.bytesCopy())
    }

    @Test fun emptyAndOversizeStreamsRejectAndCloseWithoutReadingBeyondLimitPlusOne() {
        for (size in listOf(0, SwitchReadSnapshot.MAX_SAVE_BYTES + 100)) {
            var position = 0
            var closed = false
            val input = object : InputStream() {
                override fun read(): Int = if (position < size) { position++; 7 } else -1
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                    if (position >= size) return -1
                    val count = minOf(length, size - position)
                    bytes.fill(7, offset, offset + count); position += count
                    return count
                }
                override fun close() { closed = true }
            }
            assertThrows(IllegalArgumentException::class.java) {
                SwitchReadSnapshot.readFrom(SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, input)
            }
            assertTrue(closed)
            assertTrue(position <= SwitchReadSnapshot.MAX_SAVE_BYTES + 1)
        }
    }

    @Test fun exactIntakeLimitIsAcceptedAsUnverified() {
        val source = ByteArray(SwitchReadSnapshot.MAX_SAVE_BYTES) { 3 }
        val result = SwitchReadSnapshot.readFrom(SwitchGameFamily.SCARLET_VIOLET, ByteArrayInputStream(source))
        assertEquals(source.size, result.byteCount)
        assertArrayEquals(source, result.bytesCopy())
        assertFalse(SwitchReadSession(result).isFormatVerified)
    }

    @Test fun zeroLengthReadsMakeProgressAndIoFailureClosesStream() {
        var zeroOnce = true
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2)) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (zeroOnce) { zeroOnce = false; return 0 }
                return super.read(bytes, offset, length)
            }
        }
        assertArrayEquals(byteArrayOf(1, 2), SwitchReadSnapshot.readFrom(SwitchGameFamily.SWORD_SHIELD, input).bytesCopy())
        var closed = false
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("unreadable")
            override fun close() { closed = true }
        }
        assertThrows(IOException::class.java) {
            SwitchReadSnapshot.readFrom(SwitchGameFamily.SWORD_SHIELD, broken)
        }
        assertTrue(closed)
    }

    @Test fun directCaptureRejectsEmptyAndOversizeBytes() {
        for (bytes in listOf(ByteArray(0), ByteArray(SwitchReadSnapshot.MAX_SAVE_BYTES + 1))) {
            assertThrows(IllegalArgumentException::class.java) {
                SwitchReadSnapshot.capture(SwitchGameFamily.SWORD_SHIELD, bytes)
            }
        }
    }
}

