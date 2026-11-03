package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6ConvertSaveExclusiveWorkflowTest {
    private fun storedRecord(species: Int, marker: Int): ByteArray {
        val plain = ByteArray(Gen6Pk6Adapter.STORED_SIZE)
        plain[0x08] = (species and 0xFF).toByte()
        plain[0x09] = (species ushr 8).toByte()
        plain[0x10] = marker.toByte()
        val checksum = species + marker
        plain[0x06] = (checksum and 0xFF).toByte()
        plain[0x07] = (checksum ushr 8).toByte()
        return Gen6Pk6Adapter.encode(Gen6Pk6Adapter.decode(plain))
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
        val boxOffset = if (oras) 0x33000 else 0x22600
        writeBlockChecksum(bytes, 18, 0x14200, 0x61C)
        writeBlockChecksum(bytes, 20, 0x15000, if (oras) 0x011CC else 0x006A0)
        writeBlockChecksum(bytes, if (oras) 56 else 53, boxOffset, 0x34AD0)
        return bytes
    }

    @Test
    fun publicPreviewRejectsDuplicateExactPk6RecordIdentity() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val sourceBefore = source.copyOf()
        val sharedFingerprint =
            gen6VersionExclusiveRecordFingerprint(storedRecord(270, 11))
        val choices = listOf(
            Gen6VersionExclusiveChoice(
                "lotad-as",
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                270,
                "fixture:lotad-as",
                sharedFingerprint
            ),
            Gen6VersionExclusiveChoice(
                "sableye-as",
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                302,
                "fixture:sableye-as",
                sharedFingerprint
            )
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSavePreview(
                request = Gen6ConverterRequest(
                    Gen6ConverterGame.OMEGA_RUBY,
                    Gen6ConverterGame.ALPHA_SAPPHIRE
                ),
                sourceFingerprint = gen6ConversionSourceFingerprint(source),
                sourceTitle = "Pokémon Omega Ruby",
                destinationTitle = "Pokémon Alpha Sapphire",
                suggestedFilename =
                    "Pokemon_Omega_Ruby_Pokemon_Alpha_Sapphire_Converted.sav",
                preserved = emptyList(),
                omitted = emptyList(),
                availableVersionExclusives = choices,
                compatibilityNotice = "Automated validation only."
            )
        }

        assertTrue(failure.message!!.contains("unique exact PK6 records"))
        assertArrayEquals(sourceBefore, source)
    }

    @Test
    fun explicitSelectionFlowsThroughPreviewAndConcreteDestinationWriter() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()
        val lotad = storedRecord(270, 11)
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

        val prepared = Gen6ConvertSaveWorkflow.prepare(
            source,
            template,
            "Pokemon_Omega_Ruby.sav",
            destinationBaselineAccepted = true,
            selectedVersionExclusiveIds = listOf("lotad-as"),
            versionExclusiveCatalog = catalog
        )

        assertTrue(prepared.preview.request.includeVersionExclusives)
        assertEquals(
            listOf("lotad-as"),
            prepared.preview.request.selectedVersionExclusiveIds
        )
        assertEquals(
            listOf(
                Gen6VersionExclusiveChoice(
                    "lotad-as",
                    Gen6ConverterGame.ALPHA_SAPPHIRE,
                    270,
                    "fixture:lotad-as",
                    gen6VersionExclusiveRecordFingerprint(lotad)
                )
            ),
            prepared.preview.availableVersionExclusives
        )
        assertTrue(prepared.preview.preserved.any { "1 explicitly selected" in it })
        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
        assertArrayEquals(lotad, prepared.bytes.copyOfRange(0x33000, 0x33000 + 0xE8))
        assertTrue(
            Gen6PartyStorageLayout.validate(
                prepared.bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            ).isValid
        )
    }

    @Test
    fun previewKeepsSelectionSnapshotDuringCatalogReview() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val sourceBefore = source.copyOf()
        val lotad = storedRecord(270, 11)
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
        val backing = mutableListOf("lotad-as")
        val changingSelection = object : AbstractList<String>() {
            override val size: Int
                get() = backing.size

            override fun get(index: Int): String {
                val value = backing[index]
                backing.clear()
                return value
            }
        }

        val preview = Gen6ConvertSaveWorkflow.preview(
            sourceBytes = source,
            sourceLabel = "Pokemon_Omega_Ruby.sav",
            selectedVersionExclusiveIds = changingSelection,
            versionExclusiveCatalog = catalog
        )

        assertTrue(changingSelection.isEmpty())
        assertTrue(preview.request.includeVersionExclusives)
        assertEquals(
            listOf("lotad-as"),
            preview.request.selectedVersionExclusiveIds
        )
        assertTrue(
            preview.preserved.any {
                "1 explicitly selected destination-version-exclusive" in it
            }
        )
        assertArrayEquals(sourceBefore, source)
    }

    @Test
    fun selectionWithoutCatalogFailsBeforePublishingAndPreservesInputs() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()

        assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.prepare(
                source,
                template,
                "Pokemon_Omega_Ruby.sav",
                destinationBaselineAccepted = true,
                selectedVersionExclusiveIds = listOf("lotad-as")
            )
        }

        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
    }

    @Test
    fun previewRejectsSelectionsOutsideVerifiedPairedDestination() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val sourceBefore = source.copyOf()

        val missingCatalog = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.preview(
                source,
                "Pokemon_Omega_Ruby.sav",
                selectedVersionExclusiveIds = listOf("lotad-as")
            )
        }
        assertTrue(missingCatalog.message!!.contains("verified"))
        assertArrayEquals(sourceBefore, source)

        val wrongDestinationCatalog = Gen6VersionExclusiveCatalog(
            listOf(
                Gen6VersionExclusiveEntry(
                    "seedot-or",
                    Gen6ConverterGame.OMEGA_RUBY,
                    273,
                    storedRecord(273, 12),
                    "fixture:seedot-or"
                )
            ),
            GEN6_TEST_EXCLUSIVE_VERIFIER
        )
        val wrongDestination = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.preview(
                source,
                "Pokemon_Omega_Ruby.sav",
                selectedVersionExclusiveIds = listOf("seedot-or"),
                versionExclusiveCatalog = wrongDestinationCatalog
            )
        }
        assertTrue(wrongDestination.message!!.contains("not ALPHA_SAPPHIRE"))
        assertArrayEquals(sourceBefore, source)
    }

    @Test
    fun installedCatalogFlowsThroughDestinationCapacityPreview() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()
        val lotad = storedRecord(270, 11)
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
        val installation = Gen6VersionExclusiveCatalogInstallation.Available(
            destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
            verifiedCatalog = catalog
        )

        val preview = Gen6ConvertSaveWorkflow.previewDestination(
            sourceBytes = source,
            destinationTemplate = template,
            sourceLabel = "Pokemon_Omega_Ruby.sav",
            installation = installation,
            selectedVersionExclusiveIds = listOf("lotad-as")
        )

        assertEquals(1, preview.capacity.selectedVersionExclusiveRecords)
        assertEquals(1, preview.capacity.requiredPcSlots)
        assertEquals(
            listOf("lotad-as"),
            preview.conversion.request.selectedVersionExclusiveIds
        )
        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
    }

    @Test
    fun unavailableInstallationSurvivesCapacityPreviewAndRejectsStaleSelection() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()
        val reason = "No audited Alpha Sapphire exclusive records are installed."
        val installation =
            Gen6VersionExclusiveCatalogInstallation.Unavailable(reason)

        val preview = Gen6ConvertSaveWorkflow.previewDestination(
            sourceBytes = source,
            destinationTemplate = template,
            sourceLabel = "Pokemon_Omega_Ruby.sav",
            installation = installation
        )
        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.previewDestination(
                sourceBytes = source,
                destinationTemplate = template,
                sourceLabel = "Pokemon_Omega_Ruby.sav",
                installation = installation,
                selectedVersionExclusiveIds = listOf("stale-lotad-as")
            )
        }

        assertEquals(reason, preview.conversion.versionExclusiveUnavailableReason)
        assertTrue(preview.conversion.availableVersionExclusives.isEmpty())
        assertEquals(0, preview.capacity.selectedVersionExclusiveRecords)
        assertTrue(failure.message!!.contains("catalog is unavailable"))
        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
    }

    @Test
    fun installedCatalogFlowsThroughFinalPreparation() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()
        val lotad = storedRecord(270, 11)
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
        val installation = Gen6VersionExclusiveCatalogInstallation.Available(
            destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
            verifiedCatalog = catalog
        )

        val prepared = Gen6ConvertSaveWorkflow.prepare(
            sourceBytes = source,
            destinationTemplate = template,
            sourceLabel = "Pokemon_Omega_Ruby.sav",
            destinationBaselineAccepted = true,
            installation = installation,
            selectedVersionExclusiveIds = listOf("lotad-as")
        )

        assertEquals(
            listOf("lotad-as"),
            prepared.preview.request.selectedVersionExclusiveIds
        )
        assertEquals(
            listOf("lotad-as"),
            prepared.preview.availableVersionExclusives.map { it.id }
        )
        assertArrayEquals(
            lotad,
            prepared.bytes.copyOfRange(
                0x33000,
                0x33000 + Gen6Pk6Adapter.STORED_SIZE
            )
        )
        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
    }

    @Test
    fun installationPreparationKeepsOneSelectionSnapshotThroughSerialization() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()
        val lotad = storedRecord(270, 11)
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
        val installation = Gen6VersionExclusiveCatalogInstallation.Available(
            destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
            verifiedCatalog = catalog
        )
        val backingSelection = mutableListOf("lotad-as")
        val changingSelection = object : AbstractList<String>() {
            override val size: Int
                get() = backingSelection.size

            override fun get(index: Int): String {
                val selected = backingSelection[index]
                backingSelection.clear()
                return selected
            }
        }

        val prepared = Gen6ConvertSaveWorkflow.prepare(
            sourceBytes = source,
            destinationTemplate = template,
            sourceLabel = "Pokemon_Omega_Ruby.sav",
            destinationBaselineAccepted = true,
            installation = installation,
            selectedVersionExclusiveIds = changingSelection
        )

        assertTrue(changingSelection.isEmpty())
        assertEquals(
            listOf("lotad-as"),
            prepared.preview.request.selectedVersionExclusiveIds
        )
        assertArrayEquals(
            lotad,
            prepared.bytes.copyOfRange(
                0x33000,
                0x33000 + Gen6Pk6Adapter.STORED_SIZE
            )
        )
        assertTrue(
            Gen6PartyStorageLayout.validate(
                prepared.bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            ).isValid
        )
        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
    }

    @Test
    fun unavailableInstallationReasonSurvivesPreparationAndBlocksStaleSelection() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()
        val reason = "No audited Alpha Sapphire exclusive records are installed."
        val installation =
            Gen6VersionExclusiveCatalogInstallation.Unavailable(reason)

        val prepared = Gen6ConvertSaveWorkflow.prepare(
            sourceBytes = source,
            destinationTemplate = template,
            sourceLabel = "Pokemon_Omega_Ruby.sav",
            destinationBaselineAccepted = true,
            installation = installation
        )
        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.prepare(
                sourceBytes = source,
                destinationTemplate = template,
                sourceLabel = "Pokemon_Omega_Ruby.sav",
                destinationBaselineAccepted = true,
                installation = installation,
                selectedVersionExclusiveIds = listOf("stale-lotad-as")
            )
        }

        assertEquals(reason, prepared.preview.versionExclusiveUnavailableReason)
        assertTrue(prepared.preview.availableVersionExclusives.isEmpty())
        assertTrue(failure.message!!.contains("catalog is unavailable"))
        assertTrue(
            Gen6PartyStorageLayout.validate(
                prepared.bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            ).isValid
        )
        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
    }

    @Test
    fun immutableSelectionStateFlowsThroughFinalPreparation() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()
        val lotad = storedRecord(270, 11)
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
        val installation = Gen6VersionExclusiveCatalogInstallation.Available(
            destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
            verifiedCatalog = catalog
        )
        val selectionState = Gen6VersionExclusiveSelectionState.from(
            Gen6ConvertSaveWorkflow.preview(
                sourceBytes = source,
                sourceLabel = "Pokemon_Omega_Ruby.sav",
                installation = installation
            )
        ).withSelection("lotad-as", selected = true)

        val prepared = Gen6ConvertSaveWorkflow.prepare(
            sourceBytes = source,
            destinationTemplate = template,
            sourceLabel = "Pokemon_Omega_Ruby.sav",
            destinationBaselineAccepted = true,
            installation = installation,
            selectionState = selectionState
        )

        assertEquals(listOf("lotad-as"), prepared.preview.request.selectedVersionExclusiveIds)
        assertArrayEquals(
            lotad,
            prepared.bytes.copyOfRange(
                0x33000,
                0x33000 + Gen6Pk6Adapter.STORED_SIZE
            )
        )
        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
    }

    @Test
    fun finalPreparationRejectsSelectionStateAfterCatalogRecordChanges() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()
        val reviewedCatalog = Gen6VersionExclusiveCatalog(
            listOf(
                Gen6VersionExclusiveEntry(
                    "lotad-as",
                    Gen6ConverterGame.ALPHA_SAPPHIRE,
                    270,
                    storedRecord(270, 11),
                    "fixture:lotad-as"
                )
            ),
            GEN6_TEST_EXCLUSIVE_VERIFIER
        )
        val reviewedInstallation =
            Gen6VersionExclusiveCatalogInstallation.Available(
                destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                verifiedCatalog = reviewedCatalog
            )
        val selectionState = Gen6VersionExclusiveSelectionState.from(
            Gen6ConvertSaveWorkflow.preview(
                sourceBytes = source,
                sourceLabel = "Pokemon_Omega_Ruby.sav",
                installation = reviewedInstallation
            )
        ).withSelection("lotad-as", selected = true)
        val changedCatalog = Gen6VersionExclusiveCatalog(
            listOf(
                Gen6VersionExclusiveEntry(
                    "lotad-as",
                    Gen6ConverterGame.ALPHA_SAPPHIRE,
                    270,
                    storedRecord(270, 12),
                    "fixture:lotad-as"
                )
            ),
            GEN6_TEST_EXCLUSIVE_VERIFIER
        )
        val changedInstallation =
            Gen6VersionExclusiveCatalogInstallation.Available(
                destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                verifiedCatalog = changedCatalog
            )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.prepare(
                sourceBytes = source,
                destinationTemplate = template,
                sourceLabel = "Pokemon_Omega_Ruby.sav",
                destinationBaselineAccepted = true,
                installation = changedInstallation,
                selectionState = selectionState
            )
        }

        assertTrue(failure.message!!.contains("catalog changed"))
        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
    }

    @Test
    fun preparedDestinationBytesAreDefensiveAcrossReads() {
        val prepared = Gen6ConvertSaveWorkflow.prepare(
            save(Gen6ConverterGame.OMEGA_RUBY),
            save(Gen6ConverterGame.ALPHA_SAPPHIRE),
            "Pokemon_Omega_Ruby.sav",
            destinationBaselineAccepted = true
        )
        val expected = prepared.bytes
        val exposed = prepared.bytes

        exposed.fill(0x55)

        assertArrayEquals(expected, prepared.bytes)
        assertTrue(
            Gen6PartyStorageLayout.validate(
                prepared.bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            ).isValid
        )
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

    private fun crc16Ccitt(data: ByteArray, offset: Int, length: Int): Int {
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
