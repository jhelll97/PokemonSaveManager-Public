package com.example.pokemonsavemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6VersionExclusiveSelectionStateTest {
    private fun choice(
        id: String,
        destination: Gen6ConverterGame,
        species: Int
    ) = Gen6VersionExclusiveChoice(
        id,
        destination,
        species,
        "fixture:$id",
        gen6VersionExclusiveRecordFingerprint(
            byteArrayOf(
                (species and 0xFF).toByte(),
                (species ushr 8).toByte(),
                id.length.toByte()
            )
        )
    )

    private fun preview(
        choices: List<Gen6VersionExclusiveChoice>,
        selectedIds: List<String> = emptyList(),
        includeVersionExclusives: Boolean = selectedIds.isNotEmpty(),
        unavailableReason: String? = null
    ) = Gen6ConvertSavePreview(
        request = Gen6ConverterRequest(
            source = Gen6ConverterGame.OMEGA_RUBY,
            destination = Gen6ConverterGame.ALPHA_SAPPHIRE,
            includeVersionExclusives = includeVersionExclusives,
            selectedVersionExclusiveIds = selectedIds
        ),
        sourceFingerprint = gen6ConversionSourceFingerprint(byteArrayOf()),
        sourceTitle = "Pokémon Omega Ruby",
        destinationTitle = "Pokémon Alpha Sapphire",
        suggestedFilename = "Fixture_Pokemon_Alpha_Sapphire_Converted.sav",
        preserved = emptyList(),
        omitted = emptyList(),
        availableVersionExclusives = choices,
        compatibilityNotice = "Automated validation only.",
        versionExclusiveUnavailableReason = unavailableReason
    )

    @Test
    fun previewRejectsContradictoryPublicChoiceState() {
        val lotad = choice(
            "lotad-as",
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            270
        )
        val duplicateSpecies = choice(
            "sableye-as",
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            270
        )
        val wrongDestination = choice(
            "seedot-or",
            Gen6ConverterGame.OMEGA_RUBY,
            273
        )

        val duplicateIds = assertThrows(IllegalArgumentException::class.java) {
            preview(listOf(lotad, lotad))
        }
        val duplicateSpeciesFailure =
            assertThrows(IllegalArgumentException::class.java) {
                preview(listOf(lotad, duplicateSpecies))
            }
        val wrongDestinationFailure =
            assertThrows(IllegalArgumentException::class.java) {
                preview(listOf(wrongDestination))
            }
        val staleSelection = assertThrows(IllegalArgumentException::class.java) {
            preview(listOf(lotad), selectedIds = listOf("missing"))
        }
        val contradictoryOptIn =
            assertThrows(IllegalArgumentException::class.java) {
                preview(
                    choices = listOf(lotad),
                    includeVersionExclusives = true
                )
            }

        assertTrue(duplicateIds.message!!.contains("unique ids"))
        assertTrue(duplicateSpeciesFailure.message!!.contains("unique species"))
        assertTrue(wrongDestinationFailure.message!!.contains("destination"))
        assertTrue(staleSelection.message!!.contains("available choices"))
        assertTrue(contradictoryOptIn.message!!.contains("opt-in"))
    }

    @Test
    fun selectionStateRejectsRecordIdentityChangedAfterPreviewReview() {
        val lotad = choice(
            "lotad-as",
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            270
        )
        val sableye = choice(
            "sableye-as",
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            302
        )
        val backingChoices = mutableListOf(lotad, sableye)
        val reviewedPreview = preview(backingChoices)

        backingChoices[1] = sableye.copy(
            recordFingerprint = lotad.recordFingerprint
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveSelectionState.from(reviewedPreview)
        }

        assertTrue(failure.message!!.contains("unique exact PK6 records"))
        assertEquals(listOf(lotad, backingChoices[1]), backingChoices)
    }

    @Test
    fun selectionStateSupportsEveryUniqueGen6Species() {
        val choices = (1..721).map { species ->
            choice("choice-$species", Gen6ConverterGame.ALPHA_SAPPHIRE, species)
        }
        val state = Gen6VersionExclusiveSelectionState.from(
            preview(choices, selectedIds = choices.map { it.id })
        )

        assertEquals(721, state.selectedIds.size)
        assertEquals(choices, state.selectedChoices)
    }

    @Test
    fun selectedChoicesFollowUserOrderAndTogglesStayUnique() {
        val lotad = choice(
            "lotad-as",
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            270
        )
        val sableye = choice(
            "sableye-as",
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            302
        )
        val original = Gen6VersionExclusiveSelectionState.from(
            preview(listOf(lotad, sableye), listOf("sableye-as", "lotad-as"))
        )

        assertEquals(listOf(sableye, lotad), original.selectedChoices)

        val updated = original
            .withSelection("sableye-as", selected = false)
            .withSelection("sableye-as", selected = true)
            .withSelection("sableye-as", selected = true)

        assertEquals(listOf("lotad-as", "sableye-as"), updated.selectedIds)
        assertEquals(listOf(lotad, sableye), updated.selectedChoices)
        assertEquals(listOf("sableye-as", "lotad-as"), original.selectedIds)
    }

    @Test
    fun returnedListsCannotMutateSelectionState() {
        val lotad = choice(
            "lotad-as",
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            270
        )
        val sableye = choice(
            "sableye-as",
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            302
        )
        val state = Gen6VersionExclusiveSelectionState.from(
            preview(listOf(lotad, sableye), listOf("lotad-as", "sableye-as"))
        )

        (state.selectedIds as MutableList<String>).clear()
        (state.availableChoices as MutableList<Gen6VersionExclusiveChoice>).clear()

        assertEquals(listOf("lotad-as", "sableye-as"), state.selectedIds)
        assertEquals(listOf(lotad, sableye), state.availableChoices)
    }

    @Test
    fun previewRejectsUnsafeCatalogUnavailableReasons() {
        val unsafeReasons = listOf(
            "unsafe\nreason",
            "unsafe\u202Ereason",
            " padded reason ",
            "x".repeat(513)
        )

        for (reason in unsafeReasons) {
            val failure = assertThrows(IllegalArgumentException::class.java) {
                preview(
                    choices = emptyList(),
                    unavailableReason = reason
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "unavailability reason requires no available choices"
                )
            )
        }
    }

    @Test
    fun unavailableEmptyAndInconsistentPreviewsCannotCreateSelectionState() {
        val unavailable = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveSelectionState.from(
                preview(
                    choices = emptyList(),
                    unavailableReason =
                        "No audited destination-exclusive records are installed."
                )
            )
        }
        val empty = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveSelectionState.from(preview(emptyList()))
        }
        val inconsistent = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveSelectionState.from(
                preview(
                    choices = listOf(
                        choice(
                            "lotad-as",
                            Gen6ConverterGame.ALPHA_SAPPHIRE,
                            270
                        )
                    ),
                    includeVersionExclusives = true
                )
            )
        }

        assertTrue(unavailable.message!!.contains("available verified catalog"))
        assertTrue(empty.message!!.contains("at least one verified catalog choice"))
        assertTrue(inconsistent.message!!.contains("opt-in"))
    }

    @Test
    fun unapprovedDuplicateAndWrongDestinationChoicesFailClosed() {
        val lotad = choice(
            "lotad-as",
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            270
        )
        assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveSelectionState.from(
                preview(listOf(lotad), listOf("missing"))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveSelectionState.from(
                preview(listOf(lotad, lotad))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveSelectionState.from(
                preview(
                    listOf(
                        choice(
                            "seedot-or",
                            Gen6ConverterGame.OMEGA_RUBY,
                            273
                        )
                    )
                )
            )
        }
    }
}
