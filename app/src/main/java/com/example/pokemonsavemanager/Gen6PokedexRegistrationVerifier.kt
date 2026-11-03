package com.example.pokemonsavemanager

/**
 * Verifies that migrated non-egg Pokémon remain registered in the destination
 * Pokédex after checksum rewrite and reopen.
 */
internal object Gen6PokedexRegistrationVerifier {
    private const val DEX_OFFSET = 0x15000
    private const val REGION_LENGTH = 0x60
    private const val XY_DEX_LENGTH = 0x006A0
    private const val ORAS_DEX_LENGTH = 0x011CC

    fun requireRegistered(
        destinationSave: ByteArray,
        destinationGame: Gen6ConverterGame,
        records: List<Gen6Pk6Record>
    ) {
        val dexLength = when (destinationGame) {
            Gen6ConverterGame.X,
            Gen6ConverterGame.Y -> XY_DEX_LENGTH
            Gen6ConverterGame.OMEGA_RUBY,
            Gen6ConverterGame.ALPHA_SAPPHIRE -> ORAS_DEX_LENGTH
        }
        require(DEX_OFFSET + dexLength <= destinationSave.size) {
            "Destination Gen 6 Pokédex block is outside the save."
        }

        records.forEachIndexed { index, record ->
            if (record.isEgg) return@forEachIndexed
            val bit = record.species - 1
            val byteIndex = bit ushr 3
            val mask = 1 shl (bit and 7)
            check(hasFlag(destinationSave, 0, byteIndex, mask)) {
                "Destination Pokédex ownership is missing for migrated PK6 record $index."
            }

            val shinyValue = record.tid xor record.sid xor
                (record.pid.toInt() and 0xFFFF) xor
                ((record.pid ushr 16).toInt() and 0xFFFF)
            val gender = record.gender and 1
            val variant =
                gender or ((if (shinyValue < 16) 1 else 0) shl 1)
            check(hasFlag(destinationSave, 1 + variant, byteIndex, mask)) {
                "Destination Pokédex seen variant is missing for migrated PK6 record $index."
            }
            check((5..8).any {
                hasFlag(destinationSave, it, byteIndex, mask)
            }) {
                "Destination Pokédex display entry is missing for migrated PK6 record $index."
            }
        }
    }

    private fun hasFlag(
        save: ByteArray,
        region: Int,
        byteIndex: Int,
        mask: Int
    ): Boolean {
        val offset =
            DEX_OFFSET + 0x08 + REGION_LENGTH * region + byteIndex
        return (save[offset].toInt() and mask) != 0
    }
}
