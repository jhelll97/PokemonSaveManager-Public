package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets

data class LegendsZaToGen3Review(
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
    val cachedLevel:Int,
    val form:Int,
    val isAlpha:Boolean,
    val originGame:Int,
    val ball:Int,
    val metLevel:Int,
    val originalTrainerGender:Int,
    val scale:Int,
    val obedienceLevel:Int,
    val relearnMoves:List<Int>,
    val cachedStats:List<Int>,
    val effects:List<String>
)

/** Reviews one checksum-valid native Legends: Z-A PA9 for a disclosed backward PK3 conversion. */
object LegendsZaToGen3Pk3 {
    private const val RECORD_SIZE=0x158
    private const val STORED_END=0x148
    private val orders=listOf(
        "0123","0132","0213","0312","0231","0321","1023","1032",
        "2013","3012","2031","3021","1203","1302","2103","3102",
        "2301","3201","1230","1320","2130","3120","2310","3210"
    )

    fun review(record:ByteArray):LegendsZaToGen3Review{
        val source=(LegendsZaPokemonReadOnly.read(record) as? LegendsZaSlotRead.Pokemon)?.value
            ?:error("A complete checksum-valid 344-byte native Legends: Z-A PA9 is required.")
        val plain=plainRecord(record)
        val nickname=text(plain,0x58,"nickname")
        val originalTrainer=text(plain,0xF8,"Original Trainer name")
        val abilitySlot=byte(plain,0x16) and 7
        val language=byte(plain,0xD5)
        val originGame=byte(plain,0xCE)
        val metLevel=byte(plain,0x125) and 0x7F
        val originalTrainerGender=(byte(plain,0x125) ushr 7) and 1
        val pokerus=byte(plain,0x32)
        require(source.species in 1..386){
            "Species #${source.species} did not exist in Generation 3."
        }
        require(source.species !in setOf(201,265,266,267,268,269,327,386)){
            "Species #${source.species} has PID-dependent form, evolution, pattern, or title-specific data that this bounded PK3 route will not guess."
        }
        require(source.form==0){
            "Legends: Z-A form ${source.form} has no reviewed native Generation 3 representation."
        }
        require(source.nature in 0..24&&source.statNature in 0..24){
            "The PA9 nature data is outside the native range."
        }
        require(source.gender in 0..2){
            "The PA9 explicit gender field is outside the reviewed range."
        }
        require(source.ability in 1..400&&abilitySlot in setOf(1,2,4)){
            "The PA9 ability ID or normal/hidden slot is outside the reviewed native range."
        }
        require(!source.isEgg){"Egg PA9 records remain blocked from this backward conversion."}
        require(source.heldItem==0){
            "Held item #${source.heldItem} has no reviewed PA9 -> PK3 mapping. Remove it in Legends: Z-A first."
        }
        require(!source.fatefulEncounter){
            "Fateful-event provenance cannot be represented in Generation 3."
        }
        require(language==2){
            "This bounded route supports English PA9 text only; other glyph mappings remain blocked."
        }
        require(nickname.isNotBlank()&&nickname.length<=10){
            "The PA9 nickname must contain 1-10 Generation 3-compatible characters."
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
            "The Legends: Z-A EVs are not destination-valid Generation 3 EV data."
        }
        require(source.ivs.size==6&&source.ivs.all{it in 0..31}){
            "The PA9 IV data is outside its native range."
        }
        require(source.experience in 0..0xFFFFFFFFL){
            "Experience is outside the Generation 3 four-byte range."
        }
        require(source.hyperTrainingFlags==0){
            "Hyper Training cannot be represented in Generation 3. Clear every Hyper Training flag first."
        }
        require(source.currentHandler in 0..1){
            "The PA9 current-handler field is outside the reviewed range."
        }
        require(metLevel in 1..100){
            "The PA9 met level is outside the native Generation 3 range 1-100."
        }
        val friendship=if(source.currentHandler==0)
            source.originalTrainerFriendship else source.handlingTrainerFriendship
        val markings=u16(plain,0x18)
        val contest=plain.copyOfRange(0x2C,0x32)
        val ribbons=plain.copyOfRange(0x34,0x48)
        val trackerAndTitle=plain.copyOfRange(0x127,STORED_END)
        val omittedMetadata=listOf(
            "ability/slot and marking bytes 0x14-0x1B=${hex(plain,0x14,0x1C)}",
            "nature/gender/Alpha/form bytes 0x20-0x25=${hex(plain,0x20,0x26)}",
            "contest bytes 0x2C-0x31=${hex(plain,0x2C,0x32)}; Pokérus 0x32 is retained",
            "ribbon, mark and size bytes 0x34-0x57=${hex(plain,0x34,0x58)}",
            "relearn and status bytes 0x82-0x97=${hex(plain,0x82,0x98)}",
            "handler and Legends: Z-A metadata 0x98-0xF7=${hex(plain,0x98,0xF8)}",
            "friendship, date, location, Ball and battle bytes 0x112-0x126=${hex(plain,0x112,0x127)}",
            "HOME tracker, move records and trailing title bytes 0x127-0x147=${hex(plain,0x127,STORED_END)}",
            "party cache 0x148-0x157=${hex(plain,0x148,RECORD_SIZE)}"
        ).joinToString(", ")
        val effects=listOf(
            "Explicit Legalise: backward PSE Legends: Z-A PA9 → native Generation 3 PK3. This is not an official trade, migration, Pokémon Bank or HOME transfer, encounter record, legality certification, or verified provenance.",
            "The route accepts one selected checksum-valid content-addressed 344-byte PA9. Species #${source.species}, exact default form, nickname, OT name, TID/SID, EXP, current moves, PP, PP Ups, EVs, IVs, active friendship and Pokérus enter the reviewed native PK3 mapping.",
            "PID 0x${source.pid.toString(16).uppercase().padStart(8,'0')} is retained. Native Generation 3 derives nature, gender, normal ability slot and shiny state from that PID; the review names every resulting value and any change from the PA9 fields before confirmation.",
            "The user chooses a documented Generation 3 origin title. PK3 met location becomes explicit unspecified value 0, Ball becomes Poké Ball (ID 4), and met level $metLevel plus OT gender $originalTrainerGender are retained. Source origin game $originGame and Ball ${source.ball} are omitted only by the Legalise choice.",
            "PA9 ability #${source.ability} in slot $abilitySlot, nature ${source.nature}, stat nature ${source.statNature}, explicit gender ${source.gender}, and source shiny ${source.shiny} are reviewed against the destination PID-derived results; no official encounter provenance is inferred.",
            "Encryption constant 0x${source.encryptionConstant.toString(16).uppercase().padStart(8,'0')}, stat nature ${source.statNature}, Alpha status ${source.isAlpha}, cached level ${source.cachedLevel}, current HP ${source.currentHp}/${source.maximumHp}, cached stats ${source.cachedStats.joinToString("/")}, scale ${source.scale}, obedience level ${source.obedienceLevel}, and relearn moves ${source.relearnMoves.joinToString("/")} are omitted. The Generation 3 game rebuilds party cache from the boxed PK3.",
            "The input must already have Egg, fateful-event and held-item fields cleared. English language $language is retained. Active friendship $friendship and Pokérus 0x${pokerus.toString(16).uppercase().padStart(2,'0')} are retained; current-handler identity and inactive friendship are omitted.",
            if(contest.all{it==0.toByte()})"No PA9 contest bytes require omission." else
                "PA9 contest bytes ${hex(plain,0x2C,0x32)} are omitted by the explicit Legalise choice.",
            if(ribbons.all{it==0.toByte()})"No PA9 ribbon or mark bytes require omission." else
                "PA9 ribbon and mark bytes ${hex(plain,0x34,0x48)} are omitted by the explicit Legalise choice.",
            if(trackerAndTitle.all{it==0.toByte()})"No PA9 HOME tracker, move-record or trailing title bytes require omission." else
                "PA9 HOME tracker, move-record and trailing title bytes ${hex(plain,0x127,STORED_END)} are omitted; PSE does not invent an official HOME identity.",
            "Markings 0x${markings.toString(16).uppercase().padStart(4,'0')} and every remaining title-specific byte range are disclosed before confirmation: $omittedMetadata.",
            "The source Legends: Z-A save and native PA9 Bank entry are never consumed. Destination writing is delegated only after this exact review is accepted."
        )
        return LegendsZaToGen3Review(
            source.species,nickname,originalTrainer,source.tid,source.sid,
            source.pid,source.encryptionConstant,source.experience,source.nature,
            source.statNature,source.ability,abilitySlot,source.gender,
            source.moves.map{it.id},source.moves.map{it.pp},source.moves.map{it.ppUps},
            source.ivs,source.evs,friendship,pokerus,source.shiny,source.cachedLevel,
            source.form,source.isAlpha,originGame,source.ball,metLevel,
            originalTrainerGender,source.scale,source.obedienceLevel,
            source.relearnMoves,source.cachedStats,effects
        )
    }

    private fun plainRecord(record:ByteArray):ByteArray{
        require(record.size==RECORD_SIZE){
            "A complete 344-byte native Legends: Z-A PA9 is required."
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
            "The native PA9 checksum failed."
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
