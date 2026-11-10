package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets
import kotlin.math.floor
import kotlin.math.sqrt

data class SwordShieldToLegendsArceusReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val effects: List<String>
)

object SwordShieldToLegendsArceusPa8 {
    private const val PK8_SIZE = 0x158
    private const val PA8_SIZE = 0x168
    private const val PK8_STORED_END = 0x148
    private val orders = listOf("0123","0132","0213","0312","0231","0321","1023","1032",
        "2013","3012","2031","3021","1203","1302","2103","3102","2301","3201",
        "1230","1320","2130","3120","2310","3210")
    private val reviewedOrigins = setOf(1,2,3,4,5,7,8,10,11,12,15,20,21,22,23,24,25,26,27,
        30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45)
    private val unavailableDefaultForms = setOf(58,59,100,101,157,211,503,549,550,570,571,628,705,706,713,724)

    fun review(record: ByteArray): SwordShieldToLegendsArceusReview {
        val source = SwordShieldPseBankRestoreEditing.inspectBankRecord(record)
        val s = plainPk8(record)
        val species=source.species; val form=source.form; val held=source.heldItem
        val ability=source.ability; val abilitySlot=byte(s,0x16) and 7
        val markings=u16(s,0x18); val nature=source.nature; val statNature=source.statNature
        val sourceFlags=byte(s,0x22); val sourceGender=source.gender; val exp=source.experience
        val iv32=u32(s,0x8C); val hyper=source.hyperTrainingFlags
        val language=byte(s,0xE2); val origin=byte(s,0xDE); val sourceBall=byte(s,0x124)
        val metLevel=byte(s,0x125) and 0x7F; val currentHandler=byte(s,0xC4)
        require(species in LegendsArceusPokedexReadOnly.supportedSpecies) {
            "Species #$species is not available in the reviewed Legends: Arceus destination catalogue."
        }
        require(form==0 && species !in unavailableDefaultForms) {
            "Sword/Shield form $form for species #$species has no reviewed native Legends: Arceus mapping."
        }
        require(!source.isEgg){"Egg PK8 records remain blocked from this PSE conversion."}
        require(held==0){"Held item #$held has no Legends: Arceus held-item field. Remove it in the source game first."}
        require(nature in 0..24 && statNature in 0..24){"The source nature data is outside the native range."}
        require(ability in 1..267 && abilitySlot in setOf(1,2,4)) {
            "The source ability ID or slot is outside the reviewed PA8 storage range."
        }
        require(sourceGender in 0..2){"The source gender field is outside the reviewed PA8 range."}
        require(language==2){"This bounded route supports English PK8 text only; other glyph translations remain blocked."}
        require(origin in reviewedOrigins){"Origin game ID $origin has no reviewed PK8 -> PA8 mapping."}
        require(sourceBall in 1..26){"Ball ID $sourceBall is outside the reviewed Sword/Shield range."}
        require(metLevel in 1..100){"The source met level is outside 1-100."}
        require(date(byte(s,0x119),byte(s,0x11A),byte(s,0x11B))){"The source egg date is invalid."}
        require(date(byte(s,0x11C),byte(s,0x11D),byte(s,0x11E))){"The source met date is invalid."}
        require(hyper and 0xC0 == 0){"The source Hyper Training field contains unsupported bits."}
        require(currentHandler in 0..1){"The source current-handler field is outside the reviewed PA8 range."}
        val ev=source.evs.toIntArray()
        require(ev.all{it in 0..252} && ev.sum()<=510){"The source EVs exceed the stored PA8 limits."}
        val iv=source.ivs.toIntArray()
        val moves=source.moves.map{it.id}.toIntArray()
        val pp=source.moves.map{it.pp}.toIntArray()
        val ups=source.moves.map{it.ppUps}.toIntArray()
        for(i in 0..3){
            val move=moves[i]
            if(move==0) require(pp[i]==0 && ups[i]==0){"Empty move slot ${i+1} contains PP or PP-Up data."}
            else {
                require(ups[i] in 0..3){"Move #$move has an invalid PP-Up count."}
                require(LegendsArceusMoveCatalog.choice(move)!=null){"Move #$move is not usable in Legends: Arceus."}
                require(pp[i] in 0..LegendsArceusMoveCatalog.maximumPp(move)) {
                    "Move #$move has current PP above its Legends: Arceus base limit."
                }
            }
        }
        source.relearnMoves.filter{it!=0}.forEach{
            require(LegendsArceusMoveCatalog.choice(it)!=null){"Relearn move #$it is not usable in Legends: Arceus."}
        }
        val nickname=text(s,0x58,"nickname"); val ht=text(s,0xA8,"Handling Trainer name")
        val ot=text(s,0xF8,"Original Trainer name")
        val personal=LegendsArceusPersonalCatalogue.profiles(species,form)
            .distinctBy{listOf(it.hp,it.attack,it.defense,it.speed,it.specialAttack,it.specialDefense,it.growth)}
            .singleOrNull()?:error("Species #$species form $form has no single verified Legends: Arceus profile.")
        require(exp<=minExp(100,personal.growth)){"The source EXP exceeds the reviewed level-100 value."}
        val level=level(exp,personal.growth)
        val effective=IntArray(6){i->if(hyper and (1 shl intArrayOf(0,1,2,5,3,4)[i])!=0)31 else iv[i]}
        val base=intArrayOf(personal.hp,personal.attack,personal.defense,personal.speed,personal.specialAttack,personal.specialDefense)
        fun ganbaru(i:Int):Int{
            val bias=when{effective[i]>=31->3;effective[i]>=26->2;effective[i]>=20->1;else->0}
            val multiplier=intArrayOf(0,2,3,4,7,8,9,14,15,16,25)[bias]
            val step=sqrt(base[i].toDouble())*multiplier
            return floor(((step.toFloat()+level)/2.5f+0.5f).toDouble()).toInt()
        }
        val hp=ganbaru(0)+((((level/100.0f)+1.0f)*base[0])+level).toInt()
        fun adjusted(i:Int,v:Int):Int{val up=statNature/5;val down=statNature%5;return if(up==down)v else when(i){up->v*110/100;down->v*90/100;else->v}}
        fun regular(i:Int,natureIndex:Int):Int{
            val initial=((((level/50.0f)+1.0f)*base[i])/1.5f).toInt()
            return ganbaru(i)+adjusted(natureIndex,initial)
        }
        val stats=listOf(regular(1,0),regular(2,1),regular(3,4),regular(4,2),regular(5,3))

        val o=ByteArray(PA8_SIZE)
        w32(o,0,source.encryptionConstant);w16(o,8,species);copy(s,0x0C,o,0x0C,8);w16(o,0x14,ability)
        o[0x16]=abilitySlot.toByte();w16(o,0x18,markings and 0x0FFF);w32(o,0x1C,source.pid)
        o[0x20]=nature.toByte();o[0x21]=statNature.toByte()
        o[0x22]=((sourceFlags and 1) or (sourceGender shl 2)).toByte();o[0x24]=form.toByte()
        copy(s,0x26,o,0x26,6);copy(s,0x2C,o,0x2C,6);o[0x32]=s[0x32]
        copy(s,0x72,o,0x54,8);copy(s,0x7A,o,0x5C,4);putText(o,0x60,nickname)
        copy(s,0x82,o,0x8A,8);w16(o,0x92,hp);w32(o,0x94,iv32)
        putText(o,0xB8,ht);o[0xD2]=s[0xC2];o[0xD3]=s[0xC3];o[0xD4]=s[0xC4];o[0xD8]=s[0xC8]
        o[0xEE]=s[0xDE];o[0xF2]=s[0xE2];o[0xF8]=0xFF.toByte()
        putText(o,0x110,ot);o[0x12A]=s[0x112];copy(s,0x119,o,0x131,6)
        o[0x137]=27.toByte();w16(o,0x138,u16(s,0x120));w16(o,0x13A,u16(s,0x122))
        o[0x13D]=s[0x125];o[0x13E]=s[0x126]
        var sum=0;for(offset in 8 until PA8_SIZE step 2)sum+=u16(o,offset);w16(o,6,sum and 0xFFFF)
        val native=encrypt(o)
        val p=(LegendsArceusPokemonReadOnly.read(native) as? LegendsArceusSlotRead.Pokemon)?.value
            ?:error("The converted PA8 did not reopen with a valid native checksum.")
        val reopenedPlain=plainPa8(native)
        require(p.species==species&&p.form==form&&p.gender==sourceGender&&
            p.fatefulEncounter==source.fatefulEncounter&&p.pid==source.pid&&
            p.encryptionConstant==source.encryptionConstant&&p.tid==source.tid&&p.sid==source.sid&&
            p.shiny==source.shiny&&p.experience==exp&&p.nature==nature&&p.statNature==statNature&&
            !p.isEgg&&!p.isAlpha&&!p.isNoble&&p.currentHp==hp&&p.ball==27&&
            p.originalTrainerFriendship==byte(s,0x112)&&
            p.handlingTrainerFriendship==byte(s,0xC8)&&p.currentHandler==currentHandler&&
            p.hyperTrainingFlags==hyper&&p.nickname==nickname&&p.originalTrainer==ot&&
            p.moves.map{it.id to it.pp}==source.moves.map{it.id to it.pp}&&
            p.ivs==source.ivs&&p.ganbaruValues.all{it==0}&&
            p.relearnMoves==source.relearnMoves&&u16(reopenedPlain,0x14)==ability&&
            byte(reopenedPlain,0x16)==abilitySlot&&
            u16(reopenedPlain,0x18)==(markings and 0x0FFF)&&
            byte(reopenedPlain,0x22)==((sourceFlags and 1) or (sourceGender shl 2))&&
            reopenedPlain.copyOfRange(0x26,0x33).contentEquals(s.copyOfRange(0x26,0x33))&&
            text(reopenedPlain,0xB8,"reopened Handling Trainer name")==ht&&
            byte(reopenedPlain,0xD2)==byte(s,0xC2)&&
            byte(reopenedPlain,0xD3)==byte(s,0xC3)&&
            byte(reopenedPlain,0xD4)==currentHandler&&byte(reopenedPlain,0xD8)==byte(s,0xC8)&&
            byte(reopenedPlain,0xEE)==origin&&byte(reopenedPlain,0xF2)==language&&
            byte(reopenedPlain,0xF8)==0xFF&&byte(reopenedPlain,0x12A)==byte(s,0x112)&&
            reopenedPlain.copyOfRange(0x131,0x137).contentEquals(s.copyOfRange(0x119,0x11F))&&
            reopenedPlain.copyOfRange(0x138,0x13C).contentEquals(s.copyOfRange(0x120,0x124))&&
            byte(reopenedPlain,0x13D)==byte(s,0x125)&&byte(reopenedPlain,0x13E)==hyper&&
            byte(reopenedPlain,0x17)==0&&u16(reopenedPlain,0x1A)==0&&
            byte(reopenedPlain,0x23)==0&&byte(reopenedPlain,0x25)==0&&byte(reopenedPlain,0x33)==0&&
            reopenedPlain.copyOfRange(0x34,0x54).all{it==0.toByte()}&&
            reopenedPlain.copyOfRange(0x7A,0x8A).all{it==0.toByte()}&&
            reopenedPlain.copyOfRange(0x98,0xB8).all{it==0.toByte()}&&
            reopenedPlain.copyOfRange(0xD5,0xD8).all{it==0.toByte()}&&
            reopenedPlain.copyOfRange(0xD9,0xEE).all{it==0.toByte()}&&
            reopenedPlain.copyOfRange(0xEF,0xF2).all{it==0.toByte()}&&
            reopenedPlain.copyOfRange(0xF3,0xF8).all{it==0.toByte()}&&
            reopenedPlain.copyOfRange(0xF9,0x110).all{it==0.toByte()}&&
            reopenedPlain.copyOfRange(0x12B,0x131).all{it==0.toByte()}&&
            reopenedPlain.copyOfRange(0x13C,0x13D).all{it==0.toByte()}&&
            reopenedPlain.copyOfRange(0x13F,0x168).all{it==0.toByte()}) {
            "The converted PA8 did not reopen with every reviewed identity, trainer, move, stat, and Legends: Arceus default field."
        }
        LegendsArceusPseBankRestoreEditing.inspectBankRecord(native)
        val party=LegendsArceusPokemonLevelEditing.partyRecordFromStored(native)
        val reopenedParty=(LegendsArceusPokemonReadOnly.read(party) as? LegendsArceusSlotRead.Pokemon)?.value
            ?:error("The converted PA8 did not reopen with a verified native party cache.")
        require(reopenedParty.copy(cachedLevel=null,maximumHp=null,cachedStats=null)==p&&
            reopenedParty.cachedLevel==level&&reopenedParty.maximumHp==hp&&
            reopenedParty.currentHp==hp&&reopenedParty.cachedStats==stats) {
            "The converted PA8 did not reopen with the exact reviewed stored core, level, full HP, and five native cached stats."
        }

        val ribbonBytes=s.copyOfRange(0x34,0x48)
        val heightScalar=byte(s,0x48);val weightScalar=byte(s,0x49);val sourceScale=byte(s,0x4A)
        val tracker=s.copyOfRange(0xE9,0xF1)
        val technicalRecords=s.copyOfRange(0x127,0x148)
        val omittedMetadata=listOf(
            "0x17="+hex(s,0x17,0x18), "0x1A-0x1B="+hex(s,0x1A,0x1C),
            "0x23="+hex(s,0x23,0x24), "0x25="+hex(s,0x25,0x26),
            "0x33="+hex(s,0x33,0x34), "0x4B-0x57="+hex(s,0x4B,0x58),
            "0x7E-0x81="+hex(s,0x7E,0x82), "0x8A-0x8B="+hex(s,0x8A,0x8C),
            "0x91-0xA7="+hex(s,0x91,0xA8), "0xC5-0xC7="+hex(s,0xC5,0xC8),
            "0xC9-0xDD="+hex(s,0xC9,0xDE), "0xDF-0xE1="+hex(s,0xDF,0xE2),
            "0xE3-0xE8="+hex(s,0xE3,0xE9), "0xE9-0xF7="+hex(s,0xE9,0xF8),
            "0x113-0x118="+hex(s,0x113,0x119), "0x11F="+hex(s,0x11F,0x120),
            "0x127-0x147="+hex(s,0x127,0x148), "0x148-0x157="+hex(s,0x148,0x158)
        ).joinToString(", ")
        val effects=listOf(
            "Explicit Legalise: PSE Sword/Shield PK8 -> Legends: Arceus PA8 conversion; this cross-title route is not an official HOME transfer, in-game trade, or verified provenance.",
            "Species #$species and exact default form 0 map to the reviewed Legends: Arceus Pokédex and one verified personal profile. PID, EC, TID/SID, EXP, IVs, raw EV bytes, nature, stat nature, gender, shiny state, friendship, Pokerus, contest values, origin, dates, locations, fateful flag, Hyper Training, moves, current PP and relearn moves are retained.",
            "Nickname, OT text, HT text, HT language and current-handler state are retained as English UTF-16 text; source trash bytes are normalized.",
            "Held item remains empty. Ability ID $ability and slot $abilitySlot are retained as PA8 storage data; Legends: Arceus encounter, ability and moveset legality are not certified.",
            "Source Ball #$sourceBall becomes Strange Ball #27, the native Legends: Arceus representation for a Pokémon originating outside Hisui.",
            if(ups.all{it==0})"No PP Ups require removal; PA8 PP-Up storage remains absent." else "PP Ups ${ups.joinToString("/")} are cleared because Legends: Arceus uses fixed move PP; current PP was verified against each native base limit.",
            "Ganbaru Values reset to 0. Native stats use retained IVs, Hyper Training, level, stat nature and PLA's IV bias; raw EV bytes remain stored but do not drive PLA battle stats. Current HP is rebuilt full at $hp.",
            "Sword/Shield Dynamax Level ${source.dynamaxLevel} and Gigantamax flag ${source.canGigantamax} are cleared because PA8 has no equivalent.",
            "Alpha and Noble flags, Alpha move, move-shop purchased/mastered flags and PA8-only move records remain zero. PSE does not invent Alpha, Noble, or move-shop history.",
            "Source height scalar $heightScalar, weight scalar $weightScalar and scale $sourceScale reset to 0; absolute height and weight caches remain native zero defaults.",
            if(markings and 0xF000==0)"All 12 supported marking bits are retained." else "High marking bits 0x${(markings and 0xF000).toString(16).uppercase()} are cleared; low 12 bits are retained.",
            if(ribbonBytes.all{it==0.toByte()})"No Sword/Shield ribbon or mark bits require omission." else "Ribbon and mark bytes ${ribbonBytes.joinToString(" "){"%02X".format(it)}} are cleared; this bounded adapter has no reviewed PK8 -> PA8 ribbon mapping.",
            if(technicalRecords.all{it==0.toByte()})"No Sword/Shield Technical Record flags require omission." else "${technicalRecords.count{it!=0.toByte()}} nonzero Sword/Shield Technical Record bytes are cleared; Legends: Arceus move availability is validated directly.",
            if(tracker.all{it==0.toByte()})"No source HOME tracker bytes require omission." else "Source HOME tracker bytes ${tracker.joinToString(" "){"%02X".format(it)}} are cleared; PSE does not invent or claim an official HOME identity.",
            "Trainer memories, sociability, fullness/enjoyment, battle state/version, form argument, affixed ribbon and other unmapped PK8 or PA8-only metadata reset to disclosed defaults.",
            "Unmapped PK8 metadata bytes are omitted only by this explicit Legalise choice: $omittedMetadata.",
            "The PK8 Bank entry and Legends: Arceus source remain unchanged. One user-chosen empty native PA8 pasture slot in a new copy is written with retained recovery, SHA-256 footer update, persisted reopen verification, and two-sided rollback."
        )
        return SwordShieldToLegendsArceusReview(native,species,nickname,effects)
    }

