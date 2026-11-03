package com.example.pokemonsavemanager

/** Immutable Gen 3 move-slot editing with Gen 3 bounds preserved. */
object Gen3MoveEditing {
    fun setMove(
        record: Gen3PokemonRecord,
        slotIndex: Int,
        move: Gen3MoveSlot
    ): Gen3PokemonRecord {
        require(slotIndex in 0..3) { "slotIndex must be between 0 and 3." }
        val moves = record.moves.toMutableList()
        if (slotIndex == moves.size) {
            moves.add(move)
        } else {
            require(slotIndex in moves.indices) { "slotIndex is not populated." }
            moves[slotIndex] = move
        }
        return record.copy(moves = moves).requireValid()
    }

    fun removeMove(
        record: Gen3PokemonRecord,
        slotIndex: Int
    ): Gen3PokemonRecord {
        require(slotIndex in record.moves.indices) { "slotIndex is not populated." }
        val moves = record.moves.toMutableList()
        moves.removeAt(slotIndex)
        return record.copy(moves = moves).requireValid()
    }

    fun updateMovePp(
        record: Gen3PokemonRecord,
        slotIndex: Int,
        pp: Int,
        ppUps: Int = record.moves[slotIndex].ppUps
    ): Gen3PokemonRecord {
        require(slotIndex in record.moves.indices) { "slotIndex is not populated." }
        val current = record.moves[slotIndex]
        val moves = record.moves.toMutableList()
        moves[slotIndex] = current.copy(pp = pp, ppUps = ppUps)
        return record.copy(moves = moves).requireValid()
    }
}
