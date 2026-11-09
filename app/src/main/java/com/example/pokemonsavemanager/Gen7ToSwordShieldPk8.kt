package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets

data class Gen7ToSwordShieldReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val effects: List<String>
)

object Gen7ToSwordShieldPk8 {
    private const val PK7_SIZE = 0xE8
    private const val PK8_SIZE = 0x158
    private const val PK8_END = 0x148
    private val orders = listOf("0123","0132","0213","0312","0231","0321","1023","1032",
        "2013","3012","2031","3021","1203","1302","2103","3102","2301","3201",
        "1230","1320","2130","3120","2310","3210")
    private val origins = setOf(1,2,3,4,5,7,8,10,11,12,15,20,21,22,23,24,25,26,27,
        30,31,32,33,35,36,37,38,39,40,41)

    fun review(record: ByteArray): Gen7ToSwordShieldReview {
        val s = plainPk7(record)
        val species=u16(s,8); val held=u16(s,0x0A); val ability=byte(s,0x14)
        val abilitySlot=byte(s,0x15) and 7; val markings=u16(s,0x16); val pid=u32(s,0x18)
        val nature=byte(s,0x1C); val form=byte(s,0x1D) ushr 3; val exp=u32(s,0x10)
        val iv32=u32(s,0x74); val egg=iv32 and 0x40000000L != 0L
        val nicknamed=iv32 and 0x80000000L != 0L; val hyper=byte(s,0xDE)
        val language=byte(s,0xE3); val origin=byte(s,0xDF); val ball=byte(s,0xDC)
        val metLevel=byte(s,0xDD) and 0x7F
        require(species in 1..807){"The source is not a supported Generation 7 species."}
        require(form==0){"Only verified default-form PK7 records can enter Sword/Shield in this bounded route."}
        require(!egg){"Egg PK7 records remain blocked from this PSE conversion."}
        require(held==0){"Held item #$held has no reviewed Gen 7 -> Sword/Shield mapping. Remove it in the source game first."}
        require(nature in 0..24){"The source nature is outside the native range."}
        require(ability in 1..267 && abilitySlot in setOf(1,2,4)){"The source ability ID or slot is outside the reviewed Sword/Shield range."}
        require(language==2){"This bounded route supports English PK7 text only; other glyph translations remain blocked."}
        require(origin in origins){"Origin game ID $origin has no reviewed PK7 -> PK8 mapping."}
        require(ball in 1..26){"Ball ID $ball is outside the reviewed Sword/Shield range."}
        require(metLevel in 1..100){"The source met level is outside 1-100."}
        require(date(byte(s,0xD1),byte(s,0xD2),byte(s,0xD3))){"The source egg date is invalid."}
        require(date(byte(s,0xD4),byte(s,0xD5),byte(s,0xD6))){"The source met date is invalid."}
        require(hyper and 0xC0 == 0){"The source Hyper Training field contains unsupported bits."}
        val ev=IntArray(6){byte(s,0x1E+it)}
        require(ev.all{it in 0..252} && ev.sum()<=510){"The source EVs exceed Sword/Shield limits."}
        val iv=IntArray(6){((iv32 ushr (it*5)) and 31).toInt()}
        val moves=IntArray(4){u16(s,0x5A+it*2)}
        val pp=IntArray(4){byte(s,0x62+it)}
        val ups=IntArray(4){byte(s,0x66+it)}
        for(i in 0..3){
            val move=moves[i]
            if(move==0) require(pp[i]==0 && ups[i]==0){"Empty move slot ${i+1} contains PP or PP-Up data."}
            else {
                require(SwordShieldMoveCatalog.choice(move)!=null){"Move #$move is not usable in Sword/Shield."}
                require(pp[i] in 0..SwordShieldMoveCatalog.maximumPp(move,ups[i])){"Move #$move has PP outside its Sword/Shield limit."}
            }
        }
        IntArray(4){u16(s,0x6A+it*2)}.filter{it!=0}.forEach{
            require(SwordShieldMoveCatalog.choice(it)!=null){"Relearn move #$it is not usable in Sword/Shield."}
        }
        val sourceNick=text(s,0x40,"nickname"); val ot=text(s,0xB0,"Original Trainer name")
        val ht=text(s,0x78,"Handling Trainer name")
        val nickname=if(nicknamed) sourceNick else SwordShieldSpeciesNames.name(species)
        require(nickname.isNotBlank()){"The Sword/Shield species name is unavailable."}
        val personal=SwordShieldPersonalCatalogue.profiles(species).filter{it.isDefault}
            .distinctBy{listOf(it.hp,it.attack,it.defense,it.speed,it.specialAttack,it.specialDefense,it.growth)}
            .singleOrNull()?:error("This species has no single verified default Sword/Shield profile.")
        require(exp<=minExp(100,personal.growth)){"The source EXP exceeds the reviewed level-100 value."}
        val level=level(exp,personal.growth)
        val effective=IntArray(6){i->if(hyper and (1 shl intArrayOf(0,1,2,5,3,4)[i])!=0)31 else iv[i]}
        val base=intArrayOf(personal.hp,personal.attack,personal.defense,personal.speed,personal.specialAttack,personal.specialDefense)
        fun core(i:Int)=(2*base[i]+effective[i]+ev[i]/4)*level/100
        fun adjusted(i:Int,v:Int):Int{val up=nature/5;val down=nature%5;return if(up==down)v else when(i){up->v*110/100;down->v*90/100;else->v}}
        val hp=if(species==292)1 else core(0)+level+10
        val atk=adjusted(0,core(1)+5);val def=adjusted(1,core(2)+5);val spe=adjusted(4,core(3)+5)
        val spa=adjusted(2,core(4)+5);val spd=adjusted(3,core(5)+5)

        val o=ByteArray(PK8_SIZE)
        w32(o,0,u32(s,0));w16(o,8,species);copy(s,0x0C,o,0x0C,8);w16(o,0x14,ability)
        o[0x16]=abilitySlot.toByte();w16(o,0x18,markings and 0x0FFF);w32(o,0x1C,pid)
        o[0x20]=nature.toByte();o[0x21]=nature.toByte();o[0x22]=(byte(s,0x1D) and 7).toByte()
        copy(s,0x1E,o,0x26,6);copy(s,0x24,o,0x2C,6);o[0x32]=s[0x2B]
        putText(o,0x58,nickname);copy(s,0x5A,o,0x72,16);copy(s,0x6A,o,0x82,8);w32(o,0x8C,iv32)
        putText(o,0xA8,ht);o[0xC2]=s[0x92];o[0xC4]=s[0x93];o[0xC8]=s[0xA2]
        o[0xDC]=s[0xAE];o[0xDD]=s[0xAF];o[0xDE]=s[0xDF];o[0xE2]=s[0xE3]
        copy(s,0x3C,o,0xE4,4);o[0xE8]=0xFF.toByte();putText(o,0xF8,ot);o[0x112]=s[0xCA]
        copy(s,0xD1,o,0x119,6);w16(o,0x120,u16(s,0xD8));w16(o,0x122,u16(s,0xDA))
        o[0x124]=s[0xDC];o[0x125]=s[0xDD];o[0x126]=s[0xDE];o[0x148]=level.toByte()
        w16(o,0x8A,hp);w16(o,0x14A,hp);w16(o,0x14C,atk);w16(o,0x14E,def)
        w16(o,0x150,spe);w16(o,0x152,spa);w16(o,0x154,spd)
        var sum=0;for(offset in 8 until PK8_END step 2)sum+=u16(o,offset);w16(o,6,sum and 0xFFFF)
        val native=encrypt(o)
        val p=(SwordShieldPokemonReadOnly.read(native) as? SwordShieldSlotRead.Pokemon)?.value
            ?:error("The converted PK8 did not reopen with a valid native checksum.")
        require(p.species==species&&p.pid==pid&&p.experience==exp&&p.cachedLevel==level&&p.maximumHp==hp&&
            p.cachedStats==listOf(atk,def,spe,spa,spd)){"The converted PK8 did not reopen with the reviewed identity and stats."}
        SwordShieldPseBankRestoreEditing.inspectBankRecord(native)

        val ribbons=s.copyOfRange(0x30,0x3A)
        val memories=intArrayOf(byte(s,0xA3),byte(s,0xA4),byte(s,0xA5),u16(s,0xA8),
            byte(s,0xCB),byte(s,0xCC),byte(s,0xCD),u16(s,0xCE),byte(s,0xD0))
        val effects=listOf(
            "Explicit Legalise: PSE PK7 -> PK8 conversion; not an official HOME transfer, in-game trade, or verified provenance.",
            "Species, form 0, PID, EC, TID/SID, EXP, IVs, EVs, nature, gender, shiny state, friendship, Pokerus, contest values, origin, dates, locations, ball, fateful flag, Hyper Training, moves, PP, PP Ups and relearn moves are retained.",
            if(nicknamed)"Nickname retained as English UTF-16 text: $nickname." else "Non-nicknamed text resets to the English Sword/Shield species name: $nickname.",
            "OT text, HT text and current-handler state are retained; Gen 7 text trash bytes are normalized.",
            "Held item remains empty. Ability ID $ability and slot $abilitySlot are retained; encounter and moveset legality are not certified.",
            if(markings and 0xF000==0)"All 12 supported marking bits are retained." else "High marking bits 0x${(markings and 0xF000).toString(16).uppercase()} are cleared; low 12 bits are retained.",
            if(ribbons.all{it==0.toByte()})"No Gen 7 ribbon bits require omission." else "Ribbon/memory-ribbon bytes ${ribbons.joinToString(" "){"%02X".format(it)}} are cleared; this bounded adapter has no reviewed PK8 ribbon mapping.",
            "Resort, Super Training, distributed training, affection, geography, country/region/console metadata and trainer memories are cleared. Source memory/affection values: ${memories.joinToString("/")}.",
            "PK8 defaults: stat nature=nature; Dynamax 0; Gigantamax false; sociability, size scalars, Poké Jobs, TR flags, HOME tracker and battle version zero; affixed ribbon none; HT language unset.",
            "Cached level $level and stats are rebuilt from retained values and the verified Sword/Shield profile; current HP is full $hp.",
            "The PK7 Bank entry and Sword/Shield source remain unchanged. One reviewed empty native slot in a new copy is written with recovery and reopen verification."
        )
        return Gen7ToSwordShieldReview(native,species,sourceNick,effects)
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
    private fun date(y:Int,m:Int,d:Int)=if(y==0&&m==0&&d==0)true else y in 0..99&&m in 1..12&&d in 1..31&&runCatching{java.time.LocalDate.of(2000+y,m,d)}.isSuccess
    private fun minExp(l:Int,g:Int):Long{if(l<=1)return 0;val n=l.toLong();val c=n*n*n;return when(g){0->c;1->when{l<=50->c*(100-n)/50;l<=68->c*(150-n)/100;l<=98->{val r=n%3;c*(1274+r*r-9*r-20*(n/3))/1000};else->c*(160-n)/100};2->when{l<=15->c*(24+(n+1)/3)/50;l<=35->c*(14+n)/50;else->c*(32+n/2)/50};3->maxOf(0L,6*c/5-15*n*n+100*n-140);4->4*c/5;5->5*c/4;else->error("Unsupported growth group.")}}
    private fun level(e:Long,g:Int):Int{var l=1;for(i in 2..100){if(e<minExp(i,g))break;l=i};return l}
    private fun encrypt(p:ByteArray):ByteArray{val ec=u32(p,0);val order=orders[((ec ushr 13).toInt() and 31)%24];val s=p.copyOf()
        for(block in 0..3){val d=8+(order[block]-'0')*0x50;p.copyInto(s,d,8+block*0x50,8+(block+1)*0x50)}
        xor(s,8,PK8_END,ec);xor(s,PK8_END,PK8_SIZE,ec);return s}
    private fun xor(b:ByteArray,from:Int,end:Int,seed:Long){var state=seed;for(i in from until end step 2){state=(state*0x41C64E6DL+0x6073L) and 0xFFFFFFFFL;w16(b,i,u16(b,i) xor (state ushr 16).toInt())}}
    private fun copy(a:ByteArray,ao:Int,b:ByteArray,bo:Int,n:Int)=a.copyInto(b,bo,ao,ao+n)
    private fun byte(b:ByteArray,o:Int)=b[o].toInt() and 255
    private fun u16(b:ByteArray,o:Int)=byte(b,o) or (byte(b,o+1) shl 8)
    private fun u32(b:ByteArray,o:Int)=u16(b,o).toLong() or (u16(b,o+2).toLong() shl 16)
    private fun w16(b:ByteArray,o:Int,v:Int){b[o]=v.toByte();b[o+1]=(v ushr 8).toByte()}
    private fun w32(b:ByteArray,o:Int,v:Long){w16(b,o,v.toInt());w16(b,o+2,(v ushr 16).toInt())}
}
