package com.example.pokemonsavemanager

import java.io.File

internal data class Gen3BankBatchItem(
    val bankFile: File,
    val bankBytes: ByteArray,
    val pokemon: MainActivity.PokemonEntry
)

internal data class Gen3BankPartyFailure(
    val title: String,
    val message: String
)

internal fun gen3BankPartyCompletionMessage(copyOnly: Boolean, count: Int): String =
    if(copyOnly) "Copied $count Pokémon to Party; Bank originals unchanged"
    else "Withdrew $count Pokémon to Party and verified"

internal fun finishGen3BankPartySuccess(
    copyOnly: Boolean,
    count: Int,
    recoverySet: ExactBankRecoverySet
): String {
    val message=gen3BankPartyCompletionMessage(copyOnly,count)
    if(recoverySet.cleanupExact()) return message
    val retained=recoverySet.retainedNames()
    return message +
        ". One or more temporary exact Bank recovery snapshots changed or could not be removed safely, so they were retained for review." +
        (if(retained.isNotEmpty()) " Retained snapshots: $retained" else "")
}

internal fun restoreExactGen3BankPartyRecords(
    selected: List<Gen3BankBatchItem>,
    removed: List<Gen3BankBatchItem>
): Boolean {
    val selectedIdentities=selected.map { item ->
        runCatching {
            require(item.bankFile.absoluteFile==item.bankFile.canonicalFile)
            item.bankFile.canonicalPath
        }.getOrNull()
    }
    val removedIdentities=removed.map { item ->
        runCatching {
            require(item.bankFile.absoluteFile==item.bankFile.canonicalFile)
            item.bankFile.canonicalPath
        }.getOrNull()
    }
    if(selectedIdentities.any { it==null }||removedIdentities.any { it==null })return false
    if(selectedIdentities.filterNotNull().distinct().size!=selected.size||
        removedIdentities.filterNotNull().distinct().size!=removed.size)return false
    val selectedByIdentity=selected.indices.associateBy { selectedIdentities[it]!! }
    val removedBound=removed.indices.map { index ->
        val selectedIndex=selectedByIdentity[removedIdentities[index]!!]
        selectedIndex!=null&&removed[index].bankBytes.contentEquals(selected[selectedIndex].bankBytes)
    }.all { it }
    if(!removedBound)return false
    removed.forEach { item ->
        runCatching {
            if(!item.bankFile.exists()) {
                require(item.bankFile.createNewFile())
                val writeFailure=runCatching { item.bankFile.writeBytes(item.bankBytes) }.exceptionOrNull()
                val exact=runCatching {
                    item.bankFile.isFile&&item.bankFile.readBytes().contentEquals(item.bankBytes)
                }.getOrDefault(false)
                if(!exact&&writeFailure!=null) {
                    val partial=runCatching {
                        if(item.bankFile.isFile)item.bankFile.readBytes() else null
                    }.getOrNull()
                    if(partial!=null&&partial.size<item.bankBytes.size&&
                        item.bankBytes.copyOf(partial.size).contentEquals(partial)) {
                        runCatching {
                            require(item.bankFile.absoluteFile==item.bankFile.canonicalFile)
                            require(item.bankFile.delete()&&!item.bankFile.exists())
                        }
                    }
                }
            }
        }
    }
    val restored=selected.map { item ->
        runCatching {
            item.bankFile.isFile && item.bankFile.readBytes().contentEquals(item.bankBytes)
        }.getOrDefault(false)
    }
    return restored.all { it }
}

internal fun finishGen3BankPartyFailure(
    copyOnly: Boolean,
    errorMessage: String?,
    bankRestored: Boolean,
    saveRestored: Boolean,
    backup: File?,
    recoverySet: ExactBankRecoverySet
): Gen3BankPartyFailure {
    val recoveryClean = if(bankRestored && saveRestored) {
        recoverySet.cleanupExact()
    } else {
        false
    }
    val retained = recoverySet.retainedNames()
    val recovery = when {
        bankRestored && saveRestored && recoveryClean ->
            "The original save and Bank records were preserved or restored."
        bankRestored && saveRestored ->
            "The original save and Bank records were preserved or restored. One or more temporary exact Bank recovery snapshots changed or could not be removed safely and were retained for review."
        bankRestored ->
            "Bank entries were restored. Restore the retained save backup before editing again."
        saveRestored ->
            "The original save was restored. Bank recovery snapshots were retained; review Bank before editing."
        else ->
            "Automatic rollback was incomplete. The save backup and Bank recovery snapshots were retained."
    }
    return Gen3BankPartyFailure(
        if(copyOnly) "Gen 3 Bank-to-party copy failed"
        else "Gen 3 Bank-to-party withdrawal failed",
        (errorMessage ?: "Unknown error") + "\n\n" + recovery +
            (backup?.let { "\nRecovery backup: ${it.name}" } ?: "") +
            (if(retained.isNotEmpty()) "\nBank recovery snapshots: $retained" else "")
    )
}

internal data class PseSelectedBankBatchResult(
    val moved: Int=0,
    val failure: String?=null,
    val notice: String?=null
)

internal fun retainedBankBackupMatches(
    backup: File?,
    expected: ByteArray,
    kept: Boolean
): Boolean = backup?.let { file ->
    runCatching {
        file.absoluteFile==file.canonicalFile &&
            file.isFile && kept && file.readBytes().contentEquals(expected)
    }.getOrDefault(false)
} ?: false

internal fun exactBankPathsAbsent(paths: List<File>): Boolean {
    val identities=paths.map { path ->
        runCatching {
            require(path.absoluteFile==path.canonicalFile)
            path.canonicalPath
        }.getOrNull()
    }
    if(identities.any { it==null }||identities.filterNotNull().distinct().size!=paths.size)return false
    val absent=paths.map { path ->
        runCatching {
            require(path.absoluteFile==path.canonicalFile)
            !path.exists()
        }.getOrDefault(false)
    }
    return absent.all { it }
}

internal fun requireGen3PartyBatchBackup(
    backup: File,
    expected: ByteArray,
    kept: Boolean,
    stage: Int
) {
    require(retainedBankBackupMatches(backup, expected, kept)) {
        when (stage) {
            0 -> "The retained Gen 3 party batch backup did not remain available."
            1 -> "The retained Gen 3 party batch backup changed before writing."
            else -> "The retained Gen 3 party batch backup changed after persistence."
        }
    }
}

internal fun gen3PartyBatchBackupStatus(verified: Boolean): String =
    if (verified) {
        " The exact Keep-pinned Gen 3 party batch backup was reopened and verified."
    } else {
        " The exact Keep-pinned Gen 3 party batch backup was not verified; keep any recovery file and Bank records for review."
    }

internal fun gen3PartyBatchRecoveryMessage(
    writeStarted: Boolean,
    bankClean: Boolean,
    restored: Boolean,
    toBank: Boolean,
    retainedBackup: Boolean
): String {
    val source = when {
        !writeStarted && bankClean -> "No save changes or new Bank entries were kept."
        !writeStarted -> "The save was not written, but some staged Bank files need manual review."
        restored && bankClean -> "The reviewed save was restored and verified; no new Bank entries were kept."
        restored -> "The reviewed save was restored, but some new Bank files could not be removed. Review PSE Bank before editing again."
        toBank -> "Source rollback could not be verified. Staged and finalized Bank files were retained for recovery. Restore the retained save backup and review PSE Bank before editing again."
        else -> "Automatic rollback could not be verified. Restore the retained recovery backup before editing again."
    }
    return source + gen3PartyBatchBackupStatus(retainedBackup)
}

internal fun restoreExactMissingGen3BankFile(
    file: File,
    expected: ByteArray
): Boolean = runCatching {
    require(file.absoluteFile==file.canonicalFile) {
        "The Gen 3 Bank recovery path is unresolved or noncanonical."
    }
    if (!file.exists()) {
        require(file.createNewFile()) {
            "A Gen 3 Bank recovery path could not be reserved."
        }
        val writeFailure=runCatching { file.writeBytes(expected) }.exceptionOrNull()
        val exact=runCatching {
            file.isFile&&file.readBytes().contentEquals(expected)
        }.getOrDefault(false)
        if(!exact&&writeFailure!=null) {
            val partial=runCatching { if(file.isFile)file.readBytes() else null }.getOrNull()
            if(partial!=null&&partial.size<expected.size&&expected.copyOf(partial.size).contentEquals(partial)) {
                runCatching {
                    require(file.absoluteFile==file.canonicalFile)
                    require(file.delete()&&!file.exists())
                }
            }
        }
    }
    file.isFile && file.readBytes().contentEquals(expected)
}.getOrDefault(false)

internal fun exactGen3BankRecoverySnapshots(
    files: List<File>,
    expected: List<ByteArray>
): Boolean {
    if (files.size != expected.size) return false
    val identities=files.map { file ->
        runCatching {
            require(file.absoluteFile==file.canonicalFile)
            file.canonicalPath
        }.getOrNull()
    }
    if(identities.any { it==null }||identities.filterNotNull().distinct().size!=files.size)return false
    val verified=files.indices.map { index ->
        runCatching {
            files[index].isFile &&
                files[index].readBytes().contentEquals(expected[index])
        }.getOrDefault(false)
    }
    return verified.all { it }
}

internal fun cleanupExactBankRecoverySnapshots(
    files: List<File>,
    expected: List<ByteArray>
): Boolean {
    if (files.size != expected.size) return false
    val identities=files.map { file ->
        runCatching {
            require(file.absoluteFile==file.canonicalFile)
            file.canonicalPath
        }.getOrNull()
    }
    if(identities.any { it==null }||identities.filterNotNull().distinct().size!=files.size)return false
    var clean=true
    files.indices.forEach { index ->
        val file=files[index]
        val bytes=expected[index]
        val removed=runCatching {
            if(file.exists()){
                require(file.isFile && file.readBytes().contentEquals(bytes)){
                    "A Bank recovery snapshot changed; it was retained for review."
                }
                require(file.delete()){
                    "An exact Bank recovery snapshot could not be removed."
                }
            }
            require(!file.exists()){
                "An exact Bank recovery snapshot still exists."
            }
        }.isSuccess
        if(!removed)clean=false
    }
    return clean
}

internal data class SelectedGen3BankWithdrawalCompletion(
    val message: String,
    val recoveryClean: Boolean
)

internal fun finishSelectedGen3BankWithdrawal(
    recoveryFiles: List<File>,
    expected: List<ByteArray>,
    moved: Int,
    targetBoxName: String
): SelectedGen3BankWithdrawalCompletion {
    val recoveryClean=cleanupExactBankRecoverySnapshots(recoveryFiles, expected)
    return SelectedGen3BankWithdrawalCompletion(
        "$moved Gen 3 Pokémon moved to $targetBoxName and verified" +
            if(recoveryClean) "" else
                "; a Bank recovery snapshot was retained for review",
        recoveryClean
    )
}

internal data class SelectedGen2BankWithdrawalCompletion(
    val message: String,
    val recoveryClean: Boolean
)

internal fun finishSelectedGen2BankWithdrawalCompletion(
    recoveryFiles: List<File>,
    expected: List<ByteArray>,
    moved: Int,
    targetBox: Int
): SelectedGen2BankWithdrawalCompletion {
    val recoveryClean=cleanupExactBankRecoverySnapshots(recoveryFiles, expected)
    return SelectedGen2BankWithdrawalCompletion(
        "$moved Gen 2 Pokémon moved to Box $targetBox and verified" +
            if(recoveryClean) "" else
                "; a Bank recovery snapshot was retained for review",
        recoveryClean
    )
}

internal fun finishSelectedGen2BankWithdrawal(
    recoveryFiles: List<File>,
    expected: List<ByteArray>,
    moved: Int,
    targetBox: Int
): String = finishSelectedGen2BankWithdrawalCompletion(
    recoveryFiles, expected, moved, targetBox
).message

internal fun finishSelectedGen2BankWithdrawal(
    recoveryFiles: List<File>,
    expected: List<ByteArray>,
    moved: Int,
    targetBox: Int,
    destinationBound: Boolean,
    destination: File,
    expectedDestination: ByteArray,
    backup: File,
    expectedBackup: ByteArray,
    kept: Boolean,
    removed: List<Pair<File, ByteArray>>
): String {
    val completion=finishSelectedGen2BankWithdrawalCompletion(
        recoveryFiles, expected, moved, targetBox
    )
    require(destinationBound &&
        destination.isFile && destination.readBytes().contentEquals(expectedDestination) &&
        retainedBankBackupMatches(backup, expectedBackup, kept) &&
        removed.size == moved && exactBankPathsAbsent(removed.map { it.first }) &&
        (!completion.recoveryClean || exactBankPathsAbsent(recoveryFiles))) {
        "The verified Gen 2 destination, removed Bank batch or retained destination backup changed during recovery cleanup."
    }
    return completion.message
}

internal class ExactBankRecoverySet(private val prefix:String="PSE_G3_BankPartyRecovery_") {
    private val entries=ArrayList<Pair<File,ByteArray>>()

