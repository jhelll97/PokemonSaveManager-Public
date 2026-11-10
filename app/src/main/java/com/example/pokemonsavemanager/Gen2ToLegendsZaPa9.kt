package com.example.pokemonsavemanager

data class Gen2ToLegendsZaReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val effects: List<String>
)

/**
 * Direct PSE2 -> PA9 adapter through the deterministic reviewed PK7 bridge.
 *
 * The preserved 62-byte PSE2 file remains the transaction source. Legends: Z-A's
 * own species, profile, move, box, Pokédex, and integrity rules gate the native write.
 */
object Gen2ToLegendsZaPa9 {
    fun review(
        record: ByteArray,
        destinationTrainer: LegendsZaTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): Gen2ToLegendsZaReview {
        val bridge = Gen2ToSwordShieldPk8.buildLegendsZaBridge(
            record, destinationTrainer, metadata
        )
        val source = bridge.source
        val converted = Gen7ToLegendsZaPa9.review(bridge.pk7)
        val downstream = converted.effects.filterNot {
            it.startsWith("Explicit Legalise:") ||
                it.startsWith("The PK7 Bank entry")
        }
        val game = when (source.sourceGame) {
            39 -> "Gold"
            40 -> "Silver"
            41 -> "Crystal"
            else -> "Generation 2"
        }
        val effects = buildList {
            add("Explicit Legalise: direct PSE Gen 2 PSE2 -> deterministic in-memory PK7 bridge -> Legends: Z-A PA9 conversion; not an official transfer, HOME move, trade, or verified provenance.")
            add("The exact selected 62-byte PSE2 record remains the bound Bank source. Species #" +
                source.species + ", TID " + source.trainerId +
                ", OT text, nickname text, exact EXP " + source.experience +
                ", level " + source.level +
                ", move IDs, current PP and PP-Up counts are retained.")
            add("Generation 2 happiness " + source.happiness +
                " and Pokérus byte 0x" +
                source.pokerus.toString(16).uppercase().padStart(2, '0') +
                " are retained in the bridge and native PA9.")
            if (source.heldItem == 0) {
                add("The source holds no item; the destination held-item field remains empty.")
            } else {
                add("Held item #" + source.heldItem +
                    " is explicitly removed because Gen 2 and Legends: Z-A item IDs are not interchangeable. Mail records remain blocked.")
            }
            add("Gen 2 Stat Exp " + source.statExperience.joinToString("/") +
                " maps to PA9 EVs " + bridge.evs.joinToString("/") +
                " by division by 257, splitting the single Special budget across SpA/SpD and reducing the largest values only when needed to cap the total at 510.")
            add("Gen 2 DVs " + source.dvs.joinToString("/") +
                " seed deterministic destination IVs " + bridge.ivs.joinToString("/") +
                ", including " + (if (source.species == 251) 5 else 3) +
                " required flawless transfer values. The source shiny state (" +
                source.shiny +
                ") and DV-derived gender are retained through the generated PID and gender field.")
            add("Nature is source EXP modulo 25. SID becomes 0. Encryption constant, PID and IVs are deterministic Legalise values bound to the exact PSE2 bytes, Legends: Z-A trainer and reviewed species metadata; PID does not certify origin.")
            add("Ability " + metadata.abilityId + " in slot " +
                metadata.abilityNumber + " and gender ratio " +
                metadata.genderRate +
                " are the documented metadata accepted for this PA9 review. Source happiness is retained instead of replacing it with species base friendship.")
            add("Origin is retained as " + game + " (#" + source.sourceGame +
                "), with a Poké Ball, PSE Johto met marker 30017, source level and unset encounter date. " +
                if (source.legacyGoldFallback)
                    "This legacy PSE2 record identifies Gold/Silver but cannot distinguish the pair, so Gold is the disclosed fallback."
                else "The source-title marker is exact.")
            if (source.caughtData == 0) {
                add("The source has no packed caught-data value.")
            } else {
                add("Packed Gen 2 caught data 0x" +
                    source.caughtData.toString(16).uppercase().padStart(4, '0') +
                    " is explicitly omitted because it has no exact PA9 field mapping.")
            }
            add("The validated Legends: Z-A trainer becomes current handling trainer; OT gender defaults to male because the PSE2 record does not encode it. Geographic, ribbon, memory, relearn, marking and Hyper Training fields remain empty; Alpha, Scale, Mega and PA9-only fields use the reviewed downstream defaults.")
            addAll(downstream)
            add("The PSE2 Bank entry and Legends: Z-A source remain unchanged. One reviewed empty native PA9 box slot is written only to a new copy with the required native caught registration, retained recovery, persisted reopen verification and two-sided rollback.")
        }
        return Gen2ToLegendsZaReview(
            converted.nativeRecord.copyOf(),
            source.species,
            source.nickname,
            effects
        )
    }
}
