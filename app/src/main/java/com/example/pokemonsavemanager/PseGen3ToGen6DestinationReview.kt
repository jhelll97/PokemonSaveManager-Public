package com.example.pokemonsavemanager

import android.app.AlertDialog

internal fun MainActivity.showPseGen3ToGen6DestinationReview(
    originalSource:MainActivity.PokemonEntry,
    conversionSource:MainActivity.PokemonEntry,
    converted:MainActivity.PokemonEntry,
    destinationLabel:String,
    transferLabel:String,
    canTransfer:()->Boolean,
    onTransfer:()->Unit
){
    val oldPid="%08X".format(originalSource.pid)
    val newPid="%08X".format(converted.pid)
    val pidLine=if(originalSource.pid==converted.pid)"PID remains $oldPid." else
        "PID changes $oldPid → $newPid only to preserve the reviewed ability slot, nature, gender and shiny state in native PK6."
    val preview=buildString{
        append("Final PSE Gen 3 → Gen 6 destination review\n\n")
        append(destinationLabel).append(" The slot is structurally empty; existing and nonzero destination records remain unchanged.\n\n")
        append(pseGen3BallReviewLine(originalSource.ball,conversionSource.ball,6))
        append(pidLine).append('\n')
        append("Ability: source slot ${originalSource.abilityNumber}, ability #${originalSource.ability} → verified Gen 6 ability #${converted.ability}.\n")
        append("The previously reviewed converted fields, transfer marker, conversion date, handler, empty relearn and geographic fields, Pokédex update and checksum scope are unchanged. Cancel leaves the exact Bank record and destination byte-for-byte unchanged.\n\n")
        append("This is a PSE conversion, not an in-game trade or a claim of official transfer provenance. The exact Gen 3 Bank record remains until the chosen PK6, retained recovery data, Pokédex state, checksums and reopened destination all verify.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${originalSource.nickname.ifBlank{"Pokémon #${originalSource.species}"}} • Gen 3 → Gen 6")
        .setMessage(preview).setPositiveButton(transferLabel){_,_->
            if(consumed||!canTransfer()){
                showDialog("Gen 3 → Gen 6","The destination identity, Bank record or selected slot changed, or this transfer was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            onTransfer()
        }.setNegativeButton("Cancel",null).show()
}
