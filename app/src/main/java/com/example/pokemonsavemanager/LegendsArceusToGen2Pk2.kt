package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets

data class LegendsArceusToGen2Review(
    val species:Int,
    val nickname:String,
    val originalTrainer:String,
    val tid:Int,
    val sid:Int,
    val pid:Long,
    val encryptionConstant:Long,
    val experience:Long,
    val nature:Int,
    val statNature:Int,
    val ability:Int,
    val abilitySlot:Int,
    val gender:Int,
    val moves:List<Int>,
    val pp:List<Int>,
    val ppUps:List<Int>,
    val ivs:List<Int>,
    val evs:List<Int>,
    val friendship:Int,
    val pokerus:Int,
    val shiny:Boolean,
    val form:Int,
    val isAlpha:Boolean,
    val ganbaruValues:List<Int>,
    val cachedLevel:Int?,
    val cachedStats:List<Int>?,
    val originGame:Int,
    val ball:Int,
    val metLevel:Int,
    val relearnMoves:List<Int>,
    val effects:List<String>
)

/** Reviews one native Legends: Arceus PA8 for a disclosed backward PK2 conversion. */
object LegendsArceusToGen2Pk2 {
    private const val STORED_SIZE=0x168
    private const val PARTY_SIZE=0x178
    private val orders=listOf(
        "0123","0132","0213","0312","0231","0321","1023","1032",
        "2013","3012","2031","3021","1203","1302","2103","3102",
        "2301","3201","1230","1320","2130","3120","2310","3210"
    )

