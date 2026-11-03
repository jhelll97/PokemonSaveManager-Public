package com.example.pokemonsavemanager

/**
 * Save-wide Gen 3 usability validation for UI and persistence boundaries.
 *
 * This is a feature gate, not a test harness: callers can inspect all errors
 * before writing a save, or use requireUsable to fail closed.
 */
object Gen3SaveValidation {
    fun validate(state: Gen3SaveState): List<String> = buildList {
        if (Gen3FeatureCatalog.forTitle(state.title) == null) {
            add("title is not a supported Gen 3 game.")
        }
        if (state.party.size > Gen3SaveState.PARTY_CAPACITY) {
            add("party exceeds the Gen 3 capacity of ${Gen3SaveState.PARTY_CAPACITY}.")
        }
        if (state.boxes.size != Gen3SaveState.BOX_COUNT) {
            add("PC must contain exactly ${Gen3SaveState.BOX_COUNT} boxes.")
        }
        state.boxes.forEachIndexed { boxIndex, box ->
            if (box.size > Gen3SaveState.BOX_CAPACITY) {
                add("box[$boxIndex] exceeds the capacity of ${Gen3SaveState.BOX_CAPACITY}.")
            }
        }

        state.party.forEachIndexed { index, pokemon ->
            pokemon.validate().forEach { add("party[$index]: $it") }
        }
        state.boxes.forEachIndexed { boxIndex, box ->
            box.forEachIndexed { slot, pokemon ->
                pokemon.validate().forEach { add("box[$boxIndex][$slot]: $it") }
            }
        }

        if (state.pokedexOwned.any { it !in state.pokedexSeen }) {
            add("owned Pokédex entries must also be marked seen.")
        }
        validateItems("PC", state.pcItems)
        validateItems("bag", state.bagItems)

        val trainer = state.trainer
        if (trainer.name.length > 7) {
            add("trainer name exceeds the Gen 3 limit of 7 characters.")
        }
        if (trainer.trainerId !in 0..65535) {
            add("trainerId must be a 16-bit unsigned value.")
        }
        if (trainer.secretId !in 0..65535) {
            add("secretId must be a 16-bit unsigned value.")
        }
        if (trainer.money !in 0..999999) {
            add("money must be between 0 and 999999.")
        }
    }

    fun requireUsable(state: Gen3SaveState): Gen3SaveState {
        val errors = validate(state)
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return state
    }

    private fun MutableList<String>.validateItems(
        storageName: String,
        items: List<Gen3ItemStack>
    ) {
        items.forEachIndexed { index, item ->
            if (item.itemId !in 1..376) {
                add("$storageName[$index] has an invalid Gen 3 item id.")
            }
            if (item.quantity !in 1..99) {
                add("$storageName[$index] quantity must be between 1 and 99.")
            }
        }
        items.groupBy { it.itemId }
            .filterValues { it.size > 1 }
            .keys
            .forEach { itemId ->
                add("$storageName contains duplicate item id $itemId.")
            }
    }
}
