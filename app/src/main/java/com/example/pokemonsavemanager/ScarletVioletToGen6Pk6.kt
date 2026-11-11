package com.example.pokemonsavemanager

data class ScarletVioletToGen6Review(
    val source:ScarletVioletToGen3Review,
    val effects:List<String>
)

/**
 * Reviews one native PK9 for the bounded Scarlet/Violet -> PK6 route.
 * Temporary PK3, PK4 and PK5 records are deterministic in-memory bridges only.
 */
object ScarletVioletToGen6Pk6 {
    fun review(record:ByteArray):ScarletVioletToGen6Review{
        val source=ScarletVioletToGen3Pk3.review(record)
        val effects=listOf(
            "Explicit Legalise: backward PSE Scarlet/Violet PK9 → deterministic in-memory Generation 3 PK3 → PK4 → PK5 → native Generation 6 PK6. This is not an official trade, Pal Park migration, Poké Transfer, Pokémon Bank or HOME transfer, legality certification, encounter record, or verified provenance.",
            "This bounded PK6 route accepts only the species, moves, default forms, English text, values and PID-sensitive cases already proven by the Scarlet/Violet PK9 → PK3 adapter. Post-Generation-3 species and moves remain blocked until a direct Generation 6 catalogue mapping is implemented.",
            "The exact PK9 remains the content-addressed Bank source. PK3, PK4 and PK5 are temporary conversion records and are never deposited, written to another save, or presented as verified history.",
            "The user chooses a documented Generation 3 origin for the temporary bridge. PK4 applies the reviewed conversion date and Pal Park location internally, PK5 uses explicit unspecified met location 0, and PK6 uses transfer marker 30001; these are disclosed PSE conversion fields and do not verify official transfer history.",
            "The final PK6 uses the loaded X, Y, Omega Ruby or Alpha Sapphire trainer as current handler while retaining the reviewed Original Trainer. Relearn moves, handler geography and Generation 6-only metadata start empty because the bridge formats do not contain them.",
            "Encryption constant, stat nature, Tera types, scale, obedience, cached level and stats, current HP, relearn moves, inactive friendship, handling-trainer metadata, markings, contest values, ribbons and marks, size values, dates and source locations, HOME tracker, Technical Machine flags, battle version, form argument, status condition, party cache and every other PK9 field omitted by the PK3 review remain explicit Legalise omissions. The source Scarlet/Violet save and PK9 Bank entry are never consumed."
        )
        return ScarletVioletToGen6Review(source,effects)
    }
}
