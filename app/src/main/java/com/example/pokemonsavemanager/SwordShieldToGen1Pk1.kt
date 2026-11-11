package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets

data class SwordShieldToGen1Review(
    val species:Int,
    val nickname:String,
    val originalTrainer:String,
    val tid:Int,
    val sid:Int,
    val pid:Long,
    val encryptionConstant:Long,
    val experience:Long,
    val nature:Int,
    val ability:Int,
    val gender:Int,
    val moves:List<Int>,
    val pp:List<Int>,
    val ppUps:List<Int>,
    val ivs:List<Int>,
    val evs:List<Int>,
    val friendship:Int,
    val pokerus:Int,
    val shiny:Boolean,
    val cachedLevel:Int,
    val originGame:Int,
    val ball:Int,
    val metLevel:Int,
    val dynamaxLevel:Int,
    val canGigantamax:Boolean,
    val relearnMoves:List<Int>,
    val effects:List<String>
)

/**
 * Reviews one checksum-valid native Sword/Shield PK8 for the first bounded
 * backward destination. It does not write a save and it never mutates Bank.
 */
object SwordShieldToGen1Pk1 {
    private const val RECORD_SIZE=0x158
    private const val STORED_END=0x148
    private val orders=listOf(
        "0123","0132","0213","0312","0231","0321","1023","1032",
        "2013","3012","2031","3021","1203","1302","2103","3102",
        "2301","3201","1230","1320","2130","3120","2310","3210"
    )

