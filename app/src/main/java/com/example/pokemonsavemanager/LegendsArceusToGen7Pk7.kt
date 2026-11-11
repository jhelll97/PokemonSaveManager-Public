package com.example.pokemonsavemanager

data class LegendsArceusToGen7Review(
    val source:LegendsArceusToGen3Review,
    val effects:List<String>
)

/**
 * Reviews one native PA8 for the bounded Legends: Arceus -> PK7 route.
 * Temporary PK3, PK4, PK5 and PK6 records are deterministic in-memory bridges only.
 */
object LegendsArceusToGen7Pk7 {
    fun review(record:ByteArray):LegendsArceusToGen7Review{
        val source=LegendsArceusToGen3Pk3.review(record)
        val effects=listOf(
            "Explicit Legalise: backward PSE Legends: Arceus PA8 → deterministic in-memory Generation 3 PK3 → PK4 → PK5 → PK6 → native Generation 7 PK7. This is not an official trade, Pal Park migration, Poké Transfer, Pokémon Bank or HOME transfer, legality certification, encounter record, or verified provenance.",
            "This bounded PK7 route accepts only the species, moves, default forms, English text, values and PID-sensitive cases already proven by the Legends: Arceus PA8 → PK3 adapter. Post-Generation-3 species and moves remain blocked until a direct Generation 7 catalogue mapping is implemented.",
            "The exact PA8 remains the content-addressed Bank source. PK3, PK4, PK5 and PK6 are temporary conversion records and are never deposited, written to another save, or presented as verified history.",
            "The user chooses a documented Generation 3 origin for the temporary bridge. PK4 applies the reviewed conversion date and Pal Park location internally, PK5 uses explicit unspecified met location 0, PK6 uses transfer marker 30001, and PK7 converts native one-bit markings to blue states. These are disclosed PSE conversion fields and do not verify official transfer history.",
            "The final PK7 uses the loaded Sun, Moon, Ultra Sun or Ultra Moon trainer as current handler while retaining the reviewed Original Trainer. Relearn moves, handler geography, Resort state, Hyper Training and later-generation metadata start empty because the bridge formats do not contain them.",
            "Stat nature, Ganbaru Values, Alpha status, contest values, relearn moves, PA8 markings, ribbons and marks, size data, memories, handler metadata, dates and source locations, HOME tracker, move records, party cache and every other PA8 field omitted by the PK3 review remain explicit Legalise omissions. The source Legends: Arceus save and PA8 Bank entry are never consumed."
        )
        return LegendsArceusToGen7Review(source,effects)
    }
}
