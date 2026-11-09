package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets

data class ScarletVioletToLegendsZaReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val effects: List<String>
)

object ScarletVioletToLegendsZaPa9 {
    private const val RECORD_SIZE = 0x158
    private const val STORED_END = 0x148
    private val orders = listOf("0123","0132","0213","0312","0231","0321","1023","1032",
        "2013","3012","2031","3021","1203","1302","2103","3102","2301","3201",
        "1230","1320","2130","3120","2310","3210")
    private val reviewedOrigins = setOf(1,2,3,4,5,7,8,10,11,12,15,20,21,22,23,24,25,26,27,
        30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,47,48,49,50,51)

    fun review(record: ByteArray): ScarletVioletToLegendsZaReview {
        val source = ScarletVioletPseBankRestoreEditing.inspectBankRecord(record)
        val s = plainPk9(record)
        val species=source.species; val form=source.form; val held=source.heldItem
        val ability=source.ability; val abilityNumber=byte(s,0x16) and 7
        val markings=u16(s,0x18); val nature=source.nature; val statNature=source.statNature
        val sourceFlags=byte(s,0x22); val sourceGender=source.gender; val exp=source.experience
        val iv32=u32(s,0x8C); val hyper=source.hyperTrainingFlags
        val language=source.language; val origin=source.originGame; val ball=source.ball
        val metLevel=source.metLevel; val obedience=source.obedienceLevel; val currentHandler=source.currentHandler
        val sourceScale=source.scale; val teraOriginal=source.teraTypeOriginal; val teraOverride=source.teraTypeOverride
        require(!source.isEgg){"Egg PK9 records remain blocked from this PSE conversion."}
        require(form==0){"Scarlet/Violet form $form has no bounded Legends: Z-A mapping. This first PA9 route accepts only exact default forms and never invents Mega or temporary battle state."}
        require(held==0){"Held item #$held has no reviewed Scarlet/Violet -> Legends: Z-A mapping. Remove it in the source game first."}
        require(nature in 0..24 && statNature in 0..24){"The source nature data is outside the native range."}
        require(ability in 1..310 && abilityNumber in setOf(1,2,4)) {
            "The source ability ID or slot is outside the reviewed PA9 storage range."
        }
        require(sourceGender in 0..2){"The source gender field is outside the reviewed PA9 range."}
        require(language==2){"This bounded route supports English PK9 text only; other glyph translations remain blocked."}
        require(origin in reviewedOrigins){"Origin game ID $origin has no reviewed PK9 -> PA9 mapping."}
        require(ball in 1..27){"Ball ID $ball is outside the reviewed Scarlet/Violet range."}
        require(metLevel in 1..100){"The source met level is outside 1-100."}
        require(obedience in 0..100){"The source obedience level is outside 0-100."}
        require(ScarletVioletTeraTypes.isOriginalValid(teraOriginal) &&
            ScarletVioletTeraTypes.isOverrideValid(teraOverride)){
            "The source Tera fields are outside the reviewed PK9 range."
        }
        require(date(byte(s,0x119),byte(s,0x11A),byte(s,0x11B))){"The source egg date is invalid."}
        require(date(byte(s,0x11C),byte(s,0x11D),byte(s,0x11E))){"The source met date is invalid."}
        require(hyper and 0xC0 == 0){"The source Hyper Training field contains unsupported bits."}
        require(currentHandler in 0..1){"The source current-handler field is outside the reviewed PA9 range."}
        val ev=source.evs.toIntArray()
        require(ev.all{it in 0..252} && ev.sum()<=510){"The source EVs exceed Legends: Z-A limits."}
        val iv=source.ivs.toIntArray()
        val moves=source.moves.map{it.id}.toIntArray()
        val pp=source.moves.map{it.pp}.toIntArray()
        val ups=source.moves.map{it.ppUps}.toIntArray()
        for(i in 0..3){
            val move=moves[i]
            if(move==0) require(pp[i]==0 && ups[i]==0){"Empty move slot ${i+1} contains PP or PP-Up data."}
            else {
                require(ups[i] in 0..3){"Move #$move has an invalid PP-Up count."}
                require(LegendsZaMoveCatalog.choice(move)!=null){"Move #$move is not usable in Legends: Z-A."}
                require(pp[i] in 0..LegendsZaMoveCatalog.maximumPp(move,ups[i])) {
                    "Move #$move has PP outside its Legends: Z-A limit."
                }
            }
        }
        source.relearnMoves.filter{it!=0}.forEach{
            require(LegendsZaMoveCatalog.choice(it)!=null){"Relearn move #$it is not usable in Legends: Z-A."}
        }
        val nickname=text(s,0x58,"nickname"); val ht=text(s,0xA8,"Handling Trainer name")
        val ot=text(s,0xF8,"Original Trainer name")
        val personal=LegendsZaPersonalCatalogue.profile(species,form)
            ?:error("Species #$species form $form has no verified Legends: Z-A profile.")
        require(exp<=minExp(100,personal.growth)){"The source EXP exceeds the reviewed level-100 value."}
        val level=level(exp,personal.growth)
        val effective=IntArray(6){i->if(hyper and (1 shl intArrayOf(0,1,2,5,3,4)[i])!=0)31 else iv[i]}
        val base=intArrayOf(personal.hp,personal.attack,personal.defense,personal.speed,personal.specialAttack,personal.specialDefense)
        fun core(i:Int)=(2*base[i]+effective[i]+ev[i]/4)*level/100
        fun adjusted(i:Int,v:Int):Int{val up=statNature/5;val down=statNature%5;return if(up==down)v else when(i){up->v*110/100;down->v*90/100;else->v}}
        val hp=if(species==292)1 else core(0)+level+10
        val atk=adjusted(0,core(1)+5);val def=adjusted(1,core(2)+5);val spe=adjusted(4,core(3)+5)
        val spa=adjusted(2,core(4)+5);val spd=adjusted(3,core(5)+5)

        val o=ByteArray(RECORD_SIZE)
        w32(o,0,source.encryptionConstant);w16(o,8,species);copy(s,0x0C,o,0x0C,8)
        w16(o,0x14,ability);o[0x16]=abilityNumber.toByte();w16(o,0x18,markings and 0x0FFF)
        w32(o,0x1C,source.pid);o[0x20]=nature.toByte();o[0x21]=statNature.toByte()
        o[0x22]=((sourceFlags and 1) or (sourceGender shl 1)).toByte();o[0x24]=form.toByte()
        copy(s,0x26,o,0x26,6);copy(s,0x2C,o,0x2C,6);o[0x32]=s[0x32]
        o[0x4A]=sourceScale.toByte();putText(o,0x58,nickname)
        copy(s,0x72,o,0x72,16);copy(s,0x82,o,0x82,8);w16(o,0x8A,hp);w32(o,0x8C,iv32)
        putText(o,0xA8,ht);o[0xC2]=s[0xC2];o[0xC3]=s[0xC3];o[0xC4]=s[0xC4];o[0xC8]=s[0xC8]
        o[0xCE]=s[0xCE];o[0xD4]=0xFF.toByte();o[0xD5]=s[0xD5]
        putText(o,0xF8,ot);o[0x112]=s[0x112];copy(s,0x119,o,0x119,6)
        o[0x11F]=obedience.toByte();copy(s,0x120,o,0x120,6);o[0x126]=s[0x126]
        o[0x148]=level.toByte();w16(o,0x14A,hp);w16(o,0x14C,atk);w16(o,0x14E,def)
        w16(o,0x150,spe);w16(o,0x152,spa);w16(o,0x154,spd)
        var sum=0;for(offset in 8 until STORED_END step 2)sum+=u16(o,offset);w16(o,6,sum and 0xFFFF)
        val native=encrypt(o)
        val p=(LegendsZaPokemonReadOnly.read(native) as? LegendsZaSlotRead.Pokemon)?.value
            ?:error("The converted PA9 did not reopen with a valid native checksum.")
        require(p.species==species&&p.form==form&&p.gender==sourceGender&&
            p.fatefulEncounter==source.fatefulEncounter&&p.pid==source.pid&&p.experience==exp&&
            p.cachedLevel==level&&p.maximumHp==hp&&p.currentHp==hp&&
            p.cachedStats==listOf(atk,def,spe,spa,spd)&&!p.isAlpha&&p.scale==sourceScale&&
            p.ball==ball&&p.obedienceLevel==obedience&&p.ability==ability) {
            "The converted PA9 did not reopen with the reviewed identity, stats, and Legends: Z-A defaults."
        }
        LegendsZaPseBankRestoreEditing.inspectBankRecord(native)

        val ribbonBytes=s.copyOfRange(0x34,0x48)
        val heightScalar=source.sizeScalars.getOrElse(0){0}
        val weightScalar=source.sizeScalars.getOrElse(1){0}
        val machineCount=source.technicalMachineFlags.count{it}
        val effects=listOf(
            "Explicit Legalise: PSE Scarlet/Violet PK9 -> Legends: Z-A PA9 conversion; not an official HOME transfer, in-game trade, or verified provenance.",
            "Species, exact default form, PID, EC, TID/SID, EXP, IVs, EVs, nature, stat nature, gender, shiny state, friendship, Pokerus, contest values, origin, dates, locations, ball, fateful flag, Hyper Training, moves, PP, PP Ups, relearn moves, display Scale and Obedience Level are retained.",
            "Nickname, OT text, HT text, HT language and current-handler state are retained as English UTF-16 text; source trash bytes are normalized.",
            "Held item remains empty. Ability ID $ability and slot $abilityNumber are retained as PA9 storage data; Legends: Z-A encounter, ability and moveset legality are not certified.",
            "The destination Alpha flag and all unmapped Legends: Z-A-only, Mega or temporary battle-form metadata remain native default 0. PSE does not invent Alpha, Mega or temporary battle-form state.",
            "Source display Scale $sourceScale is retained. Height Scalar $heightScalar and Weight Scalar $weightScalar have no reviewed PA9 mapping and reset to 0.",
            "Scarlet/Violet Original Tera Type $teraOriginal and override $teraOverride have no PA9 equivalent and are cleared; PSE makes no natural-Tera claim.",
            "Current HP is rebuilt full at $hp and cached level/stats are rebuilt from retained permanent values and the verified Legends: Z-A profile. Retained Obedience Level is $obedience; retained met level is $metLevel.",
            if(markings and 0xF000==0)"All 12 supported marking bits are retained." else "High marking bits 0x${(markings and 0xF000).toString(16).uppercase()} are cleared; low 12 bits are retained.",
            if(ribbonBytes.all{it==0.toByte()} && source.affixedRibbon<0)"No Scarlet/Violet ribbon, mark or affixed-ribbon data requires omission." else "Ribbon/mark bytes ${ribbonBytes.joinToString(" "){"%02X".format(it)}} and affixed ribbon ${source.affixedRibbon} are cleared; this bounded adapter has no reviewed PK9 -> PA9 ribbon mapping.",
            "Technical Machine flags set: $machineCount. Battle Version ${source.battleVersion}, Form Argument ${source.formArgument} and status condition 0x${source.statusCondition.toString(16).uppercase()} are cleared with other unmapped PK9 and PA9-only metadata.",
            if(source.homeTrackerLow==0L&&source.homeTrackerHigh==0L)"No source HOME tracker bytes require omission." else "Source HOME tracker values 0x${source.homeTrackerHigh.toString(16).uppercase()}:${source.homeTrackerLow.toString(16).uppercase()} are cleared; PSE does not invent or claim an official HOME identity.",
            "The PK9 Bank entry and Legends: Z-A source remain unchanged. One user-chosen empty native PA9 slot in a new copy is written with retained recovery, persisted reopen verification, and two-sided rollback."
        )
        return ScarletVioletToLegendsZaReview(native,species,nickname,effects)
    }

