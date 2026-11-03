package com.example.pokemonsavemanager

/** Immutable PK3 condition editing for stored and party records. */
object Gen3ContestEditing {
    // EV substructure: six EV bytes followed by Cool, Beauty, Cute, Smart, Tough, Sheen.
    // https://github.com/pret/pokeemerald/blob/master/include/pokemon.h
    private const val CONDITION_START = 30

    private fun decode(record: ByteArray): Gen3Pk3DecodedRecord {
        require(record.size == 80 || record.size == 100) { "Use a complete stored or party PK3 record." }
        require((record[0x13].toInt() and 3) == 2) { "The PK3 is empty or flagged as a Bad Egg." }
        val decoded = Gen3Pk3Codec.decode(record.copyOfRange(0, 80))
        require((record[0x13].toInt() and 4) == 0 &&
            (decoded.canonicalSection[43].toInt() and 0x40) == 0) {
            "Contest condition cannot be edited on an Egg."
        }
        return decoded
    }

    fun read(record: ByteArray): Gen3ContestState {
        val data = decode(record).canonicalSection
        return Gen3ContestState(data[30].toInt() and 255, data[31].toInt() and 255,
            data[32].toInt() and 255, data[33].toInt() and 255,
            data[34].toInt() and 255, data[35].toInt() and 255)
    }

    fun replace(record: ByteArray, values: Gen3ContestState): ByteArray {
        require(values.validate().isEmpty()) { "Every condition value must be between 0 and 255." }
        val before = decode(record)
        val canonical = before.canonicalSection.copyOf()
        val requested = intArrayOf(values.cool, values.beauty, values.cute, values.smart, values.tough, values.sheen)
        requested.forEachIndexed { index, value -> canonical[CONDITION_START + index] = value.toByte() }
        val stored = Gen3Pk3Codec.encode(before.header, before.personality,
            before.trainerId, before.secretId, canonical)
        val result = record.copyOf().also { stored.copyInto(it) }
        val after = decode(result)
        require(after.canonicalSection.contentEquals(canonical) &&
            canonical.indices.all { it in 30..35 || canonical[it] == before.canonicalSection[it] } &&
            (0 until 32).all { it in 28..29 || result[it] == record[it] } &&
            (80 until record.size).all { result[it] == record[it] } && read(result) == values) {
            "The PK3 condition edit failed exact field or integrity verification."
        }
        return result
    }
}
