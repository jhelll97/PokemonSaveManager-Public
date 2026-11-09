package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors

/** Restores one native Legends: Arceus PSE Bank entry into a reviewed new save copy. */
class SwitchLegendsArceusPseBankRestoreActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2151
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var bankEntryName: String? = null
    private var readyDestinations: List<LegendsArceusPseBankDestination> = emptyList()
    private var readyPlan: LegendsArceusPseBankRestoreCopyPlan? = null
    private var pendingPlan: LegendsArceusPseBankRestoreCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        bankEntryName = intent?.getStringExtra("pse.bank.entry")
        if (sourceUri == null || expectedHash.isNullOrBlank() || bankEntryName.isNullOrBlank()) {
            finish()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Legends: Arceus • PSE Bank"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Restore to Pastures"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "PSE restores the exact checksum-valid PA8 stored core to one empty native pasture slot you choose in a new save copy. " +
                "Only same-format Legends: Arceus records are accepted. Eggs, Noble records, invalid native limits, and species/forms without a uniquely verified game profile remain protected. " +
                "The record's existing species, form, moves, trainer data, and encounter fields are preserved; this bounded restore does not certify encounter or moveset legality."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source and PSE Bank entry…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        review = Button(this).apply {
            text = "Choose destination & review"
            isEnabled = false
            setOnClickListener { showDestinationPasturePicker() }
        }
        root.addView(review)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"
            visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply { text = "Back to PSE Bank"; setOnClickListener { if (!busy) finish() } })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val source = readSource()
                val hash = expectedHash ?: error("Reopen the source from PSE.")
                require(LegendsArceusPseBankRestoreEditing.sha256(source) == hash) {
                    "The source changed. Reopen it from PSE."
                }
                val bank = readBankRecord()
                val pokemon = LegendsArceusPseBankRestoreEditing.inspectBankRecord(bank)
                val destinations = LegendsArceusPseBankRestoreEditing.emptyDestinations(source)
                require(destinations.isNotEmpty()) {
                    "All 960 native Legends: Arceus pasture slots are occupied."
                }
                Triple(pokemon, destinations, LegendsArceusPseBankRestoreEditing.sha256(bank))
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (pokemon, destinations, bankHash) ->
                    readyDestinations = destinations
                    status.text = "Native source and Bank integrity verified\n" +
                        "${LegendsArceusSpeciesNames.name(pokemon.species)} (#${pokemon.species}) • EXP ${pokemon.experience}\n" +
                        "${destinations.size} empty native destination slots available\n" +
                        "Source SHA-256: ${expectedHash}\nBank SHA-256: $bankHash"
                }.onFailure { status.text = it.message ?: "The PSE Bank restore could not be prepared safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        review.isEnabled = !value && readyDestinations.isNotEmpty()
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun showDestinationPasturePicker() {
        if (busy || readyDestinations.isEmpty()) return
        val pastures = readyDestinations.groupBy { it.pasture }.toSortedMap()
        val labels = pastures.map { (pasture, slots) ->
            "Pasture $pasture • ${slots.size} empty slot${if (slots.size == 1) "" else "s"}"
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Choose Legends: Arceus Pasture")
            .setItems(labels) { _, index ->
                val pasture = pastures.keys.elementAt(index)
                showDestinationSlotPicker(pasture, pastures.getValue(pasture))
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showDestinationSlotPicker(
        pasture: Int,
        destinations: List<LegendsArceusPseBankDestination>
    ) {
        if (busy) return
        val labels = destinations.map { "Slot ${it.position}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Pasture $pasture • choose empty slot")
            .setItems(labels) { _, index -> prepareDestinationReview(destinations[index]) }
            .setNegativeButton("Back") { _, _ -> showDestinationPasturePicker() }
            .show()
    }

    private fun prepareDestinationReview(destination: LegendsArceusPseBankDestination) {
        if (busy || destination !in readyDestinations) return
        setBusy(true)
        status.text = "Binding Pasture ${destination.pasture}, slot ${destination.position} to the restore review…"
        worker.execute {
            val result = runCatching {
                val source = readSource()
                val hash = expectedHash ?: error("Reopen the source from PSE.")
                val bank = readBankRecord()
                LegendsArceusPseBankRestoreEditing.stage(
                    source, hash, bank, destination.pasture, destination.position
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { plan ->
                    readyPlan = plan
                    setBusy(false)
                    showReview(plan)
                }.onFailure {
                    readyPlan = null
                    status.text = it.message ?: "The selected destination could not be prepared safely."
                    setBusy(false)
                }
            }
        }
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return LegendsArceusPseBankRestoreEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = LegendsArceusPseBankRestoreEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun bankFile(): File {
        val name = bankEntryName ?: error("Reopen PSE Bank.")
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = File(directory, name).canonicalFile
        require(entry.parentFile == directory && entry.isFile &&
            entry.name.startsWith("switch_pla_") && entry.extension == "pa8") {
            "This is not a current Legends: Arceus PSE Bank entry."
        }
        return entry
    }

    private fun readBankRecord(): ByteArray {
        val entry = bankFile()
        val bytes = entry.readBytes()
        val hash = LegendsArceusPseBankRestoreEditing.sha256(bytes)
        require(entry.name == "switch_pla_${hash}.pa8") {
            "The PSE Bank filename no longer matches its native record bytes."
        }
        LegendsArceusPseBankRestoreEditing.inspectBankRecord(bytes)
        return bytes
    }

    private fun showReview(plan: LegendsArceusPseBankRestoreCopyPlan) {
        if (busy || plan !== readyPlan) return
        setBusy(true)
        var dispatched = false
        val p = plan.pokemon
        val moves = p.moves.filter { it.id != 0 }.joinToString { "#${it.id} (PP ${it.pp})" }.ifBlank { "None" }
        val formatNote = if (plan.partyTailBytes == 0) {
            "The complete 360-byte stored PA8 is written unchanged."
        } else {
            "This Bank entry includes a ${plan.partyTailBytes}-byte party-only tail with no pasture equivalent. " +
                "Its exact 360-byte stored core is written; the unchanged complete entry remains in PSE Bank."
        }
        AlertDialog.Builder(this).setTitle("Restore this Bank Pokémon to a new copy?")
            .setMessage(
                "${LegendsArceusSpeciesNames.name(p.species)} (#${p.species}) • Form ${p.form} • EXP ${p.experience}\n" +
                    "Nickname: ${p.nickname.ifBlank { "None" }} • Shiny: ${if (p.shiny) "Yes" else "No"}\n" +
                    "OT ${p.originalTrainer} • TID ${p.tid} / SID ${p.sid}\nMoves: $moves\n\n" +
                    "Destination: native Pasture ${plan.pasture}, slot ${plan.position}\n" +
                    "$formatNote\n\n" +
                    "Allowed native byte scope: one previously empty 360-byte PA8 pasture slot at 0x${plan.absoluteOffset.toString(16).uppercase()} " +
                    "plus the native SHA-256 footer. Every existing pasture slot, all party data, and all unrelated bytes remain exact. " +
                    "PSE verifies the unchanged source and Bank entry, retains a verified recovery backup, reopens the persisted copy, and removes a failed copy when possible. " +
                    "The Bank entry remains available. Choose a NEW empty document."
            )
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { if (!dispatched) setBusy(false) }
            .setPositiveButton("Choose new copy") { _, _ ->
                if (!dispatched && !isFinishing && !isDestroyed) {
                    dispatched = true
                    pendingPlan = plan
                    verifiedCopy = null
                    inspectCopy.visibility = android.view.View.GONE
                    try {
                        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            type = "application/octet-stream"
                            addCategory(Intent.CATEGORY_OPENABLE)
                            putExtra(Intent.EXTRA_TITLE, "SaveData.bin")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }, createCopyRequest)
                    } catch (failure: Exception) {
                        pendingPlan = null
                        setBusy(false)
                        status.text = failure.message ?: "The save picker could not be opened. Nothing was written."
                    }
                }
            }.show()
    }

    private fun inspectVerifiedCopy() {
        if (busy) return
        val (uri, hash) = verifiedCopy ?: return
        startActivity(Intent(this, SwitchReadOnlyActivity::class.java).setData(uri)
            .putExtra("pse.switch.family", SwitchGameFamily.LEGENDS_ARCEUS.name)
            .putExtra("pse.switch.sha256", hash)
            .putExtra("pse.switch.section", "storage")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != createCopyRequest) return
        val plan = pendingPlan
        pendingPlan = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || plan == null) {
            status.text = "Save cancelled or review expired. Nothing was written."
            setBusy(false)
            return
        }
        if (uri.scheme != "content" || uri == sourceUri) {
            status.text = "Choose a new document separate from the source. Nothing was written."
            setBusy(false)
            return
        }
        setBusy(true)
        status.text = "Saving and verifying a new copy…"
        worker.execute {
            var backupName: String? = null
            val result = runCatching {
                plan.saveNewCopy(
                    readSource = { readSource() },
                    readBankRecord = { readBankRecord() },
                    retainBackup = { original ->
                        val backup = SwitchSafeBackupStore.createVerified(
                            this, SwitchGameFamily.LEGENDS_ARCEUS, original
                        )
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.LEGENDS_ARCEUS, backup.file, backup.sha256
                        )
                    },
                    readDestination = { readDocument(uri) },
                    writeDestination = { edited ->
                        val output = contentResolver.openOutputStream(uri, "wt")
                            ?: error("The new document is not writable.")
                        output.use { it.write(edited); it.flush() }
                    },
                    discardDestination = { DocumentsContract.deleteDocument(contentResolver, uri) }
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                verifiedCopy = result.getOrNull()?.let { uri to plan.editedSha256 }
                inspectCopy.visibility = if (verifiedCopy != null) android.view.View.VISIBLE else android.view.View.GONE
                status.text = result.fold(
                    onSuccess = {
                        "PSE Bank restore saved and reopened successfully.\n" +
                            "Pasture ${plan.pasture}, slot ${plan.position} • SHA-256: ${plan.editedSha256}\n" +
                            "Recovery backup: $backupName\nOriginal source and Bank entry unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The PSE Bank restore copy could not be verified.") +
                            "\nRecovery backup: ${backupName ?: "not created"}"
                    }
                )
                readyPlan = null
                readyDestinations = emptyList()
                setBusy(false)
            }
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
