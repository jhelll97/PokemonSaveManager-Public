package com.example.pokemonsavemanager

data class SwordShieldToLetsGoReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val destinationVersion: LetsGoVersion,
    val effects: List<String>
)

/** Converts one reviewed native Sword/Shield PK8 Bank record into a native Let's Go PB7. */
object SwordShieldToLetsGoPb7 {
    fun review(record: ByteArray, destinationVersion: LetsGoVersion): SwordShieldToLetsGoReview {
        val source = SwordShieldPseBankRestoreEditing.inspectBankRecord(record)
        val bridge = SwordShieldToLegendsZaPa9.review(record)
        val converted = LegendsZaToLetsGoPb7.review(bridge.nativeRecord, destinationVersion)
        val effects = buildList {
            add(
                "Explicit Legalise: PSE Sword/Shield PK8 -> deterministic in-memory Legends: Z-A PA9 " +
                    "-> ${destinationVersion.displayName} PB7 conversion. This backward cross-generation route " +
                    "is not an official HOME transfer, in-game trade, encounter record, legality certification, " +
                    "or verified provenance."
            )
            add(
                "The exact checksum-valid 344-byte PK8 remains the content-addressed Bank source. " +
                    "The intermediate PA9 is deterministic conversion data only: it is never deposited, " +
                    "written to a Legends: Z-A save, or presented as transfer history."
            )
            bridge.effects.forEach { add("PK8 -> temporary PA9: $it") }
            converted.effects.forEach { add("Temporary PA9 -> PB7: $it") }
            add(
                "The original PK8 Bank entry and Let's Go source save remain unchanged. PSE writes only the " +
                    "next unused native PB7 storage record in a new copy, updates the native storage count and " +
                    "block CRCs, retains a verified recovery backup, reopens the persisted destination, removes " +
                    "a failed copy when possible, and re-verifies both unchanged sources."
            )
        }
        return SwordShieldToLetsGoReview(
            converted.nativeRecord,
            source.species,
            source.nickname,
            destinationVersion,
            effects
        )
    }
}
