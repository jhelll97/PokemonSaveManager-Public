package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN1_FROM_GEN2_BOX_COUNT=12
private const val PSE_GEN1_FROM_GEN2_SLOTS_PER_BOX=20
private const val PSE_GEN1_FROM_GEN2_CAPACITY=PSE_GEN1_FROM_GEN2_BOX_COUNT*PSE_GEN1_FROM_GEN2_SLOTS_PER_BOX

internal fun MainActivity.reviewGen2ToGen1Destination(
    p:MainActivity.Gen2Gen1Prepared,targetBox:Int,targetSlot:Int
){
    val transferLabel="${if(p.legalise==null)"Transfer" else "Legalise & transfer"} • Box $targetBox of $PSE_GEN1_FROM_GEN2_BOX_COUNT / Slot ${targetSlot+1} of $PSE_GEN1_FROM_GEN2_SLOTS_PER_BOX"
    val preview=buildString{
        append("Final PSE Gen 2 → Gen 1 destination and omission review\n\n")
        append("Chosen destination: Box $targetBox of $PSE_GEN1_FROM_GEN2_BOX_COUNT, Slot ${targetSlot+1} of $PSE_GEN1_FROM_GEN2_SLOTS_PER_BOX, within the native $PSE_GEN1_FROM_GEN2_CAPACITY-slot PC. This is the selected Box's exact next native record; existing destination records remain unchanged.\n\n")
        if(p.legaliseSummary==null)append("No Gen 2 happiness, Pokérus or caught/met value requires omission.\n")
        else append("Explicit Legalise omission: ${p.legaliseSummary}. Generation 1 has no native fields for those accepted values; no other source field is included in this omission.\n")
        append("Species #${p.source.species}, nickname, OT/TID, level, experience, Stat Exp, DVs, all moves, current PP and PP Ups remain represented. Gen 2 held-item byte ${p.source.heldItem} becomes the Gen 1 catch-rate byte unchanged through the reviewed Time Capsule-shaped field mapping. Status becomes healthy and native Gen 1 types are rebuilt from canonical species data as already disclosed.\n")
        append("Eggs, post-Gen-1 species or moves, and Mail remain blocked; moves are never silently removed. Cancel leaves the exact PSE2 Bank record and destination byte-for-byte unchanged.\n\n")
        append("This is a PSE conversion, not an in-game trade, official Time Capsule transfer, legality certification or verified provenance. The exact Gen 2 Bank record remains until the chosen native Gen 1 record, retained recovery data, Seen/Caught state, checksum and reopened destination all verify.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${p.source.nickname.ifBlank{"Pokémon #${p.source.species}"}} • Gen 2 → Gen 1")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!gen2ToGen1StillBound(p,targetBox,targetSlot)){
                showDialog("Gen 2 → Gen 1","The destination identity, Bank record, Legalise plan or selected Box slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            transferGen2BankToGen1(
                p.bankFile,p.source,p.bankBytes,p.destination,p.meta,targetBox,p.destinationFile,
                p.legalise,p.sourceUri,p.sourceLabel
            )
        }.setNegativeButton("Cancel",null).show()
}
