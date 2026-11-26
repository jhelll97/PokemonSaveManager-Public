package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN6_BOX_COUNT=31
private const val PSE_GEN6_SLOTS_PER_BOX=30
private const val PSE_GEN6_CAPACITY=PSE_GEN6_BOX_COUNT*PSE_GEN6_SLOTS_PER_BOX

private fun genToGen6CapacityLabel(sourceGeneration:Int,kind:Int,index:Int):String=when(kind){
    0 -> "First Structurally Empty • Box ${index/PSE_GEN6_SLOTS_PER_BOX+1} of $PSE_GEN6_BOX_COUNT • Slot ${index%PSE_GEN6_SLOTS_PER_BOX+1} of $PSE_GEN6_SLOTS_PER_BOX"
    1 -> "Box ${index+1} of $PSE_GEN6_BOX_COUNT"
    2 -> "Box ${index+1} of $PSE_GEN6_BOX_COUNT • choose slot"
    3 -> "Slot ${index+1} of $PSE_GEN6_SLOTS_PER_BOX"
    5 -> "All $PSE_GEN6_CAPACITY native Generation 6 PC slots are unavailable. Nonzero destination records and the Gen $sourceGeneration Bank source were left unchanged."
    6 -> "Chosen destination: Box ${index/PSE_GEN6_SLOTS_PER_BOX+1} of $PSE_GEN6_BOX_COUNT, Slot ${index%PSE_GEN6_SLOTS_PER_BOX+1} of $PSE_GEN6_SLOTS_PER_BOX, within the native $PSE_GEN6_CAPACITY-slot PC."
    else -> error("Unknown Gen 3, Gen 4 or Gen 5 to Gen 6 capacity label")
}

