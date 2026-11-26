package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN2_FROM_GEN5_BOX_COUNT=14
private const val PSE_GEN2_FROM_GEN5_SLOTS_PER_BOX=20
private const val PSE_GEN2_FROM_GEN5_CAPACITY=PSE_GEN2_FROM_GEN5_BOX_COUNT*PSE_GEN2_FROM_GEN5_SLOTS_PER_BOX

internal fun MainActivity.reviewGen5ToGen2Destination(
    p:MainActivity.Gen5Gen2Prepared,targetBox:Int,targetSlot:Int
){
    val canTransfer={gen5ToGen2StillBound(p,targetBox,targetSlot)}
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
    val transferLabel="Transfer • Box $targetBox of $PSE_GEN2_FROM_GEN5_BOX_COUNT / Slot ${targetSlot+1} of $PSE_GEN2_FROM_GEN5_SLOTS_PER_BOX"
    val preview=buildString{
        append("Final PSE Gen 5 → Gen 2 destination, move and omission review\n\n")
        append("Chosen destination: Box $targetBox of $PSE_GEN2_FROM_GEN5_BOX_COUNT, Slot ${targetSlot+1} of $PSE_GEN2_FROM_GEN5_SLOTS_PER_BOX, within the native $PSE_GEN2_FROM_GEN5_CAPACITY-slot PC. The slot is the selected Box's next structurally available native record; existing destination records remain unchanged.\n\n")
        append(moveLine).append("\n\n")
        append("Origin and provenance: Generation 2 has no native fields for Gen 5 origin game ID ${p.source.originGame}, PID, SID, met or Egg locations and dates, Ball, ability, nature, markings, ribbons or fateful marker. Their reviewed omission is unchanged, caught-data fields remain 0, and PSE does not assign intermediate provenance.\n")
        append("Species #${p.source.species}, nickname, OT/TID, experience, retained moves, friendship and Pokérus remain represented. The previously reviewed IV-to-DV, EV-to-Stat-Exp, level and shiny-result mappings are unchanged. Unsupported species, forms, held items, Eggs, text and invalid fields remain blocked.\n\n")
        append("Cancel leaves the exact PK5 Bank record and destination byte-for-byte unchanged. This is a PSE conversion, not an in-game trade, official migration, legality certification or verified provenance. The exact Gen 5 Bank record remains until the chosen PK2, retained recovery data, Pokédex state, checksum and reopened destination all verify.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${p.source.nickname.ifBlank{"Pokémon #${p.source.species}"}} • Gen 5 → Gen 2")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!canTransfer()){
                showDialog("Gen 5 → Gen 2","The destination identity, Bank record or selected Box slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            transferGen5BankToGen2(
                p.bankFile,p.source,p.bankBytes,p.sourceBinding,p.mon,p.ot,p.nick,p.confirmedMovePlan,targetBox,
                p.destinationFile,p.destination,p.sourceUri,p.sourceLabel,p.profileKey
            )
        }.setNegativeButton("Cancel",null).show()
}
