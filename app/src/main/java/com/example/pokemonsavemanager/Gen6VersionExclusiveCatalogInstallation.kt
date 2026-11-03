package com.example.pokemonsavemanager

private const val MAX_AUDITABLE_CATALOG_UNAVAILABLE_REASON_LENGTH = 512

internal fun isAuditableCatalogUnavailableReason(reason: String): Boolean =
    reason.isNotBlank() &&
        reason.length <= MAX_AUDITABLE_CATALOG_UNAVAILABLE_REASON_LENGTH &&
        reason == reason.trim() &&
        reason.none {
            Character.isISOControl(it) ||
                Character.getType(it) == Character.FORMAT.toInt() ||
                Character.getType(it) == Character.LINE_SEPARATOR.toInt() ||
                Character.getType(it) == Character.PARAGRAPH_SEPARATOR.toInt()
        }

/**
 * Result of installing user-supplied, evidence-backed Gen 6 version-exclusive
 * records. A catalog is exposed only when the manifest, every exact PK6 record,
 * and every legality check succeed together.
 */
sealed class Gen6VersionExclusiveCatalogInstallation {
    class Available internal constructor(
        val destinationGame: Gen6ConverterGame,
        private val verifiedCatalog: Gen6VersionExclusiveCatalog
    ) : Gen6VersionExclusiveCatalogInstallation() {
        /**
         * Prevents a catalog verified for an earlier preview from being reused
         * after the loaded source changes to a different paired destination.
         */
        fun catalogFor(
            requiredDestinationGame: Gen6ConverterGame
        ): Gen6VersionExclusiveCatalog {
            require(requiredDestinationGame == destinationGame) {
                "The installed version-exclusive catalog was verified for " +
                    "${destinationLabel(destinationGame)}, not " +
                    "${destinationLabel(requiredDestinationGame)}. " +
                    "Reload and review destination-exclusive choices."
            }
            return verifiedCatalog
        }

        private fun destinationLabel(game: Gen6ConverterGame): String =
            when (game) {
                Gen6ConverterGame.X -> "Pokémon X"
                Gen6ConverterGame.Y -> "Pokémon Y"
                Gen6ConverterGame.OMEGA_RUBY -> "Omega Ruby"
                Gen6ConverterGame.ALPHA_SAPPHIRE -> "Alpha Sapphire"
            }
    }

    data class Unavailable(
        val reason: String
    ) : Gen6VersionExclusiveCatalogInstallation() {
        init {
            require(isAuditableCatalogUnavailableReason(reason)) {
                "Unavailable version-exclusive catalog requires a bounded, " +
                    "trimmed, single-line reason."
            }
        }
    }
}

/**
 * Composes the installed manifest and record sources at the UI boundary.
 *
 * Installation is atomic from the caller's perspective: any manifest, record,
 * fingerprint, decode, or legality failure returns [Unavailable] and no
 * partially constructed catalog can be observed. Fatal JVM errors are not
 * swallowed.
 */
class Gen6VersionExclusiveCatalogInstaller(
    private val manifestSource: Gen6VersionExclusiveManifestSource,
    private val recordSource: Gen6VersionExclusiveRecordSource,
    private val legalityVerifier: Gen6VersionExclusiveLegalityVerifier
) {
    /**
     * Installs a catalog only when it contains at least one verified choice for
     * the exact destination currently being previewed. The successful result
     * remains bound to that destination for every later catalog access.
     */
    fun installFor(
        destinationGame: Gen6ConverterGame
    ): Gen6VersionExclusiveCatalogInstallation =
        try {
            val manifests = manifestSource.read().toList()
            require(manifests.isNotEmpty()) {
                "Version-exclusive evidence manifest contains no records."
            }
            val catalog = Gen6VersionExclusiveCatalogProvider(
                manifests = manifests,
                recordSource = recordSource,
                legalityVerifier = legalityVerifier
            ).load().scopedTo(destinationGame)
            require(catalog.choicesFor(destinationGame).isNotEmpty()) {
                "No evidence-backed destination-exclusive Pokémon are " +
                    "installed for ${destinationLabel(destinationGame)}."
            }
            for (manifest in manifests) {
                val currentRecord = recordSource.read(manifest.id)
                require(
                    currentRecord != null &&
                        gen6VersionExclusiveRecordFingerprint(currentRecord) ==
                        manifest.recordFingerprint
                ) {
                    "Version-exclusive record ${manifest.id} changed during catalog installation."
                }
            }
            require(manifestSource.read().toList() == manifests) {
                "Version-exclusive evidence manifest changed during catalog installation."
            }
            Gen6VersionExclusiveCatalogInstallation.Available(
                destinationGame = destinationGame,
                verifiedCatalog = catalog
            )
        } catch (failure: Exception) {
            Gen6VersionExclusiveCatalogInstallation.Unavailable(
                safeFailureReason(failure)
            )
        }

    private fun safeFailureReason(failure: Exception): String =
        when (failure) {
            is IllegalArgumentException,
            is IllegalStateException ->
                failure.message
                    ?.takeIf(::isAuditableCatalogUnavailableReason)
                    ?: GENERIC_UNAVAILABLE_REASON
            else -> GENERIC_UNAVAILABLE_REASON
        }

    private fun destinationLabel(game: Gen6ConverterGame): String =
        when (game) {
            Gen6ConverterGame.X -> "Pokémon X"
            Gen6ConverterGame.Y -> "Pokémon Y"
            Gen6ConverterGame.OMEGA_RUBY -> "Omega Ruby"
            Gen6ConverterGame.ALPHA_SAPPHIRE -> "Alpha Sapphire"
        }

    private companion object {
        const val GENERIC_UNAVAILABLE_REASON =
            "Version-exclusive catalog could not be installed."
    }
}
