package com.example.pokemonsavemanager

/** Immutable Gen 3 progression-event flag editing. */
object Gen3EventEditing {
    fun setEvent(
        state: Gen3SaveState,
        event: Gen3SaveEvent,
        enabled: Boolean
    ): Gen3SaveState =
        if (enabled) {
            state.withEvent(event)
        } else {
            state.copy(eventFlags = state.eventFlags - event)
        }

    fun clearAll(state: Gen3SaveState): Gen3SaveState =
        state.copy(eventFlags = emptySet())
}
