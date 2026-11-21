package com.example.pokemonsavemanager

import java.io.File

internal object BankTransferRecovery {
    const val GEN1_TO_7=1
    const val GEN2_TO_7=2
    const val GEN2_TO_6=3
    const val GEN2_TO_5=4
    const val GEN2_TO_4=5

    fun create(directory: File, expected: ByteArray, route: Int): File {
        val(prefix,failure)=when(route){
            GEN1_TO_7->"PSE_G1ToGen7_BankRecovery_" to "The exact Gen 1 Bank recovery snapshot did not verify."
            GEN2_TO_7->"PSE_G2ToGen7_BankRecovery_" to "The exact Gen 2 Bank recovery snapshot did not verify."
            GEN2_TO_6->"PSE_G2ToGen6_BankRecovery_" to "The exact Gen 2 Bank recovery snapshot did not verify."
            GEN2_TO_5->"PSE_G2ToGen5_BankRecovery_" to "The exact Gen 2 Bank recovery snapshot did not verify."
            GEN2_TO_4->"PSE_G2ToGen4_BankRecovery_" to "The exact Gen 2 Bank recovery snapshot did not verify."
            else->error("Unsupported Bank recovery route.")
        }
        val copy=File.createTempFile(prefix,".tmp",directory)
        copy.writeBytes(expected)
        require(matches(copy,expected)){failure}
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
        route: Int,
        bankRestored: Boolean,
        saveRestored: Boolean,
        snapshotCreated: Boolean,
        snapshotVerified: Boolean,
        snapshotClean: Boolean
    ): String = when(route){
        GEN1_TO_7->gen1ToGen7FailureMessage(bankRestored,saveRestored,snapshotCreated,snapshotVerified,snapshotClean)
        GEN2_TO_7->gen2ToGen7FailureMessage(bankRestored,saveRestored,snapshotCreated,snapshotVerified,snapshotClean)
        GEN2_TO_6->gen2ToGen6FailureMessage(bankRestored,saveRestored,snapshotCreated,snapshotVerified,snapshotClean)
        GEN2_TO_5->gen2ToGen5FailureMessage(bankRestored,saveRestored,snapshotCreated,snapshotVerified,snapshotClean)
        GEN2_TO_4->gen2ToGen4FailureMessage(bankRestored,saveRestored,snapshotCreated,snapshotVerified,snapshotClean)
        else->"Automatic recovery could not be verified. Keep all retained files for review."
    }

