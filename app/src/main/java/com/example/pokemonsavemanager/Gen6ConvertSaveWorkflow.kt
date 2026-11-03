package com.example.pokemonsavemanager

import java.security.MessageDigest

private const val MAX_GEN6_CONVERTED_FILENAME_LENGTH = 120

private fun gen6ConvertedFilenameSuffix(
    destinationGame: Gen6ConverterGame
): String {
    val destinationLabel = when (destinationGame) {
        Gen6ConverterGame.X -> "Pokemon_X"
        Gen6ConverterGame.Y -> "Pokemon_Y"
        Gen6ConverterGame.OMEGA_RUBY -> "Pokemon_Omega_Ruby"
        Gen6ConverterGame.ALPHA_SAPPHIRE -> "Pokemon_Alpha_Sapphire"
    }
    return "_${destinationLabel}_Converted.sav"
}

private fun isPortableGen6ConvertedFilename(
    filename: String,
    destinationGame: Gen6ConverterGame
): Boolean {
    val suffix = gen6ConvertedFilenameSuffix(destinationGame)
    if (!filename.endsWith(suffix)) return false
    val sourceBase = filename.dropLast(suffix.length)
    return filename.length <= MAX_GEN6_CONVERTED_FILENAME_LENGTH &&
        sourceBase.isNotEmpty() &&
        sourceBase.all {
            it in 'A'..'Z' ||
                it in 'a'..'z' ||
                it in '0'..'9' ||
                it == '_' ||
                it == '-'
        }
}

private fun suggestedGen6ConvertedFilename(
    sourceLabel: String,
    destinationGame: Gen6ConverterGame
): String {
    val suffix = gen6ConvertedFilenameSuffix(destinationGame)
    val sanitizedBase = sourceLabel.substringBeforeLast('.')
        .replace(Regex("[^A-Za-z0-9_-]+"), "_")
        .trim('_')
        .ifBlank { "Pokemon_Save" }
    val boundedBase = sanitizedBase
        .take(MAX_GEN6_CONVERTED_FILENAME_LENGTH - suffix.length)
        .trimEnd('_')
        .ifBlank { "Pokemon_Save" }
    return boundedBase + suffix
}

internal fun gen6ConversionSourceFingerprint(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return buildString(digest.size * 2) {
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            append("0123456789abcdef"[value ushr 4])
            append("0123456789abcdef"[value and 0x0F])
        }
    }
}

data class Gen6ConvertSavePreview(
    val request: Gen6ConverterRequest,
    val sourceFingerprint: String,
    val sourceTitle: String,
    val destinationTitle: String,
    val suggestedFilename: String,
    val preserved: List<String>,
    val omitted: List<String>,
    val availableVersionExclusives: List<Gen6VersionExclusiveChoice>,
    val compatibilityNotice: String,
    val versionExclusiveUnavailableReason: String? = null
) {
    init {
        require(
            isPortableGen6ConvertedFilename(
                suggestedFilename,
                request.destination
            )
        ) {
            "Gen 6 conversion preview requires a portable, bounded, " +
                "destination-specific converted-save filename."
        }
        require(
            sourceFingerprint.length == 64 &&
                sourceFingerprint.all {
                    it in '0'..'9' || it in 'a'..'f'
                }
        ) {
            "Gen 6 conversion preview requires an exact source fingerprint."
        }
        require(
            versionExclusiveUnavailableReason == null ||
                (
                    isAuditableCatalogUnavailableReason(
                        versionExclusiveUnavailableReason
                    ) &&
                        availableVersionExclusives.isEmpty()
                    )
        ) {
            "A version-exclusive unavailability reason requires no available choices."
        }

        val choiceIds = availableVersionExclusives.map { it.id }
        require(choiceIds.size == choiceIds.distinct().size) {
            "Version-exclusive preview choices must have unique ids."
        }
        val choiceSpecies = availableVersionExclusives.map { it.species }
        require(choiceSpecies.size == choiceSpecies.distinct().size) {
            "Version-exclusive preview choices must have unique species."
        }
        val choiceFingerprints =
            availableVersionExclusives.map { it.recordFingerprint }
        require(
            choiceFingerprints.size == choiceFingerprints.distinct().size
        ) {
            "Version-exclusive preview choices must identify unique exact PK6 records."
        }
        require(
            availableVersionExclusives.all {
                it.destinationGame == request.destination
            }
        ) {
            "Version-exclusive preview choices must match the destination."
        }

        val selectedIds = request.selectedVersionExclusiveIds
        require(selectedIds.size == selectedIds.distinct().size) {
            "Version-exclusive preview selections must have unique ids."
        }
        require(
            request.includeVersionExclusives == selectedIds.isNotEmpty()
        ) {
            "Version-exclusive preview opt-in must exactly match its selection."
        }
        require(selectedIds.all { it in choiceIds }) {
            "Version-exclusive preview selections must be available choices."
        }
    }

    val canOfferVersionExclusives: Boolean
        get() = availableVersionExclusives.isNotEmpty()

    fun versionExclusiveAvailabilityNotice(): String =
        if (canOfferVersionExclusives) {
            "${availableVersionExclusives.size} evidence-backed destination-exclusive " +
                "Pokémon available for explicit opt-in."
        } else {
            versionExclusiveUnavailableReason
                ?: (
                    "Destination-exclusive Pokémon are unavailable until an audited " +
                        "evidence manifest and its exact PK6 record are installed."
                    )
        }
}

