package com.example.pokemonsavemanager

/**
 * Extracts ordered encrypted PK6 records from a source save.
 *
 * Save-format offsets, party/PC ordering and slot capacity remain the
 * extractor's responsibility.
 */
fun interface Gen6Pk6SourceRecordExtractor {
    fun extract(sourceSave: ByteArray, sourceGame: Gen6ConverterGame): List<ByteArray>
}

/**
 * Places validated, ordered PK6 records in a destination save template.
 *
 * Destination layout, capacity, game-specific legality and Pokédex updates
 * remain the inserter's responsibility. This lossless migration strategy requires
 * supplied record payloads to persist unchanged; reject incompatible records or
 * use an explicit transformation strategy when destination fields must change.
 */
fun interface Gen6Pk6DestinationRecordInserter {
    fun insert(
        destinationTemplate: ByteArray,
        destinationGame: Gen6ConverterGame,
        records: List<ByteArray>
    ): ByteArray
}

/**
 * Reopens only the migrated destination slots, in transfer order.
 * Implementations must use a verified destination layout and exclude unrelated
 * Pokémon already present in the template. Reading must not modify save bytes.
 */
fun interface Gen6Pk6DestinationRecordReader {
    fun readPlaced(
        destinationSave: ByteArray,
        destinationGame: Gen6ConverterGame,
        recordCount: Int
    ): List<ByteArray>
}

/** Destination slot capacity supplied by a verified game-specific layout. */
fun interface Gen6Pk6DestinationCapacity {
    fun availableSlots(destinationTemplate: ByteArray, destinationGame: Gen6ConverterGame): Int
}

/**
 * Adapter-backed Gen 6 migration strategy for [Gen6TemplateDestinationSaveWriter].
 *
 * Each extracted PK6 is decoded, re-encoded, and decoded again before the
 * destination inserter receives it. This verifies the record checksum and
 * preserves its decrypted payload while keeping save-layout and legality
 * decisions explicit in the supplied callbacks.
 */
