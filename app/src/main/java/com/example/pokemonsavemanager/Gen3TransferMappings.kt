package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog

internal fun gen3CompatibleOriginTitle(id:Int):String?=when(id){
    1->"Pokémon Sapphire"
    2->"Pokémon Ruby"
    3->"Pokémon Emerald"
    4->"Pokémon FireRed"
    5->"Pokémon LeafGreen"
    15->"Pokémon Colosseum / XD"
    else->null
}

internal fun gen3ToGen4PidLegaliseRejection(species:Int,pid:Int,abilityNumber:Int):String?{
    if(((pid and 1)+1)==abilityNumber)return null
    return when(species){
        201->"PID Legalise is blocked for Unown because the current conversion cannot prove that a replacement PID preserves its form."
        265->"PID Legalise is blocked for Wurmple because the current conversion cannot prove that a replacement PID preserves its evolution path."
        327->"PID Legalise is blocked for Spinda because changing the PID changes its spot pattern."
        else->null
    }
}

private val gen3AbilityNames="-------|STENCH|DRIZZLE|SPEED BOOST|BATTLE ARMOR|STURDY|DAMP|LIMBER|SAND VEIL|STATIC|VOLT ABSORB|WATER ABSORB|OBLIVIOUS|CLOUD NINE|COMPOUNDEYES|INSOMNIA|COLOR CHANGE|IMMUNITY|FLASH FIRE|SHIELD DUST|OWN TEMPO|SUCTION CUPS|INTIMIDATE|SHADOW TAG|ROUGH SKIN|WONDER GUARD|LEVITATE|EFFECT SPORE|SYNCHRONIZE|CLEAR BODY|NATURAL CURE|LIGHTNINGROD|SERENE GRACE|SWIFT SWIM|CHLOROPHYLL|ILLUMINATE|TRACE|HUGE POWER|POISON POINT|INNER FOCUS|MAGMA ARMOR|WATER VEIL|MAGNET PULL|SOUNDPROOF|RAIN DISH|SAND STREAM|PRESSURE|THICK FAT|EARLY BIRD|FLAME BODY|RUN AWAY|KEEN EYE|HYPER CUTTER|PICKUP|TRUANT|HUSTLE|CUTE CHARM|PLUS|MINUS|FORECAST|STICKY HOLD|SHED SKIN|GUTS|MARVEL SCALE|LIQUID OOZE|OVERGROW|BLAZE|TORRENT|SWARM|ROCK HEAD|DROUGHT|ARENA TRAP|VITAL SPIRIT|WHITE SMOKE|PURE POWER|SHELL ARMOR|CACOPHONY|AIR LOCK".split("|")
private val gen3SpeciesAbilityRows="65,0|65,0|65,0|66,0|66,0|66,0|67,0|67,0|67,0|19,0|61,0|14,0|19,0|61,0|68,0|51,0|51,0|51,0|50,62|50,62|51,0|51,0|22,61|22,61|9,0|9,0|8,0|8,0|38,0|38,0|38,0|38,0|38,0|38,0|56,0|56,0|18,0|18,0|56,0|56,0|39,0|39,0|34,0|34,0|34,0|27,0|27,0|14,0|19,0|8,71|8,71|53,0|7,0|6,13|6,13|72,0|72,0|22,18|22,18|11,6|11,6|11,6|28,39|28,39|28,39|62,0|62,0|62,0|34,0|34,0|34,0|29,64|29,64|69,5|69,5|69,5|50,18|50,18|12,20|12,20|42,5|42,5|51,39|50,48|50,48|47,0|47,0|1,60|1,60|75,0|75,0|26,0|26,0|26,0|69,5|15,0|15,0|52,75|52,75|43,9|43,9|34,0|34,0|69,31|69,31|7,0|51,0|20,12|26,0|26,0|31,69|31,69|30,32|34,0|48,0|33,0|38,0|33,41|33,41|35,30|35,30|43,0|68,0|12,0|9,0|49,0|52,0|22,0|33,0|22,0|11,75|7,0|50,0|11,0|10,0|18,0|36,0|33,75|33,75|33,4|33,4|69,46|17,47|46,0|46,0|46,0|61,0|61,0|39,0|46,0|28,0|65,0|65,0|65,0|66,0|66,0|66,0|67,0|67,0|67,0|50,51|50,51|15,51|15,51|68,48|68,48|68,15|68,15|39,0|10,35|10,35|9,0|56,0|56,0|55,32|55,32|28,48|28,48|9,0|9,0|9,0|34,0|47,37|47,37|5,69|11,6|34,0|34,0|34,0|50,53|34,0|34,0|3,14|6,11|6,11|28,0|28,0|15,0|12,20|26,0|26,0|23,0|39,48|5,0|5,0|32,50|52,8|69,5|22,50|22,22|38,33|68,0|5,0|68,62|39,51|53,0|62,0|40,49|40,49|12,0|12,0|55,30|55,0|21,0|72,55|33,11|51,5|48,18|48,18|33,0|53,0|5,0|36,0|22,0|20,0|62,0|22,0|12,0|9,0|49,0|47,0|30,32|46,0|46,0|46,0|62,0|61,0|45,0|46,0|46,0|30,0|65,0|65,0|65,0|66,0|66,0|66,0|67,0|67,0|67,0|50,0|22,0|53,0|53,0|19,0|61,0|68,0|61,0|19,0|33,44|33,44|33,44|34,48|34,48|34,48|62,0|62,0|51,0|51,0|28,36|28,36|28,36|33,0|22,0|27,0|27,0|54,0|72,0|54,0|14,0|3,0|25,0|43,0|43,0|43,0|47,62|47,62|47,37|5,42|56,0|56,0|51,0|52,22|5,69|5,69|5,69|74,0|74,0|9,31|9,31|57,0|58,0|35,68|12,0|30,38|64,60|64,60|24,0|24,0|41,12|41,12|12,0|40,0|73,0|47,20|47,20|20,0|52,71|26,26|26,26|8,0|8,0|30,0|30,0|17,0|61,0|26,0|26,0|12,0|12,0|52,75|52,75|26,0|26,0|21,0|21,0|4,0|4,0|33,0|63,0|59,0|16,0|15,0|15,0|26,0|46,0|34,0|26,0|46,0|23,0|39,0|39,0|47,0|47,0|47,0|75,0|33,0|33,0|33,69|33,0|69,0|69,0|22,0|29,0|29,0|29,0|29,0|29,0|29,0|26,0|26,0|2,0|70,0|77,0|32,0|46,0".split("|").map { row ->
    row.split(",").map { it.toInt() }.toIntArray()
}

