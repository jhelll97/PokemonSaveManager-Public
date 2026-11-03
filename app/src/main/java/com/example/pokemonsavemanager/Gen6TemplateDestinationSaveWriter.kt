package com.example.pokemonsavemanager

fun interface Gen6SaveMigration {
    fun migrate(
        sourceBytes: ByteArray,
        destinationTemplate: ByteArray,
        request: Gen6ConverterRequest
    ): ByteArray
}

fun interface Gen6SaveBytesValidator {
    fun validate(
        destinationBytes: ByteArray,
        request: Gen6ConverterRequest
    ): Gen6DestinationValidation
}

/**
 * Destination writer backed by a lawful user-supplied save template.
 *
 * The template and source are copied before converter code receives them.
 * Migration must return a complete destination save with the same size as the
 * verified template; format, integrity and reopen checks remain mandatory.
 */
class Gen6TemplateDestinationSaveWriter(
    override val sourceGame: Gen6ConverterGame,
    override val destinationGame: Gen6ConverterGame,
    destinationTemplate: ByteArray,
    private val sourceValidator: Gen6SaveBytesValidator,
    private val migration: Gen6SaveMigration,
    private val validator: Gen6SaveBytesValidator
) : Gen6DestinationSaveWriter {
    private val baseline = destinationTemplate.copyOf()

    init {
        require(baseline.isNotEmpty()) { "Destination template is empty." }
        Gen6SaveConverterPlanner.plan(
            Gen6ConverterRequest(sourceGame, destinationGame)
        ).requireExportable()
    }

    override fun validateSource(
        sourceBytes: ByteArray,
        request: Gen6ConverterRequest
    ): Gen6DestinationValidation {
        val stableRequest = stableMatchingRequest(request)
        require(sourceBytes.isNotEmpty()) { "Source save is empty." }
        val validationInput = sourceBytes.copyOf()
        val validationRequest = stableMatchingRequest(stableRequest)
        val validation = sourceValidator.validate(
            validationInput,
            validationRequest
        )
        check(validationRequest == stableRequest) {
            "Source save validator changed conversion request."
        }
        check(validationInput.contentEquals(sourceBytes)) {
            "Source save validator changed source bytes."
        }
        return validation
    }

    override fun serialize(
        sourceBytes: ByteArray,
        request: Gen6ConverterRequest
    ): ByteArray {
        val stableRequest = stableMatchingRequest(request)
        require(sourceBytes.isNotEmpty()) { "Source save is empty." }
        val sourceValidation = validateSource(sourceBytes, stableRequest)
        require(sourceValidation.isValid) {
            "Source validation failed: ${sourceValidation.note.ifBlank { "format, integrity, reopen or identity check" }}"
        }
        require(sourceValidation.detectedGame == sourceGame) {
            "Source identity does not match $sourceGame."
        }
        val baselineBefore = baseline.copyOf()
        // A destination baseline must be valid for the requested game before
        // migration can change it. Validate a private copy and fail on mutation.
        val baselineValidationRequest = stableMatchingRequest(stableRequest)
        val baselineValidation = validator.validate(
            baselineBefore,
            baselineValidationRequest
        )
        check(baselineValidationRequest == stableRequest) {
            "Destination template validator changed conversion request."
        }
        check(baselineBefore.contentEquals(baseline)) {
            "Destination template validator changed baseline bytes."
        }
        require(baselineValidation.isValid && baselineValidation.detectedGame == destinationGame) {
            "Destination template failed format, integrity, reopen or game identity validation."
        }
        val migrationSource = sourceBytes.copyOf()
        val migrationRequest = stableMatchingRequest(stableRequest)
        val converted = migration.migrate(
            sourceBytes = migrationSource,
            destinationTemplate = baseline.copyOf(),
            request = migrationRequest
        )
        check(migrationRequest == stableRequest) {
            "Destination migration changed conversion request."
        }
        check(migrationSource.contentEquals(sourceBytes)) {
            "Destination migration changed source bytes."
        }
        check(baseline.contentEquals(baselineBefore)) {
            "Destination template changed during conversion."
        }
        require(converted.size == baseline.size) {
            "Converted save size does not match the destination template."
        }
        val convertedValidation = validateDestination(
            converted,
            stableRequest
        )
        require(
            convertedValidation.isValid &&
                convertedValidation.detectedGame == destinationGame
        ) {
            "Converted destination failed format, integrity, reopen or game identity validation."
        }
        return converted.copyOf()
    }

    override fun validateDestination(
        destinationBytes: ByteArray,
        request: Gen6ConverterRequest
    ): Gen6DestinationValidation {
        val stableRequest = stableMatchingRequest(request)
        require(destinationBytes.size == baseline.size) {
            "Destination save size does not match the destination template."
        }
        val validationInput = destinationBytes.copyOf()
        val validationRequest = stableMatchingRequest(stableRequest)
        val validation = validator.validate(validationInput, validationRequest)
        check(validationRequest == stableRequest) {
            "Destination save validator changed conversion request."
        }
        check(validationInput.contentEquals(destinationBytes)) {
            "Destination save validator changed destination bytes."
        }
        return validation
    }

    private fun stableMatchingRequest(
        request: Gen6ConverterRequest
    ): Gen6ConverterRequest {
        val plan = Gen6SaveConverterPlanner.plan(request)
        plan.requireExportable()
        return plan.request.also(::requireMatchingRoute)
    }

    private fun requireMatchingRoute(request: Gen6ConverterRequest) {
        require(request.source == sourceGame && request.destination == destinationGame) {
            "Destination template writer does not match the conversion route."
        }
    }
}
