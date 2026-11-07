package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class LegendsArceusSatchelUpgradeEditRead(
    val version: LegendsArceusVersion,
    val upgrades: Long
)

/** Reviewed editing for PKHeX's documented Satchel upgrade-count UInt32. */
object LegendsArceusSatchelUpgradeEditing {
    private const val MAX_SAVE_SIZE = 0x13AD06
    private const val FIELD_LENGTH = 4

    fun read(source: ByteArray): LegendsArceusSatchelUpgradeEditRead {
        val trainer = LegendsArceusTrainerReadOnly.read(source)
        return LegendsArceusSatchelUpgradeEditRead(
            trainer.version,
            trainer.satchelUpgrades ?: error("This save does not contain the Satchel upgrade count.")
        )
    }

    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val remaining = MAX_SAVE_SIZE + 1 - output.size()
            val count = it.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= MAX_SAVE_SIZE) {
                "The document exceeds the supported Legends: Arceus save size."
            }
        }
        output.toByteArray()
    }

    fun stage(
        source: ByteArray,
        expectedSha256: String,
        replacement: Long
    ): LegendsArceusSatchelUpgradeCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(replacement in 0L..39L) { "Changed Satchel upgrades must be between 0 and 39." }
        val before = read(original)
        require(replacement != before.upgrades) { "Change the Satchel upgrade count before reviewing." }
        val write = LegendsArceusSaveValidation.writeSatchelUpgrades(original, replacement)
        val after = read(write.bytes)
        require(after.upgrades == replacement) { "The requested Satchel upgrade count did not stage exactly." }
        verify(original, write.bytes, after, write.upgradeOffset)
        return LegendsArceusSatchelUpgradeCopyPlan(original, write.bytes, before, after, write.upgradeOffset)
    }

    internal fun verify(
        original: ByteArray,
        edited: ByteArray,
        expected: LegendsArceusSatchelUpgradeEditRead,
        upgradeOffset: Int
    ) {
        val beforeValidation = LegendsArceusSaveValidation.inspect(original)
        val afterValidation = LegendsArceusSaveValidation.inspect(edited)
        require(
            beforeValidation is LegendsArceusValidation.Verified &&
                afterValidation is LegendsArceusValidation.Verified &&
                beforeValidation.version == afterValidation.version && original.size == edited.size
        ) { "The Satchel upgrade edit changed title identity or save size." }
        val before = LegendsArceusTrainerReadOnly.read(original)
        val after = LegendsArceusTrainerReadOnly.read(edited)
        require(after.satchelUpgrades == expected.upgrades) {
            "The reviewed Satchel upgrade count did not reopen exactly."
        }
        require(before.copy(satchelUpgrades = expected.upgrades) == after) {
            "A different Legends: Arceus Trainer Data value changed."
        }
        val hashOffset = original.size - 32
        require(original.indices.all { index ->
            index in upgradeOffset until upgradeOffset + FIELD_LENGTH ||
                index in hashOffset until original.size || original[index] == edited[index]
        }) { "The Satchel upgrade edit changed unrelated save data." }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use reviewed transaction to a new document; the selected source is never replaced. */
class LegendsArceusSatchelUpgradeCopyPlan internal constructor(
    original: ByteArray,
    edited: ByteArray,
    val before: LegendsArceusSatchelUpgradeEditRead,
    val after: LegendsArceusSatchelUpgradeEditRead,
    val upgradeOffset: Int
) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val editedSha256: String = LegendsArceusSatchelUpgradeEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized
    fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen Trainer Data." }
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
            LegendsArceusSatchelUpgradeEditing.verify(original, edited, after, upgradeOffset)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) {
                "The saved copy differs from the reviewed Satchel upgrade edit."
            }
            LegendsArceusSatchelUpgradeEditing.verify(original, persisted, after, upgradeOffset)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException(
                (failure.message ?: "Satchel upgrade copy verification failed.") +
                    if (removed) " The failed new copy was removed; the recovery backup is retained."
                    else " The new copy could not be removed. Do not use it; the recovery backup is retained.",
                failure
            )
        }
    }
}
