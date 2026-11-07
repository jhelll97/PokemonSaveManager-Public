package com.example.pokemonsavemanager

import java.security.MessageDigest

enum class BdspHoneyTreeEditField(val displayName: String, val width: Int) {
    LAST_UPDATE_MINUTES("Saved update minutes", 8),
    TREE_NUMBER("Saved tree number", 1),
    SPREAD("Spread flag", 4),
    MINUTES("Tree minutes", 4),
    TABLE_ID("Encounter-table ID", 1),
    RARITY_ID("Rarity ID", 1),
    SHAKE_ID("Shake ID", 1)
}

class BdspHoneyTreeReplacement(values: Map<BdspHoneyTreeEditField, Long>) {
    private val values = values.toMap()
    init {
        require(BdspHoneyTreeEditField.values().all { it in this.values }) {
            "Every Honey Tree field must be supplied."
        }
    }
    fun value(field: BdspHoneyTreeEditField): Long = values.getValue(field)
}

data class BdspHoneyTreeEditRead(
    val version: BdspVersion,
    val revision: BdspSaveRevision,
    val honey: BdspHoneyTreesRead,
    val entry: BdspHoneyTreeEntry
)

/** Reviewed header and one-record edits for the native BDSP Honey Tree section. */
object BdspHoneyTreeEditing {
    private const val HONEY_OFFSET = 0x8F990
    private const val HEADER_SIZE = 12
    private const val RECORD_SIZE = 12
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun read(source: ByteArray, treeIndex: Int): BdspHoneyTreeEditRead {
        val honey = BdspHoneyTreesReadOnly.read(source)
        return BdspHoneyTreeEditRead(honey.version, honey.revision, honey, honey.entry(treeIndex))
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        treeIndex: Int,
        replacement: BdspHoneyTreeReplacement
    ): BdspHoneyTreeCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(treeIndex in 0 until 21) { "Honey Tree index must be 0 through 20." }
        val before = read(original, treeIndex)
        val changedFields = BdspHoneyTreeEditField.values().filter { field ->
            replacement.value(field) != value(before, field)
        }
        require(changedFields.isNotEmpty()) { "Change at least one Honey Tree value before reviewing." }
        changedFields.forEach { field -> validate(field, replacement.value(field)) }

