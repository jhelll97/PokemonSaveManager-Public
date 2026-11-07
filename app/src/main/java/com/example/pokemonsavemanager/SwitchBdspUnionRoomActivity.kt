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

/** Native BDSP Union Room editor with exact Float32 bits and reviewed new-copy persistence. */
class SwitchBdspUnionRoomActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2147
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var opened: ByteArray? = null
    private var pendingPlan: BdspUnionRoomCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var initTalkWord: EditText
    private lateinit var penaltyCount: EditText
    private lateinit var penaltyTimeBits: EditText
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
        root.addView(TextView(this).apply { text = "BDSP • Union Room Saved Data"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "Edit PKHeX's three documented native Union Room fields. A changed initial-talk flag must be 0 clear or 1 set; an unusual existing word remains exact when untouched. PenaltyCount retains its complete signed Int32 range. PenaltyTime uses the authoritative eight-hex-digit Float32 word so signed zero, infinities, NaNs, and payload bits remain exact. PSE does not infer time units, expiry, online access, unlock state, or penalty-clearing effects."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Initial-talk flag word • changed values: 0 clear or 1 set"; textSize = 17f })
        initTalkWord = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        root.addView(initTalkWord)
        root.addView(TextView(this).apply { text = "PenaltyCount • signed Int32"; textSize = 17f })
        penaltyCount = EditText(this).apply {
            hint = "-2,147,483,648 to 2,147,483,647"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        root.addView(penaltyCount)
        root.addView(TextView(this).apply { text = "PenaltyTime • exact Float32 bits in hexadecimal"; textSize = 17f })
        penaltyTimeBits = EditText(this).apply {
            hint = "00000000 to FFFFFFFF"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        root.addView(penaltyTimeBits)
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
            text = "Back to Union Room Saved Data"
            setOnClickListener { if (!busy) finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(BdspUnionRoomEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                bytes to BdspUnionRoomEditing.read(bytes)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, read) ->
                    opened = bytes
                    initTalkWord.setText(read.record.rawInitTalkWord.toString())
                    penaltyCount.setText(read.record.penaltyCount.toString())
                    penaltyTimeBits.setText(hex(read.record.rawPenaltyTimeBits))
                    status.text = describe(read) + "\nSource SHA-256: ${BdspUnionRoomEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The save could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun hex(word: Long): String = word.toString(16).uppercase().padStart(8, '0')

    private fun flag(word: Long): String = when (word) {
        0L -> "Clear"
        1L -> "Set"
        else -> "Unknown ($word)"
    }

    private fun describe(read: BdspUnionRoomEditRead): String {
        val record = read.record
        return "${read.version.displayName} • Save layout ${read.revision.displayName}\n" +
            "Initial-talk flag: ${flag(record.rawInitTalkWord)} • raw ${record.rawInitTalkWord} • hex ${hex(record.rawInitTalkWord)}\n" +
            "PenaltyCount: ${record.penaltyCount} • raw hex ${hex(record.rawPenaltyCountWord)}\n" +
            "PenaltyTime: ${record.penaltyTime} • exact bits ${hex(record.rawPenaltyTimeBits)}"
    }

    private fun parseBits(value: String): Long? {
        val digits = value.trim().removePrefix("0x").removePrefix("0X")
        if (digits.length != 8 || digits.any {
                it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F'
            }) return null
        return digits.toLongOrNull(16)
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null
        initTalkWord.isEnabled = !value && ready
        penaltyCount.isEnabled = !value && ready
        penaltyTimeBits.isEnabled = !value && ready
        review.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val talk = initTalkWord.text.toString().trim().toLongOrNull()
        val count = penaltyCount.text.toString().trim().toIntOrNull()
        val timeBits = parseBits(penaltyTimeBits.text.toString())
        if (talk == null || talk !in 0L..0xFFFF_FFFFL || count == null || timeBits == null) {
            status.text = "Enter a UInt32 initial-talk word, a complete signed Int32 PenaltyCount, and exactly eight hexadecimal digits for PenaltyTime. A changed initial-talk word must be 0 or 1."
            return
        }
        val plan = try {
            BdspUnionRoomEditing.stage(bytes, hash, BdspUnionRoomReplacement(talk, count, timeBits))
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested Union Room change is unavailable."
            return
        }
        val scopes = plan.allowedRanges.joinToString(", ") { range ->
            "0x${range.first.toString(16).uppercase()}–0x${range.last.toString(16).uppercase()}"
        }
        setBusy(true)
        var dispatched = false
        AlertDialog.Builder(this).setTitle("Save Union Room data to a new copy?")
            .setMessage(
                "Before\n${describe(plan.before)}\n\nAfter\n${describe(plan.after)}\n\n" +
                    "Changed native fields: ${plan.changedFields.joinToString()}.\n" +
                    "Allowed byte scope: $scopes, plus the native whole-file MD5. Every unrelated byte remains exact. PSE will verify a recovery backup and reopen the persisted copy. Choose a NEW empty document; the selected source cannot be replaced."
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
                        "Union Room copy saved and reopened successfully.\n${describe(plan.after)}\n" +
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
