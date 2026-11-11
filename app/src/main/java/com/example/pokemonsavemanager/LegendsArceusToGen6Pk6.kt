package com.example.pokemonsavemanager

data class LegendsArceusToGen6Review(
    val source:LegendsArceusToGen3Review,
    val effects:List<String>
)

/**
 * Reviews one native PA8 for the bounded Legends: Arceus -> PK6 route.
 * Temporary PK3, PK4 and PK5 records are deterministic in-memory bridges only.
 */
object LegendsArceusToGen6Pk6 {
    fun review(record:ByteArray):LegendsArceusToGen6Review{
        val source=LegendsArceusToGen3Pk3.review(record)
        val effects=listOf(
            "Explicit Legalise: backward PSE Legends: Arceus PA8 → deterministic in-memory Generation 3 PK3 → PK4 → PK5 → native Generation 6 PK6. This is not an official trade, Pal Park migration, Poké Transfer, Pokémon Bank or HOME transfer, legality certification, encounter record, or verified provenance.",
            "This bounded PK6 route accepts only the species, moves, default forms, English text, values and PID-sensitive cases already proven by the Legends: Arceus PA8 → PK3 adapter. Post-Generation-3 species and moves remain blocked until a direct Generation 6 catalogue mapping is implemented.",
            "The exact PA8 remains the content-addressed Bank source. PK3, PK4 and PK5 are temporary conversion records and are never deposited, written to another save, or presented as verified history.",
            "The user chooses a documented Generation 3 origin for the temporary bridge. PK4 applies the reviewed conversion date and Pal Park location internally, PK5 uses explicit unspecified met location 0, and PK6 uses transfer marker 30001; these are disclosed PSE conversion fields and do not verify official transfer history.",
            "The final PK6 uses the loaded X, Y, Omega Ruby or Alpha Sapphire trainer as current handler while retaining the reviewed Original Trainer. Relearn moves, handler geography and Generation 6-only metadata start empty because the bridge formats do not contain them.",
            "Stat nature, Ganbaru Values, Alpha status, contest values, relearn moves, PA8 markings, ribbons and marks, size data, memories, handler metadata, dates and source locations, HOME tracker, move records, party cache and every other PA8 field omitted by the PK3 review remain explicit Legalise omissions. The source Legends: Arceus save and PA8 Bank entry are never consumed."
        )
        return LegendsArceusToGen6Review(source,effects)
    }
}
