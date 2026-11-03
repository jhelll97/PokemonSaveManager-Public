package com.example.pokemonsavemanager

/**
 * Fail-closed planning boundary for same-generation Gen 6 save conversion.
 *
 * This is intentionally separate from the existing Bank/PK6 transfer helpers:
 * a converter must select a destination baseline and cannot treat a Pokémon
 * transfer as a complete save conversion.
 */
enum class Gen6ConverterGame {
    OMEGA_RUBY, ALPHA_SAPPHIRE, X, Y
}

data class Gen6ConverterRequest(
    val source: Gen6ConverterGame,
    val destination: Gen6ConverterGame,
    val includeVersionExclusives: Boolean = false,
    val selectedVersionExclusiveIds: List<String> = emptyList()
)

enum class Gen6ConverterFieldStatus { PRESERVED, TRANSFORMED, OMITTED, GENERATED }

data class Gen6ConverterField(
    val name: String,
    val status: Gen6ConverterFieldStatus,
    val note: String
)

data class Gen6ConverterPlan(
    val request: Gen6ConverterRequest,
    val fields: List<Gen6ConverterField>,
    val blockers: List<String>
) {
    val canExport: Boolean get() = blockers.isEmpty()

    fun requireExportable() {
        require(canExport) { blockers.joinToString("; ") }
    }
}

object Gen6SaveConverterPlanner {
    private enum class RouteFamily { ORAS, KALOS }

    private val routeFamilies = mapOf(
        Gen6ConverterGame.OMEGA_RUBY to RouteFamily.ORAS,
        Gen6ConverterGame.ALPHA_SAPPHIRE to RouteFamily.ORAS,
        Gen6ConverterGame.X to RouteFamily.KALOS,
        Gen6ConverterGame.Y to RouteFamily.KALOS
    )

    private val mandatoryRoutes = setOf(
        Gen6ConverterGame.OMEGA_RUBY to Gen6ConverterGame.ALPHA_SAPPHIRE,
        Gen6ConverterGame.ALPHA_SAPPHIRE to Gen6ConverterGame.OMEGA_RUBY,
        Gen6ConverterGame.X to Gen6ConverterGame.Y,
        Gen6ConverterGame.Y to Gen6ConverterGame.X
    )

    private val versionExclusiveSelectionId =
        Regex("[a-z0-9]+(?:-[a-z0-9]+)*")

    private const val MAX_DESTINATION_PC_SLOTS = 31 * 30

    fun plan(request: Gen6ConverterRequest): Gen6ConverterPlan {
        val stableRequest = request.copy(
            selectedVersionExclusiveIds =
                request.selectedVersionExclusiveIds.toList()
        )
        val blockers = buildList {
            if (stableRequest.source == stableRequest.destination) {
                add("Source and destination games must differ.")
            }
            if (stableRequest.source != stableRequest.destination &&
                stableRequest.source to stableRequest.destination !in mandatoryRoutes
            ) {
                if (isCrossFamily(stableRequest)) {
                    add(
                        "X/Y and ORAS cross-family conversion requires " +
                            "separate story-feature compatibility and " +
                            "destination initialization evidence."
                    )
                } else {
                    add("This Gen 6 route is not enabled until its destination serializer and acceptance evidence exist.")
                }
            }
            if (stableRequest.includeVersionExclusives &&
                stableRequest.selectedVersionExclusiveIds.isEmpty()
            ) {
                add(
                    "Destination-version-exclusive Pokémon require an explicit " +
                        "verified catalog selection and legal PK6 injection."
                )
            }
            if (!stableRequest.includeVersionExclusives &&
                stableRequest.selectedVersionExclusiveIds.isNotEmpty()
            ) {
                add("Destination-version-exclusive selections require explicit opt-in.")
            }
            if (stableRequest.selectedVersionExclusiveIds.any { it.isBlank() }) {
                add("Destination-version-exclusive selection ids must not be blank.")
            }
            if (stableRequest.selectedVersionExclusiveIds.any {
                    !versionExclusiveSelectionId.matches(it)
                }
            ) {
                add("Destination-version-exclusive selection ids must be safe lowercase record ids.")
            }
            if (stableRequest.selectedVersionExclusiveIds.size >
                MAX_DESTINATION_PC_SLOTS
            ) {
                add(
                    "Destination-version-exclusive selections cannot exceed " +
                        "$MAX_DESTINATION_PC_SLOTS Gen 6 PC slots."
                )
            }
            if (stableRequest.selectedVersionExclusiveIds.size !=
                stableRequest.selectedVersionExclusiveIds.distinct().size
            ) {
                add("A destination-version-exclusive Pokémon may be selected only once.")
            }
        }
        return Gen6ConverterPlan(
            request = stableRequest,
            fields = listOf(
                Gen6ConverterField(
                    "party_and_pc_pokemon",
                    Gen6ConverterFieldStatus.TRANSFORMED,
                    "Reuse PK6 parsing, destination Legalise and capacity checks."
                ),
                Gen6ConverterField(
                    "trainer_identity",
                    Gen6ConverterFieldStatus.TRANSFORMED,
                    "Preserve only fields accepted by the destination game."
                ),
                Gen6ConverterField(
                    "pokedex",
                    Gen6ConverterFieldStatus.TRANSFORMED,
                    "Update only after destination persistence and checksum verification."
                ),
                Gen6ConverterField(
                    "story_flags_badges_maps_items_money",
                    Gen6ConverterFieldStatus.OMITTED,
                    "Do not silently copy incompatible game-specific progress."
                ),
                Gen6ConverterField(
                    "destination_save_structure",
                    Gen6ConverterFieldStatus.GENERATED,
                    "Requires a verified destination baseline and serializer."
                ),
                Gen6ConverterField(
                    "source_save_and_rollback",
                    Gen6ConverterFieldStatus.PRESERVED,
                    "Export creates a new file; source remains unchanged on failure."
                )
            ),
            blockers = blockers
        )
    }

    private fun isCrossFamily(request: Gen6ConverterRequest): Boolean =
        routeFamilies[request.source] != routeFamilies[request.destination]
}
