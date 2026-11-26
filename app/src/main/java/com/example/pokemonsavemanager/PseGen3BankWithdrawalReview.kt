package com.example.pokemonsavemanager

import android.app.AlertDialog
import android.net.Uri
import java.io.File

private const val PSE_NATIVE_GEN3_BOX_COUNT=14
private const val PSE_NATIVE_GEN3_SLOTS_PER_BOX=30
private const val PSE_NATIVE_GEN3_CAPACITY=PSE_NATIVE_GEN3_BOX_COUNT*PSE_NATIVE_GEN3_SLOTS_PER_BOX

internal fun MainActivity.reviewGen3BankWithdrawal(
    bankFile:File,p:MainActivity.PokemonEntry,target:Int,openedBank:ByteArray,
    openedFile:File,openedUri:Uri?,openedLabel:String?,openedDestination:ByteArray
){
    val box=target/PSE_NATIVE_GEN3_SLOTS_PER_BOX+1
    val slot=target%PSE_NATIVE_GEN3_SLOTS_PER_BOX+1
    val review=buildString{
        append("Final native Gen 3 Bank withdrawal review\n\n")
        append("Chosen destination: Box $box of $PSE_NATIVE_GEN3_BOX_COUNT, Slot $slot of $PSE_NATIVE_GEN3_SLOTS_PER_BOX, within the native $PSE_NATIVE_GEN3_CAPACITY-slot PC. The slot is structurally empty; existing and nonzero destination records remain unchanged.\n\n")
        append("No Pokémon conversion or Legalise change is performed. The exact 80-byte PK3 Bank record is copied byte-for-byte, preserving species #${p.species}, PID ${"%08X".format(p.pid)}, TID/SID ${p.tid}/${p.sid}, nickname, OT, experience, ability, nature, gender, shiny state, moves, current PP, PP Ups, IVs, EVs, friendship, Pokérus, Ball, origin, locations, ribbons and every stored native PK3 byte. The original source save is not edited.\n\n")
        append("Only the selected empty PK3 slot and required native PC-storage section checksums may change. PSE retains and verifies exact destination and Bank recovery data, reopens the destination and exact PK3, then removes the Bank record last. Cancel leaves the Bank record and destination byte-for-byte unchanged.\n\n")
        append("This is a PSE Bank withdrawal, not an official trade, Pokémon Bank or HOME transaction, legality certification or verified provenance.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${p.nickname.ifBlank{"Pokémon #${p.species}"}} • Gen 3 withdrawal")
        .setMessage(review).setPositiveButton("Withdraw • Box $box / Slot $slot"){_,_->
            if(consumed||!gen3BankWithdrawalStillBound(bankFile,p,target,openedBank,openedFile,openedUri,openedLabel,openedDestination)){
                showDialog("Gen 3 Bank withdrawal","The destination identity, exact Bank record or selected native slot changed, or this withdrawal was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            withdrawGen3BankToSlot(bankFile,p,target,openedBank,openedFile,openedUri,openedLabel,openedDestination)
        }.setNegativeButton("Cancel",null).show()
}
