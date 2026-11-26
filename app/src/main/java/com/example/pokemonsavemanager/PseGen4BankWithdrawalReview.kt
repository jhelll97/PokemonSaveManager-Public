package com.example.pokemonsavemanager

import android.app.AlertDialog
import android.net.Uri
import java.io.File

private const val PSE_NATIVE_GEN4_BOX_COUNT=18
private const val PSE_NATIVE_GEN4_SLOTS_PER_BOX=30
private const val PSE_NATIVE_GEN4_CAPACITY=PSE_NATIVE_GEN4_BOX_COUNT*PSE_NATIVE_GEN4_SLOTS_PER_BOX

internal fun MainActivity.reviewGen4BankWithdrawal(
    bankFile:File,p:MainActivity.PokemonEntry,target:Int,openedBank:ByteArray,
    openedFile:File,openedUri:Uri?,openedLabel:String?,openedDestination:ByteArray
){
    val box=target/PSE_NATIVE_GEN4_SLOTS_PER_BOX+1
    val slot=target%PSE_NATIVE_GEN4_SLOTS_PER_BOX+1
    val review=buildString{
        append("Final native Gen 4 Bank withdrawal review\n\n")
        append("Chosen destination: Box $box of $PSE_NATIVE_GEN4_BOX_COUNT, Slot $slot of $PSE_NATIVE_GEN4_SLOTS_PER_BOX, within the native $PSE_NATIVE_GEN4_CAPACITY-slot PC. The slot is structurally empty; existing and nonzero destination records remain unchanged.\n\n")
        append("No Pokemon conversion or Legalise change is performed. The exact 136-byte PK4 Bank record is copied byte-for-byte, preserving species #${p.species}, PID ${"%08X".format(p.pid)}, TID/SID ${p.tid}/${p.sid}, encryption and checksum data, nickname, OT, experience, ability, nature, gender, form, shiny state, moves, current PP, PP Ups, IVs, EVs, friendship, Pokerus, Ball, origin, locations, dates, ribbons and every stored native PK4 byte. The original source save is not edited.\n\n")
        append("Only the selected empty PK4 slot and required active native PC-storage checksum may change. PSE retains and verifies exact destination and Bank recovery data, reopens the destination and exact PK4, then removes the Bank record last. Cancel leaves the Bank record and destination byte-for-byte unchanged.\n\n")
        append("This is a PSE Bank withdrawal, not an official trade, Pokemon Bank or HOME transaction, legality certification or verified provenance.")
    }
    var consumed=false
    AlertDialog.Builder(this).setTitle("${p.nickname.ifBlank{"Pokemon #${p.species}"}} • Gen 4 withdrawal")
        .setMessage(review).setPositiveButton("Withdraw • Box $box / Slot $slot"){_,_->
            if(consumed||!gen4BankWithdrawalStillBound(bankFile,p,target,openedBank,openedFile,openedUri,openedLabel,openedDestination)){
                showDialog("Gen 4 Bank withdrawal","The destination identity, exact Bank record or selected native slot changed, or this withdrawal was already submitted. Nothing was changed.")
                return@setPositiveButton
            }
            consumed=true
            withdrawGen4BankToSlot(bankFile,p,target,openedFile,openedDestination,openedUri,openedLabel,openedBank)
        }.setNegativeButton("Cancel",null).show()
}
