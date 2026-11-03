package com.example.pokemonsavemanager

import java.util.Collections

data class BdspNurseryParentRead(val position: Int, val read: BdspSlotRead)

/** Native Nursery records only. No compatibility calculation, RNG advance or egg generation. */
class BdspNurseryRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val parents: List<BdspNurseryParentRead>, val rawEggAvailableFlag: Long,
    val eggSeed: ULong, val eggStepCount: Int, val rawEggStepCount: Long) {
    val isEggAvailable: Boolean get() = rawEggAvailableFlag == 1L
    val hasCanonicalEggFlag: Boolean get() = rawEggAvailableFlag in 0L..1L
    val eggSeedHex: String get() = eggSeed.toString(16).uppercase().padStart(16, '0')
    val decodedParentCount: Int get() = parents.count { it.read is BdspSlotRead.Pokemon }
    val unreadableParentCount: Int get() = parents.count { it.read is BdspSlotRead.Invalid }
    fun parent(index: Int): BdspNurseryParentRead {
        require(index in 0..1) { "Nursery parent index must be 0 or 1." }
        return parents[index]
    }
}

/** Whole-file validation precedes two independent encrypted 344-byte PB8 records and native metadata. */
object BdspNurseryReadOnly {
    fun read(source: ByteArray): BdspNurseryRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0x96080
        val parents = (0..1).map { index ->
            val offset = base + index * BdspPokemonReadOnly.RECORD_SIZE
            BdspNurseryParentRead(index + 1, BdspPokemonReadOnly.read(
                source.copyOfRange(offset, offset + BdspPokemonReadOnly.RECORD_SIZE)))
        }
        val extra = base + 2 * BdspPokemonReadOnly.RECORD_SIZE
        val stepWord = u32(source, extra + 12)
        return BdspNurseryRead(checked.version, checked.revision, Collections.unmodifiableList(ArrayList(parents)),
            u32(source, extra), u64(source, extra + 4), stepWord.toInt(), stepWord)
    }
    private fun u32(b: ByteArray, offset: Int): Long = (0..3).fold(0L) { n, i ->
        n or ((b[offset + i].toLong() and 255) shl (8 * i))
    }
    private fun u64(b: ByteArray, offset: Int): ULong = (0..7).fold(0uL) { n, i ->
        n or ((b[offset + i].toInt() and 255).toULong() shl (8 * i))
    }
}
