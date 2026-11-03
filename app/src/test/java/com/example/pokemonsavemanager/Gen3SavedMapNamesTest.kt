package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class Gen3SavedMapNamesTest {
    @Test fun completePinnedMapOrdersMatchNativeFingerprints() {
        for ((title, expectedCount, expectedHash) in listOf(
            Triple("Pokémon Ruby", 394, "f70be33fdb2663cee1bbb774b6f0b3759c7a74de7689792449a4766d6f560818"),
            Triple("Pokémon Sapphire", 394, "f70be33fdb2663cee1bbb774b6f0b3759c7a74de7689792449a4766d6f560818"),
            Triple("Pokémon Emerald", 518, "d0bd42e3cb4b8a06e4cc1ceef23710df26a6de23940d41d3db74fb31cb28d64f"),
            Triple("Pokémon FireRed", 425, "d036dd1fcb0d73f56fc8e9d697a7a07b5bae8078cea1e5e77a6c7f387d1cc544"),
            Triple("Pokémon LeafGreen", 425, "d036dd1fcb0d73f56fc8e9d697a7a07b5bae8078cea1e5e77a6c7f387d1cc544"))) {
            val rows = mutableListOf<String>()
            for (group in 0..127) for (number in 0..127) {
                Gen3SavedMapNames.nameForTitle(title, group, number)?.let { rows.add("$group:$number:$it\n") }
            }
            assertEquals(expectedCount, rows.size)
            val digest = MessageDigest.getInstance("SHA-256").digest(rows.joinToString("").toByteArray(Charsets.UTF_8))
            assertEquals(expectedHash, digest.joinToString("") { "%02x".format(it.toInt() and 255) })
        }
    }

    @Test fun equalIdsUseExactTitleCatalogAndDoNotGuessAcrossFamilies() {
        assertEquals("PetalburgCity", Gen3SavedMapNames.nameForTitle("Pokémon Ruby", 0, 0))
        assertEquals("BattleColosseum_2P", Gen3SavedMapNames.nameForTitle("Pokémon FireRed", 0, 0))
        assertEquals("Underwater1", Gen3SavedMapNames.nameForTitle("Pokémon Sapphire", 0, 50))
        assertEquals("Underwater_Route124", Gen3SavedMapNames.nameForTitle("Pokémon Emerald", 0, 50))
        assertNull(Gen3SavedMapNames.nameForTitle("Pokémon LeafGreen", 0, 50))
        assertEquals("PalletTown", Gen3SavedMapNames.nameForTitle("Pokémon FireRed", 3, 0))
        assertEquals("SevenIsland_SevaultCanyon_House", Gen3SavedMapNames.nameForTitle("Pokémon LeafGreen", 42, 0))
        assertNull(Gen3SavedMapNames.nameForTitle("Pokémon Emerald", 42, 0))
    }

    @Test fun displaySpacingKeepsNativeIdentifierAndRecordSelectionIntact() {
        assertEquals("LittlerootTown_BrendansHouse_2F", Gen3SavedMapNames.nameForTitle("Pokémon Emerald", 1, 1))
        assertEquals("Littleroot Town / Brendans House / 2F", Gen3SavedMapNames.displayNameForTitle("Pokémon Emerald", 1, 1))
        assertEquals("Route101", Gen3SavedMapNames.nameForTitle("Pokémon Ruby", 0, 16))
        assertEquals("LittlerootTown_BrendansHouse_2F", Gen3SavedMapNames.nameForTitle("Pokémon Emerald", 1, 1))
    }

    @Test fun unknownOrAmbiguousTitlesNeverSelectADefaultCatalog() {
        for (title in listOf("", "Emerald", "Pokémon Emerald ", "Pokémon Ruby / Sapphire",
            "Pokémon Gen 3 (Ruby/Sapphire/Emerald/FireRed/LeafGreen)", "Pokémon Crystal", "Pokémon Let's Go Pikachu")) {
            assertNull(Gen3SavedMapNames.nameForTitle(title, 0, 0))
            assertNull(Gen3SavedMapNames.displayNameForTitle(title, 0, 0))
        }
    }

    @Test fun signedSentinelsOutOfBoundsAndHolesStayUnknownWithoutWrapping() {
        for (title in listOf("Pokémon Ruby", "Pokémon Sapphire", "Pokémon Emerald", "Pokémon FireRed", "Pokémon LeafGreen")) {
            for (invalid in listOf(Int.MIN_VALUE, -128, -1, 128, 255, Int.MAX_VALUE)) {
                assertNull(Gen3SavedMapNames.nameForTitle(title, invalid, 0))
                assertNull(Gen3SavedMapNames.nameForTitle(title, 0, invalid))
                assertNull(Gen3SavedMapNames.displayNameForTitle(title, invalid, invalid))
            }
            assertNull(Gen3SavedMapNames.nameForTitle(title, 127, 0))
            assertNull(Gen3SavedMapNames.nameForTitle(title, 0, 127))
        }
    }
}
