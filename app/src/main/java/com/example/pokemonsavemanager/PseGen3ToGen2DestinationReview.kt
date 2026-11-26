package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN2_FROM_GEN3_BOX_COUNT=14
private const val PSE_GEN2_FROM_GEN3_SLOTS_PER_BOX=20
private const val PSE_GEN2_FROM_GEN3_CAPACITY=PSE_GEN2_FROM_GEN3_BOX_COUNT*PSE_GEN2_FROM_GEN3_SLOTS_PER_BOX

internal fun MainActivity.reviewGen3ToGen2Destination(
    p:MainActivity.Gen3Gen2Prepared,targetBox:Int,targetSlot:Int
){
    val canTransfer={gen3ToGen2StillBound(p,targetBox,targetSlot)}
    val transferLabel="Transfer • Box $targetBox of $PSE_GEN2_FROM_GEN3_BOX_COUNT / Slot ${targetSlot+1} of $PSE_GEN2_FROM_GEN3_SLOTS_PER_BOX"
    val preview=buildString{
        append("Final PSE Gen 3 → Gen 2 destination and omission review\n\n")
        append("Chosen destination: Box $targetBox of $PSE_GEN2_FROM_GEN3_BOX_COUNT, Slot ${targetSlot+1} of $PSE_GEN2_FROM_GEN3_SLOTS_PER_BOX, within the native $PSE_GEN2_FROM_GEN3_CAPACITY-slot PC. The slot is the selected Box's next structurally available native record; existing destination records remain unchanged.\n\n")
        append("Origin and provenance: Generation 2 has no native fields for Gen 3 origin game ID ${p.source.originGame}, PID ${"%08X".format(p.source.pid)}, SID ${p.source.sid}, met or Egg locations and dates, Ball ${p.source.ball}, ability #${p.source.ability}/slot ${p.source.abilityNumber}, nature, gender, markings, ribbons or fateful metadata. Caught-data fields and held-item byte remain 0, and PSE does not assign intermediate provenance.\n")
        append("Species #${p.source.species}, nickname, OT/TID, experience, friendship, Pokérus, all moves, current PP and PP Ups remain represented. The previously reviewed IV-to-DV, EV-to-Stat-Exp, level and shiny-result mappings are unchanged. Unsupported species or moves, alternate forms, held items, Eggs, text and invalid fields remain blocked; moves are never silently removed.\n\n")
        append("Cancel leaves the exact PK3 Bank record and destination byte-for-byte unchanged. This is a PSE conversion, not an in-game trade, official reverse migration, legality certification or verified provenance. The exact Gen 3 Bank record remains until the chosen PK2, retained recovery data, Owned/Seen state, checksum and reopened destination all verify.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${p.source.nickname.ifBlank{"Pokémon #${p.source.species}"}} • Gen 3 → Gen 2")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!canTransfer()){
                showDialog("Gen 3 → Gen 2","The destination identity, Bank record, save profile or selected Box slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            transferGen3BankToGen2(
                p.bankFile,p.source,p.bankBytes,p.sourceBinding,p.mon,p.ot,p.nick,targetBox,
                p.destinationFile,p.destination,p.sourceUri,p.sourceLabel,p.profileKey
            )
        }.setNegativeButton("Cancel",null).show()
}
