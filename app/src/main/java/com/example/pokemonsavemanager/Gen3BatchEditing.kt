package com.example.pokemonsavemanager

/** Multi-select Gen 3 party and PC operations with capacity-safe updates. */
object Gen3BatchEditing {
    fun movePartyToBox(
        state: Gen3SaveState,
        partyIndices: Set<Int>,
        boxIndex: Int
    ): Gen3SaveState {
        require(partyIndices.isNotEmpty()) { "select at least one party Pokémon." }
        require(boxIndex in state.boxes.indices) { "boxIndex is outside the PC." }
        require(partyIndices.all { it in state.party.indices }) {
            "party selection contains an invalid index."
        }
        require(state.boxes[boxIndex].size + partyIndices.size <= Gen3SaveState.BOX_CAPACITY) {
            "destination box does not have enough capacity."
        }

        val selected = partyIndices.sorted().map { state.party[it].requireValid() }
        val newParty = state.party.filterIndexed { index, _ -> index !in partyIndices }
        val newBoxes = state.boxes.toMutableList()
        newBoxes[boxIndex] = state.boxes[boxIndex] + selected
        return state.copy(party = newParty, boxes = newBoxes)
    }

    fun moveBoxToParty(
        state: Gen3SaveState,
        boxIndex: Int,
        boxSlots: Set<Int>
    ): Gen3SaveState {
        require(boxSlots.isNotEmpty()) { "select at least one box Pokémon." }
        require(boxIndex in state.boxes.indices) { "boxIndex is outside the PC." }
        require(boxSlots.all { it in state.boxes[boxIndex].indices }) {
            "box selection contains an invalid slot."
        }
        require(state.party.size + boxSlots.size <= Gen3SaveState.PARTY_CAPACITY) {
            "party does not have enough capacity."
        }

        val selected = boxSlots.sorted().map { state.boxes[boxIndex][it].requireValid() }
        val newBoxes = state.boxes.toMutableList()
        newBoxes[boxIndex] = state.boxes[boxIndex].filterIndexed { index, _ -> index !in boxSlots }
        return state.copy(party = state.party + selected, boxes = newBoxes)
    }

    fun removeParty(
        state: Gen3SaveState,
        partyIndices: Set<Int>
    ): Gen3SaveState {
        require(partyIndices.isNotEmpty()) { "select at least one party Pokémon." }
        require(partyIndices.all { it in state.party.indices }) {
            "party selection contains an invalid index."
        }
        return state.copy(
            party = state.party.filterIndexed { index, _ -> index !in partyIndices }
        )
    }

    fun removeFromBox(
        state: Gen3SaveState,
        boxIndex: Int,
        boxSlots: Set<Int>
    ): Gen3SaveState {
        require(boxSlots.isNotEmpty()) { "select at least one box Pokémon." }
        require(boxIndex in state.boxes.indices) { "boxIndex is outside the PC." }
        require(boxSlots.all { it in state.boxes[boxIndex].indices }) {
            "box selection contains an invalid slot."
        }
        val newBoxes = state.boxes.toMutableList()
        newBoxes[boxIndex] =
            state.boxes[boxIndex].filterIndexed { index, _ -> index !in boxSlots }
        return state.copy(boxes = newBoxes)
    }
}