    fun capture(bytes: ByteArray, directory: File) {
        require(directory.absoluteFile==directory.canonicalFile&&directory.isDirectory) {
            "The Bank recovery directory is unresolved, noncanonical or unavailable."
        }
        val file=File.createTempFile(prefix, ".tmp", directory)
        entries.add(file to bytes.copyOf())
        require(file.absoluteFile==file.canonicalFile&&file.parentFile==directory) {
            "A Bank recovery snapshot was not created at its canonical directory identity."
        }
        file.writeBytes(bytes)
        require(file.isFile && file.readBytes().contentEquals(bytes)) {
            "A Bank recovery snapshot did not verify."
        }
    }

    private fun hasUniqueCanonicalIdentities(): Boolean {
        val identities=entries.map { (file,_) ->
            runCatching {
                require(file.absoluteFile==file.canonicalFile)
                file.canonicalPath
            }.getOrNull()
        }
        return identities.none { it==null }&&
            identities.filterNotNull().distinct().size==entries.size
    }

    fun matches(): Boolean {
        if(!hasUniqueCanonicalIdentities())return false
        val exactMatches=entries.map { (file,bytes) ->
            runCatching {
                require(file.absoluteFile==file.canonicalFile)
                file.isFile && file.readBytes().contentEquals(bytes)
            }.getOrDefault(false)
        }
        return exactMatches.all { it }
    }

    fun cleanupExact(): Boolean {
        if(!hasUniqueCanonicalIdentities())return false
        var clean=true
        entries.forEach { (file,bytes) ->
            if(!file.exists()) return@forEach
            val exact=runCatching {
                require(file.absoluteFile==file.canonicalFile)
                file.isFile && file.readBytes().contentEquals(bytes)
            }.getOrDefault(false)
            if(!exact) {
                clean=false
            } else {
                val removed=runCatching {
                    file.delete() || !file.exists()
                }.getOrDefault(false)
                if(!removed || file.exists()) clean=false
            }
        }
        return clean
    }

    fun retainedNames(): String =
        entries.map { it.first }.filter { it.exists() }.joinToString(", ") { it.name }
}

internal fun createGen1ToGen4BatchRecovery(
    expected:List<ByteArray>,
    directory:File
):ExactBankRecoverySet {
    val recovery=ExactBankRecoverySet("PSE_G1ToG4_BatchRecovery_")
    expected.forEach{recovery.capture(it,directory)}
    require(recovery.matches())
    return recovery
}

internal fun gen1ToGen4BatchRecoveryFailureNotice(recovery:ExactBankRecoverySet?):String {
    val names=recovery?.retainedNames().orEmpty()
    return if(names.isEmpty())"" else "\n\nBank recovery snapshots: "+names
}

internal fun finishGen1ToGen4BatchRecovery(recovery:ExactBankRecoverySet?):String=
    if(recovery==null||recovery.cleanupExact())"" else
        "; one or more exact Bank recoveries retained for review"

internal fun gen1ToGen4BatchRecoveryMessage(
    banksRestored:Boolean,
    saveRestored:Boolean
):String=when {
    banksRestored&&saveRestored->"The exact Gen 4 destination and every selected Gen 1 Bank record were restored or kept and verified."
    banksRestored->"Every selected Gen 1 Bank record was restored, but destination restoration was not verified. Use the retained destination backup before editing again."
    saveRestored->"The exact Gen 4 destination was restored, but one or more Bank records were not verified. Keep every exact Gen 1 Bank recovery payload."
    else->"Automatic recovery could not be verified. Keep the retained destination backup and every exact Gen 1 Bank recovery payload for review."
}

internal fun sameGen1ToGen4BatchPk4(
    a:MainActivity.PokemonEntry,
    b:MainActivity.PokemonEntry
):Boolean=
    a.checksumValid&&b.checksumValid&&a.pid==b.pid&&a.species==b.species&&
        a.tid==b.tid&&a.sid==b.sid&&a.nickname==b.nickname&&a.otName==b.otName&&
        a.experience==b.experience&&a.ability==b.ability&&a.nature==b.nature&&
        a.shiny==b.shiny&&a.originGame==b.originGame&&a.ball==b.ball&&
        a.metLevel==b.metLevel&&a.language==b.language&&
        a.moves.contentEquals(b.moves)&&a.movePP.contentEquals(b.movePP)&&
        a.evs.contentEquals(b.evs)&&a.ivs.contentEquals(b.ivs)

internal fun requireGen1ToGen4BatchRecoveryBoundary(
    destinationMatches:Boolean,
    backupMatches:Boolean,
    recovery:ExactBankRecoverySet,
    sourcesMatch:Boolean=true,
    stage:Int
) {
    require(destinationMatches&&backupMatches&&recovery.matches()&&sourcesMatch) {
        when(stage) {
            0->"The destination, selected Bank records or retained recovery backup changed before writing."
            1->"The verified destination identity, data or retained backup changed before Bank cleanup."
            else->"The verified Gen 4 destination changed after Bank cleanup."
        }
    }
}

internal fun requireGen1ToGen4BatchRemovedPaths(
    removed:List<Pair<File,ByteArray>>,
    expectedCount:Int?=null,
    recovery:ExactBankRecoverySet?=null,
    stage:Int
) {
    val absent=exactBankPathsAbsent(removed.map{it.first})
    require(absent&&expectedCount?.let{removed.size==it}!=false&&recovery?.matches()!=false) {
        when(stage) {
            0->"An earlier removed Gen 1 Bank path reappeared before the next cleanup step."
            1->"A removed Gen 1 Bank path did not remain absent after cleanup."
            else->"The verified Gen 1 removed Bank batch changed after cleanup."
        }
    }
}

internal data class BuiltGen1ToGen4Pk4(
    val encrypted:ByteArray,
    val nature:Int,
    val shiny:Boolean,
    val evs:IntArray,
    val ivs:IntArray,
    val nickname:String,
    val originGame:Int
)

private fun writeGen1ToGen4U16(data:ByteArray,offset:Int,value:Int) {
    data[offset]=(value and 0xFF).toByte()
    data[offset+1]=((value ushr 8) and 0xFF).toByte()
}

private fun writeGen1ToGen4U32(data:ByteArray,offset:Int,value:Long) {
    data[offset]=(value and 0xFF).toByte()
    data[offset+1]=((value ushr 8) and 0xFF).toByte()
    data[offset+2]=((value ushr 16) and 0xFF).toByte()
    data[offset+3]=((value ushr 24) and 0xFF).toByte()
}

internal fun buildGen1ToGen4Pk4Bytes(
    bankBytes:ByteArray,
    source:MainActivity.Gen1Pokemon,
    expectedBankSize:Int,
    baseFriendship:Int,
    abilityId:Int,
    dex:Int,
    pid:Long,
    nickname:String,
    originGame:Int,
    writeString:(ByteArray,Int,Int,String)->Unit,
    encrypt:(ByteArray)->ByteArray
):BuiltGen1ToGen4Pk4 {
    require(bankBytes.size==expectedBankSize){"The preserved Gen 1 Bank record has an unexpected size."}
    require(source.movePpUps.all{it in 0..3}){"The Gen 1 PP-Up values could not be represented safely."}
    require((0..3).all{i->source.moves[i]!=0||(source.movePP[i]==0&&source.movePpUps[i]==0)}){"An empty Gen 1 move slot contains PP or PP-Up data that cannot be preserved."}
    val out=ByteArray(136);val nature=(source.experience%25L).toInt()
    val shiny=source.dvs[2]==10&&source.dvs[3]==10&&source.dvs[4]==10&&source.dvs[1] in setOf(2,3,6,7,10,11,14,15)
    writeGen1ToGen4U32(out,0,pid);writeGen1ToGen4U16(out,0x08,dex);writeGen1ToGen4U16(out,0x0A,0);writeGen1ToGen4U16(out,0x0C,source.tid);writeGen1ToGen4U16(out,0x0E,0);writeGen1ToGen4U32(out,0x10,source.experience)
    out[0x14]=baseFriendship.toByte();out[0x15]=abilityId.toByte();out[0x17]=2
    val ev=intArrayOf((source.statExp[0]/257).coerceIn(0,255),(source.statExp[1]/257).coerceIn(0,255),(source.statExp[2]/257).coerceIn(0,255),(source.statExp[3]/257).coerceIn(0,255),(source.statExp[4]/257).coerceIn(0,255),(source.statExp[4]/257).coerceIn(0,255))
    var total=ev.sum();while(total>510){val i=ev.indices.maxByOrNull{ev[it]}?:0;if(ev[i]==0)break;ev[i]--;total--}
    for(i in 0..5)out[0x18+i]=ev[i].toByte()
    for(i in 0..3){writeGen1ToGen4U16(out,0x28+i*2,source.moves[i].coerceIn(0,467));out[0x30+i]=source.movePP[i].coerceIn(0,63).toByte();out[0x34+i]=source.movePpUps[i].toByte()}
    val iv=intArrayOf(source.dvs[0],source.dvs[1],source.dvs[2],source.dvs[3],source.dvs[4],source.dvs[4]).map{(it*2).coerceIn(0,31)}.toIntArray()
    var ivw=0L;for(i in 0..5)ivw=ivw or ((iv[i].toLong() and 31L) shl(i*5));writeGen1ToGen4U32(out,0x38,ivw)
    writeString(out,0x48,22,nickname);out[0x5F]=originGame.toByte();writeString(out,0x68,16,source.otName);out[0x83]=4;out[0x84]=source.level.coerceIn(1,100).toByte()
    return BuiltGen1ToGen4Pk4(encrypt(out),nature,shiny,ev,iv,nickname,originGame)
}

internal fun verifyGen1ToGen4Pk4Bytes(
    built:BuiltGen1ToGen4Pk4,
    source:MainActivity.Gen1Pokemon,
    converted:MainActivity.PokemonEntry?,
    dex:Int,
    baseFriendship:Int,
    abilityId:Int,
    ppUps:IntArray
):ByteArray {
    val q=converted?:error("The converted PK4 could not be parsed.")
    require(q.checksumValid&&q.species==dex&&q.tid==source.tid&&q.sid==0&&q.nickname==built.nickname&&q.otName==source.otName&&q.experience==source.experience&&q.nature==built.nature&&q.shiny==built.shiny&&q.ability==abilityId&&q.friendship==baseFriendship&&q.language==2&&q.heldItem==0&&q.ball==4&&q.metLevel==source.level.coerceIn(1,100)&&q.originGame==built.originGame&&q.moves.contentEquals(source.moves)&&q.movePP.contentEquals(source.movePP)&&ppUps.contentEquals(source.movePpUps)&&q.evs.contentEquals(built.evs)&&q.ivs.contentEquals(built.ivs)){"The converted PK4 failed identity or converted-field verification."}
    return built.encrypted
}

internal data class BuiltGen1ToGen5Pk5(
    val encrypted:ByteArray,
    val nature:Int,
    val shiny:Boolean,
    val gender:Int,
    val evs:IntArray,
    val ivs:IntArray,
    val nickname:String,
    val originGame:Int
)

internal fun gen1ToGen5GenderForRate(source:MainActivity.Gen1Pokemon,rate:Int):Int {
    if(rate<0)return 2
    if(rate<=0)return 0
    if(rate>=8)return 1
    return if((source.dvs[0]*16)<rate*32)1 else 0
}

internal fun buildGen1ToGen5Pk5Bytes(
    bankBytes:ByteArray,
    source:MainActivity.Gen1Pokemon,
    expectedBankSize:Int,
    baseFriendship:Int,
    abilityId:Int,
    dex:Int,
    pid:Long,
    gender:Int,
    nickname:String,
    originGame:Int,
    writeString:(ByteArray,Int,Int,String,Int)->Unit,
    encrypt:(ByteArray)->ByteArray
):BuiltGen1ToGen5Pk5 {
    require(bankBytes.size==expectedBankSize){"The preserved Gen 1 Bank record has an unexpected size."}
    require(source.movePpUps.all{it in 0..3}){"The Gen 1 PP-Up values could not be represented safely."}
    require((0..3).all{i->source.moves[i]!=0||(source.movePP[i]==0&&source.movePpUps[i]==0)}){"An empty Gen 1 move slot contains PP or PP-Up data that cannot be preserved."}
    val out=ByteArray(136);val nature=(source.experience%25L).toInt()
    val shiny=source.dvs[2]==10&&source.dvs[3]==10&&source.dvs[4]==10&&source.dvs[1] in setOf(2,3,6,7,10,11,14,15)
    writeGen1ToGen4U32(out,0,pid);writeGen1ToGen4U16(out,0x08,dex);writeGen1ToGen4U16(out,0x0C,source.tid);writeGen1ToGen4U16(out,0x0E,0);writeGen1ToGen4U32(out,0x10,source.experience)
    out[0x14]=baseFriendship.toByte();out[0x15]=abilityId.toByte();out[0x17]=2
    val ev=intArrayOf((source.statExp[0]/257).coerceIn(0,255),(source.statExp[1]/257).coerceIn(0,255),(source.statExp[2]/257).coerceIn(0,255),(source.statExp[3]/257).coerceIn(0,255),(source.statExp[4]/257).coerceIn(0,255),(source.statExp[4]/257).coerceIn(0,255))
    var total=ev.sum();while(total>510){val i=ev.indices.maxByOrNull{ev[it]}?:0;if(ev[i]==0)break;ev[i]--;total--}
    for(i in 0..5)out[0x18+i]=ev[i].toByte()
    for(i in 0..3){writeGen1ToGen4U16(out,0x28+i*2,source.moves[i].coerceIn(0,467));out[0x30+i]=source.movePP[i].coerceIn(0,63).toByte();out[0x34+i]=source.movePpUps[i].toByte()}
    val iv=intArrayOf(source.dvs[0],source.dvs[1],source.dvs[2],source.dvs[3],source.dvs[4],source.dvs[4]).map{(it*2).coerceIn(0,31)}.toIntArray()
    var ivw=0L;for(i in 0..5)ivw=ivw or ((iv[i].toLong() and 31L) shl(i*5));writeGen1ToGen4U32(out,0x38,ivw)
    out[0x40]=((gender and 3) shl 1).toByte();out[0x41]=nature.toByte();out[0x42]=0
    writeString(out,0x48,22,nickname,10);out[0x5F]=originGame.toByte();writeString(out,0x68,16,source.otName,7);out[0x83]=4;out[0x84]=source.level.coerceIn(1,100).toByte()
    return BuiltGen1ToGen5Pk5(encrypt(out),nature,shiny,gender,ev,iv,nickname,originGame)
}

