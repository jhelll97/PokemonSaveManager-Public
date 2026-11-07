package com.example.pokemonsavemanager

data class ScarletVioletTechnicalMachineChoice(
    val machineIndex: Int,
    val move: ScarletVioletMoveChoice
) {
    override fun toString(): String = "TM ${machineIndex + 1} • ${move.name} (#${move.id})"
}

/** Records one current move as previously learned from its native Scarlet/Violet Technical Machine. */
object ScarletVioletPokemonTechnicalMachineEditing {
    private const val STORED_END = 0x148
    private const val BASE_RECORD_START = 0x12F
    private const val BASE_RECORD_COUNT = 200
    private const val DLC_RECORD_START = 0x4B
    private const val USED_RECORD_COUNT = 230
    private val machineMoves = intArrayOf(
        5, 36, 204, 313, 97, 189, 184, 182, 424, 422,
        423, 352, 67, 491, 512, 522, 60, 109, 168, 574,
        885, 884, 886, 451, 83, 263, 342, 332, 523, 506,
        555, 232, 129, 345, 196, 341, 317, 577, 488, 490,
        314, 500, 101, 374, 525, 474, 419, 203, 521, 241,
        240, 201, 883, 684, 473, 91, 331, 206, 280, 428,
        369, 421, 492, 706, 339, 403, 34, 7, 9, 8,
        214, 402, 486, 409, 115, 113, 350, 127, 337, 605,
        118, 447, 86, 398, 707, 156, 157, 269, 14, 776,
        191, 390, 286, 430, 399, 141, 598, 19, 285, 442,
        349, 408, 441, 164, 334, 404, 529, 261, 242, 271,
        710, 202, 396, 366, 247, 406, 446, 304, 257, 412,
        94, 484, 227, 57, 861, 53, 85, 583, 133, 347,
        270, 676, 226, 414, 179, 58, 604, 580, 678, 581,
        417, 126, 56, 59, 519, 518, 520, 528, 188, 89,
        444, 566, 416, 307, 308, 338, 200, 315, 411, 437,
        542, 433, 405, 63, 413, 394, 87, 370, 76, 434,
        796, 851, 46, 268, 114, 92, 328, 180, 356, 479,
        360, 282, 450, 162, 410, 679, 667, 333, 503, 535,
        669, 253, 264, 311, 803, 807, 812, 814, 809, 808,
        799, 802, 220, 244, 38, 283, 572, 915, 250, 330,
        916, 527, 813, 811, 482, 815, 297, 248, 797, 806,
        800, 675, 784, 319, 174, 912, 913, 914, 917, 918
    )
    private val movesById = ScarletVioletMoveCatalog.choices.associateBy { it.id }
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    fun readDocumentBytes(input: java.io.InputStream): ByteArray =
        ScarletVioletPokemonNicknameEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = ScarletVioletPokemonNicknameEditing.sha256(bytes)

    fun choices(pokemon: ScarletVioletPokemonRead): List<ScarletVioletTechnicalMachineChoice> {
        if (pokemon.isEgg || pokemon.technicalMachineFlags.size != USED_RECORD_COUNT) return emptyList()
        val currentMoves = pokemon.moves.map { it.id }.filter { it > 0 }.toSet()
        return machineMoves.withIndex().mapNotNull { (index, moveId) ->
            val move = movesById[moveId]
            if (move != null && moveId in currentMoves && !pokemon.technicalMachineFlags[index])
                ScarletVioletTechnicalMachineChoice(index, move)
            else null
        }
    }

