package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN6_BACKWARD_BOX_COUNT=31
private const val PSE_GEN6_BACKWARD_SLOTS_PER_BOX=30
private const val PSE_GEN6_BACKWARD_CAPACITY=PSE_GEN6_BACKWARD_BOX_COUNT*PSE_GEN6_BACKWARD_SLOTS_PER_BOX

private fun gen7ToGen6DestinationLabel(kind:Int,index:Int):String=when(kind){
    0 -> "First Structurally Empty • Box ${index/PSE_GEN6_BACKWARD_SLOTS_PER_BOX+1} of $PSE_GEN6_BACKWARD_BOX_COUNT • Slot ${index%PSE_GEN6_BACKWARD_SLOTS_PER_BOX+1} of $PSE_GEN6_BACKWARD_SLOTS_PER_BOX"
    1 -> "Box ${index+1} of $PSE_GEN6_BACKWARD_BOX_COUNT"
    2 -> "Box ${index+1} of $PSE_GEN6_BACKWARD_BOX_COUNT • choose slot"
    3 -> "Slot ${index+1} of $PSE_GEN6_BACKWARD_SLOTS_PER_BOX"
    5 -> "All $PSE_GEN6_BACKWARD_CAPACITY native Generation 6 PC slots are unavailable. Nonzero destination records and the Gen 7 Bank source were left unchanged."
    6 -> "Chosen destination: Box ${index/PSE_GEN6_BACKWARD_SLOTS_PER_BOX+1} of $PSE_GEN6_BACKWARD_BOX_COUNT, Slot ${index%PSE_GEN6_BACKWARD_SLOTS_PER_BOX+1} of $PSE_GEN6_BACKWARD_SLOTS_PER_BOX, within the native $PSE_GEN6_BACKWARD_CAPACITY-slot PC."
    else -> error("Unknown Gen 7 to Gen 6 destination label")
}

private fun gen6OriginTitle(id:Int):String=when(id){
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
    20->"Pokémon White"
    21->"Pokémon Black"
    22->"Pokémon White 2"
    23->"Pokémon Black 2"
    24->"Pokémon X"
    25->"Pokémon Y"
    26->"Pokémon Alpha Sapphire"
    27->"Pokémon Omega Ruby"
    else->"game ID $id"
}

