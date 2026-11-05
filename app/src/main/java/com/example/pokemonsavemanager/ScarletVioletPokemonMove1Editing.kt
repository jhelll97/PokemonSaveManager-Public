package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Changes only Move 1 and its current PP in one reviewed native PK9 copy. */
object ScarletVioletPokemonMove1Editing {
    private const val MAX_FILE_SIZE = 0x43B2E7
    private const val STORED_END = 0x148
    private const val MOVE_1 = 0x72
    private const val MOVE_1_PP = 0x7A
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
            require(output.size() <= MAX_FILE_SIZE) { "The document exceeds the supported Scarlet/Violet save size." }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int,
        requestedMoveId: Int,
        requestedPp: Int
    ): ScarletVioletMove1CopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val blocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, box, position)
        val before = (ScarletVioletPokemonReadOnly.read(target) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK9 is not checksum-valid.")
        validateMoves(before.moves)
        val move = ScarletVioletMoveCatalog.choice(requestedMoveId)
            ?: throw IllegalArgumentException("Move #$requestedMoveId is not usable in Scarlet/Violet.")
        require(before.moves.drop(1).none { it.id == move.id }) {
            "${move.name} already appears in another move slot. Scarlet/Violet move slots must be unique."
        }
        val ppUps = before.moves.first().ppUps
        val maximumPp = ScarletVioletMoveCatalog.maximumPp(move.id, ppUps)
        require(requestedPp in 0..maximumPp) {
            "Current PP must be between 0 and $maximumPp for ${move.name} with $ppUps PP Ups."
        }
        val beforeMove = before.moves.first()
        require(beforeMove.id != move.id || beforeMove.pp != requestedPp) {
            "Choose a different Move 1 or current PP value before review."
        }
        val afterRecord = editRecord(target, move.id, requestedPp)
        require(ScarletVioletPokemonReadOnly.read(afterRecord) is ScarletVioletSlotRead.Pokemon) {
            "The edited PK9 did not pass its native checksum."
        }
        val written = if (area == ScarletVioletPokemonArea.BOX) {
            ScarletVioletSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else ScarletVioletSaveValidation.writePartyRecord(original, position, afterRecord)
        verify(original, written.bytes, area, box, position, written.absoluteOffset, move.id, requestedPp)
        return ScarletVioletMove1CopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset,
            before, move, requestedPp, ppUps, maximumPp
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        moveId: Int,
        pp: Int
    ) {
        val beforeBlocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val afterBlocks = ScarletVioletSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The Move 1 edit changed native title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val beforePokemon = (ScarletVioletPokemonReadOnly.read(beforeRecord) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PK9 no longer reopens as a Pokémon.")
        val afterPokemon = (ScarletVioletPokemonReadOnly.read(afterRecord) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PK9 did not reopen as a Pokémon.")
        validateMoves(beforePokemon.moves)
        validateMoves(afterPokemon.moves)
        val beforeMove = beforePokemon.moves.first()
        require(afterPokemon.moves.first() == ScarletVioletMoveRead(moveId, pp, beforeMove.ppUps) &&
            afterPokemon.moves.drop(1) == beforePokemon.moves.drop(1) &&
            afterPokemon.copy(moves = beforePokemon.moves) == beforePokemon) {
            "The persisted PK9 changed more than Move 1 and its current PP."
        }

        val relativeOffset = if (area == ScarletVioletPokemonArea.BOX)
            ((box - 1) * 30 + position - 1) * ScarletVioletPokemonReadOnly.RECORD_SIZE
        else (position - 1) * ScarletVioletPokemonReadOnly.RECORD_SIZE
        val beforeTargetBlock = if (area == ScarletVioletPokemonArea.BOX) beforeBlocks.boxes else beforeBlocks.party
        val afterTargetBlock = if (area == ScarletVioletPokemonArea.BOX) afterBlocks.boxes else afterBlocks.party
        val beforeOtherBlock = if (area == ScarletVioletPokemonArea.BOX) beforeBlocks.party else beforeBlocks.boxes
        val afterOtherBlock = if (area == ScarletVioletPokemonArea.BOX) afterBlocks.party else afterBlocks.boxes
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) { "The unselected native Pokémon storage object changed." }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + ScarletVioletPokemonReadOnly.RECORD_SIZE ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset in MOVE_1 until MOVE_1 + 2 || offset == MOVE_1_PP ||
                beforePlain[offset] == afterPlain[offset]
        }) { "The PK9 edit exceeded Move 1, its current PP, and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + ScarletVioletPokemonReadOnly.RECORD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun validateMoves(moves: List<ScarletVioletMoveRead>) {
        require(moves.size == 4) { "Expected four native PK9 move slots." }
        var foundEmpty = false
        val seen = mutableSetOf<Int>()
        moves.forEachIndexed { index, move ->
            require(move.ppUps in 0..3) { "Move ${index + 1} has an invalid PP Up count." }
            if (move.id == 0) {
                foundEmpty = true
                require(move.pp == 0 && move.ppUps == 0) { "An empty move slot retains PP data." }
            } else {
                require(!foundEmpty) { "Active moves must be consecutive from Move 1." }
                require(seen.add(move.id)) { "Duplicate native moves are not editable." }
                val maximum = ScarletVioletMoveCatalog.maximumPp(move.id, move.ppUps)
                require(move.pp in 0..maximum) {
                    "Move ${index + 1} current PP exceeds its Scarlet/Violet maximum."
                }
            }
        }
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
            require(box in 1..32 && position in 1..30) { "The selected native PC-box slot is outside storage." }
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

    private fun editRecord(record: ByteArray, moveId: Int, pp: Int): ByteArray {
        val plain = decryptAndUnshuffle(record)
        writeU16(plain, MOVE_1, moveId)
        plain[MOVE_1_PP] = pp.toByte()
        var checksum = 0
        for (offset in 8 until STORED_END step 2) checksum += u16(plain, offset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun decryptAndUnshuffle(record: ByteArray): ByteArray {
        require(record.size == ScarletVioletPokemonReadOnly.RECORD_SIZE) { "Expected one complete native PK9 record." }
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
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }
}

class ScarletVioletMove1CopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: ScarletVioletPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: ScarletVioletPokemonRead,
    val move: ScarletVioletMoveChoice,
    val pp: Int,
    val ppUps: Int,
    val maximumPp: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = ScarletVioletPokemonMove1Editing.sha256(this.original)
    val editedSha256 = ScarletVioletPokemonMove1Editing.sha256(this.edited)
    private var consumed = false
    val location: String get() = if (area == ScarletVioletPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This Move 1 review was already used. Reopen Pokémon Boxes." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) { "The recovery backup did not verify. Nothing was written." }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            ScarletVioletPokemonMove1Editing.verify(
                original, edited, area, box, position, absoluteOffset, move.id, pp
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed Move 1 edit." }
            ScarletVioletPokemonMove1Editing.verify(
                original, persisted, area, box, position, absoluteOffset, move.id, pp
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException((failure.message ?: "Move 1 copy verification failed.") +
                if (removed) " The failed new copy was removed; the recovery backup is retained."
                else " The new copy could not be removed. Do not use it; the recovery backup is retained.", failure)
        }
    }
}
