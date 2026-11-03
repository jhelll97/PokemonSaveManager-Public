package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6DocumentExportTransactionTest {
    private class Target(
        initialBytes: ByteArray = ByteArray(0),
        private val beforeReadFailure: Throwable? = null,
        private val readTransform: (ByteArray) -> ByteArray = { it.copyOf() },
        private val deleteResult: Boolean = true
    ) : Gen6DocumentExportTarget {
        var stored = initialBytes.copyOf()
        var beforeReads = 0
        var reads = 0
        var writes = 0
        var deleted = false

        override fun readBeforeWrite(): ByteArray {
            beforeReads += 1
            beforeReadFailure?.let { throw it }
            return stored.copyOf()
        }

        override fun write(bytes: ByteArray) {
            writes += 1
            stored = bytes.copyOf()
        }

        override fun read(): ByteArray {
            reads += 1
            return readTransform(stored.copyOf())
        }

        override fun delete(): Boolean {
            deleted = true
            stored = ByteArray(0)
            return deleteResult
        }
    }

    @Test
    fun verifiedEmptyDocumentPublishesWithoutCleanup() {
        val bytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val target = Target()

        val persisted = Gen6DocumentExportTransaction.exportNew(
            bytes,
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            target
        ) { true }

        assertArrayEquals(bytes, persisted)
        assertArrayEquals(bytes, target.stored)
        assertEquals(3, target.beforeReads)
        assertEquals(1, target.writes)
        assertFalse(target.deleted)
    }

    @Test
    fun callerMutationCannotChangeValidatedDestinationSnapshot() {
        val bytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val expected = bytes.copyOf()
        val target = Target()
        var sourceChecks = 0

        val persisted = Gen6DocumentExportTransaction.exportNew(
            bytes,
            Gen6ConverterGame.ALPHA_SAPPHIRE,
            target
        ) {
            sourceChecks += 1
            if (sourceChecks == 1) bytes.fill(0)
            true
        }

        assertArrayEquals(expected, persisted)
        assertArrayEquals(expected, target.stored)
        assertTrue(bytes.all { it == 0.toByte() })
        assertEquals(5, sourceChecks)
        assertFalse(target.deleted)
    }

    @Test
    fun existingDocumentIsPreservedWithoutWritingOrDeleting() {
        val bytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val existing = byteArrayOf(7, 6, 5, 4)
        val target = Target(initialBytes = existing)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6DocumentExportTransaction.exportNew(
                bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                target
            ) { true }
        }

        assertTrue(failure.message!!.contains("already contains data"))
        assertArrayEquals(existing, target.stored)
        assertEquals(0, target.writes)
        assertFalse(target.deleted)
    }

    @Test
    fun destinationOccupiedAfterFreshnessCheckIsPreservedWithoutWrite() {
        val bytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val concurrentBytes = byteArrayOf(4, 3, 2, 1)
        var stored = ByteArray(0)
        var beforeReads = 0
        var writes = 0
        var deletes = 0
        val target = object : Gen6DocumentExportTarget {
            override fun readBeforeWrite(): ByteArray {
                beforeReads += 1
                if (beforeReads == 2) {
                    stored = concurrentBytes.copyOf()
                }
                return stored.copyOf()
            }

            override fun write(bytes: ByteArray) {
                writes += 1
                stored = bytes.copyOf()
            }

            override fun read(): ByteArray = stored.copyOf()

            override fun delete(): Boolean {
                deletes += 1
                stored = ByteArray(0)
                return true
            }
        }

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6DocumentExportTransaction.exportNew(
                bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                target
            ) { true }
        }

        assertTrue(failure.message!!.contains("changed after its freshness check"))
        assertArrayEquals(concurrentBytes, stored)
        assertEquals(2, beforeReads)
        assertEquals(0, writes)
        assertEquals(0, deletes)
    }

    @Test
    fun destinationOccupiedImmediatelyBeforeWriteIsPreserved() {
        val bytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val concurrentBytes = byteArrayOf(9, 8, 7, 6)
        var stored = ByteArray(0)
        var beforeReads = 0
        var writes = 0
        var deletes = 0
        val target = object : Gen6DocumentExportTarget {
            override fun readBeforeWrite(): ByteArray {
                beforeReads += 1
                if (beforeReads == 3) {
                    stored = concurrentBytes.copyOf()
                }
                return stored.copyOf()
            }

            override fun write(bytes: ByteArray) {
                writes += 1
                stored = bytes.copyOf()
            }

            override fun read(): ByteArray = stored.copyOf()

            override fun delete(): Boolean {
                deletes += 1
                stored = ByteArray(0)
                return true
            }
        }

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6DocumentExportTransaction.exportNew(
                bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                target
            ) { true }
        }

        assertTrue(failure.message!!.contains("immediately before write"))
        assertArrayEquals(concurrentBytes, stored)
        assertEquals(3, beforeReads)
        assertEquals(0, writes)
        assertEquals(0, deletes)
    }

    @Test
    fun sourceChangeDuringFinalFreshnessReadPreventsWrite() {
        val bytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        var sourceIntact = true
        var beforeReads = 0
        var writes = 0
        var deletes = 0
        val target = object : Gen6DocumentExportTarget {
            override fun readBeforeWrite(): ByteArray {
                beforeReads += 1
                if (beforeReads == 2) sourceIntact = false
                return ByteArray(0)
            }

            override fun write(bytes: ByteArray) {
                writes += 1
            }

            override fun read(): ByteArray = ByteArray(0)

            override fun delete(): Boolean {
                deletes += 1
                return true
            }
        }

        val failure = assertThrows(IllegalStateException::class.java) {
            Gen6DocumentExportTransaction.exportNew(
                bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                target,
                sourceUnchanged = { sourceIntact }
            )
        }

        assertTrue(failure.message!!.contains("final destination freshness check"))
        assertEquals(2, beforeReads)
        assertEquals(0, writes)
        assertEquals(0, deletes)
    }

    @Test
    fun templateChangeDuringFinalFreshnessReadPreventsWrite() {
        val bytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        var templateIntact = true
        var beforeReads = 0
        var writes = 0
        var deletes = 0
        val target = object : Gen6DocumentExportTarget {
            override fun readBeforeWrite(): ByteArray {
                beforeReads += 1
                if (beforeReads == 2) templateIntact = false
                return ByteArray(0)
            }

            override fun write(bytes: ByteArray) {
                writes += 1
            }

            override fun read(): ByteArray = ByteArray(0)

            override fun delete(): Boolean {
                deletes += 1
                return true
            }
        }

        val failure = assertThrows(IllegalStateException::class.java) {
            Gen6DocumentExportTransaction.exportNew(
                bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                target,
                templateUnchanged = { templateIntact },
                sourceUnchanged = { true }
            )
        }

        assertTrue(failure.message!!.contains("final destination freshness check"))
        assertEquals(2, beforeReads)
        assertEquals(0, writes)
        assertEquals(0, deletes)
    }

    @Test
    fun failedFreshnessReadDoesNotWriteOrDeleteUnknownDocument() {
        val bytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val target = Target(
            beforeReadFailure = IllegalStateException("provider read failed")
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            Gen6DocumentExportTransaction.exportNew(
                bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                target
            ) { true }
        }

        assertEquals("provider read failed", failure.message)
        assertEquals(0, target.writes)
        assertFalse(target.deleted)
    }

    @Test
    fun changedReadbackDeletesIncompleteDocument() {
        val bytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val target = Target(readTransform = { value ->
            value.also { it[0] = (it[0].toInt() xor 1).toByte() }
        })

        assertThrows(IllegalArgumentException::class.java) {
            Gen6DocumentExportTransaction.exportNew(
                bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                target
            ) { true }
        }

        assertTrue(target.deleted)
        assertTrue(target.stored.isEmpty())
    }

    @Test
    fun wrongGameBytesAreRejectedBeforeDocumentIsTouched() {
        val bytes = save(Gen6ConverterGame.OMEGA_RUBY)
        val target = Target()

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6DocumentExportTransaction.exportNew(
                bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                target
            ) { true }
        }

        assertTrue(failure.message!!.contains("Prepared converted destination"))
        assertEquals(0, target.beforeReads)
        assertEquals(0, target.writes)
        assertFalse(target.deleted)
        assertTrue(target.stored.isEmpty())
    }

    @Test
    fun sourceChangeAfterFreshnessCheckPreventsWrite() {
        val bytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val target = Target()
        var checks = 0

        assertThrows(IllegalStateException::class.java) {
            Gen6DocumentExportTransaction.exportNew(
                bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                target
            ) { ++checks == 1 }
        }

        assertEquals(0, target.writes)
        assertFalse(target.deleted)
        assertEquals(2, checks)
    }

    @Test
    fun templateChangeBeforeWritePreservesBothDocuments() {
        val bytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val target = Target()

        val failure = assertThrows(IllegalStateException::class.java) {
            Gen6DocumentExportTransaction.exportNew(
                bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                target,
                sourceUnchanged = { true },
                templateUnchanged = { false }
            )
        }

        assertTrue(failure.message!!.contains("template changed before"))
        assertEquals(0, target.beforeReads)
        assertEquals(0, target.writes)
        assertFalse(target.deleted)
    }

    @Test
    fun templateChangeAfterWriteDeletesOnlyNewDestination() {
        val bytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val target = Target()
        var checks = 0

        val failure = assertThrows(IllegalStateException::class.java) {
            Gen6DocumentExportTransaction.exportNew(
                bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                target,
                sourceUnchanged = { true },
                templateUnchanged = { ++checks < 4 }
            )
        }

        assertTrue(failure.message!!.contains("template changed during"))
        assertTrue(target.deleted)
        assertEquals(4, checks)
    }

    @Test
    fun sourceChangeDuringFinalDestinationReadDeletesNewDocument() {
        val bytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        var sourceIntact = true
        var stored = ByteArray(0)
        var reads = 0
        var deletes = 0
        val target = object : Gen6DocumentExportTarget {
            override fun readBeforeWrite(): ByteArray = ByteArray(0)

            override fun write(bytes: ByteArray) {
                stored = bytes.copyOf()
            }

            override fun read(): ByteArray {
                reads += 1
                if (reads == 2) sourceIntact = false
                return stored.copyOf()
            }

            override fun delete(): Boolean {
                deletes += 1
                stored = ByteArray(0)
                return true
            }
        }

        val failure = assertThrows(IllegalStateException::class.java) {
            Gen6DocumentExportTransaction.exportNew(
                bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                target,
                sourceUnchanged = { sourceIntact }
            )
        }

        assertTrue(
            failure.message!!.contains(
                "source changed during final destination verification"
            )
        )
        assertEquals(2, reads)
        assertEquals(1, deletes)
        assertTrue(stored.isEmpty())
    }

    @Test
    fun templateChangeDuringFinalDestinationReadDeletesNewDocument() {
        val bytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        var templateIntact = true
        var stored = ByteArray(0)
        var reads = 0
        var deletes = 0
        val target = object : Gen6DocumentExportTarget {
            override fun readBeforeWrite(): ByteArray = ByteArray(0)

            override fun write(bytes: ByteArray) {
                stored = bytes.copyOf()
            }

            override fun read(): ByteArray {
                reads += 1
                if (reads == 2) templateIntact = false
                return stored.copyOf()
            }

            override fun delete(): Boolean {
                deletes += 1
                stored = ByteArray(0)
                return true
            }
        }

        val failure = assertThrows(IllegalStateException::class.java) {
            Gen6DocumentExportTransaction.exportNew(
                bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                target,
                templateUnchanged = { templateIntact },
                sourceUnchanged = { true }
            )
        }

        assertTrue(
            failure.message!!.contains(
                "template changed during final destination verification"
            )
        )
        assertEquals(2, reads)
        assertEquals(1, deletes)
        assertTrue(stored.isEmpty())
    }

    @Test
    fun destinationChangeDuringFinalInputChecksDeletesNewDocument() {
        val bytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val target = Target()
        var sourceChecks = 0

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6DocumentExportTransaction.exportNew(
                bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                target
            ) {
                sourceChecks += 1
                if (sourceChecks == 4) {
                    target.stored[0] =
                        (target.stored[0].toInt() xor 1).toByte()
                }
                true
            }
        }

        assertTrue(
            failure.message!!.contains(
                "changed during final source or template verification"
            )
        )
        assertEquals(2, target.reads)
        assertTrue(target.deleted)
        assertTrue(target.stored.isEmpty())
    }

    @Test
    fun destinationChangeDuringLastInputCheckDeletesNewDocument() {
        val bytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val target = Target()
        var sourceChecks = 0

        val failure = assertThrows(IllegalArgumentException::class.java) {
            Gen6DocumentExportTransaction.exportNew(
                bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                target
            ) {
                sourceChecks += 1
                if (sourceChecks == 5) {
                    target.stored[0] =
                        (target.stored[0].toInt() xor 1).toByte()
                }
                true
            }
        }

        assertTrue(
            failure.message!!.contains(
                "changed after final source or template verification"
            )
        )
        assertEquals(3, target.reads)
        assertEquals(5, sourceChecks)
        assertTrue(target.deleted)
        assertTrue(target.stored.isEmpty())
    }

    @Test
    fun sourceChangeAfterWriteDeletesDestination() {
        val bytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val target = Target()
        var checks = 0

        assertThrows(IllegalStateException::class.java) {
            Gen6DocumentExportTransaction.exportNew(
                bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                target
            ) { ++checks < 4 }
        }

        assertTrue(target.deleted)
        assertTrue(checks >= 4)
    }

    @Test
    fun cleanupFailureIsReportedExplicitly() {
        val bytes = save(Gen6ConverterGame.ALPHA_SAPPHIRE)
        val target = Target(
            readTransform = { ByteArray(1) },
            deleteResult = false
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            Gen6DocumentExportTransaction.exportNew(
                bytes,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                target
            ) { true }
        }

        assertTrue(failure.message!!.contains("could not be removed"))
        assertTrue(target.deleted)
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
        val pcOffset = if (oras) 0x33000 else 0x22600
        writeBlockChecksum(bytes, 18, 0x14200, 0x61C)
        writeBlockChecksum(
            bytes,
            20,
            0x15000,
            if (oras) 0x011CC else 0x006A0
        )
        writeBlockChecksum(
            bytes,
            if (oras) 56 else 53,
            pcOffset,
            0x34AD0
        )
        return bytes
    }

    private fun writeBlockChecksum(
        save: ByteArray,
        blockId: Int,
        offset: Int,
        length: Int
    ) {
        val checksumOffset =
            save.size - 0x200 + 0x14 + blockId * 8 + 6
        val value = crc16Ccitt(save, offset, length)
        save[checksumOffset] = (value and 0xFF).toByte()
        save[checksumOffset + 1] =
            ((value ushr 8) and 0xFF).toByte()
    }

    private fun crc16Ccitt(
        data: ByteArray,
        offset: Int,
        length: Int
    ): Int {
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
