package com.example.pokemonsavemanager

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class SwitchSafeBackupEntry internal constructor(
    val file: File, val sha256: String, val byteCount: Int, val modifiedAt: Long
)

/** Verified byte-preserving snapshots only. This store never writes the selected Switch source. */
object SwitchSafeBackupStore {
    private fun directory(context: android.content.Context): File =
        File(context.filesDir, "switch-recovery").apply {
            check(isDirectory || mkdirs()) { "The Switch backup folder could not be created." }
        }

    fun createVerified(
        context: android.content.Context, family: SwitchGameFamily, source: ByteArray
    ): SwitchSafeBackupEntry {
        require(source.isNotEmpty() && source.size <= SwitchReadSnapshot.MAX_SAVE_BYTES) {
            "The selected source is outside the Switch backup intake limit."
        }
        val snapshot = validate(family, source)
        val folder = directory(context).canonicalFile
        val target = File(folder, "switch_${family.name.lowercase()}_${snapshot.sourceSha256}.backup")
        if (target.exists()) {
            val existing = inspect(context, family, target)
            require(existing.sha256 == snapshot.sourceSha256 && target.readBytes().contentEquals(source)) {
                "An existing backup name does not contain the same bytes."
            }
            return existing
        }
        val temporary = File.createTempFile(".switch_${family.name.lowercase()}_", ".tmp", folder)
        var targetCreated = false
        try {
            FileOutputStream(temporary).use { output ->
                output.write(source); output.fd.sync()
            }
            require(temporary.readBytes().contentEquals(source)) { "The temporary backup did not persist byte-for-byte." }
            require(temporary.renameTo(target)) { "The verified backup could not be committed." }
            targetCreated = true
            return inspect(context, family, target).also { entry ->
                require(entry.sha256 == snapshot.sourceSha256 && target.readBytes().contentEquals(source)) {
                    "The committed backup changed after persistence."
                }
            }
        } catch (error: Exception) {
            temporary.delete()
            if (targetCreated) target.delete()
            throw error
        }
    }

    fun listVerified(context: android.content.Context, family: SwitchGameFamily): List<SwitchSafeBackupEntry> {
        val folder = directory(context)
        return folder.listFiles()?.asSequence()?.filter { it.isFile && it.extension.equals("backup", true) }
            ?.mapNotNull { file -> runCatching { inspect(context, family, file) }.getOrNull() }
            ?.sortedByDescending { it.modifiedAt }?.toList().orEmpty()
    }

    fun inspect(
        context: android.content.Context, family: SwitchGameFamily, candidate: File
    ): SwitchSafeBackupEntry {
        val folder = directory(context).canonicalFile
        val file = candidate.canonicalFile
        require(file.parentFile == folder && file.isFile && file.extension.equals("backup", true)) {
            "This file is outside PSE Switch Safe Backups."
        }
        require(file.length() in 1..SwitchReadSnapshot.MAX_SAVE_BYTES.toLong()) {
            "The backup is empty or exceeds the Switch backup intake limit."
        }
        val bytes = file.readBytes()
        val snapshot = validate(family, bytes)
        return SwitchSafeBackupEntry(file, snapshot.sourceSha256, bytes.size, file.lastModified())
    }

    fun readVerified(
        context: android.content.Context, family: SwitchGameFamily, candidate: File, expectedSha256: String
    ): ByteArray {
        val entry = inspect(context, family, candidate)
        require(entry.sha256 == expectedSha256) { "The backup changed after it was opened." }
        val bytes = entry.file.readBytes()
        val snapshot = validate(family, bytes)
        require(snapshot.sourceSha256 == expectedSha256 && bytes.size == entry.byteCount) {
            "The backup changed while it was being verified."
        }
        return bytes
    }

    fun validateSource(family: SwitchGameFamily, bytes: ByteArray): String =
        validate(family, bytes).sourceSha256

    fun removeVerified(
        context: android.content.Context, family: SwitchGameFamily, candidate: File, expectedSha256: String
    ) {
        val entry = inspect(context, family, candidate)
        require(entry.sha256 == expectedSha256) { "The backup changed after it was opened." }
        require(entry.file.delete() && !entry.file.exists()) { "The backup could not be removed." }
    }

    private fun validate(family: SwitchGameFamily, bytes: ByteArray): SwitchReadSnapshot {
        val snapshot = SwitchReadSnapshot.capture(family, bytes)
        val verified = when (family) {
            SwitchGameFamily.LETS_GO_PIKACHU_EEVEE -> snapshot.inspectLetsGo() is LetsGoValidation.Verified
            SwitchGameFamily.SWORD_SHIELD -> snapshot.inspectSwordShield() is SwordShieldValidation.Verified
            SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL -> snapshot.inspectBdsp() is BdspValidation.Verified
            SwitchGameFamily.LEGENDS_ARCEUS -> snapshot.inspectLegendsArceus() is LegendsArceusValidation.Verified
            SwitchGameFamily.SCARLET_VIOLET -> snapshot.inspectScarletViolet() is ScarletVioletValidation.Verified
            SwitchGameFamily.LEGENDS_ZA -> snapshot.inspectLegendsZa() is LegendsZaValidation.Verified
        }
        require(verified) { "The backup does not pass native ${family.displayName} identity and integrity validation." }
        return snapshot
    }
}
