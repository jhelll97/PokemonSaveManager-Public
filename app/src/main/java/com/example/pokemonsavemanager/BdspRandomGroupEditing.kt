package com.example.pokemonsavemanager

import java.security.MessageDigest

data class BdspRandomGroupEditRead(
    val version: BdspVersion,
    val revision: BdspSaveRevision,
    val groups: BdspRandomGroupsRead,
    val record: BdspRandomGroupRead
)

data class BdspRandomGroupReplacement(
    /** Null retains an unreadable native name buffer exactly. */
    val groupName: String?,
    /** Null retains an unreadable native name buffer exactly. */
    val playerName: String?,
    val maleByte: Int,
    val regionCode: Int,
    val seed: ULong,
    val random: ULong,
    val ticks: Long,
    val userId: Int
)

/** Reviewed editing for one documented native BDSP random-group record. */
object BdspRandomGroupEditing {
    private const val BASE_OFFSET = 0x9476C
    private const val RECORD_COUNT = 12
    private const val RECORD_SIZE = 0x84
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun read(source: ByteArray, index: Int): BdspRandomGroupEditRead {
        require(index in 0 until RECORD_COUNT) { "The saved random-group index must be 0 through 11." }
        val groups = BdspRandomGroupsReadOnly.read(source)
        return BdspRandomGroupEditRead(groups.version, groups.revision, groups, groups.record(index))
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        index: Int,
        replacement: BdspRandomGroupReplacement
    ): BdspRandomGroupCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = read(original, index)
        val edited = original.copyOf()
        val offset = recordOffset(index)
        val ranges = mutableListOf<IntRange>()
        val fields = mutableListOf<String>()

        if (replacement.groupName != null && replacement.groupName != before.record.groupName.text) {
            validateName(replacement.groupName, 16, "group name")
            writeName(edited, offset, 0x20, replacement.groupName)
            ranges += offset until offset + 0x20
            fields += "group name"
        }
        if (replacement.playerName != null && replacement.playerName != before.record.playerName.text) {
            validateName(replacement.playerName, 32, "player name")
            writeName(edited, offset + 0x20, 0x40, replacement.playerName)
            ranges += offset + 0x20 until offset + 0x60
            fields += "player name"
        }
        if (replacement.maleByte != before.record.rawMaleByte) {
            require(replacement.maleByte in 0..1) { "Changed male flag must be 0 clear or 1 set." }
            edited[offset + 0x60] = replacement.maleByte.toByte()
            ranges += offset + 0x60..offset + 0x60
            fields += "male flag"
        }
        if (replacement.regionCode != before.record.regionCode) {
            writeInt32(edited, offset + 0x64, replacement.regionCode)
            ranges += offset + 0x64 until offset + 0x68
            fields += "region code"
        }
        if (replacement.seed != before.record.seed) {
            writeUInt64(edited, offset + 0x68, replacement.seed)
            ranges += offset + 0x68 until offset + 0x70
            fields += "seed"
        }
        if (replacement.random != before.record.random) {
            writeUInt64(edited, offset + 0x70, replacement.random)
            ranges += offset + 0x70 until offset + 0x78
            fields += "random"
        }
        if (replacement.ticks != before.record.ticks) {
            writeInt64(edited, offset + 0x78, replacement.ticks)
            ranges += offset + 0x78 until offset + 0x80
            fields += "ticks"
        }
        if (replacement.userId != before.record.userId) {
            writeInt32(edited, offset + 0x80, replacement.userId)
            ranges += offset + 0x80 until offset + RECORD_SIZE
            fields += "user ID"
        }
        require(ranges.isNotEmpty()) { "Change at least one saved random-group field before reviewing." }
        refreshDigest(edited)
        val after = read(edited, index)
        require(
            (replacement.groupName == null || after.record.groupName.text == replacement.groupName) &&
                (replacement.playerName == null || after.record.playerName.text == replacement.playerName) &&
                after.record.rawMaleByte == replacement.maleByte &&
                after.record.regionCode == replacement.regionCode &&
                after.record.seed == replacement.seed && after.record.random == replacement.random &&
                after.record.ticks == replacement.ticks && after.record.userId == replacement.userId
        ) { "The requested random-group values did not stage exactly." }
        verify(original, edited, index, after, ranges)
        return BdspRandomGroupCopyPlan(original, edited, before, after, ranges.toList(), fields.toList())
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        index: Int,
        expected: BdspRandomGroupEditRead,
        allowedRanges: List<IntRange>
    ) {
        val before = BdspRandomGroupsReadOnly.read(original)
        val after = BdspRandomGroupsReadOnly.read(edited)
        require(
            original.size == edited.size && before.version == after.version &&
                before.revision == after.revision
        ) { "The random-group edit changed title identity, save revision, or file size." }
        require(after.record(index).rawBytes == expected.record.rawBytes) {
            "The reviewed random-group record did not reopen exactly."
        }
        require((0 until RECORD_COUNT).all { slot ->
            slot == index || before.record(slot).rawBytes == after.record(slot).rawBytes
        }) { "A different random-group record changed." }
        val offset = recordOffset(index)
        require((offset + 0x61 until offset + 0x64).all { original[it] == edited[it] }) {
            "A selected-record alignment byte changed."
        }
        require(original.indices.all { byteIndex ->
            allowedRanges.any { byteIndex in it } ||
                byteIndex in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[byteIndex] == edited[byteIndex]
        }) { "The random-group edit changed an unrelated BDSP save byte." }
    }

    fun recordOffset(index: Int): Int {
        require(index in 0 until RECORD_COUNT)
        return BASE_OFFSET + index * RECORD_SIZE
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun validateName(value: String, maxUnits: Int, label: String) {
        require(value.length <= maxUnits) { "Changed $label must use at most $maxUnits UTF-16 code units." }
        require('\u0000' !in value) { "Changed $label cannot contain a NULL code unit." }
        require(value.none { Character.isISOControl(it) }) { "Changed $label cannot contain control characters." }
        var index = 0
        while (index < value.length) {
            val unit = value[index]
            when {
                Character.isHighSurrogate(unit) -> {
                    require(index + 1 < value.length && Character.isLowSurrogate(value[index + 1])) {
                        "Changed $label contains malformed UTF-16."
                    }
                    index += 2
                }
                Character.isLowSurrogate(unit) -> throw IllegalArgumentException(
                    "Changed $label contains malformed UTF-16."
                )
                else -> index++
            }
        }
    }

    private fun writeName(bytes: ByteArray, offset: Int, length: Int, value: String) {
        val encoded = value.toByteArray(Charsets.UTF_16LE)
        require(encoded.size <= length) { "The changed name is too long for its native buffer." }
        bytes.fill(0, offset, offset + length)
        encoded.copyInto(bytes, offset)
    }

    private fun writeInt32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }

    private fun writeInt64(bytes: ByteArray, offset: Int, value: Long) {
        repeat(8) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }

    private fun writeUInt64(bytes: ByteArray, offset: Int, value: ULong) {
        repeat(8) { bytes[offset + it] = (value shr (8 * it)).toByte() }
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class BdspRandomGroupCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: BdspRandomGroupEditRead,
    val after: BdspRandomGroupEditRead,
    val allowedRanges: List<IntRange>,
    val changedFields: List<String>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = BdspRandomGroupEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Random Group Records." }
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
            BdspRandomGroupEditing.verify(original, edited, before.record.index, after, allowedRanges)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspRandomGroupEditing.verify(original, persisted, before.record.index, after, allowedRanges)
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
