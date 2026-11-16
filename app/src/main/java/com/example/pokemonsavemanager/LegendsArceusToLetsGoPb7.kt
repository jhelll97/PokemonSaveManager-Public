package com.example.pokemonsavemanager

data class LegendsArceusToLetsGoReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val destinationVersion: LetsGoVersion,
    val effects: List<String>
)

/** Converts one reviewed PA8 Bank record to Let's Go PB7 through an in-memory PA9 bridge. */
object LegendsArceusToLetsGoPb7 {
    fun review(record: ByteArray, destinationVersion: LetsGoVersion): LegendsArceusToLetsGoReview {
        val first = LegendsArceusToLegendsZaPa9.review(record)
        val second = LegendsZaToLetsGoPb7.review(first.nativeRecord, destinationVersion)
        val source = LegendsArceusPseBankRestoreEditing.inspectBankRecord(record)
        val effects = listOf(
            "Explicit two-stage PSE Legalise: native Legends: Arceus PA8 -> temporary native PA9 -> " +
                "${destinationVersion.displayName} PB7. The intermediate PA9 is held only in memory; " +
                "it is never stored in PSE Bank or written to a Legends: Z-A save. Review every effect below.",
            *first.effects.map { "PA8 -> temporary PA9: $it" }.toTypedArray(),
            *second.effects.map { "temporary PA9 -> PB7: $it" }.toTypedArray(),
            "Original checksum-valid ${record.size}-byte PA8 Bank entry and source save remain unchanged. " +
                "PSE writes only the next unused native PB7 storage record in a new copy, updates the native " +
                "storage count and block CRCs, and registers the converted gender, shiny state and English " +
                "language in the matching native caught entry when required. It refreshes the Pokédex block " +
                "CRC, retains a verified recovery backup, reopens the persisted destination, removes a failed " +
                "copy when possible, and re-verifies both unchanged sources. " +
                "No official HOME transfer, trade, encounter record, legality certification, or provenance is claimed."
        )
        return LegendsArceusToLetsGoReview(
            second.nativeRecord, source.species, source.nickname, destinationVersion, effects
        )
    }
}
