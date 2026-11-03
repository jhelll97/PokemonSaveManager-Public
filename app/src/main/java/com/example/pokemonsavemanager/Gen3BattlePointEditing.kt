package com.example.pokemonsavemanager

/** Emerald's spendable Battle Frontier BP, separate from Trainer Card earned BP. */
object Gen3BattlePointEditing {
    // pret/pokeemerald: include/global.h, src/frontier_util.c and constants/battle_frontier.h.
    // PKHeX.Core/Saves/Blocks/Gen3/SaveBlock3SmallE.cs: BP +0xEB8, BPEarned +0xEBA.
    const val BALANCE_OFFSET = 0xEB8

    fun read(section0: ByteArray): Int {
        require(section0.size == 0xF2C) { "Use a complete validated Emerald trainer section." }
        val points = (section0[BALANCE_OFFSET].toInt() and 255) or
            ((section0[BALANCE_OFFSET + 1].toInt() and 255) shl 8)
        require(points in 0..9999) { "The loaded Battle Point balance is invalid." }
        return points
    }

    fun replace(section0: ByteArray, points: Int): ByteArray {
        read(section0)
        require(points in 0..9999) { "Choose a Battle Point balance from 0 to 9999." }
        val result = section0.copyOf()
        result[BALANCE_OFFSET] = points.toByte()
        result[BALANCE_OFFSET + 1] = (points ushr 8).toByte()
        require(read(result) == points && result.indices.all {
            it in BALANCE_OFFSET..BALANCE_OFFSET + 1 || result[it] == section0[it]
        }) { "The BP edit changed a record or unrelated trainer data." }
        return result
    }
}
