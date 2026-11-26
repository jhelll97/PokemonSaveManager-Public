package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN5_FROM_GEN7_BOX_COUNT=24
private const val PSE_GEN5_FROM_GEN7_SLOTS_PER_BOX=30
private const val PSE_GEN5_FROM_GEN7_CAPACITY=PSE_GEN5_FROM_GEN7_BOX_COUNT*PSE_GEN5_FROM_GEN7_SLOTS_PER_BOX

private fun gen7ToGen5DestinationLabel(kind:Int,index:Int):String=when(kind){
    0 -> "First Structurally Empty • Box ${index/PSE_GEN5_FROM_GEN7_SLOTS_PER_BOX+1} of $PSE_GEN5_FROM_GEN7_BOX_COUNT • Slot ${index%PSE_GEN5_FROM_GEN7_SLOTS_PER_BOX+1} of $PSE_GEN5_FROM_GEN7_SLOTS_PER_BOX"
    1 -> "Box ${index+1} of $PSE_GEN5_FROM_GEN7_BOX_COUNT"
    2 -> "Box ${index+1} of $PSE_GEN5_FROM_GEN7_BOX_COUNT • choose slot"
    3 -> "Slot ${index+1} of $PSE_GEN5_FROM_GEN7_SLOTS_PER_BOX"
    5 -> "All $PSE_GEN5_FROM_GEN7_CAPACITY native Generation 5 PC slots are unavailable. Nonzero destination records and the Gen 7 Bank source were left unchanged."
    6 -> "Chosen destination: Box ${index/PSE_GEN5_FROM_GEN7_SLOTS_PER_BOX+1} of $PSE_GEN5_FROM_GEN7_BOX_COUNT, Slot ${index%PSE_GEN5_FROM_GEN7_SLOTS_PER_BOX+1} of $PSE_GEN5_FROM_GEN7_SLOTS_PER_BOX, within the native $PSE_GEN5_FROM_GEN7_CAPACITY-slot PC."
    else -> error("Unknown Gen 7 to Gen 5 destination label")
}

