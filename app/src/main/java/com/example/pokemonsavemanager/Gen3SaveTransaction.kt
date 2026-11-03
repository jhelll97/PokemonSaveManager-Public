package com.example.pokemonsavemanager

/**
 * Atomic Gen 3 save-state editing with rollback and validate-before-commit.
 *
 * The transaction is domain-level and keeps the last committed save state
 * untouched until commit() succeeds. Persistence adapters can serialize the
 * returned state after their own format-specific checks.
 */
class Gen3SaveTransaction(initial: Gen3SaveState) {
    private val original = initial
    private var working = initial

    fun snapshot(): Gen3SaveState = working

    fun isDirty(): Boolean = working != original

    fun apply(transform: (Gen3SaveState) -> Gen3SaveState): Gen3SaveTransaction {
        val candidate = transform(working)
        validate(candidate)
        working = candidate
        return this
    }

    fun rollback(): Gen3SaveState {
        working = original
        return working
    }

    fun commit(): Gen3SaveState {
        validate(working)
        return working
    }

    private fun validate(state: Gen3SaveState) {
        require(state.party.size <= Gen3SaveState.PARTY_CAPACITY) {
            "party exceeds the Gen 3 party capacity."
        }
        require(state.boxes.size == Gen3SaveState.BOX_COUNT) {
            "Gen 3 saves must contain exactly 14 boxes."
        }
        require(state.boxes.all { it.size <= Gen3SaveState.BOX_CAPACITY }) {
            "a Gen 3 PC box exceeds its 30-Pokémon capacity."
        }

        state.party.forEach { Gen3FeatureRuntime.requireUsable(state.title, it) }
        state.boxes.flatten().forEach { Gen3FeatureRuntime.requireUsable(state.title, it) }

        state.pokedexOwned.forEach { speciesId ->
            require(speciesId in 1..386) {
                "speciesId is outside the Gen 3 National Dex range."
            }
        }
        state.pcItems.forEach { item ->
            require(item.itemId in 1..376) {
                "itemId is outside the Gen 3 item range."
            }
            require(item.quantity in 1..99) {
                "PC item quantity must be between 1 and 99."
            }
        }
    }
}
