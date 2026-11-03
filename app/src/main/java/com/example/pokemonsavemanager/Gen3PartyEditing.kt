package com.example.pokemonsavemanager

enum class Gen3PartySortField { SPECIES, LEVEL, NAME }

data class Gen3PartySortEntry(
    val sourceSlot: Int,
    val species: Int,
    val level: Int,
    val name: String,
    val isEgg: Boolean
)

data class Gen3PartyToPcResult(
    val partyRecords: ByteArray,
    val partyCount: Int,
    val pc: ByteArray
)

data class Gen3PartyBatchToPcResult(
    val partyRecords: ByteArray,
    val partyCount: Int,
    val pc: ByteArray,
    val sourceSlots: List<Int>,
    val targetSlots: List<Int>
)

data class Gen3PcToPartyResult(
    val partyRecords: ByteArray,
    val partyCount: Int,
    val pc: ByteArray,
    val insertedSlot: Int
)

data class Gen3PcPartyBuild(
    val sourceGlobalIndex: Int,
    val level: Int,
    val battleStats: IntArray
)

data class Gen3StoredPartyBuild(
    val pk3: ByteArray,
    val level: Int,
    val battleStats: IntArray
)

data class Gen3StoredPartyAppendResult(
    val partyRecords: ByteArray,
    val partyCount: Int,
    val insertedSlots: List<Int>
)

data class Gen3PcBatchToPartyResult(
    val partyRecords: ByteArray,
    val partyCount: Int,
    val pc: ByteArray,
    val sourceGlobalIndices: List<Int>,
    val insertedSlots: List<Int>
)

data class Gen3PartyRemovalResult(
    val partyRecords: ByteArray,
    val partyCount: Int
)

data class Gen3PartyDuplicationResult(
    val partyRecords: ByteArray,
    val partyCount: Int,
    val duplicatedSlot: Int
)

data class Gen3PartySwapResult(
    val partyRecords: ByteArray,
    val sourceSlot: Int,
    val targetSlot: Int
)

data class Gen3PartyRecoveryResult(
    val partyRecord: ByteArray,
    val previousHp: Int,
    val maxHp: Int,
    val statusWasSet: Boolean
)

data class Gen3PartyBatchRecoveryResult(
    val partyRecords: ByteArray,
    val changedSlots: List<Int>
)

/** Immutable Gen 3 party-to-PC storage operations. */
object Gen3PartyEditing {
    private const val PARTY_RECORD_SIZE = 100
    private const val STORED_RECORD_SIZE = 80
    private const val PARTY_CAPACITY = 6
    private const val GEN3_PC_BOX_DATA_END =
        4 + Gen3SaveState.BOX_COUNT * Gen3SaveState.BOX_CAPACITY * STORED_RECORD_SIZE

    /** Replaces only raw GBA marking bits; full party extensions and inactive bytes stay exact. */
    fun setSelectedPartyMarkings(
        partyRecords: ByteArray,
        partyCount: Int,
        selectedSlots: Collection<Int>,
        markings: Int
    ): ByteArray {
        require(partyRecords.size >= PARTY_CAPACITY * PARTY_RECORD_SIZE &&
            partyCount in 1..PARTY_CAPACITY) { "The Gen 3 party storage or count is invalid." }
        require(markings in 0..15) { "Gen 3 markings must contain only four marking bits." }
        require(selectedSlots.isNotEmpty() && selectedSlots.size == selectedSlots.toSet().size &&
            selectedSlots.all { it in 0 until partyCount }) { "Select unique active party slots." }
        val slots = selectedSlots.sorted()
        slots.forEach { slot ->
            val offset = slot * PARTY_RECORD_SIZE
            val raw = partyRecords.copyOfRange(offset, offset + STORED_RECORD_SIZE)
            require((raw[0x13].toInt() and 3) == 2) {
                "A selected PK3 is empty or flagged as a Bad Egg."
            }
            Gen3Pk3Codec.decode(raw)
        }
        return partyRecords.copyOf().also { edited ->
            slots.forEach { slot ->
                val offset = slot * PARTY_RECORD_SIZE + 0x1B
                edited[offset] = ((partyRecords[offset].toInt() and 0xF0) or markings).toByte()
            }
        }
    }

