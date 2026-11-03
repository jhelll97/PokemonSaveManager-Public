package com.example.pokemonsavemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6SaveConverterPlannerTest {
    private val routes = listOf(
        Gen6ConverterGame.OMEGA_RUBY to
            Gen6ConverterGame.ALPHA_SAPPHIRE,
        Gen6ConverterGame.ALPHA_SAPPHIRE to
            Gen6ConverterGame.OMEGA_RUBY,
        Gen6ConverterGame.X to Gen6ConverterGame.Y,
        Gen6ConverterGame.Y to Gen6ConverterGame.X
    )

    @Test
    fun mandatoryRoutesRemainExportableWithoutUnselectedExtras() {
        routes.forEach { (source, destination) ->
            val plan = Gen6SaveConverterPlanner.plan(
                Gen6ConverterRequest(source, destination)
            )
            assertTrue(plan.canExport)
            assertTrue(plan.blockers.isEmpty())
        }
    }

    @Test
    fun sameGameRouteReportsOnlyTheSameGameBlocker() {
        val plan = Gen6SaveConverterPlanner.plan(
            Gen6ConverterRequest(
                source = Gen6ConverterGame.X,
                destination = Gen6ConverterGame.X
            )
        )

        assertFalse(plan.canExport)
        assertEquals(listOf("Source and destination games must differ."), plan.blockers)
        assertThrows(IllegalArgumentException::class.java) {
            plan.requireExportable()
        }
    }

    @Test
    fun crossFamilyRoutesRequireSeparateCompatibilityEvidence() {
        val crossFamilyRoutes = listOf(
            Gen6ConverterGame.OMEGA_RUBY to Gen6ConverterGame.X,
            Gen6ConverterGame.OMEGA_RUBY to Gen6ConverterGame.Y,
            Gen6ConverterGame.ALPHA_SAPPHIRE to Gen6ConverterGame.X,
            Gen6ConverterGame.ALPHA_SAPPHIRE to Gen6ConverterGame.Y,
            Gen6ConverterGame.X to Gen6ConverterGame.OMEGA_RUBY,
            Gen6ConverterGame.X to Gen6ConverterGame.ALPHA_SAPPHIRE,
            Gen6ConverterGame.Y to Gen6ConverterGame.OMEGA_RUBY,
            Gen6ConverterGame.Y to Gen6ConverterGame.ALPHA_SAPPHIRE
        )

        crossFamilyRoutes.forEach { (source, destination) ->
            val plan = Gen6SaveConverterPlanner.plan(
                Gen6ConverterRequest(source, destination)
            )

            assertFalse(plan.canExport)
            assertTrue(
                plan.blockers.any {
                    it.contains("cross-family conversion") &&
                        it.contains("story-feature compatibility") &&
                        it.contains("destination initialization evidence")
                }
            )
            assertThrows(IllegalArgumentException::class.java) {
                plan.requireExportable()
            }
        }
    }

    @Test
    fun planSnapshotsMutableVersionExclusiveSelection() {
        val selectedIds = mutableListOf("lotad-as")
        val plan = Gen6SaveConverterPlanner.plan(
            Gen6ConverterRequest(
                source = Gen6ConverterGame.OMEGA_RUBY,
                destination = Gen6ConverterGame.ALPHA_SAPPHIRE,
                includeVersionExclusives = true,
                selectedVersionExclusiveIds = selectedIds
            )
        )

        selectedIds.clear()
        selectedIds.add("substituted-id")

        assertEquals(listOf("lotad-as"), plan.request.selectedVersionExclusiveIds)
        assertTrue(plan.canExport)
        assertTrue(plan.blockers.isEmpty())
    }

    @Test
    fun versionExclusiveOptInFailsClosedForEveryRoute() {
        routes.forEach { (source, destination) ->
            val request = Gen6ConverterRequest(
                source = source,
                destination = destination,
                includeVersionExclusives = true
            )
            val plan = Gen6SaveConverterPlanner.plan(request)

            assertFalse(plan.canExport)
            assertTrue(
                plan.blockers.any {
                    "verified catalog selection" in it &&
                        "legal PK6 injection" in it
                }
            )
            assertThrows(IllegalArgumentException::class.java) {
                plan.requireExportable()
            }
        }
    }

    @Test
    fun impossibleVersionExclusiveSelectionCountFailsBeforeExport() {
        val selectedIds = (1..931).map { "exclusive-$it" }
        val plan = Gen6SaveConverterPlanner.plan(
            Gen6ConverterRequest(
                source = Gen6ConverterGame.OMEGA_RUBY,
                destination = Gen6ConverterGame.ALPHA_SAPPHIRE,
                includeVersionExclusives = true,
                selectedVersionExclusiveIds = selectedIds
            )
        )

        assertFalse(plan.canExport)
        assertTrue(
            plan.blockers.any {
                it == "Destination-version-exclusive selections cannot exceed 930 Gen 6 PC slots."
            }
        )
        assertThrows(IllegalArgumentException::class.java) {
            plan.requireExportable()
        }
    }

    @Test
    fun unsafeVersionExclusiveSelectionIdsFailBeforeExport() {
        listOf(
            "Lotad-AS",
            "lotad_as",
            " lotad-as",
            "lotad-as ",
            "lotad--as",
            "../lotad-as"
        ).forEach { selectionId ->
            val plan = Gen6SaveConverterPlanner.plan(
                Gen6ConverterRequest(
                    source = Gen6ConverterGame.OMEGA_RUBY,
                    destination = Gen6ConverterGame.ALPHA_SAPPHIRE,
                    includeVersionExclusives = true,
                    selectedVersionExclusiveIds = listOf(selectionId)
                )
            )

            assertFalse(plan.canExport)
            assertTrue(
                plan.blockers.any {
                    it == "Destination-version-exclusive selection ids must be safe lowercase record ids."
                }
            )
            assertThrows(IllegalArgumentException::class.java) {
                plan.requireExportable()
            }
        }
    }
}
