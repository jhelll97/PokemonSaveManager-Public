package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Native one-record BDSP Grand Underground inventory editor with reviewed new-copy persistence. */
class SwitchBdspUndergroundItemActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2135
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var undergroundId = -1
    private var opened: ByteArray? = null
    private var pendingPlan: BdspUndergroundItemCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var editors: Map<BdspUndergroundItemEditField, EditText>
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        undergroundId = intent?.getIntExtra("pse.switch.underground.id", -1) ?: -1
        if (sourceUri == null || expectedHash.isNullOrBlank() || undergroundId !in 0 until 999) { finish(); return }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "BDSP • Grand Underground • ID $undergroundId"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "Edit one native Grand Underground inventory record. Quantity changes use the catalogue limit shown below. New state uses 0 New or 1 Seen; Favorite uses 0 No or 1 Yes. Unusual saved fields remain exact when left unchanged."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        editors = BdspUndergroundItemEditField.values().associateWith { field ->
            root.addView(TextView(this).apply {
                text = when (field) {
                    BdspUndergroundItemEditField.QUANTITY -> "Quantity • 0 through the reference maximum when changed"
                    BdspUndergroundItemEditField.HIDE_NEW -> "New state • 0 New, 1 Seen when changed"
                    BdspUndergroundItemEditField.FAVORITE -> "Favorite • 0 No, 1 Yes when changed"
                }
                textSize = 17f
            })
            EditText(this).apply {
                hint = field.displayName
                inputType = InputType.TYPE_CLASS_NUMBER or
                    (if (field == BdspUndergroundItemEditField.QUANTITY) InputType.TYPE_NUMBER_FLAG_SIGNED else 0)
                root.addView(this)
            }
        }
        review = Button(this).apply {
            text = "Review and save new copy"
            setOnClickListener { reviewChange() }
        }
        root.addView(review)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"; visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply { text = "Back to Grand Underground"; setOnClickListener { if (!busy) finish() } })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(BdspUndergroundItemEditing.sha256(bytes) == expectedHash) { "The source changed. Reopen it from PSE." }
                bytes to BdspUndergroundItemEditing.read(bytes, undergroundId)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, read) ->
                    opened = bytes
                    BdspUndergroundItemEditField.values().forEach { field ->
                        editors.getValue(field).setText(BdspUndergroundItemEditing.value(read.entry, field).toString())
                    }
                    status.text = describe(read) + "\nSource SHA-256: ${BdspUndergroundItemEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The save could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun describe(read: BdspUndergroundItemEditRead): String = buildString {
        val entry = read.entry
        append("${read.version.displayName} • Save layout ${read.revision.displayName}\n")
        append("${entry.name} • Underground ID ${entry.undergroundId}\n")
        append("Quantity ${entry.count} (raw ${entry.rawCount}) • New ${entry.isNew} (raw hide-new ${entry.rawHideNewFlag}) • Favorite ${entry.isFavorite} (raw ${entry.rawFavoriteFlag})\n")
        append("Reference maximum ${entry.referenceMaxCount ?: "Unknown"}")
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null
        editors.values.forEach { it.isEnabled = !value && ready }
        review.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val quantity = editors.getValue(BdspUndergroundItemEditField.QUANTITY).text.toString().trim().toIntOrNull()
        val hideNew = editors.getValue(BdspUndergroundItemEditField.HIDE_NEW).text.toString().trim().toLongOrNull()
        val favorite = editors.getValue(BdspUndergroundItemEditField.FAVORITE).text.toString().trim().toLongOrNull()
        if (quantity == null || hideNew == null || favorite == null) {
            status.text = "Enter whole numbers for all native Grand Underground fields."
            return
        }
        val plan = try {
            BdspUndergroundItemEditing.stage(bytes, hash, undergroundId, BdspUndergroundItemReplacement(quantity, hideNew, favorite))
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested Underground item change is unavailable."
            return
        }
        setBusy(true)
        var dispatched = false
        val scopes = plan.changedFields.joinToString("; ") { field ->
            val start = BdspUndergroundItemEditing.recordOffset(undergroundId) + field.relativeOffset
            "${field.displayName} 4 bytes at 0x${start.toString(16).uppercase()}"
        }
        AlertDialog.Builder(this).setTitle("Save Grand Underground • ID $undergroundId to a new copy?")
            .setMessage(
                "Before\n${describe(plan.before)}\n\nAfter\n${describe(plan.after)}\n\n" +
                    "Allowed byte scope: $scopes, plus the native whole-file MD5. Every other Grand Underground inventory record remains exact. " +
                    "PSE will verify a recovery backup and reopen the persisted copy. Choose a NEW empty document; the selected source cannot be replaced."
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
                            type = "application/octet-stream"; addCategory(Intent.CATEGORY_OPENABLE)
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
                    retainBackup = { original ->
                        val backup = SwitchSafeBackupStore.createVerified(
                            this, SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, original
                        )
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, backup.file, backup.sha256
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
                        "Grand Underground copy saved and reopened successfully.\n${describe(plan.after)}\n" +
                            "SHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\nOriginal source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The copy could not be verified.") +
                            "\nRecovery backup: ${backupName ?: "not created"}"
                    }
                )
                setBusy(false)
            }
        }
    }

    private fun inspectVerifiedCopy() {
        if (busy) return
        val (uri, hash) = verifiedCopy ?: return
        startActivity(Intent(this, SwitchReadOnlyActivity::class.java).setData(uri)
            .putExtra("pse.switch.family", SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL.name)
            .putExtra("pse.switch.sha256", hash)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return readDocument(uri)
    }

    private fun readDocument(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
        return BdspItemEditing.readDocumentBytes(input)
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
