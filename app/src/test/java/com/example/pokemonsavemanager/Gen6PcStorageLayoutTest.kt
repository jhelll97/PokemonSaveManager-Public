package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6PcStorageLayoutTest {
    private val request = Gen6ConverterRequest(
        Gen6ConverterGame.OMEGA_RUBY,
        Gen6ConverterGame.ALPHA_SAPPHIRE
    )

    private fun record(
        species: Int,
        experience: Int,
        gender: Int = 0,
        isEgg: Boolean = false,
        encryptionConstant: Long = 0
    ): ByteArray {
        val plain = ByteArray(Gen6Pk6Adapter.STORED_SIZE)
        repeat(4) { index ->
            plain[index] =
                ((encryptionConstant ushr (index * 8)) and 0xFF).toByte()
        }
        plain[0x08] = (species and 0xFF).toByte()
        plain[0x09] = (species ushr 8).toByte()
        plain[0x10] = experience.toByte()
        plain[0x1D] = ((gender and 3) shl 1).toByte()
        if (isEgg) plain[0x77] = 0x40.toByte()
        val sum = species + experience + ((gender and 3) shl 9) +
            if (isEgg) 0x4000 else 0
        plain[0x06] = (sum and 0xFF).toByte()
        plain[0x07] = (sum ushr 8).toByte()
        return Gen6Pk6Adapter.encode(Gen6Pk6Adapter.decode(plain))
    }

    private fun save(
        game: Gen6ConverterGame,
        records: Map<Int, ByteArray> = emptyMap()
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
        records.forEach { (slot, pk6) ->
            require(slot in 0 until 31 * 30)
            pk6.copyInto(bytes, boxOffset + slot * Gen6Pk6Adapter.STORED_SIZE)
        }
        writeBlockChecksum(bytes, 20, 0x15000, if (oras) 0x011CC else 0x006A0)
        writeBlockChecksum(bytes, if (oras) 56 else 53, boxOffset, 0x34AD0)
        return bytes
    }

    @Test
    fun orasPcRecordsMigrateIntoVacantSlotsRegisterDexAndRewriteChecksums() {
        val originals = listOf(record(25, 10), record(64, 20))
        val existing = record(150, 30)
        val source = save(
            Gen6ConverterGame.OMEGA_RUBY,
            mapOf(0 to originals[0], 1 to originals[1])
        )
        val template = save(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            mapOf(0 to existing)
        )
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()
        val sourceLayout = Gen6PcStorageLayout(request.source, source)
        val destinationLayout = Gen6PcStorageLayout(request.destination, template)
        assertEquals(31 * 30 - 1, destinationLayout.capacityProvider().availableSlots(
            template,
            request.destination
        ))

        val migration = Gen6Pk6RecordSaveMigration(
            extractor = sourceLayout.sourceExtractor(),
            inserter = destinationLayout.recordInserter(),
            capacity = destinationLayout.capacityProvider(),
            destinationReader = destinationLayout.recordReader()
        )
        val output = migration.migrate(source, template, request)

        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
        assertFalse(output.contentEquals(template))
        assertArrayEquals(existing, output.copyOfRange(0x33000, 0x33000 + 0xE8))
        originals.forEachIndexed { index, expected ->
            val offset = 0x33000 + (index + 1) * 0xE8
            assertArrayEquals(expected, output.copyOfRange(offset, offset + 0xE8))
        }
        for (species in listOf(25, 64)) {
            assertTrue(dexFlag(output, species, 0))
            assertTrue(dexFlag(output, species, 3))
            assertTrue(dexFlag(output, species, 7))
            assertEquals(1, orasSeenCount(output, species))
        }
        assertFalse(dexChecksum(output).contentEquals(dexChecksum(template)))
        val validation = destinationLayout.bytesValidator().validate(output, request)
        assertTrue(validation.isValid)
        assertEquals(request.destination, validation.detectedGame)
    }

    @Test
    fun genderlessPcRecordUsesMaleGenderDexRegions() {
        val source = save(
            Gen6ConverterGame.OMEGA_RUBY,
            mapOf(0 to record(81, 10, gender = 2))
        )
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val sourceLayout = Gen6PcStorageLayout(request.source, source)
        val destinationLayout = Gen6PcStorageLayout(request.destination, template)
        val migration = Gen6Pk6RecordSaveMigration(
            extractor = sourceLayout.sourceExtractor(),
            inserter = destinationLayout.recordInserter(),
            capacity = destinationLayout.capacityProvider(),
            destinationReader = destinationLayout.recordReader()
        )

        val output = migration.migrate(source, template, request)

        assertTrue(dexFlag(output, 81, 3))
        assertTrue(dexFlag(output, 81, 7))
        assertFalse(dexFlag(output, 81, 4))
        assertFalse(dexFlag(output, 81, 8))
        assertTrue(destinationLayout.bytesValidator().validate(output, request).isValid)
    }

    @Test
    fun eggPcRecordMigratesWithoutRegisteringDex() {
        val egg = record(175, 10, isEgg = true)
        val source = save(
            Gen6ConverterGame.OMEGA_RUBY,
            mapOf(0 to egg)
        )
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val sourceLayout = Gen6PcStorageLayout(request.source, source)
        val destinationLayout = Gen6PcStorageLayout(request.destination, template)
        val migration = Gen6Pk6RecordSaveMigration(
            extractor = sourceLayout.sourceExtractor(),
            inserter = destinationLayout.recordInserter(),
            capacity = destinationLayout.capacityProvider(),
            destinationReader = destinationLayout.recordReader()
        )

        val output = migration.migrate(source, template, request)

        assertArrayEquals(egg, output.copyOfRange(0x33000, 0x33000 + 0xE8))
        for (region in 0..8) assertFalse(dexFlag(output, 175, region))
        assertTrue(destinationLayout.bytesValidator().validate(output, request).isValid)
    }

    @Test
    fun firstPcSpindaSetsPokedexPatternWithoutLaterOverwrite() {
        val firstPattern = 0x12345678L
        val secondPattern = 0x76543210L
        val source = save(
            Gen6ConverterGame.OMEGA_RUBY,
            mapOf(
                0 to record(327, 10, encryptionConstant = firstPattern),
                1 to record(327, 20, encryptionConstant = secondPattern)
            )
        )
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val sourceLayout = Gen6PcStorageLayout(request.source, source)
        val destinationLayout = Gen6PcStorageLayout(request.destination, template)
        val migration = Gen6Pk6RecordSaveMigration(
            extractor = sourceLayout.sourceExtractor(),
            inserter = destinationLayout.recordInserter(),
            capacity = destinationLayout.capacityProvider(),
            destinationReader = destinationLayout.recordReader()
        )

        val output = migration.migrate(source, template, request)

        assertEquals(firstPattern, readU32(output, 0x15000 + 0x0680))
        assertTrue(dexFlag(output, 327, 0))
        assertTrue(destinationLayout.bytesValidator().validate(output, request).isValid)
    }

    @Test
    fun wrongTitleCorruptChecksumAndInvalidOccupiedRecordAreRejected() {
        val wrongTitle = save(Gen6ConverterGame.ALPHA_SAPPHIRE).apply {
            this[0x14004] = 27
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6PcStorageLayout(Gen6ConverterGame.ALPHA_SAPPHIRE, wrongTitle)
        }

        val corruptChecksum = save(Gen6ConverterGame.ALPHA_SAPPHIRE).apply {
            this[0x33000] = 1
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6PcStorageLayout(Gen6ConverterGame.ALPHA_SAPPHIRE, corruptChecksum)
        }

        val invalidRecord = save(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            mapOf(0 to ByteArray(0xE8) { 1 })
        )
        assertThrows(IllegalArgumentException::class.java) {
            Gen6PcStorageLayout(Gen6ConverterGame.ALPHA_SAPPHIRE, invalidRecord)
        }
    }

    private fun dexFlag(save: ByteArray, species: Int, region: Int): Boolean {
        val bit = species - 1
        val offset = 0x15000 + 0x08 + 0x60 * region + (bit ushr 3)
        return (save[offset].toInt() and (1 shl (bit and 7))) != 0
    }

    private fun orasSeenCount(save: ByteArray, species: Int): Int {
        val offset = 0x15000 + 0x0684 + species * 2
        return (save[offset].toInt() and 0xFF) or
            ((save[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readU32(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFF) or
            ((data[offset + 1].toLong() and 0xFF) shl 8) or
            ((data[offset + 2].toLong() and 0xFF) shl 16) or
            ((data[offset + 3].toLong() and 0xFF) shl 24)

    private fun dexChecksum(save: ByteArray): ByteArray {
        val offset = save.size - 0x200 + 0x14 + 20 * 8 + 6
        return save.copyOfRange(offset, offset + 2)
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
