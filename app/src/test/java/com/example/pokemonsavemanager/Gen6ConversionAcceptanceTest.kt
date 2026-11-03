package com.example.pokemonsavemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6ConversionAcceptanceTest {
    private val destinationSaveSha256 = "0123456789abcdef".repeat(4)

    private val routes = listOf(
        Gen6ConverterGame.OMEGA_RUBY to
            Gen6ConverterGame.ALPHA_SAPPHIRE,
        Gen6ConverterGame.ALPHA_SAPPHIRE to
            Gen6ConverterGame.OMEGA_RUBY,
        Gen6ConverterGame.X to Gen6ConverterGame.Y,
        Gen6ConverterGame.Y to Gen6ConverterGame.X
    )

    @Test
    fun pendingEvidenceListsEveryMissingGate() {
        val evidence = Gen6ConversionAcceptance.pending(
            Gen6ConverterRequest(Gen6ConverterGame.OMEGA_RUBY, Gen6ConverterGame.ALPHA_SAPPHIRE)
        )
        assertTrue(evidence.missingEvidence().containsAll(listOf(
            "automated integrity",
            "destination-game load",
            "destination-game save",
            "destination-game reopen"
        )))
    }

    @Test
    fun partialEvidenceNoticeNamesOnlyOutstandingGates() {
        val evidence = Gen6ConversionAcceptance(
            Gen6ConverterRequest(Gen6ConverterGame.OMEGA_RUBY, Gen6ConverterGame.ALPHA_SAPPHIRE),
            automatedIntegrityPassed = true,
            destinationGameLoaded = true,
            destinationGameSaved = false,
            reopenedAfterGameSave = false,
            destinationSaveSha256 = destinationSaveSha256
        )
        assertTrue(evidence.missingEvidenceNotice().contains("destination-game save"))
        assertTrue(evidence.missingEvidenceNotice().contains("destination-game reopen"))
        assertFalse(evidence.missingEvidenceNotice().contains("automated integrity"))
        assertFalse(evidence.missingEvidenceNotice().contains("destination-game load"))
    }

    @Test
    fun completeEvidenceHasNoMissingGates() {
        val evidence = Gen6ConversionAcceptance(
            Gen6ConverterRequest(Gen6ConverterGame.OMEGA_RUBY, Gen6ConverterGame.ALPHA_SAPPHIRE),
            automatedIntegrityPassed = true,
            destinationGameLoaded = true,
            destinationGameSaved = true,
            reopenedAfterGameSave = true,
            destinationSaveSha256 = destinationSaveSha256
        )
        assertTrue(evidence.missingEvidence().isEmpty())
        assertTrue(evidence.missingEvidenceNotice().contains("All automated and destination-game acceptance evidence is recorded"))
    }

    @Test
    fun nextRequiredEvidenceFollowsValidationOrder() {
        val request = Gen6ConverterRequest(
            Gen6ConverterGame.OMEGA_RUBY,
            Gen6ConverterGame.ALPHA_SAPPHIRE
        )
        assertTrue(Gen6ConversionAcceptance.pending(request).nextRequiredEvidence() == "automated integrity")
        assertTrue(Gen6ConversionAcceptance.automatedOnly(request).nextRequiredEvidence() == "destination-game load")
        assertTrue(
            Gen6ConversionAcceptance(
                request,
                true,
                true,
                true,
                true,
                destinationSaveSha256
            ).nextRequiredEvidence() == null
        )
    }

    @Test
    fun rejectsOutOfOrderRuntimeEvidence() {
        val request = Gen6ConverterRequest(
            Gen6ConverterGame.OMEGA_RUBY,
            Gen6ConverterGame.ALPHA_SAPPHIRE
        )
        assertThrows(IllegalArgumentException::class.java) {
            Gen6ConversionAcceptance(request, false, true, false, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6ConversionAcceptance(request, true, false, true, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6ConversionAcceptance(request, true, true, false, true)
        }
    }

    @Test
    fun destinationGameEvidenceRequiresExactDestinationFingerprint() {
        val request = Gen6ConverterRequest(
            Gen6ConverterGame.OMEGA_RUBY,
            Gen6ConverterGame.ALPHA_SAPPHIRE
        )
        assertThrows(IllegalArgumentException::class.java) {
            Gen6ConversionAcceptance(request, true, true, false, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6ConversionAcceptance(
                request,
                true,
                false,
                false,
                false,
                "A".repeat(64)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6ConversionAcceptance(
                request,
                true,
                false,
                false,
                false,
                "a".repeat(63)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6ConversionAcceptance(
                request,
                false,
                false,
                false,
                false,
                destinationSaveSha256
            )
        }
    }

    @Test
    fun automatedEvidenceNeverClaimsPlayableCompatibility() {
        routes.forEach { (source, destination) ->
            val evidence = Gen6ConversionAcceptance.automatedOnly(
                Gen6ConverterRequest(source, destination)
            )
            assertFalse(evidence.playableValidated)
            assertTrue(
                evidence.userNotice().contains(
                    "Real destination-game load, save and reopen compatibility is not yet confirmed"
                )
            )
            assertThrows(IllegalStateException::class.java) {
                evidence.requirePlayableClaim()
            }
        }
    }

    @Test
    fun recordedEvidenceKeepsReviewedSelectionSnapshot() {
        val selected = mutableListOf("exclusive-a")
        val evidence = Gen6ConversionAcceptance(
            request = Gen6ConverterRequest(
                source = Gen6ConverterGame.OMEGA_RUBY,
                destination = Gen6ConverterGame.ALPHA_SAPPHIRE,
                includeVersionExclusives = true,
                selectedVersionExclusiveIds = selected
            ),
            automatedIntegrityPassed = true,
            destinationGameLoaded = true,
            destinationGameSaved = true,
            reopenedAfterGameSave = true,
            destinationSaveSha256 = destinationSaveSha256
        )

        selected.clear()

        assertTrue(evidence.playableValidated)
        assertTrue(evidence.request.includeVersionExclusives)
        assertEquals(
            listOf("exclusive-a"),
            evidence.request.selectedVersionExclusiveIds
        )
        assertEquals(destinationSaveSha256, evidence.destinationSaveSha256)
    }

    @Test
    fun partialRuntimeEvidenceStillCannotClaimPlayable() {
        val request = Gen6ConverterRequest(
            Gen6ConverterGame.OMEGA_RUBY,
            Gen6ConverterGame.ALPHA_SAPPHIRE
        )
        val evidence = Gen6ConversionAcceptance(
            request,
            automatedIntegrityPassed = true,
            destinationGameLoaded = true,
            destinationGameSaved = true,
            reopenedAfterGameSave = false,
            destinationSaveSha256 = destinationSaveSha256
        )
        assertFalse(evidence.playableValidated)
        assertThrows(IllegalStateException::class.java) {
            evidence.requirePlayableClaim()
        }
    }

    @Test
    fun completeRuntimeEvidenceAllowsPlayableClaim() {
        val request = Gen6ConverterRequest(
            Gen6ConverterGame.OMEGA_RUBY,
            Gen6ConverterGame.ALPHA_SAPPHIRE
        )
        val evidence = Gen6ConversionAcceptance(
            request,
            automatedIntegrityPassed = true,
            destinationGameLoaded = true,
            destinationGameSaved = true,
            reopenedAfterGameSave = true,
            destinationSaveSha256 = destinationSaveSha256
        )
        assertTrue(evidence.playableValidated)
        evidence.requirePlayableClaim()
        assertTrue(evidence.userNotice().contains("recorded destination-game"))
        assertEquals(destinationSaveSha256, evidence.destinationSaveSha256)
    }
}
