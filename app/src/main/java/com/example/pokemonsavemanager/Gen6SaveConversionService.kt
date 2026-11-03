package com.example.pokemonsavemanager

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * Runtime seam between a verified Gen 6 destination serializer and safe export.
 *
 * A writer must perform the game-specific parsing, migration, Legalise,
 * checksums and destination serialization. This service refuses to publish
 * bytes until the writer also reports format, integrity and reopen success.
 */
data class Gen6DestinationValidation(
    val formatValid: Boolean,
    val integrityValid: Boolean,
    val reopenValid: Boolean,
    val detectedGame: Gen6ConverterGame? = null,
    val note: String = ""
) {
    val isValid: Boolean
        get() = formatValid && integrityValid && reopenValid && detectedGame != null
}

internal fun interface Gen6DestinationPublisher {
    fun publish(
        sourceFile: File,
        requestedDestination: File,
        destinationBytes: ByteArray,
        expectedGame: Gen6ConverterGame
    ): File
}

interface Gen6DestinationSaveWriter {
    val sourceGame: Gen6ConverterGame
    val destinationGame: Gen6ConverterGame

    fun validateSource(
        sourceBytes: ByteArray,
        request: Gen6ConverterRequest
    ): Gen6DestinationValidation

    fun serialize(
        sourceBytes: ByteArray,
        request: Gen6ConverterRequest
    ): ByteArray

    fun validateDestination(
        destinationBytes: ByteArray,
        request: Gen6ConverterRequest
    ): Gen6DestinationValidation
}

class Gen6SaveWriterCatalog(
    writers: Iterable<Gen6DestinationSaveWriter>
) {
    private val writersByRoute = buildMap {
        for (writer in writers) {
            val route = writer.sourceGame to writer.destinationGame
            require(Gen6SaveConverterPlanner.plan(
                Gen6ConverterRequest(writer.sourceGame, writer.destinationGame)
            ).canExport) {
                "Writer route is not a mandatory Gen 6 same-generation route."
            }
            check(put(route, writer) == null) {
                "Duplicate Gen 6 destination writer for $route."
            }
        }
    }

    fun requireWriter(request: Gen6ConverterRequest): Gen6DestinationSaveWriter {
        Gen6SaveConverterPlanner.plan(request).requireExportable()
        return writersByRoute[request.source to request.destination]
            ?: error("No verified destination writer is registered for ${request.source} to ${request.destination}.")
    }
}

