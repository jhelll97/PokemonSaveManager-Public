package com.example.pokemonsavemanager

import org.junit.Assert.*
import org.junit.Test

class Gen3GameDetailsCatalogTest {
    @Test fun rubyAndSapphireHaveExactlyTheirEightTools() {
        val expected = listOf(Gen3GameDetailsAction.POKEBLOCKS, Gen3GameDetailsAction.COINS,
            Gen3GameDetailsAction.SOOT_ASH, Gen3GameDetailsAction.OPTIONS, Gen3GameDetailsAction.SELECT_SHORTCUT, Gen3GameDetailsAction.SAVED_LOCATION, Gen3GameDetailsAction.RECORDED_ACTIVITY, Gen3GameDetailsAction.HALL_OF_FAME)
        for (title in listOf("Pokémon Ruby", "Pokémon Sapphire")) {
            assertEquals(expected, Gen3GameDetailsCatalog.actionsForTitle(title))
        }
    }

    @Test fun emeraldHasEveryApplicableToolInStableDisplayOrder() {
        assertEquals(listOf(Gen3GameDetailsAction.POKEBLOCKS, Gen3GameDetailsAction.COINS,
            Gen3GameDetailsAction.BATTLE_POINTS, Gen3GameDetailsAction.BERRY_POWDER,
            Gen3GameDetailsAction.SOOT_ASH, Gen3GameDetailsAction.OPTIONS,
            Gen3GameDetailsAction.SELECT_SHORTCUT, Gen3GameDetailsAction.SAVED_LOCATION, Gen3GameDetailsAction.RECORDED_ACTIVITY, Gen3GameDetailsAction.HALL_OF_FAME), Gen3GameDetailsCatalog.actionsForTitle("Pokémon Emerald"))
    }

    @Test fun kantoRemakesExcludeHoennOnlyFeatures() {
        val expected = listOf(Gen3GameDetailsAction.COINS, Gen3GameDetailsAction.BERRY_POWDER,
            Gen3GameDetailsAction.OPTIONS, Gen3GameDetailsAction.SELECT_SHORTCUT, Gen3GameDetailsAction.SAVED_LOCATION, Gen3GameDetailsAction.RECORDED_ACTIVITY, Gen3GameDetailsAction.HALL_OF_FAME)
        for (title in listOf("Pokémon FireRed", "Pokémon LeafGreen")) {
            assertEquals(expected, Gen3GameDetailsCatalog.actionsForTitle(title))
        }
    }

    @Test fun unknownAmbiguousOrWrongGenerationTitlesDoNotExposeAnEditor() {
        for (title in listOf("", "Pokémon Gen 3 (Ruby/Sapphire/Emerald/FireRed/LeafGreen)",
            "Pokémon Ruby / Sapphire", "Pokémon FireRed / LeafGreen", "Pokémon Crystal",
            "Pokémon Let's Go Pikachu", "Pokémon Emerald ", "emerald")) {
            assertTrue(Gen3GameDetailsCatalog.actionsForTitle(title).isEmpty())
        }
    }

    @Test fun hallOfFameHasASeparateReadOnlyEntryForEverySupportedTitle() {
        assertEquals("Hall of Fame • Read-only", Gen3GameDetailsAction.HALL_OF_FAME.label)
        for (title in listOf("Pokémon Ruby", "Pokémon Sapphire", "Pokémon Emerald", "Pokémon FireRed", "Pokémon LeafGreen")) {
            val actions = Gen3GameDetailsCatalog.actionsForTitle(title)
            assertEquals(1, actions.count { it == Gen3GameDetailsAction.HALL_OF_FAME })
            assertTrue(actions.indexOf(Gen3GameDetailsAction.HALL_OF_FAME) > actions.indexOf(Gen3GameDetailsAction.RECORDED_ACTIVITY))
        }
    }

    @Test fun returnedMenusDoNotShareMutableState() {
        for (title in listOf("Pokémon Ruby", "Pokémon Sapphire", "Pokémon Emerald", "Pokémon FireRed", "Pokémon LeafGreen")) {
            val before = Gen3GameDetailsCatalog.actionsForTitle(title)
            val another = Gen3GameDetailsCatalog.actionsForTitle(title) as MutableList<Gen3GameDetailsAction>
            another.clear()
            assertEquals(before, Gen3GameDetailsCatalog.actionsForTitle(title))
            assertEquals(before.size, before.distinct().size)
            assertTrue(before.all { it.label.isNotBlank() })
        }
    }
}
