package com.example.pokemonsavemanager

/** Immutable Gen 3 party and PC box reordering operations. */
object Gen3Reordering {
    fun reorderParty(
        state: Gen3SaveState,
        fromIndex: Int,
        toIndex: Int
    ): Gen3SaveState {
        require(fromIndex in state.party.indices) { "fromIndex is outside the party." }
        require(toIndex in state.party.indices) { "toIndex is outside the party." }
        if (fromIndex == toIndex) return state

        val reordered = state.party.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)
        return state.copy(party = reordered)
    }

    fun reorderBox(
        state: Gen3SaveState,
        boxIndex: Int,
        fromSlot: Int,
        toSlot: Int
    ): Gen3SaveState {
        require(boxIndex in state.boxes.indices) { "boxIndex is outside the PC." }
        require(fromSlot in state.boxes[boxIndex].indices) {
            "fromSlot is outside the box."
        }
        require(toSlot in state.boxes[boxIndex].indices) {
            "toSlot is outside the box."
        }
        if (fromSlot == toSlot) return state

        val reordered = state.boxes[boxIndex].toMutableList()
        val moved = reordered.removeAt(fromSlot)
        reordered.add(toSlot, moved)
        val newBoxes = state.boxes.toMutableList()
        newBoxes[boxIndex] = reordered
        return state.copy(boxes = newBoxes)
    }
}