internal fun verifyGen1ToGen5Pk5Bytes(
    built:BuiltGen1ToGen5Pk5,
    source:MainActivity.Gen1Pokemon,
    converted:MainActivity.PokemonEntry?,
    dex:Int,
    baseFriendship:Int,
    abilityId:Int,
    ppUps:IntArray
):ByteArray {
    val q=converted?:error("The converted PK5 could not be parsed.")
    require(q.checksumValid&&q.species==dex&&q.tid==source.tid&&q.sid==0&&q.nickname==built.nickname&&q.otName==source.otName&&q.experience==source.experience&&q.ability==abilityId&&q.abilityNumber==1&&q.gender==built.gender&&q.nature==built.nature&&q.shiny==built.shiny&&q.friendship==baseFriendship&&q.language==2&&q.heldItem==0&&q.ball==4&&q.metLevel==source.level.coerceIn(1,100)&&q.originGame==built.originGame&&q.moves.contentEquals(source.moves)&&q.movePP.contentEquals(source.movePP)&&ppUps.contentEquals(source.movePpUps)&&q.evs.contentEquals(built.evs)&&q.ivs.contentEquals(built.ivs)){"The converted PK5 failed identity or converted-field verification."}
    return built.encrypted
}

private fun gen1ToGen5Crc16Ccitt(data:ByteArray,start:Int,endExclusive:Int):Int {
    var crc=0xFFFF
    val end=endExclusive.coerceAtMost(data.size)
    var i=start.coerceAtLeast(0)
    while(i<end) {
        crc=crc xor ((data[i].toInt() and 0xFF) shl 8)
        repeat(8) {
            crc=if((crc and 0x8000)!=0) ((crc shl 1) xor 0x1021) and 0xFFFF
            else (crc shl 1) and 0xFFFF
        }
        i++
    }
    return crc and 0xFFFF
}

private fun writeGen1ToGen5U16(data:ByteArray,offset:Int,value:Int) {
    if(offset<0||offset+1>=data.size)return
    data[offset]=(value and 0xFF).toByte()
    data[offset+1]=((value ushr 8) and 0xFF).toByte()
}

internal fun gen5PokedexBlock(profile:MainActivity.Gen5Profile):Triple<Int,Int,Int> {
    val length=if(profile.sequel)0x04DC else 0x04D4
    val local=profile.pokedexOffset+length+2
    val mirror=if(profile.sequel)0x25F6C else 0x23F6E
    return Triple(length,local,mirror)
}

internal fun registerGen1ToGen5Pokedex(
    save:ByteArray,
    profile:MainActivity.Gen5Profile,
    pokemon:MainActivity.PokemonEntry
) {
    val dex=pokemon.species
    require(dex in 1..649)
    val bit=dex-1
    val mask=1 shl(bit and 7)
    val byte=bit/8
    val region=(if(pokemon.shiny)2 else 0) or (pokemon.gender and 1)
    val base=profile.pokedexOffset
    val caught=base+0x08+byte
    val seen=base+0x5C+region*0x54+byte
    val displayedRegions=(0..3).map { base+0x1AC+it*0x54+byte }
    for(offset in listOf(caught,seen)) {
        require(offset in save.indices)
        save[offset]=(save[offset].toInt() or mask).toByte()
    }
    if(displayedRegions.none { (save[it].toInt() and mask)!=0 }) {
        val offset=base+0x1AC+region*0x54+byte
        save[offset]=(save[offset].toInt() or mask).toByte()
    }
    if(dex<=493) {
        val languageBase=base+(if(profile.sequel)0x328 else 0x320)
        val languageBit=bit*7+1
        val offset=languageBase+languageBit/8
        save[offset]=(save[offset].toInt() or (1 shl(languageBit and 7))).toByte()
    }
    val length=if(profile.sequel)0x04DC else 0x04D4
    val local=profile.pokedexOffset+length+2
    val mirror=if(profile.sequel)0x25F6C else 0x23F6E
    val pokedexCrc=gen1ToGen5Crc16Ccitt(save,base,base+length)
    writeGen1ToGen5U16(save,local,pokedexCrc)
    writeGen1ToGen5U16(save,mirror,pokedexCrc)
    val tableCrc=gen1ToGen5Crc16Ccitt(
        save,
        profile.checksumTable,
        profile.checksumTable+profile.checksumTableLength
    )
    writeGen1ToGen5U16(save,profile.checksumBlockChecksumOffset,tableCrc)
}

internal fun gen1ToGen5DexRegistered(
    save:ByteArray,
    profile:MainActivity.Gen5Profile,
    pokemon:MainActivity.PokemonEntry
):Boolean {
    val bit=pokemon.species-1
    val mask=1 shl(bit and 7)
    val byte=bit/8
    val region=(if(pokemon.shiny)2 else 0) or (pokemon.gender and 1)
    val base=profile.pokedexOffset
    return (save[base+0x08+byte].toInt() and mask)!=0&&
        (save[base+0x5C+region*0x54+byte].toInt() and mask)!=0&&
        (0..3).any { (save[base+0x1AC+it*0x54+byte].toInt() and mask)!=0 }
}

internal fun gen5DestinationSlotIsStructurallyEmptyBytes(
    data:ByteArray,
    global:Int,
    parse:(ByteArray)->MainActivity.PokemonEntry?
):Boolean {
    if(global !in 0 until 720)return false
    val offset=0x00400+(global/30)*0x1000+(global%30)*136
    if(offset<0||offset+136>data.size)return false
    val raw=data.copyOfRange(offset,offset+136)
    return raw.all { it==0.toByte() }&&parse(raw)==null
}

internal fun parsePk6Bytes(
    data:ByteArray,
    offset:Int,
    size:Int,
    party:Boolean,
    maxSpecies:Int,
    storedSize:Int
):MainActivity.PokemonEntry? {
    if(offset<0||offset+size>data.size)return null
    val raw=data.copyOfRange(offset,offset+size)
    if(raw.all { it.toInt()==0 })return null
    val pk=decryptPk6Bytes(raw,storedSize)
    val species=readPk6U16(pk,0x08)
    if(species !in 1..maxSpecies)return null

    val heldItem=readPk6U16(pk,0x0A)
    val tid=readPk6U16(pk,0x0C)
    val sid=readPk6U16(pk,0x0E)
    val experience=readPk6U32(pk,0x10)
    val ability=pk[0x14].toInt() and 0xFF
    val abilityNumber=pk[0x15].toInt() and 0x07
    val pid=readPk6U32(pk,0x18)
    val nature=pk[0x1C].toInt() and 0xFF
    val genderForm=pk[0x1D].toInt() and 0xFF
    val gender=(genderForm ushr 1) and 0x3
    val form=genderForm ushr 3
    val evs=intArrayOf(
        pk[0x1E].toInt() and 0xFF,
        pk[0x1F].toInt() and 0xFF,
        pk[0x20].toInt() and 0xFF,
        pk[0x21].toInt() and 0xFF,
        pk[0x22].toInt() and 0xFF,
        pk[0x23].toInt() and 0xFF
    )
    val pokerusState=pk[0x2B].toInt() and 0xFF
    val pokerusDays=pokerusState and 0xF
    val pokerusStrain=pokerusState ushr 4
    val nickname=readPk6String(pk,0x40,26)
    val moves=intArrayOf(
        readPk6U16(pk,0x5A),readPk6U16(pk,0x5C),readPk6U16(pk,0x5E),readPk6U16(pk,0x60)
    )
    val movePP=intArrayOf(
        pk[0x62].toInt() and 0xFF,pk[0x63].toInt() and 0xFF,
        pk[0x64].toInt() and 0xFF,pk[0x65].toInt() and 0xFF
    )
    val iv32=readPk6U32(pk,0x74)
    val ivs=intArrayOf(
        ((iv32 ushr 0) and 0x1F).toInt(),
        ((iv32 ushr 5) and 0x1F).toInt(),
        ((iv32 ushr 10) and 0x1F).toInt(),
        ((iv32 ushr 15) and 0x1F).toInt(),
        ((iv32 ushr 20) and 0x1F).toInt(),
        ((iv32 ushr 25) and 0x1F).toInt()
    )
    val isEgg=(iv32 and 0x40000000L)!=0L
    val isNicknamed=(iv32 and 0x80000000L)!=0L
    val currentHandler=pk[0x93].toInt() and 0xFF
    val friendship=if(currentHandler==0)pk[0xCA].toInt() and 0xFF else pk[0xA2].toInt() and 0xFF
    val otName=readPk6String(pk,0xB0,26)
    val language=pk[0xE3].toInt() and 0xFF
    val markingValue=if(maxSpecies<=721)pk[0x2A].toInt() and 0xFF else readPk6U16(pk,0x16)
    val fatefulEncounter=(pk[0x1D].toInt() and 0x01)!=0
    val relearnMoves=intArrayOf(
        readPk6U16(pk,0x6A),readPk6U16(pk,0x6C),readPk6U16(pk,0x6E),readPk6U16(pk,0x70)
    )
    val eggYear=pk[0xD1].toInt() and 0xFF
    val eggMonth=pk[0xD2].toInt() and 0xFF
    val eggDay=pk[0xD3].toInt() and 0xFF
    val metYear=pk[0xD4].toInt() and 0xFF
    val metMonth=pk[0xD5].toInt() and 0xFF
    val metDay=pk[0xD6].toInt() and 0xFF
    val eggLocation=readPk6U16(pk,0xD8)
    val metLocation=readPk6U16(pk,0xDA)
    val ball=pk[0xDC].toInt() and 0xFF
    val metLevelGender=pk[0xDD].toInt() and 0xFF
    val metLevel=metLevelGender and 0x7F
    val otGender=(metLevelGender ushr 7) and 1
    val hyperTrainFlags=pk[0xDE].toInt() and 0xFF
    val originGame=pk[0xDF].toInt() and 0xFF
    val country=pk[0xE0].toInt() and 0xFF
    val region=pk[0xE1].toInt() and 0xFF
    val consoleRegion=pk[0xE2].toInt() and 0xFF

    val expectedChecksum=readPk6U16(pk,0x06)
    var calc=0
    var pos=0x08
    while(pos+1<storedSize) {
        calc=(calc+readPk6U16(pk,pos)) and 0xFFFF
        pos+=2
    }
    val level=if(party&&pk.size>0xEC)pk[0xEC].toInt() and 0xFF else null
    val shinyValue=tid xor sid xor (pid.toInt() and 0xFFFF) xor ((pid.toInt() ushr 16) and 0xFFFF)
    val shiny=shinyValue<16

    return MainActivity.PokemonEntry(
        species,nickname,isNicknamed,level,calc==expectedChecksum,
        heldItem,tid,sid,experience,ability,abilityNumber,pid,nature,gender,form,
        evs,moves,movePP,ivs,isEgg,otName,friendship,currentHandler,
        pokerusDays,pokerusStrain,shiny,language,markingValue,
        fatefulEncounter,relearnMoves,eggLocation,metLocation,ball,metLevel,otGender,
        originGame,country,region,consoleRegion,eggYear,eggMonth,eggDay,
        metYear,metMonth,metDay,hyperTrainFlags
    )
}

private const val PSE_PK6_BLOCK_SIZE=56

private val PSE_PK6_BLOCK_POSITION_INVERT=intArrayOf(
    0,1,2,4,3,5,6,7,12,18,13,19,8,10,14,20,
    16,22,9,11,15,21,17,23,0,1,2,4,3,5,6,7
)

private val PSE_PK6_BLOCK_POSITION=intArrayOf(
    0,1,2,3,0,1,3,2,0,2,1,3,0,3,1,2,
    0,2,3,1,0,3,2,1,1,0,2,3,1,0,3,2,
    2,0,1,3,3,0,1,2,2,0,3,1,3,0,2,1,
    1,2,0,3,1,3,0,2,2,1,0,3,3,1,0,2,
    2,3,0,1,3,2,0,1,1,2,3,0,1,3,2,0,
    2,1,3,0,3,1,2,0,2,3,1,0,3,2,1,0,
    0,1,2,3,0,1,3,2,0,2,1,3,0,3,1,2,
    0,2,3,1,0,3,2,1,1,0,2,3,1,0,3,2
)

