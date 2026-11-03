package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotSame
import org.junit.Test

class Gen3BoxEditingTest {
    private fun markedPcSearchEntries(): List<Gen3PcSearchEntry> = filteredPcSearchEntries().map { entry ->
        entry.copy(markingValue = 0xA0 or (when (entry.globalIndex) {
            0 -> 1 // Circle
            29 -> 2 // Square in the raw GBA layout
            30 -> 4 // Triangle in the raw GBA layout
            137 -> 5 // Circle and Triangle
            else -> 15
        }))
    }

    @Test
    fun pcSearchMatchesRawGbaMarkingsAndRequiresEveryChosenMark() {
        val entries = markedPcSearchEntries()
        val before = entries.toList()
        assertEquals(listOf(0, 137, 419), Gen3BoxEditing.searchPcSlots(entries, "", requiredMarkings = 1))
        assertEquals(listOf(29, 419), Gen3BoxEditing.searchPcSlots(entries, "", requiredMarkings = 2))
        assertEquals(listOf(30, 137, 419), Gen3BoxEditing.searchPcSlots(entries, "", requiredMarkings = 4))
        assertEquals(listOf(419), Gen3BoxEditing.searchPcSlots(entries, "", requiredMarkings = 8))
        assertEquals(listOf(137, 419), Gen3BoxEditing.searchPcSlots(entries, "", requiredMarkings = 5))
        assertEquals(listOf(419), Gen3BoxEditing.searchPcSlots(entries, "", requiredMarkings = 15))
        assertEquals(listOf(0, 29, 30, 137, 419), Gen3BoxEditing.searchPcSlots(entries, ""))
        assertEquals(before, entries)
    }

    @Test
    fun pcSearchCombinesMarkingsWithTextEggShinyAndOriginalBoxScope() {
        val entries = markedPcSearchEntries()
        assertEquals(listOf(419), Gen3BoxEditing.searchPcSlots(entries, "Pikachu", requiredMarkings = 5))
        assertEquals(listOf(137), Gen3BoxEditing.searchPcSlots(entries, "Eevee", requiredMarkings = 5))
        assertEquals(listOf(30), Gen3BoxEditing.searchPcSlots(entries, "#25",
            shinyOnly = true, boxIndex = 1, requiredMarkings = 4))
        assertEquals(listOf(419), Gen3BoxEditing.searchPcSlots(entries, "Leaf",
            eggsOnly = true, shinyOnly = true, boxIndex = 13, requiredMarkings = 15))
        val reservedOnly = listOf(entries.first().copy(markingValue = 0xF0))
        assertTrue(Gen3BoxEditing.searchPcSlots(reservedOnly, "", requiredMarkings = 1).isEmpty())
        assertEquals(listOf(reservedOnly.first().globalIndex), Gen3BoxEditing.searchPcSlots(reservedOnly, ""))
    }

