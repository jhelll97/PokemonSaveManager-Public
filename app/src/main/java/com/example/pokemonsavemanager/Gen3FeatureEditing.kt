package com.example.pokemonsavemanager

/** Immutable Gen 3 ability, nature and contest-data editing operations. */
object Gen3FeatureEditing {
    fun setAbility(record: Gen3PokemonRecord, abilityId: Int): Gen3PokemonRecord =
        record.copy(abilityId = abilityId).requireValid()

    fun setNature(record: Gen3PokemonRecord, natureId: Int): Gen3PokemonRecord =
        record.copy(natureId = natureId).requireValid()

    fun setContestData(
        record: Gen3PokemonRecord,
        contest: Gen3ContestState
    ): Gen3PokemonRecord =
        record.copy(contest = contest).requireValid()
}