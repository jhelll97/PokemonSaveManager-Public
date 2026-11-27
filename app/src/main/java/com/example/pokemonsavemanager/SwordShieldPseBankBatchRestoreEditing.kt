package com.example.pokemonsavemanager

data class SwordShieldPseBankBatchRestoreItem(
    val pokemon: SwordShieldPokemonRead,
    val box: Int,
    val position: Int,
    val absoluteOffset: Int,
    val bankSha256: String
)

/** Restores a reviewed native PK8 batch into distinct empty Sword/Shield box slots. */
object SwordShieldPseBankBatchRestoreEditing {
    fun stage(
        source: ByteArray,
        expectedSha256: String,
        bankRecords: List<ByteArray>,
        destinations: List<SwordShieldPseBankDestination>
    ): SwordShieldPseBankBatchRestoreCopyPlan {
        val original = source.copyOf()
        val records = bankRecords.map { it.copyOf() }
        require(SwordShieldPseBankRestoreEditing.sha256(original) == expectedSha256) {
            "The source changed. Reopen it from PSE."
        }
        require(records.size >= 2) { "Choose at least two native PK8 Bank entries for a batch restore." }
        require(records.size == destinations.size) { "Each selected Bank entry requires one reviewed destination." }
        require(destinations.distinct().size == destinations.size) { "Every batch destination must be unique." }

        val before = SwordShieldStorageReadOnly.read(original)
        records.forEachIndexed { index, record ->
            SwordShieldPseBankRestoreEditing.inspectBankRecord(record)
            val destination = destinations[index]
            require(destination.box in 1..32 && destination.position in 1..30) {
                "A reviewed Sword/Shield destination is outside native storage."
            }
            require(before.box(destination.box - 1)[destination.position - 1].read is SwordShieldSlotRead.Empty) {
                "Box ${destination.box}, slot ${destination.position} is no longer empty."
            }
        }

        var edited = original.copyOf()
        val items = records.mapIndexed { index, record ->
            val destination = destinations[index]
            val written = SwordShieldSaveValidation.writeBoxRecord(
                edited, destination.box, destination.position, record
            )
            edited = written.bytes
            SwordShieldPseBankBatchRestoreItem(
                SwordShieldPseBankRestoreEditing.inspectBankRecord(record),
                destination.box,
                destination.position,
                written.absoluteOffset,
                SwordShieldPseBankRestoreEditing.sha256(record)
            )
        }
        verify(original, edited, records, items)
        return SwordShieldPseBankBatchRestoreCopyPlan(original, edited, records, items)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        bankRecords: List<ByteArray>,
        items: List<SwordShieldPseBankBatchRestoreItem>
    ) {
        require(bankRecords.size >= 2 && bankRecords.size == items.size) {
            "The reviewed native PK8 batch is incomplete."
        }
        require(SwordShieldSaveValidation.inspect(original) is SwordShieldValidation.Verified &&
            SwordShieldSaveValidation.inspect(edited) is SwordShieldValidation.Verified) {
            "The source or restored copy is not a validated native Sword/Shield save."
        }
        val before = SwordShieldStorageReadOnly.read(original)
        val after = SwordShieldStorageReadOnly.read(edited)
        require(before.version == after.version && original.size == edited.size) {
            "The restored copy changed native title identity or size."
        }
        require(items.map { it.box to it.position }.distinct().size == items.size) {
            "The restored batch reused a destination slot."
        }

        val beforeBlocks = SwordShieldSaveValidation.readPokemonBlocks(original)
        val afterBlocks = SwordShieldSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.party.contentEquals(afterBlocks.party)) {
            "Native party data changed during the batch restore."
        }
        val recordSize = SwordShieldPokemonReadOnly.RECORD_SIZE
        val relativeRanges = items.map { item ->
            val start = ((item.box - 1) * 30 + item.position - 1) * recordSize
            start until start + recordSize
        }
        require(beforeBlocks.boxes.indices.all { index ->
            relativeRanges.any { index in it } || beforeBlocks.boxes[index] == afterBlocks.boxes[index]
        }) { "An unrelated native PC-box byte changed during the batch restore." }

        items.forEachIndexed { index, item ->
            val record = bankRecords[index]
            require(SwordShieldPseBankRestoreEditing.sha256(record) == item.bankSha256) {
                "A reviewed PSE Bank identity changed."
            }
            require(before.box(item.box - 1)[item.position - 1].read is SwordShieldSlotRead.Empty) {
                "A reviewed destination was not empty in the original save."
            }
            val reopened = after.box(item.box - 1)[item.position - 1].read as? SwordShieldSlotRead.Pokemon
                ?: throw IllegalArgumentException("A restored PK8 did not reopen as a Pokémon.")
            require(reopened.value == SwordShieldPseBankRestoreEditing.inspectBankRecord(record)) {
                "A restored PK8 did not reopen exactly."
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

class SwordShieldPseBankBatchRestoreCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    bankRecords: List<ByteArray>,
    items: List<SwordShieldPseBankBatchRestoreItem>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val bankRecords = bankRecords.map { it.copyOf() }
    val items = items.toList()
    val sourceSha256 = SwordShieldPseBankRestoreEditing.sha256(this.original)
    val editedSha256 = SwordShieldPseBankRestoreEditing.sha256(this.edited)
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
            SwordShieldPseBankBatchRestoreEditing.verify(original, edited, bankRecords, items)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed PSE Bank batch restore."
            }
            SwordShieldPseBankBatchRestoreEditing.verify(original, persisted, bankRecords, items)
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
