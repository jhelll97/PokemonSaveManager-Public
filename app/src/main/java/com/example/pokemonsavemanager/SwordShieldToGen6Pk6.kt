package com.example.pokemonsavemanager

data class SwordShieldToGen6Review(
    val source:SwordShieldToGen3Review,
    val effects:List<String>
)

/**
 * Reviews one native PK8 for the first bounded Sword/Shield -> PK6 route.
 * Temporary PK3, PK4 and PK5 records are deterministic in-memory bridges only.
 */
object SwordShieldToGen6Pk6 {
    fun review(record:ByteArray):SwordShieldToGen6Review{
        val source=SwordShieldToGen3Pk3.review(record)
        val effects=listOf(
            "Explicit Legalise: backward PSE Sword/Shield PK8 → deterministic in-memory Generation 3 PK3 → PK4 → PK5 → native Generation 6 PK6. This is not an official trade, migration, Poké Transfer, Pokémon Bank or HOME transfer, legality certification, encounter record, or verified provenance.",
            "This first bounded PK6 route accepts only the species, moves, default forms, text, values and PID-sensitive cases already proven by the native PK3 source adapter. Post-Generation-3 species and moves remain blocked until a direct Generation 6 catalogue mapping is implemented.",
            "The exact PK8 remains the content-addressed Bank source. PK3, PK4 and PK5 are temporary conversion records and are never deposited, written to another save, or presented as verified history.",
            "The user chooses a documented Generation 3 origin for the temporary bridge. PK4 applies the reviewed transfer date, PK5 uses an unspecified location, and PK6 uses transfer marker 30001; these are disclosed PSE conversion fields and do not verify official transfer history.",
            "The final PK6 uses the loaded X, Y, Omega Ruby or Alpha Sapphire trainer as current handler while retaining the reviewed Original Trainer. Relearn moves, geography and Generation 6-only metadata start empty because the bridge formats do not contain them.",
            "Every PK8 field omitted by the PK3 review remains an explicit Legalise omission. The source Sword/Shield save and PK8 Bank entry are never consumed."
        )
        return SwordShieldToGen6Review(source,effects)
    }
}
