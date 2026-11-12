package com.example.pokemonsavemanager

data class Gen3ToLetsGoReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val destinationVersion: LetsGoVersion,
    val effects: List<String>
)

/** Converts one reviewed native Gen 3 PK3 Bank record into a native Let's Go PB7. */
object Gen3ToLetsGoPb7 {
    fun review(
        record: ByteArray,
        destinationTrainer: LetsGoTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): Gen3ToLetsGoReview {
        val bridge = Gen3ToLegendsZaPa9.reviewForLetsGo(record, destinationTrainer, metadata)
        val converted = LegendsZaToLetsGoPb7.review(bridge.nativeRecord, destinationTrainer.version)
        val effects = buildList {
            add(
                "Explicit Legalise: PSE Generation 3 PK3 -> deterministic in-memory PK7 -> PA9 " +
                    "-> ${destinationTrainer.version.displayName} PB7 conversion. This backward cross-title " +
                    "route is not an official Pal Park, Poké Transfer, Bank or HOME transfer, in-game trade, " +
                    "encounter record, legality certification, or verified provenance."
            )
            add(
                "The exact checksum-valid 80-byte PK3 remains the selected Bank source. The opened English " +
                    "${destinationTrainer.version.displayName} trainer ${destinationTrainer.name} becomes the " +
                    "generated current handling trainer; temporary PK7 and PA9 records are never deposited or " +
                    "written to another save."
            )
            bridge.effects.forEach { add("PK3 -> temporary PA9: $it") }
            converted.effects.forEach { add("Temporary PA9 -> PB7: $it") }
            add(
                "The original PK3 Bank entry and Let's Go source save remain unchanged. PSE writes only the " +
                    "next unused native PB7 storage record in a new copy, updates the native storage count and " +
                    "block CRCs, retains a verified recovery backup, reopens the persisted destination, removes " +
                    "a failed copy when possible, and re-verifies both unchanged sources."
            )
        }
        return Gen3ToLetsGoReview(
            converted.nativeRecord,
            bridge.sourceSpecies,
            bridge.sourceNickname,
            destinationTrainer.version,
            effects
        )
    }
}
