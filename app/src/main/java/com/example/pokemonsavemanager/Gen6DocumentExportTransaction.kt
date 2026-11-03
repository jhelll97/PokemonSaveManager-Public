package com.example.pokemonsavemanager

/**
 * Transaction boundary for Android's create-document export.
 *
 * Some document providers can return an existing document from
 * ACTION_CREATE_DOCUMENT. The target is therefore verified empty before the
 * first write. Any failure after writing starts removes only the incomplete
 * artifact that passed that freshness check, while the source remains
 * byte-for-byte unchanged.
 */
interface Gen6DocumentExportTarget {
    fun readBeforeWrite(): ByteArray
    fun write(bytes: ByteArray)
    fun read(): ByteArray
    fun delete(): Boolean
}

object Gen6DocumentExportTransaction {
    fun exportNew(
        destinationBytes: ByteArray,
        expectedGame: Gen6ConverterGame,
        target: Gen6DocumentExportTarget,
        templateUnchanged: () -> Boolean = { true },
        sourceUnchanged: () -> Boolean
    ): ByteArray {
        val preparedDestination = destinationBytes.copyOf()
        require(preparedDestination.isNotEmpty()) {
            "Converted destination is empty."
        }
        requireValidDestination(
            preparedDestination,
            expectedGame,
            "Prepared converted destination"
        )
        check(sourceUnchanged()) {
            "The loaded source changed before destination export."
        }
        check(templateUnchanged()) {
            "The destination template changed before destination export."
        }

        val existingBytes = target.readBeforeWrite()
        require(existingBytes.isEmpty()) {
            "The selected destination already contains data. Choose a new empty document."
        }
        check(sourceUnchanged()) {
            "The loaded source changed while checking the new destination."
        }
        check(templateUnchanged()) {
            "The destination template changed while checking the new destination."
        }

        val confirmedEmptyBytes = target.readBeforeWrite()
        require(confirmedEmptyBytes.isEmpty()) {
            "The selected destination changed after its freshness check. " +
                "Choose a new empty document."
        }
        check(sourceUnchanged()) {
            "The loaded source changed during the final destination freshness check."
        }
        check(templateUnchanged()) {
            "The destination template changed during the final destination freshness check."
        }

        val writeReadyBytes = target.readBeforeWrite()
        require(writeReadyBytes.isEmpty()) {
            "The selected destination changed immediately before write. " +
                "Choose a new empty document."
        }

        var writeStarted = false
        try {
            writeStarted = true
            target.write(preparedDestination.copyOf())
            val persisted = target.read()
            require(persisted.contentEquals(preparedDestination)) {
                "The exported destination changed during write or reopen."
            }

            requireValidDestination(
                persisted,
                expectedGame,
                "Exported destination"
            )
            check(sourceUnchanged()) {
                "The loaded source changed during destination export."
            }
            check(templateUnchanged()) {
                "The destination template changed during destination export."
            }
            val finalPersisted = target.read()
            require(finalPersisted.contentEquals(persisted)) {
                "The exported destination changed during final source or template verification."
            }
            requireValidDestination(
                finalPersisted,
                expectedGame,
                "Final exported destination"
            )
            check(sourceUnchanged()) {
                "The loaded source changed during final destination verification."
            }
            check(templateUnchanged()) {
                "The destination template changed during final destination verification."
            }
            val confirmedPersisted = target.read()
            require(confirmedPersisted.contentEquals(finalPersisted)) {
                "The exported destination changed after final source or template verification."
            }
            requireValidDestination(
                confirmedPersisted,
                expectedGame,
                "Confirmed exported destination"
            )
            return confirmedPersisted.copyOf()
        } catch (failure: Throwable) {
            if (writeStarted) {
                val deleted = runCatching { target.delete() }
                    .getOrDefault(false)
                if (!deleted) {
                    throw IllegalStateException(
                        "Converted destination failed validation and its incomplete document could not be removed.",
                        failure
                    )
                }
            }
            throw failure
        }
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
            validation.isValid &&
                validation.detectedGame == expectedGame
        ) {
            "$context failed identity, integrity or reopen validation."
        }
    }
}
