package com.example.pokemonsavemanager

data class SwordShieldToGen4Review(
    val source:SwordShieldToGen3Review,
    val effects:List<String>
)

/**
 * Reviews the native PK8 source for the first bounded Sword/Shield -> PK4 route.
 * The adapter uses the already reviewed native PK3 bridge only in memory, then
 * requires the Generation 3 -> Generation 4 writer to reopen an exact PK4.
 */
object SwordShieldToGen4Pk4 {
    fun review(record:ByteArray):SwordShieldToGen4Review{
        val source=SwordShieldToGen3Pk3.review(record)
        val effects=listOf(
            "Explicit Legalise: backward PSE Sword/Shield PK8 → deterministic in-memory Generation 3 PK3 bridge → native Generation 4 PK4. This is not an official Pal Park migration, trade, Pokémon Bank or HOME transfer, legality certification, encounter record, or verified provenance.",
            "This first bounded PK4 route accepts only the species, moves, default forms, text, values and PID-sensitive cases already proven by the native PK3 source adapter. Post-Generation-3 species and moves remain blocked until a direct Generation 4 catalogue mapping is implemented.",
            "The exact PK8 remains the content-addressed Bank source. The PK3 bridge is temporary and is never deposited, written to a save, or presented as verified history.",
            "The user chooses a documented Generation 3 origin for the temporary bridge. Native PK4 then stores that origin as a Generation 3-origin Pal Park record with met location 55 and a disclosed conversion date; this PSE provenance choice does not prove an official migration.",
            "Nature, gender, shiny state and the normal ability slot are verified through the bridge PID and the Generation 4 species rules. Any source PK8 field that cannot survive the PK3 bridge remains an explicit Legalise omission.",
            "The source Sword/Shield save and PK8 Bank entry are never consumed. Destination writing is allowed only after the exact PK3 and PK4 records reopen and the user accepts the complete review."
        )
        return SwordShieldToGen4Review(source,effects)
    }
}
