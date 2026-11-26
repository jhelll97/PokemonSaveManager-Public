package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN1_FROM_GEN3_BOX_COUNT=12
private const val PSE_GEN1_FROM_GEN3_SLOTS_PER_BOX=20
private const val PSE_GEN1_FROM_GEN3_CAPACITY=PSE_GEN1_FROM_GEN3_BOX_COUNT*PSE_GEN1_FROM_GEN3_SLOTS_PER_BOX

internal fun MainActivity.reviewGen3ToGen1Destination(
    p:MainActivity.Gen3Gen1Prepared,targetBox:Int,targetSlot:Int
){
    val canTransfer={gen3ToGen1StillBound(p,targetBox,targetSlot)}
    val transferLabel="Transfer • Box $targetBox of $PSE_GEN1_FROM_GEN3_BOX_COUNT / Slot ${targetSlot+1} of $PSE_GEN1_FROM_GEN3_SLOTS_PER_BOX"
    val preview=buildString{
        append("Final PSE Gen 3 → Gen 1 destination, Legalise and omission review\n\n")
        append("Chosen destination: Box $targetBox of $PSE_GEN1_FROM_GEN3_BOX_COUNT, Slot ${targetSlot+1} of $PSE_GEN1_FROM_GEN3_SLOTS_PER_BOX, within the native $PSE_GEN1_FROM_GEN3_CAPACITY-slot PC. The slot is the selected Box's next structurally available native record; existing destination records remain unchanged.\n\n")
        val omissionPlan=p.legalise
        if(omissionPlan!=null)append("Accepted Gen 1 omission Legalise: friendship ${omissionPlan.originalHappiness}, Pokérus 0x${omissionPlan.originalPokerus.toString(16).uppercase().padStart(2,'0')}, and Generation 2 bridge caught data 0x${omissionPlan.originalCaughtData.toString(16).uppercase().padStart(4,'0')} are omitted because Generation 1 has no fields for them. No other metadata removal is authorized.\n")
        else append("Gen 1 omission handling: friendship is already the Generation 2 bridge baseline and Pokérus/caught data are already empty, so no additional omission Legalise applies.\n")
        append("Origin and provenance: Generation 1 has no native fields for Gen 3 origin game ID ${p.source.originGame}, PID ${"%08X".format(p.source.pid)}, SID ${p.source.sid}, met or Egg locations and dates, Ball ${p.source.ball}, ability #${p.source.ability}/slot ${p.source.abilityNumber}, nature, gender, markings, ribbons or fateful metadata. Catch rate remains the reviewed value 0, and PSE does not assign intermediate provenance.\n")
        append("Species #${p.source.species}, nickname, OT/TID, experience, all moves, current PP and PP Ups remain represented. The previously reviewed IV-to-DV, EV-to-Stat-Exp, level and shiny-result mappings are unchanged. Unsupported species or moves, alternate forms, held items, Eggs, text and invalid fields remain blocked; moves are never silently removed.\n\n")
        append("Cancel leaves the exact PK3 Bank record and destination byte-for-byte unchanged. This is a PSE conversion, not an in-game trade, Time Capsule transfer, official migration, legality certification or verified provenance. The exact Gen 3 Bank record remains until the chosen PK1, retained recovery data, Seen/Caught state, checksum and reopened destination all verify.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${p.source.nickname.ifBlank{"Pokémon #${p.source.species}"}} • Gen 3 → Gen 1")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!canTransfer()){
                showDialog("Gen 3 → Gen 1","The destination identity, Bank record or selected Box slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            transferGen3BankToGen1(
                p.bankFile,p.source,p.bankBytes,p.bridge,p.converted,p.sourceBinding,
                p.destination,p.meta,targetBox,p.destinationFile,p.legalise,p.sourceUri,p.sourceLabel
            )
        }.setNegativeButton("Cancel",null).show()
}