data class Gen6ConvertDestinationPreview(
    val conversion: Gen6ConvertSavePreview,
    val capacity: Gen6ConversionCapacityReport
) {
    init {
        require(
            capacity.selectedVersionExclusiveRecords ==
                conversion.request.selectedVersionExclusiveIds.size
        ) {
            "Destination capacity preview must count every selected " +
                "version-exclusive PK6 record."
        }
    }

    val canPrepare: Boolean
        get() = capacity.canConvert

    fun capacityNotice(): String {
        if (canPrepare) {
            return "Destination template has enough PC and party capacity."
        }
        val shortages = buildList {
            if (capacity.missingPcSlots > 0) {
                add(
                    "${capacity.missingPcSlots} more PC slot" +
                        if (capacity.missingPcSlots == 1) "" else "s"
                )
            }
            if (capacity.missingPartySlots > 0) {
                add(
                    "${capacity.missingPartySlots} more party slot" +
                        if (capacity.missingPartySlots == 1) "" else "s"
                )
            }
        }
        return "Destination template needs " +
            shortages.joinToString(" and ") + "."
    }
}

/**
 * Holds the validated destination as an immutable snapshot. ByteArray is
 * mutable, so every caller receives a defensive copy and cannot alter the
 * bytes that passed conversion validation while an export is pending.
 */
class Gen6PreparedSave(
    preview: Gen6ConvertSavePreview,
    bytes: ByteArray
) {
    val preview = preview.copy(
        request = preview.request.copy(
            selectedVersionExclusiveIds =
                preview.request.selectedVersionExclusiveIds.toList()
        ),
        preserved = preview.preserved.toList(),
        omitted = preview.omitted.toList(),
        availableVersionExclusives =
            preview.availableVersionExclusives.toList()
    )
    private val validatedBytes = bytes.copyOf()

    init {
        val validationInput = validatedBytes.copyOf()
        val validation = Gen6PartyStorageLayout.validate(
            validationInput,
            preview.request.destination
        )
        check(validationInput.contentEquals(validatedBytes)) {
            "Prepared-save validation changed converted bytes."
        }
        require(
            validation.isValid &&
                validation.detectedGame == preview.request.destination
        ) {
            "Prepared Gen 6 save must be a validated destination save."
        }
    }

    val bytes: ByteArray
        get() = validatedBytes.copyOf()
}

/**
 * UI-safe orchestration for the supported Gen 6 paired-title routes.
 *
 * This object prepares validated bytes only. Android document selection and
 * separate-file export remain explicit user actions in MainActivity.
 */