    /** Produces a stable permutation; Eggs remain last in their original order. */
    fun sortedPartySlots(
        entries: List<Gen3PartySortEntry>,
        field: Gen3PartySortField,
        descending: Boolean = false
    ): List<Int> {
        require(entries.size in 1..PARTY_CAPACITY &&
            entries.map { it.sourceSlot }.sorted() == entries.indices.toList()) {
            "Sorting requires every active party slot exactly once."
        }
        require(entries.all { it.species in 1..386 && (it.isEgg || it.level in 1..100) }) {
            "A party sort key contains an invalid species or level."
        }
        val values = when (field) {
            Gen3PartySortField.SPECIES -> compareBy<Gen3PartySortEntry> { it.species }
            Gen3PartySortField.LEVEL -> compareBy<Gen3PartySortEntry> { it.level }
            Gen3PartySortField.NAME -> compareBy<Gen3PartySortEntry> {
                it.name.lowercase(java.util.Locale.ROOT)
            }
        }
        return entries.sortedWith(Comparator { first, second ->
            when {
                first.isEgg != second.isEgg -> if (first.isEgg) 1 else -1
                first.isEgg -> first.sourceSlot.compareTo(second.sourceSlot)
                else -> {
                    val compared = if (descending) values.compare(second, first)
                        else values.compare(first, second)
                    if (compared != 0) compared else first.sourceSlot.compareTo(second.sourceSlot)
                }
            }
        }).map { it.sourceSlot }
    }

    /** Reorders complete party records; inactive and extension bytes stay exact. */
    fun reorderPartyPokemon(
        partyRecords: ByteArray,
        partyCount: Int,
        slotOrder: List<Int>
    ): ByteArray {
        require(partyRecords.size >= PARTY_CAPACITY * PARTY_RECORD_SIZE &&
            partyCount in 1..PARTY_CAPACITY) {
            "The Gen 3 party storage or count is invalid."
        }
        require(slotOrder.size == partyCount &&
            slotOrder.toSet() == (0 until partyCount).toSet()) {
            "Choose every active party slot exactly once."
        }
        require((0 until partyCount).all { slot ->
            val start = slot * PARTY_RECORD_SIZE
            partyRecords.copyOfRange(start, start + STORED_RECORD_SIZE).any { it != 0.toByte() }
        }) { "An active party slot is empty." }
        return partyRecords.copyOf().also { reordered ->
            slotOrder.forEachIndexed { target, source ->
                partyRecords.copyInto(reordered, target * PARTY_RECORD_SIZE,
                    source * PARTY_RECORD_SIZE, (source + 1) * PARTY_RECORD_SIZE)
            }
        }
    }

    fun movePartyPokemonBatchToPc(
        partyRecords: ByteArray,
        partyCount: Int,
        selectedSlots: Collection<Int>,
        pc: ByteArray,
        targetBoxIndex: Int
    ): Gen3PartyBatchToPcResult {
        require(partyRecords.size >= PARTY_CAPACITY * PARTY_RECORD_SIZE) {
            "Gen 3 party storage must contain six complete records."
        }
        require(partyCount in 2..PARTY_CAPACITY) {
            "PSE will keep at least one Gen 3 party Pokémon."
        }
        require(selectedSlots.isNotEmpty() &&
            selectedSlots.size == selectedSlots.toSet().size) {
            "Select at least one unique party slot."
        }
        val slots = selectedSlots.sorted()
        require(slots.size < partyCount && slots.all { it in 0 until partyCount }) {
            "Selected party slots must be active and leave one party Pokémon."
        }
        val records = slots.map { slot ->
            val start = slot * PARTY_RECORD_SIZE
            partyRecords.copyOfRange(start, start + STORED_RECORD_SIZE).also { raw ->
                require(raw.any { it != 0.toByte() }) { "A selected party slot is empty." }
            }
        }
        val targetSlots = Gen3BoxEditing.emptyBoxSlots(pc, targetBoxIndex)
            .take(slots.size)
        require(targetSlots.size == slots.size) {
            "The destination box does not have room for the whole party selection."
        }
        val targetRange = Gen3BoxEditing.boxStorageRange(targetBoxIndex)
        val editedPc = pc.copyOf()
        records.forEachIndexed { index, raw ->
            raw.copyInto(
                editedPc,
                targetRange.first + targetSlots[index] * STORED_RECORD_SIZE
            )
        }
        val remaining = (0 until partyCount).filter { it !in slots }
        val compacted = partyRecords.copyOf()
        remaining.forEachIndexed { targetSlot, sourceSlot ->
            partyRecords.copyInto(
                compacted,
                targetSlot * PARTY_RECORD_SIZE,
                sourceSlot * PARTY_RECORD_SIZE,
                (sourceSlot + 1) * PARTY_RECORD_SIZE
            )
        }
        compacted.fill(
            0,
            remaining.size * PARTY_RECORD_SIZE,
            partyCount * PARTY_RECORD_SIZE
        )
        return Gen3PartyBatchToPcResult(
            compacted, remaining.size, editedPc, slots, targetSlots
        )
    }