    fun review(record:ByteArray):LegendsArceusToGen2Review{
        val source=LegendsArceusPseBankRestoreEditing.inspectBankRecord(record)
        val plain=plainRecord(record)
        val nickname=text(plain,0x60,"nickname")
        val originalTrainer=text(plain,0x110,"Original Trainer name")
        val language=byte(plain,0xF2)
        val currentHandler=byte(plain,0xD4)
        val ability=u16(plain,0x14)
        val abilitySlot=byte(plain,0x16) and 7
        val evs=(0..5).map{byte(plain,0x26+it)}
        require(source.species in 1..251){
            "Species #${source.species} did not exist in Generation 2."
        }
        require(source.form==0){
            "Legends: Arceus form ${source.form} has no reviewed native Generation 2 representation."
        }
        require(!source.isEgg){"Egg PA8 records remain blocked from this backward conversion."}
        require(!source.isNoble){"Noble PA8 records have no native Generation 2 representation."}
        require(!source.fatefulEncounter){
            "Fateful-event provenance cannot be represented in Generation 2."
        }
        require(ability in 1..267&&abilitySlot in setOf(1,2,4)){
            "The PA8 ability ID or normal/hidden slot is outside the reviewed native range."
        }
        require(language==2){
            "This bounded route supports English PA8 text only; other glyph mappings remain blocked."
        }
        require(nickname.isNotBlank()&&nickname.length<=10){
            "The PA8 nickname must contain 1-10 Generation 2-compatible characters."
        }
        require(originalTrainer.isNotBlank()&&originalTrainer.length<=10){
            "The Original Trainer name must contain 1-10 Generation 2-compatible characters."
        }
        require(source.moves.size==4&&source.moves.all{it.id in 0..251}){
            "At least one current move did not exist in Generation 2."
        }
        source.moves.forEachIndexed{i,move->
            if(move.id==0)require(move.pp==0){
                "Empty move slot ${i+1} contains current PP."
            }else require(move.pp in 0..63){
                "Move #${move.id} has PP outside the native six-bit range."
            }
        }
        require(evs.all{it==0}){
            "Nonzero stored PA8 EV bytes have no lossless native Generation 2 Stat Exp mapping. Clear all six EVs first."
        }
        require(source.ivs.size==6&&source.ivs.all{it in 0..31}){
            "The PA8 IV data is outside its native range."
        }
        require(source.experience in 0..0xFFFFFFL){
            "Experience is outside the Generation 2 three-byte range."
        }
        require(source.hyperTrainingFlags==0){
            "Hyper Training cannot be represented in Generation 2. Clear every Hyper Training flag first."
        }
        require(currentHandler in 0..1){
            "The PA8 current-handler field is outside the reviewed range."
        }
        require(source.gender in 0..2){
            "The PA8 gender field is outside the reviewed range."
        }
        val friendship=if(currentHandler==0)
            source.originalTrainerFriendship else source.handlingTrainerFriendship
        val pokerus=byte(plain,0x32)
        val origin=byte(plain,0xEE)
        val ball=byte(plain,0x137)
        val metLevel=byte(plain,0x13D) and 0x7F
        val moves=source.moves.map{it.id}
        val pp=source.moves.map{it.pp}
        val ppUps=List(4){0}
        val markings=u16(plain,0x18)
        val contest=plain.copyOfRange(0x2C,0x32)
        val ribbons=plain.copyOfRange(0x34,0x54)
        val trackerAndRecords=plain.copyOfRange(0x140,STORED_SIZE)
        val partyTail=if(record.size==PARTY_SIZE)plain.copyOfRange(STORED_SIZE,PARTY_SIZE)
            else ByteArray(0)
        val omittedMetadata=listOf(
            "ability/Alpha/Noble flags 0x14-0x17="+hex(plain,0x14,0x18),
            "markings 0x18-0x1B="+hex(plain,0x18,0x1C),
            "nature/gender/form flags 0x20-0x25="+hex(plain,0x20,0x26),
            "contest bytes 0x2C-0x31="+hex(plain,0x2C,0x32)+
                "; Pokérus 0x32 is retained",
            "ribbons/marks and move metadata 0x34-0x53="+hex(plain,0x34,0x54),
            "memory and relearn metadata 0x7A-0x91="+hex(plain,0x7A,0x92),
            "size/Ganbaru/cache metadata 0x92-0xB7="+hex(plain,0x92,0xB8),
            "handler metadata 0xB8-0xED="+hex(plain,0xB8,0xEE),
            "origin/language/geo metadata 0xEE-0x10F="+hex(plain,0xEE,0x110),
            "dates/locations/Ball/battle metadata 0x12A-0x13F="+hex(plain,0x12A,0x140),
            "HOME tracker/move records/title data 0x140-0x167="+hex(plain,0x140,STORED_SIZE),
            if(partyTail.isEmpty())"no PA8 party cache tail" else
                "party cache 0x168-0x177="+hex(plain,STORED_SIZE,PARTY_SIZE)
        ).joinToString(", ")
        val effects=listOf(
            "Explicit Legalise: backward PSE Legends: Arceus PA8 → native Generation 2 PK2. This is not an official trade, Time Capsule transfer, migration, Pokémon Bank or HOME transfer, encounter record, legality certification, or verified provenance.",
            "The route accepts one selected checksum-valid content-addressed ${record.size}-byte PA8. Species #${source.species}, exact default form, nickname, OT name, TID, EXP, current moves and PP enter the reviewed native bridge. PA8 has no PP-Up counts, so all four destination PP-Up values are explicitly zero.",
            "All six stored PA8 EV bytes are required to be zero, so PK2 Stat Exp is explicitly zero. Nonzero EV bytes are blocked instead of silently discarded or guessed.",
            "IVs ${source.ivs.joinToString("/")} are reviewed for the disclosed IV-to-DV mapping. PID 0x${source.pid.toString(16).uppercase().padStart(8,'0')}, encryption constant 0x${source.encryptionConstant.toString(16).uppercase().padStart(8,'0')}, SID ${source.sid}, nature ${source.nature}, stat nature ${source.statNature}, ability $ability slot $abilitySlot, explicit gender ${source.gender}, language ID $language, origin game $origin, Ball $ball and met level $metLevel have no PK2 fields and are omitted only by this Legalise choice.",
            "Active friendship $friendship and Pokérus 0x${pokerus.toString(16).uppercase().padStart(2,'0')} are retained in native Generation 2 fields. PK2 caught/met data is explicit zero.",
            "Ganbaru Values ${source.ganbaruValues.joinToString("/")}, Alpha status ${source.isAlpha}, current HP ${source.currentHp}, cached level ${source.cachedLevel?:"not stored"}, cached stats ${source.cachedStats?.joinToString("/")?:"not stored"}, relearn moves ${source.relearnMoves.joinToString("/")} and markings 0x${markings.toString(16).uppercase().padStart(4,'0')} are omitted. PK2 boxed stats are rebuilt from retained EXP and mapped DVs.",
            if(contest.all{it==0.toByte()})"No stored PA8 contest bytes require omission." else
                "Stored PA8 contest bytes ${hex(plain,0x2C,0x32)} are omitted by the explicit Legalise choice.",
            if(ribbons.all{it==0.toByte()})"No PA8 ribbon, mark or adjacent move-metadata bytes require omission." else
                "PA8 ribbon, mark and adjacent move-metadata bytes ${hex(plain,0x34,0x54)} are omitted by the explicit Legalise choice.",
            if(trackerAndRecords.all{it==0.toByte()})"No PA8 HOME tracker, move-record or trailing title bytes require omission." else
                "PA8 HOME tracker, move-record and trailing title bytes ${hex(plain,0x140,STORED_SIZE)} are omitted; PSE does not invent an official HOME identity.",
            "Every remaining title-specific byte range is disclosed before confirmation: $omittedMetadata.",
            "The source Legends: Arceus save and native PA8 Bank entry are never consumed. Destination writing is delegated only after this exact review is accepted."
        )
        return LegendsArceusToGen2Review(
            source.species,nickname,originalTrainer,source.tid,source.sid,
            source.pid,source.encryptionConstant,source.experience,source.nature,
            source.statNature,ability,abilitySlot,source.gender,moves,pp,ppUps,
            source.ivs,evs,friendship,pokerus,source.shiny,source.form,
            source.isAlpha,source.ganbaruValues,source.cachedLevel,source.cachedStats,
            origin,ball,metLevel,source.relearnMoves,effects
        )
    }

