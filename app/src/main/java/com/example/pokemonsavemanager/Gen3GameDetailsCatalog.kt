package com.example.pokemonsavemanager

enum class Gen3GameDetailsAction(val label: String) {
    POKEBLOCKS("Pokéblock Case"), COINS("Game Corner Coins"),
    BATTLE_POINTS("Battle Points"), BERRY_POWDER("Berry Powder"),
    SOOT_ASH("Soot Sack Ash"), OPTIONS("Game Options"),
    SELECT_SHORTCUT("Registered SELECT Shortcut"), SAVED_LOCATION("Saved Location • Read-only"),
    RECORDED_ACTIVITY("Recorded Activity • Read-only"), HALL_OF_FAME("Hall of Fame • Read-only")
}

/** Title-specific tools; availability does not certify device acceptance. */
object Gen3GameDetailsCatalog {
    fun actionsForTitle(title: String): List<Gen3GameDetailsAction> {
        val actions = when (title) {
            "Pokémon Ruby", "Pokémon Sapphire" -> listOf(
                Gen3GameDetailsAction.POKEBLOCKS, Gen3GameDetailsAction.COINS,
                Gen3GameDetailsAction.SOOT_ASH, Gen3GameDetailsAction.OPTIONS,
                Gen3GameDetailsAction.SELECT_SHORTCUT, Gen3GameDetailsAction.SAVED_LOCATION, Gen3GameDetailsAction.RECORDED_ACTIVITY, Gen3GameDetailsAction.HALL_OF_FAME)
            "Pokémon Emerald" -> listOf(
                Gen3GameDetailsAction.POKEBLOCKS, Gen3GameDetailsAction.COINS,
                Gen3GameDetailsAction.BATTLE_POINTS, Gen3GameDetailsAction.BERRY_POWDER,
                Gen3GameDetailsAction.SOOT_ASH, Gen3GameDetailsAction.OPTIONS,
                Gen3GameDetailsAction.SELECT_SHORTCUT, Gen3GameDetailsAction.SAVED_LOCATION, Gen3GameDetailsAction.RECORDED_ACTIVITY, Gen3GameDetailsAction.HALL_OF_FAME)
            "Pokémon FireRed", "Pokémon LeafGreen" -> listOf(
                Gen3GameDetailsAction.COINS, Gen3GameDetailsAction.BERRY_POWDER,
                Gen3GameDetailsAction.OPTIONS, Gen3GameDetailsAction.SELECT_SHORTCUT, Gen3GameDetailsAction.SAVED_LOCATION, Gen3GameDetailsAction.RECORDED_ACTIVITY, Gen3GameDetailsAction.HALL_OF_FAME)
            else -> emptyList()
        }
        return actions.toList()
    }
}
