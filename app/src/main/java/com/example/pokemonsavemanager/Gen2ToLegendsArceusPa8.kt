package com.example.pokemonsavemanager

data class Gen2ToLegendsArceusReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val effects: List<String>
)

/**
 * Direct PSE2 -> PA8 adapter through the deterministic reviewed PK7 bridge.
 *
 * The preserved 62-byte PSE2 file remains the transaction source. Hisui species,
 * profile, move, pasture, Pokédex, and integrity rules gate the native write.
 */
object Gen2ToLegendsArceusPa8 {
    fun review(
        record: ByteArray,
        destinationTrainer: LegendsArceusTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): Gen2ToLegendsArceusReview {
        val bridge = Gen2ToSwordShieldPk8.buildLegendsArceusBridge(
            record, destinationTrainer, metadata
        )
        val source = bridge.source
        val converted = Gen7ToLegendsArceusPa8.review(bridge.pk7)
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
            add("Explicit Legalise: direct PSE Gen 2 PSE2 -> deterministic in-memory PK7 bridge -> Legends: Arceus PA8 conversion; not an official transfer, HOME move, trade, or verified provenance.")
            add("The exact selected 62-byte PSE2 record remains the bound Bank source. Species #" +
                source.species + ", TID " + source.trainerId +
                ", OT text, nickname text, exact EXP " + source.experience +
                ", level " + source.level + " and move IDs are retained.")
            add("Generation 2 happiness " + source.happiness +
                " and Pokérus byte 0x" +
                source.pokerus.toString(16).uppercase().padStart(2, '0') +
                " are retained in the bridge and native PA8.")
            if (source.heldItem == 0) {
                add("The source holds no item; PA8 has no held-item field.")
            } else {
                add("Held item #" + source.heldItem +
                    " is explicitly removed because PA8 has no held-item field. Mail records remain blocked.")
            }
            add("Gen 2 Stat Exp " + source.statExperience.joinToString("/") +
                " maps to stored PA8 EV bytes " + bridge.evs.joinToString("/") +
                " by division by 257, splitting the single Special budget across SpA/SpD and reducing the largest values only when needed to cap the total at 510.")
            add("Gen 2 DVs " + source.dvs.joinToString("/") +
                " seed deterministic destination IVs " + bridge.ivs.joinToString("/") +
                ", including " + (if (source.species == 251) 5 else 3) +
                " required flawless transfer values. The source shiny state (" +
                source.shiny +
                ") and DV-derived gender are retained through the generated PID and gender field.")
            add("Current PP " + source.currentPp.joinToString("/") +
                " is normalized to Legends: Arceus fixed base PP " +
                bridge.normalizedPp.joinToString("/") +
                ". Generation 2 PP-Up counts " + source.ppUps.joinToString("/") +
                " are explicitly cleared because PA8 has no PP-Up representation.")
            add("Nature is source EXP modulo 25. SID becomes 0. Encryption constant, PID and IVs are deterministic Legalise values bound to the exact PSE2 bytes, Legends: Arceus trainer and reviewed species metadata; PID does not certify origin.")
            add("Ability " + metadata.abilityId + " in slot " +
                metadata.abilityNumber + " and gender ratio " +
                metadata.genderRate +
                " are the documented metadata accepted for this PA8 review. Source happiness is retained instead of replacing it with species base friendship.")
            add("Origin is retained as " + game + " (#" + source.sourceGame +
                "), with the PSE Johto met marker 30017, source level and unset encounter date. The bridge Poké Ball becomes the disclosed PA8 Strange Ball mapping. " +
                if (source.legacyGoldFallback)
                    "This legacy PSE2 record identifies Gold/Silver but cannot distinguish the pair, so Gold is the disclosed fallback."
                else "The source-title marker is exact.")
            if (source.caughtData == 0) {
                add("The source has no packed caught-data value.")
            } else {
                add("Packed Gen 2 caught data 0x" +
                    source.caughtData.toString(16).uppercase().padStart(4, '0') +
                    " is explicitly omitted because it has no exact PA8 field mapping.")
            }
            add("The validated Legends: Arceus trainer becomes current handling trainer; OT gender defaults to male because the PSE2 record does not encode it. Geographic, ribbon, memory, relearn, marking, Hyper Training, Alpha and Ganbaru fields remain empty or at reviewed defaults.")
            addAll(downstream)
            add("The PSE2 Bank entry and Legends: Arceus source remain unchanged. One reviewed empty native PA8 pasture slot is written only to a new copy with the required native obtained count, retained recovery, persisted reopen verification and two-sided rollback.")
        }
        return Gen2ToLegendsArceusReview(
            converted.nativeRecord.copyOf(),
            source.species,
            source.nickname,
            effects
        )
    }
}
