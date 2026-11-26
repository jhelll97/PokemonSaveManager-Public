package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN1_FROM_GEN5_BOX_COUNT=12
private const val PSE_GEN1_FROM_GEN5_SLOTS_PER_BOX=20
private const val PSE_GEN1_FROM_GEN5_CAPACITY=PSE_GEN1_FROM_GEN5_BOX_COUNT*PSE_GEN1_FROM_GEN5_SLOTS_PER_BOX

internal fun MainActivity.reviewGen5ToGen1Destination(
    p:MainActivity.Gen5Gen1Prepared,targetBox:Int,targetSlot:Int
){
    val canTransfer={gen5ToGen1StillBound(p,targetBox,targetSlot)}
    val moveLine=p.confirmedMovePlan?.takeIf{it.removed.isNotEmpty()}?.let{plan->
        buildString{
            append("Accepted move Legalise remains limited to: ")
            append(plan.removed.joinToString("; "){"source slot ${it.sourceSlot+1} move #${it.move} (PP ${it.pp}, PP Ups ${it.ppUps}) removed"})
            append(". Final native slots: ")
            append(plan.destinationMoves.indices.joinToString("; "){i->
                val move=plan.destinationMoves[i]
                if(move==0)"slot ${i+1} empty" else "slot ${i+1} move #$move (PP ${plan.destinationPp[i]}, PP Ups ${plan.destinationPpUps[i]})"
            })
            append(". No other move is removed or rewritten.")
        }
    }?:"All four move slots, current PP and PP Ups remain represented without a move Legalise change."
    val transferLabel="Transfer • Box $targetBox of $PSE_GEN1_FROM_GEN5_BOX_COUNT / Slot ${targetSlot+1} of $PSE_GEN1_FROM_GEN5_SLOTS_PER_BOX"
    val preview=buildString{
        append("Final PSE Gen 5 → Gen 1 destination, move, Legalise and omission review\n\n")
        append("Chosen destination: Box $targetBox of $PSE_GEN1_FROM_GEN5_BOX_COUNT, Slot ${targetSlot+1} of $PSE_GEN1_FROM_GEN5_SLOTS_PER_BOX, within the native $PSE_GEN1_FROM_GEN5_CAPACITY-slot PC. The slot is the selected Box's next structurally available native record; existing destination records remain unchanged.\n\n")
        append(moveLine).append("\n\n")
        val omissionPlan=p.legalise
        if(omissionPlan!=null)append("Accepted Gen 1 omission Legalise: friendship ${omissionPlan.originalHappiness}, Pokérus 0x${omissionPlan.originalPokerus.toString(16).uppercase().padStart(2,'0')}, and Generation 2 bridge caught data 0x${omissionPlan.originalCaughtData.toString(16).uppercase().padStart(4,'0')} are omitted because Generation 1 has no fields for them. No other metadata removal is authorized.\n")
        else append("Gen 1 omission handling: friendship is already the Time Capsule baseline and Pokérus/caught data are already empty, so no additional omission Legalise applies.\n")
        append("Origin and provenance: Generation 1 has no native fields for Gen 5 origin game ID ${p.source.originGame}, PID ${"%08X".format(p.source.pid)}, SID ${p.source.sid}, met or Egg locations and dates, Ball ${p.source.ball}, ability #${p.source.ability}/slot ${p.source.abilityNumber}, nature, gender, markings, ribbons or fateful metadata. Catch rate remains the reviewed value 0, and PSE does not assign intermediate provenance.\n")
        append("Species #${p.source.species}, nickname, OT/TID, experience and every retained move remain represented. The previously reviewed IV-to-DV, EV-to-Stat-Exp, level and shiny-result mappings are unchanged. Unsupported species, alternate forms, held items, Eggs, text and invalid fields remain blocked; unsupported moves are removed only through the explicit accepted plan above.\n\n")
        append("Cancel leaves the exact PK5 Bank record and destination byte-for-byte unchanged. This is a PSE conversion, not an in-game trade, Time Capsule transfer, official migration, legality certification or verified provenance. The exact Gen 5 Bank record remains until the chosen PK1, retained recovery data, Seen/Caught state, checksum and reopened destination all verify.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${p.source.nickname.ifBlank{"Pokémon #${p.source.species}"}} • Gen 5 → Gen 1")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!canTransfer()){
                showDialog("Gen 5 → Gen 1","The destination identity, Bank record or selected Box slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            transferGen5BankToGen1(
                p.bankFile,p.source,p.bankBytes,p.sourceBinding,p.bridge,p.converted,p.confirmedMovePlan,
                p.destination,p.meta,targetBox,p.destinationFile,p.legalise,p.sourceUri,p.sourceLabel
            )
        }.setNegativeButton("Cancel",null).show()
}
