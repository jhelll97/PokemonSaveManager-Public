package com.example.pokemonsavemanager

/** Immutable Gen 3 save editing operations for party, PC and trainer data. */
object Gen3SaveEditing {
    fun replaceParty(
        state: Gen3SaveState,
        party: List<Gen3PokemonRecord>
    ): Gen3SaveState {
        require(party.size <= Gen3SaveState.PARTY_CAPACITY) {
            "party exceeds the Gen 3 party capacity."
        }
        party.forEach { it.requireValid() }
        return state.copy(party = party.toList())
    }

    fun replaceBox(
        state: Gen3SaveState,
        boxIndex: Int,
        pokemon: List<Gen3PokemonRecord>
    ): Gen3SaveState {
        require(boxIndex in state.boxes.indices) { "boxIndex is outside the PC." }
        require(pokemon.size <= Gen3SaveState.BOX_CAPACITY) {
            "box exceeds the Gen 3 PC box capacity."
        }
        pokemon.forEach { it.requireValid() }
        val updatedBoxes = state.boxes.toMutableList()
        updatedBoxes[boxIndex] = pokemon.toList()
        return state.copy(boxes = updatedBoxes)
    }

    fun replacePartyPokemon(
        state: Gen3SaveState,
        partyIndex: Int,
        pokemon: Gen3PokemonRecord
    ): Gen3SaveState {
        require(partyIndex in state.party.indices) { "partyIndex is outside the party." }
        val updated = state.party.toMutableList()
        updated[partyIndex] = pokemon.requireValid()
        return state.copy(party = updated)
    }

    fun replaceBoxPokemon(
        state: Gen3SaveState,
        boxIndex: Int,
        boxSlot: Int,
        pokemon: Gen3PokemonRecord
    ): Gen3SaveState {
        require(boxIndex in state.boxes.indices) { "boxIndex is outside the PC." }
        require(boxSlot in state.boxes[boxIndex].indices) { "boxSlot is outside the box." }
        val updatedBoxes = state.boxes.toMutableList()
        val updatedBox = updatedBoxes[boxIndex].toMutableList()
        updatedBox[boxSlot] = pokemon.requireValid()
        updatedBoxes[boxIndex] = updatedBox
        return state.copy(boxes = updatedBoxes)
    }

    fun movePartyPokemon(
        state: Gen3SaveState,
        fromIndex: Int,
        toIndex: Int
    ): Gen3SaveState {
        require(fromIndex in state.party.indices) { "fromIndex is outside the party." }
        require(toIndex in state.party.indices) { "toIndex is outside the party." }
        if (fromIndex == toIndex) return state
        val updated = state.party.toMutableList()
        val moved = updated.removeAt(fromIndex)
        updated.add(toIndex, moved)
        return state.copy(party = updated)
    }

    fun moveBoxPokemon(
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
        val updatedBox = state.boxes[boxIndex].toMutableList()
        val moved = updatedBox.removeAt(fromSlot)
        updatedBox.add(toSlot, moved)
        val updatedBoxes = state.boxes.toMutableList()
        updatedBoxes[boxIndex] = updatedBox
        return state.copy(boxes = updatedBoxes)
    }

    fun movePartyToBox(
        state: Gen3SaveState,
        partyIndex: Int,
        boxIndex: Int
    ): Gen3SaveState = state.movePartyToBox(partyIndex, boxIndex)

    fun moveBoxToParty(
        state: Gen3SaveState,
        boxIndex: Int,
        boxSlot: Int
    ): Gen3SaveState = state.moveBoxToParty(boxIndex, boxSlot)

    fun removePartyPokemon(state: Gen3SaveState, partyIndex: Int): Gen3SaveState {
        require(partyIndex in state.party.indices) { "partyIndex is outside the party." }
        return state.copy(party = state.party.toMutableList().also { it.removeAt(partyIndex) })
    }

    fun removeBoxPokemon(
        state: Gen3SaveState,
        boxIndex: Int,
        boxSlot: Int
    ): Gen3SaveState {
        require(boxIndex in state.boxes.indices) { "boxIndex is outside the PC." }
        require(boxSlot in state.boxes[boxIndex].indices) { "boxSlot is outside the box." }
        val updatedBoxes = state.boxes.toMutableList()
        updatedBoxes[boxIndex] = state.boxes[boxIndex].toMutableList().also { it.removeAt(boxSlot) }
        return state.copy(boxes = updatedBoxes)
    }

