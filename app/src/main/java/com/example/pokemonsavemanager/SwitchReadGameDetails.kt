package com.example.pokemonsavemanager

import java.util.Collections

data class SwitchDetailGroup(val label: String, val entries: List<SwitchDashboardEntry>)

/** Existing native BDSP readers only. A readable group never enables an unavailable member. */
object SwitchReadGameDetails {
    fun groups(session: SwitchReadSession): List<SwitchDetailGroup> {
        if (!session.isFormatVerified || session.profile.family != SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL) return emptyList()
        fun group(label: String, vararg entries: SwitchDashboardEntry): SwitchDetailGroup =
            SwitchDetailGroup(label, Collections.unmodifiableList(entries.toList()))
        fun entry(key: String, label: String, available: Boolean): SwitchDashboardEntry = SwitchDashboardEntry(key, label, available)
        return Collections.unmodifiableList(listOf(
            group("Trainer and settings",
                entry("appearance", "Appearance and position", session.bdspTrainerAppearance != null),
                entry("playtime", "Recorded playtime", session.bdspPlayTime != null),
                entry("timestamps", "Saved timestamps", session.bdspTimestamps != null),
                entry("options", "Game options", session.bdspGameOptions != null),
                entry("menu", "Saved menu layout", session.bdspMenuSelection != null)),
            group("Items and decoration",
                entry("poffins", "Poffin Case", session.bdspPoffins != null),
                entry("stickers", "Seal stickers", session.bdspSealStickers != null),
                entry("capsules", "Ball Capsules", session.bdspBallCapsules != null)),
            group("Exploration and Underground",
                entry("underground", "Grand Underground", session.bdspUnderground != null),
                entry("encounters", "Encounters", session.bdspEncounter != null),
                entry("honey", "Honey trees", session.bdspHoneyTrees != null),
                entry("berries", "Berry plot records", session.bdspBerryPlots != null),
                entry("secretbase", "Secret Base", session.bdspSecretBase != null),
                entry("npcs", "Underground NPC records", session.bdspUndergroundNpc != null),
                entry("return", "Underground return data", session.bdspUndergroundReturn != null),
                entry("activity", "Underground activity", session.bdspUndergroundActivity != null)),
            group("Battles and records",
                entry("tower", "Battle Tower", session.bdspBattleTower != null),
                entry("trainers", "Battle trainer status", session.bdspBattleTrainerStatus != null),
                entry("statistics", "Record statistics", session.bdspRecordStatistics != null),
                entry("additional", "Additional record trainers", session.bdspAdditionalRecords != null)),
            group("Nursery and Contest",
                entry("nursery", "Nursery", session.bdspNursery != null),
                entry("contest", "Contest records", session.bdspContest != null)),
            group("Connection records",
                entry("union", "Union Room saved data", session.bdspUnionRoom != null),
                entry("groups", "Random group records", session.bdspRandomGroups != null))
        ))
    }
}
