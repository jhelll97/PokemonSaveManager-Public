package com.example.pokemonsavemanager

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6SourceFileSnapshotTest {
    @Test
    fun capturedBytesAreDefensiveAndUnchangedSourcePasses() {
        val directory = Files.createTempDirectory("gen6-source-snapshot").toFile()
        try {
            val original = byteArrayOf(1, 2, 3)
            val source = File(directory, "source.sav").apply {
                writeBytes(original)
            }
            val snapshot = Gen6SourceFileSnapshot.capture(source)

            snapshot.bytes.fill(0)
            snapshot.requireUnchanged("Source save")

            assertArrayEquals(original, snapshot.bytes)
            assertArrayEquals(original, source.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun incompleteSourceReadFailsClosed() {
        val directory =
            Files.createTempDirectory("gen6-source-incomplete-read").toFile()
        try {
            val originalBytes = byteArrayOf(1, 2, 3, 4)
            val source = File(directory, "source.sav").apply {
                writeBytes(originalBytes)
            }

            val failure =
                assertThrows(IllegalArgumentException::class.java) {
                    Gen6SourceFileSnapshot.capture(source) { capturedSource ->
                        capturedSource.readBytes().copyOf(2)
                    }
                }

            assertTrue(failure.message!!.contains("read was incomplete"))
            assertArrayEquals(originalBytes, source.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun sameLengthIncorrectSourceReadFailsClosed() {
        val directory =
            Files.createTempDirectory("gen6-source-incorrect-read").toFile()
        try {
            val originalBytes = byteArrayOf(1, 2, 3, 4)
            val source = File(directory, "source.sav").apply {
                writeBytes(originalBytes)
            }

            val failure =
                assertThrows(IllegalArgumentException::class.java) {
                    Gen6SourceFileSnapshot.capture(source) { capturedSource ->
                        capturedSource.readBytes().also {
                            it[0] = 9.toByte()
                        }
                    }
                }

            assertTrue(failure.message!!.contains("did not match verification"))
            assertArrayEquals(originalBytes, source.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun symbolicLinkSourceIdentityFailsClosed() {
        val directory = Files.createTempDirectory(
            "gen6-source-symbolic-link"
        ).toFile()
        try {
            val originalBytes = byteArrayOf(1, 2, 3)
            val backing = File(directory, "backing.sav").apply {
                writeBytes(originalBytes)
            }
            val linkedSource = File(directory, "linked-source.sav")
            Files.createSymbolicLink(
                linkedSource.toPath(),
                backing.toPath()
            )

            val initialFailure =
                assertThrows(IllegalArgumentException::class.java) {
                    Gen6SourceFileSnapshot.capture(linkedSource)
                }

            assertTrue(initialFailure.message!!.contains("symbolic link"))
            assertArrayEquals(originalBytes, backing.readBytes())

            val source = File(directory, "source.sav").apply {
                writeBytes(originalBytes)
            }
            val snapshot = Gen6SourceFileSnapshot.capture(source)
            val movedSource = File(directory, "moved-source.sav")
            Files.move(source.toPath(), movedSource.toPath())
            Files.createSymbolicLink(source.toPath(), movedSource.toPath())

            val replacementFailure =
                assertThrows(IllegalStateException::class.java) {
                    snapshot.requireUnchanged("Source save")
                }

            assertTrue(
                replacementFailure.message!!.contains(
                    "path became a symbolic link"
                )
            )
            assertArrayEquals(originalBytes, source.readBytes())
            assertArrayEquals(originalBytes, movedSource.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun sourceBecomingSymbolicLinkDuringCaptureFailsClosed() {
        val directory = Files.createTempDirectory(
            "gen6-source-capture-symbolic-link"
        ).toFile()
        try {
            val originalBytes = byteArrayOf(1, 2, 3)
            val source = File(directory, "source.sav").apply {
                writeBytes(originalBytes)
            }
            val movedSource = File(directory, "moved-source.sav")

            val failure =
                assertThrows(IllegalArgumentException::class.java) {
                    Gen6SourceFileSnapshot.capture(source) { capturedSource ->
                        val capturedBytes = capturedSource.readBytes()
                        Files.move(
                            capturedSource.toPath(),
                            movedSource.toPath()
                        )
                        Files.createSymbolicLink(
                            capturedSource.toPath(),
                            movedSource.toPath()
                        )
                        capturedBytes
                    }
                }

            assertTrue(
                failure.message!!.contains(
                    "path became a symbolic link while it was being captured"
                )
            )
            assertTrue(Files.isSymbolicLink(source.toPath()))
            assertArrayEquals(originalBytes, source.readBytes())
            assertArrayEquals(originalBytes, movedSource.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun symbolicLinkSourceDirectoryIdentityFailsClosed() {
        val directory = Files.createTempDirectory(
            "gen6-source-symbolic-link-directory"
        ).toFile()
        try {
            val originalBytes = byteArrayOf(1, 2, 3)
            val backingDirectory = File(directory, "backing").apply {
                check(mkdir())
            }
            val backingSource = File(backingDirectory, "source.sav").apply {
                writeBytes(originalBytes)
            }
            val linkedDirectory = File(directory, "linked")
            Files.createSymbolicLink(
                linkedDirectory.toPath(),
                backingDirectory.toPath()
            )
            val linkedSource = File(linkedDirectory, "source.sav")

            val initialFailure =
                assertThrows(IllegalArgumentException::class.java) {
                    Gen6SourceFileSnapshot.capture(linkedSource)
                }

            assertTrue(
                initialFailure.message!!.contains(
                    "directory must not be a symbolic link"
                )
            )
            assertArrayEquals(originalBytes, backingSource.readBytes())

            val sourceDirectory = File(directory, "captured").apply {
                check(mkdir())
            }
            val source = File(sourceDirectory, "source.sav").apply {
                writeBytes(originalBytes)
            }
            val snapshot = Gen6SourceFileSnapshot.capture(source)
            val movedDirectory = File(directory, "moved-captured")
            Files.move(sourceDirectory.toPath(), movedDirectory.toPath())
            Files.createSymbolicLink(
                sourceDirectory.toPath(),
                movedDirectory.toPath()
            )

            val replacementFailure =
                assertThrows(IllegalStateException::class.java) {
                    snapshot.requireUnchanged("Source save")
                }

            assertTrue(
                replacementFailure.message!!.contains(
                    "directory became a symbolic link"
                )
            )
            assertTrue(Files.isSymbolicLink(sourceDirectory.toPath()))
            assertArrayEquals(
                originalBytes,
                File(movedDirectory, "source.sav").readBytes()
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun retargetedAncestorSymlinkChangesSourcePathIdentity() {
        val root =
            Files.createTempDirectory("gen6-source-ancestor-link").toFile()
        val first = File(root, "first").apply { check(mkdir()) }
        val second = File(root, "second").apply { check(mkdir()) }
        val firstRecords =
            File(first, "records").apply { check(mkdir()) }
        val secondRecords =
            File(second, "records").apply { check(mkdir()) }
        val active = File(root, "active")
        try {
            val bytes = byteArrayOf(1, 2, 3)
            File(firstRecords, "source.sav").writeBytes(bytes)
            File(secondRecords, "source.sav").writeBytes(bytes)
            Files.createSymbolicLink(active.toPath(), first.toPath())
            val declaredSource = File(active, "records/source.sav")
            val snapshot = Gen6SourceFileSnapshot.capture(declaredSource)

            Files.delete(active.toPath())
            Files.createSymbolicLink(active.toPath(), second.toPath())

            val failure = assertThrows(IllegalStateException::class.java) {
                snapshot.requireUnchanged("Source save")
            }

            assertTrue(failure.message!!.contains("path identity changed"))
            assertArrayEquals(bytes, declaredSource.readBytes())
        } finally {
            Files.deleteIfExists(active.toPath())
            root.deleteRecursively()
        }
    }

    @Test
    fun regularDirectoryReplacementWithHardLinkedSourceFailsClosed() {
        val root =
            Files.createTempDirectory("gen6-source-directory-identity").toFile()
        try {
            val originalBytes = byteArrayOf(1, 2, 3)
            val sourceDirectory = File(root, "source").apply {
                check(mkdir())
            }
            val source = File(sourceDirectory, "source.sav").apply {
                writeBytes(originalBytes)
            }
            val snapshot = Gen6SourceFileSnapshot.capture(source)
            val movedDirectory = File(root, "moved-source")
            Files.move(sourceDirectory.toPath(), movedDirectory.toPath())
            check(sourceDirectory.mkdir())
            val movedSource = File(movedDirectory, source.name)
            val hardLinkedSource = File(sourceDirectory, source.name)
            Files.createLink(
                hardLinkedSource.toPath(),
                movedSource.toPath()
            )

            val failure = assertThrows(IllegalStateException::class.java) {
                snapshot.requireUnchanged("Source save")
            }

            assertTrue(failure.message!!.contains("directory identity changed"))
            assertArrayEquals(originalBytes, movedSource.readBytes())
            assertArrayEquals(originalBytes, hardLinkedSource.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun matchingBytesWithChangedMetadataFailClosed() {
        val directory = Files.createTempDirectory("gen6-source-metadata").toFile()
        try {
            val source = File(directory, "source.sav").apply {
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val snapshot = Gen6SourceFileSnapshot.capture(source)
            Files.setLastModifiedTime(
                source.toPath(),
                FileTime.fromMillis(source.lastModified() - 120_000L)
            )

            val failure = assertThrows(IllegalStateException::class.java) {
                snapshot.requireUnchanged("Source save")
            }

            assertTrue(failure.message!!.contains("metadata changed"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun replacementWithMatchingBytesAndMetadataFailsClosed() {
        val directory = Files.createTempDirectory("gen6-source-identity").toFile()
        try {
            val bytes = byteArrayOf(1, 2, 3)
            val source = File(directory, "source.sav").apply {
                writeBytes(bytes)
            }
            val originalLastModified = source.lastModified()
            val snapshot = Gen6SourceFileSnapshot.capture(source)
            val replacement = File(directory, "replacement.sav").apply {
                writeBytes(bytes)
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

            val failure = assertThrows(IllegalStateException::class.java) {
                snapshot.requireUnchanged("Source save")
            }

            assertTrue(failure.message!!.contains("identity changed"))
            assertArrayEquals(bytes, source.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }
}
