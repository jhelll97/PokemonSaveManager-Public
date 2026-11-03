package com.example.pokemonsavemanager

import java.util.Collections

data class SwitchDashboardEntry(val section: String, val label: String, val enabled: Boolean)

/** Read-only navigation uses per-file readers, never planned capabilities or family hints as proof. */
object SwitchReadDashboard {
    fun entries(session: SwitchReadSession): List<SwitchDashboardEntry> {
        val storage = when (session.profile.family) {
            SwitchGameFamily.LEGENDS_ARCEUS -> "Pokémon Pastures"
            SwitchGameFamily.LETS_GO_PIKACHU_EEVEE -> "Pokémon Storage"
            else -> "Pokémon Boxes"
        }
        return Collections.unmodifiableList(listOf(
            SwitchDashboardEntry("trainer", "Trainer Data", session.supports(SwitchReadCapability.TRAINER_PROFILE)),
            SwitchDashboardEntry("storage", storage, session.supports(SwitchReadCapability.PC_OR_BOXES) || session.supports(SwitchReadCapability.PASTURES)),
            SwitchDashboardEntry("party", "Party Pokémon", session.supports(SwitchReadCapability.PARTY)),
            SwitchDashboardEntry("inventory", "Items", session.supports(SwitchReadCapability.INVENTORY)),
            SwitchDashboardEntry("pokedex", "Pokédex", session.supports(SwitchReadCapability.POKEDEX))
        ))
    }
}
