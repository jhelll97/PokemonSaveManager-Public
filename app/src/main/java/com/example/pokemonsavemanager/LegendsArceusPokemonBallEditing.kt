package com.example.pokemonsavemanager

import java.io.InputStream

data class LegendsArceusBallChoice(val id: Int, val name: String) {
    override fun toString(): String = "$name (#$id)"
}

data class LegendsArceusPokemonBallValues(
    val pokemon: LegendsArceusPokemonRead,
    val ball: LegendsArceusBallChoice,
    val choices: List<LegendsArceusBallChoice>
)

/** Changes only one native PA8 capture Ball in a reviewed new Legends: Arceus save copy. */
object LegendsArceusPokemonBallEditing {
    private const val STORED_END = 0x168
    private const val BALL_OFFSET = 0x137
    private const val BLOCK_SIZE = 88
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")
    private val standardChoices = listOf(
        LegendsArceusBallChoice(28, "Poké Ball"),
        LegendsArceusBallChoice(29, "Great Ball"),
        LegendsArceusBallChoice(30, "Ultra Ball"),
        LegendsArceusBallChoice(31, "Feather Ball"),
        LegendsArceusBallChoice(32, "Wing Ball"),
        LegendsArceusBallChoice(33, "Jet Ball"),
        LegendsArceusBallChoice(34, "Heavy Ball"),
        LegendsArceusBallChoice(35, "Leaden Ball"),
        LegendsArceusBallChoice(36, "Gigaton Ball")
    )
    private val originBall = LegendsArceusBallChoice(37, "Origin Ball")

    fun readDocumentBytes(input: InputStream): ByteArray =
        LegendsArceusPokemonNicknameEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = LegendsArceusPokemonNicknameEditing.sha256(bytes)

    fun read(
        source: ByteArray,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int
    ): LegendsArceusPokemonBallValues {
        val blocks = LegendsArceusSaveValidation.readPokemonBlocks(source)
        return inspect(targetRecord(blocks, area, pasture, position))
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        requestedBall: Int
    ): LegendsArceusPokemonBallCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val beforeRecord = targetRecord(blocks, area, pasture, position)
        val before = inspect(beforeRecord)
        require(!before.pokemon.isEgg) { "Egg PA8 records remain protected from Ball editing." }
        require(!before.pokemon.isNoble) { "Noble PA8 records remain protected from Ball editing." }
        require(!before.pokemon.fatefulEncounter) {
            "Fateful-event PA8 records remain protected because their Ball is distribution-specific."
        }
        val selected = before.choices.singleOrNull { it.id == requestedBall }
            ?: throw IllegalArgumentException("That Ball is not available for this PA8 species.")
        require(before.ball.id != requestedBall) { "Choose a different Ball before review." }
        val afterRecord = editRecord(beforeRecord, requestedBall)
        val written = if (area == LegendsArceusPokemonArea.PASTURE) {
            LegendsArceusSaveValidation.writePastureRecord(original, pasture, position, afterRecord)
        } else LegendsArceusSaveValidation.writePartyRecord(original, position, afterRecord)
        val after = verify(
            original, written.bytes, area, pasture, position, written.absoluteOffset, requestedBall
        )
        return LegendsArceusPokemonBallCopyPlan(
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
        requestedBall: Int
    ): LegendsArceusPokemonBallValues {
        val beforeBlocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsArceusSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The Ball edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, pasture, position)
        val afterRecord = targetRecord(afterBlocks, area, pasture, position)
        val before = inspect(beforeRecord)
        val after = inspect(afterRecord)
        require(after.ball.id == requestedBall && after.choices == before.choices &&
            after.pokemon.copy(ball = before.pokemon.ball) == before.pokemon) {
            "The persisted PA8 changed more than its capture Ball."
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
        require(byte(afterPlain, BALL_OFFSET) == requestedBall) { "The native Ball byte was not changed exactly." }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset == BALL_OFFSET || beforePlain[offset] == afterPlain[offset]
        }) { "The PA8 edit exceeded the Ball and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + recordSize ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
        return after
    }

    private fun inspect(record: ByteArray): LegendsArceusPokemonBallValues {
        val pokemon = (LegendsArceusPokemonReadOnly.read(record) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA8 is not checksum-valid.")
        require(!pokemon.isEgg) { "Egg PA8 records remain protected from Ball editing." }
        require(!pokemon.isNoble) { "Noble PA8 records remain protected from Ball editing." }
        require(!pokemon.fatefulEncounter) {
            "Fateful-event PA8 records remain protected because their Ball is distribution-specific."
        }
        val choices = choices(pokemon.species)
        val current = choices.singleOrNull { it.id == pokemon.ball }
            ?: throw IllegalArgumentException(
                "The saved Ball is outside the supported native PLA capture set and remains unchanged."
            )
        return LegendsArceusPokemonBallValues(pokemon, current, choices)
    }

    private fun choices(species: Int): List<LegendsArceusBallChoice> =
        if (species == 483 || species == 484) standardChoices + originBall else standardChoices

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

    private fun editRecord(record: ByteArray, requestedBall: Int): ByteArray {
        val plain = decryptAndUnshuffle(record)
        plain[BALL_OFFSET] = requestedBall.toByte()
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

class LegendsArceusPokemonBallCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: LegendsArceusPokemonArea,
    val pasture: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: LegendsArceusPokemonBallValues,
    val after: LegendsArceusPokemonBallValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = LegendsArceusPokemonBallEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This Ball review was already used. Reopen Pokémon Details." }
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
            LegendsArceusPokemonBallEditing.verify(
                original, edited, area, pasture, position, absoluteOffset, after.ball.id
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed Ball edit." }
            LegendsArceusPokemonBallEditing.verify(
                original, persisted, area, pasture, position, absoluteOffset, after.ball.id
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Ball copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
