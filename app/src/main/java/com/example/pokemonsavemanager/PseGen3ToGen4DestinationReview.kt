package com.example.pokemonsavemanager

import android.app.AlertDialog

internal fun MainActivity.showPseGen3ToGen4DestinationReview(
    originalSource:MainActivity.PokemonEntry,
    conversionSource:MainActivity.PokemonEntry,
    converted:MainActivity.PokemonEntry,
    target:Int,
    canTransfer:()->Boolean,
    onTransfer:()->Unit
){
    val box=target/30+1
    val slot=target%30+1
    val destinationLabel="Chosen destination: Box $box of 18, Slot $slot of 30, within the native 540-slot PC."
    val transferLabel="Transfer • Box $box of 18 / Slot $slot of 30"
    val oldPid="%08X".format(originalSource.pid)
    val newPid="%08X".format(converted.pid)
    val pidLine=if(originalSource.pid==converted.pid)"PID remains $oldPid." else
        "PID changes $oldPid → $newPid only to preserve the reviewed ability slot, nature, gender and shiny state in native PK4."
    val preview=buildString{
        append("Final PSE Gen 3 → Gen 4 destination review\n\n")
        append(destinationLabel).append(" The slot is structurally empty; existing and nonzero destination records remain unchanged.\n\n")
        append(pseGen3BallReviewLine(originalSource.ball,conversionSource.ball,4))
        append(pidLine).append('\n')
        append("Ability: source slot ${originalSource.abilityNumber}, ability #${originalSource.ability} → verified Gen 4 ability #${converted.ability}.\n")
        append("The previously reviewed Pal Park location, migration date, represented fields, Pokédex update and checksum scope are unchanged. Cancel leaves the exact Bank record and destination byte-for-byte unchanged.\n\n")
        append("This is a PSE conversion using Gen 4-compatible migration semantics, not an in-game trade or a claim of official transfer provenance. The exact Gen 3 Bank record remains until the chosen PK4, retained recovery data, Pokédex state, checksums and reopened destination all verify.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${originalSource.nickname.ifBlank{"Pokémon #${originalSource.species}"}} • Gen 3 → Gen 4")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!canTransfer()){
                showDialog("Gen 3 → Gen 4","The destination identity, Bank record or selected slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            onTransfer()
        }.setNegativeButton("Cancel",null).show()
}