    @Test
    fun pcSearchRejectsUnsupportedMarkingFiltersAndMalformedMetadata() {
        for (mask in listOf(-1, 16, 255)) {
            var rejected = false
            try { Gen3BoxEditing.searchPcSlots(emptyList(), "", requiredMarkings = mask) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
        for (markings in listOf(-1, 256)) {
            var rejected = false
            try { Gen3BoxEditing.searchPcSlots(listOf(markedPcSearchEntries().first().copy(markingValue = markings)), "") }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
        assertTrue(Gen3BoxEditing.searchPcSlots(emptyList(), "", requiredMarkings = 15).isEmpty())
    }

    private fun markedPcRecord(personality: Int, egg: Boolean, markings: Int): ByteArray {
        val header = ByteArray(32) { (it + personality).toByte() }
        header[0x13] = (2 or (if (egg) 4 else 0)).toByte()
        header[0x1B] = markings.toByte()
        val canonical = ByteArray(48) { (it * 13 + personality).toByte() }
        canonical[0] = 25
        canonical[1] = 0
        canonical[43] = ((canonical[43].toInt() and 0xBF) or (if (egg) 0x40 else 0)).toByte()
        return Gen3Pk3Codec.encode(header, personality, 12345, 54321, canonical)
    }

    @Test
    fun selectedPcMarkingsPreserveEveryOtherByteAcrossAllMasksAndPk3Orders() {
        for (personality in 0 until 24) {
            val pc = ByteArray(0x83D0)
            val start = Gen3BoxEditing.boxStorageRange(13).first
            markedPcRecord(personality, false, 0xA5).copyInto(pc, start)
            markedPcRecord(personality, true, 0xF2).copyInto(pc, start + 29 * 80)
            markedPcRecord(personality, false, 0x71).copyInto(pc, start + 80)
            pc[0] = 13
            pc[0x8344] = 42
            val original = pc.copyOf()
            val offsets = setOf(start + 0x1B, start + 29 * 80 + 0x1B)
            for (mask in 0..15) {
                val edited = Gen3BoxEditing.setSelectedBoxMarkings(pc, 13, listOf(29, 0), mask)
                assertEquals(0xA0 or mask, edited[start + 0x1B].toInt() and 0xFF)
                assertEquals(0xF0 or mask, edited[start + 29 * 80 + 0x1B].toInt() and 0xFF)
                assertTrue(edited.indices.all { it in offsets || edited[it] == original[it] })
                for (slot in listOf(0, 29)) {
                    val offset = start + slot * 80
                    val before = Gen3Pk3Codec.decode(original.copyOfRange(offset, offset + 80))
                    val after = Gen3Pk3Codec.decode(edited.copyOfRange(offset, offset + 80))
                    assertEquals(before.storedChecksum, after.storedChecksum)
                    assertArrayEquals(before.canonicalSection, after.canonicalSection)
                    assertArrayEquals(before.encryptedSection, after.encryptedSection)
                }
                assertArrayEquals(original, pc)
            }
        }
    }

    @Test
    fun selectedPcMarkingsRejectInvalidSelectionsAndMasksBeforeMutation() {
        val pc = ByteArray(0x83D0)
        markedPcRecord(7, false, 0xA5).copyInto(pc, 4)
        val before = pc.copyOf()
        val actions = listOf<() -> Unit>(
            { Gen3BoxEditing.setSelectedBoxMarkings(pc, 0, listOf(0), -1) },
            { Gen3BoxEditing.setSelectedBoxMarkings(pc, 0, listOf(0), 16) },
            { Gen3BoxEditing.setSelectedBoxMarkings(pc, -1, listOf(0), 1) },
            { Gen3BoxEditing.setSelectedBoxMarkings(pc, 14, listOf(0), 1) },
            { Gen3BoxEditing.setSelectedBoxMarkings(pc, 0, emptyList(), 1) },
            { Gen3BoxEditing.setSelectedBoxMarkings(pc, 0, listOf(0, 0), 1) },
            { Gen3BoxEditing.setSelectedBoxMarkings(pc, 0, listOf(-1), 1) },
            { Gen3BoxEditing.setSelectedBoxMarkings(pc, 0, listOf(30), 1) },
            { Gen3BoxEditing.setSelectedBoxMarkings(pc, 0, listOf(1), 1) },
            { Gen3BoxEditing.setSelectedBoxMarkings(pc.copyOf(100), 0, listOf(0), 1) }
        )
        actions.forEach { action ->
            var rejected = false
            try { action() } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertArrayEquals(before, pc)
        }
    }

    @Test
    fun selectedPcMarkingsRejectCorruptOrBadEggBatchAndSupportExactNoOp() {
        val pc = ByteArray(0x83D0)
        markedPcRecord(0, false, 0xA5).copyInto(pc, 4)
        markedPcRecord(23, true, 0xF5).copyInto(pc, 4 + 29 * 80)
        assertArrayEquals(pc, Gen3BoxEditing.setSelectedBoxMarkings(pc, 0, listOf(0, 29), 5))
        for (badEgg in listOf(false, true)) {
            val invalid = pc.copyOf()
            val offset = 4 + 29 * 80 + (if (badEgg) 0x13 else 0x20)
            invalid[offset] = (invalid[offset].toInt() xor 1).toByte()
            val before = invalid.copyOf()
            var rejected = false
            try { Gen3BoxEditing.setSelectedBoxMarkings(invalid, 0, listOf(0, 29), 0) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertArrayEquals(before, invalid)
        }
    }

    @Test
    fun pcOverviewIncludesEmptyBoxesAndKeepsBoundaryOccupantsInTheirOriginalBoxes() {
        val entries = filteredPcSearchEntries()
        val before = entries.toList()
        val summaries = Gen3BoxEditing.summarizePcBoxes(entries)
        assertEquals((0 until 14).toList(), summaries.map { it.boxIndex })
        assertEquals(Gen3PcBoxSummary(0, 2, 1, 0), summaries[0])
        assertEquals(Gen3PcBoxSummary(1, 1, 0, 1), summaries[1])
        assertEquals(Gen3PcBoxSummary(4, 1, 0, 0), summaries[4])
        assertEquals(Gen3PcBoxSummary(13, 1, 1, 1), summaries[13])
        assertEquals(Gen3PcBoxSummary(2, 0, 0, 0), summaries[2])
        assertEquals(30, summaries[2].free)
        assertEquals(28, summaries[0].free)
        assertEquals(5, summaries.sumOf { it.occupied })
        assertEquals(415, summaries.sumOf { it.free })
        assertEquals(before, entries)
    }

    @Test
    fun pcOverviewCountsFullAndEmptyStorageWithoutLosingEggsOrShinyOccupants() {
        val entries = (0 until 420).map { index ->
            Gen3PcSearchEntry(index, 25, "Pikachu", "", "Trainer",
                isEgg = index % 30 == 0, shiny = index % 30 == 29)
        }.reversed()
        val full = Gen3BoxEditing.summarizePcBoxes(entries)
        assertTrue(full.all { it.occupied == 30 && it.free == 0 && it.eggs == 1 && it.shiny == 1 })
        assertEquals(420, full.sumOf { it.occupied })
        val empty = Gen3BoxEditing.summarizePcBoxes(emptyList())
        assertEquals(14, empty.size)
        assertTrue(empty.all { it.occupied == 0 && it.free == 30 && it.eggs == 0 && it.shiny == 0 })
    }

    @Test
    fun pcOverviewRejectsDuplicateLocationsAndInvalidIndexMetadata() {
        val first = filteredPcSearchEntries().first()
        val invalid = listOf(listOf(first, first), listOf(first.copy(globalIndex = -1)),
            listOf(first.copy(globalIndex = 420)), listOf(first.copy(species = 0)),
            listOf(first.copy(species = 387)))
        invalid.forEach { entries ->
            var rejected = false
            try { Gen3BoxEditing.summarizePcBoxes(entries) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
    }

    private fun filteredPcSearchEntries(): List<Gen3PcSearchEntry> = listOf(
        Gen3PcSearchEntry(419, 25, "Pikachu", "Spark", "Leaf", true, true),
        Gen3PcSearchEntry(29, 133, "Eevee", "Egg", "Brock", true, false),
        Gen3PcSearchEntry(0, 25, "Pikachu", "Bolt", "Ash", false, false),
        Gen3PcSearchEntry(30, 25, "Pikachu", "Bright", "Ash", false, true),
        Gen3PcSearchEntry(137, 133, "Eevee", "Fluff", "Leaf", false, false)
    )

    @Test
    fun pcSearchEggAndShinyFiltersIntersectWithoutChangingDefaultResults() {
        val entries = filteredPcSearchEntries()
        val before = entries.toList()
        assertEquals(listOf(29, 419), Gen3BoxEditing.searchPcSlots(entries, "", eggsOnly = true))
        assertEquals(listOf(30, 419), Gen3BoxEditing.searchPcSlots(entries, "", shinyOnly = true))
        assertEquals(listOf(419),
            Gen3BoxEditing.searchPcSlots(entries, "", eggsOnly = true, shinyOnly = true))
        assertEquals(listOf(0, 29, 30, 137, 419), Gen3BoxEditing.searchPcSlots(entries, ""))
        assertEquals(before, entries)
    }

    @Test
    fun pcSearchBoxScopeUsesExactBoundariesAndCombinesWithTextAndFlags() {
        val entries = filteredPcSearchEntries()
        assertEquals(listOf(0, 29), Gen3BoxEditing.searchPcSlots(entries, "", boxIndex = 0))
        assertEquals(listOf(30), Gen3BoxEditing.searchPcSlots(entries, "", boxIndex = 1))
        assertEquals(listOf(419), Gen3BoxEditing.searchPcSlots(entries, "#25", true, true, 13))
        assertEquals(listOf(30), Gen3BoxEditing.searchPcSlots(entries, "Ash", shinyOnly = true, boxIndex = 1))
        assertTrue(Gen3BoxEditing.searchPcSlots(entries, "Eevee", shinyOnly = true).isEmpty())
        assertTrue(Gen3BoxEditing.searchPcSlots(entries, "Pikachu", eggsOnly = true, boxIndex = 0).isEmpty())
    }

    @Test
    fun pcSearchRejectsInvalidScopeEvenWhenThereAreNoEntries() {
        for (box in listOf(-1, 14)) {
            var rejected = false
            try { Gen3BoxEditing.searchPcSlots(emptyList(), "", boxIndex = box) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
        assertTrue(Gen3BoxEditing.searchPcSlots(emptyList(), "", true, true, 13).isEmpty())
    }

    private fun pcSearchEntries(): List<Gen3PcSearchEntry> = listOf(
        Gen3PcSearchEntry(419, 25, "Pikachu", "Sparky", "Misty"),
        Gen3PcSearchEntry(0, 25, "Pikachu", "Bolt", "Ash"),
        Gen3PcSearchEntry(30, 29, "Nidoran♀", "Twenty-five", "ASH"),
        Gen3PcSearchEntry(137, 133, "Eevee", "Sparky", "Leaf")
    )

    @Test
    fun pcSearchFindsNamesOriginalTrainerAndAllTermsAcrossBoxes() {
        val entries = pcSearchEntries()
        assertEquals(listOf(0, 419), Gen3BoxEditing.searchPcSlots(entries, "  PIKACHU  "))
        assertEquals(listOf(137, 419), Gen3BoxEditing.searchPcSlots(entries, "sparky"))
        assertEquals(listOf(0, 30), Gen3BoxEditing.searchPcSlots(entries, "ash"))
        assertEquals(listOf(0), Gen3BoxEditing.searchPcSlots(entries, "pika Ash"))
        assertEquals(listOf(30), Gen3BoxEditing.searchPcSlots(entries, "NIDORAN♀"))
        assertTrue(Gen3BoxEditing.searchPcSlots(entries, "missing").isEmpty())
    }

    @Test
    fun pcSearchMatchesDexExactlyAndKeepsOriginalLocations() {
        val entries = pcSearchEntries()
        val before = entries.toList()
        assertEquals(listOf(0, 419), Gen3BoxEditing.searchPcSlots(entries, "#25"))
        assertEquals(listOf(0, 419), Gen3BoxEditing.searchPcSlots(entries, "25"))
        assertTrue(Gen3BoxEditing.searchPcSlots(entries, "2").isEmpty())
        assertEquals(listOf(0, 30, 137, 419), Gen3BoxEditing.searchPcSlots(entries, "  "))
        assertEquals(before, entries)
        assertTrue(Gen3BoxEditing.searchPcSlots(emptyList(), "Pikachu").isEmpty())
    }

    @Test
    fun pcSearchSupportsAll420SlotsAndStableBoundaryResults() {
        val entries = (0 until 420).map { Gen3PcSearchEntry(it, 1, "Bulbasaur", "", "Trainer") }.reversed()
        assertEquals((0 until 420).toList(), Gen3BoxEditing.searchPcSlots(entries, "1"))
    }

    @Test
    fun pcSearchRejectsDuplicateOutOfRangeAndMalformedEntriesOrLongQueries() {
        val first = pcSearchEntries()[1]
        val invalid = listOf(listOf(first, first), listOf(first.copy(globalIndex = -1)),
            listOf(first.copy(globalIndex = 420)), listOf(first.copy(species = 0)),
            listOf(first.copy(species = 387)))
        invalid.forEach { entries ->
            var rejected = false
            try { Gen3BoxEditing.searchPcSlots(entries, "Pikachu") }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
        var rejected = false
        try { Gen3BoxEditing.searchPcSlots(listOf(first), "x".repeat(101)) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

    @Test
    fun packingTheOnlyPokemonMovesItsExactRecordIntoTheFirstSlot() {
        val pc = ByteArray(0x83D0)
        val range = Gen3BoxEditing.boxStorageRange(13)
        val raw = ByteArray(80) { (it + 1).toByte() }
        raw.copyInto(pc, range.first + 29 * 80)
        val before = pc.copyOf()
        val order = Gen3BoxEditing.sortedOccupiedSlots(
            listOf(Gen3BoxSortEntry(29, 25, 10, "Only", true)), Gen3BoxSortField.PACK)
        assertEquals(listOf(29), order)
        val result = Gen3BoxEditing.reorderBoxStorage(pc, 13, order)
        assertArrayEquals(raw, result.copyOfRange(range.first, range.first + 80))
        assertTrue(result.copyOfRange(range.first + 80, range.last + 1).all { it == 0.toByte() })
        assertTrue(before.indices.all { it in range || before[it] == result[it] })
        assertArrayEquals(before, pc)
    }

    @Test
    fun packGapsKeepsCurrentRelativeOrderIncludingInterleavedEggs() {
        val entries = boxSortEntries()
        val expected = listOf(2, 7, 9, 14, 29)
        assertEquals(expected, Gen3BoxEditing.sortedOccupiedSlots(entries, Gen3BoxSortField.PACK))
        assertEquals(expected, Gen3BoxEditing.sortedOccupiedSlots(entries.reversed(), Gen3BoxSortField.PACK, true))
        val pc = ByteArray(0x83D0) { 0x5A.toByte() }
        val range = Gen3BoxEditing.boxStorageRange(0)
        pc.fill(0, range.first, range.last + 1)
        entries.forEach { entry ->
            ByteArray(80) { (entry.sourceSlot + it + 1).toByte() }
                .copyInto(pc, range.first + entry.sourceSlot * 80)
        }
        val before = pc.copyOf()
        val result = Gen3BoxEditing.reorderBoxStorage(pc, 0, expected)
        expected.forEachIndexed { target, source ->
            assertArrayEquals(before.copyOfRange(range.first + source * 80, range.first + (source + 1) * 80),
                result.copyOfRange(range.first + target * 80, range.first + (target + 1) * 80))
        }
        assertTrue(result.copyOfRange(range.first + entries.size * 80, range.last + 1)
            .all { it == 0.toByte() })
        assertTrue(before.indices.all { it in range || before[it] == result[it] })
        assertArrayEquals(before, pc)
    }

    @Test
    fun packingAnAlreadyCompactBoxIsByteExactAndIdempotent() {
        val pc = ByteArray(0x83D0)
        val range = Gen3BoxEditing.boxStorageRange(13)
        for (slot in 0 until 30) {
            ByteArray(80) { (slot + it + 1).toByte() }.copyInto(pc, range.first + slot * 80)
        }
        val entries = (0 until 30).map { slot ->
            Gen3BoxSortEntry(slot, 30 - slot, 10, "Name $slot", slot % 2 == 1)
        }.reversed()
        val order = Gen3BoxEditing.sortedOccupiedSlots(entries, Gen3BoxSortField.PACK)
        val once = Gen3BoxEditing.reorderBoxStorage(pc, 13, order)
        val twice = Gen3BoxEditing.reorderBoxStorage(once, 13, order)
        assertArrayEquals(pc, once)
        assertArrayEquals(once, twice)
    }

    private fun boxSortEntries(): List<Gen3BoxSortEntry> = listOf(
        Gen3BoxSortEntry(29, 25, 20, "alpha", false),
        Gen3BoxSortEntry(9, 150, 0, "Egg B", true),
        Gen3BoxSortEntry(2, 25, 50, "ALPHA", false),
        Gen3BoxSortEntry(14, 1, 5, "Egg A", true),
        Gen3BoxSortEntry(7, 1, 20, "Beta", false)
    )

    @Test
    fun sparseBoxSortSupportsAllFieldsAndDirectionsWithStableEggOrder() {
        val entries = boxSortEntries()
        assertEquals(listOf(7, 2, 29, 9, 14),
            Gen3BoxEditing.sortedOccupiedSlots(entries, Gen3BoxSortField.SPECIES))
        assertEquals(listOf(2, 29, 7, 9, 14),
            Gen3BoxEditing.sortedOccupiedSlots(entries, Gen3BoxSortField.SPECIES, true))
        assertEquals(listOf(7, 29, 2, 9, 14),
            Gen3BoxEditing.sortedOccupiedSlots(entries, Gen3BoxSortField.LEVEL))
        assertEquals(listOf(2, 7, 29, 9, 14),
            Gen3BoxEditing.sortedOccupiedSlots(entries, Gen3BoxSortField.LEVEL, true))
        assertEquals(listOf(2, 29, 7, 9, 14),
            Gen3BoxEditing.sortedOccupiedSlots(entries, Gen3BoxSortField.NAME))
        assertEquals(listOf(7, 2, 29, 9, 14),
            Gen3BoxEditing.sortedOccupiedSlots(entries, Gen3BoxSortField.NAME, true))
    }

    @Test
    fun boxSortPlansPackExactRecordsAndPreserveOtherBoxesAndMetadata() {
        val entries = boxSortEntries()
        val pc = ByteArray(0x83D0) { 0x5A.toByte() }
        val range = Gen3BoxEditing.boxStorageRange(13)
        pc.fill(0, range.first, range.last + 1)
        entries.forEach { entry ->
            ByteArray(80) { (entry.sourceSlot + it + 1).toByte() }
                .copyInto(pc, range.first + entry.sourceSlot * 80)
        }
        val before = pc.copyOf()
        for (field in Gen3BoxSortField.values()) for (descending in listOf(false, true)) {
            val order = Gen3BoxEditing.sortedOccupiedSlots(entries, field, descending)
            val result = Gen3BoxEditing.reorderBoxStorage(pc, 13, order)
            order.forEachIndexed { target, source ->
                assertArrayEquals(before.copyOfRange(range.first + source * 80, range.first + (source + 1) * 80),
                    result.copyOfRange(range.first + target * 80, range.first + (target + 1) * 80))
            }
            assertTrue(result.copyOfRange(range.first + entries.size * 80, range.last + 1)
                .all { it == 0.toByte() })
            assertTrue(before.indices.all { it in range || before[it] == result[it] })
        }
        assertArrayEquals(before, pc)
    }

    @Test
    fun boxSortSupportsEmptyAndFullBoxes() {
        assertTrue(Gen3BoxEditing.sortedOccupiedSlots(emptyList(), Gen3BoxSortField.SPECIES).isEmpty())
        val entries = (0 until 30).map { Gen3BoxSortEntry(it, 30 - it, 10, "Same", false) }
        assertEquals((0 until 30).reversed().toList(),
            Gen3BoxEditing.sortedOccupiedSlots(entries, Gen3BoxSortField.SPECIES))
        assertEquals((0 until 30).toList(),
            Gen3BoxEditing.sortedOccupiedSlots(entries.reversed(), Gen3BoxSortField.NAME, true))
    }

    @Test
    fun boxSortRejectsInvalidSlotsAndKeys() {
        val first = Gen3BoxSortEntry(0, 1, 10, "First", false)
        val invalid = listOf(
            listOf(first, first), listOf(first.copy(sourceSlot = -1)),
            listOf(first.copy(sourceSlot = 30)),
            (0 until 31).map { first.copy(sourceSlot = it) },
            listOf(first.copy(species = 0)), listOf(first.copy(species = 387)),
            listOf(first.copy(level = 0)), listOf(first.copy(level = 101))
        )
        invalid.forEach { entries ->
            var rejected = false
            try { Gen3BoxEditing.sortedOccupiedSlots(entries, Gen3BoxSortField.LEVEL) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
    }

    @Test
    fun renameBoxNamesUpdatesOnlyRequestedBoxAndTrimsInput() {
        val loaded = List(14) { index -> "Box ${index + 1}" }

        val renamed = Gen3BoxEditing.renameBoxNames(loaded, 4, "  Eggs  ")

        assertEquals("Eggs", renamed[4])
        assertEquals("Box 4", renamed[3])
        assertEquals("Box 6", renamed[5])
        assertEquals("Box 5", loaded[4])
    }

    @Test(expected = IllegalArgumentException::class)
    fun renameBoxNamesRejectsUnsupportedCharacters() {
        Gen3BoxEditing.renameBoxNames(List(14) { "Box" }, 0, "Box🙂")
    }

    @Test(expected = IllegalArgumentException::class)
    fun renameBoxNamesRejectsOverlongNames() {
        Gen3BoxEditing.renameBoxNames(List(14) { "Box" }, 0, "NineChars")
    }

    @Test(expected = IllegalArgumentException::class)
    fun renameBoxNamesRejectsOutOfRangeBox() {
        Gen3BoxEditing.renameBoxNames(List(14) { "Box" }, 14, "Box")
    }

    @Test
    fun clearBoxStorageZerosOnlyRequestedBoxAndPreservesSource() {
        val loaded = ByteArray(0x83D0) { index -> ((index % 251) + 1).toByte() }
        val original = loaded.copyOf()
        val cleared = Gen3BoxEditing.clearBoxStorage(loaded, 3)
        val range = Gen3BoxEditing.boxStorageRange(3)

        assertArrayEquals(original, loaded)
        assertTrue(range.all { cleared[it] == 0.toByte() })
        assertTrue(cleared.indices.all { it in range || cleared[it] == original[it] })
    }

    @Test
    fun clearSelectedBoxSlotsZerosOnlyRequestedRecords() {
        val loaded = ByteArray(0x83D0)
        val boxIndex = 7
        val range = Gen3BoxEditing.boxStorageRange(boxIndex)
        val selected = listOf(2, 11, 27)
        selected.forEachIndexed { recordIndex, slot ->
            val offset = range.first + slot * 80
            for (byteIndex in 0 until 80) {
                loaded[offset + byteIndex] =
                    (recordIndex * 80 + byteIndex + 1).toByte()
            }
        }
        val unselectedOffset = range.first + 20 * 80
        loaded[unselectedOffset] = 0x5A
        val original = loaded.copyOf()

        val cleared = Gen3BoxEditing.clearSelectedBoxSlots(
            loaded,
            boxIndex,
            selected
        )

        assertArrayEquals(original, loaded)
        selected.forEach { slot ->
            val offset = range.first + slot * 80
            assertTrue(cleared.copyOfRange(offset, offset + 80).all {
                it == 0.toByte()
            })
        }
        assertEquals(0x5A.toByte(), cleared[unselectedOffset])
        assertTrue(cleared.indices.all { index ->
            selected.any { slot ->
                index in range.first + slot * 80 until
                    range.first + (slot + 1) * 80
            } || cleared[index] == original[index]
        })
    }

    @Test
    fun snapshotSelectedBoxRecordsMapsSortedSlotsToDetachedRecords() {
        val loaded = ByteArray(0x83D0)
        val boxIndex = 9
        val range = Gen3BoxEditing.boxStorageRange(boxIndex)
        val firstOffset = range.first + 4 * 80
        val secondOffset = range.first + 17 * 80
        for (index in 0 until 80) {
            loaded[firstOffset + index] = (index + 3).toByte()
            loaded[secondOffset + index] = (220 - index).toByte()
        }
        val original = loaded.copyOf()

        val snapshot = Gen3BoxEditing.snapshotSelectedBoxRecords(
            loaded,
            boxIndex,
            listOf(17, 4)
        )

        assertEquals(boxIndex, snapshot.boxIndex)
        assertEquals(listOf(4, 17), snapshot.slots)
        assertArrayEquals(
            original.copyOfRange(firstOffset, firstOffset + 80),
            snapshot.records[0]
        )
        assertArrayEquals(
            original.copyOfRange(secondOffset, secondOffset + 80),
            snapshot.records[1]
        )
        snapshot.records[0][0] = 0
        assertArrayEquals(original, loaded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun snapshotSelectedBoxRecordsRejectsDuplicateSlots() {
        val loaded = ByteArray(0x83D0)
        loaded[Gen3BoxEditing.boxStorageRange(0).first] = 1
        Gen3BoxEditing.snapshotSelectedBoxRecords(
            loaded,
            0,
            listOf(0, 0)
        )
    }

    @Test
    fun copySelectedBoxRecordsReturnsSortedIndependentCopies() {
        val loaded = ByteArray(0x83D0)
        val boxIndex = 3
        val range = Gen3BoxEditing.boxStorageRange(boxIndex)
        val lowOffset = range.first + 2 * 80
        val highOffset = range.first + 11 * 80
        for (index in 0 until 80) {
            loaded[lowOffset + index] = (index + 1).toByte()
            loaded[highOffset + index] = (160 - index).toByte()
        }
        val original = loaded.copyOf()

        val records = Gen3BoxEditing.copySelectedBoxRecords(
            loaded,
            boxIndex,
            listOf(11, 2)
        )

        assertEquals(2, records.size)
        assertArrayEquals(
            original.copyOfRange(lowOffset, lowOffset + 80),
            records[0]
        )
        assertArrayEquals(
            original.copyOfRange(highOffset, highOffset + 80),
            records[1]
        )
        records[0][0] = 0
        assertArrayEquals(original, loaded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun clearSelectedBoxSlotsRejectsDuplicateSelection() {
        val loaded = ByteArray(0x83D0)
        loaded[4] = 1
        Gen3BoxEditing.clearSelectedBoxSlots(loaded, 0, listOf(0, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun clearSelectedBoxSlotsRejectsEmptySlot() {
        Gen3BoxEditing.clearSelectedBoxSlots(ByteArray(0x83D0), 0, listOf(4))
    }

    @Test(expected = IllegalArgumentException::class)
    fun clearBoxStorageRejectsShortPcData() {
        Gen3BoxEditing.clearBoxStorage(ByteArray(0x8343), 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun clearBoxStorageRejectsOutOfRangeBox() {
        Gen3BoxEditing.clearBoxStorage(ByteArray(0x83D0), 14)
    }


    @Test
    fun swapBoxStorageExchangesOnlySelectedBoxRegions() {
        val loaded = ByteArray(0x83D0) { index -> ((index % 251) + 1).toByte() }
        val original = loaded.copyOf()
        val firstRange = Gen3BoxEditing.boxStorageRange(1)
        val secondRange = Gen3BoxEditing.boxStorageRange(12)

        val swapped = Gen3BoxEditing.swapBoxStorage(loaded, 1, 12)

        assertArrayEquals(original, loaded)
        assertArrayEquals(
            original.copyOfRange(secondRange.first, secondRange.last + 1),
            swapped.copyOfRange(firstRange.first, firstRange.last + 1)
        )
        assertArrayEquals(
            original.copyOfRange(firstRange.first, firstRange.last + 1),
            swapped.copyOfRange(secondRange.first, secondRange.last + 1)
        )
        assertTrue(swapped.indices.all {
            it in firstRange || it in secondRange || swapped[it] == original[it]
        })
    }

    @Test
    fun swapBoxStorageSameBoxReturnsIndependentCopy() {
        val loaded = ByteArray(0x83D0) { 0x5A.toByte() }

        val unchanged = Gen3BoxEditing.swapBoxStorage(loaded, 6, 6)

        assertArrayEquals(loaded, unchanged)
        assertNotSame(loaded, unchanged)
    }


    @Test
    fun movePokemonStorageMovesExactRecordAndPreservesSourceBuffer() {
        val loaded = ByteArray(0x83D0)
        val sourceGlobal = 8
        val targetGlobal = 301
        val sourceOffset = 4 + sourceGlobal * 80
        val targetOffset = 4 + targetGlobal * 80
        for (index in 0 until 80) loaded[sourceOffset + index] = (index + 1).toByte()
        val original = loaded.copyOf()

        val moved = Gen3BoxEditing.movePokemonStorage(loaded, sourceGlobal, targetGlobal)

        assertArrayEquals(original, loaded)
        assertTrue(moved.copyOfRange(sourceOffset, sourceOffset + 80).all {
            it == 0.toByte()
        })
        assertArrayEquals(
            original.copyOfRange(sourceOffset, sourceOffset + 80),
            moved.copyOfRange(targetOffset, targetOffset + 80)
        )
        assertTrue(moved.indices.all {
            it in sourceOffset until sourceOffset + 80 ||
                it in targetOffset until targetOffset + 80 ||
                moved[it] == original[it]
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun movePokemonStorageRejectsEmptySource() {
        Gen3BoxEditing.movePokemonStorage(ByteArray(0x83D0), 0, 30)
    }

    @Test(expected = IllegalArgumentException::class)
    fun movePokemonStorageRejectsOccupiedTarget() {
        val loaded = ByteArray(0x83D0)
        loaded[4] = 1
        loaded[4 + 30 * 80] = 2
        Gen3BoxEditing.movePokemonStorage(loaded, 0, 30)
    }


    @Test
    fun emptyBoxSlotsReturnsOnlyExactZeroRecords() {
        val loaded = ByteArray(0x83D0)
        val boxIndex = 5
        val boxStart = Gen3BoxEditing.boxStorageRange(boxIndex).first
        loaded[boxStart] = 1
        loaded[boxStart + 29 * 80 + 79] = 2

        val empty = Gen3BoxEditing.emptyBoxSlots(loaded, boxIndex)

        assertEquals((1..28).toList(), empty)
    }


    @Test
    fun movePokemonStorageSupportsSameBoxReordering() {
        val loaded = ByteArray(0x83D0)
        val sourceGlobal = 151
        val targetGlobal = 158
        val sourceOffset = 4 + sourceGlobal * 80
        val targetOffset = 4 + targetGlobal * 80
        for (index in 0 until 80) loaded[sourceOffset + index] = (80 - index).toByte()

        val moved = Gen3BoxEditing.movePokemonStorage(loaded, sourceGlobal, targetGlobal)

        assertTrue(moved.copyOfRange(sourceOffset, sourceOffset + 80).all {
            it == 0.toByte()
        })
        assertArrayEquals(
            loaded.copyOfRange(sourceOffset, sourceOffset + 80),
            moved.copyOfRange(targetOffset, targetOffset + 80)
        )
    }


    @Test
    fun swapPokemonStorageExchangesExactRecordsAndPreservesSource() {
        val loaded = ByteArray(0x83D0)
        val firstGlobal = 211
        val secondGlobal = 219
        val firstOffset = 4 + firstGlobal * 80
        val secondOffset = 4 + secondGlobal * 80
        for (index in 0 until 80) {
            loaded[firstOffset + index] = (index + 1).toByte()
            loaded[secondOffset + index] = (160 - index).toByte()
        }
        val original = loaded.copyOf()

        val swapped = Gen3BoxEditing.swapPokemonStorage(
            loaded,
            firstGlobal,
            secondGlobal
        )

        assertArrayEquals(original, loaded)
        assertArrayEquals(
            original.copyOfRange(secondOffset, secondOffset + 80),
            swapped.copyOfRange(firstOffset, firstOffset + 80)
        )
        assertArrayEquals(
            original.copyOfRange(firstOffset, firstOffset + 80),
            swapped.copyOfRange(secondOffset, secondOffset + 80)
        )
        assertTrue(swapped.indices.all {
            it in firstOffset until firstOffset + 80 ||
                it in secondOffset until secondOffset + 80 ||
                swapped[it] == original[it]
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun swapPokemonStorageRejectsEmptyTarget() {
        val loaded = ByteArray(0x83D0)
        loaded[4] = 1
        Gen3BoxEditing.swapPokemonStorage(loaded, 0, 1)
    }



    @Test
    fun reorderBoxStoragePacksExactRecordsInRequestedOrder() {
        val loaded = ByteArray(0x83D0)
        val boxIndex = 4
        val range = Gen3BoxEditing.boxStorageRange(boxIndex)
        val occupiedSlots = listOf(2, 9, 28)
        occupiedSlots.forEachIndexed { recordIndex, slot ->
            val offset = range.first + slot * 80
            for (byteIndex in 0 until 80) {
                loaded[offset + byteIndex] =
                    (recordIndex * 80 + byteIndex + 1).toByte()
            }
        }
        val original = loaded.copyOf()
        val requested = listOf(28, 2, 9)

        val reordered = Gen3BoxEditing.reorderBoxStorage(
            loaded,
            boxIndex,
            requested
        )

        assertArrayEquals(original, loaded)
        requested.forEachIndexed { destinationSlot, sourceSlot ->
            assertArrayEquals(
                original.copyOfRange(
                    range.first + sourceSlot * 80,
                    range.first + (sourceSlot + 1) * 80
                ),
                reordered.copyOfRange(
                    range.first + destinationSlot * 80,
                    range.first + (destinationSlot + 1) * 80
                )
            )
        }
        assertTrue(
            reordered.copyOfRange(
                range.first + requested.size * 80,
                range.last + 1
            ).all { it == 0.toByte() }
        )
        assertTrue(reordered.indices.all {
            it in range || reordered[it] == original[it]
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun reorderBoxStorageRejectsIncompleteOccupiedOrder() {
        val loaded = ByteArray(0x83D0)
        val range = Gen3BoxEditing.boxStorageRange(0)
        loaded[range.first] = 1
        loaded[range.first + 5 * 80] = 2
        Gen3BoxEditing.reorderBoxStorage(loaded, 0, listOf(5))
    }



    @Test
    fun moveSelectedBoxRecordsUsesFirstEmptyTargetsAndPreservesUnrelatedBytes() {
        val loaded = ByteArray(0x83D0)
        val sourceBox = 2
        val targetBox = 7
        val sourceRange = Gen3BoxEditing.boxStorageRange(sourceBox)
        val targetRange = Gen3BoxEditing.boxStorageRange(targetBox)
        val selected = listOf(12, 3)
        selected.forEachIndexed { recordIndex, slot ->
            val offset = sourceRange.first + slot * 80
            for (byteIndex in 0 until 80) {
                loaded[offset + byteIndex] =
                    (recordIndex * 80 + byteIndex + 1).toByte()
            }
        }
        loaded[targetRange.first] = 99
        loaded[targetRange.first + 2 * 80] = 88
        val original = loaded.copyOf()

        val result = Gen3BoxEditing.moveSelectedBoxRecords(
            loaded,
            sourceBox,
            selected,
            targetBox
        )

        assertArrayEquals(original, loaded)
        assertEquals(listOf(3, 12), result.sourceSlots)
        assertEquals(listOf(1, 3), result.targetSlots)
        result.sourceSlots.forEach { slot ->
            val offset = sourceRange.first + slot * 80
            assertTrue(result.pc.copyOfRange(offset, offset + 80).all {
                it == 0.toByte()
            })
        }
        result.sourceSlots.forEachIndexed { index, sourceSlot ->
            val sourceOffset = sourceRange.first + sourceSlot * 80
            val targetOffset = targetRange.first + result.targetSlots[index] * 80
            assertArrayEquals(
                original.copyOfRange(sourceOffset, sourceOffset + 80),
                result.pc.copyOfRange(targetOffset, targetOffset + 80)
            )
        }
        val changedRanges = result.sourceSlots.map { slot ->
            val start = sourceRange.first + slot * 80
            start until start + 80
        } + result.targetSlots.map { slot ->
            val start = targetRange.first + slot * 80
            start until start + 80
        }
        assertTrue(result.pc.indices.all { index ->
            changedRanges.any { index in it } || result.pc[index] == original[index]
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun moveSelectedBoxRecordsRejectsSameBox() {
        val loaded = ByteArray(0x83D0)
        loaded[Gen3BoxEditing.boxStorageRange(1).first] = 1
        Gen3BoxEditing.moveSelectedBoxRecords(loaded, 1, listOf(0), 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun moveSelectedBoxRecordsRejectsInsufficientCapacity() {
        val loaded = ByteArray(0x83D0)
        val sourceRange = Gen3BoxEditing.boxStorageRange(0)
        loaded[sourceRange.first] = 1
        loaded[sourceRange.first + 80] = 2
        val targetRange = Gen3BoxEditing.boxStorageRange(1)
        for (slot in 0 until 29) {
            loaded[targetRange.first + slot * 80] = (slot + 1).toByte()
        }
        Gen3BoxEditing.moveSelectedBoxRecords(
            loaded,
            0,
            listOf(0, 1),
            1
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun moveSelectedBoxRecordsRejectsDuplicateSelection() {
        val loaded = ByteArray(0x83D0)
        loaded[Gen3BoxEditing.boxStorageRange(0).first] = 1
        Gen3BoxEditing.moveSelectedBoxRecords(
            loaded,
            0,
            listOf(0, 0),
            1
        )
    }


    @Test
    fun insertBoxRecordsUsesFirstEmptySlotsAndPreservesInput() {
        val loaded = ByteArray(0x83D0)
        val boxIndex = 6
        val range = Gen3BoxEditing.boxStorageRange(boxIndex)
        loaded[range.first] = 99
        loaded[range.first + 2 * 80] = 88
        val first = ByteArray(80) { index -> (index + 1).toByte() }
        val second = ByteArray(80) { index -> (160 - index).toByte() }
        val original = loaded.copyOf()

        val result = Gen3BoxEditing.insertBoxRecords(
            loaded,
            boxIndex,
            listOf(first, second)
        )

        assertArrayEquals(original, loaded)
        assertEquals(listOf(1, 3), result.targetSlots)
        assertArrayEquals(
            first,
            result.pc.copyOfRange(range.first + 80, range.first + 160)
        )
        assertArrayEquals(
            second,
            result.pc.copyOfRange(range.first + 3 * 80, range.first + 4 * 80)
        )
        val changedRanges = result.targetSlots.map { slot ->
            val start = range.first + slot * 80
            start until start + 80
        }
        assertTrue(result.pc.indices.all { index ->
            changedRanges.any { index in it } || result.pc[index] == original[index]
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun insertBoxRecordsRejectsInsufficientCapacity() {
        val loaded = ByteArray(0x83D0)
        val range = Gen3BoxEditing.boxStorageRange(0)
        for (slot in 0 until 29) {
            loaded[range.first + slot * 80] = (slot + 1).toByte()
        }
        Gen3BoxEditing.insertBoxRecords(
            loaded,
            0,
            listOf(ByteArray(80) { 1 }, ByteArray(80) { 2 })
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun insertBoxRecordsRejectsWrongRecordSize() {
        Gen3BoxEditing.insertBoxRecords(
            ByteArray(0x83D0),
            0,
            listOf(ByteArray(79) { 1 })
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun insertBoxRecordsRejectsEmptyBatch() {
        Gen3BoxEditing.insertBoxRecords(
            ByteArray(0x83D0),
            0,
            emptyList()
        )
    }


    @Test
    fun duplicateSelectedBoxRecordsKeepsSourcesAndCopiesToFirstEmptySlots() {
        val loaded = ByteArray(0x83D0)
        val sourceBox = 4
        val targetBox = 9
        val sourceRange = Gen3BoxEditing.boxStorageRange(sourceBox)
        val targetRange = Gen3BoxEditing.boxStorageRange(targetBox)
        val selected = listOf(17, 2)
        selected.forEachIndexed { recordIndex, slot ->
            val offset = sourceRange.first + slot * 80
            for (byteIndex in 0 until 80) {
                loaded[offset + byteIndex] =
                    (recordIndex * 80 + byteIndex + 1).toByte()
            }
        }
        loaded[targetRange.first] = 91
        loaded[targetRange.first + 2 * 80] = 92
        val original = loaded.copyOf()

        val result = Gen3BoxEditing.duplicateSelectedBoxRecords(
            loaded,
            sourceBox,
            selected,
            targetBox
        )

        assertArrayEquals(original, loaded)
        assertEquals(listOf(2, 17), result.sourceSlots)
        assertEquals(listOf(1, 3), result.targetSlots)
        result.sourceSlots.forEach { sourceSlot ->
            val sourceOffset = sourceRange.first + sourceSlot * 80
            assertArrayEquals(
                original.copyOfRange(sourceOffset, sourceOffset + 80),
                result.pc.copyOfRange(sourceOffset, sourceOffset + 80)
            )
        }
        result.sourceSlots.forEachIndexed { index, sourceSlot ->
            val sourceOffset = sourceRange.first + sourceSlot * 80
            val targetOffset = targetRange.first + result.targetSlots[index] * 80
            assertArrayEquals(
                original.copyOfRange(sourceOffset, sourceOffset + 80),
                result.pc.copyOfRange(targetOffset, targetOffset + 80)
            )
        }
        val targetRanges = result.targetSlots.map { slot ->
            val start = targetRange.first + slot * 80
            start until start + 80
        }
        assertTrue(result.pc.indices.all { index ->
            targetRanges.any { index in it } || result.pc[index] == original[index]
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateSelectedBoxRecordsRejectsSameBox() {
        val loaded = ByteArray(0x83D0)
        loaded[Gen3BoxEditing.boxStorageRange(1).first] = 1
        Gen3BoxEditing.duplicateSelectedBoxRecords(
            loaded,
            1,
            listOf(0),
            1
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateSelectedBoxRecordsRejectsInsufficientCapacity() {
        val loaded = ByteArray(0x83D0)
        val sourceRange = Gen3BoxEditing.boxStorageRange(0)
        loaded[sourceRange.first] = 1
        loaded[sourceRange.first + 80] = 2
        val targetRange = Gen3BoxEditing.boxStorageRange(1)
        for (slot in 0 until 29) {
            loaded[targetRange.first + slot * 80] = (slot + 1).toByte()
        }
        Gen3BoxEditing.duplicateSelectedBoxRecords(
            loaded,
            0,
            listOf(0, 1),
            1
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateSelectedBoxRecordsRejectsDuplicateSelection() {
        val loaded = ByteArray(0x83D0)
        loaded[Gen3BoxEditing.boxStorageRange(0).first] = 1
        Gen3BoxEditing.duplicateSelectedBoxRecords(
            loaded,
            0,
            listOf(0, 0),
            1
        )
    }

}
