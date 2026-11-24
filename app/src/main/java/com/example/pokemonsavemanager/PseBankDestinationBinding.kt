package com.example.pokemonsavemanager

import android.system.Os
import android.system.OsConstants
import java.io.File

internal class PseBankDestinationBinding private constructor(
    private val file:File,
    private val identity:String,
    private val device:Long,
    private val inode:Long
) {
    companion object {
        fun capture(file:File):PseBankDestinationBinding?=runCatching {
            require(file.absoluteFile==file.canonicalFile&&file.isFile)
            val stat=Os.stat(file.path)
            require(OsConstants.S_ISREG(stat.st_mode))
            PseBankDestinationBinding(file,file.canonicalPath,stat.st_dev,stat.st_ino)
        }.getOrNull()
    }

    fun matches(expected:ByteArray,bound:Boolean):Boolean=runCatching {
        val stat=Os.stat(file.path)
        bound&&file.absoluteFile==file.canonicalFile&&file.canonicalPath==identity&&
            OsConstants.S_ISREG(stat.st_mode)&&stat.st_dev==device&&stat.st_ino==inode&&
            file.isFile&&file.readBytes().contentEquals(expected)
    }.getOrDefault(false)

    fun absent(bound:Boolean):Boolean=runCatching {
        bound&&file.absoluteFile==file.canonicalFile&&file.canonicalPath==identity&&!file.exists()
    }.getOrDefault(false)
}

internal typealias PseBankSourceBinding=PseBankDestinationBinding
