package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets
import kotlin.math.floor
import kotlin.math.sqrt

data class Gen7ToLegendsArceusReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val effects: List<String>
)

data class Gen7LegendsArceusPk7Source(
    val decryptedBytes: ByteArray,
    val species: Int,
    val gender: Int,
    val abilityId: Int,
    val abilityNumber: Int
)

object Gen7ToLegendsArceusPa8 {
    private const val PK7_SIZE = 0xE8
    private const val PA8_SIZE = 0x168
    private val orders = listOf("0123","0132","0213","0312","0231","0321","1023","1032",
        "2013","3012","2031","3021","1203","1302","2103","3102","2301","3201",
        "1230","1320","2130","3120","2310","3210")
    private val origins = setOf(1,2,3,4,5,7,8,10,11,12,15,20,21,22,23,24,25,26,27,
        30,31,32,33,35,36,37,38,39,40,41)
    private val unavailableDefaultForms = setOf(58,59,100,101,157,211,503,549,550,570,571,628,705,706,713,724)

    fun inspectSource(record: ByteArray): Gen7LegendsArceusPk7Source {
        val s = plainPk7(record)
        val flags = byte(s,0x1D)
        return Gen7LegendsArceusPk7Source(
            s, u16(s,8), (flags ushr 1) and 3, byte(s,0x14), byte(s,0x15) and 7
        )
    }

    fun review(record: ByteArray): Gen7ToLegendsArceusReview =
        reviewInternal(record, null, null)

    fun review(
        record: ByteArray,
        destinationTrainer: LegendsArceusTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): Gen7ToLegendsArceusReview = reviewInternal(record, destinationTrainer, metadata)

