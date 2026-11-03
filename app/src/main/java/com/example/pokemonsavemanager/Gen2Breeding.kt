package com.example.pokemonsavemanager

/** Gen 2 engine semantics from pret/pokegold and pret/pokecrystal:
 * engine/events/daycare.asm and engine/pokemon/breeding.asm.
 * Pure logic; callers provide version-specific verified data and randomness.
 */
object Gen2Breeding {
    data class Species(val pre:Int,val ratio:Int,val cycles:Int,val groups:Set<String>,
        val learnset:List<Pair<Int,Int>>,val eggMoves:Set<Int>,val machines:Set<Int>)
    data class Parent(val species:Int,val dvs:IntArray,val moves:IntArray)
    data class Egg(val species:Int,val moves:IntArray,val dvs:IntArray,val cycles:Int,val motherSlot:Int,val steps:Int)

    fun gender(parent:Parent,data:Map<Int,Species>):Int? {
        val ratio=requireNotNull(data[parent.species]).ratio
        return when(ratio){255->null;0->1;254->0;else->if(((parent.dvs[1] shl 4) or parent.dvs[3])>ratio)1 else 0}
    }

    fun compatible(first:Parent,second:Parent,data:Map<Int,Species>):Boolean {
        require(first.dvs.size==5&&second.dvs.size==5&&first.dvs.all{it in 0..15}&&second.dvs.all{it in 0..15})
        val a=requireNotNull(data[first.species]);val b=requireNotNull(data[second.species])
        if("EGG_NONE" in a.groups||"EGG_NONE" in b.groups)return false
        if(first.species==132&&second.species==132)return false
        if(first.species!=132&&second.species!=132){
            val ag=gender(first,data);val bg=gender(second,data)
            if(ag==null||bg==null||ag==bg||a.groups.intersect(b.groups).isEmpty())return false
        }
        return first.dvs[2]!=second.dvs[2]||(first.dvs[4] and 7)!=(second.dvs[4] and 7)
    }

    fun initialise(first:Parent,second:Parent,data:Map<Int,Species>,random:(Int)->Int):Egg {
        require(compatible(first,second,data)){"This pair cannot breed under Generation 2 rules."}
        fun draw(bound:Int):Int=random(bound).also{require(it in 0 until bound)}
        val mother=when{first.species==132->1;second.species==132->0;gender(first,data)==0->0;else->1}
        val parents=listOf(first,second)
        var species=parents[mother].species
        repeat(2){species=requireNotNull(data[species]).pre}
        if(species==29&&draw(256)>=129)species=32
        val child=requireNotNull(data[species])
        val moves=mutableListOf<Int>()
        fun add(move:Int){if(move==0||move in moves)return;if(moves.size==4)moves.removeAt(0);moves.add(move)}
        child.learnset.filter{it.first<=5}.forEach{add(it.second)}
        // Ditto supplies the father's move list when the other parent is female.
        val father=when{
            first.species==132->if(gender(second,data)==0)0 else 1
            second.species==132->if(gender(first,data)==0)1 else 0
            else->1-mother
        }
        val otherMoves=if(first.species==132)first.moves else if(second.species==132)second.moves else parents[mother].moves
        for(move in parents[father].moves){
            if(move==0)break
            if(move in child.eggMoves||(move in otherMoves&&child.learnset.any{it.second==move})||move in child.machines)add(move)
        }
        val a=draw(256);val b=draw(256)
        val attack=a ushr 4;var defense=a and 15;val speed=b ushr 4;var special=b and 15
        val provisional=Parent(species,intArrayOf(0,attack,defense,speed,special),intArrayOf())
        val donor=when{first.species==132->first;second.species==132->second;
            gender(provisional,data)==null->null;gender(provisional,data)==1->parents[mother];else->parents[1-mother]}
        if(donor!=null){defense=donor.dvs[2];special=(special and 8) or (donor.dvs[4] and 7)}
        val hp=((attack and 1) shl 3) or ((defense and 1) shl 2) or ((speed and 1) shl 1) or (special and 1)
        require(moves.isNotEmpty()&&moves.all{it in 1..251}&&child.cycles in 1..255)
        return Egg(species,IntArray(4){moves.getOrElse(it){0}},intArrayOf(hp,attack,defense,speed,special),child.cycles,mother,150+draw(106))
    }
}
