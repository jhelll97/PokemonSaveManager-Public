package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets

data class LegendsArceusToBdspReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val effects: List<String>
)

object LegendsArceusToBdspPb8 {
    private const val PA8_STORED_SIZE = 0x168
    private const val PA8_PARTY_SIZE = 0x178
    private const val PB8_SIZE = 0x158
    private const val PB8_STORED_END = 0x148
    private val orders = listOf("0123","0132","0213","0312","0231","0321","1023","1032",
        "2013","3012","2031","3021","1203","1302","2103","3102","2301","3201",
        "1230","1320","2130","3120","2310","3210")
    private val reviewedOrigins = setOf(1,2,3,4,5,7,8,10,11,12,15,20,21,22,23,24,25,26,27,
        30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45)

    fun review(record: ByteArray): LegendsArceusToBdspReview {
        val source = LegendsArceusPseBankRestoreEditing.inspectBankRecord(record)
        val s = plainPa8(record)
        val species=source.species; val form=source.form
        val ability=u16(s,0x14); val abilitySlot=byte(s,0x16) and 7
        val markings=u16(s,0x18); val nature=source.nature; val statNature=source.statNature
        val sourceFlags=byte(s,0x22); val gender=source.gender; val exp=source.experience
        val iv32=u32(s,0x94); val hyper=source.hyperTrainingFlags
        val language=byte(s,0xF2); val origin=byte(s,0xEE); val sourceBall=source.ball
        val metLevel=byte(s,0x13D) and 0x7F; val currentHandler=source.currentHandler
        val heightScalar=byte(s,0x50); val weightScalar=byte(s,0x51); val displayScale=byte(s,0x52)
        require(species in 1..493) {
            "Species #$species is outside BDSP's native species range 1-493."
        }
        require(form==0) {
            "Legends: Arceus form $form for species #$species has no reviewed native BDSP mapping in this bounded route."
        }
        require(!source.isEgg){"Egg PA8 records remain blocked from this PSE conversion."}
        require(!source.isNoble){"Noble PA8 records have no reviewed BDSP storage mapping."}
        require(nature in 0..24 && statNature in 0..24){"The source nature data is outside the native range."}
        require(ability in 1..267 && abilitySlot in setOf(1,2,4)) {
            "The source ability ID or slot is outside the reviewed PB8 storage range."
        }
        require(gender in 0..2){"The source gender field is outside the reviewed PB8 range."}
        require(language==2){"This bounded route supports English PA8 text only; other glyph translations remain blocked."}
        require(origin in reviewedOrigins){"Origin game ID $origin has no reviewed PA8 -> PB8 mapping."}
        require(sourceBall in 27..37){"Ball ID $sourceBall is outside the reviewed Legends: Arceus range."}
        require(metLevel in 1..100){"The source met level is outside 1-100."}
        require(date(byte(s,0x131),byte(s,0x132),byte(s,0x133))){"The source egg date is invalid."}
        require(date(byte(s,0x134),byte(s,0x135),byte(s,0x136))){"The source met date is invalid."}
        require(hyper and 0xC0 == 0){"The source Hyper Training field contains unsupported bits."}
        require(currentHandler in 0..1){"The source current-handler field is outside the reviewed PB8 range."}
        val ev=IntArray(6){byte(s,0x26+it)}
        require(ev.all{it in 0..252} && ev.sum()<=510){"The stored PA8 EV bytes exceed BDSP limits."}
        val iv=source.ivs.toIntArray()
        val moves=source.moves.map{it.id}.toIntArray()
        val pp=source.moves.map{it.pp}.toIntArray()
        for(i in 0..3){
            val move=moves[i]
            if(move==0) require(pp[i]==0){"Empty move slot ${i+1} contains PP data."}
            else {
                require(BdspMoveCatalog.choice(move)!=null){"Move #$move is not usable in BDSP."}
                require(pp[i] in 0..BdspMoveCatalog.maximumPp(move,0)) {
                    "Move #$move has PP outside its BDSP no-PP-Up limit."
                }
            }
        }
        source.relearnMoves.filter{it!=0}.forEach{
            require(BdspMoveCatalog.choice(it)!=null){"Relearn move #$it is not usable in BDSP."}
        }
        val nickname=text(s,0x60,"nickname"); val ht=text(s,0xB8,"Handling Trainer name")
        val ot=text(s,0x110,"Original Trainer name")
        val personal=BdspPersonalCatalogue.profiles(species,0)
            .distinctBy{listOf(it.hp,it.attack,it.defense,it.speed,it.specialAttack,it.specialDefense,it.growth)}
            .singleOrNull()?:error("Species #$species has no single verified default BDSP profile.")
        require(exp<=minExp(100,personal.growth)){"The source EXP exceeds the reviewed level-100 value."}
        val level=level(exp,personal.growth)
        val effective=IntArray(6){i->if(hyper and (1 shl intArrayOf(0,1,2,5,3,4)[i])!=0)31 else iv[i]}
        val base=intArrayOf(personal.hp,personal.attack,personal.defense,personal.speed,personal.specialAttack,personal.specialDefense)
        fun core(i:Int)=(2*base[i]+effective[i]+ev[i]/4)*level/100
        fun adjusted(stat:Int,v:Int):Int{
            val up=statNature/5;val down=statNature%5
            return if(up==down)v else when(stat){up->v*110/100;down->v*90/100;else->v}
        }
        val hp=if(species==292)1 else core(0)+level+10
        val atk=adjusted(0,core(1)+5);val def=adjusted(1,core(2)+5)
        val spe=adjusted(2,core(3)+5);val spa=adjusted(3,core(4)+5);val spd=adjusted(4,core(5)+5)

        val o=ByteArray(PB8_SIZE)
        w32(o,0,source.encryptionConstant);w16(o,8,species);copy(s,0x0C,o,0x0C,8)
        w16(o,0x14,ability);o[0x16]=abilitySlot.toByte();w16(o,0x18,markings and 0x0FFF)
        w32(o,0x1C,source.pid);o[0x20]=nature.toByte();o[0x21]=statNature.toByte()
        o[0x22]=((sourceFlags and 1) or (gender shl 2)).toByte();o[0x24]=0
        copy(s,0x26,o,0x26,6);copy(s,0x2C,o,0x2C,6);o[0x32]=s[0x32]
        putText(o,0x58,nickname);copy(s,0x54,o,0x72,8);copy(s,0x5C,o,0x7A,4)
        copy(s,0x8A,o,0x82,8);w16(o,0x8A,hp);w32(o,0x8C,iv32)
        putText(o,0xA8,ht);o[0xC2]=s[0xD2];o[0xC3]=s[0xD3];o[0xC4]=s[0xD4];o[0xC8]=s[0xD8]
        o[0xDE]=s[0xEE];o[0xE2]=s[0xF2];o[0xE8]=0xFF.toByte()
        putText(o,0xF8,ot);o[0x112]=s[0x12A];copy(s,0x131,o,0x119,6)
        copy(s,0x138,o,0x120,4);o[0x124]=4;o[0x125]=s[0x13D];o[0x126]=s[0x13E]
        o[0x148]=level.toByte();w16(o,0x14A,hp);w16(o,0x14C,atk);w16(o,0x14E,def)
        w16(o,0x150,spe);w16(o,0x152,spa);w16(o,0x154,spd)
        var checksum=0;for(offset in 8 until PB8_STORED_END step 2)checksum+=u16(o,offset)
        w16(o,6,checksum and 0xFFFF)
        val native=encryptPb8(o)
        val p=(BdspPokemonReadOnly.read(native) as? BdspSlotRead.Pokemon)?.value
            ?:error("The converted PB8 did not reopen with a valid native checksum.")
        val reopened=plainPb8(native)
        require(p.species==species&&p.form==0&&p.gender==gender&&
            p.fatefulEncounter==source.fatefulEncounter&&p.pid==source.pid&&p.experience==exp&&
            p.nature==nature&&p.statNature==statNature&&p.ability==ability&&p.heldItem==0&&
            p.cachedLevel==level&&p.currentHp==hp&&p.maximumHp==hp&&
            p.cachedStats==listOf(atk,def,spe,spa,spd)&&
            p.moves.map{it.id}==moves.toList()&&p.moves.map{it.pp}==pp.toList()&&
            p.moves.map{it.ppUps}.all{it==0}&&p.relearnMoves==source.relearnMoves&&
            p.ivs==source.ivs&&p.evs==ev.toList()&&byte(reopened,0x124)==4&&
            byte(reopened,0xDE)==origin&&byte(reopened,0x48)==0&&
            byte(reopened,0x49)==0&&byte(reopened,0x4A)==0) {
            "The converted PB8 did not reopen with the reviewed identity, values, stats, moves, Poké Ball, size, and title defaults."
        }
        BdspPseBankRestoreEditing.inspectBankRecord(native)

        val ribbons=s.copyOfRange(0x34,0x48)
        val tracker=s.copyOfRange(0xF9,0x101)
        val alphaMove=u16(s,0x3E)
        val purchased=s.copyOfRange(0x155,0x15D)
        val mastered=s.copyOfRange(0x15D,0x165)
        val partyTail=record.size==PA8_PARTY_SIZE
        val effects=listOf(
            "Explicit Legalise: PSE Legends: Arceus PA8 -> BDSP PB8 conversion; this backward cross-title route is not an official HOME transfer, in-game trade, or verified provenance.",
            "Species #$species and exact default form 0 map to the reviewed BDSP Pokédex and one verified personal profile. PID, EC, TID/SID, EXP, IVs, stored EV bytes, nature, stat nature, gender, shiny state, friendship, Pokérus, contest values, reviewed origin, dates, locations, fateful flag, Hyper Training, moves, current PP and relearn moves are retained.",
            "Nickname, OT text, HT text, HT language and current-handler state are retained as English UTF-16 text; source trash bytes are normalized.",
            "Legends: Arceus has no held-item field, so the PB8 held item remains empty. Ability ID $ability and slot $abilitySlot are retained as PB8 storage data; encounter, ability and moveset legality are not certified.",
            "Source Ball #$sourceBall becomes Poké Ball #4 because BDSP has no destination-valid Hisui Ball field. This disclosed Legalise mapping is not a provenance claim.",
            "PA8 PP Ups have no field and PB8 PP Ups remain 0. Each retained current PP value was verified against BDSP's no-PP-Up limit.",
            "Ganbaru Values ${source.ganbaruValues.joinToString("/")} are omitted because PB8 has no equivalent. Stored EV bytes are retained and drive rebuilt BDSP stats.",
            "Source Height Scalar $heightScalar, Weight Scalar $weightScalar, and display Scale $displayScale reset to 0 because this bounded PB8 mapping does not infer BDSP size metadata.",
            "Alpha=${source.isAlpha}, Noble=${source.isNoble}, Alpha Move #$alphaMove, purchased flags ${hex(purchased)}, and mastered flags ${hex(mastered)} are cleared because BDSP has no reviewed equivalent. Noble records are blocked before review.",
            "PB8 has no Dynamax or Gigantamax fields. Current HP is rebuilt full at $hp and cached level $level and stats are rebuilt from retained permanent values and the verified BDSP profile.",
            if(markings and 0xF000==0)"All 12 supported marking bits are retained." else "High marking bits 0x${(markings and 0xF000).toString(16).uppercase()} are cleared; low 12 bits are retained.",
            if(ribbons.all{it==0.toByte()})"No PA8 ribbon or mark bits require omission." else "Ribbon and mark bytes ${hex(ribbons)} are cleared; this bounded adapter has no reviewed PA8 -> PB8 ribbon mapping.",
            if(tracker.all{it==0.toByte()})"No source HOME tracker bytes require omission." else "Source HOME tracker bytes ${hex(tracker)} are cleared; PSE does not invent or claim an official HOME identity.",
            "Trainer memories, sociability, fullness/enjoyment, battle state/version, form argument, move records, TM/TR flags, affixed ribbon and other unmapped PA8 or PB8-only metadata reset to disclosed defaults.",
            if(partyTail)"The 16-byte PA8 party cache is omitted; PB8 Level, current/max HP and cached stats are rebuilt and reopened from permanent values." else "The source is a 360-byte stored PA8 with no party-only cache to omit.",
            "The PA8 Bank entry and BDSP source save remain unchanged. One user-chosen empty native PB8 slot in a new copy is written with a retained recovery backup, native whole-file MD5 update, persisted reopen verification, failed-copy cleanup, and two-sided rollback."
        )
        return LegendsArceusToBdspReview(native,species,nickname,effects)
    }

