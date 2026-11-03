package com.example.pokemonsavemanager

/**
 * Verified Gen 6 PC-storage layout shared by X/Y and OR/AS destination saves.
 *
 * The offsets, sizes, title identifiers and checksum blocks mirror the
 * generation-specific runtime parser. This adapter migrates boxed PK6 records
 * and registers each inserted Pokémon in the destination Pokédex; party
 * migration remains a separate requirement.
 */
class Gen6PcStorageLayout(
    private val game: Gen6ConverterGame,
    save: ByteArray
) {
    private data class Profile(
        val saveSize: Int,
        val gameId: Int,
        val boxOffset: Int,
        val boxBlockId: Int,
        val dexLength: Int
    )

    private val profile = profileFor(game)
    private val baseline = save.copyOf()
    private val vacantOffsets: IntArray
    private val occupiedOffsets: IntArray

    init {
        val validation = validate(baseline, game)
        require(validation.isValid && validation.detectedGame == game) {
            "Gen 6 save failed size, identity, checksum or PC-record validation."
        }
        val vacant = ArrayList<Int>()
        val occupied = ArrayList<Int>()
        repeat(SLOT_COUNT) { index ->
            val offset = profile.boxOffset + index * Gen6Pk6Adapter.STORED_SIZE
            if (isVacant(baseline, offset)) vacant.add(offset) else occupied.add(offset)
        }
        vacantOffsets = vacant.toIntArray()
        occupiedOffsets = occupied.toIntArray()
    }

    fun sourceExtractor(): Gen6Pk6SourceRecordExtractor =
        Gen6Pk6SourceRecordExtractor { sourceSave, sourceGame ->
            requireGame(sourceGame)
            requireBoundSave(sourceSave)
            occupiedOffsets.map { offset ->
                sourceSave.copyOfRange(offset, offset + Gen6Pk6Adapter.STORED_SIZE)
            }
        }

    fun capacityProvider(): Gen6Pk6DestinationCapacity =
        Gen6Pk6DestinationCapacity { destinationTemplate, destinationGame ->
            requireGame(destinationGame)
            requireBoundSave(destinationTemplate)
            vacantOffsets.size
        }

    fun recordInserter(): Gen6Pk6DestinationRecordInserter =
        Gen6Pk6DestinationRecordInserter { destinationTemplate, destinationGame, records ->
            requireGame(destinationGame)
            requireBoundSave(destinationTemplate)
            require(records.size <= vacantOffsets.size) {
                "Destination has ${vacantOffsets.size} vacant Gen 6 PC slots for ${records.size} records."
            }

            val validated = records.map { record ->
                Gen6Pk6Adapter.decode(record.copyOf()) to record.copyOf()
            }
            val output = destinationTemplate.copyOf()
            validated.forEachIndexed { index, (decoded, record) ->
                record.copyInto(output, destinationOffset = vacantOffsets[index])
                if (!decoded.isEgg) registerDex(output, game, decoded)
            }
            updateBlockChecksum(output, profile, profile.boxBlockId)
            updateBlockChecksum(output, profile, DEX_BLOCK_ID)
            val validation = validate(output, game)
            check(validation.isValid && validation.detectedGame == game) {
                "Gen 6 PC placement failed destination checksum or reopen validation."
            }
            Gen6PokedexRegistrationVerifier.requireRegistered(
                output,
                game,
                validated.map { it.first }
            )
            output
        }

    fun recordReader(): Gen6Pk6DestinationRecordReader =
        Gen6Pk6DestinationRecordReader { destinationSave, destinationGame, recordCount ->
            requireGame(destinationGame)
            require(recordCount in 0..vacantOffsets.size) {
                "Placed PK6 record count must be between 0 and ${vacantOffsets.size}."
            }
            val validation = validate(destinationSave, game)
            require(validation.isValid && validation.detectedGame == game) {
                "Gen 6 destination failed checksum or reopen validation."
            }
            vacantOffsets.take(recordCount).map { offset ->
                destinationSave.copyOfRange(offset, offset + Gen6Pk6Adapter.STORED_SIZE)
            }
        }

    fun bytesValidator(): Gen6SaveBytesValidator =
        Gen6SaveBytesValidator { destinationBytes, _ -> validate(destinationBytes, game) }

    private fun requireGame(actual: Gen6ConverterGame) {
        require(actual == game) { "Gen 6 PC layout is for $game, not $actual." }
    }

    private fun requireBoundSave(actual: ByteArray) {
        require(actual.contentEquals(baseline)) {
            "Gen 6 save does not match the snapshot bound to this PC layout."
        }
    }

    companion object {
        private const val XY_SIZE = 0x65600
        private const val ORAS_SIZE = 0x76000
        private const val STATUS_GAME_ID = 0x14004
        private const val XY_BOX_OFFSET = 0x22600
        private const val ORAS_BOX_OFFSET = 0x33000
        private const val BOX_BLOCK_LENGTH = 0x34AD0
        private const val DEX_OFFSET = 0x15000
        private const val XY_DEX_LENGTH = 0x006A0
        private const val ORAS_DEX_LENGTH = 0x011CC
        private const val DEX_BLOCK_ID = 20
        private const val SLOT_COUNT = 31 * 30
        private const val SPINDA_SPECIES = 327
        private const val XY_SPINDA_OFFSET = 0x0648
        private const val ORAS_SPINDA_OFFSET = 0x0680
        private const val ORAS_SEEN_COUNT_OFFSET = 0x0684

        fun detectGame(save: ByteArray): Gen6ConverterGame? {
            if (STATUS_GAME_ID !in save.indices) return null
            val id = save[STATUS_GAME_ID].toInt() and 0xFF
            return when (save.size) {
                XY_SIZE -> when (id) {
                    24 -> Gen6ConverterGame.X
                    25 -> Gen6ConverterGame.Y
                    else -> null
                }
                ORAS_SIZE -> when (id) {
                    26 -> Gen6ConverterGame.ALPHA_SAPPHIRE
                    27 -> Gen6ConverterGame.OMEGA_RUBY
                    else -> null
                }
                else -> null
            }
        }

        fun validate(
            save: ByteArray,
            expectedGame: Gen6ConverterGame
        ): Gen6DestinationValidation {
            val profile = profileFor(expectedGame)
            val detected = detectGame(save)
            val formatValid = save.size == profile.saveSize && detected == expectedGame
            val integrityValid = formatValid &&
                blockChecksumValid(save, profile, DEX_BLOCK_ID) &&
                blockChecksumValid(save, profile, profile.boxBlockId)
            val reopenValid = integrityValid && (0 until SLOT_COUNT).all { index ->
                val offset = profile.boxOffset + index * Gen6Pk6Adapter.STORED_SIZE
                isVacant(save, offset) ||
                    runCatching {
                        Gen6Pk6Adapter.decode(
                            save.copyOfRange(offset, offset + Gen6Pk6Adapter.STORED_SIZE)
                        )
                    }.isSuccess
            }
            val note = when {
                !formatValid -> "Gen 6 save size or title identity does not match $expectedGame."
                !integrityValid -> "Gen 6 Pokédex or PC-storage checksum is invalid."
                !reopenValid -> "Gen 6 PC storage contains an invalid non-empty PK6 record."
                else -> ""
            }
            return Gen6DestinationValidation(
                formatValid = formatValid,
                integrityValid = integrityValid,
                reopenValid = reopenValid,
                detectedGame = detected,
                note = note
            )
        }

        private fun profileFor(game: Gen6ConverterGame): Profile = when (game) {
            Gen6ConverterGame.X -> Profile(XY_SIZE, 24, XY_BOX_OFFSET, 53, XY_DEX_LENGTH)
            Gen6ConverterGame.Y -> Profile(XY_SIZE, 25, XY_BOX_OFFSET, 53, XY_DEX_LENGTH)
            Gen6ConverterGame.ALPHA_SAPPHIRE ->
                Profile(ORAS_SIZE, 26, ORAS_BOX_OFFSET, 56, ORAS_DEX_LENGTH)
            Gen6ConverterGame.OMEGA_RUBY ->
                Profile(ORAS_SIZE, 27, ORAS_BOX_OFFSET, 56, ORAS_DEX_LENGTH)
        }

        private fun isVacant(save: ByteArray, offset: Int): Boolean =
            (offset until offset + Gen6Pk6Adapter.STORED_SIZE).all { save[it] == 0.toByte() }

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

            val alreadyDisplayed = (5..8).any { region ->
                val offset = DEX_OFFSET + 0x08 + regionLength * region + byteIndex
                (save[offset].toInt() and mask) != 0
            }
            if (!alreadyDisplayed) {
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

        private fun blockInfo(profile: Profile, blockId: Int): Pair<Int, Int>? =
            when (blockId) {
                DEX_BLOCK_ID -> DEX_OFFSET to profile.dexLength
                profile.boxBlockId -> profile.boxOffset to BOX_BLOCK_LENGTH
                else -> null
            }

        private fun checksumOffset(save: ByteArray, blockId: Int): Int =
            save.size - 0x200 + 0x14 + blockId * 8 + 6

        private fun blockChecksumValid(
            save: ByteArray,
            profile: Profile,
            blockId: Int
        ): Boolean {
            val (offset, length) = blockInfo(profile, blockId) ?: return false
            val checksum = checksumOffset(save, blockId)
            if (offset < 0 || length < 0 || offset + length > save.size ||
                checksum < 0 || checksum + 1 >= save.size
            ) return false
            return readU16(save, checksum) == crc16Ccitt(save, offset, length)
        }

        private fun updateBlockChecksum(
            save: ByteArray,
            profile: Profile,
            blockId: Int
        ) {
            val (offset, length) = blockInfo(profile, blockId)
                ?: error("Unknown Gen 6 checksum block $blockId.")
            val checksum = checksumOffset(save, blockId)
            writeU16(save, checksum, crc16Ccitt(save, offset, length))
        }

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
