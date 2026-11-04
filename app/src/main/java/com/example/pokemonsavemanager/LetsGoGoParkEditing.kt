package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class LetsGoGp1ImportRead(
    val bytes: ByteArray,
    val species: Int,
    val cp: Int,
    val level: Float,
    val ivHp: Int,
    val ivAttack: Int,
    val ivDefense: Int,
    val capturedDate: Int,
    val form: Int,
    val shiny: Boolean
)

/** Clears one occupied native GP1 slot and recalculates only block 18's CRC. */
object LetsGoGoParkEditing {
    private const val BLOCK_ID = 18
    private const val DESCRIPTORS = 0xB8614
    private const val CRC_OFFSET = DESCRIPTORS + BLOCK_ID * 8 + 6
    private val BLANK_PREFIX = byteArrayOf(
        0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x80.toByte(), 0x3F, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF0.toByte(), 0x3F,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF0.toByte(), 0x3F, 0x00, 0x00, 0x80.toByte(), 0x3F, 0x00, 0x00, 0x80.toByte(), 0x3F,
        0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x85.toByte(), 0xEC.toByte(), 0x33, 0x01
    )

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        val max = LetsGoSaveValidation.FILE_SIZE
        while (true) {
            val remaining = max + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= max) { "The document exceeds the supported Let's Go save size." }
        }
        output.toByteArray()
    }

    fun stage(source: ByteArray, expectedSha256: String, index: Int): LetsGoGoParkClearCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(index in 0 until LetsGoGoParkReadOnly.RECORD_COUNT) { "GO Park slot is out of range." }
        val beforeRead = LetsGoGoParkReadOnly.read(original)
        val before = beforeRead.entries.firstOrNull { it.index == index }
            ?: throw IllegalArgumentException("That GO Park slot is already empty.")
        val recordOffset = LetsGoGoParkReadOnly.BLOCK_OFFSET + index * LetsGoGoParkReadOnly.RECORD_SIZE
        val edited = original.copyOf()
        blankRecord().copyInto(edited, recordOffset)
        writeU16(edited, CRC_OFFSET, LetsGoSaveValidation.crc16(
            edited, LetsGoGoParkReadOnly.BLOCK_OFFSET, LetsGoGoParkReadOnly.BLOCK_LENGTH))
        verify(original, edited, index, recordOffset)
        return LetsGoGoParkClearCopyPlan(original, edited, before, recordOffset)
    }

    fun inspectGp1(record: ByteArray): LetsGoGp1ImportRead {
        require(record.size == LetsGoGoParkReadOnly.RECORD_SIZE) { "A native GP1 record must be exactly 0x1B0 bytes." }
        fun u16(offset: Int): Int = (record[offset].toInt() and 0xFF) or ((record[offset + 1].toInt() and 0xFF) shl 8)
        fun i32(offset: Int): Int = (record[offset].toInt() and 0xFF) or
            ((record[offset + 1].toInt() and 0xFF) shl 8) or
            ((record[offset + 2].toInt() and 0xFF) shl 16) or (record[offset + 3].toInt() shl 24)
        fun f32(offset: Int): Float = Float.fromBits(i32(offset))
        val species = u16(0x28)
        require(species in 1..151 || species == 808 || species == 809) { "The GP1 species is not available in Let's Go." }
        require(record[0x2A].toInt() == 0 && record[0x2B].toInt() == 0) { "The GP1 species field is not native." }
        val cp = i32(0x2C); val level = f32(0x30); val hp = i32(0x34)
        val height = f32(0x48); val weight = f32(0x4C)
        val ivHp = i32(0x50); val ivAttack = i32(0x54); val ivDefense = i32(0x58)
        val date = i32(0x5C); val year = date / 10_000; val month = (date / 100) % 100; val day = date % 100
        require(cp >= 0 && hp >= 0) { "The GP1 CP or stored HP is invalid." }
        require(level.isFinite() && level in 1f..100f) { "The GP1 level is invalid." }
        require(height.isFinite() && height >= 0f && weight.isFinite() && weight >= 0f) { "The GP1 size values are invalid." }
        require(ivHp in 0..15 && ivAttack in 0..15 && ivDefense in 0..15) { "The GP1 GO IVs are invalid." }
        require(year in 2000..2100 && month in 1..12 && day in 1..31) { "The GP1 captured date is invalid." }
        require((record[0x70].toInt() and 0xFF) in 1..3) { "The GP1 gender value is invalid." }
        require((record[0x73].toInt() and 0xFF) in 0..1) { "The GP1 shiny value is invalid." }
        return LetsGoGp1ImportRead(record.copyOf(), species, cp, level, ivHp, ivAttack, ivDefense,
            date, record[0x72].toInt() and 0xFF, record[0x73].toInt() == 1)
    }

    fun canonicalEmptySlots(source: ByteArray): List<Int> {
        require(LetsGoSaveValidation.inspect(source) is LetsGoValidation.Verified) {
            "A fully validated native Let's Go save is required."
        }
        val blank = blankRecord()
        return (0 until LetsGoGoParkReadOnly.RECORD_COUNT).filter { index ->
            val offset = LetsGoGoParkReadOnly.BLOCK_OFFSET + index * LetsGoGoParkReadOnly.RECORD_SIZE
            source.copyOfRange(offset, offset + LetsGoGoParkReadOnly.RECORD_SIZE).contentEquals(blank)
        }
    }

    fun stageImport(source: ByteArray, expectedSha256: String, index: Int, record: ByteArray): LetsGoGoParkImportCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(index in canonicalEmptySlots(original)) { "Choose a canonical empty GO Park slot." }
        val imported = inspectGp1(record)
        val recordOffset = LetsGoGoParkReadOnly.BLOCK_OFFSET + index * LetsGoGoParkReadOnly.RECORD_SIZE
        val edited = original.copyOf()
        imported.bytes.copyInto(edited, recordOffset)
        writeU16(edited, CRC_OFFSET, LetsGoSaveValidation.crc16(
            edited, LetsGoGoParkReadOnly.BLOCK_OFFSET, LetsGoGoParkReadOnly.BLOCK_LENGTH))
        verifyImport(original, edited, index, imported.bytes, recordOffset)
        return LetsGoGoParkImportCopyPlan(original, edited, imported, index, recordOffset)
    }

    internal fun verifyImport(original: ByteArray, edited: ByteArray, index: Int, record: ByteArray, recordOffset: Int) {
        val before = LetsGoGoParkReadOnly.read(original)
        val after = LetsGoGoParkReadOnly.read(edited)
        require(index in canonicalEmptySlots(original) && after.entries.any { it.index == index }) {
            "The imported GO Park slot did not reopen as occupied."
        }
        require(after.occupiedCount == before.occupiedCount + 1) { "An unexpected GO Park occupancy change occurred." }
        require(after.entries.filterNot { it.index == index } == before.entries) { "An unrelated GO Park record changed." }
        require(edited.copyOfRange(recordOffset, recordOffset + LetsGoGoParkReadOnly.RECORD_SIZE).contentEquals(record)) {
            "The persisted GO Park record differs from the supplied GP1 bytes."
        }
        require(original.indices.all { byteIndex ->
            byteIndex in recordOffset until recordOffset + LetsGoGoParkReadOnly.RECORD_SIZE ||
                byteIndex in CRC_OFFSET until CRC_OFFSET + 2 || original[byteIndex] == edited[byteIndex]
        }) { "An unrelated Let's Go save byte changed." }
    }

    internal fun verify(original: ByteArray, edited: ByteArray, index: Int, recordOffset: Int) {
        val before = LetsGoGoParkReadOnly.read(original)
        val after = LetsGoGoParkReadOnly.read(edited)
        require(before.entries.any { it.index == index } && after.entries.none { it.index == index }) {
            "The selected GO Park slot did not reopen as empty."
        }
        require(after.occupiedCount == before.occupiedCount - 1) { "An unexpected GO Park occupancy change occurred." }
        require(before.entries.filterNot { it.index == index } == after.entries) {
            "An unrelated GO Park record changed."
        }
        require(edited.copyOfRange(recordOffset, recordOffset + LetsGoGoParkReadOnly.RECORD_SIZE)
            .contentEquals(blankRecord())) { "The cleared slot is not the canonical native blank GP1 record." }
        require(original.indices.all { byteIndex ->
            byteIndex in recordOffset until recordOffset + LetsGoGoParkReadOnly.RECORD_SIZE ||
                byteIndex in CRC_OFFSET until CRC_OFFSET + 2 || original[byteIndex] == edited[byteIndex]
        }) { "An unrelated Let's Go save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun blankRecord(): ByteArray = ByteArray(LetsGoGoParkReadOnly.RECORD_SIZE).also {
        BLANK_PREFIX.copyInto(it)
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }
}

class LetsGoGoParkImportCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val imported: LetsGoGp1ImportRead,
    val index: Int,
    val recordOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = LetsGoGoParkEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(readSource: () -> ByteArray, retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray, writeDestination: (ByteArray) -> Unit, discardDestination: () -> Boolean) {
        check(!consumed) { "This review was already used. Reopen GO Park import." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) { "The selected source changed. Reopen it from PSE." }
        requireSource()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        require(retainBackup(original.copyOf()).contentEquals(original)) { "The recovery backup did not verify. Nothing was written." }
        requireSource(); require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LetsGoGoParkEditing.verifyImport(original, edited, index, imported.bytes, recordOffset)
            started = true; writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed import." }
            LetsGoGoParkEditing.verifyImport(original, persisted, index, imported.bytes, recordOffset)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException((failure.message ?: "Copy verification failed.") +
                if (removed) " The failed new copy was removed; the recovery backup is retained."
                else " The new copy could not be removed. Do not use it; the recovery backup is retained.", failure)
        }
    }
}

class LetsGoGoParkClearCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: LetsGoGoParkEntry,
    val recordOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = LetsGoGoParkEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen GO Park." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        require(retainBackup(original.copyOf()).contentEquals(original)) {
            "The recovery backup did not verify. Nothing was written."
        }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LetsGoGoParkEditing.verify(original, edited, before.index, recordOffset)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LetsGoGoParkEditing.verify(original, persisted, before.index, recordOffset)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
