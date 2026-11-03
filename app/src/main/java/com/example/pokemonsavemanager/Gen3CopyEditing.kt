package com.example.pokemonsavemanager

/** Safe Gen 3 Pokémon copy/duplicate operations that leave the source intact. */
object Gen3CopyEditing {
    fun duplicatePartyToBox(
        state: Gen3SaveState,
        partyIndex: Int,
        boxIndex: Int
    ): Gen3SaveState {
        require(partyIndex in state.party.indices) { "partyIndex is outside the party." }
        require(boxIndex in state.boxes.indices) { "boxIndex is outside the PC." }
        require(state.boxes[boxIndex].size < Gen3SaveState.BOX_CAPACITY) {
            "destination box is full."
        }
        val copy = state.party[partyIndex].requireValid()
        val newBoxes = state.boxes.toMutableList()
        newBoxes[boxIndex] = state.boxes[boxIndex] + copy
        return state.copy(boxes = newBoxes)
    }

    fun duplicateBoxToParty(
        state: Gen3SaveState,
        boxIndex: Int,
        boxSlot: Int
    ): Gen3SaveState {
        require(boxIndex in state.boxes.indices) { "boxIndex is outside the PC." }
        require(boxSlot in state.boxes[boxIndex].indices) {
            "boxSlot is outside the box."
        }
        require(state.party.size < Gen3SaveState.PARTY_CAPACITY) {
            "party is full."
        }
        val copy = state.boxes[boxIndex][boxSlot].requireValid()
        return state.copy(party = state.party + copy)
    }

    fun duplicateBoxToBox(
        state: Gen3SaveState,
        sourceBoxIndex: Int,
        sourceSlot: Int,
        destinationBoxIndex: Int
    ): Gen3SaveState {
        require(sourceBoxIndex in state.boxes.indices) {
            "sourceBoxIndex is outside the PC."
        }
        require(destinationBoxIndex in state.boxes.indices) {
            "destinationBoxIndex is outside the PC."
        }
        require(sourceSlot in state.boxes[sourceBoxIndex].indices) {
            "sourceSlot is outside the source box."
        }
        require(state.boxes[destinationBoxIndex].size < Gen3SaveState.BOX_CAPACITY) {
            "destination box is full."
        }
        val copy = state.boxes[sourceBoxIndex][sourceSlot].requireValid()
        val newBoxes = state.boxes.toMutableList()
        newBoxes[destinationBoxIndex] = state.boxes[destinationBoxIndex] + copy
        return state.copy(boxes = newBoxes)
    }
}
