package com.example.pokemonsavemanager

/** One exact native PB7 and the append-only destination reviewed for a batch restore. */
data class LetsGoPseBankBatchRestoreItem(
    val pokemon: LetsGoPokemonRead,
    val storageIndex: Int,
    val recordOffset: Int,
    val bankSha256: String
)

/** Restores a reviewed native PB7 batch into consecutive unused Let's Go storage records. */
object LetsGoPseBankBatchRestoreEditing {
    private const val DEX = 0x2A00
    private const val DEX_BLOCK_LENGTH = 0x20E8
    private const val STORAGE_META = 0x5A00
    private const val STORAGE_META_LENGTH = 0x12
    private const val STORAGE_COUNT = 0x5A0E
    private const val STORAGE_RECORDS = 0x5C00
    private const val STORAGE_RECORDS_LENGTH = 0x3F7A0
    private const val DESCRIPTORS = 0xB8614
    private const val META_BLOCK = 8
    private const val RECORD_BLOCK = 9

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        bankRecords: List<ByteArray>
    ): LetsGoPseBankBatchRestoreCopyPlan {
        val original = source.copyOf()
        val records = bankRecords.map { it.copyOf() }
        require(LetsGoPseBankRestoreEditing.sha256(original) == expectedSha256) {
            "The source changed. Reopen it from PSE."
        }
        require(LetsGoSaveValidation.inspect(original) is LetsGoValidation.Verified) {
            "A validated native Let's Go save is required."
        }
        require(records.size >= 2) { "Choose at least two native PB7 Bank entries for a batch restore." }
        val before = LetsGoStorageReadOnly.read(original)
        require(before.count + records.size <= LetsGoPseBankRestoreEditing.STORAGE_CAPACITY) {
            "The native Let's Go storage pool does not have enough unused records for this batch."
        }
        records.forEachIndexed { index, record ->
            LetsGoPseBankRestoreEditing.inspectBankRecord(record)
            val offset = STORAGE_RECORDS + (before.count + index) * LetsGoPokemonReadOnly.RECORD_SIZE
            require(original.copyOfRange(offset, offset + LetsGoPokemonReadOnly.RECORD_SIZE)
                .all { it == 0.toByte() }) {
                "Native storage slot ${before.count + index + 1} contains protected bytes."
            }
        }

        val edited = original.copyOf()
        val items = records.mapIndexed { index, record ->
            val storageIndex = before.count + index
            val offset = STORAGE_RECORDS + storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE
            record.copyInto(edited, offset)
            LetsGoPseBankBatchRestoreItem(
                LetsGoPseBankRestoreEditing.inspectBankRecord(record),
                storageIndex,
                offset,
                LetsGoPseBankRestoreEditing.sha256(record)
            )
        }
        writeU16(edited, STORAGE_COUNT, before.count + records.size)
        writeBlockCrc(edited, META_BLOCK, STORAGE_META, STORAGE_META_LENGTH)
        writeBlockCrc(edited, RECORD_BLOCK, STORAGE_RECORDS, STORAGE_RECORDS_LENGTH)
        verify(original, edited, records, items)
        return LetsGoPseBankBatchRestoreCopyPlan(original, edited, records, items)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        bankRecords: List<ByteArray>,
        items: List<LetsGoPseBankBatchRestoreItem>
    ) {
        require(bankRecords.size >= 2 && bankRecords.size == items.size) {
            "The reviewed native PB7 batch is incomplete."
        }
        val beforeValidation = LetsGoSaveValidation.inspect(original)
        val afterValidation = LetsGoSaveValidation.inspect(edited)
        require(beforeValidation is LetsGoValidation.Verified && afterValidation is LetsGoValidation.Verified &&
            beforeValidation.version == afterValidation.version && original.size == edited.size) {
            "The restored copy changed native title identity, size, or block integrity."
        }
        val before = LetsGoStorageReadOnly.read(original)
        val after = LetsGoStorageReadOnly.read(edited)
        require(after.count == before.count + bankRecords.size && items.indices.all {
            items[it].storageIndex == before.count + it
        }) { "The batch did not append exactly the reviewed native storage records." }
        require((0 until before.count).all { index ->
            before.slot(index) == after.slot(index)
        }) { "An existing Pokémon record, party pointer, or partner pointer changed." }
        require(original.copyOfRange(DEX, DEX + DEX_BLOCK_LENGTH)
            .contentEquals(edited.copyOfRange(DEX, DEX + DEX_BLOCK_LENGTH))) {
            "Native Let's Go Pokédex data changed during a same-title batch restore."
        }

        items.forEachIndexed { index, item ->
            val record = bankRecords[index]
            require(LetsGoPseBankRestoreEditing.sha256(record) == item.bankSha256) {
                "A reviewed PSE Bank identity changed."
            }
            require(item.recordOffset == STORAGE_RECORDS + item.storageIndex * LetsGoPokemonReadOnly.RECORD_SIZE) {
                "A reviewed native storage destination changed."
            }
            require(original.copyOfRange(item.recordOffset, item.recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
                .all { it == 0.toByte() }) {
                "A reviewed native storage destination was not empty."
            }
            val reopened = after.slot(item.storageIndex)
            val pokemon = reopened.read as? LetsGoSlotRead.Pokemon
                ?: throw IllegalArgumentException("A restored PB7 did not reopen as a Pokémon.")
            require(pokemon.value == LetsGoPseBankRestoreEditing.inspectBankRecord(record) &&
                reopened.partyPositions.isEmpty() && !reopened.partner) {
                "A restored PB7 did not reopen exactly as an unassigned storage Pokémon."
            }
            require(edited.copyOfRange(item.recordOffset, item.recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE)
                .contentEquals(record)) { "The native PSE Bank bytes were not preserved exactly." }
        }

        val recordRanges = items.map {
            it.recordOffset until it.recordOffset + LetsGoPokemonReadOnly.RECORD_SIZE
        }
        val metaCrc = descriptorCrcOffset(META_BLOCK)
        val recordCrc = descriptorCrcOffset(RECORD_BLOCK)
        require(original.indices.all { offset ->
            offset in STORAGE_COUNT until STORAGE_COUNT + 2 ||
                recordRanges.any { offset in it } ||
                offset in metaCrc until metaCrc + 2 || offset in recordCrc until recordCrc + 2 ||
                original[offset] == edited[offset]
        }) { "An unrelated Let's Go save byte changed." }
    }

    private fun descriptorCrcOffset(block: Int): Int = DESCRIPTORS + block * 8 + 6
    private fun writeBlockCrc(bytes: ByteArray, block: Int, offset: Int, length: Int) =
        writeU16(bytes, descriptorCrcOffset(block), LetsGoSaveValidation.crc16(bytes, offset, length))
    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
}

class LetsGoPseBankBatchRestoreCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    bankRecords: List<ByteArray>,
    items: List<LetsGoPseBankBatchRestoreItem>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    private val bankRecords = bankRecords.map { it.copyOf() }
    val items = items.toList()
    val sourceSha256 = LetsGoPseBankRestoreEditing.sha256(this.original)
    val editedSha256 = LetsGoPseBankRestoreEditing.sha256(this.edited)
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
            LetsGoPseBankBatchRestoreEditing.verify(original, edited, bankRecords, items)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed PSE Bank batch restore."
            }
            LetsGoPseBankBatchRestoreEditing.verify(original, persisted, bankRecords, items)
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
