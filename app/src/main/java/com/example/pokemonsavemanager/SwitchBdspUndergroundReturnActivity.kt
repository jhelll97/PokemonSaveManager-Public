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
import java.util.LinkedHashMap
import java.util.concurrent.Executors

/** Native BDSP Underground return-field editor with reviewed new-copy persistence. */
class SwitchBdspUndergroundReturnActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2141
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var opened: ByteArray? = null
    private var pendingPlan: BdspUndergroundReturnCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var review: Button
    private lateinit var inspectCopy: Button
    private val inputs = LinkedHashMap<BdspUndergroundReturnEditField, EditText>()

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
        root.addView(TextView(this).apply { text = "BDSP • Underground Return Data"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "Edit the ten documented native return fields. Integer values retain their full signed Int32 range. Position values use exact 32-bit hexadecimal Float words so signed zero, subnormals, infinities, and NaN payloads remain controllable without normalization. PSE does not infer map names, coordinate systems, directions, spawn validity, or teleport destinations."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        BdspUndergroundReturnEditField.values().forEach { field ->
            root.addView(TextView(this).apply {
                text = field.displayName + if (field.rawFloatWord) " • 8-digit hex word" else " • signed Int32"
                textSize = 17f
            })
            val input = EditText(this).apply {
                hint = if (field.rawFloatWord) "00000000–FFFFFFFF" else "-2147483648–2147483647"
                inputType = if (field.rawFloatWord) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                } else InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            }
            inputs[field] = input
            root.addView(input)
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
        root.addView(Button(this).apply {
            text = "Back to Underground Return Data"
            setOnClickListener { if (!busy) finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(BdspUndergroundReturnEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                bytes to BdspUndergroundReturnEditing.read(bytes)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, read) ->
                    opened = bytes
                    BdspUndergroundReturnEditField.values().forEach { field ->
                        val value = BdspUndergroundReturnEditing.value(read, field)
                        inputs.getValue(field).setText(
                            if (field.rawFloatWord) value.toString(16).uppercase().padStart(8, '0') else value.toString()
                        )
                    }
                    status.text = "${read.version.displayName} • Save layout ${read.revision.displayName}\n" +
                        "Source SHA-256: ${BdspUndergroundReturnEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The save could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(setting: Boolean) {
        busy = setting
        val ready = opened != null
        inputs.values.forEach { it.isEnabled = !setting && ready }
        review.isEnabled = !setting && ready
        inspectCopy.isEnabled = !setting && verifiedCopy != null
    }

    private fun parse(field: BdspUndergroundReturnEditField): Long {
        val text = inputs.getValue(field).text.toString().trim()
        if (field.rawFloatWord) {
            val raw = text.removePrefix("0x").removePrefix("0X")
            require(raw.matches(Regex("[0-9A-Fa-f]{8}"))) {
                "${field.displayName} must be an 8-digit hexadecimal word."
            }
            return raw.toLong(16)
        }
        val value = text.toLongOrNull()
            ?: throw IllegalArgumentException("${field.displayName} must be a whole signed Int32 value.")
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "${field.displayName} must be -2147483648 through 2147483647."
        }
        return value
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val replacement = try {
            BdspUndergroundReturnReplacement(
                BdspUndergroundReturnEditField.values().associateWith { field -> parse(field) }
            )
        } catch (failure: Exception) {
            status.text = failure.message ?: "Enter valid Underground return values."
            return
        }
        val plan = try {
            BdspUndergroundReturnEditing.stage(bytes, hash, replacement)
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested Underground return change is unavailable."
            return
        }
        val changes = plan.changedFields.joinToString("\n") { field ->
            "${field.displayName}: ${BdspUndergroundReturnEditing.displayValue(plan.before, field)} → " +
                BdspUndergroundReturnEditing.displayValue(plan.after, field)
        }
        val scope = plan.changedFields.joinToString(", ") { field ->
            "0x${BdspUndergroundReturnEditing.absoluteOffset(field).toString(16).uppercase()}–" +
                "0x${(BdspUndergroundReturnEditing.absoluteOffset(field) + 3).toString(16).uppercase()}"
        }
        setBusy(true)
        var dispatched = false
        AlertDialog.Builder(this).setTitle("Save Underground Return Data to a new copy?")
            .setMessage(
                "Before → After\n$changes\n\nAllowed byte scope: $scope, plus the native whole-file MD5. " +
                    "Dig points, encounter Pokémon and positions, Secret Base, NPC records, and every unrelated byte remain exact. PSE will verify a recovery backup and reopen the persisted copy. Choose a NEW empty document; the selected source cannot be replaced."
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
                            this.type = "application/octet-stream"
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
                            this, SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL,
                            backup.file, backup.sha256
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
                        "Underground Return Data copy saved and reopened successfully.\n" +
                            "Changed fields: ${plan.changedFields.joinToString { it.displayName }}\n" +
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
