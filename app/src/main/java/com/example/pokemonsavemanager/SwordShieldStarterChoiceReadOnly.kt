package com.example.pokemonsavemanager

data class SwordShieldStarterChoiceRead(
    val version: SwordShieldVersion,
    val rawValue: Long
) {
    val starterName: String? get() = when (rawValue) {
        0L -> "Grookey"
        1L -> "Scorbunny"
        2L -> "Sobble"
        else -> null
    }
}

/** Exact read-only projection of PKHeX's documented Sword/Shield starter-choice record. */
object SwordShieldStarterChoiceReadOnly {
    fun read(source: ByteArray): SwordShieldStarterChoiceRead {
        val block = SwordShieldSaveValidation.readStarterChoiceBlock(source)
        return SwordShieldStarterChoiceRead(block.version, uint32(block.value))
    }

    private fun uint32(bytes: ByteArray): Long {
        require(bytes.size == 4) { "The native Sword/Shield starter-choice record is truncated." }
        return (bytes[0].toLong() and 0xFF) or
            ((bytes[1].toLong() and 0xFF) shl 8) or
            ((bytes[2].toLong() and 0xFF) shl 16) or
            ((bytes[3].toLong() and 0xFF) shl 24)
    }
}
