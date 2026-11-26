package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN3_FROM_GEN5_BOX_COUNT=14
private const val PSE_GEN3_FROM_GEN5_SLOTS_PER_BOX=30
private const val PSE_GEN3_FROM_GEN5_CAPACITY=PSE_GEN3_FROM_GEN5_BOX_COUNT*PSE_GEN3_FROM_GEN5_SLOTS_PER_BOX

private fun gen5ToGen3DestinationLabel(kind:Int,index:Int):String=when(kind){
    0 -> "First Structurally Empty • Box ${index/PSE_GEN3_FROM_GEN5_SLOTS_PER_BOX+1} of $PSE_GEN3_FROM_GEN5_BOX_COUNT • Slot ${index%PSE_GEN3_FROM_GEN5_SLOTS_PER_BOX+1} of $PSE_GEN3_FROM_GEN5_SLOTS_PER_BOX"
    1 -> "Box ${index+1} of $PSE_GEN3_FROM_GEN5_BOX_COUNT"
    2 -> "Box ${index+1} of $PSE_GEN3_FROM_GEN5_BOX_COUNT • choose slot"
    3 -> "Slot ${index+1} of $PSE_GEN3_FROM_GEN5_SLOTS_PER_BOX"
    5 -> "All $PSE_GEN3_FROM_GEN5_CAPACITY native Generation 3 PC slots are unavailable. Nonzero destination records and the Gen 5 Bank source were left unchanged."
    6 -> "Chosen destination: Box ${index/PSE_GEN3_FROM_GEN5_SLOTS_PER_BOX+1} of $PSE_GEN3_FROM_GEN5_BOX_COUNT, Slot ${index%PSE_GEN3_FROM_GEN5_SLOTS_PER_BOX+1} of $PSE_GEN3_FROM_GEN5_SLOTS_PER_BOX, within the native $PSE_GEN3_FROM_GEN5_CAPACITY-slot PC."
    else -> error("Unknown Gen 5 to Gen 3 destination label")
}

