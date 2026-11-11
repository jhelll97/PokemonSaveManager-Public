package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets

data class ScarletVioletToGen3Review(
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
    val abilityNumber:Int,
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
    val originalTrainerGender:Int,
    val teraTypeOriginal:Int,
    val teraTypeOverride:Int,
    val scale:Int,
    val obedienceLevel:Int,
    val relearnMoves:List<Int>,
    val effects:List<String>
)

/**
 * Reviews one checksum-valid native Scarlet/Violet PK9 for a bounded backward
 * Generation 3 destination. It does not write a save and it never mutates Bank.
 */
object ScarletVioletToGen3Pk3 {
    private const val RECORD_SIZE=0x158
    private const val STORED_END=0x148
    private val orders=listOf(
        "0123","0132","0213","0312","0231","0321","1023","1032",
        "2013","3012","2031","3021","1203","1302","2103","3102",
        "2301","3201","1230","1320","2130","3120","2310","3210"
    )

    fun review(record:ByteArray):ScarletVioletToGen3Review{
        val source=(ScarletVioletPokemonReadOnly.read(record) as? ScarletVioletSlotRead.Pokemon)?.value
            ?:error("A complete checksum-valid 344-byte native Scarlet/Violet PK9 is required.")
        val plain=plainRecord(record)
        val nickname=text(plain,0x58,"nickname")
        val originalTrainer=text(plain,0xF8,"Original Trainer name")
        require(source.species in 1..386){
            "Species #${source.species} did not exist in Generation 3."
        }
        require(source.species !in setOf(201,265,266,267,268,269,327,386)){
            "Species #${source.species} has PID-dependent form, evolution, pattern, or title-specific form data that this bounded PK3 route will not guess."
        }
        require(source.form==0){
            "Scarlet/Violet form ${source.form} has no reviewed native Generation 3 representation."
        }
        require(source.nature in 0..24&&source.statNature in 0..24){
            "The PK9 nature data is outside the native range."
        }
        require(source.gender in 0..2){
            "The PK9 explicit gender field is outside the reviewed range."
        }
        require(source.ability in 1..310){
            "The PK9 ability ID is outside the reviewed Scarlet/Violet range."
        }
        require(source.abilityNumber in setOf(1,2,4)){
            "The PK9 ability slot is outside the reviewed normal or hidden-slot values."
        }
        require(!source.isEgg){"Egg PK9 records remain blocked from this backward conversion."}
        require(source.heldItem==0){
            "Held item #${source.heldItem} has no reviewed PK9 -> PK3 mapping. Remove it in Scarlet/Violet first."
        }
        require(!source.fatefulEncounter){
            "Fateful-event provenance cannot be represented in Generation 3."
        }
        require(source.language==2){
            "This bounded route supports English PK9 text only; other glyph mappings remain blocked."
        }
        require(nickname.isNotBlank()&&nickname.length<=10){
            "The PK9 nickname must contain 1-10 Generation 3-compatible characters."
        }
        require(originalTrainer.isNotBlank()&&originalTrainer.length<=7){
            "The Original Trainer name must contain 1-7 Generation 3-compatible characters."
        }
        require(source.moves.size==4&&source.moves.all{it.id in 0..354}){
            "At least one current move did not exist in Generation 3."
        }
        source.moves.forEachIndexed{i,move->
            if(move.id==0)require(move.pp==0&&move.ppUps==0){
                "Empty move slot ${i+1} contains PP or PP-Up data."
            }else{
                require(move.pp in 0..63){"Move #${move.id} has PP outside the native six-bit range."}
                require(move.ppUps in 0..3){"Move #${move.id} has an invalid PP-Up count."}
            }
        }
        require(source.evs.size==6&&source.evs.all{it in 0..255}&&source.evs.sum()<=510){
            "The Scarlet/Violet EVs are not destination-valid Generation 3 EV data."
        }
        require(source.ivs.size==6&&source.ivs.all{it in 0..31}){
            "The PK9 IV data is outside its native range."
        }
        require(source.experience in 0..0xFFFFFFFFL){
            "Experience is outside the Generation 3 four-byte range."
        }
        require(source.hyperTrainingFlags==0){
            "Hyper Training cannot be represented in Generation 3. Clear every Hyper Training flag first."
        }
        require(source.currentHandler in 0..1){
            "The PK9 current-handler field is outside the reviewed range."
        }
        require(source.metLevel in 1..100){
            "The PK9 met level is outside the native Generation 3 range 1-100."
        }
        require(source.originalTrainerGender in 0..1){
            "The PK9 Original Trainer gender is outside the native Generation 3 range."
        }
        val friendship=if(source.currentHandler==0)
            source.originalTrainerFriendship else source.handlingTrainerFriendship
        val pokerus=((source.pokerusStrain and 0xF) shl 4) or
            (source.pokerusDays and 0xF)
        val ribbonCount=source.ribbonFlags.count{it}
        val tmCount=source.technicalMachineFlags.count{it}
        val omittedMetadata=listOf(
            "marking value 0x${source.markingValue.toString(16).uppercase().padStart(4,'0')} and colors ${source.markingColors.joinToString("/")}",
            "contest ${source.contestStats.joinToString("/")}",
            "ribbon/mark flags $ribbonCount and affixed ribbon ${source.affixedRibbon}",
            "size scalars ${source.sizeScalars.joinToString("/")}",
            "language ${source.language}",
            "handler ${source.currentHandler} (${source.handlingTrainer.ifBlank{"blank"}}), gender ${source.handlingTrainerGender}, language ${source.handlingTrainerLanguage}",
            "OT/handler friendship ${source.originalTrainerFriendship}/${source.handlingTrainerFriendship}",
            "HOME tracker ${source.homeTrackerHigh.toString(16).uppercase().padStart(8,'0')}${source.homeTrackerLow.toString(16).uppercase().padStart(8,'0')}",
            "egg date/location ${source.eggYear}-${source.eggMonth}-${source.eggDay}/${source.eggLocation}",
            "met date/location ${source.metYear}-${source.metMonth}-${source.metDay}/${source.metLocation}",
            "TM flags $tmCount",
            "battle version ${source.battleVersion}",
            "form argument ${source.formArgument}",
            "status condition 0x${source.statusCondition.toString(16).uppercase().padStart(8,'0')}",
            "party cache 0x148-0x157=${hex(plain,0x148,0x158)}"
        ).joinToString(", ")
        val effects=listOf(
            "Explicit Legalise: backward PSE Scarlet/Violet PK9 → native Generation 3 PK3. This is not an official trade, migration, Pokémon Bank or HOME transfer, encounter record, legality certification, or verified provenance.",
            "The route accepts one selected checksum-valid content-addressed 344-byte PK9. Species #${source.species}, exact default form, nickname, OT name, TID/SID, EXP, current moves, PP, PP Ups, EVs, IVs, active friendship and Pokérus enter the reviewed native PK3 mapping.",
            "PID 0x${source.pid.toString(16).uppercase().padStart(8,'0')} is retained. Native Generation 3 derives nature, gender, normal ability slot and shiny state from that PID; the review names every resulting value and any change from the PK9 fields before confirmation.",
            "The user chooses a documented Generation 3 origin title. PK3 met location becomes explicit unspecified value 0, Ball becomes Poké Ball (ID 4), and met level ${source.metLevel} plus OT gender ${source.originalTrainerGender} are retained. Source origin game ${source.originGame} and Ball ${source.ball} are omitted only by the Legalise choice.",
            "PK9 ability #${source.ability} in slot ${source.abilityNumber}, nature ${source.nature}, stat nature ${source.statNature}, explicit gender ${source.gender}, and source shiny ${source.shiny} are reviewed against the destination PID-derived results; no official encounter provenance is inferred.",
            "Encryption constant 0x${source.encryptionConstant.toString(16).uppercase().padStart(8,'0')}, stat nature ${source.statNature}, Scarlet/Violet cached level ${source.cachedLevel}, current HP ${source.currentHp}/${source.maximumHp}, cached stats ${source.cachedStats.joinToString("/")}, Tera types ${source.teraTypeOriginal}/${source.teraTypeOverride}, scale ${source.scale}, obedience level ${source.obedienceLevel}, and relearn moves ${source.relearnMoves.joinToString("/")} are omitted. The Generation 3 game rebuilds party cache from the boxed PK3.",
            "The input must already have Egg, fateful-event and held-item fields cleared. English language 2 is retained. Active friendship $friendship is retained; current-handler identity and inactive friendship are omitted.",
            "Scarlet/Violet markings, contest values, ribbons and marks, size data, handling-trainer metadata, dates, locations, HOME tracker, Technical Machine flags and every remaining title-specific field are disclosed before confirmation: $omittedMetadata.",
            if(ribbonCount==0)
                "No Scarlet/Violet ribbon or mark bits require omission."
            else "$ribbonCount Scarlet/Violet ribbon or mark flags are omitted by the explicit Legalise choice.",
            if(source.homeTrackerLow==0L&&source.homeTrackerHigh==0L)
                "No source HOME tracker bytes require omission."
            else "The source HOME tracker is omitted; PSE does not invent an official HOME identity.",
            if(tmCount==0)
                "No Scarlet/Violet Technical Machine flags require omission."
            else "$tmCount Scarlet/Violet Technical Machine flags are omitted; current move IDs are validated separately.",
            "The source Scarlet/Violet save and native PK9 Bank entry are never consumed. Destination writing is delegated only after this exact review is accepted."
        )
        return ScarletVioletToGen3Review(
            source.species,nickname,originalTrainer,source.tid,source.sid,
            source.pid,source.encryptionConstant,source.experience,source.nature,
            source.statNature,source.ability,source.abilityNumber,source.gender,
            source.moves.map{it.id},source.moves.map{it.pp},source.moves.map{it.ppUps},
            source.ivs,source.evs,friendship,pokerus,source.shiny,source.cachedLevel,
            source.originGame,source.ball,source.metLevel,source.originalTrainerGender,
            source.teraTypeOriginal,source.teraTypeOverride,source.scale,
            source.obedienceLevel,source.relearnMoves,effects
        )
    }

    private fun plainRecord(record:ByteArray):ByteArray{
        require(record.size==RECORD_SIZE){
            "A complete 344-byte native Scarlet/Violet PK9 is required."
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
            "The native PK9 checksum failed."
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