private fun readPk6U16(data:ByteArray,offset:Int):Int {
    if(offset<0||offset+1>=data.size)return 0
    return (data[offset].toInt() and 0xFF) or ((data[offset+1].toInt() and 0xFF) shl 8)
}

private fun readPk6U32(data:ByteArray,offset:Int):Long {
    if(offset<0||offset+3>=data.size)return 0
    return (data[offset].toLong() and 0xFF) or
        ((data[offset+1].toLong() and 0xFF) shl 8) or
        ((data[offset+2].toLong() and 0xFF) shl 16) or
        ((data[offset+3].toLong() and 0xFF) shl 24)
}

private fun readPk6String(data:ByteArray,offset:Int,byteLength:Int):String {
    val sb=StringBuilder()
    var p=offset
    val end=minOf(offset+byteLength,data.size)
    while(p+1<end) {
        val value=readPk6U16(data,p)
        if(value==0||value==0xFFFF)break
        if(value in 0xD800..0xDFFF)sb.append('�') else sb.append(value.toChar())
        p+=2
    }
    return sb.toString().trimEnd('\u0000')
}

private fun cryptPk6Bytes(data:ByteArray,start:Int,end:Int,initialSeed:Long) {
    var seed=initialSeed
    var p=start
    while(p+1<end) {
        seed=(0x41C64E6DL*seed+0x6073L) and 0xFFFFFFFFL
        val xor=((seed ushr 16) and 0xFFFF).toInt()
        val value=readPk6U16(data,p) xor xor
        data[p]=(value and 0xFF).toByte()
        data[p+1]=((value ushr 8) and 0xFF).toByte()
        p+=2
    }
}

private fun swapPk6ByteBlocks(data:ByteArray,a:Int,b:Int) {
    for(i in 0 until PSE_PK6_BLOCK_SIZE) {
        val t=data[a+i]
        data[a+i]=data[b+i]
        data[b+i]=t
    }
}

private fun shufflePk6Bytes(data:ByteArray,start:Int,sv:Int) {
    if(sv==0)return
    val base=sv*4
    val perm=intArrayOf(0,1,2,3)
    val slotOf=intArrayOf(0,1,2,3)
    for(i in 0 until 3) {
        val desired=PSE_PK6_BLOCK_POSITION[base+i]
        val j=slotOf[desired]
        if(j==i)continue
        swapPk6ByteBlocks(data,start+i*PSE_PK6_BLOCK_SIZE,start+j*PSE_PK6_BLOCK_SIZE)
        val blockAtI=perm[i]
        perm[j]=blockAtI
        slotOf[blockAtI]=j
    }
}

internal fun decryptPk6Bytes(input:ByteArray,storedSize:Int):ByteArray {
    val data=input.copyOf()
    if(data.size<storedSize)return data
    val encrypted=readPk6U16(data,0xC8)!=0||readPk6U16(data,0x58)!=0
    if(!encrypted)return data
    val ec=readPk6U32(data,0) and 0xFFFFFFFFL
    cryptPk6Bytes(data,0x08,storedSize,ec)
    if(data.size>storedSize)cryptPk6Bytes(data,storedSize,data.size,ec)
    shufflePk6Bytes(data,0x08,((ec ushr 13) and 31).toInt())
    return data
}

internal fun updatePk6ChecksumBytes(pk:ByteArray,storedSize:Int) {
    var sum=0
    var pos=0x08
    while(pos+1<storedSize) {
        sum=(sum+readPk6U16(pk,pos)) and 0xFFFF
        pos+=2
    }
    if(0x07<pk.size) {
        pk[0x06]=(sum and 0xFF).toByte()
        pk[0x07]=((sum ushr 8) and 0xFF).toByte()
    }
}

internal fun encryptPk6Bytes(decrypted:ByteArray,storedSize:Int):ByteArray {
    val data=decrypted.copyOf()
    if(data.size<storedSize)return data
    val ec=readPk6U32(data,0) and 0xFFFFFFFFL
    val sv=((ec ushr 13) and 31).toInt()
    shufflePk6Bytes(data,0x08,PSE_PK6_BLOCK_POSITION_INVERT[sv])
    cryptPk6Bytes(data,0x08,storedSize,ec)
    if(data.size>storedSize)cryptPk6Bytes(data,storedSize,data.size,ec)
    return data
}

internal data class BuiltGen3ToGen7Pk7(
    val encrypted:ByteArray,
    val pid:Long,
    val customNickname:Boolean,
    val expectedNickname:String,
    val expectedMoves:IntArray,
    val expectedMovePp:IntArray,
    val expectedEvs:IntArray,
    val expectedIvs:IntArray,
    val expectedGender:Int,
    val trainerName:String,
    val baseFriendship:Int,
    val abilityId:Int
)

internal fun buildGen3ToGen7Pk7Bytes(
    source:MainActivity.PokemonEntry,
    trainerName:String,
    trainerGender:String,
    speciesName:String,
    baseFriendship:Int,
    genderRate:Int,
    abilityId:Int,
    ppUps:IntArray,
    storedSize:Int,
    randomU32:(java.security.SecureRandom)->Long,
    forceShinyState:(Long,Int,Int,Boolean)->Long,
    transferGender:(MainActivity.PokemonEntry,Int)->Int,
    writeString:(ByteArray,Int,Int,String)->Unit,
    updateChecksum:(ByteArray)->Unit,
    encrypt:(ByteArray)->ByteArray
):BuiltGen3ToGen7Pk7 {
    require(ppUps.all { it in 0..3 }&&(0..3).all { source.moves[it]!=0||ppUps[it]==0 }){
        "The source PP-Up data cannot be preserved exactly."
    }
    val pk=ByteArray(storedSize)
    writeGen1ToGen4U32(pk,0x00,randomU32(java.security.SecureRandom()))
    writeGen1ToGen4U16(pk,0x04,0)
    writeGen1ToGen4U16(pk,0x08,source.species)
    writeGen1ToGen4U16(pk,0x0A,0)
    writeGen1ToGen4U16(pk,0x0C,source.tid)
    writeGen1ToGen4U16(pk,0x0E,source.sid)
    writeGen1ToGen4U32(pk,0x10,source.experience)
    pk[0x14]=abilityId.toByte()
    pk[0x15]=source.abilityNumber.coerceIn(1,2).toByte()
    writeGen1ToGen4U16(pk,0x16,0)
    val pid=forceShinyState(source.pid,source.tid,source.sid,source.shiny)
    writeGen1ToGen4U32(pk,0x18,pid)
    pk[0x1C]=source.nature.coerceIn(0,24).toByte()
    val expectedGender=transferGender(source,genderRate)
    pk[0x1D]=((expectedGender and 3) shl 1).toByte()
    val expectedEvs=IntArray(6){source.evs.getOrElse(it){0}.coerceIn(0,255)}
    for(i in 0 until 6)pk[0x1E+i]=expectedEvs[i].toByte()
    pk[0x2B]=(((source.pokerusStrain and 0xF) shl 4) or (source.pokerusDays and 0xF)).toByte()
    val custom=source.nickname.isNotBlank()&&!source.nickname.equals(speciesName,true)
    val expectedNickname=if(source.nickname.isBlank())speciesName else source.nickname
    writeString(pk,0x40,26,expectedNickname)
    val expectedMoves=IntArray(4){source.moves.getOrElse(it){0}}
    val expectedMovePp=IntArray(4){i->val move=expectedMoves[i];if(move==0)0 else (source.movePP.getOrElse(i){0} and 0x3F).coerceIn(0,63)}
    for(i in 0..3) {
        val move=expectedMoves[i]
        writeGen1ToGen4U16(pk,0x5A+i*2,move)
        pk[0x62+i]=expectedMovePp[i].toByte()
        pk[0x66+i]=if(move==0)0 else ppUps[i].toByte()
    }
    val expectedIvs=IntArray(6){source.ivs.getOrElse(it){0} and 31}
    var ivWord=0L
    for(i in 0..5)ivWord=ivWord or ((expectedIvs[i].toLong() and 31L) shl(i*5))
    if(custom)ivWord=ivWord or 0x80000000L
    writeGen1ToGen4U32(pk,0x74,ivWord)
    writeString(pk,0x78,26,trainerName)
    pk[0x92]=if(trainerGender.equals("Female",true))1 else 0
    pk[0x93]=1
    pk[0xA2]=baseFriendship.toByte()
    writeString(pk,0xB0,26,source.otName)
    pk[0xCA]=source.friendship.coerceIn(0,255).toByte()
    val calendar=java.util.Calendar.getInstance()
    pk[0xD4]=(calendar.get(java.util.Calendar.YEAR)-2000).coerceIn(0,255).toByte()
    pk[0xD5]=(calendar.get(java.util.Calendar.MONTH)+1).toByte()
    pk[0xD6]=calendar.get(java.util.Calendar.DAY_OF_MONTH).toByte()
    writeGen1ToGen4U16(pk,0xD8,0)
    writeGen1ToGen4U16(pk,0xDA,30001)
    pk[0xDC]=source.ball.toByte()
    pk[0xDD]=(source.metLevel and 0x7F).toByte()
    pk[0xDE]=0
    pk[0xDF]=source.originGame.toByte()
    pk[0xE0]=0
    pk[0xE1]=0
    pk[0xE2]=0
    pk[0xE3]=source.language.toByte()
    updateChecksum(pk)
    return BuiltGen3ToGen7Pk7(encrypt(pk),pid,custom,expectedNickname,expectedMoves,expectedMovePp,expectedEvs,expectedIvs,expectedGender,trainerName,baseFriendship,abilityId)
}

internal fun verifyGen3ToGen7Pk7Bytes(
    built:BuiltGen3ToGen7Pk7,
    source:MainActivity.PokemonEntry,
    converted:MainActivity.PokemonEntry?,
    handlerName:String,
    verifiedRaw:ByteArray,
    ppUps:IntArray
):ByteArray {
    val pokemon=converted?:error("The converted PK7 could not be parsed.")
    val rawMetadataValid=(verifiedRaw[0x93].toInt() and 0xFF)==1&&
        (verifiedRaw[0xA2].toInt() and 0xFF)==built.baseFriendship&&
        (verifiedRaw[0xCA].toInt() and 0xFF)==source.friendship.coerceIn(0,255)&&
        (0..3).all { i->(verifiedRaw[0x66+i].toInt() and 3)==if(built.expectedMoves[i]==0)0 else ppUps[i] }
    require(pokemon.checksumValid&&pokemon.species==source.species&&pokemon.tid==source.tid&&pokemon.sid==source.sid&&pokemon.shiny==source.shiny&&
        pokemon.experience==source.experience&&pokemon.ability==built.abilityId&&pokemon.abilityNumber==source.abilityNumber.coerceIn(1,2)&&
        pokemon.pid==built.pid&&pokemon.nature==source.nature.coerceIn(0,24)&&pokemon.gender==built.expectedGender&&
        pokemon.nickname==built.expectedNickname&&pokemon.isNicknamed==built.customNickname&&pokemon.otName==source.otName&&
        handlerName==built.trainerName&&pokemon.evs.contentEquals(built.expectedEvs)&&pokemon.moves.contentEquals(built.expectedMoves)&&
        pokemon.movePP.contentEquals(built.expectedMovePp)&&pokemon.ivs.contentEquals(built.expectedIvs)&&pokemon.currentHandler==1&&
        pokemon.friendship==built.baseFriendship&&pokemon.pokerusDays==(source.pokerusDays and 0xF)&&
        pokemon.pokerusStrain==(source.pokerusStrain and 0xF)&&!pokemon.isEgg&&pokemon.heldItem==0&&pokemon.eggLocation==0&&
        pokemon.metLocation==30001&&pokemon.ball==source.ball&&pokemon.metLevel==source.metLevel&&pokemon.originGame==source.originGame&&
        pokemon.language==source.language&&rawMetadataValid){
        "The converted PK7 failed round-trip verification."
    }
    return built.encrypted
}

