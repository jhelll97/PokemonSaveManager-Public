package com.example.pokemonsavemanager

import java.util.Collections

class BdspContestPhotoRead internal constructor(val index: Int, val rawBytes: List<Int>)

class BdspContestRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val rankPoints: Long, val photos: List<BdspContestPhotoRead>, val rawBytes: List<Int>) {
    fun photo(index: Int): BdspContestPhotoRead {
        require(index in photos.indices) { "The saved contest photo index is outside 0..4." }
        return photos[index]
    }
}

object BdspContestReadOnly {
    fun read(source: ByteArray): BdspContestRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) { (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required." }
        val base = 0x79C08
        fun word(o: Int): Long = (0..3).fold(0L) { n, i -> n or ((source[o + i].toLong() and 255) shl (8 * i)) }
        fun raw(o: Int, size: Int): List<Int> = Collections.unmodifiableList((0 until size).map { source[o + it].toInt() and 255 })
        val photos = (0 until 5).map { BdspContestPhotoRead(it, raw(base + it * 0x16C, 0x16C)) }
        return BdspContestRead(checked.version, checked.revision, word(base + 0x71C), Collections.unmodifiableList(photos), raw(base, 0x720))
    }
}