    private fun gen1ToGen7FailureMessage(
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

    private fun gen2ToGen7FailureMessage(
        bankRestored: Boolean,
        saveRestored: Boolean,
        snapshotCreated: Boolean,
        snapshotVerified: Boolean,
        snapshotClean: Boolean
    ): String = when {
        bankRestored&&saveRestored&&snapshotClean->
            "The exact Gen 7 destination and Gen 2 Bank record were restored or kept and verified."
        bankRestored&&saveRestored->
            "The exact Gen 7 destination and Gen 2 Bank record were restored or kept and verified. The temporary Bank recovery snapshot changed or could not be removed safely and was retained for review."
        bankRestored->
            "The Gen 2 Bank record was restored or kept, but destination recovery was not verified. Use the retained recovery backup before editing again."
        saveRestored&&snapshotVerified->
            "The exact Gen 7 destination was restored or kept, but Bank recovery was not verified. The exact verified Gen 2 Bank recovery snapshot was retained for recovery."
        saveRestored->
            "The exact Gen 7 destination was restored or kept, but Bank recovery was not verified. Keep the remaining Bank record and recovery files for review."
        snapshotCreated&&snapshotVerified->
            "Automatic recovery could not be fully verified. Keep the retained destination backup and exact verified Gen 2 Bank recovery snapshot for review."
        else->
            "Automatic recovery could not be fully verified. Stop editing and keep the retained backup and remaining files for review."
    }

    private fun gen2ToGen6FailureMessage(
        bankRestored: Boolean,
        saveRestored: Boolean,
        snapshotCreated: Boolean,
        snapshotVerified: Boolean,
        snapshotClean: Boolean
    ): String = when {
        bankRestored&&saveRestored&&snapshotClean->
            "The exact Gen 6 destination and Gen 2 Bank record were restored or kept and verified."
        bankRestored&&saveRestored->
            "The exact Gen 6 destination and Gen 2 Bank record were restored or kept and verified. The temporary Bank recovery snapshot changed or could not be removed safely and was retained for review."
        bankRestored->
            "The Gen 2 Bank record was restored or kept, but destination recovery was not verified. Use the retained recovery backup before editing again."
        saveRestored&&snapshotVerified->
            "The exact Gen 6 destination was restored or kept, but Bank recovery was not verified. The exact verified Gen 2 Bank recovery snapshot was retained for recovery."
        saveRestored->
            "The exact Gen 6 destination was restored or kept, but Bank recovery was not verified. Keep the remaining Bank record and recovery files for review."
        snapshotCreated&&snapshotVerified->
            "Automatic recovery could not be fully verified. Keep the retained destination backup and exact verified Gen 2 Bank recovery snapshot for review."
        else->
            "Automatic recovery could not be fully verified. Stop editing and keep the retained backup and remaining files for review."
    }

    private fun gen2ToGen5FailureMessage(
        bankRestored: Boolean,
        saveRestored: Boolean,
        snapshotCreated: Boolean,
        snapshotVerified: Boolean,
        snapshotClean: Boolean
    ): String = when {
        bankRestored&&saveRestored&&snapshotClean->
            "The exact Gen 5 destination and Gen 2 Bank record were restored or kept and verified."
        bankRestored&&saveRestored->
            "The exact Gen 5 destination and Gen 2 Bank record were restored or kept and verified. The temporary Bank recovery snapshot changed or could not be removed safely and was retained for review."
        bankRestored->
            "The Gen 2 Bank record was restored or kept, but destination recovery was not verified. Use the retained recovery backup before editing again."
        saveRestored&&snapshotVerified->
            "The exact Gen 5 destination was restored or kept, but Bank recovery was not verified. The exact verified Gen 2 Bank recovery snapshot was retained for recovery."
        saveRestored->
            "The exact Gen 5 destination was restored or kept, but Bank recovery was not verified. Keep the remaining Bank record and recovery files for review."
        snapshotCreated&&snapshotVerified->
            "Automatic recovery could not be verified. Keep the retained destination backup and exact verified Gen 2 Bank recovery snapshot for review."
        else->
            "Automatic recovery could not be verified. Stop editing and keep the working save and remaining Bank record until a verified backup and Bank contents have been reviewed."
    }

    private fun gen2ToGen4FailureMessage(
        bankRestored: Boolean,
        saveRestored: Boolean,
        snapshotCreated: Boolean,
        snapshotVerified: Boolean,
        snapshotClean: Boolean
    ): String = when {
        bankRestored&&saveRestored&&snapshotClean->
            "The exact Gen 4 destination and Gen 2 Bank record were restored or kept and verified."
        bankRestored&&saveRestored->
            "The exact Gen 4 destination and Gen 2 Bank record were restored or kept and verified. The temporary Bank recovery snapshot changed or could not be removed safely and was retained for review."
        bankRestored->
            "The Gen 2 Bank record was restored or kept, but destination recovery was not verified. Use the retained recovery backup before editing again."
        saveRestored&&snapshotVerified->
            "The exact Gen 4 destination was restored or kept, but Bank recovery was not verified. The exact verified Gen 2 Bank recovery snapshot was retained for recovery."
        saveRestored->
            "The exact Gen 4 destination was restored or kept, but Bank recovery was not verified. Keep the remaining Bank record and recovery files for review."
        snapshotCreated&&snapshotVerified->
            "Automatic recovery could not be verified. Keep the retained destination backup and exact verified Gen 2 Bank recovery snapshot for review."
        else->
            "Automatic recovery could not be verified. Stop editing and keep the working save and remaining Bank record until a verified backup and Bank contents have been reviewed."
    }

    fun retainedPath(snapshot: File?): String =
        snapshot?.takeIf{it.exists()}?.let{"\n\nBank recovery snapshot: "+it.name}?:""

    fun successNotice(retained: Boolean): String =
        if(retained)"; exact temporary Bank recovery retained for review" else ""
}