    private fun plainPk9(record:ByteArray):ByteArray{
        require(record.size==RECORD_SIZE){"A complete 344-byte native PK9 record is required."}
        val ec=u32(record,0);val decoded=record.copyOf();xor(decoded,8,STORED_END,ec);xor(decoded,STORED_END,RECORD_SIZE,ec)
        val order=orders[((ec ushr 13).toInt() and 31)%24];val plain=decoded.copyOf()
        for(block in 0..3){val from=8+(order[block]-'0')*0x50;decoded.copyInto(plain,8+block*0x50,from,from+0x50)}
        var sum=0;for(offset in 8 until STORED_END step 2)sum+=u16(plain,offset)
        require(u16(record,4)==0&&(sum and 0xFFFF)==u16(record,6)){"The PK9 checksum failed."}
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
        for(block in 0..3){val d=8+(order[block]-'0')*0x50;p.copyInto(s,d,8+block*0x50,8+(block+1)*0x50)}
        xor(s,8,STORED_END,ec);xor(s,STORED_END,RECORD_SIZE,ec);return s}
    private fun xor(b:ByteArray,from:Int,end:Int,seed:Long){var state=seed;for(i in from until end step 2){state=(state*0x41C64E6DL+0x6073L) and 0xFFFFFFFFL;w16(b,i,u16(b,i) xor (state ushr 16).toInt())}}
    private fun copy(a:ByteArray,ao:Int,b:ByteArray,bo:Int,n:Int)=a.copyInto(b,bo,ao,ao+n)
    private fun byte(b:ByteArray,o:Int)=b[o].toInt() and 255
    private fun u16(b:ByteArray,o:Int)=byte(b,o) or (byte(b,o+1) shl 8)
    private fun u32(b:ByteArray,o:Int)=u16(b,o).toLong() or (u16(b,o+2).toLong() shl 16)
    private fun w16(b:ByteArray,o:Int,v:Int){b[o]=v.toByte();b[o+1]=(v ushr 8).toByte()}
    private fun w32(b:ByteArray,o:Int,v:Long){w16(b,o,v.toInt());w16(b,o+2,(v ushr 16).toInt())}
}
