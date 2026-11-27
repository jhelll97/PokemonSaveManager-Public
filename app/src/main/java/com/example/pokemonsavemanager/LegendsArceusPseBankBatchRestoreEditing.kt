package com.example.pokemonsavemanager

data class LegendsArceusPseBankBatchRestoreItem(
    val pokemon: LegendsArceusPokemonRead,
    val pasture: Int,
    val position: Int,
    val absoluteOffset: Int,
    val bankSha256: String,
    val partyTailBytes: Int
)

/** Restores a reviewed native PA8 batch into distinct empty Legends: Arceus pasture slots. */
object LegendsArceusPseBankBatchRestoreEditing {
    private const val FOOTER_SIZE = 32

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        bankRecords: List<ByteArray>,
        destinations: List<LegendsArceusPseBankDestination>
    ): LegendsArceusPseBankBatchRestoreCopyPlan {
        val original = source.copyOf()
        val records = bankRecords.map { it.copyOf() }
        require(LegendsArceusPseBankRestoreEditing.sha256(original) == expectedSha256) {
            "The source changed. Reopen it from PSE."
        }
        require(records.size >= 2) { "Choose at least two native PA8 Bank entries for a batch restore." }
        require(records.size == destinations.size) { "Each selected Bank entry requires one reviewed destination." }
        require(destinations.distinct().size == destinations.size) { "Every batch destination must be unique." }

        val before = LegendsArceusStorageReadOnly.read(original)
        records.forEachIndexed { index, record ->
            LegendsArceusPseBankRestoreEditing.inspectBankRecord(record)
            val destination = destinations[index]
            require(destination.pasture in 1..32 && destination.position in 1..30) {
                "A reviewed Legends: Arceus destination is outside native storage."
            }
            require(before.pasture(destination.pasture - 1)[destination.position - 1].read is LegendsArceusSlotRead.Empty) {
                "Pasture ${destination.pasture}, slot ${destination.position} is no longer empty."
            }
        }

        var edited = original.copyOf()
        val storedRecords = records.map {
            it.copyOfRange(0, LegendsArceusPokemonReadOnly.STORED_SIZE).also { core ->
                LegendsArceusPseBankRestoreEditing.inspectBankRecord(core)
            }
        }
        val items = records.mapIndexed { index, record ->
            val destination = destinations[index]
            val written = LegendsArceusSaveValidation.writePastureRecord(
                edited, destination.pasture, destination.position, storedRecords[index]
            )
            edited = written.bytes
            LegendsArceusPseBankBatchRestoreItem(
                LegendsArceusPseBankRestoreEditing.inspectBankRecord(record),
                destination.pasture,
                destination.position,
                written.absoluteOffset,
                LegendsArceusPseBankRestoreEditing.sha256(record),
                record.size - LegendsArceusPokemonReadOnly.STORED_SIZE
            )
        }
        verify(original, edited, records, storedRecords, items)
        return LegendsArceusPseBankBatchRestoreCopyPlan(
            original, edited, records, storedRecords, items
        )
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        bankRecords: List<ByteArray>,
        storedRecords: List<ByteArray>,
        items: List<LegendsArceusPseBankBatchRestoreItem>
    ) {
        require(bankRecords.size >= 2 && bankRecords.size == storedRecords.size &&
            bankRecords.size == items.size) { "The reviewed native PA8 batch is incomplete." }
        require(LegendsArceusSaveValidation.inspect(original) is LegendsArceusValidation.Verified &&
            LegendsArceusSaveValidation.inspect(edited) is LegendsArceusValidation.Verified) {
            "The source or restored copy is not a validated native Legends: Arceus save."
        }
        val before = LegendsArceusStorageReadOnly.read(original)
        val after = LegendsArceusStorageReadOnly.read(edited)
        require(original.size == edited.size && before.version == after.version) {
            "The restored copy changed native title identity or size."
        }
        require(items.map { it.pasture to it.position }.distinct().size == items.size) {
            "The restored batch reused a destination slot."
        }

        val beforeBlocks = LegendsArceusSaveValidation.readPokemonBlocks(original)
        val afterBlocks = LegendsArceusSaveValidation.readPokemonBlocks(edited)
        require(beforeBlocks.party.contentEquals(afterBlocks.party)) {
            "Native party data changed during the batch restore."
        }
        val storedSize = LegendsArceusPokemonReadOnly.STORED_SIZE
        val relativeRanges = items.map { item ->
            val start = ((item.pasture - 1) * 30 + item.position - 1) * storedSize
            start until start + storedSize
        }
        require(beforeBlocks.pastures.indices.all { index ->
            relativeRanges.any { index in it } ||
                beforeBlocks.pastures[index] == afterBlocks.pastures[index]
        }) { "An unrelated native pasture byte changed during the batch restore." }
        require(LegendsArceusSaveValidation.readDexBlock(original).data.contentEquals(
            LegendsArceusSaveValidation.readDexBlock(edited).data
        )) { "Native Hisui Pokédex data changed during a same-title batch restore." }

        items.forEachIndexed { index, item ->
            val bankRecord = bankRecords[index]
            val storedRecord = storedRecords[index]
            require(LegendsArceusPseBankRestoreEditing.sha256(bankRecord) == item.bankSha256) {
                "A reviewed PSE Bank identity changed."
            }
            require(item.partyTailBytes == bankRecord.size - storedRecord.size) {
                "A reviewed PA8 representation changed."
            }
            require(before.pasture(item.pasture - 1)[item.position - 1].read is LegendsArceusSlotRead.Empty) {
                "A reviewed destination was not empty in the original save."
            }
            val reopened = after.pasture(item.pasture - 1)[item.position - 1].read as? LegendsArceusSlotRead.Pokemon
                ?: throw IllegalArgumentException("A restored PA8 did not reopen as a Pokémon.")
            require(reopened.value == LegendsArceusPseBankRestoreEditing.inspectBankRecord(storedRecord)) {
                "A restored PA8 did not reopen exactly."
            }
            val relative = relativeRanges[index]
            require(afterBlocks.pastures.copyOfRange(relative.first, relative.last + 1)
                .contentEquals(storedRecord)) { "The native PA8 stored core was not preserved exactly." }
        }

        val absoluteRanges = items.map { it.absoluteOffset until it.absoluteOffset + storedSize }
        val footerOffset = original.size - FOOTER_SIZE
        require(original.indices.all { index ->
            absoluteRanges.any { index in it } || index in footerOffset until original.size ||
                original[index] == edited[index]
        }) { "An unrelated encrypted container byte changed during the batch restore." }
    }
}

class LegendsArceusPseBankBatchRestoreCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    bankRecords: List<ByteArray>,
    storedRecords: List<ByteArray>,
    items: List<LegendsArceusPseBankBatchRestoreItem>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val bankRecords = bankRecords.map { it.copyOf() }
    private val storedRecords = storedRecords.map { it.copyOf() }
    val items = items.toList()
    val sourceSha256 = LegendsArceusPseBankRestoreEditing.sha256(this.original)
    val editedSha256 = LegendsArceusPseBankRestoreEditing.sha256(this.edited)
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
            LegendsArceusPseBankBatchRestoreEditing.verify(
                original, edited, bankRecords, storedRecords, items
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed PSE Bank batch restore."
            }
            LegendsArceusPseBankBatchRestoreEditing.verify(
                original, persisted, bankRecords, storedRecords, items
            )
            requireSource()
            requireBank()
        } catch (failure: Exception) {
            if (started) runCatching { discardDestination() }
            val destinationEmpty = runCatching { readDestination().isEmpty() }.getOrDefault(false)
            val recoveryStatus = when {
                destinationEmpty && started -> " The failed new copy was cleared and verified empty; the recovery backup is retained."
                destinationEmpty -> " The new copy remained empty and was independently verified; the recovery backup is retained."
                else -> " The new copy could not be verified empty. Do not use it; the recovery backup is retained."
            }
            throw IllegalStateException(
                (failure.message ?: "PSE Bank batch restore verification failed.") + recoveryStatus,
                failure
            )
        }
    }
}
