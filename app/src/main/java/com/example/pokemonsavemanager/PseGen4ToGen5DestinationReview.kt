package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN5_BOX_COUNT=24
private const val PSE_GEN5_SLOTS_PER_BOX=30
private const val PSE_GEN5_CAPACITY=PSE_GEN5_BOX_COUNT*PSE_GEN5_SLOTS_PER_BOX

private fun genToGen5CapacityLabel(sourceGeneration:Int,kind:Int,index:Int):String=when(kind){
    0 -> "First Structurally Empty • Box ${index/PSE_GEN5_SLOTS_PER_BOX+1} of $PSE_GEN5_BOX_COUNT • Slot ${index%PSE_GEN5_SLOTS_PER_BOX+1} of $PSE_GEN5_SLOTS_PER_BOX"
    1 -> "Box ${index+1} of $PSE_GEN5_BOX_COUNT"
    2 -> "Box ${index+1} of $PSE_GEN5_BOX_COUNT • choose slot"
    3 -> "Slot ${index+1} of $PSE_GEN5_SLOTS_PER_BOX"
    5 -> "All $PSE_GEN5_CAPACITY native Generation 5 PC slots are unavailable. Nonzero destination records and the Gen $sourceGeneration Bank source were left unchanged."
    6 -> "Chosen destination: Box ${index/PSE_GEN5_SLOTS_PER_BOX+1} of $PSE_GEN5_BOX_COUNT, Slot ${index%PSE_GEN5_SLOTS_PER_BOX+1} of $PSE_GEN5_SLOTS_PER_BOX, within the native $PSE_GEN5_CAPACITY-slot PC."
    else -> error("Unknown Gen 3 or Gen 4 to Gen 5 capacity label")
}

