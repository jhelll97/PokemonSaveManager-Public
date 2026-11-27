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

private data class LegendsZaBatchRestoreCandidate(
    val name: String,
    val record: ByteArray,
    val pokemon: LegendsZaPokemonRead,
    val sha256: String
)

/** Restores a reviewed selection of exact native PA9 Bank entries into one new save copy. */
class SwitchLegendsZaPseBankBatchRestoreActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2154
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var candidates: List<LegendsZaBatchRestoreCandidate> = emptyList()
    private var emptyDestinations: List<LegendsZaPseBankDestination> = emptyList()
    private var readyPlan: LegendsZaPseBankBatchRestoreCopyPlan? = null
    private var readySelection: List<LegendsZaBatchRestoreCandidate> = emptyList()
    private var pendingPlan: LegendsZaPseBankBatchRestoreCopyPlan? = null
    private var pendingSelection: List<LegendsZaBatchRestoreCandidate> = emptyList()
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var choose: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        if (sourceUri == null || expectedHash.isNullOrBlank()) {
            finish()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Legends: Z-A • PSE Bank"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Restore selected native PA9 batch"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "Choose two or more checksum-valid native Legends: Z-A PA9 Bank entries. PSE binds each exact content-addressed entry to a distinct empty PC-box slot, shows the full mapping for review, and writes one new save copy. Existing slots, Party data, Pokédex data, title/revision identity, source save and Bank entries remain unchanged. Converted entries stay on their explicit one-record Legalise routes."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Reopening the source and native Bank entries…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        choose = Button(this).apply {
            text = "Choose native Bank entries"
            isEnabled = false
            setOnClickListener { showSelection() }
        }
        root.addView(choose)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"
            visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply { text = "Back to PSE Bank"; setOnClickListener { if (!busy) finish() } })
        setContentView(ScrollView(this).apply { addView(root) })
        loadCandidates()
    }

    private fun loadCandidates() {
        setBusy(true)
        worker.execute {
            val result = runCatching {
                val source = readVerifiedSource()
                bankCandidates() to LegendsZaPseBankRestoreEditing.emptyDestinations(source)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (entries, destinations) ->
                    candidates = entries
                    emptyDestinations = destinations
                    status.text = when {
                        entries.size < 2 -> "At least two checksum-valid native PA9 Bank entries are required. Converted entries remain available through their explicit Legalise routes."
                        destinations.size < 2 -> "At least two empty native Legends: Z-A PC-box slots are required. No occupied slot can be replaced."
                        else -> "${entries.size} native PA9 Bank entries verified\n${destinations.size} empty native PC-box slots available\nSource SHA-256: $expectedHash"
                    }
                }.onFailure {
                    status.text = it.message ?: "The native Bank batch could not be prepared safely."
                }
                setBusy(false)
            }
        }
    }

    private fun showSelection() {
        if (busy || candidates.size < 2 || emptyDestinations.size < 2) return
        val selected = BooleanArray(candidates.size)
        val labels = candidates.map { candidate ->
            val species = ScarletVioletSpeciesNames.name(candidate.pokemon.species)
            val name = candidate.pokemon.nickname.takeIf { it.isNotBlank() && it != species }
                ?.let { "$it ($species)" } ?: species
            "$name • Lv ${candidate.pokemon.cachedLevel}" +
                (if (candidate.pokemon.shiny) " • Shiny" else "") + "\n${candidate.name}"
        }.toTypedArray()
        var bulkButton: Button? = null
        var reviewButton: Button? = null
        fun refreshSelectionActions() {
            bulkButton?.text = if (selected.all { it }) "Clear all" else "Select all"
            reviewButton?.text = "Review destinations (${selected.count { it }})"
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Choose native PA9 entries")
            .setMultiChoiceItems(labels, selected) { _, index, checked ->
                selected[index] = checked
                refreshSelectionActions()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Select all", null)
            .setPositiveButton("Review destinations (0)", null)
            .create()
        dialog.setOnShowListener {
            bulkButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            reviewButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            refreshSelectionActions()
            bulkButton?.setOnClickListener {
                val check = selected.any { !it }
                selected.indices.forEach { index ->
                    selected[index] = check
                    dialog.listView.setItemChecked(index, check)
                }
                refreshSelectionActions()
            }
            reviewButton?.setOnClickListener {
                val chosen = candidates.filterIndexed { index, _ -> selected[index] }
                when {
                    chosen.size < 2 -> status.text = "Choose at least two native PA9 entries. Nothing was written."
                    chosen.size > emptyDestinations.size -> status.text = "Choose no more than ${emptyDestinations.size} entries; every restore requires a distinct empty slot."
                    else -> {
                        dialog.dismiss()
                        prepareReview(chosen)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun prepareReview(chosen: List<LegendsZaBatchRestoreCandidate>) {
        if (busy) return
        setBusy(true)
        status.text = "Reopening every selected Bank entry and binding empty destinations…"
        worker.execute {
            val result = runCatching {
                val source = readVerifiedSource()
                val current = bankCandidates().associateBy { it.name }
                val exact = chosen.map { reviewed ->
                    current[reviewed.name]?.takeIf { it.record.contentEquals(reviewed.record) }
                        ?: error("${reviewed.name} changed during review.")
                }
                val destinations = LegendsZaPseBankRestoreEditing.emptyDestinations(source).take(exact.size)
                require(destinations.size == exact.size) {
                    "There are no longer enough empty PC-box slots for this batch."
                }
                val plan = LegendsZaPseBankBatchRestoreEditing.stage(
                    source, expectedHash ?: error("Reopen the source from PSE."),
                    exact.map { it.record }, destinations
                )
                plan to exact
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setBusy(false)
                result.onSuccess { (plan, exact) ->
                    readyPlan = plan
                    readySelection = exact
                    showFinalReview(plan, exact)
                }.onFailure {
                    readyPlan = null
                    readySelection = emptyList()
                    status.text = it.message ?: "The native batch destinations could not be prepared safely."
                }
            }
        }
    }

    private fun showFinalReview(
        plan: LegendsZaPseBankBatchRestoreCopyPlan,
        selection: List<LegendsZaBatchRestoreCandidate>
    ) {
        if (busy || plan !== readyPlan || selection != readySelection) return
        setBusy(true)
        var dispatched = false
        val message = buildString {
            append("Restore ${selection.size} exact native PA9 Bank entries into one new Legends: Z-A save copy?\n\n")
            plan.items.forEachIndexed { index, item ->
                val candidate = selection[index]
                val species = ScarletVioletSpeciesNames.name(candidate.pokemon.species)
                append("• ${candidate.name}\n  $species (#${candidate.pokemon.species}) • Lv ${candidate.pokemon.cachedLevel}\n")
                append("  SHA-256: ${candidate.sha256}\n  → Box ${item.box}, slot ${item.position} (0x${item.absoluteOffset.toString(16).uppercase()})\n")
            }
            append("\nOnly the 344-byte PA9 body in each reviewed empty 408-byte PC slot and the native SHA-256 footer may change. Every 64-byte slot footer, occupied PC slot, Party byte, Pokédex byte, source byte, title/revision identity and selected Bank entry remains exact. PSE retains a verified recovery backup, reopens the persisted copy, and removes a failed copy when possible. Choose a NEW empty document.")
        }
        AlertDialog.Builder(this).setTitle("Review native Bank batch restore")
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { if (!dispatched) setBusy(false) }
            .setPositiveButton("Choose new copy") { _, _ ->
                if (!dispatched && !isFinishing && !isDestroyed) {
                    dispatched = true
                    pendingPlan = plan
                    pendingSelection = selection
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
                        pendingSelection = emptyList()
                        setBusy(false)
                        status.text = failure.message ?: "The save picker could not be opened. Nothing was written."
                    }
                }
            }.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != createCopyRequest) return
        val plan = pendingPlan
        val selection = pendingSelection
        pendingPlan = null
        pendingSelection = emptyList()
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || plan == null || selection.isEmpty()) {
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
        status.text = "Saving and reopening the reviewed native Bank batch copy…"
        worker.execute {
            var backupName: String? = null
            val result = runCatching {
                plan.saveNewCopy(
                    readSource = { readVerifiedSource() },
                    readBankRecords = { readExactBankRecords(selection) },
                    retainBackup = { original ->
                        val backup = SwitchSafeBackupStore.createVerified(
                            this, SwitchGameFamily.LEGENDS_ZA, original
                        )
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.LEGENDS_ZA, backup.file, backup.sha256
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
                        "Native Legends: Z-A Bank batch restored and reopened successfully.\n" +
                            plan.items.joinToString("\n") { "Box ${it.box}, slot ${it.position} • ${it.bankSha256}" } +
                            "\nNew copy SHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\nOriginal source and all Bank entries unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The native Bank batch restore copy could not be verified.") +
                            "\nRecovery backup: ${backupName ?: "not created"}"
                    }
                )
                readyPlan = null
                readySelection = emptyList()
                candidates = emptyList()
                emptyDestinations = emptyList()
                setBusy(false)
            }
        }
    }

    private fun inspectVerifiedCopy() {
        if (busy) return
        val (uri, hash) = verifiedCopy ?: return
        startActivity(Intent(this, SwitchReadOnlyActivity::class.java).setData(uri)
            .putExtra("pse.switch.family", SwitchGameFamily.LEGENDS_ZA.name)
            .putExtra("pse.switch.sha256", hash)
            .putExtra("pse.switch.section", "storage")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun readVerifiedSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the selected Legends: Z-A save from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        val bytes = LegendsZaPseBankRestoreEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
        require(LegendsZaPseBankRestoreEditing.sha256(bytes) == expectedHash) {
            "The selected save changed. Reopen it from PSE."
        }
        return bytes
    }

    private fun readDocument(uri: Uri): ByteArray = LegendsZaPseBankRestoreEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun bankCandidates(): List<LegendsZaBatchRestoreCandidate> {
        val directory = File(filesDir, "pse_bank").apply { mkdirs() }.canonicalFile
        return directory.listFiles()?.filter {
            it.isFile && it.name.startsWith("switch_za_") && it.extension.equals("pa9", true)
        }?.sortedBy { it.name }?.map { listed ->
            val file = listed.canonicalFile
            require(file.parentFile == directory) { "A PSE Bank entry escaped native storage." }
            val record = file.readBytes()
            val hash = LegendsZaPseBankRestoreEditing.sha256(record)
            require(file.name == "switch_za_${hash}.pa9") {
                "${file.name} no longer matches its native PA9 bytes."
            }
            LegendsZaBatchRestoreCandidate(
                file.name, record, LegendsZaPseBankRestoreEditing.inspectBankRecord(record), hash
            )
        }.orEmpty()
    }

    private fun readExactBankRecords(selection: List<LegendsZaBatchRestoreCandidate>): List<ByteArray> {
        val current = bankCandidates().associateBy { it.name }
        return selection.map { reviewed ->
            current[reviewed.name]?.takeIf { it.record.contentEquals(reviewed.record) }?.record
                ?: error("${reviewed.name} changed after review.")
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        choose.isEnabled = !value && candidates.size >= 2 && emptyDestinations.size >= 2
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() {
        pendingPlan = null
        pendingSelection = emptyList()
        worker.shutdown()
        super.onDestroy()
    }
}
