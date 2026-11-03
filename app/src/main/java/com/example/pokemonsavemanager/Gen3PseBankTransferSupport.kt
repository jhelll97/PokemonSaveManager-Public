package com.example.pokemonsavemanager

/**
 * Gen 3 PSE Bank transfer surface for UI and adapter routing.
 *
 * The route lookup is directional and refuses same-generation routes. Actual
 * save writes remain behind the destination adapter's integrity and rollback
 * boundary.
 */
object Gen3PseBankTransferSupport {
    private val titles = setOf(
        SaveConverterGame.RUBY,
        SaveConverterGame.SAPPHIRE,
        SaveConverterGame.EMERALD,
        SaveConverterGame.FIRERED,
        SaveConverterGame.LEAFGREEN
    )

    fun isGen3(title: SaveConverterGame): Boolean = title in titles

    fun forwardRoutes(title: SaveConverterGame): List<PseBankTransferRoute> =
        requireGen3(title).let(PseBankTransferRoutes::forwardFrom)

    fun backwardRoutes(title: SaveConverterGame): List<PseBankTransferRoute> =
        requireGen3(title).let(PseBankTransferRoutes::backwardFrom)

    fun findForward(
        source: SaveConverterGame,
        destination: SaveConverterGame
    ): PseBankTransferRoute? =
        if (isGen3(source)) PseBankTransferRoutes.findForward(source, destination) else null

    fun findBackward(
        source: SaveConverterGame,
        destination: SaveConverterGame
    ): PseBankTransferRoute? =
        if (isGen3(source)) PseBankTransferRoutes.findBackward(source, destination) else null

    private fun requireGen3(title: SaveConverterGame): SaveConverterGame =
        title.takeIf(::isGen3) ?: error(title.toString() + " is not a Gen 3 title.")
}