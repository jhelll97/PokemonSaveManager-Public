package com.example.pokemonsavemanager

import java.io.InputStream

data class ScarletVioletBallChoice(val id: Int, val name: String) {
    override fun toString(): String = "$name (#$id)"
}

data class ScarletVioletPokemonBallValues(
    val pokemon: ScarletVioletPokemonRead,
    val ball: ScarletVioletBallChoice,
    val choices: List<ScarletVioletBallChoice>
)

/** Changes only one native PK9 capture Ball byte in a reviewed new Scarlet/Violet save copy. */
object ScarletVioletPokemonBallEditing {
    private const val STORED_END = 0x148
    private const val BALL_OFFSET = 0x124
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    val choices = listOf(
        ScarletVioletBallChoice(1, "Master Ball"),
        ScarletVioletBallChoice(2, "Ultra Ball"),
        ScarletVioletBallChoice(3, "Great Ball"),
        ScarletVioletBallChoice(4, "Poké Ball"),
        ScarletVioletBallChoice(5, "Safari Ball"),
        ScarletVioletBallChoice(6, "Net Ball"),
        ScarletVioletBallChoice(7, "Dive Ball"),
        ScarletVioletBallChoice(8, "Nest Ball"),
        ScarletVioletBallChoice(9, "Repeat Ball"),
        ScarletVioletBallChoice(10, "Timer Ball"),
        ScarletVioletBallChoice(11, "Luxury Ball"),
        ScarletVioletBallChoice(12, "Premier Ball"),
        ScarletVioletBallChoice(13, "Dusk Ball"),
        ScarletVioletBallChoice(14, "Heal Ball"),
        ScarletVioletBallChoice(15, "Quick Ball"),
        ScarletVioletBallChoice(16, "Cherish Ball"),
        ScarletVioletBallChoice(17, "Fast Ball"),
        ScarletVioletBallChoice(18, "Level Ball"),
        ScarletVioletBallChoice(19, "Lure Ball"),
        ScarletVioletBallChoice(20, "Heavy Ball"),
        ScarletVioletBallChoice(21, "Love Ball"),
        ScarletVioletBallChoice(22, "Friend Ball"),
        ScarletVioletBallChoice(23, "Moon Ball"),
        ScarletVioletBallChoice(24, "Sport Ball"),
        ScarletVioletBallChoice(25, "Dream Ball"),
        ScarletVioletBallChoice(26, "Beast Ball"),
        ScarletVioletBallChoice(27, "Strange Ball")
    )

    fun name(id: Int): String = choices.firstOrNull { it.id == id }?.name ?: "Unknown Ball"

    fun readDocumentBytes(input: InputStream): ByteArray =
        ScarletVioletPokemonNicknameEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = ScarletVioletPokemonNicknameEditing.sha256(bytes)