object Gen6ConvertSaveWorkflow {
    /**
     * Builds a preview from the complete fail-closed installation result.
     * Available catalogs remain destination-bound. Unavailable installations
     * expose their safe reason but cannot carry stale selected ids.
     */
    fun preview(
        sourceBytes: ByteArray,
        sourceLabel: String,
        installation: Gen6VersionExclusiveCatalogInstallation,
        selectedVersionExclusiveIds: List<String> = emptyList()
    ): Gen6ConvertSavePreview {
        val routePreview = preview(sourceBytes, sourceLabel)
        return when (installation) {
            is Gen6VersionExclusiveCatalogInstallation.Available -> {
                val catalog = installation.catalogFor(
                    routePreview.request.destination
                )
                preview(
                    sourceBytes = sourceBytes,
                    sourceLabel = sourceLabel,
                    selectedVersionExclusiveIds = selectedVersionExclusiveIds,
                    versionExclusiveCatalog = catalog
                )
            }
            is Gen6VersionExclusiveCatalogInstallation.Unavailable -> {
                require(selectedVersionExclusiveIds.isEmpty()) {
                    "Destination-exclusive Pokémon cannot be selected while " +
                        "the verified catalog is unavailable."
                }
                routePreview.copy(
                    versionExclusiveUnavailableReason = installation.reason
                )
            }
        }
    }

    fun preview(
        sourceBytes: ByteArray,
        sourceLabel: String,
        selectedVersionExclusiveIds: List<String> = emptyList(),
        versionExclusiveCatalog: Gen6VersionExclusiveCatalog? = null
    ): Gen6ConvertSavePreview {
        val sourceSnapshot = sourceBytes.copyOf()
        val stableSelectedIds = selectedVersionExclusiveIds.toList()
        val sourceGame = Gen6PcStorageLayout.detectGame(sourceSnapshot)
            ?: throw IllegalArgumentException(
                "The loaded save is not an identified Pokémon X/Y/OR/AS save."
            )
        val sourceValidation =
            Gen6PartyStorageLayout.validate(sourceSnapshot, sourceGame)
        require(sourceValidation.isValid) {
            "The loaded Gen 6 source failed identity, checksum or reopen validation."
        }
        val destinationGame = pairedDestination(sourceGame)
        fun requireSourceUnchanged() {
            check(sourceBytes.contentEquals(sourceSnapshot)) {
                "The Gen 6 source save changed during conversion preview."
            }
        }
        fun <T> verifyCatalog(block: () -> T): T {
            var failure: Throwable? = null
            return try {
                block()
            } catch (error: Throwable) {
                failure = error
                throw error
            } finally {
                if (!sourceBytes.contentEquals(sourceSnapshot)) {
                    throw IllegalStateException(
                        "The Gen 6 source save changed during conversion preview.",
                        failure
                    )
                }
            }
        }
        val availableVersionExclusives =
            verifyCatalog {
                val choices =
                    versionExclusiveCatalog?.choicesFor(destinationGame).orEmpty()
                if (choices.isNotEmpty()) {
                    // Revalidate exact records at the preview trust boundary;
                    // cached choice metadata alone is not current legality proof.
                    requireNotNull(versionExclusiveCatalog).resolve(
                        destinationGame,
                        choices.map { it.id }
                    )
                }
                choices
            }
        requireSourceUnchanged()
        if (stableSelectedIds.isNotEmpty()) {
            verifyCatalog {
                requireNotNull(versionExclusiveCatalog) {
                    "A verified destination-version-exclusive catalog is required."
                }.selectedChoicesFor(destinationGame, stableSelectedIds)
            }
            requireSourceUnchanged()
        }
        val request = Gen6ConverterRequest(
            source = sourceGame,
            destination = destinationGame,
            includeVersionExclusives = stableSelectedIds.isNotEmpty(),
            selectedVersionExclusiveIds = stableSelectedIds
        )
        Gen6SaveConverterPlanner.plan(request).requireExportable()
        val sourceTitle = displayName(sourceGame)
        val destinationTitle = displayName(destinationGame)
        val preview = Gen6ConvertSavePreview(
            request = request,
            sourceFingerprint =
                gen6ConversionSourceFingerprint(sourceSnapshot),
            sourceTitle = sourceTitle,
            destinationTitle = destinationTitle,
            suggestedFilename = suggestedGen6ConvertedFilename(
                sourceLabel,
                destinationGame
            ),
            preserved = buildList {
                add("Complete validated party records")
                add("All validated occupied PC records")
                add("PK6 identity and supported metadata")
                add("Destination Pokédex registration and save integrity")
                if (stableSelectedIds.isNotEmpty()) {
                    add(
                        "${stableSelectedIds.size} explicitly selected " +
                            "destination-version-exclusive Pokémon"
                    )
                }
            },
            omitted = buildList {
                add("Source story, map and event progress")
                add("Source badges, items and money")
                add("Unverified source-title-specific save blocks")
                if (stableSelectedIds.isEmpty()) {
                    add(
                        "Destination-version-exclusive Pokémon " +
                            "(none explicitly selected)"
                    )
                }
            },
            availableVersionExclusives = availableVersionExclusives,
            compatibilityNotice =
                Gen6ConversionAcceptance.pending(request).userNotice()
        )
        requireSourceUnchanged()
        return preview
    }

