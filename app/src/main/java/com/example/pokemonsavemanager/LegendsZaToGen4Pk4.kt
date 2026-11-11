package com.example.pokemonsavemanager

data class LegendsZaToGen4Review(
    val source:LegendsZaToGen3Review,
    val effects:List<String>
)

/** Reviews one native PA9 for a bounded Z-A -> PK3 bridge -> native PK4 conversion. */
object LegendsZaToGen4Pk4 {
    fun review(record:ByteArray):LegendsZaToGen4Review{
        val source=LegendsZaToGen3Pk3.review(record)
        val effects=listOf(
            "Explicit Legalise: backward PSE Legends: Z-A PA9 → deterministic in-memory Generation 3 PK3 bridge → native Generation 4 PK4. This is not an official Pal Park migration, trade, Pokémon Bank or HOME transfer, legality certification, encounter record, or verified provenance.",
            "This bounded PK4 route accepts only the species, moves, default forms, English text, values and PID-sensitive cases already proven by the Legends: Z-A PA9 → PK3 adapter. Post-Generation-3 species and moves remain blocked until a direct Generation 4 catalogue mapping is implemented.",
            "The exact PA9 remains the content-addressed Bank source. The PK3 bridge is temporary and is never deposited, written to a save, or presented as verified history.",
            "The user chooses a documented Generation 3 origin for the temporary bridge. Native PK4 stores that origin as a Generation 3-origin Pal Park record with met location 55 and a disclosed conversion date; this PSE provenance choice does not prove an official migration.",
            "Nature, gender, shiny state and the normal ability slot are verified through the retained bridge PID and Generation 4 species rules. Encryption constant, stat nature, Alpha status, scale, obedience, cached level and stats, current HP, relearn moves, inactive friendship, handling-trainer metadata, markings, contest values, ribbons and marks, dates and source locations, HOME tracker, move-record flags, party cache and every other PA9 field that cannot survive the reviewed bridge remain explicit Legalise omissions.",
            "The source Legends: Z-A save and PA9 Bank entry are never consumed. Destination writing is allowed only after the exact PK3 and PK4 records reopen and the user accepts the complete review."
        )
        return LegendsZaToGen4Review(source,effects)
    }
}