internal fun MainActivity.chooseGen7ToGen6Destination(p:MainActivity.Gen7Gen6Prepared){
    if(!gen7ToGen6SnapshotsMatch(p))return showDialog("Gen 7 → Gen 6","The destination or Bank record changed. Reopen PSE Bank.")
    val info=boxStorageInfo(p.destination)?:return
    if(info.second!=PSE_GEN6_BACKWARD_CAPACITY)return showDialog("Gen 7 → Gen 6","The native Generation 6 PC capacity could not be verified. Nothing was changed.")
    val first=(0 until PSE_GEN6_BACKWARD_CAPACITY).firstOrNull{
        gen6DestinationSlotIsStructurallyEmpty(p.destination,info.first,info.second,info.third,it)
    }?:return showDialog("Gen 7 → Gen 6",gen7ToGen6DestinationLabel(5,0))
    AlertDialog.Builder(this).setTitle("Choose native Gen 6 destination")
        .setMessage("The reviewed PK6 will be written to one structurally empty slot. PSE will update only that slot, required Pokédex flags and native block checksums. Nonzero destination records remain unchanged.")
        .setItems(arrayOf(gen7ToGen6DestinationLabel(0,first),"Choose Box / Slot • $PSE_GEN6_BACKWARD_BOX_COUNT Boxes × $PSE_GEN6_BACKWARD_SLOTS_PER_BOX slots")){_,which->
            if(!gen7ToGen6SnapshotsMatch(p))showDialog("Gen 7 → Gen 6","The destination or Bank record changed. Reopen PSE Bank.")
            else if(which==0)reviewGen7ToGen6Destination(p,first)
            else showGen7ToGen6BoxPicker(p)
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.showGen7ToGen6BoxPicker(p:MainActivity.Gen7Gen6Prepared){
    if(!gen7ToGen6SnapshotsMatch(p))return showDialog("Gen 7 → Gen 6","The destination or Bank record changed. Reopen PSE Bank.")
    val info=boxStorageInfo(p.destination)?:return
    if(info.second!=PSE_GEN6_BACKWARD_CAPACITY)return showDialog("Gen 7 → Gen 6","The native Generation 6 PC capacity could not be verified. Nothing was changed.")
    val boxes=(0 until PSE_GEN6_BACKWARD_BOX_COUNT).filter{box->(0 until PSE_GEN6_BACKWARD_SLOTS_PER_BOX).any{slot->
        gen6DestinationSlotIsStructurallyEmpty(p.destination,info.first,info.second,info.third,box*PSE_GEN6_BACKWARD_SLOTS_PER_BOX+slot)
    }}
    if(boxes.isEmpty())return showDialog("Gen 7 → Gen 6",gen7ToGen6DestinationLabel(5,0))
    AlertDialog.Builder(this).setTitle("Choose Gen 6 destination box")
        .setItems(boxes.map{gen7ToGen6DestinationLabel(1,it)}.toTypedArray()){_,which->
            val box=boxes[which]
            val slots=(0 until PSE_GEN6_BACKWARD_SLOTS_PER_BOX).filter{slot->
                gen6DestinationSlotIsStructurallyEmpty(p.destination,info.first,info.second,info.third,box*PSE_GEN6_BACKWARD_SLOTS_PER_BOX+slot)
            }
            AlertDialog.Builder(this).setTitle(gen7ToGen6DestinationLabel(2,box))
                .setItems(slots.map{gen7ToGen6DestinationLabel(3,it)}.toTypedArray()){_,slotIndex->
                    reviewGen7ToGen6Destination(p,box*PSE_GEN6_BACKWARD_SLOTS_PER_BOX+slots[slotIndex])
                }.setNegativeButton("Cancel",null).show()
        }.setNegativeButton("Cancel",null).show()
}

private fun MainActivity.reviewGen7ToGen6Destination(p:MainActivity.Gen7Gen6Prepared,target:Int){
    val canTransfer={
        val current=runCatching{p.destinationFile.readBytes()}.getOrNull()
        val info=current?.let{boxStorageInfo(it)}
        gen7ToGen6SnapshotsMatch(p)&&!p.submitted&&target in 0 until PSE_GEN6_BACKWARD_CAPACITY&&
            current!=null&&current.contentEquals(p.destination)&&info!=null&&info.second==PSE_GEN6_BACKWARD_CAPACITY&&
            gen6DestinationSlotIsStructurallyEmpty(current,info.first,info.second,info.third,target)
    }
    val legalised=p.source.originGame!=p.originGame
    val originTitle=gen6OriginTitle(p.originGame)
    val originLine=if(legalised)
        "Origin Legalise accepted: game ID ${p.source.originGame} → $originTitle (ID ${p.originGame}). This is the explicit user-selected provenance change reviewed earlier."
    else "Origin remains $originTitle (ID ${p.originGame})."
    val destinationLabel=gen7ToGen6DestinationLabel(6,target)
    val transferLabel="Transfer • Box ${target/PSE_GEN6_BACKWARD_SLOTS_PER_BOX+1} of $PSE_GEN6_BACKWARD_BOX_COUNT / Slot ${target%PSE_GEN6_BACKWARD_SLOTS_PER_BOX+1} of $PSE_GEN6_BACKWARD_SLOTS_PER_BOX"
    val preview=buildString{
        append("Final PSE Gen 7 → Gen 6 destination and origin review\n\n")
        append(destinationLabel).append(" The slot is structurally empty; existing and nonzero destination records remain unchanged.\n\n")
        append(originLine).append('\n')
        append("Ball: exact Generation 7 ball ID ${p.source.ball} is preserved in the native PK6.\n")
        append("PID remains ${"%08X".format(p.source.pid)}.\n")
        append("Ability: source slot ${p.source.abilityNumber}, ability #${p.source.ability} → verified Generation 6 ability #${p.converted.ability}.\n")
        append("Markings: ${p.markingNote}.\n")
        append("The previously reviewed empty Super Training Bag state, blocked Resort, Hyper Training and Gen 7-only data, represented fields, Pokédex flags and native checksum scope are unchanged. Cancel leaves the exact Bank record and destination byte-for-byte unchanged.\n\n")
        append("This is a PSE conversion, not an in-game trade or a claim of official backward-transfer provenance. The exact Gen 7 Bank record remains until the chosen PK6, retained recovery data, Pokédex state, checksums and reopened destination all verify.")
    }
    AlertDialog.Builder(this).setTitle("${p.source.nickname.ifBlank{"Pokémon #${p.source.species}"}} • Gen 7 → Gen 6")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(!canTransfer()){
                showDialog("Gen 7 → Gen 6","The destination identity, Bank record or selected slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            transferGen7BankToGen6(p,target)
        }.setNegativeButton("Cancel",null).show()
}
