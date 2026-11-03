package com.example.pokemonsavemanager

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6VersionExclusiveManifestSourceTest {
    private val header =
        "id\tdestination\tspecies\trecord_sha256\tevidence"

    @Test
    fun tsvManifestLoadsAuditedRowsInFileOrder() {
        val directory = Files.createTempDirectory("gen6-exclusive-manifest").toFile()
        try {
            val manifestFile = File(directory, "gen6-exclusives.tsv")
            manifestFile.writeText(
                listOf(
                    header,
                    "lotad-as\tALPHA_SAPPHIRE\t270\t${"a".repeat(64)}\tevidence:oras:lotad",
                    "seedot-or\tOMEGA_RUBY\t273\t${"b".repeat(64)}\tevidence:oras:seedot"
                ).joinToString("\n")
            )

            val manifests =
                Gen6TsvVersionExclusiveManifestSource(manifestFile).read()

            assertEquals(
                listOf(
                    Gen6VersionExclusiveEvidenceManifest(
                        "lotad-as",
                        Gen6ConverterGame.ALPHA_SAPPHIRE,
                        270,
                        "a".repeat(64),
                        "evidence:oras:lotad"
                    ),
                    Gen6VersionExclusiveEvidenceManifest(
                        "seedot-or",
                        Gen6ConverterGame.OMEGA_RUBY,
                        273,
                        "b".repeat(64),
                        "evidence:oras:seedot"
                    )
                ),
                manifests
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun tsvManifestRejectsEvidenceChangedAfterCapture() {
        val directory =
            Files.createTempDirectory("gen6-exclusive-manifest-freshness").toFile()
        try {
            val manifestFile = File(directory, "gen6-exclusives.tsv")
            manifestFile.writeText(
                "$header\nlotad-as\tALPHA_SAPPHIRE\t270\t" +
                    "${"a".repeat(64)}\tevidence:oras:lotad"
            )
            val source =
                Gen6TsvVersionExclusiveManifestSource.withAfterCaptureForTest(
                    manifestFile
                ) {
                    manifestFile.writeText(
                        "$header\nlotad-as\tALPHA_SAPPHIRE\t270\t" +
                            "${"b".repeat(64)}\tevidence:oras:lotad"
                    )
                }

            val failure = assertThrows(IllegalStateException::class.java) {
                source.read()
            }

            assertTrue(
                failure.message!!.contains(
                    "Version-exclusive evidence manifest bytes changed"
                )
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun tsvManifestRejectsMalformedUtf8WithoutChangingEvidence() {
        val directory =
            Files.createTempDirectory("gen6-exclusive-manifest-utf8").toFile()
        try {
            val manifestFile = File(directory, "gen6-exclusives.tsv")
            val malformedBytes = (
                "$header\nlotad-as\tALPHA_SAPPHIRE\t270\t" +
                    "${"a".repeat(64)}\t"
                ).toByteArray(Charsets.UTF_8) +
                byteArrayOf(0xC3.toByte(), 0x28)
            manifestFile.writeBytes(malformedBytes)

            val failure = assertThrows(IllegalArgumentException::class.java) {
                Gen6TsvVersionExclusiveManifestSource(manifestFile).read()
            }

            assertTrue(failure.message!!.contains("valid UTF-8"))
            assertTrue(malformedBytes.contentEquals(manifestFile.readBytes()))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun programmaticManifestRejectsUnsafeRecordIdentity() {
        val invalidIds = listOf("../lotad", "Lotad AS", "lotad--as")

        for (id in invalidIds) {
            val failure = assertThrows(IllegalArgumentException::class.java) {
                Gen6VersionExclusiveEvidenceManifest(
                    id = id,
                    destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                    species = 270,
                    recordFingerprint = "a".repeat(64),
                    encounterEvidenceReference = "evidence:oras:lotad"
                )
            }

            assertTrue(failure.message!!.contains("safe lowercase record id"))
        }
    }

    @Test
    fun tsvManifestRejectsLinkedInstallationDirectory() {
        val realDirectory =
            Files.createTempDirectory("gen6-exclusive-manifest-directory").toFile()
        val linkedDirectory =
            File(realDirectory.parentFile, "${realDirectory.name}-link")
        try {
            val manifestFile = File(realDirectory, "gen6-exclusives.tsv")
            manifestFile.writeText(
                "$header\nlotad-as\tALPHA_SAPPHIRE\t270\t" +
                    "${"a".repeat(64)}\tevidence:oras:lotad"
            )
            Files.createSymbolicLink(
                linkedDirectory.toPath(),
                realDirectory.toPath()
            )

            val failure = assertThrows(IllegalArgumentException::class.java) {
                Gen6TsvVersionExclusiveManifestSource(
                    File(linkedDirectory, manifestFile.name)
                ).read()
            }

            assertTrue(failure.message!!.contains("directory"))
            assertTrue(failure.message!!.contains("symbolic link"))
        } finally {
            Files.deleteIfExists(linkedDirectory.toPath())
            realDirectory.deleteRecursively()
        }
    }

    @Test
    fun tsvManifestRejectsDirectoryReplacedByLinkAfterCapture() {
        val declaredDirectory =
            Files.createTempDirectory("gen6-exclusive-manifest-swap").toFile()
        val preservedDirectory =
            File(declaredDirectory.parentFile, "${declaredDirectory.name}-preserved")
        val replacementDirectory =
            Files.createTempDirectory("gen6-exclusive-manifest-replacement").toFile()
        val manifestName = "gen6-exclusives.tsv"
        val manifestBytes = (
            "$header\nlotad-as\tALPHA_SAPPHIRE\t270\t" +
                "${"a".repeat(64)}\tevidence:oras:lotad"
            ).toByteArray()
        try {
            val manifestFile = File(declaredDirectory, manifestName).apply {
                writeBytes(manifestBytes)
            }
            val replacementManifest =
                File(replacementDirectory, manifestName).apply {
                    writeBytes(manifestBytes)
                }
            val source =
                Gen6TsvVersionExclusiveManifestSource.withAfterCaptureForTest(
                    manifestFile
                ) {
                    Files.move(
                        declaredDirectory.toPath(),
                        preservedDirectory.toPath()
                    )
                    Files.createSymbolicLink(
                        declaredDirectory.toPath(),
                        replacementDirectory.toPath()
                    )
                }

            val failure = assertThrows(IllegalArgumentException::class.java) {
                source.read()
            }

            assertTrue(failure.message!!.contains("directory"))
            assertTrue(failure.message!!.contains("symbolic link"))
            assertTrue(
                manifestBytes.contentEquals(
                    File(preservedDirectory, manifestName).readBytes()
                )
            )
            assertTrue(
                manifestBytes.contentEquals(replacementManifest.readBytes())
            )
        } finally {
            if (Files.isSymbolicLink(declaredDirectory.toPath())) {
                Files.deleteIfExists(declaredDirectory.toPath())
            } else {
                declaredDirectory.deleteRecursively()
            }
            preservedDirectory.deleteRecursively()
            replacementDirectory.deleteRecursively()
        }
    }

    @Test
    fun tsvManifestRejectsRetargetedAncestorAfterCapture() {
        val root =
            Files.createTempDirectory("gen6-manifest-ancestor-link").toFile()
        val first = File(root, "first").apply { check(mkdir()) }
        val second = File(root, "second").apply { check(mkdir()) }
        val firstConfig = File(first, "config").apply { check(mkdir()) }
        val secondConfig = File(second, "config").apply { check(mkdir()) }
        val active = File(root, "active")
        val manifestName = "gen6-exclusives.tsv"
        val manifestBytes = (
            "$header\nlotad-as\tALPHA_SAPPHIRE\t270\t" +
                "${"a".repeat(64)}\tevidence:oras:lotad"
            ).toByteArray()
        try {
            File(firstConfig, manifestName).writeBytes(manifestBytes)
            File(secondConfig, manifestName).writeBytes(manifestBytes)
            Files.createSymbolicLink(active.toPath(), first.toPath())
            val declaredManifest =
                File(active, "config/$manifestName")
            val source =
                Gen6TsvVersionExclusiveManifestSource.withAfterCaptureForTest(
                    declaredManifest
                ) {
                    Files.delete(active.toPath())
                    Files.createSymbolicLink(active.toPath(), second.toPath())
                }

            val failure = assertThrows(IllegalArgumentException::class.java) {
                source.read()
            }

            assertTrue(failure.message!!.contains("path identity changed"))
            assertTrue(
                manifestBytes.contentEquals(
                    File(firstConfig, manifestName).readBytes()
                )
            )
            assertTrue(
                manifestBytes.contentEquals(
                    File(secondConfig, manifestName).readBytes()
                )
            )
        } finally {
            Files.deleteIfExists(active.toPath())
            root.deleteRecursively()
        }
    }

    @Test
    fun tsvManifestRejectsSymlinksUnsafeIdsAndMalformedRows() {
        val directory = Files.createTempDirectory("gen6-exclusive-manifest-guard").toFile()
        val outside = Files.createTempFile("gen6-exclusive-manifest", ".tsv").toFile()
        try {
            outside.writeText(
                "$header\nlotad-as\tALPHA_SAPPHIRE\t270\t" +
                    "${"a".repeat(64)}\tevidence:oras:lotad"
            )
            val manifestFile = File(directory, "gen6-exclusives.tsv")
            Files.createSymbolicLink(manifestFile.toPath(), outside.toPath())

            val linked = assertThrows(IllegalArgumentException::class.java) {
                Gen6TsvVersionExclusiveManifestSource(manifestFile).read()
            }
            assertTrue(linked.message!!.contains("symbolic link"))

            Files.delete(manifestFile.toPath())
            manifestFile.writeText(
                "$header\n../lotad\tALPHA_SAPPHIRE\t270\t" +
                    "${"a".repeat(64)}\tevidence:oras:lotad"
            )
            val unsafe = assertThrows(IllegalArgumentException::class.java) {
                Gen6TsvVersionExclusiveManifestSource(manifestFile).read()
            }
            assertTrue(unsafe.message!!.contains("unsafe id"))

            manifestFile.writeText(
                "$header\nlotad-as\tNOT_A_GAME\t270\t" +
                    "${"a".repeat(64)}\tevidence:oras:lotad"
            )
            val invalidDestination =
                assertThrows(IllegalArgumentException::class.java) {
                    Gen6TsvVersionExclusiveManifestSource(manifestFile).read()
                }
            assertTrue(invalidDestination.message!!.contains("invalid destination"))
        } finally {
            directory.deleteRecursively()
            outside.delete()
        }
    }

    @Test
    fun tsvManifestRejectsNonCanonicalSpeciesWithoutChangingEvidence() {
        val directory =
            Files.createTempDirectory("gen6-exclusive-species-text").toFile()
        try {
            val manifestFile = File(directory, "gen6-exclusives.tsv")
            val manifestBytes = listOf(
                header,
                "lotad-as\tALPHA_SAPPHIRE\t0270\t${"a".repeat(64)}\tevidence:one"
            ).joinToString("\n").toByteArray()
            manifestFile.writeBytes(manifestBytes)

            val failure = assertThrows(IllegalArgumentException::class.java) {
                Gen6TsvVersionExclusiveManifestSource(manifestFile).read()
            }

            assertTrue(
                failure.message!!.contains("canonical decimal species")
            )
            assertTrue(manifestBytes.contentEquals(manifestFile.readBytes()))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun tsvManifestRejectsBlankRowsWithoutChangingEvidence() {
        val directory =
            Files.createTempDirectory("gen6-exclusive-blank-row").toFile()
        try {
            val manifestFile = File(directory, "gen6-exclusives.tsv")
            val manifestBytes = listOf(
                header,
                "lotad-as\tALPHA_SAPPHIRE\t270\t${"a".repeat(64)}\tevidence:one",
                "",
                "seedot-or\tOMEGA_RUBY\t273\t${"b".repeat(64)}\tevidence:two"
            ).joinToString("\n").toByteArray()
            manifestFile.writeBytes(manifestBytes)

            val failure = assertThrows(IllegalArgumentException::class.java) {
                Gen6TsvVersionExclusiveManifestSource(manifestFile).read()
            }

            assertTrue(failure.message!!.contains("line 3 must not be blank"))
            assertTrue(manifestBytes.contentEquals(manifestFile.readBytes()))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun tsvManifestRejectsDuplicateFingerprintsWithoutChangingEvidence() {
        val directory =
            Files.createTempDirectory("gen6-exclusive-fingerprints").toFile()
        try {
            val manifestFile = File(directory, "gen6-exclusives.tsv")
            val fingerprint = "a".repeat(64)
            val manifestBytes = listOf(
                header,
                "lotad-as\tALPHA_SAPPHIRE\t270\t$fingerprint\tevidence:one",
                "seedot-or\tOMEGA_RUBY\t273\t$fingerprint\tevidence:two"
            ).joinToString("\n").toByteArray()
            manifestFile.writeBytes(manifestBytes)

            val failure = assertThrows(IllegalArgumentException::class.java) {
                Gen6TsvVersionExclusiveManifestSource(manifestFile).read()
            }

            assertTrue(
                failure.message!!.contains("duplicate record fingerprints")
            )
            assertTrue(manifestBytes.contentEquals(manifestFile.readBytes()))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun tsvManifestRejectsDuplicateIdsAndDestinationSpecies() {
        val directory = Files.createTempDirectory("gen6-exclusive-duplicates").toFile()
        try {
            val manifestFile = File(directory, "gen6-exclusives.tsv")
            manifestFile.writeText(
                listOf(
                    header,
                    "lotad-as\tALPHA_SAPPHIRE\t270\t${"a".repeat(64)}\tevidence:one",
                    "lotad-as\tALPHA_SAPPHIRE\t302\t${"b".repeat(64)}\tevidence:two"
                ).joinToString("\n")
            )
            val duplicateId = assertThrows(IllegalArgumentException::class.java) {
                Gen6TsvVersionExclusiveManifestSource(manifestFile).read()
            }
            assertTrue(duplicateId.message!!.contains("duplicate ids"))

            manifestFile.writeText(
                listOf(
                    header,
                    "lotad-as-a\tALPHA_SAPPHIRE\t270\t${"a".repeat(64)}\tevidence:one",
                    "lotad-as-b\tALPHA_SAPPHIRE\t270\t${"b".repeat(64)}\tevidence:two"
                ).joinToString("\n")
            )
            val duplicateSpecies =
                assertThrows(IllegalArgumentException::class.java) {
                    Gen6TsvVersionExclusiveManifestSource(manifestFile).read()
                }
            assertTrue(
                duplicateSpecies.message!!.contains("duplicate destination species")
            )
        } finally {
            directory.deleteRecursively()
        }
    }
}
