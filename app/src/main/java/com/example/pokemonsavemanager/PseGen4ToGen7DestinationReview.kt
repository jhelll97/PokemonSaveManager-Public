package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN7_BOX_COUNT=32
private const val PSE_GEN7_SLOTS_PER_BOX=30
private const val PSE_GEN7_CAPACITY=PSE_GEN7_BOX_COUNT*PSE_GEN7_SLOTS_PER_BOX

private fun genToGen7CapacityLabel(sourceGeneration:Int,kind:Int,index:Int):String=when(kind){
    0 -> "First Structurally Empty • Box ${index/PSE_GEN7_SLOTS_PER_BOX+1} of $PSE_GEN7_BOX_COUNT • Slot ${index%PSE_GEN7_SLOTS_PER_BOX+1} of $PSE_GEN7_SLOTS_PER_BOX"
    1 -> "Box ${index+1} of $PSE_GEN7_BOX_COUNT"
    2 -> "Box ${index+1} of $PSE_GEN7_BOX_COUNT • choose slot"
    3 -> "Slot ${index+1} of $PSE_GEN7_SLOTS_PER_BOX"
    5 -> "All $PSE_GEN7_CAPACITY native Generation 7 PC slots are unavailable. Nonzero destination records and the Gen $sourceGeneration Bank source were left unchanged."
    6 -> "Chosen destination: Box ${index/PSE_GEN7_SLOTS_PER_BOX+1} of $PSE_GEN7_BOX_COUNT, Slot ${index%PSE_GEN7_SLOTS_PER_BOX+1} of $PSE_GEN7_SLOTS_PER_BOX, within the native $PSE_GEN7_CAPACITY-slot PC."
    else -> error("Unknown Gen 4, Gen 5 or Gen 6 to Gen 7 capacity label")
}

