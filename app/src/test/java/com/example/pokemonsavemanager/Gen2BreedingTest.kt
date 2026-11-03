package com.example.pokemonsavemanager

import org.junit.Assert.*
import org.junit.Test

class Gen2BreedingTest {
    private val data=mapOf(
        29 to Gen2Breeding.Species(29,254,20,setOf("EGG_MONSTER","EGG_GROUND"),listOf(1 to 45,1 to 33,8 to 10,12 to 24,17 to 40,23 to 39,30 to 44,38 to 154),setOf(48,50,36,116,204,68,251),setOf(29,174,92,237,241,173,59,182,240,203,218,231,87,216,189,104,207,214,111,197,156,213,168)),
        32 to Gen2Breeding.Species(32,0,20,setOf("EGG_MONSTER","EGG_GROUND"),listOf(1 to 43,1 to 33,8 to 30,12 to 24,17 to 40,23 to 116,30 to 31,38 to 32),setOf(48,50,36,93,133,68,251),setOf(29,174,92,237,241,173,59,182,240,203,218,231,87,216,189,104,207,214,111,197,156,213,168)),
        132 to Gen2Breeding.Species(132,255,20,setOf("EGG_DITTO","EGG_DITTO"),listOf(1 to 144),setOf(),setOf()),
        158 to Gen2Breeding.Species(158,31,20,setOf("EGG_MONSTER","EGG_WATER_1"),listOf(1 to 10,1 to 43,7 to 99,13 to 55,20 to 44,27 to 184,35 to 163,43 to 103,52 to 56),setOf(242,37,56,246,13,157),setOf(223,29,174,92,237,173,59,196,182,240,203,218,231,216,91,189,104,8,207,214,197,156,213,15,57,250)),
        159 to Gen2Breeding.Species(158,31,20,setOf("EGG_MONSTER","EGG_WATER_1"),listOf(1 to 10,1 to 43,1 to 99,7 to 99,13 to 55,21 to 44,28 to 184,37 to 163,45 to 103,55 to 56),setOf(),setOf(223,29,174,46,92,249,237,173,59,196,182,240,203,218,231,216,91,189,104,8,207,214,197,156,213,210,15,57,70,250)),
        160 to Gen2Breeding.Species(159,31,20,setOf("EGG_MONSTER","EGG_WATER_1"),listOf(1 to 10,1 to 43,1 to 99,1 to 55,7 to 99,13 to 55,21 to 44,28 to 184,38 to 163,47 to 103,58 to 56),setOf(),setOf(223,29,174,46,92,249,237,173,59,63,196,182,240,203,218,231,89,216,91,189,104,8,207,214,197,156,213,210,15,57,70,250)),
        177 to Gen2Breeding.Species(177,127,20,setOf("EGG_FLYING","EGG_FLYING"),listOf(1 to 64,1 to 43,10 to 101,20 to 100,30 to 248,40 to 109,50 to 94),setOf(114,65,98,185,211),setOf(174,92,244,237,241,173,182,202,203,218,76,216,94,104,207,214,129,138,197,156,213,168,171,148)),
        178 to Gen2Breeding.Species(177,127,20,setOf("EGG_FLYING","EGG_FLYING"),listOf(1 to 64,1 to 43,1 to 101,10 to 101,20 to 100,35 to 248,50 to 109,65 to 94),setOf(),setOf(174,92,244,237,241,173,63,182,202,203,218,76,216,94,104,207,214,129,138,197,156,213,168,171,19,148)),
        179 to Gen2Breeding.Species(179,127,20,setOf("EGG_MONSTER","EGG_GROUND"),listOf(1 to 33,1 to 45,9 to 84,16 to 86,23 to 178,30 to 113,37 to 87),setOf(85,36,34,219,103,115),setOf(29,174,92,192,237,173,182,240,203,218,231,87,216,104,207,214,129,111,156,213,148)),
        180 to Gen2Breeding.Species(179,127,20,setOf("EGG_MONSTER","EGG_GROUND"),listOf(1 to 33,1 to 45,1 to 84,9 to 84,18 to 86,27 to 178,36 to 113,45 to 87),setOf(),setOf(223,29,174,92,192,249,237,173,182,240,203,218,231,87,216,104,207,214,129,111,9,156,213,7,70,148)),
        181 to Gen2Breeding.Species(180,127,20,setOf("EGG_MONSTER","EGG_GROUND"),listOf(1 to 33,1 to 45,1 to 84,1 to 86,9 to 84,18 to 86,27 to 178,30 to 9,42 to 113,57 to 87),setOf(),setOf(223,29,174,92,192,249,237,173,63,182,240,203,218,231,87,216,104,207,214,129,111,9,156,213,7,70,148)),
        216 to Gen2Breeding.Species(216,127,20,setOf("EGG_GROUND","EGG_GROUND"),listOf(1 to 10,1 to 43,8 to 122,15 to 154,22 to 185,29 to 156,36 to 163,43 to 173,50 to 37),setOf(242,36,69,116,68),setOf(223,29,174,205,46,92,192,249,237,241,173,182,203,218,89,216,91,189,104,8,207,214,129,111,9,156,213,168,7,210,15,70)),
        217 to Gen2Breeding.Species(216,127,20,setOf("EGG_GROUND","EGG_GROUND"),listOf(1 to 10,1 to 43,1 to 122,1 to 154,8 to 122,15 to 154,22 to 185,29 to 156,39 to 163,49 to 173,59 to 37),setOf(),setOf(223,29,174,205,46,92,192,249,237,241,173,63,182,203,218,89,216,91,189,104,8,207,214,129,111,9,156,213,168,7,210,15,70)),
        201 to Gen2Breeding.Species(201,255,40,setOf("EGG_NONE","EGG_NONE"),listOf(1 to 237),setOf(),setOf()),
        251 to Gen2Breeding.Species(251,255,120,setOf("EGG_NONE","EGG_NONE"),listOf(1 to 73,1 to 93,1 to 105,1 to 215,10 to 219,20 to 246,30 to 248,40 to 226,50 to 195),setOf(),setOf(174,92,244,237,241,230,173,63,182,240,202,203,218,76,216,94,247,189,104,207,214,201,129,111,138,197,156,171,148))
    )
    private fun parent(species:Int,attack:Int=15,defense:Int=1,speed:Int=10,special:Int=3,moves:IntArray=intArrayOf(33,43,0,0)):Gen2Breeding.Parent {
        val hp=((attack and 1) shl 3) or ((defense and 1) shl 2) or ((speed and 1) shl 1) or (special and 1)
        return Gen2Breeding.Parent(species,intArrayOf(hp,attack,defense,speed,special),moves)
    }
    @Test fun dittoSupportsAllFourReportedPartySpeciesAndBothSlots(){
        val ditto=parent(132,2,14,6,5)
        for(species in listOf(160,178,181,217)){
            val other=parent(species)
            assertTrue(Gen2Breeding.compatible(ditto,other,data))
            assertTrue(Gen2Breeding.compatible(other,ditto,data))
            val expected=when(species){160->158;178->177;181->179;else->216}
            for(pair in listOf(ditto to other,other to ditto)){
                val egg=Gen2Breeding.initialise(pair.first,pair.second,data){0}
                assertEquals(expected,egg.species)
                assertEquals(14,egg.dvs[2])
                assertEquals(5,egg.dvs[4] and 7)
                assertTrue(egg.moves.any{it!=0})
                assertEquals(150,egg.steps)
                assertEquals(if(pair.first.species==132)1 else 0,egg.motherSlot)
            }
        }
    }
    @Test fun rejectsNoEggsDoubleDittoAndForbiddenDvPairs(){
        val ditto=parent(132,2,14,6,5)
        for(species in listOf(132,201,251))assertFalse(Gen2Breeding.compatible(ditto,parent(species),data))
        assertFalse(Gen2Breeding.compatible(ditto,parent(160,15,14,10,13),data))
        assertFalse(Gen2Breeding.compatible(parent(160),parent(158),data))
    }
    @Test fun compatibleFemaleAndMalePairUsesFemaleSpecies(){
        val male=parent(160,15,1,10,3)
        val female=parent(158,0,2,10,4)
        assertTrue(Gen2Breeding.compatible(male,female,data))
        val egg=Gen2Breeding.initialise(male,female,data){0}
        assertEquals(158,egg.species)
        assertEquals(1,egg.motherSlot)
    }
    @Test fun maleWithDittoPassesVerifiedEggMoves(){
        val male=parent(160,moves=intArrayOf(246,56,82,0))
        val egg=Gen2Breeding.initialise(male,parent(132,2,14,6,5),data){0}
        assertTrue(246 in egg.moves) // AncientPower is a Totodile Egg move.
        assertTrue(56 in egg.moves) // Hydro Pump is also a documented Totodile Egg move.
        assertTrue(82 !in egg.moves) // Dragon Rage is neither an Egg move nor a teachable machine.
    }
    @Test fun randomBoundariesAndParentArraysArePreserved(){
        val first=parent(132,2,14,6,5);val second=parent(160)
        val original=second.dvs.copyOf();val originalMoves=second.moves.copyOf()
        val egg=Gen2Breeding.initialise(first,second,data){it-1}
        assertEquals(255,egg.steps)
        assertEquals(13,egg.dvs[4])
        assertArrayEquals(original,second.dvs)
        assertArrayEquals(originalMoves,second.moves)
    }
}