    /** Copies exact stored PK3 records while preserving every source party byte. */
    fun duplicatePartyPokemonBatchToPc(
        partyRecords: ByteArray,
        partyCount: Int,
        selectedSlots: Collection<Int>,
        pc: ByteArray,
        targetBoxIndex: Int
    ): Gen3PartyBatchToPcResult {
        require(partyRecords.size >= PARTY_CAPACITY * PARTY_RECORD_SIZE) {
            "Gen 3 party storage must contain six complete records."
        }
        require(partyCount in 1..PARTY_CAPACITY) {
            "Gen 3 party count must be between 1 and 6."
        }
        require(selectedSlots.isNotEmpty() &&
            selectedSlots.size == selectedSlots.toSet().size) {
            "Select at least one unique party slot."
        }
        val slots = selectedSlots.sorted()
        require(slots.all { it in 0 until partyCount }) {
            "A selected slot is outside the active party."
        }
        val records = slots.map { slot ->
            val offset = slot * PARTY_RECORD_SIZE
            partyRecords.copyOfRange(offset, offset + STORED_RECORD_SIZE)
        }
        val inserted = Gen3BoxEditing.insertBoxRecords(pc, targetBoxIndex, records)
        return Gen3PartyBatchToPcResult(
            partyRecords.copyOf(), partyCount, inserted.pc, slots, inserted.targetSlots
        )
    }

    /** The caller resolves eligible (non-Egg, checksum-verified) party slots. */
    fun restoreEligiblePartyHpAndStatus(
        partyRecords: ByteArray,
        partyCount: Int,
        eligibleSlots: Collection<Int>,
        selectedSlots: Collection<Int>
    ): Gen3PartyBatchRecoveryResult {
        require(partyCount in 1..PARTY_CAPACITY &&
            eligibleSlots.size == eligibleSlots.toSet().size &&
            eligibleSlots.all { it in 0 until partyCount }) {
            "The eligible party selection is invalid."
        }
        require(selectedSlots.all { it in eligibleSlots }) {
            "A selected party slot is not eligible for recovery."
        }
        return restorePartyBatchHpAndStatus(partyRecords, partyCount, selectedSlots)
    }

    /** Restores selected active records without exposing a partially recovered party. */
    fun restorePartyBatchHpAndStatus(
        partyRecords: ByteArray,
        partyCount: Int,
        selectedSlots: Collection<Int>
    ): Gen3PartyBatchRecoveryResult {
        require(partyRecords.size >= PARTY_CAPACITY * PARTY_RECORD_SIZE) {
            "Gen 3 party storage must contain six complete records."
        }
        require(partyCount in 1..PARTY_CAPACITY) {
            "Gen 3 party count must be between 1 and 6."
        }
        require(selectedSlots.isNotEmpty() &&
            selectedSlots.size == selectedSlots.toSet().size) {
            "Select at least one unique party slot."
        }
        val slots = selectedSlots.sorted()
        require(slots.all { it in 0 until partyCount }) {
            "A selected slot is outside the active party."
        }
        // Validate every selected record before constructing the replacement buffer.
        val recoveries = slots.map { slot ->
            val offset = slot * PARTY_RECORD_SIZE
            restorePartyHpAndStatus(
                partyRecords.copyOfRange(offset, offset + PARTY_RECORD_SIZE)
            )
        }
        val changedSlots = slots.filterIndexed { index, _ ->
            val recovery = recoveries[index]
            recovery.previousHp != recovery.maxHp || recovery.statusWasSet
        }
        val restored = partyRecords.copyOf()
        slots.forEachIndexed { index, slot ->
            recoveries[index].partyRecord.copyInto(
                restored,
                destinationOffset = slot * PARTY_RECORD_SIZE
            )
        }
        return Gen3PartyBatchRecoveryResult(restored, changedSlots)
    }

