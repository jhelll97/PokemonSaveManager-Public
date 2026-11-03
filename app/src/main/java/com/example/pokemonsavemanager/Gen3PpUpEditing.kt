package com.example.pokemonsavemanager

/** Immutable PP Up editing for stored/party PK3 records; current PP and moves are preserved. */
object Gen3PpUpEditing {
    // Growth +8 packs four 2-bit PP Up counts. Attacks +8 stores four current PP bytes.
    // CalculatePPWithBonus: basePP + floor(basePP * PP Ups / 5).
    // https://github.com/pret/pokeemerald/blob/master/src/pokemon.c
    fun maximumPp(move: Int, ups: Int, basePp: Map<Int, Int>): Int {
        require(move in 0..354 && ups in 0..3) { "Invalid Gen 3 move or PP Up count." }
        if (move == 0) {
            require(ups == 0) { "An empty move slot cannot have PP Ups." }
            return 0
        }
        val base = requireNotNull(basePp[move]) { "Verified Gen 3 base PP is unavailable for move #$move." }
        require(base in 1..40) { "The Gen 3 base PP is invalid." }
        require(base > 4 || ups == 0) { "Move #$move does not support PP Ups." }
        return base + base * ups / 5
    }

    private fun decode(record: ByteArray): Gen3Pk3DecodedRecord {
        require(record.size == 80 || record.size == 100) { "Use a complete stored or party PK3 record." }
        require((record[0x13].toInt() and 3) == 2) { "The PK3 is empty or flagged as a Bad Egg." }
        val decoded = Gen3Pk3Codec.decode(record.copyOfRange(0, 80))
        require((record[0x13].toInt() and 4) == 0 &&
            (decoded.canonicalSection[43].toInt() and 0x40) == 0) { "PP Ups cannot be edited on an Egg." }
        return decoded
    }

    private fun moveAt(data: ByteArray, slot: Int): Int =
        (data[12 + slot * 2].toInt() and 255) or ((data[13 + slot * 2].toInt() and 255) shl 8)

    fun read(record: ByteArray, basePp: Map<Int, Int>): List<Int> {
        val data = decode(record).canonicalSection
        return List(4) { slot ->
            val ups = (data[8].toInt() ushr (slot * 2)) and 3
            val pp = data[20 + slot].toInt() and 255
            require(pp <= maximumPp(moveAt(data, slot), ups, basePp)) {
                "Move slot ${slot + 1} has invalid current PP. Nothing was changed."
            }
            ups
        }
    }

    fun replace(record: ByteArray, counts: List<Int>, basePp: Map<Int, Int>): ByteArray {
        val requested = counts.toList()
        require(requested.size == 4 && requested.all { it in 0..3 }) { "Choose 0–3 PP Ups for each of four slots." }
        read(record, basePp)
        val before = decode(record)
        val canonical = before.canonicalSection.copyOf()
        requested.forEachIndexed { slot, ups ->
            require((canonical[20 + slot].toInt() and 255) <= maximumPp(moveAt(canonical, slot), ups, basePp)) {
                "Move slot ${slot + 1} has more current PP than the new maximum. Lower its current PP first."
            }
        }
        canonical[8] = requested.foldIndexed(0) { slot, packed, ups -> packed or (ups shl (slot * 2)) }.toByte()
        val stored = Gen3Pk3Codec.encode(before.header, before.personality,
            before.trainerId, before.secretId, canonical)
        val result = record.copyOf().also { stored.copyInto(it) }
        val after = decode(result)
        require(after.canonicalSection.contentEquals(canonical) &&
            canonical.indices.all { it == 8 || canonical[it] == before.canonicalSection[it] } &&
            (0 until 32).all { it in 28..29 || result[it] == record[it] } &&
            (80 until record.size).all { result[it] == record[it] } && read(result, basePp) == requested) {
            "The PK3 PP Up edit failed exact field or integrity verification."
        }
        return result
    }
}
