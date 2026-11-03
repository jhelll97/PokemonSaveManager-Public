package com.example.pokemonsavemanager

data class Gen3PcSearchEntry(
    val globalIndex: Int,
    val species: Int,
    val speciesName: String,
    val nickname: String,
    val otName: String,
    val isEgg: Boolean = false,
    val shiny: Boolean = false,
    val markingValue: Int = 0
)

data class Gen3PcBoxSummary(
    val boxIndex: Int,
    val occupied: Int,
    val eggs: Int,
    val shiny: Int
) {
    val free: Int get() = Gen3SaveState.BOX_CAPACITY - occupied
}

enum class Gen3BoxSortField { SPECIES, LEVEL, NAME, PACK }

data class Gen3BoxSortEntry(
    val sourceSlot: Int,
    val species: Int,
    val level: Int,
    val name: String,
    val isEgg: Boolean
)

/** Immutable Gen 3 PC box management operations. */
object Gen3BoxEditing {
    private const val GEN3_BOX_RECORD_SIZE = 80
    private const val GEN3_PC_BOX_DATA_END =
        4 + Gen3SaveState.BOX_COUNT * Gen3SaveState.BOX_CAPACITY * GEN3_BOX_RECORD_SIZE

    /** Searches metadata only; required markings use raw GBA bits and must all match. */
    fun searchPcSlots(
        entries: List<Gen3PcSearchEntry>, query: String,
        eggsOnly: Boolean = false, shinyOnly: Boolean = false, boxIndex: Int? = null,
        requiredMarkings: Int = 0
    ): List<Int> {
        val capacity = Gen3SaveState.BOX_COUNT * Gen3SaveState.BOX_CAPACITY
        require(entries.size <= capacity && entries.map { it.globalIndex }.distinct().size == entries.size &&
            entries.all { it.globalIndex in 0 until capacity && it.species in 1..386 &&
                it.markingValue in 0..255 }) {
            "The Gen 3 PC search index is invalid."
        }
        require(query.length <= 100) { "Use at most 100 characters for a PC search." }
        require(boxIndex == null || boxIndex in 0 until Gen3SaveState.BOX_COUNT) {
            "The selected search box is outside the Gen 3 PC."
        }
        require(requiredMarkings in 0..15) { "Choose only the four Gen 3 markings." }
        val normalized = query.trim().lowercase(java.util.Locale.ROOT)
        val dex = normalized.removePrefix("#").toIntOrNull()
        val terms = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return entries.filter { entry ->
            if ((eggsOnly && !entry.isEgg) || (shinyOnly && !entry.shiny) ||
                (boxIndex != null && entry.globalIndex / Gen3SaveState.BOX_CAPACITY != boxIndex) ||
                (entry.markingValue and requiredMarkings) != requiredMarkings) false
            else if (normalized.isEmpty()) true
            else if (dex != null) entry.species == dex
            else {
                val names = listOf(entry.speciesName, entry.nickname, entry.otName,
                    "#${entry.species}").joinToString(" ").lowercase(java.util.Locale.ROOT)
                terms.all { names.contains(it) }
            }
        }.sortedBy { it.globalIndex }.map { it.globalIndex }
    }

    /** Counts all 14 boxes, including empty boxes; Eggs count as occupied slots. */
    fun summarizePcBoxes(entries: List<Gen3PcSearchEntry>): List<Gen3PcBoxSummary> {
        searchPcSlots(entries, "") // Apply the same complete index validation as PC search.
        val byBox = entries.groupBy { it.globalIndex / Gen3SaveState.BOX_CAPACITY }
        return List(Gen3SaveState.BOX_COUNT) { box ->
            val occupants = byBox[box].orEmpty()
            Gen3PcBoxSummary(box, occupants.size, occupants.count { it.isEgg },
                occupants.count { it.shiny })
        }
    }

