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

/** Native Legends: Arceus Merit Point editor with reviewed new-copy persistence. */
class SwitchLegendsArceusMeritPointActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2161
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var opened: ByteArray? = null
    private var currentAvailable = false
    private var totalAvailable = false
    private var pendingPlan: LegendsArceusMeritPointCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var current: EditText
    private lateinit var total: EditText
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
        root.addView(TextView(this).apply { text = "Legends: Arceus • Merit Points"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "Edit PKHeX's documented native Current Merit Points and Total Merit Points earned UInt32 records. Changed values use the documented editor range 0–999,999,999. Unusual existing values remain exact when untouched."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Current Merit Points"; textSize = 17f })
        current = EditText(this).apply {
            hint = "0 to 999,999,999"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        root.addView(current)
        root.addView(TextView(this).apply { text = "Total Merit Points earned"; textSize = 17f })
        total = EditText(this).apply {
            hint = "0 to 999,999,999"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        root.addView(total)
        root.addView(TextView(this).apply {
            text = "PSE changes only selected four-byte native counters and the required SHA-256 footer. It does not infer lost-satchel history, shop purchases, rewards, rank, research, or story consistency."
            textSize = 14f
        })
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
            text = "Back to Trainer Data"
            setOnClickListener { if (!busy) finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LegendsArceusMeritPointEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                bytes to LegendsArceusMeritPointEditing.read(bytes)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, read) ->
                    opened = bytes
                    currentAvailable = read.current != null
                    totalAvailable = read.totalEarned != null
                    current.setText(read.current?.toString().orEmpty())
                    total.setText(read.totalEarned?.toString().orEmpty())
                    if (!currentAvailable) current.hint = "Not present in this save"
                    if (!totalAvailable) total.hint = "Not present in this save"
                    status.text = describe(read) +
                        "\nSource SHA-256: ${LegendsArceusMeritPointEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The save could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun describe(read: LegendsArceusMeritPointEditRead): String =
        "${read.version.displayName}\n" +
            "Current Merit Points: ${read.current ?: "Not present"}\n" +
            "Total Merit Points earned: ${read.totalEarned ?: "Not present"}"

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null
        current.isEnabled = !value && ready && currentAvailable
        total.isEnabled = !value && ready && totalAvailable
        review.isEnabled = !value && ready && (currentAvailable || totalAvailable)
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val requestedCurrent = if (currentAvailable) current.text.toString().trim().toLongOrNull() else null
        val requestedTotal = if (totalAvailable) total.text.toString().trim().toLongOrNull() else null
        if (currentAvailable && requestedCurrent == null) {
            status.text = "Enter Current Merit Points as a whole number."
            return
        }
        if (totalAvailable && requestedTotal == null) {
            status.text = "Enter Total Merit Points earned as a whole number."
            return
        }
        val plan = try {
            LegendsArceusMeritPointEditing.stage(bytes, hash, requestedCurrent, requestedTotal)
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested Merit Point change is unavailable."
            return
        }
        setBusy(true)
        var dispatched = false
        val changes = buildList {
            if (plan.currentChanged) add("Current Merit Points: ${plan.before.current} → ${plan.after.current}")
            if (plan.totalChanged) add("Total Merit Points earned: ${plan.before.totalEarned} → ${plan.after.totalEarned}")
        }.joinToString("\n")
        val scopes = buildList {
            if (plan.currentChanged) add("4 bytes at 0x${plan.currentOffset.toString(16).uppercase()}")
            if (plan.totalChanged) add("4 bytes at 0x${plan.totalOffset.toString(16).uppercase()}")
        }.joinToString(" and ")
        AlertDialog.Builder(this).setTitle("Save Merit Points to a new copy?")
            .setMessage(
                "$changes\n\nAllowed native scope: $scopes plus the native SHA-256 footer. " +
                    "Money, Expedition Team rank, satchel upgrades, trainer identity and every unrelated object remain exact. " +
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
                        "Merit Point copy saved and reopened successfully.\n${describe(plan.after)}\n" +
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
            .putExtra("pse.switch.family", SwitchGameFamily.LEGENDS_ARCEUS.name)
            .putExtra("pse.switch.sha256", hash)
            .putExtra("pse.switch.section", "trainer")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return readDocument(uri)
    }

    private fun readDocument(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
        return LegendsArceusMeritPointEditing.readDocumentBytes(input)
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
