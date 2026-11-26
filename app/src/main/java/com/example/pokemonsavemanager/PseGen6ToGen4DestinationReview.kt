package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN4_FROM_GEN6_BOX_COUNT=18
private const val PSE_GEN4_FROM_GEN6_SLOTS_PER_BOX=30
private const val PSE_GEN4_FROM_GEN6_CAPACITY=PSE_GEN4_FROM_GEN6_BOX_COUNT*PSE_GEN4_FROM_GEN6_SLOTS_PER_BOX

private fun gen6ToGen4DestinationLabel(kind:Int,index:Int):String=when(kind){
    0 -> "First Structurally Empty • Box ${index/PSE_GEN4_FROM_GEN6_SLOTS_PER_BOX+1} of $PSE_GEN4_FROM_GEN6_BOX_COUNT • Slot ${index%PSE_GEN4_FROM_GEN6_SLOTS_PER_BOX+1} of $PSE_GEN4_FROM_GEN6_SLOTS_PER_BOX"
    1 -> "Box ${index+1} of $PSE_GEN4_FROM_GEN6_BOX_COUNT"
    2 -> "Box ${index+1} of $PSE_GEN4_FROM_GEN6_BOX_COUNT • choose slot"
    3 -> "Slot ${index+1} of $PSE_GEN4_FROM_GEN6_SLOTS_PER_BOX"
    5 -> "All $PSE_GEN4_FROM_GEN6_CAPACITY native Generation 4 PC slots are unavailable. Nonzero destination records and the Gen 6 Bank source were left unchanged."
    6 -> "Chosen destination: Box ${index/PSE_GEN4_FROM_GEN6_SLOTS_PER_BOX+1} of $PSE_GEN4_FROM_GEN6_BOX_COUNT, Slot ${index%PSE_GEN4_FROM_GEN6_SLOTS_PER_BOX+1} of $PSE_GEN4_FROM_GEN6_SLOTS_PER_BOX, within the native $PSE_GEN4_FROM_GEN6_CAPACITY-slot PC."
    else -> error("Unknown Gen 6 to Gen 4 destination label")
}

