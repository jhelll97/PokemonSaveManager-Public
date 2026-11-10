package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets

data class Gen7ToScarletVioletReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val effects: List<String>
)

data class Gen7ScarletVioletPk7Source(
    val decryptedBytes: ByteArray,
    val species: Int,
    val gender: Int,
    val abilityId: Int,
    val abilityNumber: Int
)

object Gen7ToScarletVioletPk9 {
    private const val PK7_SIZE = 0xE8
    private const val PK9_SIZE = 0x158
    private const val STORED_END = 0x148
    private val orders = listOf("0123","0132","0213","0312","0231","0321","1023","1032",
        "2013","3012","2031","3021","1203","1302","2103","3102","2301","3201",
        "1230","1320","2130","3120","2310","3210")
    private val origins = setOf(1,2,3,4,5,7,8,10,11,12,15,20,21,22,23,24,25,26,27,
        30,31,32,33,34,35,36,37,38,39,40,41,42,43)

    fun inspectSource(record: ByteArray): Gen7ScarletVioletPk7Source {
        val s = plainPk7(record)
        val flags = byte(s,0x1D)
        return Gen7ScarletVioletPk7Source(
            s, u16(s,8), (flags ushr 1) and 3, byte(s,0x14), byte(s,0x15) and 7
        )
    }

    fun review(record: ByteArray): Gen7ToScarletVioletReview =
        reviewInternal(record, null, null)

    fun review(
        record: ByteArray,
        destinationTrainer: ScarletVioletTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): Gen7ToScarletVioletReview = reviewInternal(record, destinationTrainer, metadata)

