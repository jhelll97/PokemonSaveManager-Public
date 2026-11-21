package com.example.pokemonsavemanager

import java.io.File

internal object Gen1ToGen7BankRecovery {
    fun create(directory: File, expected: ByteArray): File {
        val copy=File.createTempFile("PSE_G1ToGen7_BankRecovery_",".tmp",directory)
        copy.writeBytes(expected)
        require(matches(copy,expected)){"The exact Gen 1 Bank recovery snapshot did not verify."}
        return copy
    }

    fun matches(snapshot: File?, expected: ByteArray): Boolean =
        snapshot?.let{copy->runCatching{
            copy.isFile&&copy.readBytes().contentEquals(expected)
        }.getOrDefault(false)}?:false

    fun cleanup(snapshot: File?, expected: ByteArray): Boolean {
        if(snapshot==null)return true
        return runCatching{
            if(!snapshot.exists()||!matches(snapshot,expected))false
            else snapshot.delete()&&!snapshot.exists()
        }.getOrDefault(false)
    }

    fun failureMessage(
        bankRestored: Boolean,
        saveRestored: Boolean,
        snapshotCreated: Boolean,
        snapshotVerified: Boolean,
        snapshotClean: Boolean
    ): String = when {
        bankRestored&&saveRestored&&snapshotClean->
            "The exact Gen 7 destination and Gen 1 Bank record were restored or kept and verified."
        bankRestored&&saveRestored->
            "The exact Gen 7 destination and Gen 1 Bank record were restored or kept and verified. The temporary Bank recovery snapshot changed or could not be removed safely and was retained for review."
        bankRestored->
            "The Gen 1 Bank record was restored, but destination restoration was not verified. Use the retained destination backup before editing again."
        saveRestored&&snapshotVerified->
            "The exact Gen 7 destination was restored, but Bank restoration was not verified. The exact verified Gen 1 Bank recovery snapshot was retained for recovery."
        saveRestored->
            "The exact Gen 7 destination was restored, but Bank restoration was not verified. Keep the exact Gen 1 Bank payload for recovery."
        snapshotCreated&&snapshotVerified->
            "Automatic restoration could not be verified. Keep the retained destination backup and exact verified Gen 1 Bank recovery snapshot for review."
        else->
            "Automatic recovery could not be verified. Keep the retained destination backup and exact Gen 1 Bank payload for review."
    }

    fun retainedPath(snapshot: File?): String =
        snapshot?.takeIf{it.exists()}?.let{"\n\nBank recovery snapshot: "+it.name}?:""

    fun successNotice(retained: Boolean): String =
        if(retained)"; exact temporary Bank recovery retained for review" else ""
}
