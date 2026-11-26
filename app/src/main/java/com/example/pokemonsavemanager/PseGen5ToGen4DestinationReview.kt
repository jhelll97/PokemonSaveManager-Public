package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN4_BOX_COUNT=18
private const val PSE_GEN4_SLOTS_PER_BOX=30
private const val PSE_GEN4_CAPACITY=PSE_GEN4_BOX_COUNT*PSE_GEN4_SLOTS_PER_BOX

private fun gen5ToGen4DestinationLabel(kind:Int,index:Int):String=when(kind){
    0 -> "First Structurally Empty • Box ${index/PSE_GEN4_SLOTS_PER_BOX+1} of $PSE_GEN4_BOX_COUNT • Slot ${index%PSE_GEN4_SLOTS_PER_BOX+1} of $PSE_GEN4_SLOTS_PER_BOX"
    1 -> "Box ${index+1} of $PSE_GEN4_BOX_COUNT"
    2 -> "Box ${index+1} of $PSE_GEN4_BOX_COUNT • choose slot"
    3 -> "Slot ${index+1} of $PSE_GEN4_SLOTS_PER_BOX"
    5 -> "All $PSE_GEN4_CAPACITY native Generation 4 PC slots are unavailable. Nonzero destination records and the Gen 5 Bank source were left unchanged."
    6 -> "Chosen destination: Box ${index/PSE_GEN4_SLOTS_PER_BOX+1} of $PSE_GEN4_BOX_COUNT, Slot ${index%PSE_GEN4_SLOTS_PER_BOX+1} of $PSE_GEN4_SLOTS_PER_BOX, within the native $PSE_GEN4_CAPACITY-slot PC."
    else -> error("Unknown Gen 5 to Gen 4 destination label")
}

private fun gen4OriginTitle(id:Int):String=when(id){
    1->"Pokémon Sapphire"
    2->"Pokémon Ruby"
    3->"Pokémon Emerald"
    4->"Pokémon FireRed"
    5->"Pokémon LeafGreen"
    7->"Pokémon HeartGold"
    8->"Pokémon SoulSilver"
    10->"Pokémon Diamond"
    11->"Pokémon Pearl"
    12->"Pokémon Platinum"
    15->"Pokémon Colosseum / XD"
    else->"game ID $id"
}

