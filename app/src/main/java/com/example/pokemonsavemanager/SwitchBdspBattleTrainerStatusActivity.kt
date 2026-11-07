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

/** Native one-entry BDSP battle-trainer status editor with reviewed new-copy persistence. */
class SwitchBdspBattleTrainerStatusActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2145
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var trainerIndex = -1
    private var opened: ByteArray? = null
    private var pendingPlan: BdspBattleTrainerStatusCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var winWord: EditText
    private lateinit var battleSearcherWord: EditText
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        trainerIndex = intent?.getIntExtra("pse.switch.bdsp.battle.trainer.index", -1) ?: -1
        if (sourceUri == null || expectedHash.isNullOrBlank() || trainerIndex !in 0 until 707) {
            finish()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        fun label(value: String) = TextView(this).apply { text = value; textSize = 17f }
        root.addView(TextView(this).apply { text = "BDSP • Battle Trainer Status"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "Edit one native eight-byte trainer-status entry in saved index order. Changed flag words must be 0 clear or 1 set. An unusual existing word remains exact when left untouched. PSE does not infer a trainer name, encounter, rematch eligibility, or story consistency, and does not provide bulk defeat or rebattle actions."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(label("Win flag word • changed values: 0 clear or 1 set"))
        winWord = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        root.addView(winWord)
        root.addView(label("Battle-searcher flag word • changed values: 0 clear or 1 set"))
        battleSearcherWord = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        root.addView(battleSearcherWord)
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
            text = "Back to Battle Trainer Status"
            setOnClickListener { if (!busy) finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(BdspBattleTrainerStatusEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                bytes to BdspBattleTrainerStatusEditing.read(bytes, trainerIndex)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, read) ->
                    opened = bytes
                    winWord.setText(read.entry.rawWinWord.toString())
                    battleSearcherWord.setText(read.entry.rawBattleSearcherWord.toString())
                    status.text = describe(read) +
                        "\nSource SHA-256: ${BdspBattleTrainerStatusEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The save could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun flag(word: Long): String = when (word) {
        0L -> "Clear"
        1L -> "Set"
        else -> "Unknown ($word)"
    }

    private fun describe(read: BdspBattleTrainerStatusEditRead): String {
        val entry = read.entry
        return "${read.version.displayName} • Save layout ${read.revision.displayName}\n" +
            "Saved trainer index ${entry.index}\n" +
            "Win flag: ${flag(entry.rawWinWord)} • raw ${entry.rawWinWord} • hex ${entry.rawWinWord.toString(16).uppercase().padStart(8, '0')}\n" +
            "Battle-searcher flag: ${flag(entry.rawBattleSearcherWord)} • raw ${entry.rawBattleSearcherWord} • hex ${entry.rawBattleSearcherWord.toString(16).uppercase().padStart(8, '0')}"
    }

    private fun updateControls() {
        val ready = opened != null
        winWord.isEnabled = !busy && ready
        battleSearcherWord.isEnabled = !busy && ready
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
        val winValue = winWord.text.toString().trim().toLongOrNull()
        val searcherValue = battleSearcherWord.text.toString().trim().toLongOrNull()
        if (winValue == null || searcherValue == null ||
            winValue !in 0L..0xFFFF_FFFFL || searcherValue !in 0L..0xFFFF_FFFFL
        ) {
            status.text = "Enter complete decimal UInt32 words from 0 through 4,294,967,295. A changed word must be 0 or 1."
            return
        }
        val plan = try {
            BdspBattleTrainerStatusEditing.stage(
                bytes, hash, trainerIndex,
                BdspBattleTrainerStatusReplacement(winValue, searcherValue)
            )
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested battle-trainer change is unavailable."
            return
        }
        val scopes = plan.allowedRanges.joinToString(", ") { range ->
            "0x${range.first.toString(16).uppercase()}–0x${range.last.toString(16).uppercase()}"
        }
        setBusy(true)
        var dispatched = false
        AlertDialog.Builder(this).setTitle("Save trainer index $trainerIndex to a new copy?")
            .setMessage(
                "Before\n${describe(plan.before)}\n\nAfter\n${describe(plan.after)}\n\n" +
                    "Changed native fields: ${plan.changedFields.joinToString()}.\n" +
                    "Allowed byte scope: $scopes, plus the native whole-file MD5. " +
                    "The other 706 entries and every unrelated byte remain exact. PSE will verify a recovery backup and reopen the persisted copy. Choose a NEW empty document; the selected source cannot be replaced."
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
                        "Battle-trainer copy saved and reopened successfully.\n${describe(plan.after)}\n" +
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
