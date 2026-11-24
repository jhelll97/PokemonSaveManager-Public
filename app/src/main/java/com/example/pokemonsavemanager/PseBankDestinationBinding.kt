package com.example.pokemonsavemanager

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream

internal class PseBankDestinationBinding private constructor(
    private val file:File,
    private val identity:String,
    private val device:Long,
    private val inode:Long,
    private val parent:File,
    private val parentIdentity:String,
    private val parentDevice:Long,
    private val parentInode:Long
) {
    companion object {
        fun capture(file:File):PseBankDestinationBinding?=runCatching {
            require(file.absoluteFile==file.canonicalFile&&file.isFile)
            val parent=file.parentFile?:error("Missing parent")
            require(parent.absoluteFile==parent.canonicalFile&&parent.isDirectory)
            val parentStat=Os.stat(parent.path)
            require(OsConstants.S_ISDIR(parentStat.st_mode))
            val opened=FileInputStream(file).use { input ->
                Os.fstat(input.fd).also { require(OsConstants.S_ISREG(it.st_mode)) }
            }
            val linked=Os.stat(file.path)
            require(OsConstants.S_ISREG(linked.st_mode)&&
                linked.st_dev==opened.st_dev&&linked.st_ino==opened.st_ino)
            val linkedParent=Os.stat(parent.path)
            require(OsConstants.S_ISDIR(linkedParent.st_mode)&&
                linkedParent.st_dev==parentStat.st_dev&&linkedParent.st_ino==parentStat.st_ino)
            PseBankDestinationBinding(
                file,file.canonicalPath,opened.st_dev,opened.st_ino,
                parent,parent.canonicalPath,parentStat.st_dev,parentStat.st_ino
            )
        }.getOrNull()
    }

    private fun parentMatches():Boolean=runCatching {
        val stat=Os.stat(parent.path)
        parent.absoluteFile==parent.canonicalFile&&parent.canonicalPath==parentIdentity&&
            parent.isDirectory&&OsConstants.S_ISDIR(stat.st_mode)&&
            stat.st_dev==parentDevice&&stat.st_ino==parentInode
    }.getOrDefault(false)

    fun matches(expected:ByteArray,bound:Boolean):Boolean=runCatching {
        if(!bound||!parentMatches()||file.absoluteFile!=file.canonicalFile||
            file.canonicalPath!=identity||!file.isFile)return@runCatching false
        FileInputStream(file).use { input ->
            val opened=Os.fstat(input.fd)
            if(!OsConstants.S_ISREG(opened.st_mode)||opened.st_dev!=device||opened.st_ino!=inode)
                return@use false
            if(!input.readBytes().contentEquals(expected)||!parentMatches()||
                file.absoluteFile!=file.canonicalFile||file.canonicalPath!=identity||!file.isFile)
                return@use false
            val linked=Os.stat(file.path)
            OsConstants.S_ISREG(linked.st_mode)&&linked.st_dev==device&&linked.st_ino==inode
        }
    }.getOrDefault(false)

    fun absent(bound:Boolean):Boolean=runCatching {
        bound&&parentMatches()&&file.absoluteFile==file.canonicalFile&&file.canonicalPath==identity&&!file.exists()
    }.getOrDefault(false)
}

internal typealias PseBankSourceBinding=PseBankDestinationBinding
