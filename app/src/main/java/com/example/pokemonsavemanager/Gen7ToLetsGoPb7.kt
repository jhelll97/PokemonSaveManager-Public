package com.example.pokemonsavemanager

data class Gen7ToLetsGoReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val destinationVersion: LetsGoVersion,
    val effects: List<String>
)

/** Converts one reviewed native Gen 7 PK7 Bank record into a native Let's Go PB7. */
object Gen7ToLetsGoPb7 {
    fun review(record: ByteArray, destinationVersion: LetsGoVersion): Gen7ToLetsGoReview {
        val bridge = Gen7ToLegendsZaPa9.review(record)
        val converted = LegendsZaToLetsGoPb7.review(bridge.nativeRecord, destinationVersion)
        val effects = buildList {
            add(
                "Explicit Legalise: PSE Generation 7 PK7 -> deterministic in-memory Legends: Z-A PA9 " +
                    "-> ${destinationVersion.displayName} PB7 conversion. This cross-title route is not an " +
                    "official Bank, Transporter or HOME transfer, in-game trade, encounter record, legality " +
                    "certification, or verified provenance."
            )
            add(
                "The exact checksum-valid 232-byte PK7 remains the selected Bank source. The intermediate " +
                    "PA9 is deterministic conversion data only: it is never deposited, written to a " +
                    "Legends: Z-A save, or presented as transfer history."
            )
            bridge.effects.forEach { add("PK7 -> temporary PA9: $it") }
            converted.effects.forEach { add("Temporary PA9 -> PB7: $it") }
            add(
                "The original PK7 Bank entry and Let's Go source save remain unchanged. PSE writes only the " +
                    "next unused native PB7 storage record in a new copy, updates the native storage count and " +
                    "block CRCs, retains a verified recovery backup, reopens the persisted destination, removes " +
                    "a failed copy when possible, and re-verifies both unchanged sources."
            )
        }
        return Gen7ToLetsGoReview(
            converted.nativeRecord,
            bridge.sourceSpecies,
            bridge.sourceNickname,
            destinationVersion,
            effects
        )
    }
}