internal fun gen2ForwardBuiltPkMismatchBytes(
    pokemon:MainActivity.PokemonEntry,
    source:MainActivity.Gen2Pokemon,
    expectedName:String,
    customNickname:Boolean,
    abilityId:Int,
    abilityNumber:Int,
    pid:Long,
    nature:Int,
    gender:Int,
    evs:IntArray,
    expectedPpUps:IntArray,
    ivs:IntArray,
    language:Int,
    fateful:Boolean,
    originGame:Int,
    actualPpUps:IntArray,
    actualHandlerName:String,
    expectedHandlerName:String
):String?=when {
    !pokemon.checksumValid->"checksum"
    pokemon.species!=source.species->"species"
    pokemon.nickname!=expectedName->"nickname"
    pokemon.isNicknamed!=customNickname->"nickname flag"
    pokemon.level!=source.level->"level"
    pokemon.heldItem!=0->"held item"
    pokemon.tid!=source.tid->"Trainer ID"
    pokemon.sid!=0->"Secret ID"
    pokemon.experience!=source.experience->"experience"
    pokemon.ability!=abilityId->"ability"
    pokemon.abilityNumber!=abilityNumber->"ability slot"
    pokemon.pid!=pid->"PID"
    pokemon.nature!=nature->"nature"
    pokemon.gender!=gender->"gender"
    pokemon.form!=0->"form"
    !pokemon.evs.contentEquals(evs)->"EVs"
    !pokemon.moves.contentEquals(source.moves)->"moves"
    !pokemon.movePP.contentEquals(source.movePP)->"move PP"
    !actualPpUps.contentEquals(expectedPpUps)->"PP-Up data"
    !pokemon.ivs.contentEquals(ivs)->"IVs"
    pokemon.isEgg->"Egg flag"
    pokemon.otName!=source.otName->"Original Trainer name"
    pokemon.friendship!=source.happiness->"friendship"
    pokemon.currentHandler!=1->"current handler"
    actualHandlerName!=expectedHandlerName->"current handler name"
    pokemon.pokerusDays!=(source.pokerus and 0xF)||pokemon.pokerusStrain!=((source.pokerus ushr 4) and 0xF)->"Pokérus"
    pokemon.shiny!=source.shiny->"shiny state"
    pokemon.language!=language->"language"
    pokemon.markingValue!=0->"markings"
    pokemon.fatefulEncounter!=fateful->"fateful encounter flag"
    pokemon.relearnMoves.any { it!=0 }->"relearn moves"
    pokemon.eggLocation!=0->"Egg location"
    pokemon.metLocation!=30017->"met location"
    pokemon.ball!=4->"Poké Ball"
    pokemon.metLevel!=source.level.coerceIn(1,100)->"met level"
    pokemon.otGender!=0->"Original Trainer gender"
    pokemon.originGame!=originGame->"origin game"
    pokemon.country!=0->"country"
    pokemon.region!=0->"region"
    pokemon.consoleRegion!=0->"console region"
    pokemon.eggYear!=0||pokemon.eggMonth!=0||pokemon.eggDay!=0->"Egg date"
    pokemon.metYear!=0||pokemon.metMonth!=0||pokemon.metDay!=0->"met date"
    pokemon.hyperTrainFlags!=0->"Hyper Training flags"
    else->null
}

internal data class BuiltGen2ToGen7Pk7(
    val encrypted:ByteArray,
    val pid:Long,
    val language:Int,
    val fateful:Boolean
)

internal fun buildGen2ToGen7Pk7Bytes(
    source:MainActivity.Gen2Pokemon,
    trainerName:String,
    trainerGender:String,
    transferMeta:MainActivity.Gen1ToGen7TransferMeta,
    nature:Int,
    gender:Int,
    originGame:Int,
    ppUps:IntArray,
    evs:IntArray,
    ivs:IntArray,
    expectedName:String,
    customNickname:Boolean,
    storedSize:Int,
    destinationLanguage:Int,
    forceShinyState:(Long,Int,Boolean)->Long,
    writeString:(ByteArray,Int,Int,String)->Unit,
    updateChecksum:(ByteArray)->Unit,
    encrypt:(ByteArray)->ByteArray
):BuiltGen2ToGen7Pk7 {
    val pk=ByteArray(storedSize)
    var ec=((source.tid.toLong() shl 16) xor source.experience xor (source.species.toLong() shl 8) xor source.caughtData.toLong() xor 0x47503237L) and 0xFFFFFFFFL
    if(ec==0L)ec=1L
    writeGen1ToGen4U32(pk,0x00,ec)
    writeGen1ToGen4U16(pk,0x04,0)
    writeGen1ToGen4U16(pk,0x08,source.species)
    writeGen1ToGen4U16(pk,0x0A,0)
    writeGen1ToGen4U16(pk,0x0C,source.tid)
    writeGen1ToGen4U16(pk,0x0E,0)
    writeGen1ToGen4U32(pk,0x10,source.experience)
    pk[0x14]=transferMeta.abilityId.toByte()
    pk[0x15]=transferMeta.abilityNumber.toByte()
    writeGen1ToGen4U16(pk,0x16,0)
    val pid=forceShinyState((ec xor 0xA5A55A5AL) and 0xFFFFFFFFL,source.tid,source.shiny)
    writeGen1ToGen4U32(pk,0x18,pid)
    pk[0x1C]=nature.toByte()
    val fateful=source.species==251
    pk[0x1D]=(((gender and 3) shl 1) or (if(fateful)1 else 0)).toByte()
    for(i in 0..5)pk[0x1E+i]=evs[i].toByte()
    pk[0x2B]=source.pokerus.toByte()
    writeString(pk,0x40,26,expectedName)
    for(i in 0..3) {
        val move=source.moves[i]
        writeGen1ToGen4U16(pk,0x5A+i*2,move)
        pk[0x62+i]=if(move==0)0 else source.movePP[i].coerceIn(0,63).toByte()
        pk[0x66+i]=if(move==0)0 else ppUps[i].toByte()
    }
    var ivWord=0L
    for(i in 0..5)ivWord=ivWord or ((ivs[i].toLong() and 31L) shl(i*5))
    if(customNickname)ivWord=ivWord or 0x80000000L
    writeGen1ToGen4U32(pk,0x74,ivWord)
    writeString(pk,0x78,26,trainerName)
    pk[0x92]=if(trainerGender.equals("Female",true))1 else 0
    pk[0x93]=1
    pk[0xA2]=source.happiness.toByte()
    writeString(pk,0xB0,26,source.otName)
    pk[0xCA]=source.happiness.toByte()
    writeGen1ToGen4U16(pk,0xD8,0)
    writeGen1ToGen4U16(pk,0xDA,30017)
    pk[0xDC]=4
    pk[0xDD]=(source.level.coerceIn(1,100) and 0x7F).toByte()
    pk[0xDF]=originGame.toByte()
    val language=destinationLanguage.coerceIn(1,10)
    pk[0xE3]=language.toByte()
    updateChecksum(pk)
    return BuiltGen2ToGen7Pk7(encrypt(pk),pid,language,fateful)
}

internal data class BuiltGen1ToGen7Pk7(
    val encrypted:ByteArray,
    val dex:Int,
    val nature:Int,
    val transferExperience:Long,
    val pid:Long,
    val shiny:Boolean,
    val gender:Int,
    val fateful:Boolean,
    val nickname:String,
    val customNickname:Boolean,
    val healedPp:IntArray,
    val ivs:IntArray,
    val language:Int,
    val metYear:Int,
    val metMonth:Int,
    val metDay:Int,
    val trainerName:String,
    val baseFriendship:Int,
    val abilityId:Int,
    val abilityNumber:Int
)

internal fun buildGen1ToGen7Pk7Bytes(
    sourceBankBytes:ByteArray,
    source:MainActivity.Gen1Pokemon,
    destinationSize:Int,
    trainerName:String,
    trainerGender:String,
    speciesMeta:MainActivity.Gen1SpeciesMeta,
    transferMeta:MainActivity.Gen1ToGen7TransferMeta,
    dex:Int,
    defaultName:String,
    expectedBankSize:Int,
    smSize:Int,
    usumSize:Int,
    storedSize:Int,
    destinationLanguage:Int,
    randomU32:(java.security.SecureRandom)->Long,
    shuffledIvs:(Int,java.security.SecureRandom)->IntArray,
    forceShinyState:(Long,Int,Boolean)->Long,
    experienceForLevel:(String,Int)->Long,
    shinyFromDvs:(IntArray)->Boolean,
    transferGender:(MainActivity.Gen1Pokemon,Int)->Int,
    writeString:(ByteArray,Int,Int,String)->Unit,
    updateChecksum:(ByteArray)->Unit,
    encrypt:(ByteArray)->ByteArray
):BuiltGen1ToGen7Pk7 {
    require(destinationSize==smSize||destinationSize==usumSize){"A Generation 7 destination save is required."}
    require(sourceBankBytes.size==expectedBankSize){"The preserved Gen 1 Bank record has an unexpected size."}
    require(source.moves.all { it in 0..165 }){"A Gen 1 move could not be represented in Generation 7."}
    require(source.movePpUps.all { it in 0..3 }){"The Gen 1 PP-Up values could not be represented safely."}
    require((0..3).all { i->source.moves[i]!=0||(source.movePP[i]==0&&source.movePpUps[i]==0) }){"An empty Gen 1 move slot contains PP or PP-Up data that cannot be preserved."}
    val rng=java.security.SecureRandom()
    val pk=ByteArray(storedSize)
    val ec=randomU32(rng)
    writeGen1ToGen4U32(pk,0x00,ec)
    writeGen1ToGen4U16(pk,0x04,0)
    writeGen1ToGen4U16(pk,0x08,dex)
    writeGen1ToGen4U16(pk,0x0A,0)
    writeGen1ToGen4U16(pk,0x0C,source.tid)
    writeGen1ToGen4U16(pk,0x0E,0)
    val nature=(source.experience%25L).toInt().coerceIn(0,24)
    val transferExperience=experienceForLevel(speciesMeta.growth,source.level)
    writeGen1ToGen4U32(pk,0x10,transferExperience)
    pk[0x14]=transferMeta.abilityId.toByte()
    pk[0x15]=transferMeta.abilityNumber.toByte()
    writeGen1ToGen4U16(pk,0x16,0)
    val shiny=shinyFromDvs(source.dvs)
    val pid=forceShinyState(randomU32(rng),source.tid,shiny)
    writeGen1ToGen4U32(pk,0x18,pid)
    pk[0x1C]=nature.toByte()
    val gender=transferGender(source,transferMeta.genderRate)
    val fateful=dex==151
    pk[0x1D]=(((gender and 3) shl 1) or (if(fateful)1 else 0)).toByte()
    for(i in 0 until 6)pk[0x1E+i]=0
    pk[0x2B]=0
    val custom=source.nickname.isNotBlank()&&!source.nickname.equals(defaultName,true)
    val nickname=if(custom)source.nickname else defaultName
    writeString(pk,0x40,26,nickname)
    val healedPp=IntArray(4)
    for(i in 0..3) {
        val move=source.moves[i]
        writeGen1ToGen4U16(pk,0x5A+i*2,move)
        if(move==0) {
            healedPp[i]=0
            pk[0x62+i]=0
            pk[0x66+i]=0
        } else {
            val base=transferMeta.moveBasePp[move]?:error("Move #$move has no documented Generation 7 base-PP mapping.")
            require(base in 1..63){"Move #$move has an invalid Generation 7 base-PP mapping."}
            val ppUps=source.movePpUps[i]
            healedPp[i]=(base*(5+ppUps)/5).coerceIn(1,63)
            pk[0x62+i]=healedPp[i].toByte()
            pk[0x66+i]=ppUps.toByte()
        }
    }
    val ivs=shuffledIvs(if(dex==151)5 else 3,rng)
    var ivWord=0L
    for(i in 0..5)ivWord=ivWord or ((ivs[i].toLong() and 31L) shl(i*5))
    if(custom)ivWord=ivWord or 0x80000000L
    writeGen1ToGen4U32(pk,0x74,ivWord)
    writeString(pk,0x78,26,trainerName)
    pk[0x92]=if(trainerGender.equals("Female",true))1 else 0
    pk[0x93]=1
    pk[0xA2]=transferMeta.baseFriendship.toByte()
    writeString(pk,0xB0,26,source.otName)
    pk[0xCA]=transferMeta.baseFriendship.toByte()
    val calendar=java.util.Calendar.getInstance()
    val metYear=(calendar.get(java.util.Calendar.YEAR)-2000).coerceIn(0,255)
    val metMonth=calendar.get(java.util.Calendar.MONTH)+1
    val metDay=calendar.get(java.util.Calendar.DAY_OF_MONTH)
    pk[0xD4]=metYear.toByte()
    pk[0xD5]=metMonth.toByte()
    pk[0xD6]=metDay.toByte()
    writeGen1ToGen4U16(pk,0xD8,0)
    writeGen1ToGen4U16(pk,0xDA,30013)
    pk[0xDC]=4
    pk[0xDD]=(source.level.coerceIn(1,100) and 0x7F).toByte()
    pk[0xDE]=0
    pk[0xDF]=35
    pk[0xE0]=0
    pk[0xE1]=0
    pk[0xE2]=0
    val language=destinationLanguage.coerceIn(1,10)
    pk[0xE3]=language.toByte()
    updateChecksum(pk)
    return BuiltGen1ToGen7Pk7(encrypt(pk),dex,nature,transferExperience,pid,shiny,gender,fateful,nickname,custom,healedPp,ivs,language,metYear,metMonth,metDay,trainerName,transferMeta.baseFriendship,transferMeta.abilityId,transferMeta.abilityNumber)
}