    fun review(record:ByteArray):SwordShieldToGen1Review{
        val source=(SwordShieldPokemonReadOnly.read(record) as? SwordShieldSlotRead.Pokemon)?.value
            ?:error("A complete checksum-valid 344-byte native Sword/Shield PK8 is required.")
        val plain=plainRecord(record)
        val nickname=text(plain,0x58,"nickname")
        val originalTrainer=text(plain,0xF8,"Original Trainer name")
        val language=byte(plain,0xE2)
        val currentHandler=byte(plain,0xC4)
        require(source.species in 1..151){
            "Species #${source.species} did not exist in Generation 1."
        }
        require(source.form==0){
            "Sword/Shield form ${source.form} has no reviewed native Generation 1 representation."
        }
        require(!source.isEgg){"Egg PK8 records remain blocked from this backward conversion."}
        require(source.heldItem==0){
            "Held item #${source.heldItem} has no reviewed PK8 -> PK1 mapping. Remove it in Sword/Shield first."
        }
        require(!source.fatefulEncounter){
            "Fateful-event provenance cannot be represented in Generation 1."
        }
        require(language==2){
            "This bounded route supports English PK8 text only; other glyph mappings remain blocked."
        }
        require(nickname.isNotBlank()&&nickname.length<=10){
            "The PK8 nickname must contain 1-10 Generation 1-compatible characters."
        }
        require(originalTrainer.isNotBlank()&&originalTrainer.length<=10){
            "The Original Trainer name must contain 1-10 Generation 1-compatible characters."
        }
        require(source.moves.size==4&&source.moves.all{it.id in 0..165}){
            "At least one current move did not exist in Generation 1."
        }
        source.moves.forEachIndexed{i,move->
            if(move.id==0)require(move.pp==0&&move.ppUps==0){
                "Empty move slot ${i+1} contains PP or PP-Up data."
            }else{
                require(move.pp in 0..63){"Move #${move.id} has PP outside the native six-bit range."}
                require(move.ppUps in 0..3){"Move #${move.id} has an invalid PP-Up count."}
            }
        }
        require(source.evs.size==6&&source.evs.all{it==0}){
            "Nonzero Sword/Shield EVs have no lossless native Generation 1 Stat Exp mapping. Clear all six EVs before using this bounded route."
        }
        require(source.ivs.size==6&&source.ivs.all{it in 0..31}){
            "The PK8 IV data is outside its native range."
        }
        require(source.experience in 0..0xFFFFFFL){
            "Experience is outside the Generation 1 three-byte range."
        }
        require(source.hyperTrainingFlags==0){
            "Hyper Training cannot be represented in Generation 1. Clear every Hyper Training flag first."
        }
        require(currentHandler in 0..1){
            "The PK8 current-handler field is outside the reviewed range."
        }
        val friendship=byte(plain,if(currentHandler==0)0x112 else 0xC8)
        val pokerus=byte(plain,0x32)
        val origin=byte(plain,0xDE)
        val ball=byte(plain,0x124)
        val metLevel=byte(plain,0x125) and 0x7F
        val moves=source.moves.map{it.id}
        val pp=source.moves.map{it.pp}
        val ppUps=source.moves.map{it.ppUps}
        val ribbonBytes=plain.copyOfRange(0x34,0x48)
        val tracker=plain.copyOfRange(0xE9,0xF1)
        val technicalRecords=plain.copyOfRange(0x127,0x148)
        val omittedMetadata=listOf(
            "markings 0x18-0x19="+hex(plain,0x18,0x1A),
            "contest 0x2C-0x31="+hex(plain,0x2C,0x32),
            "ribbons/marks 0x34-0x47="+hex(plain,0x34,0x48),
            "size 0x48-0x4A="+hex(plain,0x48,0x4B),
            "memories 0x90-0xA7="+hex(plain,0x90,0xA8),
            "handler metadata 0xC2-0xDD="+hex(plain,0xC2,0xDE),
            "language/geo 0xDF-0xE8="+hex(plain,0xDF,0xE9),
            "HOME tracker 0xE9-0xF0="+hex(plain,0xE9,0xF1),
            "dates/locations/Ball 0x113-0x126="+hex(plain,0x113,0x127),
            "Technical Records 0x127-0x147="+hex(plain,0x127,0x148),
            "party cache 0x148-0x157="+hex(plain,0x148,0x158)
        ).joinToString(", ")
        val effects=listOf(
            "Explicit Legalise: backward PSE Sword/Shield PK8 → native Generation 1 PK1. This is not an official trade, Time Capsule transfer, migration, Pokémon Bank or HOME transfer, encounter record, legality certification, or verified provenance.",
            "The route accepts one selected checksum-valid content-addressed 344-byte PK8. Species #${source.species}, exact default form, nickname, OT name, TID, EXP, current moves, PP, PP Ups and zero EV state enter the reviewed native bridge.",
            "All six Sword/Shield EVs are required to be zero, so PK1 Stat Exp is explicitly zero. Nonzero EVs are blocked instead of silently discarded or guessed.",
            "IVs ${source.ivs.joinToString("/")} are reviewed for the disclosed IV-to-DV mapping. PID 0x${source.pid.toString(16).uppercase().padStart(8,'0')}, encryption constant 0x${source.encryptionConstant.toString(16).uppercase().padStart(8,'0')}, SID ${source.sid}, nature ${source.nature}, ability ${source.ability}, explicit gender ${source.gender}, language ID $language, origin game $origin, Ball $ball and met level $metLevel have no PK1 fields and are omitted only by this Legalise choice.",
            "Active friendship $friendship and Pokérus 0x${pokerus.toString(16).uppercase().padStart(2,'0')} enter the reviewed bridge only so their unavoidable Generation 1 omission is named explicitly before confirmation.",
            "Sword/Shield cached level ${source.cachedLevel}, current HP ${source.currentHp}/${source.maximumHp}, cached stats ${source.cachedStats.joinToString("/")}, Dynamax Level ${source.dynamaxLevel}, Gigantamax flag ${source.canGigantamax}, and relearn moves ${source.relearnMoves.joinToString("/")} are omitted. PK1 boxed stats are rebuilt from retained EXP and mapped DVs.",
            if(ribbonBytes.all{it==0.toByte()})
                "No Sword/Shield ribbon or mark bits require omission."
            else "Sword/Shield ribbon and mark bytes ${hex(plain,0x34,0x48)} are omitted by the explicit Legalise choice.",
            if(tracker.all{it==0.toByte()})
                "No source HOME tracker bytes require omission."
            else "Source HOME tracker bytes ${hex(plain,0xE9,0xF1)} are omitted; PSE does not invent an official HOME identity.",
            if(technicalRecords.all{it==0.toByte()})
                "No Sword/Shield Technical Record flags require omission."
            else "${technicalRecords.count{it!=0.toByte()}} nonzero Sword/Shield Technical Record bytes are omitted; current move IDs are validated separately.",
            "Every remaining title-specific byte range is disclosed before confirmation: $omittedMetadata.",
            "The source Sword/Shield save and native PK8 Bank entry are never consumed. Destination writing is delegated only after this exact review is accepted."
        )
        return SwordShieldToGen1Review(
            source.species,nickname,originalTrainer,source.tid,source.sid,
            source.pid,source.encryptionConstant,source.experience,source.nature,
            source.ability,source.gender,moves,pp,ppUps,source.ivs,source.evs,
            friendship,pokerus,source.shiny,source.cachedLevel,origin,ball,
            metLevel,source.dynamaxLevel,source.canGigantamax,
            source.relearnMoves,effects
        )
    }

    private fun plainRecord(record:ByteArray):ByteArray{
        require(record.size==RECORD_SIZE){
            "A complete 344-byte native Sword/Shield PK8 is required."
        }
        val ec=u32(record,0)
        val decoded=record.copyOf()
        xor(decoded,8,STORED_END,ec);xor(decoded,STORED_END,RECORD_SIZE,ec)
        val order=orders[((ec ushr 13).toInt() and 31)%24]
        val plain=decoded.copyOf()
        for(block in 0..3){
            val from=8+(order[block]-'0')*0x50
            decoded.copyInto(plain,8+block*0x50,from,from+0x50)
        }
        var sum=0
        for(offset in 8 until STORED_END step 2)sum+=u16(plain,offset)
        require(u16(record,4)==0&&(sum and 0xFFFF)==u16(record,6)){
            "The native PK8 checksum failed."
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
