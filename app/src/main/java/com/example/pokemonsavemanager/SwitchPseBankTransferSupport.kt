package com.example.pokemonsavemanager

/**
 * Maps validated Switch save families onto PSE Bank's title-level route inventory.
 *
 * This exposes directional Bank destinations only. It does not enable a transfer
 * unless the source and destination adapters can serialize and validate it.
 */
object SwitchPseBankTransferSupport {
    private val gamesByFamily = mapOf(
        SwitchGameFamily.LETS_GO_PIKACHU_EEVEE to listOf(
            SaveConverterGame.LETS_GO_PIKACHU,
            SaveConverterGame.LETS_GO_EEVEE
        ),
        SwitchGameFamily.SWORD_SHIELD to listOf(
            SaveConverterGame.SWORD,
            SaveConverterGame.SHIELD
        ),
        SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL to listOf(
            SaveConverterGame.BRILLIANT_DIAMOND,
            SaveConverterGame.SHINING_PEARL
        ),
        SwitchGameFamily.LEGENDS_ARCEUS to listOf(SaveConverterGame.LEGENDS_ARCEUS),
        SwitchGameFamily.SCARLET_VIOLET to listOf(
            SaveConverterGame.SCARLET,
            SaveConverterGame.VIOLET
        ),
        SwitchGameFamily.LEGENDS_ZA to listOf(SaveConverterGame.LEGENDS_ZA)
    )

    fun gamesFor(family: SwitchGameFamily): List<SaveConverterGame> =
        gamesByFamily.getValue(family)

    fun isSwitchGame(game: SaveConverterGame): Boolean =
        gamesByFamily.values.any { game in it }

    fun routesFrom(family: SwitchGameFamily): List<PseBankTransferRoute> =
        gamesFor(family).flatMap(PseBankTransferRoutes::forSource)

    fun forwardRoutesFrom(game: SaveConverterGame): List<PseBankTransferRoute> =
        if (isSwitchGame(game)) PseBankTransferRoutes.forwardFrom(game) else emptyList()

    fun backwardRoutesFrom(game: SaveConverterGame): List<PseBankTransferRoute> =
        if (isSwitchGame(game)) PseBankTransferRoutes.backwardFrom(game) else emptyList()
}
