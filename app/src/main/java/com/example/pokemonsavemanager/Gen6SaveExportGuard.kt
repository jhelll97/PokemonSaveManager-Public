package com.example.pokemonsavemanager

import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * Non-destructive export guard shared by future Gen 6 destination serializers.
 *
 * The caller supplies already-serialized destination bytes. This class never
 * overwrites an existing path and restores the source-preservation invariant
 * if writing or verification fails.
 */
object Gen6SaveExportGuard {
    fun exportNew(
        sourceFile: File,
        requestedDestination: File,
        destinationBytes: ByteArray,
        expectedGame: Gen6ConverterGame
    ): File = exportNew(
        sourceFile = sourceFile,
        requestedDestination = requestedDestination,
        destinationBytes = destinationBytes,
        expectedGame = expectedGame,
        beforePublish = {},
        afterPublish = { _, _ -> }
    )

    internal fun exportNew(
        sourceFile: File,
        requestedDestination: File,
        destinationBytes: ByteArray,
        expectedGame: Gen6ConverterGame,
        beforePublish: () -> Unit,
        afterPublish: (File, File) -> Unit = { _, _ -> }
    ): File {
        val sourceState = Gen6SourceFileSnapshot.capture(sourceFile)
        val sourceCanonical = sourceFile.canonicalFile
        val requestedAbsolute = requestedDestination.absoluteFile
        val requestedParent = requestedAbsolute.parentFile
            ?: error("Destination must have a parent directory.")
        require(!Files.isSymbolicLink(requestedParent.toPath())) {
            "Destination directory must not be a symbolic link."
        }
        val requestedCanonical = requestedAbsolute.canonicalFile
        val destinationAliasesSource =
            sourceCanonical == requestedCanonical ||
                (
                    requestedCanonical.exists() &&
                        Files.isSameFile(
                            sourceCanonical.toPath(),
                            requestedCanonical.toPath()
                        )
                    )
        require(!destinationAliasesSource) {
            "Destination must not alias the source save."
        }
        require(
            !Files.isSymbolicLink(requestedAbsolute.toPath())
        ) {
            "Destination path must not be a symbolic link."
        }

        val preparedDestination = destinationBytes.copyOf()
        require(preparedDestination.isNotEmpty()) { "Destination save is empty." }
        requireValidDestination(
            preparedDestination,
            expectedGame,
            "Prepared destination save"
        )

        val parent = requestedCanonical.parentFile
            ?: error("Destination must have a parent directory.")
        var temporary: File? = null
        var temporaryFileKey: Any? = null
        var destination: File? = null
        var finalized = false

        try {
            require(parent.exists() && parent.isDirectory) {
                "Destination directory does not exist."
            }

            temporary = File.createTempFile(".pse-convert-", ".tmp", parent)
            temporaryFileKey = attributes(temporary).fileKey()
            temporary.writeBytes(preparedDestination.copyOf())
            check(temporary.readBytes().contentEquals(preparedDestination)) {
                "Destination save failed temporary write verification."
            }
            sourceState.requireUnchanged("Source save during export")
            beforePublish()
            require(!Files.isSymbolicLink(requestedParent.toPath())) {
                "Destination directory must not be a symbolic link."
            }
            require(!Files.isSymbolicLink(requestedAbsolute.toPath())) {
                "Destination path must not be a symbolic link."
            }

            destination = publishNewLink(
                temporary = temporary,
                requested = requestedCanonical
            )
            finalized = true
            afterPublish(requireNotNull(temporary), destination)
            check(!Files.isSymbolicLink(destination.toPath())) {
                "Published destination must not be a symbolic link."
            }

            val persisted = destination.readBytes()
            check(persisted.contentEquals(preparedDestination)) {
                "Destination save failed final verification."
            }
            requireValidDestination(
                persisted,
                expectedGame,
                "Published destination save"
            )
            sourceState.requireUnchanged("Source save during export")

            val publishedTemporary = requireNotNull(temporary)
            val publishedDestination = requireNotNull(destination)
            check(!Files.isSymbolicLink(publishedDestination.toPath())) {
                "Published destination must not be a symbolic link."
            }
            check(
                Files.isSameFile(
                    publishedTemporary.toPath(),
                    publishedDestination.toPath()
                )
            ) {
                "Published destination identity changed during verification."
            }
            check(publishedTemporary.delete()) {
                "Could not remove the temporary destination after publication."
            }
            temporary = null
            return publishedDestination
        } catch (failure: Throwable) {
            rethrowAfterFailedExportCleanup(
                temporary = temporary,
                temporaryFileKey = temporaryFileKey,
                destination = destination,
                finalized = finalized,
                failure = failure
            )
        }
    }

