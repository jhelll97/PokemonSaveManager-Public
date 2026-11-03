package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6VersionExclusiveMigrationTest {
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
    fun selectedExclusiveIsAppendedAndIncludedInCapacityAndReadback() {
        val migrated = record(25, 1)
        val selected = record(270, 2)
        val template = byteArrayOf(4, 5, 6)
        var placed = emptyList<ByteArray>()
        var capacityCheckedFor = -1
        val catalog = Gen6VersionExclusiveCatalog(
            listOf(
                Gen6VersionExclusiveEntry(
                    "lotad-as", Gen6ConverterGame.ALPHA_SAPPHIRE,
                    270, selected, "fixture:lotad-as"
                )
            ),
            GEN6_TEST_EXCLUSIVE_VERIFIER
        )
        val migration = Gen6Pk6RecordSaveMigration(
            extractor = Gen6Pk6SourceRecordExtractor { _, _ -> listOf(migrated) },
            inserter = Gen6Pk6DestinationRecordInserter { destination, _, records ->
                placed = records.map { it.copyOf() }
                destination.copyOf()
            },
            capacity = Gen6Pk6DestinationCapacity { _, _ ->
                capacityCheckedFor = 2
                2
            },
            destinationReader = Gen6Pk6DestinationRecordReader { _, _, count ->
                assertEquals(2, count)
                placed.map { it.copyOf() }
            },
            versionExclusiveCatalog = catalog
        )

        migration.migrate(
            byteArrayOf(1, 2, 3),
            template,
            Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                includeVersionExclusives = true,
                selectedVersionExclusiveIds = listOf("lotad-as")
            )
        )

        assertEquals(2, capacityCheckedFor)
        assertEquals(2, placed.size)
        assertArrayEquals(migrated, placed[0])
        assertArrayEquals(selected, placed[1])
        assertArrayEquals(byteArrayOf(4, 5, 6), template)
    }

    @Test
    fun migrationSnapshotsExclusiveSelectionBeforeExtractorCallbacks() {
        val selected = record(270, 2)
        val selectedIds = mutableListOf("lotad-as")
        var placed = emptyList<ByteArray>()
        val catalog = Gen6VersionExclusiveCatalog(
            listOf(
                Gen6VersionExclusiveEntry(
                    "lotad-as",
                    Gen6ConverterGame.ALPHA_SAPPHIRE,
                    270,
                    selected,
                    "fixture:lotad-as"
                )
            ),
            GEN6_TEST_EXCLUSIVE_VERIFIER
        )
        val migration = Gen6Pk6RecordSaveMigration(
            extractor = Gen6Pk6SourceRecordExtractor { _, _ ->
                selectedIds.clear()
                emptyList()
            },
            inserter = Gen6Pk6DestinationRecordInserter {
                    destination, _, records ->
                placed = records.map { it.copyOf() }
                destination.copyOf()
            },
            capacity = Gen6Pk6DestinationCapacity { _, _ -> 1 },
            destinationReader = Gen6Pk6DestinationRecordReader {
                    _, _, _ ->
                placed.map { it.copyOf() }
            },
            versionExclusiveCatalog = catalog
        )

        migration.migrate(
            byteArrayOf(1, 2, 3),
            byteArrayOf(4, 5, 6),
            Gen6ConverterRequest(
                Gen6ConverterGame.OMEGA_RUBY,
                Gen6ConverterGame.ALPHA_SAPPHIRE,
                includeVersionExclusives = true,
                selectedVersionExclusiveIds = selectedIds
            )
        )

        assertTrue(selectedIds.isEmpty())
        assertEquals(1, placed.size)
        assertArrayEquals(selected, placed.single())
    }

    @Test
    fun selectedExtrasRequireCatalogBeforePlacement() {
        var placementCalled = false
        val migration = Gen6Pk6RecordSaveMigration(
            extractor = Gen6Pk6SourceRecordExtractor { _, _ -> emptyList() },
            inserter = Gen6Pk6DestinationRecordInserter { destination, _, _ ->
                placementCalled = true
                destination
            },
            capacity = Gen6Pk6DestinationCapacity { _, _ -> 1 },
            destinationReader = Gen6Pk6DestinationRecordReader { _, _, _ -> emptyList() }
        )

        assertThrows(IllegalArgumentException::class.java) {
            migration.migrate(
                byteArrayOf(1),
                byteArrayOf(2),
                Gen6ConverterRequest(
                    Gen6ConverterGame.OMEGA_RUBY,
                    Gen6ConverterGame.ALPHA_SAPPHIRE,
                    includeVersionExclusives = true,
                    selectedVersionExclusiveIds = listOf("lotad-as")
                )
            )
        }
        assertEquals(false, placementCalled)
    }
}
