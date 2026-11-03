package com.example.pokemonsavemanager

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections

class BdspAdditionalTrainerRecordRead internal constructor(val index: Int, val name: String?, val nameIssue: String?,
    val hasNameTerminator: Boolean, val languageId: Int, val genderByte: Int, val bodyTypeId: Int,
    val id32: Long, val rawRecordBytes: List<Int>) {
    val tid16: Int get() = (id32 and 65535).toInt()
    val sid16: Int get() = (id32 ushr 16).toInt()
    val hasNonzeroRecordBytes: Boolean get() = rawRecordBytes.any { it != 0 }
}

class BdspAdditionalRecordsRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val hasBlock: Boolean, val records: List<BdspAdditionalTrainerRecordRead>,
    val rawRankingBytes: List<Int>, val rawBlockBytes: List<Int>) {
    val absenceReason: String? get() = if (hasBlock) null else "This native save layout does not store the additional-record block."
    fun record(index: Int): BdspAdditionalTrainerRecordRead {
        require(index in records.indices) { "The additional record index is not present in this save." }
        return records[index]
    }
}

/** Native expansion only. Never substitute GetSafe's synthetic buffer for an absent saved block. */
object BdspAdditionalRecordsReadOnly {
    fun read(source: ByteArray): BdspAdditionalRecordsRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) {
            (checked as? BdspValidation.Invalid)?.reason ?: "A validated native BDSP save is required."
        }
        val base = 0xE9828
        if (source.size < base + 0x3C0)
            return BdspAdditionalRecordsRead(checked.version, checked.revision, false, emptyList(), emptyList(), emptyList())
        fun bytes(offset: Int, count: Int): List<Int> = Collections.unmodifiableList((0 until count).map { source[offset + it].toInt() and 255 })
        fun word(offset: Int): Long = (0..3).fold(0L) { n, i -> n or ((source[offset + i].toLong() and 255) shl (8 * i)) }
        fun codeUnit(offset: Int): Int = (source[offset].toInt() and 255) or ((source[offset + 1].toInt() and 255) shl 8)
        val records = (0 until 12).map { index ->
            val offset = base + index * 0x30
            var length = 0
            while (length < 26 && codeUnit(offset + length) != 0) length += 2
            val decoded = runCatching {
                val text = Charsets.UTF_16LE.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(source, offset, length)).toString()
                require(text.none { Character.isISOControl(it) }) { "The saved name contains control characters." }
                text
            }
            val issue = decoded.exceptionOrNull()?.let {
                if (it is java.nio.charset.CharacterCodingException) "The saved name contains malformed UTF-16."
                else "The saved name contains control characters."
            }
            BdspAdditionalTrainerRecordRead(index, decoded.getOrNull(), issue, length < 26,
                word(offset + 0x1C).toInt(), source[offset + 0x20].toInt() and 255, word(offset + 0x24).toInt(),
                word(offset + 0x28), bytes(offset, 0x30))
        }
        return BdspAdditionalRecordsRead(checked.version, checked.revision, true, Collections.unmodifiableList(records),
            bytes(base + 0x240, 0x180), bytes(base, 0x3C0))
    }
}
