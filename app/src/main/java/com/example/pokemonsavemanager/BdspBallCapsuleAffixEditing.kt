package com.example.pokemonsavemanager

import java.security.MessageDigest

enum class BdspBallCapsuleAffixEditField(val displayName: String, val relativeOffset: Int) {
    SEAL_ID("Seal ID", 0x00),
    X("X coordinate", 0x02),
    Y("Y coordinate", 0x04),
    Z("Z coordinate", 0x06)
}

data class BdspBallCapsuleAffixReplacement(
    val sealId: Int,
    val x: Int,
    val y: Int,
    val z: Int
) {
    fun value(field: BdspBallCapsuleAffixEditField): Int = when (field) {
        BdspBallCapsuleAffixEditField.SEAL_ID -> sealId
        BdspBallCapsuleAffixEditField.X -> x
        BdspBallCapsuleAffixEditField.Y -> y
        BdspBallCapsuleAffixEditField.Z -> z
    }
}

data class BdspBallCapsuleAffixEditRead(
    val version: BdspVersion,
    val revision: BdspSaveRevision,
    val capsuleIndex: Int,
    val affix: BdspAffixedSealRead
)

/** Reviewed edits for one native eight-byte BDSP Ball Capsule affix record. */
object BdspBallCapsuleAffixEditing {
    private const val CAPSULES_OFFSET = 0x8FB84
    private const val HEADER_SIZE = 4
    private const val CAPSULE_SIZE = 0xAC
    private const val CAPSULE_HEADER_SIZE = 12
    private const val AFFIX_SIZE = 8
    private const val HASH_OFFSET = 0xE9818
    private const val HASH_LENGTH = 16

    fun read(source: ByteArray, capsuleIndex: Int, affixIndex: Int): BdspBallCapsuleAffixEditRead {
        val capsules = BdspBallCapsulesReadOnly.read(source)
        return BdspBallCapsuleAffixEditRead(
            capsules.version,
            capsules.revision,
            capsuleIndex,
            capsules.capsule(capsuleIndex).seal(affixIndex)
        )
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        capsuleIndex: Int,
        affixIndex: Int,
        replacement: BdspBallCapsuleAffixReplacement
    ): BdspBallCapsuleAffixCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(capsuleIndex in 0 until 99) { "Ball Capsule index must be 0 through 98." }
        require(affixIndex in 0 until 20) { "Affix index must be 0 through 19." }
        val before = read(original, capsuleIndex, affixIndex)
        val changedFields = BdspBallCapsuleAffixEditField.values().filter { field ->
            replacement.value(field) != value(before.affix, field)
        }
        require(changedFields.isNotEmpty()) { "Change at least one affix value before reviewing." }
        changedFields.forEach { field -> validate(field, replacement.value(field)) }

