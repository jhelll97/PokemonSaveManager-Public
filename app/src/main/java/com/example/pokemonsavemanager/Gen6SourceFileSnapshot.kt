package com.example.pokemonsavemanager

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

/**
 * Immutable source-file evidence shared by Gen 6 conversion and publication.
 *
 * The contents, size, timestamp and filesystem identity must all remain stable.
 * Matching bytes alone are not enough because the source path may have been
 * replaced with a different file while a destination is being prepared.
 */
internal class Gen6SourceFileSnapshot private constructor(
    private val sourceFile: File,
    private val canonicalSourceFile: File,
    bytes: ByteArray,
    private val length: Long,
    private val lastModified: Long,
    private val fileKey: Any?,
    private val directoryFileKey: Any?
) {
    private val capturedBytes = bytes.copyOf()

    val bytes: ByteArray
        get() = capturedBytes.copyOf()

    fun requireUnchanged(context: String) {
        val sourceDirectory = sourceFile.absoluteFile.parentFile
            ?: error("$context must have a parent directory.")
        check(!Files.isSymbolicLink(sourceDirectory.toPath())) {
            "$context directory became a symbolic link."
        }
        check(!Files.isSymbolicLink(sourceFile.toPath())) {
            "$context path became a symbolic link."
        }
        check(sourceFile.canonicalFile == canonicalSourceFile) {
            "$context path identity changed."
        }
        val directoryBefore = attributes(sourceDirectory)
        check(
            directoryFileKey == null ||
                directoryBefore.fileKey() == directoryFileKey
        ) {
            "$context directory identity changed."
        }
        val before = attributes(sourceFile)
        val currentBytes = sourceFile.readBytes()
        val after = attributes(sourceFile)
        val directoryAfter = attributes(sourceDirectory)
        check(!Files.isSymbolicLink(sourceFile.toPath())) {
            "$context path became a symbolic link."
        }
        check(!Files.isSymbolicLink(sourceDirectory.toPath())) {
            "$context directory became a symbolic link."
        }
        check(
            directoryFileKey == null ||
                directoryAfter.fileKey() == directoryFileKey
        ) {
            "$context directory identity changed."
        }

        check(currentBytes.contentEquals(capturedBytes)) {
            "$context bytes changed."
        }
        check(
            fileKey == null ||
                (before.fileKey() == fileKey && after.fileKey() == fileKey)
        ) {
            "$context identity changed."
        }
        check(
            before.size() == length &&
                after.size() == length &&
                before.lastModifiedTime().toMillis() == lastModified &&
                after.lastModifiedTime().toMillis() == lastModified
        ) {
            "$context metadata changed."
        }
    }

    internal fun matchesCapturedFile(): Boolean {
        val sourceDirectory = sourceFile.absoluteFile.parentFile
            ?: return false
        return runCatching {
            sourceFile.canonicalFile == canonicalSourceFile &&
                run {
                    val directoryBefore = attributes(sourceDirectory)
            val before = attributes(sourceFile)
            val currentBytes = sourceFile.readBytes()
            val after = attributes(sourceFile)
            val directoryAfter = attributes(sourceDirectory)
            !Files.isSymbolicLink(sourceDirectory.toPath()) &&
                !Files.isSymbolicLink(sourceFile.toPath()) &&
                (
                    directoryFileKey == null ||
                        (
                            directoryBefore.fileKey() == directoryFileKey &&
                                directoryAfter.fileKey() == directoryFileKey
                        )
                    ) &&
                currentBytes.contentEquals(capturedBytes) &&
                (
                    fileKey == null ||
                        (
                            before.fileKey() == fileKey &&
                                after.fileKey() == fileKey
                        )
                    ) &&
                before.size() == length &&
                after.size() == length &&
                before.lastModifiedTime().toMillis() == lastModified &&
                after.lastModifiedTime().toMillis() == lastModified
                }
        }.getOrDefault(false)
    }

    internal fun matchesCapturedIdentity(): Boolean {
        val sourceDirectory = sourceFile.absoluteFile.parentFile
            ?: return false
        return runCatching {
            // Rollback ownership is physical-file evidence. A retargeted
            // ancestor can change the canonical path while still resolving to
            // the exact captured inode that is safe to remove.
            val directoryBefore = attributes(sourceDirectory)
            val before = attributes(sourceFile)
            val after = attributes(sourceFile)
            val directoryAfter = attributes(sourceDirectory)
            fileKey != null &&
                before.isRegularFile &&
                after.isRegularFile &&
                !Files.isSymbolicLink(sourceDirectory.toPath()) &&
                !Files.isSymbolicLink(sourceFile.toPath()) &&
                (
                    directoryFileKey == null ||
                        (
                            directoryBefore.fileKey() == directoryFileKey &&
                                directoryAfter.fileKey() == directoryFileKey
                        )
                    ) &&
                before.fileKey() == fileKey &&
                after.fileKey() == fileKey
        }.getOrDefault(false)
    }

    companion object {
        fun capture(sourceFile: File): Gen6SourceFileSnapshot =
            capture(sourceFile) { it.readBytes() }

        internal fun capture(
            sourceFile: File,
            readSource: (File) -> ByteArray
        ): Gen6SourceFileSnapshot {
            val sourceDirectory = sourceFile.absoluteFile.parentFile
                ?: error("Source save must have a parent directory.")
            val canonicalSourceFile = sourceFile.canonicalFile
            require(!Files.isSymbolicLink(sourceDirectory.toPath())) {
                "Source save directory must not be a symbolic link."
            }
            require(!Files.isSymbolicLink(sourceFile.toPath())) {
                "Source save must not be a symbolic link."
            }
            require(sourceFile.isFile) {
                "Source save must be a regular file."
            }
            val directoryBefore = attributes(sourceDirectory)
            val before = attributes(sourceFile)
            val bytes = readSource(sourceFile)
            val after = attributes(sourceFile)
            val directoryAfter = attributes(sourceDirectory)
            require(!Files.isSymbolicLink(sourceFile.toPath())) {
                "Source save path became a symbolic link while it was being captured."
            }
            require(!Files.isSymbolicLink(sourceDirectory.toPath())) {
                "Source save directory became a symbolic link while it was being captured."
            }
            require(sourceFile.canonicalFile == canonicalSourceFile) {
                "Source save path identity changed while it was being captured."
            }
            require(
                directoryBefore.fileKey() == null ||
                    directoryBefore.fileKey() == directoryAfter.fileKey()
            ) {
                "Source save directory identity changed while it was being captured."
            }
            require(
                before.fileKey() == null ||
                    before.fileKey() == after.fileKey()
            ) {
                "Source save identity changed while it was being captured."
            }
            require(
                before.size() == after.size() &&
                    before.lastModifiedTime() == after.lastModifiedTime()
            ) {
                "Source save metadata changed while it was being captured."
            }
            require(bytes.size.toLong() == after.size()) {
                "Source save read was incomplete while it was being captured."
            }
            val verificationBytes = sourceFile.readBytes()
            val verified = attributes(sourceFile)
            val directoryVerified = attributes(sourceDirectory)
            require(!Files.isSymbolicLink(sourceFile.toPath())) {
                "Source save path became a symbolic link while its capture was being verified."
            }
            require(!Files.isSymbolicLink(sourceDirectory.toPath())) {
                "Source save directory became a symbolic link while its capture was being verified."
            }
            require(sourceFile.canonicalFile == canonicalSourceFile) {
                "Source save path identity changed while its capture was being verified."
            }
            require(
                directoryAfter.fileKey() == null ||
                    directoryAfter.fileKey() == directoryVerified.fileKey()
            ) {
                "Source save directory identity changed while its capture was being verified."
            }
            require(
                after.fileKey() == null ||
                    after.fileKey() == verified.fileKey()
            ) {
                "Source save identity changed while its capture was being verified."
            }
            require(
                after.size() == verified.size() &&
                    after.lastModifiedTime() == verified.lastModifiedTime()
            ) {
                "Source save metadata changed while its capture was being verified."
            }
            require(verificationBytes.size.toLong() == verified.size()) {
                "Source save verification read was incomplete."
            }
            require(bytes.contentEquals(verificationBytes)) {
                "Source save read did not match verification."
            }
            return Gen6SourceFileSnapshot(
                sourceFile = sourceFile,
                canonicalSourceFile = canonicalSourceFile,
                bytes = bytes,
                length = verified.size(),
                lastModified = verified.lastModifiedTime().toMillis(),
                fileKey = verified.fileKey(),
                directoryFileKey = directoryVerified.fileKey()
            )
        }

        private fun attributes(file: File): BasicFileAttributes =
            Files.readAttributes(
                file.toPath(),
                BasicFileAttributes::class.java
            )
    }
}