internal fun MainActivity.chooseGen5ToGen4Destination(p:MainActivity.Gen5Gen4Prepared){
    if(!gen5ToGen4StillBound(p))return showDialog("Gen 5 → Gen 4","The prepared snapshots changed. Reopen PSE Bank.")
    val profile=detectGen4Profile(p.destination)?:return
    val first=(0 until PSE_GEN4_CAPACITY).firstOrNull{gen4DestinationSlotIsStructurallyEmpty(p.destination,profile,it)}
        ?:return showDialog("Gen 5 → Gen 4",gen5ToGen4DestinationLabel(5,0))
    AlertDialog.Builder(this).setTitle("Choose native Gen 4 destination")
        .setMessage("The reviewed PSE conversion will be written to one structurally empty native PK4 slot. PSE preserves every nonzero destination record and updates only the Pokédex and active block CRCs; this does not claim official backward transfer or verified provenance.")
        .setItems(arrayOf(gen5ToGen4DestinationLabel(0,first),"Choose Box / Slot • $PSE_GEN4_BOX_COUNT Boxes × $PSE_GEN4_SLOTS_PER_BOX slots")){_,which->
            if(!gen5ToGen4StillBound(p))showDialog("Gen 5 → Gen 4","The prepared snapshots changed. Reopen PSE Bank.")
            else if(which==0)reviewGen5ToGen4Destination(p,first)
            else showGen5ToGen4BoxPicker(p)
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.showGen5ToGen4BoxPicker(p:MainActivity.Gen5Gen4Prepared){
    if(!gen5ToGen4StillBound(p))return showDialog("Gen 5 → Gen 4","The prepared snapshots changed. Reopen PSE Bank.")
    val profile=detectGen4Profile(p.destination)?:return
    val boxes=(0 until PSE_GEN4_BOX_COUNT).filter{box->(0 until PSE_GEN4_SLOTS_PER_BOX).any{slot->
        gen4DestinationSlotIsStructurallyEmpty(p.destination,profile,box*PSE_GEN4_SLOTS_PER_BOX+slot)
    }}
    if(boxes.isEmpty())return showDialog("Gen 5 → Gen 4",gen5ToGen4DestinationLabel(5,0))
    AlertDialog.Builder(this).setTitle("Choose Gen 4 destination box")
        .setMessage("Only boxes with structurally empty slots are listed. Nonzero destination records remain unchanged.")
        .setItems(boxes.map{gen5ToGen4DestinationLabel(1,it)}.toTypedArray()){_,which->
            val box=boxes[which]
            val slots=(0 until PSE_GEN4_SLOTS_PER_BOX).filter{slot->
                gen4DestinationSlotIsStructurallyEmpty(p.destination,profile,box*PSE_GEN4_SLOTS_PER_BOX+slot)
            }
            AlertDialog.Builder(this).setTitle(gen5ToGen4DestinationLabel(2,box))
                .setItems(slots.map{gen5ToGen4DestinationLabel(3,it)}.toTypedArray()){_,slotIndex->
                    reviewGen5ToGen4Destination(p,box*PSE_GEN4_SLOTS_PER_BOX+slots[slotIndex])
                }.setNegativeButton("Cancel",null).show()
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.reviewGen5ToGen4Destination(p:MainActivity.Gen5Gen4Prepared,target:Int){
    val canTransfer={
        gen5ToGen4StillBound(p)&&target in 0 until PSE_GEN4_CAPACITY&&runCatching{
            val fresh=p.destinationFile.readBytes()
            val profile=detectGen4Profile(fresh)?:return@runCatching false
            fresh.contentEquals(p.destination)&&gen4DestinationSlotIsStructurallyEmpty(fresh,profile,target)
        }.getOrDefault(false)
    }
    val oldPid="%08X".format(p.source.pid)
    val newPid="%08X".format(p.converted.pid)
    val pidLine=if(p.source.pid==p.converted.pid)"PID remains $oldPid." else
        "PID changes $oldPid → $newPid only to preserve the reviewed Generation 4 nature, ability slot, gender and shiny state."
    val originTitle=gen4OriginTitle(p.originGame)
    val originLine=if(p.originLegalised)
        "Origin Legalise accepted: game ID ${p.source.originGame} → $originTitle (ID ${p.originGame}). This is the explicit user-selected provenance change reviewed earlier."
    else "Origin remains $originTitle (ID ${p.originGame})."
    val destinationLabel=gen5ToGen4DestinationLabel(6,target)
    val transferLabel="Transfer • Box ${target/PSE_GEN4_SLOTS_PER_BOX+1} of $PSE_GEN4_BOX_COUNT / Slot ${target%PSE_GEN4_SLOTS_PER_BOX+1} of $PSE_GEN4_SLOTS_PER_BOX"
    val preview=buildString{
        append("Final PSE Gen 5 → Gen 4 destination and origin review\n\n")
        append(destinationLabel).append(" The slot is structurally empty; existing and nonzero destination records remain unchanged.\n\n")
        append(originLine).append('\n')
        append("Ball: exact Generation 5 ball ID ${p.source.ball} is preserved in the native PK4.\n")
        append(pidLine).append('\n')
        append("Ability: source slot ${p.source.abilityNumber}, ability #${p.source.ability} → verified Generation 4 ability #${p.converted.ability}.\n")
        append("The previously reviewed represented fields, unspecified met-location requirement, Pokédex mirrors and active block CRC scope are unchanged. Cancel leaves the exact Bank record and destination byte-for-byte unchanged.\n\n")
        append("This is a PSE conversion, not an in-game trade or a claim of official backward-transfer provenance. The exact Gen 5 Bank record remains until the chosen PK4, retained recovery data, Pokédex state, checksums and reopened destination all verify.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${p.source.nickname.ifBlank{"Pokémon #${p.source.species}"}} • Gen 5 → Gen 4")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!canTransfer()){
                showDialog("Gen 5 → Gen 4","The destination identity, Bank record or selected slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            transferGen5BankToGen4(p,target)
        }.setNegativeButton("Cancel",null).show()
}
