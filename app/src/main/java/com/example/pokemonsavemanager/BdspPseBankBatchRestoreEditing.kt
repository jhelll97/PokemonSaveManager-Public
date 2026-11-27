package com.example.pokemonsavemanager

import java.security.MessageDigest

data class BdspPseBankBatchRestoreItem(
    val pokemon: BdspPokemonRead,
    val box: Int,
    val position: Int,
    val recordOffset: Int,
    val bankSha256: String
)

/** Restores a reviewed native PB8 batch into distinct empty BDSP box slots. */
object BdspPseBankBatchRestoreEditing {
    private const val BOX_OFFSET = 0x14EF4
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        bankRecords: List<ByteArray>,
        destinations: List<BdspPseBankDestination>
    ): BdspPseBankBatchRestoreCopyPlan {
        val original = source.copyOf()
        val records = bankRecords.map { it.copyOf() }
        require(BdspPseBankRestoreEditing.sha256(original) == expectedSha256) {
            "The source changed. Reopen it from PSE."
        }
        require(records.size >= 2) { "Choose at least two native PB8 Bank entries for a batch restore." }
        require(records.size == destinations.size) { "Each selected Bank entry requires one reviewed destination." }
        require(destinations.distinct().size == destinations.size) { "Every batch destination must be unique." }

        val before = BdspStorageReadOnly.read(original)
        records.forEachIndexed { index, record ->
            BdspPseBankRestoreEditing.inspectBankRecord(record)
            val destination = destinations[index]
            require(destination.box in 1..40 && destination.position in 1..30) {
                "A reviewed BDSP destination is outside native storage."
            }
            require(before.box(destination.box - 1)[destination.position - 1].read is BdspSlotRead.Empty) {
                "Box ${destination.box}, slot ${destination.position} is no longer empty."
            }
        }

        val edited = original.copyOf()
        val items = records.mapIndexed { index, record ->
            val destination = destinations[index]
            val recordOffset = BOX_OFFSET +
                ((destination.box - 1) * 30 + destination.position - 1) * BdspPokemonReadOnly.RECORD_SIZE
            record.copyInto(edited, recordOffset)
            BdspPseBankBatchRestoreItem(
                BdspPseBankRestoreEditing.inspectBankRecord(record),
                destination.box,
                destination.position,
                recordOffset,
                BdspPseBankRestoreEditing.sha256(record)
            )
        }
        refreshDigest(edited)
        verify(original, edited, records, items)
        return BdspPseBankBatchRestoreCopyPlan(original, edited, records, items)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        bankRecords: List<ByteArray>,
        items: List<BdspPseBankBatchRestoreItem>
    ) {
        require(bankRecords.size >= 2 && bankRecords.size == items.size) {
            "The reviewed native PB8 batch is incomplete."
        }
        val before = BdspStorageReadOnly.read(original)
        val after = BdspStorageReadOnly.read(edited)
        require(original.size == edited.size && before.version == after.version &&
            before.revision == after.revision) {
            "The restored copy changed native title identity, revision, or size."
        }
        require(items.map { it.box to it.position }.distinct().size == items.size) {
            "The restored batch reused a destination slot."
        }

        items.forEachIndexed { index, item ->
            val record = bankRecords[index]
            require(BdspPseBankRestoreEditing.sha256(record) == item.bankSha256) {
                "A reviewed PSE Bank identity changed."
            }
            require(before.box(item.box - 1)[item.position - 1].read is BdspSlotRead.Empty) {
                "A reviewed destination was not empty in the original save."
            }
            val reopened = after.box(item.box - 1)[item.position - 1].read as? BdspSlotRead.Pokemon
                ?: throw IllegalArgumentException("A restored PB8 did not reopen as a Pokémon.")
            require(reopened.value == BdspPseBankRestoreEditing.inspectBankRecord(record)) {
                "A restored PB8 did not reopen exactly."
            }
            require(edited.copyOfRange(item.recordOffset, item.recordOffset + record.size)
                .contentEquals(record)) { "The native PSE Bank bytes were not preserved exactly." }
        }

        val ranges = items.map {
            it.recordOffset until it.recordOffset + BdspPokemonReadOnly.RECORD_SIZE
        }
        require(original.indices.all { index ->
            ranges.any { index in it } || index in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[index] == edited[index]
        }) { "An unrelated BDSP save byte changed during the batch restore." }
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

class BdspPseBankBatchRestoreCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    bankRecords: List<ByteArray>,
    items: List<BdspPseBankBatchRestoreItem>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val bankRecords = bankRecords.map { it.copyOf() }
    val items = items.toList()
    val sourceSha256 = BdspPseBankRestoreEditing.sha256(this.original)
    val editedSha256 = BdspPseBankRestoreEditing.sha256(this.edited)
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
            BdspPseBankBatchRestoreEditing.verify(original, edited, bankRecords, items)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed PSE Bank batch restore."
            }
            BdspPseBankBatchRestoreEditing.verify(original, persisted, bankRecords, items)
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
