package com.example.pokemonsavemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PseBankTransferRoutesTest {
    @Test
    fun gen2IncludesForwardAndBackwardRoutes() {
        val forward = PseBankTransferRoutes.forSource(SaveConverterGame.GOLD)
            .filter { it.direction == PseBankTransferDirection.FORWARD }
        val backward = PseBankTransferRoutes.forSource(SaveConverterGame.GOLD)
            .filter { it.direction == PseBankTransferDirection.BACKWARD }

        assertTrue(forward.isNotEmpty())
        assertEquals(3, backward.size)
        assertTrue(PseBankTransferRoutes.contains(SaveConverterGame.GOLD, SaveConverterGame.RED))
        assertTrue(PseBankTransferRoutes.contains(SaveConverterGame.GOLD, SaveConverterGame.X))
    }

    @Test
    fun everyRouteDirectionMatchesItsGenerationOrder() {
        SaveConverterGame.entries.forEach { source ->
            PseBankTransferRoutes.forSource(source).forEach { route ->
                assertFalse(route.source.generation == route.destination.generation)
                val expected = if (route.destination.generation > route.source.generation) {
                    PseBankTransferDirection.FORWARD
                } else {
                    PseBankTransferDirection.BACKWARD
                }
                assertEquals(expected, route.direction)
            }
        }
    }

    @Test
    fun everyGen2TitleHasTheSameCrossGenerationRouteShape() {
        val gen2Titles = listOf(
            SaveConverterGame.GOLD,
            SaveConverterGame.SILVER,
            SaveConverterGame.CRYSTAL
        )
        val routeCounts = gen2Titles.map { PseBankTransferRoutes.forSource(it).size }
        assertEquals(1, routeCounts.distinct().size)
        assertEquals(25, routeCounts.first())
    }

    @Test
    fun everyGen2TitleHasTheSameDestinationTitleSet() {
        val gen2Titles = listOf(
            SaveConverterGame.GOLD,
            SaveConverterGame.SILVER,
            SaveConverterGame.CRYSTAL
        )
        val destinationSets = gen2Titles.map { source ->
            PseBankTransferRoutes.forSource(source)
                .map { it.destination }
                .toSet()
        }
        assertEquals(1, destinationSets.distinct().size)
    }

    @Test
    fun directionalSelectorsMatchFilteredRouteInventory() {
        SaveConverterGame.entries.forEach { source ->
            assertEquals(
                PseBankTransferRoutes.forSource(source)
                    .filter { it.direction == PseBankTransferDirection.FORWARD },
                PseBankTransferRoutes.forwardFrom(source)
            )
            assertEquals(
                PseBankTransferRoutes.forSource(source)
                    .filter { it.direction == PseBankTransferDirection.BACKWARD },
                PseBankTransferRoutes.backwardFrom(source)
            )
        }
    }

    @Test
    fun gen2DirectionalSelectorsExposeExpectedRouteCounts() {
        val gen2Titles = listOf(
            SaveConverterGame.GOLD,
            SaveConverterGame.SILVER,
            SaveConverterGame.CRYSTAL
        )

        gen2Titles.forEach { source ->
            assertEquals(22, PseBankTransferRoutes.forwardFrom(source).size)
            assertEquals(3, PseBankTransferRoutes.backwardFrom(source).size)
        }
    }

    @Test
    fun gen2DirectionalSelectorsExposeExpectedGenerationBoundaries() {
        val gen2Titles = listOf(
            SaveConverterGame.GOLD,
            SaveConverterGame.SILVER,
            SaveConverterGame.CRYSTAL
        )
        val expectedBackward = setOf(
            SaveConverterGame.RED,
            SaveConverterGame.BLUE,
            SaveConverterGame.YELLOW
        )
        val expectedForward = SaveConverterGame.entries
            .filter { it.generation >= 3 }
            .toSet()

        gen2Titles.forEach { source ->
            assertEquals(
                expectedBackward,
                PseBankTransferRoutes.backwardFrom(source)
                    .map { it.destination }
                    .toSet()
            )
            assertEquals(
                expectedForward,
                PseBankTransferRoutes.forwardFrom(source)
                    .map { it.destination }
                    .toSet()
            )
        }
    }

    @Test
    fun gen2DirectionalSelectorsHaveUniqueDestinations() {
        val gen2Titles = listOf(
            SaveConverterGame.GOLD,
            SaveConverterGame.SILVER,
            SaveConverterGame.CRYSTAL
        )

        gen2Titles.forEach { source ->
            val forwardDestinations = PseBankTransferRoutes.forwardFrom(source)
                .map { it.destination }
            val backwardDestinations = PseBankTransferRoutes.backwardFrom(source)
                .map { it.destination }

            assertEquals(forwardDestinations.size, forwardDestinations.toSet().size)
            assertEquals(backwardDestinations.size, backwardDestinations.toSet().size)
        }
    }

    @Test
    fun gen2DirectionalSelectorsPartitionAllCrossGenerationRoutes() {
        val gen2Titles = listOf(
            SaveConverterGame.GOLD,
            SaveConverterGame.SILVER,
            SaveConverterGame.CRYSTAL
        )

        gen2Titles.forEach { source ->
            val allRoutes = PseBankTransferRoutes.forSource(source).toSet()
            val selectedRoutes = (
                PseBankTransferRoutes.forwardFrom(source) +
                    PseBankTransferRoutes.backwardFrom(source)
                ).toSet()

            assertEquals(allRoutes, selectedRoutes)
            assertEquals(allRoutes.size, selectedRoutes.size)
        }
    }

    @Test
    fun exactLookupReturnsRouteAndRejectsSameGenerationPair() {
        val route = PseBankTransferRoutes.find(
            SaveConverterGame.GOLD,
            SaveConverterGame.ALPHA_SAPPHIRE
        )

        assertEquals(SaveConverterGame.GOLD, route?.source)
        assertEquals(SaveConverterGame.ALPHA_SAPPHIRE, route?.destination)
        assertEquals(PseBankTransferDirection.FORWARD, route?.direction)
        assertEquals(null, PseBankTransferRoutes.find(
            SaveConverterGame.GOLD,
            SaveConverterGame.SILVER
        ))
    }

    @Test
    fun everyGen2TitleFindsEachExpectedCrossGenerationRoute() {
        val gen2Titles = listOf(
            SaveConverterGame.GOLD,
            SaveConverterGame.SILVER,
            SaveConverterGame.CRYSTAL
        )

        gen2Titles.forEach { source ->
            PseBankTransferRoutes.forSource(source).forEach { expected ->
                val actual = PseBankTransferRoutes.find(source, expected.destination)
                assertEquals(expected, actual)
            }
        }
    }

    @Test
    fun exactLookupRejectsEverySameGenerationGen2Pair() {
        val gen2Titles = listOf(
            SaveConverterGame.GOLD,
            SaveConverterGame.SILVER,
            SaveConverterGame.CRYSTAL
        )

        gen2Titles.forEach { source ->
            gen2Titles.forEach { destination ->
                assertEquals(null, PseBankTransferRoutes.find(source, destination))
            }
        }
    }

    @Test
    fun directionalExactLookupMatchesEveryGen2RouteAndRejectsOppositeDirections() {
        val gen2Titles = listOf(
            SaveConverterGame.GOLD,
            SaveConverterGame.SILVER,
            SaveConverterGame.CRYSTAL
        )

        gen2Titles.forEach { source ->
            PseBankTransferRoutes.forwardFrom(source).forEach { expected ->
                assertEquals(
                    expected,
                    PseBankTransferRoutes.findForward(source, expected.destination)
                )
                assertEquals(
                    null,
                    PseBankTransferRoutes.findBackward(source, expected.destination)
                )
            }
            PseBankTransferRoutes.backwardFrom(source).forEach { expected ->
                assertEquals(
                    expected,
                    PseBankTransferRoutes.findBackward(source, expected.destination)
                )
                assertEquals(
                    null,
                    PseBankTransferRoutes.findForward(source, expected.destination)
                )
            }
        }
    }
}