internal fun MainActivity.chooseGen5ToGen6Destination(p:MainActivity.Gen5Gen6Prepared){
    val title="Gen ${p.sourceGeneration} → Gen 6"
    if(!gen5ToGen6StillBound(p))return showDialog(title,"The destination or Bank record changed. Reopen PSE Bank.")
    val info=boxStorageInfo(p.destination)?:return
    if(info.second!=PSE_GEN6_CAPACITY)return showDialog(title,"The native Generation 6 PC capacity could not be verified. Nothing was changed.")
    val first=(0 until PSE_GEN6_CAPACITY).firstOrNull{
        gen6DestinationSlotIsStructurallyEmpty(p.destination,info.first,info.second,info.third,it)
    }?:return showDialog(title,genToGen6CapacityLabel(p.sourceGeneration,5,0))
    AlertDialog.Builder(this).setTitle("Choose native Gen 6 destination")
        .setMessage("The reviewed PK6 will be written to one structurally empty slot. Nonzero destination records remain unchanged. PSE will update only that slot, the required Pokédex flags, and native block checksums.")
        .setItems(arrayOf(genToGen6CapacityLabel(p.sourceGeneration,0,first),"Choose Box / Slot • $PSE_GEN6_BOX_COUNT Boxes × $PSE_GEN6_SLOTS_PER_BOX slots")){_,which->
            if(!gen5ToGen6StillBound(p))showDialog(title,"The destination or Bank record changed. Reopen PSE Bank.")
            else if(which==0)reviewGenToGen6Destination(p,first)
            else showGen5ToGen6BoxPicker(p)
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.showGen5ToGen6BoxPicker(p:MainActivity.Gen5Gen6Prepared){
    val title="Gen ${p.sourceGeneration} → Gen 6"
    if(!gen5ToGen6StillBound(p))return showDialog(title,"The destination or Bank record changed. Reopen PSE Bank.")
    val info=boxStorageInfo(p.destination)?:return
    if(info.second!=PSE_GEN6_CAPACITY)return showDialog(title,"The native Generation 6 PC capacity could not be verified. Nothing was changed.")
    val boxes=(0 until PSE_GEN6_BOX_COUNT).filter{box->(0 until PSE_GEN6_SLOTS_PER_BOX).any{slot->
        gen6DestinationSlotIsStructurallyEmpty(p.destination,info.first,info.second,info.third,box*PSE_GEN6_SLOTS_PER_BOX+slot)
    }}
    if(boxes.isEmpty())return showDialog(title,genToGen6CapacityLabel(p.sourceGeneration,5,0))
    AlertDialog.Builder(this).setTitle("Choose Gen 6 destination box")
        .setItems(boxes.map{genToGen6CapacityLabel(p.sourceGeneration,1,it)}.toTypedArray()){_,which->
            val box=boxes[which]
            val slots=(0 until PSE_GEN6_SLOTS_PER_BOX).filter{slot->
                gen6DestinationSlotIsStructurallyEmpty(p.destination,info.first,info.second,info.third,box*PSE_GEN6_SLOTS_PER_BOX+slot)
            }
            AlertDialog.Builder(this).setTitle(genToGen6CapacityLabel(p.sourceGeneration,2,box))
                .setItems(slots.map{genToGen6CapacityLabel(p.sourceGeneration,3,it)}.toTypedArray()){_,slotIndex->
                    reviewGenToGen6Destination(p,box*PSE_GEN6_SLOTS_PER_BOX+slots[slotIndex])
                }.setNegativeButton("Cancel",null).show()
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.reviewGenToGen6Destination(p:MainActivity.Gen5Gen6Prepared,target:Int){
    val canTransfer={
        val current=runCatching{p.destinationFile.readBytes()}.getOrNull()
        val info=current?.let{boxStorageInfo(it)}
        gen5ToGen6StillBound(p)&&current!=null&&info!=null&&info.second==PSE_GEN6_CAPACITY&&
            gen6DestinationSlotIsStructurallyEmpty(current,info.first,info.second,info.third,target)
    }
    val transfer={transferGen5BankToGen6(p,target)}
    val destinationLabel=genToGen6CapacityLabel(p.sourceGeneration,6,target)
    val transferLabel="Transfer • Box ${target/PSE_GEN6_SLOTS_PER_BOX+1} of $PSE_GEN6_BOX_COUNT / Slot ${target%PSE_GEN6_SLOTS_PER_BOX+1} of $PSE_GEN6_SLOTS_PER_BOX"
    if(p.sourceGeneration==3&&p.originalSource!=null)showPseGen3ToGen6DestinationReview(
        p.originalSource,p.source,p.converted,destinationLabel,transferLabel,canTransfer,transfer
    )else if(p.sourceGeneration==4)showPseGen4ToGen6DestinationReview(
        p.source,p.converted,destinationLabel,transferLabel,canTransfer,transfer
    )else transfer()
}

private fun MainActivity.showPseGen4ToGen6DestinationReview(
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
        "PID changes $oldPid → $newPid only to preserve the reviewed ability slot, nature, gender and shiny state across the native PK5 bridge and PK6."
    val preview=buildString{
        append("Final PSE Gen 4 → Gen 6 destination review\n\n")
        append(destinationLabel).append(" The slot is structurally empty; existing and nonzero destination records remain unchanged.\n\n")
        append("Ball: exact Generation 4 ball ID ${source.ball} is preserved in the native PK6.\n")
        append(pidLine).append('\n')
        append("Ability: source slot ${source.abilityNumber}, ability #${source.ability} → verified Gen 6 ability #${converted.ability}.\n")
        append("The previously reviewed transfer marker, Egg-location conversion, current handler, empty relearn and geographic fields, represented fields, Pokédex update and checksum scope are unchanged. Cancel leaves the exact Bank record and destination byte-for-byte unchanged.\n\n")
        append("This is a PSE conversion, not an in-game trade or a claim of official transfer provenance. The exact Gen 4 Bank record remains until the chosen PK6, retained recovery data, Pokédex state, checksums and reopened destination all verify.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${source.nickname.ifBlank{"Pokémon #${source.species}"}} • Gen 4 → Gen 6")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!canTransfer()){
                showDialog("Gen 4 → Gen 6","The destination identity, Bank record or selected slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            onTransfer()
        }.setNegativeButton("Cancel",null).show()
}
