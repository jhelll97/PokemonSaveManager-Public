package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3PartyEditingTest {
    private fun partyMarkingRecord(personality: Int, egg: Boolean, markings: Int): ByteArray {
        val header = ByteArray(32) { (it + personality).toByte() }
        header[0x13] = (2 or (if (egg) 4 else 0)).toByte()
        header[0x1B] = markings.toByte()
        val canonical = ByteArray(48) { (it * 13 + personality).toByte() }
        canonical[0] = 25
        canonical[1] = 0
        canonical[43] = ((canonical[43].toInt() and 0xBF) or (if (egg) 0x40 else 0)).toByte()
        return ByteArray(100) { (it * 7 + personality).toByte() }.also { record ->
            Gen3Pk3Codec.encode(header, personality, 12345, 54321, canonical).copyInto(record)
        }
    }

    @Test
    fun partyMarkingsPreserveHpStatusStatsEggDataAndAllUnselectedBytesForEveryMask() {
        for (personality in 0 until 24) {
            val party = ByteArray(620) { (it % 251 + 1).toByte() }
            partyMarkingRecord(personality, false, 0xA5).copyInto(party, 0)
            partyMarkingRecord(personality, true, 0xF2).copyInto(party, 500)
            val original = party.copyOf()
            for (mask in 0..15) {
                val result = Gen3PartyEditing.setSelectedPartyMarkings(party, 6, listOf(5, 0), mask)
                assertEquals(0xA0 or mask, result[0x1B].toInt() and 255)
                assertEquals(0xF0 or mask, result[500 + 0x1B].toInt() and 255)
                assertTrue(result.indices.all { it == 0x1B || it == 500 + 0x1B || result[it] == original[it] })
                for (offset in listOf(0, 500)) {
                    val before = Gen3Pk3Codec.decode(original.copyOfRange(offset, offset + 80))
                    val after = Gen3Pk3Codec.decode(result.copyOfRange(offset, offset + 80))
                    assertEquals(before.storedChecksum, after.storedChecksum)
                    assertArrayEquals(before.encryptedSection, after.encryptedSection)
                    assertArrayEquals(before.canonicalSection, after.canonicalSection)
                    assertArrayEquals(original.copyOfRange(offset + 80, offset + 100),
                        result.copyOfRange(offset + 80, offset + 100))
                }
                assertArrayEquals(original, party)
            }
        }
    }

    @Test
    fun partyMarkingsRejectInvalidCountStorageMaskAndInactiveOrDuplicateSlots() {
        val party = ByteArray(620)
        partyMarkingRecord(7, false, 0xA5).copyInto(party)
        val before = party.copyOf()
        val actions = listOf<() -> Unit>(
            { Gen3PartyEditing.setSelectedPartyMarkings(party, 0, listOf(0), 1) },
            { Gen3PartyEditing.setSelectedPartyMarkings(party, 7, listOf(0), 1) },
            { Gen3PartyEditing.setSelectedPartyMarkings(party.copyOf(599), 1, listOf(0), 1) },
            { Gen3PartyEditing.setSelectedPartyMarkings(party, 1, listOf(0), -1) },
            { Gen3PartyEditing.setSelectedPartyMarkings(party, 1, listOf(0), 16) },
            { Gen3PartyEditing.setSelectedPartyMarkings(party, 1, emptyList(), 1) },
            { Gen3PartyEditing.setSelectedPartyMarkings(party, 1, listOf(0, 0), 1) },
            { Gen3PartyEditing.setSelectedPartyMarkings(party, 1, listOf(-1), 1) },
            { Gen3PartyEditing.setSelectedPartyMarkings(party, 1, listOf(1), 1) },
            { Gen3PartyEditing.setSelectedPartyMarkings(party, 2, listOf(1), 1) }
        )
        actions.forEach { action ->
            var rejected = false
            try { action() } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertArrayEquals(before, party)
        }
    }

    @Test
    fun partyMarkingsRejectCorruptOrBadEggBatchAndKeepInactiveSlotsOnNoOp() {
        val party = ByteArray(620) { 9 }
        partyMarkingRecord(0, false, 0xA5).copyInto(party, 0)
        partyMarkingRecord(23, true, 0xF5).copyInto(party, 100)
        assertArrayEquals(party,
            Gen3PartyEditing.setSelectedPartyMarkings(party, 2, listOf(0, 1), 5))
        for (badEgg in listOf(false, true)) {
            val invalid = party.copyOf()
            val offset = 100 + (if (badEgg) 0x13 else 0x20)
            invalid[offset] = (invalid[offset].toInt() xor 1).toByte()
            val before = invalid.copyOf()
            var rejected = false
            try { Gen3PartyEditing.setSelectedPartyMarkings(invalid, 2, listOf(0, 1), 0) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertArrayEquals(before, invalid)
        }
    }

    private fun partySortEntries(): List<Gen3PartySortEntry> = listOf(
        Gen3PartySortEntry(0, 25, 20, "Zulu", false),
        Gen3PartySortEntry(1, 1, 50, "Beta", false),
        Gen3PartySortEntry(2, 150, 0, "Egg B", true),
        Gen3PartySortEntry(3, 25, 20, "alpha", false),
        Gen3PartySortEntry(4, 1, 5, "Egg A", true),
        Gen3PartySortEntry(5, 1, 20, "ALPHA", false)
    )

    @Test
    fun partyDexSortKeepsStableTiesAndOriginalEggOrderInBothDirections() {
        val entries = partySortEntries().reversed()
        assertEquals(listOf(1, 5, 0, 3, 2, 4),
            Gen3PartyEditing.sortedPartySlots(entries, Gen3PartySortField.SPECIES))
        assertEquals(listOf(0, 3, 1, 5, 2, 4),
            Gen3PartyEditing.sortedPartySlots(entries, Gen3PartySortField.SPECIES, true))
    }

    @Test
    fun partyLevelSortKeepsEqualLevelsStableAndDoesNotUseEggLevels() {
        val entries = partySortEntries()
        assertEquals(listOf(0, 3, 5, 1, 2, 4),
            Gen3PartyEditing.sortedPartySlots(entries, Gen3PartySortField.LEVEL))
        assertEquals(listOf(1, 0, 3, 5, 2, 4),
            Gen3PartyEditing.sortedPartySlots(entries, Gen3PartySortField.LEVEL, true))
    }

    @Test
    fun partyNameSortIsCaseInsensitiveAndPlansPreserveCompleteRawRecords() {
        val entries = partySortEntries()
        assertEquals(listOf(3, 5, 1, 0, 2, 4),
            Gen3PartyEditing.sortedPartySlots(entries, Gen3PartySortField.NAME))
        assertEquals(listOf(0, 1, 3, 5, 2, 4),
            Gen3PartyEditing.sortedPartySlots(entries, Gen3PartySortField.NAME, true))
        val party = ByteArray(620) { (it % 251 + 1).toByte() }
        val before = party.copyOf()
        for (field in Gen3PartySortField.values()) for (descending in listOf(false, true)) {
            val order = Gen3PartyEditing.sortedPartySlots(entries, field, descending)
            val result = Gen3PartyEditing.reorderPartyPokemon(party, 6, order)
            order.forEachIndexed { target, source ->
                assertArrayEquals(before.copyOfRange(source * 100, (source + 1) * 100),
                    result.copyOfRange(target * 100, (target + 1) * 100))
            }
            assertArrayEquals(before.copyOfRange(600, 620), result.copyOfRange(600, 620))
        }
        assertArrayEquals(before, party)
    }

    @Test
    fun partySortRejectsInvalidSlotSetsAndKeys() {
        val first = partySortEntries().first()
        val invalid = listOf(
            emptyList(),
            listOf(first, first),
            listOf(first, first.copy(sourceSlot = 2)),
            listOf(first.copy(sourceSlot = -1)),
            (0 until 7).map { first.copy(sourceSlot = it) },
            listOf(first.copy(species = 0)),
            listOf(first.copy(species = 387)),
            listOf(first.copy(level = 0)),
            listOf(first.copy(level = 101))
        )
        invalid.forEach { entries ->
            var rejected = false
            try { Gen3PartyEditing.sortedPartySlots(entries, Gen3PartySortField.SPECIES) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
    }

    @Test
    fun pcBatchCopyFillsPartyAndPreservesEverySourcePcByte() {
        val party = ByteArray(620)
        for (index in 0 until 300) party[index] = (index % 251 + 1).toByte()
        party.fill(9, 600, 620)
        val pc = ByteArray(0x83D0)
        pc[0] = 5
        pc.fill(7, 4 + 420 * 80, pc.size)
        val indices = listOf(419, 0, 137)
        indices.forEachIndexed { index, global ->
            ByteArray(80) { (index * 20 + it + 1).toByte() }.copyInto(pc, 4 + global * 80)
        }
        val beforeParty = party.copyOf()
        val beforePc = pc.copyOf()
        val builds = indices.mapIndexed { index, global ->
            Gen3PcPartyBuild(global, 40 + index, intArrayOf(300, 150, 140, 130, 120, 110))
        }
        val result = Gen3PartyEditing.copyPcPokemonBatchToParty(party, 3, pc, builds)
        assertEquals(6, result.partyCount)
        assertEquals(indices, result.sourceGlobalIndices)
        assertEquals(listOf(3, 4, 5), result.insertedSlots)
        assertArrayEquals(beforePc, pc)
        assertArrayEquals(beforePc, result.pc)
        assertArrayEquals(beforeParty, party)
        assertArrayEquals(beforeParty.copyOfRange(0, 300), result.partyRecords.copyOfRange(0, 300))
        assertArrayEquals(beforeParty.copyOfRange(600, 620), result.partyRecords.copyOfRange(600, 620))
        indices.forEachIndexed { index, global ->
            val offset = (index + 3) * 100
            assertArrayEquals(beforePc.copyOfRange(4 + global * 80, 4 + (global + 1) * 80),
                result.partyRecords.copyOfRange(offset, offset + 80))
            assertEquals(40 + index, result.partyRecords[offset + 0x54].toInt() and 255)
        }
        result.pc.fill(0)
        result.partyRecords.fill(0)
        assertArrayEquals(beforePc, pc)
        assertArrayEquals(beforeParty, party)
    }

    @Test
    fun pcCopyAndMoveBuildTheSamePartyWithDifferentSourcePolicies() {
        val party = ByteArray(600)
        party[0] = 1
        val pc = ByteArray(0x83D0)
        pc.fill(3, 4, 84)
        val item = Gen3PcPartyBuild(0, 10, intArrayOf(30, 20, 20, 20, 20, 20))
        val copied = Gen3PartyEditing.copyPcPokemonBatchToParty(party, 1, pc, listOf(item))
        val moved = Gen3PartyEditing.movePcPokemonBatchToParty(party, 1, pc, listOf(item))
        assertArrayEquals(copied.partyRecords, moved.partyRecords)
        assertEquals(copied.insertedSlots, moved.insertedSlots)
        assertArrayEquals(pc, copied.pc)
        assertTrue(moved.pc.copyOfRange(4, 84).all { it == 0.toByte() })
        assertTrue(pc.copyOfRange(4, 84).all { it == 3.toByte() })
    }

    @Test
    fun pcBatchCopyRejectsInvalidLaterRecordsAndDestinationsAtomically() {
        val party = ByteArray(600)
        party[0] = 1
        val pc = ByteArray(0x83D0)
        pc.fill(3, 4, 164)
        val beforeParty = party.copyOf()
        val beforePc = pc.copyOf()
        val first = Gen3PcPartyBuild(0, 5, intArrayOf(20, 10, 10, 10, 10, 10))
        val second = first.copy(sourceGlobalIndex = 1)
        val occupied = party.copyOf().also { it[100] = 1 }
        val occupiedBefore = occupied.copyOf()
        val calls = listOf<() -> Unit>(
            { Gen3PartyEditing.copyPcPokemonBatchToParty(party, 1, pc, listOf(first, second.copy(level = 101))) },
            { Gen3PartyEditing.copyPcPokemonBatchToParty(party, 1, pc,
                listOf(first, second.copy(battleStats = intArrayOf(0, 1, 1, 1, 1, 1)))) },
            { Gen3PartyEditing.copyPcPokemonBatchToParty(party, 1, pc,
                listOf(first, second.copy(sourceGlobalIndex = 2))) },
            { Gen3PartyEditing.copyPcPokemonBatchToParty(occupied, 1, pc, listOf(first)) },
            { Gen3PartyEditing.copyPcPokemonBatchToParty(party, 5, pc, listOf(first, second)) },
            { Gen3PartyEditing.copyPcPokemonBatchToParty(party, 1, pc, listOf(first, first)) }
        )
        calls.forEach { call ->
            var rejected = false
            try { call() } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertArrayEquals(beforePc, pc)
            assertArrayEquals(beforeParty, party)
            assertArrayEquals(occupiedBefore, occupied)
        }
    }

    @Test
    fun partyOrderPermutesCompleteRecordsWithoutChangingInactiveStorage() {
        val party = ByteArray(620) { (it % 251 + 1).toByte() }
        val before = party.copyOf()
        val order = listOf(2, 0, 3, 1)
        val result = Gen3PartyEditing.reorderPartyPokemon(party, 4, order)
        assertArrayEquals(before, party)
        order.forEachIndexed { target, source ->
            assertArrayEquals(before.copyOfRange(source * 100, (source + 1) * 100),
                result.copyOfRange(target * 100, (target + 1) * 100))
        }
        assertArrayEquals(before.copyOfRange(400, 620), result.copyOfRange(400, 620))
        val restored = Gen3PartyEditing.reorderPartyPokemon(result, 4,
            (0 until 4).map { order.indexOf(it) })
        assertArrayEquals(before, restored)
    }

    @Test
    fun partyOrderSupportsEveryPermutationOfASixMemberParty() {
        val party = ByteArray(600) { (it % 251 + 1).toByte() }
        val before = party.copyOf()
        var checked = 0
        fun visit(prefix: List<Int>, remaining: List<Int>) {
            if (remaining.isEmpty()) {
                val result = Gen3PartyEditing.reorderPartyPokemon(party, 6, prefix)
                prefix.forEachIndexed { target, source ->
                    assertArrayEquals(before.copyOfRange(source * 100, (source + 1) * 100),
                        result.copyOfRange(target * 100, (target + 1) * 100))
                }
                checked++
            } else remaining.forEach { visit(prefix + it, remaining - it) }
        }
        visit(emptyList(), (0 until 6).toList())
        assertEquals(720, checked)
        assertArrayEquals(before, party)
    }

    @Test
    fun partyOrderIdentityReturnsDetachedStorageForTheOnlyMember() {
        val party = ByteArray(600) { 7 }
        val result = Gen3PartyEditing.reorderPartyPokemon(party, 1, listOf(0))
        assertArrayEquals(party, result)
        result.fill(0)
        assertTrue(party.all { it == 7.toByte() })
    }

    @Test
    fun partyOrderRejectsMissingDuplicateInactiveAndEmptySourcesAtomically() {
        val party = ByteArray(600) { 1 }
        val before = party.copyOf()
        val emptySource = party.copyOf().also { it.fill(0, 200, 280) }
        val emptyBefore = emptySource.copyOf()
        val calls = listOf<() -> Unit>(
            { Gen3PartyEditing.reorderPartyPokemon(party, 3, listOf(0, 1)) },
            { Gen3PartyEditing.reorderPartyPokemon(party, 3, listOf(0, 1, 1)) },
            { Gen3PartyEditing.reorderPartyPokemon(party, 3, listOf(0, 1, 3)) },
            { Gen3PartyEditing.reorderPartyPokemon(party, 3, listOf(-1, 1, 2)) },
            { Gen3PartyEditing.reorderPartyPokemon(party, 3, listOf(0, 1, 2, 2)) },
            { Gen3PartyEditing.reorderPartyPokemon(party, 0, emptyList()) },
            { Gen3PartyEditing.reorderPartyPokemon(party, 7, (0 until 7).toList()) },
            { Gen3PartyEditing.reorderPartyPokemon(ByteArray(599) { 1 }, 3, listOf(0, 1, 2)) },
            { Gen3PartyEditing.reorderPartyPokemon(emptySource, 3, listOf(2, 1, 0)) }
        )
        calls.forEach { call ->
            var rejected = false
            try { call() } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertArrayEquals(before, party)
            assertArrayEquals(emptyBefore, emptySource)
        }
    }

    @Test
    fun eligibleRecoveryPreservesExcludedInjuredMembersAndOnlyChangesSelectedSlots() {
        val party = ByteArray(620) { 0x5A.toByte() }
        for (slot in 0 until 6) recoveryPartyRecord(0, 300 + slot, 8).copyInto(party, slot * 100)
        val before = party.copyOf()
        val result = Gen3PartyEditing.restoreEligiblePartyHpAndStatus(
            party, 6, listOf(0, 2, 4, 5), listOf(5, 2)
        )
        assertEquals(listOf(2, 5), result.changedSlots)
        assertArrayEquals(before, party)
        for (slot in listOf(0, 1, 3, 4)) {
            assertArrayEquals(before.copyOfRange(slot * 100, (slot + 1) * 100),
                result.partyRecords.copyOfRange(slot * 100, (slot + 1) * 100))
        }
        assertArrayEquals(before.copyOfRange(600, 620), result.partyRecords.copyOfRange(600, 620))
        for (slot in result.changedSlots) {
            val start = slot * 100
            assertTrue(result.partyRecords.copyOfRange(start + 0x50, start + 0x54)
                .all { it == 0.toByte() })
            assertArrayEquals(before.copyOfRange(start + 0x58, start + 0x5A),
                result.partyRecords.copyOfRange(start + 0x56, start + 0x58))
            assertArrayEquals(before.copyOfRange(start, start + 80),
                result.partyRecords.copyOfRange(start, start + 80))
        }
    }

    @Test
    fun eligibleRecoveryRejectsExcludedSlotsAndInvalidEligibilityWithoutMutation() {
        val party = ByteArray(600)
        for (slot in 0 until 3) recoveryPartyRecord(1, 100, 8).copyInto(party, slot * 100)
        val before = party.copyOf()
        val calls = listOf<() -> Unit>(
            { Gen3PartyEditing.restoreEligiblePartyHpAndStatus(party, 3, listOf(0, 2), listOf(0, 1)) },
            { Gen3PartyEditing.restoreEligiblePartyHpAndStatus(party, 3, listOf(0, 0), listOf(0)) },
            { Gen3PartyEditing.restoreEligiblePartyHpAndStatus(party, 3, listOf(-1, 0), listOf(0)) },
            { Gen3PartyEditing.restoreEligiblePartyHpAndStatus(party, 3, listOf(0, 3), listOf(0)) },
            { Gen3PartyEditing.restoreEligiblePartyHpAndStatus(party, 3, emptyList(), listOf(0)) },
            { Gen3PartyEditing.restoreEligiblePartyHpAndStatus(party, 3, listOf(0), emptyList()) },
            { Gen3PartyEditing.restoreEligiblePartyHpAndStatus(party, 3, listOf(0), listOf(0, 0)) }
        )
        calls.forEach { call ->
            var rejected = false
            try { call() } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertArrayEquals(before, party)
        }
    }

    @Test
    fun eligibleRecoveryRevalidatesEverySelectedHpFieldBeforeProducingResult() {
        val party = ByteArray(600)
        recoveryPartyRecord(0, 100, 8).copyInto(party)
        recoveryPartyRecord(101, 100, 0).copyInto(party, 200)
        val before = party.copyOf()
        var rejected = false
        try {
            Gen3PartyEditing.restoreEligiblePartyHpAndStatus(party, 3, listOf(0, 2), listOf(0, 2))
        } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertArrayEquals(before, party)
    }

    @Test
    fun storedCopyFillsLastPartySlotAndPreservesExtendedStorage() {
        val party = ByteArray(620)
        for (index in 0 until 500) party[index] = (index % 251 + 1).toByte()
        for (index in 600 until 620) party[index] = 9
        val before = party.copyOf()
        val source = ByteArray(80) { (it + 1).toByte() }
        val sourceBefore = source.copyOf()
        val stats = intArrayOf(301, 151, 141, 131, 121, 111)
        val result = Gen3PartyEditing.appendStoredPokemonBatchToParty(
            party, 5, listOf(Gen3StoredPartyBuild(source, 100, stats))
        )
        assertEquals(6, result.partyCount)
        assertEquals(listOf(5), result.insertedSlots)
        assertArrayEquals(before, party)
        assertArrayEquals(sourceBefore, source)
        assertArrayEquals(before.copyOfRange(0, 500), result.partyRecords.copyOfRange(0, 500))
        assertArrayEquals(sourceBefore, result.partyRecords.copyOfRange(500, 580))
        assertArrayEquals(before.copyOfRange(600, 620), result.partyRecords.copyOfRange(600, 620))
        source.fill(0)
        stats.fill(0)
        party.fill(0)
        assertArrayEquals(sourceBefore, result.partyRecords.copyOfRange(500, 580))
        assertEquals(100, result.partyRecords[500 + 0x54].toInt() and 255)
        assertEquals(301 and 255, result.partyRecords[500 + 0x58].toInt() and 255)
        assertEquals(301 ushr 8, result.partyRecords[500 + 0x59].toInt() and 255)
    }

    @Test
    fun storedCopyAllowsIdenticalBankRecordsWithoutAliasingPartyCopies() {
        val party = ByteArray(600)
        party[0] = 7
        val source = ByteArray(80) { (it + 5).toByte() }
        val original = source.copyOf()
        val item = Gen3StoredPartyBuild(source, 10, intArrayOf(30, 20, 20, 20, 20, 20))
        val result = Gen3PartyEditing.appendStoredPokemonBatchToParty(party, 1, listOf(item, item))
        assertEquals(3, result.partyCount)
        assertEquals(listOf(1, 2), result.insertedSlots)
        assertArrayEquals(original, result.partyRecords.copyOfRange(100, 180))
        assertArrayEquals(original, result.partyRecords.copyOfRange(200, 280))
        result.partyRecords.fill(0, 100, 200)
        assertArrayEquals(original, result.partyRecords.copyOfRange(200, 280))
        assertArrayEquals(original, source)
        assertEquals(7, party[0].toInt())
        assertTrue(party.copyOfRange(100, 600).all { it == 0.toByte() })
    }

    @Test
    fun storedCopyRejectsInvalidStorageCountsEmptyBatchAndOverflowingStats() {
        val party = ByteArray(600)
        party[0] = 1
        val before = party.copyOf()
        val source = ByteArray(80) { 2 }
        val item = Gen3StoredPartyBuild(source, 5, intArrayOf(20, 10, 10, 10, 10, 10))
        val calls = listOf<() -> Unit>(
            { Gen3PartyEditing.appendStoredPokemonBatchToParty(ByteArray(599), 1, listOf(item)) },
            { Gen3PartyEditing.appendStoredPokemonBatchToParty(party, 0, listOf(item)) },
            { Gen3PartyEditing.appendStoredPokemonBatchToParty(party, 7, listOf(item)) },
            { Gen3PartyEditing.appendStoredPokemonBatchToParty(party, 1, emptyList()) },
            { Gen3PartyEditing.appendStoredPokemonBatchToParty(party, 1,
                listOf(item.copy(battleStats = intArrayOf(65536, 1, 1, 1, 1, 1)))) },
            { Gen3PartyEditing.appendStoredPokemonBatchToParty(party, 1,
                listOf(item.copy(battleStats = intArrayOf(20, -1, 1, 1, 1, 1)))) }
        )
        calls.forEach { call ->
            var rejected = false
            try { call() } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertArrayEquals(before, party)
            assertTrue(source.all { it == 2.toByte() })
        }
    }

    @Test
    fun storedBatchAppendPreservesSourcesAndBuildsExactOrderedPartyRecords() {
        val party = ByteArray(600)
        for (index in 0 until 200) party[index] = (index + 1).toByte()
        val first = ByteArray(80) { (it + 3).toByte() }
        val second = ByteArray(80) { (200 - it).toByte() }
        val beforeParty = party.copyOf()
        val beforeFirst = first.copyOf()
        val beforeSecond = second.copyOf()
        val stats = intArrayOf(300, 150, 140, 130, 120, 110)
        val result = Gen3PartyEditing.appendStoredPokemonBatchToParty(
            party, 2, listOf(
                Gen3StoredPartyBuild(first, 40, stats),
                Gen3StoredPartyBuild(second, 50, stats)
            )
        )
        assertEquals(4, result.partyCount)
        assertEquals(listOf(2, 3), result.insertedSlots)
        assertArrayEquals(beforeParty, party)
        assertArrayEquals(beforeFirst, first)
        assertArrayEquals(beforeSecond, second)
        assertArrayEquals(beforeParty.copyOfRange(0, 200), result.partyRecords.copyOfRange(0, 200))
        assertArrayEquals(first, result.partyRecords.copyOfRange(200, 280))
        assertArrayEquals(second, result.partyRecords.copyOfRange(300, 380))
        assertTrue(result.partyRecords.copyOfRange(400, 600).all { it == 0.toByte() })
        for ((index, level) in listOf(40, 50).withIndex()) {
            val offset = (index + 2) * 100
            assertEquals(level, result.partyRecords[offset + 0x54].toInt() and 0xFF)
            assertTrue(result.partyRecords.copyOfRange(offset + 0x50, offset + 0x54)
                .all { it == 0.toByte() })
            fun u16(at: Int): Int = (result.partyRecords[at].toInt() and 255) or
                ((result.partyRecords[at + 1].toInt() and 255) shl 8)
            assertEquals(stats[0], u16(offset + 0x56))
            for (stat in stats.indices) assertEquals(stats[stat], u16(offset + 0x58 + stat * 2))
        }
    }

    @Test
    fun storedBatchAppendRejectsCapacityAndOccupiedTailWithoutInputMutation() {
        val party = ByteArray(600) { 1 }
        val before = party.copyOf()
        val item = Gen3StoredPartyBuild(ByteArray(80) { 2 }, 5, intArrayOf(20, 10, 10, 10, 10, 10))
        val calls = listOf<() -> Unit>(
            { Gen3PartyEditing.appendStoredPokemonBatchToParty(party, 6, listOf(item)) },
            { Gen3PartyEditing.appendStoredPokemonBatchToParty(party, 5, listOf(item, item)) },
            { Gen3PartyEditing.appendStoredPokemonBatchToParty(party, 2, listOf(item)) }
        )
        calls.forEach { call ->
            var rejected = false
            try { call() } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertArrayEquals(before, party)
        }
    }

    @Test
    fun storedBatchAppendRejectsMalformedRecordLevelAndStatsAtomically() {
        val party = ByteArray(600)
        party[0] = 1
        val before = party.copyOf()
        val valid = Gen3StoredPartyBuild(ByteArray(80) { 1 }, 5, intArrayOf(20, 10, 10, 10, 10, 10))
        val invalid = listOf(
            valid.copy(pk3 = ByteArray(79) { 1 }),
            valid.copy(pk3 = ByteArray(80)),
            valid.copy(level = 0),
            valid.copy(level = 101),
            valid.copy(battleStats = intArrayOf(1, 2)),
            valid.copy(battleStats = intArrayOf(0, 1, 1, 1, 1, 1))
        )
        invalid.forEach { item ->
            var rejected = false
            try {
                Gen3PartyEditing.appendStoredPokemonBatchToParty(party, 1, listOf(valid, item))
            } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertArrayEquals(before, party)
        }
    }

    @Test
    fun selectedPartySnapshotsCanCopyTheOnlyMemberWithoutChangingPartyTail() {
        val party = ByteArray(600)
        for (index in 0 until 100) party[index] = (index + 1).toByte()
        val before = party.copyOf()
        val copies = Gen3PartyEditing.copySelectedPartyRecords(party, 1, listOf(0))
        assertEquals(1, copies.size)
        assertArrayEquals(before.copyOfRange(0, 80), copies.single())
        assertArrayEquals(before, party)
        copies.single().fill(0)
        assertArrayEquals(before, party)
    }

    @Test
    fun selectedPartySnapshotsCanCopyAllSixWithOriginalSlotOrderAndStateBytes() {
        val party = ByteArray(620) { (it % 251 + 1).toByte() }
        val before = party.copyOf()
        val copies = Gen3PartyEditing.copySelectedPartyRecords(
            party, 6, listOf(5, 3, 1, 4, 2, 0)
        )
        assertEquals(6, copies.size)
        for (slot in 0 until 6) {
            assertEquals(80, copies[slot].size)
            assertArrayEquals(
                before.copyOfRange(slot * 100, slot * 100 + 80), copies[slot]
            )
        }
        assertArrayEquals(before, party)
        copies.forEach { it.fill(0) }
        assertArrayEquals(before, party)
    }

    @Test
    fun selectedPartyRecordsAreSortedExactDetachedPk3WithoutPartyOnlyFields() {
        val party = ByteArray(620) { (it % 251 + 1).toByte() }
        val before = party.copyOf()
        val records = Gen3PartyEditing.copySelectedPartyRecords(party, 4, listOf(3, 1))
        assertEquals(2, records.size)
        assertEquals(80, records[0].size)
        assertEquals(80, records[1].size)
        assertArrayEquals(before.copyOfRange(100, 180), records[0])
        assertArrayEquals(before.copyOfRange(300, 380), records[1])
        records[0][0] = 0
        assertArrayEquals(before, party)
    }

    @Test
    fun selectedPartyRecordsDoNotChangeWhenSourceBufferChanges() {
        val party = ByteArray(600) { 7 }
        val records = Gen3PartyEditing.copySelectedPartyRecords(party, 6, listOf(5))
        party.fill(0)
        assertTrue(records.single().all { it == 7.toByte() })
    }

    @Test
    fun selectedPartyRecordsRejectDuplicatesInactiveSlotsAndInvalidStorage() {
        val party = ByteArray(600) { 1 }
        val calls = listOf<() -> Unit>(
            { Gen3PartyEditing.copySelectedPartyRecords(party, 3, emptyList()) },
            { Gen3PartyEditing.copySelectedPartyRecords(party, 3, listOf(1, 1)) },
            { Gen3PartyEditing.copySelectedPartyRecords(party, 3, listOf(-1)) },
            { Gen3PartyEditing.copySelectedPartyRecords(party, 3, listOf(3)) },
            { Gen3PartyEditing.copySelectedPartyRecords(party, 0, listOf(0)) },
            { Gen3PartyEditing.copySelectedPartyRecords(party, 7, listOf(0)) },
            { Gen3PartyEditing.copySelectedPartyRecords(ByteArray(599), 3, listOf(0)) }
        )
        calls.forEach { call ->
            var rejected = false
            try { call() } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertTrue(party.all { it == 1.toByte() })
        }
    }

    @Test
    fun selectedPartyRecordsRejectAnEmptyLaterSelectionWithoutChangingSource() {
        val party = ByteArray(600)
        party[0] = 1
        val before = party.copyOf()
        var rejected = false
        try {
            Gen3PartyEditing.copySelectedPartyRecords(party, 3, listOf(0, 2))
        } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertArrayEquals(before, party)
    }

    @Test
    fun partyBatchRemovalUsesOriginalSlotsAndPreservesSurvivorsAndInactiveBytes() {
        val party = ByteArray(620) { (it % 251 + 1).toByte() }
        val before = party.copyOf()
        val result = Gen3PartyEditing.removePartyPokemonBatch(party, 4, listOf(0, 2))
        assertEquals(2, result.partyCount)
        assertArrayEquals(before, party)
        assertArrayEquals(before.copyOfRange(100, 200), result.partyRecords.copyOfRange(0, 100))
        assertArrayEquals(before.copyOfRange(300, 400), result.partyRecords.copyOfRange(100, 200))
        assertTrue(result.partyRecords.copyOfRange(200, 400).all { it == 0.toByte() })
        assertArrayEquals(before.copyOfRange(400, 620), result.partyRecords.copyOfRange(400, 620))
        val reversed = Gen3PartyEditing.removePartyPokemonBatch(party, 4, listOf(2, 0))
        assertArrayEquals(result.partyRecords, reversed.partyRecords)
        result.partyRecords[0] = 0
        assertArrayEquals(before, party)
    }

    @Test
    fun partyBatchRemovalCanRemoveFiveButRetainsExactFinalRecord() {
        val party = ByteArray(600) { (it % 251 + 1).toByte() }
        val before = party.copyOf()
        val result = Gen3PartyEditing.removePartyPokemonBatch(party, 6, listOf(5, 1, 0, 4, 2))
        assertEquals(1, result.partyCount)
        assertArrayEquals(before.copyOfRange(300, 400), result.partyRecords.copyOfRange(0, 100))
        assertTrue(result.partyRecords.copyOfRange(100, 600).all { it == 0.toByte() })
        assertArrayEquals(before, party)
    }

    @Test
    fun partyBatchRemovalDoesNotExposePartialEditsWhenALaterRemovalFails() {
        val party = ByteArray(600)
        for (slot in 1..2) party[slot * 100] = slot.toByte()
        val before = party.copyOf()
        var rejected = false
        try {
            // Slot 2 stages successfully; the empty slot 0 must then reject the whole batch.
            Gen3PartyEditing.removePartyPokemonBatch(party, 3, listOf(0, 2))
        } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertArrayEquals(before, party)
    }

    @Test
    fun partyBatchRemovalRejectsDuplicateInactiveAndFinalMemberSelections() {
        val party = ByteArray(600) { 1 }
        val calls = listOf<() -> Unit>(
            { Gen3PartyEditing.removePartyPokemonBatch(party, 3, emptyList()) },
            { Gen3PartyEditing.removePartyPokemonBatch(party, 3, listOf(0, 0)) },
            { Gen3PartyEditing.removePartyPokemonBatch(party, 3, listOf(-1)) },
            { Gen3PartyEditing.removePartyPokemonBatch(party, 3, listOf(3)) },
            { Gen3PartyEditing.removePartyPokemonBatch(party, 3, listOf(0, 1, 2)) },
            { Gen3PartyEditing.removePartyPokemonBatch(party, 1, listOf(0)) },
            { Gen3PartyEditing.removePartyPokemonBatch(party, 7, listOf(0)) },
            { Gen3PartyEditing.removePartyPokemonBatch(ByteArray(599), 3, listOf(0)) }
        )
        calls.forEach { call ->
            var rejected = false
            try { call() } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertTrue(party.all { it == 1.toByte() })
        }
    }

    @Test
    fun partyBatchCopyKeepsFullPartyAndCopiesExactRecordsInSlotOrder() {
        val party = ByteArray(620) { (it % 251 + 1).toByte() }
        val pc = ByteArray(0x83D0)
        val range = Gen3BoxEditing.boxStorageRange(13)
        pc[range.first] = 7
        val beforeParty = party.copyOf()
        val beforePc = pc.copyOf()
        val result = Gen3PartyEditing.duplicatePartyPokemonBatchToPc(
            party, 6, listOf(5, 0, 4, 2, 1, 3), pc, 13
        )
        assertEquals(6, result.partyCount)
        assertEquals((0 until 6).toList(), result.sourceSlots)
        assertEquals((1..6).toList(), result.targetSlots)
        assertArrayEquals(beforeParty, party)
        assertArrayEquals(beforeParty, result.partyRecords)
        assertArrayEquals(beforePc, pc)
        result.sourceSlots.forEachIndexed { index, slot ->
            val target = range.first + result.targetSlots[index] * 80
            assertArrayEquals(
                beforeParty.copyOfRange(slot * 100, slot * 100 + 80),
                result.pc.copyOfRange(target, target + 80)
            )
        }
        val ranges = result.targetSlots.map { slot ->
            range.first + slot * 80 until range.first + (slot + 1) * 80
        }
        assertTrue(pc.indices.all { index ->
            ranges.any { index in it } || result.pc[index] == beforePc[index]
        })
        result.partyRecords[0] = 0
        assertArrayEquals(beforeParty, party)
    }

    @Test
    fun partyBatchCopySupportsTheOnlyPartyMemberWithoutRemovingIt() {
        val party = ByteArray(600)
        for (index in 0 until 100) party[index] = (index + 1).toByte()
        val result = Gen3PartyEditing.duplicatePartyPokemonBatchToPc(
            party, 1, listOf(0), ByteArray(0x83D0), 0
        )
        assertEquals(1, result.partyCount)
        assertArrayEquals(party, result.partyRecords)
        assertArrayEquals(party.copyOfRange(0, 80), result.pc.copyOfRange(4, 84))
    }

    @Test
    fun partyBatchCopyRejectsInsufficientSpaceWithoutMutatingInputs() {
        val party = ByteArray(600) { 1 }
        val pc = ByteArray(0x83D0)
        val range = Gen3BoxEditing.boxStorageRange(0)
        for (slot in 0 until 29) pc[range.first + slot * 80] = 9
        val beforeParty = party.copyOf()
        val beforePc = pc.copyOf()
        var rejected = false
        try {
            Gen3PartyEditing.duplicatePartyPokemonBatchToPc(party, 2, listOf(0, 1), pc, 0)
        } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertArrayEquals(beforeParty, party)
        assertArrayEquals(beforePc, pc)
    }

    @Test
    fun partyBatchCopyRejectsMalformedInputsAndDuplicateSelections() {
        val party = ByteArray(600) { 1 }
        val pc = ByteArray(0x83D0)
        val calls = listOf<() -> Unit>(
            { Gen3PartyEditing.duplicatePartyPokemonBatchToPc(party, 2, emptyList(), pc, 0) },
            { Gen3PartyEditing.duplicatePartyPokemonBatchToPc(party, 2, listOf(0, 0), pc, 0) },
            { Gen3PartyEditing.duplicatePartyPokemonBatchToPc(party, 2, listOf(-1), pc, 0) },
            { Gen3PartyEditing.duplicatePartyPokemonBatchToPc(party, 2, listOf(2), pc, 0) },
            { Gen3PartyEditing.duplicatePartyPokemonBatchToPc(party, 0, listOf(0), pc, 0) },
            { Gen3PartyEditing.duplicatePartyPokemonBatchToPc(party, 7, listOf(0), pc, 0) },
            { Gen3PartyEditing.duplicatePartyPokemonBatchToPc(ByteArray(599), 2, listOf(0), pc, 0) },
            { Gen3PartyEditing.duplicatePartyPokemonBatchToPc(ByteArray(600), 2, listOf(0), pc, 0) },
            { Gen3PartyEditing.duplicatePartyPokemonBatchToPc(party, 2, listOf(0), ByteArray(5), 0) },
            { Gen3PartyEditing.duplicatePartyPokemonBatchToPc(party, 2, listOf(0), pc, 14) }
        )
        calls.forEach { call ->
            var rejected = false
            try { call() } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertTrue(party.all { it == 1.toByte() })
            assertTrue(pc.all { it == 0.toByte() })
        }
    }

    @Test
    fun partyBatchMovePreservesExactPk3CompactsSurvivorsAndUsesEmptySlots() {
        val party = ByteArray(620) { (it % 251 + 1).toByte() }
        val pc = ByteArray(0x83D0)
        val box = 4
        val range = Gen3BoxEditing.boxStorageRange(box)
        pc[range.first] = 9
        pc[range.first + 2 * 80] = 7
        val beforeParty = party.copyOf()
        val beforePc = pc.copyOf()

        val moved = Gen3PartyEditing.movePartyPokemonBatchToPc(
            party, 4, listOf(2, 0), pc, box
        )

        assertEquals(2, moved.partyCount)
        assertEquals(listOf(0, 2), moved.sourceSlots)
        assertEquals(listOf(1, 3), moved.targetSlots)
        assertArrayEquals(beforeParty, party)
        assertArrayEquals(beforePc, pc)
        for ((destination, source) in listOf(1, 3).withIndex()) {
            assertArrayEquals(
                beforeParty.copyOfRange(source * 100, (source + 1) * 100),
                moved.partyRecords.copyOfRange(destination * 100, (destination + 1) * 100)
            )
        }
        assertTrue(moved.partyRecords.copyOfRange(200, 400).all { it == 0.toByte() })
        assertArrayEquals(beforeParty.copyOfRange(400, 620), moved.partyRecords.copyOfRange(400, 620))
        moved.sourceSlots.forEachIndexed { index, source ->
            val destination = range.first + moved.targetSlots[index] * 80
            assertArrayEquals(
                beforeParty.copyOfRange(source * 100, source * 100 + 80),
                moved.pc.copyOfRange(destination, destination + 80)
            )
        }
        val targetRanges = moved.targetSlots.map { slot ->
            range.first + slot * 80 until range.first + (slot + 1) * 80
        }
        assertTrue(pc.indices.all { index ->
            targetRanges.any { index in it } || moved.pc[index] == beforePc[index]
        })
    }

    @Test
    fun partyBatchMoveCanMoveFiveFromAFullParty() {
        val party = ByteArray(600) { (it % 251 + 1).toByte() }
        val before = party.copyOf()
        val moved = Gen3PartyEditing.movePartyPokemonBatchToPc(
            party, 6, listOf(5, 4, 2, 1, 0), ByteArray(0x83D0), 13
        )
        assertEquals(1, moved.partyCount)
        assertEquals(listOf(0, 1, 2, 3, 4), moved.targetSlots)
        assertArrayEquals(before.copyOfRange(300, 400), moved.partyRecords.copyOfRange(0, 100))
        assertTrue(moved.partyRecords.copyOfRange(100, 600).all { it == 0.toByte() })
        assertArrayEquals(before, party)
    }

    @Test
    fun partyBatchMoveRejectsInsufficientSpaceWithoutChangingEitherInput() {
        val party = ByteArray(600) { 1 }
        val pc = ByteArray(0x83D0)
        val range = Gen3BoxEditing.boxStorageRange(2)
        for (slot in 0 until 29) pc[range.first + slot * 80] = 1
        val beforeParty = party.copyOf()
        val beforePc = pc.copyOf()
        var rejected = false
        try {
            Gen3PartyEditing.movePartyPokemonBatchToPc(party, 3, listOf(0, 1), pc, 2)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
        assertArrayEquals(beforeParty, party)
        assertArrayEquals(beforePc, pc)
    }

    @Test
    fun partyBatchMoveRejectsInvalidSelectionsAndFinalMember() {
        val party = ByteArray(600) { 1 }
        val pc = ByteArray(0x83D0)
        val selections = listOf(emptyList(), listOf(0, 0), listOf(-1), listOf(3), listOf(0, 1, 2))
        selections.forEach { selection ->
            var rejected = false
            try {
                Gen3PartyEditing.movePartyPokemonBatchToPc(party, 3, selection, pc, 0)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
            assertTrue(party.all { it == 1.toByte() })
            assertTrue(pc.all { it == 0.toByte() })
        }
    }

    @Test
    fun partyBatchMoveRejectsEmptySourceAndMalformedStorage() {
        val party = ByteArray(600) { 1 }
        val pc = ByteArray(0x83D0)
        val calls = listOf<() -> Unit>(
            { Gen3PartyEditing.movePartyPokemonBatchToPc(ByteArray(600), 2, listOf(0), pc, 0) },
            { Gen3PartyEditing.movePartyPokemonBatchToPc(ByteArray(599), 2, listOf(0), pc, 0) },
            { Gen3PartyEditing.movePartyPokemonBatchToPc(party, 1, listOf(0), pc, 0) },
            { Gen3PartyEditing.movePartyPokemonBatchToPc(party, 7, listOf(0), pc, 0) },
            { Gen3PartyEditing.movePartyPokemonBatchToPc(party, 2, listOf(0), ByteArray(5), 0) },
            { Gen3PartyEditing.movePartyPokemonBatchToPc(party, 2, listOf(0), pc, 14) }
        )
        calls.forEach { call ->
            var rejected = false
            try { call() } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertTrue(party.all { it == 1.toByte() })
            assertTrue(pc.all { it == 0.toByte() })
        }
    }

    private fun recoveryPartyRecord(hp: Int, maxHp: Int, status: Int): ByteArray =
        ByteArray(100) { (it + 1).toByte() }.also { record ->
            record.fill(0, 0x50, 0x54)
            record[0x50] = status.toByte()
            record[0x56] = (hp and 0xFF).toByte()
            record[0x57] = (hp ushr 8).toByte()
            record[0x58] = (maxHp and 0xFF).toByte()
            record[0x59] = (maxHp ushr 8).toByte()
        }

    @Test
    fun batchRecoveryChangesOnlySelectedHpAndStatusAndKeepsInactiveBytes() {
        val party = ByteArray(620) { 0x5A.toByte() }
        recoveryPartyRecord(0, 300, 8).copyInto(party, 0)
        recoveryPartyRecord(50, 50, 0).copyInto(party, 100)
        recoveryPartyRecord(20, 100, 0).copyInto(party, 200)
        recoveryPartyRecord(70, 80, 16).copyInto(party, 300)
        val before = party.copyOf()

        val result = Gen3PartyEditing.restorePartyBatchHpAndStatus(
            party, 4, listOf(2, 0, 1)
        )

        assertEquals(listOf(0, 2), result.changedSlots)
        assertArrayEquals(before, party)
        for (slot in listOf(0, 2)) {
            val start = slot * 100
            assertTrue(result.partyRecords.copyOfRange(start + 0x50, start + 0x54)
                .all { it == 0.toByte() })
            assertArrayEquals(
                before.copyOfRange(start + 0x58, start + 0x5A),
                result.partyRecords.copyOfRange(start + 0x56, start + 0x58)
            )
        }
        assertTrue(before.indices.all { index ->
            val slot = index / 100
            val local = index % 100
            (slot in listOf(0, 2) &&
                (local in 0x50 until 0x54 || local in 0x56 until 0x58)) ||
                before[index] == result.partyRecords[index]
        })
    }

    @Test
    fun batchRecoveryReportsNoChangesForHealthyParty() {
        val party = ByteArray(600)
        recoveryPartyRecord(100, 100, 0).copyInto(party)
        val result = Gen3PartyEditing.restorePartyBatchHpAndStatus(party, 1, listOf(0))
        assertTrue(result.changedSlots.isEmpty())
        assertArrayEquals(party, result.partyRecords)
        result.partyRecords[0] = 0
        assertEquals(1, party[0].toInt())
    }

    @Test
    fun batchRecoveryClearsStatusOnlyAtFullHpAndHandlesSixMemberParty() {
        val party = ByteArray(600)
        for (slot in 0 until 6) {
            recoveryPartyRecord(0xFFFF, 0xFFFF, 0).copyInto(party, slot * 100)
        }
        party[5 * 100 + 0x53] = 0x40
        val before = party.copyOf()
        val result = Gen3PartyEditing.restorePartyBatchHpAndStatus(
            party, 6, (0 until 6).toList().reversed()
        )
        assertEquals(listOf(5), result.changedSlots)
        assertArrayEquals(before, party)
        assertTrue(before.indices.all { index ->
            if (index == 5 * 100 + 0x53) result.partyRecords[index] == 0.toByte()
            else result.partyRecords[index] == before[index]
        })
    }

    @Test
    fun batchRecoveryRejectsLaterInvalidRecordWithoutMutatingEarlierRecord() {
        val party = ByteArray(600)
        recoveryPartyRecord(1, 200, 8).copyInto(party)
        recoveryPartyRecord(201, 200, 0).copyInto(party, 100)
        val before = party.copyOf()
        var rejected = false
        try {
            Gen3PartyEditing.restorePartyBatchHpAndStatus(party, 2, listOf(0, 1))
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
        assertArrayEquals(before, party)
    }

    @Test
    fun batchRecoveryRejectsInvalidSelectionsAndStorage() {
        val party = ByteArray(600)
        recoveryPartyRecord(1, 100, 0).copyInto(party)
        val invalidCalls = listOf<() -> Unit>(
            { Gen3PartyEditing.restorePartyBatchHpAndStatus(party, 1, emptyList()) },
            { Gen3PartyEditing.restorePartyBatchHpAndStatus(party, 1, listOf(0, 0)) },
            { Gen3PartyEditing.restorePartyBatchHpAndStatus(party, 1, listOf(1)) },
            { Gen3PartyEditing.restorePartyBatchHpAndStatus(party, 1, listOf(-1)) },
            { Gen3PartyEditing.restorePartyBatchHpAndStatus(party, 0, listOf(0)) },
            { Gen3PartyEditing.restorePartyBatchHpAndStatus(party, 7, listOf(0)) },
            { Gen3PartyEditing.restorePartyBatchHpAndStatus(ByteArray(599), 1, listOf(0)) },
            { Gen3PartyEditing.restorePartyBatchHpAndStatus(ByteArray(600), 1, listOf(0)) }
        )
        val before = party.copyOf()
        invalidCalls.forEach { call ->
            var rejected = false
            try {
                call()
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
            assertArrayEquals(before, party)
        }
    }

    @Test
    fun movePartyPokemonToPcCompactsPartyAndPreservesExactPk3() {
        val party = ByteArray(600)
        for (slot in 0 until 3) {
            val offset = slot * 100
            for (index in 0 until 100) {
                party[offset + index] = (slot * 100 + index + 1).toByte()
            }
        }
        val pc = ByteArray(0x83D0)
        val originalParty = party.copyOf()
        val originalPc = pc.copyOf()
        val targetGlobal = 137
        val targetOffset = 4 + targetGlobal * 80

        val result = Gen3PartyEditing.movePartyPokemonToPc(
            party,
            partyCount = 3,
            sourceSlot = 1,
            pc = pc,
            targetGlobalIndex = targetGlobal
        )

        assertEquals(2, result.partyCount)
        assertArrayEquals(originalParty, party)
        assertArrayEquals(originalPc, pc)
        assertArrayEquals(
            originalParty.copyOfRange(0, 100),
            result.partyRecords.copyOfRange(0, 100)
        )
        assertArrayEquals(
            originalParty.copyOfRange(200, 300),
            result.partyRecords.copyOfRange(100, 200)
        )
        assertTrue(result.partyRecords.copyOfRange(200, 300)
            .all { it == 0.toByte() })
        assertArrayEquals(
            originalParty.copyOfRange(100, 180),
            result.pc.copyOfRange(targetOffset, targetOffset + 80)
        )
        assertTrue(result.pc.indices.all {
            it in targetOffset until targetOffset + 80 ||
                result.pc[it] == originalPc[it]
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun movePartyPokemonToPcRejectsOccupiedDestination() {
        val party = ByteArray(600)
        party[0] = 1
        val pc = ByteArray(0x83D0)
        pc[4 + 10 * 80] = 2
        Gen3PartyEditing.movePartyPokemonToPc(
            party,
            partyCount = 1,
            sourceSlot = 0,
            pc = pc,
            targetGlobalIndex = 10
        )
    }

    @Test
    fun removePartyPokemonCompactsFullRecordsAndPreservesSource() {
        val party = ByteArray(600)
        for (slot in 0 until 4) {
            val offset = slot * 100
            for (index in 0 until 100) {
                party[offset + index] = (slot * 100 + index + 1).toByte()
            }
        }
        val original = party.copyOf()

        val result = Gen3PartyEditing.removePartyPokemon(
            party,
            partyCount = 4,
            sourceSlot = 1
        )

        assertEquals(3, result.partyCount)
        assertArrayEquals(original, party)
        assertArrayEquals(
            original.copyOfRange(0, 100),
            result.partyRecords.copyOfRange(0, 100)
        )
        assertArrayEquals(
            original.copyOfRange(200, 300),
            result.partyRecords.copyOfRange(100, 200)
        )
        assertArrayEquals(
            original.copyOfRange(300, 400),
            result.partyRecords.copyOfRange(200, 300)
        )
        assertTrue(result.partyRecords.copyOfRange(300, 400)
            .all { it == 0.toByte() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun removePartyPokemonRejectsFinalPartyMember() {
        val party = ByteArray(600)
        party[0] = 1
        Gen3PartyEditing.removePartyPokemon(
            party,
            partyCount = 1,
            sourceSlot = 0
        )
    }



    @Test
    fun duplicatePartyPokemonCopiesExactFullRecordAndPreservesSource() {
        val party = ByteArray(600)
        for (slot in 0 until 2) {
            val offset = slot * 100
            for (index in 0 until 100) {
                party[offset + index] = (slot * 100 + index + 1).toByte()
            }
        }
        val original = party.copyOf()

        val result = Gen3PartyEditing.duplicatePartyPokemon(
            party,
            partyCount = 2,
            sourceSlot = 0
        )

        assertEquals(3, result.partyCount)
        assertEquals(2, result.duplicatedSlot)
        assertArrayEquals(original, party)
        assertArrayEquals(
            original.copyOfRange(0, 100),
            result.partyRecords.copyOfRange(200, 300)
        )
        assertTrue(result.partyRecords.indices.all {
            it in 200 until 300 || result.partyRecords[it] == original[it]
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicatePartyPokemonRejectsFullParty() {
        val party = ByteArray(600) { 1 }
        Gen3PartyEditing.duplicatePartyPokemon(
            party,
            partyCount = 6,
            sourceSlot = 0
        )
    }


    @Test
    fun swapPartyPokemonExchangesExactFullRecordsAndPreservesOthers() {
        val party = ByteArray(600)
        for (slot in 0 until 3) {
            val offset = slot * 100
            for (index in 0 until 100) {
                party[offset + index] = (slot * 100 + index + 1).toByte()
            }
        }
        val original = party.copyOf()

        val result = Gen3PartyEditing.swapPartyPokemon(
            party,
            partyCount = 3,
            sourceSlot = 0,
            targetSlot = 2
        )

        assertEquals(0, result.sourceSlot)
        assertEquals(2, result.targetSlot)
        assertArrayEquals(original, party)
        assertArrayEquals(
            original.copyOfRange(200, 300),
            result.partyRecords.copyOfRange(0, 100)
        )
        assertArrayEquals(
            original.copyOfRange(0, 100),
            result.partyRecords.copyOfRange(200, 300)
        )
        assertArrayEquals(
            original.copyOfRange(100, 200),
            result.partyRecords.copyOfRange(100, 200)
        )
        assertTrue(result.partyRecords.indices.all {
            it in 0 until 100 || it in 200 until 300 ||
                result.partyRecords[it] == original[it]
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun swapPartyPokemonRejectsSameSlot() {
        val party = ByteArray(600) { 1 }
        Gen3PartyEditing.swapPartyPokemon(
            party,
            partyCount = 2,
            sourceSlot = 0,
            targetSlot = 0
        )
    }


    @Test
    fun movePcPokemonToPartyPreservesPk3AndBuildsOnlyPartyFields() {
        val party = ByteArray(600)
        for (index in 0 until 100) {
            party[index] = (index + 1).toByte()
        }
        val pc = ByteArray(0x83D0)
        val sourceGlobal = 41
        val sourceOffset = 4 + sourceGlobal * 80
        for (index in 0 until 80) {
            pc[sourceOffset + index] = (index + 11).toByte()
        }
        val originalParty = party.copyOf()
        val originalPc = pc.copyOf()
        val stats = intArrayOf(123, 81, 72, 95, 104, 88)

        val result = Gen3PartyEditing.movePcPokemonToParty(
            party,
            partyCount = 1,
            pc = pc,
            sourceGlobalIndex = sourceGlobal,
            level = 37,
            battleStats = stats
        )

        fun readU16(buffer: ByteArray, offset: Int): Int =
            (buffer[offset].toInt() and 0xFF) or
                ((buffer[offset + 1].toInt() and 0xFF) shl 8)

        assertEquals(2, result.partyCount)
        assertEquals(1, result.insertedSlot)
        assertArrayEquals(originalParty, party)
        assertArrayEquals(originalPc, pc)
        assertArrayEquals(
            originalPc.copyOfRange(sourceOffset, sourceOffset + 80),
            result.partyRecords.copyOfRange(100, 180)
        )
        assertEquals(37, result.partyRecords[100 + 0x54].toInt() and 0xFF)
        assertEquals(stats[0], readU16(result.partyRecords, 100 + 0x56))
        for (index in stats.indices) {
            assertEquals(stats[index], readU16(result.partyRecords, 100 + 0x58 + index * 2))
        }
        assertTrue(result.pc.copyOfRange(sourceOffset, sourceOffset + 80)
            .all { it == 0.toByte() })
        assertTrue(result.pc.indices.all {
            it in sourceOffset until sourceOffset + 80 ||
                result.pc[it] == originalPc[it]
        })
        assertArrayEquals(
            originalParty.copyOfRange(0, 100),
            result.partyRecords.copyOfRange(0, 100)
        )
        assertTrue(result.partyRecords.copyOfRange(200, 600)
            .all { it == 0.toByte() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun movePcPokemonToPartyRejectsFullParty() {
        val party = ByteArray(600) { 1 }
        val pc = ByteArray(0x83D0)
        pc[4] = 1
        Gen3PartyEditing.movePcPokemonToParty(
            party,
            partyCount = 6,
            pc = pc,
            sourceGlobalIndex = 0,
            level = 5,
            battleStats = intArrayOf(20, 10, 10, 10, 10, 10)
        )
    }


    @Test
    fun restorePartyHpAndStatusChangesOnlyPartyRecoveryFields() {
        val record = ByteArray(100) { (it + 1).toByte() }
        record[0x50] = 0x08
        record[0x51] = 0x12
        record[0x52] = 0x34
        record[0x53] = 0x56
        record[0x56] = 25
        record[0x57] = 0
        record[0x58] = 120
        record[0x59] = 0
        val original = record.copyOf()

        val result = Gen3PartyEditing.restorePartyHpAndStatus(record)

        assertEquals(25, result.previousHp)
        assertEquals(120, result.maxHp)
        assertTrue(result.statusWasSet)
        assertArrayEquals(original, record)
        assertTrue(result.partyRecord.copyOfRange(0x50, 0x54)
            .all { it == 0.toByte() })
        assertEquals(120, result.partyRecord[0x56].toInt() and 0xFF)
        assertEquals(0, result.partyRecord[0x57].toInt() and 0xFF)
        assertTrue(result.partyRecord.indices.all {
            it in 0x50 until 0x54 || it in 0x56 until 0x58 ||
                result.partyRecord[it] == original[it]
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun restorePartyHpAndStatusRejectsInvalidHpFields() {
        val record = ByteArray(100) { 1 }
        record[0x56] = 101
        record[0x57] = 0
        record[0x58] = 100
        record[0x59] = 0
        Gen3PartyEditing.restorePartyHpAndStatus(record)
    }



    @Test
    fun movePcPokemonBatchToPartyAppendsInOrderAndClearsExactSources() {
        val party = ByteArray(600)
        for (slot in 0 until 2) {
            for (index in 0 until 100) {
                party[slot * 100 + index] = (slot * 100 + index + 1).toByte()
            }
        }
        val pc = ByteArray(0x83D0)
        val sources = listOf(35, 92)
        sources.forEachIndexed { recordIndex, source ->
            val offset = 4 + source * 80
            for (index in 0 until 80) {
                pc[offset + index] =
                    (recordIndex * 80 + index + 7).toByte()
            }
        }
        val originalParty = party.copyOf()
        val originalPc = pc.copyOf()
        val builds = listOf(
            Gen3PcPartyBuild(
                sources[0],
                24,
                intArrayOf(80, 45, 42, 55, 60, 48)
            ),
            Gen3PcPartyBuild(
                sources[1],
                31,
                intArrayOf(101, 62, 58, 75, 79, 66)
            )
        )

        val result = Gen3PartyEditing.movePcPokemonBatchToParty(
            party,
            partyCount = 2,
            pc = pc,
            items = builds
        )

        assertArrayEquals(originalParty, party)
        assertArrayEquals(originalPc, pc)
        assertEquals(4, result.partyCount)
        assertEquals(sources, result.sourceGlobalIndices)
        assertEquals(listOf(2, 3), result.insertedSlots)
        sources.forEachIndexed { index, source ->
            val sourceOffset = 4 + source * 80
            val partyOffset = (index + 2) * 100
            assertArrayEquals(
                originalPc.copyOfRange(sourceOffset, sourceOffset + 80),
                result.partyRecords.copyOfRange(partyOffset, partyOffset + 80)
            )
            assertTrue(result.pc.copyOfRange(sourceOffset, sourceOffset + 80)
                .all { it == 0.toByte() })
        }
        assertArrayEquals(
            originalParty.copyOfRange(0, 200),
            result.partyRecords.copyOfRange(0, 200)
        )
        assertTrue(result.partyRecords.copyOfRange(400, 600)
            .all { it == 0.toByte() })
        val sourceRanges = sources.map { source ->
            val start = 4 + source * 80
            start until start + 80
        }
        assertTrue(result.pc.indices.all { index ->
            sourceRanges.any { index in it } || result.pc[index] == originalPc[index]
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun movePcPokemonBatchToPartyRejectsDuplicateSources() {
        val party = ByteArray(600)
        party[0] = 1
        val pc = ByteArray(0x83D0)
        pc[4] = 1
        val build = Gen3PcPartyBuild(
            0,
            5,
            intArrayOf(20, 10, 10, 10, 10, 10)
        )
        Gen3PartyEditing.movePcPokemonBatchToParty(
            party,
            partyCount = 1,
            pc = pc,
            items = listOf(build, build)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun movePcPokemonBatchToPartyRejectsCapacityOverflow() {
        val party = ByteArray(600) { 1 }
        val pc = ByteArray(0x83D0)
        pc[4] = 1
        pc[84] = 2
        val stats = intArrayOf(20, 10, 10, 10, 10, 10)
        Gen3PartyEditing.movePcPokemonBatchToParty(
            party,
            partyCount = 5,
            pc = pc,
            items = listOf(
                Gen3PcPartyBuild(0, 5, stats),
                Gen3PcPartyBuild(1, 5, stats)
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun movePcPokemonBatchToPartyRejectsEmptyBatch() {
        val party = ByteArray(600)
        party[0] = 1
        Gen3PartyEditing.movePcPokemonBatchToParty(
            party,
            partyCount = 1,
            pc = ByteArray(0x83D0),
            items = emptyList()
        )
    }

}