internal fun MainActivity.chooseGen6ToGen7Destination(p:MainActivity.Gen6Gen7Prepared){
    val title="Gen ${p.sourceGeneration} → Gen 7"
    if(!gen6ToGen7SnapshotsMatch(p))return showDialog(title,"The destination or Bank record changed. Reopen PSE Bank.")
    val info=boxStorageInfo(p.destination)?:return
    if(info.second!=PSE_GEN7_CAPACITY)return showDialog(title,"The native Generation 7 PC capacity could not be verified. Nothing was changed.")
    val first=(0 until PSE_GEN7_CAPACITY).firstOrNull{
        gen6DestinationSlotIsStructurallyEmpty(p.destination,info.first,info.second,info.third,it)
    }?:return showDialog(title,genToGen7CapacityLabel(p.sourceGeneration,5,0))
    AlertDialog.Builder(this).setTitle("Choose native Gen 7 destination")
        .setMessage("The reviewed PK7 will be written to one structurally empty slot. PSE will update only that slot, required Pokédex flags, native block CRCs and the save signature. Nonzero destination records remain unchanged.")
        .setItems(arrayOf(genToGen7CapacityLabel(p.sourceGeneration,0,first),"Choose Box / Slot • $PSE_GEN7_BOX_COUNT Boxes × $PSE_GEN7_SLOTS_PER_BOX slots")){_,which->
            if(!gen6ToGen7SnapshotsMatch(p))showDialog(title,"The destination or Bank record changed. Reopen PSE Bank.")
            else if(which==0)reviewGenToGen7Destination(p,first)
            else showGen6ToGen7BoxPicker(p)
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.showGen6ToGen7BoxPicker(p:MainActivity.Gen6Gen7Prepared){
    val title="Gen ${p.sourceGeneration} → Gen 7"
    if(!gen6ToGen7SnapshotsMatch(p))return showDialog(title,"The destination or Bank record changed. Reopen PSE Bank.")
    val info=boxStorageInfo(p.destination)?:return
    if(info.second!=PSE_GEN7_CAPACITY)return showDialog(title,"The native Generation 7 PC capacity could not be verified. Nothing was changed.")
    val boxes=(0 until PSE_GEN7_BOX_COUNT).filter{box->(0 until PSE_GEN7_SLOTS_PER_BOX).any{slot->
        gen6DestinationSlotIsStructurallyEmpty(p.destination,info.first,info.second,info.third,box*PSE_GEN7_SLOTS_PER_BOX+slot)
    }}
    if(boxes.isEmpty())return showDialog(title,genToGen7CapacityLabel(p.sourceGeneration,5,0))
    AlertDialog.Builder(this).setTitle("Choose Gen 7 destination box")
        .setItems(boxes.map{genToGen7CapacityLabel(p.sourceGeneration,1,it)}.toTypedArray()){_,which->
            val box=boxes[which]
            val slots=(0 until PSE_GEN7_SLOTS_PER_BOX).filter{slot->
                gen6DestinationSlotIsStructurallyEmpty(p.destination,info.first,info.second,info.third,box*PSE_GEN7_SLOTS_PER_BOX+slot)
            }
            AlertDialog.Builder(this).setTitle(genToGen7CapacityLabel(p.sourceGeneration,2,box))
                .setItems(slots.map{genToGen7CapacityLabel(p.sourceGeneration,3,it)}.toTypedArray()){_,slotIndex->
                    reviewGenToGen7Destination(p,box*PSE_GEN7_SLOTS_PER_BOX+slots[slotIndex])
                }.setNegativeButton("Cancel",null).show()
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.reviewGenToGen7Destination(p:MainActivity.Gen6Gen7Prepared,target:Int){
    val canTransfer={
        val current=runCatching{p.destinationFile.readBytes()}.getOrNull()
        val info=current?.let{boxStorageInfo(it)}
        gen6ToGen7SnapshotsMatch(p)&&current!=null&&info!=null&&info.second==PSE_GEN7_CAPACITY&&
            gen6DestinationSlotIsStructurallyEmpty(current,info.first,info.second,info.third,target)
    }
    val box=target/PSE_GEN7_SLOTS_PER_BOX+1
    val slot=target%PSE_GEN7_SLOTS_PER_BOX+1
    val destinationLabel=genToGen7CapacityLabel(p.sourceGeneration,6,target)
    val transferLabel="Transfer • Box $box of $PSE_GEN7_BOX_COUNT / Slot $slot of $PSE_GEN7_SLOTS_PER_BOX"
    when(p.sourceGeneration){
        4->showPseGen4ToGen7DestinationReview(p,destinationLabel,transferLabel,canTransfer){transferGen6BankToGen7(p,target)}
        5->showPseGen5ToGen7DestinationReview(p,destinationLabel,transferLabel,canTransfer){transferGen6BankToGen7(p,target)}
        else->transferGen6BankToGen7(p,target)
    }
}

private fun MainActivity.showPseGen4ToGen7DestinationReview(
    p:MainActivity.Gen6Gen7Prepared,
    destinationLabel:String,
    transferLabel:String,
    canTransfer:()->Boolean,
    onTransfer:()->Unit
){
    val source=p.source
    val converted=p.converted
    val oldPid="%08X".format(source.pid)
    val newPid="%08X".format(converted.pid)
    val pidLine=if(source.pid==converted.pid)"PID remains $oldPid." else
        "PID changes $oldPid → $newPid only to preserve the reviewed ability slot, nature, gender and shiny state through the native PK5, PK6 and PK7 formats."
    val preview=buildString{
        append("Final PSE Gen 4 → Gen 7 destination review\n\n")
        append(destinationLabel).append(" The slot is structurally empty; existing and nonzero destination records remain unchanged.\n\n")
        append("Ball: exact Generation 4 ball ID ${source.ball} is preserved in the native PK7.\n")
        append(pidLine).append('\n')
        append("Ability: source slot ${source.abilityNumber}, ability #${source.ability} → verified Gen 7 ability #${converted.ability}.\n")
        append("Markings: ${p.markingNote}.\n")
        append("The previously reviewed transfer marker, Egg-location conversion, current handler, empty relearn and geographic fields, Resort and Hyper Training defaults, represented fields, Pokédex update, block CRCs and save signature are unchanged. Cancel leaves the exact Bank record and destination byte-for-byte unchanged.\n\n")
        append("This is a PSE conversion, not an in-game trade or a claim of official transfer provenance. The exact Gen 4 Bank record remains until the chosen PK7, retained recovery data, Pokédex state, checksums, save signature and reopened destination all verify.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${source.nickname.ifBlank{"Pokémon #${source.species}"}} • Gen 4 → Gen 7")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!canTransfer()){
                showDialog("Gen 4 → Gen 7","The destination identity, Bank record or selected slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            onTransfer()
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.showPseGen5ToGen7DestinationReview(
    p:MainActivity.Gen6Gen7Prepared,
    destinationLabel:String,
    transferLabel:String,
    canTransfer:()->Boolean,
    onTransfer:()->Unit
){
    val source=p.source
    val converted=p.converted
    val oldPid="%08X".format(source.pid)
    val newPid="%08X".format(converted.pid)
    val pidLine=if(source.pid==converted.pid)"PID remains $oldPid." else
        "PID changes $oldPid → $newPid only to preserve the reviewed shiny state under the wider Generation 6 and 7 threshold."
    val preview=buildString{
        append("Final PSE Gen 5 → Gen 7 destination review\n\n")
        append(destinationLabel).append(" The slot is structurally empty; existing and nonzero destination records remain unchanged.\n\n")
        append("Ball: exact Generation 5 ball ID ${source.ball} is preserved in the native PK7.\n")
        append(pidLine).append('\n')
        append("Ability: source slot ${source.abilityNumber}, ability #${source.ability} → verified Gen 7 ability #${converted.ability}.\n")
        append("Markings: ${p.markingNote}.\n")
        append("The previously reviewed transfer marker, Egg-location conversion, current handler, empty relearn and geographic fields, Resort and Hyper Training defaults, represented fields, Pokédex update, block CRCs and save signature are unchanged. Cancel leaves the exact Bank record and destination byte-for-byte unchanged.\n\n")
        append("This is a PSE conversion, not an official Pokémon Bank transaction, in-game trade or provenance claim. The exact Gen 5 Bank record remains until the chosen PK7, retained recovery data, Pokédex state, checksums, save signature and reopened destination all verify.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${source.nickname.ifBlank{"Pokémon #${source.species}"}} • Gen 5 → Gen 7")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!canTransfer()){
                showDialog("Gen 5 → Gen 7","The destination identity, Bank record or selected slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            onTransfer()
        }.setNegativeButton("Cancel",null).show()
}
