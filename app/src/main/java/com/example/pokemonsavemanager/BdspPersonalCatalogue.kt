package com.example.pokemonsavemanager

data class BdspPersonalData(
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val speed: Int,
    val specialAttack: Int,
    val specialDefense: Int,
    val growth: Int
)

/** BDSP-native Generation 8 profiles, narrowed from the pinned shared Generation 8 catalogue. */
object BdspPersonalCatalogue {
    private val maximumNativeForm = mapOf(
        201 to 27, 351 to 3, 386 to 3, 412 to 2, 413 to 2, 421 to 1,
        422 to 1, 423 to 1, 479 to 5, 487 to 1, 492 to 1, 493 to 17
    )

    fun profiles(species: Int, form: Int): List<BdspPersonalData> {
        if (species !in 1..493 || form !in 0..255) return emptyList()
        if (form != 0 && form !in 1..maximumNativeForm.getOrDefault(species, 0)) return emptyList()
        val source = SwordShieldPersonalCatalogue.profiles(species)
        val candidates = if (form == 0) source.filter { it.isDefault } else source
        return candidates.map {
            BdspPersonalData(
                it.hp, it.attack, it.defense, it.speed,
                it.specialAttack, it.specialDefense, it.growth
            )
        }.distinct()
    }
}
