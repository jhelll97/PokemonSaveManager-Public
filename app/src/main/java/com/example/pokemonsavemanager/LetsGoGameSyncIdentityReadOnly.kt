package com.example.pokemonsavemanager

data class LetsGoGameSyncIdentityRead(
    val version: LetsGoVersion,
    val idBytes: List<Int>
) {
    val hex: String get() = idBytes.joinToString("") { it.toString(16).uppercase().padStart(2, '0') }
    val isUnset: Boolean get() = idBytes.all { it == 0 }
}

/** Reads PKHeX's documented eight-byte MyStatus Game Sync ID without contacting a service. */
object LetsGoGameSyncIdentityReadOnly {
    private const val STATUS = 0x01000
    private const val ID_OFFSET = 0x010
    private const val ID_LENGTH = 8

    fun read(bytes: ByteArray): LetsGoGameSyncIdentityRead {
        val verified = LetsGoSaveValidation.inspect(bytes) as? LetsGoValidation.Verified
            ?: throw IllegalArgumentException("A checksum-valid native Let's Go save is required.")
        return LetsGoGameSyncIdentityRead(
            version = verified.version,
            idBytes = (0 until ID_LENGTH).map { index ->
                bytes[STATUS + ID_OFFSET + index].toInt() and 0xFF
            }
        )
    }
}
