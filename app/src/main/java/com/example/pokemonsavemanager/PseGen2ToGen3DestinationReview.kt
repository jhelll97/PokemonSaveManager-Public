package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN3_FROM_GEN2_BOX_COUNT=14
private const val PSE_GEN3_FROM_GEN2_SLOTS_PER_BOX=30
private const val PSE_GEN3_FROM_GEN2_CAPACITY=PSE_GEN3_FROM_GEN2_BOX_COUNT*PSE_GEN3_FROM_GEN2_SLOTS_PER_BOX

internal fun MainActivity.reviewGen2ToGen3Destination(p:MainActivity.Gen2Gen3Prepared,target:Int){
    val box=target/PSE_GEN3_FROM_GEN2_SLOTS_PER_BOX+1
    val slot=target%PSE_GEN3_FROM_GEN2_SLOTS_PER_BOX+1
    val q=p.converted
    val source=p.source
    val preview=buildString{
        append("Final PSE Gen 2 → Gen 3 destination and Legalise review\n\n")
        append("Chosen destination: Box $box of $PSE_GEN3_FROM_GEN2_BOX_COUNT, Slot $slot of $PSE_GEN3_FROM_GEN2_SLOTS_PER_BOX, within the native $PSE_GEN3_FROM_GEN2_CAPACITY-slot PC. The slot is structurally empty; existing and nonzero destination records remain unchanged.\n\n")
        append("Accepted Gen 3 mapping:\n${p.legaliseChanges}\n\n")
        append("Preserved: species #${source.species}, nickname, OT/TID, experience, level ${source.level}, every supported move, current PP ${q.movePP.joinToString("/")}, PP Ups ${p.ppUps.joinToString("/")}, happiness ${source.happiness}, Pokérus, gender and shiny state. Final PID ${"%08X".format(q.pid)}, nature #${q.nature}, SID ${q.sid}, ability #${q.ability} in normal slot ${q.abilityNumber}, IVs ${q.ivs.joinToString("/")} and EVs ${q.evs.joinToString("/")} use the disclosed deterministic mapping.\n")
        append("Native Gen 3 defaults are no held item, Poké Ball, met location 0, met level ${q.metLevel}, destination origin #${q.originGame}, English language and male OT. Any reviewed Gen 2 held item or caught/met value remains omitted exactly as disclosed. No move is removed or replaced.\n\n")
        append("There was no official Gen 2 → Gen 3 transfer route. This is a PSE conversion, not an official migration, legality certification or verified provenance. Cancel leaves the exact PSE2 Bank record and destination byte-for-byte unchanged. The Bank record remains until the chosen PK3, retained recovery data, Pokédex mirrors, active-section checksums and reopened destination all verify.")
    }
    var consumed=false
    val transferLabel="Transfer • Box $box of $PSE_GEN3_FROM_GEN2_BOX_COUNT / Slot $slot of $PSE_GEN3_FROM_GEN2_SLOTS_PER_BOX"
    AlertDialog.Builder(this).setTitle("${source.nickname.ifBlank{"Pokémon"}} • Gen 2 → Gen 3")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!gen2ToGen3StillBound(p,target)){
                showDialog("Gen 2 → Gen 3","The destination identity, Bank record, Legalise plan, deterministic PK3 or selected native slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            transferGen2BankToGen3(p.bankFile,p.source,p.bankBytes,p.destination,p.meta,p.plan,p.pk3,p.converted,target,p.openedFile,p.openedUri,p.openedLabel)
        }.setNegativeButton("Cancel",null).show()
}
