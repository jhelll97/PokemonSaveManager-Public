package com.example.pokemonsavemanager

data class Gen3PokerusState(val strain: Int = 0, val days: Int = 0) {
    fun requireValid(): Gen3PokerusState {
        require(strain in 0..15) { "Pokérus strain must be between 0 and 15." }
        val maximumDays = if (strain == 0) 0 else (strain and 3) + 1
        require(days in 0..maximumDays) {
            "Strain $strain allows 0–$maximumDays remaining days. Strain 0 means uninfected."
        }
        return this
    }

    fun status(): String = when {
        strain == 0 -> "Uninfected"
        days == 0 -> "Cured"
        else -> "Infectious"
    }
}

/** Immutable PK3 Pokérus editing; the Misc substructure's first byte stores strain/days. */
object Gen3PokerusEditing {
    // High nibble=strain; low nibble=days, initially (strain & 3)+1, then decremented.
    // https://github.com/pret/pokeemerald/blob/master/src/pokemon.c
    private fun decode(record: ByteArray): Gen3Pk3DecodedRecord {
        require(record.size == 80 || record.size == 100) { "Use a complete stored or party PK3 record." }
        require((record[0x13].toInt() and 3) == 2) { "The PK3 is empty or flagged as a Bad Egg." }
        val decoded = Gen3Pk3Codec.decode(record.copyOfRange(0, 80))
        require((record[0x13].toInt() and 4) == 0 &&
            (decoded.canonicalSection[43].toInt() and 0x40) == 0) {
            "Pokérus cannot be edited on an Egg."
        }
        return decoded
    }

    fun read(record: ByteArray): Gen3PokerusState {
        val raw = decode(record).canonicalSection[36].toInt() and 255
        return Gen3PokerusState(raw ushr 4, raw and 15).requireValid()
    }

    fun replace(record: ByteArray, state: Gen3PokerusState): ByteArray {
        state.requireValid()
        read(record) // Reject malformed source states instead of silently normalizing them.
        val before = decode(record)
        val canonical = before.canonicalSection.copyOf()
        canonical[36] = ((state.strain shl 4) or state.days).toByte()
        val stored = Gen3Pk3Codec.encode(before.header, before.personality,
            before.trainerId, before.secretId, canonical)
        val result = record.copyOf().also { stored.copyInto(it) }
        val after = decode(result)
        require(after.canonicalSection.contentEquals(canonical) &&
            canonical.indices.all { it == 36 || canonical[it] == before.canonicalSection[it] } &&
            (0 until 32).all { it in 28..29 || result[it] == record[it] } &&
            (80 until record.size).all { result[it] == record[it] } && read(result) == state) {
            "The PK3 Pokérus edit failed exact field or integrity verification."
        }
        return result
    }
}
