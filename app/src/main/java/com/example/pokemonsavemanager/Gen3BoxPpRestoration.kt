package com.example.pokemonsavemanager

data class Gen3BoxPpRecoveryResult(val pc: ByteArray, val changedSlots: List<Int>)

/** Recover selected stored PK3 records together, preserving unselected slots and PC metadata. */
object Gen3BoxPpRestoration {
    fun restore(pc: ByteArray, boxIndex: Int, selectedSlots: Collection<Int>,
        basePp: Map<Int, Int>): Gen3BoxPpRecoveryResult {
        val snapshot = Gen3BoxEditing.snapshotSelectedBoxRecords(pc, boxIndex, selectedSlots)
        val restored = snapshot.records.map { Gen3PpRestoration.restore(it, basePp) }
        val changed = snapshot.slots.filterIndexed { index, _ ->
            !restored[index].contentEquals(snapshot.records[index])
        }
        val start = Gen3BoxEditing.boxStorageRange(boxIndex).first
        val result = pc.copyOf()
        snapshot.slots.forEachIndexed { index, slot -> restored[index].copyInto(result, start + slot * 80) }
        val allowed = changed.flatMap { slot -> (0 until 80).map { start + slot * 80 + it } }.toSet()
        require(result.indices.all { it in allowed || result[it] == pc[it] }) {
            "PP recovery changed an unselected slot or PC metadata."
        }
        snapshot.slots.forEachIndexed { index, slot ->
            val offset = start + slot * 80
            require(result.copyOfRange(offset, offset + 80).contentEquals(restored[index])) {
                "A selected PC record did not stage exactly."
            }
        }
        return Gen3BoxPpRecoveryResult(result, changed)
    }
}