internal fun MainActivity.chooseGen7ToGen5Destination(p:MainActivity.Gen7Gen5Prepared){
    if(!gen7ToGen5StillBound(p))return showDialog("Gen 7 → Gen 5","The destination or Bank record changed. Reopen PSE Bank.")
    val first=(0 until PSE_GEN5_FROM_GEN7_CAPACITY).firstOrNull{gen5DestinationSlotIsStructurallyEmpty(p.destination,it)}
        ?:return showDialog("Gen 7 → Gen 5",gen7ToGen5DestinationLabel(5,0))
    AlertDialog.Builder(this).setTitle("Choose native Gen 5 destination")
        .setMessage("The reviewed PK5 will be written only to a structurally empty slot with scoped Pokédex and checksum updates. Nonzero destination records remain unchanged.")
        .setItems(arrayOf(gen7ToGen5DestinationLabel(0,first),"Choose Box / Slot • $PSE_GEN5_FROM_GEN7_BOX_COUNT Boxes × $PSE_GEN5_FROM_GEN7_SLOTS_PER_BOX slots")){_,which->
            if(!gen7ToGen5StillBound(p))showDialog("Gen 7 → Gen 5","The destination or Bank record changed. Reopen PSE Bank.")
            else if(which==0)reviewGen7ToGen5Destination(p,first)
            else showGen7ToGen5BoxPicker(p)
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.showGen7ToGen5BoxPicker(p:MainActivity.Gen7Gen5Prepared){
    if(!gen7ToGen5StillBound(p))return showDialog("Gen 7 → Gen 5","The destination or Bank record changed. Reopen PSE Bank.")
    val boxes=(0 until PSE_GEN5_FROM_GEN7_BOX_COUNT).filter{box->(0 until PSE_GEN5_FROM_GEN7_SLOTS_PER_BOX).any{slot->
        gen5DestinationSlotIsStructurallyEmpty(p.destination,box*PSE_GEN5_FROM_GEN7_SLOTS_PER_BOX+slot)
    }}
    if(boxes.isEmpty())return showDialog("Gen 7 → Gen 5",gen7ToGen5DestinationLabel(5,0))
    AlertDialog.Builder(this).setTitle("Choose Gen 5 destination box")
        .setMessage("Only boxes with structurally empty slots are listed. Nonzero destination records remain unchanged.")
        .setItems(boxes.map{gen7ToGen5DestinationLabel(1,it)}.toTypedArray()){_,which->
            val box=boxes[which]
            val slots=(0 until PSE_GEN5_FROM_GEN7_SLOTS_PER_BOX).filter{slot->
                gen5DestinationSlotIsStructurallyEmpty(p.destination,box*PSE_GEN5_FROM_GEN7_SLOTS_PER_BOX+slot)
            }
            AlertDialog.Builder(this).setTitle(gen7ToGen5DestinationLabel(2,box))
                .setItems(slots.map{gen7ToGen5DestinationLabel(3,it)}.toTypedArray()){_,slotIndex->
                    reviewGen7ToGen5Destination(p,box*PSE_GEN5_FROM_GEN7_SLOTS_PER_BOX+slots[slotIndex])
                }.setNegativeButton("Cancel",null).show()
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.reviewGen7ToGen5Destination(p:MainActivity.Gen7Gen5Prepared,target:Int){
    val canTransfer={
        gen7ToGen5StillBound(p)&&target in 0 until PSE_GEN5_FROM_GEN7_CAPACITY&&runCatching{
            val fresh=p.destinationFile.readBytes()
            fresh.contentEquals(p.destination)&&detectGen5Profile(fresh)!=null&&gen5DestinationSlotIsStructurallyEmpty(fresh,target)
        }.getOrDefault(false)
    }
    val oldPid="%08X".format(p.source.pid)
    val newPid="%08X".format(p.converted5.pid)
    val pidLine=if(p.source.pid==p.converted5.pid)"PID remains $oldPid." else
        "PID changes $oldPid → $newPid only to preserve the reviewed shiny state and normal ability slot under Generation 5 PID rules."
    val originLine=if(p.originLegalised)
        "Origin Legalise accepted: game ID ${p.source.originGame} → ${p.plan5.originLabel}. This is the explicit user-selected provenance change reviewed earlier."
    else "Origin remains ${p.plan5.originLabel}."
    val destinationLabel=gen7ToGen5DestinationLabel(6,target)
    val transferLabel="Transfer • Box ${target/PSE_GEN5_FROM_GEN7_SLOTS_PER_BOX+1} of $PSE_GEN5_FROM_GEN7_BOX_COUNT / Slot ${target%PSE_GEN5_FROM_GEN7_SLOTS_PER_BOX+1} of $PSE_GEN5_FROM_GEN7_SLOTS_PER_BOX"
    val preview=buildString{
        append("Final PSE Gen 7 → Gen 5 destination and origin review\n\n")
        append(destinationLabel).append(" The slot is structurally empty; existing and nonzero destination records remain unchanged.\n\n")
        append(originLine).append('\n')
        append("Ball: exact Generation 7 ball ID ${p.source.ball} is preserved through the reviewed PK6 bridge in the native PK5.\n")
        append(pidLine).append('\n')
        append("Ability: source slot ${p.source.abilityNumber}, ability #${p.source.ability} → verified Generation 5 ability #${p.converted5.ability}.\n")
        append("Markings: ${p.markingNote}; native Generation 5 stores the resulting six one-bit value 0x${"%02X".format(p.converted5.markingValue)}.\n")
        append("The previously reviewed PK6 bridge, unspecified met and Egg locations, friendship representation, omitted metadata, Pokédex flags, box CRC and checksum-table scope are unchanged. Cancel leaves the exact Bank record and destination byte-for-byte unchanged.\n\n")
        append("This is a PSE conversion, not an in-game trade or a claim of official backward-transfer provenance. The exact Gen 7 Bank record remains until the chosen PK5, retained recovery data, Pokédex state, checksums and reopened destination all verify.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${p.source.nickname.ifBlank{"Pokémon #${p.source.species}"}} • Gen 7 → Gen 5")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!canTransfer()){
                showDialog("Gen 7 → Gen 5","The destination identity, Bank record or selected slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            transferGen7BankToGen5(p,target)
        }.setNegativeButton("Cancel",null).show()
}