    private fun reviewInternal(
        record: ByteArray,
        destinationTrainer: ScarletVioletTrainerRead?,
        metadata: Gen1ToSwordShieldMetadata?
    ): Gen7ToScarletVioletReview {
        val source = inspectSource(record)
        val s = source.decryptedBytes
        val species=source.species; val held=u16(s,0x0A); val ability=source.abilityId
        val abilitySlot=source.abilityNumber; val markings=u16(s,0x16); val pid=u32(s,0x18)
        val nature=byte(s,0x1C); val sourceFlags=byte(s,0x1D); val form=sourceFlags ushr 3
        val gender=source.gender; val exp=u32(s,0x10); val iv32=u32(s,0x74)
        val egg=iv32 and 0x40000000L != 0L; val nicknamed=iv32 and 0x80000000L != 0L
        val hyper=byte(s,0xDE); val language=byte(s,0xE3); val origin=byte(s,0xDF)
        val ball=byte(s,0xDC); val metLevel=byte(s,0xDD) and 0x7F; val currentHandler=byte(s,0x93)
        require(species in 1..807){"The source is not a supported Generation 7 species."}
        require(form==0){"Only verified default-form PK7 records can enter Scarlet/Violet in this bounded route."}
        require(gender in 0..2){"The source gender field is outside the reviewed PK9 range."}
        if (destinationTrainer != null || metadata != null) {
            require(destinationTrainer != null && metadata != null) {
                "Destination trainer and documented species metadata must be reviewed together."
            }
            require(destinationTrainer.languageId==2){"This bounded route currently requires an English Scarlet/Violet destination trainer."}
            require(metadata.baseFriendship in 0..255 && metadata.genderRate in -1..8){"The documented species friendship or gender mapping is invalid."}
            require(metadata.abilityId==ability && metadata.abilityNumber==abilitySlot){"The documented Scarlet/Violet ability does not exactly match PK7 ability #$ability in slot $abilitySlot."}
            require(destinationGender(gender,metadata.genderRate)){"The PK7 gender does not match the documented Scarlet/Violet species ratio."}
        }
        require(!egg){"Egg PK7 records remain blocked from this PSE conversion."}
        require(held==0){"Held item #$held has no reviewed Gen 7 -> Scarlet/Violet mapping. Remove it in the source game first."}
        require(nature in 0..24){"The source nature is outside the native range."}
        require(ability in 1..267 && abilitySlot in setOf(1,2,4)){
            "The source ability ID or slot is outside the reviewed PK9 storage range."
        }
        require(language==2){"This bounded route supports English PK7 text only; other glyph translations remain blocked."}
        require(origin in origins){"Origin game ID $origin has no reviewed PK7 -> PK9 mapping."}
        require(ball in 1..26){"Ball ID $ball is outside the reviewed Generation 7 range."}
        require(metLevel in 1..100){"The source met level is outside 1-100."}
        require(date(byte(s,0xD1),byte(s,0xD2),byte(s,0xD3))){"The source egg date is invalid."}
        require(date(byte(s,0xD4),byte(s,0xD5),byte(s,0xD6))){"The source met date is invalid."}
        require(hyper and 0xC0 == 0){"The source Hyper Training field contains unsupported bits."}
        require(currentHandler in 0..1){"The source current-handler field is outside the reviewed PK9 range."}
        val ev=IntArray(6){byte(s,0x1E+it)}
        require(ev.all{it in 0..252} && ev.sum()<=510){"The source EVs exceed Scarlet/Violet limits."}
        val iv=IntArray(6){((iv32 ushr (it*5)) and 31).toInt()}
        val moves=IntArray(4){u16(s,0x5A+it*2)}
        val pp=IntArray(4){byte(s,0x62+it)}
        val ups=IntArray(4){byte(s,0x66+it)}
        require(ups.all{it in 0..3}){"A PK7 move slot contains unsupported PP-Up bits."}
        for(i in 0..3){
            val move=moves[i]
            if(move==0) require(pp[i]==0 && ups[i]==0){"Empty move slot ${i+1} contains PP or PP-Up data."}
            else {
                require(ups[i] in 0..3){"Move #$move has an invalid PP-Up count."}
                require(ScarletVioletMoveCatalog.choice(move)!=null){"Move #$move is not usable in Scarlet/Violet."}
                require(pp[i] in 0..ScarletVioletMoveCatalog.maximumPp(move,ups[i])){
                    "Move #$move has PP outside its Scarlet/Violet limit."
                }
            }
        }
        IntArray(4){u16(s,0x6A+it*2)}.filter{it!=0}.forEach{
            require(ScarletVioletMoveCatalog.choice(it)!=null){"Relearn move #$it is not usable in Scarlet/Violet."}
        }
        val sourceNick=text(s,0x40,"nickname"); val ot=text(s,0xB0,"Original Trainer name")
        val ht=text(s,0x78,"Handling Trainer name")
        val personal=ScarletVioletPersonalCatalogue.profile(species,0)
            ?:error("Species #$species form 0 has no verified Scarlet/Violet profile.")
        val nickname=if(nicknamed) sourceNick else ScarletVioletSpeciesNames.name(species)
        require(nickname.isNotBlank()){"The Scarlet/Violet species name is unavailable."}
        require(exp<=minExp(100,personal.growth)){"The source EXP exceeds the reviewed level-100 value."}
        val level=level(exp,personal.growth)
        val effective=IntArray(6){i->if(hyper and (1 shl intArrayOf(0,1,2,5,3,4)[i])!=0)31 else iv[i]}
        val base=intArrayOf(personal.hp,personal.attack,personal.defense,personal.speed,personal.specialAttack,personal.specialDefense)
        fun core(i:Int)=(2*base[i]+effective[i]+ev[i]/4)*level/100
        fun adjusted(i:Int,v:Int):Int{val up=nature/5;val down=nature%5;return if(up==down)v else when(i){up->v*110/100;down->v*90/100;else->v}}
        val hp=if(species==292)1 else core(0)+level+10
        val atk=adjusted(0,core(1)+5);val def=adjusted(1,core(2)+5);val spe=adjusted(2,core(3)+5)
        val spa=adjusted(3,core(4)+5);val spd=adjusted(4,core(5)+5)

        val o=ByteArray(PK9_SIZE)
        w32(o,0,u32(s,0));w16(o,8,species);copy(s,0x0C,o,0x0C,8);w16(o,0x14,ability)
        o[0x16]=abilitySlot.toByte();w16(o,0x18,markings and 0x0FFF);w32(o,0x1C,pid)
        o[0x20]=nature.toByte();o[0x21]=nature.toByte();o[0x22]=(sourceFlags and 7).toByte();o[0x24]=0
        copy(s,0x1E,o,0x26,6);copy(s,0x24,o,0x2C,6);o[0x32]=s[0x2B]
        putText(o,0x58,nickname);copy(s,0x5A,o,0x72,16);copy(s,0x6A,o,0x82,8)
        w16(o,0x8A,hp);w32(o,0x8C,iv32);o[0x94]=0;o[0x95]=ScarletVioletTeraTypes.OVERRIDE_NONE.toByte()
        putText(o,0xA8,ht);o[0xC2]=s[0x92];o[0xC4]=s[0x93];o[0xC8]=s[0xA2]
        o[0xCE]=s[0xDF];o[0xD4]=0xFF.toByte();o[0xD5]=s[0xE3]
        putText(o,0xF8,ot);o[0x112]=s[0xCA];copy(s,0xD1,o,0x119,6)
        o[0x11F]=metLevel.toByte();w16(o,0x120,u16(s,0xD8));w16(o,0x122,u16(s,0xDA))
        o[0x124]=s[0xDC];o[0x125]=s[0xDD];o[0x126]=s[0xDE]
        o[0x148]=level.toByte();w16(o,0x14A,hp);w16(o,0x14C,atk);w16(o,0x14E,def)
        w16(o,0x150,spe);w16(o,0x152,spa);w16(o,0x154,spd)
        var sum=0;for(offset in 8 until STORED_END step 2)sum+=u16(o,offset);w16(o,6,sum and 0xFFFF)
        val native=encrypt(o)
        val p=(ScarletVioletPokemonReadOnly.read(native) as? ScarletVioletSlotRead.Pokemon)?.value
            ?:error("The converted PK9 did not reopen with a valid native checksum.")
        require(p.species==species&&p.form==0&&p.gender==gender&&
            p.fatefulEncounter==((sourceFlags and 1)!=0)&&p.pid==pid&&p.experience==exp&&
            p.cachedLevel==level&&p.maximumHp==hp&&p.currentHp==hp&&
            p.cachedStats==listOf(atk,def,spe,spa,spd)&&
            p.teraTypeOriginal==0&&p.teraTypeOverride==ScarletVioletTeraTypes.OVERRIDE_NONE){
            "The converted PK9 did not reopen with the reviewed identity, stats, and Tera defaults."
        }
        ScarletVioletPseBankRestoreEditing.inspectBankRecord(native)

        val ribbons=s.copyOfRange(0x30,0x3A)
        val memories=intArrayOf(byte(s,0xA3),byte(s,0xA4),byte(s,0xA5),u16(s,0xA8),
            byte(s,0xCB),byte(s,0xCC),byte(s,0xCD),u16(s,0xCE),byte(s,0xD0))
        val omittedMetadata=listOf(
            "0x2A="+hex(s,0x2A,0x2B), "0x2C-0x2F="+hex(s,0x2C,0x30),
            "0x3A-0x3F="+hex(s,0x3A,0x40), "0x58-0x59="+hex(s,0x58,0x5A),
            "0x72-0x73="+hex(s,0x72,0x74), "0x90-0x91="+hex(s,0x90,0x92),
            "0x94-0xA1="+hex(s,0x94,0xA2), "0xA3-0xAD="+hex(s,0xA3,0xAE),
            "0xAE-0xAF="+hex(s,0xAE,0xB0), "0xC8-0xC9="+hex(s,0xC8,0xCA),
            "0xCB-0xD0="+hex(s,0xCB,0xD1), "0xD7="+hex(s,0xD7,0xD8),
            "0xE0-0xE2="+hex(s,0xE0,0xE3), "0xE4-0xE7="+hex(s,0xE4,0xE8)
        ).joinToString(", ")
        val effects=listOf(
            "Explicit Legalise: PSE Generation 7 PK7 -> Scarlet/Violet PK9 conversion; not an official HOME transfer, in-game trade, or verified provenance.",
            "Species, form 0, PID, EC, TID/SID, EXP, IVs, EVs, nature, gender, shiny state, friendship, Pokerus, contest values, origin, dates, locations, ball, fateful flag, Hyper Training, moves, PP, PP Ups and relearn moves are retained.",
            if(nicknamed)"Nickname retained as English UTF-16 text: $nickname." else "Non-nicknamed text resets to the English Scarlet/Violet species name: $nickname.",
            "OT text, HT text and current-handler state are retained; Generation 7 text trash bytes are normalized.",
            if(metadata==null)"Held item remains empty. Ability ID $ability and slot $abilitySlot are retained as PK9 storage data; encounter, ability and moveset legality are not certified." else "Held item remains empty. Documented Scarlet/Violet ability ID $ability and slot $abilitySlot are retained as PK9 storage data; the source gender matches the documented destination ratio; encounter, ability and moveset legality are not certified.",
            "Original Tera Type is explicitly legalised to Normal (0) with no override (19). This deterministic default is disclosed and is not a claim about the species' natural Tera Type.",
            "Obedience Level becomes the retained met level $metLevel. Current HP is rebuilt full at $hp and cached level/stats are rebuilt from retained permanent values and the verified Scarlet/Violet profile.",
            if(markings and 0xF000==0)"All 12 supported marking bits are retained." else "High marking bits 0x${(markings and 0xF000).toString(16).uppercase()} are cleared; low 12 bits are retained.",
            if(ribbons.all{it==0.toByte()})"No Generation 7 ribbon bits require omission." else "Ribbon/memory-ribbon bytes ${ribbons.joinToString(" "){"%02X".format(it)}} are cleared; this bounded adapter has no reviewed PK7 -> PK9 ribbon mapping.",
            "Resort, Super Training, distributed training, affection, geography, country/region/console metadata and trainer memories are cleared. Source memory/affection values: ${memories.joinToString("/")}.",
            "Unmapped PK7 metadata bytes are omitted only by this explicit Legalise choice: $omittedMetadata.",
            "PK9 defaults: stat nature=nature; size scalars and scale, tracker, sociability, fullness/enjoyment, battle state/version, form argument, TM flags and PK9-only metadata zero; affixed ribbon none.",
            "The PK7 Bank entry and Scarlet/Violet source remain unchanged. One user-chosen empty native PK9 slot in a new copy is written with required native caught registration, retained recovery, persisted reopen verification, and two-sided rollback."
        )
        return Gen7ToScarletVioletReview(native,species,sourceNick,effects)
    }

