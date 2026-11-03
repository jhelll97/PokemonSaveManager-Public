package com.example.pokemonsavemanager

/**
 * Concrete Gen 6 party layout shared by X/Y and OR/AS saves.
 *
 * Party migration copies complete 0x104-byte records so the encrypted PK6 core
 * and cached party battle data stay together. Records are appended after the
 * destination's existing party and both source/template snapshots remain
 * untouched.
 */
class Gen6PartyStorageLayout(
    private val game: Gen6ConverterGame,
    save: ByteArray
) {
    private val baseline = save.copyOf()
    private val initialCount: Int

    init {
        val validation = validate(baseline, game)
        require(validation.isValid && validation.detectedGame == game) {
            "Gen 6 save failed identity, party checksum or party-record validation."
        }
        initialCount = readPartyCount(baseline)
    }

    fun sourceRecords(
        sourceSave: ByteArray,
        sourceGame: Gen6ConverterGame
    ): List<ByteArray> {
        requireGame(sourceGame)
        requireBoundSave(sourceSave)
        return (0 until initialCount).map { index ->
            val offset = PARTY_OFFSET + index * PARTY_RECORD_SIZE
            sourceSave.copyOfRange(offset, offset + PARTY_RECORD_SIZE)
        }
    }

    fun availableSlots(
        destinationTemplate: ByteArray,
        destinationGame: Gen6ConverterGame
    ): Int {
        requireGame(destinationGame)
        requireBoundSave(destinationTemplate)
        return PARTY_CAPACITY - initialCount
    }

    fun insertRecords(
        destinationTemplate: ByteArray,
        destinationGame: Gen6ConverterGame,
        records: List<ByteArray>
    ): ByteArray {
        requireGame(destinationGame)
        requireBoundSave(destinationTemplate)
        require(records.size <= PARTY_CAPACITY - initialCount) {
            "Destination has ${PARTY_CAPACITY - initialCount} vacant Gen 6 party slots for ${records.size} records."
        }

        val validated = records.map { record ->
            require(record.size == PARTY_RECORD_SIZE) {
                "Gen 6 party record must be exactly 0x104 bytes."
            }
            val decoded = Gen6Pk6Adapter.decode(
                record.copyOfRange(0, Gen6Pk6Adapter.STORED_SIZE)
            )
            decoded to record.copyOf()
        }

        val output = destinationTemplate.copyOf()
        validated.forEachIndexed { index, (decoded, record) ->
            val destinationOffset =
                PARTY_OFFSET + (initialCount + index) * PARTY_RECORD_SIZE
            record.copyInto(output, destinationOffset)
            if (!decoded.isEgg) registerDex(output, game, decoded)
        }
        writePartyCount(output, initialCount + validated.size)
        updateBlockChecksum(
            output,
            blockId = PARTY_BLOCK_ID,
            offset = PARTY_OFFSET,
            length = PARTY_BLOCK_LENGTH
        )
        updateBlockChecksum(
            output,
            blockId = DEX_BLOCK_ID,
            offset = DEX_OFFSET,
            length = dexLength(game)
        )

        requirePlacedRecords(
            destinationSave = output,
            destinationGame = game,
            records = validated.map { it.second }
        )
        Gen6PokedexRegistrationVerifier.requireRegistered(
            output,
            game,
            validated.map { it.first }
        )
        return output
    }

    /**
     * Reopens the appended party range and proves that the complete 0x104-byte
     * records, including cached battle data, persisted in transfer order.
     */
    internal fun requirePlacedRecords(
        destinationSave: ByteArray,
        destinationGame: Gen6ConverterGame,
        records: List<ByteArray>
    ) {
        requireGame(destinationGame)
        require(records.size <= PARTY_CAPACITY - initialCount) {
            "Placed Gen 6 party record count exceeds the bound destination capacity."
        }
        val validation = validate(destinationSave, game)
        check(validation.isValid && validation.detectedGame == game) {
            "Gen 6 party placement failed checksum or reopen validation."
        }
        check(readPartyCount(destinationSave) == initialCount + records.size) {
            "Destination Gen 6 party count changed during placement or reopen."
        }
        records.forEachIndexed { index, expected ->
            require(expected.size == PARTY_RECORD_SIZE) {
                "Gen 6 party record must be exactly 0x104 bytes."
            }
            val offset =
                PARTY_OFFSET + (initialCount + index) * PARTY_RECORD_SIZE
            check(
                destinationSave.copyOfRange(
                    offset,
                    offset + PARTY_RECORD_SIZE
                ).contentEquals(expected)
            ) {
                "Destination Gen 6 party record $index changed during placement or reopen."
            }
        }
    }

    fun bytesValidator(): Gen6SaveBytesValidator =
        Gen6SaveBytesValidator { destinationBytes, _ -> validate(destinationBytes, game) }

    private fun requireGame(actual: Gen6ConverterGame) {
        require(actual == game) { "Gen 6 party layout is for $game, not $actual." }
    }

    private fun requireBoundSave(actual: ByteArray) {
        require(actual.contentEquals(baseline)) {
            "Gen 6 save does not match the snapshot bound to this party layout."
        }
    }

    companion object {
        private const val PARTY_OFFSET = 0x14200
        private const val PARTY_RECORD_SIZE = 0x104
        private const val PARTY_CAPACITY = 6
        private const val PARTY_BLOCK_ID = 18
        private const val PARTY_BLOCK_LENGTH = 0x61C
        private const val DEX_BLOCK_ID = 20
        private const val DEX_OFFSET = 0x15000
        private const val XY_DEX_LENGTH = 0x006A0
        private const val ORAS_DEX_LENGTH = 0x011CC
        private const val PARTY_COUNT_OFFSET =
            PARTY_OFFSET + PARTY_CAPACITY * PARTY_RECORD_SIZE
        private const val SPINDA_SPECIES = 327
        private const val XY_SPINDA_OFFSET = 0x0648
        private const val ORAS_SPINDA_OFFSET = 0x0680
        private const val ORAS_SEEN_COUNT_OFFSET = 0x0684

        fun validate(
            save: ByteArray,
            expectedGame: Gen6ConverterGame
        ): Gen6DestinationValidation {
            val base = Gen6PcStorageLayout.validate(save, expectedGame)
            val partyChecksumValid = base.formatValid &&
                blockChecksumValid(
                    save,
                    PARTY_BLOCK_ID,
                    PARTY_OFFSET,
                    PARTY_BLOCK_LENGTH
                )
            val count = if (PARTY_COUNT_OFFSET in save.indices) {
                save[PARTY_COUNT_OFFSET].toInt() and 0xFF
            } else {
                -1
            }
            val partyRecordsValid = partyChecksumValid &&
                count in 0..PARTY_CAPACITY &&
                (0 until count).all { index ->
                    val offset = PARTY_OFFSET + index * PARTY_RECORD_SIZE
                    runCatching {
                        Gen6Pk6Adapter.decode(
                            save.copyOfRange(
                                offset,
                                offset + Gen6Pk6Adapter.STORED_SIZE
                            )
                        )
                    }.isSuccess
                }
            val integrityValid = base.integrityValid && partyChecksumValid
            val reopenValid = base.reopenValid && partyRecordsValid
            val note = when {
                !base.formatValid -> base.note
                !base.integrityValid -> base.note
                !partyChecksumValid -> "Gen 6 party checksum is invalid."
                count !in 0..PARTY_CAPACITY -> "Gen 6 party count is outside 0..6."
                !partyRecordsValid -> "Gen 6 party contains an invalid PK6 record."
                else -> ""
            }
            return Gen6DestinationValidation(
                formatValid = base.formatValid,
                integrityValid = integrityValid,
                reopenValid = reopenValid,
                detectedGame = base.detectedGame,
                note = note
            )
        }

        private fun readPartyCount(save: ByteArray): Int =
            save[PARTY_COUNT_OFFSET].toInt() and 0xFF

        private fun writePartyCount(save: ByteArray, count: Int) {
            require(count in 0..PARTY_CAPACITY)
            save[PARTY_COUNT_OFFSET] = count.toByte()
        }

        private fun dexLength(game: Gen6ConverterGame): Int = when (game) {
            Gen6ConverterGame.X, Gen6ConverterGame.Y -> XY_DEX_LENGTH
            Gen6ConverterGame.OMEGA_RUBY,
            Gen6ConverterGame.ALPHA_SAPPHIRE -> ORAS_DEX_LENGTH
        }

        private fun registerDex(
            save: ByteArray,
            game: Gen6ConverterGame,
            record: Gen6Pk6Record
        ) {
            val bit = record.species - 1
            val byteIndex = bit ushr 3
            val mask = 1 shl (bit and 7)
            val regionLength = 0x60
            val alreadySeen = (1..4).any { region ->
                val offset = DEX_OFFSET + 0x08 + regionLength * region + byteIndex
                (save[offset].toInt() and mask) != 0
            }
            if (record.species == SPINDA_SPECIES && !alreadySeen) {
                val relativeOffset = when (game) {
                    Gen6ConverterGame.X, Gen6ConverterGame.Y -> XY_SPINDA_OFFSET
                    Gen6ConverterGame.OMEGA_RUBY,
                    Gen6ConverterGame.ALPHA_SAPPHIRE -> ORAS_SPINDA_OFFSET
                }
                writeU32(save, DEX_OFFSET + relativeOffset, record.encryptionConstant)
            }
            setDexFlag(save, DEX_OFFSET + 0x08 + byteIndex, mask)

            val shinyValue = record.tid xor record.sid xor
                (record.pid.toInt() and 0xFFFF) xor
                ((record.pid ushr 16).toInt() and 0xFFFF)
            // Gen 6 Pokédex regions encode genderless sightings with male.
            val gender = record.gender and 1
            val variant = gender or ((if (shinyValue < 16) 1 else 0) shl 1)
            setDexFlag(
                save,
                DEX_OFFSET + 0x08 + regionLength * (1 + variant) + byteIndex,
                mask
            )
            if (game == Gen6ConverterGame.OMEGA_RUBY ||
                game == Gen6ConverterGame.ALPHA_SAPPHIRE
            ) {
                incrementOrasSeenCount(save, record.species)
            }
            val displayed = (5..8).any { region ->
                val offset =
                    DEX_OFFSET + 0x08 + regionLength * region + byteIndex
                (save[offset].toInt() and mask) != 0
            }
            if (!displayed) {
                setDexFlag(
                    save,
                    DEX_OFFSET + 0x08 + regionLength * (5 + variant) + byteIndex,
                    mask
                )
            }
        }

        private fun setDexFlag(save: ByteArray, offset: Int, mask: Int) {
            save[offset] = (save[offset].toInt() or mask).toByte()
        }

        private fun incrementOrasSeenCount(save: ByteArray, species: Int) {
            val offset = DEX_OFFSET + ORAS_SEEN_COUNT_OFFSET + species * 2
            val count = readU16(save, offset)
            if (count != 0xFFFF) writeU16(save, offset, count + 1)
        }

        private fun blockChecksumValid(
            save: ByteArray,
            blockId: Int,
            offset: Int,
            length: Int
        ): Boolean {
            val checksum = checksumOffset(save, blockId)
            if (offset < 0 || length < 0 || offset + length > save.size ||
                checksum < 0 || checksum + 1 >= save.size
            ) return false
            return readU16(save, checksum) == crc16Ccitt(save, offset, length)
        }

        private fun updateBlockChecksum(
            save: ByteArray,
            blockId: Int,
            offset: Int,
            length: Int
        ) {
            val checksum = checksumOffset(save, blockId)
            writeU16(save, checksum, crc16Ccitt(save, offset, length))
        }

        private fun checksumOffset(save: ByteArray, blockId: Int): Int =
            save.size - 0x200 + 0x14 + blockId * 8 + 6

        private fun crc16Ccitt(data: ByteArray, offset: Int, length: Int): Int {
            require(offset >= 0 && length >= 0 && offset + length <= data.size)
            var crc = 0xFFFF
            for (index in offset until offset + length) {
                crc = crc xor ((data[index].toInt() and 0xFF) shl 8)
                repeat(8) {
                    crc = if ((crc and 0x8000) != 0) {
                        ((crc shl 1) xor 0x1021) and 0xFFFF
                    } else {
                        (crc shl 1) and 0xFFFF
                    }
                }
            }
            return crc
        }

        private fun readU16(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8)

        private fun writeU16(data: ByteArray, offset: Int, value: Int) {
            data[offset] = (value and 0xFF).toByte()
            data[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        }

        private fun writeU32(data: ByteArray, offset: Int, value: Long) {
            repeat(4) { index ->
                data[offset + index] = ((value ushr (index * 8)) and 0xFF).toByte()
            }
        }
    }
}
