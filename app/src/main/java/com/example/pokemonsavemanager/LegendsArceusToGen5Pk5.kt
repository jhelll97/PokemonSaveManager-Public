package com.example.pokemonsavemanager

data class LegendsArceusToGen5Review(
    val source:LegendsArceusToGen3Review,
    val effects:List<String>
)

/**
 * Reviews one native PA8 for the bounded Legends: Arceus -> PK5 route.
 * Temporary PK3 and PK4 records are deterministic in-memory bridges only.
 */
object LegendsArceusToGen5Pk5 {
    fun review(record:ByteArray):LegendsArceusToGen5Review{
        val source=LegendsArceusToGen3Pk3.review(record)
        val effects=listOf(
            "Explicit Legalise: backward PSE Legends: Arceus PA8 → deterministic in-memory Generation 3 PK3 → PK4 → native Generation 5 PK5. This is not an official trade, Pal Park migration, Poké Transfer, Pokémon Bank or HOME transfer, legality certification, encounter record, or verified provenance.",
            "This bounded PK5 route accepts only the species, moves, default forms, English text, values and PID-sensitive cases already proven by the Legends: Arceus PA8 → PK3 adapter. Post-Generation-3 species and moves remain blocked until a direct Generation 5 catalogue mapping is implemented.",
            "The exact PA8 remains the content-addressed Bank source. PK3 and PK4 are temporary conversion records and are never deposited, written to another save, or presented as verified history.",
            "The user chooses a documented Generation 3 origin for the temporary bridge. PK4 applies the reviewed conversion date and Pal Park location internally; PK5 uses explicit unspecified met location 0 because Generation 4 and Generation 5 location catalogues are not interchangeable.",
            "The final PK5 explicitly stores nature and verifies gender, shiny state and the normal ability slot against historical Generation 5 species data. PID may change only as disclosed to preserve the normal ability slot and shiny state.",
            "Stat nature, Ganbaru Values, Alpha status, contest values, relearn moves, markings, ribbons and marks, size data, memories, handler metadata, dates and locations, HOME tracker, move records, party cache and every other PA8 field omitted by the PK3 review remain explicit Legalise omissions. The source Legends: Arceus save and PA8 Bank entry are never consumed."
        )
        return LegendsArceusToGen5Review(source,effects)
    }
}
