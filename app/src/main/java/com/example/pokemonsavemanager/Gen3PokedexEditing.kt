package com.example.pokemonsavemanager

data class Gen3PokedexChangeSummary(
    val registered: Int,
    val cleared: Int
) {
    val total: Int get() = registered + cleared
}

data class Gen3PokedexChange(
    val dexNumber: Int,
    val willBeRegistered: Boolean
)

enum class Gen3PokedexChangeDirection {
    REGISTER,
    CLEAR
}

enum class Gen3PokedexRegistrationStatus {
    REGISTERED,
    UNREGISTERED,
    PENDING_REGISTER,
    PENDING_CLEAR
}

enum class Gen3PokedexGenerationFilter {
    ALL,
    GEN1,
    GEN2,
    GEN3
}

enum class Gen3PokedexSortOrder {
    NATIONAL_DEX,
    SPECIES_NAME
}

/** Immutable helpers for staged Gen 3 Pokédex UI edits. */
object Gen3PokedexEditing {
    private const val NATIONAL_DEX_SIZE = 386

    fun speciesName(
        dexNumber: Int,
        nationalNames: List<String>
    ): String {
        require(dexNumber in 1..NATIONAL_DEX_SIZE) {
            "Gen 3 National Dex number must be between 1 and 386."
        }
        require(nationalNames.size == NATIONAL_DEX_SIZE) {
            "Gen 3 species names must contain exactly 386 entries."
        }
        return nationalNames[dexNumber - 1].also { name ->
            require(name.isNotBlank()) { "Gen 3 species name must not be blank." }
        }
    }

    fun orderedDexNumbers(
        nationalNames: List<String>,
        sortOrder: Gen3PokedexSortOrder
    ): List<Int> {
        require(nationalNames.size == NATIONAL_DEX_SIZE) {
            "Gen 3 species names must contain exactly 386 entries."
        }
        val dexNumbers = (1..NATIONAL_DEX_SIZE).toList()
        nationalNames.forEachIndexed { index, _ ->
            speciesName(index + 1, nationalNames)
        }
        return when (sortOrder) {
            Gen3PokedexSortOrder.NATIONAL_DEX -> dexNumbers
            Gen3PokedexSortOrder.SPECIES_NAME -> dexNumbers.sortedWith(
                compareBy<Int> { speciesName(it, nationalNames) }
                    .thenBy { it }
            )
        }
    }

    fun matchesGeneration(
        dexNumber: Int,
        filter: Gen3PokedexGenerationFilter
    ): Boolean {
        require(dexNumber in 1..NATIONAL_DEX_SIZE) {
            "Gen 3 National Dex number must be between 1 and 386."
        }
        return when (filter) {
            Gen3PokedexGenerationFilter.ALL -> true
            Gen3PokedexGenerationFilter.GEN1 -> dexNumber in 1..151
            Gen3PokedexGenerationFilter.GEN2 -> dexNumber in 152..251
            Gen3PokedexGenerationFilter.GEN3 -> dexNumber in 252..386
        }
    }

    fun restoreLoaded(loaded: BooleanArray): BooleanArray {
        require(loaded.size == NATIONAL_DEX_SIZE) {
            "Gen 3 Pokédex registration must contain exactly 386 flags."
        }
        return loaded.copyOf()
    }

    fun restoreLoadedAtIndices(
        loaded: BooleanArray,
        staged: BooleanArray,
        indices: Collection<Int>
    ): BooleanArray {
        require(loaded.size == NATIONAL_DEX_SIZE && staged.size == NATIONAL_DEX_SIZE) {
            "Gen 3 Pokédex registration must contain exactly 386 flags."
        }
        val restored = staged.copyOf()
        indices.forEach { index ->
            require(index in loaded.indices) { "Gen 3 Pokédex index must be between 0 and 385." }
            restored[index] = loaded[index]
        }
        return restored
    }

    fun invertAtIndices(
        staged: BooleanArray,
        indices: Collection<Int>
    ): BooleanArray {
        require(staged.size == NATIONAL_DEX_SIZE) {
            "Gen 3 Pokédex registration must contain exactly 386 flags."
        }
        val inverted = staged.copyOf()
        indices.toSet().forEach { index ->
            require(index in staged.indices) { "Gen 3 Pokédex index must be between 0 and 385." }
            inverted[index] = !inverted[index]
        }
        return inverted
    }

