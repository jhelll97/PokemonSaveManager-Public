package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.concurrent.Executors

/** One native Let's Go caught-count edit, persisted only to a reviewed new copy. */
class SwitchLetsGoCaptureRecordsActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2117
    private val speciesIds = (1..151).toList() + listOf(808, 809)
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var opened: ByteArray? = null
    private var records: LetsGoCaptureRecordsRead? = null
    private var pendingPlan: LetsGoCaptureRecordCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var species: Spinner
    private lateinit var field: Spinner
    private lateinit var count: EditText
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        if (sourceUri == null || expectedHash.isNullOrBlank()) { finish(); return }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Let's Go • Capture Records"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "Change one native per-species caught or Professor Oak transfer counter. The all-species totals and every other field stay unchanged. PSE writes only a verified new copy."
            textSize = 16f
        })
        status = TextView(this).apply { text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true) }
        root.addView(status)
        species = Spinner(this).apply {
            adapter = ArrayAdapter(this@SwitchLetsGoCaptureRecordsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                speciesIds.map { "#$it ${LetsGoSpeciesNames.name(it)}" })
        }
        root.addView(species)
        field = Spinner(this).apply {
            adapter = ArrayAdapter(this@SwitchLetsGoCaptureRecordsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                LetsGoCaptureRecordField.values().map { it.displayName })
        }
        root.addView(field)
        count = EditText(this).apply {
            hint = "New native count"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        root.addView(count)
        review = Button(this).apply { text = "Review and save new copy"; setOnClickListener { reviewChange() } }
        root.addView(review)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"; visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply { text = "Back to Capture Records"; setOnClickListener { if (!busy) finish() } })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LetsGoCaptureRecordEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                Triple(bytes, LetsGoCaptureRecordsReadOnly.read(bytes), LetsGoSaveValidation.inspect(bytes))
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, read, validation) ->
                    val verified = validation as LetsGoValidation.Verified
                    opened = bytes; records = read
                    status.text = "${verified.version.displayName} • All ${verified.checkedBlocks} native block CRCs passed\n" +
                        "Total caught: ${read.totalCaptured} • Total transferred: ${read.totalTransferred}\n" +
                        "Source SHA-256: ${LetsGoCaptureRecordEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The save could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null && records != null
        species.isEnabled = !value && ready
        field.isEnabled = !value && ready
        count.isEnabled = !value && ready
        review.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return readDocument(uri)
    }

    private fun readDocument(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
        return LetsGoCaptureRecordEditing.readDocumentBytes(input)
    }

    private fun inspectVerifiedCopy() {
        if (busy) return
        val (uri, hash) = verifiedCopy ?: return
        startActivity(Intent(this, SwitchReadOnlyActivity::class.java).setData(uri)
            .putExtra("pse.switch.family", SwitchGameFamily.LETS_GO_PIKACHU_EEVEE.name)
            .putExtra("pse.switch.sha256", hash)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedSpecies = speciesIds.getOrNull(species.selectedItemPosition) ?: return
        val selectedField = LetsGoCaptureRecordField.values().getOrNull(field.selectedItemPosition) ?: return
        val requested = count.text.toString().trim().toLongOrNull()
        val plan = try {
            LetsGoCaptureRecordEditing.stage(bytes, hash, selectedSpecies, selectedField,
                requested ?: throw IllegalArgumentException("Enter a new native count."))
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested capture-record edit is unavailable."
            return
        }
        setBusy(true)
        var dispatched = false
        AlertDialog.Builder(this).setTitle("Save ${plan.field.displayName.lowercase()} count to a new copy?")
            .setMessage("#${plan.speciesId} ${LetsGoSpeciesNames.name(plan.speciesId)}\n" +
                "Caught: ${plan.before.captured} → ${plan.after.captured}\n" +
                "Transferred: ${plan.before.transferred} → ${plan.after.transferred}\n\n" +
                "Allowed byte scope: one native four-byte counter at 0x${plan.valueOffset.toString(16).uppercase()} plus block 12's two-byte CRC. " +
                "Both native all-species totals remain unchanged. PSE will verify a recovery backup and reopen the persisted copy. Choose a NEW empty document.")
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

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