internal fun MainActivity.chooseGen4ToGen5Destination(p:MainActivity.Gen4Gen5Prepared){
    val title="Gen ${p.sourceGeneration} → Gen 5"
    if(!gen4ToGen5StillBound(p))return showDialog(title,"The destination or Bank record changed. Reopen PSE Bank.")
    if(detectGen5Profile(p.destination)==null)return
    val first=(0 until PSE_GEN5_CAPACITY).firstOrNull{gen5DestinationSlotIsStructurallyEmpty(p.destination,it)}
        ?:return showDialog(title,genToGen5CapacityLabel(p.sourceGeneration,5,0))
    AlertDialog.Builder(this).setTitle("Choose native Gen 5 destination")
        .setMessage("The reviewed PSE conversion will be written only to a structurally empty PK5 slot. The destination box checksum and Pokédex will be updated, and nonzero records remain unchanged.")
        .setItems(arrayOf(genToGen5CapacityLabel(p.sourceGeneration,0,first),"Choose Box / Slot • $PSE_GEN5_BOX_COUNT Boxes × $PSE_GEN5_SLOTS_PER_BOX slots")){_,which->
            if(!gen4ToGen5StillBound(p))showDialog(title,"The destination or Bank record changed. Reopen PSE Bank.")
            else if(which==0)reviewGenToGen5Destination(p,first)
            else showGen4ToGen5BoxPicker(p)
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.showGen4ToGen5BoxPicker(p:MainActivity.Gen4Gen5Prepared){
    val title="Gen ${p.sourceGeneration} → Gen 5"
    if(!gen4ToGen5StillBound(p))return showDialog(title,"The destination or Bank record changed. Reopen PSE Bank.")
    val boxes=(0 until PSE_GEN5_BOX_COUNT).filter{box->(0 until PSE_GEN5_SLOTS_PER_BOX).any{slot->
        gen5DestinationSlotIsStructurallyEmpty(p.destination,box*PSE_GEN5_SLOTS_PER_BOX+slot)
    }}
    if(boxes.isEmpty())return showDialog(title,genToGen5CapacityLabel(p.sourceGeneration,5,0))
    AlertDialog.Builder(this).setTitle("Choose Gen 5 destination box")
        .setMessage("Only boxes with structurally empty slots are listed. Nonzero destination records remain unchanged.")
        .setItems(boxes.map{genToGen5CapacityLabel(p.sourceGeneration,1,it)}.toTypedArray()){_,which->
            val box=boxes[which]
            val slots=(0 until PSE_GEN5_SLOTS_PER_BOX).filter{slot->
                gen5DestinationSlotIsStructurallyEmpty(p.destination,box*PSE_GEN5_SLOTS_PER_BOX+slot)
            }
            AlertDialog.Builder(this).setTitle(genToGen5CapacityLabel(p.sourceGeneration,2,box))
                .setItems(slots.map{genToGen5CapacityLabel(p.sourceGeneration,3,it)}.toTypedArray()){_,slotIndex->
                    reviewGenToGen5Destination(p,box*PSE_GEN5_SLOTS_PER_BOX+slots[slotIndex])
                }.setNegativeButton("Cancel",null).show()
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.reviewGenToGen5Destination(p:MainActivity.Gen4Gen5Prepared,target:Int){
    val canTransfer={
        gen4ToGen5StillBound(p)&&runCatching{
            gen5DestinationSlotIsStructurallyEmpty(p.destinationFile.readBytes(),target)
        }.getOrDefault(false)
    }
    val transfer={transferGen4BankToGen5(p,target)}
    val destinationLabel=genToGen5CapacityLabel(p.sourceGeneration,6,target)
    val transferLabel="Transfer • Box ${target/PSE_GEN5_SLOTS_PER_BOX+1} of $PSE_GEN5_BOX_COUNT / Slot ${target%PSE_GEN5_SLOTS_PER_BOX+1} of $PSE_GEN5_SLOTS_PER_BOX"
    if(p.sourceGeneration==3)showPseGen3ToGen5DestinationReview(
        p.source,p.converted,destinationLabel,transferLabel,canTransfer,transfer
    )else showPseGen4ToGen5DestinationReview(
        p.source,p.converted,destinationLabel,transferLabel,canTransfer,transfer
    )
}

private fun MainActivity.showPseGen4ToGen5DestinationReview(
    source:MainActivity.PokemonEntry,
    converted:MainActivity.PokemonEntry,
    destinationLabel:String,
    transferLabel:String,
    canTransfer:()->Boolean,
    onTransfer:()->Unit
){
    val oldPid="%08X".format(source.pid)
    val newPid="%08X".format(converted.pid)
    val pidLine=if(source.pid==converted.pid)"PID remains $oldPid." else
        "PID changes $oldPid → $newPid only to preserve the reviewed ability slot, nature, gender and shiny state in native PK5."
    val preview=buildString{
        append("Final PSE Gen 4 → Gen 5 destination review\n\n")
        append(destinationLabel).append(" The slot is structurally empty; existing and nonzero destination records remain unchanged.\n\n")
        append("Ball: exact Generation 4 ball ID ${source.ball} is preserved in the native PK5.\n")
        append(pidLine).append('\n')
        append("Ability: source slot ${source.abilityNumber}, ability #${source.ability} → verified Gen 5 ability #${converted.ability}.\n")
        append("The previously reviewed unspecified met-location mapping, represented fields, Pokédex update, box checksum and checksum-table scope are unchanged. Cancel leaves the exact Bank record and destination byte-for-byte unchanged.\n\n")
        append("This is a PSE conversion, not an in-game trade or a claim of official transfer provenance. The exact Gen 4 Bank record remains until the chosen PK5, retained recovery data, Pokédex state, checksums and reopened destination all verify.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${source.nickname.ifBlank{"Pokémon #${source.species}"}} • Gen 4 → Gen 5")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!canTransfer()){
                showDialog("Gen 4 → Gen 5","The destination identity, Bank record or selected slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            onTransfer()
        }.setNegativeButton("Cancel",null).show()
}
