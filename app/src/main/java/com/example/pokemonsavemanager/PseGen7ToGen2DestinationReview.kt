package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN2_FROM_GEN7_BOX_COUNT=14
private const val PSE_GEN2_FROM_GEN7_SLOTS_PER_BOX=20
private const val PSE_GEN2_FROM_GEN7_CAPACITY=PSE_GEN2_FROM_GEN7_BOX_COUNT*PSE_GEN2_FROM_GEN7_SLOTS_PER_BOX

internal fun MainActivity.reviewGen7ToGen2Destination(
    p:MainActivity.Gen7Gen2Prepared,targetBox:Int,targetSlot:Int
){
    val canTransfer={gen7ToGen2StillBound(p,targetBox,targetSlot)}
    val transferLabel="Transfer • Box $targetBox of $PSE_GEN2_FROM_GEN7_BOX_COUNT / Slot ${targetSlot+1} of $PSE_GEN2_FROM_GEN7_SLOTS_PER_BOX"
    val preview=buildString{
        append("Final PSE Gen 7 → Gen 2 destination, form and omission review\n\n")
        append("Chosen destination: Box $targetBox of $PSE_GEN2_FROM_GEN7_BOX_COUNT, Slot ${targetSlot+1} of $PSE_GEN2_FROM_GEN7_SLOTS_PER_BOX, within the native $PSE_GEN2_FROM_GEN7_CAPACITY-slot PC. The slot is the selected Box's next structurally available native record; existing destination records remain unchanged.\n\n")
        val plan=p.confirmedFormPlan
        if(plan!=null){
            append("Accepted form Legalise: ${plan.kind} ID ${plan.sourceForm} → native base form ${plan.destinationForm}. This is the only authorized form normalization; the destination uses species #${plan.species}'s Generation 2 identity, typing and stats, while the regional or cosmetic form identity is omitted.\n")
        }else{
            append("Form handling: the source is already native base form 0, so no form Legalise or form normalization is applied.\n")
        }
        append("Origin and provenance: Generation 2 has no native fields for Gen 7 origin game ID ${p.source.originGame}, PID ${"%08X".format(p.source.pid)}, SID ${p.source.sid}, met or Egg locations and dates, Ball ${p.source.ball}, ability #${p.source.ability}/slot ${p.source.abilityNumber}, nature, gender, markings, handler data, relearn moves, ribbons, geography, affection, memory, Resort state or fateful metadata. Their reviewed omission is unchanged, caught-data fields remain 0, and PSE does not assign intermediate provenance.\n")
        append("Exact previously reviewed PK7-only metadata remains omitted: ${p.omittedMetadata}.\n")
        append("Species #${p.source.species}, nickname, OT/TID, experience, all moves, current PP, PP Ups, friendship and Pokérus remain represented. The previously reviewed IV-to-DV, EV-to-Stat-Exp, level and shiny-result mappings are unchanged. Unsupported species or moves, unreviewed forms, held items, Eggs, Hyper Training, text and invalid fields remain blocked; moves are never silently removed.\n\n")
        append("Cancel leaves the exact PK7 Bank record and destination byte-for-byte unchanged. This is a PSE conversion, not an in-game trade, official migration, legality certification or verified provenance. The exact Gen 7 Bank record remains until the chosen PK2, retained recovery data, Pokédex state, checksum and reopened destination all verify.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${p.source.nickname.ifBlank{"Pokémon #${p.source.species}"}} • Gen 7 → Gen 2")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!canTransfer()){
                showDialog("Gen 7 → Gen 2","The destination identity, Bank record or selected Box slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            transferGen7BankToGen2(
                p.bankFile,p.source,p.bankBytes,p.sourceBinding,p.mon,p.ot,p.nick,
                p.confirmedFormPlan,targetBox,p.destinationFile,p.destination,p.sourceUri,
                p.sourceLabel,p.profileKey
            )
        }.setNegativeButton("Cancel",null).show()
}
