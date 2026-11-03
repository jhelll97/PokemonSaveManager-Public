package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class Gen6Pk6DestinationReadbackTest {
    private val request = Gen6ConverterRequest(
        Gen6ConverterGame.OMEGA_RUBY, Gen6ConverterGame.ALPHA_SAPPHIRE
    )

    // Synthetic records exercise PK6 encryption/checksum, not game compatibility.
    private fun record(species: Int, experience: Int): ByteArray {
        val plain = ByteArray(Gen6Pk6Adapter.STORED_SIZE)
        plain[0x08] = (species and 255).toByte()
        plain[0x09] = (species ushr 8).toByte()
        plain[0x10] = experience.toByte()
        val sum = species + experience
        plain[0x06] = (sum and 255).toByte()
        plain[0x07] = (sum ushr 8).toByte()
        return Gen6Pk6Adapter.encode(Gen6Pk6Adapter.decode(plain))
    }

    private val originals get() = listOf(record(25, 10), record(64, 20))

    private fun placedRecords(save: ByteArray): List<ByteArray> =
        (0..1).map { index ->
            val offset = index * Gen6Pk6Adapter.STORED_SIZE
            save.copyOfRange(offset, offset + Gen6Pk6Adapter.STORED_SIZE)
        }

    private fun migration(
        reader: Gen6Pk6DestinationRecordReader,
        records: List<ByteArray> = originals
    ) = Gen6Pk6RecordSaveMigration(
            extractor = Gen6Pk6SourceRecordExtractor { _, _ -> records },
            inserter = Gen6Pk6DestinationRecordInserter { save, game, records ->
                assertEquals(request.destination, game)
                records.forEachIndexed { index, bytes ->
                    bytes.copyInto(save, index * Gen6Pk6Adapter.STORED_SIZE)
                }
                save
            },
            capacity = Gen6Pk6DestinationCapacity { _, _ -> 2 },
            destinationReader = reader
        )

    @Test
    fun placedRecordsMustReopenInOrderWithExactPayloads() {
        val source = byteArrayOf(11, 22, 33)
        val template = ByteArray(Gen6Pk6Adapter.STORED_SIZE * 2)
        val output = migration(Gen6Pk6DestinationRecordReader { bytes, game, recordCount ->
            assertEquals(request.destination, game)
            assertEquals(originals.size, recordCount)
            placedRecords(bytes).take(recordCount)
        }).migrate(source, template, request)
        placedRecords(output).forEachIndexed { index, bytes ->
            assertArrayEquals(
                Gen6Pk6Adapter.decode(originals[index]).decryptedBytes,
                Gen6Pk6Adapter.decode(bytes).decryptedBytes
            )
        }
        assertArrayEquals(byteArrayOf(11, 22, 33), source)
        assertArrayEquals(ByteArray(template.size), template)
    }


    @Test
    fun destinationReaderReceivesActualTransferCount() {
        val source = byteArrayOf(11, 22, 33)
        val template = ByteArray(Gen6Pk6Adapter.STORED_SIZE * 2)
        val transferred = originals.take(1)

        val output = migration(
            Gen6Pk6DestinationRecordReader { bytes, game, recordCount ->
                assertEquals(request.destination, game)
                assertEquals(transferred.size, recordCount)
                placedRecords(bytes).take(recordCount)
            },
            transferred
        ).migrate(source, template, request)

        assertArrayEquals(
            Gen6Pk6Adapter.decode(transferred.single()).decryptedBytes,
            Gen6Pk6Adapter.decode(placedRecords(output).first()).decryptedBytes
        )
        assertArrayEquals(byteArrayOf(11, 22, 33), source)
        assertArrayEquals(ByteArray(template.size), template)
    }

    @Test
    fun droppedReorderedOrChangedRecordsAreRejectedWithoutChangingInputs() {
        val readers = listOf<Gen6Pk6DestinationRecordReader>(
            Gen6Pk6DestinationRecordReader { bytes, _, _ -> placedRecords(bytes).take(1) },
            Gen6Pk6DestinationRecordReader { bytes, _, _ -> placedRecords(bytes).reversed() },
            Gen6Pk6DestinationRecordReader { bytes, _, _ ->
                listOf(record(25, 11), placedRecords(bytes)[1])
            }
        )
        for (reader in readers) {
            val source = byteArrayOf(11, 22, 33)
            val template = ByteArray(Gen6Pk6Adapter.STORED_SIZE * 2)
            assertThrows(IllegalStateException::class.java) {
                migration(reader).migrate(source, template, request)
            }
            assertArrayEquals(byteArrayOf(11, 22, 33), source)
            assertArrayEquals(ByteArray(template.size), template)
        }
    }

    @Test
    fun invalidChecksumInReopenedRecordIsRejected() {
        val source = byteArrayOf(11, 22, 33)
        val template = ByteArray(Gen6Pk6Adapter.STORED_SIZE * 2)
        val reader = Gen6Pk6DestinationRecordReader { bytes, _, _ ->
            val records = placedRecords(bytes)
            records[0][0x06] = (records[0][0x06].toInt() xor 1).toByte()
            records
        }
        assertThrows(IllegalArgumentException::class.java) {
            migration(reader).migrate(source, template, request)
        }
        assertArrayEquals(byteArrayOf(11, 22, 33), source)
        assertArrayEquals(ByteArray(template.size), template)
    }

    @Test
    fun plaintextReadbackIsRejectedEvenWhenItsDecryptedPayloadMatches() {
        val source = byteArrayOf(11, 22, 33)
        val template = ByteArray(Gen6Pk6Adapter.STORED_SIZE * 2)
        val reader = Gen6Pk6DestinationRecordReader { bytes, _, _ ->
            val records = placedRecords(bytes)
            listOf(
                Gen6Pk6Adapter.decode(records[0]).decryptedBytes.copyOf(),
                records[1]
            )
        }

        val failure = assertThrows(IllegalStateException::class.java) {
            migration(reader).migrate(source, template, request)
        }

        assertEquals(
            "Destination PK6 record 0 did not preserve exact encrypted bytes after placement.",
            failure.message
        )
        assertArrayEquals(byteArrayOf(11, 22, 33), source)
        assertArrayEquals(ByteArray(template.size), template)
    }

    @Test
    fun readbackMustNotMutateDestinationBytes() {
        val source = byteArrayOf(11, 22, 33)
        val template = ByteArray(Gen6Pk6Adapter.STORED_SIZE * 2)
        val reader = Gen6Pk6DestinationRecordReader { bytes, _, _ ->
            val records = placedRecords(bytes)
            bytes[0] = (bytes[0].toInt() xor 1).toByte()
            records
        }
        assertThrows(IllegalStateException::class.java) {
            migration(reader).migrate(source, template, request)
        }
        assertArrayEquals(byteArrayOf(11, 22, 33), source)
        assertArrayEquals(ByteArray(template.size), template)
    }

    @Test
    fun nonemptyTransferWithoutReadbackProviderFailsBeforePlacement() {
        var placed = false
        val source = byteArrayOf(11, 22, 33)
        val template = ByteArray(Gen6Pk6Adapter.STORED_SIZE * 2)
        val migration = Gen6Pk6RecordSaveMigration(
            extractor = Gen6Pk6SourceRecordExtractor { _, _ -> originals },
            inserter = Gen6Pk6DestinationRecordInserter { bytes, _, _ ->
                placed = true
                bytes
            },
            capacity = Gen6Pk6DestinationCapacity { _, _ -> 2 }
        )
        assertThrows(IllegalArgumentException::class.java) {
            migration.migrate(source, template, request)
        }
        assertFalse(placed)
        assertArrayEquals(byteArrayOf(11, 22, 33), source)
        assertArrayEquals(ByteArray(template.size), template)
    }
}
