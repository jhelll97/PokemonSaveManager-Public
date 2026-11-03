package com.example.pokemonsavemanager

/** Immutable Gen 3 PC item editing operations. */
object Gen3ItemEditing {
    fun setPcItem(
        state: Gen3SaveState,
        itemId: Int,
        quantity: Int
    ): Gen3SaveState =
        state.withPcItem(itemId, quantity)

    fun removePcItem(state: Gen3SaveState, itemId: Int): Gen3SaveState =
        Gen3SaveEditing.removePcItem(state, itemId)

    fun changePcItemQuantity(
        state: Gen3SaveState,
        itemId: Int,
        quantityChange: Int
    ): Gen3SaveState {
        val existing = state.pcItems.firstOrNull { it.itemId == itemId }
        val nextQuantity = (existing?.quantity ?: 0) + quantityChange
        return if (nextQuantity <= 0) {
            removePcItem(state, itemId)
        } else {
            require(nextQuantity <= 99) { "quantity must not exceed 99." }
            setPcItem(state, itemId, nextQuantity)
        }
    }
}