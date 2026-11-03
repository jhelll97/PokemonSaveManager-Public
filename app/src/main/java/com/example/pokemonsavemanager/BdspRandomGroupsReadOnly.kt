package com.example.pokemonsavemanager

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections

class BdspRandomGroupNameRead internal constructor(val text: String?, val unavailableReason: String?, val rawBytes: List<Int>)

class BdspRandomGroupRead internal constructor(val index: Int, val groupName: BdspRandomGroupNameRead,
    val playerName: BdspRandomGroupNameRead, val rawMaleByte: Int, val alignmentBytes: List<Int>,
    val regionCode: Int, val seed: ULong, val random: ULong, val ticks: Long, val userId: Int, val rawBytes: List<Int>) {
    val male: Boolean get() = rawMaleByte == 1
    val hasCanonicalMaleByte: Boolean get() = rawMaleByte in 0..1
}

class BdspRandomGroupsRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val records: List<BdspRandomGroupRead>, val rawBytes: List<Int>) {
    fun record(index: Int): BdspRandomGroupRead {
        require(index in records.indices) { "The saved random-group index is outside 0..11." }
        return records[index]
    }
}

/** Computed native offsets, not stale comments. No RNG advance, time conversion, group joining or writer. */
object BdspRandomGroupsReadOnly {
    fun read(source: ByteArray): BdspRandomGroupsRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x9476C
        fun raw(o: Int, count: Int): List<Int> = Collections.unmodifiableList((0 until count).map { source[o + it].toInt() and 255 })
        fun word(o: Int): Long = (0..3).fold(0L) { n, i -> n or ((source[o + i].toLong() and 255) shl (8 * i)) }
        fun wide(o: Int): ULong = (0..7).fold(0uL) { n, i -> n or ((source[o + i].toInt() and 255).toULong() shl (8 * i)) }
        fun unit(o: Int): Int = (source[o].toInt() and 255) or ((source[o + 1].toInt() and 255) shl 8)
        fun name(o: Int, count: Int): BdspRandomGroupNameRead {
            val bytes = raw(o, count)
            var length = 0
            while (length < count && unit(o + length) != 0) length += 2
            val text = try {
                Charsets.UTF_16LE.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(source, o, length)).toString()
            } catch (_: java.nio.charset.CharacterCodingException) {
                return BdspRandomGroupNameRead(null, "Malformed UTF-16; raw name bytes retained.", bytes)
            }
            if (text.any { Character.isISOControl(it) }) return BdspRandomGroupNameRead(null, "Control characters; raw name bytes retained.", bytes)
            return BdspRandomGroupNameRead(text, null, bytes)
        }
        val records = (0 until 12).map { index ->
            val o = base + index * 0x84
            BdspRandomGroupRead(index, name(o, 0x20), name(o + 0x20, 0x40), source[o + 0x60].toInt() and 255,
                raw(o + 0x61, 3), word(o + 0x64).toInt(), wide(o + 0x68), wide(o + 0x70), wide(o + 0x78).toLong(),
                word(o + 0x80).toInt(), raw(o, 0x84))
        }
        return BdspRandomGroupsRead(checked.version, checked.revision, Collections.unmodifiableList(records), raw(base, 0x630))
    }
}
