package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6ConvertSaveWorkflowTest {
    private fun storedRecord(species: Int, experience: Int): ByteArray {
        val plain = ByteArray(Gen6Pk6Adapter.STORED_SIZE)
        plain[0x08] = (species and 0xFF).toByte()
        plain[0x09] = (species ushr 8).toByte()
        plain[0x10] = experience.toByte()
        val sum = species + experience
        plain[0x06] = (sum and 0xFF).toByte()
        plain[0x07] = (sum ushr 8).toByte()
        return Gen6Pk6Adapter.encode(Gen6Pk6Adapter.decode(plain))
    }

    private fun partyRecord(species: Int, marker: Int): ByteArray =
        ByteArray(0x104).also { party ->
            storedRecord(species, marker).copyInto(party)
            for (index in 0xE8 until party.size) party[index] = marker.toByte()
        }

    private fun exclusiveCatalog(
        evidence: String = "fixture:lotad-as",
        marker: Int = 11,
        onVerification: () -> Unit = {}
    ): Gen6VersionExclusiveCatalog =
        Gen6VersionExclusiveCatalog(
            listOf(
                Gen6VersionExclusiveEntry(
                    id = "lotad-as",
                    destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                    species = 270,
                    encryptedPk6 = storedRecord(270, marker),
                    legalityEvidence = evidence
                )
            ),
            Gen6VersionExclusiveLegalityVerifier { destination, record, _ ->
                onVerification()
                Gen6VersionExclusiveLegalityResult(
                    destinationGame = destination,
                    species = record.species,
                    encounterLegal = true,
                    movesLegal = true,
                    abilityLegal = true,
                    formLegal = true,
                    originMetadataLegal = true
                )
            }
        )

    private fun save(
        game: Gen6ConverterGame,
        pc: List<ByteArray> = emptyList(),
        party: List<ByteArray> = emptyList()
    ): ByteArray {
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
        pc.forEachIndexed { index, record ->
            record.copyInto(bytes, boxOffset + index * 0xE8)
        }
        party.forEachIndexed { index, record ->
            record.copyInto(bytes, 0x14200 + index * 0x104)
        }
        bytes[0x14200 + 6 * 0x104] = party.size.toByte()
        writeBlockChecksum(bytes, 18, 0x14200, 0x61C)
        writeBlockChecksum(bytes, 20, 0x15000, if (oras) 0x011CC else 0x006A0)
        writeBlockChecksum(bytes, if (oras) 56 else 53, boxOffset, 0x34AD0)
        return bytes
    }

    @Test
    fun previewSelectsAllFourRequiredPairedRoutes() {
        val expected = mapOf(
            Gen6ConverterGame.OMEGA_RUBY to
                Gen6ConverterGame.ALPHA_SAPPHIRE,
            Gen6ConverterGame.ALPHA_SAPPHIRE to
                Gen6ConverterGame.OMEGA_RUBY,
            Gen6ConverterGame.X to Gen6ConverterGame.Y,
            Gen6ConverterGame.Y to Gen6ConverterGame.X
        )
        expected.forEach { (source, destination) ->
            val preview =
                Gen6ConvertSaveWorkflow.preview(save(source), "main.sav")
            assertEquals(source, preview.request.source)
            assertEquals(destination, preview.request.destination)
            assertTrue(preview.suggestedFilename.endsWith("_Converted.sav"))
            assertTrue(preview.preserved.any { "party" in it.lowercase() })
            assertTrue(preview.omitted.any { "story" in it.lowercase() })
            assertTrue(preview.omitted.any { "exclusive" in it.lowercase() })
            assertFalse(preview.canOfferVersionExclusives)
            assertTrue(
                preview.versionExclusiveAvailabilityNotice()
                    .contains("unavailable until an audited evidence manifest")
            )
            assertTrue(
                preview.compatibilityNotice.contains(
                    "Real destination-game compatibility is not confirmed"
                )
            )
        }
    }

    @Test
    fun suggestedFilenameIsPortableAndBoundedForOversizedSourceLabel() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val sourceBefore = source.copyOf()
        val preview = Gen6ConvertSaveWorkflow.preview(
            source,
            ("Ω very long/source label!? ".repeat(30)) + ".sav"
        )

        assertEquals(120, preview.suggestedFilename.length)
        assertTrue(
            preview.suggestedFilename.endsWith(
                "_Pokemon_Alpha_Sapphire_Converted.sav"
            )
        )
        assertTrue(
            preview.suggestedFilename.all {
                it.isLetterOrDigit() || it == '_' || it == '-' || it == '.'
            }
        )
        assertArrayEquals(sourceBefore, source)
    }

    @Test
    fun previewRejectsUnsafeOrWrongDestinationSuggestedFilename() {
        val preview = Gen6ConvertSaveWorkflow.preview(
            save(Gen6ConverterGame.OMEGA_RUBY),
            "Pokemon_Omega_Ruby.sav"
        )
        val unsafeNames = listOf(
            "../x_Pokemon_Alpha_Sapphire_Converted.sav",
            "Pokémon_Pokemon_Alpha_Sapphire_Converted.sav",
            "A".repeat(121) + "_Pokemon_Alpha_Sapphire_Converted.sav",
            "Pokemon_X_Converted.sav"
        )

        unsafeNames.forEach { filename ->
            val failure = assertThrows(IllegalArgumentException::class.java) {
                preview.copy(suggestedFilename = filename)
            }
            assertTrue(
                failure.message!!.contains(
                    "portable, bounded, destination-specific"
                )
            )
        }
    }

    @Test
    fun previewRejectsSourceChangeDuringCatalogVerification() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val sourceBefore = source.copyOf()
        var verificationCount = 0
        var previewStarted = false
        val catalog = exclusiveCatalog(
            onVerification = {
                verificationCount++
                if (previewStarted) {
                    source[0x15000] =
                        (source[0x15000].toInt() xor 1).toByte()
                }
            }
        )
        val constructionVerificationCount = verificationCount
        previewStarted = true

        val failure = assertThrows(IllegalStateException::class.java) {
            Gen6ConvertSaveWorkflow.preview(
                source,
                "Pokemon_Omega_Ruby.sav",
                versionExclusiveCatalog = catalog
            )
        }

        assertEquals(constructionVerificationCount + 1, verificationCount)
        assertTrue(
            failure.message!!.contains(
                "source save changed during conversion preview"
            )
        )
        assertFalse(source.contentEquals(sourceBefore))
    }

    @Test
    fun destinationBoundInstallationFeedsOnlyItsMatchingPreview() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val sourceBefore = source.copyOf()
        val installation = Gen6VersionExclusiveCatalogInstallation.Available(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            exclusiveCatalog()
        )

        val preview = Gen6ConvertSaveWorkflow.preview(
            sourceBytes = source,
            sourceLabel = "Pokemon_Omega_Ruby.sav",
            installation = installation
        )

        assertEquals(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            preview.request.destination
        )
        assertEquals(
            listOf("lotad-as"),
            preview.availableVersionExclusives.map { it.id }
        )
        assertArrayEquals(sourceBefore, source)
    }

    @Test
    fun destinationBoundInstallationRejectsChangedSourceRoute() {
        val source = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val sourceBefore = source.copyOf()
        val installation = Gen6VersionExclusiveCatalogInstallation.Available(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            exclusiveCatalog()
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.preview(
                sourceBytes = source,
                sourceLabel = "Pokemon_Alpha_Sapphire.sav",
                installation = installation
            )
        }

        assertTrue(failure.message!!.contains("verified for Alpha Sapphire"))
        assertTrue(failure.message!!.contains("not Omega Ruby"))
        assertArrayEquals(sourceBefore, source)
    }

    @Test
    fun unavailableInstallationDisablesChoicesAndRejectsStaleSelection() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val sourceBefore = source.copyOf()
        val installation = Gen6VersionExclusiveCatalogInstallation.Unavailable(
            "Installed evidence failed exact fingerprint validation."
        )

        val preview = Gen6ConvertSaveWorkflow.preview(
            sourceBytes = source,
            sourceLabel = "Pokemon_Omega_Ruby.sav",
            installation = installation
        )

        assertFalse(preview.canOfferVersionExclusives)
        assertEquals(
            installation.reason,
            preview.versionExclusiveAvailabilityNotice()
        )
        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.preview(
                sourceBytes = source,
                sourceLabel = "Pokemon_Omega_Ruby.sav",
                installation = installation,
                selectedVersionExclusiveIds = listOf("lotad-as")
            )
        }
        assertTrue(failure.message!!.contains("catalog is unavailable"))
        assertArrayEquals(sourceBefore, source)
    }

    @Test
    fun selectionStateRefreshesPreviewAgainstMatchingSourceAndCatalog() {
        val catalog = exclusiveCatalog()
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val initial = Gen6ConvertSaveWorkflow.preview(
            source,
            "Pokemon_Omega_Ruby.sav",
            versionExclusiveCatalog = catalog
        )
        val selection = Gen6VersionExclusiveSelectionState.from(initial)
            .withSelection("lotad-as", selected = true)

        val refreshed = Gen6ConvertSaveWorkflow.preview(
            sourceBytes = source,
            sourceLabel = "Pokemon_Omega_Ruby.sav",
            selectionState = selection,
            versionExclusiveCatalog = catalog
        )

        assertTrue(refreshed.request.includeVersionExclusives)
        assertTrue(refreshed.canOfferVersionExclusives)
        assertTrue(
            refreshed.versionExclusiveAvailabilityNotice()
                .contains("1 evidence-backed destination-exclusive")
        )
        assertEquals(
            listOf("lotad-as"),
            refreshed.request.selectedVersionExclusiveIds
        )
        assertTrue(
            refreshed.preserved.any {
                "1 explicitly selected" in it
            }
        )
    }

    @Test
    fun installationBoundSelectionRefreshRejectsUnavailableOrChangedCatalog() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val catalog = exclusiveCatalog("fixture:lotad-as:v1")
        val installation = Gen6VersionExclusiveCatalogInstallation.Available(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            catalog
        )
        val initial = Gen6ConvertSaveWorkflow.preview(
            sourceBytes = source,
            sourceLabel = "Pokemon_Omega_Ruby.sav",
            installation = installation
        )
        val selection = Gen6VersionExclusiveSelectionState.from(initial)
            .withSelection("lotad-as", selected = true)
        val sourceBefore = source.copyOf()

        val refreshed = Gen6ConvertSaveWorkflow.preview(
            sourceBytes = source,
            sourceLabel = "Pokemon_Omega_Ruby.sav",
            installation = installation,
            selectionState = selection
        )

        assertEquals(
            listOf("lotad-as"),
            refreshed.request.selectedVersionExclusiveIds
        )

        val unavailable = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.preview(
                sourceBytes = source,
                sourceLabel = "Pokemon_Omega_Ruby.sav",
                installation =
                    Gen6VersionExclusiveCatalogInstallation.Unavailable(
                        "Installed evidence is unavailable."
                    ),
                selectionState = selection
            )
        }
        assertTrue(unavailable.message!!.contains("catalog is unavailable"))

        val changed = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.preview(
                sourceBytes = source,
                sourceLabel = "Pokemon_Omega_Ruby.sav",
                installation =
                    Gen6VersionExclusiveCatalogInstallation.Available(
                        Gen6ConverterGame.ALPHA_SAPPHIRE,
                        exclusiveCatalog("fixture:lotad-as:v2")
                    ),
                selectionState = selection
            )
        }
        assertTrue(changed.message!!.contains("catalog changed"))
        assertArrayEquals(sourceBefore, source)
    }

    @Test
    fun selectionStateRejectsChangedSourcePairing() {
        val catalog = exclusiveCatalog()
        val initial = Gen6ConvertSaveWorkflow.preview(
            save(Gen6ConverterGame.OMEGA_RUBY),
            "Pokemon_Omega_Ruby.sav",
            versionExclusiveCatalog = catalog
        )
        val selection = Gen6VersionExclusiveSelectionState.from(initial)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.preview(
                sourceBytes = save(Gen6ConverterGame.X),
                sourceLabel = "Pokemon_X.sav",
                selectionState = selection,
                versionExclusiveCatalog = catalog
            )
        }

        assertTrue(failure.message!!.contains("loaded source no longer matches"))
    }

    @Test
    fun selectionStateRejectsChangedSameGameSourceSave() {
        val catalog = exclusiveCatalog()
        val originalSource = save(
            Gen6ConverterGame.OMEGA_RUBY,
            pc = listOf(storedRecord(151, 30))
        )
        val changedSource = save(
            Gen6ConverterGame.OMEGA_RUBY,
            pc = listOf(storedRecord(150, 31))
        )
        val initial = Gen6ConvertSaveWorkflow.preview(
            originalSource,
            "Pokemon_Omega_Ruby.sav",
            versionExclusiveCatalog = catalog
        )
        val selection = Gen6VersionExclusiveSelectionState.from(initial)
            .withSelection("lotad-as", selected = true)
        val originalBefore = originalSource.copyOf()
        val changedBefore = changedSource.copyOf()

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.preview(
                sourceBytes = changedSource,
                sourceLabel = "Pokemon_Omega_Ruby.sav",
                selectionState = selection,
                versionExclusiveCatalog = catalog
            )
        }

        assertTrue(failure.message!!.contains("source save changed"))
        assertArrayEquals(originalBefore, originalSource)
        assertArrayEquals(changedBefore, changedSource)
    }

    @Test
    fun changedSourceIsRejectedBeforeCatalogRevalidation() {
        var verificationCount = 0
        val catalog = exclusiveCatalog(
            onVerification = { verificationCount++ }
        )
        val initial = Gen6ConvertSaveWorkflow.preview(
            save(Gen6ConverterGame.OMEGA_RUBY),
            "Pokemon_Omega_Ruby.sav",
            versionExclusiveCatalog = catalog
        )
        val selection = Gen6VersionExclusiveSelectionState.from(initial)
            .withSelection("lotad-as", selected = true)

        assertEquals(2, verificationCount)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.preview(
                sourceBytes = save(
                    Gen6ConverterGame.OMEGA_RUBY,
                    pc = listOf(storedRecord(150, 31))
                ),
                sourceLabel = "Pokemon_Omega_Ruby.sav",
                selectionState = selection,
                versionExclusiveCatalog = catalog
            )
        }

        assertTrue(failure.message!!.contains("source save changed"))
        assertEquals(2, verificationCount)
    }

    @Test
    fun selectionStateRejectsChangedExactRecordWithSameEvidence() {
        val initialCatalog = exclusiveCatalog(
            evidence = "fixture:lotad-as",
            marker = 11
        )
        val initial = Gen6ConvertSaveWorkflow.preview(
            save(Gen6ConverterGame.OMEGA_RUBY),
            "Pokemon_Omega_Ruby.sav",
            versionExclusiveCatalog = initialCatalog
        )
        val selection = Gen6VersionExclusiveSelectionState.from(initial)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.preview(
                sourceBytes = save(Gen6ConverterGame.OMEGA_RUBY),
                sourceLabel = "Pokemon_Omega_Ruby.sav",
                selectionState = selection,
                versionExclusiveCatalog = exclusiveCatalog(
                    evidence = "fixture:lotad-as",
                    marker = 12
                )
            )
        }

        assertTrue(failure.message!!.contains("catalog changed"))
    }

    @Test
    fun destinationPreviewPreservesApprovedSelectionAndRejectsStaleCatalog() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val occupied = storedRecord(151, 30)
        val template = save(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            pc = List(930) { occupied }
        )
        val catalog = exclusiveCatalog("fixture:lotad-as:v1")
        val initial = Gen6ConvertSaveWorkflow.preview(
            source,
            "Pokemon_Omega_Ruby.sav",
            versionExclusiveCatalog = catalog
        )
        val selection = Gen6VersionExclusiveSelectionState.from(initial)
            .withSelection("lotad-as", selected = true)
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()

        val destinationPreview = Gen6ConvertSaveWorkflow.previewDestination(
            sourceBytes = source,
            destinationTemplate = template,
            sourceLabel = "Pokemon_Omega_Ruby.sav",
            selectionState = selection,
            versionExclusiveCatalog = catalog
        )

        assertEquals(
            listOf("lotad-as"),
            destinationPreview.conversion.request.selectedVersionExclusiveIds
        )
        assertEquals(1, destinationPreview.capacity.missingPcSlots)
        assertFalse(destinationPreview.canPrepare)
        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)

        val staleCatalog = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.previewDestination(
                sourceBytes = source,
                destinationTemplate = template,
                sourceLabel = "Pokemon_Omega_Ruby.sav",
                selectionState = selection,
                versionExclusiveCatalog =
                    exclusiveCatalog("fixture:lotad-as:v2")
            )
        }
        assertTrue(staleCatalog.message!!.contains("catalog changed"))
        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
    }

    @Test
    fun installationBoundDestinationPreviewRejectsUnavailableOrChangedCatalog() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val catalog = exclusiveCatalog("fixture:lotad-as:v1")
        val installation = Gen6VersionExclusiveCatalogInstallation.Available(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            catalog
        )
        val initial = Gen6ConvertSaveWorkflow.preview(
            sourceBytes = source,
            sourceLabel = "Pokemon_Omega_Ruby.sav",
            installation = installation
        )
        val selection = Gen6VersionExclusiveSelectionState.from(initial)
            .withSelection("lotad-as", selected = true)
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()

        val destinationPreview = Gen6ConvertSaveWorkflow.previewDestination(
            sourceBytes = source,
            destinationTemplate = template,
            sourceLabel = "Pokemon_Omega_Ruby.sav",
            installation = installation,
            selectionState = selection
        )

        assertEquals(
            listOf("lotad-as"),
            destinationPreview.conversion.request.selectedVersionExclusiveIds
        )
        assertTrue(destinationPreview.canPrepare)

        val unavailable = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.previewDestination(
                sourceBytes = source,
                destinationTemplate = template,
                sourceLabel = "Pokemon_Omega_Ruby.sav",
                installation =
                    Gen6VersionExclusiveCatalogInstallation.Unavailable(
                        "Installed evidence is unavailable."
                    ),
                selectionState = selection
            )
        }
        assertTrue(unavailable.message!!.contains("catalog is unavailable"))

        val changed = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.previewDestination(
                sourceBytes = source,
                destinationTemplate = template,
                sourceLabel = "Pokemon_Omega_Ruby.sav",
                installation =
                    Gen6VersionExclusiveCatalogInstallation.Available(
                        Gen6ConverterGame.ALPHA_SAPPHIRE,
                        exclusiveCatalog("fixture:lotad-as:v2")
                    ),
                selectionState = selection
            )
        }
        assertTrue(changed.message!!.contains("catalog changed"))
        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
    }

    @Test
    fun destinationPreviewReportsCapacityShortfallsWithoutMutation() {
        val source = save(
            Gen6ConverterGame.OMEGA_RUBY,
            pc = listOf(storedRecord(25, 10), storedRecord(64, 20)),
            party = listOf(partyRecord(150, 0x31))
        )
        val occupied = storedRecord(151, 30)
        val template = save(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            pc = List(929) { occupied },
            party = (1..6).map { partyRecord(it, it) }
        )
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()

        val destinationPreview = Gen6ConvertSaveWorkflow.previewDestination(
            source,
            template,
            "Pokemon_Omega_Ruby.sav"
        )

        assertEquals(1, destinationPreview.capacity.missingPcSlots)
        assertEquals(1, destinationPreview.capacity.missingPartySlots)
        assertFalse(destinationPreview.canPrepare)
        assertEquals(
            "Destination template needs 1 more PC slot and 1 more party slot.",
            destinationPreview.capacityNotice()
        )
        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
    }

    @Test
    fun destinationPreviewRejectsCorruptTemplatesForEveryPairedRoute() {
        val routes = mapOf(
            Gen6ConverterGame.OMEGA_RUBY to
                Gen6ConverterGame.ALPHA_SAPPHIRE,
            Gen6ConverterGame.ALPHA_SAPPHIRE to
                Gen6ConverterGame.OMEGA_RUBY,
            Gen6ConverterGame.X to Gen6ConverterGame.Y,
            Gen6ConverterGame.Y to Gen6ConverterGame.X
        )

        routes.forEach { (sourceGame, destinationGame) ->
            val source = save(sourceGame)
            val template = save(destinationGame).also {
                it[0x15000] = (it[0x15000].toInt() xor 1).toByte()
            }
            val sourceBefore = source.copyOf()
            val templateBefore = template.copyOf()

            val failure = assertThrows(IllegalArgumentException::class.java) {
                Gen6ConvertSaveWorkflow.previewDestination(
                    source,
                    template,
                    "source.sav"
                )
            }

            assertTrue(
                failure.message!!.contains(
                    "failed identity, checksum or reopen validation"
                )
            )
            assertArrayEquals(sourceBefore, source)
            assertArrayEquals(templateBefore, template)
        }
    }

    @Test
    fun prepareUsesLawfulTemplateAndPreservesBothInputs() {
        val source = save(
            Gen6ConverterGame.OMEGA_RUBY,
            pc = listOf(storedRecord(25, 10)),
            party = listOf(partyRecord(64, 0x22))
        )
        val template = save(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            pc = listOf(storedRecord(150, 20)),
            party = listOf(partyRecord(151, 0x33))
        )
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()

        val prepared = Gen6ConvertSaveWorkflow.prepare(
            source,
            template,
            "Pokemon_Omega_Ruby.sav",
            destinationBaselineAccepted = true
        )

        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
        assertEquals(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            Gen6PcStorageLayout.detectGame(prepared.bytes)
        )
        assertTrue(
            Gen6PartyStorageLayout.validate(
                prepared.bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            ).isValid
        )
        assertEquals(2, prepared.bytes[0x14200 + 6 * 0x104].toInt() and 0xFF)
    }

    @Test
    fun preparedSaveSnapshotsReviewedPreviewMetadata() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val basePreview = Gen6ConvertSaveWorkflow.preview(
            source,
            "Pokemon_Omega_Ruby.sav"
        )
        val selectedIds = mutableListOf<String>()
        val preserved = basePreview.preserved.toMutableList()
        val omitted = basePreview.omitted.toMutableList()
        val choices =
            basePreview.availableVersionExclusives.toMutableList()
        val mutablePreview = basePreview.copy(
            request = basePreview.request.copy(
                selectedVersionExclusiveIds = selectedIds
            ),
            preserved = preserved,
            omitted = omitted,
            availableVersionExclusives = choices
        )
        val expectedPreserved = preserved.toList()
        val expectedOmitted = omitted.toList()
        val prepared = Gen6PreparedSave(
            mutablePreview,
            save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        )

        selectedIds += "late-selection"
        preserved.clear()
        omitted.clear()
        choices += Gen6VersionExclusiveChoice(
            id = "late-choice",
            destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
            species = 270,
            legalityEvidence = "fixture:late-choice",
            recordFingerprint = gen6VersionExclusiveRecordFingerprint(
                storedRecord(270, 12)
            )
        )

        assertTrue(
            prepared.preview.request.selectedVersionExclusiveIds.isEmpty()
        )
        assertEquals(expectedPreserved, prepared.preview.preserved)
        assertEquals(expectedOmitted, prepared.preview.omitted)
        assertTrue(prepared.preview.availableVersionExclusives.isEmpty())
    }

    @Test
    fun preparedSaveRejectsWrongGameAndCorruptDestinationBytes() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val preview = Gen6ConvertSaveWorkflow.preview(
            source,
            "Pokemon_Omega_Ruby.sav"
        )
        val wrongGame = source.copyOf()
        val corruptDestination = save(
            Gen6ConverterGame.ALPHA_SAPPHIRE
        ).also {
            it[0x15000] =
                (it[0x15000].toInt() xor 1).toByte()
        }
        val wrongGameBefore = wrongGame.copyOf()
        val corruptBefore = corruptDestination.copyOf()

        val wrongFailure =
            assertThrows(IllegalArgumentException::class.java) {
                Gen6PreparedSave(preview, wrongGame)
            }
        val corruptFailure =
            assertThrows(IllegalArgumentException::class.java) {
                Gen6PreparedSave(preview, corruptDestination)
            }

        assertTrue(wrongFailure.message!!.contains("validated destination"))
        assertTrue(corruptFailure.message!!.contains("validated destination"))
        assertArrayEquals(wrongGameBefore, wrongGame)
        assertArrayEquals(corruptBefore, corruptDestination)
    }

    @Test
    fun wrongDestinationTemplateIsRejectedWithoutMutation() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val wrongTemplate = save(Gen6ConverterGame.OMEGA_RUBY)
        val sourceBefore = source.copyOf()
        val templateBefore = wrongTemplate.copyOf()

        assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.prepare(
                source,
                wrongTemplate,
                "Pokemon_Omega_Ruby.sav",
                destinationBaselineAccepted = true
            )
        }
        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, wrongTemplate)
    }

    @Test
    fun destinationBaselineRequiresExplicitConsentWithoutMutation() {
        val source = save(
            Gen6ConverterGame.OMEGA_RUBY,
            pc = listOf(storedRecord(25, 10))
        )
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.prepare(
                source,
                template,
                "Pokemon_Omega_Ruby.sav",
                destinationBaselineAccepted = false
            )
        }

        assertTrue(failure.message!!.contains("destination progress"))
        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
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