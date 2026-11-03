package com.example.pokemonsavemanager

/** Highest achieved Contest rank in each category: 0=None, 1=Normal, 2=Super, 3=Hyper, 4=Master. */
data class Gen3ContestRibbonRanks(
    val cool: Int = 0, val beauty: Int = 0, val cute: Int = 0,
    val smart: Int = 0, val tough: Int = 0
) {
    fun values(): List<Int> = listOf(cool, beauty, cute, smart, tough)
}

/** Immutable stored/party PK3 edit restricted to the five three-bit Contest ribbon ranks. */
object Gen3ContestRibbonEditing {
    // Misc substructure +8: five ranks in bits 0..14; all other ribbon/event bits are preserved.
    // https://github.com/pret/pokeemerald/blob/master/include/pokemon.h
    private fun decode(record: ByteArray): Gen3Pk3DecodedRecord {
        require(record.size == 80 || record.size == 100) { "Use a complete stored or party PK3 record." }
        require((record[0x13].toInt() and 3) == 2) { "The PK3 is empty or flagged as a Bad Egg." }
        val decoded = Gen3Pk3Codec.decode(record.copyOfRange(0, 80))
        require((record[0x13].toInt() and 4) == 0 &&
            (decoded.canonicalSection[43].toInt() and 0x40) == 0) {
            "Contest ribbons cannot be edited on an Egg."
        }
        return decoded
    }

    fun read(record: ByteArray): Gen3ContestRibbonRanks {
        val canonical = decode(record).canonicalSection
        val word = (canonical[44].toInt() and 255) or ((canonical[45].toInt() and 255) shl 8)
        val ranks = Gen3ContestRibbonRanks(word and 7, (word ushr 3) and 7,
            (word ushr 6) and 7, (word ushr 9) and 7, (word ushr 12) and 7)
        require(ranks.values().all { it in 0..4 }) { "The stored Contest ribbon ranks are invalid. Nothing was changed." }
        return ranks
    }

    fun replace(record: ByteArray, ranks: Gen3ContestRibbonRanks): ByteArray {
        require(ranks.values().all { it in 0..4 }) { "Every Contest ribbon rank must be between None and Master (0–4)." }
        read(record) // Reject malformed existing ranks rather than silently repairing them.
        val before = decode(record)
        val canonical = before.canonicalSection.copyOf()
        val packed = ranks.values().foldIndexed(0) { index, word, rank -> word or (rank shl (index * 3)) }
        canonical[44] = packed.toByte()
        canonical[45] = ((canonical[45].toInt() and 0x80) or (packed ushr 8)).toByte()
        val stored = Gen3Pk3Codec.encode(before.header, before.personality,
            before.trainerId, before.secretId, canonical)
        val result = record.copyOf().also { stored.copyInto(it) }
        val after = decode(result)
        require(after.canonicalSection.contentEquals(canonical) &&
            canonical.indices.all { it in 44..45 || canonical[it] == before.canonicalSection[it] } &&
            (canonical[45].toInt() and 0x80) == (before.canonicalSection[45].toInt() and 0x80) &&
            (0 until 32).all { it in 28..29 || result[it] == record[it] } &&
            (80 until record.size).all { result[it] == record[it] } && read(result) == ranks) {
            "The PK3 Contest ribbon edit failed exact field or integrity verification."
        }
        return result
    }
}
