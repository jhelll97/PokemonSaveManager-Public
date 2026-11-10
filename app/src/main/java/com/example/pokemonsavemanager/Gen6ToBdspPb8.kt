package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets

data class Gen6ToBdspReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val effects: List<String>
)

object Gen6ToBdspPb8 {
    private const val PB8_SIZE = 0x158
    private const val PB8_END = 0x148
    private val orders = listOf("0123","0132","0213","0312","0231","0321","1023","1032",
        "2013","3012","2031","3021","1203","1302","2103","3102","2301","3201",
        "1230","1320","2130","3120","2310","3210")
    private val origins = setOf(1,2,3,4,5,7,8,10,11,12,15,20,21,22,23,24,25,26,27)

    fun inspectSource(record: ByteArray): Gen6Pk6Record = Gen6Pk6Adapter.decode(record)

    fun review(
        record: ByteArray,
        destinationTrainer: BdspTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): Gen6ToBdspReview {
        val source = inspectSource(record)
        val s = source.decryptedBytes
        val species=u16(s,8); val held=u16(s,0x0A); val ability=byte(s,0x14)
        val abilitySlot=byte(s,0x15) and 7; val markings=byte(s,0x16); val pid=u32(s,0x18)
        val nature=byte(s,0x1C); val gender=(byte(s,0x1D) ushr 1) and 3
        val form=byte(s,0x1D) ushr 3; val exp=u32(s,0x10)
        val iv32=u32(s,0x74); val egg=iv32 and 0x40000000L != 0L
        val nicknamed=iv32 and 0x80000000L != 0L
        val language=byte(s,0x17); val origin=byte(s,0xDF); val ball=byte(s,0xDC)
        val metLevel=byte(s,0xDD) and 0x7F
        require(species in 1..493){"The source species is outside BDSP's reviewed native range."}
        require(form==0){"Only verified default-form PK6 records can enter BDSP in this bounded route."}
        require(!egg){"Egg PK6 records remain blocked from this PSE conversion."}
        require(held==0){"Held item #$held has no reviewed Gen 6 -> BDSP mapping. Remove it in the source game first."}
        require(nature in 0..24){"The source nature is outside the native range."}
        require(ability in 1..191 && abilitySlot in setOf(1,2,4)){"The source ability ID or slot is outside the reviewed Generation 6 range."}
        require(destinationTrainer.languageId==2){"This bounded route currently requires an English BDSP destination trainer."}
        require(language==2){"This bounded route supports English PK6 text only; other glyph translations remain blocked."}
        require(metadata.baseFriendship in 0..255 && metadata.genderRate in -1..8){"The documented species friendship or gender mapping is invalid."}
        require(metadata.abilityId==ability && metadata.abilityNumber==abilitySlot){"The documented BDSP ability does not exactly match PK6 ability #$ability in slot $abilitySlot."}
        require(destinationGender(gender,metadata.genderRate)){"The PK6 gender does not match the documented BDSP species ratio."}
        require(origin in origins){"Origin game ID $origin has no reviewed PK6 -> PB8 mapping."}
        require(ball in 1..25){"Ball ID $ball has no reviewed Generation 6 -> BDSP mapping."}
        require(metLevel in 1..100){"The source met level is outside 1-100."}
        require(date(byte(s,0xD1),byte(s,0xD2),byte(s,0xD3))){"The source egg date is invalid."}
        require(date(byte(s,0xD4),byte(s,0xD5),byte(s,0xD6))){"The source met date is invalid."}
        val ev=IntArray(6){byte(s,0x1E+it)}
        require(ev.all{it in 0..252} && ev.sum()<=510){"The source EVs exceed BDSP limits."}
        val iv=IntArray(6){((iv32 ushr (it*5)) and 31).toInt()}
        val moves=IntArray(4){u16(s,0x5A+it*2)}
        val pp=IntArray(4){byte(s,0x62+it)}
        val ups=IntArray(4){byte(s,0x66+it)}
        require(ups.all{it in 0..3}){"A PK6 move slot contains unsupported PP-Up bits."}
        for(i in 0..3){
            val move=moves[i]
            if(move==0) require(pp[i]==0 && ups[i]==0){"Empty move slot ${i+1} contains PP or PP-Up data."}
            else {
                require(BdspMoveCatalog.choice(move)!=null){"Move #$move is not usable in BDSP."}
                require(pp[i] in 0..BdspMoveCatalog.maximumPp(move,ups[i])){"Move #$move has PP outside its BDSP limit."}
            }
        }
        IntArray(4){u16(s,0x6A+it*2)}.filter{it!=0}.forEach{
            require(BdspMoveCatalog.choice(it)!=null){"Relearn move #$it is not usable in BDSP."}
        }
        val sourceNick=text(s,0x40,"nickname"); val ot=text(s,0xB0,"Original Trainer name")
        val ht=text(s,0x78,"Handling Trainer name")
        require(byte(s,0x92) in 0..1 && byte(s,0x93) in 0..1){"The PK6 handling-trainer gender or current-handler field is outside the reviewed PB8 range."}
        val nickname=if(nicknamed) sourceNick else SwordShieldSpeciesNames.name(species)
        require(nickname.isNotBlank()){"The BDSP species name is unavailable."}
        val personal=BdspPersonalCatalogue.profiles(species,0)
            .distinctBy{listOf(it.hp,it.attack,it.defense,it.speed,it.specialAttack,it.specialDefense,it.growth)}
            .singleOrNull()?:error("This species has no single verified default BDSP profile.")
        require(exp<=minExp(100,personal.growth)){"The source EXP exceeds the reviewed level-100 value."}
        val level=level(exp,personal.growth)
        val effective=iv
        val base=intArrayOf(personal.hp,personal.attack,personal.defense,personal.speed,personal.specialAttack,personal.specialDefense)
        fun core(i:Int)=(2*base[i]+effective[i]+ev[i]/4)*level/100
        fun adjusted(i:Int,v:Int):Int{val up=nature/5;val down=nature%5;return if(up==down)v else when(i){up->v*110/100;down->v*90/100;else->v}}
        val hp=if(species==292)1 else core(0)+level+10
        val atk=adjusted(0,core(1)+5);val def=adjusted(1,core(2)+5);val spe=adjusted(4,core(3)+5)
        val spa=adjusted(2,core(4)+5);val spd=adjusted(3,core(5)+5)

        val o=ByteArray(PB8_SIZE)
        w32(o,0,u32(s,0));w16(o,8,species);copy(s,0x0C,o,0x0C,8);w16(o,0x14,ability)
        o[0x16]=abilitySlot.toByte();w16(o,0x18,0);w32(o,0x1C,pid)
        val sourceFlags=byte(s,0x1D);val sourceGender=(sourceFlags ushr 1) and 3
        require(sourceGender in 0..2){"The source gender field is outside the reviewed PB8 range."}
        o[0x20]=nature.toByte();o[0x21]=nature.toByte()
        o[0x22]=((sourceFlags and 1) or ((sourceFlags and 6) shl 1)).toByte()
        copy(s,0x1E,o,0x26,6);copy(s,0x24,o,0x2C,6);o[0x32]=s[0x2B]
        putText(o,0x58,nickname);copy(s,0x5A,o,0x72,16);copy(s,0x6A,o,0x82,8);w32(o,0x8C,iv32)
        putText(o,0xA8,ht);o[0xC2]=s[0x92];o[0xC4]=s[0x93];o[0xC8]=s[0xA2]
        o[0xDC]=0;o[0xDD]=0;o[0xDE]=s[0xDF];o[0xE2]=s[0x17]
        w32(o,0xE4,0);o[0xE8]=0xFF.toByte();putText(o,0xF8,ot);o[0x112]=s[0xCA]
        copy(s,0xD1,o,0x119,6);w16(o,0x120,u16(s,0xD8));w16(o,0x122,u16(s,0xDA))
        o[0x124]=s[0xDC];o[0x125]=s[0xDD];o[0x126]=0;o[0x148]=level.toByte()
        w16(o,0x8A,hp);w16(o,0x14A,hp);w16(o,0x14C,atk);w16(o,0x14E,def)
        w16(o,0x150,spe);w16(o,0x152,spa);w16(o,0x154,spd)
        var sum=0;for(offset in 8 until PB8_END step 2)sum+=u16(o,offset);w16(o,6,sum and 0xFFFF)
        val native=encrypt(o)
        val p=(BdspPokemonReadOnly.read(native) as? BdspSlotRead.Pokemon)?.value
            ?:error("The converted PB8 did not reopen with a valid native checksum.")
        require(p.species==species&&p.form==0&&p.gender==sourceGender&&
            p.fatefulEncounter==((sourceFlags and 1)!=0)&&p.pid==pid&&p.experience==exp&&
            p.cachedLevel==level&&p.maximumHp==hp&&p.cachedStats==listOf(atk,def,spe,spa,spd)) {
            "The converted PB8 did not reopen with the reviewed identity, gender, flags, and stats."
        }
        BdspPseBankRestoreEditing.inspectBankRecord(native)

        val ribbons=s.copyOfRange(0x30,0x3A)
        val memories=intArrayOf(byte(s,0xA3),byte(s,0xA4),byte(s,0xA5),u16(s,0xA8),
            byte(s,0xCB),byte(s,0xCC),byte(s,0xCD),u16(s,0xCE),byte(s,0xD0))
        val omittedMetadata=listOf(
            "0x2A="+hex(s,0x2A,0x2B), "0x2C-0x3F="+hex(s,0x2C,0x40),
            "0x72-0x73="+hex(s,0x72,0x74), "0x90-0x91="+hex(s,0x90,0x92),
            "0x94-0xA1="+hex(s,0x94,0xA2), "0xA3-0xAF="+hex(s,0xA3,0xB0),
            "0xCB-0xD0="+hex(s,0xCB,0xD1), "0xD7="+hex(s,0xD7,0xD8),
            "0xDE="+hex(s,0xDE,0xDF), "0xE0-0xE7="+hex(s,0xE0,0xE8)
        ).joinToString(", ")
        val effects=listOf(
            "Explicit Legalise: PSE PK6 -> PB8 conversion; not an official HOME transfer, in-game trade, or verified provenance.",
            "Species, form 0, PID, EC, TID/SID, EXP, IVs, EVs, nature, gender, shiny state, friendship, Pokerus, contest values, origin, dates, locations, ball, fateful flag, moves, PP, PP Ups and relearn moves are retained.",
            if(nicknamed)"Nickname retained as English UTF-16 text: $nickname." else "Non-nicknamed text resets to the English BDSP species name: $nickname.",
            "OT text, HT text and current-handler state are retained; Gen 6 text trash bytes are normalized.",
            "Held item remains empty. Documented BDSP ability ID $ability and slot $abilitySlot are retained; encounter and moveset legality are not certified.",
            if(markings==0)"No Generation 6 markings require omission." else "Generation 6 marking bits 0x${markings.toString(16).uppercase()} are cleared because this adapter has no reviewed PK6 marking-to-PB8 mapping.",
            if(ribbons.all{it==0.toByte()})"No Gen 6 ribbon bits require omission." else "Ribbon/memory-ribbon bytes ${ribbons.joinToString(" "){"%02X".format(it)}} are cleared; this bounded adapter has no reviewed PB8 ribbon mapping.",
            "Resort, Super Training, distributed training, affection, geography, country/region/console metadata and trainer memories are cleared. Source memory/affection values: ${memories.joinToString("/")}.",
            "Unmapped PK6 metadata bytes are omitted only by this explicit Legalise choice: $omittedMetadata.",
            "PB8 defaults: stat nature=nature; Hyper Training none; favorite clear; size scalars, battle version, HOME tracker and unused native fields zero; affixed ribbon none; HT language unset.",
            "Cached level $level and stats are rebuilt from retained values and the verified BDSP profile; current HP is full $hp.",
            "The PK6 Bank entry and BDSP source remain unchanged. One user-chosen empty native PB8 slot in a new copy is written with retained recovery, persisted reopen verification, and two-sided rollback."
        )
        return Gen6ToBdspReview(native,species,sourceNick,effects)
    }

