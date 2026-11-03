package com.example.pokemonsavemanager

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6SaveConversionServiceTest {
    @Test
    fun conversionSnapshotsExclusiveSelectionBeforeWriterCallbacks() {
        val directory = Files.createTempDirectory(
            "gen6-conversion-selection-snapshot"
        ).toFile()
        try {
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val destination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val selectedIds = mutableListOf("lotad-as")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                includeVersionExclusives = true,
                selectedVersionExclusiveIds = selectedIds
            )
            val observedSelections = mutableListOf<List<String>>()
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    observedSelections +=
                        request.selectedVersionExclusiveIds.toList()
                    selectedIds.clear()
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    observedSelections +=
                        request.selectedVersionExclusiveIds.toList()
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    observedSelections +=
                        request.selectedVersionExclusiveIds.toList()
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, destinationBytes, _ ->
                requested.writeBytes(destinationBytes)
                requested
            }

            Gen6SaveConversionService(writer, publisher).convert(
                sourceFile,
                destination,
                request
            )

            assertTrue(selectedIds.isEmpty())
            assertEquals(4, observedSelections.size)
            assertTrue(
                observedSelections.all { it == listOf("lotad-as") }
            )
            assertArrayEquals(byteArrayOf(7, 8, 9), destination.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun writerCannotChangeSelectionAcrossCallbackBoundaries() {
        for (mutationCall in 1..4) {
            val directory = Files.createTempDirectory(
                "gen6-writer-request-$mutationCall"
            ).toFile()
            try {
                val sourceBytes = byteArrayOf(1, 2, 3)
                val sourceFile = File(
                    directory,
                    "Pokemon_Omega_Ruby.sav"
                ).apply { writeBytes(sourceBytes) }
                val destination = File(
                    directory,
                    "Pokemon_Alpha_Sapphire_Converted.sav"
                )
                val selectedIds =
                    mutableListOf("exclusive-a", "exclusive-b")
                val request = Gen6ConverterRequest(
                    Gen6ConverterGame.OMEGA_RUBY,
                    Gen6ConverterGame.ALPHA_SAPPHIRE,
                    includeVersionExclusives = true,
                    selectedVersionExclusiveIds = selectedIds
                )
                var callbackCalls = 0
                fun observe(route: Gen6ConverterRequest) {
                    callbackCalls++
                    if (callbackCalls == mutationCall) {
                        (route.selectedVersionExclusiveIds
                            as MutableList<String>).clear()
                    }
                }
                val writer = object : Gen6DestinationSaveWriter {
                    override val sourceGame = request.source
                    override val destinationGame = request.destination

                    override fun validateSource(
                        sourceBytes: ByteArray,
                        request: Gen6ConverterRequest
                    ): Gen6DestinationValidation {
                        observe(request)
                        return Gen6DestinationValidation(
                            true,
                            true,
                            true,
                            detectedGame = request.source
                        )
                    }

                    override fun serialize(
                        sourceBytes: ByteArray,
                        request: Gen6ConverterRequest
                    ): ByteArray {
                        observe(request)
                        return byteArrayOf(7, 8, 9)
                    }

                    override fun validateDestination(
                        destinationBytes: ByteArray,
                        request: Gen6ConverterRequest
                    ): Gen6DestinationValidation {
                        observe(request)
                        return Gen6DestinationValidation(
                            true,
                            true,
                            true,
                            detectedGame = request.destination
                        )
                    }
                }
                val publisher = Gen6DestinationPublisher {
                        _, requested, destinationBytes, _ ->
                    requested.writeBytes(destinationBytes)
                    requested
                }

                val failure = assertThrows(
                    IllegalStateException::class.java
                ) {
                    Gen6SaveConversionService(writer, publisher).convert(
                        sourceFile,
                        destination,
                        request
                    )
                }

                assertTrue(
                    failure.message!!.contains(
                        "changed conversion request"
                    )
                )
                assertEquals(mutationCall, callbackCalls)
                assertFalse(destination.exists())
                assertEquals(
                    listOf("exclusive-a", "exclusive-b"),
                    selectedIds
                )
                assertArrayEquals(sourceBytes, sourceFile.readBytes())
            } finally {
                directory.deleteRecursively()
            }
        }
    }

    @Test
    fun serializerAliasMutationDuringValidationFailsBeforePublication() {
        val directory = Files.createTempDirectory(
            "gen6-serializer-alias-mutation"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val serializedBytes = byteArrayOf(7, 8, 9)
            val sourceFile = File(
                directory,
                "Pokemon_Omega_Ruby.sav"
            ).apply { writeBytes(sourceBytes) }
            val destination = File(
                directory,
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true,
                    true,
                    true,
                    detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = serializedBytes

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    serializedBytes[0] = 0
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            var published = false
            val publisher = Gen6DestinationPublisher {
                    _, requested, destinationBytes, _ ->
                published = true
                requested.writeBytes(destinationBytes)
                requested
            }

            val failure = assertThrows(
                IllegalStateException::class.java
            ) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    destination,
                    request
                )
            }

            assertEquals(
                "Destination serializer changed its returned bytes " +
                    "during validation.",
                failure.message
            )
            assertFalse(published)
            assertFalse(destination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertArrayEquals(byteArrayOf(0, 8, 9), serializedBytes)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun validatorMutationFailsClosedWithoutPublishingOrChangingSource() {
        val directory = Files.createTempDirectory("gen6-conversion-validator").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination = File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = Gen6ConverterGame.OMEGA_RUBY
                override val destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation = Gen6DestinationValidation(
                    formatValid = true,
                    integrityValid = true,
                    reopenValid = true,
                    detectedGame = Gen6ConverterGame.OMEGA_RUBY
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = byteArrayOf(7, 8, 9)

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationBytes[0] = 0
                    return Gen6DestinationValidation(
                        formatValid = true,
                        integrityValid = true,
                        reopenValid = true,
                        detectedGame = Gen6ConverterGame.ALPHA_SAPPHIRE
                    )
                }
            }

            assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedPublisherUnexpectedPathPrioritizesDirectoryIntegrity() {
        val directory = Files.createTempDirectory(
            "gen6-conversion-publisher-throw"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sidecarBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val sidecar = File(directory, "publisher-sidecar.tmp")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val publisherFailure =
                IllegalStateException("Publisher failed after write.")
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                requested.writeBytes(bytes)
                sidecar.writeBytes(sidecarBytes)
                throw publisherFailure
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination publisher left unexpected paths after failing"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === publisherFailure)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sidecarBytes, sidecar.readBytes())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publisherFailureAfterPartialWriteRemovesIncompleteDestination() {
        val directory = Files.createTempDirectory(
            "gen6-conversion-publisher-partial-throw"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                requested.writeBytes(bytes.copyOf(2))
                throw IllegalStateException("Publisher failed during write.")
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(failure.message!!.contains("Publisher failed during write"))
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertFalse(requestedDestination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publisherFailureAfterCreatingDestinationDirectoryRemovesEmptyEntry() {
        val directory = Files.createTempDirectory(
            "gen6-conversion-publisher-directory-throw"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, _, _ ->
                check(requested.mkdir())
                throw IllegalStateException("Publisher failed after directory.")
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains("Publisher failed after directory")
            )
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertFalse(requestedDestination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publisherFailurePreservesNonEmptyDirectoryCleanupException() {
        val directory = Files.createTempDirectory(
            "gen6-conversion-publisher-nonempty-directory-throw"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val incompleteChild =
                File(requestedDestination, "incomplete.tmp")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisherFailure =
                IllegalStateException("Publisher failed after directory.")
            val publisher = Gen6DestinationPublisher {
                    _, requested, _, _ ->
                check(requested.mkdir())
                File(requested, incompleteChild.name).writeBytes(
                    byteArrayOf(10, 11, 12)
                )
                throw publisherFailure
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "incomplete new destination could not be removed"
                )
            )
            assertTrue(failure.cause === publisherFailure)
            assertEquals(1, failure.suppressed.size)
            assertTrue(
                failure.suppressed.single() is
                    java.nio.file.DirectoryNotEmptyException
            )
            assertTrue(requestedDestination.isDirectory)
            assertArrayEquals(
                byteArrayOf(10, 11, 12),
                incompleteChild.readBytes()
            )
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun successfulPublisherDestinationAncestorSymlinkRollsBackBeforeReadback() {
        val directory =
            Files.createTempDirectory("gen6-published-destination-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val destinationRoot = File(directory, "Destination_Root").apply {
                check(mkdir())
            }
            val destinationDirectory =
                File(destinationRoot, "Nested_Destination").apply {
                    check(mkdir())
                }
            val movedDestinationRoot =
                File(directory, "Moved_Destination_Root")
            val requestedDestination = File(
                destinationDirectory,
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var destinationValidationCalls = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCalls += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                Files.move(
                    destinationRoot.toPath(),
                    movedDestinationRoot.toPath()
                )
                Files.createSymbolicLink(
                    destinationRoot.toPath(),
                    movedDestinationRoot.absoluteFile.toPath()
                )
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination directory chain became a symbolic link " +
                        "during publication"
                )
            )
            assertEquals(1, destinationValidationCalls)
            assertEquals(1, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(destinationRoot.toPath()))
            assertFalse(
                Files.isSymbolicLink(destinationDirectory.toPath())
            )
            assertFalse(requestedDestination.exists())
            val movedDestinationFile = File(
                File(movedDestinationRoot, "Nested_Destination"),
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            assertFalse(movedDestinationFile.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun successfulPublisherSourceAncestorSymlinkRollsBackBeforeReadback() {
        val directory =
            Files.createTempDirectory("gen6-published-source-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceRoot = File(directory, "Source_Root").apply {
                check(mkdir())
            }
            val sourceDirectory = File(sourceRoot, "Nested_Source").apply {
                check(mkdir())
            }
            val sourceFile =
                File(sourceDirectory, "Pokemon_Omega_Ruby.sav").apply {
                    writeBytes(sourceBytes)
                }
            val movedSourceRoot = File(directory, "Moved_Source_Root")
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var destinationValidationCalls = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCalls += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                Files.move(
                    sourceRoot.toPath(),
                    movedSourceRoot.toPath()
                )
                Files.createSymbolicLink(
                    sourceRoot.toPath(),
                    movedSourceRoot.absoluteFile.toPath()
                )
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source directory must not contain symbolic links"
                )
            )
            assertEquals(1, destinationValidationCalls)
            assertEquals(1, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(sourceRoot.toPath()))
            assertFalse(Files.isSymbolicLink(sourceFile.parentFile.toPath()))
            assertFalse(requestedDestination.exists())
            val movedSourceFile = File(
                File(movedSourceRoot, "Nested_Source"),
                "Pokemon_Omega_Ruby.sav"
            )
            assertArrayEquals(sourceBytes, movedSourceFile.readBytes())
            assertTrue(
                movedSourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publisherFailurePrioritizesDestinationAncestorSymlinkAfterCleanup() {
        val directory =
            Files.createTempDirectory("gen6-publisher-destination-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val destinationRoot = File(directory, "Destination_Root").apply {
                check(mkdir())
            }
            val destinationDirectory =
                File(destinationRoot, "Nested_Destination").apply {
                    check(mkdir())
                }
            val movedDestinationRoot =
                File(directory, "Moved_Destination_Root")
            val requestedDestination = File(
                destinationDirectory,
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val publisherFailure =
                IllegalStateException("destination publisher interrupted")
            var destinationValidationCalls = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCalls += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                Files.move(
                    destinationRoot.toPath(),
                    movedDestinationRoot.toPath()
                )
                Files.createSymbolicLink(
                    destinationRoot.toPath(),
                    movedDestinationRoot.absoluteFile.toPath()
                )
                throw publisherFailure
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination directory chain became a symbolic link " +
                        "during publication"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === publisherFailure)
            assertEquals(1, destinationValidationCalls)
            assertEquals(1, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(destinationRoot.toPath()))
            assertFalse(
                Files.isSymbolicLink(destinationDirectory.toPath())
            )
            assertFalse(requestedDestination.exists())
            val movedDestinationFile = File(
                File(movedDestinationRoot, "Nested_Destination"),
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            assertFalse(movedDestinationFile.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publisherFailurePrioritizesSourceAncestorSymlinkAfterCleanup() {
        val directory =
            Files.createTempDirectory("gen6-publisher-source-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceRoot = File(directory, "Source_Root").apply {
                check(mkdir())
            }
            val sourceDirectory = File(sourceRoot, "Nested_Source").apply {
                check(mkdir())
            }
            val sourceFile =
                File(sourceDirectory, "Pokemon_Omega_Ruby.sav").apply {
                    writeBytes(sourceBytes)
                }
            val movedSourceRoot = File(directory, "Moved_Source_Root")
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val publisherFailure =
                IllegalStateException("destination publisher interrupted")
            var destinationValidationCalls = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCalls += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                Files.move(
                    sourceRoot.toPath(),
                    movedSourceRoot.toPath()
                )
                Files.createSymbolicLink(
                    sourceRoot.toPath(),
                    movedSourceRoot.absoluteFile.toPath()
                )
                throw publisherFailure
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source directory must not contain symbolic links"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === publisherFailure)
            assertEquals(1, destinationValidationCalls)
            assertEquals(1, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(sourceRoot.toPath()))
            assertFalse(Files.isSymbolicLink(sourceFile.parentFile.toPath()))
            assertFalse(requestedDestination.exists())
            val movedSourceFile = File(
                File(movedSourceRoot, "Nested_Source"),
                "Pokemon_Omega_Ruby.sav"
            )
            assertArrayEquals(sourceBytes, movedSourceFile.readBytes())
            assertTrue(
                movedSourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publisherFailureAfterCreatingSourceSymlinkRemovesOnlyNewLink() {
        val directory = Files.createTempDirectory(
            "gen6-conversion-publisher-throw-link"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisher = Gen6DestinationPublisher {
                    source, requested, _, _ ->
                Files.createSymbolicLink(
                    requested.toPath(),
                    source.absoluteFile.toPath()
                )
                throw IllegalStateException("Publisher failed after link.")
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(failure.message!!.contains("Publisher failed after link"))
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertFalse(Files.isSymbolicLink(requestedDestination.toPath()))
            assertFalse(requestedDestination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publisherMutationRemovesIncompleteDestination() {
        val directory = Files.createTempDirectory(
            "gen6-conversion-publisher-mutation"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val publishedBytes = byteArrayOf(0, 8, 9)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                bytes[0] = publishedBytes[0]
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "publisher changed serialized save bytes"
                )
            )
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertFalse(requestedDestination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun successfulPublisherSourceMutationStopsBeforeReadbackValidation() {
        val directory = Files.createTempDirectory(
            "gen6-conversion-publisher-source-mutation"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val changedSourceLastModified =
                originalSourceLastModified - 120_000L
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var destinationValidationCalls = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCalls += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    source, requested, bytes, _ ->
                requested.writeBytes(bytes)
                Files.setLastModifiedTime(
                    source.toPath(),
                    FileTime.fromMillis(changedSourceLastModified)
                )
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source save during destination publication metadata changed"
                )
            )
            assertEquals(1, destinationValidationCalls)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == changedSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun readbackValidatorNeighborMutationIsRejectedAndDestinationRemoved() {
        val directory = Files.createTempDirectory(
            "gen6-conversion-readback-neighbor-mutation"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val neighborBytes = byteArrayOf(4, 5, 6)
            val neighbor = File(directory, "Existing_Backup.sav").apply {
                writeBytes(neighborBytes)
            }
            val originalNeighborLastModified = neighbor.lastModified()
            val changedNeighborLastModified =
                originalNeighborLastModified - 120_000L
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var destinationValidationCalls = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCalls += 1
                    if (destinationValidationCalls == 2) {
                        Files.setLastModifiedTime(
                            neighbor.toPath(),
                            FileTime.fromMillis(
                                changedNeighborLastModified
                            )
                        )
                    }
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Pre-existing destination file during publication " +
                        "metadata changed"
                )
            )
            assertEquals(2, destinationValidationCalls)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
            assertArrayEquals(neighborBytes, neighbor.readBytes())
            assertTrue(
                neighbor.lastModified() == changedNeighborLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun returnedReadbackRejectsSourceAncestorSymlinkAndRollsBack() {
        val directory =
            Files.createTempDirectory("gen6-returned-readback-source-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceRoot = File(directory, "Source_Root").apply {
                check(mkdir())
            }
            val sourceDirectory = File(sourceRoot, "Nested_Source").apply {
                check(mkdir())
            }
            val sourceFile =
                File(sourceDirectory, "Pokemon_Omega_Ruby.sav").apply {
                    writeBytes(sourceBytes)
                }
            val movedSourceRoot = File(directory, "Moved_Source_Root")
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var destinationValidationCalls = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCalls += 1
                    if (destinationValidationCalls == 2) {
                        Files.move(
                            sourceRoot.toPath(),
                            movedSourceRoot.toPath()
                        )
                        Files.createSymbolicLink(
                            sourceRoot.toPath(),
                            movedSourceRoot.absoluteFile.toPath()
                        )
                    }
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source directory must not contain symbolic links"
                )
            )
            assertEquals(2, destinationValidationCalls)
            assertEquals(1, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(sourceRoot.toPath()))
            assertFalse(Files.isSymbolicLink(sourceFile.parentFile.toPath()))
            assertFalse(requestedDestination.exists())
            val movedSourceFile = File(
                File(movedSourceRoot, "Nested_Source"),
                "Pokemon_Omega_Ruby.sav"
            )
            assertArrayEquals(sourceBytes, movedSourceFile.readBytes())
            assertTrue(
                movedSourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownReadbackPrioritizesSourceAncestorSymlinkAndRollsBack() {
        val directory =
            Files.createTempDirectory("gen6-readback-source-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceRoot = File(directory, "Source_Root").apply {
                check(mkdir())
            }
            val sourceDirectory = File(sourceRoot, "Nested_Source").apply {
                check(mkdir())
            }
            val sourceFile =
                File(sourceDirectory, "Pokemon_Omega_Ruby.sav").apply {
                    writeBytes(sourceBytes)
                }
            val movedSourceRoot = File(directory, "Moved_Source_Root")
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val readbackFailure =
                IllegalStateException("readback validation interrupted")
            var destinationValidationCalls = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCalls += 1
                    if (destinationValidationCalls == 2) {
                        Files.move(
                            sourceRoot.toPath(),
                            movedSourceRoot.toPath()
                        )
                        Files.createSymbolicLink(
                            sourceRoot.toPath(),
                            movedSourceRoot.absoluteFile.toPath()
                        )
                        throw readbackFailure
                    }
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source directory must not contain symbolic links"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === readbackFailure)
            assertEquals(2, destinationValidationCalls)
            assertEquals(1, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(sourceRoot.toPath()))
            assertFalse(Files.isSymbolicLink(sourceFile.parentFile.toPath()))
            assertFalse(requestedDestination.exists())
            val movedSourceFile = File(
                File(movedSourceRoot, "Nested_Source"),
                "Pokemon_Omega_Ruby.sav"
            )
            assertArrayEquals(sourceBytes, movedSourceFile.readBytes())
            assertTrue(
                movedSourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun successfulReadbackDestinationAncestorSymlinkRollsBack() {
        val directory =
            Files.createTempDirectory("gen6-successful-readback-destination-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val destinationRoot = File(directory, "Destination_Root").apply {
                check(mkdir())
            }
            val destinationDirectory =
                File(destinationRoot, "Nested_Destination").apply {
                    check(mkdir())
                }
            val movedDestinationRoot =
                File(directory, "Moved_Destination_Root")
            val requestedDestination = File(
                destinationDirectory,
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var destinationValidationCalls = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCalls += 1
                    if (destinationValidationCalls == 2) {
                        Files.move(
                            destinationRoot.toPath(),
                            movedDestinationRoot.toPath()
                        )
                        Files.createSymbolicLink(
                            destinationRoot.toPath(),
                            movedDestinationRoot.absoluteFile.toPath()
                        )
                    }
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination directory chain became a symbolic link " +
                        "during publication"
                )
            )
            assertTrue(failure.suppressed.isEmpty())
            assertEquals(2, destinationValidationCalls)
            assertEquals(1, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(destinationRoot.toPath()))
            assertFalse(
                Files.isSymbolicLink(destinationDirectory.toPath())
            )
            assertFalse(requestedDestination.exists())
            val movedDestinationFile = File(
                File(movedDestinationRoot, "Nested_Destination"),
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            assertFalse(movedDestinationFile.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownReadbackPrioritizesDestinationAncestorSymlinkAndRollsBack() {
        val directory =
            Files.createTempDirectory("gen6-readback-destination-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val destinationRoot = File(directory, "Destination_Root").apply {
                check(mkdir())
            }
            val destinationDirectory =
                File(destinationRoot, "Nested_Destination").apply {
                    check(mkdir())
                }
            val movedDestinationRoot =
                File(directory, "Moved_Destination_Root")
            val requestedDestination = File(
                destinationDirectory,
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val readbackFailure =
                IllegalStateException("readback validation interrupted")
            var destinationValidationCalls = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCalls += 1
                    if (destinationValidationCalls == 2) {
                        Files.move(
                            destinationRoot.toPath(),
                            movedDestinationRoot.toPath()
                        )
                        Files.createSymbolicLink(
                            destinationRoot.toPath(),
                            movedDestinationRoot.absoluteFile.toPath()
                        )
                        throw readbackFailure
                    }
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination directory chain became a symbolic link " +
                        "during publication"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === readbackFailure)
            assertEquals(2, destinationValidationCalls)
            assertEquals(1, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(destinationRoot.toPath()))
            assertFalse(
                Files.isSymbolicLink(destinationDirectory.toPath())
            )
            assertFalse(requestedDestination.exists())
            val movedDestinationFile = File(
                File(movedDestinationRoot, "Nested_Destination"),
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            assertFalse(movedDestinationFile.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownReadbackNeighborMutationPrioritizesNeighborIntegrity() {
        val directory = Files.createTempDirectory(
            "gen6-conversion-thrown-readback-neighbor-mutation"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val neighborBytes = byteArrayOf(4, 5, 6)
            val neighbor = File(directory, "Existing_Backup.sav").apply {
                writeBytes(neighborBytes)
            }
            val originalNeighborLastModified = neighbor.lastModified()
            val changedNeighborLastModified =
                originalNeighborLastModified - 120_000L
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val readbackFailure =
                IllegalStateException("readback validation interrupted")
            var destinationValidationCalls = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCalls += 1
                    if (destinationValidationCalls == 2) {
                        Files.setLastModifiedTime(
                            neighbor.toPath(),
                            FileTime.fromMillis(
                                changedNeighborLastModified
                            )
                        )
                        throw readbackFailure
                    }
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Pre-existing destination file during publication " +
                        "metadata changed"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === readbackFailure)
            assertEquals(2, destinationValidationCalls)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
            assertArrayEquals(neighborBytes, neighbor.readBytes())
            assertTrue(
                neighbor.lastModified() == changedNeighborLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun readbackValidatorUnexpectedPathIsRejectedAndDestinationRemoved() {
        val directory = Files.createTempDirectory(
            "gen6-conversion-readback-unexpected-path"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val unexpectedBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val unexpectedEntry =
                File(directory, "Unexpected_Readback_Output.tmp")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var destinationValidationCalls = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCalls += 1
                    if (destinationValidationCalls == 2) {
                        unexpectedEntry.writeBytes(unexpectedBytes)
                    }
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination publisher created unexpected paths"
                )
            )
            assertEquals(2, destinationValidationCalls)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
            assertArrayEquals(unexpectedBytes, unexpectedEntry.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun invalidOrWrongGameSourceFailsBeforeSerialization() {
        val request = Gen6ConverterRequest(
            Gen6ConverterGame.OMEGA_RUBY,
            Gen6ConverterGame.ALPHA_SAPPHIRE
        )
        val invalidResults = listOf(
            Gen6DestinationValidation(false, true, true, request.source),
            Gen6DestinationValidation(true, false, true, request.source),
            Gen6DestinationValidation(true, true, false, request.source),
            Gen6DestinationValidation(true, true, true),
            Gen6DestinationValidation(true, true, true, request.destination)
        )

        invalidResults.forEachIndexed { index, validation ->
            val directory = Files.createTempDirectory("gen6-invalid-source-$index").toFile()
            try {
                val sourceBytes = byteArrayOf(1, 2, 3)
                val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                    writeBytes(sourceBytes)
                }
                val requestedDestination =
                    File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
                var serialized = false
                var destinationValidated = false
                val writer = object : Gen6DestinationSaveWriter {
                    override val sourceGame = request.source
                    override val destinationGame = request.destination

                    override fun validateSource(
                        sourceBytes: ByteArray,
                        request: Gen6ConverterRequest
                    ): Gen6DestinationValidation = validation

                    override fun serialize(
                        sourceBytes: ByteArray,
                        request: Gen6ConverterRequest
                    ): ByteArray {
                        serialized = true
                        return byteArrayOf(7, 8, 9)
                    }

                    override fun validateDestination(
                        destinationBytes: ByteArray,
                        request: Gen6ConverterRequest
                    ): Gen6DestinationValidation {
                        destinationValidated = true
                        return Gen6DestinationValidation(
                            true, true, true, detectedGame = request.destination
                        )
                    }
                }

                assertThrows(IllegalArgumentException::class.java) {
                    Gen6SaveConversionService(writer).convert(
                        sourceFile,
                        requestedDestination,
                        request
                    )
                }
                assertFalse(serialized)
                assertFalse(destinationValidated)
                assertFalse(requestedDestination.exists())
                assertArrayEquals(sourceBytes, sourceFile.readBytes())
            } finally {
                directory.deleteRecursively()
            }
        }
    }

    @Test
    fun sourceValidatorMutationFailsBeforeSerialization() {
        val directory = Files.createTempDirectory("gen6-source-validator").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination = File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var serialized = false
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    sourceBytes[0] = 99
                    return Gen6DestinationValidation(
                        true, true, true, detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    serialized = true
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }

            assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }
            assertFalse(serialized)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun sourceValidatorFileMutationFailsBeforeSerialization() {
        val directory =
            Files.createTempDirectory("gen6-source-validator-file-mutation").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val changedLastModified = originalLastModified - 120_000L
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var serialized = false
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    Files.setLastModifiedTime(
                        sourceFile.toPath(),
                        FileTime.fromMillis(changedLastModified)
                    )
                    return Gen6DestinationValidation(
                        true, true, true, detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    serialized = true
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(failure.message!!.contains("metadata changed"))
            assertFalse(serialized)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() != originalLastModified)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun sourceSerializerMutationFailsBeforePublication() {
        val directory =
            Files.createTempDirectory("gen6-source-serializer-mutation").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val destinationBytes = byteArrayOf(7, 8, 9)
            var published = false
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    sourceBytes[0] = 99
                    return destinationBytes.copyOf()
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                published = true
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "serializer changed source save bytes"
                )
            )
            assertFalse(published)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publisherReturningUnrelatedSymbolicLinkIsRejectedAndRemoved() {
        val directory =
            Files.createTempDirectory("gen6-publisher-linked-destination").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val backingFile = File(directory, "publisher-backing.sav").apply {
                writeBytes(destinationBytes)
            }
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, _, _ ->
                Files.createSymbolicLink(
                    requested.toPath(),
                    backingFile.toPath()
                )
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(failure.message!!.contains("symbolic link"))
            assertFalse(Files.isSymbolicLink(requestedDestination.toPath()))
            assertFalse(requestedDestination.exists())
            assertTrue(backingFile.exists())
            assertArrayEquals(destinationBytes, backingFile.readBytes())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publisherCreatingExtraDestinationPathIsRejectedAndRemovesPublishedFile() {
        val directory = Files.createTempDirectory(
            "gen6-publisher-extra-path"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val extraBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val destinationDirectory = File(directory, "destination").apply {
                check(mkdir())
            }
            val requestedDestination = File(
                destinationDirectory,
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            val extraDestination =
                File(destinationDirectory, "unexpected-sidecar.tmp")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                requested.writeBytes(bytes)
                extraDestination.writeBytes(extraBytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(failure.message!!.contains("unexpected paths"))
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertFalse(requestedDestination.exists())
            assertArrayEquals(extraBytes, extraDestination.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publisherReplacingRequestedDirectoryIsRejected() {
        val directory = Files.createTempDirectory(
            "gen6-publisher-replaced-directory"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val destinationDirectory = File(directory, "destination").apply {
                check(mkdir())
            }
            val movedDestinationDirectory =
                File(directory, "moved-destination")
            val requestedDestination = File(
                destinationDirectory,
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                Files.move(
                    destinationDirectory.toPath(),
                    movedDestinationDirectory.toPath()
                )
                check(destinationDirectory.mkdir())
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(failure.message!!.contains("directory identity changed"))
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertArrayEquals(
                destinationBytes,
                requestedDestination.readBytes()
            )
            assertTrue(movedDestinationDirectory.isDirectory)
            assertTrue(movedDestinationDirectory.listFiles()!!.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publisherReturningFileOutsideRequestedDirectoryIsRejected() {
        val directory = Files.createTempDirectory(
            "gen6-publisher-outside-directory"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val destinationDirectory = File(directory, "destination").apply {
                check(mkdir())
            }
            val requestedDestination = File(
                destinationDirectory,
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            val siblingDirectory = File(directory, "sibling").apply {
                check(mkdir())
            }
            val siblingFile = File(siblingDirectory, "existing.sav").apply {
                writeBytes(destinationBytes)
            }
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisher = Gen6DestinationPublisher {
                    _, _, _, _ ->
                siblingFile
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(failure.message!!.contains("outside the requested directory"))
            assertArrayEquals(destinationBytes, siblingFile.readBytes())
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publisherMutatingPreExistingFileRemovesNewDestination() {
        val directory = Files.createTempDirectory(
            "gen6-publisher-mutated-existing-file"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val existingBytes = byteArrayOf(4, 5, 6)
            val changedExistingBytes = byteArrayOf(6, 5, 4)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val existingFile = File(directory, "unrelated-existing.sav").apply {
                writeBytes(existingBytes)
            }
            val originalLastModified = existingFile.lastModified()
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                requested.writeBytes(bytes)
                existingFile.writeBytes(changedExistingBytes)
                Files.setLastModifiedTime(
                    existingFile.toPath(),
                    FileTime.fromMillis(originalLastModified)
                )
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Pre-existing destination file during publication bytes changed"
                )
            )
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertArrayEquals(changedExistingBytes, existingFile.readBytes())
            assertTrue(existingFile.lastModified() == originalLastModified)
            assertFalse(requestedDestination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publisherReplacingPreExistingFileIdentityRemovesNewDestination() {
        val directory = Files.createTempDirectory(
            "gen6-publisher-replaced-existing-file"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val existingBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val existingFile = File(directory, "unrelated-existing.sav").apply {
                writeBytes(existingBytes)
            }
            val originalLastModified = existingFile.lastModified()
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                requested.writeBytes(bytes)
                val replacement = File(directory, "replacement.sav").apply {
                    writeBytes(existingBytes)
                }
                Files.setLastModifiedTime(
                    replacement.toPath(),
                    FileTime.fromMillis(originalLastModified)
                )
                Files.move(
                    replacement.toPath(),
                    existingFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
                Files.setLastModifiedTime(
                    existingFile.toPath(),
                    FileTime.fromMillis(originalLastModified)
                )
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Pre-existing destination entry identity changed"
                )
            )
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertArrayEquals(existingBytes, existingFile.readBytes())
            assertTrue(existingFile.lastModified() == originalLastModified)
            assertFalse(requestedDestination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publisherReturningHardLinkToPreExistingFileRemovesLinkAndPreservesFile() {
        val directory = Files.createTempDirectory(
            "gen6-publisher-pre-existing-hard-link"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val existingFile = File(directory, "unrelated-existing.sav").apply {
                writeBytes(destinationBytes)
            }
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, _, _ ->
                Files.createLink(requested.toPath(), existingFile.toPath())
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains("pre-existing file identity")
            )
            assertFalse(requestedDestination.exists())
            assertArrayEquals(destinationBytes, existingFile.readBytes())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publisherReturningPreExistingRegularFileIsRejectedAndPreserved() {
        val directory = Files.createTempDirectory(
            "gen6-publisher-pre-existing-file"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val existingFile = File(directory, "unrelated-existing.sav").apply {
                writeBytes(destinationBytes)
            }
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisher = Gen6DestinationPublisher {
                    _, _, _, _ ->
                existingFile
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(failure.message!!.contains("pre-existing path"))
            assertArrayEquals(destinationBytes, existingFile.readBytes())
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publisherReturningUncapturableNewPathIsPreserved() {
        val directory = Files.createTempDirectory(
            "gen6-publisher-uncapturable-path"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val returnedDirectory = File(directory, "returned-directory")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = byteArrayOf(7, 8, 9)

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisher = Gen6DestinationPublisher {
                    _, _, _, _ ->
                returnedDirectory.apply {
                    check(mkdir())
                }
            }

            val failure = assertThrows(IllegalArgumentException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(failure.message!!.contains("regular file"))
            assertTrue(returnedDirectory.isDirectory)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun sourceAncestorSymlinkFailsBeforeWriterCallbacks() {
        val directory =
            Files.createTempDirectory("gen6-source-ancestor-symlink")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val realSourceRoot = File(directory, "Real_Source_Root").apply {
                check(mkdir())
            }
            val realSourceDirectory =
                File(realSourceRoot, "Nested_Source").apply {
                    check(mkdir())
                }
            val sourceTarget =
                File(realSourceDirectory, "Pokemon_Omega_Ruby.sav").apply {
                    writeBytes(sourceBytes)
                }
            val linkedSourceRoot = File(directory, "Linked_Source_Root")
            Files.createSymbolicLink(
                linkedSourceRoot.toPath(),
                realSourceRoot.absoluteFile.toPath()
            )
            val sourceFile = File(
                File(linkedSourceRoot, "Nested_Source"),
                "Pokemon_Omega_Ruby.sav"
            )
            val originalSourceLastModified = sourceTarget.lastModified()
            val originalSourceDirectoryLastModified =
                realSourceDirectory.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source directory must not contain symbolic links"
                )
            )
            assertEquals(0, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(linkedSourceRoot.toPath()))
            assertFalse(
                Files.isSymbolicLink(sourceFile.parentFile.toPath())
            )
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceTarget.readBytes())
            assertTrue(
                sourceTarget.lastModified() == originalSourceLastModified
            )
            assertTrue(
                realSourceDirectory.lastModified() ==
                    originalSourceDirectoryLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun sourceParentSymlinkFailsBeforeWriterCallbacks() {
        val directory =
            Files.createTempDirectory("gen6-source-parent-symlink")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val realSourceDirectory = File(directory, "Real_Source").apply {
                check(mkdir())
            }
            val sourceTarget =
                File(realSourceDirectory, "Pokemon_Omega_Ruby.sav").apply {
                    writeBytes(sourceBytes)
                }
            val linkedSourceDirectory = File(directory, "Linked_Source")
            Files.createSymbolicLink(
                linkedSourceDirectory.toPath(),
                realSourceDirectory.absoluteFile.toPath()
            )
            val sourceFile =
                File(linkedSourceDirectory, "Pokemon_Omega_Ruby.sav")
            val originalSourceLastModified = sourceTarget.lastModified()
            val originalSourceDirectoryLastModified =
                realSourceDirectory.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source directory must not contain symbolic links"
                )
            )
            assertEquals(0, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(linkedSourceDirectory.toPath()))
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceTarget.readBytes())
            assertTrue(
                sourceTarget.lastModified() == originalSourceLastModified
            )
            assertTrue(
                realSourceDirectory.lastModified() ==
                    originalSourceDirectoryLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun sourceSymlinkFailsBeforeWriterCallbacks() {
        val directory =
            Files.createTempDirectory("gen6-source-symlink")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceTarget = File(
                directory,
                "Pokemon_Omega_Ruby_Original.sav"
            ).apply {
                writeBytes(sourceBytes)
            }
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav")
            Files.createSymbolicLink(
                sourceFile.toPath(),
                sourceTarget.absoluteFile.toPath()
            )
            val originalSourceLastModified = sourceTarget.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source save must not be a symbolic link"
                )
            )
            assertEquals(0, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(sourceFile.toPath()))
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceTarget.readBytes())
            assertTrue(
                sourceTarget.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownDestinationValidationPrioritizesSourceAncestorSymlink() {
        val directory =
            Files.createTempDirectory("gen6-thrown-source-ancestor-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceRoot = File(directory, "Source_Root").apply {
                check(mkdir())
            }
            val sourceDirectory = File(sourceRoot, "Nested_Source").apply {
                check(mkdir())
            }
            val sourceFile =
                File(sourceDirectory, "Pokemon_Omega_Ruby.sav").apply {
                    writeBytes(sourceBytes)
                }
            val movedSourceRoot = File(directory, "Moved_Source_Root")
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val validatorFailure =
                IllegalStateException("destination validation interrupted")
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    Files.move(
                        sourceRoot.toPath(),
                        movedSourceRoot.toPath()
                    )
                    Files.createSymbolicLink(
                        sourceRoot.toPath(),
                        movedSourceRoot.absoluteFile.toPath()
                    )
                    throw validatorFailure
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source directory must not contain symbolic links"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === validatorFailure)
            assertEquals(3, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(sourceRoot.toPath()))
            assertFalse(Files.isSymbolicLink(sourceFile.parentFile.toPath()))
            assertFalse(requestedDestination.exists())
            val movedSourceFile = File(
                File(movedSourceRoot, "Nested_Source"),
                "Pokemon_Omega_Ruby.sav"
            )
            assertArrayEquals(sourceBytes, movedSourceFile.readBytes())
            assertTrue(
                movedSourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun returnedInvalidDestinationValidationPrioritizesSourceAncestorSymlink() {
        val directory =
            Files.createTempDirectory("gen6-invalid-validation-source-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceRoot = File(directory, "Source_Root").apply {
                check(mkdir())
            }
            val sourceDirectory = File(sourceRoot, "Nested_Source").apply {
                check(mkdir())
            }
            val sourceFile =
                File(sourceDirectory, "Pokemon_Omega_Ruby.sav").apply {
                    writeBytes(sourceBytes)
                }
            val movedSourceRoot = File(directory, "Moved_Source_Root")
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    Files.move(
                        sourceRoot.toPath(),
                        movedSourceRoot.toPath()
                    )
                    Files.createSymbolicLink(
                        sourceRoot.toPath(),
                        movedSourceRoot.absoluteFile.toPath()
                    )
                    return Gen6DestinationValidation(
                        false,
                        false,
                        false,
                        detectedGame = request.destination,
                        note = "invalid destination"
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source directory must not contain symbolic links"
                )
            )
            assertEquals(3, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(sourceRoot.toPath()))
            assertFalse(Files.isSymbolicLink(sourceDirectory.toPath()))
            assertFalse(requestedDestination.exists())
            val movedSourceFile = File(
                File(movedSourceRoot, "Nested_Source"),
                "Pokemon_Omega_Ruby.sav"
            )
            assertArrayEquals(sourceBytes, movedSourceFile.readBytes())
            assertTrue(
                movedSourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun returnedDestinationValidationRejectsUnexpectedDestinationEntry() {
        val directory =
            Files.createTempDirectory("gen6-validation-sidecar")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sidecarBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val sidecar = File(directory, "destination-validator-sidecar.tmp")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    sidecar.writeBytes(sidecarBytes)
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination validator left unexpected paths"
                )
            )
            assertEquals(3, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sidecarBytes, sidecar.readBytes())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun returnedDestinationValidationRemovesPrematureDestinationBeforePublication() {
        val directory =
            Files.createTempDirectory("gen6-validation-destination-file")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val prematureBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    requestedDestination.writeBytes(prematureBytes)
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination validator created the requested " +
                        "destination before publication"
                )
            )
            assertEquals(3, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun returnedDestinationValidationRejectsDestinationAncestorSymlinkBeforePublication() {
        val directory =
            Files.createTempDirectory("gen6-validated-destination-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val destinationRoot = File(directory, "Destination_Root").apply {
                check(mkdir())
            }
            val destinationDirectory =
                File(destinationRoot, "Nested_Destination").apply {
                    check(mkdir())
                }
            val movedDestinationRoot =
                File(directory, "Moved_Destination_Root")
            val requestedDestination = File(
                destinationDirectory,
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    Files.move(
                        destinationRoot.toPath(),
                        movedDestinationRoot.toPath()
                    )
                    Files.createSymbolicLink(
                        destinationRoot.toPath(),
                        movedDestinationRoot.absoluteFile.toPath()
                    )
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination directory must not contain symbolic links"
                )
            )
            assertEquals(3, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(destinationRoot.toPath()))
            assertFalse(
                Files.isSymbolicLink(destinationDirectory.toPath())
            )
            assertFalse(requestedDestination.exists())
            val movedDestinationDirectory =
                File(movedDestinationRoot, "Nested_Destination")
            assertTrue(movedDestinationDirectory.listFiles()!!.isEmpty())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownDestinationValidationPrioritizesNestedDirectoryMetadataMutation() {
        val directory =
            Files.createTempDirectory("gen6-thrown-validation-directory-metadata")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val existingSubdirectory = File(directory, "existing").apply {
                mkdir()
            }
            val originalDirectoryLastModified =
                existingSubdirectory.lastModified()
            val changedDirectoryLastModified =
                originalDirectoryLastModified - 10_000L
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val validatorFailure =
                IllegalStateException("destination validator interrupted")
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    Files.setLastModifiedTime(
                        existingSubdirectory.toPath(),
                        FileTime.fromMillis(changedDirectoryLastModified)
                    )
                    throw validatorFailure
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Pre-existing destination directory metadata changed"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === validatorFailure)
            assertEquals(3, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertFalse(requestedDestination.exists())
            assertEquals(
                changedDirectoryLastModified,
                existingSubdirectory.lastModified()
            )
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownDestinationValidationPrioritizesUnexpectedNestedDestinationEntry() {
        val directory =
            Files.createTempDirectory("gen6-thrown-validation-nested-sidecar")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sidecarBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val existingSubdirectory = File(directory, "existing").apply {
                mkdir()
            }
            val sidecar =
                File(existingSubdirectory, "destination-validator-sidecar.tmp")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val validatorFailure =
                IllegalStateException("destination validator interrupted")
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    sidecar.writeBytes(sidecarBytes)
                    throw validatorFailure
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination validator left unexpected paths"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === validatorFailure)
            assertEquals(3, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sidecarBytes, sidecar.readBytes())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownDestinationValidationPrioritizesUnexpectedDestinationEntry() {
        val directory =
            Files.createTempDirectory("gen6-thrown-validation-sidecar")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sidecarBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val sidecar = File(directory, "destination-validator-sidecar.tmp")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val validatorFailure =
                IllegalStateException("destination validator interrupted")
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    sidecar.writeBytes(sidecarBytes)
                    throw validatorFailure
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination validator left unexpected paths"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === validatorFailure)
            assertEquals(3, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sidecarBytes, sidecar.readBytes())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownDestinationValidationRemovesPrematureDestinationBeforeRethrow() {
        val directory =
            Files.createTempDirectory("gen6-thrown-validation-destination")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val prematureBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val validatorFailure =
                IllegalStateException("destination validator interrupted")
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    requestedDestination.writeBytes(prematureBytes)
                    throw validatorFailure
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination validator created the requested destination " +
                        "before publication"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === validatorFailure)
            assertEquals(3, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownDestinationValidationPrioritizesDestinationAncestorSymlink() {
        val directory =
            Files.createTempDirectory("gen6-thrown-destination-ancestor-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val destinationRoot = File(directory, "Destination_Root").apply {
                check(mkdir())
            }
            val destinationDirectory =
                File(destinationRoot, "Nested_Destination").apply {
                    check(mkdir())
                }
            val movedDestinationRoot =
                File(directory, "Moved_Destination_Root")
            val requestedDestination = File(
                destinationDirectory,
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val validatorFailure =
                IllegalStateException("destination validation interrupted")
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    Files.move(
                        destinationRoot.toPath(),
                        movedDestinationRoot.toPath()
                    )
                    Files.createSymbolicLink(
                        destinationRoot.toPath(),
                        movedDestinationRoot.absoluteFile.toPath()
                    )
                    throw validatorFailure
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination directory must not contain symbolic links"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === validatorFailure)
            assertEquals(3, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(destinationRoot.toPath()))
            assertFalse(
                Files.isSymbolicLink(destinationDirectory.toPath())
            )
            assertFalse(requestedDestination.exists())
            val movedDestinationDirectory =
                File(movedDestinationRoot, "Nested_Destination")
            assertTrue(movedDestinationDirectory.listFiles()!!.isEmpty())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun writerIntroducedSourceAncestorSymlinkFailsBeforePublication() {
        val directory =
            Files.createTempDirectory("gen6-writer-source-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceRoot = File(directory, "Source_Root").apply {
                check(mkdir())
            }
            val sourceDirectory = File(sourceRoot, "Nested_Source").apply {
                check(mkdir())
            }
            val sourceFile =
                File(sourceDirectory, "Pokemon_Omega_Ruby.sav").apply {
                    writeBytes(sourceBytes)
                }
            val movedSourceRoot = File(directory, "Moved_Source_Root")
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    Files.move(
                        sourceRoot.toPath(),
                        movedSourceRoot.toPath()
                    )
                    Files.createSymbolicLink(
                        sourceRoot.toPath(),
                        movedSourceRoot.absoluteFile.toPath()
                    )
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source directory must not contain symbolic links"
                )
            )
            assertEquals(3, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(sourceRoot.toPath()))
            assertFalse(Files.isSymbolicLink(sourceFile.parentFile.toPath()))
            assertFalse(requestedDestination.exists())
            val movedSourceFile = File(
                File(movedSourceRoot, "Nested_Source"),
                "Pokemon_Omega_Ruby.sav"
            )
            assertArrayEquals(sourceBytes, movedSourceFile.readBytes())
            assertTrue(
                movedSourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun writerIntroducedDestinationAncestorSymlinkFailsBeforePublication() {
        val directory =
            Files.createTempDirectory("gen6-writer-destination-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val destinationRoot =
                File(directory, "Destination_Root").apply {
                    check(mkdir())
                }
            File(destinationRoot, "Nested_Destination").apply {
                check(mkdir())
            }
            val alternateRoot =
                File(directory, "Alternate_Root").apply {
                    check(mkdir())
                }
            val alternateDestinationDirectory =
                File(alternateRoot, "Nested_Destination").apply {
                    check(mkdir())
                }
            val requestedDestination = File(
                File(destinationRoot, "Nested_Destination"),
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            val originalSourceLastModified = sourceFile.lastModified()
            val originalAlternateDirectoryLastModified =
                alternateDestinationDirectory.lastModified()
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    check(destinationRoot.deleteRecursively())
                    Files.createSymbolicLink(
                        destinationRoot.toPath(),
                        alternateRoot.absoluteFile.toPath()
                    )
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination directory must not contain symbolic links"
                )
            )
            assertEquals(3, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(destinationRoot.toPath()))
            assertFalse(
                Files.isSymbolicLink(requestedDestination.parentFile.toPath())
            )
            assertFalse(requestedDestination.exists())
            assertTrue(
                alternateDestinationDirectory.listFiles()!!.isEmpty()
            )
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
            assertTrue(
                alternateDestinationDirectory.lastModified() ==
                    originalAlternateDirectoryLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun requestedDestinationAncestorSymlinkFailsBeforeWriterCallbacks() {
        val directory =
            Files.createTempDirectory("gen6-destination-ancestor-symlink")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val realDestinationRoot =
                File(directory, "Real_Destination_Root").apply {
                    check(mkdir())
                }
            val realDestinationDirectory =
                File(realDestinationRoot, "Nested_Destination").apply {
                    check(mkdir())
                }
            val linkedDestinationRoot =
                File(directory, "Linked_Destination_Root")
            Files.createSymbolicLink(
                linkedDestinationRoot.toPath(),
                realDestinationRoot.absoluteFile.toPath()
            )
            val requestedDestination = File(
                File(linkedDestinationRoot, "Nested_Destination"),
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            val originalSourceLastModified = sourceFile.lastModified()
            val originalDestinationDirectoryLastModified =
                realDestinationDirectory.lastModified()
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination directory must not contain symbolic links"
                )
            )
            assertEquals(0, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(
                Files.isSymbolicLink(linkedDestinationRoot.toPath())
            )
            assertFalse(
                Files.isSymbolicLink(requestedDestination.parentFile.toPath())
            )
            assertFalse(requestedDestination.exists())
            assertTrue(realDestinationDirectory.listFiles()!!.isEmpty())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
            assertTrue(
                realDestinationDirectory.lastModified() ==
                    originalDestinationDirectoryLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun requestedDestinationParentSymlinkFailsBeforeWriterCallbacks() {
        val directory =
            Files.createTempDirectory("gen6-destination-parent-symlink")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val realDestinationDirectory =
                File(directory, "Real_Destination").apply {
                    check(mkdir())
                }
            val linkedDestinationDirectory =
                File(directory, "Linked_Destination")
            Files.createSymbolicLink(
                linkedDestinationDirectory.toPath(),
                realDestinationDirectory.absoluteFile.toPath()
            )
            val originalSourceLastModified = sourceFile.lastModified()
            val originalDestinationDirectoryLastModified =
                realDestinationDirectory.lastModified()
            val requestedDestination = File(
                linkedDestinationDirectory,
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination directory must not contain symbolic links"
                )
            )
            assertEquals(0, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertFalse(requestedDestination.exists())
            assertTrue(
                Files.isSymbolicLink(linkedDestinationDirectory.toPath())
            )
            assertTrue(realDestinationDirectory.listFiles()!!.isEmpty())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
            assertTrue(
                realDestinationDirectory.lastModified() ==
                    originalDestinationDirectoryLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun requestedDestinationSymlinkFailsBeforeWriterCallbacks() {
        val directory =
            Files.createTempDirectory("gen6-requested-destination-symlink")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val targetBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val symlinkTarget = File(directory, "Existing_Target.sav").apply {
                writeBytes(targetBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val originalTargetLastModified = symlinkTarget.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            Files.createSymbolicLink(
                requestedDestination.toPath(),
                symlinkTarget.absoluteFile.toPath()
            )
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Requested destination must not be a symbolic link"
                )
            )
            assertEquals(0, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(requestedDestination.toPath()))
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertArrayEquals(targetBytes, symlinkTarget.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
            assertTrue(
                symlinkTarget.lastModified() == originalTargetLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun preExistingRequestedDestinationFailsBeforeWriterCallbacks() {
        val directory =
            Files.createTempDirectory("gen6-existing-requested-destination")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav").apply {
                    writeBytes(destinationBytes)
                }
            val originalSourceLastModified = sourceFile.lastModified()
            val originalDestinationLastModified =
                requestedDestination.lastModified()
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Requested destination must not already exist"
                )
            )
            assertEquals(0, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertArrayEquals(
                destinationBytes,
                requestedDestination.readBytes()
            )
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
            assertTrue(
                requestedDestination.lastModified() ==
                    originalDestinationLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun requestedDestinationHardLinkToSourceFailsBeforeWriterCallbacks() {
        val directory =
            Files.createTempDirectory("gen6-requested-source-hard-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            Files.createLink(
                requestedDestination.toPath(),
                sourceFile.toPath()
            )
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Requested destination must be distinct from the source save"
                )
            )
            assertEquals(0, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSameFile(
                sourceFile.toPath(),
                requestedDestination.toPath()
            ))
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertArrayEquals(sourceBytes, requestedDestination.readBytes())
            assertTrue(sourceFile.lastModified() == originalLastModified)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publisherReturningSourceIsRejectedWithoutDeletingOriginal() {
        val directory =
            Files.createTempDirectory("gen6-publisher-source-alias").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = byteArrayOf(7, 8, 9)

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisher = Gen6DestinationPublisher {
                    source,
                    _,
                    _,
                    _ ->
                source
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(failure.message!!.contains("returned the source save"))
            assertTrue(sourceFile.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertFalse(requestedDestination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publishedDestinationReplacementDuringReadbackFailsClosed() {
        val directory =
            Files.createTempDirectory("gen6-published-identity").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var destinationValidationCount = 0
            val replacementAnchor =
                File(directory, "replacement-destination-anchor.sav")
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes.copyOf()

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCount += 1
                    if (destinationValidationCount == 2) {
                        val originalLastModified =
                            requestedDestination.lastModified()
                        val replacement =
                            File(directory, "replacement-destination.sav").apply {
                                writeBytes(destinationBytes)
                            }
                        Files.setLastModifiedTime(
                            replacement.toPath(),
                            FileTime.fromMillis(originalLastModified)
                        )
                        Files.createLink(
                            replacementAnchor.toPath(),
                            replacement.toPath()
                        )
                        Files.move(
                            replacement.toPath(),
                            requestedDestination.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                        Files.setLastModifiedTime(
                            requestedDestination.toPath(),
                            FileTime.fromMillis(originalLastModified)
                        )
                    }
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, serialized, _ ->
                requested.writeBytes(serialized)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(failure.message!!.contains("identity changed"))
            assertTrue(requestedDestination.exists())
            assertTrue(
                Files.isSameFile(
                    replacementAnchor.toPath(),
                    requestedDestination.toPath()
                )
            )
            assertArrayEquals(
                destinationBytes,
                requestedDestination.readBytes()
            )
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun changedPublishedBytesAreRemovedBeforeReturn() {
        val directory = Files.createTempDirectory("gen6-published-readback").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = byteArrayOf(7, 8, 9)

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisher = Gen6DestinationPublisher {
                    _,
                    requested,
                    destinationBytes,
                    _ ->
                val changed = destinationBytes.copyOf().also {
                    it[0] = (it[0].toInt() xor 1).toByte()
                }
                requested.writeBytes(changed)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "differ from the serialized candidate"
                )
            )
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun sourceReplacementDuringPublicationRemovesNewDestination() {
        val directory = Files.createTempDirectory("gen6-source-publish").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = byteArrayOf(7, 8, 9)

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisher = Gen6DestinationPublisher {
                    source,
                    requested,
                    destinationBytes,
                    _ ->
                requested.writeBytes(destinationBytes)
                val replacement = File(directory, "replacement.sav").apply {
                    writeBytes(sourceBytes)
                }
                Files.setLastModifiedTime(
                    replacement.toPath(),
                    FileTime.fromMillis(originalLastModified)
                )
                Files.move(
                    replacement.toPath(),
                    source.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
                Files.setLastModifiedTime(
                    source.toPath(),
                    FileTime.fromMillis(originalLastModified)
                )
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(failure.message!!.contains("identity changed"))
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() == originalLastModified)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun sourceMetadataMutationFailsClosedBeforePublishing() {
        val directory = Files.createTempDirectory("gen6-source-metadata").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val changedLastModified = originalLastModified - 120_000L
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var destinationValidated = false
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    Files.setLastModifiedTime(
                        sourceFile.toPath(),
                        FileTime.fromMillis(changedLastModified)
                    )
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidated = true
                    return Gen6DestinationValidation(
                        true, true, true, detectedGame = request.destination
                    )
                }
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(failure.message!!.contains("metadata changed"))
            assertFalse(destinationValidated)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() != originalLastModified)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun sourceReplacementWithMatchingBytesAndMetadataFailsClosed() {
        val directory = Files.createTempDirectory("gen6-source-identity").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    val replacement = File(directory, "replacement.sav").apply {
                        writeBytes(sourceBytes)
                    }
                    Files.setLastModifiedTime(
                        replacement.toPath(),
                        FileTime.fromMillis(originalLastModified)
                    )
                    Files.move(
                        replacement.toPath(),
                        sourceFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                    Files.setLastModifiedTime(
                        sourceFile.toPath(),
                        FileTime.fromMillis(originalLastModified)
                    )
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(failure.message!!.contains("identity changed"))
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() == originalLastModified)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedPublisherSourceMutationIsReportedAfterDestinationCleanup() {
        val directory =
            Files.createTempDirectory("gen6-failed-publisher-source").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val changedLastModified = originalLastModified - 120_000L
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = byteArrayOf(7, 8, 9)

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisherFailure =
                IllegalStateException("publisher interrupted")
            val publisher = Gen6DestinationPublisher {
                    source,
                    requested,
                    destinationBytes,
                    _ ->
                requested.writeBytes(destinationBytes)
                Files.setLastModifiedTime(
                    source.toPath(),
                    FileTime.fromMillis(changedLastModified)
                )
                throw publisherFailure
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source save during failed destination publication " +
                        "metadata changed"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === publisherFailure)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() == changedLastModified)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedPublisherNeighborMutationIsReportedAfterDestinationCleanup() {
        val directory =
            Files.createTempDirectory("gen6-failed-publisher-neighbor").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val neighborBytes = byteArrayOf(4, 5, 6)
            val neighbor = File(directory, "Existing_Backup.sav").apply {
                writeBytes(neighborBytes)
            }
            val originalNeighborLastModified = neighbor.lastModified()
            val changedNeighborLastModified =
                originalNeighborLastModified - 120_000L
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = byteArrayOf(7, 8, 9)

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisherFailure =
                IllegalStateException("publisher interrupted")
            val publisher = Gen6DestinationPublisher {
                    _,
                    requested,
                    destinationBytes,
                    _ ->
                requested.writeBytes(destinationBytes)
                Files.setLastModifiedTime(
                    neighbor.toPath(),
                    FileTime.fromMillis(changedNeighborLastModified)
                )
                throw publisherFailure
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Pre-existing destination file during publication " +
                        "metadata changed"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === publisherFailure)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertArrayEquals(neighborBytes, neighbor.readBytes())
            assertTrue(
                neighbor.lastModified() == changedNeighborLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedPublisherPreservesMovedPreExistingDestinationEntry() {
        val directory =
            Files.createTempDirectory("gen6-failed-publisher-moved-entry")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val neighborBytes = byteArrayOf(4, 5, 6)
            val neighbor = File(directory, "Existing_Backup.sav").apply {
                writeBytes(neighborBytes)
            }
            val neighborAnchor =
                File(directory, "Existing_Backup_Anchor.sav")
            Files.createLink(
                neighborAnchor.toPath(),
                neighbor.toPath()
            )
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = byteArrayOf(7, 8, 9)

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisherFailure =
                IllegalStateException("publisher interrupted")
            val publisher = Gen6DestinationPublisher {
                    _,
                    requested,
                    _,
                    _ ->
                Files.move(neighbor.toPath(), requested.toPath())
                throw publisherFailure
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Pre-existing destination entry identity changed during " +
                        "publication"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === publisherFailure)
            assertFalse(neighbor.exists())
            assertTrue(requestedDestination.exists())
            assertTrue(
                Files.isSameFile(
                    neighborAnchor.toPath(),
                    requestedDestination.toPath()
                )
            )
            assertArrayEquals(
                neighborBytes,
                requestedDestination.readBytes()
            )
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedPublisherBufferMutationIsReportedAfterDestinationCleanup() {
        val directory =
            Files.createTempDirectory("gen6-failed-publisher-buffer").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = byteArrayOf(7, 8, 9)

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisherFailure =
                IllegalStateException("publisher interrupted")
            val publisher = Gen6DestinationPublisher {
                    _,
                    requested,
                    destinationBytes,
                    _ ->
                destinationBytes[0] =
                    (destinationBytes[0].toInt() xor 1).toByte()
                requested.writeBytes(destinationBytes)
                throw publisherFailure
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination publisher changed serialized save bytes " +
                        "before failing"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === publisherFailure)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedPublisherDestinationDirectoryReplacementIsReported() {
        val root =
            Files.createTempDirectory("gen6-failed-publisher-directory").toFile()
        try {
            val sourceDirectory = File(root, "source").apply {
                check(mkdir())
            }
            val destinationDirectory = File(root, "destination").apply {
                check(mkdir())
            }
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile =
                File(sourceDirectory, "Pokemon_Omega_Ruby.sav").apply {
                    writeBytes(sourceBytes)
                }
            val requestedDestination = File(
                destinationDirectory,
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            val displacedDirectory = File(root, "displaced-destination")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = byteArrayOf(7, 8, 9)

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.destination
                )
            }
            val publisherFailure =
                IllegalStateException("publisher interrupted")
            val publisher = Gen6DestinationPublisher {
                    _, _, _, _ ->
                Files.move(
                    destinationDirectory.toPath(),
                    displacedDirectory.toPath()
                )
                check(destinationDirectory.mkdir())
                throw publisherFailure
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination directory identity changed during publication"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === publisherFailure)
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertFalse(requestedDestination.exists())
            assertTrue(destinationDirectory.isDirectory)
            assertTrue(displacedDirectory.isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun thrownSourceValidationPrioritizesUnexpectedDestinationEntry() {
        val directory =
            Files.createTempDirectory("gen6-thrown-source-sidecar")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sidecarBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val sidecar = File(directory, "source-validator-sidecar.tmp")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val validatorFailure =
                IllegalStateException("source validator interrupted")
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    sidecar.writeBytes(sidecarBytes)
                    throw validatorFailure
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = error("Serialization must not run.")

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source validator left unexpected paths"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === validatorFailure)
            assertEquals(1, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sidecarBytes, sidecar.readBytes())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownSourceValidationRemovesPrematureDestinationBeforeRethrow() {
        val directory =
            Files.createTempDirectory("gen6-thrown-source-destination-file")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val prematureBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val validatorFailure =
                IllegalStateException("source validator interrupted")
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    requestedDestination.writeBytes(prematureBytes)
                    throw validatorFailure
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = error("Serialization must not run.")

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source validator created the requested destination " +
                        "before publication"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === validatorFailure)
            assertEquals(1, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownSourceValidationPrioritizesDestinationAncestorSymlink() {
        val directory =
            Files.createTempDirectory("gen6-source-validation-destination-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val destinationRoot = File(directory, "Destination_Root").apply {
                check(mkdir())
            }
            val destinationDirectory =
                File(destinationRoot, "Nested_Destination").apply {
                    check(mkdir())
                }
            val movedDestinationRoot =
                File(directory, "Moved_Destination_Root")
            val requestedDestination = File(
                destinationDirectory,
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val validatorFailure =
                IllegalStateException("source validator interrupted")
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    Files.move(
                        destinationRoot.toPath(),
                        movedDestinationRoot.toPath()
                    )
                    Files.createSymbolicLink(
                        destinationRoot.toPath(),
                        movedDestinationRoot.absoluteFile.toPath()
                    )
                    throw validatorFailure
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = error("Serialization must not run.")

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination directory must not contain symbolic links"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === validatorFailure)
            assertEquals(1, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(destinationRoot.toPath()))
            assertFalse(
                Files.isSymbolicLink(destinationDirectory.toPath())
            )
            assertFalse(requestedDestination.exists())
            val movedDestinationDirectory =
                File(movedDestinationRoot, "Nested_Destination")
            assertTrue(movedDestinationDirectory.listFiles()!!.isEmpty())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownSourceValidationPrioritizesSourceAncestorSymlink() {
        val directory =
            Files.createTempDirectory("gen6-source-validation-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceRoot = File(directory, "Source_Root").apply {
                check(mkdir())
            }
            val sourceDirectory = File(sourceRoot, "Nested_Source").apply {
                check(mkdir())
            }
            val sourceFile =
                File(sourceDirectory, "Pokemon_Omega_Ruby.sav").apply {
                    writeBytes(sourceBytes)
                }
            val movedSourceRoot = File(directory, "Moved_Source_Root")
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val validatorFailure =
                IllegalStateException("source validator interrupted")
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    Files.move(
                        sourceRoot.toPath(),
                        movedSourceRoot.toPath()
                    )
                    Files.createSymbolicLink(
                        sourceRoot.toPath(),
                        movedSourceRoot.absoluteFile.toPath()
                    )
                    throw validatorFailure
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = error("Serialization must not run.")

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source directory must not contain symbolic links"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === validatorFailure)
            assertEquals(1, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(sourceRoot.toPath()))
            assertFalse(Files.isSymbolicLink(sourceFile.parentFile.toPath()))
            assertFalse(requestedDestination.exists())
            val movedSourceFile = File(
                File(movedSourceRoot, "Nested_Source"),
                "Pokemon_Omega_Ruby.sav"
            )
            assertArrayEquals(sourceBytes, movedSourceFile.readBytes())
            assertTrue(
                movedSourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun returnedSourceValidationRejectsUnexpectedDestinationEntry() {
        val directory =
            Files.createTempDirectory("gen6-source-validation-sidecar")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sidecarBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val sidecar = File(directory, "source-validator-sidecar.tmp")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    sidecar.writeBytes(sidecarBytes)
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = error("Serialization must not run.")

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source validator left unexpected paths"
                )
            )
            assertEquals(1, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sidecarBytes, sidecar.readBytes())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun returnedSourceValidationRemovesPrematureDestinationBeforeSerialization() {
        val directory =
            Files.createTempDirectory("gen6-source-validation-destination")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val prematureBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    requestedDestination.writeBytes(prematureBytes)
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = error("Serialization must not run.")

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source validator created the requested destination " +
                        "before publication"
                )
            )
            assertEquals(1, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun returnedSourceValidationRejectsDestinationAncestorSymlinkBeforeSerialization() {
        val directory =
            Files.createTempDirectory("gen6-returned-destination-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val destinationRoot = File(directory, "Destination_Root").apply {
                check(mkdir())
            }
            val destinationDirectory =
                File(destinationRoot, "Nested_Destination").apply {
                    check(mkdir())
                }
            val movedDestinationRoot =
                File(directory, "Moved_Destination_Root")
            val requestedDestination = File(
                destinationDirectory,
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    Files.move(
                        destinationRoot.toPath(),
                        movedDestinationRoot.toPath()
                    )
                    Files.createSymbolicLink(
                        destinationRoot.toPath(),
                        movedDestinationRoot.absoluteFile.toPath()
                    )
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = error("Serialization must not run.")

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination directory must not contain symbolic links"
                )
            )
            assertEquals(1, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(destinationRoot.toPath()))
            assertFalse(
                Files.isSymbolicLink(destinationDirectory.toPath())
            )
            assertFalse(requestedDestination.exists())
            val movedDestinationDirectory =
                File(movedDestinationRoot, "Nested_Destination")
            assertTrue(movedDestinationDirectory.listFiles()!!.isEmpty())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun returnedSourceValidationRejectsSourceAncestorSymlinkBeforeSerialization() {
        val directory =
            Files.createTempDirectory("gen6-returned-source-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceRoot = File(directory, "Source_Root").apply {
                check(mkdir())
            }
            val sourceDirectory = File(sourceRoot, "Nested_Source").apply {
                check(mkdir())
            }
            val sourceFile =
                File(sourceDirectory, "Pokemon_Omega_Ruby.sav").apply {
                    writeBytes(sourceBytes)
                }
            val movedSourceRoot = File(directory, "Moved_Source_Root")
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    Files.move(
                        sourceRoot.toPath(),
                        movedSourceRoot.toPath()
                    )
                    Files.createSymbolicLink(
                        sourceRoot.toPath(),
                        movedSourceRoot.absoluteFile.toPath()
                    )
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = error("Serialization must not run.")

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source directory must not contain symbolic links"
                )
            )
            assertEquals(1, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(sourceRoot.toPath()))
            assertFalse(Files.isSymbolicLink(sourceFile.parentFile.toPath()))
            assertFalse(requestedDestination.exists())
            val movedSourceFile = File(
                File(movedSourceRoot, "Nested_Source"),
                "Pokemon_Omega_Ruby.sav"
            )
            assertArrayEquals(sourceBytes, movedSourceFile.readBytes())
            assertTrue(
                movedSourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun sourceValidatorFailureStillVerifiesOriginalSave() {
        val directory =
            Files.createTempDirectory("gen6-source-validator-failure").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val changedLastModified = originalLastModified - 120_000L
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val validatorFailure =
                IllegalStateException("source validator interrupted")
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    Files.setLastModifiedTime(
                        sourceFile.toPath(),
                        FileTime.fromMillis(changedLastModified)
                    )
                    throw validatorFailure
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = error("Serialization must not run.")

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source save during failed source validation " +
                        "metadata changed"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === validatorFailure)
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() == changedLastModified)
            assertFalse(requestedDestination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownDestinationSerializationPrioritizesPrivateInputIntegrity() {
        val directory =
            Files.createTempDirectory("gen6-serialization-thrown-input")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val serializerFailure =
                IllegalStateException("destination serializer interrupted")
            var serializationCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    serializationCount += 1
                    sourceBytes[0] = (sourceBytes[0].toInt() xor 1).toByte()
                    throw serializerFailure
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination serializer changed source save bytes " +
                        "before failing"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === serializerFailure)
            assertEquals(1, serializationCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() == originalLastModified)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun returnedSerializationRejectsSourceAncestorSymlinkBeforeValidation() {
        val directory =
            Files.createTempDirectory("gen6-returned-serialization-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceRoot = File(directory, "Source_Root").apply {
                check(mkdir())
            }
            val sourceDirectory = File(sourceRoot, "Nested_Source").apply {
                check(mkdir())
            }
            val sourceFile =
                File(sourceDirectory, "Pokemon_Omega_Ruby.sav").apply {
                    writeBytes(sourceBytes)
                }
            val movedSourceRoot = File(directory, "Moved_Source_Root")
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    Files.move(
                        sourceRoot.toPath(),
                        movedSourceRoot.toPath()
                    )
                    Files.createSymbolicLink(
                        sourceRoot.toPath(),
                        movedSourceRoot.absoluteFile.toPath()
                    )
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source directory must not contain symbolic links"
                )
            )
            assertEquals(2, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(sourceRoot.toPath()))
            assertFalse(Files.isSymbolicLink(sourceFile.parentFile.toPath()))
            assertFalse(requestedDestination.exists())
            val movedSourceFile = File(
                File(movedSourceRoot, "Nested_Source"),
                "Pokemon_Omega_Ruby.sav"
            )
            assertArrayEquals(sourceBytes, movedSourceFile.readBytes())
            assertTrue(
                movedSourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun returnedDestinationSerializationRejectsUnexpectedDestinationEntry() {
        val directory =
            Files.createTempDirectory("gen6-serialization-sidecar")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sidecarBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val sidecar = File(directory, "serializer-sidecar.tmp")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    sidecar.writeBytes(sidecarBytes)
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination serializer left unexpected paths"
                )
            )
            assertEquals(2, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sidecarBytes, sidecar.readBytes())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun returnedDestinationSerializationRemovesPrematureDestinationBeforeValidation() {
        val directory =
            Files.createTempDirectory("gen6-serialization-destination-file")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val prematureBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    requestedDestination.writeBytes(prematureBytes)
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination serializer created the requested " +
                        "destination before publication"
                )
            )
            assertEquals(2, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun returnedDestinationSerializationRejectsDestinationAncestorSymlinkBeforeValidation() {
        val directory =
            Files.createTempDirectory("gen6-serialized-destination-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val destinationRoot = File(directory, "Destination_Root").apply {
                check(mkdir())
            }
            val destinationDirectory =
                File(destinationRoot, "Nested_Destination").apply {
                    check(mkdir())
                }
            val movedDestinationRoot =
                File(directory, "Moved_Destination_Root")
            val requestedDestination = File(
                destinationDirectory,
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    Files.move(
                        destinationRoot.toPath(),
                        movedDestinationRoot.toPath()
                    )
                    Files.createSymbolicLink(
                        destinationRoot.toPath(),
                        movedDestinationRoot.absoluteFile.toPath()
                    )
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination directory must not contain symbolic links"
                )
            )
            assertEquals(2, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(destinationRoot.toPath()))
            assertFalse(
                Files.isSymbolicLink(destinationDirectory.toPath())
            )
            assertFalse(requestedDestination.exists())
            val movedDestinationDirectory =
                File(movedDestinationRoot, "Nested_Destination")
            assertTrue(movedDestinationDirectory.listFiles()!!.isEmpty())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownDestinationSerializationPrioritizesUnexpectedDestinationEntry() {
        val directory =
            Files.createTempDirectory("gen6-thrown-serialization-sidecar")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sidecarBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val sidecar = File(directory, "serializer-sidecar.tmp")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val serializerFailure =
                IllegalStateException("destination serializer interrupted")
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    sidecar.writeBytes(sidecarBytes)
                    throw serializerFailure
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination serializer left unexpected paths"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === serializerFailure)
            assertEquals(2, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sidecarBytes, sidecar.readBytes())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownDestinationSerializationRemovesPrematureDestinationBeforeRethrow() {
        val directory =
            Files.createTempDirectory("gen6-thrown-serialization-destination")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val prematureBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val serializerFailure =
                IllegalStateException("destination serializer interrupted")
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    requestedDestination.writeBytes(prematureBytes)
                    throw serializerFailure
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination serializer created the requested destination " +
                        "before publication"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === serializerFailure)
            assertEquals(2, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownDestinationSerializationPrioritizesDestinationAncestorSymlink() {
        val directory =
            Files.createTempDirectory("gen6-serialization-destination-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalSourceLastModified = sourceFile.lastModified()
            val destinationRoot = File(directory, "Destination_Root").apply {
                check(mkdir())
            }
            val destinationDirectory =
                File(destinationRoot, "Nested_Destination").apply {
                    check(mkdir())
                }
            val movedDestinationRoot =
                File(directory, "Moved_Destination_Root")
            val requestedDestination = File(
                destinationDirectory,
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val serializerFailure =
                IllegalStateException("destination serializer interrupted")
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    Files.move(
                        destinationRoot.toPath(),
                        movedDestinationRoot.toPath()
                    )
                    Files.createSymbolicLink(
                        destinationRoot.toPath(),
                        movedDestinationRoot.absoluteFile.toPath()
                    )
                    throw serializerFailure
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination directory must not contain symbolic links"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === serializerFailure)
            assertEquals(2, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(destinationRoot.toPath()))
            assertFalse(
                Files.isSymbolicLink(destinationDirectory.toPath())
            )
            assertFalse(requestedDestination.exists())
            val movedDestinationDirectory =
                File(movedDestinationRoot, "Nested_Destination")
            assertTrue(movedDestinationDirectory.listFiles()!!.isEmpty())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(
                sourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownDestinationSerializationPrioritizesSourceAncestorSymlink() {
        val directory =
            Files.createTempDirectory("gen6-serialization-source-link")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceRoot = File(directory, "Source_Root").apply {
                check(mkdir())
            }
            val sourceDirectory = File(sourceRoot, "Nested_Source").apply {
                check(mkdir())
            }
            val sourceFile =
                File(sourceDirectory, "Pokemon_Omega_Ruby.sav").apply {
                    writeBytes(sourceBytes)
                }
            val movedSourceRoot = File(directory, "Moved_Source_Root")
            val originalSourceLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val serializerFailure =
                IllegalStateException("destination serializer interrupted")
            var writerCallbackCount = 0
            var publisherCallbackCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    writerCallbackCount += 1
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    writerCallbackCount += 1
                    Files.move(
                        sourceRoot.toPath(),
                        movedSourceRoot.toPath()
                    )
                    Files.createSymbolicLink(
                        sourceRoot.toPath(),
                        movedSourceRoot.absoluteFile.toPath()
                    )
                    throw serializerFailure
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                publisherCallbackCount += 1
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source directory must not contain symbolic links"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === serializerFailure)
            assertEquals(2, writerCallbackCount)
            assertEquals(0, publisherCallbackCount)
            assertTrue(Files.isSymbolicLink(sourceRoot.toPath()))
            assertFalse(Files.isSymbolicLink(sourceFile.parentFile.toPath()))
            assertFalse(requestedDestination.exists())
            val movedSourceFile = File(
                File(movedSourceRoot, "Nested_Source"),
                "Pokemon_Omega_Ruby.sav"
            )
            assertArrayEquals(sourceBytes, movedSourceFile.readBytes())
            assertTrue(
                movedSourceFile.lastModified() == originalSourceLastModified
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun destinationSerializerFailureStillVerifiesOriginalSave() {
        val directory =
            Files.createTempDirectory("gen6-serializer-failure").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val changedLastModified = originalLastModified - 120_000L
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val serializerFailure =
                IllegalStateException("destination serializer interrupted")
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    Files.setLastModifiedTime(
                        sourceFile.toPath(),
                        FileTime.fromMillis(changedLastModified)
                    )
                    throw serializerFailure
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source save during failed destination serialization " +
                        "metadata changed"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === serializerFailure)
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() == changedLastModified)
            assertFalse(requestedDestination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownDestinationValidationPrioritizesPrivateInputIntegrity() {
        val directory =
            Files.createTempDirectory("gen6-validation-thrown-input")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val validatorFailure =
                IllegalStateException("destination validator interrupted")
            var destinationValidationCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = byteArrayOf(7, 8, 9)

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCount += 1
                    destinationBytes[0] =
                        (destinationBytes[0].toInt() xor 1).toByte()
                    throw validatorFailure
                }
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination validator changed serialized save bytes " +
                        "before failing"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === validatorFailure)
            assertEquals(1, destinationValidationCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() == originalLastModified)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun destinationValidatorFailureStillVerifiesOriginalSave() {
        val directory =
            Files.createTempDirectory("gen6-destination-validator-failure")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val changedLastModified = originalLastModified - 120_000L
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val validatorFailure =
                IllegalStateException("destination validator interrupted")
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = byteArrayOf(7, 8, 9)

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    Files.setLastModifiedTime(
                        sourceFile.toPath(),
                        FileTime.fromMillis(changedLastModified)
                    )
                    throw validatorFailure
                }
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source save during failed destination validation " +
                        "metadata changed"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === validatorFailure)
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() == changedLastModified)
            assertFalse(requestedDestination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publishedDestinationValidatorFailureStillVerifiesOriginalSave() {
        val directory =
            Files.createTempDirectory("gen6-readback-validator-failure")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val changedLastModified = originalLastModified - 120_000L
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val validatorFailure =
                IllegalStateException("readback validator interrupted")
            var destinationValidationCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = byteArrayOf(7, 8, 9)

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCount += 1
                    if (destinationValidationCount == 2) {
                        Files.setLastModifiedTime(
                            sourceFile.toPath(),
                            FileTime.fromMillis(changedLastModified)
                        )
                        throw validatorFailure
                    }
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }

            val publisher = Gen6DestinationPublisher {
                    _, requested, destinationBytes, _ ->
                requested.writeBytes(destinationBytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source save during failed published destination " +
                        "validation metadata changed"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === validatorFailure)
            assertEquals(2, destinationValidationCount)
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() == changedLastModified)
            assertFalse(requestedDestination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publishedDestinationInvalidResultStillVerifiesOriginalSave() {
        val directory =
            Files.createTempDirectory("gen6-readback-invalid-result")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val changedLastModified = originalLastModified - 120_000L
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var destinationValidationCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = byteArrayOf(7, 8, 9)

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCount += 1
                    if (destinationValidationCount == 2) {
                        Files.setLastModifiedTime(
                            sourceFile.toPath(),
                            FileTime.fromMillis(changedLastModified)
                        )
                        return Gen6DestinationValidation(
                            false,
                            true,
                            true,
                            detectedGame = request.destination,
                            note = "readback rejected"
                        )
                    }
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, destinationBytes, _ ->
                requested.writeBytes(destinationBytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source save during published destination validation " +
                        "metadata changed"
                )
            )
            assertEquals(2, destinationValidationCount)
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() == changedLastModified)
            assertFalse(requestedDestination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun returnedDestinationValidationPrioritizesSourceIntegrity() {
        val directory =
            Files.createTempDirectory("gen6-validation-source-priority")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val changedLastModified = originalLastModified - 120_000L
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var destinationValidationCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = byteArrayOf(7, 8, 9)

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCount += 1
                    Files.setLastModifiedTime(
                        sourceFile.toPath(),
                        FileTime.fromMillis(changedLastModified)
                    )
                    destinationBytes[0] =
                        (destinationBytes[0].toInt() xor 1).toByte()
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source save during destination serialization or " +
                        "validation metadata changed"
                )
            )
            assertEquals(1, destinationValidationCount)
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() == changedLastModified)
            assertFalse(requestedDestination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownSourceValidationPrioritizesPrivateInputIntegrity() {
        val directory =
            Files.createTempDirectory("gen6-source-validation-thrown-input")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val validatorFailure =
                IllegalStateException("source validator interrupted")
            var sourceValidationCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    sourceValidationCount += 1
                    sourceBytes[0] = (sourceBytes[0].toInt() xor 1).toByte()
                    throw validatorFailure
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = error("Serialization must not run.")

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source validator changed source save bytes before failing"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === validatorFailure)
            assertEquals(1, sourceValidationCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() == originalLastModified)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun returnedSourceValidationPrioritizesSourceIntegrity() {
        val directory =
            Files.createTempDirectory("gen6-source-validation-priority")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val changedLastModified = originalLastModified - 120_000L
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var sourceValidationCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    sourceValidationCount += 1
                    Files.setLastModifiedTime(
                        sourceFile.toPath(),
                        FileTime.fromMillis(changedLastModified)
                    )
                    sourceBytes[0] = (sourceBytes[0].toInt() xor 1).toByte()
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.source
                    )
                }

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = error("Serialization must not run.")

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source save during source validation metadata changed"
                )
            )
            assertEquals(1, sourceValidationCount)
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() == changedLastModified)
            assertFalse(requestedDestination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun returnedDestinationSerializationPrioritizesSourceIntegrity() {
        val directory =
            Files.createTempDirectory("gen6-serialization-source-priority")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val changedLastModified = originalLastModified - 120_000L
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var serializationCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray {
                    serializationCount += 1
                    Files.setLastModifiedTime(
                        sourceFile.toPath(),
                        FileTime.fromMillis(changedLastModified)
                    )
                    sourceBytes[0] = (sourceBytes[0].toInt() xor 1).toByte()
                    return byteArrayOf(7, 8, 9)
                }

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation =
                    error("Destination validation must not run.")
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Source save during destination serialization " +
                        "metadata changed"
                )
            )
            assertEquals(1, serializationCount)
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() == changedLastModified)
            assertFalse(requestedDestination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownReadbackUnexpectedPathPrioritizesDirectoryIntegrity() {
        val directory =
            Files.createTempDirectory("gen6-readback-thrown-unexpected-path")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(7, 8, 9)
            val unexpectedBytes = byteArrayOf(4, 5, 6)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val unexpectedPath = File(directory, "unexpected.tmp")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val readbackFailure =
                IllegalStateException("readback validation interrupted")
            var destinationValidationCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = destinationBytes

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCount += 1
                    if (destinationValidationCount == 2) {
                        unexpectedPath.writeBytes(unexpectedBytes)
                        throw readbackFailure
                    }
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, bytes, _ ->
                requested.writeBytes(bytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination publisher created unexpected paths"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === readbackFailure)
            assertEquals(2, destinationValidationCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(unexpectedBytes, unexpectedPath.readBytes())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() == originalLastModified)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownReadbackPrioritizesPrivateInputIntegrity() {
        val directory =
            Files.createTempDirectory("gen6-readback-thrown-input")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val validatorFailure =
                IllegalStateException("readback validator interrupted")
            var destinationValidationCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = byteArrayOf(7, 8, 9)

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCount += 1
                    if (destinationValidationCount == 2) {
                        destinationBytes[0] =
                            (destinationBytes[0].toInt() xor 1).toByte()
                        throw validatorFailure
                    }
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, destinationBytes, _ ->
                requested.writeBytes(destinationBytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination validator changed published save bytes " +
                        "before failing"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === validatorFailure)
            assertEquals(2, destinationValidationCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() == originalLastModified)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun thrownReadbackPrioritizesPublishedDestinationIntegrity() {
        val directory =
            Files.createTempDirectory("gen6-readback-thrown-destination")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            val validatorFailure =
                IllegalStateException("readback validator interrupted")
            var destinationValidationCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = byteArrayOf(7, 8, 9)

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCount += 1
                    if (destinationValidationCount == 2) {
                        check(requestedDestination.isFile)
                        requestedDestination.writeBytes(byteArrayOf(4, 5, 6))
                        throw validatorFailure
                    }
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, destinationBytes, _ ->
                requested.writeBytes(destinationBytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Published destination during failed readback validation " +
                        "bytes changed"
                )
            )
            assertEquals(1, failure.suppressed.size)
            assertTrue(failure.suppressed.single() === validatorFailure)
            assertEquals(2, destinationValidationCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() == originalLastModified)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedReadbackRemovesMutatedDestinationWithCapturedIdentity() {
        val directory =
            Files.createTempDirectory("gen6-readback-mutated-destination")
                .toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val sourceFile = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val originalLastModified = sourceFile.lastModified()
            val requestedDestination =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val request = Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )
            var destinationValidationCount = 0
            val writer = object : Gen6DestinationSaveWriter {
                override val sourceGame = request.source
                override val destinationGame = request.destination

                override fun validateSource(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ) = Gen6DestinationValidation(
                    true, true, true, detectedGame = request.source
                )

                override fun serialize(
                    sourceBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): ByteArray = byteArrayOf(7, 8, 9)

                override fun validateDestination(
                    destinationBytes: ByteArray,
                    request: Gen6ConverterRequest
                ): Gen6DestinationValidation {
                    destinationValidationCount += 1
                    if (destinationValidationCount == 2) {
                        check(requestedDestination.isFile)
                        requestedDestination.writeBytes(byteArrayOf(4, 5, 6))
                        return Gen6DestinationValidation(
                            false,
                            true,
                            true,
                            detectedGame = request.destination,
                            note = "readback rejected"
                        )
                    }
                    return Gen6DestinationValidation(
                        true,
                        true,
                        true,
                        detectedGame = request.destination
                    )
                }
            }
            val publisher = Gen6DestinationPublisher {
                    _, requested, destinationBytes, _ ->
                requested.writeBytes(destinationBytes)
                requested
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer, publisher).convert(
                    sourceFile,
                    requestedDestination,
                    request
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Published destination during readback validation " +
                        "bytes changed"
                )
            )
            assertEquals(2, destinationValidationCount)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, sourceFile.readBytes())
            assertTrue(sourceFile.lastModified() == originalLastModified)
        } finally {
            directory.deleteRecursively()
        }
    }

}