    fun updateTrainer(state: Gen3SaveState, trainer: Gen3TrainerState): Gen3SaveState =
        state.copy(trainer = trainer)

    fun updateTrainerName(state: Gen3SaveState, name: String): Gen3SaveState {
        require(name.length in 1..7) {
            "Gen 3 trainer names must contain between 1 and 7 characters."
        }
        return state.copy(trainer = state.trainer.copy(name = name))
    }

    fun updateTrainerMoney(state: Gen3SaveState, money: Int): Gen3SaveState {
        require(money in 0..999_999) {
            "Gen 3 trainer money must be between 0 and 999999."
        }
        return state.copy(trainer = state.trainer.copy(money = money))
    }

    fun updateTrainerIds(
        state: Gen3SaveState,
        trainerId: Int,
        secretId: Int
    ): Gen3SaveState {
        require(trainerId in 0..65_535) {
            "trainerId must fit the Gen 3 16-bit trainer ID range."
        }
        require(secretId in 0..65_535) {
            "secretId must fit the Gen 3 16-bit secret ID range."
        }
        return state.copy(
            trainer = state.trainer.copy(trainerId = trainerId, secretId = secretId)
        )
    }

    fun markSpeciesSeen(state: Gen3SaveState, speciesId: Int): Gen3SaveState =
        state.withSeenSpecies(speciesId)

    fun markSpeciesOwned(state: Gen3SaveState, speciesId: Int): Gen3SaveState =
        state.withOwnedSpecies(speciesId)

    fun removeOwnedSpecies(state: Gen3SaveState, speciesId: Int): Gen3SaveState =
        state.removeOwnedSpecies(speciesId)

    fun clearPokedex(state: Gen3SaveState): Gen3SaveState =
        state.clearPokedex()

    fun removePcItem(state: Gen3SaveState, itemId: Int): Gen3SaveState =
        state.copy(pcItems = state.pcItems.filterNot { it.itemId == itemId })

    fun removeBagItem(state: Gen3SaveState, itemId: Int): Gen3SaveState =
        state.removeBagItem(itemId)

    fun addPcItem(
        state: Gen3SaveState,
        itemId: Int,
        quantity: Int
    ): Gen3SaveState = state.withPcItem(itemId, quantity)

    fun addBagItem(
        state: Gen3SaveState,
        itemId: Int,
        quantity: Int
    ): Gen3SaveState = state.withBagItem(itemId, quantity)

    fun changePcItemQuantity(
        state: Gen3SaveState,
        itemId: Int,
        quantity: Int
    ): Gen3SaveState {
        require(itemId in 1..376) { "itemId is outside the Gen 3 item range." }
        require(quantity in 1..99) { "quantity must be between 1 and 99." }
        val itemIndex = state.pcItems.indexOfFirst { it.itemId == itemId }
        require(itemIndex >= 0) { "item is not present in PC storage." }
        val updated = state.pcItems.toMutableList()
        updated[itemIndex] = Gen3ItemStack(itemId, quantity)
        return state.copy(pcItems = updated)
    }

    fun changeBagItemQuantity(
        state: Gen3SaveState,
        itemId: Int,
        quantity: Int
    ): Gen3SaveState = state.changeBagItemQuantity(itemId, quantity)

    fun moveBagItemToPc(state: Gen3SaveState, itemId: Int): Gen3SaveState =
        state.moveBagItemToPc(itemId)

    fun movePcItemToBag(state: Gen3SaveState, itemId: Int): Gen3SaveState =
        state.movePcItemToBag(itemId)

    fun clearEvent(state: Gen3SaveState, event: Gen3SaveEvent): Gen3SaveState =
        state.copy(eventFlags = state.eventFlags - event)

    fun clearEvents(state: Gen3SaveState): Gen3SaveState =
        state.copy(eventFlags = emptySet())

    fun renameBox(state: Gen3SaveState, boxIndex: Int, name: String): Gen3SaveState =
        state.renameBox(boxIndex, name)

    fun resetBoxName(state: Gen3SaveState, boxIndex: Int): Gen3SaveState =
        state.resetBoxName(boxIndex)

    fun replaceEvents(
        state: Gen3SaveState,
        events: Set<Gen3SaveEvent>
    ): Gen3SaveState = state.copy(eventFlags = events)
}