        val edited = original.copyOf()
        val base = affixOffset(capsuleIndex, affixIndex)
        changedFields.forEach { field -> writeU16(edited, base + field.relativeOffset, replacement.value(field)) }
        refreshDigest(edited)
        val after = read(edited, capsuleIndex, affixIndex)
        verify(original, edited, capsuleIndex, affixIndex, after, changedFields)
        return BdspBallCapsuleAffixCopyPlan(original, edited, before, after, changedFields)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        capsuleIndex: Int,
        affixIndex: Int,
        expected: BdspBallCapsuleAffixEditRead,
        changedFields: List<BdspBallCapsuleAffixEditField>
    ) {
        require(changedFields.isNotEmpty())
        val beforeSet = BdspBallCapsulesReadOnly.read(original)
        val afterSet = BdspBallCapsulesReadOnly.read(edited)
        require(
            original.size == edited.size && beforeSet.version == afterSet.version &&
                beforeSet.revision == afterSet.revision
        ) { "The Ball Capsule edit changed title identity, save revision, or file size." }
        val reopened = afterSet.capsule(capsuleIndex).seal(affixIndex)
        require(
            reopened.sealId == expected.affix.sealId && reopened.x == expected.affix.x &&
                reopened.y == expected.affix.y && reopened.z == expected.affix.z &&
                reopened.rawBytes == expected.affix.rawBytes
        ) { "The reviewed affix values did not reopen exactly." }
        require(beforeSet.savedCapsuleCount == afterSet.savedCapsuleCount &&
            beforeSet.rawHeaderBytes == afterSet.rawHeaderBytes) {
            "The saved Ball Capsule header changed."
        }
        require(beforeSet.capsules.indices.all { capsule ->
            beforeSet.capsule(capsule).rawBytes == afterSet.capsule(capsule).rawBytes ||
                capsule == capsuleIndex
        }) { "A different Ball Capsule changed." }
        val beforeCapsule = beforeSet.capsule(capsuleIndex)
        val afterCapsule = afterSet.capsule(capsuleIndex)
        require(
            beforeCapsule.speciesId == afterCapsule.speciesId &&
                beforeCapsule.encryptionConstant == afterCapsule.encryptionConstant &&
                beforeCapsule.unknownWord == afterCapsule.unknownWord &&
                beforeCapsule.seals.indices.all {
                    it == affixIndex || beforeCapsule.seal(it).rawBytes == afterCapsule.seal(it).rawBytes
                }
        ) { "The capsule identity words or a different affix changed." }
        val base = affixOffset(capsuleIndex, affixIndex)
        require(original.indices.all { byteIndex ->
            changedFields.any { field ->
                byteIndex in base + field.relativeOffset until base + field.relativeOffset + 2
            } || byteIndex in HASH_OFFSET until HASH_OFFSET + HASH_LENGTH ||
                original[byteIndex] == edited[byteIndex]
        }) { "The affix edit changed an unrelated BDSP save byte." }
    }

    fun affixOffset(capsuleIndex: Int, affixIndex: Int): Int {
        require(capsuleIndex in 0 until 99)
        require(affixIndex in 0 until 20)
        return CAPSULES_OFFSET + HEADER_SIZE + capsuleIndex * CAPSULE_SIZE +
            CAPSULE_HEADER_SIZE + affixIndex * AFFIX_SIZE
    }

    fun value(affix: BdspAffixedSealRead, field: BdspBallCapsuleAffixEditField): Int = when (field) {
        BdspBallCapsuleAffixEditField.SEAL_ID -> affix.sealId
        BdspBallCapsuleAffixEditField.X -> affix.x
        BdspBallCapsuleAffixEditField.Y -> affix.y
        BdspBallCapsuleAffixEditField.Z -> affix.z
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun validate(field: BdspBallCapsuleAffixEditField, value: Int) {
        when (field) {
            BdspBallCapsuleAffixEditField.SEAL_ID -> require(value in 0..96) {
                "A changed Seal ID must be 0 empty or a documented native ID 1 through 96."
            }
            BdspBallCapsuleAffixEditField.X,
            BdspBallCapsuleAffixEditField.Y,
            BdspBallCapsuleAffixEditField.Z -> require(value in Short.MIN_VALUE..Short.MAX_VALUE) {
                "Changed ${field.displayName} must fit its native signed Int16 field."
            }
        }
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun refreshDigest(bytes: ByteArray) {
        bytes.fill(0, HASH_OFFSET, HASH_OFFSET + HASH_LENGTH)
        MessageDigest.getInstance("MD5").digest(bytes).copyInto(bytes, HASH_OFFSET)
    }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class BdspBallCapsuleAffixCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: BdspBallCapsuleAffixEditRead,
    val after: BdspBallCapsuleAffixEditRead,
    val changedFields: List<BdspBallCapsuleAffixEditField>
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = BdspBallCapsuleAffixEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen the Ball Capsule affix." }
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
            BdspBallCapsuleAffixEditing.verify(
                original, edited, before.capsuleIndex, before.affix.index, after, changedFields
            )
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspBallCapsuleAffixEditing.verify(
                original, persisted, before.capsuleIndex, before.affix.index, after, changedFields
            )
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
