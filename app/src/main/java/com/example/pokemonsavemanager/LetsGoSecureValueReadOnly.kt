package com.example.pokemonsavemanager

import java.util.Collections

data class LetsGoSecureValueRead(
    val version: LetsGoVersion,
    val current: ULong,
    val previous: ULong,
    val currentBytes: List<Int>,
    val previousBytes: List<Int>
)

/** Native SAV_BEEF footer secure values. Their clock/unit is not inferred or converted. */
object LetsGoSecureValueReadOnly {
    private const val FOOTER = 0xB8600

    fun read(source: ByteArray): LetsGoSecureValueRead {
        val checked = LetsGoSaveValidation.inspect(source)
        require(checked is LetsGoValidation.Verified) { "A fully validated native Let's Go save is required." }
        val currentBytes = bytes(source, FOOTER)
        val previousBytes = bytes(source, FOOTER + 8)
        return LetsGoSecureValueRead(
            checked.version,
            value(currentBytes),
            value(previousBytes),
            currentBytes,
            previousBytes
        )
    }

    private fun bytes(source: ByteArray, offset: Int): List<Int> =
        Collections.unmodifiableList((0 until 8).map { source[offset + it].toInt() and 255 })

    private fun value(bytes: List<Int>): ULong = bytes.foldIndexed(0uL) { index, result, byte ->
        result or (byte.toULong() shl (8 * index))
    }
}
