package com.example.pokemonsavemanager

import java.io.File

internal fun restoreExactStagedDestination(
    file: File,
    original: ByteArray,
    staged: ByteArray?,
    writeStarted: Boolean,
    generationName: String
) {
    val current=file.readBytes()
    if(current.contentEquals(original))return
    require(writeStarted){"No $generationName destination write began; unknown current bytes were left untouched."}
    require(staged!=null&&current.contentEquals(staged)){"The $generationName destination no longer matches this transfer's staged bytes; the retained backup was left for recovery."}
    file.writeBytes(original)
}
