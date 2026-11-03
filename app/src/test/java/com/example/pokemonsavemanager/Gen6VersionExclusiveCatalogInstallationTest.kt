package com.example.pokemonsavemanager

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6VersionExclusiveCatalogInstallationTest {
    private fun record(species: Int, marker: Int): ByteArray {
        val plain = ByteArray(Gen6Pk6Adapter.STORED_SIZE)
        plain[0x08] = (species and 0xFF).toByte()
        plain[0x09] = (species ushr 8).toByte()
        plain[0x10] = marker.toByte()
        val checksum = species + marker
        plain[0x06] = (checksum and 0xFF).toByte()
        plain[0x07] = (checksum ushr 8).toByte()
        return Gen6Pk6Adapter.encode(Gen6Pk6Adapter.decode(plain))
    }

    private fun writeManifest(
        directory: File,
        fingerprint: String
    ): File = File(directory, "catalog.tsv").apply {
        writeText(
            "id\tdestination\tspecies\trecord_sha256\tevidence\n" +
                "lotad-as\tALPHA_SAPPHIRE\t270\t${fingerprint}\t" +
                "fixture:alpha-sapphire:lotad\n",
            Charsets.UTF_8
        )
    }

    private fun installFor(
        destinationGame: Gen6ConverterGame
    ): Gen6VersionExclusiveCatalogInstallation {
        val directory = Files.createTempDirectory("gen6-exclusive-install").toFile()
        val approved = record(270, 11)
        File(directory, "lotad-as.pk6").writeBytes(approved)
        val manifest = writeManifest(
            directory,
            gen6VersionExclusiveRecordFingerprint(approved)
        )
        return Gen6VersionExclusiveCatalogInstaller(
            manifestSource = Gen6TsvVersionExclusiveManifestSource(manifest),
            recordSource = Gen6DirectoryVersionExclusiveRecordSource(directory),
            legalityVerifier = GEN6_TEST_EXCLUSIVE_VERIFIER
        ).installFor(destinationGame)
    }

    @Test
    fun completeInstalledEvidenceExposesOneDestinationBoundCatalog() {
        val approved = record(270, 11)
        val installation = installFor(Gen6ConverterGame.ALPHA_SAPPHIRE)

        assertTrue(
            installation is Gen6VersionExclusiveCatalogInstallation.Available
        )
        val available =
            installation as Gen6VersionExclusiveCatalogInstallation.Available
        assertEquals(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            available.destinationGame
        )
        val catalog =
            available.catalogFor(Gen6ConverterGame.ALPHA_SAPPHIRE)
        assertEquals(
            listOf("lotad-as"),
            catalog.choicesFor(Gen6ConverterGame.ALPHA_SAPPHIRE).map { it.id }
        )
        assertArrayEquals(
            approved,
            catalog.resolve(
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                listOf("lotad-as")
            ).single()
        )
    }

    @Test
    fun successfulInstallationContainsOnlyTheReviewedDestination() {
        val alphaRecord = record(270, 11)
        val omegaRecord = record(273, 12)
        val records = mapOf(
            "lotad-as" to alphaRecord,
            "seedot-or" to omegaRecord
        )
        val manifests = listOf(
            Gen6VersionExclusiveEvidenceManifest(
                id = "lotad-as",
                destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                species = 270,
                recordFingerprint =
                    gen6VersionExclusiveRecordFingerprint(alphaRecord),
                encounterEvidenceReference = "fixture:alpha-sapphire:lotad"
            ),
            Gen6VersionExclusiveEvidenceManifest(
                id = "seedot-or",
                destinationGame = Gen6ConverterGame.OMEGA_RUBY,
                species = 273,
                recordFingerprint =
                    gen6VersionExclusiveRecordFingerprint(omegaRecord),
                encounterEvidenceReference = "fixture:omega-ruby:seedot"
            )
        )

        val installation = Gen6VersionExclusiveCatalogInstaller(
            manifestSource = Gen6VersionExclusiveManifestSource { manifests },
            recordSource = Gen6VersionExclusiveRecordSource { id ->
                records[id]?.copyOf()
            },
            legalityVerifier = GEN6_TEST_EXCLUSIVE_VERIFIER
        ).installFor(Gen6ConverterGame.ALPHA_SAPPHIRE)
            as Gen6VersionExclusiveCatalogInstallation.Available
        val catalog =
            installation.catalogFor(Gen6ConverterGame.ALPHA_SAPPHIRE)

        assertEquals(
            listOf("lotad-as"),
            catalog.choicesFor(Gen6ConverterGame.ALPHA_SAPPHIRE).map { it.id }
        )
        assertTrue(catalog.choicesFor(Gen6ConverterGame.OMEGA_RUBY).isEmpty())
        val failure = assertThrows(IllegalArgumentException::class.java) {
            catalog.resolve(
                Gen6ConverterGame.OMEGA_RUBY,
                listOf("seedot-or")
            )
        }
        assertTrue(failure.message!!.contains("not in the verified catalog"))
    }

    @Test
    fun successfulInstallationRejectsReuseAfterDestinationChanges() {
        val installation = installFor(Gen6ConverterGame.ALPHA_SAPPHIRE)
            as Gen6VersionExclusiveCatalogInstallation.Available

        val failure = assertThrows(IllegalArgumentException::class.java) {
            installation.catalogFor(Gen6ConverterGame.OMEGA_RUBY)
        }

        assertTrue(failure.message!!.contains("verified for Alpha Sapphire"))
        assertTrue(failure.message!!.contains("not Omega Ruby"))
    }

    @Test
    fun catalogForAnotherDestinationDoesNotEnableCurrentDestination() {
        val installation = installFor(Gen6ConverterGame.OMEGA_RUBY)

        assertEquals(
            Gen6VersionExclusiveCatalogInstallation.Unavailable(
                "No evidence-backed destination-exclusive Pokémon are " +
                    "installed for Omega Ruby."
            ),
            installation
        )
    }

    @Test
    fun emptyProgrammaticManifestCannotCreateAvailableCatalog() {
        val installation = Gen6VersionExclusiveCatalogInstaller(
            manifestSource = Gen6VersionExclusiveManifestSource { emptyList() },
            recordSource = Gen6VersionExclusiveRecordSource { null },
            legalityVerifier = GEN6_TEST_EXCLUSIVE_VERIFIER
        ).installFor(Gen6ConverterGame.ALPHA_SAPPHIRE)

        assertEquals(
            Gen6VersionExclusiveCatalogInstallation.Unavailable(
                "Version-exclusive evidence manifest contains no records."
            ),
            installation
        )
    }

    @Test
    fun manifestChangedDuringInstallationLeavesNoAvailableCatalog() {
        val approved = record(270, 11)
        val firstManifest = Gen6VersionExclusiveEvidenceManifest(
            id = "lotad-as",
            destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
            species = 270,
            recordFingerprint =
                gen6VersionExclusiveRecordFingerprint(approved),
            encounterEvidenceReference = "fixture:alpha-sapphire:lotad"
        )
        val changedManifest = firstManifest.copy(
            encounterEvidenceReference = "fixture:alpha-sapphire:lotad-updated"
        )
        var reads = 0
        val installation = Gen6VersionExclusiveCatalogInstaller(
            manifestSource = Gen6VersionExclusiveManifestSource {
                reads += 1
                if (reads == 1) {
                    listOf(firstManifest)
                } else {
                    listOf(changedManifest)
                }
            },
            recordSource = Gen6VersionExclusiveRecordSource { id ->
                approved.copyOf().takeIf { id == "lotad-as" }
            },
            legalityVerifier = GEN6_TEST_EXCLUSIVE_VERIFIER
        ).installFor(Gen6ConverterGame.ALPHA_SAPPHIRE)

        assertEquals(2, reads)
        assertEquals(
            Gen6VersionExclusiveCatalogInstallation.Unavailable(
                "Version-exclusive evidence manifest changed during catalog installation."
            ),
            installation
        )
    }

    @Test
    fun reusedMutableManifestListCannotHideMidInstallationChange() {
        val approved = record(270, 11)
        val original = Gen6VersionExclusiveEvidenceManifest(
            id = "lotad-as",
            destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
            species = 270,
            recordFingerprint =
                gen6VersionExclusiveRecordFingerprint(approved),
            encounterEvidenceReference = "fixture:alpha-sapphire:lotad"
        )
        val manifests = mutableListOf(original)
        val installation = Gen6VersionExclusiveCatalogInstaller(
            manifestSource = Gen6VersionExclusiveManifestSource { manifests },
            recordSource = Gen6VersionExclusiveRecordSource { id ->
                manifests[0] = original.copy(
                    encounterEvidenceReference =
                        "fixture:alpha-sapphire:lotad-updated"
                )
                approved.copyOf().takeIf { id == "lotad-as" }
            },
            legalityVerifier = GEN6_TEST_EXCLUSIVE_VERIFIER
        ).installFor(Gen6ConverterGame.ALPHA_SAPPHIRE)

        assertEquals(
            Gen6VersionExclusiveCatalogInstallation.Unavailable(
                "Version-exclusive evidence manifest changed during catalog installation."
            ),
            installation
        )
    }

    @Test
    fun recordSubstitutedDuringInstallationLeavesNoAvailableCatalog() {
        val approved = record(270, 11)
        val substituted = record(270, 12)
        val manifest = Gen6VersionExclusiveEvidenceManifest(
            id = "lotad-as",
            destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
            species = 270,
            recordFingerprint =
                gen6VersionExclusiveRecordFingerprint(approved),
            encounterEvidenceReference = "fixture:alpha-sapphire:lotad"
        )
        var reads = 0
        val installation = Gen6VersionExclusiveCatalogInstaller(
            manifestSource = Gen6VersionExclusiveManifestSource {
                listOf(manifest)
            },
            recordSource = Gen6VersionExclusiveRecordSource { id ->
                if (id != "lotad-as") {
                    null
                } else {
                    reads += 1
                    if (reads == 1) approved.copyOf() else substituted.copyOf()
                }
            },
            legalityVerifier = GEN6_TEST_EXCLUSIVE_VERIFIER
        ).installFor(Gen6ConverterGame.ALPHA_SAPPHIRE)

        assertEquals(2, reads)
        assertEquals(
            Gen6VersionExclusiveCatalogInstallation.Unavailable(
                "Version-exclusive record lotad-as changed during catalog installation."
            ),
            installation
        )
    }

    @Test
    fun recordRemovedDuringInstallationLeavesNoAvailableCatalog() {
        val approved = record(270, 11)
        val manifest = Gen6VersionExclusiveEvidenceManifest(
            id = "lotad-as",
            destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
            species = 270,
            recordFingerprint =
                gen6VersionExclusiveRecordFingerprint(approved),
            encounterEvidenceReference = "fixture:alpha-sapphire:lotad"
        )
        var reads = 0
        val installation = Gen6VersionExclusiveCatalogInstaller(
            manifestSource = Gen6VersionExclusiveManifestSource {
                listOf(manifest)
            },
            recordSource = Gen6VersionExclusiveRecordSource { id ->
                if (id != "lotad-as") {
                    null
                } else {
                    reads += 1
                    approved.copyOf().takeIf { reads == 1 }
                }
            },
            legalityVerifier = GEN6_TEST_EXCLUSIVE_VERIFIER
        ).installFor(Gen6ConverterGame.ALPHA_SAPPHIRE)

        assertEquals(2, reads)
        assertEquals(
            Gen6VersionExclusiveCatalogInstallation.Unavailable(
                "Version-exclusive record lotad-as changed during catalog installation."
            ),
            installation
        )
    }

    @Test
    fun substitutedRecordLeavesNoPartiallyAvailableCatalog() {
        val directory = Files.createTempDirectory("gen6-exclusive-reject").toFile()
        val approved = record(270, 11)
        File(directory, "lotad-as.pk6").writeBytes(record(270, 12))
        val manifest = writeManifest(
            directory,
            gen6VersionExclusiveRecordFingerprint(approved)
        )
        var legalityCalls = 0
        val verifier = Gen6VersionExclusiveLegalityVerifier {
                destination, decoded, _ ->
            legalityCalls += 1
            Gen6VersionExclusiveLegalityResult(
                destinationGame = destination,
                species = decoded.species,
                encounterLegal = true,
                movesLegal = true,
                abilityLegal = true,
                formLegal = true,
                originMetadataLegal = true
            )
        }

        val installation = Gen6VersionExclusiveCatalogInstaller(
            manifestSource = Gen6TsvVersionExclusiveManifestSource(manifest),
            recordSource = Gen6DirectoryVersionExclusiveRecordSource(directory),
            legalityVerifier = verifier
        ).installFor(Gen6ConverterGame.ALPHA_SAPPHIRE)

        assertTrue(
            installation is Gen6VersionExclusiveCatalogInstallation.Unavailable
        )
        val unavailable =
            installation as Gen6VersionExclusiveCatalogInstallation.Unavailable
        assertTrue(unavailable.reason.contains("audited fingerprint"))
        assertEquals(0, legalityCalls)
    }

    @Test
    fun unsafeValidationFailureCannotSpoofUnavailableReason() {
        val unsafeReasons = listOf(
            "unsafe\nreason",
            "unsafe\u202Ereason",
            " padded reason "
        )

        for (unsafeReason in unsafeReasons) {
            val installation = Gen6VersionExclusiveCatalogInstaller(
                manifestSource = Gen6VersionExclusiveManifestSource {
                    throw IllegalArgumentException(unsafeReason)
                },
                recordSource = Gen6VersionExclusiveRecordSource { null },
                legalityVerifier = GEN6_TEST_EXCLUSIVE_VERIFIER
            ).installFor(Gen6ConverterGame.ALPHA_SAPPHIRE)

            assertEquals(
                Gen6VersionExclusiveCatalogInstallation.Unavailable(
                    "Version-exclusive catalog could not be installed."
                ),
                installation
            )
        }
    }

    @Test
    fun catalogFailureReasonLengthIsBoundedBeforeUiExposure() {
        fun installWith(reason: String): Gen6VersionExclusiveCatalogInstallation =
            Gen6VersionExclusiveCatalogInstaller(
                manifestSource = Gen6VersionExclusiveManifestSource {
                    throw IllegalArgumentException(reason)
                },
                recordSource = Gen6VersionExclusiveRecordSource { null },
                legalityVerifier = GEN6_TEST_EXCLUSIVE_VERIFIER
            ).installFor(Gen6ConverterGame.ALPHA_SAPPHIRE)

        val maximumAuditableReason = "x".repeat(512)
        assertEquals(
            Gen6VersionExclusiveCatalogInstallation.Unavailable(
                maximumAuditableReason
            ),
            installWith(maximumAuditableReason)
        )
        assertEquals(
            Gen6VersionExclusiveCatalogInstallation.Unavailable(
                "Version-exclusive catalog could not be installed."
            ),
            installWith("x".repeat(513))
        )
    }

    @Test
    fun unexpectedSourceFailureIsFailClosedWithoutLeakingItsDetail() {
        val installation = Gen6VersionExclusiveCatalogInstaller(
            manifestSource = Gen6VersionExclusiveManifestSource {
                throw IOException("/private/user/path/catalog.tsv")
            },
            recordSource = Gen6VersionExclusiveRecordSource { null },
            legalityVerifier = GEN6_TEST_EXCLUSIVE_VERIFIER
        ).installFor(Gen6ConverterGame.ALPHA_SAPPHIRE)

        assertEquals(
            Gen6VersionExclusiveCatalogInstallation.Unavailable(
                "Version-exclusive catalog could not be installed."
            ),
            installation
        )
    }
}
