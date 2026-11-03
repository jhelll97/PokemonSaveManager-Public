package com.example.pokemonsavemanager

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6ConcreteTemplateWriterTest {
    private val request = Gen6ConverterRequest(
        Gen6ConverterGame.OMEGA_RUBY,
        Gen6ConverterGame.ALPHA_SAPPHIRE
    )

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

    private fun partyRecord(
        species: Int,
        experience: Int,
        marker: Int
    ): ByteArray = ByteArray(0x104).also { party ->
        storedRecord(species, experience).copyInto(party)
        for (index in 0xE8 until party.size) party[index] = marker.toByte()
    }

    private fun save(
        game: Gen6ConverterGame,
        pcRecords: Map<Int, ByteArray> = emptyMap(),
        party: List<ByteArray> = emptyList()
    ): ByteArray {
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
        pcRecords.forEach { (slot, record) ->
            record.copyInto(bytes, boxOffset + slot * 0xE8)
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
    fun concreteWriterMigratesPcAndPartyIntoSeparateValidatedSave() {
        val sourcePc = listOf(storedRecord(25, 10), storedRecord(64, 20))
        val sourceParty = partyRecord(280, 30, 0x24)
        val existingPc = storedRecord(150, 40)
        val existingParty = partyRecord(151, 50, 0x35)
        val sourceBytes = save(
            request.source,
            mapOf(0 to sourcePc[0], 1 to sourcePc[1]),
            listOf(sourceParty)
        )
        val template = save(
            request.destination,
            mapOf(0 to existingPc),
            listOf(existingParty)
        )
        val sourceBefore = sourceBytes.copyOf()
        val templateBefore = template.copyOf()
        val directory = Files.createTempDirectory("gen6-concrete-writer").toFile()
        try {
            val sourceFile =
                File(directory, "Omega_Ruby.sav").apply { writeBytes(sourceBytes) }
            val requested =
                File(directory, "Alpha_Sapphire_Converted.sav")
            val writer = Gen6ConcreteTemplateWriterFactory.create(
                request.source,
                request.destination,
                template
            )

            val outputFile = Gen6SaveConversionService(writer).convert(
                sourceFile,
                requested,
                request
            )
            val output = outputFile.readBytes()

            assertEquals(requested.canonicalFile, outputFile.canonicalFile)
            assertFalse(outputFile.canonicalFile == sourceFile.canonicalFile)
            assertArrayEquals(sourceBefore, sourceFile.readBytes())
            assertArrayEquals(templateBefore, template)
            assertArrayEquals(existingPc, output.copyOfRange(0x33000, 0x330E8))
            sourcePc.forEachIndexed { index, expected ->
                val offset = 0x33000 + (index + 1) * 0xE8
                assertArrayEquals(
                    expected,
                    output.copyOfRange(offset, offset + 0xE8)
                )
            }
            assertEquals(2, output[0x14200 + 6 * 0x104].toInt() and 0xFF)
            assertArrayEquals(
                existingParty,
                output.copyOfRange(0x14200, 0x14304)
            )
            assertArrayEquals(
                sourceParty,
                output.copyOfRange(0x14304, 0x14408)
            )
            val validation =
                Gen6PartyStorageLayout.validate(output, request.destination)
            assertTrue(validation.isValid)
            assertEquals(request.destination, validation.detectedGame)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun combinedMigrationKeepsExclusiveSelectionSnapshot() {
        val exclusive = storedRecord(270, 12)
        val catalog = Gen6VersionExclusiveCatalog(
            listOf(
                Gen6VersionExclusiveEntry(
                    id = "exclusive-a",
                    destinationGame = request.destination,
                    species = 270,
                    encryptedPk6 = exclusive,
                    legalityEvidence = "deterministic test evidence"
                )
            ),
            Gen6VersionExclusiveLegalityVerifier {
                    destination,
                    record,
                    _ ->
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
        val source = save(request.source)
        val template = save(request.destination)
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()

        val output = Gen6PcAndPartySaveMigration(catalog).migrate(
            source,
            template,
            request.copy(
                includeVersionExclusives = true,
                selectedVersionExclusiveIds = changingSelection
            )
        )

        assertTrue(changingSelection.isEmpty())
        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
        assertArrayEquals(
            exclusive,
            output.copyOfRange(0x33000, 0x33000 + 0xE8)
        )
        val validation =
            Gen6PartyStorageLayout.validate(output, request.destination)
        assertTrue(validation.isValid)
        assertEquals(request.destination, validation.detectedGame)
    }

    @Test
    fun fullDestinationPartyRejectsWholeConversionWithoutPublishing() {
        val sourceBytes = save(
            request.source,
            party = listOf(partyRecord(25, 10, 0x11))
        )
        val template = save(
            request.destination,
            party = (1..6).map { partyRecord(it, it, it) }
        )
        val sourceBefore = sourceBytes.copyOf()
        val templateBefore = template.copyOf()
        val directory = Files.createTempDirectory("gen6-concrete-capacity").toFile()
        try {
            val sourceFile =
                File(directory, "Omega_Ruby.sav").apply { writeBytes(sourceBytes) }
            val requested =
                File(directory, "Alpha_Sapphire_Converted.sav")
            val writer = Gen6ConcreteTemplateWriterFactory.create(
                request.source,
                request.destination,
                template
            )

            assertThrows(IllegalArgumentException::class.java) {
                Gen6SaveConversionService(writer).convert(
                    sourceFile,
                    requested,
                    request
                )
            }
            assertFalse(requested.exists())
            assertArrayEquals(sourceBefore, sourceFile.readBytes())
            assertArrayEquals(templateBefore, template)
        } finally {
            directory.deleteRecursively()
        }
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
