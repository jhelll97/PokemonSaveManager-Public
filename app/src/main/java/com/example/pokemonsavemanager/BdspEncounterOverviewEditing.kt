package com.example.pokemonsavemanager

import java.security.MessageDigest

enum class BdspEncounterOverviewField(
    val displayName: String,
    val relativeOffset: Int,
    val width: Int,
    val signed: Boolean = false
) {
    WALK_COUNT("Encounter walk count", 0x000, 4, true),
    SAFARI_SEED("Safari RNG seed", 0x004, 4),
    GENERATE_SEED("Generate RNG seed", 0x008, 4),
    HILL_TALK("Hill talk flag", 0x00C, 4),
    HILL_TABLE_1("Hill encounter table 1", 0x010, 2),
    HILL_TABLE_2("Hill encounter table 2", 0x012, 2),
    BEFORE_ZONE("Previous zone", 0x134, 4, true),
    OLD_ZONE("Older zone", 0x138, 4, true),
    GENERATE_VALID("Generate-valid flag", 0x17C, 4),
    SPRAY_COUNT("Repel step count", 0x180, 2, true),
    SPRAY_TYPE("Repel type", 0x182, 1),
    VS_SEEKER_CHARGE("Vs. Seeker charge", 0x183, 1),
    RADAR_CHARGE("Poké Radar charge", 0x184, 1),
    FLUTE_TYPE("Flute type", 0x185, 1)
}

class BdspEncounterOverviewReplacement(values: Map<BdspEncounterOverviewField, Long>) {
    private val values = values.toMap()
    init {
        require(BdspEncounterOverviewField.values().all { it in this.values }) {
            "Every encounter overview field must be supplied."
        }
    }
    fun value(field: BdspEncounterOverviewField): Long = values.getValue(field)
}

data class BdspEncounterOverviewEditRead(
    val version: BdspVersion,
    val revision: BdspSaveRevision,
    val encounter: BdspEncounterRead
)

/** Reviewed scalar edits for the native BDSP encounter overview record. */
object BdspEncounterOverviewEditing {
    private const val ENCOUNTER_OFFSET = 0x8F97C
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun read(source: ByteArray): BdspEncounterOverviewEditRead {
        val encounter = BdspEncounterReadOnly.read(source)
        return BdspEncounterOverviewEditRead(encounter.version, encounter.revision, encounter)
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        replacement: BdspEncounterOverviewReplacement
    ): BdspEncounterOverviewCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = read(original)
        val changedFields = BdspEncounterOverviewField.values().filter { field ->
            replacement.value(field) != value(before.encounter, field)
        }
        require(changedFields.isNotEmpty()) { "Change at least one encounter overview value before reviewing." }
        changedFields.forEach { field -> validate(field, replacement.value(field)) }

