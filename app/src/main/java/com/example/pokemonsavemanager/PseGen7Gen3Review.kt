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
