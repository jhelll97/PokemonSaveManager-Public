package com.example.pokemonsavemanager

data class BdspToGen4Review(
    val source:BdspToGen3Review,
    val effects:List<String>
)

/**
 * Reviews one native PB8 for the bounded BDSP -> PK4 route. The adapter uses
 * the reviewed native PK3 bridge only in memory and requires the Generation 4
 * writer to reopen an exact PK4 before any destination can be selected.
 */
object BdspToGen4Pk4 {
    fun review(record:ByteArray):BdspToGen4Review{
        val source=BdspToGen3Pk3.review(record)
        val effects=listOf(
            "Explicit Legalise: backward PSE Brilliant Diamond/Shining Pearl PB8 → deterministic in-memory Generation 3 PK3 bridge → native Generation 4 PK4. This is not an official Pal Park migration, trade, Pokémon Bank or HOME transfer, legality certification, encounter record, or verified provenance.",
            "This bounded PK4 route accepts only the species, moves, default forms, text, values and PID-sensitive cases already proven by the BDSP PB8 → PK3 adapter. Post-Generation-3 species and moves remain blocked until a direct Generation 4 catalogue mapping is implemented.",
            "The exact PB8 remains the content-addressed Bank source. The PK3 bridge is temporary and is never deposited, written to a save, or presented as verified history.",
            "The user chooses a documented Generation 3 origin for the temporary bridge. Native PK4 stores that origin as a Generation 3-origin Pal Park record with met location 55 and a disclosed conversion date; this PSE provenance choice does not prove an official migration.",
            "Nature, gender, shiny state and the normal ability slot are verified through the retained bridge PID and Generation 4 species rules. Contest values, stat nature, relearn moves, markings, ribbons, HOME tracker, TM/TR flags and every other PB8 field that cannot survive the reviewed bridge remain explicit Legalise omissions.",
            "The source BDSP save and PB8 Bank entry are never consumed. Destination writing is allowed only after the exact PK3 and PK4 records reopen and the user accepts the complete review."
        )
        return BdspToGen4Review(source,effects)
    }
}
