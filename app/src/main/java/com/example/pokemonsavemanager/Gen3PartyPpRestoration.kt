package com.example.pokemonsavemanager

data class Gen3PartyPpRecoveryResult(val partyRecords: ByteArray, val changedSlots: List<Int>)

/** Stage one complete PP-recovery batch; selected records validate before any result is exposed. */
object Gen3PartyPpRestoration {
    fun restore(
        partyRecords: ByteArray, partyCount: Int, selectedSlots: Collection<Int>, basePp: Map<Int, Int>
    ): Gen3PartyPpRecoveryResult {
        require(partyRecords.size >= 600 && partyCount in 1..6) { "The Gen 3 party storage or count is invalid." }
        val slots = selectedSlots.toList()
        require(slots.isNotEmpty() && slots.size == slots.toSet().size && slots.all { it in 0 until partyCount }) {
            "Select at least one unique active party slot."
        }
        val ordered = slots.sorted()
        val restored = ordered.map { slot ->
            val raw = partyRecords.copyOfRange(slot * 100, (slot + 1) * 100)
            Gen3PpRestoration.restore(raw, basePp) // Complete PK3, Egg, current PP and PP Up validation.
        }
        val changed = ordered.filterIndexed { index, slot ->
            !restored[index].contentEquals(partyRecords.copyOfRange(slot * 100, (slot + 1) * 100))
        }
        val result = partyRecords.copyOf()
        ordered.forEachIndexed { index, slot -> restored[index].copyInto(result, slot * 100) }
        require(result.indices.all { index ->
            (index / 100 in ordered && index % 100 < 80) || result[index] == partyRecords[index]
        }) { "PP recovery changed an unselected record, party extension or inactive byte." }
        ordered.forEachIndexed { index, slot ->
            require(result.copyOfRange(slot * 100, (slot + 1) * 100).contentEquals(restored[index])) {
                "A recovered party record did not stage exactly."
            }
        }
        return Gen3PartyPpRecoveryResult(result, changed)
    }
}