    private fun plainPa8(record:ByteArray):ByteArray{
        require(record.size==PA8_STORED_SIZE||record.size==PA8_PARTY_SIZE) {
            "A complete 360-byte stored or 376-byte party PA8 record is required."
        }
        val ec=u32(record,0);val decoded=record.copyOf();xor(decoded,8,PA8_STORED_SIZE,ec)
        if(record.size==PA8_PARTY_SIZE)xor(decoded,PA8_STORED_SIZE,PA8_PARTY_SIZE,ec)
        val order=orders[((ec ushr 13).toInt() and 31)%24];val plain=decoded.copyOf()
        for(block in 0..3){val from=8+(order[block]-'0')*0x58;decoded.copyInto(plain,8+block*0x58,from,from+0x58)}
        var sum=0;for(offset in 8 until PA8_STORED_SIZE step 2)sum+=u16(plain,offset)
        require(u16(record,4)==0&&(sum and 0xFFFF)==u16(record,6)){"The PA8 checksum failed."}
        return plain
    }
    private fun plainPb8(record:ByteArray):ByteArray{
        require(record.size==PB8_SIZE){"A complete 344-byte native PB8 record is required."}
        val ec=u32(record,0);val decoded=record.copyOf();xor(decoded,8,PB8_STORED_END,ec);xor(decoded,PB8_STORED_END,PB8_SIZE,ec)
        val order=orders[((ec ushr 13).toInt() and 31)%24];val plain=decoded.copyOf()
        for(block in 0..3){val from=8+(order[block]-'0')*0x50;decoded.copyInto(plain,8+block*0x50,from,from+0x50)}
        var sum=0;for(offset in 8 until PB8_STORED_END step 2)sum+=u16(plain,offset)
        require(u16(record,4)==0&&(sum and 0xFFFF)==u16(record,6)){"The converted PB8 checksum failed."}
        return plain
    }
    private fun text(b:ByteArray,o:Int,label:String):String{var end=o;while(end<o+26&&u16(b,end)!=0)end+=2
        val v=String(b,o,end-o,StandardCharsets.UTF_16LE)
        require(v.length<=12&&v.all{it.code !in 0xD800..0xDFFF&&it.code>=0x20&&it.code !in 0xE000..0xF8FF}){"The $label contains an unmapped glyph."};return v}
    private fun putText(b:ByteArray,o:Int,v:String){val x=v.toByteArray(StandardCharsets.UTF_16LE);require(x.size<=24){"Converted text exceeds 12 characters."};x.copyInto(b,o)}
    private fun date(y:Int,m:Int,d:Int)=if(y==0&&m==0&&d==0)true else y in 0..99&&m in 1..12&&d in 1..31&&runCatching{java.time.LocalDate.of(2000+y,m,d)}.isSuccess
    private fun minExp(l:Int,g:Int):Long{if(l<=1)return 0;val n=l.toLong();val v=n*n*n;return when(g){0->v;1->when{l<=50->v*(100-n)/50;l<=68->v*(150-n)/100;l<=98->{val r=n%3;v*(1274+r*r-9*r-20*(n/3))/1000};else->v*(160-n)/100};2->when{l<=15->v*(24+(n+1)/3)/50;l<=35->v*(14+n)/50;else->v*(32+n/2)/50};3->maxOf(0L,6*v/5-15*n*n+100*n-140);4->4*v/5;5->5*v/4;else->error("Unsupported growth group.")}}
    private fun level(e:Long,g:Int):Int{var l=1;for(i in 2..100){if(e<minExp(i,g))break;l=i};return l}
    private fun encryptPb8(p:ByteArray):ByteArray{val ec=u32(p,0);val order=orders[((ec ushr 13).toInt() and 31)%24];val s=p.copyOf()
        for(block in 0..3){val d=8+(order[block]-'0')*0x50;p.copyInto(s,d,8+block*0x50,8+(block+1)*0x50)}
        xor(s,8,PB8_STORED_END,ec);xor(s,PB8_STORED_END,PB8_SIZE,ec);return s}
    private fun hex(b:ByteArray)=b.joinToString(" "){"%02X".format(it)}
    private fun xor(b:ByteArray,from:Int,end:Int,seed:Long){var state=seed;for(i in from until end step 2){state=(state*0x41C64E6DL+0x6073L) and 0xFFFFFFFFL;w16(b,i,u16(b,i) xor (state ushr 16).toInt())}}
    private fun copy(a:ByteArray,ao:Int,b:ByteArray,bo:Int,n:Int)=a.copyInto(b,bo,ao,ao+n)
    private fun byte(b:ByteArray,o:Int)=b[o].toInt() and 255
    private fun u16(b:ByteArray,o:Int)=byte(b,o) or (byte(b,o+1) shl 8)
    private fun u32(b:ByteArray,o:Int)=u16(b,o).toLong() or (u16(b,o+2).toLong() shl 16)
    private fun w16(b:ByteArray,o:Int,v:Int){b[o]=v.toByte();b[o+1]=(v ushr 8).toByte()}
    private fun w32(b:ByteArray,o:Int,v:Long){w16(b,o,v.toInt());w16(b,o+2,(v ushr 16).toInt())}
}
