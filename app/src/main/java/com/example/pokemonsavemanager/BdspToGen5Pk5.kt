package com.example.pokemonsavemanager

data class BdspToGen5Review(
    val source:BdspToGen3Review,
    val effects:List<String>
)

/**
 * Reviews one native PB8 for the bounded BDSP -> PK5 route. Temporary PK3
 * and PK4 records are deterministic in-memory bridges only.
 */
object BdspToGen5Pk5 {
    fun review(record:ByteArray):BdspToGen5Review{
        val source=BdspToGen3Pk3.review(record)
        val effects=listOf(
            "Explicit Legalise: backward PSE Brilliant Diamond/Shining Pearl PB8 → deterministic in-memory Generation 3 PK3 → PK4 → native Generation 5 PK5. This is not an official trade, Pal Park migration, Poké Transfer, Pokémon Bank or HOME transfer, legality certification, encounter record, or verified provenance.",
            "This bounded PK5 route accepts only the species, moves, default forms, text, values and PID-sensitive cases already proven by the BDSP PB8 → PK3 adapter. Post-Generation-3 species and moves remain blocked until a direct Generation 5 catalogue mapping is implemented.",
            "The exact PB8 remains the content-addressed Bank source. PK3 and PK4 are temporary conversion records and are never deposited, written to another save, or presented as verified history.",
            "The user chooses a documented Generation 3 origin for the temporary bridge. PK4 applies the reviewed conversion date and Pal Park location internally; PK5 uses explicit unspecified met location 0 because Generation 4 and Generation 5 location catalogues are not interchangeable.",
            "The final PK5 explicitly stores nature and verifies gender, shiny state and the normal ability slot against historical Generation 5 species data. PID may change only as disclosed to preserve the normal ability slot and shiny state.",
            "Every PB8 field omitted by the PK3 review remains an explicit Legalise omission. The source BDSP save and PB8 Bank entry are never consumed."
        )
        return BdspToGen5Review(source,effects)
    }
}