    private fun plainPk7(source:ByteArray):ByteArray{
        require(source.size==PK7_SIZE){"A complete 232-byte PK7 record is required."}
        require(u16(source,4)==0){"The PK7 sanity field is not zero."}
        fun valid(b:ByteArray):Boolean{var sum=0;for(i in 8 until PK7_SIZE step 2)sum+=u16(b,i);return (sum and 0xFFFF)==u16(source,6)&&u16(b,8) in 1..807}
        if(valid(source))return source.copyOf()
        val ec=u32(source,0);val decoded=source.copyOf();xor(decoded,8,PK7_SIZE,ec)
        val order=orders[((ec ushr 13).toInt() and 31)%24];val plain=decoded.copyOf()
        for(block in 0..3){val from=8+(order[block]-'0')*0x38;decoded.copyInto(plain,8+block*0x38,from,from+0x38)}
        require(valid(plain)){"The PK7 checksum failed."};return plain
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
        xor(s,8,STORED_END,ec);xor(s,STORED_END,PK9_SIZE,ec);return s}
    private fun xor(b:ByteArray,from:Int,end:Int,seed:Long){var state=seed;for(i in from until end step 2){state=(state*0x41C64E6DL+0x6073L) and 0xFFFFFFFFL;w16(b,i,u16(b,i) xor (state ushr 16).toInt())}}
    private fun copy(a:ByteArray,ao:Int,b:ByteArray,bo:Int,n:Int)=a.copyInto(b,bo,ao,ao+n)
    private fun byte(b:ByteArray,o:Int)=b[o].toInt() and 255
    private fun u16(b:ByteArray,o:Int)=byte(b,o) or (byte(b,o+1) shl 8)
    private fun u32(b:ByteArray,o:Int)=u16(b,o).toLong() or (u16(b,o+2).toLong() shl 16)
    private fun w16(b:ByteArray,o:Int,v:Int){b[o]=v.toByte();b[o+1]=(v ushr 8).toByte()}
    private fun w32(b:ByteArray,o:Int,v:Long){w16(b,o,v.toInt());w16(b,o+2,(v ushr 16).toInt())}
}