internal fun MainActivity.chooseGen5ToGen3Destination(p:MainActivity.Gen5Gen3Prepared){
    if(!gen5ToGen3StillBound(p))return showDialog("Gen 5 → Gen 3","The prepared snapshots changed. Reopen PSE Bank.")
    val pc=gen3PcBuffer(p.destination)?:return showDialog("Gen 5 → Gen 3","PSE could not reconstruct Gen 3 PC storage.")
    val first=(0 until PSE_GEN3_FROM_GEN5_CAPACITY).firstOrNull{gen3DestinationSlotIsStructurallyEmpty(pc,it)}
        ?:return showDialog("Gen 5 → Gen 3",gen5ToGen3DestinationLabel(5,0))
    AlertDialog.Builder(this).setTitle("Choose native Gen 3 destination")
        .setMessage("The reviewed PSE conversion will be written to one structurally empty native PK3 slot. Every nonzero destination record remains unchanged; this does not claim official migration or verified provenance.")
        .setItems(arrayOf(gen5ToGen3DestinationLabel(0,first),"Choose Box / Slot • $PSE_GEN3_FROM_GEN5_BOX_COUNT Boxes × $PSE_GEN3_FROM_GEN5_SLOTS_PER_BOX slots")){_,which->
            if(!gen5ToGen3StillBound(p))showDialog("Gen 5 → Gen 3","The prepared snapshots changed. Reopen PSE Bank.")
            else if(which==0)reviewGen5ToGen3Destination(p,first)
            else showGen5ToGen3BoxPicker(p)
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.showGen5ToGen3BoxPicker(p:MainActivity.Gen5Gen3Prepared){
    if(!gen5ToGen3StillBound(p))return showDialog("Gen 5 → Gen 3","The prepared snapshots changed. Reopen PSE Bank.")
    val pc=gen3PcBuffer(p.destination)?:return showDialog("Gen 5 → Gen 3","PSE could not reconstruct Gen 3 PC storage.")
    val boxes=(0 until PSE_GEN3_FROM_GEN5_BOX_COUNT).filter{box->(0 until PSE_GEN3_FROM_GEN5_SLOTS_PER_BOX).any{slot->
        gen3DestinationSlotIsStructurallyEmpty(pc,box*PSE_GEN3_FROM_GEN5_SLOTS_PER_BOX+slot)
    }}
    if(boxes.isEmpty())return showDialog("Gen 5 → Gen 3",gen5ToGen3DestinationLabel(5,0))
    AlertDialog.Builder(this).setTitle("Choose Gen 3 destination box")
        .setItems(boxes.map{gen5ToGen3DestinationLabel(1,it)}.toTypedArray()){_,which->
            val box=boxes[which]
            val slots=(0 until PSE_GEN3_FROM_GEN5_SLOTS_PER_BOX).filter{slot->
                gen3DestinationSlotIsStructurallyEmpty(pc,box*PSE_GEN3_FROM_GEN5_SLOTS_PER_BOX+slot)
            }
            AlertDialog.Builder(this).setTitle(gen5ToGen3DestinationLabel(2,box))
                .setItems(slots.map{gen5ToGen3DestinationLabel(3,it)}.toTypedArray()){_,slotIndex->
                    reviewGen5ToGen3Destination(p,box*PSE_GEN3_FROM_GEN5_SLOTS_PER_BOX+slots[slotIndex])
                }.setNegativeButton("Cancel",null).show()
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.reviewGen5ToGen3Destination(p:MainActivity.Gen5Gen3Prepared,target:Int){
    val canTransfer={
        gen5ToGen3StillBound(p)&&target in 0 until PSE_GEN3_FROM_GEN5_CAPACITY&&runCatching{
            val fresh=p.destinationFile.readBytes()
            val pc=gen3PcBuffer(fresh)?:return@runCatching false
            fresh.contentEquals(p.destination)&&gen3DestinationSlotIsStructurallyEmpty(pc,target)
        }.getOrDefault(false)
    }
    val oldPid="%08X".format(p.source.pid)
    val newPid="%08X".format(p.converted3.pid)
    val pidLine=if(p.source.pid==p.converted3.pid)"PID remains $oldPid." else
        "PID changes $oldPid → $newPid only to preserve the reviewed shiny state, nature, gender and verified normal ability slot under Generation 4/3 PID rules."
    val originTitle=gen3CompatibleOriginTitle(p.originGame)?:"Generation 3 title"
    val originLine=if(p.originLegalised)
        "Origin Legalise accepted: game ID ${p.source.originGame} → $originTitle (ID ${p.originGame}). This is the explicit user-selected provenance change reviewed earlier."
    else "Origin remains $originTitle (ID ${p.originGame})."
    val destinationLabel=gen5ToGen3DestinationLabel(6,target)
    val transferLabel="Transfer • Box ${target/PSE_GEN3_FROM_GEN5_SLOTS_PER_BOX+1} of $PSE_GEN3_FROM_GEN5_BOX_COUNT / Slot ${target%PSE_GEN3_FROM_GEN5_SLOTS_PER_BOX+1} of $PSE_GEN3_FROM_GEN5_SLOTS_PER_BOX"
    val preview=buildString{
        append("Final PSE Gen 5 → Gen 3 destination and origin review\n\n")
        append(destinationLabel).append(" The slot is structurally empty; existing and nonzero destination records remain unchanged.\n\n")
        append(originLine).append('\n')
        append("Ball: exact Generation 5 ball ID ${p.source.ball} is preserved through the reviewed PK4 bridge in the native PK3.\n")
        append(pidLine).append('\n')
        append("Ability: source slot ${p.source.abilityNumber}, ability #${p.source.ability} → verified Generation 3 ability #${p.converted3.ability}.\n")
        append("Markings: native source value 0x${"%02X".format(p.source.markingValue)} → native Generation 3 value 0x${"%02X".format(p.converted3.markingValue)}.\n")
        append("The previously reviewed PK4 bridge, unspecified met location, represented fields, Pokédex mirrors and section-checksum scope are unchanged. Cancel leaves the exact Bank record and destination byte-for-byte unchanged.\n\n")
        append("This is a PSE conversion, not an in-game trade or a claim of official backward-transfer provenance. The exact Gen 5 Bank record remains until the chosen PK3, retained recovery data, Pokédex state, checksums and reopened destination all verify.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${p.source.nickname.ifBlank{"Pokémon #${p.source.species}"}} • Gen 5 → Gen 3")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!canTransfer()){
                showDialog("Gen 5 → Gen 3","The destination identity, Bank record or selected slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            transferGen5BankToGen3(p,target)
        }.setNegativeButton("Cancel",null).show()
}
