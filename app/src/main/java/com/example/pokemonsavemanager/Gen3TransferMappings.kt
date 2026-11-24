package com.example.pokemonsavemanager

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
