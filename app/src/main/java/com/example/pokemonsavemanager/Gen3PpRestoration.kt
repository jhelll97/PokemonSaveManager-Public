package com.example.pokemonsavemanager

/** Restore current move PP using the existing PP Ups without altering any other Pokémon data. */
object Gen3PpRestoration {
    // Attacks +8..+11: current PP. CalculatePPWithBonus uses Growth +8 PP Up pairs.
    // https://github.com/pret/pokeemerald/blob/master/include/pokemon.h
    // https://github.com/pret/pokeemerald/blob/master/src/pokemon.c
    fun restore(record: ByteArray, basePp: Map<Int, Int>): ByteArray {
        val before = Gen3MoveOrderEditing.read(record, basePp)
        require(before.any { it.moveId != 0 }) { "A Pokémon must have at least one move." }
        val requested = before.map { it.copy(pp = Gen3PpUpEditing.maximumPp(it.moveId, it.ppUps, basePp)) }
        if (requested == before) return record.copyOf()
        val decoded = Gen3Pk3Codec.decode(record.copyOfRange(0, 80))
        val canonical = decoded.canonicalSection.copyOf()
        requested.forEachIndexed { index, move -> canonical[20 + index] = move.pp.toByte() }
        val stored = Gen3Pk3Codec.encode(decoded.header, decoded.personality,
            decoded.trainerId, decoded.secretId, canonical)
        val result = record.copyOf().also { stored.copyInto(it) }
        val after = Gen3Pk3Codec.decode(result.copyOfRange(0, 80))
        require(after.canonicalSection.contentEquals(canonical) &&
            canonical.indices.all { it in 20..23 || canonical[it] == decoded.canonicalSection[it] } &&
            (0 until 32).all { it in 28..29 || result[it] == record[it] } &&
            (80 until record.size).all { result[it] == record[it] } &&
            Gen3MoveOrderEditing.read(result, basePp) == requested) {
            "The PK3 PP restoration failed exact slot or integrity verification."
        }
        return result
    }
}
