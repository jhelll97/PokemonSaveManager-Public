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
