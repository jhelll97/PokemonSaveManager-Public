package com.example.pokemonsavemanager

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6VersionExclusiveSelectionTest {
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

    @Test
    fun plannerRequiresExplicitOptInAndExplicitSelectionTogether() {
        val selected = listOf("lotad-as")
        assertTrue(
            Gen6SaveConverterPlanner.plan(
                Gen6ConverterRequest(
                    Gen6ConverterGame.OMEGA_RUBY,
                    Gen6ConverterGame.ALPHA_SAPPHIRE,
                    includeVersionExclusives = true,
                    selectedVersionExclusiveIds = selected
                )
            ).canExport
        )
        assertFalse(
            Gen6SaveConverterPlanner.plan(
                Gen6ConverterRequest(
                    Gen6ConverterGame.OMEGA_RUBY,
                    Gen6ConverterGame.ALPHA_SAPPHIRE,
                    selectedVersionExclusiveIds = selected
                )
            ).canExport
        )
    }

    @Test
    fun explicitSelectionsResolveInUserOrderAsDefensiveCopies() {
        val first = record(270, 11)
        val second = record(302, 22)
        val catalog = Gen6VersionExclusiveCatalog(
            listOf(
                Gen6VersionExclusiveEntry(
                    "lotad-as", Gen6ConverterGame.ALPHA_SAPPHIRE,
                    270, first, "fixture:lotad-as"
                ),
                Gen6VersionExclusiveEntry(
                    "sableye-as", Gen6ConverterGame.ALPHA_SAPPHIRE,
                    302, second, "fixture:sableye-as"
                )
            ),
            GEN6_TEST_EXCLUSIVE_VERIFIER
        )

        val selected = catalog.resolve(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            listOf("sableye-as", "lotad-as")
        )

        assertEquals(2, selected.size)
        assertArrayEquals(second, selected[0])
        assertArrayEquals(first, selected[1])
        selected[0][0] = (selected[0][0].toInt() xor 0x7F).toByte()
        assertArrayEquals(
            second,
            catalog.resolve(
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                listOf("sableye-as")
            ).single()
        )
    }

    @Test
    fun catalogResolutionKeepsOneSnapshotOfCallerSelection() {
        val lotad = record(270, 11)
        val catalog = Gen6VersionExclusiveCatalog(
            listOf(
                Gen6VersionExclusiveEntry(
                    "lotad-as",
                    Gen6ConverterGame.ALPHA_SAPPHIRE,
                    270,
                    lotad,
                    "fixture:lotad-as"
                )
            ),
            GEN6_TEST_EXCLUSIVE_VERIFIER
        )

        fun oneShotSelection(): List<String> {
            val backing = mutableListOf("lotad-as")
            return object : AbstractList<String>() {
                override val size: Int
                    get() = backing.size

                override fun get(index: Int): String {
                    val selected = backing[index]
                    backing.clear()
                    return selected
                }
            }
        }

        val resolved = catalog.resolve(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            oneShotSelection()
        )
        val choices = catalog.selectedChoicesFor(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            oneShotSelection()
        )

        assertEquals(1, resolved.size)
        assertArrayEquals(lotad, resolved.single())
        assertEquals(listOf("lotad-as"), choices.map { it.id })
        assertEquals(listOf(270), choices.map { it.species })
    }

    @Test
    fun choicesExposeOnlyDestinationMetadataInCatalogOrder() {
        val catalog = Gen6VersionExclusiveCatalog(
            listOf(
                Gen6VersionExclusiveEntry(
                    "lotad-as", Gen6ConverterGame.ALPHA_SAPPHIRE,
                    270, record(270, 11), "fixture:lotad-as"
                ),
                Gen6VersionExclusiveEntry(
                    "seedot-or", Gen6ConverterGame.OMEGA_RUBY,
                    273, record(273, 12), "fixture:seedot-or"
                ),
                Gen6VersionExclusiveEntry(
                    "sableye-as", Gen6ConverterGame.ALPHA_SAPPHIRE,
                    302, record(302, 13), "fixture:sableye-as"
                )
            ),
            GEN6_TEST_EXCLUSIVE_VERIFIER
        )

        assertEquals(
            listOf(
                Gen6VersionExclusiveChoice(
                    "lotad-as", Gen6ConverterGame.ALPHA_SAPPHIRE,
                    270, "fixture:lotad-as",
                    gen6VersionExclusiveRecordFingerprint(record(270, 11))
                ),
                Gen6VersionExclusiveChoice(
                    "sableye-as", Gen6ConverterGame.ALPHA_SAPPHIRE,
                    302, "fixture:sableye-as",
                    gen6VersionExclusiveRecordFingerprint(record(302, 13))
                )
            ),
            catalog.choicesFor(Gen6ConverterGame.ALPHA_SAPPHIRE)
        )
        assertTrue(catalog.choicesFor(Gen6ConverterGame.Y).isEmpty())
    }

    @Test
    fun missingDuplicateAndWrongDestinationSelectionsFailClosed() {
        val catalog = Gen6VersionExclusiveCatalog(
            listOf(
                Gen6VersionExclusiveEntry(
                    "lotad-as", Gen6ConverterGame.ALPHA_SAPPHIRE,
                    270, record(270, 11), "fixture:lotad-as"
                )
            ),
            GEN6_TEST_EXCLUSIVE_VERIFIER
        )

        assertThrows(IllegalArgumentException::class.java) {
            catalog.resolve(Gen6ConverterGame.ALPHA_SAPPHIRE, listOf("missing"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            catalog.resolve(
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                listOf("lotad-as", "lotad-as")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            catalog.resolve(Gen6ConverterGame.OMEGA_RUBY, listOf("lotad-as"))
        }
    }

    @Test
    fun duplicateSelectionIdentifiesIdBeforeLegalityRevalidation() {
        var legalityCalls = 0
        val catalog = Gen6VersionExclusiveCatalog(
            listOf(
                Gen6VersionExclusiveEntry(
                    "lotad-as",
                    Gen6ConverterGame.ALPHA_SAPPHIRE,
                    270,
                    record(270, 11),
                    "fixture:lotad-as"
                )
            ),
            Gen6VersionExclusiveLegalityVerifier {
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
        )
        assertEquals(1, legalityCalls)
        legalityCalls = 0

        val failure = assertThrows(IllegalArgumentException::class.java) {
            catalog.resolve(
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                listOf("lotad-as", "lotad-as")
            )
        }

        assertTrue(
            failure.message!!.contains(
                "Destination-version-exclusive selection lotad-as " +
                    "may be selected only once."
            )
        )
        assertEquals(0, legalityCalls)
    }

    @Test
    fun unsafeSelectionIdFailsBeforeLegalityRevalidationWithoutEcho() {
        var legalityCalls = 0
        val catalog = Gen6VersionExclusiveCatalog(
            listOf(
                Gen6VersionExclusiveEntry(
                    "lotad-as",
                    Gen6ConverterGame.ALPHA_SAPPHIRE,
                    270,
                    record(270, 11),
                    "fixture:lotad-as"
                )
            ),
            Gen6VersionExclusiveLegalityVerifier {
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
        )
        assertEquals(1, legalityCalls)
        legalityCalls = 0

        val failure = assertThrows(IllegalArgumentException::class.java) {
            catalog.resolve(
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                listOf("lotad-as\nforged")
            )
        }

        assertTrue(
            failure.message!!.contains(
                "selection ids must be safe lowercase record ids"
            )
        )
        assertEquals(false, failure.message!!.contains("\n"))
        assertEquals(0, legalityCalls)
    }

    @Test
    fun publicChoiceRejectsMalformedCatalogMetadata() {
        val fingerprint = gen6VersionExclusiveRecordFingerprint(record(270, 11))

        val blankId = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveChoice(
                "",
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                270,
                "fixture:lotad-as",
                fingerprint
            )
        }
        val unsafeId = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveChoice(
                "../lotad",
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                270,
                "fixture:lotad-as",
                fingerprint
            )
        }
        val invalidSpecies = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveChoice(
                "invalid-species",
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                722,
                "fixture:invalid-species",
                fingerprint
            )
        }
        val missingEvidence = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveChoice(
                "lotad-as",
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                270,
                "",
                fingerprint
            )
        }

        assertTrue(blankId.message!!.contains("id must not be blank"))
        assertTrue(unsafeId.message!!.contains("safe lowercase record id"))
        assertTrue(invalidSpecies.message!!.contains("invalid Gen 6 species"))
        assertTrue(missingEvidence.message!!.contains("requires legality evidence"))
    }

    @Test
    fun entryRejectsUnsafeIdentityAndSpeciesOutsideGen6NationalDex() {
        val unsafeId = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveEntry(
                "Lotad AS",
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                270,
                record(270, 11),
                "fixture:lotad-as"
            )
        }
        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveEntry(
                "invalid-species",
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                722,
                record(722, 11),
                "fixture:invalid-species"
            )
        }

        assertTrue(unsafeId.message!!.contains("safe lowercase record id"))
        assertTrue(failure.message!!.isNotBlank())
    }

    @Test
    fun legalityResultNotesMustBeTrimmedAndSingleLine() {
        val invalidNotes = listOf(
            " leading note",
            "trailing note ",
            "first line\nspoofed result"
        )

        for (note in invalidNotes) {
            val failure = assertThrows(IllegalArgumentException::class.java) {
                Gen6VersionExclusiveLegalityResult(
                    destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                    species = 270,
                    encounterLegal = false,
                    movesLegal = true,
                    abilityLegal = true,
                    formLegal = true,
                    originMetadataLegal = true,
                    note = note
                )
            }

            assertTrue(failure.message!!.contains("trimmed and single-line"))
        }
    }

    @Test
    fun auditMetadataLengthIsBoundedAtEveryBoundary() {
        val fingerprint = gen6VersionExclusiveRecordFingerprint(record(270, 11))
        val maximumAuditableEvidence = "e".repeat(512)
        val oversizedEvidence = "e".repeat(513)

        assertEquals(
            maximumAuditableEvidence,
            Gen6VersionExclusiveEvidenceManifest(
                id = "lotad-as",
                destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                species = 270,
                recordFingerprint = fingerprint,
                encounterEvidenceReference = maximumAuditableEvidence
            ).encounterEvidenceReference
        )
        assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveEvidenceManifest(
                id = "lotad-as",
                destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                species = 270,
                recordFingerprint = fingerprint,
                encounterEvidenceReference = oversizedEvidence
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveChoice(
                id = "lotad-as",
                destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                species = 270,
                legalityEvidence = oversizedEvidence,
                recordFingerprint = fingerprint
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveEntry(
                id = "lotad-as",
                destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                species = 270,
                encryptedPk6 = record(270, 11),
                legalityEvidence = oversizedEvidence
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveLegalityResult(
                destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                species = 270,
                encounterLegal = false,
                movesLegal = true,
                abilityLegal = true,
                formLegal = true,
                originMetadataLegal = true,
                note = oversizedEvidence
            )
        }
    }

    @Test
    fun auditMetadataRejectsUnicodeFormatControlsAtEveryBoundary() {
        val fingerprint = gen6VersionExclusiveRecordFingerprint(record(270, 11))
        val spoofedReference = "evidence:oras:\u202Etxt"

        val manifest = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveEvidenceManifest(
                id = "lotad-as",
                destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                species = 270,
                recordFingerprint = fingerprint,
                encounterEvidenceReference = spoofedReference
            )
        }
        val choice = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveChoice(
                id = "lotad-as",
                destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                species = 270,
                legalityEvidence = spoofedReference,
                recordFingerprint = fingerprint
            )
        }
        val entry = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveEntry(
                id = "lotad-as",
                destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                species = 270,
                encryptedPk6 = record(270, 11),
                legalityEvidence = spoofedReference
            )
        }
        val result = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveLegalityResult(
                destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                species = 270,
                encounterLegal = false,
                movesLegal = true,
                abilityLegal = true,
                formLegal = true,
                originMetadataLegal = true,
                note = spoofedReference
            )
        }

        assertTrue(manifest.message!!.contains("auditable"))
        assertTrue(choice.message!!.contains("trimmed and single-line"))
        assertTrue(entry.message!!.contains("trimmed and single-line"))
        assertTrue(result.message!!.contains("trimmed and single-line"))
    }

    @Test
    fun auditMetadataRejectsUnicodeLineSeparatorsAtEveryBoundary() {
        val fingerprint = gen6VersionExclusiveRecordFingerprint(record(270, 11))
        val separators = listOf('\u2028', '\u2029')

        for (separator in separators) {
            val spoofedReference = "evidence:oras:${separator}continued"

            assertThrows(IllegalArgumentException::class.java) {
                Gen6VersionExclusiveEvidenceManifest(
                    id = "lotad-as",
                    destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                    species = 270,
                    recordFingerprint = fingerprint,
                    encounterEvidenceReference = spoofedReference
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                Gen6VersionExclusiveChoice(
                    id = "lotad-as",
                    destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                    species = 270,
                    legalityEvidence = spoofedReference,
                    recordFingerprint = fingerprint
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                Gen6VersionExclusiveEntry(
                    id = "lotad-as",
                    destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                    species = 270,
                    encryptedPk6 = record(270, 11),
                    legalityEvidence = spoofedReference
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                Gen6VersionExclusiveLegalityResult(
                    destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                    species = 270,
                    encounterLegal = false,
                    movesLegal = true,
                    abilityLegal = true,
                    formLegal = true,
                    originMetadataLegal = true,
                    note = spoofedReference
                )
            }
        }
    }

    @Test
    fun evidenceReferencesMustBeTrimmedAndSingleLineAtEveryBoundary() {
        val fingerprint = gen6VersionExclusiveRecordFingerprint(record(270, 11))
        val invalidReferences = listOf(
            " evidence:oras:lotad",
            "evidence:oras:lotad\nspoofed"
        )

        for (reference in invalidReferences) {
            val manifest = assertThrows(IllegalArgumentException::class.java) {
                Gen6VersionExclusiveEvidenceManifest(
                    id = "lotad-as",
                    destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                    species = 270,
                    recordFingerprint = fingerprint,
                    encounterEvidenceReference = reference
                )
            }
            val choice = assertThrows(IllegalArgumentException::class.java) {
                Gen6VersionExclusiveChoice(
                    id = "lotad-as",
                    destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                    species = 270,
                    legalityEvidence = reference,
                    recordFingerprint = fingerprint
                )
            }
            val entry = assertThrows(IllegalArgumentException::class.java) {
                Gen6VersionExclusiveEntry(
                    id = "lotad-as",
                    destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                    species = 270,
                    encryptedPk6 = record(270, 11),
                    legalityEvidence = reference
                )
            }

            assertTrue(manifest.message!!.contains("single-line"))
            assertTrue(choice.message!!.contains("trimmed and single-line"))
            assertTrue(entry.message!!.contains("trimmed and single-line"))
        }
    }

    @Test
    fun catalogRejectsDuplicateRecordsBeforeLegalityChecks() {
        val sharedRecord = record(270, 11)
        val duplicateFingerprint =
            gen6VersionExclusiveRecordFingerprint(sharedRecord)
        val entries = listOf(
            Gen6VersionExclusiveEntry(
                "lotad-as",
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                270,
                sharedRecord,
                "fixture:lotad-as"
            ),
            Gen6VersionExclusiveEntry(
                "lotad-y",
                Gen6ConverterGame.Y,
                270,
                sharedRecord.copyOf(),
                "fixture:lotad-y"
            )
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

        val failure = assertThrows(IllegalStateException::class.java) {
            Gen6VersionExclusiveCatalog(entries, verifier)
        }

        assertTrue(
            failure.message!!.contains(
                "Duplicate Gen 6 version-exclusive catalog PK6 fingerprint " +
                    "$duplicateFingerprint."
            )
        )
        assertEquals(0, legalityCalls)
    }

    @Test
    fun catalogRejectsDuplicateIdsBeforeLegalityChecks() {
        val entries = listOf(
            Gen6VersionExclusiveEntry(
                "shared-exclusive",
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                270,
                record(270, 11),
                "fixture:lotad-as"
            ),
            Gen6VersionExclusiveEntry(
                "shared-exclusive",
                Gen6ConverterGame.Y,
                270,
                record(270, 11),
                "fixture:lotad-y"
            )
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

        val failure = assertThrows(IllegalStateException::class.java) {
            Gen6VersionExclusiveCatalog(entries, verifier)
        }

        assertTrue(failure.message!!.contains("Duplicate Gen 6 version-exclusive catalog id"))
        assertEquals(0, legalityCalls)
    }

    @Test
    fun catalogRejectsDuplicateSpeciesForOneDestination() {
        val entries = listOf(
            Gen6VersionExclusiveEntry(
                "lotad-as-a",
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                270,
                record(270, 11),
                "fixture:lotad-as-a"
            ),
            Gen6VersionExclusiveEntry(
                "lotad-as-b",
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                270,
                record(270, 12),
                "fixture:lotad-as-b"
            )
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            Gen6VersionExclusiveCatalog(entries, GEN6_TEST_EXCLUSIVE_VERIFIER)
        }

        assertTrue(failure.message!!.contains("Duplicate Gen 6 version-exclusive species 270"))
    }

    @Test
    fun catalogRejectsLegalityVerifierMutationOfDecodedRecord() {
        val approved = record(270, 11)
        val mutations: List<(Gen6Pk6Record) -> Unit> = listOf(
            { decoded ->
                decoded.encryptedBytes[0] =
                    (decoded.encryptedBytes[0].toInt() xor 0x7F).toByte()
            },
            { decoded ->
                decoded.decryptedBytes[0] =
                    (decoded.decryptedBytes[0].toInt() xor 0x7F).toByte()
            },
            { decoded -> decoded.moves[0] += 1 },
            { decoded -> decoded.movePp[0] += 1 },
            { decoded -> decoded.ivs[0] += 1 },
            { decoded -> decoded.evs[0] += 1 }
        )

        for (mutation in mutations) {
            val entry = Gen6VersionExclusiveEntry(
                "lotad-as",
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                270,
                approved,
                "fixture:lotad-as"
            )
            val failure = assertThrows(IllegalStateException::class.java) {
                Gen6VersionExclusiveCatalog(
                    listOf(entry),
                    Gen6VersionExclusiveLegalityVerifier {
                            destination, decoded, _ ->
                        mutation(decoded)
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
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "legality verifier changed decoded PK6 record lotad-as"
                )
            )
            assertArrayEquals(approved, entry.recordBytes())
        }
    }

    @Test
    fun catalogRejectsIncompleteDestinationLegalityEvidence() {
        val entry = Gen6VersionExclusiveEntry(
            "lotad-as",
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            270,
            record(270, 11),
            "fixture:lotad-as"
        )
        val incomplete = Gen6VersionExclusiveLegalityVerifier {
                destination, decoded, _ ->
            Gen6VersionExclusiveLegalityResult(
                destinationGame = destination,
                species = decoded.species,
                encounterLegal = true,
                movesLegal = false,
                abilityLegal = true,
                formLegal = true,
                originMetadataLegal = true,
                note = "Move set is not legal for the approved encounter."
            )
        }

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveCatalog(listOf(entry), incomplete)
        }

        assertTrue(failure.message!!.contains("moves legality verification"))
    }

    @Test
    fun evidenceProviderRejectsEmptyManifestSet() {
        var recordReads = 0
        var legalityCalls = 0

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveCatalogProvider(
                manifests = emptyList(),
                recordSource = Gen6VersionExclusiveRecordSource {
                    recordReads += 1
                    null
                },
                legalityVerifier = Gen6VersionExclusiveLegalityVerifier {
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
            )
        }

        assertTrue(failure.message!!.contains("must not be empty"))
        assertEquals(0, recordReads)
        assertEquals(0, legalityCalls)
    }

    @Test
    fun evidenceProviderIdentifiesDuplicateIdBeforeRecordReads() {
        val manifests = listOf(
            Gen6VersionExclusiveEvidenceManifest(
                id = "shared-exclusive",
                destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                species = 270,
                recordFingerprint =
                    gen6VersionExclusiveRecordFingerprint(record(270, 11)),
                encounterEvidenceReference = "evidence:alpha-sapphire:lotad"
            ),
            Gen6VersionExclusiveEvidenceManifest(
                id = "shared-exclusive",
                destinationGame = Gen6ConverterGame.Y,
                species = 270,
                recordFingerprint =
                    gen6VersionExclusiveRecordFingerprint(record(270, 12)),
                encounterEvidenceReference = "evidence:y:lotad"
            )
        )
        var recordReads = 0
        var legalityCalls = 0

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveCatalogProvider(
                manifests = manifests,
                recordSource = Gen6VersionExclusiveRecordSource {
                    recordReads += 1
                    record(270, 11)
                },
                legalityVerifier = Gen6VersionExclusiveLegalityVerifier {
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
            )
        }

        assertTrue(
            failure.message!!.contains(
                "Duplicate Gen 6 version-exclusive evidence id shared-exclusive."
            )
        )
        assertEquals(0, recordReads)
        assertEquals(0, legalityCalls)
    }

    @Test
    fun evidenceProviderRejectsDuplicateDestinationSpeciesBeforeRecordReads() {
        val manifests = listOf(
            Gen6VersionExclusiveEvidenceManifest(
                id = "lotad-as-a",
                destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                species = 270,
                recordFingerprint =
                    gen6VersionExclusiveRecordFingerprint(record(270, 11)),
                encounterEvidenceReference = "evidence:alpha-sapphire:lotad-a"
            ),
            Gen6VersionExclusiveEvidenceManifest(
                id = "lotad-as-b",
                destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                species = 270,
                recordFingerprint =
                    gen6VersionExclusiveRecordFingerprint(record(270, 11)),
                encounterEvidenceReference = "evidence:alpha-sapphire:lotad-b"
            )
        )
        var recordReads = 0
        var legalityCalls = 0

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveCatalogProvider(
                manifests = manifests,
                recordSource = Gen6VersionExclusiveRecordSource {
                    recordReads += 1
                    record(270, 11)
                },
                legalityVerifier = Gen6VersionExclusiveLegalityVerifier {
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
            )
        }

        assertTrue(
            failure.message!!.contains(
                "Duplicate Gen 6 version-exclusive evidence species 270 " +
                    "for ALPHA_SAPPHIRE."
            )
        )
        assertEquals(0, recordReads)
        assertEquals(0, legalityCalls)
    }

    @Test
    fun evidenceProviderRejectsDuplicateFingerprintsBeforeRecordReads() {
        val duplicateFingerprint =
            gen6VersionExclusiveRecordFingerprint(record(270, 11))
        val manifests = listOf(
            Gen6VersionExclusiveEvidenceManifest(
                id = "lotad-as",
                destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                species = 270,
                recordFingerprint = duplicateFingerprint,
                encounterEvidenceReference = "evidence:alpha-sapphire:lotad"
            ),
            Gen6VersionExclusiveEvidenceManifest(
                id = "seedot-or",
                destinationGame = Gen6ConverterGame.OMEGA_RUBY,
                species = 273,
                recordFingerprint = duplicateFingerprint,
                encounterEvidenceReference = "evidence:omega-ruby:seedot"
            )
        )
        var recordReads = 0
        var legalityCalls = 0

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveCatalogProvider(
                manifests = manifests,
                recordSource = Gen6VersionExclusiveRecordSource {
                    recordReads += 1
                    record(270, 11)
                },
                legalityVerifier = Gen6VersionExclusiveLegalityVerifier {
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
            )
        }

        assertTrue(
            failure.message!!.contains(
                "Duplicate Gen 6 version-exclusive audited PK6 fingerprint " +
                    "$duplicateFingerprint."
            )
        )
        assertEquals(0, recordReads)
        assertEquals(0, legalityCalls)
    }

    @Test
    fun evidenceProviderLoadsOnlyTheExactAuditedRecord() {
        val approved = record(270, 11)
        val manifest = Gen6VersionExclusiveEvidenceManifest(
            id = "lotad-as",
            destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
            species = 270,
            recordFingerprint = gen6VersionExclusiveRecordFingerprint(approved),
            encounterEvidenceReference = "fixture:alpha-sapphire:lotad"
        )
        val provider = Gen6VersionExclusiveCatalogProvider(
            manifests = listOf(manifest),
            recordSource = Gen6VersionExclusiveRecordSource { id ->
                approved.copyOf().takeIf { id == manifest.id }
            },
            legalityVerifier = GEN6_TEST_EXCLUSIVE_VERIFIER
        )

        val catalog = provider.load()

        assertEquals(
            listOf(
                Gen6VersionExclusiveChoice(
                    id = manifest.id,
                    destinationGame = manifest.destinationGame,
                    species = manifest.species,
                    legalityEvidence = manifest.encounterEvidenceReference,
                    recordFingerprint = manifest.recordFingerprint
                )
            ),
            catalog.choicesFor(Gen6ConverterGame.ALPHA_SAPPHIRE)
        )
        assertArrayEquals(
            approved,
            catalog.resolve(
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                listOf(manifest.id)
            ).single()
        )
    }

    @Test
    fun evidenceProviderRejectsMissingOrSubstitutedRecordsBeforeLegalityApproval() {
        val approved = record(270, 11)
        val manifest = Gen6VersionExclusiveEvidenceManifest(
            id = "lotad-as",
            destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
            species = 270,
            recordFingerprint = gen6VersionExclusiveRecordFingerprint(approved),
            encounterEvidenceReference = "evidence:alpha-sapphire:lotad"
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

        val missing = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveCatalogProvider(
                manifests = listOf(manifest),
                recordSource = Gen6VersionExclusiveRecordSource { null },
                legalityVerifier = verifier
            ).load()
        }
        assertTrue(missing.message!!.contains("missing"))
        assertEquals(0, legalityCalls)

        val substituted = assertThrows(IllegalArgumentException::class.java) {
            Gen6VersionExclusiveCatalogProvider(
                manifests = listOf(manifest),
                recordSource = Gen6VersionExclusiveRecordSource {
                    record(270, 12)
                },
                legalityVerifier = verifier
            ).load()
        }
        assertTrue(substituted.message!!.contains("audited fingerprint"))
        assertEquals(0, legalityCalls)
    }

    @Test
    fun directoryRecordSourceLoadsOnlyExactInstalledPk6AsDefensiveCopies() {
        val directory = Files.createTempDirectory("gen6-exclusive-records").toFile()
        try {
            val approved = record(270, 11)
            File(directory, "lotad-as.pk6").writeBytes(approved)
            val source = Gen6DirectoryVersionExclusiveRecordSource(directory)

            val first = source.read("lotad-as")!!
            assertArrayEquals(approved, first)
            first[0] = (first[0].toInt() xor 0x7F).toByte()

            assertArrayEquals(approved, source.read("lotad-as"))
            assertNull(source.read("missing-record"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun directoryRecordSourceRejectsSymbolicLinkRoot() {
        val parent = Files.createTempDirectory("gen6-exclusive-root-link").toFile()
        val installed = File(parent, "installed").apply {
            check(mkdir())
        }
        val linked = File(parent, "linked")
        try {
            Files.createSymbolicLink(linked.toPath(), installed.toPath())

            val failure = assertThrows(IllegalArgumentException::class.java) {
                Gen6DirectoryVersionExclusiveRecordSource(linked)
            }

            assertTrue(failure.message!!.contains("directory must not be a symbolic link"))
        } finally {
            Files.deleteIfExists(linked.toPath())
            parent.deleteRecursively()
        }
    }

    @Test
    fun directoryRecordSourceRejectsRetargetedParentSymlink() {
        val parent =
            Files.createTempDirectory("gen6-exclusive-parent-link").toFile()
        val first = File(parent, "first").apply { check(mkdir()) }
        val second = File(parent, "second").apply { check(mkdir()) }
        val firstRecords =
            File(first, "records").apply { check(mkdir()) }
        val secondRecords =
            File(second, "records").apply { check(mkdir()) }
        val active = File(parent, "active")
        try {
            val approved = record(270, 11)
            File(firstRecords, "lotad-as.pk6").writeBytes(approved)
            File(secondRecords, "lotad-as.pk6").writeBytes(approved)
            Files.createSymbolicLink(active.toPath(), first.toPath())
            val source = Gen6DirectoryVersionExclusiveRecordSource(
                File(active, "records")
            )

            Files.delete(active.toPath())
            Files.createSymbolicLink(active.toPath(), second.toPath())

            val failure = assertThrows(IllegalStateException::class.java) {
                source.read("lotad-as")
            }

            assertTrue(
                failure.message!!.contains(
                    "directory path identity changed"
                )
            )
        } finally {
            Files.deleteIfExists(active.toPath())
            parent.deleteRecursively()
        }
    }

    @Test
    fun directoryRecordSourceRejectsReplacedRootAfterConstruction() {
        val directory = Files.createTempDirectory("gen6-exclusive-replaced-root").toFile()
        try {
            val approved = record(270, 11)
            File(directory, "lotad-as.pk6").writeBytes(approved)
            val source = Gen6DirectoryVersionExclusiveRecordSource(directory)

            check(directory.deleteRecursively())
            check(directory.mkdir())
            File(directory, "lotad-as.pk6").writeBytes(approved)

            val failure = assertThrows(IllegalStateException::class.java) {
                source.read("lotad-as")
            }

            assertTrue(failure.message!!.contains("directory identity changed"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun directoryRecordSourceRejectsRecordChangedAfterCapture() {
        val directory = Files.createTempDirectory(
            "gen6-exclusive-changed-record"
        ).toFile()
        try {
            val recordFile = File(directory, "lotad-as.pk6")
            recordFile.writeBytes(record(270, 11))
            val source =
                Gen6DirectoryVersionExclusiveRecordSource
                    .withAfterRecordCaptureForTest(directory) { id, file ->
                        if (id == "lotad-as") {
                            file.writeBytes(record(270, 12))
                        }
                    }

            val failure = assertThrows(IllegalStateException::class.java) {
                source.read("lotad-as")
            }

            assertTrue(
                failure.message!!.contains(
                    "Version-exclusive record lotad-as bytes changed"
                )
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun directoryRecordSourceRejectsUnsafeLinksAndWrongSizedRecords() {
        val directory = Files.createTempDirectory("gen6-exclusive-source").toFile()
        val outside = Files.createTempFile("gen6-exclusive-outside", ".pk6").toFile()
        try {
            outside.writeBytes(record(270, 11))
            Files.createSymbolicLink(
                File(directory, "lotad-as.pk6").toPath(),
                outside.toPath()
            )
            val source = Gen6DirectoryVersionExclusiveRecordSource(directory)

            assertThrows(IllegalArgumentException::class.java) {
                source.read("../lotad-as")
            }
            assertThrows(IllegalArgumentException::class.java) {
                source.read("lotad-as")
            }

            Files.delete(File(directory, "lotad-as.pk6").toPath())
            File(directory, "lotad-as.pk6").writeBytes(byteArrayOf(1, 2, 3))
            val wrongSizedSource =
                Gen6DirectoryVersionExclusiveRecordSource(directory)
            val wrongSize = assertThrows(IllegalArgumentException::class.java) {
                wrongSizedSource.read("lotad-as")
            }
            assertTrue(wrongSize.message!!.contains("expected"))
        } finally {
            directory.deleteRecursively()
            outside.delete()
        }
    }

}
