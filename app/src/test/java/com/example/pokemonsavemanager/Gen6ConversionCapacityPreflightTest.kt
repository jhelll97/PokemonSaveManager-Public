package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6ConversionCapacityPreflightTest {
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

    private fun save(
        game: Gen6ConverterGame,
        pc: List<ByteArray> = emptyList(),
        party: List<ByteArray> = emptyList()
    ): ByteArray {
        require(pc.size <= 31 * 30)
        require(party.size <= 6)
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
            record.copyInto(bytes, boxOffset + index * Gen6Pk6Adapter.STORED_SIZE)
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
    fun reportRejectsNegativeCapacityInputs() {
        assertThrows(IllegalArgumentException::class.java) {
            Gen6ConversionCapacityReport(-1, 0, 1, 0, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6ConversionCapacityReport(0, -1, 1, 0, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6ConversionCapacityReport(0, 0, -1, 0, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6ConversionCapacityReport(0, 0, 1, -1, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6ConversionCapacityReport(0, 0, 1, 0, -1)
        }
    }

    @Test
    fun reportRejectsImpossiblePhysicalCapacities() {
        listOf(
            { Gen6ConversionCapacityReport(931, 0, 930, 0, 6) },
            { Gen6ConversionCapacityReport(0, 931, 930, 0, 6) },
            { Gen6ConversionCapacityReport(0, 0, 931, 0, 6) },
            { Gen6ConversionCapacityReport(0, 0, 930, 7, 6) },
            { Gen6ConversionCapacityReport(0, 0, 930, 0, 7) }
        ).forEach { invalidReport ->
            assertThrows(IllegalArgumentException::class.java) {
                invalidReport()
            }
        }
    }

    @Test
    fun reportRejectsCombinedPcRequirementAbovePhysicalCapacity() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConversionCapacityReport(
                sourcePcRecords = 930,
                selectedVersionExclusiveRecords = 1,
                destinationPcSlots = 930,
                sourcePartyRecords = 0,
                destinationPartySlots = 0
            )
        }

        assertTrue(failure.message!!.contains("Required Gen 6 PC slot count"))
    }

    @Test
    fun reportRejectsOverflowingRequiredPcSlotCount() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConversionCapacityReport(
                sourcePcRecords = Int.MAX_VALUE,
                selectedVersionExclusiveRecords = 1,
                destinationPcSlots = Int.MAX_VALUE,
                sourcePartyRecords = 0,
                destinationPartySlots = 0
            )
        }

        assertTrue(failure.message!!.contains("exceeds the supported range"))
    }

    @Test
    fun reportExposesExactPcAndPartyShortfalls() {
        val report = Gen6ConversionCapacityReport(
            sourcePcRecords = 7,
            selectedVersionExclusiveRecords = 2,
            destinationPcSlots = 5,
            sourcePartyRecords = 6,
            destinationPartySlots = 4
        )

        assertEquals(4, report.missingPcSlots)
        assertEquals(2, report.missingPartySlots)
        assertFalse(report.pcFits)
        assertFalse(report.partyFits)
        assertFalse(report.canConvert)
    }

    @Test
    fun reportCountsPcPartyAndSelectedExtrasWithoutMutation() {
        val source = save(
            Gen6ConverterGame.OMEGA_RUBY,
            pc = listOf(storedRecord(25, 10), storedRecord(64, 20)),
            party = listOf(partyRecord(150, 0x31))
        )
        val template = save(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            pc = listOf(storedRecord(151, 30)),
            party = (1..5).map { partyRecord(it, it) }
        )
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()
        val request = Gen6ConverterRequest(
            source = Gen6ConverterGame.OMEGA_RUBY,
            destination = Gen6ConverterGame.ALPHA_SAPPHIRE,
            includeVersionExclusives = true,
            selectedVersionExclusiveIds = listOf("exclusive-a", "exclusive-b")
        )

        val report = Gen6ConversionCapacityPreflight.inspect(
            source,
            template,
            request
        )

        assertEquals(2, report.sourcePcRecords)
        assertEquals(2, report.selectedVersionExclusiveRecords)
        assertEquals(4, report.requiredPcSlots)
        assertEquals(929, report.destinationPcSlots)
        assertEquals(1, report.sourcePartyRecords)
        assertEquals(1, report.destinationPartySlots)
        assertTrue(report.pcFits)
        assertTrue(report.partyFits)
        assertEquals(0, report.missingPcSlots)
        assertEquals(0, report.missingPartySlots)
        assertTrue(report.canConvert)
        report.requireFits()
        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
    }

    @Test
    fun capacityUsesSelectionSnapshotWhenCallerListChangesDuringPlanning() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val backing = mutableListOf("exclusive-a")
        val changingSelection = object : AbstractList<String>() {
            override val size: Int
                get() = backing.size

            override fun get(index: Int): String {
                val value = backing[index]
                backing.clear()
                return value
            }
        }

        val report = Gen6ConversionCapacityPreflight.inspect(
            source,
            template,
            Gen6ConverterRequest(
                source = Gen6ConverterGame.OMEGA_RUBY,
                destination = Gen6ConverterGame.ALPHA_SAPPHIRE,
                includeVersionExclusives = true,
                selectedVersionExclusiveIds = changingSelection
            )
        )

        assertTrue(changingSelection.isEmpty())
        assertEquals(1, report.selectedVersionExclusiveRecords)
        assertEquals(1, report.requiredPcSlots)
    }

    @Test
    fun selectedExtrasAreIncludedWhenPcCapacityIsChecked() {
        val occupied = storedRecord(25, 10)
        val source = save(
            Gen6ConverterGame.OMEGA_RUBY,
            pc = listOf(storedRecord(64, 20))
        )
        val template = save(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            pc = List(929) { occupied }
        )
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()
        val request = Gen6ConverterRequest(
            source = Gen6ConverterGame.OMEGA_RUBY,
            destination = Gen6ConverterGame.ALPHA_SAPPHIRE,
            includeVersionExclusives = true,
            selectedVersionExclusiveIds = listOf("exclusive-a")
        )

        val report = Gen6ConversionCapacityPreflight.inspect(
            source,
            template,
            request
        )
        val failure = assertThrows(IllegalArgumentException::class.java) {
            report.requireFits()
        }

        assertEquals(1, report.destinationPcSlots)
        assertEquals(2, report.requiredPcSlots)
        assertFalse(report.pcFits)
        assertFalse(report.canConvert)
        assertTrue(failure.message!!.contains("1 source PC records"))
        assertTrue(failure.message!!.contains("1 selected version-exclusive records"))
        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
    }

    @Test
    fun destinationPreviewRejectsCapacityThatUndercountsSelectedRecords() {
        val source = save(Gen6ConverterGame.OMEGA_RUBY)
        val sourceBefore = source.copyOf()
        val selectedRecord = storedRecord(270, 11)
        val catalog = Gen6VersionExclusiveCatalog(
            listOf(
                Gen6VersionExclusiveEntry(
                    id = "lotad-as",
                    destinationGame = Gen6ConverterGame.ALPHA_SAPPHIRE,
                    species = 270,
                    encryptedPk6 = selectedRecord,
                    legalityEvidence = "fixture:lotad-as"
                )
            ),
            GEN6_TEST_EXCLUSIVE_VERIFIER
        )
        val conversion = Gen6ConvertSaveWorkflow.preview(
            sourceBytes = source,
            sourceLabel = "Pokemon_Omega_Ruby.sav",
            selectedVersionExclusiveIds = listOf("lotad-as"),
            versionExclusiveCatalog = catalog
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertDestinationPreview(
                conversion = conversion,
                capacity = Gen6ConversionCapacityReport(
                    sourcePcRecords = 0,
                    selectedVersionExclusiveRecords = 0,
                    destinationPcSlots = 930,
                    sourcePartyRecords = 0,
                    destinationPartySlots = 6
                )
            )
        }

        assertTrue(failure.message!!.contains("count every selected"))
        assertArrayEquals(sourceBefore, source)
    }

    @Test
    fun workflowRejectsFullPartyBeforeSerializationAndPreservesInputs() {
        val source = save(
            Gen6ConverterGame.OMEGA_RUBY,
            party = listOf(partyRecord(25, 0x11))
        )
        val template = save(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            party = (1..6).map { partyRecord(it, it) }
        )
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6ConvertSaveWorkflow.prepare(
                source,
                template,
                "Pokemon_Omega_Ruby.sav",
                destinationBaselineAccepted = true
            )
        }

        assertTrue(failure.message!!.contains("0 vacant Gen 6 party slots"))
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
