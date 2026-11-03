package com.example.pokemonsavemanager

/**
 * Gen 3's inherited baseline and title-specific feature surface.
 *
 * This is the capability contract used by Gen 3 adapters and UI routing.
 * It does not claim that a feature is complete until its save offsets,
 * persistence, integrity, rollback and runtime checks are implemented.
 */
enum class Gen3Feature {
    SAVE_DETECTION_AND_VALIDATION,
    PARTY_VIEW_AND_EDIT,
    PC_STORAGE_VIEW_AND_EDIT,
    PARTY_BOX_MOVEMENT,
    MULTI_SELECT_OPERATIONS,
    DUPLICATE_DELETE_REORDER,
    LEVEL_AND_EXP_EDITING,
    POKEMON_DETAIL_EDITING,
    TRAINER_DATA,
    POKEDEX,
    ITEMS_AND_PC_ITEM_STORAGE,
    EVENTS,
    PSE_BANK_DEPOSIT_WITHDRAWAL,
    FORWARD_AND_BACKWARD_BANK_TRANSFERS,
    BACKUP_ROLLBACK_AND_INTEGRITY,
    CAPACITY_AND_FULL_BOX_HANDLING,
    STALE_SELECTION_PROTECTION,
    EXACT_PERSISTENCE_AND_REOPEN,
    LEGALISE_FOR_DESTINATION,

    ABILITIES,
    NATURES,
    HELD_ITEMS,
    GENDER_AND_SHINY_DATA,
    RIBBONS_AND_CONTEST_DATA,
    FRIENDSHIP_AND_POKERUS,
    MET_AND_ORIGIN_DATA,
    EGGS_AND_BREEDING,
    GEN3_MOVE_AND_ITEM_DATA,
    GEN3_ENCRYPTION_AND_CHECKSUMS
}

enum class Gen3TitleFamily {
    HOENN,
    KANTO_REMAKE
}

data class Gen3FeatureProfile(
    val title: SaveConverterGame,
    val family: Gen3TitleFamily,
    val features: Set<Gen3Feature>
) {
    fun supports(feature: Gen3Feature): Boolean = feature in features
}

object Gen3FeatureCatalog {
    private val inheritedBaseline = setOf(
        Gen3Feature.SAVE_DETECTION_AND_VALIDATION,
        Gen3Feature.PARTY_VIEW_AND_EDIT,
        Gen3Feature.PC_STORAGE_VIEW_AND_EDIT,
        Gen3Feature.PARTY_BOX_MOVEMENT,
        Gen3Feature.MULTI_SELECT_OPERATIONS,
        Gen3Feature.DUPLICATE_DELETE_REORDER,
        Gen3Feature.LEVEL_AND_EXP_EDITING,
        Gen3Feature.POKEMON_DETAIL_EDITING,
        Gen3Feature.TRAINER_DATA,
        Gen3Feature.POKEDEX,
        Gen3Feature.ITEMS_AND_PC_ITEM_STORAGE,
        Gen3Feature.EVENTS,
        Gen3Feature.PSE_BANK_DEPOSIT_WITHDRAWAL,
        Gen3Feature.FORWARD_AND_BACKWARD_BANK_TRANSFERS,
        Gen3Feature.BACKUP_ROLLBACK_AND_INTEGRITY,
        Gen3Feature.CAPACITY_AND_FULL_BOX_HANDLING,
        Gen3Feature.STALE_SELECTION_PROTECTION,
        Gen3Feature.EXACT_PERSISTENCE_AND_REOPEN,
        Gen3Feature.LEGALISE_FOR_DESTINATION
    )

    private val gen3Features = setOf(
        Gen3Feature.ABILITIES,
        Gen3Feature.NATURES,
        Gen3Feature.HELD_ITEMS,
        Gen3Feature.GENDER_AND_SHINY_DATA,
        Gen3Feature.RIBBONS_AND_CONTEST_DATA,
        Gen3Feature.FRIENDSHIP_AND_POKERUS,
        Gen3Feature.MET_AND_ORIGIN_DATA,
        Gen3Feature.EGGS_AND_BREEDING,
        Gen3Feature.GEN3_MOVE_AND_ITEM_DATA,
        Gen3Feature.GEN3_ENCRYPTION_AND_CHECKSUMS
    )

    private val profiles = mapOf(
        SaveConverterGame.RUBY to Gen3FeatureProfile(
            SaveConverterGame.RUBY,
            Gen3TitleFamily.HOENN,
            inheritedBaseline + gen3Features
        ),
        SaveConverterGame.SAPPHIRE to Gen3FeatureProfile(
            SaveConverterGame.SAPPHIRE,
            Gen3TitleFamily.HOENN,
            inheritedBaseline + gen3Features
        ),
        SaveConverterGame.EMERALD to Gen3FeatureProfile(
            SaveConverterGame.EMERALD,
            Gen3TitleFamily.HOENN,
            inheritedBaseline + gen3Features
        ),
        SaveConverterGame.FIRERED to Gen3FeatureProfile(
            SaveConverterGame.FIRERED,
            Gen3TitleFamily.KANTO_REMAKE,
            inheritedBaseline + gen3Features
        ),
        SaveConverterGame.LEAFGREEN to Gen3FeatureProfile(
            SaveConverterGame.LEAFGREEN,
            Gen3TitleFamily.KANTO_REMAKE,
            inheritedBaseline + gen3Features
        )
    )

    fun forTitle(title: SaveConverterGame): Gen3FeatureProfile? = profiles[title]
}
