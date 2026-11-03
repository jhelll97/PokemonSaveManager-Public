package com.example.pokemonsavemanager

import java.io.File
import java.security.MessageDigest

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
    fun depositSwordShield(context: android.content.Context, record: ByteArray): File {
        require(record.size == SwordShieldPokemonReadOnly.RECORD_SIZE) { "A complete native PK8 record is required." }
        require(SwordShieldPokemonReadOnly.read(record) is SwordShieldSlotRead.Pokemon) {
            "Only a readable, checksum-valid Sword/Shield Pokémon can be added to PSE Bank."
        }
        return deposit(context, "switch_swsh_", "pk8", record)
    }

    private fun deposit(
        context: android.content.Context,
        prefix: String,
        extension: String,
        record: ByteArray
    ): File {
        val directory = File(context.filesDir, "pse_bank").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256").digest(record)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
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
}
