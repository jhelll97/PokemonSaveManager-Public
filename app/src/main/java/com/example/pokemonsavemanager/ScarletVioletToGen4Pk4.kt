package com.example.pokemonsavemanager

data class ScarletVioletToGen4Review(
    val source:ScarletVioletToGen3Review,
    val effects:List<String>
)

/**
 * Reviews one native PK9 for the bounded Scarlet/Violet -> PK4 route. The
 * reviewed native PK3 bridge exists only in memory and the destination writer
 * must reopen the exact PK4 before allowing a slot to be selected.
 */
object ScarletVioletToGen4Pk4 {
    fun review(record:ByteArray):ScarletVioletToGen4Review{
        val source=ScarletVioletToGen3Pk3.review(record)
        val effects=listOf(
            "Explicit Legalise: backward PSE Scarlet/Violet PK9 → deterministic in-memory Generation 3 PK3 bridge → native Generation 4 PK4. This is not an official Pal Park migration, trade, Pokémon Bank or HOME transfer, legality certification, encounter record, or verified provenance.",
            "This bounded PK4 route accepts only the species, moves, default forms, English text, values and PID-sensitive cases already proven by the Scarlet/Violet PK9 → PK3 adapter. Post-Generation-3 species and moves remain blocked until a direct Generation 4 catalogue mapping is implemented.",
            "The exact PK9 remains the content-addressed Bank source. The PK3 bridge is temporary and is never deposited, written to a save, or presented as verified history.",
            "The user chooses a documented Generation 3 origin for the temporary bridge. Native PK4 stores that origin as a Generation 3-origin Pal Park record with met location 55 and a disclosed conversion date; this PSE provenance choice does not prove an official migration.",
            "Nature, gender, shiny state and the normal ability slot are verified through the retained bridge PID and Generation 4 species rules. Encryption constant, stat nature, Tera types, scale, obedience, cached level and stats, current HP, relearn moves, inactive friendship, handling-trainer metadata, markings, contest values, ribbons and marks, size values, dates and locations, HOME tracker, Technical Machine flags, battle version, form argument, status condition, party cache and every other PK9 field that cannot survive the reviewed bridge remain explicit Legalise omissions.",
            "The source Scarlet/Violet save and PK9 Bank entry are never consumed. Destination writing is allowed only after the exact PK3 and PK4 records reopen and the user accepts the complete review."
        )
        return ScarletVioletToGen4Review(source,effects)
    }
}
