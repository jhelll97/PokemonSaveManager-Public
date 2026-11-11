package com.example.pokemonsavemanager

data class BdspToGen7Review(
    val source:BdspToGen3Review,
    val effects:List<String>
)

/**
 * Reviews one native PB8 for the bounded BDSP -> PK7 route. Temporary PK3,
 * PK4, PK5 and PK6 records are deterministic in-memory bridges only.
 */
object BdspToGen7Pk7 {
    fun review(record:ByteArray):BdspToGen7Review{
        val source=BdspToGen3Pk3.review(record)
        val effects=listOf(
            "Explicit Legalise: backward PSE Brilliant Diamond/Shining Pearl PB8 → deterministic in-memory Generation 3 PK3 → PK4 → PK5 → PK6 → native Generation 7 PK7. This is not an official trade, Pal Park migration, Poké Transfer, Pokémon Bank or HOME transfer, legality certification, encounter record, or verified provenance.",
            "This bounded PK7 route accepts only the species, moves, default forms, text, values and PID-sensitive cases already proven by the BDSP PB8 → PK3 adapter. Post-Generation-3 species and moves remain blocked until a direct Generation 7 catalogue mapping is implemented.",
            "The exact PB8 remains the content-addressed Bank source. PK3, PK4, PK5 and PK6 are temporary conversion records and are never deposited, written to another save, or presented as verified history.",
            "The user chooses a documented Generation 3 origin for the temporary bridge. PK4 applies the reviewed conversion date, PK5 uses unspecified met location 0, PK6 uses transfer marker 30001, and PK7 converts native one-bit markings to blue states. These are disclosed PSE conversion fields and do not verify official transfer history.",
            "The final PK7 uses the loaded Sun, Moon, Ultra Sun or Ultra Moon trainer as current handler while retaining the reviewed Original Trainer. Relearn moves, geography, Resort state, Hyper Training and later-generation metadata start empty because the bridge formats do not contain them.",
            "Every PB8 field omitted by the PK3 review remains an explicit Legalise omission. The source BDSP save and PB8 Bank entry are never consumed."
        )
        return BdspToGen7Review(source,effects)
    }
}
