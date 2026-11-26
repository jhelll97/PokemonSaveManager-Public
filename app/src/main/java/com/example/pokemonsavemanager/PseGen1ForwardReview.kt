package com.example.pokemonsavemanager

internal fun buildPseGen1ToGen7Review(
    destinationLabel:String,
    originalSource:MainActivity.Gen1Pokemon,
    source:MainActivity.Gen1Pokemon,
    sourceWasLegalised:Boolean,
    converted:MainActivity.PokemonEntry,
    ppUps:IntArray,
    encryptionConstant:String,
    handlerName:String
):String=buildString{
    append("PSE Bank conversion preview\n\n")
    append(destinationLabel).append(" The slot is structurally empty; nonzero destination records remain unchanged.\n\n")
    val legalChanges=ArrayList<String>()
    if(sourceWasLegalised){
        if(originalSource.level!=source.level)legalChanges.add("Level ${originalSource.level} → ${source.level}")
        if(originalSource.experience!=source.experience)legalChanges.add("EXP ${originalSource.experience} → ${source.experience} in the in-memory source copy")
        for(i in 0..3){
            if(originalSource.moves[i]!=source.moves[i]||originalSource.movePP[i]!=source.movePP[i])
                legalChanges.add("Move slot ${i+1}: #${originalSource.moves[i]} PP ${originalSource.movePP[i]} → #${source.moves[i]} PP ${source.movePP[i]}")
        }
    }
    if(legalChanges.isEmpty())append("In-memory source legalisation: none.\n")
    else{append("In-memory source legalisation (the original Bank file remains unchanged until verified cleanup):\n");legalChanges.forEach{append("• ").append(it).append("\n")}}
    append("Preserved: species, nickname, original OT, TID, level, moves, PP-Up counts (${ppUps.joinToString("/")}) and shiny state (${converted.shiny}).\n")
    append("Current PP is healed to the exact Generation 7 maximum after PP Ups: ${converted.movePP.joinToString("/")}. Source EXP ${source.experience} becomes the minimum ${converted.experience} for level ${source.level}; EVs reset to ${converted.evs.joinToString("/")}; friendship resets to species base ${converted.friendship}.\n")
    append("Encryption constant: $encryptionConstant. PID: ${"%08X".format(converted.pid)}; SID becomes 0; nature #${converted.nature} comes from source EXP mod 25; gender becomes #${converted.gender}; ability #${converted.ability}, slot ${converted.abilityNumber}.\n")
    append("IVs are generated as ${converted.ivs.joinToString("/")}: ${if(converted.species==151)5 else 3} guaranteed flawless values and random remaining values. Mew alone receives the fateful-encounter flag (${converted.fatefulEncounter}).\n")
    append("Destination trainer “$handlerName” becomes current handler; original OT/TID remain. Native values: language #${converted.language}, no held item, Poké Ball, met level ${converted.metLevel}, met location #${converted.metLocation}, origin #${converted.originGame} because the exact Gen 1 version is unavailable, and conversion date ${converted.metYear+2000}-${converted.metMonth}-${converted.metDay}.\n")
    append("The Gen 1 catch-rate byte, Stat Exp, source current PP, exact source version, egg/geographic/relearn/ribbon/marking/Pokérus/Hyper Training fields and data without a native PK7 representation are reset, omitted or left empty as shown. OT gender defaults to male because Gen 1 does not encode it.\n\n")
    append("This is a reviewed PSE native PK7 conversion and does not claim official migration provenance. The source save stays unchanged. The exact Bank record remains until a retained destination backup, native PK7, Pokédex registration, Gen 7 checksums and reopened destination verify.")
}
