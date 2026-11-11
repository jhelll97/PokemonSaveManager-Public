package com.example.pokemonsavemanager

data class LegendsZaToGen5Review(
    val source:LegendsZaToGen3Review,
    val effects:List<String>
)

/** Reviews one native PA9 for a bounded Z-A -> PK3 -> PK4 -> native PK5 conversion. */
object LegendsZaToGen5Pk5 {
    fun review(record:ByteArray):LegendsZaToGen5Review{
        val source=LegendsZaToGen3Pk3.review(record)
        val effects=listOf(
            "Explicit Legalise: backward PSE Legends: Z-A PA9 → deterministic in-memory Generation 3 PK3 → PK4 → native Generation 5 PK5. This is not an official Pal Park migration, Poké Transfer, trade, Pokémon Bank or HOME transfer, legality certification, encounter record, or verified provenance.",
            "This bounded PK5 route accepts only the species, moves, default forms, English text, values and PID-sensitive cases already proven by the Legends: Z-A PA9 → PK3 adapter. Post-Generation-3 species and moves remain blocked until a direct Generation 5 catalogue mapping is implemented.",
            "The exact PA9 remains the content-addressed Bank source. PK3 and PK4 are temporary conversion records and are never deposited, written to another save, or presented as verified history.",
            "The user chooses a documented Generation 3 origin for the temporary bridge. PK4 applies the reviewed conversion date and Pal Park location internally; PK5 uses explicit unspecified met location 0 because Generation 4 and Generation 5 location catalogues are not interchangeable.",
            "The final PK5 explicitly stores nature and verifies gender, shiny state and the normal ability slot against historical Generation 5 species data. PID may change only as disclosed to preserve the normal ability slot and shiny state.",
            "Encryption constant, stat nature, Alpha status, scale, obedience, cached level and stats, current HP, relearn moves, inactive friendship, handling-trainer metadata, markings, contest values, ribbons and marks, dates and source locations, HOME tracker, move-record flags, party cache and every other PA9 field omitted by the PK3 review remain explicit Legalise omissions. The source Legends: Z-A save and PA9 Bank entry are never consumed."
        )
        return LegendsZaToGen5Review(source,effects)
    }
}
