package com.example.pokemonsavemanager

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.util.Collections

data class LetsGoMysteryGiftItem(val itemId: Int, val quantity: Int)

data class LetsGoMysteryGiftRecord(
    val slot: Int,
    val epochSeconds: Long,
    val utcTimestamp: String?,
    val cardId: Int,
    val cardType: Int,
    val giftTypeId: Int,
    val itemCount: Int,
    val species: Int,
    val detailByte: Int,
    val items: List<LetsGoMysteryGiftItem>,
    val originalTrainerName: String?,
    val receivedLanguageId: Int
) {
    val giftTypeName: String get() = when (giftTypeId) {
        0 -> "None"
        1 -> "Pokémon"
        2 -> "Items"
        else -> "Unknown ($giftTypeId)"
    }
}

class LetsGoMysteryGiftHistoryRead internal constructor(
    val version: LetsGoVersion,
    val records: List<LetsGoMysteryGiftRecord>,
    val receivedCardIds: List<Int>
)

/** Native block 11 WR7 summaries and receipt flags; no WB7 reconstruction or write path. */
object LetsGoMysteryGiftHistoryReadOnly {
    private const val BLOCK_OFFSET = 0x45600
    private const val RECORD_SIZE = 0x140
    private const val RECORD_COUNT = 10
    private const val FLAG_OFFSET = BLOCK_OFFSET + RECORD_SIZE * RECORD_COUNT
    private const val FLAG_COUNT = 0x1080

    fun read(source: ByteArray): LetsGoMysteryGiftHistoryRead {
        val checked = LetsGoSaveValidation.inspect(source)
        require(checked is LetsGoValidation.Verified) { "A fully validated native Let's Go save is required." }
        val records = (0 until RECORD_COUNT).mapNotNull { index ->
            val base = BLOCK_OFFSET + index * RECORD_SIZE
            if ((0 until RECORD_SIZE).all { source[base + it].toInt() == 0 }) null else {
                val rawEpoch = (0 until 8).foldIndexed(0uL) { byteIndex, value, _ ->
                    value or (byte(source, base + byteIndex).toULong() shl (8 * byteIndex))
                }.toLong()
                val giftType = byte(source, base + 0x0C)
                val items = Collections.unmodifiableList((0 until 6).mapNotNull { itemIndex ->
                    val item = u16(source, base + 0x110 + itemIndex * 4)
                    val quantity = u16(source, base + 0x112 + itemIndex * 4)
                    if (item == 0 && quantity == 0) null else LetsGoMysteryGiftItem(item, quantity)
                })
                LetsGoMysteryGiftRecord(
                    index + 1,
                    rawEpoch,
                    runCatching { Instant.ofEpochSecond(rawEpoch).toString() }.getOrNull(),
                    u16(source, base + 0x08),
                    u16(source, base + 0x0A),
                    giftType,
                    byte(source, base + 0x0D),
                    u16(source, base + 0x10C),
                    byte(source, base + 0x10E),
                    items,
                    if (giftType == 1) text(source, base + 0x120, 0x1A) else null,
                    byte(source, base + 0x13A)
                )
            }
        }
        val flags = Collections.unmodifiableList((0 until FLAG_COUNT).filter { id ->
            val saved = byte(source, FLAG_OFFSET + id / 8)
            saved and (1 shl (id and 7)) != 0
        })
        return LetsGoMysteryGiftHistoryRead(checked.version, Collections.unmodifiableList(records), flags)
    }

    private fun text(source: ByteArray, offset: Int, length: Int): String? {
        var end = offset
        while (end + 1 < offset + length && u16(source, end) != 0) end += 2
        if (end == offset) return ""
        return runCatching {
            Charsets.UTF_16LE.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(source, offset, end - offset)).toString()
                .takeIf { value -> value.none { Character.isISOControl(it) } }
        }.getOrNull()
    }

    private fun byte(source: ByteArray, offset: Int): Int = source[offset].toInt() and 255
    private fun u16(source: ByteArray, offset: Int): Int = byte(source, offset) or (byte(source, offset + 1) shl 8)
}
