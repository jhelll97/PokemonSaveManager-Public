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
    removed.forEach { item ->
        runCatching {
            if(!item.bankFile.exists() && item.bankFile.createNewFile()) {
                item.bankFile.writeBytes(item.bankBytes)
            }
        }
    }
    return selected.all { item ->
        runCatching {
            item.bankFile.isFile && item.bankFile.readBytes().contentEquals(item.bankBytes)
        }.getOrDefault(false)
    }
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
        file.isFile && kept && file.readBytes().contentEquals(expected)
    }.getOrDefault(false)
} ?: false

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
    if (!file.exists()) {
        require(file.createNewFile()) {
            "A Gen 3 Bank recovery path could not be reserved."
        }
        file.writeBytes(expected)
    }
    file.isFile && file.readBytes().contentEquals(expected)
}.getOrDefault(false)

internal fun exactGen3BankRecoverySnapshots(
    files: List<File>,
    expected: List<ByteArray>
): Boolean = files.size == expected.size && files.indices.all { index ->
    runCatching {
        files[index].isFile &&
            files[index].readBytes().contentEquals(expected[index])
    }.getOrDefault(false)
}

internal fun cleanupExactBankRecoverySnapshots(
    files: List<File>,
    expected: List<ByteArray>
): Boolean {
    if (files.size != expected.size) return false
    var clean=true
    files.forEachIndexed { index,file ->
        val removed=runCatching {
            if(file.exists()){
                require(file.isFile && file.readBytes().contentEquals(expected[index])){
                    "A Gen 3 Bank recovery snapshot changed; it was retained for review."
                }
                require(file.delete()){
                    "An exact Gen 3 Bank recovery snapshot could not be removed."
                }
            }
            require(!file.exists()){
                "An exact Gen 3 Bank recovery snapshot still exists."
            }
        }.isSuccess
        if(!removed)clean=false
    }
    return clean
}

internal fun finishSelectedGen3BankWithdrawal(
    recoveryFiles: List<File>,
    expected: List<ByteArray>,
    moved: Int,
    targetBoxName: String
): String {
    val recoveryClean=cleanupExactBankRecoverySnapshots(recoveryFiles, expected)
    return "$moved Gen 3 Pokémon moved to $targetBoxName and verified" +
        if(recoveryClean) "" else
            "; a Bank recovery snapshot was retained for review"
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
        removed.size == moved && removed.all { !it.first.exists() } &&
        (!completion.recoveryClean || recoveryFiles.none { it.exists() })) {
        "The verified Gen 2 destination, removed Bank batch or retained destination backup changed during recovery cleanup."
    }
    return completion.message
}

internal class ExactBankRecoverySet {
    private val entries=ArrayList<Pair<File,ByteArray>>()

    fun capture(bytes: ByteArray, directory: File) {
        val file=File.createTempFile("PSE_G3_BankPartyRecovery_", ".tmp", directory)
        entries.add(file to bytes.copyOf())
        file.writeBytes(bytes)
        require(file.isFile && file.readBytes().contentEquals(bytes)) {
            "A Bank recovery snapshot did not verify."
        }
    }

    fun matches(): Boolean = entries.all { (file,bytes) ->
        runCatching {
            file.isFile && file.readBytes().contentEquals(bytes)
        }.getOrDefault(false)
    }

