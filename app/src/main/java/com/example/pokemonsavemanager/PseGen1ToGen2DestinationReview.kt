package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN2_FROM_GEN1_BOX_COUNT=14
private const val PSE_GEN2_FROM_GEN1_SLOTS_PER_BOX=20
private const val PSE_GEN2_FROM_GEN1_CAPACITY=PSE_GEN2_FROM_GEN1_BOX_COUNT*PSE_GEN2_FROM_GEN1_SLOTS_PER_BOX

internal fun MainActivity.reviewGen1ToGen2Destination(
    p:MainActivity.Gen1Gen2Prepared,targetBox:Int,targetSlot:Int
){
    val heldItem=p.catchRateByte
    val preview=buildString{
        append("Final PSE Gen 1 → Gen 2 destination and mapping review\n\n")
        append("Chosen destination: Box $targetBox of $PSE_GEN2_FROM_GEN1_BOX_COUNT, Slot ${targetSlot+1} of $PSE_GEN2_FROM_GEN1_SLOTS_PER_BOX, within the native $PSE_GEN2_FROM_GEN1_CAPACITY-slot PC. This is the selected Box's exact next packed native record; existing destination records remain unchanged.\n\n")
        append("Species, nickname, OT/TID, level, experience, Stat Exp, DVs, all moves, current PP and PP Ups remain represented. Gen 1 catch-rate byte $heldItem becomes Gen 2 held-item ID $heldItem through the reviewed Time Capsule-shaped field mapping. Gen 2 friendship becomes 70; Pokérus and caught/met data become 0 because Generation 1 has no fields for them.\n")
        append("No move is removed or replaced. Cancel leaves the exact PSE1 Bank record and destination byte-for-byte unchanged.\n\n")
        append("This is a PSE native-format conversion, not an in-game trade, official Time Capsule transfer, legality certification or verified provenance. The exact Gen 1 Bank record remains until the chosen native Gen 2 record, retained recovery data, Seen/Owned state, primary checksum and reopened destination all verify.")
    }
    var consumed=false
    val transferLabel="Transfer • Box $targetBox of $PSE_GEN2_FROM_GEN1_BOX_COUNT / Slot ${targetSlot+1} of $PSE_GEN2_FROM_GEN1_SLOTS_PER_BOX"
    AlertDialog.Builder(this).setTitle("${p.source.nickname.ifBlank{"Pokémon"}} • Gen 1 → Gen 2")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!gen1ToGen2StillBound(p,targetBox,targetSlot)){
                showDialog("Gen 1 → Gen 2 PSE conversion","The destination identity, Bank record, save profile or selected Box slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            transferGen1BankToGen2(
                p.bankFile,p.source,p.bankBytes,targetBox,p.destinationFile,p.destination,
                p.sourceUri,p.sourceLabel,p.profileKey
            )
        }.setNegativeButton("Cancel",null).show()
}
