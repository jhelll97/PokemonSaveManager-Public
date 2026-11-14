package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/** BDSP Events imports one supplied native fateful-event PB8 into a reviewed new save copy. */
class SwitchBdspEventActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val pickEventRequest = 2115
    private val createCopyRequest = 2116
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var opened: ByteArray? = null
    private var pendingPlan: BdspEventCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var chooseEvent: Button
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
        root.addView(TextView(this).apply { text = "BDSP • Events"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "Import one native fateful-event PB8 into the first empty PC-box slot. " +
                "PSE preserves the supplied 344 bytes exactly and does not claim or invent distribution provenance."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        chooseEvent = Button(this).apply {
            text = "Choose native event PB8"
            isEnabled = false
            setOnClickListener { pickEventRecord() }
        }
        root.addView(chooseEvent)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"; visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply { text = "Back to Events"; setOnClickListener { if (!busy) finish() } })
        OmegaRubyUi.styleWorkflowScreen(this, root, status)
        setContentView(OmegaRubyUi.scrollingPage(this, root))

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(BdspEventEditing.sha256(bytes) == expectedHash) { "The source changed. Reopen it from PSE." }
                val validation = BdspSaveValidation.inspect(bytes)
                require(validation is BdspValidation.Verified) { "A validated native BDSP save is required." }
                val storage = BdspStorageReadOnly.read(bytes)
                require(storage.occupiedBoxSlots < 1200) { "All 1,200 native BDSP PC-box slots are occupied." }
                Triple(bytes, validation, storage)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, validation, storage) ->
                    opened = bytes
                    val destination = (0 until 40).asSequence().flatMap { box -> storage.box(box).asSequence() }
                        .first { it.read is BdspSlotRead.Empty }
                    status.text = "${validation.version.displayName} • Layout ${validation.revision.displayName} • Native integrity verified\n" +
                        "Occupied PC slots: ${storage.occupiedBoxSlots} / 1,200\n" +
                        "Next destination: Box ${destination.box + 1}, slot ${destination.position}\n" +
                        "Source SHA-256: ${BdspEventEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The save could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        chooseEvent.isEnabled = !value && opened != null
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        val input = contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        return BdspEventEditing.readDocumentBytes(input)
    }

    private fun readDocument(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
        return BdspEventEditing.readDocumentBytes(input)
    }

    private fun pickEventRecord() {
        if (busy || opened == null) return
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "application/octet-stream"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, pickEventRequest)
        } catch (failure: Exception) {
            status.text = failure.message ?: "The event file picker could not be opened."
        }
    }

    private fun inspectVerifiedCopy() {
        if (busy) return
        val (uri, hash) = verifiedCopy ?: return
        startActivity(Intent(this, SwitchReadOnlyActivity::class.java).setData(uri)
            .putExtra("pse.switch.family", SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL.name)
            .putExtra("pse.switch.sha256", hash).putExtra("pse.switch.section", "storage")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun reviewEvent(uri: Uri) {
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        setBusy(true)
        worker.execute {
            val result = runCatching {
                val input = contentResolver.openInputStream(uri) ?: error("The selected event PB8 cannot be read.")
                val record = BdspEventEditing.readEventRecord(input)
                BdspEventEditing.stage(bytes, hash, record)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { showReview(it) }
                    .onFailure { status.text = it.message ?: "The native event PB8 could not be staged."; setBusy(false) }
            }
        }
    }

    private fun showReview(plan: BdspEventCopyPlan) {
        var dispatched = false
        val moves = plan.event.moves.filter { it.id != 0 }.joinToString { "#${it.id} (PP ${it.pp})" }.ifBlank { "None" }
        AlertDialog.Builder(this).setTitle("Import native event Pokémon to a new copy?")
            .setMessage(
                "${SwordShieldSpeciesNames.name(plan.event.species)} • Level ${plan.event.cachedLevel}\n" +
                    "OT ${plan.event.originalTrainer} • TID ${plan.event.tid} / SID ${plan.event.sid}\n" +
                    "Fateful Encounter: ${plan.event.fatefulEncounter} • Shiny: ${plan.event.shiny}\n" +
                    "Moves: $moves\n\n" +
                    "Destination: native Box ${plan.box}, slot ${plan.position}\n" +
                    "Allowed byte scope: one 344-byte PB8 slot at 0x${plan.recordOffset.toString(16).uppercase()} " +
                    "plus the native whole-file MD5. Existing Pokémon and party data stay unchanged. " +
                    "PSE will verify a recovery backup and reopen the persisted copy. Choose a NEW empty document."
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
                        pendingPlan = null; setBusy(false)
                        status.text = failure.message ?: "The save picker could not be opened. Nothing was written."
                    }
                }
            }.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickEventRequest) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null && uri.scheme == "content") reviewEvent(uri)
            else status.text = "Event selection cancelled. Nothing was written."
            return
        }
        if (requestCode != createCopyRequest) return
        val plan = pendingPlan
        pendingPlan = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || plan == null) {
            status.text = "Save cancelled or review expired. Nothing was written."; setBusy(false); return
        }
        if (uri.scheme != "content" || uri == sourceUri) {
            status.text = "Choose a new document separate from the source. Nothing was written."; setBusy(false); return
        }
        setBusy(true)
        status.text = "Saving and verifying a new copy…"
        worker.execute {
            var backupName: String? = null
            val result = runCatching {
                plan.saveNewCopy(
                    readSource = { readSource() },
                    retainBackup = { original ->
                        val backup = SwitchSafeBackupStore.createVerified(this, SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, original)
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(this, SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, backup.file, backup.sha256)
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
                    onSuccess = { "Event copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\nOriginal source unchanged." },
                    onFailure = { (it.message ?: "The event copy could not be verified.") + "\nRecovery backup: ${backupName ?: "not created"}" }
                )
                setBusy(false)
            }
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