    /**
     * Rebuilds a preview from immutable UI state only when both the currently
     * loaded source and the verified catalog still match that state.
     */
    fun preview(
        sourceBytes: ByteArray,
        sourceLabel: String,
        selectionState: Gen6VersionExclusiveSelectionState,
        versionExclusiveCatalog: Gen6VersionExclusiveCatalog
    ): Gen6ConvertSavePreview {
        val detectedSource = Gen6PcStorageLayout.detectGame(sourceBytes)
        if (detectedSource != null) {
            require(
                pairedDestination(detectedSource) ==
                    selectionState.destinationGame
            ) {
                "The loaded source no longer matches the " +
                    "version-exclusive selection destination."
            }
            require(
                gen6ConversionSourceFingerprint(sourceBytes) ==
                    selectionState.sourceFingerprint
            ) {
                "The loaded source save changed; review version-exclusive " +
                    "selections again."
            }
        }
        val refreshed = preview(
            sourceBytes = sourceBytes,
            sourceLabel = sourceLabel,
            selectedVersionExclusiveIds = selectionState.selectedIds,
            versionExclusiveCatalog = versionExclusiveCatalog
        )
        require(refreshed.request.destination == selectionState.destinationGame) {
            "The loaded source no longer matches the version-exclusive selection destination."
        }
        require(
            refreshed.sourceFingerprint == selectionState.sourceFingerprint
        ) {
            "The loaded source save changed; review version-exclusive " +
                "selections again."
        }
        require(
            refreshed.availableVersionExclusives ==
                selectionState.availableChoices
        ) {
            "The verified version-exclusive catalog changed; review the selection again."
        }
        return refreshed
    }

    /**
     * Refreshes immutable reviewed selection state through the complete
     * destination-bound installation, so UI callers cannot accidentally drop
     * an unavailable or replaced catalog before destination selection.
     */
    fun preview(
        sourceBytes: ByteArray,
        sourceLabel: String,
        installation: Gen6VersionExclusiveCatalogInstallation,
        selectionState: Gen6VersionExclusiveSelectionState
    ): Gen6ConvertSavePreview {
        val sourceSnapshot = sourceBytes.copyOf()
        val routePreview = preview(sourceSnapshot, sourceLabel)
        val available = requireNotNull(
            installation as? Gen6VersionExclusiveCatalogInstallation.Available
        ) {
            "Version-exclusive selection state cannot be used while the " +
                "verified catalog is unavailable."
        }
        val catalog = available.catalogFor(routePreview.request.destination)
        val refreshed = preview(
            sourceBytes = sourceSnapshot,
            sourceLabel = sourceLabel,
            selectionState = selectionState,
            versionExclusiveCatalog = catalog
        )
        check(sourceBytes.contentEquals(sourceSnapshot)) {
            "Selection preview changed the conversion source."
        }
        return refreshed
    }

    fun previewDestination(
        sourceBytes: ByteArray,
        destinationTemplate: ByteArray,
        sourceLabel: String,
        selectedVersionExclusiveIds: List<String> = emptyList(),
        versionExclusiveCatalog: Gen6VersionExclusiveCatalog? = null
    ): Gen6ConvertDestinationPreview {
        val sourceSnapshot = sourceBytes.copyOf()
        val templateSnapshot = destinationTemplate.copyOf()
        val conversion = preview(
            sourceSnapshot,
            sourceLabel,
            selectedVersionExclusiveIds,
            versionExclusiveCatalog
        )
        requireValidDestinationTemplate(
            templateSnapshot,
            conversion
        )
        val capacity = Gen6ConversionCapacityPreflight.inspect(
            sourceSnapshot,
            templateSnapshot,
            conversion.request
        )
        check(sourceBytes.contentEquals(sourceSnapshot)) {
            "Destination preview changed the conversion source."
        }
        check(destinationTemplate.contentEquals(templateSnapshot)) {
            "Destination preview changed the destination template."
        }
        return Gen6ConvertDestinationPreview(conversion, capacity)
    }

