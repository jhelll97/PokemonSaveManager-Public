package com.example.pokemonsavemanager

/**
 * Keeps automated integrity evidence separate from destination-game evidence.
 */
class Gen6ConversionAcceptance(
    request: Gen6ConverterRequest,
    val automatedIntegrityPassed: Boolean,
    val destinationGameLoaded: Boolean,
    val destinationGameSaved: Boolean,
    val reopenedAfterGameSave: Boolean,
    val destinationSaveSha256: String? = null
) {
    private val requestPlan = Gen6SaveConverterPlanner.plan(request)

    val request: Gen6ConverterRequest =
        requestPlan.also { it.requireExportable() }.request

    init {
        require(!destinationGameLoaded || automatedIntegrityPassed) {
            "Destination-game load evidence requires automated integrity evidence first."
        }
        require(!destinationGameSaved || destinationGameLoaded) {
            "Destination-game save evidence requires load evidence first."
        }
        require(!reopenedAfterGameSave || destinationGameSaved) {
            "Destination-game reopen evidence requires save evidence first."
        }

        val hasDestinationGameEvidence =
            destinationGameLoaded || destinationGameSaved || reopenedAfterGameSave
        destinationSaveSha256?.let { fingerprint ->
            require(
                fingerprint.length == 64 &&
                    fingerprint.all { character ->
                        character in '0'..'9' || character in 'a'..'f'
                    }
            ) {
                "Destination save fingerprint must be an exact lowercase SHA-256 value."
            }
        }
        require(destinationSaveSha256 == null || automatedIntegrityPassed) {
            "Destination save fingerprint requires automated integrity evidence first."
        }
        require(!hasDestinationGameEvidence || destinationSaveSha256 != null) {
            "Destination-game evidence must be bound to the exact destination save SHA-256."
        }
    }

    val playableValidated: Boolean
        get() = automatedIntegrityPassed &&
            destinationGameLoaded &&
            destinationGameSaved &&
            reopenedAfterGameSave

    fun missingEvidence(): List<String> = buildList {
        if (!automatedIntegrityPassed) add("automated integrity")
        if (!destinationGameLoaded) add("destination-game load")
        if (!destinationGameSaved) add("destination-game save")
        if (!reopenedAfterGameSave) add("destination-game reopen")
    }

    fun nextRequiredEvidence(): String? = missingEvidence().firstOrNull()

    fun missingEvidenceNotice(): String {
        val missing = missingEvidence()
        return if (missing.isEmpty()) {
            "All automated and destination-game acceptance evidence is recorded."
        } else {
            "Missing evidence: ${missing.joinToString(", ")}."
        }
    }

    fun userNotice(): String = when {
        playableValidated ->
            "This route has automated integrity plus recorded destination-game load, save and reopen evidence."
        automatedIntegrityPassed ->
            "Automated identity, checksum and readback checks passed. Real destination-game load, save and reopen compatibility is not yet confirmed."
        else ->
            "This route has no completed export evidence yet. Real destination-game compatibility is not confirmed."
    }

    fun requirePlayableClaim() {
        check(playableValidated) {
            "Playable compatibility requires automated integrity and real destination-game load, save and reopen evidence."
        }
    }

    companion object {
        fun pending(request: Gen6ConverterRequest) =
            Gen6ConversionAcceptance(
                request,
                automatedIntegrityPassed = false,
                destinationGameLoaded = false,
                destinationGameSaved = false,
                reopenedAfterGameSave = false
            )

        fun automatedOnly(request: Gen6ConverterRequest) =
            Gen6ConversionAcceptance(
                request,
                automatedIntegrityPassed = true,
                destinationGameLoaded = false,
                destinationGameSaved = false,
                reopenedAfterGameSave = false
            )
    }
}
