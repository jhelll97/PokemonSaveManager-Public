package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class Gen6Pk6RecordSaveMigrationTest {
    private fun storedRecord(species: Int, experience: Int): ByteArray {
        val plain = ByteArray(Gen6Pk6Adapter.STORED_SIZE)
        plain[0x08] = (species and 0xFF).toByte()
        plain[0x09] = (species ushr 8).toByte()
        plain[0x10] = experience.toByte()
        val sum = species + experience
        plain[0x06] = (sum and 0xFF).toByte()
        plain[0x07] = (sum ushr 8).toByte()
        return Gen6Pk6Adapter.encode(Gen6Pk6Adapter.decode(plain))
    }

    @Test
    fun malformedPk6IsRejectedBeforeDestinationPlacementAndInputsStayUntouched() {
        val source = byteArrayOf(11, 22, 33)
        val originalSource = source.copyOf()
        val destinationTemplate = byteArrayOf(44, 55, 66)
        val originalTemplate = destinationTemplate.copyOf()
        var destinationPlacementCalled = false

        val migration = Gen6Pk6RecordSaveMigration(
            extractor = Gen6Pk6SourceRecordExtractor { extractedSource, sourceGame ->
                assertEquals(Gen6ConverterGame.OMEGA_RUBY, sourceGame)
                assertArrayEquals(originalSource, extractedSource)
                // A correctly sized but invalid PK6 record must fail closed.
                listOf(ByteArray(Gen6Pk6Adapter.STORED_SIZE))
            },
            inserter = Gen6Pk6DestinationRecordInserter { _, _, _ ->
                destinationPlacementCalled = true
                byteArrayOf(1)
            }
        )

        assertThrows(IllegalArgumentException::class.java) {
            migration.migrate(
                sourceBytes = source,
                destinationTemplate = destinationTemplate,
                request = Gen6ConverterRequest(
                    Gen6ConverterGame.OMEGA_RUBY,
                    Gen6ConverterGame.ALPHA_SAPPHIRE
                )
            )
        }

        assertArrayEquals(originalSource, source)
        assertArrayEquals(originalTemplate, destinationTemplate)
        assertFalse(destinationPlacementCalled)
    }

    @Test
    fun extractorMutationFailsBeforeCapacityDecodeOrPlacement() {
        val source = byteArrayOf(11, 22, 33)
        val template = byteArrayOf(44, 55, 66)
        var capacityChecked = false
        var placed = false
        val migration = Gen6Pk6RecordSaveMigration(
            extractor = Gen6Pk6SourceRecordExtractor { sourceCopy, _ ->
                sourceCopy[0] = 99
                emptyList()
            },
            inserter = Gen6Pk6DestinationRecordInserter { _, _, _ ->
                placed = true
                byteArrayOf(44, 55, 66)
            },
            capacity = Gen6Pk6DestinationCapacity { _, _ ->
                capacityChecked = true
                1
            }
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            migration.migrate(
                source,
                template,
                Gen6ConverterRequest(
                    Gen6ConverterGame.OMEGA_RUBY,
                    Gen6ConverterGame.ALPHA_SAPPHIRE
                )
            )
        }

        assertEquals(
            "PK6 source extractor changed source bytes.",
            failure.message
        )
        assertFalse(capacityChecked)
        assertFalse(placed)
        assertArrayEquals(byteArrayOf(11, 22, 33), source)
        assertArrayEquals(byteArrayOf(44, 55, 66), template)
    }

    @Test
    fun inserterMutationOfValidatedRecordFailsBeforeDestinationReadback() {
        val source = byteArrayOf(11, 22, 33)
        val originalSource = source.copyOf()
        val template = byteArrayOf(44, 55, 66)
        val originalTemplate = template.copyOf()
        val record = storedRecord(25, 10)
        val originalRecord = record.copyOf()
        var destinationReadbackCalled = false

        val migration = Gen6Pk6RecordSaveMigration(
            extractor = Gen6Pk6SourceRecordExtractor { _, _ -> listOf(record) },
            inserter = Gen6Pk6DestinationRecordInserter { destination, _, records ->
                records.single()[0] = (records.single()[0].toInt() xor 1).toByte()
                destination
            },
            capacity = Gen6Pk6DestinationCapacity { _, _ -> 1 },
            destinationReader = Gen6Pk6DestinationRecordReader { _, _, _ ->
                destinationReadbackCalled = true
                listOf(originalRecord)
            }
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            migration.migrate(
                source,
                template,
                Gen6ConverterRequest(
                    Gen6ConverterGame.OMEGA_RUBY,
                    Gen6ConverterGame.ALPHA_SAPPHIRE
                )
            )
        }

        assertEquals(
            "Destination inserter changed validated PK6 records.",
            failure.message
        )
        assertFalse(destinationReadbackCalled)
        assertArrayEquals(originalSource, source)
        assertArrayEquals(originalTemplate, template)
        assertArrayEquals(originalRecord, record)
    }

    @Test
    fun capacityCallbackMutationOfExtractedRecordFailsBeforeDecodeOrPlacement() {
        val source = byteArrayOf(11, 22, 33)
        val originalSource = source.copyOf()
        val template = byteArrayOf(44, 55, 66)
        val originalTemplate = template.copyOf()
        val extractedRecord = storedRecord(25, 10)
        var placementCalled = false
        var readbackCalled = false

        val migration = Gen6Pk6RecordSaveMigration(
            extractor = Gen6Pk6SourceRecordExtractor { _, _ ->
                listOf(extractedRecord)
            },
            inserter = Gen6Pk6DestinationRecordInserter { destination, _, _ ->
                placementCalled = true
                destination
            },
            capacity = Gen6Pk6DestinationCapacity { _, _ ->
                extractedRecord[0] =
                    (extractedRecord[0].toInt() xor 1).toByte()
                1
            },
            destinationReader = Gen6Pk6DestinationRecordReader { _, _, _ ->
                readbackCalled = true
                emptyList()
            }
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            migration.migrate(
                source,
                template,
                Gen6ConverterRequest(
                    Gen6ConverterGame.OMEGA_RUBY,
                    Gen6ConverterGame.ALPHA_SAPPHIRE
                )
            )
        }

        assertEquals(
            "PK6 source records changed during destination capacity preflight.",
            failure.message
        )
        assertFalse(placementCalled)
        assertFalse(readbackCalled)
        assertArrayEquals(originalSource, source)
        assertArrayEquals(originalTemplate, template)
    }

    @Test
    fun capacityPreflightRejectsInsufficientSlotsBeforePk6DecodeOrPlacement() {
        val source = byteArrayOf(11, 22, 33)
        val template = byteArrayOf(44, 55, 66)
        var placed = false
        val migration = Gen6Pk6RecordSaveMigration(
            extractor = Gen6Pk6SourceRecordExtractor { _, _ ->
                listOf(ByteArray(Gen6Pk6Adapter.STORED_SIZE))
            },
            inserter = Gen6Pk6DestinationRecordInserter { _, _, _ ->
                placed = true
                byteArrayOf(1)
            },
            capacity = Gen6Pk6DestinationCapacity { copy, game ->
                assertEquals(Gen6ConverterGame.ALPHA_SAPPHIRE, game)
                assertArrayEquals(template, copy)
                0
            }
        )
        assertThrows(IllegalArgumentException::class.java) {
            migration.migrate(
                source, template,
                Gen6ConverterRequest(Gen6ConverterGame.OMEGA_RUBY, Gen6ConverterGame.ALPHA_SAPPHIRE)
            )
        }
        assertFalse(placed)
        assertArrayEquals(byteArrayOf(11, 22, 33), source)
        assertArrayEquals(byteArrayOf(44, 55, 66), template)
    }


    @Test
    fun capacityProviderMutationFailsBeforePk6DecodeOrPlacement() {
        val source = byteArrayOf(11, 22, 33)
        val template = byteArrayOf(44, 55, 66)
        var placed = false
        val migration = Gen6Pk6RecordSaveMigration(
            extractor = Gen6Pk6SourceRecordExtractor { _, _ ->
                listOf(ByteArray(Gen6Pk6Adapter.STORED_SIZE))
            },
            inserter = Gen6Pk6DestinationRecordInserter { _, _, _ ->
                placed = true
                byteArrayOf(44, 55, 66)
            },
            capacity = Gen6Pk6DestinationCapacity { destinationCopy, _ ->
                destinationCopy[0] = 0
                1
            }
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            migration.migrate(
                source,
                template,
                Gen6ConverterRequest(
                    Gen6ConverterGame.OMEGA_RUBY,
                    Gen6ConverterGame.ALPHA_SAPPHIRE
                )
            )
        }

        assertEquals(
            "Destination capacity provider changed template bytes.",
            failure.message
        )
        assertFalse(placed)
        assertArrayEquals(byteArrayOf(11, 22, 33), source)
        assertArrayEquals(byteArrayOf(44, 55, 66), template)
    }

    @Test
    fun placementCannotChangeDestinationSaveLength() {
        val source = byteArrayOf(11, 22, 33)
        val template = byteArrayOf(44, 55, 66)
        val migration = Gen6Pk6RecordSaveMigration(
            extractor = Gen6Pk6SourceRecordExtractor { _, _ -> emptyList() },
            inserter = Gen6Pk6DestinationRecordInserter { _, _, _ ->
                byteArrayOf(44, 55, 66, 77)
            },
            capacity = Gen6Pk6DestinationCapacity { _, _ -> 0 }
        )
        assertThrows(IllegalArgumentException::class.java) {
            migration.migrate(
                source, template,
                Gen6ConverterRequest(Gen6ConverterGame.OMEGA_RUBY, Gen6ConverterGame.ALPHA_SAPPHIRE)
            )
        }
        assertArrayEquals(byteArrayOf(11, 22, 33), source)
        assertArrayEquals(byteArrayOf(44, 55, 66), template)
    }

    @Test
    fun unchangedDestinationLengthIsAccepted() {
        val source = byteArrayOf(11, 22, 33)
        val template = byteArrayOf(44, 55, 66)
        val migration = Gen6Pk6RecordSaveMigration(
            extractor = Gen6Pk6SourceRecordExtractor { _, _ -> emptyList() },
            inserter = Gen6Pk6DestinationRecordInserter { destination, _, _ ->
                destination[1] = 99
                destination
            },
            capacity = Gen6Pk6DestinationCapacity { _, _ -> 0 }
        )
        val output = migration.migrate(
            source, template,
            Gen6ConverterRequest(Gen6ConverterGame.OMEGA_RUBY, Gen6ConverterGame.ALPHA_SAPPHIRE)
        )
        assertArrayEquals(byteArrayOf(44, 99, 66), output)
        assertArrayEquals(byteArrayOf(44, 55, 66), template)
    }


    @Test
    fun missingCapacityProviderRejectsNonemptyTransferBeforePlacement() {
        val source = byteArrayOf(11, 22, 33)
        val template = byteArrayOf(44, 55, 66)
        var placed = false
        val migration = Gen6Pk6RecordSaveMigration(
            extractor = Gen6Pk6SourceRecordExtractor { _, _ ->
                listOf(ByteArray(Gen6Pk6Adapter.STORED_SIZE))
            },
            inserter = Gen6Pk6DestinationRecordInserter { _, _, _ ->
                placed = true
                byteArrayOf(44, 55, 66)
            }
        )
        val failure = assertThrows(IllegalArgumentException::class.java) {
            migration.migrate(
                source, template,
                Gen6ConverterRequest(Gen6ConverterGame.OMEGA_RUBY, Gen6ConverterGame.ALPHA_SAPPHIRE)
            )
        }
        assertEquals(
            "A verified destination capacity provider is required for PK6 placement.",
            failure.message
        )
        assertFalse(placed)
        assertArrayEquals(byteArrayOf(11, 22, 33), source)
        assertArrayEquals(byteArrayOf(44, 55, 66), template)
    }


    @Test
    fun negativeDestinationCapacityFailsClosedBeforePk6DecodeAndPlacement() {
        val source = byteArrayOf(11, 22, 33)
        val template = byteArrayOf(44, 55, 66)
        var placementCalled = false
        val migration = Gen6Pk6RecordSaveMigration(
            extractor = Gen6Pk6SourceRecordExtractor { _, _ ->
                listOf(ByteArray(Gen6Pk6Adapter.STORED_SIZE))
            },
            inserter = Gen6Pk6DestinationRecordInserter { _, _, _ ->
                placementCalled = true
                byteArrayOf(44, 55, 66)
            },
            capacity = Gen6Pk6DestinationCapacity { destinationCopy, game ->
                assertEquals(Gen6ConverterGame.ALPHA_SAPPHIRE, game)
                assertArrayEquals(template, destinationCopy)
                -1
            }
        )
        val error = assertThrows(IllegalArgumentException::class.java) {
            migration.migrate(
                source, template,
                Gen6ConverterRequest(Gen6ConverterGame.OMEGA_RUBY, Gen6ConverterGame.ALPHA_SAPPHIRE)
            )
        }
        assertEquals("Destination capacity must not be negative.", error.message)
        assertFalse(placementCalled)
        assertArrayEquals(byteArrayOf(11, 22, 33), source)
        assertArrayEquals(byteArrayOf(44, 55, 66), template)
    }

}