internal fun MainActivity.chooseGen6ToGen4Destination(p:MainActivity.Gen6Gen4Prepared){
    if(!gen6ToGen4StillBound(p))return showDialog("Gen 6 → Gen 4","The prepared snapshots changed. Reopen PSE Bank.")
    val profile=detectGen4Profile(p.destination)?:return
    val first=(0 until PSE_GEN4_FROM_GEN6_CAPACITY).firstOrNull{gen4DestinationSlotIsStructurallyEmpty(p.destination,profile,it)}
        ?:return showDialog("Gen 6 → Gen 4",gen6ToGen4DestinationLabel(5,0))
    AlertDialog.Builder(this).setTitle("Choose native Gen 4 destination")
        .setMessage("The reviewed PSE conversion will write one native PK4 only into a structurally empty slot and update the Pokédex and active block CRCs. Every nonzero destination record remains unchanged; this does not claim official migration or verified provenance.")
        .setItems(arrayOf(gen6ToGen4DestinationLabel(0,first),"Choose Box / Slot • $PSE_GEN4_FROM_GEN6_BOX_COUNT Boxes × $PSE_GEN4_FROM_GEN6_SLOTS_PER_BOX slots")){_,which->
            if(!gen6ToGen4StillBound(p))showDialog("Gen 6 → Gen 4","The prepared snapshots changed. Reopen PSE Bank.")
            else if(which==0)reviewGen6ToGen4Destination(p,first)
            else showGen6ToGen4BoxPicker(p)
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.showGen6ToGen4BoxPicker(p:MainActivity.Gen6Gen4Prepared){
    if(!gen6ToGen4StillBound(p))return showDialog("Gen 6 → Gen 4","The prepared snapshots changed. Reopen PSE Bank.")
    val profile=detectGen4Profile(p.destination)?:return
    val boxes=(0 until PSE_GEN4_FROM_GEN6_BOX_COUNT).filter{box->(0 until PSE_GEN4_FROM_GEN6_SLOTS_PER_BOX).any{slot->
        gen4DestinationSlotIsStructurallyEmpty(p.destination,profile,box*PSE_GEN4_FROM_GEN6_SLOTS_PER_BOX+slot)
    }}
    if(boxes.isEmpty())return showDialog("Gen 6 → Gen 4",gen6ToGen4DestinationLabel(5,0))
    AlertDialog.Builder(this).setTitle("Choose Gen 4 destination box")
        .setItems(boxes.map{gen6ToGen4DestinationLabel(1,it)}.toTypedArray()){_,which->
            val box=boxes[which]
            val slots=(0 until PSE_GEN4_FROM_GEN6_SLOTS_PER_BOX).filter{slot->
                gen4DestinationSlotIsStructurallyEmpty(p.destination,profile,box*PSE_GEN4_FROM_GEN6_SLOTS_PER_BOX+slot)
            }
            AlertDialog.Builder(this).setTitle(gen6ToGen4DestinationLabel(2,box))
                .setItems(slots.map{gen6ToGen4DestinationLabel(3,it)}.toTypedArray()){_,slotIndex->
                    reviewGen6ToGen4Destination(p,box*PSE_GEN4_FROM_GEN6_SLOTS_PER_BOX+slots[slotIndex])
                }.setNegativeButton("Cancel",null).show()
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.reviewGen6ToGen4Destination(p:MainActivity.Gen6Gen4Prepared,target:Int){
    val canTransfer={
        gen6ToGen4StillBound(p)&&target in 0 until PSE_GEN4_FROM_GEN6_CAPACITY&&runCatching{
            val fresh=p.destinationFile.readBytes()
            val profile=detectGen4Profile(fresh)?:return@runCatching false
            fresh.contentEquals(p.destination)&&gen4DestinationSlotIsStructurallyEmpty(fresh,profile,target)
        }.getOrDefault(false)
    }
    val oldPid="%08X".format(p.source.pid)
    val newPid="%08X".format(p.converted4.pid)
    val pidLine=if(p.source.pid==p.converted4.pid)"PID remains $oldPid." else
        "PID changes $oldPid → $newPid only to preserve the reviewed shiny state, nature, gender and verified normal ability slot under Generation 5/4 PID rules."
    val originLine=if(p.plan5.legalised)
        "Origin Legalise accepted: game ID ${p.source.originGame} → ${p.plan5.originLabel}. This is the explicit user-selected provenance change reviewed earlier."
    else "Origin remains ${p.plan5.originLabel}."
    val destinationLabel=gen6ToGen4DestinationLabel(6,target)
    val transferLabel="Transfer • Box ${target/PSE_GEN4_FROM_GEN6_SLOTS_PER_BOX+1} of $PSE_GEN4_FROM_GEN6_BOX_COUNT / Slot ${target%PSE_GEN4_FROM_GEN6_SLOTS_PER_BOX+1} of $PSE_GEN4_FROM_GEN6_SLOTS_PER_BOX"
    val preview=buildString{
        append("Final PSE Gen 6 → Gen 4 destination and origin review\n\n")
        append(destinationLabel).append(" The slot is structurally empty; existing and nonzero destination records remain unchanged.\n\n")
        append(originLine).append('\n')
        append("Ball: exact Generation 6 ball ID ${p.source.ball} is preserved through the reviewed PK5 bridge in the native PK4.\n")
        append(pidLine).append('\n')
        append("Ability: source slot ${p.source.abilityNumber}, ability #${p.source.ability} → verified Generation 4 ability #${p.converted4.ability}.\n")
        append("Markings: native six-bit value 0x${"%02X".format(p.source.markingValue)} → 0x${"%02X".format(p.converted4.markingValue)}.\n")
        append("The previously reviewed PK5 bridge, unspecified met and Egg locations, omitted PK6 metadata, represented fields, Pokédex flags and active block CRC scope are unchanged. Cancel leaves the exact Bank record and destination byte-for-byte unchanged.\n\n")
        append("This is a PSE conversion, not an in-game trade or a claim of official backward-transfer provenance. The exact Gen 6 Bank record remains until the chosen PK4, retained recovery data, Pokédex state, checksums and reopened destination all verify.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${p.source.nickname.ifBlank{"Pokémon #${p.source.species}"}} • Gen 6 → Gen 4")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!canTransfer()){
                showDialog("Gen 6 → Gen 4","The destination identity, Bank record or selected slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            transferGen6BankToGen4(p,target)
        }.setNegativeButton("Cancel",null).show()
}
