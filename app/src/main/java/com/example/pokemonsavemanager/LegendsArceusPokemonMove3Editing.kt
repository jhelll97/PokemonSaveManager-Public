package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Changes only Move 3 and its current PP in one reviewed native PA8 copy. */
object LegendsArceusPokemonMove3Editing {
    private const val MAX_FILE_SIZE = 0x13AD06
    private const val STORED_END = 0x168
    private const val MOVE_3 = 0x58
    private const val MOVE_3_PP = 0x5E
    private const val BLOCK_SIZE = 88
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val remaining = MAX_FILE_SIZE + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= MAX_FILE_SIZE) {
                "The document exceeds the supported Legends: Arceus save size."
            }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        requestedMoveId: Int,
        requestedPp: Int
    ): LegendsArceusMove3CopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, pasture, position)
        val before = (LegendsArceusPokemonReadOnly.read(target) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PA8 is not checksum-valid.")
        validateMoves(before.moves)
        require(before.moves[1].id != 0) { "Move 2 must be set before Move 3 can be edited." }
        val move = LegendsArceusMoveCatalog.choice(requestedMoveId)
            ?: throw IllegalArgumentException("Move #$requestedMoveId is not usable in Legends: Arceus.")
        require(before.moves.withIndex().none { it.index != 2 && it.value.id == move.id }) {
            "${move.name} already appears in another move slot. Legends: Arceus move slots must be unique."
        }
        val maximumPp = LegendsArceusMoveCatalog.maximumPp(move.id)
        require(requestedPp in 0..maximumPp) {
            "Current PP must be between 0 and $maximumPp for ${move.name}."
        }
        val beforeMove = before.moves[2]
        require(beforeMove.id != move.id || beforeMove.pp != requestedPp) {
            "Choose a different Move 3 or current PP value before review."
        }
        val afterRecord = editRecord(target, move.id, requestedPp)
        require(LegendsArceusPokemonReadOnly.read(afterRecord) is LegendsArceusSlotRead.Pokemon) {
            "The edited PA8 did not pass its native checksum."
        }
        val written = if (area == LegendsArceusPokemonArea.PASTURE) {
            LegendsArceusSaveValidation.writePastureRecord(original, pasture, position, afterRecord)
        } else LegendsArceusSaveValidation.writePartyRecord(original, position, afterRecord)
        verify(original, written.bytes, area, pasture, position, written.absoluteOffset, move.id, requestedPp)
        return LegendsArceusMove3CopyPlan(
            original, written.bytes, area, pasture, position, written.absoluteOffset,
            before, move, requestedPp, maximumPp
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int,
        absoluteOffset: Int,
        moveId: Int,
        pp: Int
    ) {
        val beforeBlocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsArceusSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The Move 3 edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, pasture, position)
        val afterRecord = targetRecord(afterBlocks, area, pasture, position)
        val beforePokemon = (LegendsArceusPokemonReadOnly.read(beforeRecord) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PA8 no longer reopens as a Pokémon.")
        val afterPokemon = (LegendsArceusPokemonReadOnly.read(afterRecord) as? LegendsArceusSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PA8 did not reopen as a Pokémon.")
        validateMoves(beforePokemon.moves)
        validateMoves(afterPokemon.moves)
        require(afterPokemon.moves[2] == LegendsArceusMoveRead(moveId, pp) &&
            afterPokemon.moves.indices.filter { it != 2 }.all {
                afterPokemon.moves[it] == beforePokemon.moves[it]
            } &&
            afterPokemon.copy(moves = beforePokemon.moves) == beforePokemon) {
            "The persisted PA8 changed more than Move 3 and its current PP."
        }

        val recordSize = recordSize(area)
        val relativeOffset = if (area == LegendsArceusPokemonArea.PASTURE)
            ((pasture - 1) * 30 + position - 1) * recordSize else (position - 1) * recordSize
        val beforeTargetBlock = if (area == LegendsArceusPokemonArea.PASTURE) beforeBlocks.pastures else beforeBlocks.party
        val afterTargetBlock = if (area == LegendsArceusPokemonArea.PASTURE) afterBlocks.pastures else afterBlocks.party
        val beforeOtherBlock = if (area == LegendsArceusPokemonArea.PASTURE) beforeBlocks.party else beforeBlocks.pastures
        val afterOtherBlock = if (area == LegendsArceusPokemonArea.PASTURE) afterBlocks.party else afterBlocks.pastures
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) {
            "The unselected native Pokémon storage object changed."
        }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + recordSize ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in MOVE_3 until MOVE_3 + 2 || offset == MOVE_3_PP ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PA8 edit exceeded Move 3, its current PP, and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + recordSize ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun validateMoves(moves: List<LegendsArceusMoveRead>) {
        require(moves.size == 4) { "Expected four native PA8 move slots." }
        var foundEmpty = false
        val seen = mutableSetOf<Int>()
        moves.forEachIndexed { index, move ->
            if (move.id == 0) {
                foundEmpty = true
                require(move.pp == 0) { "An empty move slot retains PP data." }
            } else {
                require(!foundEmpty) { "Active moves must be consecutive from Move 1." }
                require(seen.add(move.id)) { "Duplicate native moves are not editable." }
                val maximum = LegendsArceusMoveCatalog.maximumPp(move.id)
                require(move.pp in 0..maximum) {
                    "Move ${index + 1} current PP exceeds its Legends: Arceus maximum."
                }
            }
        }
    }

    private fun targetRecord(
        blocks: LegendsArceusPokemonBlocks,
        area: LegendsArceusPokemonArea,
        pasture: Int,
        position: Int
    ): ByteArray {
        val size = recordSize(area)
        val source: ByteArray
        val offset: Int
        if (area == LegendsArceusPokemonArea.PASTURE) {
            require(pasture in 1..32 && position in 1..30) {
                "The selected native pasture slot is outside storage."
            }
            source = blocks.pastures
            offset = ((pasture - 1) * 30 + position - 1) * size
        } else {
            require(position in 1..6) { "The selected native party slot is outside the six-slot party." }
            val count = blocks.party[6 * size].toInt() and 255
            require(position <= count) { "The selected native party slot is not occupied." }
            source = blocks.party
            offset = (position - 1) * size
        }
        return source.copyOfRange(offset, offset + size)
    }

    private fun recordSize(area: LegendsArceusPokemonArea): Int =
        if (area == LegendsArceusPokemonArea.PASTURE) LegendsArceusPokemonReadOnly.STORED_SIZE
        else LegendsArceusPokemonReadOnly.PARTY_SIZE

    private fun editRecord(record: ByteArray, moveId: Int, pp: Int): ByteArray {
        val plain = decryptAndUnshuffle(record)
        writeU16(plain, MOVE_3, moveId)
        plain[MOVE_3_PP] = pp.toByte()
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
    private fun u16(bytes: ByteArray, offset: Int): Int =
        byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long =
        u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
}

class LegendsArceusMove3CopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: LegendsArceusPokemonArea,
    val pasture: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: LegendsArceusPokemonRead,
    val move: LegendsArceusMoveChoice,
    val pp: Int,
    val maximumPp: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = LegendsArceusPokemonMove3Editing.sha256(this.original)
    val editedSha256 = LegendsArceusPokemonMove3Editing.sha256(this.edited)
    private var consumed = false
    val recordSize: Int get() = if (area == LegendsArceusPokemonArea.PASTURE)
        LegendsArceusPokemonReadOnly.STORED_SIZE else LegendsArceusPokemonReadOnly.PARTY_SIZE
    val location: String get() = if (area == LegendsArceusPokemonArea.PASTURE)
        "Pasture $pasture • Slot $position" else "Party $position"

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This Move 3 review was already used. Reopen Pokémon Boxes." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) {
            "Choose a new empty document; existing files cannot be replaced."
        }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) {
            "The recovery backup did not verify. Nothing was written."
        }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            LegendsArceusPokemonMove3Editing.verify(
                original, edited, area, pasture, position, absoluteOffset, move.id, pp
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Move 3 edit."
            }
            LegendsArceusPokemonMove3Editing.verify(
                original, persisted, area, pasture, position, absoluteOffset, move.id, pp
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException((failure.message ?: "Move 3 copy verification failed.") +
                if (removed) " The failed new copy was removed; the recovery backup is retained."
                else " The new copy could not be removed. Do not use it; the recovery backup is retained.", failure)
        }
    }
}