    fun restorePartyHpAndStatus(
        partyRecord: ByteArray
    ): Gen3PartyRecoveryResult {
        require(partyRecord.size == PARTY_RECORD_SIZE) {
            "A Gen 3 party record must be exactly 100 bytes."
        }
        require(partyRecord.copyOfRange(0, STORED_RECORD_SIZE)
            .any { it != 0.toByte() }) {
            "party slot is empty."
        }
        fun readU16(offset: Int): Int =
            (partyRecord[offset].toInt() and 0xFF) or
                ((partyRecord[offset + 1].toInt() and 0xFF) shl 8)
        fun writeU16(buffer: ByteArray, offset: Int, value: Int) {
            buffer[offset] = (value and 0xFF).toByte()
            buffer[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        }
        val previousHp = readU16(0x56)
        val maxHp = readU16(0x58)
        require(maxHp in 1..0xFFFF && previousHp in 0..maxHp) {
            "party HP fields are invalid."
        }
        val statusWasSet = partyRecord.copyOfRange(0x50, 0x54)
            .any { it != 0.toByte() }
        val restored = partyRecord.copyOf()
        restored.fill(0, 0x50, 0x54)
        writeU16(restored, 0x56, maxHp)
        return Gen3PartyRecoveryResult(
            partyRecord = restored,
            previousHp = previousHp,
            maxHp = maxHp,
            statusWasSet = statusWasSet
        )
    }

    fun movePcPokemonToParty(
        partyRecords: ByteArray,
        partyCount: Int,
        pc: ByteArray,
        sourceGlobalIndex: Int,
        level: Int,
        battleStats: IntArray
    ): Gen3PcToPartyResult {
        require(partyRecords.size >= PARTY_CAPACITY * PARTY_RECORD_SIZE) {
            "Gen 3 party storage must contain six complete records."
        }
        require(partyCount in 1 until PARTY_CAPACITY) {
            "The Gen 3 party is full or has an invalid count."
        }
        require(pc.size >= GEN3_PC_BOX_DATA_END) {
            "Gen 3 PC storage is too short for all 14 boxes."
        }
        val slotCount = Gen3SaveState.BOX_COUNT * Gen3SaveState.BOX_CAPACITY
        require(sourceGlobalIndex in 0 until slotCount) {
            "sourceGlobalIndex is outside the PC."
        }
        require(level in 1..100) {
            "Gen 3 party level must be between 1 and 100."
        }
        require(battleStats.size == 6 && battleStats.all { it in 1..0xFFFF }) {
            "Gen 3 party battle stats must contain six unsigned 16-bit values."
        }

        val sourceOffset = 4 + sourceGlobalIndex * STORED_RECORD_SIZE
        val storedRecord = pc.copyOfRange(
            sourceOffset,
            sourceOffset + STORED_RECORD_SIZE
        )
        require(storedRecord.any { it != 0.toByte() }) {
            "source PC slot is empty."
        }
        val targetOffset = partyCount * PARTY_RECORD_SIZE
        require(partyRecords.copyOfRange(
            targetOffset,
            targetOffset + PARTY_RECORD_SIZE
        ).all { it == 0.toByte() }) {
            "the next party slot is not empty."
        }

        fun writeU16(buffer: ByteArray, offset: Int, value: Int) {
            buffer[offset] = (value and 0xFF).toByte()
            buffer[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        }
        val partyRecord = ByteArray(PARTY_RECORD_SIZE)
        storedRecord.copyInto(partyRecord, destinationOffset = 0)
        partyRecord[0x54] = level.toByte()
        writeU16(partyRecord, 0x56, battleStats[0])
        writeU16(partyRecord, 0x58, battleStats[0])
        writeU16(partyRecord, 0x5A, battleStats[1])
        writeU16(partyRecord, 0x5C, battleStats[2])
        writeU16(partyRecord, 0x5E, battleStats[3])
        writeU16(partyRecord, 0x60, battleStats[4])
        writeU16(partyRecord, 0x62, battleStats[5])

        return Gen3PcToPartyResult(
            partyRecords = partyRecords.copyOf().also {
                partyRecord.copyInto(it, destinationOffset = targetOffset)
            },
            partyCount = partyCount + 1,
            pc = pc.copyOf().also {
                it.fill(0, sourceOffset, sourceOffset + STORED_RECORD_SIZE)
            },
            insertedSlot = partyCount
        )
    }

    /** Reuses the validated PC-to-party builder without modifying a source save. */
    fun appendStoredPokemonBatchToParty(
        partyRecords: ByteArray,
        partyCount: Int,
        items: List<Gen3StoredPartyBuild>
    ): Gen3StoredPartyAppendResult {
        require(partyCount in 1 until PARTY_CAPACITY && items.isNotEmpty() &&
            items.size <= PARTY_CAPACITY - partyCount) {
            "The Gen 3 party does not have room for the complete batch."
        }
        require(items.all { it.pk3.size == STORED_RECORD_SIZE &&
            it.pk3.any { byte -> byte != 0.toByte() } }) {
            "Every stored Pokémon must contain exactly 80 non-empty PK3 bytes."
        }
        val stagingPc = ByteArray(GEN3_PC_BOX_DATA_END)
        val builds = items.mapIndexed { index, item ->
            item.pk3.copyInto(stagingPc, 4 + index * STORED_RECORD_SIZE)
            Gen3PcPartyBuild(index, item.level, item.battleStats.copyOf())
        }
        val appended = movePcPokemonBatchToParty(partyRecords, partyCount, stagingPc, builds)
        return Gen3StoredPartyAppendResult(
            appended.partyRecords, appended.partyCount, appended.insertedSlots
        )
    }

    /** Copies into party while keeping the entire source PC byte-for-byte intact. */
    fun copyPcPokemonBatchToParty(
        partyRecords: ByteArray,
        partyCount: Int,
        pc: ByteArray,
        items: List<Gen3PcPartyBuild>
    ): Gen3PcBatchToPartyResult {
        val appended = movePcPokemonBatchToParty(partyRecords, partyCount, pc, items)
        return appended.copy(pc = pc.copyOf())
    }

    fun movePcPokemonBatchToParty(
        partyRecords: ByteArray,
        partyCount: Int,
        pc: ByteArray,
        items: List<Gen3PcPartyBuild>
    ): Gen3PcBatchToPartyResult {
        require(items.isNotEmpty()) {
            "At least one Gen 3 PC Pokémon must be selected."
        }
        require(items.size <= PARTY_CAPACITY - partyCount) {
            "The Gen 3 party does not have room for the complete batch."
        }
        require(items.map { it.sourceGlobalIndex }.distinct().size == items.size) {
            "Batch source PC slots must be unique."
        }
        var editedParty = partyRecords.copyOf()
        var editedPc = pc.copyOf()
        var editedCount = partyCount
        val insertedSlots = ArrayList<Int>()
        items.forEach { item ->
            val moved = movePcPokemonToParty(
                editedParty,
                editedCount,
                editedPc,
                item.sourceGlobalIndex,
                item.level,
                item.battleStats
            )
            editedParty = moved.partyRecords
            editedPc = moved.pc
            editedCount = moved.partyCount
            insertedSlots.add(moved.insertedSlot)
        }
        return Gen3PcBatchToPartyResult(
            partyRecords = editedParty,
            partyCount = editedCount,
            pc = editedPc,
            sourceGlobalIndices = items.map { it.sourceGlobalIndex },
            insertedSlots = insertedSlots
        )
    }

    fun swapPartyPokemon(
        partyRecords: ByteArray,
        partyCount: Int,
        sourceSlot: Int,
        targetSlot: Int
    ): Gen3PartySwapResult {
        require(partyRecords.size >= PARTY_CAPACITY * PARTY_RECORD_SIZE) {
            "Gen 3 party storage must contain six complete records."
        }
        require(partyCount in 2..PARTY_CAPACITY) {
            "At least two active Gen 3 party Pokémon are required."
        }
        require(sourceSlot in 0 until partyCount) {
            "sourceSlot is outside the active party."
        }
        require(targetSlot in 0 until partyCount) {
            "targetSlot is outside the active party."
        }
        require(sourceSlot != targetSlot) {
            "sourceSlot and targetSlot must be different."
        }
        val sourceOffset = sourceSlot * PARTY_RECORD_SIZE
        val targetOffset = targetSlot * PARTY_RECORD_SIZE
        val sourceRecord = partyRecords.copyOfRange(
            sourceOffset,
            sourceOffset + PARTY_RECORD_SIZE
        )
        val targetRecord = partyRecords.copyOfRange(
            targetOffset,
            targetOffset + PARTY_RECORD_SIZE
        )
        require(sourceRecord.copyOfRange(0, STORED_RECORD_SIZE)
            .any { it != 0.toByte() }) {
            "source party slot is empty."
        }
        require(targetRecord.copyOfRange(0, STORED_RECORD_SIZE)
            .any { it != 0.toByte() }) {
            "target party slot is empty."
        }
        return Gen3PartySwapResult(
            partyRecords = partyRecords.copyOf().also {
                targetRecord.copyInto(it, destinationOffset = sourceOffset)
                sourceRecord.copyInto(it, destinationOffset = targetOffset)
            },
            sourceSlot = sourceSlot,
            targetSlot = targetSlot
        )
    }

    fun duplicatePartyPokemon(
        partyRecords: ByteArray,
        partyCount: Int,
        sourceSlot: Int
    ): Gen3PartyDuplicationResult {
        require(partyRecords.size >= PARTY_CAPACITY * PARTY_RECORD_SIZE) {
            "Gen 3 party storage must contain six complete records."
        }
        require(partyCount in 1 until PARTY_CAPACITY) {
            "The Gen 3 party is full or has an invalid count."
        }
        require(sourceSlot in 0 until partyCount) {
            "sourceSlot is outside the active party."
        }
        val sourceOffset = sourceSlot * PARTY_RECORD_SIZE
        val targetOffset = partyCount * PARTY_RECORD_SIZE
        val sourceRecord = partyRecords.copyOfRange(
            sourceOffset,
            sourceOffset + PARTY_RECORD_SIZE
        )
        require(sourceRecord.copyOfRange(0, STORED_RECORD_SIZE)
            .any { it != 0.toByte() }) {
            "source party slot is empty."
        }
        require(partyRecords.copyOfRange(
            targetOffset,
            targetOffset + PARTY_RECORD_SIZE
        ).all { it == 0.toByte() }) {
            "the next party slot is not empty."
        }
        return Gen3PartyDuplicationResult(
            partyRecords = partyRecords.copyOf().also {
                sourceRecord.copyInto(it, destinationOffset = targetOffset)
            },
            partyCount = partyCount + 1,
            duplicatedSlot = partyCount
        )
    }

    /** Returns detached stored PK3 records in original party-slot order. */
    fun copySelectedPartyRecords(
        partyRecords: ByteArray,
        partyCount: Int,
        selectedSlots: Collection<Int>
    ): List<ByteArray> {
        require(partyRecords.size >= PARTY_CAPACITY * PARTY_RECORD_SIZE) {
            "Gen 3 party storage must contain six complete records."
        }
        require(partyCount in 1..PARTY_CAPACITY) {
            "Gen 3 party count must be between 1 and 6."
        }
        require(selectedSlots.isNotEmpty() &&
            selectedSlots.size == selectedSlots.toSet().size &&
            selectedSlots.all { it in 0 until partyCount }) {
            "Select at least one unique active party slot."
        }
        return selectedSlots.sorted().map { slot ->
            val offset = slot * PARTY_RECORD_SIZE
            partyRecords.copyOfRange(offset, offset + STORED_RECORD_SIZE).also { raw ->
                require(raw.any { it != 0.toByte() }) { "A selected party slot is empty." }
            }
        }
    }

    /** Applies original-slot removals in descending order to keep identities stable. */
    fun removePartyPokemonBatch(
        partyRecords: ByteArray,
        partyCount: Int,
        selectedSlots: Collection<Int>
    ): Gen3PartyRemovalResult {
        require(partyRecords.size >= PARTY_CAPACITY * PARTY_RECORD_SIZE) {
            "Gen 3 party storage must contain six complete records."
        }
        require(partyCount in 2..PARTY_CAPACITY) {
            "PSE will keep at least one Gen 3 party Pokémon."
        }
        require(selectedSlots.isNotEmpty() &&
            selectedSlots.size == selectedSlots.toSet().size &&
            selectedSlots.size < partyCount &&
            selectedSlots.all { it in 0 until partyCount }) {
            "Select unique active party slots and leave at least one party Pokémon."
        }
        var staged = Gen3PartyRemovalResult(partyRecords.copyOf(), partyCount)
        selectedSlots.sortedDescending().forEach { slot ->
            staged = removePartyPokemon(staged.partyRecords, staged.partyCount, slot)
        }
        return staged
    }

    fun removePartyPokemon(
        partyRecords: ByteArray,
        partyCount: Int,
        sourceSlot: Int
    ): Gen3PartyRemovalResult {
        require(partyRecords.size >= PARTY_CAPACITY * PARTY_RECORD_SIZE) {
            "Gen 3 party storage must contain six complete records."
        }
        require(partyCount in 2..PARTY_CAPACITY) {
            "PSE will not remove the final Gen 3 party Pokémon."
        }
        require(sourceSlot in 0 until partyCount) {
            "sourceSlot is outside the active party."
        }
        val sourceOffset = sourceSlot * PARTY_RECORD_SIZE
        require(partyRecords.copyOfRange(
            sourceOffset,
            sourceOffset + STORED_RECORD_SIZE
        ).any { it != 0.toByte() }) {
            "source party slot is empty."
        }

        val compactedParty = partyRecords.copyOf()
        for (slot in sourceSlot until partyCount - 1) {
            partyRecords.copyInto(
                compactedParty,
                destinationOffset = slot * PARTY_RECORD_SIZE,
                startIndex = (slot + 1) * PARTY_RECORD_SIZE,
                endIndex = (slot + 2) * PARTY_RECORD_SIZE
            )
        }
        compactedParty.fill(
            0,
            (partyCount - 1) * PARTY_RECORD_SIZE,
            partyCount * PARTY_RECORD_SIZE
        )
        return Gen3PartyRemovalResult(
            partyRecords = compactedParty,
            partyCount = partyCount - 1
        )
    }

    fun movePartyPokemonToPc(
        partyRecords: ByteArray,
        partyCount: Int,
        sourceSlot: Int,
        pc: ByteArray,
        targetGlobalIndex: Int
    ): Gen3PartyToPcResult {
        require(partyRecords.size >= PARTY_CAPACITY * PARTY_RECORD_SIZE) {
            "Gen 3 party storage must contain six complete records."
        }
        require(partyCount in 1..PARTY_CAPACITY) {
            "Gen 3 party count must be between 1 and 6."
        }
        require(sourceSlot in 0 until partyCount) {
            "sourceSlot is outside the active party."
        }
        require(pc.size >= GEN3_PC_BOX_DATA_END) {
            "Gen 3 PC storage is too short for all 14 boxes."
        }
        val slotCount = Gen3SaveState.BOX_COUNT * Gen3SaveState.BOX_CAPACITY
        require(targetGlobalIndex in 0 until slotCount) {
            "targetGlobalIndex is outside the PC."
        }

        val sourceOffset = sourceSlot * PARTY_RECORD_SIZE
        val storedRecord = partyRecords.copyOfRange(
            sourceOffset,
            sourceOffset + STORED_RECORD_SIZE
        )
        require(storedRecord.any { it != 0.toByte() }) {
            "source party slot is empty."
        }
        val targetOffset = 4 + targetGlobalIndex * STORED_RECORD_SIZE
        require(pc.copyOfRange(targetOffset, targetOffset + STORED_RECORD_SIZE)
            .all { it == 0.toByte() }) {
            "target PC slot is occupied."
        }

        val compactedParty = partyRecords.copyOf()
        for (slot in sourceSlot until partyCount - 1) {
            partyRecords.copyInto(
                compactedParty,
                destinationOffset = slot * PARTY_RECORD_SIZE,
                startIndex = (slot + 1) * PARTY_RECORD_SIZE,
                endIndex = (slot + 2) * PARTY_RECORD_SIZE
            )
        }
        compactedParty.fill(
            0,
            (partyCount - 1) * PARTY_RECORD_SIZE,
            partyCount * PARTY_RECORD_SIZE
        )
        val editedPc = pc.copyOf().also {
            storedRecord.copyInto(it, destinationOffset = targetOffset)
        }
        return Gen3PartyToPcResult(
            partyRecords = compactedParty,
            partyCount = partyCount - 1,
            pc = editedPc
        )
    }
}