        val edited = original.copyOf()
        changedFields.forEach { field ->
            writeLittleEndian(edited, absoluteOffset(field), field.width, replacement.value(field))
        }
        refreshDigest(edited)
        val after = read(edited)
        verify(original, edited, after, changedFields)
        return BdspEncounterOverviewCopyPlan(original, edited, before, after, changedFields)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: BdspEncounterOverviewEditRead,
        changedFields: List<BdspEncounterOverviewField>
    ) {
        require(changedFields.isNotEmpty())
        val before = read(original)
        val after = read(edited)
        require(
            original.size == edited.size && before.version == after.version &&
                before.revision == after.revision
        ) { "The encounter overview edit changed title identity, save revision, or file size." }
        require(BdspEncounterOverviewField.values().all { field ->
            value(after.encounter, field) == value(expected.encounter, field)
        }) { "The reviewed encounter overview values did not reopen exactly." }
        require(before.encounter.rawEncounterBytes.indices.all { relative ->
            val fileOffset = ENCOUNTER_OFFSET + relative
            changedFields.any { field -> fileOffset in absoluteOffset(field) until absoluteOffset(field) + field.width } ||
                before.encounter.rawEncounterBytes[relative] == after.encounter.rawEncounterBytes[relative]
        }) { "A honey-tree, radar, roamer, padding, or other encounter byte changed." }
        require(original.indices.all { byteIndex ->
            changedFields.any { field ->
                byteIndex in absoluteOffset(field) until absoluteOffset(field) + field.width
            } || byteIndex in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[byteIndex] == edited[byteIndex]
        }) { "The encounter overview edit changed an unrelated BDSP save byte." }
    }

    fun absoluteOffset(field: BdspEncounterOverviewField): Int = ENCOUNTER_OFFSET + field.relativeOffset

    fun value(encounter: BdspEncounterRead, field: BdspEncounterOverviewField): Long = when (field) {
        BdspEncounterOverviewField.WALK_COUNT -> encounter.walkCount.toLong()
        BdspEncounterOverviewField.SAFARI_SEED -> encounter.safariSeed
        BdspEncounterOverviewField.GENERATE_SEED -> encounter.generateSeed
        BdspEncounterOverviewField.HILL_TALK -> encounter.rawHillTalkWord
        BdspEncounterOverviewField.HILL_TABLE_1 -> encounter.hillTable1.toLong()
        BdspEncounterOverviewField.HILL_TABLE_2 -> encounter.hillTable2.toLong()
        BdspEncounterOverviewField.BEFORE_ZONE -> encounter.beforeZone.toLong()
        BdspEncounterOverviewField.OLD_ZONE -> encounter.oldZone.toLong()
        BdspEncounterOverviewField.GENERATE_VALID -> encounter.rawGenerateValidWord
        BdspEncounterOverviewField.SPRAY_COUNT -> encounter.sprayCount.toLong()
        BdspEncounterOverviewField.SPRAY_TYPE -> encounter.sprayType.toLong()
        BdspEncounterOverviewField.VS_SEEKER_CHARGE -> encounter.vsSeekerCharge.toLong()
        BdspEncounterOverviewField.RADAR_CHARGE -> encounter.radarCharge.toLong()
        BdspEncounterOverviewField.FLUTE_TYPE -> encounter.fluteType.toLong()
    }

    fun rangeLabel(field: BdspEncounterOverviewField): String = when (field) {
        BdspEncounterOverviewField.WALK_COUNT,
        BdspEncounterOverviewField.BEFORE_ZONE,
        BdspEncounterOverviewField.OLD_ZONE -> "-2147483648–2147483647"
        BdspEncounterOverviewField.SAFARI_SEED,
        BdspEncounterOverviewField.GENERATE_SEED -> "0–4294967295"
        BdspEncounterOverviewField.HILL_TALK,
        BdspEncounterOverviewField.GENERATE_VALID -> "0 Clear, 1 Set"
        BdspEncounterOverviewField.HILL_TABLE_1,
        BdspEncounterOverviewField.HILL_TABLE_2 -> "0–65535"
        BdspEncounterOverviewField.SPRAY_COUNT -> "-32768–32767"
        BdspEncounterOverviewField.SPRAY_TYPE,
        BdspEncounterOverviewField.FLUTE_TYPE -> "0–255"
        BdspEncounterOverviewField.VS_SEEKER_CHARGE -> "0–100"
        BdspEncounterOverviewField.RADAR_CHARGE -> "0–50"
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun validate(field: BdspEncounterOverviewField, value: Long) {
        val valid = when (field) {
            BdspEncounterOverviewField.WALK_COUNT,
            BdspEncounterOverviewField.BEFORE_ZONE,
            BdspEncounterOverviewField.OLD_ZONE -> value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
            BdspEncounterOverviewField.SAFARI_SEED,
            BdspEncounterOverviewField.GENERATE_SEED -> value in 0L..0xFFFF_FFFFL
            BdspEncounterOverviewField.HILL_TALK,
            BdspEncounterOverviewField.GENERATE_VALID -> value in 0L..1L
            BdspEncounterOverviewField.HILL_TABLE_1,
            BdspEncounterOverviewField.HILL_TABLE_2 -> value in 0L..0xFFFFL
            BdspEncounterOverviewField.SPRAY_COUNT -> value in Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong()
            BdspEncounterOverviewField.SPRAY_TYPE,
            BdspEncounterOverviewField.FLUTE_TYPE -> value in 0L..0xFFL
            BdspEncounterOverviewField.VS_SEEKER_CHARGE -> value in 0L..100L
            BdspEncounterOverviewField.RADAR_CHARGE -> value in 0L..50L
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
class BdspEncounterOverviewCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: BdspEncounterOverviewEditRead,
    val after: BdspEncounterOverviewEditRead,
    val changedFields: List<BdspEncounterOverviewField>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = BdspEncounterOverviewEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen the encounter overview." }
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
            BdspEncounterOverviewEditing.verify(original, edited, after, changedFields)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspEncounterOverviewEditing.verify(original, persisted, after, changedFields)
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