class Gen6SaveConversionService private constructor(
    private val writerFor: (Gen6ConverterRequest) -> Gen6DestinationSaveWriter,
    private val publisher: Gen6DestinationPublisher
) {
    constructor(writer: Gen6DestinationSaveWriter) : this(
        writerResolver(writer),
        defaultPublisher
    )

    constructor(catalog: Gen6SaveWriterCatalog) : this(
        catalog::requireWriter,
        defaultPublisher
    )

    internal constructor(
        writer: Gen6DestinationSaveWriter,
        publisher: Gen6DestinationPublisher
    ) : this(writerResolver(writer), publisher)

    fun convert(
        sourceFile: File,
        requestedDestination: File,
        request: Gen6ConverterRequest
    ): File {
        val plan = Gen6SaveConverterPlanner.plan(request)
        plan.requireExportable()
        requireDistinctRequestedDestination(sourceFile, requestedDestination)
        val stableRequest = plan.request
        val writer = writerFor(stableRequest)

        // Keep both contents and filesystem metadata across writer execution:
        // a writer must never alter the user's original save while preparing a
        // converted copy.
        val sourceState = Gen6SourceFileSnapshot.capture(sourceFile)
        val sourceSnapshot = sourceState.bytes
        require(sourceSnapshot.isNotEmpty()) { "Source save is empty." }
        val sourceValidationDestinationDirectory =
            snapshotDestinationDirectory(requestedDestination, sourceFile)
        val sourceValidationInput = sourceSnapshot.copyOf()
        val sourceValidationRequest = callbackRequest(stableRequest)
        val sourceValidation = try {
            writer.validateSource(
                sourceValidationInput,
                sourceValidationRequest
            )
        } catch (failure: Throwable) {
            rethrowSourceValidationFailureAfterIntegrityVerification(
                sourceFile = sourceFile,
                requestedDestination = requestedDestination,
                sourceState = sourceState,
                destinationDirectory =
                    sourceValidationDestinationDirectory,
                sourceValidationInput = sourceValidationInput,
                sourceSnapshot = sourceSnapshot,
                sourceValidationRequest = sourceValidationRequest,
                stableRequest = stableRequest,
                failure = failure
            )
        }
        requireSourceDirectoryChainWithoutSymbolicLinks(sourceFile)
        sourceState.requireUnchanged("Source save during source validation")
        requireRequestedDestinationDirectoryChainWithoutSymbolicLinks(
            requestedDestination
        )
        requireRequestedDestinationAbsentAfterCallback(
            requestedDestination,
            "Source validator"
        )
        requireDestinationDirectoryUnchanged(
            sourceValidationDestinationDirectory
        )
        requirePreExistingDestinationEntriesUnchanged(
            sourceValidationDestinationDirectory
        )
        requireNoDestinationEntriesAdded(
            sourceValidationDestinationDirectory,
            "Source validator left unexpected paths."
        )
        check(sourceValidationRequest == stableRequest) {
            "Source validator changed conversion request."
        }
        check(sourceValidationInput.contentEquals(sourceSnapshot)) {
            "Source validator changed source save bytes."
        }
        require(sourceValidation.isValid) {
            "Source validation failed: ${sourceValidation.note.ifBlank { "format, integrity, reopen or identity check" }}"
        }
        require(sourceValidation.detectedGame == stableRequest.source) {
            "Source identity does not match ${stableRequest.source}."
        }
        // Serialization is also a read-only boundary. Isolate the writer from
        // the captured source and reject any attempt to mutate its input.
        val serializationDestinationDirectory =
            snapshotDestinationDirectory(requestedDestination, sourceFile)
        val serializationInput = sourceSnapshot.copyOf()
        val serializationRequest = callbackRequest(stableRequest)
        val destinationBytes = try {
            writer.serialize(
                serializationInput,
                serializationRequest
            )
        } catch (failure: Throwable) {
            rethrowSerializationFailureAfterIntegrityVerification(
                sourceFile = sourceFile,
                requestedDestination = requestedDestination,
                sourceState = sourceState,
                destinationDirectory =
                    serializationDestinationDirectory,
                serializationInput = serializationInput,
                sourceSnapshot = sourceSnapshot,
                serializationRequest = serializationRequest,
                stableRequest = stableRequest,
                failure = failure
            )
        }
        requireSourceDirectoryChainWithoutSymbolicLinks(sourceFile)
        sourceState.requireUnchanged(
            "Source save during destination serialization"
        )
        requireRequestedDestinationDirectoryChainWithoutSymbolicLinks(
            requestedDestination
        )
        requireRequestedDestinationAbsentAfterCallback(
            requestedDestination,
            "Destination serializer"
        )
        requireDestinationDirectoryUnchanged(
            serializationDestinationDirectory
        )
        requirePreExistingDestinationEntriesUnchanged(
            serializationDestinationDirectory
        )
        requireNoDestinationEntriesAdded(
            serializationDestinationDirectory,
            "Destination serializer left unexpected paths."
        )
        check(serializationRequest == stableRequest) {
            "Destination serializer changed conversion request."
        }
        check(serializationInput.contentEquals(sourceSnapshot)) {
            "Destination serializer changed source save bytes."
        }
        require(destinationBytes.isNotEmpty()) { "Destination serializer returned no bytes." }
        val serializedSnapshot = destinationBytes.copyOf()
        // Validation is a read-only boundary. Give the writer a private copy and
        // reject mutation so exported bytes are exactly the serialized candidate.
        val validationDestinationDirectory =
            snapshotDestinationDirectory(requestedDestination, sourceFile)
        val validationInput = serializedSnapshot.copyOf()
        val validationRequest = callbackRequest(stableRequest)
        val validation = try {
            writer.validateDestination(
                validationInput,
                validationRequest
            )
        } catch (failure: Throwable) {
            rethrowDestinationValidationFailureAfterIntegrityVerification(
                sourceFile = sourceFile,
                requestedDestination = requestedDestination,
                sourceState = sourceState,
                destinationDirectory = validationDestinationDirectory,
                validationInput = validationInput,
                serializedSnapshot = serializedSnapshot,
                validationRequest = validationRequest,
                stableRequest = stableRequest,
                destinationBytes = destinationBytes,
                failure = failure
            )
        }
        requireSourceDirectoryChainWithoutSymbolicLinks(sourceFile)
        sourceState.requireUnchanged(
            "Source save during destination serialization or validation"
        )
        requireRequestedDestinationDirectoryChainWithoutSymbolicLinks(
            requestedDestination
        )
        requireRequestedDestinationAbsentAfterCallback(
            requestedDestination,
            "Destination validator"
        )
        requireDestinationDirectoryUnchanged(
            validationDestinationDirectory
        )
        requirePreExistingDestinationEntriesUnchanged(
            validationDestinationDirectory
        )
        requireNoDestinationEntriesAdded(
            validationDestinationDirectory,
            "Destination validator left unexpected paths."
        )
        check(validationRequest == stableRequest) {
            "Destination validator changed conversion request."
        }
        check(validationInput.contentEquals(serializedSnapshot)) {
            "Destination validator changed serialized save bytes."
        }
        check(destinationBytes.contentEquals(serializedSnapshot)) {
            "Destination serializer changed its returned bytes during validation."
        }
        require(validation.isValid) {
            "Destination validation failed: ${validation.note.ifBlank { "format, integrity, reopen or identity check" }}"
        }
        require(validation.detectedGame == stableRequest.destination) {
            "Destination identity does not match ${stableRequest.destination}."
        }
        requireSourceDirectoryChainWithoutSymbolicLinks(sourceFile)
        val destinationDirectory =
            snapshotDestinationDirectory(requestedDestination, sourceFile)
        val publicationInput = serializedSnapshot.copyOf()
        val exported = try {
            publisher.publish(
                sourceFile = sourceFile,
                requestedDestination = requestedDestination,
                destinationBytes = publicationInput,
                expectedGame = stableRequest.destination
            )
        } catch (failure: Throwable) {
            rethrowPublisherFailureAfterCleanup(
                sourceFile = sourceFile,
                requestedDestination = requestedDestination,
                destinationDirectory = destinationDirectory,
                sourceState = sourceState,
                publicationInput = publicationInput,
                serializedSnapshot = serializedSnapshot,
                failure = failure
            )
        }
        rejectSourceAlias(sourceFile, exported)
        requireDestinationDirectoryUnchanged(destinationDirectory)
        rejectOutsideDestinationDirectory(requestedDestination, exported)
        rejectPreExistingDestination(exported, destinationDirectory.existingPaths)
        rejectLinkedDestination(exported)
        var publishedCleanupState: Gen6SourceFileSnapshot? = null
        try {
            val publishedState = Gen6SourceFileSnapshot.capture(exported)
            publishedCleanupState = publishedState
            requireSourceDirectoryChainWithoutSymbolicLinks(sourceFile)
            requireDestinationDirectoryChainWithoutSymbolicLinks(
                destinationDirectory.directory
            )
            sourceState.requireUnchanged(
                "Source save during destination publication"
            )
            requirePreExistingDestinationEntriesUnchanged(destinationDirectory)
            requireOnlyPublishedDestinationAdded(destinationDirectory, exported)
            rejectPreExistingDestinationIdentity(
                exported,
                destinationDirectory.existingFileKeysByPath.values
            )
            check(publicationInput.contentEquals(serializedSnapshot)) {
                "Destination publisher changed serialized save bytes."
            }
            val persisted = publishedState.bytes
            check(persisted.contentEquals(serializedSnapshot)) {
                "Published destination bytes differ from the serialized candidate."
            }
            val readbackInput = persisted.copyOf()
            val readbackValidationRequest = callbackRequest(stableRequest)
            val readbackValidation = try {
                writer.validateDestination(
                    readbackInput,
                    readbackValidationRequest
                )
            } catch (failure: Throwable) {
                rethrowReadbackFailureAfterIntegrityVerification(
                    sourceFile = sourceFile,
                    sourceState = sourceState,
                    publishedState = publishedState,
                    destinationDirectory = destinationDirectory,
                    exported = exported,
                    readbackInput = readbackInput,
                    persisted = persisted,
                    readbackValidationRequest = readbackValidationRequest,
                    stableRequest = stableRequest,
                    failure = failure
                )
            }
            requireSourceDirectoryChainWithoutSymbolicLinks(sourceFile)
            sourceState.requireUnchanged(
                "Source save during published destination validation"
            )
            requireDestinationDirectoryChainWithoutSymbolicLinks(
                destinationDirectory.directory
            )
            publishedState.requireUnchanged(
                "Published destination during readback validation"
            )
            requireDestinationDirectoryUnchanged(destinationDirectory)
            requirePreExistingDestinationEntriesUnchanged(
                destinationDirectory
            )
            requireOnlyPublishedDestinationAdded(
                destinationDirectory,
                exported
            )
            check(readbackValidationRequest == stableRequest) {
                "Published destination validator changed conversion request."
            }
            check(readbackInput.contentEquals(persisted)) {
                "Destination validator changed published save bytes."
            }
            require(readbackValidation.isValid) {
                "Published destination validation failed: " +
                    readbackValidation.note.ifBlank {
                        "format, integrity, reopen or identity check"
                    }
            }
            require(
                readbackValidation.detectedGame == stableRequest.destination
            ) {
                "Published destination identity does not match " +
                    "${stableRequest.destination}."
            }
            publishedState.requireUnchanged(
                "Published destination during final verification"
            )
            sourceState.requireUnchanged(
                "Source save during final destination publication"
            )
            return exported
        } catch (failure: Throwable) {
            val ownsPublishedDestination =
                publishedCleanupState?.let { publishedState ->
                    publishedState.matchesCapturedFile() ||
                        publishedState.matchesCapturedIdentity()
                } == true
            val deleted = !ownsPublishedDestination ||
                runCatching { exported.delete() }.getOrDefault(false)
            if (!deleted) {
                throw IllegalStateException(
                    "Destination publication failed and the new destination " +
                        "could not be removed.",
                    failure
                )
            }
            throw failure
        }
    }

    companion object {
        private fun callbackRequest(
            stableRequest: Gen6ConverterRequest
        ): Gen6ConverterRequest = stableRequest.copy(
            selectedVersionExclusiveIds =
                stableRequest.selectedVersionExclusiveIds.toList()
        )

        private fun rethrowSourceValidationFailureAfterIntegrityVerification(
            sourceFile: File,
            requestedDestination: File,
            sourceState: Gen6SourceFileSnapshot,
            destinationDirectory: DestinationDirectorySnapshot,
            sourceValidationInput: ByteArray,
            sourceSnapshot: ByteArray,
            sourceValidationRequest: Gen6ConverterRequest,
            stableRequest: Gen6ConverterRequest,
            failure: Throwable
        ): Nothing {
            try {
                requireSourceDirectoryChainWithoutSymbolicLinks(sourceFile)
                sourceState.requireUnchanged(
                    "Source save during failed source validation"
                )
            } catch (sourceIntegrityFailure: Throwable) {
                sourceIntegrityFailure.addSuppressed(failure)
                throw sourceIntegrityFailure
            }
            try {
                requireRequestedDestinationDirectoryChainWithoutSymbolicLinks(
                    requestedDestination
                )
                requireRequestedDestinationAbsentAfterCallback(
                    requestedDestination,
                    "Source validator"
                )
                requireDestinationDirectoryUnchanged(destinationDirectory)
                requirePreExistingDestinationEntriesUnchanged(
                    destinationDirectory
                )
                requireNoDestinationEntriesAdded(
                    destinationDirectory,
                    "Source validator left unexpected paths."
                )
                check(sourceValidationRequest == stableRequest) {
                    "Source validator changed conversion request before failing."
                }
                check(sourceValidationInput.contentEquals(sourceSnapshot)) {
                    "Source validator changed source save bytes before failing."
                }
            } catch (callbackIntegrityFailure: Throwable) {
                callbackIntegrityFailure.addSuppressed(failure)
                throw callbackIntegrityFailure
            }
            throw failure
        }

        private fun rethrowSerializationFailureAfterIntegrityVerification(
            sourceFile: File,
            requestedDestination: File,
            sourceState: Gen6SourceFileSnapshot,
            destinationDirectory: DestinationDirectorySnapshot,
            serializationInput: ByteArray,
            sourceSnapshot: ByteArray,
            serializationRequest: Gen6ConverterRequest,
            stableRequest: Gen6ConverterRequest,
            failure: Throwable
        ): Nothing {
            try {
                requireSourceDirectoryChainWithoutSymbolicLinks(sourceFile)
                sourceState.requireUnchanged(
                    "Source save during failed destination serialization"
                )
            } catch (sourceIntegrityFailure: Throwable) {
                sourceIntegrityFailure.addSuppressed(failure)
                throw sourceIntegrityFailure
            }
            try {
                requireRequestedDestinationDirectoryChainWithoutSymbolicLinks(
                    requestedDestination
                )
                requireRequestedDestinationAbsentAfterCallback(
                    requestedDestination,
                    "Destination serializer"
                )
                requireDestinationDirectoryUnchanged(destinationDirectory)
                requirePreExistingDestinationEntriesUnchanged(
                    destinationDirectory
                )
                requireNoDestinationEntriesAdded(
                    destinationDirectory,
                    "Destination serializer left unexpected paths."
                )
                check(serializationRequest == stableRequest) {
                    "Destination serializer changed conversion request before failing."
                }
                check(serializationInput.contentEquals(sourceSnapshot)) {
                    "Destination serializer changed source save bytes before failing."
                }
            } catch (callbackIntegrityFailure: Throwable) {
                callbackIntegrityFailure.addSuppressed(failure)
                throw callbackIntegrityFailure
            }
            throw failure
        }

        private fun rethrowDestinationValidationFailureAfterIntegrityVerification(
            sourceFile: File,
            requestedDestination: File,
            sourceState: Gen6SourceFileSnapshot,
            destinationDirectory: DestinationDirectorySnapshot,
            validationInput: ByteArray,
            serializedSnapshot: ByteArray,
            validationRequest: Gen6ConverterRequest,
            stableRequest: Gen6ConverterRequest,
            destinationBytes: ByteArray,
            failure: Throwable
        ): Nothing {
            try {
                requireSourceDirectoryChainWithoutSymbolicLinks(sourceFile)
                sourceState.requireUnchanged(
                    "Source save during failed destination validation"
                )
            } catch (sourceIntegrityFailure: Throwable) {
                sourceIntegrityFailure.addSuppressed(failure)
                throw sourceIntegrityFailure
            }
            try {
                requireRequestedDestinationDirectoryChainWithoutSymbolicLinks(
                    requestedDestination
                )
                requireRequestedDestinationAbsentAfterCallback(
                    requestedDestination,
                    "Destination validator"
                )
                requireDestinationDirectoryUnchanged(destinationDirectory)
                requirePreExistingDestinationEntriesUnchanged(
                    destinationDirectory
                )
                requireNoDestinationEntriesAdded(
                    destinationDirectory,
                    "Destination validator left unexpected paths."
                )
                check(validationRequest == stableRequest) {
                    "Destination validator changed conversion request before failing."
                }
                check(validationInput.contentEquals(serializedSnapshot)) {
                    "Destination validator changed serialized save bytes " +
                        "before failing."
                }
                check(destinationBytes.contentEquals(serializedSnapshot)) {
                    "Destination serializer changed its returned bytes during " +
                        "failed validation."
                }
            } catch (callbackIntegrityFailure: Throwable) {
                callbackIntegrityFailure.addSuppressed(failure)
                throw callbackIntegrityFailure
            }
            throw failure
        }

        private fun rethrowReadbackFailureAfterIntegrityVerification(
            sourceFile: File,
            sourceState: Gen6SourceFileSnapshot,
            publishedState: Gen6SourceFileSnapshot,
            destinationDirectory: DestinationDirectorySnapshot,
            exported: File,
            readbackInput: ByteArray,
            persisted: ByteArray,
            readbackValidationRequest: Gen6ConverterRequest,
            stableRequest: Gen6ConverterRequest,
            failure: Throwable
        ): Nothing {
            try {
                requireSourceDirectoryChainWithoutSymbolicLinks(sourceFile)
                sourceState.requireUnchanged(
                    "Source save during failed published destination validation"
                )
            } catch (sourceIntegrityFailure: Throwable) {
                sourceIntegrityFailure.addSuppressed(failure)
                throw sourceIntegrityFailure
            }
            try {
                requireDestinationDirectoryChainWithoutSymbolicLinks(
                    destinationDirectory.directory
                )
                requireDestinationDirectoryUnchanged(destinationDirectory)
                requirePreExistingDestinationEntriesUnchanged(
                    destinationDirectory
                )
                requireOnlyPublishedDestinationAdded(
                    destinationDirectory,
                    exported
                )
            } catch (destinationDirectoryIntegrityFailure: Throwable) {
                destinationDirectoryIntegrityFailure.addSuppressed(failure)
                throw destinationDirectoryIntegrityFailure
            }
            try {
                publishedState.requireUnchanged(
                    "Published destination during failed readback validation"
                )
            } catch (destinationIntegrityFailure: Throwable) {
                destinationIntegrityFailure.addSuppressed(failure)
                throw destinationIntegrityFailure
            }
            try {
                check(readbackValidationRequest == stableRequest) {
                    "Published destination validator changed conversion request " +
                        "before failing."
                }
                check(readbackInput.contentEquals(persisted)) {
                    "Destination validator changed published save bytes " +
                        "before failing."
                }
            } catch (callbackIntegrityFailure: Throwable) {
                callbackIntegrityFailure.addSuppressed(failure)
                throw callbackIntegrityFailure
            }
            throw failure
        }

        private fun requireSourceDirectoryChainWithoutSymbolicLinks(
            sourceFile: File
        ) {
            val sourceParent = sourceFile.absoluteFile.parentFile
                ?: error("Source save must have a parent directory.")
            generateSequence(sourceParent) { it.parentFile }.forEach {
                    sourceDirectory ->
                check(!Files.isSymbolicLink(sourceDirectory.toPath())) {
                    "Source directory must not contain symbolic links."
                }
            }
        }

        private fun requireDestinationDirectoryChainWithoutSymbolicLinks(
            destinationDirectory: File
        ) {
            generateSequence(destinationDirectory.absoluteFile) {
                    destinationAncestor ->
                destinationAncestor.parentFile
            }.forEach { destinationAncestor ->
                check(!Files.isSymbolicLink(destinationAncestor.toPath())) {
                    "Destination directory chain became a symbolic link " +
                        "during publication."
                }
            }
        }

        private fun requireRequestedDestinationDirectoryChainWithoutSymbolicLinks(
            requestedDestination: File
        ) {
            val destinationParent = requestedDestination.absoluteFile.parentFile
                ?: error("Destination must have a parent directory.")
            generateSequence(destinationParent) { it.parentFile }.forEach {
                    destinationDirectory ->
                check(!Files.isSymbolicLink(destinationDirectory.toPath())) {
                    "Destination directory must not contain symbolic links."
                }
            }
        }

        private fun requireRequestedDestinationAbsentAfterCallback(
            requestedDestination: File,
            callbackName: String
        ) {
            val requestedPath = requestedDestination.absoluteFile.toPath()
            if (!Files.exists(requestedPath, LinkOption.NOFOLLOW_LINKS)) return

            val removed = runCatching {
                Files.deleteIfExists(requestedPath)
            }.getOrDefault(false)
            check(removed) {
                "$callbackName created the requested destination before " +
                    "publication and it could not be removed."
            }
            throw IllegalStateException(
                "$callbackName created the requested destination before publication."
            )
        }

        private fun requireDistinctRequestedDestination(
            sourceFile: File,
            requestedDestination: File
        ) {
            val sourceAbsolute = sourceFile.absoluteFile
            requireSourceDirectoryChainWithoutSymbolicLinks(sourceAbsolute)
            check(!Files.isSymbolicLink(sourceAbsolute.toPath())) {
                "Source save must not be a symbolic link."
            }
            val sourceCanonical = sourceAbsolute.canonicalFile
            val destinationAbsolute = requestedDestination.absoluteFile
            requireRequestedDestinationDirectoryChainWithoutSymbolicLinks(
                destinationAbsolute
            )
            check(!Files.isSymbolicLink(destinationAbsolute.toPath())) {
                "Requested destination must not be a symbolic link."
            }
            val destinationCanonical = destinationAbsolute.canonicalFile
            val aliasesSource =
                sourceCanonical == destinationCanonical ||
                    (
                        sourceCanonical.exists() &&
                            destinationAbsolute.exists() &&
                            Files.isSameFile(
                                sourceCanonical.toPath(),
                                destinationAbsolute.toPath()
                            )
                    )
            check(!aliasesSource) {
                "Requested destination must be distinct from the source save."
            }
            check(
                !Files.exists(
                    destinationAbsolute.toPath(),
                    LinkOption.NOFOLLOW_LINKS
                )
            ) {
                "Requested destination must not already exist."
            }
        }

        private fun rejectSourceAlias(
            sourceFile: File,
            exported: File
        ) {
            val sourceCanonical = sourceFile.canonicalFile
            val exportedAbsolute = exported.absoluteFile
            val exportedCanonical = exportedAbsolute.canonicalFile
            val aliasesSource =
                sourceCanonical == exportedCanonical ||
                    (
                        sourceCanonical.exists() &&
                            exportedCanonical.exists() &&
                            Files.isSameFile(
                                sourceCanonical.toPath(),
                                exportedCanonical.toPath()
                            )
                    )
            if (!aliasesSource) return

            if (
                Files.isSymbolicLink(exportedAbsolute.toPath()) ||
                exportedCanonical != sourceCanonical
            ) {
                runCatching {
                    Files.deleteIfExists(exportedAbsolute.toPath())
                }
            }
            throw IllegalStateException(
                "Destination publisher returned the source save instead of a new file."
            )
        }

        private data class DestinationDirectorySnapshot(
            val directory: File,
            val fileKey: Any,
            val existingPaths: Set<String>,
            val existingDescendantPaths: Set<String>,
            val existingFileKeysByPath: Map<String, Any>,
            val existingDirectoryLastModifiedByPath: Map<String, Long>,
            val existingRegularFiles: List<Gen6SourceFileSnapshot>
        )

        private fun snapshotDestinationDirectory(
            requestedDestination: File,
            sourceFile: File
        ): DestinationDirectorySnapshot {
            val parent = requestedDestination.absoluteFile.parentFile
                ?: error("Destination must have a parent directory.")
            generateSequence(parent) { it.parentFile }.forEach {
                    destinationDirectory ->
                check(!Files.isSymbolicLink(destinationDirectory.toPath())) {
                    "Destination directory must not contain symbolic links."
                }
            }
            val attributes = readAttributes(parent)
            check(attributes.isDirectory) {
                "Destination directory does not exist."
            }
            val fileKey = checkNotNull(attributes.fileKey()) {
                "Destination directory identity is unavailable."
            }
            val existingEntries = parent.listFiles()?.toList() ?: emptyList()
            val existingEntryTree = destinationEntryTree(existingEntries)
            val neighboringEntries = existingEntries.filterNot { entry ->
                normalizedPath(entry) == normalizedPath(sourceFile)
            }
            val neighboringEntryTree =
                destinationEntryTree(neighboringEntries)
            return DestinationDirectorySnapshot(
                directory = parent,
                fileKey = fileKey,
                existingPaths = existingEntries.mapTo(
                    mutableSetOf(),
                    ::normalizedPath
                ),
                existingDescendantPaths = existingEntryTree.mapTo(
                    mutableSetOf(),
                    ::normalizedPath
                ),
                existingFileKeysByPath = buildMap {
                    for (entry in neighboringEntryTree) {
                        val entryFileKey = runCatching {
                            readEntryAttributes(entry).fileKey()
                        }.getOrNull() ?: continue
                        put(normalizedPath(entry), entryFileKey)
                    }
                },
                existingDirectoryLastModifiedByPath = buildMap {
                    for (entry in neighboringEntryTree) {
                        val entryAttributes = runCatching {
                            readEntryAttributes(entry)
                        }.getOrNull() ?: continue
                        if (entryAttributes.isDirectory) {
                            put(
                                normalizedPath(entry),
                                entryAttributes.lastModifiedTime().toMillis()
                            )
                        }
                    }
                },
                existingRegularFiles = neighboringEntryTree
                    .filter { entry ->
                        entry.isFile &&
                            !Files.isSymbolicLink(entry.toPath())
                    }
                    .map(Gen6SourceFileSnapshot::capture)
            )
        }

        private fun destinationEntryTree(
            entries: List<File>
        ): List<File> = buildList {
            for (entry in entries) {
                add(entry)
                val isDirectory = !Files.isSymbolicLink(entry.toPath()) &&
                    runCatching {
                        readEntryAttributes(entry).isDirectory
                    }.getOrDefault(false)
                if (isDirectory) {
                    addAll(
                        destinationEntryTree(
                            entry.listFiles()?.toList() ?: emptyList()
                        )
                    )
                }
            }
        }

        private fun currentDestinationEntryTreePaths(
            snapshot: DestinationDirectorySnapshot
        ): Set<String> = destinationEntryTree(
            snapshot.directory.listFiles()?.toList() ?: emptyList()
        ).mapTo(mutableSetOf(), ::normalizedPath)

        private fun requireDestinationDirectoryUnchanged(
            snapshot: DestinationDirectorySnapshot
        ) {
            check(!Files.isSymbolicLink(snapshot.directory.toPath())) {
                "Destination directory became a symbolic link during publication."
            }
            val attributes = readAttributes(snapshot.directory)
            check(attributes.isDirectory && attributes.fileKey() == snapshot.fileKey) {
                "Destination directory identity changed during publication."
            }
        }

        private fun rethrowPublisherFailureAfterCleanup(
            sourceFile: File,
            requestedDestination: File,
            destinationDirectory: DestinationDirectorySnapshot,
            sourceState: Gen6SourceFileSnapshot,
            publicationInput: ByteArray,
            serializedSnapshot: ByteArray,
            failure: Throwable
        ): Nothing {
            val requestedPath = requestedDestination.absoluteFile.toPath()
            val ownsFailedDestinationEntry = runCatching {
                requireDestinationDirectoryUnchanged(destinationDirectory)
                check(
                    normalizedPath(requestedDestination) !in
                        destinationDirectory.existingPaths
                )
                check(Files.exists(requestedPath, LinkOption.NOFOLLOW_LINKS))
                val failedDestinationFileKey =
                    readEntryAttributes(requestedDestination).fileKey()
                check(failedDestinationFileKey != null)
                failedDestinationFileKey !in
                    destinationDirectory.existingFileKeysByPath.values
            }.getOrDefault(false)

            val cleanupFailure = if (ownsFailedDestinationEntry) {
                val deletionAttempt = runCatching {
                    Files.deleteIfExists(requestedPath)
                }
                if (deletionAttempt.getOrDefault(false)) {
                    null
                } else {
                    IllegalStateException(
                        "Destination publisher failed and its incomplete new " +
                            "destination could not be removed.",
                        failure
                    ).apply {
                        deletionAttempt.exceptionOrNull()?.let {
                            addSuppressed(it)
                        }
                    }
                }
            } else {
                null
            }

            try {
                requireSourceDirectoryChainWithoutSymbolicLinks(sourceFile)
                sourceState.requireUnchanged(
                    "Source save during failed destination publication"
                )
                requireDestinationDirectoryChainWithoutSymbolicLinks(
                    destinationDirectory.directory
                )
                requireDestinationDirectoryUnchanged(destinationDirectory)
                requirePreExistingDestinationEntriesUnchanged(
                    destinationDirectory
                )
                if (cleanupFailure == null) {
                    requireNoDestinationEntriesAdded(destinationDirectory)
                }
                check(publicationInput.contentEquals(serializedSnapshot)) {
                    "Destination publisher changed serialized save bytes " +
                        "before failing."
                }
            } catch (publicationIntegrityFailure: Throwable) {
                publicationIntegrityFailure.addSuppressed(
                    cleanupFailure ?: failure
                )
                throw publicationIntegrityFailure
            }
            cleanupFailure?.let { throw it }
            throw failure
        }

        private fun requireNoDestinationEntriesAdded(
            snapshot: DestinationDirectorySnapshot,
            failureMessage: String =
                "Destination publisher left unexpected paths after failing."
        ) {
            val currentPaths = snapshot.directory.listFiles()
                ?.mapTo(mutableSetOf(), ::normalizedPath)
                ?: emptySet()
            val currentDescendantPaths =
                currentDestinationEntryTreePaths(snapshot)
            check(
                (currentPaths - snapshot.existingPaths).isEmpty() &&
                    (currentDescendantPaths -
                        snapshot.existingDescendantPaths).isEmpty()
            ) {
                failureMessage
            }
        }

        private fun requirePreExistingDestinationEntriesUnchanged(
            snapshot: DestinationDirectorySnapshot
        ) {
            val unchanged =
                snapshot.existingFileKeysByPath.all { (path, fileKey) ->
                    runCatching {
                        readEntryAttributes(File(path)).fileKey() == fileKey
                    }.getOrDefault(false)
                }
            check(unchanged) {
                "Pre-existing destination entry identity changed during " +
                    "publication."
            }
            snapshot.existingRegularFiles.forEach { existingFile ->
                existingFile.requireUnchanged(
                    "Pre-existing destination file during publication"
                )
            }
            val addedDescendantPaths =
                currentDestinationEntryTreePaths(snapshot) -
                    snapshot.existingDescendantPaths
            val directoryMetadataUnchanged =
                snapshot.existingDirectoryLastModifiedByPath.all {
                        (path, lastModified) ->
                    val directoryPath =
                        File(path).absoluteFile.toPath().normalize()
                    val containsUnexpectedAddition =
                        addedDescendantPaths.any { addedPath ->
                            val normalizedAddedPath =
                                File(addedPath).absoluteFile.toPath().normalize()
                            normalizedAddedPath != directoryPath &&
                                normalizedAddedPath.startsWith(directoryPath)
                        }
                    containsUnexpectedAddition || runCatching {
                        readEntryAttributes(File(path))
                            .lastModifiedTime()
                            .toMillis() == lastModified
                    }.getOrDefault(false)
                }
            check(directoryMetadataUnchanged) {
                "Pre-existing destination directory metadata changed during " +
                    "publication."
            }
        }

        private fun readAttributes(file: File): BasicFileAttributes =
            Files.readAttributes(
                file.toPath(),
                BasicFileAttributes::class.java
            )

        private fun readEntryAttributes(file: File): BasicFileAttributes =
            Files.readAttributes(
                file.toPath(),
                BasicFileAttributes::class.java,
                LinkOption.NOFOLLOW_LINKS
            )

        private fun rejectOutsideDestinationDirectory(
            requestedDestination: File,
            exported: File
        ) {
            val requestedParent = requestedDestination.absoluteFile.parentFile
                ?: error("Destination must have a parent directory.")
            val exportedParent = exported.absoluteFile.parentFile
                ?: error("Published destination must have a parent directory.")
            check(normalizedPath(exportedParent) == normalizedPath(requestedParent)) {
                "Destination publisher returned a path outside the requested directory."
            }
        }

        private fun rejectPreExistingDestination(
            exported: File,
            existingDestinationPaths: Set<String>
        ) {
            check(normalizedPath(exported) !in existingDestinationPaths) {
                "Destination publisher returned a pre-existing path instead of a new file."
            }
        }

        private fun normalizedPath(file: File): String =
            file.absoluteFile.toPath().normalize().toString()

        private fun requireOnlyPublishedDestinationAdded(
            snapshot: DestinationDirectorySnapshot,
            exported: File
        ) {
            val currentPaths = snapshot.directory.listFiles()
                ?.mapTo(mutableSetOf(), ::normalizedPath)
                ?: emptySet()
            val addedPaths = currentPaths - snapshot.existingPaths
            val addedDescendantPaths =
                currentDestinationEntryTreePaths(snapshot) -
                    snapshot.existingDescendantPaths
            val expectedPublishedPath = setOf(normalizedPath(exported))
            check(
                addedPaths == expectedPublishedPath &&
                    addedDescendantPaths == expectedPublishedPath
            ) {
                "Destination publisher created unexpected paths."
            }
        }

        private fun rejectPreExistingDestinationIdentity(
            exported: File,
            existingFileKeys: Collection<Any>
        ) {
            val exportedFileKey = checkNotNull(
                readAttributes(exported).fileKey()
            ) {
                "Published destination identity is unavailable."
            }
            check(exportedFileKey !in existingFileKeys) {
                "Destination publisher returned an alias of a " +
                    "pre-existing file identity."
            }
        }

        private fun rejectLinkedDestination(exported: File) {
            val exportedPath = exported.absoluteFile.toPath()
            if (!Files.isSymbolicLink(exportedPath)) return

            val removed = runCatching {
                Files.deleteIfExists(exportedPath)
            }.getOrDefault(false)
            check(removed) {
                "Destination publisher returned a symbolic link that could not be removed."
            }
            throw IllegalStateException(
                "Destination publisher returned a symbolic link instead of a new file."
            )
        }

        private val defaultPublisher = Gen6DestinationPublisher {
                sourceFile,
                requestedDestination,
                destinationBytes,
                expectedGame ->
            Gen6SaveExportGuard.exportNew(
                sourceFile = sourceFile,
                requestedDestination = requestedDestination,
                destinationBytes = destinationBytes,
                expectedGame = expectedGame
            )
        }

        private fun writerResolver(
            writer: Gen6DestinationSaveWriter
        ): (Gen6ConverterRequest) -> Gen6DestinationSaveWriter = { request ->
            require(writer.sourceGame == request.source) {
                "Destination writer source does not match the conversion request."
            }
            require(writer.destinationGame == request.destination) {
                "Destination writer game does not match the conversion request."
            }
            writer
        }
    }
}
