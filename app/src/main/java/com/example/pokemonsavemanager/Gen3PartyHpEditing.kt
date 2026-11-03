package com.example.pokemonsavemanager

data class Gen3PartyHp(val current: Int, val maximum: Int)

/** Edit current party HP only, retaining at least one usable non-Egg party member. */
object Gen3PartyHpEditing {
    // Pokemon.hp/maxHP are unsigned LE u16 fields at 0x56/0x58, outside the encrypted PK3.
    // https://github.com/pret/pokeemerald/blob/master/include/pokemon.h
    private fun u16(record: ByteArray, offset: Int): Int =
        (record[offset].toInt() and 255) or ((record[offset + 1].toInt() and 255) shl 8)

    fun read(record: ByteArray): Gen3PartyHp {
        Gen3PartyStatusEditing.read(record) // Complete party PK3, Egg, status, level and HP validation.
        return Gen3PartyHp(u16(record, 0x56), u16(record, 0x58))
    }

    fun replace(partyRecords: ByteArray, partyCount: Int, slot: Int, hp: Int): ByteArray {
        require(partyRecords.size >= 600 && partyCount in 1..6 && slot in 0 until partyCount) {
            "The Gen 3 party storage, count or selected slot is invalid."
        }
        val members = List(partyCount) { index ->
            val raw = partyRecords.copyOfRange(index * 100, (index + 1) * 100)
            require((raw[0x13].toInt() and 3) == 2) { "An active party PK3 is empty or flagged as a Bad Egg." }
            val decoded = Gen3Pk3Codec.decode(raw.copyOfRange(0, 80))
            val headerEgg = (raw[0x13].toInt() and 4) != 0
            val dataEgg = (decoded.canonicalSection[43].toInt() and 0x40) != 0
            require(headerEgg == dataEgg) { "An active party Pokémon has inconsistent Egg flags." }
            if (dataEgg) null else read(raw)
        }
        val before = requireNotNull(members[slot]) { "An Egg's HP cannot be edited." }
        require(hp in 0..before.maximum) { "Current HP must be between 0 and ${before.maximum}." }
        if (hp == before.current) return partyRecords.copyOf()
        require(hp > 0 || members.indices.any { it != slot && (members[it]?.current ?: 0) > 0 }) {
            "Keep at least one usable non-Egg party Pokémon with HP remaining."
        }
        val result = partyRecords.copyOf()
        val offset = slot * 100 + 0x56
        result[offset] = hp.toByte()
        result[offset + 1] = (hp ushr 8).toByte()
        require(result.indices.all { it in offset..offset + 1 || result[it] == partyRecords[it] } &&
            read(result.copyOfRange(slot * 100, (slot + 1) * 100)) == Gen3PartyHp(hp, before.maximum)) {
            "The current HP edit failed exact field or integrity verification."
        }
        return result
    }
}
