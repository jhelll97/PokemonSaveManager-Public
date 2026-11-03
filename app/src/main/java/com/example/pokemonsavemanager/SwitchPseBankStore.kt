package com.example.pokemonsavemanager

import java.io.File
import java.security.MessageDigest

/** Byte-preserving local PSE Bank storage for validated native Sword/Shield PK8 records. */
object SwitchPseBankStore {
    private val recordSize = SwordShieldPokemonReadOnly.RECORD_SIZE

    fun depositSwordShield(context: android.content.Context, record: ByteArray): File {
        require(record.size == recordSize) { "A complete native PK8 record is required." }
        require(SwordShieldPokemonReadOnly.read(record) is SwordShieldSlotRead.Pokemon) {
            "Only a readable, checksum-valid Sword/Shield Pokémon can be added to PSE Bank."
        }
        val directory = File(context.filesDir, "pse_bank").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256").digest(record)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        val target = File(directory, "switch_swsh_" + digest + ".pk8")
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
                error("Could not add the PK8 record to PSE Bank.")
            }
            return target
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }
}
