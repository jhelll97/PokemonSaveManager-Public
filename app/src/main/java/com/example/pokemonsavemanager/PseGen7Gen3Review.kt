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
