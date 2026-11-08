package com.example.pokemonsavemanager

import java.io.InputStream

data class ScarletVioletOriginGameChoice(val id: Int, val name: String) {
    override fun toString(): String = "$name (#$id)"
}

data class ScarletVioletPokemonOriginGameValues(
    val pokemon: ScarletVioletPokemonRead,
    val originGame: ScarletVioletOriginGameChoice,
    val choices: List<ScarletVioletOriginGameChoice>
)

/** Changes only one native PK9 Origin Game byte in a reviewed new Scarlet/Violet save copy. */
object ScarletVioletPokemonOriginGameEditing {
    private const val STORED_END = 0x148
    private const val ORIGIN_GAME_OFFSET = 0xCE
    private val orders = listOf("0123", "0132", "0213", "0312", "0231", "0321",
        "1023", "1032", "2013", "3012", "2031", "3021", "1203", "1302",
        "2103", "3102", "2301", "3201", "1230", "1320", "2130", "3120", "2310", "3210")

    const val SCARLET = 50
    const val VIOLET = 51

    val choices = listOf(
        ScarletVioletOriginGameChoice(SCARLET, "Pokémon Scarlet"),
        ScarletVioletOriginGameChoice(VIOLET, "Pokémon Violet")
    )

    fun name(id: Int): String = choices.firstOrNull { it.id == id }?.name ?: "Unknown Origin Game"

    fun readDocumentBytes(input: InputStream): ByteArray =
        ScarletVioletPokemonNicknameEditing.readDocumentBytes(input)

    fun sha256(bytes: ByteArray): String = ScarletVioletPokemonNicknameEditing.sha256(bytes)

    fun read(
        source: ByteArray,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int
    ): ScarletVioletPokemonOriginGameValues =
        inspect(targetRecord(ScarletVioletSaveValidation.readPokemonBlocks(source), area, box, position))

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        area: ScarletVioletPokemonArea,
        box: Int,
        position: Int,
        requestedOriginGame: Int
    ): ScarletVioletPokemonOriginGameCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val beforeRecord = targetRecord(
            ScarletVioletSaveValidation.readPokemonBlocks(original), area, box, position
        )
        val before = inspect(beforeRecord)
        require(choices.any { it.id == requestedOriginGame }) { "Choose Pokémon Scarlet or Pokémon Violet." }
        require(before.originGame.id != requestedOriginGame) { "Choose a different Origin Game before review." }
        val afterRecord = editRecord(beforeRecord, requestedOriginGame)
        val written = if (area == ScarletVioletPokemonArea.BOX) {
            ScarletVioletSaveValidation.writeBoxRecord(original, box, position, afterRecord)
        } else ScarletVioletSaveValidation.writePartyRecord(original, position, afterRecord)
        val after = verify(
            original, written.bytes, area, box, position, written.absoluteOffset, requestedOriginGame
        )
        return ScarletVioletPokemonOriginGameCopyPlan(
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
        requestedOriginGame: Int
    ): ScarletVioletPokemonOriginGameValues {
        val beforeBlocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val afterBlocks = ScarletVioletSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.version == afterBlocks.version && original.size == edited.size) {
            "The Origin Game edit changed title identity or save size."
        }
        val beforeRecord = targetRecord(beforeBlocks, area, box, position)
        val afterRecord = targetRecord(afterBlocks, area, box, position)
        val before = inspect(beforeRecord)
        val after = inspect(afterRecord)
        require(after.originGame.id == requestedOriginGame && after.choices == before.choices &&
            after.pokemon.copy(originGame = before.pokemon.originGame) == before.pokemon) {
            "The persisted PK9 changed more than its Origin Game."
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
        require(byte(afterPlain, ORIGIN_GAME_OFFSET) == requestedOriginGame) {
            "The native PK9 Origin Game byte was not changed exactly."
        }
        require(beforePlain.indices.all { offset ->
            offset in 6..7 || offset == ORIGIN_GAME_OFFSET || beforePlain[offset] == afterPlain[offset]
        }) { "The PK9 edit exceeded Origin Game and checksum fields." }

        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in absoluteOffset until absoluteOffset + ScarletVioletPokemonReadOnly.RECORD_SIZE ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed." }
        return after
    }

    private fun inspect(record: ByteArray): ScarletVioletPokemonOriginGameValues {
        val pokemon = (ScarletVioletPokemonReadOnly.read(record) as? ScarletVioletSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The selected native PK9 is not checksum-valid.")
        require(!pokemon.isEgg) { "Egg PK9 records remain protected from Origin Game editing." }
        require(!pokemon.fatefulEncounter) {
            "Fateful-event PK9 records remain protected because their saved origin is distribution-specific."
        }
        val current = choices.firstOrNull { it.id == pokemon.originGame }
            ?: throw IllegalArgumentException(
                "The saved Origin Game ID ${pokemon.originGame} is outside Scarlet/Violet's directly editable origin set and remains unchanged."
            )
        return ScarletVioletPokemonOriginGameValues(pokemon, current, choices)
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

    private fun editRecord(record: ByteArray, requestedOriginGame: Int): ByteArray {
        val plain = decryptAndUnshuffle(record)
        plain[ORIGIN_GAME_OFFSET] = requestedOriginGame.toByte()
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

class ScarletVioletPokemonOriginGameCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val area: ScarletVioletPokemonArea,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val before: ScarletVioletPokemonOriginGameValues,
    val after: ScarletVioletPokemonOriginGameValues
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = ScarletVioletPokemonOriginGameEditing.sha256(this.edited)
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
        check(!consumed) { "This Origin Game review was already used. Reopen Pokémon Boxes." }
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
            ScarletVioletPokemonOriginGameEditing.verify(
                original, edited, area, box, position, absoluteOffset, after.originGame.id
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Origin Game edit."
            }
            ScarletVioletPokemonOriginGameEditing.verify(
                original, persisted, area, box, position, absoluteOffset, after.originGame.id
            )
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Origin Game copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
