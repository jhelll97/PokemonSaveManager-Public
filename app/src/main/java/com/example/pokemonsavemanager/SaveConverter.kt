package com.example.pokemonsavemanager

/**
 * Pure planning boundary for cross-game save conversion.
 *
 * This deliberately does not serialize a destination save. Adapters must provide
 * a lawful destination baseline/template before export is enabled.
 */
enum class SaveConverterGame(val generation: Int) {
    RED(1), BLUE(1), YELLOW(1),
    GOLD(2), SILVER(2), CRYSTAL(2),
    RUBY(3), SAPPHIRE(3), EMERALD(3), FIRERED(3), LEAFGREEN(3),
    DIAMOND(4), PEARL(4), PLATINUM(4), HEARTGOLD(4), SOULSILVER(4),
    BLACK(5), WHITE(5), BLACK_2(5), WHITE_2(5),
    X(6), Y(6), OMEGA_RUBY(6), ALPHA_SAPPHIRE(6),
    SUN(7), MOON(7), ULTRA_SUN(7), ULTRA_MOON(7)
}

enum class ConverterFieldStatus { PRESERVED, TRANSFORMED, OMITTED, GENERATED }

data class ConverterField(val name: String, val status: ConverterFieldStatus, val note: String)

data class SaveConverterRequest(
    val source: SaveConverterGame,
    val destination: SaveConverterGame,
    val includeVersionExclusives: Boolean = false
)

data class SaveConverterPlan(
    val request: SaveConverterRequest,
    val fields: List<ConverterField>,
    val blockers: List<String>
) {
    val canExport: Boolean get() = blockers.isEmpty()

    fun requireExportable() {
        require(canExport) { blockers.joinToString("; ") }
    }
}

/**
 * Directional PSE Bank route inventory. The route describes which source and
 * destination adapters must exist; it does not bypass legality, capacity,
 * checksum, rollback or destination-integrity gates.
 */
enum class PseBankTransferDirection { FORWARD, BACKWARD }

data class PseBankTransferRoute(
    val source: SaveConverterGame,
    val destination: SaveConverterGame,
    val direction: PseBankTransferDirection
)

object PseBankTransferRoutes {
    fun forSource(source: SaveConverterGame): List<PseBankTransferRoute> =
        SaveConverterGame.entries
            .asSequence()
            .filter { it.generation != source.generation }
            .map { destination ->
                PseBankTransferRoute(
                    source = source,
                    destination = destination,
                    direction = if (destination.generation > source.generation) {
                        PseBankTransferDirection.FORWARD
                    } else {
                        PseBankTransferDirection.BACKWARD
                    }
                )
            }
            .toList()

    fun forwardFrom(source: SaveConverterGame): List<PseBankTransferRoute> =
        forSource(source).filter { it.direction == PseBankTransferDirection.FORWARD }

    fun backwardFrom(source: SaveConverterGame): List<PseBankTransferRoute> =
        forSource(source).filter { it.direction == PseBankTransferDirection.BACKWARD }

    fun find(
        source: SaveConverterGame,
        destination: SaveConverterGame
    ): PseBankTransferRoute? =
        forSource(source).firstOrNull { it.destination == destination }

    fun findForward(
        source: SaveConverterGame,
        destination: SaveConverterGame
    ): PseBankTransferRoute? =
        find(source, destination)?.takeIf { it.direction == PseBankTransferDirection.FORWARD }

    fun findBackward(
        source: SaveConverterGame,
        destination: SaveConverterGame
    ): PseBankTransferRoute? =
        find(source, destination)?.takeIf { it.direction == PseBankTransferDirection.BACKWARD }

    fun contains(source: SaveConverterGame, destination: SaveConverterGame): Boolean =
        find(source, destination) != null
}

object SaveConverterPlanner {
    fun plan(request: SaveConverterRequest): SaveConverterPlan {
        val blockers = buildList {
            if (request.source.generation != 3) add("Only Gen 3 source adapters are enabled in Stage 1.")
            if (request.destination.generation != 6) add("Stage 1 destinations must be Gen 6.")
            if (request.source == request.destination) add("Source and destination games must differ.")
            if (request.destination != SaveConverterGame.ALPHA_SAPPHIRE) {
                add("Ruby → Alpha Sapphire is the first enabled destination route.")
            }
        }
        val fields = listOf(
            ConverterField("party_and_pc_pokemon", ConverterFieldStatus.TRANSFORMED,
                "Route through the existing PK3 parser and PK6 Legalise rules."),
            ConverterField("trainer_identity", ConverterFieldStatus.TRANSFORMED,
                "Preserve only fields accepted by the destination adapter."),
            ConverterField("pokedex", ConverterFieldStatus.TRANSFORMED,
                "Register only after destination persistence and checksum verification."),
            ConverterField("story_flags_badges_maps_items_money", ConverterFieldStatus.OMITTED,
                "Gen 3 progress is not silently copied into a Gen 6 save."),
            ConverterField("destination_save_structure", ConverterFieldStatus.GENERATED,
                "Requires a verified Alpha Sapphire baseline/template and serializer."),
            ConverterField("source_save_and_rollback", ConverterFieldStatus.PRESERVED,
                "The source remains unchanged until export verification succeeds.")
        )
        return SaveConverterPlan(request, fields, blockers)
    }
}
