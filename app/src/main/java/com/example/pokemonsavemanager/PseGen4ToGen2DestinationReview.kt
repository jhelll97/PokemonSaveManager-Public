package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN2_FROM_GEN4_BOX_COUNT=14
private const val PSE_GEN2_FROM_GEN4_SLOTS_PER_BOX=20
private const val PSE_GEN2_FROM_GEN4_CAPACITY=PSE_GEN2_FROM_GEN4_BOX_COUNT*PSE_GEN2_FROM_GEN4_SLOTS_PER_BOX

internal fun MainActivity.reviewGen4ToGen2Destination(
    p:MainActivity.Gen4Gen2Prepared,targetBox:Int,targetSlot:Int
){
    val canTransfer={gen4ToGen2StillBound(p,targetBox,targetSlot)}
    val transferLabel="Transfer • Box $targetBox of $PSE_GEN2_FROM_GEN4_BOX_COUNT / Slot ${targetSlot+1} of $PSE_GEN2_FROM_GEN4_SLOTS_PER_BOX"
    val preview=buildString{
        append("Final PSE Gen 4 → Gen 2 destination and omission review\n\n")
        append("Chosen destination: Box $targetBox of $PSE_GEN2_FROM_GEN4_BOX_COUNT, Slot ${targetSlot+1} of $PSE_GEN2_FROM_GEN4_SLOTS_PER_BOX, within the native $PSE_GEN2_FROM_GEN4_CAPACITY-slot PC. The slot is the selected Box's next structurally available native record; existing destination records remain unchanged.\n\n")
        append("Origin and provenance: Generation 2 has no native fields for the PK4 source title, PID, SID, met or Egg locations and dates, Ball, ability, nature, markings, ribbons or fateful marker. Their reviewed omission is unchanged, caught-data fields remain 0, and PSE does not replace them with guessed provenance.\n")
        append("Species #${p.source.species}, nickname, OT/TID, experience, moves, current PP, PP Ups, friendship and Pokérus remain represented. The previously reviewed IV-to-DV, EV-to-Stat-Exp, level and shiny-result mappings are unchanged. Unsupported species, moves, forms, held items, Eggs, text and invalid fields remain blocked.\n\n")
        append("Cancel leaves the exact PK4 Bank record and destination byte-for-byte unchanged. This is a PSE conversion, not an in-game trade, official migration, legality certification or verified provenance. The exact Gen 4 Bank record remains until the chosen PK2, retained recovery data, Pokédex state, checksum and reopened destination all verify.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${p.source.nickname.ifBlank{"Pokémon #${p.source.species}"}} • Gen 4 → Gen 2")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!canTransfer()){
                showDialog("Gen 4 → Gen 2","The destination identity, Bank record or selected Box slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            transferGen4BankToGen2(
                p.bankFile,p.source,p.bankBytes,p.sourceBinding,p.mon,p.ot,p.nick,targetBox,
                p.destinationFile,p.destination,p.sourceUri,p.sourceLabel,p.profileKey
            )
        }.setNegativeButton("Cancel",null).show()
}
