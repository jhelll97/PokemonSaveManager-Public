package com.example.pokemonsavemanager

import org.junit.Assert.assertEquals
import org.junit.Test

class SaveConverterGen2IdentityTest {
    @Test
    fun gen2TitlesAreRepresentedAsGen2Games() {
        assertEquals(
            listOf(
                SaveConverterGame.GOLD,
                SaveConverterGame.SILVER,
                SaveConverterGame.CRYSTAL
            ),
            SaveConverterGame.entries.filter { it.generation == 2 }
        )
    }
}
