package com.example.pokemonsavemanager

/**
 * Concrete Gen 3 Pokémon state shared by Ruby, Sapphire, Emerald, FireRed and
 * LeafGreen adapters.
 *
 * This is deliberately save-format independent: adapters map these values to
 * each title's PK3 sections, then perform title-specific checksum and integrity
 * handling before persistence.
 */
data class Gen3PokemonRecord(
    val speciesId: Int,
    val nickname: String,
    val level: Int,
    val experience: Long,
    val abilityId: Int,
    val natureId: Int,
    val heldItemId: Int?,
    val gender: Gen3Gender,
    val isShiny: Boolean,
    val moves: List<Gen3MoveSlot>,
    val ribbons: Set<Int> = emptySet(),
    val contest: Gen3ContestState = Gen3ContestState(),
    val friendship: Int = 0,
    val pokerusDays: Int = 0,
    val met: Gen3MetData = Gen3MetData(),
    val breeding: Gen3BreedingState = Gen3BreedingState(),
    val ivs: Gen3StatValues = Gen3StatValues(),
    val evs: Gen3StatValues = Gen3StatValues()
) {
    fun validate(): List<String> = buildList {
        if (speciesId !in 1..386) add("speciesId must be in the Gen 3 National Dex range.")
        if (nickname.length > 10) add("nickname exceeds the Gen 3 limit of 10 characters.")
        if (level !in 1..100) add("level must be between 1 and 100.")
        if (experience < 0) add("experience cannot be negative.")
        if (abilityId !in 0..77) add("abilityId is outside the Gen 3 ability range.")
        if (natureId !in 0..24) add("natureId must be between 0 and 24.")
        if (heldItemId != null && heldItemId !in 1..376) {
            add("heldItemId is outside the Gen 3 item range.")
        }
        if (moves.size > 4) add("a Pokémon cannot have more than four moves.")
        moves.forEachIndexed { index, move ->
            validateMove(move, index)?.let(::add)
        }
        if (ribbons.any { it !in 1..80 }) add("ribbons contain an invalid Gen 3 ribbon id.")
        if (friendship !in 0..255) add("friendship must be between 0 and 255.")
        if (pokerusDays !in 0..15) add("pokerusDays must be between 0 and 15.")
        addAll(contest.validate())
        addAll(ivs.validate("IV"))
        addAll(evs.validate("EV"))
        addAll(met.validate())
        addAll(breeding.validate())
    }

    fun requireValid(): Gen3PokemonRecord {
        val errors = validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return this
    }

    private fun validateMove(move: Gen3MoveSlot, index: Int): String? {
        if (move.moveId !in 1..354) return "move $index has an invalid Gen 3 move id."
        if (move.pp !in 0..40) return "move $index has invalid PP."
        if (move.ppUps !in 0..3) return "move $index has invalid PP Ups."
        return null
    }
}

enum class Gen3Gender { MALE, FEMALE, GENDERLESS }

data class Gen3MoveSlot(
    val moveId: Int,
    val pp: Int,
    val ppUps: Int = 0
)

data class Gen3ContestState(
    val cool: Int = 0,
    val beauty: Int = 0,
    val cute: Int = 0,
    val smart: Int = 0,
    val tough: Int = 0,
    val sheen: Int = 0
) {
    fun validate(): List<String> = listOf(
        "cool" to cool,
        "beauty" to beauty,
        "cute" to cute,
        "smart" to smart,
        "tough" to tough,
        "sheen" to sheen
    ).mapNotNull { (name, value) ->
        if (value !in 0..255) "$name contest value must be between 0 and 255." else null
    }
}

data class Gen3MetData(
    val locationId: Int = 0,
    val levelMet: Int = 0,
    val game: SaveConverterGame? = null,
    val ballId: Int = 4
) {
    fun validate(): List<String> = buildList {
        if (locationId < 0) add("locationId cannot be negative.")
        if (levelMet !in 0..100) add("levelMet must be between 0 and 100.")
        if (game != null && Gen3FeatureCatalog.forTitle(game) == null) {
            add("met game must be a supported Gen 3 title.")
        }
        if (ballId !in 1..12) add("ballId is outside the Gen 3 ball range.")
    }
}

data class Gen3BreedingState(
    val isEgg: Boolean = false,
    val eggCyclesRemaining: Int = 0,
    val isNicknamed: Boolean = false
) {
    fun validate(): List<String> = buildList {
        if (eggCyclesRemaining !in 0..255) add("eggCyclesRemaining must be between 0 and 255.")
        if (!isEgg && eggCyclesRemaining != 0) {
            add("non-egg Pokémon cannot have remaining egg cycles.")
        }
    }
}

data class Gen3StatValues(
    val hp: Int = 0,
    val attack: Int = 0,
    val defense: Int = 0,
    val specialAttack: Int = 0,
    val specialDefense: Int = 0,
    val speed: Int = 0
) {
    fun validate(label: String): List<String> = listOf(
        label + " HP" to hp,
        label + " Attack" to attack,
        label + " Defense" to defense,
        label + " Special Attack" to specialAttack,
        label + " Special Defense" to specialDefense,
        label + " Speed" to speed
    ).mapNotNull { (name, value) ->
        val max = if (label == "IV") 15 else 255
        if (value !in 0..max) name + " must be between 0 and " + max + "." else null
    }

    fun total(): Int = hp + attack + defense + specialAttack + specialDefense + speed
}
