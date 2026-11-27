package com.example.pokemonsavemanager

data class LegendsZaPseBankBatchRestoreItem(
    val pokemon: LegendsZaPokemonRead,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val bankSha256: String
)

/** Restores a reviewed native PA9 batch into distinct empty Legends: Z-A box slots. */
object LegendsZaPseBankBatchRestoreEditing {
    fun stage(
        source: ByteArray,
        expectedSha256: String,
        bankRecords: List<ByteArray>,
        destinations: List<LegendsZaPseBankDestination>
    ): LegendsZaPseBankBatchRestoreCopyPlan {
        val original = source.copyOf()
        val records = bankRecords.map { it.copyOf() }
        require(LegendsZaPseBankRestoreEditing.sha256(original) == expectedSha256) {
            "The source changed. Reopen it from PSE."
        }
        require(records.size >= 2) { "Choose at least two native PA9 Bank entries for a batch restore." }
        require(records.size == destinations.size) { "Each selected Bank entry requires one reviewed destination." }
        require(destinations.distinct().size == destinations.size) { "Every batch destination must be unique." }

        val before = LegendsZaStorageReadOnly.read(original)
        records.forEachIndexed { index, record ->
            LegendsZaPseBankRestoreEditing.inspectBankRecord(record)
            val destination = destinations[index]
            require(destination.box in 1..32 && destination.position in 1..30) {
                "A reviewed Legends: Z-A destination is outside native storage."
            }
            require(before.box(destination.box - 1)[destination.position - 1].read is LegendsZaSlotRead.Empty) {
                "Box ${destination.box}, slot ${destination.position} is no longer empty."
            }
        }

        var edited = original.copyOf()
        val items = records.mapIndexed { index, record ->
            val destination = destinations[index]
            val written = LegendsZaSaveValidation.writeBoxRecord(
                edited, destination.box, destination.position, record
            )
            edited = written.bytes
            LegendsZaPseBankBatchRestoreItem(
                LegendsZaPseBankRestoreEditing.inspectBankRecord(record),
                destination.box,
                destination.position,
                written.absoluteOffset,
                LegendsZaPseBankRestoreEditing.sha256(record)
            )
        }
        verify(original, edited, records, items)
        return LegendsZaPseBankBatchRestoreCopyPlan(original, edited, records, items)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        bankRecords: List<ByteArray>,
        items: List<LegendsZaPseBankBatchRestoreItem>
    ) {
        require(bankRecords.size >= 2 && bankRecords.size == items.size) {
            "The reviewed native PA9 batch is incomplete."
        }
        require(LegendsZaSaveValidation.inspect(original) is LegendsZaValidation.Verified &&
            LegendsZaSaveValidation.inspect(edited) is LegendsZaValidation.Verified) {
            "The source or restored copy is not a validated native Legends: Z-A save."
        }
        val before = LegendsZaStorageReadOnly.read(original)
        val after = LegendsZaStorageReadOnly.read(edited)
        require(before.version == after.version && before.revision == after.revision && original.size == edited.size) {
            "The restored copy changed native title/revision identity or size."
        }
        require(items.map { it.box to it.position }.distinct().size == items.size) {
            "The restored batch reused a destination slot."
        }

        val beforeBlocks = LegendsZaSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsZaSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.party.contentEquals(afterBlocks.party)) {
            "Native party data changed during the batch restore."
        }
        require(LegendsZaSaveValidation.readDexBlock(original).data.contentEquals(
            LegendsZaSaveValidation.readDexBlock(edited).data
        )) { "Native Legends: Z-A Pokédex data changed during a same-title batch restore." }
        val recordSize = LegendsZaPokemonReadOnly.RECORD_SIZE
        val relativeRanges = items.map { item ->
            val start = ((item.box - 1) * 30 + item.position - 1) * LegendsZaStorageReadOnly.BOX_STRIDE
            start until start + recordSize
        }
        require(beforeBlocks.boxes.indices.all { index ->
            relativeRanges.any { index in it } || beforeBlocks.boxes[index] == afterBlocks.boxes[index]
        }) { "An unrelated native PC-box byte changed during the batch restore." }

        items.forEachIndexed { index, item ->
            val record = bankRecords[index]
            require(LegendsZaPseBankRestoreEditing.sha256(record) == item.bankSha256) {
                "A reviewed PSE Bank identity changed."
            }
            require(before.box(item.box - 1)[item.position - 1].read is LegendsZaSlotRead.Empty) {
                "A reviewed destination was not empty in the original save."
            }
            val reopened = after.box(item.box - 1)[item.position - 1].read as? LegendsZaSlotRead.Pokemon
                ?: throw IllegalArgumentException("A restored PA9 did not reopen as a Pokémon.")
            require(reopened.value == LegendsZaPseBankRestoreEditing.inspectBankRecord(record)) {
                "A restored PA9 did not reopen exactly."
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

class LegendsZaPseBankBatchRestoreCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    bankRecords: List<ByteArray>,
    items: List<LegendsZaPseBankBatchRestoreItem>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val bankRecords = bankRecords.map { it.copyOf() }
    val items = items.toList()
    val sourceSha256 = LegendsZaPseBankRestoreEditing.sha256(this.original)
    val editedSha256 = LegendsZaPseBankRestoreEditing.sha256(this.edited)
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
            LegendsZaPseBankBatchRestoreEditing.verify(original, edited, bankRecords, items)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed PSE Bank batch restore."
            }
            LegendsZaPseBankBatchRestoreEditing.verify(original, persisted, bankRecords, items)
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
