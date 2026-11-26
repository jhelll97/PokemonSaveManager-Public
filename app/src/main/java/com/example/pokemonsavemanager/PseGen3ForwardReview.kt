package com.example.pokemonsavemanager

internal fun buildPseGen3ToGen7Review(
    source:MainActivity.PokemonEntry,
    conversionSource:MainActivity.PokemonEntry,
    converted:MainActivity.PokemonEntry,
    speciesName:String,
    abilityId:Int,
    baseFriendship:Int,
    trainerName:String,
    trainerGender:String,
    destinationLabel:String?=null
):String=buildString{
    val oldPid="%08X".format(source.pid);val newPid="%08X".format(converted.pid)
    val pidNote=if(source.pid==converted.pid)"PID remains $oldPid." else
        "PID changes $oldPid → $newPid only to preserve the reviewed ${if(source.shiny)"shiny" else "non-shiny"} state under the Gen 7 threshold."
    append("PSE Bank conversion preview\n\n")
    destinationLabel?.let{append(it).append(" The slot is structurally empty; nonzero destination records remain unchanged.\n\n")}
    append("Preserved: species ($speciesName), TID/SID, nickname and OT, EXP, nature, gender, shiny state, moves, current PP, PP Ups, IVs, EVs, Pokérus, language and source title.\n")
    append(pseGen3BallReviewLine(source.ball,conversionSource.ball,7))
    append("$pidNote\n")
    append("Ability: source slot ${source.abilityNumber}, ability #${source.ability} → verified Gen 7 ability #$abilityId.\n")
    append("Required Gen 7 changes: held items and Eggs are blocked; current handler becomes $trainerName ($trainerGender); current-handler friendship starts at species base friendship $baseFriendship; original OT friendship remains ${source.friendship}; met location becomes transfer marker 30001 with today's conversion date; Egg location, relearn moves and geographic fields start empty. Gen 3 contest data, ribbons, markings, original met/egg details and event marker are not represented by this bounded PK7 conversion.\n\n")
    append("This is a PSE conversion, not an in-game trade or a claim of official transfer provenance. The source save stays unchanged. PSE writes only one structurally empty native Gen 7 slot; nonzero destination records remain unchanged. The exact Gen 3 Bank record remains until a verified recovery backup is retained and the reviewed PK7, Pokédex update, checksums and reopened destination all verify.")
}