    /**
     * Carries the complete fail-closed catalog installation result through
     * destination-template validation and capacity inspection. This prevents
     * UI callers from dropping an unavailable result or reusing a catalog
     * installed for a different paired destination.
     */
    fun previewDestination(
        sourceBytes: ByteArray,
        destinationTemplate: ByteArray,
        sourceLabel: String,
        installation: Gen6VersionExclusiveCatalogInstallation,
        selectedVersionExclusiveIds: List<String> = emptyList()
    ): Gen6ConvertDestinationPreview {
        val sourceSnapshot = sourceBytes.copyOf()
        val templateSnapshot = destinationTemplate.copyOf()
        val conversion = preview(
            sourceBytes = sourceSnapshot,
            sourceLabel = sourceLabel,
            installation = installation,
            selectedVersionExclusiveIds = selectedVersionExclusiveIds
        )
        requireValidDestinationTemplate(
            templateSnapshot,
            conversion
        )
        val capacity = Gen6ConversionCapacityPreflight.inspect(
            sourceSnapshot,
            templateSnapshot,
            conversion.request
        )
        check(sourceBytes.contentEquals(sourceSnapshot)) {
            "Destination preview changed the conversion source."
        }
        check(destinationTemplate.contentEquals(templateSnapshot)) {
            "Destination preview changed the destination template."
        }
        return Gen6ConvertDestinationPreview(conversion, capacity)
    }

    fun previewDestination(
        sourceBytes: ByteArray,
        destinationTemplate: ByteArray,
        sourceLabel: String,
        selectionState: Gen6VersionExclusiveSelectionState,
        versionExclusiveCatalog: Gen6VersionExclusiveCatalog
    ): Gen6ConvertDestinationPreview {
        val sourceSnapshot = sourceBytes.copyOf()
        val templateSnapshot = destinationTemplate.copyOf()
        val conversion = preview(
            sourceBytes = sourceSnapshot,
            sourceLabel = sourceLabel,
            selectionState = selectionState,
            versionExclusiveCatalog = versionExclusiveCatalog
        )
        requireValidDestinationTemplate(
            templateSnapshot,
            conversion
        )
        val capacity = Gen6ConversionCapacityPreflight.inspect(
            sourceSnapshot,
            templateSnapshot,
            conversion.request
        )
        check(sourceBytes.contentEquals(sourceSnapshot)) {
            "Destination preview changed the conversion source."
        }
        check(destinationTemplate.contentEquals(templateSnapshot)) {
            "Destination preview changed the destination template."
        }
        return Gen6ConvertDestinationPreview(conversion, capacity)
    }

    /**
     * Keeps immutable reviewed selection state attached to the complete
     * destination-bound installation during capacity preview. An unavailable,
     * wrong-destination, or changed catalog must be reviewed again.
     */
    fun previewDestination(
        sourceBytes: ByteArray,
        destinationTemplate: ByteArray,
        sourceLabel: String,
        installation: Gen6VersionExclusiveCatalogInstallation,
        selectionState: Gen6VersionExclusiveSelectionState
    ): Gen6ConvertDestinationPreview {
        val sourceSnapshot = sourceBytes.copyOf()
        val templateSnapshot = destinationTemplate.copyOf()
        val routePreview = preview(sourceSnapshot, sourceLabel)
        val available = requireNotNull(
            installation as? Gen6VersionExclusiveCatalogInstallation.Available
        ) {
            "Version-exclusive selection state cannot be used while the " +
                "verified catalog is unavailable."
        }
        val catalog = available.catalogFor(routePreview.request.destination)
        val destinationPreview = previewDestination(
            sourceBytes = sourceSnapshot,
            destinationTemplate = templateSnapshot,
            sourceLabel = sourceLabel,
            selectionState = selectionState,
            versionExclusiveCatalog = catalog
        )
        check(sourceBytes.contentEquals(sourceSnapshot)) {
            "Destination preview changed the conversion source."
        }
        check(destinationTemplate.contentEquals(templateSnapshot)) {
            "Destination preview changed the destination template."
        }
        return destinationPreview
    }

