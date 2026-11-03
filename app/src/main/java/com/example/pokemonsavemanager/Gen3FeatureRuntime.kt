package com.example.pokemonsavemanager

/** Runtime entry point for Gen 3 feature consumers. */
object Gen3FeatureRuntime {
    fun profileFor(title: SaveConverterGame): Gen3FeatureProfile =
        Gen3FeatureCatalog.forTitle(title)
            ?: error(title.toString() + " is not a supported Gen 3 title.")

    fun supports(title: SaveConverterGame, feature: Gen3Feature): Boolean =
        profileFor(title).supports(feature)

    fun validate(title: SaveConverterGame, record: Gen3PokemonRecord): List<String> {
        val profile = Gen3FeatureCatalog.forTitle(title)
        return buildList {
            if (profile == null) {
                add(title.toString() + " is not a supported Gen 3 title.")
            } else {
                addAll(record.validate())
                if (record.met.game != null && record.met.game != title) {
                    add("met game " + record.met.game + " does not match destination title " + title + ".")
                }
                if (record.breeding.isEgg && !profile.supports(Gen3Feature.EGGS_AND_BREEDING)) {
                    add("egg and breeding data is unavailable for " + title + ".")
                }
            }
        }
    }

    fun requireUsable(title: SaveConverterGame, record: Gen3PokemonRecord): Gen3PokemonRecord {
        val errors = validate(title, record)
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return record
    }

    fun availableFeatures(title: SaveConverterGame): Set<Gen3Feature> =
        profileFor(title).features
}