    fun recordedCurrentMoves(pokemon: ScarletVioletPokemonRead): String {
        if (pokemon.technicalMachineFlags.size != USED_RECORD_COUNT) return "Unavailable"
        val currentMoves = pokemon.moves.map { it.id }.filter { it > 0 }.toSet()
        val recorded = machineMoves.withIndex().mapNotNull { (index, moveId) ->
            if (moveId in currentMoves && pokemon.technicalMachineFlags[index])
                movesById[moveId]?.name ?: "Move #$moveId"
            else null
        }
        return if (recorded.isEmpty()) "None" else recorded.joinToString(", ")
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int,
        requestedMachineIndex: Int
    ): ScarletVioletTechnicalMachineCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(requestedMachineIndex in machineMoves.indices) {
            "Choose one supported Scarlet/Violet Technical Machine."
        }
        val blocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val target = targetRecord(blocks, area, box, position)
        val before = (ScarletVioletPokemonReadOnly.read(target) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK9 is not checksum-valid.")
        require(!before.isEgg) { "Egg Technical Machine history editing is not supported." }
        require(before.technicalMachineFlags.size == USED_RECORD_COUNT) {
            "The native Technical Machine history could not be read exactly."
        }
        val moveId = machineMoves[requestedMachineIndex]
        require(before.moves.any { it.id == moveId }) {
            "PSE only records a Technical Machine move that this Pokémon currently knows."
        }
        require(!before.technicalMachineFlags[requestedMachineIndex]) {
            "This Technical Machine use is already recorded."
        }
        val move = movesById[moveId]
            ?: throw IllegalArgumentException("The selected Technical Machine is unavailable in this title catalogue.")
        val afterRecord = editRecord(target, requestedMachineIndex)
        val written = if (area == ScarletVioletPokemonArea.BOX) {
            ScarletVioletSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else ScarletVioletSaveValidation.writePartyRecord(original, position, afterRecord)
        verify(
            original, written.bytes, area, box, position, written.absoluteOffset,
            requestedMachineIndex, moveId
        )
        return ScarletVioletTechnicalMachineCopyPlan(
            original, written.bytes, area, box, position, written.absoluteOffset,
            before, ScarletVioletTechnicalMachineChoice(requestedMachineIndex, move)
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int,
        absoluteOffset: Int,
        expectedMachineIndex: Int,
        expectedMoveId: Int
    ) {
        val beforeBlocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val afterBlocks = ScarletVioletSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The Technical Machine edit changed title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val beforePokemon = (ScarletVioletPokemonReadOnly.read(beforeRecord) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The original PK9 no longer reopens as a Pokémon.")
        val afterPokemon = (ScarletVioletPokemonReadOnly.read(afterRecord) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The edited PK9 did not reopen as a Pokémon.")
        require(expectedMachineIndex in machineMoves.indices &&
            machineMoves[expectedMachineIndex] == expectedMoveId &&
            beforePokemon.moves.any { it.id == expectedMoveId }) {
            "The reviewed Technical Machine no longer matches the selected current move."
        }
        val expectedFlags = beforePokemon.technicalMachineFlags.toMutableList().apply {
            this[expectedMachineIndex] = true
        }.toList()
        require(afterPokemon.technicalMachineFlags == expectedFlags &&
            afterPokemon.copy(technicalMachineFlags = beforePokemon.technicalMachineFlags) == beforePokemon) {
            "The persisted PK9 changed more than one Technical Machine history bit."
        }

        val relativeOffset = if (area == ScarletVioletPokemonArea.BOX)
            ((box - 1) * 30 + position - 1) * ScarletVioletPokemonReadOnly.RECORD_SIZE
        else (position - 1) * ScarletVioletPokemonReadOnly.RECORD_SIZE
        val beforeTargetBlock = if (area == ScarletVioletPokemonArea.BOX) beforeBlocks.boxes else beforeBlocks.party
        val afterTargetBlock = if (area == ScarletVioletPokemonArea.BOX) afterBlocks.boxes else afterBlocks.party
        val beforeOtherBlock = if (area == ScarletVioletPokemonArea.BOX) beforeBlocks.party else beforeBlocks.boxes
        val afterOtherBlock = if (area == ScarletVioletPokemonArea.BOX) afterBlocks.party else afterBlocks.boxes
        require(beforeOtherBlock.contentEquals(afterOtherBlock)) {
            "The unselected native Pokémon storage object changed."
        }
        require(beforeTargetBlock.indices.all { index ->
            index in relativeOffset until relativeOffset + ScarletVioletPokemonReadOnly.RECORD_SIZE ||
                beforeTargetBlock[index] == afterTargetBlock[index]
        }) { "Another native Pokémon slot changed." }

        val beforePlain = decryptAndUnshuffle(beforeRecord)
        val afterPlain = decryptAndUnshuffle(afterRecord)
        val changedByte = recordByte(expectedMachineIndex)
        val bit = 1 shl recordBitIndex(expectedMachineIndex)
        require(byte(afterPlain, changedByte) == (byte(beforePlain, changedByte) or bit)) {
            "The native Technical Machine history bit was not added exactly."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset == changedByte || beforePlain[offset] == afterPlain[offset]
        }) { "The PK9 edit exceeded one Technical Machine history bit and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + ScarletVioletPokemonReadOnly.RECORD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
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

    private fun editRecord(record: ByteArray, machineIndex: Int): ByteArray {
        val plain = decryptAndUnshuffle(record)
        val offset = recordByte(machineIndex)
        plain[offset] = (byte(plain, offset) or (1 shl recordBitIndex(machineIndex))).toByte()
        var checksum = 0
        for (checksumOffset in 8 until STORED_END step 2) checksum += u16(plain, checksumOffset)
        writeU16(plain, 6, checksum and 0xFFFF)
        return shuffleAndEncrypt(plain)
    }

    private fun recordByte(machineIndex: Int): Int =
        if (machineIndex < BASE_RECORD_COUNT) BASE_RECORD_START + (machineIndex ushr 3)
        else DLC_RECORD_START + ((machineIndex - BASE_RECORD_COUNT) ushr 3)

    private fun recordBitIndex(machineIndex: Int): Int =
        if (machineIndex < BASE_RECORD_COUNT) machineIndex and 7
        else (machineIndex - BASE_RECORD_COUNT) and 7

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

class ScarletVioletTechnicalMachineCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: ScarletVioletPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: ScarletVioletPokemonRead,
    val choice: ScarletVioletTechnicalMachineChoice
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = ScarletVioletPokemonTechnicalMachineEditing.sha256(this.edited)
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
        check(!consumed) { "This Technical Machine review was already used. Reopen Pokémon Boxes." }
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
            ScarletVioletPokemonTechnicalMachineEditing.verify(
                original, edited, area, box, position, absoluteOffset,
                choice.machineIndex, choice.move.id
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Technical Machine edit."
            }
            ScarletVioletPokemonTechnicalMachineEditing.verify(
                original, persisted, area, box, position, absoluteOffset,
                choice.machineIndex, choice.move.id
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Technical Machine copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
