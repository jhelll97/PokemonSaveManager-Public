package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Native Restricted Sparring best-streak editor with reviewed new-copy persistence. */
class SwitchSwordShieldRestrictedSparringActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2129
    private val labels = listOf(
        "Normal-type best streak",
        "Fire-type best streak",
        "Water-type best streak",
        "Electric-type best streak",
        "Grass-type best streak",
        "Ice-type best streak",
        "Fighting-type best streak",
        "Poison-type best streak",
        "Ground-type best streak",
        "Flying-type best streak",
        "Psychic-type best streak",
        "Bug-type best streak",
        "Rock-type best streak",
        "Ghost-type best streak",
        "Dragon-type best streak",
        "Dark-type best streak",
        "Steel-type best streak",
        "Fairy-type best streak"
    )
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var opened: ByteArray? = null
    private var pendingPlan: SwordShieldRestrictedSparringCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var fields: List<EditText>
    private lateinit var review: Button
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
        root.addView(TextView(this).apply { text = "Restricted Sparring Streaks"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "Pokémon Sword/Shield\nEdit all 18 type-specific native best-streak counters. " +
                "PSE does not infer type availability, stored teams, completion, or reward eligibility. The selected source stays unchanged."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        fields = labels.map { addNumberField(root, it) }
        review = Button(this).apply {
            text = "Review and save new copy"
            setOnClickListener { reviewChange() }
        }
        root.addView(review)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"
            visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply {
            text = "Back to Restricted Sparring Streaks"
            setOnClickListener { if (!busy) finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(SwordShieldRestrictedSparringEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                bytes to SwordShieldRestrictedSparringEditing.read(bytes)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, read) ->
                    opened = bytes
                    read.values.forEachIndexed { index, value -> fields[index].setText(value.toString()) }
                    status.text = "${read.version.displayName}\n" +
                        labels.indices.joinToString("\n") { "${labels[it]}: ${read.values[it]}" } +
                        "\nSource SHA-256: ${SwordShieldRestrictedSparringEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The save could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun addNumberField(root: LinearLayout, title: String): EditText {
        root.addView(TextView(this).apply { text = title; textSize = 18f })
        root.addView(TextView(this).apply { text = "Native UInt32 value"; textSize = 14f })
        return EditText(this).apply {
            hint = title
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            root.addView(this)
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null
        fields.forEach { it.isEnabled = !value && ready }
        review.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val replacement = fields.map { it.text.toString().toLongOrNull() }
        if (replacement.any { it == null || it !in 0L..0xFFFF_FFFFL }) {
            status.text = "Each best streak must fit its native unsigned 32-bit field."
            return
        }
        val plan = try {
            SwordShieldRestrictedSparringEditing.stage(bytes, hash, replacement.map { it!! })
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested Restricted Sparring change is unavailable."
            return
        }
        setBusy(true)
        var dispatched = false
        val scopes = labels.indices.filter { plan.changes[it] }.joinToString("; ") { index ->
            "${labels[index]} UInt32 at 0x${plan.offsets[index].toString(16).uppercase()}"
        }
        AlertDialog.Builder(this)
            .setTitle("Save Restricted Sparring Streaks to a new copy?")
            .setMessage(
                "${changes(plan, "\n\n")}\n\nAllowed encrypted scope: $scopes, plus the native SHA-256 footer. " +
                    "PSE will verify a recovery backup and reopen the persisted copy. Choose a NEW empty document; " +
                    "the selected source cannot be replaced."
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
                        startActivityForResult(
                            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                type = "application/octet-stream"
                                addCategory(Intent.CATEGORY_OPENABLE)
                                putExtra(Intent.EXTRA_TITLE, "main")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            }, createCopyRequest
                        )
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
                            this, SwitchGameFamily.SWORD_SHIELD, original
                        )
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.SWORD_SHIELD, backup.file, backup.sha256
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
                        "Restricted Sparring copy saved and reopened successfully.\n" +
                            changes(plan, "\n") + "\nSHA-256: ${plan.editedSha256}\n" +
                            "Recovery backup: $backupName\nOriginal source unchanged."
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

    private fun changes(plan: SwordShieldRestrictedSparringCopyPlan, separator: String): String =
        labels.indices.filter { plan.changes[it] }.joinToString(separator) { index ->
            "${labels[index]}: ${plan.before.values[index]} → ${plan.after.values[index]}"
        }

    private fun inspectVerifiedCopy() {
        if (busy) return
        val (uri, hash) = verifiedCopy ?: return
        try {
            startActivity(
                Intent(this, SwitchReadOnlyActivity::class.java)
                    .setData(uri)
                    .putExtra("pse.switch.family", SwitchGameFamily.SWORD_SHIELD.name)
                    .putExtra("pse.switch.sha256", hash)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (failure: Exception) {
            status.text = failure.message ?: "The verified copy could not be opened for inspection."
        }
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return readDocument(uri)
    }

    private fun readDocument(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
        return SwordShieldRestrictedSparringEditing.readDocumentBytes(input)
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() {
        if (!busy) super.onBackPressed()
    }

    override fun onDestroy() {
        pendingPlan = null
        worker.shutdown()
        super.onDestroy()
    }
}