    /**
     * Final preparation from immutable UI state requires the exact available
     * choice metadata and record fingerprints the user reviewed. A refreshed
     * or unavailable installation must be reviewed before serialization.
     */
    fun prepare(
        sourceBytes: ByteArray,
        destinationTemplate: ByteArray,
        sourceLabel: String,
        destinationBaselineAccepted: Boolean,
        installation: Gen6VersionExclusiveCatalogInstallation,
        selectionState: Gen6VersionExclusiveSelectionState
    ): Gen6PreparedSave {
        require(destinationBaselineAccepted) {
            "Confirm that destination progress will come from the selected template " +
                "and omitted source progress will not be copied."
        }
        val sourceSnapshot = sourceBytes.copyOf()
        val templateSnapshot = destinationTemplate.copyOf()
        val routePreview = preview(sourceSnapshot, sourceLabel)
        val available = requireNotNull(
            installation as? Gen6VersionExclusiveCatalogInstallation.Available
        ) {
            "Version-exclusive selection state cannot be used while the " +
                "verified catalog is unavailable."
        }
        val catalog = available.catalogFor(
            routePreview.request.destination
        )
        val refreshedPreview = preview(
            sourceBytes = sourceSnapshot,
            sourceLabel = sourceLabel,
            selectionState = selectionState,
            versionExclusiveCatalog = catalog
        )
        val prepared = prepare(
            sourceBytes = sourceSnapshot,
            destinationTemplate = templateSnapshot,
            sourceLabel = sourceLabel,
            destinationBaselineAccepted = true,
            installation = installation,
            selectedVersionExclusiveIds = selectionState.selectedIds
        )
        check(prepared.preview == refreshedPreview) {
            "The verified version-exclusive catalog changed during save preparation."
        }
        check(sourceBytes.contentEquals(sourceSnapshot)) {
            "The source save changed while preparing conversion."
        }
        check(destinationTemplate.contentEquals(templateSnapshot)) {
            "The destination template changed while preparing conversion."
        }
        return prepared
    }

    /**
     * Keeps the complete installation result attached through validated
     * destination serialization. Unavailable installations can still prepare
     * a conversion without extras, while their safe reason survives for UI
     * display and stale selected ids remain rejected.
     */
    fun prepare(
        sourceBytes: ByteArray,
        destinationTemplate: ByteArray,
        sourceLabel: String,
        destinationBaselineAccepted: Boolean,
        installation: Gen6VersionExclusiveCatalogInstallation,
        selectedVersionExclusiveIds: List<String> = emptyList()
    ): Gen6PreparedSave {
        require(destinationBaselineAccepted) {
            "Confirm that destination progress will come from the selected template " +
                "and omitted source progress will not be copied."
        }
        val stableSelectedIds = selectedVersionExclusiveIds.toList()
        val sourceSnapshot = sourceBytes.copyOf()
        val templateSnapshot = destinationTemplate.copyOf()
        val installationPreview = preview(
            sourceBytes = sourceSnapshot,
            sourceLabel = sourceLabel,
            installation = installation,
            selectedVersionExclusiveIds = stableSelectedIds
        )
        val catalog = when (installation) {
            is Gen6VersionExclusiveCatalogInstallation.Available ->
                installation.catalogFor(
                    installationPreview.request.destination
                )
            is Gen6VersionExclusiveCatalogInstallation.Unavailable -> null
        }
        val prepared = prepare(
            sourceBytes = sourceSnapshot,
            destinationTemplate = templateSnapshot,
            sourceLabel = sourceLabel,
            destinationBaselineAccepted = true,
            selectedVersionExclusiveIds = stableSelectedIds,
            versionExclusiveCatalog = catalog
        )
        check(prepared.preview.request == installationPreview.request) {
            "The Gen 6 conversion route changed during save preparation."
        }
        check(
            prepared.preview.availableVersionExclusives ==
                installationPreview.availableVersionExclusives
        ) {
            "The verified version-exclusive catalog changed during save preparation."
        }
        check(sourceBytes.contentEquals(sourceSnapshot)) {
            "The source save changed while preparing conversion."
        }
        check(destinationTemplate.contentEquals(templateSnapshot)) {
            "The destination template changed while preparing conversion."
        }
        return Gen6PreparedSave(installationPreview, prepared.bytes)
    }