internal fun verifyGen1ToGen7Pk7Bytes(
    built:BuiltGen1ToGen7Pk7,
    source:MainActivity.Gen1Pokemon,
    converted:MainActivity.PokemonEntry?,
    ppUps:IntArray,
    handlerName:String
):ByteArray {
    val pokemon=converted?:throw IllegalArgumentException("The converted PK7 could not be parsed.")
    val mismatch=when {
        !pokemon.checksumValid->"PK7 checksum"
        pokemon.species!=built.dex->"species"
        pokemon.nickname!=built.nickname->"nickname"
        pokemon.isNicknamed!=built.customNickname->"nickname flag"
        pokemon.level!=source.level->"level"
        pokemon.heldItem!=0->"held item"
        pokemon.tid!=source.tid->"Trainer ID"
        pokemon.sid!=0->"Secret ID"
        pokemon.experience!=built.transferExperience->"experience"
        pokemon.ability!=built.abilityId->"ability"
        pokemon.abilityNumber!=built.abilityNumber->"ability slot"
        pokemon.pid!=built.pid->"PID"
        pokemon.nature!=built.nature->"nature"
        pokemon.gender!=built.gender->"gender"
        pokemon.form!=0->"form"
        pokemon.evs.any { it!=0 }->"EVs"
        !pokemon.moves.contentEquals(source.moves)->"moves"
        !pokemon.movePP.contentEquals(built.healedPp)->"move PP"
        !ppUps.contentEquals(source.movePpUps)->"move PP Ups"
        !pokemon.ivs.contentEquals(built.ivs)->"IVs"
        pokemon.isEgg->"Egg flag"
        pokemon.otName!=source.otName->"Original Trainer name"
        pokemon.friendship!=built.baseFriendship->"friendship"
        pokemon.currentHandler!=1->"current handler"
        handlerName!=built.trainerName->"current handler name"
        pokemon.pokerusDays!=0||pokemon.pokerusStrain!=0->"Pokérus"
        pokemon.shiny!=built.shiny->"shiny state"
        pokemon.language!=built.language->"language"
        pokemon.markingValue!=0->"markings"
        pokemon.fatefulEncounter!=built.fateful->"fateful encounter flag"
        pokemon.relearnMoves.any { it!=0 }->"relearn moves"
        pokemon.eggLocation!=0->"Egg location"
        pokemon.metLocation!=30013->"met location"
        pokemon.ball!=4->"Poké Ball"
        pokemon.metLevel!=source.level.coerceIn(1,100)->"met level"
        pokemon.otGender!=0->"Original Trainer gender"
        pokemon.originGame!=35->"origin game"
        pokemon.country!=0->"country"
        pokemon.region!=0->"region"
        pokemon.consoleRegion!=0->"console region"
        pokemon.eggYear!=0||pokemon.eggMonth!=0||pokemon.eggDay!=0->"Egg date"
        pokemon.metYear!=built.metYear||pokemon.metMonth!=built.metMonth||pokemon.metDay!=built.metDay->"met date"
        pokemon.hyperTrainFlags!=0->"Hyper Training flags"
        else->null
    }
    require(mismatch==null){"The converted PK7 failed exact converted-field verification ($mismatch)."}
    return built.encrypted
}

internal data class BuiltGen1ToGen6Pk6(
    val encrypted:ByteArray,
    val dex:Int,
    val shiny:Boolean,
    val nature:Int,
    val gender:Int,
    val evs:IntArray,
    val ivs:IntArray,
    val nickname:String,
    val customNickname:Boolean,
    val language:Int,
    val metYear:Int,
    val metMonth:Int,
    val metDay:Int,
    val trainerName:String
)

internal fun buildGen1ToGen6Pk6Bytes(
    bankBytes:ByteArray,
    source:MainActivity.Gen1Pokemon,
    expectedBankSize:Int,
    destinationSize:Int,
    xySize:Int,
    orasSize:Int,
    trainerGameId:Int,
    trainerName:String,
    trainerGender:String,
    baseFriendship:Int,
    abilityId:Int,
    genderRate:Int,
    dex:Int,
    storedSize:Int,
    defaultName:String,
    destinationLanguage:Int,
    shinyFromDvs:(IntArray)->Boolean,
    forceShinyState:(Long,Int,Boolean)->Long,
    writeString:(ByteArray,Int,Int,String)->Unit,
    updateChecksum:(ByteArray)->Unit,
    encrypt:(ByteArray)->ByteArray
):BuiltGen1ToGen6Pk6 {
    require(bankBytes.size==expectedBankSize){"The preserved Gen 1 Bank record has an unexpected size."}
    require(destinationSize==xySize||destinationSize==orasSize){"A valid Generation 6 destination save is required."}
    require(trainerGameId in 24..27){"PSE could not identify the loaded Generation 6 title."}
    require(source.moves.all { it in 0..165 }){"A Gen 1 move could not be represented in Generation 6."}
    require(source.movePpUps.all { it in 0..3 }){"The Gen 1 PP-Up values could not be represented safely."}
    require((0..3).all { i->source.moves[i]!=0||(source.movePP[i]==0&&source.movePpUps[i]==0) }){"An empty Gen 1 move slot contains PP or PP-Up data that cannot be preserved."}
    val out=ByteArray(storedSize)
    var ec=((source.tid.toLong() shl 16) xor source.experience xor dex.toLong() xor 0x47503136L) and 0xFFFFFFFFL
    if(ec==0L)ec=1L
    writeGen1ToGen4U32(out,0,ec)
    writeGen1ToGen4U16(out,0x08,dex)
    writeGen1ToGen4U16(out,0x0C,source.tid)
    writeGen1ToGen4U16(out,0x0E,0)
    writeGen1ToGen4U32(out,0x10,source.experience)
    out[0x14]=abilityId.toByte()
    out[0x15]=1
    val shiny=shinyFromDvs(source.dvs)
    val pid=forceShinyState((ec xor 0xA5A55A5AL) and 0xFFFFFFFFL,source.tid,shiny)
    writeGen1ToGen4U32(out,0x18,pid)
    val nature=(source.experience%25L).toInt()
    out[0x1C]=nature.toByte()
    val gender=gen1ToGen5GenderForRate(source,genderRate)
    out[0x1D]=((gender and 3) shl 1).toByte()
    val evs=intArrayOf(
        (source.statExp[0]/257).coerceIn(0,252),
        (source.statExp[1]/257).coerceIn(0,252),
        (source.statExp[2]/257).coerceIn(0,252),
        (source.statExp[3]/257).coerceIn(0,252),
        (source.statExp[4]/257).coerceIn(0,252),
        (source.statExp[4]/257).coerceIn(0,252)
    )
    var total=evs.sum()
    while(total>510) {
        val index=evs.indices.maxByOrNull { evs[it] }?:0
        if(evs[index]==0)break
        evs[index]--
        total--
    }
    for(i in 0..5)out[0x1E+i]=evs[i].toByte()
    val custom=source.nickname.isNotBlank()&&!source.nickname.equals(defaultName,true)
    val nickname=if(custom)source.nickname else defaultName
    writeString(out,0x40,26,nickname)
    for(i in 0..3) {
        writeGen1ToGen4U16(out,0x5A+i*2,source.moves[i])
        out[0x62+i]=source.movePP[i].coerceIn(0,63).toByte()
        out[0x66+i]=source.movePpUps[i].toByte()
    }
    val ivs=intArrayOf(source.dvs[0],source.dvs[1],source.dvs[2],source.dvs[3],source.dvs[4],source.dvs[4]).map { (it*2).coerceIn(0,31) }.toIntArray()
    var ivWord=0L
    for(i in 0..5)ivWord=ivWord or ((ivs[i].toLong() and 31L) shl(i*5))
    if(custom)ivWord=ivWord or 0x80000000L
    writeGen1ToGen4U32(out,0x74,ivWord)
    writeString(out,0x78,26,trainerName)
    out[0x92]=if(trainerGender.equals("Female",true))1 else 0
    out[0x93]=1
    out[0xA2]=baseFriendship.toByte()
    writeString(out,0xB0,26,source.otName)
    out[0xCA]=baseFriendship.toByte()
    val calendar=java.util.Calendar.getInstance()
    val metYear=(calendar.get(java.util.Calendar.YEAR)-2000).coerceIn(0,255)
    val metMonth=calendar.get(java.util.Calendar.MONTH)+1
    val metDay=calendar.get(java.util.Calendar.DAY_OF_MONTH)
    out[0xD4]=metYear.toByte()
    out[0xD5]=metMonth.toByte()
    out[0xD6]=metDay.toByte()
    writeGen1ToGen4U16(out,0xD8,0)
    writeGen1ToGen4U16(out,0xDA,30013)
    out[0xDC]=4
    out[0xDD]=source.level.coerceIn(1,100).toByte()
    out[0xDF]=35
    val language=if(destinationLanguage in setOf(1,2,3,4,5,7,8))destinationLanguage else 2
    out[0xE3]=language.toByte()
    updateChecksum(out)
    return BuiltGen1ToGen6Pk6(encrypt(out),dex,shiny,nature,gender,evs,ivs,nickname,custom,language,metYear,metMonth,metDay,trainerName)
}

internal fun verifyGen1ToGen6Pk6Bytes(
    built:BuiltGen1ToGen6Pk6,
    source:MainActivity.Gen1Pokemon,
    converted:MainActivity.PokemonEntry?,
    baseFriendship:Int,
    abilityId:Int,
    handlerName:String,
    ppUps:IntArray
):ByteArray {
    val pokemon=converted?:error("The converted PK6 could not be parsed.")
    require(
        pokemon.checksumValid&&pokemon.species==built.dex&&pokemon.tid==source.tid&&pokemon.sid==0&&
            pokemon.nickname==built.nickname&&pokemon.isNicknamed==built.customNickname&&pokemon.otName==source.otName&&
            pokemon.experience==source.experience&&pokemon.ability==abilityId&&pokemon.abilityNumber==1&&
            pokemon.gender==built.gender&&pokemon.nature==built.nature&&pokemon.shiny==built.shiny&&
            pokemon.friendship==baseFriendship&&pokemon.language==built.language&&pokemon.heldItem==0&&
            pokemon.ball==4&&pokemon.originGame==35&&pokemon.metLocation==30013&&
            pokemon.metLevel==source.level.coerceIn(1,100)&&pokemon.metYear==built.metYear&&
            pokemon.metMonth==built.metMonth&&pokemon.metDay==built.metDay&&pokemon.currentHandler==1&&
            handlerName==built.trainerName&&pokemon.moves.contentEquals(source.moves)&&
            pokemon.movePP.contentEquals(source.movePP)&&ppUps.contentEquals(source.movePpUps)&&
            pokemon.evs.contentEquals(built.evs)&&pokemon.ivs.contentEquals(built.ivs)&&pokemon.eggLocation==0&&
            !pokemon.isEgg&&pokemon.relearnMoves.all { it==0 }&&pokemon.country==0&&pokemon.region==0&&
            pokemon.consoleRegion==0
    ){"The converted PK6 failed identity or converted-field verification."}
    return built.encrypted
}

internal fun requireExactGen3BankPartySource(
    item: Gen3BankBatchItem,
    root: File,
    parsed: MainActivity.PokemonEntry?
) {
    require(item.bankFile.canonicalFile == item.bankFile &&
        item.bankFile.parentFile == root && item.bankFile.isFile &&
        item.bankFile.readBytes().contentEquals(item.bankBytes)) {
        "A selected Bank record changed after review."
    }
    require(parsed != null && parsed.checksumValid &&
        parsed.pid == item.pokemon.pid && parsed.species == item.pokemon.species &&
        parsed.isEgg == item.pokemon.isEgg) {
        "A selected Bank PK3 failed revalidation."
    }
}

internal fun requireGen3BankPartyWithdrawalBackup(
    backup: File,
    expected: ByteArray,
    kept: Boolean,
    stage: Int,
    recovery: ExactBankRecoverySet? = null
) {
    require(retainedBankBackupMatches(backup, expected, kept)) {
        when(stage) {
            0 -> "The retained Gen 3 Bank-to-party backup did not remain available."
            1 -> "The retained Gen 3 Bank-to-party backup changed before writing."
            2 -> "The retained Gen 3 Bank-to-party backup changed before Bank cleanup."
            else -> "The retained Gen 3 Bank-to-party backup changed after persistence."
        }
    }
    recovery?.let {
        require(it.matches()) {
            when(stage) {
                1 -> "A Gen 3 Bank-to-party recovery snapshot changed before writing."
                2 -> "A Gen 3 Bank-to-party recovery snapshot changed before Bank cleanup."
                else -> "A Gen 3 Bank-to-party recovery snapshot changed after persistence."
            }
        }
    }
}

internal fun requireGen3BankPartyCleanupBoundary(
    backup: File,
    expectedBackup: ByteArray,
    kept: Boolean,
    stage: Int,
    recovery: ExactBankRecoverySet,
    removed: List<Gen3BankBatchItem>,
    destinationBound: Boolean,
    destination: File,
    expectedDestination: ByteArray
) {
    requireGen3BankPartyWithdrawalBackup(
        backup, expectedBackup, kept, stage, recovery
    )
    require(destinationBound && runCatching {
        destination.isFile &&
            destination.readBytes().contentEquals(expectedDestination)
    }.getOrDefault(false)) {
        if(stage == 2) {
            "The Gen 3 party destination identity or exact staged bytes changed before Bank cleanup."
        } else {
            "The Gen 3 party destination identity or exact staged bytes changed after Bank cleanup."
        }
    }
    require(exactBankPathsAbsent(removed.map { it.bankFile })) {
        if(stage == 2) {
            "A previously removed Gen 3 Bank source reappeared before the next cleanup step."
        } else {
            "A removed Gen 3 Bank source reappeared after cleanup."
        }
    }
}

internal fun restoreExactGen3BankPartyDestination(
    file: File,
    original: ByteArray,
    staged: ByteArray?,
    writeStarted: Boolean
): ByteArray? = runCatching {
    restoreExactStagedDestination(
        file,
        original,
        staged,
        writeStarted,
        "No Generation 3 party destination write began; unknown current bytes were left untouched.",
        "The Generation 3 party destination no longer matches this transaction's staged bytes; the retained backup was left for recovery."
    )
    file.readBytes().takeIf { it.contentEquals(original) }
}.getOrNull()

