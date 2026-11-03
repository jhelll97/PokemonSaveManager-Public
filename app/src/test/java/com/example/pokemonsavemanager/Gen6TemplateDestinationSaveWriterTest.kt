package com.example.pokemonsavemanager

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6TemplateDestinationSaveWriterTest {
    private val request = Gen6ConverterRequest(
        Gen6ConverterGame.OMEGA_RUBY, Gen6ConverterGame.ALPHA_SAPPHIRE
    )

    private fun valid(game: Gen6ConverterGame = request.destination) =
        Gen6DestinationValidation(true, true, true, detectedGame = game)

    @Test
    fun invalidBaselineFailsBeforeMigrationAndPreservesInputs() {
        val invalidResults = listOf(
            Gen6DestinationValidation(false, true, true, request.destination),
            Gen6DestinationValidation(true, false, true, request.destination),
            Gen6DestinationValidation(true, true, false, request.destination),
            Gen6DestinationValidation(true, true, true),
            valid(Gen6ConverterGame.OMEGA_RUBY)
        )
        for (validation in invalidResults) {
            val source = byteArrayOf(1, 2, 3)
            val template = byteArrayOf(4, 5, 6)
            var migrated = false
            val writer = Gen6TemplateDestinationSaveWriter(
                request.source, request.destination, template,
                Gen6SaveBytesValidator { _, _ -> valid(request.source) },
                Gen6SaveMigration { _, _, _ ->
                    migrated = true
                    byteArrayOf(7, 8, 9)
                },
                Gen6SaveBytesValidator { bytes, route ->
                    assertEquals(request, route)
                    assertArrayEquals(template, bytes)
                    validation
                }
            )
            assertThrows(IllegalArgumentException::class.java) {
                writer.serialize(source, request)
            }
            assertFalse(migrated)
            assertArrayEquals(byteArrayOf(1, 2, 3), source)
            assertArrayEquals(byteArrayOf(4, 5, 6), template)
        }
    }

    @Test
    fun invalidSourceIsRejectedBeforeTemplateValidationOrMigration() {
        val invalidResults = listOf(
            Gen6DestinationValidation(false, true, true, request.source),
            Gen6DestinationValidation(true, false, true, request.source),
            Gen6DestinationValidation(true, true, false, request.source),
            Gen6DestinationValidation(true, true, true),
            valid(request.destination)
        )
        for (validation in invalidResults) {
            val source = byteArrayOf(1, 2, 3)
            val template = byteArrayOf(4, 5, 6)
            var templateValidated = false
            var migrated = false
            val writer = Gen6TemplateDestinationSaveWriter(
                request.source,
                request.destination,
                template,
                Gen6SaveBytesValidator { bytes, route ->
                    assertEquals(request, route)
                    assertArrayEquals(source, bytes)
                    validation
                },
                Gen6SaveMigration { _, _, _ ->
                    migrated = true
                    byteArrayOf(7, 8, 9)
                },
                Gen6SaveBytesValidator { _, _ ->
                    templateValidated = true
                    valid()
                }
            )

            assertThrows(IllegalArgumentException::class.java) {
                writer.serialize(source, request)
            }

            assertFalse(templateValidated)
            assertFalse(migrated)
            assertArrayEquals(byteArrayOf(1, 2, 3), source)
            assertArrayEquals(byteArrayOf(4, 5, 6), template)
        }
    }

    @Test
    fun invalidMigratedSaveIsRejectedBeforeSerializeReturns() {
        val invalidResults = listOf(
            Gen6DestinationValidation(false, true, true, request.destination),
            Gen6DestinationValidation(true, false, true, request.destination),
            Gen6DestinationValidation(true, true, false, request.destination),
            Gen6DestinationValidation(true, true, true),
            valid(Gen6ConverterGame.OMEGA_RUBY)
        )
        for (validation in invalidResults) {
            val source = byteArrayOf(1, 2, 3)
            val template = byteArrayOf(4, 5, 6)
            var validationCalls = 0
            val writer = Gen6TemplateDestinationSaveWriter(
                request.source,
                request.destination,
                template,
                Gen6SaveBytesValidator { _, _ -> valid(request.source) },
                Gen6SaveMigration { _, _, _ -> byteArrayOf(7, 8, 9) },
                Gen6SaveBytesValidator { bytes, route ->
                    validationCalls++
                    assertEquals(request, route)
                    if (validationCalls == 1) {
                        assertArrayEquals(template, bytes)
                        valid()
                    } else {
                        assertArrayEquals(byteArrayOf(7, 8, 9), bytes)
                        validation
                    }
                }
            )

            val failure = assertThrows(IllegalArgumentException::class.java) {
                writer.serialize(source, request)
            }

            assertEquals(
                "Converted destination failed format, integrity, reopen or game identity validation.",
                failure.message
            )
            assertEquals(2, validationCalls)
            assertArrayEquals(byteArrayOf(1, 2, 3), source)
            assertArrayEquals(byteArrayOf(4, 5, 6), template)
        }
    }

    @Test
    fun baselineValidatorCannotRewriteTemplateToMakeItPass() {
        val source = byteArrayOf(1, 2, 3)
        val template = byteArrayOf(4, 5, 6)
        var migrated = false
        val writer = Gen6TemplateDestinationSaveWriter(
            request.source, request.destination, template,
            Gen6SaveBytesValidator { _, _ -> valid(request.source) },
            Gen6SaveMigration { _, _, _ ->
                migrated = true
                byteArrayOf(7, 8, 9)
            },
            Gen6SaveBytesValidator { bytes, _ ->
                bytes[0] = 99
                valid()
            }
        )
        assertThrows(IllegalStateException::class.java) {
            writer.serialize(source, request)
        }
        assertFalse(migrated)
        assertArrayEquals(byteArrayOf(1, 2, 3), source)
        assertArrayEquals(byteArrayOf(4, 5, 6), template)
    }


    @Test
    fun sourceValidatorCannotChangeSelectionBeforeSerialization() {
        val directory = Files.createTempDirectory(
            "gen6-source-request-validator"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val template = byteArrayOf(4, 5, 6)
            val selectedIds = mutableListOf("exclusive-a", "exclusive-b")
            val selectedRequest = request.copy(
                includeVersionExclusives = true,
                selectedVersionExclusiveIds = selectedIds
            )
            val source = File(directory, "Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination = File(
                directory,
                "Alpha_Sapphire_Converted.sav"
            )
            var migrated = false
            val writer = Gen6TemplateDestinationSaveWriter(
                request.source,
                request.destination,
                template,
                Gen6SaveBytesValidator { _, route ->
                    (route.selectedVersionExclusiveIds
                        as MutableList<String>).clear()
                    valid(request.source)
                },
                Gen6SaveMigration { _, baseline, _ ->
                    migrated = true
                    baseline
                },
                Gen6SaveBytesValidator { _, _ -> valid() }
            )

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer).convert(
                    source,
                    requestedDestination,
                    selectedRequest
                )
            }

            assertEquals(
                "Source save validator changed conversion request.",
                failure.message
            )
            assertFalse(migrated)
            assertFalse(requestedDestination.exists())
            assertEquals(
                listOf("exclusive-a", "exclusive-b"),
                selectedIds
            )
            assertArrayEquals(sourceBytes, source.readBytes())
            assertArrayEquals(byteArrayOf(4, 5, 6), template)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun sourceValidatorCannotRewriteSourceToMakeItPass() {
        val source = byteArrayOf(1, 2, 3)
        val template = byteArrayOf(4, 5, 6)
        val writer = Gen6TemplateDestinationSaveWriter(
            request.source,
            request.destination,
            template,
            Gen6SaveBytesValidator { bytes, _ ->
                bytes[0] = 99
                valid(request.source)
            },
            Gen6SaveMigration { _, baseline, _ -> baseline },
            Gen6SaveBytesValidator { _, _ -> valid() }
        )

        assertThrows(IllegalStateException::class.java) {
            writer.validateSource(source, request)
        }
        assertArrayEquals(byteArrayOf(1, 2, 3), source)
        assertArrayEquals(byteArrayOf(4, 5, 6), template)
    }

    @Test
    fun baselineValidatorCannotChangeExclusiveSelectionBeforeMigration() {
        val source = byteArrayOf(1, 2, 3)
        val template = byteArrayOf(4, 5, 6)
        val selectedIds = mutableListOf("exclusive-a", "exclusive-b")
        val selectedRequest = request.copy(
            includeVersionExclusives = true,
            selectedVersionExclusiveIds = selectedIds
        )
        var migrated = false
        val writer = Gen6TemplateDestinationSaveWriter(
            request.source,
            request.destination,
            template,
            Gen6SaveBytesValidator { _, _ -> valid(request.source) },
            Gen6SaveMigration { _, baseline, _ ->
                migrated = true
                baseline
            },
            Gen6SaveBytesValidator { _, route ->
                (route.selectedVersionExclusiveIds as MutableList<String>)
                    .clear()
                valid()
            }
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            writer.serialize(source, selectedRequest)
        }

        assertEquals(
            "Destination template validator changed conversion request.",
            failure.message
        )
        assertFalse(migrated)
        assertEquals(
            listOf("exclusive-a", "exclusive-b"),
            selectedIds
        )
        assertArrayEquals(byteArrayOf(1, 2, 3), source)
        assertArrayEquals(byteArrayOf(4, 5, 6), template)
    }

    @Test
    fun migrationCannotRewriteItsIsolatedSourceInput() {
        val source = byteArrayOf(1, 2, 3)
        val template = byteArrayOf(4, 5, 6)
        val writer = Gen6TemplateDestinationSaveWriter(
            request.source,
            request.destination,
            template,
            Gen6SaveBytesValidator { _, _ -> valid(request.source) },
            Gen6SaveMigration { sourceCopy, baseline, _ ->
                sourceCopy[0] = 99
                baseline
            },
            Gen6SaveBytesValidator { _, _ -> valid() }
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            writer.serialize(source, request)
        }

        assertEquals(
            "Destination migration changed source bytes.",
            failure.message
        )
        assertArrayEquals(byteArrayOf(1, 2, 3), source)
        assertArrayEquals(byteArrayOf(4, 5, 6), template)
    }

    @Test
    fun migrationCannotChangeExclusiveSelection() {
        val source = byteArrayOf(1, 2, 3)
        val template = byteArrayOf(4, 5, 6)
        val selectedIds = mutableListOf("exclusive-a", "exclusive-b")
        val selectedRequest = request.copy(
            includeVersionExclusives = true,
            selectedVersionExclusiveIds = selectedIds
        )
        val writer = Gen6TemplateDestinationSaveWriter(
            request.source,
            request.destination,
            template,
            Gen6SaveBytesValidator { _, _ -> valid(request.source) },
            Gen6SaveMigration { _, baseline, route ->
                (route.selectedVersionExclusiveIds as MutableList<String>)
                    .clear()
                baseline
            },
            Gen6SaveBytesValidator { _, _ -> valid() }
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            writer.serialize(source, selectedRequest)
        }

        assertEquals(
            "Destination migration changed conversion request.",
            failure.message
        )
        assertEquals(
            listOf("exclusive-a", "exclusive-b"),
            selectedIds
        )
        assertArrayEquals(byteArrayOf(1, 2, 3), source)
        assertArrayEquals(byteArrayOf(4, 5, 6), template)
    }

    @Test
    fun validBaselineIsCheckedBeforeMigrationAndExportsSeparateSave() {
        val directory = Files.createTempDirectory("gen6-template-preflight").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
            val expectedDestination = template.copyOf().also { it[0] = 7 }
            val source = File(directory, "Omega_Ruby.sav").apply { writeBytes(sourceBytes) }
            val originalModified = source.lastModified()
            val validations = mutableListOf<ByteArray>()
            val writer = Gen6TemplateDestinationSaveWriter(
                request.source, request.destination, template,
                Gen6SaveBytesValidator { _, _ -> valid(request.source) },
                Gen6SaveMigration { sourceCopy, baselineCopy, route ->
                    assertEquals(request, route)
                    assertEquals(1, validations.size)
                    assertArrayEquals(template, validations[0])
                    assertArrayEquals(sourceBytes, sourceCopy)
                    baselineCopy[0] = 7
                    baselineCopy
                },
                Gen6SaveBytesValidator { bytes, _ ->
                    validations.add(bytes.copyOf())
                    valid()
                }
            )
            val output = Gen6SaveConversionService(writer).convert(
                source, File(directory, "Alpha_Sapphire_Converted.sav"), request
            )
            assertNotEquals(source.canonicalFile, output.canonicalFile)
            assertEquals(4, validations.size)
            assertArrayEquals(expectedDestination, validations[1])
            assertArrayEquals(expectedDestination, validations[2])
            assertArrayEquals(expectedDestination, validations[3])
            assertArrayEquals(expectedDestination, output.readBytes())
            assertArrayEquals(sourceBytes, source.readBytes())
            assertEquals(originalModified, source.lastModified())
            assertArrayEquals(save(Gen6ConverterGame.ALPHA_SAPPHIRE), template)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun serializeKeepsSelectionSnapshotAcrossValidationAndMigration() {
        val selected = mutableListOf("exclusive-a")
        val mutableRequest = request.copy(
            includeVersionExclusives = true,
            selectedVersionExclusiveIds = selected
        )
        val template = byteArrayOf(4, 5, 6)
        var migratedSelection = emptyList<String>()
        val writer = Gen6TemplateDestinationSaveWriter(
            request.source,
            request.destination,
            template,
            Gen6SaveBytesValidator { _, _ -> valid(request.source) },
            Gen6SaveMigration { _, baseline, route ->
                migratedSelection = route.selectedVersionExclusiveIds.toList()
                baseline
            },
            Gen6SaveBytesValidator { _, route ->
                assertEquals(
                    listOf("exclusive-a"),
                    route.selectedVersionExclusiveIds
                )
                selected.clear()
                valid()
            }
        )

        val converted = writer.serialize(
            byteArrayOf(1, 2, 3),
            mutableRequest
        )

        assertTrue(selected.isEmpty())
        assertEquals(listOf("exclusive-a"), migratedSelection)
        assertArrayEquals(template, converted)
    }

    @Test
    fun destinationValidatorCannotChangeSelectionBeforePublication() {
        val directory = Files.createTempDirectory(
            "gen6-destination-request-validator"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val template = byteArrayOf(4, 5, 6)
            val selectedIds = mutableListOf("exclusive-a", "exclusive-b")
            val selectedRequest = request.copy(
                includeVersionExclusives = true,
                selectedVersionExclusiveIds = selectedIds
            )
            val source = File(directory, "Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requestedDestination = File(
                directory,
                "Alpha_Sapphire_Converted.sav"
            )
            var validationCalls = 0
            val writer = Gen6TemplateDestinationSaveWriter(
                request.source,
                request.destination,
                template,
                Gen6SaveBytesValidator { _, _ -> valid(request.source) },
                Gen6SaveMigration { _, baseline, _ -> baseline },
                Gen6SaveBytesValidator { _, route ->
                    validationCalls++
                    if (validationCalls == 2) {
                        (route.selectedVersionExclusiveIds
                            as MutableList<String>).clear()
                    }
                    valid()
                }
            )

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer).convert(
                    source,
                    requestedDestination,
                    selectedRequest
                )
            }

            assertEquals(
                "Destination save validator changed conversion request.",
                failure.message
            )
            assertEquals(2, validationCalls)
            assertFalse(requestedDestination.exists())
            assertEquals(
                listOf("exclusive-a", "exclusive-b"),
                selectedIds
            )
            assertArrayEquals(sourceBytes, source.readBytes())
            assertArrayEquals(byteArrayOf(4, 5, 6), template)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun destinationValidatorCannotRewriteCandidateToMakeItPass() {
        val directory = Files.createTempDirectory("gen6-destination-validator").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val template = byteArrayOf(4, 5, 6)
            val source = File(directory, "Omega_Ruby.sav").apply { writeBytes(sourceBytes) }
            val requestedDestination =
                File(directory, "Alpha_Sapphire_Converted.sav")
            var validationCalls = 0
            val writer = Gen6TemplateDestinationSaveWriter(
                request.source,
                request.destination,
                template,
                Gen6SaveBytesValidator { _, _ -> valid(request.source) },
                Gen6SaveMigration { _, baseline, _ ->
                    baseline[0] = 7
                    baseline
                },
                Gen6SaveBytesValidator { bytes, _ ->
                    validationCalls++
                    if (validationCalls == 2) bytes[0] = 99
                    valid()
                }
            )

            assertThrows(IllegalStateException::class.java) {
                Gen6SaveConversionService(writer).convert(
                    source,
                    requestedDestination,
                    request
                )
            }
            assertEquals(2, validationCalls)
            assertFalse(requestedDestination.exists())
            assertArrayEquals(sourceBytes, source.readBytes())
            assertArrayEquals(byteArrayOf(4, 5, 6), template)
        } finally {
            directory.deleteRecursively()
        }
    }


    private fun save(game: Gen6ConverterGame): ByteArray {
        val oras = game == Gen6ConverterGame.OMEGA_RUBY ||
            game == Gen6ConverterGame.ALPHA_SAPPHIRE
        val bytes = ByteArray(if (oras) 0x76000 else 0x65600)
        bytes[0x14004] = when (game) {
            Gen6ConverterGame.X -> 24
            Gen6ConverterGame.Y -> 25
            Gen6ConverterGame.ALPHA_SAPPHIRE -> 26
            Gen6ConverterGame.OMEGA_RUBY -> 27
        }.toByte()
        val pcOffset = if (oras) 0x33000 else 0x22600
        writeBlockChecksum(bytes, 18, 0x14200, 0x61C)
        writeBlockChecksum(
            bytes,
            20,
            0x15000,
            if (oras) 0x011CC else 0x006A0
        )
        writeBlockChecksum(bytes, if (oras) 56 else 53, pcOffset, 0x34AD0)
        return bytes
    }

    private fun writeBlockChecksum(
        save: ByteArray,
        blockId: Int,
        offset: Int,
        length: Int
    ) {
        val checksumOffset = save.size - 0x200 + 0x14 + blockId * 8 + 6
        val value = crc16Ccitt(save, offset, length)
        save[checksumOffset] = (value and 0xFF).toByte()
        save[checksumOffset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun crc16Ccitt(
        data: ByteArray,
        offset: Int,
        length: Int
    ): Int {
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
}
