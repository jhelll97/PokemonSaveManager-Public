package com.example.pokemonsavemanager

private fun appendPseGen2PreservedReview(out:StringBuilder,converted:MainActivity.PokemonEntry,ppUps:IntArray){
    out.append("Preserved: species, nickname, original OT/TID, EXP, level, moves, current PP (${converted.movePP.joinToString("/")}), PP Ups (${ppUps.joinToString("/")}), happiness ${converted.friendship}, Pokérus strain ${converted.pokerusStrain}/days ${converted.pokerusDays}, and shiny state (${converted.shiny}).\n")
}

private fun appendPseGen2PidReview(out:StringBuilder,converted:MainActivity.PokemonEntry,gender:Int=converted.gender){
    out.append("PID: ${"%08X".format(converted.pid)}; SID becomes 0; nature #${converted.nature} is EXP mod 25; gender becomes #$gender; ability #${converted.ability}, slot ${converted.abilityNumber}.\n")
}

private fun appendPseGen2Omissions(out:StringBuilder,source:MainActivity.Gen2Pokemon,generation:Int,packedLabel:String,representation:String){
    if(source.heldItem!=0)out.append("Held item #${source.heldItem} is explicitly removed because Gen 2 and Gen $generation item IDs are not interchangeable.\n")
    if(source.caughtData!=0)out.append("$packedLabel 0x${source.caughtData.toString(16).uppercase().padStart(4,'0')} $representation\n")
}

internal fun buildPseGen2ToGen3Review(
    legaliseChanges:String,firstDestination:String,source:MainActivity.Gen2Pokemon,converted:MainActivity.PokemonEntry,
    ppUps:IntArray,gender:Int
):String=buildString{
    append("PSE Bank conversion preview\n\n")
    append(legaliseChanges).append("\n\n")
    append("$firstDestination; Choose Box / Slot can select another structurally empty slot. Every nonzero destination record remains unchanged.\n")
    appendPseGen2PreservedReview(this,converted,ppUps)
    append("PID: ${"%08X".format(converted.pid)}; SID becomes 0; nature #${converted.nature} is EXP mod 25; gender becomes #$gender; ability slot becomes ${converted.abilityNumber}.\n")
    append("IVs: deterministic Gen 3 mapping ${converted.ivs.joinToString("/")}. EVs: Gen 2 Stat Exp ÷ 257, with the single Special budget split across SpA/SpD and total capped at 510; final ${converted.evs.joinToString("/")} (total ${converted.evs.sum()}).\n")
    append("Native values: no held item, Poké Ball, met level ${converted.metLevel}, origin #${converted.originGame}, language #${converted.language}, met location 0, and male OT-gender default.\n")
    appendPseGen2Omissions(this,source,3,"Packed Gen 2 caught/met value","is omitted because it has no exact PK3 representation.")
    append("Egg and unsupported native fields remain empty. This is a reviewed PSE native PK3 conversion and does not claim official migration provenance.\n\n")
    append("The source save stays unchanged. The exact Bank record remains until a Keep-pinned destination backup, native PK3, current PP and PP Ups, Pokédex mirrors, affected Gen 3 checksums and reopened destination all verify. The retained destination backup and independent Bank recovery snapshot stay byte-exact through Bank cleanup; failure restores the destination and Bank record independently.")
}

internal fun buildPseGen2ToGen4Review(
    legaliseChanges:String,destinationLabel:String,source:MainActivity.Gen2Pokemon,converted:MainActivity.PokemonEntry,ppUps:IntArray
):String=buildString{
    append("PSE Bank conversion preview\n\n")
    append(legaliseChanges).append("\n\n")
    append(destinationLabel).append("\n")
    append("The chosen slot is structurally empty; nonzero destination records remain unchanged.\n")
    appendPseGen2PreservedReview(this,converted,ppUps)
    appendPseGen2PidReview(this,converted)
    append("IVs: deterministic Gen 4 mapping ${converted.ivs.joinToString("/")}. EVs: Gen 2 Stat Exp ÷ 257, with the single Special budget split across SpA/SpD and total capped at 510; final ${converted.evs.joinToString("/")} (total ${converted.evs.sum()}).\n")
    append("Native values: no held item, Poké Ball, met level ${converted.metLevel}, origin #${converted.originGame}, language #${converted.language}, and male OT-gender default.\n")
    appendPseGen2Omissions(this,source,4,"Packed Gen 2 caught/met value","is omitted because it has no exact PK4 representation.")
    append("Egg and unsupported native fields remain empty. This is a reviewed PSE native PK4 conversion and does not claim official migration or verified provenance.\n\n")
    append("The source save stays unchanged. The exact Bank record remains until a Keep-pinned destination backup, native PK4, current PP and PP Ups, Pokédex registration, Gen 4 checksums and reopened destination all verify. Failure restores the destination and Bank record independently.")
}

