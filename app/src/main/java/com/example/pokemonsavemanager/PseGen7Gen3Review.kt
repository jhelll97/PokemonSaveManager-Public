package com.example.pokemonsavemanager

internal fun buildPseGen7ToGen3Review(
    originNotice:String,
    pidNotice:String,
    markingNote:String,
    omitted:String
):String=buildString{
    append("PSE backward conversion; this is not an in-game trade, official migration or provenance claim. The source save stays unchanged and the exact PK7 remains in Bank until the native PK3 reopens successfully.\n\n")
    append("Preserved: species, OT/TID/SID, nickname, EXP, nature, shiny state, verified non-hidden ability slot, gender, IVs, EVs, moves, current PP, PP Ups, friendship, Pokérus, Ball, met level and markings. ")
    append(originNotice).append(" ").append(pidNotice).append("\n\n")
    append("Required native differences: ").append(markingNote).append(". Gen 7 met and Egg locations become unspecified location 0 through the verified bridges; PK3 does not store encounter dates, current handler, relearn moves, geography, affection, memory or Hyper Training. Omitted exact PK6-bridge metadata: ").append(omitted).append(". Held items, Eggs, alternate forms, fateful encounters, post-Gen-3 species/moves/abilities, unsupported Balls/text and ambiguous ability mappings are blocked instead of discarded.\n\n")
    append("A verified retained recovery backup is required. PSE writes one explicitly selected native PK3, updates only its Pokédex mirrors and required section checksums, reopens the exact record, then removes the Bank entry. Any failure restores or preserves both sides.")
}

internal fun buildPseGen7ToGen4Review(
    pidNote:String,
    originNote:String,
    markingNote:String,
    omitted:String
):String=buildString{
    append("PSE backward conversion; this is not an in-game trade, official migration or provenance claim. The source save stays unchanged and the exact PK7 remains in Bank until the native PK4 reopens successfully.\n\n")
    append("Preserved: species/form, TID/SID, nickname and OT, EXP, nature, gender, shiny state, verified non-hidden ability/slot, IVs, EVs, moves, current PP, PP Ups, active friendship, Pokérus, Ball, met level, dates, markings and fateful marker. ")
    append(pidNote).append(" ").append(originNote).append("\n\n")
    append("Required native differences: ").append(markingNote).append(". Gen 7 met and Egg locations become explicit unspecified location 0 because the catalogues are not interchangeable. PK4 has no current handler, relearn moves, geography, affection, memory or Hyper Training fields; relearn moves and Hyper Training must already be empty. Omitted exact PK6-bridge metadata: ").append(omitted).append(".\n\n")
    append("Held items and unsupported species, forms, moves, abilities, Balls, text or ambiguous fields are blocked instead of discarded. A verified retained recovery backup is required. PSE writes one selected native PK4, updates only its Pokédex flags and active block CRCs, reopens the exact record, then removes the Bank entry. Any failure restores or preserves both sides.")
}

internal fun buildPseGen6ToGen4Review(
    pidNote:String,
    originNote:String,
    omitted:String
):String=buildString{
    append("PSE backward conversion; this is not an in-game trade, official migration or provenance claim. The source save stays unchanged and the exact PK6 remains in Bank until the native PK4 reopens successfully.\n\n")
    append("Preserved: species/form, TID/SID, nickname and OT, EXP, nature, gender, shiny state, verified non-hidden ability/slot, IVs, EVs, moves, current PP, PP Ups, active friendship, Pokérus, Ball, met level, dates, markings and fateful marker. ")
    append(pidNote).append(" ").append(originNote).append("\n\n")
    append("Required native differences: Gen 6 met and Egg locations become explicit unspecified location 0 because the catalogues are not interchangeable. PK4 has no current handler, relearn moves, geography, affection, memory or Hyper Training fields; relearn moves and Hyper Training must already be empty. Omitted exact PK6 metadata: ").append(omitted).append(".\n\n")
    append("Held items and unsupported species, forms, moves, abilities, Balls, text or ambiguous fields are blocked instead of discarded. A verified retained recovery backup is required. PSE writes one selected native PK4, updates only its Pokédex flags and active block CRCs, reopens the exact record, then removes the Bank entry. Any failure restores or preserves both sides.")
}

internal fun buildPseGen7ToGen5Review(
    pidNote:String,
    originNote:String,
    markingNote:String,
    omitted:String
):String=buildString{
    append("PSE backward conversion; this is not an in-game trade, official migration or provenance claim. The source save stays unchanged and the exact PK7 remains in Bank until the native PK5 reopens successfully.\n\n")
    append("Preserved: destination-valid species/form, TID/SID, nickname and OT, EXP, nature, gender, shiny state, verified ability/slot, IVs, EVs, moves, current PP, PP Ups, active friendship, Pokérus, Ball, met level, dates, markings and fateful marker. ")
    append(pidNote).append(" ").append(originNote).append("\n\n")
    append("Reviewed native differences: ").append(markingNote).append(". Gen 7 met and Egg locations become explicit unspecified location 0 because the catalogues are not interchangeable. PK5 has no current handler, relearn-move, geography, affection, memory, Resort, Hyper Training or Gen 7-only ribbon fields; unsupported states must already be empty. Omitted exact bridge metadata: ").append(omitted).append(".\n\n")
    append("Held items and post-Gen-5 species, forms, moves, abilities, Balls, text or ambiguous fields are blocked instead of discarded. A verified retained recovery backup is required. PSE writes one selected native PK5, updates only required Pokédex flags, the box CRC and checksum table, reopens the exact record, then removes the Bank entry. Any failure restores or preserves both sides.")
}