    fun cleanupExact(): Boolean {
        var clean=true
        entries.forEach { (file,bytes) ->
            if(!file.exists()) return@forEach
            val exact=runCatching {
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
    require(removed.all { !it.bankFile.exists() }) {
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
    targetBox: Int
): String {
    val recoveryClean=cleanupExactBankRecoverySnapshots(recoveryFiles, expected)
    return "$moved Gen 1 Pokémon moved to Box $targetBox and verified" +
        if(recoveryClean) "" else
            "; a Bank recovery snapshot was retained for review"
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
    candidates.distinctBy{it.first.absolutePath}.forEach { (file,bytes) ->
        val removed=runCatching {
            if(file.exists()){
                require(file.readBytes().contentEquals(bytes)){"A staged Gen 3 Bank copy changed; it was retained for review."}
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
    val reviewedDestination=runCatching{file.readBytes()}.getOrNull()
        ?:return showFailure("Move selected","The destination save could not be reviewed. Nothing was changed.")
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
            if(current==null||!bound()||!current.contentEquals(reviewedDestination)){
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
                val root=bankDirectory().canonicalFile
                val paths=chosen.map{it.canonicalFile}
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
                    if(original==null||!bound()||!original.contentEquals(reviewedDestination)){
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
    val sources=ArrayList<File>()
    val sourceBytes=ArrayList<ByteArray>()
    val bankRecoveries=ArrayList<File>()
    val removed=ArrayList<File>()
    try {
        require(chosen.isNotEmpty()){"No Bank records are selected."}
        require(generation in 6..7&&nativeValid(original)){"The loaded save does not match this PSE Bank generation."}
        require(targetBox in 0 until (slotCount/30)){"The selected destination box is unavailable."}
        require(plannedSlots.size==chosen.size&&reviewedSources.size==chosen.size&&
            plannedSlots.distinct().size==plannedSlots.size&&plannedSlots.all{it in 0 until slotCount&&it/30==targetBox&&structurallyEmpty(original,it)}){
            "The reviewed destination slots changed or are no longer structurally empty."
        }
        val root=bankDirectory.canonicalFile
        chosen.forEachIndexed { index,selected ->
            val exact=selected.canonicalFile
            require(exact.parentFile==root&&exact.isFile&&exact.extension.equals("pk$generation",true)){"A selected Bank record is outside the Generation $generation PSE Bank."}
            require(exact !in sources){"The same Bank record was selected more than once."}
            val raw=exact.readBytes()
            require(raw.contentEquals(reviewedSources[index])&&recordValid(raw)){"A selected Bank Pokémon changed after review or failed its native size or PK checksum."}
            sources.add(exact)
            sourceBytes.add(raw)
        }
        require(bound()&&file.readBytes().contentEquals(original)){"The destination identity or bytes changed before staging."}
        val work=original.copyOf()
        sourceBytes.forEachIndexed { index,raw -> raw.copyInto(work,boxOffset+plannedSlots[index]*recordSize) }
        refreshDestination(work)
        require(nativeValid(work)){"The staged destination failed native checksum validation."}
        sourceBytes.forEachIndexed { index,raw ->
            val off=boxOffset+plannedSlots[index]*recordSize
            require(work.copyOfRange(off,off+recordSize).contentEquals(raw)&&recordValid(raw)){"A staged Bank Pokémon did not verify exactly."}
        }
        val backup=File.createTempFile(backupBaseName+"_PreSelectedBankMove_",".backup",backupsDirectory)
        safetyBackup=backup
        backup.writeBytes(original)
        retainBackup(backup,original)
        require(backup.readBytes().contentEquals(original)&&backupKept(backup)){"The exact kept destination recovery backup did not verify."}
        sourceBytes.forEachIndexed { index,raw ->
            val recovery=File.createTempFile("PSE_Selected_PK${generation}_BankRecovery_",".tmp",bankDirectory)
            recovery.writeBytes(raw)
            require(recovery.readBytes().contentEquals(raw)){"Bank recovery snapshot ${index+1} did not verify."}
            bankRecoveries.add(recovery)
        }
        fun recoveriesValid()=bankRecoveries.size==sourceBytes.size&&bankRecoveries.indices.all {
            bankRecoveries[it].readBytes().contentEquals(sourceBytes[it])
        }
        require(bound()&&file.readBytes().contentEquals(original)&&sources.indices.all {
            sources[it].readBytes().contentEquals(sourceBytes[it])
        }&&backup.readBytes().contentEquals(original)&&backupKept(backup)&&recoveriesValid()){
            "The destination, Bank records or retained recovery snapshots changed before writing."
        }
        stagedDestination=work.copyOf()
        writeStarted=true
        file.writeBytes(work)
        val verify=file.readBytes()
        require(bound()&&verify.contentEquals(work)&&nativeValid(verify)){"The persisted destination or native checksums did not verify."}
        sourceBytes.forEachIndexed { index,raw ->
            val off=boxOffset+plannedSlots[index]*recordSize
            val actual=verify.copyOfRange(off,off+recordSize)
            require(actual.contentEquals(raw)&&recordValid(actual)){"A moved Pokémon did not verify after persistence."}
        }
        require(sources.indices.all{sources[it].readBytes().contentEquals(sourceBytes[it])}&&recoveriesValid()){
            "A Bank record or retained recovery snapshot changed before cleanup."
        }
        cleanupAttempted=true
        sources.forEachIndexed { index,source ->
            require(bound()&&file.readBytes().contentEquals(work)&&nativeValid(work)&&
                backup.readBytes().contentEquals(original)&&backupKept(backup)&&recoveriesValid()&&
                removed.none{it.exists()}&&source.readBytes().contentEquals(sourceBytes[index])){
                "The verified destination, source record or retained recovery changed before Bank cleanup."
            }
            require(source.delete()&&!source.exists()){"A Bank record could not be removed after destination verification."}
            removed.add(source)
        }
        require(bound()&&file.readBytes().contentEquals(work)&&nativeValid(work)&&
            backup.readBytes().contentEquals(original)&&backupKept(backup)&&removed.size==sources.size&&
            removed.none{it.exists()}&&recoveriesValid()){
            "The verified destination or retained recovery snapshots changed after Bank cleanup."
        }
        val bankRecoveryClean=cleanupExactBankRecoverySnapshots(
            bankRecoveries,sourceBytes
        )
        require(bound()&&file.isFile&&file.readBytes().contentEquals(work)&&nativeValid(work)&&
            backup.isFile&&backup.readBytes().contentEquals(original)&&backupKept(backup)&&
            removed.size==sources.size&&removed.none{it.exists()}&&
            (!bankRecoveryClean||bankRecoveries.none{it.exists()})){
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
            require(file.readBytes().let{it.contentEquals(original)&&nativeValid(it)}){"The exact destination could not be restored or verified."}
            true
        }.getOrDefault(false)
        if(bankRestored&&destinationRestored)bankRecoveries.forEach{runCatching{it.delete()}}
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
