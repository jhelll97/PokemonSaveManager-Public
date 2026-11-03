package com.example.pokemonsavemanager

/** Immutable Gen 3 Pokémon editing operations used by the app UI. */
object Gen3PokemonEditing {
    fun withHeldItem(record: Gen3PokemonRecord, itemId: Int?): Gen3PokemonRecord =
        record.copy(heldItemId = itemId).requireValid()

    fun withoutHeldItem(record: Gen3PokemonRecord): Gen3PokemonRecord =
        record.copy(heldItemId = null).requireValid()

    fun rename(record: Gen3PokemonRecord, nickname: String): Gen3PokemonRecord =
        record.copy(nickname = nickname).requireValid()

    fun setLevel(record: Gen3PokemonRecord, level: Int, experience: Long): Gen3PokemonRecord =
        record.copy(level = level, experience = experience).requireValid()

    fun setFriendship(record: Gen3PokemonRecord, friendship: Int): Gen3PokemonRecord =
        record.copy(friendship = friendship).requireValid()

    fun addRibbon(record: Gen3PokemonRecord, ribbonId: Int): Gen3PokemonRecord =
        record.copy(ribbons = record.ribbons + ribbonId).requireValid()

    fun removeRibbon(record: Gen3PokemonRecord, ribbonId: Int): Gen3PokemonRecord =
        record.copy(ribbons = record.ribbons - ribbonId).requireValid()

    fun replaceMoves(
        record: Gen3PokemonRecord,
        moves: List<Gen3MoveSlot>
    ): Gen3PokemonRecord =
        record.copy(moves = moves).requireValid()
}