    private fun plainPk8(record:ByteArray):ByteArray{
        require(record.size==PK8_SIZE){"A complete 344-byte native PK8 record is required."}
        val ec=u32(record,0);val decoded=record.copyOf();xor(decoded,8,PK8_STORED_END,ec);xor(decoded,PK8_STORED_END,PK8_SIZE,ec)
        val order=orders[((ec ushr 13).toInt() and 31)%24];val plain=decoded.copyOf()
        for(block in 0..3){val from=8+(order[block]-'0')*0x50;decoded.copyInto(plain,8+block*0x50,from,from+0x50)}
        var sum=0;for(offset in 8 until PK8_STORED_END step 2)sum+=u16(plain,offset)
        require(u16(record,4)==0&&(sum and 0xFFFF)==u16(record,6)){"The PK8 checksum failed."}
        return plain
    }
    private fun plainPa8(record:ByteArray):ByteArray{
        require(record.size==PA8_SIZE){"A complete 360-byte native stored PA8 record is required."}
        val ec=u32(record,0);val decoded=record.copyOf();xor(decoded,8,PA8_SIZE,ec)
        val order=orders[((ec ushr 13).toInt() and 31)%24];val plain=decoded.copyOf()
        for(block in 0..3){val from=8+(order[block]-'0')*0x58;decoded.copyInto(plain,8+block*0x58,from,from+0x58)}
        var sum=0;for(offset in 8 until PA8_SIZE step 2)sum+=u16(plain,offset)
        require(u16(record,4)==0&&(sum and 0xFFFF)==u16(record,6)){"The PA8 checksum failed."}
        return plain
    }
    private fun text(b:ByteArray,o:Int,label:String):String{var end=o;while(end<o+26&&u16(b,end)!=0)end+=2
        val v=String(b,o,end-o,StandardCharsets.UTF_16LE)
        require(v.length<=12&&v.all{it.code !in 0xD800..0xDFFF&&it.code>=0x20&&it.code !in 0xE000..0xF8FF}){"The $label contains an unmapped glyph."};return v}
    private fun putText(b:ByteArray,o:Int,v:String){val x=v.toByteArray(StandardCharsets.UTF_16LE);require(x.size<=24){"Converted text exceeds 12 characters."};x.copyInto(b,o)}
    private fun date(y:Int,m:Int,d:Int)=if(y==0&&m==0&&d==0)true else y in 0..99&&m in 1..12&&d in 1..31&&runCatching{java.time.LocalDate.of(2000+y,m,d)}.isSuccess
    private fun minExp(l:Int,g:Int):Long{if(l<=1)return 0;val n=l.toLong();val c=n*n*n;return when(g){0->c;1->when{l<=50->c*(100-n)/50;l<=68->c*(150-n)/100;l<=98->{val r=n%3;c*(1274+r*r-9*r-20*(n/3))/1000};else->c*(160-n)/100};2->when{l<=15->c*(24+(n+1)/3)/50;l<=35->c*(14+n)/50;else->c*(32+n/2)/50};3->maxOf(0L,6*c/5-15*n*n+100*n-140);4->4*c/5;5->5*c/4;else->error("Unsupported growth group.")}}
    private fun level(e:Long,g:Int):Int{var l=1;for(i in 2..100){if(e<minExp(i,g))break;l=i};return l}
    private fun encrypt(p:ByteArray):ByteArray{val ec=u32(p,0);val order=orders[((ec ushr 13).toInt() and 31)%24];val s=p.copyOf()
        for(block in 0..3){val d=8+(order[block]-'0')*0x58;p.copyInto(s,d,8+block*0x58,8+(block+1)*0x58)}
        xor(s,8,PA8_SIZE,ec);return s}
    private fun xor(b:ByteArray,from:Int,end:Int,seed:Long){var state=seed;for(i in from until end step 2){state=(state*0x41C64E6DL+0x6073L) and 0xFFFFFFFFL;w16(b,i,u16(b,i) xor (state ushr 16).toInt())}}
    private fun copy(a:ByteArray,ao:Int,b:ByteArray,bo:Int,n:Int)=a.copyInto(b,bo,ao,ao+n)
    private fun hex(b:ByteArray,from:Int,end:Int)=b.copyOfRange(from,end).joinToString(""){"%02X".format(it)}
    private fun byte(b:ByteArray,o:Int)=b[o].toInt() and 255
    private fun u16(b:ByteArray,o:Int)=byte(b,o) or (byte(b,o+1) shl 8)
    private fun u32(b:ByteArray,o:Int)=u16(b,o).toLong() or (u16(b,o+2).toLong() shl 16)
    private fun w16(b:ByteArray,o:Int,v:Int){b[o]=v.toByte();b[o+1]=(v ushr 8).toByte()}
    private fun w32(b:ByteArray,o:Int,v:Long){w16(b,o,v.toInt());w16(b,o+2,(v ushr 16).toInt())}
}