    fun prepare(
        sourceBytes: ByteArray,
        destinationTemplate: ByteArray,
        sourceLabel: String,
        destinationBaselineAccepted: Boolean,
        selectedVersionExclusiveIds: List<String> = emptyList(),
        versionExclusiveCatalog: Gen6VersionExclusiveCatalog? = null
    ): Gen6PreparedSave {
        require(destinationBaselineAccepted) {
            "Confirm that destination progress will come from the selected template " +
                "and omitted source progress will not be copied."
        }
        val sourceSnapshot = sourceBytes.copyOf()
        val templateSnapshot = destinationTemplate.copyOf()
        val destinationPreview = previewDestination(
            sourceSnapshot,
            templateSnapshot,
            sourceLabel,
            selectedVersionExclusiveIds,
            versionExclusiveCatalog
        )
        val preview = destinationPreview.conversion
        destinationPreview.capacity.requireFits()

        val writer = Gen6ConcreteTemplateWriterFactory.create(
            preview.request.source,
            preview.request.destination,
            templateSnapshot,
            versionExclusiveCatalog
        )
        val sourceValidationInput = sourceSnapshot.copyOf()
        val sourceValidation =
            writer.validateSource(sourceValidationInput, preview.request)
        check(sourceValidationInput.contentEquals(sourceSnapshot)) {
            "Source validation changed the conversion source."
        }
        require(sourceValidation.isValid &&
            sourceValidation.detectedGame == preview.request.source
        ) {
            "The selected source failed concrete Gen 6 validation."
        }

        val converted =
            writer.serialize(sourceSnapshot.copyOf(), preview.request)
        val destinationValidationInput = converted.copyOf()
        val destinationValidation = writer.validateDestination(
            destinationValidationInput,
            preview.request
        )
        check(destinationValidationInput.contentEquals(converted)) {
            "Destination validation changed converted bytes."
        }
        require(destinationValidation.isValid &&
            destinationValidation.detectedGame == preview.request.destination
        ) {
            "The converted destination failed identity, integrity or reopen validation."
        }
        check(sourceBytes.contentEquals(sourceSnapshot)) {
            "The source save changed while preparing conversion."
        }
        check(destinationTemplate.contentEquals(templateSnapshot)) {
            "The destination template changed while preparing conversion."
        }
        return Gen6PreparedSave(preview, converted)
    }

    private fun requireValidDestinationTemplate(
        destinationTemplate: ByteArray,
        conversion: Gen6ConvertSavePreview
    ) {
        val validationInput = destinationTemplate.copyOf()
        val validation = Gen6PartyStorageLayout.validate(
            validationInput,
            conversion.request.destination
        )
        check(validationInput.contentEquals(destinationTemplate)) {
            "Destination-template validation changed template bytes."
        }
        require(
            validation.isValid &&
                validation.detectedGame == conversion.request.destination
        ) {
            "The selected destination template failed identity, checksum or " +
                "reopen validation for ${conversion.destinationTitle}."
        }
    }

    private fun pairedDestination(game: Gen6ConverterGame): Gen6ConverterGame =
        when (game) {
            Gen6ConverterGame.OMEGA_RUBY ->
                Gen6ConverterGame.ALPHA_SAPPHIRE
            Gen6ConverterGame.ALPHA_SAPPHIRE ->
                Gen6ConverterGame.OMEGA_RUBY
            Gen6ConverterGame.X -> Gen6ConverterGame.Y
            Gen6ConverterGame.Y -> Gen6ConverterGame.X
        }

    private fun displayName(game: Gen6ConverterGame): String = when (game) {
        Gen6ConverterGame.OMEGA_RUBY -> "Omega Ruby"
        Gen6ConverterGame.ALPHA_SAPPHIRE -> "Alpha Sapphire"
        Gen6ConverterGame.X -> "Pokémon X"
        Gen6ConverterGame.Y -> "Pokémon Y"
    }
}