internal fun canonicalGen3AbilityName(ability:Int):String? =
    gen3AbilityNames.getOrNull(ability)?.takeUnless { ability==0 }
internal fun canonicalGen3SpeciesAbilities(species:Int):IntArray? =
    gen3SpeciesAbilityRows.getOrNull(species-1)?.copyOf()
internal fun canonicalGen3AbilityId(species:Int,slot:Int):Int? {
    if(slot !in 1..2)return null
    val abilities=canonicalGen3SpeciesAbilities(species)?:return null
    return abilities.getOrNull(slot-1)?.takeIf{it in 1..77}
        ?:abilities.firstOrNull{it in 1..77}
}

internal fun showPseGen3BallLegaliseDialog(activity:Activity,targetGeneration:Int,onConfirm:()->Unit){
    AlertDialog.Builder(activity)
        .setTitle("Gen 3 → Gen $targetGeneration")
        .setMessage("This PK3 stores ball ID 0, which has no native Generation $targetGeneration ball mapping. PSE can map it to Poké Ball ID 4 only after your explicit choice. This changes destination metadata and does not claim official migration, legality certification or verified provenance. Cancel leaves the Bank record and destination unchanged.")
        .setNegativeButton("Cancel",null)
        .setPositiveButton("Poké Ball"){_,_->onConfirm()}
        .show()
}

internal fun pseGen3BallReviewLine(sourceBall:Int,destinationBall:Int,targetGeneration:Int):String=
    if(sourceBall==destinationBall)
        "Ball: exact Generation 3 ball ID $sourceBall is preserved in Generation $targetGeneration.\n"
    else
        "Ball Legalise: source PK3 ball ID $sourceBall → Poké Ball ID $destinationBall. This is the explicit destination-metadata change reviewed above; the source PK3 remains unchanged.\n"
