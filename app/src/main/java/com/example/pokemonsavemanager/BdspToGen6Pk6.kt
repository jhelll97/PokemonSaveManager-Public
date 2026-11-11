package com.example.pokemonsavemanager

data class BdspToGen6Review(
    val source:BdspToGen3Review,
    val effects:List<String>
)

/**
 * Reviews one native PB8 for the bounded BDSP -> PK6 route. Temporary PK3,
 * PK4 and PK5 records are deterministic in-memory bridges only.
 */
object BdspToGen6Pk6 {
    fun review(record:ByteArray):BdspToGen6Review{
        val source=BdspToGen3Pk3.review(record)
        val effects=listOf(
            "Explicit Legalise: backward PSE Brilliant Diamond/Shining Pearl PB8 → deterministic in-memory Generation 3 PK3 → PK4 → PK5 → native Generation 6 PK6. This is not an official trade, Pal Park migration, Poké Transfer, Pokémon Bank or HOME transfer, legality certification, encounter record, or verified provenance.",
            "This bounded PK6 route accepts only the species, moves, default forms, text, values and PID-sensitive cases already proven by the BDSP PB8 → PK3 adapter. Post-Generation-3 species and moves remain blocked until a direct Generation 6 catalogue mapping is implemented.",
            "The exact PB8 remains the content-addressed Bank source. PK3, PK4 and PK5 are temporary conversion records and are never deposited, written to another save, or presented as verified history.",
            "The user chooses a documented Generation 3 origin for the temporary bridge. PK4 applies the reviewed conversion date, PK5 uses unspecified met location 0, and PK6 uses transfer marker 30001; these are disclosed PSE conversion fields and do not verify official transfer history.",
            "The final PK6 uses the loaded X, Y, Omega Ruby or Alpha Sapphire trainer as current handler while retaining the reviewed Original Trainer. Relearn moves, geography and Generation 6-only metadata start empty because the bridge formats do not contain them.",
            "Every PB8 field omitted by the PK3 review remains an explicit Legalise omission. The source BDSP save and PB8 Bank entry are never consumed."
        )
        return BdspToGen6Review(source,effects)
    }
}
