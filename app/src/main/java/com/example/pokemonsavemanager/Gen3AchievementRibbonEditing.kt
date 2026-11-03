package com.example.pokemonsavemanager

data class Gen3AchievementRibbons(
    val champion: Boolean = false, val winning: Boolean = false,
    val victory: Boolean = false, val artist: Boolean = false, val effort: Boolean = false
) {
    fun values(): List<Boolean> = listOf(champion, winning, victory, artist, effort)
}

/** Immutable edit of the five ordinary Gen 3 achievement ribbon flags in stored/party PK3 data. */
object Gen3AchievementRibbonEditing {
    // Misc substructure +8, bits 15..19: Champion, Winning, Victory, Artist, Effort.
    // https://github.com/pret/pokeemerald/blob/master/include/pokemon.h
    private fun decode(record: ByteArray): Gen3Pk3DecodedRecord {
        require(record.size == 80 || record.size == 100) { "Use a complete stored or party PK3 record." }
        require((record[0x13].toInt() and 3) == 2) { "The PK3 is empty or flagged as a Bad Egg." }
        val decoded = Gen3Pk3Codec.decode(record.copyOfRange(0, 80))
        require((record[0x13].toInt() and 4) == 0 &&
            (decoded.canonicalSection[43].toInt() and 0x40) == 0) {
            "Achievement ribbons cannot be edited on an Egg."
        }
        return decoded
    }

    fun read(record: ByteArray): Gen3AchievementRibbons {
        val canonical = decode(record).canonicalSection
        val flags = ((canonical[45].toInt() and 0x80) ushr 7) or
            ((canonical[46].toInt() and 0x0F) shl 1)
        return Gen3AchievementRibbons((flags and 1) != 0, (flags and 2) != 0,
            (flags and 4) != 0, (flags and 8) != 0, (flags and 16) != 0)
    }

    fun replace(record: ByteArray, ribbons: Gen3AchievementRibbons): ByteArray {
        val before = decode(record)
        val canonical = before.canonicalSection.copyOf()
        val flags = ribbons.values().foldIndexed(0) { index, bits, enabled ->
            if (enabled) bits or (1 shl index) else bits
        }
        canonical[45] = ((canonical[45].toInt() and 0x7F) or ((flags and 1) shl 7)).toByte()
        canonical[46] = ((canonical[46].toInt() and 0xF0) or (flags ushr 1)).toByte()
        val stored = Gen3Pk3Codec.encode(before.header, before.personality,
            before.trainerId, before.secretId, canonical)
        val result = record.copyOf().also { stored.copyInto(it) }
        val after = decode(result)
        require(after.canonicalSection.contentEquals(canonical) &&
            canonical.indices.all { it in 45..46 || canonical[it] == before.canonicalSection[it] } &&
            (canonical[45].toInt() and 0x7F) == (before.canonicalSection[45].toInt() and 0x7F) &&
            (canonical[46].toInt() and 0xF0) == (before.canonicalSection[46].toInt() and 0xF0) &&
            (0 until 32).all { it in 28..29 || result[it] == record[it] } &&
            (80 until record.size).all { result[it] == record[it] } && read(result) == ribbons) {
            "The PK3 achievement ribbon edit failed exact field or integrity verification."
        }
        return result
    }
}
