package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Native one-slot BDSP additional-trainer editor with reviewed new-copy persistence. */
class SwitchBdspAdditionalTrainerActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2144
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var recordIndex = -1
    private var opened: ByteArray? = null
    private var openedRead: BdspAdditionalTrainerEditRead? = null
    private var pendingPlan: BdspAdditionalTrainerCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var name: EditText
    private lateinit var replaceMalformedName: CheckBox
    private lateinit var language: EditText
    private lateinit var gender: EditText
    private lateinit var bodyType: EditText
    private lateinit var id32: EditText
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        recordIndex = intent?.getIntExtra("pse.switch.bdsp.additional.record.index", -1) ?: -1
        if (sourceUri == null || expectedHash.isNullOrBlank() || recordIndex !in 0 until 12) {
            finish()
            return
        }
        val signedNumber = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        fun label(value: String) = TextView(this).apply { text = value; textSize = 17f }
        root.addView(TextView(this).apply { text = "BDSP • Additional Record Trainers"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "Edit one native 48-byte trainer record in saved slot order. PSE preserves the record's reserved bytes, the other eleven trainer records, and the separate unclassified ranking region. A nonzero record does not establish ownership, chronology, ranking association, or a currently active trainer."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(label("Saved OT name • at most 12 UTF-16 code units"))
        name = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        root.addView(name)
        replaceMalformedName = CheckBox(this).apply {
            text = "Replace the unreadable native name field"; visibility = View.GONE
            setOnCheckedChangeListener { _, _ -> updateControls() }
        }
        root.addView(replaceMalformedName)
        root.addView(label("Language • native signed Int32"))
        language = EditText(this).apply { inputType = signedNumber }
        root.addView(language)
        root.addView(label("Gender • native byte 0–255"))
        gender = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        root.addView(gender)
        root.addView(label("Body type • native signed Int32"))
        bodyType = EditText(this).apply { inputType = signedNumber }
        root.addView(bodyType)
        root.addView(label("ID32 • native UInt32 0–4,294,967,295"))
        id32 = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        root.addView(id32)
        review = Button(this).apply {
            text = "Review and save new copy"
            setOnClickListener { reviewChange() }
        }
        root.addView(review)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"; visibility = View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply {
            text = "Back to Additional Record Trainers"
            setOnClickListener { if (!busy) finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(BdspAdditionalTrainerEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                bytes to BdspAdditionalTrainerEditing.read(bytes, recordIndex)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, read) ->
                    opened = bytes
                    openedRead = read
                    if (read.entry.name == null) {
                        name.setText("")
                        name.hint = read.entry.nameIssue ?: "Unreadable native name"
                        replaceMalformedName.visibility = View.VISIBLE
                        replaceMalformedName.isChecked = false
                    } else {
                        name.setText(read.entry.name)
                        replaceMalformedName.visibility = View.GONE
                    }
                    language.setText(read.entry.languageId.toString())
                    gender.setText(read.entry.genderByte.toString())
                    bodyType.setText(read.entry.bodyTypeId.toString())
                    id32.setText(read.entry.id32.toString())
                    status.text = describe(read) +
                        "\nSource SHA-256: ${BdspAdditionalTrainerEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The save could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun describe(read: BdspAdditionalTrainerEditRead): String {
        val entry = read.entry
        return "${read.version.displayName} • Save layout ${read.revision.displayName}\n" +
            "Additional trainer slot ${entry.index}\n" +
            "OT name: ${entry.name?.let { "\"$it\"" } ?: "Unavailable (${entry.nameIssue})"}\n" +
            "Language: ${entry.languageId}\nGender byte: ${entry.genderByte}\n" +
            "Body type: ${entry.bodyTypeId}\nID32: ${entry.id32} • TID16 ${entry.tid16} • SID16 ${entry.sid16}"
    }

    private fun updateControls() {
        val ready = opened != null
        val nameReadable = openedRead?.entry?.name != null
        name.isEnabled = !busy && ready && (nameReadable || replaceMalformedName.isChecked)
        replaceMalformedName.isEnabled = !busy && ready && !nameReadable
        language.isEnabled = !busy && ready
        gender.isEnabled = !busy && ready
        bodyType.isEnabled = !busy && ready
        id32.isEnabled = !busy && ready
        review.isEnabled = !busy && ready
        inspectCopy.isEnabled = !busy && verifiedCopy != null
    }

    private fun setBusy(setting: Boolean) {
        busy = setting
        updateControls()
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val read = openedRead ?: return
        val languageValue = language.text.toString().trim().toIntOrNull()
        val genderValue = gender.text.toString().trim().toIntOrNull()
        val bodyTypeValue = bodyType.text.toString().trim().toIntOrNull()
        val idValue = id32.text.toString().trim().toLongOrNull()
        if (languageValue == null || genderValue == null || bodyTypeValue == null || idValue == null) {
            status.text = "Enter complete decimal values within each native field range."
            return
        }
        val nameValue = if (read.entry.name != null || replaceMalformedName.isChecked) {
            name.text.toString()
        } else null
        val plan = try {
            BdspAdditionalTrainerEditing.stage(
                bytes, hash, recordIndex,
                BdspAdditionalTrainerReplacement(nameValue, languageValue, genderValue, bodyTypeValue, idValue)
            )
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested trainer-record change is unavailable."
            return
        }
        val scopes = plan.allowedRanges.joinToString(", ") { range ->
            val first = "0x${range.first.toString(16).uppercase()}"
            if (range.first == range.last) first else "$first–0x${range.last.toString(16).uppercase()}"
        }
        setBusy(true)
        var dispatched = false
        AlertDialog.Builder(this).setTitle("Save additional trainer slot $recordIndex to a new copy?")
            .setMessage(
                "Before\n${describe(plan.before)}\n\nAfter\n${describe(plan.after)}\n\n" +
                    "Changed native fields: ${plan.changedFields.joinToString()}.\n" +
                    "Allowed byte scope: $scopes, plus the native whole-file MD5. " +
                    "Reserved bytes, the other eleven trainer records, the ranking region, and every unrelated byte remain exact. PSE will verify a recovery backup and reopen the persisted copy. Choose a NEW empty document; the selected source cannot be replaced."
            )
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { if (!dispatched) setBusy(false) }
            .setPositiveButton("Choose new copy") { _, _ ->
                if (!dispatched && !isFinishing && !isDestroyed) {
                    dispatched = true
                    pendingPlan = plan
                    verifiedCopy = null
                    inspectCopy.visibility = View.GONE
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
                inspectCopy.visibility = if (verifiedCopy != null) View.VISIBLE else View.GONE
                status.text = result.fold(
                    onSuccess = {
                        "Additional trainer copy saved and reopened successfully.\n${describe(plan.after)}\n" +
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
