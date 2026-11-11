package com.example.pokemonsavemanager

data class LegendsZaToGen6Review(
    val source:LegendsZaToGen3Review,
    val effects:List<String>
)

/** Reviews one native PA9 for a bounded Z-A -> PK3 -> PK4 -> PK5 -> native PK6 conversion. */
object LegendsZaToGen6Pk6 {
    fun review(record:ByteArray):LegendsZaToGen6Review{
        val source=LegendsZaToGen3Pk3.review(record)
        val effects=listOf(
            "Explicit Legalise: backward PSE Legends: Z-A PA9 → deterministic in-memory Generation 3 PK3 → PK4 → PK5 → native Generation 6 PK6. This is not an official Pal Park migration, Poké Transfer, trade, Pokémon Bank or HOME transfer, legality certification, encounter record, or verified provenance.",
            "This bounded PK6 route accepts only the species, moves, default forms, English text, values and PID-sensitive cases already proven by the Legends: Z-A PA9 → PK3 adapter. Post-Generation-3 species and moves remain blocked until a direct Generation 6 catalogue mapping is implemented.",
            "The exact PA9 remains the content-addressed Bank source. PK3, PK4 and PK5 are temporary conversion records and are never deposited, written to another save, or presented as verified history.",
            "The user chooses a documented Generation 3 origin for the temporary bridge. PK4 applies the reviewed conversion date and Pal Park location internally, PK5 uses explicit unspecified met location 0, and PK6 uses transfer marker 30001; these are disclosed PSE conversion fields and do not verify official transfer history.",
            "The final PK6 uses the loaded X, Y, Omega Ruby or Alpha Sapphire trainer as current handler while retaining the reviewed Original Trainer. Relearn moves, handler geography, memories, affection and other Generation 6-only metadata start empty because the bridge formats do not contain them.",
            "Encryption constant, stat nature, Alpha status, scale, obedience, cached level and stats, current HP, relearn moves, inactive friendship, source handling-trainer metadata, markings, contest values, ribbons and marks, dates and source locations, HOME tracker, move-record flags, party cache and every other PA9 field omitted by the PK3 review remain explicit Legalise omissions. The source Legends: Z-A save and PA9 Bank entry are never consumed."
        )
        return LegendsZaToGen6Review(source,effects)
    }
}
