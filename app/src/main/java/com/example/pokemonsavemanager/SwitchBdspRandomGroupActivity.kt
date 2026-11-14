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
import android.widget.TextView
import java.util.concurrent.Executors

/** Native one-record BDSP random-group editor with reviewed new-copy persistence. */
class SwitchBdspRandomGroupActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2148
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var recordIndex = -1
    private var opened: ByteArray? = null
    private var openedRead: BdspRandomGroupEditRead? = null
    private var pendingPlan: BdspRandomGroupCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var groupName: EditText
    private lateinit var replaceMalformedGroupName: CheckBox
    private lateinit var playerName: EditText
    private lateinit var replaceMalformedPlayerName: CheckBox
    private lateinit var maleByte: EditText
    private lateinit var regionCode: EditText
    private lateinit var seed: EditText
    private lateinit var random: EditText
    private lateinit var ticks: EditText
    private lateinit var userId: EditText
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        recordIndex = intent?.getIntExtra("pse.switch.bdsp.random.group.index", -1) ?: -1
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
        root.addView(TextView(this).apply { text = "BDSP • Random Group Records"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "Edit one documented native 132-byte record in saved order. PSE preserves its three alignment bytes and the other eleven records. Saved names and IDs do not verify an online identity, membership, or usable group; RNG words are not advanced, and ticks are not converted into a claimed timestamp."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(label("Group name • at most 16 UTF-16 code units"))
        groupName = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS }
        root.addView(groupName)
        replaceMalformedGroupName = CheckBox(this).apply {
            text = "Replace the unreadable native group-name buffer"; visibility = View.GONE
            setOnCheckedChangeListener { _, _ -> updateControls() }
        }
        root.addView(replaceMalformedGroupName)
        root.addView(label("Saved player name • at most 32 UTF-16 code units"))
        playerName = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS }
        root.addView(playerName)
        replaceMalformedPlayerName = CheckBox(this).apply {
            text = "Replace the unreadable native player-name buffer"; visibility = View.GONE
            setOnCheckedChangeListener { _, _ -> updateControls() }
        }
        root.addView(replaceMalformedPlayerName)
        root.addView(label("Male flag byte • changed values: 0 clear or 1 set"))
        maleByte = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        root.addView(maleByte)
        root.addView(label("Region code • signed Int32"))
        regionCode = EditText(this).apply { inputType = signedNumber }
        root.addView(regionCode)
        root.addView(label("Seed • UInt64"))
        seed = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        root.addView(seed)
        root.addView(label("Random • UInt64"))
        random = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        root.addView(random)
        root.addView(label("Ticks • signed Int64"))
        ticks = EditText(this).apply { inputType = signedNumber }
        root.addView(ticks)
        root.addView(label("User ID • signed Int32"))
        userId = EditText(this).apply { inputType = signedNumber }
        root.addView(userId)
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
            text = "Back to Random Group Records"
            setOnClickListener { if (!busy) finish() }
        })
        OmegaRubyUi.stylePokemonEditor(this, root, status)
        setContentView(OmegaRubyUi.scrollingPage(this, root))

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(BdspRandomGroupEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                bytes to BdspRandomGroupEditing.read(bytes, recordIndex)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, read) ->
                    opened = bytes
                    openedRead = read
                    bindName(groupName, replaceMalformedGroupName, read.record.groupName)
                    bindName(playerName, replaceMalformedPlayerName, read.record.playerName)
                    maleByte.setText(read.record.rawMaleByte.toString())
                    regionCode.setText(read.record.regionCode.toString())
                    seed.setText(read.record.seed.toString())
                    random.setText(read.record.random.toString())
                    ticks.setText(read.record.ticks.toString())
                    userId.setText(read.record.userId.toString())
                    status.text = describe(read) + "\nSource SHA-256: ${BdspRandomGroupEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The save could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun bindName(field: EditText, replace: CheckBox, value: BdspRandomGroupNameRead) {
        if (value.text == null) {
            field.setText("")
            field.hint = value.unavailableReason ?: "Unreadable native name"
            replace.visibility = View.VISIBLE
            replace.isChecked = false
        } else {
            field.setText(value.text)
            replace.visibility = View.GONE
        }
    }

    private fun displayName(value: BdspRandomGroupNameRead): String =
        value.text?.let { "\"$it\"" } ?: "Unavailable (${value.unavailableReason})"

    private fun describe(read: BdspRandomGroupEditRead): String {
        val r = read.record
        val male = when (r.rawMaleByte) { 0 -> "Clear"; 1 -> "Set"; else -> "Unknown (${r.rawMaleByte})" }
        return "${read.version.displayName} • Save layout ${read.revision.displayName}\n" +
            "Saved random-group index ${r.index}\nGroup name: ${displayName(r.groupName)}\n" +
            "Player name: ${displayName(r.playerName)}\nMale flag: $male\nRegion code: ${r.regionCode}\n" +
            "Seed: ${r.seed} • hex ${r.seed.toString(16).uppercase().padStart(16, '0')}\n" +
            "Random: ${r.random} • hex ${r.random.toString(16).uppercase().padStart(16, '0')}\n" +
            "Ticks: ${r.ticks} • bits ${r.ticks.toULong().toString(16).uppercase().padStart(16, '0')}\n" +
            "User ID: ${r.userId}"
    }

    private fun updateControls() {
        val read = openedRead
        val ready = opened != null && read != null
        val groupReadable = read?.record?.groupName?.text != null
        val playerReadable = read?.record?.playerName?.text != null
        groupName.isEnabled = !busy && ready && (groupReadable || replaceMalformedGroupName.isChecked)
        replaceMalformedGroupName.isEnabled = !busy && ready && !groupReadable
        playerName.isEnabled = !busy && ready && (playerReadable || replaceMalformedPlayerName.isChecked)
        replaceMalformedPlayerName.isEnabled = !busy && ready && !playerReadable
        maleByte.isEnabled = !busy && ready
        regionCode.isEnabled = !busy && ready
        seed.isEnabled = !busy && ready
        random.isEnabled = !busy && ready
        ticks.isEnabled = !busy && ready
        userId.isEnabled = !busy && ready
        review.isEnabled = !busy && ready
        inspectCopy.isEnabled = !busy && verifiedCopy != null
    }

    private fun setBusy(value: Boolean) {
        busy = value
        updateControls()
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val read = openedRead ?: return
        val male = maleByte.text.toString().trim().toIntOrNull()
        val region = regionCode.text.toString().trim().toIntOrNull()
        val seedValue = seed.text.toString().trim().toULongOrNull()
        val randomValue = random.text.toString().trim().toULongOrNull()
        val ticksValue = ticks.text.toString().trim().toLongOrNull()
        val user = userId.text.toString().trim().toIntOrNull()
        if (male == null || male !in 0..255 || region == null || seedValue == null ||
            randomValue == null || ticksValue == null || user == null
        ) {
            status.text = "Enter complete values within each displayed native field range. A changed male flag must be 0 or 1."
            return
        }
        val groupValue = if (read.record.groupName.text != null || replaceMalformedGroupName.isChecked) groupName.text.toString() else null
        val playerValue = if (read.record.playerName.text != null || replaceMalformedPlayerName.isChecked) playerName.text.toString() else null
        val plan = try {
            BdspRandomGroupEditing.stage(
                bytes, hash, recordIndex,
                BdspRandomGroupReplacement(groupValue, playerValue, male, region, seedValue, randomValue, ticksValue, user)
            )
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested random-group change is unavailable."
            return
        }
        val scopes = plan.allowedRanges.joinToString(", ") { range ->
            val first = "0x${range.first.toString(16).uppercase()}"
            if (range.first == range.last) first else "$first–0x${range.last.toString(16).uppercase()}"
        }
        setBusy(true)
        var dispatched = false
        AlertDialog.Builder(this).setTitle("Save random-group record ${recordIndex + 1} to a new copy?")
            .setMessage(
                "Before\n${describe(plan.before)}\n\nAfter\n${describe(plan.after)}\n\n" +
                    "Changed native fields: ${plan.changedFields.joinToString()}.\n" +
                    "Allowed byte scope: $scopes, plus the native whole-file MD5. Alignment bytes, the other eleven records, and every unrelated byte remain exact. PSE will verify a recovery backup and reopen the persisted copy. Choose a NEW empty document; the selected source cannot be replaced."
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
                        "Random-group copy saved and reopened successfully.\n${describe(plan.after)}\n" +
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