    fun read(
        source: ByteArray,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int
    ): ScarletVioletPokemonBallValues =
        inspect(targetRecord(ScarletVioletSaveValidation.readPokemonBlocks(source), area, box, position))

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int,
        requestedBall: Int
    ): ScarletVioletPokemonBallCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val beforeRecord = targetRecord(
            ScarletVioletSaveValidation.readPokemonBlocks(original), area, box, position
        )
        val before = inspect(beforeRecord)
        require(choices.any { it.id == requestedBall }) { "Choose a supported PK9 Ball." }
        require(before.ball.id != requestedBall) { "Choose a different capture Ball before review." }
        val afterRecord = editRecord(beforeRecord, requestedBall)
        val written = if (area == ScarletVioletPokemonArea.BOX) {
            ScarletVioletSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else ScarletVioletSaveValidation.writePartyRecord(original, position, afterRecord)
        val after = verify(
            original, written.bytes, area, box, position, written.absoluteOffset, requestedBall
        )
        return ScarletVioletPokemonBallCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset, before, after
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        requestedBall: Int
    ): ScarletVioletPokemonBallValues {
        val beforeBlocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val afterBlocks = ScarletVioletSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The capture Ball edit changed title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val before = inspect(beforeRecord)
        val after = inspect(afterRecord)
        require(after.ball.id == requestedBall && after.choices == before.choices &&
            after.pokemon.copy(ball = before.pokemon.ball) == before.pokemon) {
            "The persisted PK9 changed more than its capture Ball."
        }

        val relativeOffset = if (area == ScarletVioletPokemonArea.BOX)
            ((box - 1) * 30 + position - 1) * ScarletVioletPokemonReadOnly.RECORD_SIZE
        else (position - 1) * ScarletVioletPokemonReadOnly.RECORD_SIZE
        val beforeTarget = if (area == ScarletVioletPokemonArea.BOX) beforeBlocks.boxes else beforeBlocks.party
        val afterTarget = if (area == ScarletVioletPokemonArea.BOX) afterBlocks.boxes else afterBlocks.party
        val beforeOther = if (area == ScarletVioletPokemonArea.BOX) beforeBlocks.party else beforeBlocks.boxes
        val afterOther = if (area == ScarletVioletPokemonArea.BOX) afterBlocks.party else afterBlocks.boxes
        require(beforeOther.contentEquals(afterOther)) {
            "The unselected native Pokémon storage object changed."
        }
        require(beforeTarget.indices.all { index ->
            index in relativeOffset until relativeOffset + ScarletVioletPokemonReadOnly.RECORD_SIZE ||
                beforeTarget[index] == afterTarget[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(byte(afterPlain, BALL_OFFSET) == requestedBall) {
            "The native PK9 Ball byte was not changed exactly."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset == BALL_OFFSET || beforePlain[offset] == afterPlain[offset]
        }) { "The PK9 edit exceeded capture Ball and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + ScarletVioletPokemonReadOnly.RECORD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
        return after
    }

    private fun inspect(record: ByteArray): ScarletVioletPokemonBallValues {
        val pokemon = (ScarletVioletPokemonReadOnly.read(record) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK9 is not checksum-valid.")
        require(!pokemon.isEgg) { "Egg PK9 records remain protected from capture Ball editing." }
        require(!pokemon.fatefulEncounter) {
            "Fateful-event PK9 records remain protected because their Ball is distribution-specific."
        }
        val current = choices.firstOrNull { it.id == pokemon.ball }
            ?: throw IllegalArgumentException(
                "The saved Ball ID ${pokemon.ball} is outside the supported PK9 set and remains unchanged."
            )
        return ScarletVioletPokemonBallValues(pokemon, current, choices)
    }

    private fun targetRecord(
        blocks: ScarletVioletPokemonBlocks,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int
    ): ByteArray {
        val size = ScarletVioletPokemonReadOnly.RECORD_SIZE
        val source: ByteArray
        val offset: Int
        if (area == ScarletVioletPokemonArea.BOX) {
            require(box in 1..32 && position in 1..30) {
                "The selected native PC-box slot is outside storage."
            }
            source = blocks.boxes
            offset = ((box - 1) * 30 + position - 1) * size
        } else {
            require(position in 1..6) { "The selected native party slot is outside the six-slot party." }
            val count = blocks.party[6 * size].toInt() and 255
            require(position <= count) { "The selected native party slot is not occupied." }
            source = blocks.party
            offset = (position - 1) * size
        }
        return source.copyOfRange(offset, offset + size)
    }

    private fun editRecord(record: ByteArray, requestedBall: Int): ByteArray {
        val plain = decryptAndUnshuffle(record)
        plain[BALL_OFFSET] = requestedBall.toByte()
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun decryptAndUnshuffle(record: ByteArray): ByteArray {
        require(record.size == ScarletVioletPokemonReadOnly.RECORD_SIZE) {
            "Expected one complete native PK9 record."
        }
        val ec = u32(record, 0)
        val decoded = record.copyOf()
        xorWords(decoded, 8, STORED_END, ec)
        xorWords(decoded, STORED_END, ScarletVioletPokemonReadOnly.RECORD_SIZE, ec)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val plain = decoded.copyOf()
        for (block in 0..3) {
            val from = 8 + (order[block] - '0') * 80
            decoded.copyInto(plain, 8 + block * 80, from, from + 80)
        }
        return plain
    }

    private fun shuffleAndEncrypt(plain: ByteArray): ByteArray {
        val ec = u32(plain, 0)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val shuffled = plain.copyOf()
        for (block in 0..3) {
            val destination = 8 + (order[block] - '0') * 80
            plain.copyInto(shuffled, destination, 8 + block * 80, 8 + (block + 1) * 80)
        }
        xorWords(shuffled, 8, STORED_END, ec)
        xorWords(shuffled, STORED_END, ScarletVioletPokemonReadOnly.RECORD_SIZE, ec)
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
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
}

class ScarletVioletPokemonBallCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: ScarletVioletPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: ScarletVioletPokemonBallValues,
    val after: ScarletVioletPokemonBallValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = ScarletVioletPokemonBallEditing.sha256(this.edited)
    private var consumed = false
    val location: String get() = if (area == ScarletVioletPokemonArea.BOX)
        "Box $box • Slot $position" else "Party $position"

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This capture Ball review was already used. Reopen Pokémon Boxes." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) {
            "Choose a new empty document; existing files cannot be replaced."
        }
        require(retainBackup(original.copyOf()).contentEquals(original)) {
            "The recovery backup did not verify. Nothing was written."
        }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            ScarletVioletPokemonBallEditing.verify(
                original, edited, area, box, position, absoluteOffset, after.ball.id
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed capture Ball edit."
            }
            ScarletVioletPokemonBallEditing.verify(
                original, persisted, area, box, position, absoluteOffset, after.ball.id
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Capture Ball copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
