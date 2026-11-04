package com.example.pokemonsavemanager

import java.security.MessageDigest

enum class LetsGoAccessoryTarget(
    val displayName: String,
    val blockId: Int,
    val blockOffset: Int,
    val blockLength: Int,
    val crcOffset: Int
) {
    PLAYER("Player", 16, 0x4DE00, 0x98, 0xB869A),
    STARTER("Partner Pokémon", 17, 0x4E000, 0x68, 0xB86A2)
}

data class LetsGoPlayerAccessoryRead internal constructor(
    val version: LetsGoVersion,
    val target: LetsGoAccessoryTarget,
    val matchingTemplateBytes: Int,
    val totalTemplateBytes: Int,
    val isAllUnlocked: Boolean,
    val blockSha256: String
)

/** One exact native Fashion-block replacement using PKHeX's pinned player or starter template. */
object LetsGoPlayerAccessoryEditing {
    private val allPlayerAccessories = ByteArray(LetsGoAccessoryTarget.PLAYER.blockLength).apply {
        for (offset in intArrayOf(0x30, 0x40, 0x50, 0x60, 0x70)) {
            this[offset] = 0xFF.toByte()
            this[offset + 1] = 0xFF.toByte()
            this[offset + 2] = 0x07
        }
        this[0x80] = 0x01
    }
    private val allStarterAccessories = ByteArray(LetsGoAccessoryTarget.STARTER.blockLength).apply {
        this[0x10] = 0x7F; this[0x11] = 0xFD.toByte(); this[0x12] = 0xFB.toByte(); this[0x13] = 0x03
        this[0x20] = 0xFF.toByte(); this[0x21] = 0x03
        this[0x30] = 0xFF.toByte(); this[0x31] = 0xFF.toByte(); this[0x32] = 0xFF.toByte()
        this[0x33] = 0xFF.toByte(); this[0x34] = 0x01
        this[0x40] = 0x7F; this[0x41] = 0xFD.toByte(); this[0x42] = 0x1F
        this[0x50] = 0x03
    }

    fun read(source: ByteArray, target: LetsGoAccessoryTarget = LetsGoAccessoryTarget.PLAYER): LetsGoPlayerAccessoryRead {
        val checked = LetsGoSaveValidation.inspect(source)
        require(checked is LetsGoValidation.Verified) { "A fully validated native Let's Go save is required." }
        val template = template(target)
        val block = source.copyOfRange(target.blockOffset, target.blockOffset + target.blockLength)
        val matching = block.indices.count { block[it] == template[it] }
        return LetsGoPlayerAccessoryRead(
            checked.version, target, matching, target.blockLength, matching == target.blockLength, sha256(block)
        )
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        target: LetsGoAccessoryTarget = LetsGoAccessoryTarget.PLAYER
    ): LetsGoPlayerAccessoryCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        val before = read(original, target)
        require(!before.isAllUnlocked) { "All native ${target.displayName.lowercase()} accessories are already unlocked." }
        val edited = original.copyOf()
        template(target).copyInto(edited, target.blockOffset)
        writeU16(edited, target.crcOffset,
            LetsGoSaveValidation.crc16(edited, target.blockOffset, target.blockLength))
        verify(original, edited, target)
        val after = read(edited, target)
        val changed = (0 until target.blockLength).count {
            original[target.blockOffset + it] != edited[target.blockOffset + it]
        }
        return LetsGoPlayerAccessoryCopyPlan(original, edited, target, before, after, changed)
    }

    internal fun verify(original: ByteArray, edited: ByteArray, target: LetsGoAccessoryTarget) {
        val before = read(original, target)
        val after = read(edited, target)
        require(!before.isAllUnlocked && after.isAllUnlocked) {
            "The staged ${target.displayName.lowercase()} accessory catalogue did not reopen as fully unlocked."
        }
        require(original.indices.all { index ->
            index in target.blockOffset until target.blockOffset + target.blockLength ||
                index in target.crcOffset until target.crcOffset + 2 || original[index] == edited[index]
        }) { "An unrelated Let's Go save byte changed." }
    }

    private fun template(target: LetsGoAccessoryTarget): ByteArray = when (target) {
        LetsGoAccessoryTarget.PLAYER -> allPlayerAccessories
        LetsGoAccessoryTarget.STARTER -> allStarterAccessories
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
    val target: LetsGoAccessoryTarget,
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
            LetsGoPlayerAccessoryEditing.verify(original, edited, target)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            LetsGoPlayerAccessoryEditing.verify(original, persisted, target)
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
