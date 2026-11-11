package com.example.pokemonsavemanager

data class SwordShieldToGen5Review(
    val source:SwordShieldToGen3Review,
    val effects:List<String>
)

/**
 * Reviews one native PK8 for the first bounded Sword/Shield -> PK5 route.
 * Temporary PK3 and PK4 records are deterministic in-memory bridges only.
 */
object SwordShieldToGen5Pk5 {
    fun review(record:ByteArray):SwordShieldToGen5Review{
        val source=SwordShieldToGen3Pk3.review(record)
        val effects=listOf(
            "Explicit Legalise: backward PSE Sword/Shield PK8 → deterministic in-memory Generation 3 PK3 → PK4 → native Generation 5 PK5. This is not an official trade, migration, Poké Transfer, Pokémon Bank or HOME transfer, legality certification, encounter record, or verified provenance.",
            "This first bounded PK5 route accepts only the species, moves, default forms, text, values and PID-sensitive cases already proven by the native PK3 source adapter. Post-Generation-3 species and moves remain blocked until a direct Generation 5 catalogue mapping is implemented.",
            "The exact PK8 remains the content-addressed Bank source. PK3 and PK4 are temporary conversion records and are never deposited, written to another save, or presented as verified history.",
            "The user chooses a documented Generation 3 origin for the temporary bridge. PK4 applies the reviewed transfer date and Pal Park location internally; PK5 uses explicit unspecified met location 0 because Generation 4 and Generation 5 location catalogues are not interchangeable.",
            "The final PK5 explicitly stores nature and verifies gender, shiny state and the normal ability slot against historical Generation 5 species data. PID may change only as disclosed to preserve the normal ability slot and shiny state.",
            "Every PK8 field omitted by the PK3 review remains an explicit Legalise omission. The source Sword/Shield save and PK8 Bank entry are never consumed."
        )
        return SwordShieldToGen5Review(source,effects)
    }
}
