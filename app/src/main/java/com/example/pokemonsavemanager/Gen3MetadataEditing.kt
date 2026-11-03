package com.example.pokemonsavemanager

/** Immutable Gen 3 metadata editing operations. */
object Gen3MetadataEditing {
    fun setGender(record: Gen3PokemonRecord, gender: Gen3Gender): Gen3PokemonRecord =
        record.copy(gender = gender).requireValid()

    fun setShiny(record: Gen3PokemonRecord, shiny: Boolean): Gen3PokemonRecord =
        record.copy(isShiny = shiny).requireValid()

    fun setMetData(record: Gen3PokemonRecord, met: Gen3MetData): Gen3PokemonRecord =
        record.copy(met = met).requireValid()

    fun setEggState(
        record: Gen3PokemonRecord,
        isEgg: Boolean,
        cyclesRemaining: Int
    ): Gen3PokemonRecord =
        record.copy(
            breeding = record.breeding.copy(
                isEgg = isEgg,
                eggCyclesRemaining = if (isEgg) cyclesRemaining else 0
            )
        ).requireValid()

    fun setPokerusDays(record: Gen3PokemonRecord, days: Int): Gen3PokemonRecord =
        record.copy(pokerusDays = days).requireValid()
}