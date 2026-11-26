package com.example.pokemonsavemanager

import android.app.AlertDialog

internal fun MainActivity.chooseGen3ToGen7Destination(p:MainActivity.Gen3Gen7Prepared){
    if(!gen3ToGen7StillBound(p))return showDialog("Gen 3 → Gen 7","The destination or Bank record changed. Reopen PSE Bank.")
    val info=boxStorageInfo(p.destination)?:return
    if(info.second!=GEN7_PC_CAPACITY)return showDialog("Gen 3 → Gen 7","The native Generation 7 PC capacity could not be verified. Nothing was changed.")
    val first=(0 until GEN7_PC_CAPACITY).firstOrNull{
        gen6DestinationSlotIsStructurallyEmpty(p.destination,info.first,info.second,info.third,it)
    }?:return showDialog("Gen 3 → Gen 7",gen3ToGen7CapacityLabel(5,0))
    var consumed=false
    AlertDialog.Builder(this).setTitle("Choose native Gen 7 destination")
        .setMessage("Choose the exact structurally empty PK7 slot across $GEN7_PC_BOX_COUNT Boxes × $GEN7_PC_SLOTS_PER_BOX slots ($GEN7_PC_CAPACITY total). PSE will update the Pokédex and required checksums; Cancel leaves both sides unchanged.")
        .setItems(arrayOf(gen3ToGen7CapacityLabel(0,first),"Choose Box / Slot • $GEN7_PC_BOX_COUNT × $GEN7_PC_SLOTS_PER_BOX")){_,which->
            if(consumed||!gen3ToGen7StillBound(p)){showDialog("Gen 3 → Gen 7","The destination or Bank record changed, or this selection was already submitted. Reopen PSE Bank.");return@setItems}
            consumed=true
            if(which==0)reviewGen3ToGen7Destination(p,first)else showGen3ToGen7BoxPicker(p)
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.reviewGen3ToGen7Destination(p:MainActivity.Gen3Gen7Prepared,target:Int){
    if(!gen3ToGen7StillBound(p))return showDialog("Gen 3 → Gen 7","The destination or Bank record changed before final review. Reopen PSE Bank.")
    val info=boxStorageInfo(p.destination)?:return
    if(info.second!=GEN7_PC_CAPACITY||target !in 0 until GEN7_PC_CAPACITY||!gen6DestinationSlotIsStructurallyEmpty(p.destination,info.first,info.second,info.third,target))return showDialog("Gen 3 → Gen 7","The selected destination is invalid or no longer structurally empty. Existing or nonzero slot data was left unchanged.")
    val preview=buildPseGen3ToGen7Review(p.originalSource,p.source,p.converted,p.meta.speciesName,p.meta.abilityId,p.meta.baseFriendship,p.trainer.trainerName,p.trainer.gender,gen3ToGen7CapacityLabel(6,target))
    var consumed=false
    AlertDialog.Builder(this).setTitle("${p.source.nickname.ifBlank{p.meta.speciesName}} • Gen 3 → Gen 7").setMessage(preview)
        .setPositiveButton("Transfer • Box ${target/GEN7_PC_SLOTS_PER_BOX+1} of $GEN7_PC_BOX_COUNT / Slot ${target%GEN7_PC_SLOTS_PER_BOX+1} of $GEN7_PC_SLOTS_PER_BOX"){_,_->
            val current=runCatching{p.destinationFile.readBytes()}.getOrNull()
            val selectedSlotStillEmpty=current?.let{bytes->val currentInfo=boxStorageInfo(bytes);currentInfo!=null&&currentInfo.second==GEN7_PC_CAPACITY&&target in 0 until GEN7_PC_CAPACITY&&gen6DestinationSlotIsStructurallyEmpty(bytes,currentInfo.first,currentInfo.second,currentInfo.third,target)}==true
            if(consumed||!gen3ToGen7StillBound(p)||!selectedSlotStillEmpty){showDialog("Gen 3 → Gen 7","The destination identity, Bank record or selected slot changed, or this transfer was already submitted. Nothing was changed.");return@setPositiveButton}
            consumed=true
            transferGen3BankToGen7(p,target)
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.showGen3ToGen7BoxPicker(p:MainActivity.Gen3Gen7Prepared){
    if(!gen3ToGen7StillBound(p))return showDialog("Gen 3 → Gen 7","The destination or Bank record changed. Reopen PSE Bank.")
    val info=boxStorageInfo(p.destination)?:return
    if(info.second!=GEN7_PC_CAPACITY)return showDialog("Gen 3 → Gen 7","The native Generation 7 PC capacity could not be verified. Nothing was changed.")
    val boxes=(0 until GEN7_PC_BOX_COUNT).filter{box->(0 until GEN7_PC_SLOTS_PER_BOX).any{slot->
        gen6DestinationSlotIsStructurallyEmpty(p.destination,info.first,info.second,info.third,box*GEN7_PC_SLOTS_PER_BOX+slot)
    }}
    if(boxes.isEmpty())return showDialog("Gen 3 → Gen 7",gen3ToGen7CapacityLabel(5,0))
    var consumed=false
    AlertDialog.Builder(this).setTitle("Choose Gen 7 destination box • $GEN7_PC_BOX_COUNT total")
        .setItems(boxes.map{box->val count=(0 until GEN7_PC_SLOTS_PER_BOX).count{slot->gen6DestinationSlotIsStructurallyEmpty(p.destination,info.first,info.second,info.third,box*GEN7_PC_SLOTS_PER_BOX+slot)};gen3ToGen7CapacityLabel(1,box)+" • $count structurally empty"}.toTypedArray()){_,which->
            if(consumed||!gen3ToGen7StillBound(p)){showDialog("Gen 3 → Gen 7","The destination or Bank record changed, or this box selection was already submitted. Reopen PSE Bank.");return@setItems}
            val box=boxes[which]
            consumed=true
            val slots=(0 until GEN7_PC_SLOTS_PER_BOX).filter{slot->
                gen6DestinationSlotIsStructurallyEmpty(p.destination,info.first,info.second,info.third,box*GEN7_PC_SLOTS_PER_BOX+slot)
            }
            AlertDialog.Builder(this).setTitle(gen3ToGen7CapacityLabel(2,box))
                .setItems(slots.map{gen3ToGen7CapacityLabel(3,it)}.toTypedArray()){_,slotIndex->reviewGen3ToGen7Destination(p,box*GEN7_PC_SLOTS_PER_BOX+slots[slotIndex])}
                .setNegativeButton("Back"){_,_->showGen3ToGen7BoxPicker(p)}.setNeutralButton("Cancel",null).show()
        }.setNegativeButton("Back"){_,_->if(!consumed)chooseGen3ToGen7Destination(p)}.setNeutralButton("Cancel",null).show()
}
