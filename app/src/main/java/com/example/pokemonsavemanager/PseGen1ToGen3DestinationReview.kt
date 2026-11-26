package com.example.pokemonsavemanager

import android.app.AlertDialog

private const val PSE_GEN3_FROM_GEN1_BOX_COUNT=14
private const val PSE_GEN3_FROM_GEN1_SLOTS_PER_BOX=30
private const val PSE_GEN3_FROM_GEN1_CAPACITY=PSE_GEN3_FROM_GEN1_BOX_COUNT*PSE_GEN3_FROM_GEN1_SLOTS_PER_BOX

internal fun MainActivity.reviewGen1ToGen3Destination(p:MainActivity.Gen1Gen3Prepared,target:Int){
    val box=target/PSE_GEN3_FROM_GEN1_SLOTS_PER_BOX+1
    val slot=target%PSE_GEN3_FROM_GEN1_SLOTS_PER_BOX+1
    val q=p.converted
    val preview=buildString{
        append("Final PSE Gen 1 → Gen 3 destination and mapping review\n\n")
        append("Chosen destination: Box $box of $PSE_GEN3_FROM_GEN1_BOX_COUNT, Slot $slot of $PSE_GEN3_FROM_GEN1_SLOTS_PER_BOX, within the native $PSE_GEN3_FROM_GEN1_CAPACITY-slot PC. The slot is structurally empty; existing and nonzero destination records remain unchanged.\n\n")
        append("Preserved: species #${q.species}, nickname, OT/TID, experience, level, every move, current PP and reviewed shiny state. PID ${"%08X".format(q.pid)}, nature #${q.nature}, SID 0, IVs ${q.ivs.joinToString("/")}, EVs ${q.evs.joinToString("/")}, friendship 70, English language, no held item, Poké Ball, met location 0, met level ${q.metLevel} and destination origin #${q.originGame} use the already disclosed deterministic mapping.\n")
        append("The Gen 1 catch-rate byte and fields without a native PK3 representation remain omitted. No move is removed or replaced. Cancel leaves the exact PSE1 Bank record and destination byte-for-byte unchanged.\n\n")
        append("There was no official Gen 1/2 → Gen 3 transfer route. This is a PSE conversion, not an official migration, legality certification or verified provenance. The exact Gen 1 Bank record remains until the chosen PK3, retained recovery data, Pokédex mirrors, active-section checksums and reopened destination all verify.")
    }
    var consumed=false
    val transferLabel="Transfer • Box $box of $PSE_GEN3_FROM_GEN1_BOX_COUNT / Slot $slot of $PSE_GEN3_FROM_GEN1_SLOTS_PER_BOX"
    AlertDialog.Builder(this).setTitle("${p.source.nickname.ifBlank{"Pokémon"}} • Gen 1 → Gen 3")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!gen1ToGen3StillBound(p,target)){
                showDialog("Gen 1 → Gen 3","The destination identity, Bank record, conversion or selected native slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            transferGen1BankToGen3(p.bankFile,p.source,p.pk3,p.converted,target)
        }.setNegativeButton("Cancel",null).show()
}
