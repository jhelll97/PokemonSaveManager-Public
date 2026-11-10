package com.example.pokemonsavemanager

data class Gen1ToBdspReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val effects: List<String>
)

/**
 * Direct PSE1 -> PB8 adapter through the deterministic reviewed PK7 bridge.
 *
 * The preserved 63-byte PSE1 file remains the transaction source; the bridge exists
 * only in memory and is accepted only when BDSP's own catalogues validate every field.
 */
object Gen1ToBdspPb8 {
    fun review(
        record: ByteArray,
        destinationTrainer: BdspTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): Gen1ToBdspReview {
        val bridge = Gen1ToSwordShieldPk8.buildBdspBridge(
            record, destinationTrainer, metadata
        )
        val source = bridge.source
        val converted = Gen7ToBdspPb8.review(bridge.pk7)
        val shiny = source.dvs.size >= 5 &&
            source.dvs[2] == 10 && source.dvs[3] == 10 && source.dvs[4] == 10 &&
            source.dvs[1] in setOf(2, 3, 6, 7, 10, 11, 14, 15)
        val downstream = converted.effects.filterNot {
            it.startsWith("Explicit Legalise:") ||
                it.startsWith("The PK7 Bank entry")
        }
        val effects = buildList {
            add("Explicit Legalise: direct PSE Gen 1 PSE1 -> deterministic in-memory PK7 bridge -> BDSP PB8 conversion; not an official transfer, HOME move, trade, or verified provenance.")
            add("The exact selected 63-byte PSE1 record remains the bound Bank source. Species #" +
                source.species + ", TID " + source.trainerId +
                ", OT text, nickname text, level, move IDs and PP-Up counts are retained.")
            add("Generation 1 current HP " + source.currentHp + ", status 0x" +
                source.status.toString(16).uppercase() + ", type bytes " +
                source.type1 + "/" + source.type2 + ", catch-rate byte " +
                source.catchRate + " and stat experience " +
                source.statExperience.joinToString("/") +
                " have no reviewed PB8 fields and are explicitly omitted; destination EVs are zero.")
            add("Generation 1 DVs " + source.dvs.joinToString("/") +
                " determine only the converted shiny state (" +
                (if (shiny) "shiny" else "not shiny") +
                ") and gender. Deterministic destination IVs are generated with " +
                (if (source.species == 151) 5 else 3) + " perfect stats.")
            add("Source EXP " + source.experience + " is normalized to the level-" +
                source.level + " BDSP growth-floor value " + bridge.minimumExperience +
                "; nature " + (source.experience % 25L) +
                " is derived from source EXP modulo 25.")
            add("Current PP " + source.currentPp.joinToString("/") +
                " is normalized to BDSP base PP " +
                bridge.normalizedPp.joinToString("/") +
                " with retained PP-Up counts " + source.ppUps.joinToString("/") +
                ". Unsupported BDSP moves remain blocked.")
            add("SID is generated as 0. Encryption constant, PID and IVs are deterministic Legalise values bound to the PSE1 bytes, BDSP trainer and reviewed species metadata; PID does not certify origin.")
            add("Ability " + metadata.abilityId + " in slot " +
                metadata.abilityNumber + ", base friendship " +
                metadata.baseFriendship + " and gender ratio " +
                metadata.genderRate +
                " are the documented metadata accepted for this BDSP review.")
            add("The bridge uses generated Red origin ID 35, Poké Ball, Poké Transporter met location 30013, unset encounter date, English language and the BDSP trainer as handling trainer. The legacy PSE1 record does not identify Red, Blue or Yellow, so this is an explicit provenance Legalise choice.")
            addAll(downstream)
            add("The PSE1 Bank entry and BDSP source remain unchanged. One reviewed empty native PB8 slot is written only to a new copy with caught registration, retained recovery and persisted reopen verification.")
        }
        return Gen1ToBdspReview(
            converted.nativeRecord.copyOf(),
            source.species,
            source.nickname,
            effects
        )
    }
}
