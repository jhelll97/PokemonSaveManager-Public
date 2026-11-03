package com.example.pokemonsavemanager

/** Reorder complete move slots, retaining each move's current PP and PP Up association. */
object Gen3MoveOrderEditing {
    // Growth +8: four PP Up bit pairs. Attacks +0: four u16 moves; +8: four PP bytes.
    // https://github.com/pret/pokeemerald/blob/master/include/pokemon.h
    private fun requirePackedMoves(slots: List<Gen3MoveSlot>) {
        val firstEmpty = slots.indexOfFirst { it.moveId == 0 }.let { if (it < 0) 4 else it }
        require(slots.drop(firstEmpty).all { it.moveId == 0 }) {
            "Moves must occupy consecutive slots before empty slots."
        }
    }

    fun read(record: ByteArray, basePp: Map<Int, Int>): List<Gen3MoveSlot> {
        val ups = Gen3PpUpEditing.read(record, basePp) // Length, checksum, Egg, move and PP validation.
        val data = Gen3Pk3Codec.decode(record.copyOfRange(0, 80)).canonicalSection
        val slots = List(4) { slot ->
            val move = (data[12 + slot * 2].toInt() and 255) or
                ((data[13 + slot * 2].toInt() and 255) shl 8)
            Gen3MoveSlot(move, data[20 + slot].toInt() and 255, ups[slot])
        }
        requirePackedMoves(slots)
        return slots
    }

    fun reorder(record: ByteArray, order: List<Int>, basePp: Map<Int, Int>): ByteArray {
        val selected = order.toList()
        require(selected.size == 4 && selected.sorted() == listOf(0, 1, 2, 3)) {
            "Choose every original move slot exactly once."
        }
        val before = read(record, basePp)
        val requested = selected.map { before[it] }
        requirePackedMoves(requested)
        val decoded = Gen3Pk3Codec.decode(record.copyOfRange(0, 80))
        val canonical = decoded.canonicalSection.copyOf()
        canonical[8] = requested.foldIndexed(0) { slot, packed, move ->
            packed or (move.ppUps shl (slot * 2))
        }.toByte()
        requested.forEachIndexed { slot, move ->
            canonical[12 + slot * 2] = move.moveId.toByte()
            canonical[13 + slot * 2] = (move.moveId ushr 8).toByte()
            canonical[20 + slot] = move.pp.toByte()
        }
        val stored = Gen3Pk3Codec.encode(decoded.header, decoded.personality,
            decoded.trainerId, decoded.secretId, canonical)
        val result = record.copyOf().also { stored.copyInto(it) }
        val after = Gen3Pk3Codec.decode(result.copyOfRange(0, 80))
        require(after.canonicalSection.contentEquals(canonical) &&
            canonical.indices.all { it == 8 || it in 12..23 || canonical[it] == decoded.canonicalSection[it] } &&
            (0 until 32).all { it in 28..29 || result[it] == record[it] } &&
            (80 until record.size).all { result[it] == record[it] } && read(result, basePp) == requested) {
            "The PK3 move reorder failed exact slot or integrity verification."
        }
        return result
    }
}
