package com.example.pokemonsavemanager

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6SaveExportGuardTest {
    @Test
    fun existingCollisionFilesAreNeverOverwritten() {
        val directory = Files.createTempDirectory("gen6-export-collisions").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
            val firstContents = byteArrayOf(11, 12)
            val secondContents = byteArrayOf(21, 22)
            val source = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requested =
                File(directory, "Pokemon_Alpha_Sapphire_Converted.sav").apply {
                    writeBytes(firstContents)
                }
            val firstCollision =
                File(directory, "Pokemon_Alpha_Sapphire_Converted (1).sav").apply {
                    writeBytes(secondContents)
                }

            val exported = Gen6SaveExportGuard.exportNew(
                source,
                requested,
                destinationBytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            )

            assertEquals(
                "Pokemon_Alpha_Sapphire_Converted (2).sav",
                exported.name
            )
            assertArrayEquals(destinationBytes, exported.readBytes())
            assertArrayEquals(firstContents, requested.readBytes())
            assertArrayEquals(secondContents, firstCollision.readBytes())
            assertArrayEquals(sourceBytes, source.readBytes())
            assertFalse(Files.isSameFile(source.toPath(), exported.toPath()))
            assertTrue(
                directory.listFiles()!!.none {
                    it.name.startsWith(".pse-convert-")
                }
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun symlinkAliasToSourceIsRejectedWithoutPublishing() {
        val directory = Files.createTempDirectory("gen6-export-alias").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val source = File(directory, "Pokemon_X.sav").apply {
                writeBytes(sourceBytes)
            }
            val alias = File(directory, "Pokemon_Y_Converted.sav")
            Files.createSymbolicLink(alias.toPath(), source.toPath())

            val failure = assertThrows(IllegalArgumentException::class.java) {
                Gen6SaveExportGuard.exportNew(
                    source,
                    alias,
                    byteArrayOf(7, 8, 9),
                    Gen6ConverterGame.Y
                )
            }

            assertTrue(failure.message!!.contains("must not alias"))
            assertArrayEquals(sourceBytes, source.readBytes())
            assertTrue(Files.isSymbolicLink(alias.toPath()))
            assertArrayEquals(sourceBytes, alias.readBytes())
            assertTrue(
                directory.listFiles()!!.none {
                    it.name.startsWith(".pse-convert-")
                }
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun symlinkDestinationToUnrelatedFileIsRejectedWithoutPublishing() {
        val directory = Files.createTempDirectory(
            "gen6-export-unrelated-symlink"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val unrelatedBytes = byteArrayOf(9, 8, 7)
            val source = File(directory, "Pokemon_X.sav").apply {
                writeBytes(sourceBytes)
            }
            val unrelated = File(directory, "existing-unrelated.sav").apply {
                writeBytes(unrelatedBytes)
            }
            val requested = File(directory, "Pokemon_Y_Converted.sav")
            Files.createSymbolicLink(requested.toPath(), unrelated.toPath())

            val failure = assertThrows(IllegalArgumentException::class.java) {
                Gen6SaveExportGuard.exportNew(
                    source,
                    requested,
                    save(Gen6ConverterGame.Y),
                    Gen6ConverterGame.Y
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination path must not be a symbolic link"
                )
            )
            assertArrayEquals(sourceBytes, source.readBytes())
            assertArrayEquals(unrelatedBytes, unrelated.readBytes())
            assertTrue(Files.isSymbolicLink(requested.toPath()))
            assertArrayEquals(unrelatedBytes, requested.readBytes())
            assertFalse(File(directory, "existing-unrelated (1).sav").exists())
            assertTrue(
                directory.listFiles()!!.none {
                    it.name.startsWith(".pse-convert-")
                }
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun destinationBecomingSymlinkBeforePublicationFailsClosed() {
        val directory = Files.createTempDirectory(
            "gen6-export-late-symlink"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val unrelatedBytes = byteArrayOf(9, 8, 7)
            val source = File(directory, "Pokemon_X.sav").apply {
                writeBytes(sourceBytes)
            }
            val unrelated = File(directory, "existing-unrelated.sav").apply {
                writeBytes(unrelatedBytes)
            }
            val requested = File(directory, "Pokemon_Y_Converted.sav")

            val failure = assertThrows(IllegalArgumentException::class.java) {
                Gen6SaveExportGuard.exportNew(
                    sourceFile = source,
                    requestedDestination = requested,
                    destinationBytes = save(Gen6ConverterGame.Y),
                    expectedGame = Gen6ConverterGame.Y,
                    beforePublish = {
                        Files.createSymbolicLink(
                            requested.toPath(),
                            unrelated.toPath()
                        )
                    }
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination path must not be a symbolic link"
                )
            )
            assertArrayEquals(sourceBytes, source.readBytes())
            assertArrayEquals(unrelatedBytes, unrelated.readBytes())
            assertTrue(Files.isSymbolicLink(requested.toPath()))
            assertArrayEquals(unrelatedBytes, requested.readBytes())
            assertFalse(
                File(directory, "Pokemon_Y_Converted (1).sav").exists()
            )
            assertTrue(
                directory.listFiles()!!.none {
                    it.name.startsWith(".pse-convert-")
                }
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun symlinkDestinationDirectoryIsRejectedWithoutPublishing() {
        val directory = Files.createTempDirectory(
            "gen6-export-symlink-directory"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val source = File(directory, "Pokemon_X.sav").apply {
                writeBytes(sourceBytes)
            }
            val realDestinationDirectory =
                File(directory, "real-destination").apply {
                    check(mkdir())
                }
            val linkedDestinationDirectory =
                File(directory, "linked-destination")
            Files.createSymbolicLink(
                linkedDestinationDirectory.toPath(),
                realDestinationDirectory.toPath()
            )
            val requested = File(
                linkedDestinationDirectory,
                "Pokemon_Y_Converted.sav"
            )

            val failure = assertThrows(IllegalArgumentException::class.java) {
                Gen6SaveExportGuard.exportNew(
                    source,
                    requested,
                    save(Gen6ConverterGame.Y),
                    Gen6ConverterGame.Y
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Destination directory must not be a symbolic link"
                )
            )
            assertArrayEquals(sourceBytes, source.readBytes())
            assertTrue(
                Files.isSymbolicLink(linkedDestinationDirectory.toPath())
            )
            assertFalse(requested.exists())
            assertTrue(realDestinationDirectory.listFiles()!!.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun hardLinkAliasToSourceIsRejectedWithoutPublishing() {
        val directory = Files.createTempDirectory(
            "gen6-export-hard-link-alias"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val source = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val alias = File(
                directory,
                "Pokemon_Alpha_Sapphire_Converted.sav"
            )
            Files.createLink(alias.toPath(), source.toPath())

            val failure = assertThrows(IllegalArgumentException::class.java) {
                Gen6SaveExportGuard.exportNew(
                    source,
                    alias,
                    save(Gen6ConverterGame.ALPHA_SAPPHIRE),
                    Gen6ConverterGame.ALPHA_SAPPHIRE
                )
            }

            assertTrue(failure.message!!.contains("must not alias"))
            assertTrue(Files.isSameFile(source.toPath(), alias.toPath()))
            assertArrayEquals(sourceBytes, source.readBytes())
            assertArrayEquals(sourceBytes, alias.readBytes())
            assertFalse(
                File(
                    directory,
                    "Pokemon_Alpha_Sapphire_Converted (1).sav"
                ).exists()
            )
            assertTrue(
                directory.listFiles()!!.none {
                    it.name.startsWith(".pse-convert-")
                }
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun wrongGameDestinationIsRejectedBeforePublishing() {
        val directory = Files.createTempDirectory("gen6-export-wrong-game").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val source = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requested = File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")

            val failure = assertThrows(IllegalArgumentException::class.java) {
                Gen6SaveExportGuard.exportNew(
                    source,
                    requested,
                    save(Gen6ConverterGame.OMEGA_RUBY),
                    Gen6ConverterGame.ALPHA_SAPPHIRE
                )
            }

            assertTrue(failure.message!!.contains("Prepared destination save"))
            assertFalse(requested.exists())
            assertArrayEquals(sourceBytes, source.readBytes())
            assertTrue(
                directory.listFiles()!!.none {
                    it.name.startsWith(".pse-convert-")
                }
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun invalidIntegrityIsRejectedBeforePublishing() {
        val directory = Files.createTempDirectory("gen6-export-invalid").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val source = File(directory, "Pokemon_Omega_Ruby.sav").apply {
                writeBytes(sourceBytes)
            }
            val requested = File(directory, "Pokemon_Alpha_Sapphire_Converted.sav")
            val damaged = save(Gen6ConverterGame.ALPHA_SAPPHIRE).also {
                it[0x33000] = 1
            }

            assertThrows(IllegalArgumentException::class.java) {
                Gen6SaveExportGuard.exportNew(
                    source,
                    requested,
                    damaged,
                    Gen6ConverterGame.ALPHA_SAPPHIRE
                )
            }

            assertFalse(requested.exists())
            assertArrayEquals(sourceBytes, source.readBytes())
            assertTrue(
                directory.listFiles()!!.none {
                    it.name.startsWith(".pse-convert-")
                }
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun newDestinationPublishesExactBytesAndPreservesSource() {
        val directory = Files.createTempDirectory("gen6-export-new").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = save(Gen6ConverterGame.Y)
            val source = File(directory, "Pokemon_X.sav").apply {
                writeBytes(sourceBytes)
            }
            val requested = File(directory, "Pokemon_Y_Converted.sav")

            val exported = Gen6SaveExportGuard.exportNew(
                source,
                requested,
                destinationBytes,
                Gen6ConverterGame.Y
            )

            assertEquals(requested.canonicalFile, exported.canonicalFile)
            assertArrayEquals(destinationBytes, exported.readBytes())
            assertArrayEquals(sourceBytes, source.readBytes())
            assertTrue(
                directory.listFiles()!!.none {
                    it.name.startsWith(".pse-convert-")
                }
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun publishedDestinationBecomingSymlinkFailsClosedAndCleansArtifacts() {
        val directory = Files.createTempDirectory(
            "gen6-export-published-symlink"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val source = File(directory, "Pokemon_X.sav").apply {
                writeBytes(sourceBytes)
            }
            val requested = File(directory, "Pokemon_Y_Converted.sav")

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveExportGuard.exportNew(
                    sourceFile = source,
                    requestedDestination = requested,
                    destinationBytes = save(Gen6ConverterGame.Y),
                    expectedGame = Gen6ConverterGame.Y,
                    beforePublish = {},
                    afterPublish = { temporary, destination ->
                        check(destination.delete())
                        Files.createSymbolicLink(
                            destination.toPath(),
                            temporary.toPath()
                        )
                    }
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "Published destination must not be a symbolic link"
                )
            )
            assertArrayEquals(sourceBytes, source.readBytes())
            assertFalse(
                Files.exists(
                    requested.toPath(),
                    LinkOption.NOFOLLOW_LINKS
                )
            )
            assertTrue(
                directory.listFiles()!!.none {
                    it.name.startsWith(".pse-convert-")
                }
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun replacedTemporaryIsPreservedWhenFinalVerificationFails() {
        val directory = Files.createTempDirectory(
            "gen6-export-replaced-temporary"
        ).toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = save(Gen6ConverterGame.Y)
            val replacementBytes = destinationBytes.copyOf().also {
                it[0] = (it[0].toInt() xor 1).toByte()
            }
            val source = File(directory, "Pokemon_X.sav").apply {
                writeBytes(sourceBytes)
            }
            val requested = File(directory, "Pokemon_Y_Converted.sav")
            var replacementPath: File? = null

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveExportGuard.exportNew(
                    sourceFile = source,
                    requestedDestination = requested,
                    destinationBytes = destinationBytes,
                    expectedGame = Gen6ConverterGame.Y,
                    beforePublish = {
                        val temporary = directory.listFiles()!!.single {
                            it.name.startsWith(".pse-convert-")
                        }
                        val replacement = File(
                            directory,
                            "replacement.tmp"
                        ).apply {
                            writeBytes(replacementBytes)
                        }
                        Files.move(
                            replacement.toPath(),
                            temporary.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                        replacementPath = temporary
                    }
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "failed final verification"
                )
            )
            assertArrayEquals(sourceBytes, source.readBytes())
            assertFalse(requested.exists())
            assertArrayEquals(
                replacementBytes,
                requireNotNull(replacementPath).readBytes()
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedExportCleanupRemovesArtifactsBeforeRethrowingOriginalFailure() {
        val directory = Files.createTempDirectory("gen6-export-cleanup").toFile()
        try {
            val temporary = File(directory, ".pse-convert-test.tmp").apply {
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val destination = File(directory, "Pokemon_Y_Converted.sav")
            Files.createLink(destination.toPath(), temporary.toPath())
            val original = IllegalStateException("final verification failed")

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveExportGuard.rethrowAfterFailedExportCleanup(
                    temporary = temporary,
                    destination = destination,
                    finalized = true,
                    failure = original
                )
            }

            assertSame(original, failure)
            assertFalse(temporary.exists())
            assertFalse(destination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cleanupFailureReportsRemainingArtifactAndPreservesOriginalCause() {
        val directory = Files.createTempDirectory("gen6-export-cleanup-failure").toFile()
        try {
            val nonEmptyTemporary = File(directory, ".pse-convert-test.tmp").apply {
                check(mkdir())
                File(this, "remaining").writeBytes(byteArrayOf(1))
            }
            val destinationBytes = byteArrayOf(4, 5, 6)
            val destination = File(directory, "Pokemon_Y_Converted.sav").apply {
                writeBytes(destinationBytes)
            }
            val original = IllegalStateException("final verification failed")

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveExportGuard.rethrowAfterFailedExportCleanup(
                    temporary = nonEmptyTemporary,
                    destination = destination,
                    finalized = true,
                    failure = original
                )
            }

            assertTrue(failure.message!!.contains("temporary destination"))
            assertSame(original, failure.cause)
            assertTrue(nonEmptyTemporary.exists())
            assertArrayEquals(destinationBytes, destination.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedExportCleanupPreservesReplacedDestination() {
        val directory = Files.createTempDirectory("gen6-export-replaced").toFile()
        try {
            val temporary = File(directory, ".pse-convert-test.tmp").apply {
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val destination = File(directory, "Pokemon_Y_Converted.sav")
            Files.createLink(destination.toPath(), temporary.toPath())
            check(destination.delete())
            val replacementBytes = byteArrayOf(7, 8, 9)
            destination.writeBytes(replacementBytes)
            val original = IllegalStateException(
                "published destination identity changed"
            )

            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6SaveExportGuard.rethrowAfterFailedExportCleanup(
                    temporary = temporary,
                    destination = destination,
                    finalized = true,
                    failure = original
                )
            }

            assertSame(original, failure)
            assertFalse(temporary.exists())
            assertArrayEquals(replacementBytes, destination.readBytes())
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
