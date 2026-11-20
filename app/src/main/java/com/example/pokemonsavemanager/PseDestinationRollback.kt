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
    val current=file.readBytes()
    if(current.contentEquals(original))return
    require(writeStarted){writeNotStartedMessage}
    require(staged!=null&&current.contentEquals(staged)){stagedMismatchMessage}
    file.writeBytes(original)
}


internal fun restoreExactBankRecords(
    files: List<File>,
    originals: List<ByteArray>,
    recoveries: List<File>,
    cleanupStarted: Boolean
) {
    require(files.size==originals.size){"The selected Bank recovery set is incomplete."}
    files.indices.forEach { index ->
        val file=files[index]
        val original=originals[index]
        if(!file.exists()) {
            require(cleanupStarted){"A Bank record disappeared before PSE attempted cleanup; its path was not overwritten."}
            val recovery=recoveries.getOrNull(index)
            require(recovery!=null&&recovery.readBytes().contentEquals(original)){"The exact Bank recovery snapshot is unavailable."}
            require(file.createNewFile()){"A Bank recovery path could not be reserved."}
            file.writeBytes(original)
        }
        require(file.readBytes().contentEquals(original)){"A Bank record could not be restored or verified exactly."}
    }
}