    private fun reviewInternal(
        record: ByteArray,
        destinationTrainer: LegendsArceusTrainerRead?,
        metadata: Gen1ToSwordShieldMetadata?
    ): Gen7ToLegendsArceusReview {
        val source = inspectSource(record)
        val s = source.decryptedBytes
        val species=source.species; val held=u16(s,0x0A); val ability=source.abilityId
        val abilitySlot=source.abilityNumber; val markings=u16(s,0x16); val pid=u32(s,0x18)
        val nature=byte(s,0x1C); val sourceFlags=byte(s,0x1D); val form=sourceFlags ushr 3
        val exp=u32(s,0x10); val iv32=u32(s,0x74); val egg=iv32 and 0x40000000L != 0L
        val nicknamed=iv32 and 0x80000000L != 0L; val hyper=byte(s,0xDE)
        val language=byte(s,0xE3); val origin=byte(s,0xDF); val sourceBall=byte(s,0xDC)
        val metLevel=byte(s,0xDD) and 0x7F; val currentHandler=byte(s,0x93)
        require(species in 1..807 && species in LegendsArceusPokedexReadOnly.supportedSpecies) {
            "Species #$species is not available in the reviewed Legends: Arceus destination catalogue."
        }
        require(form==0 && species !in unavailableDefaultForms) {
            "This PK7 default form has no reviewed native Legends: Arceus form mapping."
        }
        require(!egg){"Egg PK7 records remain blocked from this PSE conversion."}
        require(held==0){"Held item #$held has no Legends: Arceus held-item field. Remove it in the source game first."}
        require(nature in 0..24){"The source nature is outside the native range."}
        require(ability in 1..267 && abilitySlot in setOf(1,2,4)) {
            "The source ability ID or slot is outside the reviewed PA8 storage range."
        }
        require(language==2){"This bounded route supports English PK7 text only; other glyph translations remain blocked."}
        require(origin in origins){"Origin game ID $origin has no reviewed PK7 -> PA8 mapping."}
        require(sourceBall in 1..26){"Ball ID $sourceBall is outside the reviewed Gen 7 range."}
        require(metLevel in 1..100){"The source met level is outside 1-100."}
        require(date(byte(s,0xD1),byte(s,0xD2),byte(s,0xD3))){"The source egg date is invalid."}
        require(date(byte(s,0xD4),byte(s,0xD5),byte(s,0xD6))){"The source met date is invalid."}
        require(hyper and 0xC0 == 0){"The source Hyper Training field contains unsupported bits."}
        require(currentHandler in 0..1){"The source current-handler field is outside the reviewed PA8 range."}
        val sourceGender=source.gender
        require(sourceGender in 0..2){"The source gender field is outside the reviewed PA8 range."}
        if (destinationTrainer != null || metadata != null) {
            require(destinationTrainer != null && metadata != null) {
                "Destination trainer and documented species metadata must be reviewed together."
            }
            require(destinationTrainer.languageId==2){"This bounded route currently requires an English Legends: Arceus destination trainer."}
            require(metadata.baseFriendship in 0..255 && metadata.genderRate in -1..8){"The documented species friendship or gender mapping is invalid."}
            require(metadata.abilityId==ability && metadata.abilityNumber==abilitySlot){"The documented Legends: Arceus ability does not exactly match PK7 ability #$ability in slot $abilitySlot."}
            require(destinationGender(sourceGender,metadata.genderRate)){"The PK7 gender does not match the documented Legends: Arceus species ratio."}
        }
        val ev=IntArray(6){byte(s,0x1E+it)}
        require(ev.all{it in 0..252} && ev.sum()<=510){"The source EVs exceed the stored PA8 limits."}
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
                require(LegendsArceusMoveCatalog.choice(move)!=null){"Move #$move is not usable in Legends: Arceus."}
                require(pp[i] in 0..LegendsArceusMoveCatalog.maximumPp(move)) {
                    "Move #$move has current PP above its Legends: Arceus base limit."
                }
            }
        }
        val relearn=IntArray(4){u16(s,0x6A+it*2)}
        relearn.filter{it!=0}.forEach{
            require(LegendsArceusMoveCatalog.choice(it)!=null){"Relearn move #$it is not usable in Legends: Arceus."}
        }
        val sourceNick=text(s,0x40,"nickname"); val ot=text(s,0xB0,"Original Trainer name")
        val ht=text(s,0x78,"Handling Trainer name")
        val nickname=if(nicknamed) sourceNick else LegendsArceusSpeciesNames.name(species)
        require(nickname.isNotBlank()){"The Legends: Arceus species name is unavailable."}
        val personal=LegendsArceusPersonalCatalogue.profiles(species,0)
            .distinctBy{listOf(it.hp,it.attack,it.defense,it.speed,it.specialAttack,it.specialDefense,it.growth)}
            .singleOrNull()?:error("This species has no single verified default Legends: Arceus profile.")
        require(exp<=minExp(100,personal.growth)){"The source EXP exceeds the reviewed level-100 value."}
        val level=level(exp,personal.growth)
        val effective=IntArray(6){i->if(hyper and (1 shl intArrayOf(0,1,2,5,3,4)[i])!=0)31 else iv[i]}
        val base=intArrayOf(personal.hp,personal.attack,personal.defense,personal.speed,personal.specialAttack,personal.specialDefense)
        fun ganbaru(i:Int):Int {
            val bias=when{effective[i]>=31->3;effective[i]>=26->2;effective[i]>=20->1;else->0}
            val multiplier=intArrayOf(0,2,3,4,7,8,9,14,15,16,25)[bias]
            val step=sqrt(base[i].toDouble())*multiplier
            return floor(((step.toFloat()+level)/2.5f+0.5f).toDouble()).toInt()
        }
        val hp=ganbaru(0)+((((level/100.0f)+1.0f)*base[0])+level).toInt()

        val o=ByteArray(PA8_SIZE)
        w32(o,0,u32(s,0));w16(o,8,species);copy(s,0x0C,o,0x0C,8);w16(o,0x14,ability)
        o[0x16]=abilitySlot.toByte();w16(o,0x18,markings and 0x0FFF);w32(o,0x1C,pid)
        o[0x20]=nature.toByte();o[0x21]=nature.toByte()
        o[0x22]=((sourceFlags and 1) or ((sourceFlags and 6) shl 1)).toByte()
        copy(s,0x1E,o,0x26,6);copy(s,0x24,o,0x2C,6);o[0x32]=s[0x2B]
        copy(s,0x5A,o,0x54,8);copy(s,0x62,o,0x5C,4);putText(o,0x60,nickname)
        copy(s,0x6A,o,0x8A,8);w16(o,0x92,hp);w32(o,0x94,iv32)
        putText(o,0xB8,ht);o[0xD2]=s[0x92];o[0xD4]=s[0x93];o[0xD8]=s[0xA2]
        o[0xEC]=s[0xAE];o[0xED]=s[0xAF];o[0xEE]=s[0xDF];o[0xF2]=s[0xE3]
        o[0xF8]=0xFF.toByte();putText(o,0x110,ot);o[0x12A]=s[0xCA]
        copy(s,0xD1,o,0x131,6);o[0x137]=27.toByte()
        w16(o,0x138,u16(s,0xD8));w16(o,0x13A,u16(s,0xDA))
        o[0x13D]=s[0xDD];o[0x13E]=s[0xDE]
        var sum=0;for(offset in 8 until PA8_SIZE step 2)sum+=u16(o,offset);w16(o,6,sum and 0xFFFF)
        val native=encrypt(o)
        val p=(LegendsArceusPokemonReadOnly.read(native) as? LegendsArceusSlotRead.Pokemon)?.value
            ?:error("The converted PA8 did not reopen with a valid native checksum.")
        require(p.species==species&&p.form==0&&p.gender==sourceGender&&
            p.fatefulEncounter==((sourceFlags and 1)!=0)&&p.pid==pid&&p.experience==exp&&
            p.currentHp==hp&&p.ball==27&&p.ganbaruValues.all{it==0}) {
            "The converted PA8 did not reopen with the reviewed identity, flags, HP, ball, and Ganbaru defaults."
        }
        LegendsArceusPseBankRestoreEditing.inspectBankRecord(native)
        val party=LegendsArceusPokemonLevelEditing.partyRecordFromStored(native)
        val reopenedParty=(LegendsArceusPokemonReadOnly.read(party) as? LegendsArceusSlotRead.Pokemon)?.value
            ?:error("The converted PA8 did not reopen with a verified native party cache.")
        require(reopenedParty.cachedLevel==level&&reopenedParty.maximumHp==hp&&reopenedParty.currentHp==hp) {
            "The converted PA8 did not reopen with the reviewed level and full native HP."
        }

        val ribbons=s.copyOfRange(0x30,0x3A)
        val memories=intArrayOf(byte(s,0xA3),byte(s,0xA4),byte(s,0xA5),u16(s,0xA8),
            byte(s,0xCB),byte(s,0xCC),byte(s,0xCD),u16(s,0xCE),byte(s,0xD0))
        val omittedMetadata=listOf(
            "0x2A="+hex(s,0x2A,0x2B), "0x2C-0x2F="+hex(s,0x2C,0x30),
            "0x3A-0x3F="+hex(s,0x3A,0x40), "0x58-0x59="+hex(s,0x58,0x5A),
            "0x72-0x73="+hex(s,0x72,0x74), "0x90-0x91="+hex(s,0x90,0x92),
            "0x94-0xA1="+hex(s,0x94,0xA2), "0xA3-0xAD="+hex(s,0xA3,0xAE),
            "0xC8-0xC9="+hex(s,0xC8,0xCA), "0xCB-0xD0="+hex(s,0xCB,0xD1),
            "0xD7="+hex(s,0xD7,0xD8), "0xE0-0xE2="+hex(s,0xE0,0xE3),
            "0xE4-0xE7="+hex(s,0xE4,0xE8)
        ).joinToString(", ")
        val effects=listOf(
            "Explicit Legalise: PSE PK7 -> PA8 conversion; not an official HOME transfer, in-game trade, or verified provenance.",
            "Species, reviewed default form, PID, EC, TID/SID, EXP, IVs, raw EV bytes, nature, gender, shiny state, friendship, Pokerus, contest values, origin, dates, locations, fateful flag, Hyper Training, moves, current PP and relearn moves are retained.",
            if(nicknamed)"Nickname retained as English UTF-16 text: $nickname." else "Non-nicknamed text resets to the English Legends: Arceus species name: $nickname.",
            "OT text, HT text and current-handler state are retained; Gen 7 text trash bytes are normalized.",
            if(metadata==null)"Held item remains empty. Ability ID $ability and slot $abilitySlot are retained as PA8 storage data; encounter, ability and moveset legality are not certified." else "Held item remains empty. Documented Legends: Arceus ability ID $ability and slot $abilitySlot are retained as PA8 storage data; the source gender matches the documented destination ratio; encounter, ability and moveset legality are not certified.",
            "Source Ball #$sourceBall becomes Strange Ball #27, the native Legends: Arceus representation for a Pokémon originating outside Hisui.",
            if(ups.all{it==0})"No PP Ups require removal; PA8 PP-Up bytes remain zero." else "PP Ups ${ups.joinToString("/")} are cleared because Legends: Arceus uses fixed move PP; current PP was verified against each native base limit.",
            "Ganbaru Values reset to 0. Native stats use retained IVs, Hyper Training, level, nature and PLA's IV bias; raw EV bytes remain stored but do not drive PLA battle stats. Current HP is rebuilt full at $hp.",
            "Alpha and Noble flags, Alpha move, move-shop purchased/mastered flags, move records, battle state/version and HOME tracker are cleared. Size scalars, scale and absolute size caches use zero native defaults.",
            if(markings and 0xF000==0)"All 12 supported marking bits are retained." else "High marking bits 0x${(markings and 0xF000).toString(16).uppercase()} are cleared; low 12 bits are retained.",
            if(ribbons.all{it==0.toByte()})"No Gen 7 ribbon bits require omission." else "Ribbon/memory-ribbon bytes ${ribbons.joinToString(" "){"%02X".format(it)}} are cleared; this bounded adapter has no reviewed PA8 ribbon mapping.",
            "Resort, Super Training, distributed training, affection, geography, country/region/console metadata, sociability and trainer memories are cleared. Source memory/affection values: ${memories.joinToString("/")}.",
            "Unmapped PK7 metadata bytes are omitted only by this explicit Legalise choice: $omittedMetadata.",
            "The PK7 Bank entry and Legends: Arceus source remain unchanged. One user-chosen empty native PA8 pasture slot in a new copy is written with retained recovery, persisted reopen verification, and two-sided rollback."
        )
        return Gen7ToLegendsArceusReview(native,species,sourceNick,effects)
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
        for(block in 0..3){val d=8+(order[block]-'0')*0x58;p.copyInto(s,d,8+block*0x58,8+(block+1)*0x58)}
        xor(s,8,PA8_SIZE,ec);return s}
    private fun xor(b:ByteArray,from:Int,end:Int,seed:Long){var state=seed;for(i in from until end step 2){state=(state*0x41C64E6DL+0x6073L) and 0xFFFFFFFFL;w16(b,i,u16(b,i) xor (state ushr 16).toInt())}}
    private fun copy(a:ByteArray,ao:Int,b:ByteArray,bo:Int,n:Int)=a.copyInto(b,bo,ao,ao+n)
    private fun byte(b:ByteArray,o:Int)=b[o].toInt() and 255
    private fun u16(b:ByteArray,o:Int)=byte(b,o) or (byte(b,o+1) shl 8)
    private fun u32(b:ByteArray,o:Int)=u16(b,o).toLong() or (u16(b,o+2).toLong() shl 16)
    private fun w16(b:ByteArray,o:Int,v:Int){b[o]=v.toByte();b[o+1]=(v ushr 8).toByte()}
    private fun w32(b:ByteArray,o:Int,v:Long){w16(b,o,v.toInt());w16(b,o+2,(v ushr 16).toInt())}
}
