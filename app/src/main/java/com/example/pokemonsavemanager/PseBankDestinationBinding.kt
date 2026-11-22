package com.example.pokemonsavemanager

import java.io.File

internal class PseBankDestinationBinding private constructor(
    private val file:File,
    private val identity:String
) {
    companion object {
        fun capture(file:File):PseBankDestinationBinding?=runCatching {
            require(file.absoluteFile==file.canonicalFile&&file.isFile)
            PseBankDestinationBinding(file,file.canonicalPath)
        }.getOrNull()
    }

    fun matches(expected:ByteArray,bound:Boolean):Boolean=runCatching {
        bound&&file.absoluteFile==file.canonicalFile&&file.canonicalPath==identity&&
            file.isFile&&file.readBytes().contentEquals(expected)
    }.getOrDefault(false)
}
