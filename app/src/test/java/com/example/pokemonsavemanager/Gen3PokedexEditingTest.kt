package com.example.pokemonsavemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3PokedexEditingTest {
    @Test
    fun restoreLoadedReturnsIndependentSnapshot() {
        val loaded = BooleanArray(386)
        loaded[0] = true
        loaded[385] = true

        val restored = Gen3PokedexEditing.restoreLoaded(loaded)
        restored[0] = false

        assertTrue(loaded[0])
        assertFalse(restored[0])
        assertTrue(restored[385])
    }

    @Test
    fun restoreLoadedAtIndicesOnlyRestoresRequestedFlags() {
        val loaded = BooleanArray(386)
        loaded[1] = true
        val staged = loaded.copyOf()
        staged[0] = true
        staged[1] = false
        staged[2] = true

        val restored = Gen3PokedexEditing.restoreLoadedAtIndices(loaded, staged, listOf(0, 1))

        assertFalse(restored[0])
        assertTrue(restored[1])
        assertTrue(restored[2])
        assertTrue(staged[0])
        assertFalse(staged[1])
    }

    @Test(expected = IllegalArgumentException::class)
    fun restoreLoadedAtIndicesRejectsOutOfRangeIndex() {
        Gen3PokedexEditing.restoreLoadedAtIndices(
            BooleanArray(386),
            BooleanArray(386),
            listOf(-1)
        )
    }

    @Test
    fun summarizeChangesSeparatesRegistrationsAndClears() {
        val loaded = BooleanArray(386)
        loaded[1] = true
        loaded[2] = true
        val staged = loaded.copyOf()
        staged[0] = true
        staged[1] = false
        staged[100] = true

        val summary = Gen3PokedexEditing.summarizeChanges(loaded, staged)

        assertEquals(2, summary.registered)
        assertEquals(1, summary.cleared)
        assertEquals(3, summary.total)
    }

    @Test
    fun changeDirectionDistinguishesRegistrationClearAndUnchanged() {
        val loaded = BooleanArray(386)
        loaded[1] = true
        val staged = loaded.copyOf()
        staged[0] = true
        staged[1] = false

        assertEquals(
            Gen3PokedexChangeDirection.REGISTER,
            Gen3PokedexEditing.changeDirection(loaded, staged, 0)
        )
        assertEquals(
            Gen3PokedexChangeDirection.CLEAR,
            Gen3PokedexEditing.changeDirection(loaded, staged, 1)
        )
        assertEquals(null, Gen3PokedexEditing.changeDirection(loaded, staged, 2))
    }

    @Test
    fun listChangesReturnsOrderedDexNumbersAndTargetState() {
        val loaded = BooleanArray(386)
        loaded[24] = true
        val staged = loaded.copyOf()
        staged[0] = true
        staged[24] = false
        staged[385] = true

        assertEquals(
            listOf(
                Gen3PokedexChange(1, true),
                Gen3PokedexChange(25, false),
                Gen3PokedexChange(386, true)
            ),
            Gen3PokedexEditing.listChanges(loaded, staged)
        )
    }

    @Test
    fun readyToSaveRequiresAChangeAndReviewAcknowledgement() {
        val loaded = BooleanArray(386)
        val staged = loaded.copyOf()
        staged[42] = true

        assertFalse(Gen3PokedexEditing.readyToSave(loaded, staged, false))
        assertTrue(Gen3PokedexEditing.readyToSave(loaded, staged, true))
        assertFalse(Gen3PokedexEditing.readyToSave(loaded, loaded.copyOf(), true))
    }

    @Test
    fun hasChangedReportsOnlyTheRequestedFlag() {
        val loaded = BooleanArray(386)
        val staged = loaded.copyOf()
        staged[151] = true

        assertFalse(Gen3PokedexEditing.hasChanged(loaded, staged, 0))
        assertTrue(Gen3PokedexEditing.hasChanged(loaded, staged, 151))
    }

    @Test(expected = IllegalArgumentException::class)
    fun hasChangedRejectsOutOfRangeIndex() {
        Gen3PokedexEditing.hasChanged(BooleanArray(386), BooleanArray(386), 386)
    }

    @Test
    fun changedCountReportsOnlyDifferentFlags() {
        val loaded = BooleanArray(386)
        val staged = loaded.copyOf()
        staged[0] = true
        staged[151] = true

        assertEquals(2, Gen3PokedexEditing.changedCount(loaded, staged))
        assertEquals(0, Gen3PokedexEditing.changedCount(loaded, loaded.copyOf()))
    }
    @Test
    fun invertAtIndicesTogglesOnlyUniqueRequestedFlags() {
        val staged = BooleanArray(386)
        staged[1] = true
        staged[3] = true

        val inverted = Gen3PokedexEditing.invertAtIndices(
            staged,
            listOf(0, 1, 1, 385)
        )

        assertTrue(inverted[0])
        assertFalse(inverted[1])
        assertTrue(inverted[3])
        assertTrue(inverted[385])
        assertFalse(staged[0])
        assertTrue(staged[1])
        assertFalse(staged[385])
    }

    @Test(expected = IllegalArgumentException::class)
    fun invertAtIndicesRejectsOutOfRangeIndex() {
        Gen3PokedexEditing.invertAtIndices(BooleanArray(386), listOf(386))
    }


    @Test
    fun restoreChangesByDirectionOnlyDiscardsRequestedChangeType() {
        val loaded = BooleanArray(386)
        loaded[1] = true
        loaded[3] = true
        val staged = loaded.copyOf()
        staged[0] = true
        staged[1] = false
        staged[2] = true
        staged[3] = false

        val registrationsRestored = Gen3PokedexEditing.restoreChangesByDirection(
            loaded,
            staged,
            Gen3PokedexChangeDirection.REGISTER
        )
        assertFalse(registrationsRestored[0])
        assertFalse(registrationsRestored[1])
        assertFalse(registrationsRestored[2])
        assertFalse(registrationsRestored[3])

        val clearsRestored = Gen3PokedexEditing.restoreChangesByDirection(
            loaded,
            staged,
            Gen3PokedexChangeDirection.CLEAR
        )
        assertTrue(clearsRestored[0])
        assertTrue(clearsRestored[1])
        assertTrue(clearsRestored[2])
        assertTrue(clearsRestored[3])

        assertTrue(staged[0])
        assertFalse(staged[1])
        assertTrue(staged[2])
        assertFalse(staged[3])
    }


    @Test
    fun undoActionOnlyRestoresAnUnchangedPostActionSnapshot() {
        val previous = BooleanArray(386)
        previous[10] = true
        val expectedCurrent = previous.copyOf()
        expectedCurrent[20] = true

        val restored = Gen3PokedexEditing.undoActionIfCurrent(
            expectedCurrent.copyOf(),
            previous,
            expectedCurrent
        )
        assertEquals(previous.toList(), restored?.toList())

        val staleCurrent = expectedCurrent.copyOf()
        staleCurrent[30] = true
        assertEquals(
            null,
            Gen3PokedexEditing.undoActionIfCurrent(
                staleCurrent,
                previous,
                expectedCurrent
            )
        )

        assertTrue(previous[10])
        assertFalse(previous[20])
        assertTrue(expectedCurrent[20])
    }


    @Test
    fun redoActionOnlyRestoresAnUnchangedPreviousSnapshot() {
        val previous = BooleanArray(386)
        previous[12] = true
        val next = previous.copyOf()
        next[22] = true

        val restored = Gen3PokedexEditing.redoActionIfPrevious(
            previous.copyOf(),
            previous,
            next
        )
        assertEquals(next.toList(), restored?.toList())

        val staleCurrent = previous.copyOf()
        staleCurrent[32] = true
        assertEquals(
            null,
            Gen3PokedexEditing.redoActionIfPrevious(
                staleCurrent,
                previous,
                next
            )
        )

        assertTrue(previous[12])
        assertFalse(previous[22])
        assertTrue(next[22])
    }


    @Test
    fun generationFilterUsesNationalDexBoundaries() {
        assertTrue(
            Gen3PokedexEditing.matchesGeneration(
                1,
                Gen3PokedexGenerationFilter.GEN1
            )
        )
        assertTrue(
            Gen3PokedexEditing.matchesGeneration(
                151,
                Gen3PokedexGenerationFilter.GEN1
            )
        )
        assertTrue(
            Gen3PokedexEditing.matchesGeneration(
                152,
                Gen3PokedexGenerationFilter.GEN2
            )
        )
        assertTrue(
            Gen3PokedexEditing.matchesGeneration(
                251,
                Gen3PokedexGenerationFilter.GEN2
            )
        )
        assertTrue(
            Gen3PokedexEditing.matchesGeneration(
                252,
                Gen3PokedexGenerationFilter.GEN3
            )
        )
        assertTrue(
            Gen3PokedexEditing.matchesGeneration(
                386,
                Gen3PokedexGenerationFilter.GEN3
            )
        )
        assertFalse(
            Gen3PokedexEditing.matchesGeneration(
                151,
                Gen3PokedexGenerationFilter.GEN2
            )
        )
        assertTrue(
            Gen3PokedexEditing.matchesGeneration(
                386,
                Gen3PokedexGenerationFilter.ALL
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun generationFilterRejectsOutOfRangeDexNumber() {
        Gen3PokedexEditing.matchesGeneration(
            0,
            Gen3PokedexGenerationFilter.ALL
        )
    }



    @Test
    fun speciesNameUsesCompleteNationalDexCatalogBoundaries() {
        val names = (1..386).map { "Species $it" }

        assertEquals("Species 1", Gen3PokedexEditing.speciesName(1, names))
        assertEquals("Species 252", Gen3PokedexEditing.speciesName(252, names))
        assertEquals("Species 386", Gen3PokedexEditing.speciesName(386, names))
    }

    @Test(expected = IllegalArgumentException::class)
    fun speciesNameRejectsIncompleteCatalog() {
        Gen3PokedexEditing.speciesName(252, List(251) { "Species" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun speciesNameRejectsOutOfRangeDexNumber() {
        Gen3PokedexEditing.speciesName(387, List(386) { "Species" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun speciesNameRejectsBlankCatalogEntry() {
        val names = MutableList(386) { "Species" }
        names[251] = " "
        Gen3PokedexEditing.speciesName(252, names)
    }


    @Test
    fun orderedDexNumbersSupportsNationalAndAlphabeticalOrder() {
        val names = MutableList(386) { index ->
            "Name " + (index + 1).toString().padStart(3, '0')
        }
        names[0] = "Zulu"
        names[1] = "Alpha"
        names[2] = "Alpha"

        assertEquals(
            listOf(1, 2, 3),
            Gen3PokedexEditing.orderedDexNumbers(
                names,
                Gen3PokedexSortOrder.NATIONAL_DEX
            ).take(3)
        )
        val alphabetical = Gen3PokedexEditing.orderedDexNumbers(
            names,
            Gen3PokedexSortOrder.SPECIES_NAME
        )
        assertEquals(listOf(2, 3, 4), alphabetical.take(3))
        assertEquals(1, alphabetical.last())
    }

    @Test(expected = IllegalArgumentException::class)
    fun orderedDexNumbersRejectsBlankCatalogEntries() {
        val names = MutableList(386) { "Species" }
        names[385] = ""
        Gen3PokedexEditing.orderedDexNumbers(
            names,
            Gen3PokedexSortOrder.SPECIES_NAME
        )
    }


    @Test
    fun registrationStatusDistinguishesLoadedAndPendingStates() {
        val loaded = BooleanArray(386)
        loaded[1] = true
        val staged = loaded.copyOf()

        assertEquals(
            Gen3PokedexRegistrationStatus.UNREGISTERED,
            Gen3PokedexEditing.registrationStatus(loaded, staged, 0)
        )
        assertEquals(
            Gen3PokedexRegistrationStatus.REGISTERED,
            Gen3PokedexEditing.registrationStatus(loaded, staged, 1)
        )

        staged[0] = true
        staged[1] = false
        assertEquals(
            Gen3PokedexRegistrationStatus.PENDING_REGISTER,
            Gen3PokedexEditing.registrationStatus(loaded, staged, 0)
        )
        assertEquals(
            Gen3PokedexRegistrationStatus.PENDING_CLEAR,
            Gen3PokedexEditing.registrationStatus(loaded, staged, 1)
        )
    }

}