internal fun buildPseGen2ToGen5Review(
    legaliseChanges:String,destinationLabel:String,source:MainActivity.Gen2Pokemon,converted:MainActivity.PokemonEntry,ppUps:IntArray
):String=buildString{
    append("PSE Bank conversion preview\n\n")
    append(legaliseChanges).append("\n\n")
    append(destinationLabel).append(" The slot is structurally empty; nonzero destination records remain unchanged.\n")
    appendPseGen2PreservedReview(this,converted,ppUps)
    appendPseGen2PidReview(this,converted)
    append("IVs: deterministic Gen 5 mapping ${converted.ivs.joinToString("/")}. EVs: Gen 2 Stat Exp ÷ 257, with the single Special budget split across SpA/SpD and total capped at 510; final ${converted.evs.joinToString("/")} (total ${converted.evs.sum()}).\n")
    append("Native values: no held item, Poké Ball, met level ${converted.metLevel}, origin #${converted.originGame}, and OT gender #${converted.otGender}.\n")
    appendPseGen2Omissions(this,source,5,"Packed Gen 2 caught location/time","is omitted; only its recorded OT gender is preserved when present.")
    append("Egg and unsupported native fields remain empty. This is a reviewed PSE native PK5 conversion and does not claim official migration or verified provenance.\n\n")
    append("The source save stays unchanged. The exact Bank record remains until a Keep-pinned destination backup, native PK5, current PP and PP Ups, Pokédex registration, Gen 5 checksums and reopened destination all verify. Failure restores the destination and Bank record independently.")
}

internal fun buildPseGen2ToGen6Review(
    legaliseChanges:String,destinationLabel:String,source:MainActivity.Gen2Pokemon,converted:MainActivity.PokemonEntry,ppUps:IntArray,
    encryptionConstant:String,handlerName:String
):String=buildString{
    append("PSE Bank conversion preview\n\n")
    append(legaliseChanges).append("\n\n")
    append(destinationLabel).append(" The slot is structurally empty; nonzero destination records remain unchanged.\n")
    appendPseGen2PreservedReview(this,converted,ppUps)
    append("Encryption constant: $encryptionConstant. ")
    appendPseGen2PidReview(this,converted)
    append("IVs: deterministic Gen 6 mapping ${converted.ivs.joinToString("/")}. EVs: Gen 2 Stat Exp ÷ 257, with the single Special budget split across SpA/SpD and total capped at 510; final ${converted.evs.joinToString("/")} (total ${converted.evs.sum()}).\n")
    append("Destination trainer “$handlerName” becomes current handler. Native values: language #${converted.language}, no held item, Poké Ball, met level ${converted.metLevel}, PSE Johto marker #${converted.metLocation}, origin #${converted.originGame}, and no met date.\n")
    appendPseGen2Omissions(this,source,6,"Gen 2 caught/met value","is omitted because it has no exact PK6 representation.")
    append("Egg, geographic, relearn, ribbon, marking and other unsupported fields remain empty; OT gender defaults to male because Gen 2 does not encode it in this Bank record.\n\n")
    append("This is a reviewed PSE native PK6 conversion and does not claim official migration provenance. The source save stays unchanged. The exact Bank record remains until a Keep-pinned destination backup, native PK6, PP Ups, Pokédex registration, Gen 6 checksums and reopened destination all verify.")
}

internal fun buildPseGen2ToGen7Review(
    legaliseChanges:String,source:MainActivity.Gen2Pokemon,converted:MainActivity.PokemonEntry,ppUps:IntArray,
    encryptionConstant:String,handlerName:String
):String=buildString{
    append("PSE Bank conversion preview\n\n")
    append(legaliseChanges).append("\n\n")
    appendPseGen2PreservedReview(this,converted,ppUps)
    append("Encryption constant: $encryptionConstant. ")
    appendPseGen2PidReview(this,converted)
    append("IVs: deterministic Gen 7 mapping ${converted.ivs.joinToString("/")}, including ${if(source.species==251)5 else 3} required flawless values while preserving gender and shiny state. EVs: Gen 2 Stat Exp ÷ 257, with the single Special budget split across SpA/SpD and total capped at 510; final ${converted.evs.joinToString("/")} (total ${converted.evs.sum()}).\n")
    append("Destination trainer “$handlerName” becomes current handler. Native values: language #${converted.language}, no held item, Poké Ball, met level ${converted.metLevel}, PSE Johto marker #${converted.metLocation}, origin #${converted.originGame}, and no met date. Celebi alone receives the fateful-encounter flag (${converted.fatefulEncounter}).\n")
    appendPseGen2Omissions(this,source,7,"Gen 2 caught/met value","is omitted because it has no exact PK7 representation.")
    append("Egg, geographic, relearn, ribbon, marking and Hyper Training fields remain empty; OT gender defaults to male because Gen 2 does not encode it in this Bank record.\n\n")
    append("This is a reviewed PSE native PK7 conversion and does not claim official migration provenance. The source save stays unchanged. The exact Bank record remains until a Keep-pinned destination backup, native PK7, PP Ups, Pokédex registration, Gen 7 checksums and reopened destination all verify.")
}