    private fun plainRecord(record:ByteArray):ByteArray{
        require(record.size==STORED_SIZE||record.size==PARTY_SIZE){
            "A complete 360-byte stored or 376-byte party PA8 is required."
        }
        val ec=u32(record,0)
        val decoded=record.copyOf()
        xor(decoded,8,STORED_SIZE,ec)
        if(record.size==PARTY_SIZE)xor(decoded,STORED_SIZE,PARTY_SIZE,ec)
        val order=orders[((ec ushr 13).toInt() and 31)%24]
        val plain=decoded.copyOf()
        for(block in 0..3){
            val from=8+(order[block]-'0')*0x58
            decoded.copyInto(plain,8+block*0x58,from,from+0x58)
        }
        var sum=0
        for(offset in 8 until STORED_SIZE step 2)sum+=u16(plain,offset)
        require(u16(record,4)==0&&(sum and 0xFFFF)==u16(record,6)){
            "The native PA8 checksum failed."
        }
        return plain
    }

    private fun text(bytes:ByteArray,offset:Int,label:String):String{
        var end=offset
        while(end<offset+26&&u16(bytes,end)!=0)end+=2
        val value=String(bytes,offset,end-offset,StandardCharsets.UTF_16LE)
        require(value.all{
            it.code !in 0xD800..0xDFFF&&it.code>=0x20&&
                it.code !in 0xE000..0xF8FF
        }){"The $label contains an unmapped glyph."}
        return value
    }

    private fun xor(bytes:ByteArray,from:Int,end:Int,seed:Long){
        var state=seed
        for(offset in from until end step 2){
            state=(state*0x41C64E6DL+0x6073L) and 0xFFFFFFFFL
            w16(bytes,offset,u16(bytes,offset) xor (state ushr 16).toInt())
        }
    }
    private fun hex(bytes:ByteArray,from:Int,end:Int)=
        bytes.copyOfRange(from,end).joinToString(""){"%02X".format(it)}
    private fun byte(bytes:ByteArray,offset:Int)=bytes[offset].toInt() and 255
    private fun u16(bytes:ByteArray,offset:Int)=
        byte(bytes,offset) or (byte(bytes,offset+1) shl 8)
    private fun u32(bytes:ByteArray,offset:Int)=
        u16(bytes,offset).toLong() or (u16(bytes,offset+2).toLong() shl 16)
    private fun w16(bytes:ByteArray,offset:Int,value:Int){
        bytes[offset]=value.toByte();bytes[offset+1]=(value ushr 8).toByte()
    }
}