class Gen6Pk6RecordSaveMigration(
    private val extractor: Gen6Pk6SourceRecordExtractor,
    private val inserter: Gen6Pk6DestinationRecordInserter,
    private val capacity: Gen6Pk6DestinationCapacity? = null,
    private val destinationReader: Gen6Pk6DestinationRecordReader? = null,
    private val versionExclusiveCatalog: Gen6VersionExclusiveCatalog? = null
) : Gen6SaveMigration {
    override fun migrate(
        sourceBytes: ByteArray,
        destinationTemplate: ByteArray,
        request: Gen6ConverterRequest
    ): ByteArray {
        val plan = Gen6SaveConverterPlanner.plan(request)
        plan.requireExportable()
        val stableRequest = plan.request
        require(sourceBytes.isNotEmpty()) { "Source save is empty." }
        require(destinationTemplate.isNotEmpty()) { "Destination template is empty." }

        val sourceSnapshot = sourceBytes.copyOf()
        val templateSnapshot = destinationTemplate.copyOf()
        val extractionInput = sourceSnapshot.copyOf()
        val sourceRecords = extractor.extract(
            extractionInput,
            stableRequest.source
        )
        check(extractionInput.contentEquals(sourceSnapshot)) {
            "PK6 source extractor changed source bytes."
        }
        check(sourceBytes.contentEquals(sourceSnapshot)) {
            "Source save changed during PK6 extraction."
        }
        check(destinationTemplate.contentEquals(templateSnapshot)) {
            "Destination template changed during PK6 extraction."
        }
        val sourceRecordSnapshots = sourceRecords.map { it.copyOf() }
        val selectedExclusiveRecords = if (stableRequest.includeVersionExclusives) {
            requireNotNull(versionExclusiveCatalog) {
                "A verified destination version-exclusive catalog is required."
            }.resolve(
                destinationGame = stableRequest.destination,
                selectedIds = stableRequest.selectedVersionExclusiveIds
            )
        } else {
            emptyList()
        }
        check(
            sourceRecords.size == sourceRecordSnapshots.size &&
                sourceRecords.indices.all { index ->
                    sourceRecords[index].contentEquals(sourceRecordSnapshots[index])
                }
        ) {
            "PK6 source records changed during version-exclusive resolution."
        }
        val recordsToPlace =
            sourceRecordSnapshots + selectedExclusiveRecords.map { it.copyOf() }
        // Never place a non-empty transfer without a verified destination slot count.
        // An absent capacity provider is not evidence that the destination has room.
        require(recordsToPlace.isEmpty() || capacity != null) {
            "A verified destination capacity provider is required for PK6 placement."
        }
        capacity?.let { checker ->
            val capacityInput = templateSnapshot.copyOf()
            val available = checker.availableSlots(
                capacityInput,
                stableRequest.destination
            )
            check(capacityInput.contentEquals(templateSnapshot)) {
                "Destination capacity provider changed template bytes."
            }
            check(
                sourceRecords.size == sourceRecordSnapshots.size &&
                    sourceRecords.indices.all { index ->
                        sourceRecords[index].contentEquals(sourceRecordSnapshots[index])
                    }
            ) {
                "PK6 source records changed during destination capacity preflight."
            }
            require(available >= 0) { "Destination capacity must not be negative." }
            require(recordsToPlace.size <= available) {
                "Destination has $available available PK6 slots for ${recordsToPlace.size} records."
            }
            check(sourceBytes.contentEquals(sourceSnapshot) && destinationTemplate.contentEquals(templateSnapshot)) {
                "Save bytes changed during destination capacity preflight."
            }
        }
        require(recordsToPlace.isEmpty() || destinationReader != null) {
            "A verified destination reader is required for PK6 placement."
        }
        val validatedRecords = recordsToPlace.mapIndexed { index, encryptedRecord ->
            val decoded = Gen6Pk6Adapter.decode(encryptedRecord.copyOf())
            val reencoded = Gen6Pk6Adapter.encode(decoded)
            val verified = Gen6Pk6Adapter.decode(reencoded)

            check(verified.checksumValid) {
                "PK6 record $index failed checksum validation after round trip."
            }
            check(verified.species == decoded.species) {
                "PK6 record $index changed species during round trip."
            }
            check(verified.decryptedBytes.contentEquals(decoded.decryptedBytes)) {
                "PK6 record $index changed payload during round trip."
            }
            reencoded.copyOf()
        }

        check(sourceBytes.contentEquals(sourceSnapshot)) {
            "Source save changed during PK6 extraction."
        }
        check(destinationTemplate.contentEquals(templateSnapshot)) {
            "Destination template changed during PK6 extraction."
        }

        val placementRecords = validatedRecords.map { it.copyOf() }
        val destinationBytes = inserter.insert(
            destinationTemplate = templateSnapshot.copyOf(),
            destinationGame = stableRequest.destination,
            records = placementRecords
        )
        check(
            placementRecords.size == validatedRecords.size &&
                placementRecords.indices.all { index ->
                    placementRecords[index].contentEquals(validatedRecords[index])
                }
        ) {
            "Destination inserter changed validated PK6 records."
        }
        check(sourceBytes.contentEquals(sourceSnapshot)) {
            "Source save changed during PK6 placement."
        }
        check(destinationTemplate.contentEquals(templateSnapshot)) {
            "Destination template changed during PK6 placement."
        }
        require(destinationBytes.isNotEmpty()) {
            "PK6 destination inserter returned no bytes."
        }
        require(destinationBytes.size == templateSnapshot.size) {
            "PK6 destination placement changed the save template size."
        }
        val output = destinationBytes.copyOf()
        if (validatedRecords.isNotEmpty()) {
            val readbackInput = output.copyOf()
            val persistedRecords = destinationReader!!.readPlaced(
                readbackInput,
                stableRequest.destination,
                validatedRecords.size
            )
            check(readbackInput.contentEquals(output)) {
                "Destination reader changed save bytes during PK6 readback."
            }
            check(persistedRecords.size == validatedRecords.size) {
                "Destination PK6 readback count does not match the transfer."
            }
            persistedRecords.forEachIndexed { index, persisted ->
                val actual = Gen6Pk6Adapter.decode(persisted.copyOf())
                val expected = Gen6Pk6Adapter.decode(validatedRecords[index])
                check(persisted.contentEquals(validatedRecords[index])) {
                    "Destination PK6 record $index did not preserve exact encrypted bytes after placement."
                }
                check(actual.decryptedBytes.contentEquals(expected.decryptedBytes)) {
                    "Destination PK6 record $index changed payload or order after placement."
                }
            }
        }
        check(sourceBytes.contentEquals(sourceSnapshot) && destinationTemplate.contentEquals(templateSnapshot)) {
            "Save bytes changed during destination PK6 readback."
        }
        return output
    }
}
