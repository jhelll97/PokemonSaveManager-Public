package com.example.pokemonsavemanager

/** Gen 3 egg and breeding-state editing operations. */
object Gen3BreedingEditing {
    fun setEggCycles(
        record: Gen3PokemonRecord,
        cyclesRemaining: Int
    ): Gen3PokemonRecord {
        require(record.breeding.isEgg) { "only an egg can have remaining egg cycles." }
        return record.copy(
            breeding = record.breeding.copy(eggCyclesRemaining = cyclesRemaining)
        ).requireValid()
    }

    fun advanceEggCycles(
        record: Gen3PokemonRecord,
        cycles: Int
    ): Gen3PokemonRecord {
        require(cycles >= 0) { "cycles cannot be negative." }
        if (!record.breeding.isEgg) return record

        val remaining = (record.breeding.eggCyclesRemaining - cycles).coerceAtLeast(0)
        return if (remaining == 0) {
            hatch(record)
        } else {
            record.copy(
                breeding = record.breeding.copy(eggCyclesRemaining = remaining)
            ).requireValid()
        }
    }

    fun hatch(record: Gen3PokemonRecord): Gen3PokemonRecord =
        record.copy(
            breeding = record.breeding.copy(
                isEgg = false,
                eggCyclesRemaining = 0
            )
        ).requireValid()

    fun setNicknamed(
        record: Gen3PokemonRecord,
        nicknamed: Boolean
    ): Gen3PokemonRecord =
        record.copy(
            breeding = record.breeding.copy(isNicknamed = nicknamed)
        ).requireValid()
}