    internal fun rethrowAfterFailedExportCleanup(
        temporary: File?,
        temporaryFileKey: Any? = null,
        destination: File?,
        finalized: Boolean,
        failure: Throwable
    ): Nothing {
        val leftovers = mutableListOf<String>()

        fun removeIfPresent(file: File?, label: String) {
            if (file == null) return
            val removed = try {
                !Files.exists(
                    file.toPath(),
                    LinkOption.NOFOLLOW_LINKS
                ) || file.delete()
            } catch (_: SecurityException) {
                false
            }
            if (!removed) leftovers += label
        }

        val ownsTemporaryArtifact =
            temporary != null &&
                try {
                    !Files.isSymbolicLink(temporary.toPath()) &&
                        (
                            temporaryFileKey == null ||
                                attributes(temporary).fileKey() == temporaryFileKey
                            )
                } catch (_: Exception) {
                    false
                }

        val ownsPublishedDestination =
            finalized &&
                temporary != null &&
                destination != null &&
                try {
                    temporary.exists() &&
                        destination.exists() &&
                        Files.isSameFile(
                            temporary.toPath(),
                            destination.toPath()
                        )
                } catch (_: Exception) {
                    false
                }

        if (ownsTemporaryArtifact) {
            removeIfPresent(temporary, "temporary destination")
        }
        if (ownsPublishedDestination) {
            removeIfPresent(destination, "published destination")
        }

        if (leftovers.isNotEmpty()) {
            throw IllegalStateException(
                "Gen 6 export failed and could not remove " +
                    leftovers.joinToString(" and ") + ".",
                failure
            )
        }
        throw failure
    }

    private fun requireValidDestination(
        bytes: ByteArray,
        expectedGame: Gen6ConverterGame,
        context: String
    ) {
        val validationInput = bytes.copyOf()
        val validation = Gen6PartyStorageLayout.validate(
            validationInput,
            expectedGame
        )
        check(validationInput.contentEquals(bytes)) {
            "$context validation changed destination bytes."
        }
        require(
            validation.isValid && validation.detectedGame == expectedGame
        ) {
            "$context failed identity, integrity or reopen validation."
        }
    }

    /**
     * Hard-link publication is atomic on the destination filesystem and fails
     * if the candidate already exists. This prevents a race from replacing a
     * file created after collision checking. The temporary file lives in the
     * same directory, so publication never crosses filesystems.
     */
    private fun publishNewLink(temporary: File, requested: File): File {
        for (index in 0..9999) {
            val candidate = collisionCandidate(requested, index)
            try {
                Files.createLink(candidate.toPath(), temporary.toPath())
                return candidate
            } catch (_: FileAlreadyExistsException) {
                // A pre-existing or concurrently created file is never replaced.
            } catch (unsupported: UnsupportedOperationException) {
                throw IllegalStateException(
                    "Destination filesystem does not support atomic non-overwriting export.",
                    unsupported
                )
            }
        }
        error("Could not find a collision-free destination filename.")
    }

    private fun attributes(file: File): BasicFileAttributes =
        Files.readAttributes(
            file.toPath(),
            BasicFileAttributes::class.java
        )

    private fun collisionCandidate(requested: File, index: Int): File {
        if (index == 0) return requested
        val parent = requested.parentFile
            ?: error("Destination must have a parent directory.")
        val stem = requested.nameWithoutExtension
        val extension = requested.extension
            .takeIf { it.isNotEmpty() }
            ?.let { ".$it" }
            .orEmpty()
        return File(parent, "$stem ($index)$extension")
    }
}
