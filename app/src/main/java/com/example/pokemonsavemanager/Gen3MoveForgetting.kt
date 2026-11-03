package com.example.pokemonsavemanager

/** Forget one move while compacting complete move/PP/PP Up slots and protecting the last move. */
object Gen3MoveForgetting {
    // Growth +8: PP Up pairs. Attacks +0/+8: move IDs/current PP.
    // https://github.com/pret/pokeemerald/blob/master/include/pokemon.h
    // https://github.com/pret/pokeemerald/blob/master/src/pokemon.c
    fun forget(record: ByteArray, slot: Int, basePp: Map<Int, Int>): ByteArray {
        val before = Gen3MoveOrderEditing.read(record, basePp)
        val activeCount = before.count { it.moveId != 0 }
        require(activeCount > 1) { "A Pokémon must keep at least one move." }
        require(slot in 0 until activeCount) { "Choose a populated move slot to forget." }
        val survivors = before.filterIndexed { index, move -> index != slot && move.moveId != 0 }
        val requested = survivors + List(4 - survivors.size) { Gen3MoveSlot(0, 0, 0) }
        val decoded = Gen3Pk3Codec.decode(record.copyOfRange(0, 80))
        val canonical = decoded.canonicalSection.copyOf()
        canonical[8] = requested.foldIndexed(0) { index, packed, move ->
            packed or (move.ppUps shl (index * 2))
        }.toByte()
        requested.forEachIndexed { index, move ->
            canonical[12 + index * 2] = move.moveId.toByte()
            canonical[13 + index * 2] = (move.moveId ushr 8).toByte()
            canonical[20 + index] = move.pp.toByte()
        }
        val stored = Gen3Pk3Codec.encode(decoded.header, decoded.personality,
            decoded.trainerId, decoded.secretId, canonical)
        val result = record.copyOf().also { stored.copyInto(it) }
        val after = Gen3Pk3Codec.decode(result.copyOfRange(0, 80))
        require(after.canonicalSection.contentEquals(canonical) &&
            canonical.indices.all { it == 8 || it in 12..23 || canonical[it] == decoded.canonicalSection[it] } &&
            (0 until 32).all { it in 28..29 || result[it] == record[it] } &&
            (80 until record.size).all { result[it] == record[it] } &&
            Gen3MoveOrderEditing.read(result, basePp) == requested) {
            "The PK3 move-forgetting edit failed exact slot or integrity verification."
        }
        return result
    }
}