internal fun buildPseGen5ToGen3Review(
    originNotice:String,
    pidNotice:String
):String=buildString{
    append("PSE backward conversion; this is not an in-game trade, official migration or provenance claim. The source save stays unchanged and the exact PK5 remains in Bank until the native PK3 reopens successfully.\n\n")
    append("Preserved: species, OT/TID/SID, nickname, EXP, nature, shiny state, verified non-hidden ability slot, gender, IVs, EVs, moves, current PP, PP Ups, friendship, Pokérus, Ball, met level and markings. ")
    append(originNotice).append(" ").append(pidNotice).append("\n\n")
    append("Required native differences: PK3 met location becomes 0 (unspecified); Gen 5 location IDs and encounter dates are not written. Held items, Eggs, alternate forms, fateful encounters, post-Gen-3 species/moves/abilities, unsupported Balls/text and ambiguous ability mappings are blocked instead of discarded.\n\n")
    append("A verified retained recovery backup is required. PSE writes one explicitly selected native PK3, updates only its Pokédex mirrors and required section checksums, reopens the exact record, then removes the Bank entry. Any failure restores or preserves both sides.")
}

internal fun buildPseGen4ToGen3Review(
    pid:Long,species:Int,nickname:String,otName:String,tid:Int,sid:Int,experience:Long,
    nature:Int,gender:Int,shiny:Boolean,ability:Int,abilityNumber:Int,
    moves:IntArray,movePP:IntArray,ppUps:IntArray,ivs:IntArray,evs:IntArray,friendship:Int,
    pokerusStrain:Int,pokerusDays:Int,ball:Int,metLevel:Int,markingValue:Int,
    originDisclosure:String,metYear:Int,metMonth:Int,metDay:Int
):String=buildString{
    append("PSE Bank conversion preview\n\n")
    append("Native PK3 values: PID ${"%08X".format(pid)}, species #$species, nickname “$nickname”, OT “$otName”, TID/SID $tid/$sid, EXP $experience.\n")
    append("Nature #$nature, gender #$gender, shiny $shiny, ability #$ability in slot $abilityNumber.\n")
    append("Moves: ${moves.joinToString("/")}. Current PP: ${movePP.joinToString("/")}. PP Ups: ${ppUps.joinToString("/")}.\n")
    append("IVs: ${ivs.joinToString("/")}. EVs: ${evs.joinToString("/")} (total ${evs.sum()}). Friendship $friendship; Pokérus strain $pokerusStrain/days $pokerusDays; Ball #$ball; met level $metLevel; markings 0x${markingValue.toString(16).uppercase()}.\n")
    append(originDisclosure).append("\n")
    append("Destination format changes: met location becomes 0 (unspecified); Gen 4 migration date ${metYear.toString().padStart(4,'0')}-${metMonth.toString().padStart(2,'0')}-${metDay.toString().padStart(2,'0')} is omitted because PK3 has no date fields.\n")
    append("Held items, forms, Eggs, fateful encounters and other unsupported records remain blocked, so none are silently discarded. This is a reviewed backward PSE conversion and does not claim official reverse-migration provenance.\n\n")
    append("The source save stays unchanged. The exact PK4 Bank record remains until a Keep-pinned destination backup, one native PK3, unchanged unrelated PC data, Pokédex mirrors, affected section checksums and reopened destination all verify. The Keep-pinned destination backup and independent exact Bank snapshot stay byte-bound before writing and through cleanup. If failure happens before PSE writes or deletes a side, PSE verifies that side stayed exact instead of claiming restoration; after a PSE mutation, it restores and reopens that side independently.")
}

internal fun buildPseGen4ToGen7Review(
    pidNote:String,sourceAbility:Int,sourceAbilityNumber:Int,verifiedAbility:Int,
    markingNote:String,oldMet:String,oldEgg:String,trainerName:String,trainerGender:String
):String=buildString{
    append("PSE Bank conversion preview; this does not claim an official Pokémon Bank transaction or provenance.\n\n")
    append("Preserved: species/form, TID/SID, nickname and OT, EXP, verified ability, nature, gender, shiny state, moves, current PP, PP Ups, IVs, EVs, friendship, Pokérus, Ball, language, source title, met level/date and fateful marker.\n")
    append("$pidNote\nAbility: source ability #$sourceAbility in slot $sourceAbilityNumber remains verified ability #$verifiedAbility.\n")
    append("Markings: $markingNote.\n")
    append("Required format changes: Gen 4 met location $oldMet becomes native transfer marker 30001; Egg location $oldEgg becomes 0. Current handler becomes $trainerName ($trainerGender); relearn moves, geographic fields, Resort state and Hyper Training start empty.\n\n")
    append("Held items, Eggs and unsupported values are blocked instead of discarded. The source save stays unchanged. The exact Gen 4 Bank record remains until a retained recovery backup, chosen PK7 slot, Pokédex update, block CRCs, save signature and reopened destination all verify.")
}