    fun restoreChangesByDirection(
        loaded: BooleanArray,
        staged: BooleanArray,
        direction: Gen3PokedexChangeDirection
    ): BooleanArray {
        require(loaded.size == NATIONAL_DEX_SIZE && staged.size == NATIONAL_DEX_SIZE) {
            "Gen 3 Pokédex registration must contain exactly 386 flags."
        }
        val restored = staged.copyOf()
        loaded.indices.forEach { index ->
            val matchesDirection = when (direction) {
                Gen3PokedexChangeDirection.REGISTER -> !loaded[index] && staged[index]
                Gen3PokedexChangeDirection.CLEAR -> loaded[index] && !staged[index]
            }
            if (matchesDirection) restored[index] = loaded[index]
        }
        return restored
    }

    fun undoActionIfCurrent(
        current: BooleanArray,
        previous: BooleanArray,
        expectedCurrent: BooleanArray
    ): BooleanArray? {
        require(
            current.size == NATIONAL_DEX_SIZE &&
                previous.size == NATIONAL_DEX_SIZE &&
                expectedCurrent.size == NATIONAL_DEX_SIZE
        ) {
            "Gen 3 Pokédex registration must contain exactly 386 flags."
        }
        if (!current.contentEquals(expectedCurrent)) return null
        return previous.copyOf()
    }

    fun redoActionIfPrevious(
        current: BooleanArray,
        previous: BooleanArray,
        next: BooleanArray
    ): BooleanArray? = undoActionIfCurrent(
        current = current,
        previous = next,
        expectedCurrent = previous
    )

    fun summarizeChanges(
        loaded: BooleanArray,
        staged: BooleanArray
    ): Gen3PokedexChangeSummary {
        require(loaded.size == NATIONAL_DEX_SIZE && staged.size == NATIONAL_DEX_SIZE) {
            "Gen 3 Pokédex registration must contain exactly 386 flags."
        }
        var registered = 0
        var cleared = 0
        loaded.indices.forEach { index ->
            if (!loaded[index] && staged[index]) registered++
            if (loaded[index] && !staged[index]) cleared++
        }
        return Gen3PokedexChangeSummary(registered, cleared)
    }

    fun changeDirection(
        loaded: BooleanArray,
        staged: BooleanArray,
        index: Int
    ): Gen3PokedexChangeDirection? {
        require(loaded.size == NATIONAL_DEX_SIZE && staged.size == NATIONAL_DEX_SIZE) {
            "Gen 3 Pokédex registration must contain exactly 386 flags."
        }
        require(index in loaded.indices) { "Gen 3 Pokédex index must be between 0 and 385." }
        return when {
            !loaded[index] && staged[index] -> Gen3PokedexChangeDirection.REGISTER
            loaded[index] && !staged[index] -> Gen3PokedexChangeDirection.CLEAR
            else -> null
        }
    }

    fun registrationStatus(
        loaded: BooleanArray,
        staged: BooleanArray,
        index: Int
    ): Gen3PokedexRegistrationStatus =
        when (changeDirection(loaded, staged, index)) {
            Gen3PokedexChangeDirection.REGISTER ->
                Gen3PokedexRegistrationStatus.PENDING_REGISTER
            Gen3PokedexChangeDirection.CLEAR ->
                Gen3PokedexRegistrationStatus.PENDING_CLEAR
            null -> if (staged[index]) {
                Gen3PokedexRegistrationStatus.REGISTERED
            } else {
                Gen3PokedexRegistrationStatus.UNREGISTERED
            }
        }

    fun listChanges(
        loaded: BooleanArray,
        staged: BooleanArray
    ): List<Gen3PokedexChange> {
        require(loaded.size == NATIONAL_DEX_SIZE && staged.size == NATIONAL_DEX_SIZE) {
            "Gen 3 Pokédex registration must contain exactly 386 flags."
        }
        return loaded.indices
            .filter { loaded[it] != staged[it] }
            .map { Gen3PokedexChange(dexNumber = it + 1, willBeRegistered = staged[it]) }
    }

    fun readyToSave(
        loaded: BooleanArray,
        staged: BooleanArray,
        reviewAcknowledged: Boolean
    ): Boolean = reviewAcknowledged && changedCount(loaded, staged) > 0

    fun hasChanged(
        loaded: BooleanArray,
        staged: BooleanArray,
        index: Int
    ): Boolean {
        require(loaded.size == NATIONAL_DEX_SIZE && staged.size == NATIONAL_DEX_SIZE) {
            "Gen 3 Pokédex registration must contain exactly 386 flags."
        }
        require(index in loaded.indices) { "Gen 3 Pokédex index must be between 0 and 385." }
        return loaded[index] != staged[index]
    }

    fun changedCount(loaded: BooleanArray, staged: BooleanArray): Int =
        summarizeChanges(loaded, staged).total
}
