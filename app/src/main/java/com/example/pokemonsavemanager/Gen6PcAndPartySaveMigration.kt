package com.example.pokemonsavemanager

/**
 * Complete Pokémon-data migration for a lawful Gen 6 destination template.
 *
 * PC and party writes remain independently validated, but are composed here
 * into one atomic in-memory result. The caller receives bytes only after the
 * destination passes the combined identity, checksum and reopen validation.
 */
class Gen6PcAndPartySaveMigration(
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
        val sourceSnapshot = sourceBytes.copyOf()
        val templateSnapshot = destinationTemplate.copyOf()

        val sourcePc = Gen6PcStorageLayout(stableRequest.source, sourceSnapshot)
        val sourceParty = Gen6PartyStorageLayout(stableRequest.source, sourceSnapshot)
        val destinationPc =
            Gen6PcStorageLayout(stableRequest.destination, templateSnapshot)
        val destinationParty =
            Gen6PartyStorageLayout(stableRequest.destination, templateSnapshot)

        val partyRecords =
            sourceParty.sourceRecords(sourceSnapshot, stableRequest.source)
        require(partyRecords.size <= destinationParty.availableSlots(
            templateSnapshot,
            stableRequest.destination
        )) {
            "Destination party does not have room for all source party records."
        }

        val pcMigration = Gen6Pk6RecordSaveMigration(
            extractor = sourcePc.sourceExtractor(),
            inserter = destinationPc.recordInserter(),
            capacity = destinationPc.capacityProvider(),
            destinationReader = destinationPc.recordReader(),
            versionExclusiveCatalog = versionExclusiveCatalog
        )
        val pcOutput = pcMigration.migrate(
            sourceSnapshot,
            templateSnapshot,
            stableRequest
        )
        check(sourceBytes.contentEquals(sourceSnapshot)) {
            "Source save changed during concrete PC migration."
        }
        check(destinationTemplate.contentEquals(templateSnapshot)) {
            "Destination template changed during concrete PC migration."
        }

        val migratedParty =
            Gen6PartyStorageLayout(stableRequest.destination, pcOutput)
        val output = migratedParty.insertRecords(
            pcOutput,
            stableRequest.destination,
            partyRecords
        )
        check(sourceBytes.contentEquals(sourceSnapshot)) {
            "Source save changed during concrete party migration."
        }
        check(destinationTemplate.contentEquals(templateSnapshot)) {
            "Destination template changed during concrete party migration."
        }

        val validation =
            Gen6PartyStorageLayout.validate(output, stableRequest.destination)
        check(validation.isValid &&
            validation.detectedGame == stableRequest.destination
        ) {
            "Combined Gen 6 PC and party destination failed final validation."
        }
        return output.copyOf()
    }
}

/**
 * Builds the production template writer from the concrete Gen 6 layouts.
 *
 * Source and destination validators include title identity, Pokédex, PC and
 * party checksums plus PK6 reopen checks. Export remains the responsibility of
 * [Gen6SaveConversionService], which always creates a separate file.
 */
object Gen6ConcreteTemplateWriterFactory {
    fun create(
        sourceGame: Gen6ConverterGame,
        destinationGame: Gen6ConverterGame,
        destinationTemplate: ByteArray,
        versionExclusiveCatalog: Gen6VersionExclusiveCatalog? = null
    ): Gen6TemplateDestinationSaveWriter =
        Gen6TemplateDestinationSaveWriter(
            sourceGame = sourceGame,
            destinationGame = destinationGame,
            destinationTemplate = destinationTemplate,
            sourceValidator = Gen6SaveBytesValidator { sourceBytes, _ ->
                Gen6PartyStorageLayout.validate(sourceBytes, sourceGame)
            },
            migration = Gen6PcAndPartySaveMigration(versionExclusiveCatalog),
            validator = Gen6SaveBytesValidator { destinationBytes, _ ->
                Gen6PartyStorageLayout.validate(
                    destinationBytes,
                    destinationGame
                )
            }
        )
}