        val edited = original.copyOf()
        changedFields.forEach { field ->
            writeLittleEndian(edited, absoluteOffset(treeIndex, field), field.width, replacement.value(field))
        }
        refreshDigest(edited)
        val after = read(edited, treeIndex)
        verify(original, edited, treeIndex, after, changedFields)
        return BdspHoneyTreeCopyPlan(original, edited, before, after, changedFields)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        treeIndex: Int,
        expected: BdspHoneyTreeEditRead,
        changedFields: List<BdspHoneyTreeEditField>
    ) {
        require(changedFields.isNotEmpty())
        val before = BdspHoneyTreesReadOnly.read(original)
        val after = BdspHoneyTreesReadOnly.read(edited)
        require(
            original.size == edited.size && before.version == after.version &&
                before.revision == after.revision
        ) { "The Honey Tree edit changed title identity, save revision, or file size." }
        val reopened = BdspHoneyTreeEditRead(after.version, after.revision, after, after.entry(treeIndex))
        require(BdspHoneyTreeEditField.values().all { field ->
            value(reopened, field) == value(expected, field)
        }) { "The reviewed Honey Tree values did not reopen exactly." }
        require(before.entries.indices.all { index ->
            index == treeIndex || before.entry(index).rawBytes == after.entry(index).rawBytes
        }) { "A different Honey Tree record changed." }
        require(before.rawHoneyBytes.indices.all { relative ->
            val fileOffset = HONEY_OFFSET + relative
            changedFields.any { field ->
                fileOffset in absoluteOffset(treeIndex, field) until
                    absoluteOffset(treeIndex, field) + field.width
            } || before.rawHoneyBytes[relative] == after.rawHoneyBytes[relative]
        }) { "A retained Honey Tree header, padding, or record byte changed." }
        require(original.indices.all { byteIndex ->
            changedFields.any { field ->
                byteIndex in absoluteOffset(treeIndex, field) until
                    absoluteOffset(treeIndex, field) + field.width
            } || byteIndex in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[byteIndex] == edited[byteIndex]
        }) { "The Honey Tree edit changed an unrelated BDSP save byte." }
    }

    fun absoluteOffset(treeIndex: Int, field: BdspHoneyTreeEditField): Int {
        require(treeIndex in 0 until 21)
        val record = HONEY_OFFSET + HEADER_SIZE + treeIndex * RECORD_SIZE
        return when (field) {
            BdspHoneyTreeEditField.LAST_UPDATE_MINUTES -> HONEY_OFFSET
            BdspHoneyTreeEditField.TREE_NUMBER -> HONEY_OFFSET + 8
            BdspHoneyTreeEditField.SPREAD -> record
            BdspHoneyTreeEditField.MINUTES -> record + 4
            BdspHoneyTreeEditField.TABLE_ID -> record + 8
            BdspHoneyTreeEditField.RARITY_ID -> record + 9
            BdspHoneyTreeEditField.SHAKE_ID -> record + 10
        }
    }

    fun value(read: BdspHoneyTreeEditRead, field: BdspHoneyTreeEditField): Long = when (field) {
        BdspHoneyTreeEditField.LAST_UPDATE_MINUTES -> read.honey.lastUpdateMinutes
        BdspHoneyTreeEditField.TREE_NUMBER -> read.honey.treeNumber.toLong()
        BdspHoneyTreeEditField.SPREAD -> read.entry.rawSpreadWord
        BdspHoneyTreeEditField.MINUTES -> read.entry.minutes.toLong()
        BdspHoneyTreeEditField.TABLE_ID -> read.entry.tableId.toLong()
        BdspHoneyTreeEditField.RARITY_ID -> read.entry.rarityId.toLong()
        BdspHoneyTreeEditField.SHAKE_ID -> read.entry.shakeId.toLong()
    }

    fun rangeLabel(field: BdspHoneyTreeEditField): String = when (field) {
        BdspHoneyTreeEditField.LAST_UPDATE_MINUTES -> "signed Int64"
        BdspHoneyTreeEditField.TREE_NUMBER,
        BdspHoneyTreeEditField.TABLE_ID,
        BdspHoneyTreeEditField.RARITY_ID,
        BdspHoneyTreeEditField.SHAKE_ID -> "0–255"
        BdspHoneyTreeEditField.SPREAD -> "0 Clear, 1 Set"
        BdspHoneyTreeEditField.MINUTES -> "-2147483648–2147483647"
    }

    fun isSigned(field: BdspHoneyTreeEditField): Boolean = when (field) {
        BdspHoneyTreeEditField.LAST_UPDATE_MINUTES,
        BdspHoneyTreeEditField.MINUTES -> true
        else -> false
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun validate(field: BdspHoneyTreeEditField, value: Long) {
        val valid = when (field) {
            BdspHoneyTreeEditField.LAST_UPDATE_MINUTES -> true
            BdspHoneyTreeEditField.TREE_NUMBER,
            BdspHoneyTreeEditField.TABLE_ID,
            BdspHoneyTreeEditField.RARITY_ID,
            BdspHoneyTreeEditField.SHAKE_ID -> value in 0L..255L
            BdspHoneyTreeEditField.SPREAD -> value in 0L..1L
            BdspHoneyTreeEditField.MINUTES -> value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
        }
        require(valid) { "Changed ${field.displayName} must be ${rangeLabel(field)}." }
    }

    private fun writeLittleEndian(bytes: ByteArray, offset: Int, width: Int, value: Long) {
        repeat(width) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class BdspHoneyTreeCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: BdspHoneyTreeEditRead,
    val after: BdspHoneyTreeEditRead,
    val changedFields: List<BdspHoneyTreeEditField>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = BdspHoneyTreeEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen the Honey Tree." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) {
            "The selected source changed. Reopen it from PSE."
        }
        requireSource()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        require(retainBackup(original.copyOf()).contentEquals(original)) {
            "The recovery backup did not verify. Nothing was written."
        }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            BdspHoneyTreeEditing.verify(original, edited, before.entry.index, after, changedFields)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspHoneyTreeEditing.verify(original, persisted, before.entry.index, after, changedFields)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
