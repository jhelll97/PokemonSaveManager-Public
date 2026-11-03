package com.example.pokemonsavemanager

/** Immutable Gen 3 Seen/Owned Pokédex state. */
data class Gen3PokedexState(
    val seen: Set<Int> = emptySet(),
    val owned: Set<Int> = emptySet()
) {
    init {
        require(seen.all(::isGen3Species))
        require(owned.all(::isGen3Species))
        require(owned.all { it in seen })
    }

    fun markSeen(speciesId: Int): Gen3PokedexState {
        require(isGen3Species(speciesId)) { "speciesId is outside the Gen 3 National Dex range." }
        return copy(seen = seen + speciesId)
    }

    fun markOwned(speciesId: Int): Gen3PokedexState {
        require(isGen3Species(speciesId)) { "speciesId is outside the Gen 3 National Dex range." }
        return copy(seen = seen + speciesId, owned = owned + speciesId)
    }

    fun removeOwned(speciesId: Int): Gen3PokedexState =
        copy(owned = owned - speciesId)

    fun clear(): Gen3PokedexState = Gen3PokedexState()

    private fun isGen3Species(speciesId: Int): Boolean = speciesId in 1..386
}