    private fun text(b:ByteArray,o:Int,label:String):String{var end=o;while(end<o+24&&u16(b,end)!=0)end+=2
        val v=String(b,o,end-o,StandardCharsets.UTF_16LE)
        require(v.length<=12&&v.all{it.code !in 0xD800..0xDFFF&&it.code>=0x20&&it.code !in 0xE000..0xF8FF}){"The $label contains an unmapped glyph."};return v}
    private fun putText(b:ByteArray,o:Int,v:String){val x=v.toByteArray(StandardCharsets.UTF_16LE);require(x.size<=24){"Converted text exceeds 12 characters."};x.copyInto(b,o)}
    private fun hex(b:ByteArray,from:Int,end:Int)=b.copyOfRange(from,end).joinToString(""){"%02X".format(it)}
    private fun destinationGender(gender:Int,rate:Int)=when{rate<0->gender==2;rate==0->gender==0;rate>=8->gender==1;else->gender in 0..1}
    private fun date(y:Int,m:Int,d:Int)=if(y==0&&m==0&&d==0)true else y in 0..99&&m in 1..12&&d in 1..31&&runCatching{java.time.LocalDate.of(2000+y,m,d)}.isSuccess
    private fun minExp(l:Int,g:Int):Long{if(l<=1)return 0;val n=l.toLong();val c=n*n*n;return when(g){0->c;1->when{l<=50->c*(100-n)/50;l<=68->c*(150-n)/100;l<=98->{val r=n%3;c*(1274+r*r-9*r-20*(n/3))/1000};else->c*(160-n)/100};2->when{l<=15->c*(24+(n+1)/3)/50;l<=35->c*(14+n)/50;else->c*(32+n/2)/50};3->maxOf(0L,6*c/5-15*n*n+100*n-140);4->4*c/5;5->5*c/4;else->error("Unsupported growth group.")}}
    private fun level(e:Long,g:Int):Int{var l=1;for(i in 2..100){if(e<minExp(i,g))break;l=i};return l}
    private fun encrypt(p:ByteArray):ByteArray{val ec=u32(p,0);val order=orders[((ec ushr 13).toInt() and 31)%24];val s=p.copyOf()
        for(block in 0..3){val d=8+(order[block]-'0')*0x50;p.copyInto(s,d,8+block*0x50,8+(block+1)*0x50)}
        xor(s,8,PB8_END,ec);xor(s,PB8_END,PB8_SIZE,ec);return s}
    private fun xor(b:ByteArray,from:Int,end:Int,seed:Long){var state=seed;for(i in from until end step 2){state=(state*0x41C64E6DL+0x6073L) and 0xFFFFFFFFL;w16(b,i,u16(b,i) xor (state ushr 16).toInt())}}
    private fun copy(a:ByteArray,ao:Int,b:ByteArray,bo:Int,n:Int)=a.copyInto(b,bo,ao,ao+n)
    private fun byte(b:ByteArray,o:Int)=b[o].toInt() and 255
    private fun u16(b:ByteArray,o:Int)=byte(b,o) or (byte(b,o+1) shl 8)
    private fun u32(b:ByteArray,o:Int)=u16(b,o).toLong() or (u16(b,o+2).toLong() shl 16)
    private fun w16(b:ByteArray,o:Int,v:Int){b[o]=v.toByte();b[o+1]=(v ushr 8).toByte()}
    private fun w32(b:ByteArray,o:Int,v:Long){w16(b,o,v.toInt());w16(b,o+2,(v ushr 16).toInt())}
}
