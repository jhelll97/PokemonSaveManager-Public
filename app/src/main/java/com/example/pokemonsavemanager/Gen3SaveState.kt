package com.example.pokemonsavemanager

/**
 * Usable Gen 3 save state for party, PC, trainer, Pokédex, items and events.
 *
 * Operations return a new state so callers can keep the original save as the
 * rollback copy until the destination adapter has persisted and verified it.
 */
data class Gen3SaveState(
    val title: SaveConverterGame,
    val trainer: Gen3TrainerState = Gen3TrainerState(),
    val party: List<Gen3PokemonRecord> = emptyList(),
    val boxes: List<List<Gen3PokemonRecord>> = List(14) { emptyList() },
    val pokedexSeen: Set<Int> = emptySet(),
    val pokedexOwned: Set<Int> = emptySet(),
    val pcItems: List<Gen3ItemStack> = emptyList(),
    val bagItems: List<Gen3ItemStack> = emptyList(),
    val eventFlags: Set<Gen3SaveEvent> = emptySet(),
    val boxNames: List<String> = List(14) { index -> "Box " + (index + 1) }
) {
    init {
        require(Gen3FeatureRuntime.isSupportedTitle(title))
        require(party.size <= PARTY_CAPACITY)
        require(boxes.size == BOX_COUNT)
        require(boxes.all { it.size <= BOX_CAPACITY })
        require(boxNames.size == BOX_COUNT)
        require(boxNames.all { it.length in 1..8 })
        require(pokedexSeen.all(::isGen3Species))
        require(pokedexOwned.all(::isGen3Species))
        require(pokedexOwned.all { it in pokedexSeen })
    }

    fun movePartyToBox(partyIndex: Int, boxIndex: Int): Gen3SaveState {
        require(partyIndex in party.indices) { "partyIndex is outside the party." }
        require(boxIndex in boxes.indices) { "boxIndex is outside the PC." }
        require(boxes[boxIndex].size < BOX_CAPACITY) { "destination box is full." }
        val moved = party[partyIndex].requireValid()
        val newParty = party.toMutableList().also { it.removeAt(partyIndex) }
        val newBoxes = boxes.toMutableList()
        newBoxes[boxIndex] = boxes[boxIndex] + moved
        return copy(party = newParty, boxes = newBoxes)
    }

    fun moveBoxToParty(boxIndex: Int, boxSlot: Int): Gen3SaveState {
        require(boxIndex in boxes.indices) { "boxIndex is outside the PC." }
        require(boxSlot in boxes[boxIndex].indices) { "boxSlot is outside the box." }
        require(party.size < PARTY_CAPACITY) { "party is full." }
        val moved = boxes[boxIndex][boxSlot].requireValid()
        val newBoxes = boxes.toMutableList()
        newBoxes[boxIndex] = boxes[boxIndex].toMutableList().also { it.removeAt(boxSlot) }
        return copy(party = party + moved, boxes = newBoxes)
    }

    fun withSeenSpecies(speciesId: Int): Gen3SaveState {
        require(isGen3Species(speciesId)) {
            "speciesId is outside the Gen 3 National Dex range."
        }
        return copy(pokedexSeen = pokedexSeen + speciesId)
    }

    fun withOwnedSpecies(speciesId: Int): Gen3SaveState {
        require(isGen3Species(speciesId)) {
            "speciesId is outside the Gen 3 National Dex range."
        }
        return copy(
            pokedexSeen = pokedexSeen + speciesId,
            pokedexOwned = pokedexOwned + speciesId
        )
    }

    fun removeOwnedSpecies(speciesId: Int): Gen3SaveState =
        copy(pokedexOwned = pokedexOwned - speciesId)

    fun clearPokedex(): Gen3SaveState =
        copy(pokedexSeen = emptySet(), pokedexOwned = emptySet())

    fun withPcItem(itemId: Int, quantity: Int): Gen3SaveState {
        require(itemId in 1..376) { "itemId is outside the Gen 3 item range." }
        require(quantity in 1..99) { "quantity must be between 1 and 99." }
        val remaining = pcItems.filterNot { it.itemId == itemId }
        return copy(pcItems = remaining + Gen3ItemStack(itemId, quantity))
    }

    fun withBagItem(itemId: Int, quantity: Int): Gen3SaveState {
        require(itemId in 1..376) { "itemId is outside the Gen 3 item range." }
        require(quantity in 1..99) { "quantity must be between 1 and 99." }
        val remaining = bagItems.filterNot { it.itemId == itemId }
        return copy(bagItems = remaining + Gen3ItemStack(itemId, quantity))
    }

    fun removeBagItem(itemId: Int): Gen3SaveState =
        copy(bagItems = bagItems.filterNot { it.itemId == itemId })

    fun changeBagItemQuantity(itemId: Int, quantity: Int): Gen3SaveState {
        require(itemId in 1..376) { "itemId is outside the Gen 3 item range." }
        require(quantity in 1..99) { "quantity must be between 1 and 99." }
        val itemIndex = bagItems.indexOfFirst { it.itemId == itemId }
        require(itemIndex >= 0) { "item is not present in the bag." }
        val updated = bagItems.toMutableList()
        updated[itemIndex] = Gen3ItemStack(itemId, quantity)
        return copy(bagItems = updated)
    }

    fun moveBagItemToPc(itemId: Int): Gen3SaveState {
        val moved = bagItems.firstOrNull { it.itemId == itemId }
            ?: error("item is not present in the bag.")
        val newPcItems = pcItems.filterNot { it.itemId == itemId } + moved
        return copy(
            bagItems = bagItems.filterNot { it.itemId == itemId },
            pcItems = newPcItems
        )
    }

    fun movePcItemToBag(itemId: Int): Gen3SaveState {
        val moved = pcItems.firstOrNull { it.itemId == itemId }
            ?: error("item is not present in PC storage.")
        val newBagItems = bagItems.filterNot { it.itemId == itemId } + moved
        return copy(
            pcItems = pcItems.filterNot { it.itemId == itemId },
            bagItems = newBagItems
        )
    }

    fun withEvent(event: Gen3SaveEvent): Gen3SaveState =
        copy(eventFlags = eventFlags + event)

    fun hasEvent(event: Gen3SaveEvent): Boolean = event in eventFlags

    fun clearEvent(event: Gen3SaveEvent): Gen3SaveState =
        copy(eventFlags = eventFlags - event)

    fun clearEvents(): Gen3SaveState =
        copy(eventFlags = emptySet())

    fun renameBox(boxIndex: Int, name: String): Gen3SaveState {
        require(boxIndex in boxes.indices) { "boxIndex is outside the PC." }
        require(name.length in 1..8) {
            "Gen 3 PC box names must contain between 1 and 8 characters."
        }
        val updatedNames = boxNames.toMutableList()
        updatedNames[boxIndex] = name
        return copy(boxNames = updatedNames)
    }

    fun resetBoxName(boxIndex: Int): Gen3SaveState {
        require(boxIndex in boxes.indices) { "boxIndex is outside the PC." }
        val updatedNames = boxNames.toMutableList()
        updatedNames[boxIndex] = "Box " + (boxIndex + 1)
        return copy(boxNames = updatedNames)
    }

    private fun isGen3Species(speciesId: Int): Boolean = speciesId in 1..386

    companion object {
        const val PARTY_CAPACITY = 6
        const val BOX_COUNT = 14
        const val BOX_CAPACITY = 30
    }
}

data class Gen3TrainerState(
    val name: String = "",
    val trainerId: Int = 0,
    val secretId: Int = 0,
    val money: Int = 0,
    val gender: Int = 0,
    val playedHours: Int = 0,
    val playedMinutes: Int = 0,
    val playedSeconds: Int = 0,
    val playedFrames: Int = 0
) {
    init {
        require(name.length <= 7)
        require(trainerId in 0..65_535)
        require(secretId in 0..65_535)
        require(money in 0..999_999)
        require(gender in 0..1)
        require(playedHours in 0..999)
        require(playedMinutes in 0..59)
        require(playedSeconds in 0..59)
        require(playedFrames in 0..59)
    }
}

data class Gen3ItemStack(val itemId: Int, val quantity: Int)

enum class Gen3SaveEvent {
    INTRO_COMPLETE,
    POKEDEX_RECEIVED,
    NATIONAL_DEX_UNLOCKED,
    CONTEST_HALL_UNLOCKED,
    BATTLE_FRONTIER_UNLOCKED
}

private fun Gen3FeatureRuntime.isSupportedTitle(title: SaveConverterGame): Boolean =
    Gen3FeatureRuntime.supports(title, Gen3Feature.SAVE_DETECTION_AND_VALIDATION)