    /** Sorts stably with Eggs last; PACK preserves all slot order and ignores direction. */
    fun sortedOccupiedSlots(
        entries: List<Gen3BoxSortEntry>,
        field: Gen3BoxSortField,
        descending: Boolean = false
    ): List<Int> {
        require(entries.size <= Gen3SaveState.BOX_CAPACITY &&
            entries.map { it.sourceSlot }.distinct().size == entries.size &&
            entries.all { it.sourceSlot in 0 until Gen3SaveState.BOX_CAPACITY }) {
            "The occupied box slot selection is invalid."
        }
        require(entries.all { it.species in 1..386 && (it.isEgg || it.level in 1..100) }) {
            "A box sort key contains an invalid species or level."
        }
        val values = when (field) {
            Gen3BoxSortField.PACK -> compareBy<Gen3BoxSortEntry> { it.sourceSlot }
            Gen3BoxSortField.SPECIES -> compareBy<Gen3BoxSortEntry> { it.species }
            Gen3BoxSortField.LEVEL -> compareBy<Gen3BoxSortEntry> { it.level }
            Gen3BoxSortField.NAME -> compareBy<Gen3BoxSortEntry> {
                it.name.lowercase(java.util.Locale.ROOT)
            }
        }
        return entries.sortedWith(Comparator { first, second ->
            when {
                field == Gen3BoxSortField.PACK -> first.sourceSlot.compareTo(second.sourceSlot)
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

    fun renameBoxNames(
        boxNames: List<String>,
        boxIndex: Int,
        requestedName: String
    ): List<String> {
        require(boxNames.size == Gen3SaveState.BOX_COUNT) {
            "Gen 3 PC storage must contain exactly 14 box names."
        }
        require(boxIndex in boxNames.indices) { "boxIndex is outside the PC." }
        val name = requestedName.trim()
        require(name.length in 1..8) {
            "Gen 3 PC box names must contain between 1 and 8 characters."
        }
        require(name.all { character ->
            character in 'A'..'Z' ||
                character in 'a'..'z' ||
                character in '0'..'9' ||
                character in " !?.,/'-♂♀"
        }) {
            "Gen 3 PC box name contains unsupported characters."
        }
        return boxNames.toMutableList().also { it[boxIndex] = name }
    }

    fun boxStorageRange(boxIndex: Int): IntRange {
        require(boxIndex in 0 until Gen3SaveState.BOX_COUNT) {
            "boxIndex is outside the PC."
        }
        val start = 4 + boxIndex * Gen3SaveState.BOX_CAPACITY * GEN3_BOX_RECORD_SIZE
        return start until (start + Gen3SaveState.BOX_CAPACITY * GEN3_BOX_RECORD_SIZE)
    }

    fun emptyBoxSlots(pc: ByteArray, boxIndex: Int): List<Int> {
        require(pc.size >= GEN3_PC_BOX_DATA_END) {
            "Gen 3 PC storage is too short for all 14 boxes."
        }
        val boxStart = boxStorageRange(boxIndex).first
        return (0 until Gen3SaveState.BOX_CAPACITY).filter { slot ->
            val offset = boxStart + slot * GEN3_BOX_RECORD_SIZE
            pc.copyOfRange(offset, offset + GEN3_BOX_RECORD_SIZE)
                .all { it == 0.toByte() }
        }
    }

    data class SelectedBoxSnapshot(
        val boxIndex: Int,
        val slots: List<Int>,
        val records: List<ByteArray>
    )

    /**
     * Captures an ordered, detached snapshot for a multi-record persistence flow.
     * The source PC buffer is never exposed or modified.
     */
    fun snapshotSelectedBoxRecords(
        pc: ByteArray,
        boxIndex: Int,
        selectedSlots: Collection<Int>
    ): SelectedBoxSnapshot {
        val slots = selectedSlots.sorted()
        val records = copySelectedBoxRecords(pc, boxIndex, slots)
        return SelectedBoxSnapshot(
            boxIndex = boxIndex,
            slots = slots.toList(),
            records = records.map { it.copyOf() }
        )
    }

    fun copySelectedBoxRecords(
        pc: ByteArray,
        boxIndex: Int,
        selectedSlots: Collection<Int>
    ): List<ByteArray> {
        require(pc.size >= GEN3_PC_BOX_DATA_END) {
            "Gen 3 PC storage is too short for all 14 boxes."
        }
        require(selectedSlots.isNotEmpty()) {
            "At least one PC slot must be selected."
        }
        require(selectedSlots.size == selectedSlots.toSet().size) {
            "Selected PC slots must be unique."
        }
        val range = boxStorageRange(boxIndex)
        val slots = selectedSlots.sorted()
        require(slots.all { it in 0 until Gen3SaveState.BOX_CAPACITY }) {
            "A selected PC slot is outside the box."
        }
        return slots.map { slot ->
            val offset = range.first + slot * GEN3_BOX_RECORD_SIZE
            pc.copyOfRange(offset, offset + GEN3_BOX_RECORD_SIZE).also { record ->
                require(record.any { it != 0.toByte() }) {
                    "A selected PC slot is empty."
                }
            }
        }
    }

    /**
     * Replaces raw GBA markings (Circle, Square, Triangle, Heart) at PK3 header 0x1B.
     * Preserves reserved header bits and the complete checksum-protected encrypted data.
     * Layout reference: https://github.com/kwsch/PKHeX/blob/master/PKHeX.Core/PKM/PK3.cs
     */
    fun setSelectedBoxMarkings(
        pc: ByteArray,
        boxIndex: Int,
        selectedSlots: Collection<Int>,
        markings: Int
    ): ByteArray {
        require(markings in 0..15) { "Gen 3 markings must contain only four marking bits." }
        val snapshot = snapshotSelectedBoxRecords(pc, boxIndex, selectedSlots)
        snapshot.records.forEach { record ->
            require((record[0x13].toInt() and 3) == 2) {
                "A selected PK3 is empty or flagged as a Bad Egg."
            }
            Gen3Pk3Codec.decode(record)
        }
        val start = boxStorageRange(boxIndex).first
        return pc.copyOf().also { edited ->
            snapshot.slots.forEach { slot ->
                val offset = start + slot * GEN3_BOX_RECORD_SIZE + 0x1B
                edited[offset] = ((pc[offset].toInt() and 0xF0) or markings).toByte()
            }
        }
    }

    fun clearSelectedBoxSlots(
        pc: ByteArray,
        boxIndex: Int,
        selectedSlots: Collection<Int>
    ): ByteArray {
        copySelectedBoxRecords(pc, boxIndex, selectedSlots)
        val range = boxStorageRange(boxIndex)
        return pc.copyOf().also { cleared ->
            selectedSlots.sorted().forEach { slot ->
                val offset = range.first + slot * GEN3_BOX_RECORD_SIZE
                cleared.fill(0, offset, offset + GEN3_BOX_RECORD_SIZE)
            }
        }
    }

    data class BatchMoveResult(
        val pc: ByteArray,
        val sourceSlots: List<Int>,
        val targetSlots: List<Int>
    )

    fun moveSelectedBoxRecords(
        pc: ByteArray,
        sourceBoxIndex: Int,
        selectedSlots: Collection<Int>,
        targetBoxIndex: Int
    ): BatchMoveResult {
        require(sourceBoxIndex != targetBoxIndex) {
            "Source and destination boxes must be different."
        }
        val sourceSlots = selectedSlots.sorted()
        val records = copySelectedBoxRecords(pc, sourceBoxIndex, sourceSlots)
        val targetSlots = emptyBoxSlots(pc, targetBoxIndex).take(records.size)
        require(targetSlots.size == records.size) {
            "The destination box does not have enough empty slots."
        }
        val sourceRange = boxStorageRange(sourceBoxIndex)
        val targetRange = boxStorageRange(targetBoxIndex)
        val moved = pc.copyOf()
        sourceSlots.forEach { slot ->
            val offset = sourceRange.first + slot * GEN3_BOX_RECORD_SIZE
            moved.fill(0, offset, offset + GEN3_BOX_RECORD_SIZE)
        }
        records.forEachIndexed { index, record ->
            record.copyInto(
                moved,
                destinationOffset = targetRange.first +
                    targetSlots[index] * GEN3_BOX_RECORD_SIZE
            )
        }
        return BatchMoveResult(
            pc = moved,
            sourceSlots = sourceSlots,
            targetSlots = targetSlots
        )
    }

    data class BatchInsertResult(
        val pc: ByteArray,
        val targetSlots: List<Int>
    )

    fun insertBoxRecords(
        pc: ByteArray,
        boxIndex: Int,
        records: List<ByteArray>
    ): BatchInsertResult {
        require(records.isNotEmpty()) {
            "At least one PK3 record must be provided."
        }
        require(records.all { record ->
            record.size == GEN3_BOX_RECORD_SIZE &&
                record.any { it != 0.toByte() }
        }) {
            "Every inserted PK3 record must contain exactly 80 non-empty bytes."
        }
        val targetSlots = emptyBoxSlots(pc, boxIndex).take(records.size)
        require(targetSlots.size == records.size) {
            "The destination box does not have enough empty slots."
        }
        val range = boxStorageRange(boxIndex)
        val inserted = pc.copyOf()
        records.forEachIndexed { index, record ->
            record.copyInto(
                inserted,
                destinationOffset = range.first +
                    targetSlots[index] * GEN3_BOX_RECORD_SIZE
            )
        }
        return BatchInsertResult(inserted, targetSlots)
    }

    data class BatchDuplicateResult(
        val pc: ByteArray,
        val sourceSlots: List<Int>,
        val targetSlots: List<Int>
    )

    fun duplicateSelectedBoxRecords(
        pc: ByteArray,
        sourceBoxIndex: Int,
        selectedSlots: Collection<Int>,
        targetBoxIndex: Int
    ): BatchDuplicateResult {
        require(sourceBoxIndex != targetBoxIndex) {
            "Source and destination boxes must be different."
        }
        val sourceSlots = selectedSlots.sorted()
        val records = copySelectedBoxRecords(pc, sourceBoxIndex, sourceSlots)
        val inserted = insertBoxRecords(pc, targetBoxIndex, records)
        return BatchDuplicateResult(
            pc = inserted.pc,
            sourceSlots = sourceSlots,
            targetSlots = inserted.targetSlots
        )
    }

    fun clearBoxStorage(pc: ByteArray, boxIndex: Int): ByteArray {
        require(pc.size >= GEN3_PC_BOX_DATA_END) {
            "Gen 3 PC storage is too short for all 14 boxes."
        }
        val range = boxStorageRange(boxIndex)
        return pc.copyOf().also { cleared ->
            cleared.fill(0, range.first, range.last + 1)
        }
    }

    fun swapBoxStorage(
        pc: ByteArray,
        firstBoxIndex: Int,
        secondBoxIndex: Int
    ): ByteArray {
        require(pc.size >= GEN3_PC_BOX_DATA_END) {
            "Gen 3 PC storage is too short for all 14 boxes."
        }
        val firstRange = boxStorageRange(firstBoxIndex)
        val secondRange = boxStorageRange(secondBoxIndex)
        if (firstBoxIndex == secondBoxIndex) return pc.copyOf()

        val firstBytes = pc.copyOfRange(firstRange.first, firstRange.last + 1)
        return pc.copyOf().also { swapped ->
            pc.copyInto(
                swapped,
                destinationOffset = firstRange.first,
                startIndex = secondRange.first,
                endIndex = secondRange.last + 1
            )
            firstBytes.copyInto(swapped, destinationOffset = secondRange.first)
        }
    }

    fun movePokemonStorage(
        pc: ByteArray,
        sourceGlobalIndex: Int,
        targetGlobalIndex: Int
    ): ByteArray {
        require(pc.size >= GEN3_PC_BOX_DATA_END) {
            "Gen 3 PC storage is too short for all 14 boxes."
        }
        val slotCount = Gen3SaveState.BOX_COUNT * Gen3SaveState.BOX_CAPACITY
        require(sourceGlobalIndex in 0 until slotCount) {
            "sourceGlobalIndex is outside the PC."
        }
        require(targetGlobalIndex in 0 until slotCount) {
            "targetGlobalIndex is outside the PC."
        }
        require(sourceGlobalIndex != targetGlobalIndex) {
            "source and target PC slots must be different."
        }
        val sourceOffset = 4 + sourceGlobalIndex * GEN3_BOX_RECORD_SIZE
        val targetOffset = 4 + targetGlobalIndex * GEN3_BOX_RECORD_SIZE
        val source = pc.copyOfRange(sourceOffset, sourceOffset + GEN3_BOX_RECORD_SIZE)
        require(source.any { it != 0.toByte() }) {
            "source PC slot is empty."
        }
        require(pc.copyOfRange(targetOffset, targetOffset + GEN3_BOX_RECORD_SIZE)
            .all { it == 0.toByte() }) {
            "target PC slot is occupied."
        }
        return pc.copyOf().also { moved ->
            source.copyInto(moved, destinationOffset = targetOffset)
            moved.fill(0, sourceOffset, sourceOffset + GEN3_BOX_RECORD_SIZE)
        }
    }

    fun swapPokemonStorage(
        pc: ByteArray,
        firstGlobalIndex: Int,
        secondGlobalIndex: Int
    ): ByteArray {
        require(pc.size >= GEN3_PC_BOX_DATA_END) {
            "Gen 3 PC storage is too short for all 14 boxes."
        }
        val slotCount = Gen3SaveState.BOX_COUNT * Gen3SaveState.BOX_CAPACITY
        require(firstGlobalIndex in 0 until slotCount) {
            "firstGlobalIndex is outside the PC."
        }
        require(secondGlobalIndex in 0 until slotCount) {
            "secondGlobalIndex is outside the PC."
        }
        require(firstGlobalIndex != secondGlobalIndex) {
            "PC slots to swap must be different."
        }
        val firstOffset = 4 + firstGlobalIndex * GEN3_BOX_RECORD_SIZE
        val secondOffset = 4 + secondGlobalIndex * GEN3_BOX_RECORD_SIZE
        val first = pc.copyOfRange(firstOffset, firstOffset + GEN3_BOX_RECORD_SIZE)
        val second = pc.copyOfRange(secondOffset, secondOffset + GEN3_BOX_RECORD_SIZE)
        require(first.any { it != 0.toByte() }) {
            "first PC slot is empty."
        }
        require(second.any { it != 0.toByte() }) {
            "second PC slot is empty."
        }
        return pc.copyOf().also { swapped ->
            second.copyInto(swapped, destinationOffset = firstOffset)
            first.copyInto(swapped, destinationOffset = secondOffset)
        }
    }

    fun reorderBoxStorage(
        pc: ByteArray,
        boxIndex: Int,
        orderedOccupiedSlots: List<Int>
    ): ByteArray {
        require(pc.size >= GEN3_PC_BOX_DATA_END) {
            "Gen 3 PC storage is too short for all 14 boxes."
        }
        val range = boxStorageRange(boxIndex)
        val occupiedSlots = (0 until Gen3SaveState.BOX_CAPACITY).filter { slot ->
            val offset = range.first + slot * GEN3_BOX_RECORD_SIZE
            pc.copyOfRange(offset, offset + GEN3_BOX_RECORD_SIZE)
                .any { it != 0.toByte() }
        }
        require(orderedOccupiedSlots.size == occupiedSlots.size &&
            orderedOccupiedSlots.toSet() == occupiedSlots.toSet()) {
            "The requested order must contain every occupied slot exactly once."
        }
        val records = orderedOccupiedSlots.map { slot ->
            val offset = range.first + slot * GEN3_BOX_RECORD_SIZE
            pc.copyOfRange(offset, offset + GEN3_BOX_RECORD_SIZE)
        }
        return pc.copyOf().also { reordered ->
            reordered.fill(0, range.first, range.last + 1)
            records.forEachIndexed { destinationSlot, record ->
                record.copyInto(
                    reordered,
                    destinationOffset = range.first +
                        destinationSlot * GEN3_BOX_RECORD_SIZE
                )
            }
        }
    }

    fun clearBox(
        state: Gen3SaveState,
        boxIndex: Int
    ): Gen3SaveState {
        require(boxIndex in state.boxes.indices) { "boxIndex is outside the PC." }
        val newBoxes = state.boxes.toMutableList()
        newBoxes[boxIndex] = emptyList()
        return state.copy(boxes = newBoxes)
    }

    fun swapBoxes(
        state: Gen3SaveState,
        firstBoxIndex: Int,
        secondBoxIndex: Int
    ): Gen3SaveState {
        require(firstBoxIndex in state.boxes.indices) {
            "firstBoxIndex is outside the PC."
        }
        require(secondBoxIndex in state.boxes.indices) {
            "secondBoxIndex is outside the PC."
        }
        if (firstBoxIndex == secondBoxIndex) return state

        val newBoxes = state.boxes.toMutableList()
        val first = newBoxes[firstBoxIndex]
        newBoxes[firstBoxIndex] = newBoxes[secondBoxIndex]
        newBoxes[secondBoxIndex] = first
        return state.copy(boxes = newBoxes)
    }

    fun replaceBoxContents(
        state: Gen3SaveState,
        boxIndex: Int,
        pokemon: List<Gen3PokemonRecord>
    ): Gen3SaveState {
        require(boxIndex in state.boxes.indices) { "boxIndex is outside the PC." }
        require(pokemon.size <= Gen3SaveState.BOX_CAPACITY) {
            "a Gen 3 PC box cannot contain more than 30 Pokémon."
        }
        pokemon.forEach { it.requireValid() }
        val newBoxes = state.boxes.toMutableList()
        newBoxes[boxIndex] = pokemon.toList()
        return state.copy(boxes = newBoxes)
    }
}
