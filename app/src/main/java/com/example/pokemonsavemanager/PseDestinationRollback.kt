package com.example.pokemonsavemanager

import java.io.File

internal fun restoreExactStagedDestination(
    file: File,
    original: ByteArray,
    staged: ByteArray?,
    writeStarted: Boolean,
    generationName: String
)=restoreExactStagedDestination(
    file,
    original,
    staged,
    writeStarted,
    "No $generationName destination write began; unknown current bytes were left untouched.",
    "The $generationName destination no longer matches this transfer's staged bytes; the retained backup was left for recovery."
)

internal fun restoreExactStagedDestination(
    file: File,
    original: ByteArray,
    staged: ByteArray?,
    writeStarted: Boolean,
    writeNotStartedMessage: String,
    stagedMismatchMessage: String
) {
    val destination=runCatching { file.canonicalFile }.getOrElse {
        throw IllegalStateException("The destination path could not be resolved for rollback.")
    }
    require(destination.isFile){"The destination file is unavailable for rollback."}
    val current=destination.readBytes()
    if(current.contentEquals(original))return
    require(writeStarted){writeNotStartedMessage}
    require(staged!=null&&current.contentEquals(staged)){stagedMismatchMessage}
    destination.writeBytes(original)
    require(destination.isFile&&destination.readBytes().contentEquals(original)){
        "The destination rollback write did not reopen byte-for-byte."
    }
}


private fun releaseFailedBankRecoveryReservation(file: File, original: ByteArray) {
    runCatching {
        require(file.absoluteFile==file.canonicalFile)
        require(file.isFile)
        val current=file.readBytes()
        require(current.size<original.size&&original.copyOf(current.size).contentEquals(current))
        require(file.delete())
    }
}

internal fun restoreExactBankRecords(
    files: List<File>,
    originals: List<ByteArray>,
    recoveries: List<File>,
    cleanupStarted: Boolean
) {
    require(files.size==originals.size){"The selected Bank recovery set is incomplete."}
    val identities=files.indices.map { index ->
        runCatching {
            require(files[index].absoluteFile==files[index].canonicalFile)
            files[index].canonicalPath
        }.getOrNull()
    }
    require(identities.all { it!=null }){"A selected Bank recovery path is unresolved or noncanonical."}
    val grouped=files.indices.groupBy { identities[it]!! }
    val consistent=grouped.values.map { indices ->
        val expected=originals[indices.first()]
        indices.map { originals[it].contentEquals(expected) }.all { it }
    }
    require(consistent.all { it }){"One physical Bank path was bound to conflicting reviewed payloads."}
    val recoveryIdentities=recoveries.map { recovery ->
        runCatching {
            require(recovery.absoluteFile==recovery.canonicalFile)
            recovery.canonicalPath
        }.getOrNull()
    }
    val restored=files.indices.map { index ->
        runCatching {
            val file=files[index]
            val original=originals[index]
            if(!file.exists()) {
                require(cleanupStarted){"A Bank record disappeared before PSE attempted cleanup; its path was not overwritten."}
                val recovery=recoveries.getOrNull(index)
                val recoveryIdentity=recoveryIdentities.getOrNull(index)
                require(recovery!=null&&recoveryIdentity!=null&&
                    recoveryIdentities.count { it==recoveryIdentity }==1&&
                    recovery.isFile&&recovery.readBytes().contentEquals(original)){"The exact one-to-one Bank recovery snapshot is unavailable."}
                require(file.createNewFile()){"A Bank recovery path could not be reserved."}
                val writeFailure=runCatching { file.writeBytes(original) }.exceptionOrNull()
                if(writeFailure!=null) {
                    val exact=runCatching {
                        file.isFile&&file.readBytes().contentEquals(original)
                    }.getOrDefault(false)
                    if(!exact) {
                        releaseFailedBankRecoveryReservation(file, original)
                        throw writeFailure
                    }
                }
            }
            file.isFile&&file.readBytes().contentEquals(original)
        }.getOrDefault(false)
    }
    require(restored.all { it }){"One or more Bank records could not be restored or verified exactly."}
}