internal fun finishSelectedGen1BankWithdrawal(
    recoveryFiles: List<File>,
    expected: List<ByteArray>,
    moved: Int,
    targetBox: Int,
    destination: File,
    expectedDestination: ByteArray,
    backup: File,
    expectedBackup: ByteArray,
    kept: Boolean,
    sources: List<File>,
    postCleanupIdentity: () -> Boolean
): String {
    val recoveryClean=cleanupExactBankRecoverySnapshots(recoveryFiles, expected)
    require(postCleanupIdentity() &&
        destination.isFile && destination.readBytes().contentEquals(expectedDestination) &&
        retainedBankBackupMatches(backup, expectedBackup, kept) &&
        sources.size == moved && exactBankPathsAbsent(sources) &&
        (!recoveryClean || exactBankPathsAbsent(recoveryFiles))) {
        "The verified Gen 1 destination, removed Bank batch or retained destination backup changed during recovery cleanup."
    }
    return "$moved Gen 1 Pokémon moved to Box $targetBox and verified" +
        if(recoveryClean) "" else
            "; a Bank recovery snapshot was retained for review"
}

internal fun finishSelectedGen1BankWithdrawal(
    recoveryFiles: List<File>,
    expected: List<ByteArray>,
    moved: Int,
    targetBox: Int
): String {
    val recoveryClean=cleanupExactBankRecoverySnapshots(recoveryFiles, expected)
    return "$moved Gen 1 Pokémon moved to Box $targetBox and verified" +
        if(recoveryClean) "" else
            "; a Bank recovery snapshot was retained for review"
}

internal fun gen5BankBatchSuccessMessage(
    moved: Int,
    targetBox: Int,
    targetSlots: List<Int>,
    retainedRecovery: Boolean
): String {
    val recoveryNotice=if(retainedRecovery)
        "; one or more exact temporary Bank recoveries retained for review" else ""
    return "Withdrew $moved exact Gen 5 PK5 records to Box ${targetBox+1} • Slots ${targetSlots.joinToString(", "){(it+1).toString()}} and verified$recoveryNotice"
}

internal fun gen5BankBatchFailureMessage(
    errorMessage: String?,
    bankRestored: Boolean,
    destinationRestored: Boolean,
    recoveryCopiesClean: Boolean,
    retainedBackup: Boolean,
    allRecoveryCopiesVerified: Boolean,
    verifiedRecoveryCount: Int,
    selectedCount: Int,
    safetyBackup: File?,
    verifiedRecoveryCopies: List<Pair<*, File>>
): String {
    val recovery=when {
        bankRestored&&destinationRestored&&recoveryCopiesClean->
            "The exact Generation 5 destination and every selected PK5 Bank record were restored or kept and verified."
        bankRestored&&destinationRestored->
            "The exact Generation 5 destination and every selected PK5 Bank record were restored or kept and verified. One or more exact temporary Bank recovery snapshots changed or could not be removed safely and were retained for review."
        bankRestored&&retainedBackup->
            "Every selected PK5 Bank record was restored or kept. Destination restoration was not verified, but the exact retained destination backup verified."
        bankRestored->
            "Every selected PK5 Bank record was restored or kept, but destination restoration and its retained backup were not verified. The current destination was left untouched."
        destinationRestored&&allRecoveryCopiesVerified->
            "The exact Generation 5 destination was restored. Bank restoration was incomplete, but every exact PK5 recovery snapshot verified."
        destinationRestored&&verifiedRecoveryCount>0->
            "The exact Generation 5 destination was restored. Bank restoration was incomplete; only $verifiedRecoveryCount of $selectedCount PK5 recovery snapshots verified."
        destinationRestored->
            "The exact Generation 5 destination was restored, but Bank restoration and all PK5 recovery snapshots were not verified."
        retainedBackup&&allRecoveryCopiesVerified->
            "Destination and Bank restoration were incomplete, but the exact retained destination backup and every PK5 recovery snapshot verified."
        retainedBackup&&verifiedRecoveryCount>0->
            "Destination and Bank restoration were incomplete. The exact retained destination backup and $verifiedRecoveryCount of $selectedCount PK5 recovery snapshots verified."
        retainedBackup->
            "Destination and Bank restoration were incomplete. The exact retained destination backup verified, but no PK5 recovery snapshot did."
        allRecoveryCopiesVerified->
            "Destination and Bank restoration were incomplete. Every PK5 recovery snapshot verified, but the retained destination backup did not."
        verifiedRecoveryCount>0->
            "Destination and Bank restoration were incomplete. Only $verifiedRecoveryCount of $selectedCount PK5 recovery snapshots verified; the retained destination backup did not."
        else->
            "Automatic recovery and all retained recovery snapshots could not be verified. Keep the current destination and Bank files for review."
    }
    return (errorMessage?:"Unknown error")+"\n\n"+recovery+
        (safetyBackup?.takeIf{retainedBackup}?.let{"\n\nVerified recovery backup: ${it.name}"}?:"")+
        (verifiedRecoveryCopies.filter{it.second.exists()}.takeIf{it.isNotEmpty()}?.joinToString(
            prefix="\nVerified Bank recovery snapshots: "
        ){it.second.name}?:"")
}

internal fun finishSelectedNativeBankBatchRecoveryCleanup(
    recoveryFiles: List<File>,
    expected: List<ByteArray>
): Boolean = !cleanupExactBankRecoverySnapshots(recoveryFiles, expected)

internal fun requireNativeBankWithdrawalPostCleanup(
    generation: Int,
    destinationBound: Boolean,
    retainedRecovery: Boolean,
    recoveryFile: File,
    destination: File,
    expectedDestination: ByteArray,
    backup: File,
    expectedBackup: ByteArray,
    kept: Boolean,
    source: File
) {
    require(destinationBound &&
        destination.isFile && destination.readBytes().contentEquals(expectedDestination) &&
        retainedBankBackupMatches(backup, expectedBackup, kept) &&
        !source.exists() &&
        (retainedRecovery || !recoveryFile.exists())) {
        "The verified Generation $generation destination, removed Bank source or retained recovery data changed during recovery cleanup."
    }
}

internal fun requireSelectedNativeBankBatchPostCleanup(
    generation: Int,
    destinationBound: Boolean,
    retainedRecovery: Boolean,
    recoveryFiles: List<File>,
    destination: File,
    expectedDestination: ByteArray,
    backup: File,
    expectedBackup: ByteArray,
    kept: Boolean,
    sources: List<File>
) {
    require(destinationBound &&
        destination.isFile && destination.readBytes().contentEquals(expectedDestination) &&
        retainedBankBackupMatches(backup, expectedBackup, kept) &&
        exactBankPathsAbsent(sources) &&
        (retainedRecovery || exactBankPathsAbsent(recoveryFiles))) {
        "The verified Generation $generation destination, removed Bank batch or retained destination backup changed during recovery cleanup."
    }
}

internal fun restoreExactMissingStandardBankRecord(
    file: File,
    expected: ByteArray,
    cleanupAttempted: Boolean,
    recovery: File?
) {
    require(file.absoluteFile==file.canonicalFile) {
        "The Bank recovery path is unresolved or noncanonical."
    }
    if (recovery?.exists() == true) {
        require(recovery.absoluteFile==recovery.canonicalFile &&
            recovery.canonicalFile!=file.canonicalFile &&
            recovery.isFile && recovery.readBytes().contentEquals(expected)) {
            "An existing Bank recovery snapshot aliases the source, is noncanonical or changed; it was retained for review."
        }
    }
    if (file.exists()) {
        require(file.isFile && file.readBytes().contentEquals(expected)) {
            "An existing Bank path no longer matches the reviewed record; it was not overwritten."
        }
        return
    }
    require(cleanupAttempted) {
        "A Bank record disappeared before PSE attempted cleanup; its path was not overwritten."
    }
    require(file.createNewFile()) { "A Bank recovery path could not be reserved." }
    val writeFailure=runCatching { file.writeBytes(expected) }.exceptionOrNull()
    val restored=runCatching {
        file.isFile && file.readBytes().contentEquals(expected)
    }.getOrDefault(false)
    if (!restored && writeFailure!=null) {
        val partial=runCatching { if(file.isFile) file.readBytes() else null }.getOrNull()
        if(partial!=null&&partial.size<expected.size&&expected.copyOf(partial.size).contentEquals(partial)) {
            runCatching {
                require(file.absoluteFile==file.canonicalFile)
                require(file.delete()&&!file.exists())
            }
        }
    }
    require(restored) { "The restored Bank record did not reopen byte-for-byte." }
}

internal fun cleanupExactSelectedGen3BankCopies(
    temporary: List<File>,
    finalized: List<File>,
    expected: List<ByteArray>
): Boolean {
    var clean=true
    val candidates=ArrayList<Pair<File,ByteArray>>()
    temporary.forEachIndexed { index,file ->
        if(index in expected.indices)candidates.add(file to expected[index])
        else if(file.exists())clean=false
    }
    finalized.forEachIndexed { index,file ->
        if(index in expected.indices)candidates.add(file to expected[index])
        else if(file.exists())clean=false
    }
    val paths=LinkedHashMap<String,MutableList<Pair<File,ByteArray>>>()
    candidates.forEach { candidate ->
        val identity=runCatching { candidate.first.canonicalPath }.getOrNull()
        if(identity==null){
            clean=false
        }else{
            paths.getOrPut(identity){ArrayList()}.add(candidate)
        }
    }
    paths.values.forEach { references ->
        val(file,bytes)=references.first()
        val canonicalReferences=references.map { (candidate,_) ->
            runCatching {
                candidate.absoluteFile==candidate.canonicalFile
            }.getOrDefault(false)
        }.all { it }
        val expectationsMatch=references.map { (_,expected) ->
            expected.contentEquals(bytes)
        }.all { it }
        if(!canonicalReferences||!expectationsMatch){
            clean=false
            return@forEach
        }
        val removed=runCatching {
            if(file.exists()){
                require(file.isFile&&file.readBytes().contentEquals(bytes)){"A staged Gen 3 Bank copy changed; it was retained for review."}
                require(file.delete()){"An exact staged Gen 3 Bank copy could not be removed."}
            }
            require(!file.exists()){"An exact staged Gen 3 Bank copy still exists."}
        }.isSuccess
        if(!removed)clean=false
    }
    return clean
}

internal fun runSelectedStandardBankBatchReview(
    activity: android.app.Activity,
    chosen: List<File>,
    generation: Int,
    file: File,
    bound: () -> Boolean,
    storageInfo: (ByteArray) -> Triple<Int,Int,Int>?,
    bankDirectory: () -> File,
    backupsDirectory: () -> File,
    backupBaseName: () -> String,
    structurallyEmpty: (ByteArray,Int,Int,Int,Int) -> Boolean,
    recordValid: (ByteArray,Int) -> Boolean,
    nativeValid: (ByteArray) -> Boolean,
    refreshDestination: (ByteArray) -> Unit,
    retainBackup: (File,ByteArray) -> Unit,
    backupKept: (File) -> Boolean,
    showFailure: (String,String) -> Unit,
    onSuccess: (Int,Int) -> Unit
) {
    val reviewedIdentity=runCatching {
        require(file.absoluteFile==file.canonicalFile&&file.isFile)
        file.canonicalPath
    }.getOrNull()?:return showFailure(
        "Move selected",
        "The destination path is unresolved, noncanonical or unavailable. Reopen PSE Bank. Nothing was changed."
    )
    fun destinationIdentityBound()=runCatching {
        file.absoluteFile==file.canonicalFile&&file.canonicalPath==reviewedIdentity&&file.isFile&&bound()
    }.getOrDefault(false)
    val reviewedDestination=runCatching{file.readBytes()}.getOrNull()
        ?:return showFailure("Move selected","The destination save could not be reviewed. Nothing was changed.")
    if(!destinationIdentityBound())return showFailure(
        "Move selected",
        "The destination identity changed before review. Reopen PSE Bank. Nothing was changed."
    )
    val info=storageInfo(reviewedDestination)
        ?:return showFailure("Move selected","PSE could not resolve this save's PC storage. Nothing was changed.")
    val(boxOffset,slotCount,maxSpecies)=info
    val labels=(0 until slotCount/30).map{box->
        val empty=(0 until 30).count{slot->
            val global=box*30+slot
            global<slotCount&&structurallyEmpty(reviewedDestination,boxOffset,slotCount,maxSpecies,global)
        }
        "Box ${box+1} • $empty structurally empty slot${if(empty==1)"" else "s"}"
    }.toTypedArray()
    android.app.AlertDialog.Builder(activity).setTitle("Move ${chosen.size} Pokémon to which box?")
        .setItems(labels){_,which->
            val current=runCatching{file.readBytes()}.getOrNull()
            if(current==null||!destinationIdentityBound()||!current.contentEquals(reviewedDestination)){
                showFailure("Move selected","The reviewed destination identity or bytes changed. Reopen PSE Bank. Nothing was changed.")
                return@setItems
            }
            val slots=(0 until 30).map{which*30+it}.filter{
                it<slotCount&&structurallyEmpty(current,boxOffset,slotCount,maxSpecies,it)
            }.take(chosen.size)
            if(slots.size!=chosen.size){
                showFailure("Move selected","Box ${which+1} no longer has enough structurally empty slots. Nonzero records were left unchanged.")
                return@setItems
            }
            val reviewedSources=try{
                val directory=bankDirectory()
                require(directory.absoluteFile==directory.canonicalFile&&directory.isDirectory){
                    "The Generation $generation PSE Bank directory is unresolved, noncanonical or unavailable."
                }
                val root=directory.canonicalFile
                val paths=chosen.map{selected->
                    require(selected.absoluteFile==selected.canonicalFile){
                        "A selected Bank path is unresolved or noncanonical."
                    }
                    selected.canonicalFile
                }
                require(paths.distinct().size==paths.size){"The same Bank record was selected more than once."}
                paths.map{selected->
                    require(selected.parentFile==root&&selected.isFile&&selected.extension.equals("pk$generation",true)){
                        "A selected Bank path is outside the Generation $generation PSE Bank."
                    }
                    val raw=selected.readBytes()
                    require(recordValid(raw,maxSpecies)){"A selected Bank Pokémon failed its native size or checksum."}
                    raw.copyOf()
                }
            }catch(error:Exception){
                showFailure("Move selected",(error.message?:"The selected Bank records could not be reviewed.")+" Nothing was changed.")
                return@setItems
            }
            val destinations=slots.joinToString(", "){slot->"Box ${slot/30+1} • Slot ${slot%30+1}"}
            android.app.AlertDialog.Builder(activity).setTitle("Confirm PSE Bank batch withdrawal")
                .setMessage("Generation $generation PSE withdrawal\n\nDestination slots: $destinations\n\nThe exact selected Bank bytes and destination save are bound to this review. No conversion or Legalise change occurs. This is not an official trade, Pokémon Bank or HOME transfer, legality certification, or verified provenance. Cancel leaves every file unchanged.")
                .setPositiveButton("Move and verify"){_,_->
                    val original=runCatching{file.readBytes()}.getOrNull()
                    if(original==null||!destinationIdentityBound()||!original.contentEquals(reviewedDestination)){
                        showFailure("Move selected","The reviewed destination identity or bytes changed. Reopen PSE Bank. Nothing was changed.")
                        return@setPositiveButton
                    }
                    val result=executeSelectedStandardBankBatch(
                        chosen,generation,which,slots,reviewedSources,file,original,bankDirectory(),backupsDirectory(),backupBaseName(),
                        boxOffset,slotCount,232,bound,
                        {data,slot->structurallyEmpty(data,boxOffset,slotCount,maxSpecies,slot)},
                        {raw->recordValid(raw,maxSpecies)},nativeValid,refreshDestination,retainBackup,backupKept
                    )
                    result.failure?.let{showFailure("Move selected failed",it);return@setPositiveButton}
                    onSuccess(result.moved,which)
                    result.notice?.let{
                        showFailure("Move selected completed with retained recovery",it)
                    }
                }.setNegativeButton("Cancel",null).show()
        }.setNegativeButton("Cancel",null).show()
}

