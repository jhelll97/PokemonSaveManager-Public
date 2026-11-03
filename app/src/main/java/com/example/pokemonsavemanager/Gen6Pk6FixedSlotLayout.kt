package com.example.pokemonsavemanager

/** Determines whether one verified destination PK6 slot is available for placement. */
fun interface Gen6Pk6SlotVacancyValidator {
    fun isVacant(slotBytes: ByteArray, destinationGame: Gen6ConverterGame): Boolean
}

/**
 * Small, fail-closed adapter for a verified Gen 6 destination slot table.
 *
 * The offsets must come from a game-specific layout audit. This class binds
 * them to one lawful user-supplied destination template and does not claim
 * that arbitrary offsets are playable save structures.
 */
class Gen6Pk6FixedSlotLayout(
    private val destinationGame: Gen6ConverterGame,
    destinationTemplate: ByteArray,
    slotOffsets: IntArray,
    private val vacancyValidator: Gen6Pk6SlotVacancyValidator
) {
    private val baseline = destinationTemplate.copyOf()
    private val slotOffsets = slotOffsets.copyOf()

    init {
        require(baseline.isNotEmpty()) { "Destination template is empty." }
        require(this.slotOffsets.isNotEmpty()) { "At least one destination slot is required." }
        require(this.slotOffsets.all { it >= 0 }) {
            "Destination slot offsets must not be negative."
        }
        require((1 until this.slotOffsets.size).all { index ->
            this.slotOffsets[index - 1] + Gen6Pk6Adapter.STORED_SIZE <= this.slotOffsets[index]
        }) {
            "Destination PK6 slot ranges must not overlap."
        }
        validateBounds(baseline)
        this.slotOffsets.forEachIndexed { index, offset ->
            val slot = baseline.copyOfRange(offset, offset + Gen6Pk6Adapter.STORED_SIZE)
            val validationInput = slot.copyOf()
            val vacant = vacancyValidator.isVacant(validationInput, destinationGame)
            check(validationInput.contentEquals(slot)) {
                "Destination vacancy validator changed PK6 slot $index."
            }
            require(vacant) {
                "Destination PK6 slot $index is not vacant."
            }
        }
    }

    fun capacityProvider(): Gen6Pk6DestinationCapacity =
        Gen6Pk6DestinationCapacity { destinationTemplate, game ->
            availableSlots(destinationTemplate, game)
        }

    fun recordInserter(): Gen6Pk6DestinationRecordInserter =
        Gen6Pk6DestinationRecordInserter { destinationTemplate, game, records ->
            insert(destinationTemplate, game, records)
        }

    /**
     * Builds a reader that reopens the exact transfer size requested by the
     * migration, excluding unrelated occupied slots from verification.
     */
    fun recordReader(): Gen6Pk6DestinationRecordReader =
        Gen6Pk6DestinationRecordReader { destinationSave, game, recordCount ->
            readPlaced(destinationSave, game, recordCount)
        }

    fun availableSlots(
        destinationTemplate: ByteArray,
        game: Gen6ConverterGame
    ): Int {
        requireGame(game)
        requireBoundTemplate(destinationTemplate)
        return slotOffsets.size
    }

    fun insert(
        destinationTemplate: ByteArray,
        game: Gen6ConverterGame,
        records: List<ByteArray>
    ): ByteArray {
        requireGame(game)
        requireBoundTemplate(destinationTemplate)
        require(records.size <= slotOffsets.size) {
            "Destination has ${slotOffsets.size} vacant PK6 slots for ${records.size} records."
        }

        // Validate every record before changing the output buffer, so a bad
        // record cannot produce a partially written destination.
        val validatedRecords = records.map { record ->
            Gen6Pk6Adapter.decode(record.copyOf())
            record.copyOf()
        }
        val output = destinationTemplate.copyOf()
        validatedRecords.forEachIndexed { index, record ->
            record.copyInto(output, destinationOffset = slotOffsets[index])
        }
        return output
    }

    fun readPlaced(
        destinationSave: ByteArray,
        game: Gen6ConverterGame,
        recordCount: Int
    ): List<ByteArray> {
        requireGame(game)
        validateBounds(destinationSave)
        require(recordCount in 0..slotOffsets.size) {
            "Placed PK6 record count must be between 0 and ${slotOffsets.size}."
        }
        return slotOffsets.take(recordCount).map { offset ->
            destinationSave.copyOfRange(offset, offset + Gen6Pk6Adapter.STORED_SIZE)
        }
    }

    private fun requireGame(game: Gen6ConverterGame) {
        require(game == destinationGame) {
            "Fixed PK6 layout is for $destinationGame, not $game."
        }
    }

    private fun requireBoundTemplate(destinationTemplate: ByteArray) {
        require(destinationTemplate.contentEquals(baseline)) {
            "Destination save does not match the template bound to this PK6 layout."
        }
    }

    private fun validateBounds(save: ByteArray) {
        slotOffsets.forEachIndexed { index, offset ->
            require(offset <= save.size - Gen6Pk6Adapter.STORED_SIZE) {
                "Destination slot $index exceeds the save buffer."
            }
        }
    }
}
