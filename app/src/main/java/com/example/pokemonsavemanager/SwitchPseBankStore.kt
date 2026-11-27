package com.example.pokemonsavemanager

import java.io.File
import java.security.MessageDigest

data class SwitchPseBankBatchDepositResult(
    val entries: List<File>,
    val createdEntries: List<File>
)

/** Byte-preserving local PSE Bank storage for validated native Switch Pokémon records. */
object SwitchPseBankStore {
    fun depositLetsGo(context: android.content.Context, record: ByteArray): File {
        require(record.size == LetsGoPokemonReadOnly.RECORD_SIZE) { "A complete native PB7 record is required." }
        require(LetsGoPokemonReadOnly.read(record) is LetsGoSlotRead.Pokemon) {
            "Only a readable, checksum-valid Let's Go Pokémon can be added to PSE Bank."
        }
        return deposit(context, "switch_lgpe_", "pb7", record)
    }

    fun depositBdsp(context: android.content.Context, record: ByteArray): File {
        require(record.size == BdspPokemonReadOnly.RECORD_SIZE) { "A complete native PB8 record is required." }
        require(BdspPokemonReadOnly.read(record) is BdspSlotRead.Pokemon) {
            "Only a readable, checksum-valid BDSP Pokémon can be added to PSE Bank."
        }
        return deposit(context, "switch_bdsp_", "pb8", record)
    }

    fun depositLegendsArceus(context: android.content.Context, record: ByteArray): File {
        require(record.size == LegendsArceusPokemonReadOnly.STORED_SIZE ||
            record.size == LegendsArceusPokemonReadOnly.PARTY_SIZE) {
            "A complete native stored or party PA8 record is required."
        }
        require(LegendsArceusPokemonReadOnly.read(record) is LegendsArceusSlotRead.Pokemon) {
            "Only a readable, checksum-valid Legends: Arceus Pokémon can be added to PSE Bank."
        }
        return deposit(context, "switch_pla_", "pa8", record)
    }

    fun depositScarletViolet(context: android.content.Context, record: ByteArray): File {
        require(record.size == ScarletVioletPokemonReadOnly.RECORD_SIZE) { "A complete native PK9 record is required." }
        require(ScarletVioletPokemonReadOnly.read(record) is ScarletVioletSlotRead.Pokemon) {
            "Only a readable, checksum-valid Scarlet/Violet Pokémon can be added to PSE Bank."
        }
        return deposit(context, "switch_sv_", "pk9", record)
    }
    fun depositLegendsZa(context: android.content.Context, record: ByteArray): File {
        require(record.size == LegendsZaPokemonReadOnly.RECORD_SIZE) { "A complete native PA9 record is required." }
        require(LegendsZaPokemonReadOnly.read(record) is LegendsZaSlotRead.Pokemon) {
            "Only a readable, checksum-valid Legends: Z-A Pokémon can be added to PSE Bank."
        }
        return deposit(context, "switch_za_", "pa9", record)
    }
    fun depositSwordShield(context: android.content.Context, record: ByteArray): File {
        require(record.size == SwordShieldPokemonReadOnly.RECORD_SIZE) { "A complete native PK8 record is required." }
        require(SwordShieldPokemonReadOnly.read(record) is SwordShieldSlotRead.Pokemon) {
            "Only a readable, checksum-valid Sword/Shield Pokémon can be added to PSE Bank."
        }
        return deposit(context, "switch_swsh_", "pk8", record)
    }

    fun depositSwordShieldBatch(
        context: android.content.Context,
        records: List<ByteArray>
    ): SwitchPseBankBatchDepositResult {
        require(records.isNotEmpty()) { "Choose at least one native Sword/Shield Pokémon." }
        val preserved = records.map { record ->
            require(record.size == SwordShieldPokemonReadOnly.RECORD_SIZE) {
                "Every selected record must be a complete native PK8."
            }
            require(SwordShieldPokemonReadOnly.read(record) is SwordShieldSlotRead.Pokemon) {
                "Every selected Sword/Shield Pokémon must remain readable and checksum-valid."
            }
            record.copyOf()
        }
        val directory = File(context.filesDir, "pse_bank").apply { mkdirs() }.canonicalFile
        val targets = preserved.map { record ->
            val digest = sha256(record)
            File(directory, "switch_swsh_$digest.pk8") to record
        }
        val uniqueTargets = LinkedHashMap<String, Pair<File, ByteArray>>()
        targets.forEach { (target, record) -> uniqueTargets.putIfAbsent(target.name, target to record) }
        val created = mutableListOf<Pair<File, ByteArray>>()
        try {
            uniqueTargets.values.forEach { (target, record) ->
                require(target.canonicalFile.parentFile == directory) { "The PSE Bank destination is unavailable." }
                if (target.exists()) {
                    require(target.isFile && target.readBytes().contentEquals(record)) {
                        "A Sword/Shield Bank entry no longer matches its content hash. Nothing was changed."
                    }
                } else {
                    val temporary = File(directory, ".${target.name}.${System.nanoTime()}.tmp")
                    try {
                        temporary.writeBytes(record)
                        require(temporary.readBytes().contentEquals(record)) {
                            "A staged Sword/Shield Bank entry failed verification."
                        }
                        require(temporary.renameTo(target)) {
                            "A native Sword/Shield Bank entry could not be committed."
                        }
                        created += target to record
                        require(target.readBytes().contentEquals(record)) {
                            "A committed Sword/Shield Bank entry failed verification."
                        }
                    } finally {
                        if (temporary.exists()) temporary.delete()
                    }
                }
            }
            uniqueTargets.values.forEach { (target, record) ->
                require(target.isFile && target.readBytes().contentEquals(record)) {
                    "The complete Sword/Shield Bank batch did not verify."
                }
            }
            return SwitchPseBankBatchDepositResult(
                targets.map { it.first }, created.map { it.first }
            )
        } catch (failure: Exception) {
            val retained = created.mapNotNull { (target, record) ->
                if (!target.exists()) null
                else if (runCatching { target.readBytes().contentEquals(record) && target.delete() }
                        .getOrDefault(false)) null
                else target.name
            }
            throw IllegalStateException(
                (failure.message ?: "The Sword/Shield Bank batch could not be copied.") +
                    if (retained.isEmpty()) " No new Bank entries were retained."
                    else " These verified new entries could not be rolled back: ${retained.joinToString()}",
                failure
            )
        }
    }

    private fun deposit(
        context: android.content.Context,
        prefix: String,
        extension: String,
        record: ByteArray
    ): File {
        val directory = File(context.filesDir, "pse_bank").apply { mkdirs() }
        val digest = sha256(record)
        val target = File(directory, prefix + digest + "." + extension)
        if (target.exists()) {
            require(target.readBytes().contentEquals(record)) { "Bank entry hash collision; no data was changed." }
            return target
        }
        val temporary = File(directory, "." + target.name + ".tmp")
        try {
            temporary.writeBytes(record)
            require(temporary.readBytes().contentEquals(record)) { "Bank write verification failed." }
            if (!temporary.renameTo(target)) {
                if (target.exists() && target.readBytes().contentEquals(record)) return target
                error("Could not add the native Pokémon record to PSE Bank.")
            }
            require(target.readBytes().contentEquals(record)) { "Persisted Bank verification failed." }
            return target
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun sha256(record: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(record)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
