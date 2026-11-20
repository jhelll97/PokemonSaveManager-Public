package com.example.pokemonsavemanager

import java.io.File

internal data class PseSelectedBankBatchResult(
    val moved: Int=0,
    val failure: String?=null
)

internal fun executeSelectedStandardBankBatch(
    chosen: List<File>,
    generation: Int,
    targetBox: Int,
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
    try {
        require(chosen.isNotEmpty()){"No Bank records are selected."}
        require(generation in 6..7&&nativeValid(original)){"The loaded save does not match this PSE Bank generation."}
        require(targetBox in 0 until (slotCount/30)){"The selected destination box is unavailable."}
        val root=bankDirectory.canonicalFile
        val empty=(0 until 30).map{targetBox*30+it}.filter{it<slotCount&&structurallyEmpty(original,it)}
        require(empty.size>=chosen.size){"Box ${targetBox+1} has only ${empty.size} structurally empty slots but ${chosen.size} Pokémon are selected. Choose another box."}
        chosen.forEach { selected ->
            val exact=selected.canonicalFile
            require(exact.parentFile==root&&exact.isFile&&exact.extension.equals("pk$generation",true)){"A selected Bank record is outside the Generation $generation PSE Bank."}
            require(exact !in sources){"The same Bank record was selected more than once."}
            val raw=exact.readBytes()
            require(recordValid(raw)){"A selected Bank Pokémon failed its native size or PK checksum."}
            sources.add(exact)
            sourceBytes.add(raw)
        }
        require(bound()&&file.readBytes().contentEquals(original)){"The destination identity or bytes changed before staging."}
        val work=original.copyOf()
        sourceBytes.forEachIndexed { index,raw -> raw.copyInto(work,boxOffset+empty[index]*recordSize) }
        refreshDestination(work)
        require(nativeValid(work)){"The staged destination failed native checksum validation."}
        sourceBytes.forEachIndexed { index,raw ->
            val off=boxOffset+empty[index]*recordSize
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
            val off=boxOffset+empty[index]*recordSize
            val actual=verify.copyOfRange(off,off+recordSize)
            require(actual.contentEquals(raw)&&recordValid(actual)){"A moved Pokémon did not verify after persistence."}
        }
        require(sources.indices.all{sources[it].readBytes().contentEquals(sourceBytes[it])}&&recoveriesValid()){
            "A Bank record or retained recovery snapshot changed before cleanup."
        }
        cleanupAttempted=true
        sources.forEach{require(it.delete()&&!it.exists()){"A Bank record could not be removed after destination verification."}}
        require(bound()&&file.readBytes().contentEquals(work)&&nativeValid(work)&&
            backup.readBytes().contentEquals(original)&&backupKept(backup)&&sources.none{it.exists()}&&recoveriesValid()){
            "The verified destination or retained recovery snapshots changed after Bank cleanup."
        }
        bankRecoveries.forEach{runCatching{it.delete()}}
        return PseSelectedBankBatchResult(moved=sources.size)
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
