package com.example.pokemonsavemanager

data class SwitchSaveIdentity(val family: SwitchGameFamily, val game: String, val byteCount: Int, val sha256: String)

/** Identity-only discovery for the shared source library; semantic readers stay independent. */
object SwitchSaveDiscovery {
    fun inspect(snapshot: SwitchReadSnapshot): SwitchSaveIdentity? {
        fun found(family: SwitchGameFamily, game: String) = SwitchSaveIdentity(family, game, snapshot.byteCount, snapshot.sourceSha256)
        when (val v = snapshot.inspectSwordShield()) {
            is SwordShieldValidation.Verified -> return found(SwitchGameFamily.SWORD_SHIELD, v.version.displayName)
            is SwordShieldValidation.Invalid -> return null
            else -> Unit
        }
        when (val v = snapshot.inspectBdsp()) {
            is BdspValidation.Verified -> return found(SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, v.version.displayName)
            is BdspValidation.Invalid -> return null
            else -> Unit
        }
        when (val v = snapshot.inspectLegendsArceus()) {
            is LegendsArceusValidation.Verified -> return found(SwitchGameFamily.LEGENDS_ARCEUS, v.version.displayName)
            is LegendsArceusValidation.Invalid -> return null
            else -> Unit
        }
        when (val v = snapshot.inspectScarletViolet()) {
            is ScarletVioletValidation.Verified -> return found(SwitchGameFamily.SCARLET_VIOLET, v.version.displayName)
            is ScarletVioletValidation.Invalid -> return null
            else -> Unit
        }
        when (val v = snapshot.inspectLegendsZa()) {
            is LegendsZaValidation.Verified -> return found(SwitchGameFamily.LEGENDS_ZA, v.version.displayName)
            is LegendsZaValidation.Invalid -> return null
            else -> Unit
        }
        return when (val v = snapshot.inspectLetsGo()) {
            is LetsGoValidation.Verified -> found(SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, v.version.displayName)
            else -> null
        }
    }
}
