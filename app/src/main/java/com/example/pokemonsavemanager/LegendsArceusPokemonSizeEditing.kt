package com.example.pokemonsavemanager

import java.io.InputStream

data class LegendsArceusPokemonSizeValues(
    val pokemon: LegendsArceusPokemonRead,
    val heightScalar: Int,
    val weightScalar: Int,
    val displayScale: Int
)

/** Changes only one native PA8 size tuple in a reviewed new Legends: Arceus save copy. */
object LegendsArceusPokemonSizeEditing {
    private const val STORED_END = 0x168
    private const val HEIGHT_SCALAR = 0x50
    private const val WEIGHT_SCALAR = 0x51
    private const val DISPLAY_SCALE = 0x52
    private const val BLOCK_SIZE = 88
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: InputStream): ByteArray =
        LegendsArceusPokemonNicknameEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = LegendsArceusPokemonNicknameEditing.sha256(bytes)

    fun read(
        source: ByteArray,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int
    ): LegendsArceusPokemonSizeValues {
        val blocks = LegendsArceusSaveValidation.readPokemonBlocks(source)
        return inspect(targetRecord(blocks, area, pasture, position))
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        requestedHeightScalar: Int,
        requestedWeightScalar: Int
    ): LegendsArceusPokemonSizeCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(requestedHeightScalar in 0..255) { "Height Scalar must be between 0 and 255." }
        require(requestedWeightScalar in 0..255) { "Weight Scalar must be between 0 and 255." }
        val blocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val beforeRecord = targetRecord(blocks, area, pasture, position)
        val before = inspect(beforeRecord)
        require(!before.pokemon.isEgg) { "Egg PA8 records remain protected from size editing." }
        require(!before.pokemon.isNoble) { "Noble PA8 records remain protected from size editing." }
        require(!before.pokemon.isAlpha) {
            "Alpha PA8 size is encounter-bound. PSE does not expose an unsafe size control for it."
        }
        require(before.heightScalar == before.displayScale) {
            "The saved display Scale does not match Height Scalar. PSE will not normalize an inconsistent record."
        }
        require(before.heightScalar != requestedHeightScalar || before.weightScalar != requestedWeightScalar) {
            "Enter a different Height Scalar or Weight Scalar before review."
        }
        val afterRecord = editRecord(beforeRecord, requestedHeightScalar, requestedWeightScalar)
        val written = if (area == LegendsArceusPokemonArea.PASTURE) {
            LegendsArceusSaveValidation.writePastureRecord(original, pasture, position, afterRecord)
        } else LegendsArceusSaveValidation.writePartyRecord(original, position, afterRecord)
        val after = verify(
            original, written.bytes, area, pasture, position, written.absoluteOffset,
            requestedHeightScalar, requestedWeightScalar
        )
        return LegendsArceusPokemonSizeCopyPlan(
            original, written.bytes, area, pasture, position, written.absoluteOffset, before, after
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        absoluteOffset: Int,
        requestedHeightScalar: Int,
        requestedWeightScalar: Int
    ): LegendsArceusPokemonSizeValues {
        val beforeBlocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsArceusSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The size edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, pasture, position)
        val afterRecord = targetRecord(afterBlocks, area, pasture, position)
        val before = inspect(beforeRecord)
        val after = inspect(afterRecord)
        require(after.pokemon == before.pokemon &&
            after.heightScalar == requestedHeightScalar &&
            after.weightScalar == requestedWeightScalar &&
            after.displayScale == requestedHeightScalar) {
            "The persisted PA8 changed outside its native size tuple."
        }

        val recordSize = recordSize(area)
        val relativeOffset = if (area == LegendsArceusPokemonArea.PASTURE)
            ((pasture - 1) * 30 + position - 1) * recordSize else (position - 1) * recordSize
        val beforeTarget = if (area == LegendsArceusPokemonArea.PASTURE) beforeBlocks.pastures else beforeBlocks.party
        val afterTarget = if (area == LegendsArceusPokemonArea.PASTURE) afterBlocks.pastures else afterBlocks.party
        val beforeOther = if (area == LegendsArceusPokemonArea.PASTURE) beforeBlocks.party else beforeBlocks.pastures
        val afterOther = if (area == LegendsArceusPokemonArea.PASTURE) afterBlocks.party else afterBlocks.pastures
        require(beforeOther.contentEquals(afterOther)) { "The unselected native Pokémon storage object changed." }
        require(beforeTarget.indices.all { index ->
            index in relativeOffset until relativeOffset + recordSize || beforeTarget[index] == afterTarget[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(byte(afterPlain, HEIGHT_SCALAR) == requestedHeightScalar &&
            byte(afterPlain, WEIGHT_SCALAR) == requestedWeightScalar &&
            byte(afterPlain, DISPLAY_SCALE) == requestedHeightScalar) {
            "The native size tuple was not changed exactly."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset == HEIGHT_SCALAR || offset == WEIGHT_SCALAR ||
                offset == DISPLAY_SCALE || beforePlain[offset] == afterPlain[offset]
        }) { "The PA8 edit exceeded Height Scalar, Weight Scalar, display Scale and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + recordSize ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
        return after
    }

    private fun inspect(record: ByteArray): LegendsArceusPokemonSizeValues {
        val pokemon = (LegendsArceusPokemonReadOnly.read(record) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA8 is not checksum-valid.")
        val plain = decryptAndUnshuffle(record)
        return LegendsArceusPokemonSizeValues(
            pokemon,
            byte(plain, HEIGHT_SCALAR),
            byte(plain, WEIGHT_SCALAR),
            byte(plain, DISPLAY_SCALE)
        )
    }

    private fun targetRecord(
        blocks: LegendsArceusPokemonBlocks,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int
    ): ByteArray {
        val size = recordSize(area)
        if (area == LegendsArceusPokemonArea.PASTURE) {
            require(pasture in 1..32 && position in 1..30) { "The selected native pasture slot is outside storage." }
            val offset = ((pasture - 1) * 30 + position - 1) * size
            return blocks.pastures.copyOfRange(offset, offset + size)
        }
        require(position in 1..6) { "The selected native party slot is outside the six-slot party." }
        val count = blocks.party[6 * size].toInt() and 255
        require(position <= count) { "The selected native party slot is not occupied." }
        val offset = (position - 1) * size
        return blocks.party.copyOfRange(offset, offset + size)
    }

    private fun recordSize(area: LegendsArceusPokemonArea): Int =
        if (area == LegendsArceusPokemonArea.PASTURE) LegendsArceusPokemonReadOnly.STORED_SIZE
        else LegendsArceusPokemonReadOnly.PARTY_SIZE

    private fun editRecord(record: ByteArray, heightScalar: Int, weightScalar: Int): ByteArray {
        val plain = decryptAndUnshuffle(record)
        plain[HEIGHT_SCALAR] = heightScalar.toByte()
        plain[WEIGHT_SCALAR] = weightScalar.toByte()
        plain[DISPLAY_SCALE] = heightScalar.toByte()
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun decryptAndUnshuffle(record: ByteArray): ByteArray {
        require(record.size == LegendsArceusPokemonReadOnly.STORED_SIZE ||
            record.size == LegendsArceusPokemonReadOnly.PARTY_SIZE) {
            "Expected one complete native stored or party PA8 record."
        }
        val ec = u32(record, 0)
        val decoded = record.copyOf()
        xorWords(decoded, 8, STORED_END, ec)
        if (record.size == LegendsArceusPokemonReadOnly.PARTY_SIZE)
            xorWords(decoded, STORED_END, LegendsArceusPokemonReadOnly.PARTY_SIZE, ec)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val plain = decoded.copyOf()
        for (block in 0..3) {
            val from = 8 + (order[block] - '0') * BLOCK_SIZE
            decoded.copyInto(plain, 8 + block * BLOCK_SIZE, from, from + BLOCK_SIZE)
        }
        return plain
    }

    private fun shuffleAndEncrypt(plain: ByteArray): ByteArray {
        val ec = u32(plain, 0)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val shuffled = plain.copyOf()
        for (block in 0..3) {
            val destination = 8 + (order[block] - '0') * BLOCK_SIZE
            plain.copyInto(shuffled, destination, 8 + block * BLOCK_SIZE, 8 + (block + 1) * BLOCK_SIZE)
        }
        xorWords(shuffled, 8, STORED_END, ec)
        if (plain.size == LegendsArceusPokemonReadOnly.PARTY_SIZE)
            xorWords(shuffled, STORED_END, LegendsArceusPokemonReadOnly.PARTY_SIZE, ec)
        return shuffled
    }

    private fun xorWords(bytes: ByteArray, from: Int, end: Int, seed: Long) {
        var state = seed
        for (offset in from until end step 2) {
            state = (state * 0x41C64E6DL + 0x6073L) and 0xFFFFFFFFL
            writeU16(bytes, offset, u16(bytes, offset) xor (state ushr 16).toInt())
        }
    }

    private fun byte(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 255
    private fun u16(bytes: ByteArray, offset: Int): Int = byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long =
        u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }
}

class LegendsArceusPokemonSizeCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: LegendsArceusPokemonArea,
    val pasture: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: LegendsArceusPokemonSizeValues,
    val after: LegendsArceusPokemonSizeValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = LegendsArceusPokemonSizeEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This size review was already used. Reopen Pokémon Details." }
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
            LegendsArceusPokemonSizeEditing.verify(
                original, edited, area, pasture, position, absoluteOffset,
                after.heightScalar, after.weightScalar
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed size edit." }
            LegendsArceusPokemonSizeEditing.verify(
                original, persisted, area, pasture, position, absoluteOffset,
                after.heightScalar, after.weightScalar
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Size copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