internal fun executeSelectedStandardBankBatch(
    chosen: List<File>,
    generation: Int,
    targetBox: Int,
    plannedSlots: List<Int>,
    reviewedSources: List<ByteArray>,
    file: File,
    original: ByteArray,
    bankDirectory: File,
    backupsDirectory: File,
    backupBaseName: String,
    boxOffset: Int,
    slotCount: Int,
    recordSize: Int,
    bound: () -> Boolean,
    structurallyEmpty: (ByteArray,Int) -> Boolean,
    recordValid: (ByteArray) -> Boolean,
    nativeValid: (ByteArray) -> Boolean,
    refreshDestination: (ByteArray) -> Unit,
    retainBackup: (File,ByteArray) -> Unit,
    backupKept: (File) -> Boolean
): PseSelectedBankBatchResult {
    var writeStarted=false
    var cleanupAttempted=false
    var safetyBackup:File?=null
    var stagedDestination:ByteArray?=null
    var destinationIdentity:String?=null
    fun destinationMatches(expected:ByteArray):Boolean {
        val identity=destinationIdentity?:return false
        return runCatching {
            file.absoluteFile==file.canonicalFile&&file.canonicalPath==identity&&file.isFile&&
                bound()&&file.readBytes().contentEquals(expected)
        }.getOrDefault(false)
    }
    val sources=ArrayList<File>()
    val sourceBytes=ArrayList<ByteArray>()
    val bankRecoveries=ArrayList<File>()
    val removed=ArrayList<File>()
    try {
        require(chosen.isNotEmpty()){"No Bank records are selected."}
        require(generation in 6..7&&nativeValid(original)){"The loaded save does not match this PSE Bank generation."}
        require(file.absoluteFile==file.canonicalFile&&file.isFile){
            "The Generation $generation destination path is unresolved, noncanonical or unavailable."
        }
        destinationIdentity=file.canonicalPath
        require(targetBox in 0 until (slotCount/30)){"The selected destination box is unavailable."}
        require(plannedSlots.size==chosen.size&&reviewedSources.size==chosen.size&&
            plannedSlots.distinct().size==plannedSlots.size&&plannedSlots.all{it in 0 until slotCount&&it/30==targetBox&&structurallyEmpty(original,it)}){
            "The reviewed destination slots changed or are no longer structurally empty."
        }
        require(bankDirectory.absoluteFile==bankDirectory.canonicalFile&&bankDirectory.isDirectory){
            "The Generation $generation PSE Bank directory is unresolved, noncanonical or unavailable."
        }
        val root=bankDirectory.canonicalFile
        chosen.forEachIndexed { index,selected ->
            require(selected.absoluteFile==selected.canonicalFile){
                "A selected Bank path is unresolved or noncanonical."
            }
            val exact=selected.canonicalFile
            require(exact.parentFile==root&&exact.isFile&&exact.extension.equals("pk$generation",true)){"A selected Bank record is outside the Generation $generation PSE Bank."}
            require(exact !in sources){"The same Bank record was selected more than once."}
            val raw=exact.readBytes()
            require(raw.contentEquals(reviewedSources[index])&&recordValid(raw)){"A selected Bank Pokémon changed after review or failed its native size or PK checksum."}
            sources.add(exact)
            sourceBytes.add(raw)
        }
        require(destinationMatches(original)){"The destination identity or bytes changed before staging."}
        val work=original.copyOf()
        sourceBytes.forEachIndexed { index,raw -> raw.copyInto(work,boxOffset+plannedSlots[index]*recordSize) }
        refreshDestination(work)
        require(nativeValid(work)){"The staged destination failed native checksum validation."}
        sourceBytes.forEachIndexed { index,raw ->
            val off=boxOffset+plannedSlots[index]*recordSize
            require(work.copyOfRange(off,off+recordSize).contentEquals(raw)&&recordValid(raw)){"A staged Bank Pokémon did not verify exactly."}
        }
        require(backupsDirectory.absoluteFile==backupsDirectory.canonicalFile&&backupsDirectory.isDirectory){
            "The destination backup directory is unresolved, noncanonical or unavailable."
        }
        val backup=File.createTempFile(backupBaseName+"_PreSelectedBankMove_",".backup",backupsDirectory)
        safetyBackup=backup
        require(backup.absoluteFile==backup.canonicalFile&&backup.parentFile==backupsDirectory){
            "The destination recovery backup was not created at its canonical directory identity."
        }
        backup.writeBytes(original)
        retainBackup(backup,original)
        require(retainedBankBackupMatches(backup,original,backupKept(backup))){"The exact kept destination recovery backup did not verify."}
        sourceBytes.forEachIndexed { index,raw ->
            val recovery=File.createTempFile("PSE_Selected_PK${generation}_BankRecovery_",".tmp",bankDirectory)
            bankRecoveries.add(recovery)
            require(recovery.absoluteFile==recovery.canonicalFile&&recovery.parentFile==bankDirectory){
                "Bank recovery snapshot ${index+1} was not created at its canonical directory identity."
            }
            recovery.writeBytes(raw)
            require(recovery.isFile&&recovery.readBytes().contentEquals(raw)){"Bank recovery snapshot ${index+1} did not verify."}
        }
        fun exactUniqueRecords(files: List<File>): Boolean {
            if(files.size!=sourceBytes.size)return false
            val identities=files.map { candidate ->
                runCatching {
                    require(candidate.absoluteFile==candidate.canonicalFile)
                    candidate.canonicalPath
                }.getOrNull()
            }
            if(identities.any { it==null }||identities.filterNotNull().distinct().size!=files.size)return false
            val exactMatches=files.indices.map { index ->
                runCatching {
                    files[index].absoluteFile==files[index].canonicalFile&&
                        files[index].canonicalPath==identities[index]&&
                        files[index].isFile&&
                        files[index].readBytes().contentEquals(sourceBytes[index])
                }.getOrDefault(false)
            }
            return exactMatches.all { it }
        }
        fun sourcesValid(): Boolean = exactUniqueRecords(sources)
        fun recoveriesValid(): Boolean = exactUniqueRecords(bankRecoveries)
        require(destinationMatches(original)&&sourcesValid()&&
            retainedBankBackupMatches(backup,original,backupKept(backup))&&recoveriesValid()){
            "The destination, Bank records or retained recovery snapshots changed before writing."
        }
        stagedDestination=work.copyOf()
        writeStarted=true
        file.writeBytes(work)
        val verify=file.readBytes()
        require(destinationMatches(work)&&verify.contentEquals(work)&&nativeValid(verify)){"The persisted destination or native checksums did not verify."}
        sourceBytes.forEachIndexed { index,raw ->
            val off=boxOffset+plannedSlots[index]*recordSize
            val actual=verify.copyOfRange(off,off+recordSize)
            require(actual.contentEquals(raw)&&recordValid(actual)){"A moved Pokémon did not verify after persistence."}
        }
        require(sourcesValid()&&recoveriesValid()){
            "A Bank record or retained recovery snapshot changed before cleanup."
        }
        cleanupAttempted=true
        sources.forEachIndexed { index,source ->
            require(destinationMatches(work)&&nativeValid(work)&&
                retainedBankBackupMatches(backup,original,backupKept(backup))&&recoveriesValid()&&
                exactBankPathsAbsent(removed)&&source.absoluteFile==source.canonicalFile&&source.isFile&&
                source.readBytes().contentEquals(sourceBytes[index])){
                "The verified destination, source record or retained recovery changed before Bank cleanup."
            }
            require(source.delete()&&!source.exists()){"A Bank record could not be removed after destination verification."}
            removed.add(source)
        }
        require(destinationMatches(work)&&nativeValid(work)&&
            retainedBankBackupMatches(backup,original,backupKept(backup))&&removed.size==sources.size&&
            exactBankPathsAbsent(removed)&&recoveriesValid()){
            "The verified destination or retained recovery snapshots changed after Bank cleanup."
        }
        val bankRecoveryClean=cleanupExactBankRecoverySnapshots(
            bankRecoveries,sourceBytes
        )
        require(destinationMatches(work)&&nativeValid(work)&&
            retainedBankBackupMatches(backup,original,backupKept(backup))&&
            removed.size==sources.size&&exactBankPathsAbsent(removed)&&
            (!bankRecoveryClean||exactBankPathsAbsent(bankRecoveries))){
            "The verified destination, removed Bank batch or retained destination backup changed during recovery cleanup."
        }
        return PseSelectedBankBatchResult(
            moved=sources.size,
            notice=if(bankRecoveryClean) null else
                "The verified destination and selected Bank removals completed, but one or more exact temporary recovery snapshots could not be removed safely. Changed or unremovable snapshots were retained for review."
        )
    } catch(error:Exception) {
        val bankRestored=runCatching {
            restoreExactBankRecords(sources,sourceBytes,bankRecoveries,cleanupAttempted)
            true
        }.getOrDefault(false)
        val destinationRestored=runCatching {
            restoreExactStagedDestination(
                file,original,stagedDestination,writeStarted,
                "No Generation $generation destination write began; unknown current bytes were left untouched.",
                "The Generation $generation destination no longer matches this batch's staged bytes; the retained backup was left for recovery."
            )
            val restored=file.readBytes()
            require(destinationMatches(original)&&restored.contentEquals(original)&&nativeValid(restored)){
                "The exact destination could not be restored or verified at its reviewed identity."
            }
            true
        }.getOrDefault(false)
        if(bankRestored&&destinationRestored) {
            cleanupExactBankRecoverySnapshots(bankRecoveries,sourceBytes)
        }
        val recovery=when {
            bankRestored&&destinationRestored->"The exact destination and every Bank record were restored or kept and verified."
            bankRestored->"Every Bank record was restored or kept, but destination recovery was not verified. Use the retained destination backup before editing again."
            destinationRestored->"The exact destination was restored, but Bank recovery was not verified. Keep every Bank recovery snapshot for review."
            else->"Automatic recovery could not be fully verified. Keep the retained destination backup and every Bank recovery snapshot for review."
        }
        val recoveryNames=bankRecoveries.filter{it.exists()}.joinToString(", "){it.name}
        return PseSelectedBankBatchResult(
            failure=(error.message?:"Unknown error")+"\n\n"+recovery+
                (safetyBackup?.let{"\n\nRecovery backup: ${it.name}"}?:"")+
                (if(recoveryNames.isNotEmpty())"\nBank recovery snapshots: $recoveryNames" else "")
        )
    }
}
