package com.example.pokemonsavemanager

data class ScarletVioletPseBankBatchRestoreItem(
    val pokemon: ScarletVioletPokemonRead,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val bankSha256: String
)

/** Restores a reviewed native PK9 batch into distinct empty Scarlet/Violet box slots. */
object ScarletVioletPseBankBatchRestoreEditing {
    fun stage(
        source: ByteArray,
        expectedSha256: String,
        bankRecords: List<ByteArray>,
        destinations: List<ScarletVioletPseBankDestination>
    ): ScarletVioletPseBankBatchRestoreCopyPlan {
        val original = source.copyOf()
        val records = bankRecords.map { it.copyOf() }
        require(ScarletVioletPseBankRestoreEditing.sha256(original) == expectedSha256) {
            "The source changed. Reopen it from PSE."
        }
        require(records.size >= 2) { "Choose at least two native PK9 Bank entries for a batch restore." }
        require(records.size == destinations.size) { "Each selected Bank entry requires one reviewed destination." }
        require(destinations.distinct().size == destinations.size) { "Every batch destination must be unique." }

        val before = ScarletVioletStorageReadOnly.read(original)
        records.forEachIndexed { index, record ->
            ScarletVioletPseBankRestoreEditing.inspectBankRecord(record)
            val destination = destinations[index]
            require(destination.box in 1..32 && destination.position in 1..30) {
                "A reviewed Scarlet/Violet destination is outside native storage."
            }
            require(before.box(destination.box - 1)[destination.position - 1].read is ScarletVioletSlotRead.Empty) {
                "Box ${destination.box}, slot ${destination.position} is no longer empty."
            }
        }

        var edited = original.copyOf()
        val items = records.mapIndexed { index, record ->
            val destination = destinations[index]
            val written = ScarletVioletSaveValidation.writeBoxRecord(
                edited, destination.box, destination.position, record
            )
            edited = written.bytes
            ScarletVioletPseBankBatchRestoreItem(
                ScarletVioletPseBankRestoreEditing.inspectBankRecord(record),
                destination.box,
                destination.position,
                written.absoluteOffset,
                ScarletVioletPseBankRestoreEditing.sha256(record)
            )
        }
        verify(original, edited, records, items)
        return ScarletVioletPseBankBatchRestoreCopyPlan(original, edited, records, items)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        bankRecords: List<ByteArray>,
        items: List<ScarletVioletPseBankBatchRestoreItem>
    ) {
        require(bankRecords.size >= 2 && bankRecords.size == items.size) {
            "The reviewed native PK9 batch is incomplete."
        }
        require(ScarletVioletSaveValidation.inspect(original) is ScarletVioletValidation.Verified &&
            ScarletVioletSaveValidation.inspect(edited) is ScarletVioletValidation.Verified) {
            "The source or restored copy is not a validated native Scarlet/Violet save."
        }
        val before = ScarletVioletStorageReadOnly.read(original)
        val after = ScarletVioletStorageReadOnly.read(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The restored copy changed native title identity or size."
        }
        require(before.rideLegend == after.rideLegend) {
            "The native ride Pokémon changed during the batch restore."
        }
        require(items.map { it.box to it.position }.distinct().size == items.size) {
            "The restored batch reused a destination slot."
        }

        val beforeBlocks = ScarletVioletSaveValidation.readPokemonBlocks(original)
        val afterBlocks = ScarletVioletSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.party.contentEquals(afterBlocks.party)) {
            "Native party data changed during the batch restore."
        }
        val beforeDex = ScarletVioletSaveValidation.readDexBlocks(original)
        val afterDex = ScarletVioletSaveValidation.readDexBlocks(edited)
        val sameDlc = when {
            beforeDex.dlc == null && afterDex.dlc == null -> true
            beforeDex.dlc == null || afterDex.dlc == null -> false
            else -> beforeDex.dlc.contentEquals(afterDex.dlc)
        }
        require(beforeDex.original.contentEquals(afterDex.original) && sameDlc) {
            "Native Scarlet/Violet Pokédex data changed during a same-title batch restore."
        }
        val recordSize = ScarletVioletPokemonReadOnly.RECORD_SIZE
        val relativeRanges = items.map { item ->
            val start = ((item.box - 1) * 30 + item.position - 1) * recordSize
            start until start + recordSize
        }
        require(beforeBlocks.boxes.indices.all { index ->
            relativeRanges.any { index in it } || beforeBlocks.boxes[index] == afterBlocks.boxes[index]
        }) { "An unrelated native PC-box byte changed during the batch restore." }

        items.forEachIndexed { index, item ->
            val record = bankRecords[index]
            require(ScarletVioletPseBankRestoreEditing.sha256(record) == item.bankSha256) {
                "A reviewed PSE Bank identity changed."
            }
            require(before.box(item.box - 1)[item.position - 1].read is ScarletVioletSlotRead.Empty) {
                "A reviewed destination was not empty in the original save."
            }
            val reopened = after.box(item.box - 1)[item.position - 1].read as? ScarletVioletSlotRead.Pokemon
                ?: throw IllegalArgumentException("A restored PK9 did not reopen as a Pokémon.")
            require(reopened.value == ScarletVioletPseBankRestoreEditing.inspectBankRecord(record)) {
                "A restored PK9 did not reopen exactly."
            }
            val relative = relativeRanges[index]
            require(afterBlocks.boxes.copyOfRange(relative.first, relative.last + 1).contentEquals(record)) {
                "The native PSE Bank bytes were not preserved exactly."
            }
        }

        val absoluteRanges = items.map { it.absoluteOffset until it.absoluteOffset + recordSize }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            absoluteRanges.any { index in it } || index in hashOffset until original.size ||
                original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed during the batch restore." }
    }
}

class ScarletVioletPseBankBatchRestoreCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    bankRecords: List<ByteArray>,
    items: List<ScarletVioletPseBankBatchRestoreItem>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val bankRecords = bankRecords.map { it.copyOf() }
    val items = items.toList()
    val sourceSha256 = ScarletVioletPseBankRestoreEditing.sha256(this.original)
    val editedSha256 = ScarletVioletPseBankRestoreEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        readBankRecords: () -> List<ByteArray>,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This PSE Bank batch review was already used. Reopen PSE Bank." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        fun requireBank() {
            val current = readBankRecords()
            require(current.size == bankRecords.size && current.indices.all {
                current[it].contentEquals(bankRecords[it])
            }) { "A selected PSE Bank entry changed. Reopen PSE Bank." }
        }
        requireSource()
        requireBank()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        require(retainBackup(original.copyOf()).contentEquals(original)) {
            "The recovery backup did not verify. Nothing was written."
        }
        requireSource()
        requireBank()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            ScarletVioletPseBankBatchRestoreEditing.verify(original, edited, bankRecords, items)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed PSE Bank batch restore."
            }
            ScarletVioletPseBankBatchRestoreEditing.verify(original, persisted, bankRecords, items)
            requireSource()
            requireBank()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "PSE Bank batch restore verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
