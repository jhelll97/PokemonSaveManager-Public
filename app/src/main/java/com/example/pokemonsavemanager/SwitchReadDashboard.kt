package com.example.pokemonsavemanager

import java.util.Collections

data class SwitchDashboardEntry(val section: String, val label: String, val enabled: Boolean)

/** Navigation uses per-file readers, never planned capabilities or family hints as proof. */
object SwitchReadDashboard {
    fun entries(session: SwitchReadSession): List<SwitchDashboardEntry> =
        Collections.unmodifiableList(listOf(
            SwitchDashboardEntry("trainer", "◎  Trainer Data", session.supports(SwitchReadCapability.TRAINER_PROFILE)),
            SwitchDashboardEntry(
                "storage",
                "◉  Pokémon Boxes",
                session.supports(SwitchReadCapability.PARTY) &&
                    (session.supports(SwitchReadCapability.PC_OR_BOXES) || session.supports(SwitchReadCapability.PASTURES))
            ),
            SwitchDashboardEntry("inventory", "▦  Items", session.supports(SwitchReadCapability.INVENTORY)),
            SwitchDashboardEntry("pokedex", "Pokédex", session.supports(SwitchReadCapability.POKEDEX))
        ))
}
