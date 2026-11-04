package com.example.pokemonsavemanager

import java.security.MessageDigest

data class LetsGoPlayerAccessoryRead internal constructor(
    val version: LetsGoVersion,
    val matchingTemplateBytes: Int,
    val totalTemplateBytes: Int,
    val isAllUnlocked: Boolean,
    val blockSha256: String
)

/** One exact native block-16 replacement using PKHeX's pinned all-player-accessories template. */
object LetsGoPlayerAccessoryEditing {
    const val BLOCK_OFFSET = 0x4DE00
    const val BLOCK_LENGTH = 0x98
    const val CRC_OFFSET = 0xB869A
    private val allPlayerAccessories = ByteArray(BLOCK_LENGTH).apply {
        for (offset in intArrayOf(0x30, 0x40, 0x50, 0x60, 0x70)) {
            this[offset] = 0xFF.toByte()
            this[offset + 1] = 0xFF.toByte()
            this[offset + 2] = 0x07
        }
        this[0x80] = 0x01
    }

    fun read(source: ByteArray): LetsGoPlayerAccessoryRead {
        val checked = LetsGoSaveValidation.inspect(source)
        require(checked is LetsGoValidation.Verified) { "A fully validated native Let's Go save is required." }
        val block = source.copyOfRange(BLOCK_OFFSET, BLOCK_OFFSET + BLOCK_LENGTH)
        val matching = block.indices.count { block[it] == allPlayerAccessories[it] }
        return LetsGoPlayerAccessoryRead(
            checked.version, matching, BLOCK_LENGTH, matching == BLOCK_LENGTH, sha256(block)
        )
    }

    fun stage(source: ByteArray, expectedSha256: String): LetsGoPlayerAccessoryCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = read(original)
        require(!before.isAllUnlocked) { "All native player accessories are already unlocked." }
        val edited = original.copyOf()
        allPlayerAccessories.copyInto(edited, BLOCK_OFFSET)
        writeU16(edited, CRC_OFFSET, LetsGoSaveValidation.crc16(edited, BLOCK_OFFSET, BLOCK_LENGTH))
        verify(original, edited)
        val after = read(edited)
        val changed = (0 until BLOCK_LENGTH).count { original[BLOCK_OFFSET + it] != edited[BLOCK_OFFSET + it] }
        return LetsGoPlayerAccessoryCopyPlan(original, edited, before, after, changed)
    }

    internal fun verify(original: ByteArray, edited: ByteArray) {
        val before = read(original)
        val after = read(edited)
        require(!before.isAllUnlocked && after.isAllUnlocked) {
            "The staged player-accessory catalogue did not reopen as fully unlocked."
        }
        require(original.indices.all { index ->
            index in BLOCK_OFFSET until BLOCK_OFFSET + BLOCK_LENGTH ||
                index in CRC_OFFSET until CRC_OFFSET + 2 || original[index] == edited[index]
        }) { "An unrelated Let's Go save byte changed." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
}

class LetsGoPlayerAccessoryCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: LetsGoPlayerAccessoryRead,
    val after: LetsGoPlayerAccessoryRead,
    val changedByteCount: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = LetsGoPlayerAccessoryEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Player Accessories." }
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
            LetsGoPlayerAccessoryEditing.verify(original, edited)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LetsGoPlayerAccessoryEditing.verify(original, persisted)
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
