package com.example.pokemonsavemanager

import java.util.Collections

data class SwordShieldBoxNameRead(
    val box: Int,
    val name: String?,
    val rawBytes: List<Int>,
    val unavailableReason: String?
)

data class SwordShieldBoxLayoutRead(
    val version: SwordShieldVersion,
    val rawCurrentBox: Long,
    val names: List<SwordShieldBoxNameRead>
) {
    val currentBoxIndex: Int? get() = if (rawCurrentBox in 0L..31L) rawCurrentBox.toInt() else null
}

/** Exact read-only projection of PKHeX's documented Sword/Shield BoxLayout8 fields. */
object SwordShieldBoxLayoutReadOnly {
    private const val BOX_COUNT = 32
    private const val NAME_SIZE = 0x22

    fun read(source: ByteArray): SwordShieldBoxLayoutRead {
        val blocks = SwordShieldSaveValidation.readBoxLayoutBlocks(source)
        require(blocks.names.size == BOX_COUNT * NAME_SIZE) {
            "The native Sword/Shield box-name object is truncated."
        }
        require(blocks.currentBox.size == 4) {
            "The native Sword/Shield current-box scalar is truncated."
        }
        val names = (0 until BOX_COUNT).map { index ->
            val raw = blocks.names.copyOfRange(index * NAME_SIZE, (index + 1) * NAME_SIZE)
            val decoded = runCatching { decodeName(raw) }
            SwordShieldBoxNameRead(
                index + 1,
                decoded.getOrNull(),
                immutable(raw.map { it.toInt() and 0xFF }),
                decoded.exceptionOrNull()?.message
            )
        }
        return SwordShieldBoxLayoutRead(
            blocks.version,
            uint32(blocks.currentBox),
            immutable(names)
        )
    }

    private fun decodeName(raw: ByteArray): String {
        val chars = ArrayList<Char>(NAME_SIZE / 2)
        for (offset in raw.indices step 2) {
            val value = (raw[offset].toInt() and 0xFF) or ((raw[offset + 1].toInt() and 0xFF) shl 8)
            if (value == 0) break
            val char = value.toChar()
            when {
                Character.isHighSurrogate(char) -> {
                    require(offset + 3 < raw.size) { "Saved name ends with an unmatched high surrogate." }
                    val next = ((raw[offset + 2].toInt() and 0xFF) or
                        ((raw[offset + 3].toInt() and 0xFF) shl 8)).toChar()
                    require(Character.isLowSurrogate(next)) { "Saved name contains an unmatched high surrogate." }
                }
                Character.isLowSurrogate(char) -> require(chars.lastOrNull()?.let { Character.isHighSurrogate(it) } == true) {
                    "Saved name contains an unmatched low surrogate."
                }
            }
            chars += char
        }
        return chars.joinToString("")
    }

    private fun uint32(bytes: ByteArray): Long =
        (bytes[0].toLong() and 0xFF) or
            ((bytes[1].toLong() and 0xFF) shl 8) or
            ((bytes[2].toLong() and 0xFF) shl 16) or
            ((bytes[3].toLong() and 0xFF) shl 24)

    private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(values)
}
