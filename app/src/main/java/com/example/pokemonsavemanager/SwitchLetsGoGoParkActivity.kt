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
import java.util.concurrent.Executors

/** Clears one reviewed native GP1 slot, persisted only to a verified new save copy. */
class SwitchLetsGoGoParkActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2119
    private val exportGp1Request = 2120
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var selectedIndex = -1
    private var opened: ByteArray? = null
    private var entry: LetsGoGoParkEntry? = null
    private var rawRecord: ByteArray? = null
    private var pendingPlan: LetsGoGoParkClearCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var review: Button
    private lateinit var export: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        selectedIndex = intent?.getIntExtra("pse.go_park.index", -1) ?: -1
        if (sourceUri == null || expectedHash.isNullOrBlank() || selectedIndex !in 0 until LetsGoGoParkReadOnly.RECORD_COUNT) {
            finish(); return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt(); setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Let's Go • GO Park"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "Clear one occupied native GP1 slot in a verified new copy. Every other GO Park slot and save field stays unchanged."
            textSize = 16f
        })
        status = TextView(this).apply { text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true) }
        root.addView(status)
        review = Button(this).apply { text = "Review clearing this slot"; setOnClickListener { reviewChange() } }
        root.addView(review)
        export = Button(this).apply { text = "Export exact native GP1 record"; setOnClickListener { exportRecord() } }
        root.addView(export)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"; visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply { text = "Back to GO Park"; setOnClickListener { if (!busy) finish() } })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LetsGoGoParkEditing.sha256(bytes) == expectedHash) { "The source changed. Reopen it from PSE." }
                val read = LetsGoGoParkReadOnly.read(bytes)
                val selected = read.entries.firstOrNull { it.index == selectedIndex }
                    ?: throw IllegalArgumentException("The selected GO Park slot is empty.")
                Pair(Triple(bytes, selected, read), LetsGoGoParkReadOnly.recordBytes(bytes, selectedIndex))
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (summary, record) ->
                    val (bytes, selected, read) = summary
                    opened = bytes; entry = selected; rawRecord = record
                    status.text = "${read.version.displayName} • All 21 native block CRCs passed\n" +
                        "GO Park ${selected.area}, slot ${selected.slot}: #${selected.species} ${selected.displayName}\n" +
                        "Level ${selected.level} • CP ${selected.cp} • ${read.occupiedCount}/1,000 slots occupied\n" +
                        "Source SHA-256: ${LetsGoGoParkEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The GO Park slot could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        review.isEnabled = !value && opened != null && entry != null
        export.isEnabled = !value && rawRecord != null
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return readDocument(uri)
    }

    private fun readDocument(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
        return LetsGoGoParkEditing.readDocumentBytes(input)
    }

    private fun inspectVerifiedCopy() {
        if (busy) return
        val (uri, hash) = verifiedCopy ?: return
        startActivity(Intent(this, SwitchReadOnlyActivity::class.java).setData(uri)
            .putExtra("pse.switch.family", SwitchGameFamily.LETS_GO_PIKACHU_EEVEE.name)
            .putExtra("pse.switch.sha256", hash).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun exportRecord() {
        if (busy || rawRecord == null) return
        val selected = entry ?: return
        setBusy(true)
        try {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "application/octet-stream"; addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_TITLE, "GO-Park-${selected.area}-${selected.slot}-${selected.species}.gp1")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }, exportGp1Request)
        } catch (failure: Exception) {
            setBusy(false); status.text = failure.message ?: "The GP1 export picker could not be opened."
        }
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val selected = entry ?: return
        val plan = try {
            LetsGoGoParkEditing.stage(opened ?: return, expectedHash ?: return, selected.index)
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested GO Park edit is unavailable."; return
        }
        setBusy(true)
        var dispatched = false
        AlertDialog.Builder(this).setTitle("Clear this GO Park slot in a new copy?")
            .setMessage("GO Park ${selected.area}, slot ${selected.slot}\n" +
                "Before: #${selected.species} ${selected.displayName} • Level ${selected.level} • CP ${selected.cp}\n" +
                "After: Empty canonical GP1 slot\n\n" +
                "Allowed byte scope: one native 0x1B0-byte GP1 record at 0x${plan.recordOffset.toString(16).uppercase()} plus block 18's two-byte CRC. " +
                "PSE will verify a recovery backup and reopen the persisted copy. Choose a NEW empty document.")
            .setNegativeButton("Cancel", null).setOnDismissListener { if (!dispatched) setBusy(false) }
            .setPositiveButton("Choose new copy") { _, _ ->
                if (!dispatched && !isFinishing && !isDestroyed) {
                    dispatched = true; pendingPlan = plan; verifiedCopy = null
                    inspectCopy.visibility = android.view.View.GONE
                    try {
                        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            type = "application/octet-stream"; addCategory(Intent.CATEGORY_OPENABLE)
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
        if (requestCode == exportGp1Request) {
            handleExportResult(resultCode, data?.data); return
        }
        if (requestCode != createCopyRequest) return
        val plan = pendingPlan; pendingPlan = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || plan == null) {
            status.text = "Save cancelled or review expired. Nothing was written."; setBusy(false); return
        }
        if (uri.scheme != "content" || uri == sourceUri) {
            status.text = "Choose a new document separate from the source. Nothing was written."; setBusy(false); return
        }
        setBusy(true); status.text = "Saving and verifying a new copy…"
        worker.execute {
            var backupName: String? = null
            val result = runCatching {
                plan.saveNewCopy(
                    readSource = { readSource() },
                    retainBackup = { original ->
                        val backup = SwitchSafeBackupStore.createVerified(this,
                            SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, original)
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(this,
                            SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, backup.file, backup.sha256)
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
                    onSuccess = { "Edited copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\nOriginal source unchanged." },
                    onFailure = { (it.message ?: "The copy could not be verified.") + "\nRecovery backup: ${backupName ?: "not created"}" }
                )
                setBusy(false)
            }
        }
    }

    private fun handleExportResult(resultCode: Int, uri: Uri?) {
        val expected = rawRecord?.copyOf()
        if (resultCode != RESULT_OK || uri == null || expected == null) {
            status.text = "GP1 export cancelled. The save was not changed."; setBusy(false); return
        }
        if (uri.scheme != "content" || uri == sourceUri) {
            status.text = "Choose a new document separate from the save. Nothing was exported."; setBusy(false); return
        }
        setBusy(true); status.text = "Exporting and reopening the native GP1 record…"
        worker.execute {
            var started = false
            val result = runCatching {
                require(LetsGoGoParkEditing.sha256(readSource()) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                require(readGp1Document(uri).isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
                started = true
                val output = contentResolver.openOutputStream(uri, "wt")
                    ?: error("The GP1 document is not writable.")
                output.use { it.write(expected); it.flush() }
                require(readGp1Document(uri).contentEquals(expected)) {
                    "The exported GP1 document differs from the selected native record."
                }
                require(LetsGoGoParkEditing.sha256(readSource()) == expectedHash) {
                    "The source changed during export."
                }
            }.recoverCatching { failure ->
                val removed = started && runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }.getOrDefault(false)
                throw IllegalStateException((failure.message ?: "GP1 export failed.") +
                    if (!started) " Nothing was exported."
                    else if (removed) " The failed export was removed."
                    else " The failed export could not be removed; do not use it.", failure)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                status.text = result.fold(
                    onSuccess = { "Native GP1 record exported and reopened successfully (${expected.size} bytes). The source save is unchanged." },
                    onFailure = { it.message ?: "The GP1 export could not be verified." }
                )
                setBusy(false)
            }
        }
    }

    private fun readGp1Document(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("The GP1 document cannot be read.")
        return input.use {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(512)
            while (true) {
                val count = it.read(buffer, 0, minOf(buffer.size, LetsGoGoParkReadOnly.RECORD_SIZE + 1 - output.size()))
                if (count < 0) break
                if (count == 0) {
                    val single = it.read()
                    if (single < 0) break
                    output.write(single)
                } else output.write(buffer, 0, count)
                require(output.size() <= LetsGoGoParkReadOnly.RECORD_SIZE) { "The GP1 document exceeds 0x1B0 bytes." }
            }
            output.toByteArray()
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
