package com.example.pokemonsavemanager

enum class Gen3StatusCondition { HEALTHY, SLEEP, POISON, BURN, FREEZE, PARALYSIS, BAD_POISON }

data class Gen3PartyStatus(
    val condition: Gen3StatusCondition,
    val sleepTurns: Int = 0,
    val toxicCounter: Int = 0
) {
    fun requireValid(): Gen3PartyStatus {
        require(if (condition == Gen3StatusCondition.SLEEP) sleepTurns in 1..7 else sleepTurns == 0) {
            "Sleep requires 1–7 remaining turns; other conditions must have zero sleep turns."
        }
        require(if (condition == Gen3StatusCondition.BAD_POISON) toxicCounter in 0..15 else toxicCounter == 0) {
            "The bad-poison counter must be 0–15 and zero for other conditions."
        }
        return this
    }
}

/** Party-only nonvolatile status edits. Stored PC Pokémon have no party status extension. */
object Gen3PartyStatusEditing {
    // Pokemon.status is the u32 at 0x50. No encrypted PK3 bytes or HP/stats are changed.
    // https://github.com/pret/pokeemerald/blob/master/include/pokemon.h
    // https://github.com/pret/pokeemerald/blob/master/include/constants/battle.h
    // https://github.com/pret/pokeemerald/blob/master/src/battle_util.c
    val conditionNames = listOf("Healthy", "Sleep", "Poison", "Burn", "Freeze", "Paralysis", "Bad poison")

    private fun hp(record: ByteArray, offset: Int): Int =
        (record[offset].toInt() and 255) or ((record[offset + 1].toInt() and 255) shl 8)

    fun read(record: ByteArray): Gen3PartyStatus {
        require(record.size == 100) { "Status editing requires a complete 100-byte party Pokémon." }
        require((record[0x13].toInt() and 3) == 2) { "The PK3 is empty or flagged as a Bad Egg." }
        val decoded = Gen3Pk3Codec.decode(record.copyOfRange(0, 80))
        require((record[0x13].toInt() and 4) == 0 &&
            (decoded.canonicalSection[43].toInt() and 0x40) == 0) { "An Egg cannot receive a status condition." }
        require((record[0x54].toInt() and 255) in 1..100 &&
            hp(record, 0x58) > 0 && hp(record, 0x56) <= hp(record, 0x58)) {
            "The party level or HP fields are invalid."
        }
        val word = (0 until 4).fold(0L) { value, index ->
            value or ((record[0x50 + index].toLong() and 255L) shl (index * 8))
        }
        require((word and 0xFFFFF000L) == 0L) { "The party status contains unsupported bits." }
        val flags = word.toInt() and 255
        val sleep = flags and 7
        val conditions = listOf(sleep != 0, (flags and 8) != 0, (flags and 16) != 0,
            (flags and 32) != 0, (flags and 64) != 0, (flags and 128) != 0)
        require(conditions.count { it } <= 1) { "The party status contains conflicting conditions." }
        val condition = when {
            sleep != 0 -> Gen3StatusCondition.SLEEP
            (flags and 8) != 0 -> Gen3StatusCondition.POISON
            (flags and 16) != 0 -> Gen3StatusCondition.BURN
            (flags and 32) != 0 -> Gen3StatusCondition.FREEZE
            (flags and 64) != 0 -> Gen3StatusCondition.PARALYSIS
            (flags and 128) != 0 -> Gen3StatusCondition.BAD_POISON
            else -> Gen3StatusCondition.HEALTHY
        }
        return Gen3PartyStatus(condition, sleep, ((word ushr 8) and 15L).toInt()).requireValid()
    }

    fun replace(record: ByteArray, status: Gen3PartyStatus): ByteArray {
        status.requireValid()
        val before = read(record)
        if (status == before) return record.copyOf()
        require(status.condition == Gen3StatusCondition.HEALTHY || hp(record, 0x56) > 0) {
            "A fainted Pokémon can be cured, but cannot receive a new status condition."
        }
        val word = when (status.condition) {
            Gen3StatusCondition.HEALTHY -> 0
            Gen3StatusCondition.SLEEP -> status.sleepTurns
            Gen3StatusCondition.POISON -> 8
            Gen3StatusCondition.BURN -> 16
            Gen3StatusCondition.FREEZE -> 32
            Gen3StatusCondition.PARALYSIS -> 64
            Gen3StatusCondition.BAD_POISON -> 128 or (status.toxicCounter shl 8)
        }
        val result = record.copyOf()
        for (index in 0 until 4) result[0x50 + index] = (word ushr (index * 8)).toByte()
        require(result.indices.all { it in 0x50..0x53 || result[it] == record[it] } && read(result) == status) {
            "The party status edit failed exact field or integrity verification."
        }
        return result
    }
}
