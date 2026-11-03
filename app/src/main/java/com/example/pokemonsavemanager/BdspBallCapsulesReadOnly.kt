package com.example.pokemonsavemanager

import java.util.Collections

class BdspAffixedSealRead internal constructor(val index: Int, val sealId: Int,
    val x: Int, val y: Int, val z: Int, val rawBytes: List<Int>) {
    val nativeLabel: String? get() = BdspSealStickerLabels.nativeLabel(sealId)
}

class BdspBallCapsuleRead internal constructor(val index: Int, val speciesId: Long,
    val encryptionConstant: Long, val unknownWord: Long, val seals: List<BdspAffixedSealRead>, val rawBytes: List<Int>) {
    val hasNonzeroBytes: Boolean get() = rawBytes.any { it != 0 }
    fun seal(index: Int): BdspAffixedSealRead {
        require(index in seals.indices) { "The saved affixed-seal index is outside 0..19." }
        return seals[index]
    }
}

class BdspBallCapsulesRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val savedCapsuleCount: Int, val rawHeaderBytes: List<Int>, val capsules: List<BdspBallCapsuleRead>) {
    val countWithinCapacity: Boolean get() = savedCapsuleCount in 0..99
    fun capsule(index: Int): BdspBallCapsuleRead {
        require(index in capsules.indices) { "The saved Ball Capsule index is outside 0..98." }
        return capsules[index]
    }
    fun filtered(nonzeroOnly: Boolean): List<BdspBallCapsuleRead> =
        Collections.unmodifiableList(capsules.filter { !nonzeroOnly || it.hasNonzeroBytes })
}

/** Direct native fields only. Count is not an unlock/active-slot rule; no writers or normalization. */
object BdspBallCapsulesReadOnly {
    fun read(source: ByteArray): BdspBallCapsulesRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x8FB84
        fun word(o: Int): Long = (0..3).fold(0L) { n, i -> n or ((source[o + i].toLong() and 255) shl (8 * i)) }
        fun u16(o: Int): Int = (source[o].toInt() and 255) or ((source[o + 1].toInt() and 255) shl 8)
        fun s16(o: Int): Int = u16(o).toShort().toInt()
        fun raw(o: Int, size: Int): List<Int> = Collections.unmodifiableList((0 until size).map { source[o + it].toInt() and 255 })
        val capsules = (0 until 99).map { index ->
            val o = base + 4 + index * 0xAC
            val seals = (0 until 20).map { sealIndex ->
                val s = o + 12 + sealIndex * 8
                BdspAffixedSealRead(sealIndex, u16(s), s16(s + 2), s16(s + 4), s16(s + 6), raw(s, 8))
            }
            BdspBallCapsuleRead(index, word(o), word(o + 4), word(o + 8), Collections.unmodifiableList(seals), raw(o, 0xAC))
        }
        return BdspBallCapsulesRead(checked.version, checked.revision, source[base].toInt() and 255,
            raw(base, 4), Collections.unmodifiableList(capsules))
    }
}
