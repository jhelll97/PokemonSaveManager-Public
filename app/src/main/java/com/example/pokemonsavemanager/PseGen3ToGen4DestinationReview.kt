package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN4_BOX_COUNT=18
private const val PSE_GEN4_SLOTS_PER_BOX=30
private const val PSE_GEN4_CAPACITY=PSE_GEN4_BOX_COUNT*PSE_GEN4_SLOTS_PER_BOX

internal fun MainActivity.chooseGen3ToGen4Destination(p:MainActivity.Gen3Gen4Prepared){
    if(!preparedGen3ToGen4StillBound(p))return showDialog("Gen 3 → Gen 4","The destination identity or prepared snapshots changed. Reopen PSE Bank.")
    val profile=detectGen4Profile(p.destination)?:return
    val first=(0 until PSE_GEN4_CAPACITY).firstOrNull{gen4DestinationSlotIsStructurallyEmpty(p.destination,profile,it)}
        ?:return showDialog("Gen 3 → Gen 4","No structurally empty Gen 4 PC slot is available. Nonzero records were left unchanged.")
    AlertDialog.Builder(this).setTitle("Choose native Gen 4 destination")
        .setMessage("PSE will write only to a completely zero-filled, unparsable PK4 slot. Nonzero destination records remain unchanged.")
        .setItems(arrayOf("First structurally empty • Box ${first/PSE_GEN4_SLOTS_PER_BOX+1} • Slot ${first%PSE_GEN4_SLOTS_PER_BOX+1}","Choose a box / slot")){_,which->
            if(which==0)reviewGen3ToGen4Destination(p,first) else showGen3ToGen4BoxPicker(p)
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.showGen3ToGen4BoxPicker(p:MainActivity.Gen3Gen4Prepared){
    if(!preparedGen3ToGen4StillBound(p))return showDialog("Gen 3 → Gen 4","The destination identity or prepared snapshots changed. Reopen PSE Bank.")
    val profile=detectGen4Profile(p.destination)?:return
    val boxes=(0 until PSE_GEN4_BOX_COUNT).filter{box->(0 until PSE_GEN4_SLOTS_PER_BOX).any{slot->
        gen4DestinationSlotIsStructurallyEmpty(p.destination,profile,box*PSE_GEN4_SLOTS_PER_BOX+slot)
    }}
    AlertDialog.Builder(this).setTitle("Choose Gen 4 box")
        .setMessage("Only structurally empty slots are listed. Nonzero destination records remain unchanged.")
        .setItems(boxes.map{box->val count=(0 until PSE_GEN4_SLOTS_PER_BOX).count{slot->gen4DestinationSlotIsStructurallyEmpty(p.destination,profile,box*PSE_GEN4_SLOTS_PER_BOX+slot)};"Box ${box+1} • $count structurally empty"}.toTypedArray()){_,which->
            val box=boxes[which]
            val slots=(0 until PSE_GEN4_SLOTS_PER_BOX).filter{slot->gen4DestinationSlotIsStructurallyEmpty(p.destination,profile,box*PSE_GEN4_SLOTS_PER_BOX+slot)}
            AlertDialog.Builder(this).setTitle("Box ${box+1} • choose slot")
                .setItems(slots.map{"Slot ${it+1}"}.toTypedArray()){_,slotIndex->reviewGen3ToGen4Destination(p,box*PSE_GEN4_SLOTS_PER_BOX+slots[slotIndex])}
                .setNegativeButton("Back"){_,_->showGen3ToGen4BoxPicker(p)}.setNeutralButton("Cancel",null).show()
        }.setNegativeButton("Back"){_,_->chooseGen3ToGen4Destination(p)}.setNeutralButton("Cancel",null).show()
}

private fun MainActivity.reviewGen3ToGen4Destination(p:MainActivity.Gen3Gen4Prepared,target:Int){
    showPseGen3ToGen4DestinationReview(
        p.originalSource,p.source,p.converted,target,
        {
            preparedGen3ToGen4StillBound(p)&&runCatching{
                val current=p.destinationFile.readBytes();val currentProfile=detectGen4Profile(current)
                currentProfile!=null&&target in 0 until PSE_GEN4_CAPACITY&&gen4DestinationSlotIsStructurallyEmpty(current,currentProfile,target)
            }.getOrDefault(false)
        },{transferGen3BankToGen4(p,target)}
    )
}

internal fun MainActivity.showPseGen3ToGen4DestinationReview(
    originalSource:MainActivity.PokemonEntry,
    conversionSource:MainActivity.PokemonEntry,
    converted:MainActivity.PokemonEntry,
    target:Int,
    canTransfer:()->Boolean,
    onTransfer:()->Unit
){
    val box=target/PSE_GEN4_SLOTS_PER_BOX+1
    val slot=target%PSE_GEN4_SLOTS_PER_BOX+1
    val destinationLabel="Chosen destination: Box $box of $PSE_GEN4_BOX_COUNT, Slot $slot of $PSE_GEN4_SLOTS_PER_BOX, within the native $PSE_GEN4_CAPACITY-slot PC."
    val transferLabel="Transfer • Box $box of $PSE_GEN4_BOX_COUNT / Slot $slot of $PSE_GEN4_SLOTS_PER_BOX"
    val oldPid="%08X".format(originalSource.pid)
    val newPid="%08X".format(converted.pid)
    val pidLine=if(originalSource.pid==converted.pid)"PID remains $oldPid." else
        "PID changes $oldPid → $newPid only to preserve the reviewed ability slot, nature, gender and shiny state in native PK4."
    val preview=buildString{
        append("Final PSE Gen 3 → Gen 4 destination review\n\n")
        append(destinationLabel).append(" The slot is structurally empty; existing and nonzero destination records remain unchanged.\n\n")
        append(pseGen3BallReviewLine(originalSource.ball,conversionSource.ball,4))
        append(pidLine).append('\n')
        append("Ability: source slot ${originalSource.abilityNumber}, ability #${originalSource.ability} → verified Gen 4 ability #${converted.ability}.\n")
        append("The previously reviewed Pal Park location, migration date, represented fields, Pokédex update and checksum scope are unchanged. Cancel leaves the exact Bank record and destination byte-for-byte unchanged.\n\n")
        append("This is a PSE conversion using Gen 4-compatible migration semantics, not an in-game trade or a claim of official transfer provenance. The exact Gen 3 Bank record remains until the chosen PK4, retained recovery data, Pokédex state, checksums and reopened destination all verify.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${originalSource.nickname.ifBlank{"Pokémon #${originalSource.species}"}} • Gen 3 → Gen 4")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!canTransfer()){
                showDialog("Gen 3 → Gen 4","The destination identity, Bank record or selected slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            onTransfer()
        }.setNegativeButton("Cancel",null).show()
}
