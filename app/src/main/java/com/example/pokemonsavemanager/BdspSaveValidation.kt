package com.example.pokemonsavemanager

import java.security.MessageDigest

enum class BdspVersion(val romCode: Int, val displayName: String) {
    BRILLIANT_DIAMOND(0, "Pokémon Brilliant Diamond"), SHINING_PEARL(1, "Pokémon Shining Pearl")
}

enum class BdspSaveRevision(val code: Long, val fileSize: Int, val displayName: String) {
    V1_0(0x25, 0xE9828, "1.0"), V1_1(0x2C, 0xEDC20, "1.1"),
    V1_2(0x32, 0xEED8C, "1.2"), V1_3(0x34, 0xEF0A4, "1.3")
}

sealed class BdspValidation {
    object Unrecognized : BdspValidation()
    data class Invalid(val reason: String) : BdspValidation()
    data class Verified(val version: BdspVersion, val revision: BdspSaveRevision) : BdspValidation()
}

/** Native whole-file integrity gate only. No serializer, repair or upstream save object is invoked. */
object BdspSaveValidation {
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16
    private const val ROM_CODE_OFFSET = 0x79BB4 + 0x2B

    fun inspect(source: ByteArray): BdspValidation {
        val revision = BdspSaveRevision.values().firstOrNull { it.fileSize == source.size }
            ?: return BdspValidation.Unrecognized
        val storedRevision = (0..3).fold(0L) { result, index ->
            result or ((source[index].toLong() and 255) shl (index * 8))
        }
        if (storedRevision != revision.code)
            return BdspValidation.Invalid("The BDSP revision does not match the native file size.")
        // The digest field is in the middle for expanded saves. Hash zero bytes in its
        // place without clearing or repairing any caller-owned bytes, including on failure.
        val digest = MessageDigest.getInstance("MD5")
        digest.update(source, 0, HASH_OFFSET)
        digest.update(ByteArray(HASH_LENGTH))
        digest.update(source, HASH_OFFSET + HASH_LENGTH, source.size - HASH_OFFSET - HASH_LENGTH)
        if (!MessageDigest.isEqual(digest.digest(), source.copyOfRange(HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)))
            return BdspValidation.Invalid("The native BDSP whole-file MD5 check failed.")
        val romCode = source[ROM_CODE_OFFSET].toInt() and 255
        val version = BdspVersion.values().firstOrNull { it.romCode == romCode }
            ?: return BdspValidation.Invalid("The saved title code is not Brilliant Diamond or Shining Pearl.")
        return BdspValidation.Verified(version, revision)
    }
}
