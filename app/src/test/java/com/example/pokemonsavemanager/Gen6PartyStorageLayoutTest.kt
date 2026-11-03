package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6PartyStorageLayoutTest {
    private val request = Gen6ConverterRequest(
        Gen6ConverterGame.OMEGA_RUBY,
        Gen6ConverterGame.ALPHA_SAPPHIRE
    )

    private fun storedRecord(
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

    private fun partyRecord(
        species: Int,
        experience: Int,
        partyMarker: Int,
        gender: Int = 0,
        isEgg: Boolean = false,
        encryptionConstant: Long = 0
    ): ByteArray = ByteArray(0x104).also { party ->
        storedRecord(
            species,
            experience,
            gender,
            isEgg,
            encryptionConstant
        ).copyInto(party)
        for (index in 0xE8 until party.size) {
            party[index] = partyMarker.toByte()
        }
    }

    private fun save(
        game: Gen6ConverterGame,
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
        party.forEachIndexed { index, record ->
            record.copyInto(bytes, 0x14200 + index * 0x104)
        }
        bytes[0x14200 + 6 * 0x104] = party.size.toByte()
        val boxOffset = if (oras) 0x33000 else 0x22600
        writeBlockChecksum(bytes, 18, 0x14200, 0x61C)
        writeBlockChecksum(bytes, 20, 0x15000, if (oras) 0x011CC else 0x006A0)
        writeBlockChecksum(bytes, if (oras) 56 else 53, boxOffset, 0x34AD0)
        return bytes
    }

    @Test
    fun orasPartyRecordsAppendPreserveInputsRegisterDexAndRewriteChecksums() {
        val migrating = listOf(
            partyRecord(25, 10, 0x21),
            partyRecord(64, 20, 0x32)
        )
        val existing = partyRecord(150, 30, 0x43)
        val source = save(Gen6ConverterGame.OMEGA_RUBY, migrating)
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE, listOf(existing))
        val sourceBefore = source.copyOf()
        val templateBefore = template.copyOf()
        val sourceLayout = Gen6PartyStorageLayout(request.source, source)
        val destinationLayout =
            Gen6PartyStorageLayout(request.destination, template)

        val records = sourceLayout.sourceRecords(source, request.source)
        assertEquals(2, records.size)
        assertEquals(5, destinationLayout.availableSlots(
            template,
            request.destination
        ))

        val output = destinationLayout.insertRecords(
            template,
            request.destination,
            records
        )

        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(templateBefore, template)
        assertFalse(output.contentEquals(template))
        assertEquals(3, output[0x14200 + 6 * 0x104].toInt() and 0xFF)
        assertArrayEquals(existing, output.copyOfRange(0x14200, 0x14304))
        migrating.forEachIndexed { index, expected ->
            val offset = 0x14200 + (index + 1) * 0x104
            assertArrayEquals(expected, output.copyOfRange(offset, offset + 0x104))
        }
        for (species in listOf(25, 64)) {
            assertTrue(dexFlag(output, species, 0))
            assertTrue(dexFlag(output, species, 3))
            assertTrue(dexFlag(output, species, 7))
            assertEquals(1, orasSeenCount(output, species))
        }
        assertFalse(blockChecksum(output, 18).contentEquals(
            blockChecksum(template, 18)
        ))
        assertFalse(blockChecksum(output, 20).contentEquals(
            blockChecksum(template, 20)
        ))
        val validation = destinationLayout.bytesValidator().validate(output, request)
        assertTrue(validation.isValid)
        assertEquals(request.destination, validation.detectedGame)
    }

    @Test
    fun genderlessPartyRecordUsesMaleGenderDexRegions() {
        val source = save(
            Gen6ConverterGame.OMEGA_RUBY,
            listOf(partyRecord(81, 10, 0x55, gender = 2))
        )
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val sourceLayout = Gen6PartyStorageLayout(request.source, source)
        val destinationLayout =
            Gen6PartyStorageLayout(request.destination, template)

        val output = destinationLayout.insertRecords(
            template,
            request.destination,
            sourceLayout.sourceRecords(source, request.source)
        )

        assertTrue(dexFlag(output, 81, 3))
        assertTrue(dexFlag(output, 81, 7))
        assertFalse(dexFlag(output, 81, 4))
        assertFalse(dexFlag(output, 81, 8))
        assertTrue(destinationLayout.bytesValidator().validate(output, request).isValid)
    }

    @Test
    fun eggPartyRecordMigratesWithoutRegisteringDex() {
        val egg = partyRecord(175, 10, 0x66, isEgg = true)
        val source = save(Gen6ConverterGame.OMEGA_RUBY, listOf(egg))
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val sourceLayout = Gen6PartyStorageLayout(request.source, source)
        val destinationLayout =
            Gen6PartyStorageLayout(request.destination, template)

        val output = destinationLayout.insertRecords(
            template,
            request.destination,
            sourceLayout.sourceRecords(source, request.source)
        )

        assertArrayEquals(egg, output.copyOfRange(0x14200, 0x14304))
        for (region in 0..8) assertFalse(dexFlag(output, 175, region))
        assertTrue(destinationLayout.bytesValidator().validate(output, request).isValid)
    }

    @Test
    fun firstPartySpindaSetsPokedexPatternWithoutLaterOverwrite() {
        val firstPattern = 0x12345678L
        val secondPattern = 0x76543210L
        val source = save(
            Gen6ConverterGame.OMEGA_RUBY,
            listOf(
                partyRecord(327, 10, 0x11, encryptionConstant = firstPattern),
                partyRecord(327, 20, 0x22, encryptionConstant = secondPattern)
            )
        )
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val sourceLayout = Gen6PartyStorageLayout(request.source, source)
        val destinationLayout =
            Gen6PartyStorageLayout(request.destination, template)

        val output = destinationLayout.insertRecords(
            template,
            request.destination,
            sourceLayout.sourceRecords(source, request.source)
        )

        assertEquals(firstPattern, readU32(output, 0x15000 + 0x0680))
        assertTrue(dexFlag(output, 327, 0))
        assertTrue(destinationLayout.bytesValidator().validate(output, request).isValid)
    }

    @Test
    fun exactPartyReadbackRejectsChangedCachedBattleDataWithValidChecksums() {
        val migrating = partyRecord(25, 10, 0x55)
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val templateBefore = template.copyOf()
        val layout = Gen6PartyStorageLayout(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            template
        )
        val reopened = layout.insertRecords(
            template,
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            listOf(migrating)
        ).apply {
            this[0x14200 + 0xE8] =
                (this[0x14200 + 0xE8].toInt() xor 1).toByte()
            writeBlockChecksum(this, 18, 0x14200, 0x61C)
        }

        assertTrue(
            Gen6PartyStorageLayout.validate(
                reopened,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            ).isValid
        )
        val failure = assertThrows(IllegalStateException::class.java) {
            layout.requirePlacedRecords(
                reopened,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                listOf(migrating)
            )
        }

        assertTrue(failure.message!!.contains("party record 0 changed"))
        assertArrayEquals(templateBefore, template)
    }

    @Test
    fun checksumValidDestinationWithoutRequiredPokedexOwnershipIsRejected() {
        val migrating = partyRecord(25, 10, 0x55)
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val templateBefore = template.copyOf()
        val layout = Gen6PartyStorageLayout(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            template
        )
        val reopened = layout.insertRecords(
            template,
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            listOf(migrating)
        ).apply {
            val bit = 25 - 1
            val ownedOffset = 0x15000 + 0x08 + (bit ushr 3)
            this[ownedOffset] = (
                this[ownedOffset].toInt() and
                    (1 shl (bit and 7)).inv()
                ).toByte()
            writeBlockChecksum(this, 20, 0x15000, 0x011CC)
        }

        assertTrue(
            Gen6PartyStorageLayout.validate(
                reopened,
                Gen6ConverterGame.ALPHA_SAPPHIRE
            ).isValid
        )
        val failure = assertThrows(IllegalStateException::class.java) {
            Gen6PokedexRegistrationVerifier.requireRegistered(
                reopened,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                listOf(
                    Gen6Pk6Adapter.decode(
                        migrating.copyOfRange(
                            0,
                            Gen6Pk6Adapter.STORED_SIZE
                        )
                    )
                )
            )
        }

        assertTrue(failure.message!!.contains("Pokédex ownership"))
        assertArrayEquals(templateBefore, template)
    }

    @Test
    fun corruptPartyAndOverCapacityMigrationAreRejectedWithoutMutation() {
        val corrupt = save(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            listOf(partyRecord(1, 10, 0x11))
        ).apply {
            this[0x14200 + 0xF0] = (this[0x14200 + 0xF0].toInt() xor 1).toByte()
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6PartyStorageLayout(Gen6ConverterGame.ALPHA_SAPPHIRE, corrupt)
        }

        val fullParty = (1..6).map { partyRecord(it, it, it) }
        val template = save(Gen6ConverterGame.ALPHA_SAPPHIRE, fullParty)
        val before = template.copyOf()
        val layout = Gen6PartyStorageLayout(
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            template
        )
        assertEquals(0, layout.availableSlots(
            template,
            Gen6ConverterGame.ALPHA_SAPPHIRE
        ))
        assertThrows(IllegalArgumentException::class.java) {
            layout.insertRecords(
                template,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                listOf(partyRecord(25, 10, 0x55))
            )
        }
        assertArrayEquals(before, template)
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

    private fun blockChecksum(save: ByteArray, blockId: Int): ByteArray {
        val offset = save.size - 0x200 + 0x14 + blockId * 8 + 6
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
