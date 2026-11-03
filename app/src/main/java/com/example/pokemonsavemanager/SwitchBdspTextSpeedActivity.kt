package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/** Explicit opt-in editor; the existing inspection session and source are never mutated. */
class SwitchBdspTextSpeedActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2102
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var opened: ByteArray? = null
    private var pendingPlan: BdspTextSpeedCopyPlan? = null
    private var busy = false
    private var setting = BdspEditableSetting.TEXT_SPEED
    private lateinit var status: TextView
    private lateinit var speed: Spinner
    private lateinit var review: Button
    private lateinit var inspectCopy: Button
    private var verifiedCopy: Pair<Uri, String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        val requestedSetting = intent?.getStringExtra("pse.switch.setting")
        if (requestedSetting != null) {
            setting = BdspEditableSetting.values().firstOrNull { it.name == requestedSetting } ?: run { finish(); return }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "BDSP • Edit ${setting.label}"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "Choose ${setting.choices.joinToString(" / ")} and save a new native save copy. PSE retains a verified recovery backup. Your selected source stays unchanged. Reimport the edited copy to inspect it. Other Switch edits and source replacement remain unavailable."
            textSize = 16f
        })
        status = TextView(this).apply { text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true) }
        if (setting == BdspEditableSetting.PARTY_BOX) root.addView(TextView(this).apply {
            text = "This changes a saved preference. Party and box Pokémon stay in their current slots."
            textSize = 16f
        })
        if (setting == BdspEditableSetting.NICKNAME_REGISTRATION) root.addView(TextView(this).apply {
            text = "This changes the saved nickname-registration preference. Existing Pokémon names stay unchanged."
            textSize = 16f
        })
        if (setting == BdspEditableSetting.GYRO_SENSOR) root.addView(TextView(this).apply {
            text = "This changes the gyro-sensor preference stored in the native save."
            textSize = 16f
        })
        if (setting == BdspEditableSetting.FOSSIL_CAMERA_SHAKE) root.addView(TextView(this).apply {
            text = "This changes the fossil camera-shake preference stored in the native save."
            textSize = 16f
        })
        if (setting == BdspEditableSetting.AUTO_REPORT) root.addView(TextView(this).apply {
            text = "This changes the auto-report preference stored in the native save. PSE writes only the new copy you review."
            textSize = 16f
        })
        if (setting == BdspEditableSetting.KANJI_SCRIPT) root.addView(TextView(this).apply {
            text = "This changes the stored Kanji-script preference. The saved language and Pokémon text stay unchanged."
            textSize = 16f
        })
        root.addView(status)
        speed = Spinner(this).apply {
            contentDescription = "New ${setting.label}"
            adapter = ArrayAdapter(this@SwitchBdspTextSpeedActivity, android.R.layout.simple_spinner_dropdown_item,
                setting.choices)
        }
        root.addView(speed)
        review = Button(this).apply { text = "Review and save new copy"; setOnClickListener { reviewChange() } }
        root.addView(review)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"; visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply { text = "Back to save"; setOnClickListener { if (!busy) finish() } })
        setContentView(ScrollView(this).apply { addView(root) })
        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(BdspTextSpeedEditing.sha256(bytes) == expectedHash) { "The source changed. Reopen it from PSE." }
                val options = BdspGameOptionsReadOnly.read(bytes)
                require(setting.acceptsSaved(setting.current(options))) { "Unknown saved ${setting.label}; editing is unavailable." }
                bytes to options
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, options) ->
                    opened = bytes
                    val current = setting.current(options)
                    speed.setSelection(current.toInt().coerceAtMost(setting.choices.lastIndex))
                    status.text = "${options.version.displayName} • Layout ${options.revision.displayName}\n" +
                        "Saved ${setting.label}: ${setting.savedLabel(current)} (ID $current)\n" +
                        "Source SHA-256: ${BdspTextSpeedEditing.sha256(bytes)}\n" +
                        if (setting == BdspEditableSetting.TEXT_SPEED && current == 3L) "Instant is preserved until you explicitly select a different speed." else "Select a different value to review."
                }.onFailure { status.text = it.message ?: "The save could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        speed.isEnabled = !value && opened != null
        review.isEnabled = !value && opened != null
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun inspectVerifiedCopy() {
        if (busy) return
        val (uri, hash) = verifiedCopy ?: return
        try {
            startActivity(Intent(this, SwitchReadOnlyActivity::class.java)
                .setData(uri).putExtra("pse.switch.family", "BRILLIANT_DIAMOND_SHINING_PEARL")
                .putExtra("pse.switch.sha256", hash)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
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
        return BdspTextSpeedEditing.readDocumentBytes(input)
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selected = speed.selectedItemPosition
        val plan = try { when (setting) {
            BdspEditableSetting.BGM_VOLUME -> BdspTextSpeedEditing.stageMusicVolume(bytes, hash, selected)
            BdspEditableSetting.SOUND_EFFECTS_VOLUME -> BdspTextSpeedEditing.stageSoundEffectsVolume(bytes, hash, selected)
            BdspEditableSetting.TEXT_SPEED -> BdspTextSpeedEditing.stage(bytes, hash, selected)
            BdspEditableSetting.BATTLE_STYLE -> BdspTextSpeedEditing.stageBattleStyle(bytes, hash, selected)
            BdspEditableSetting.MOVE_ANIMATIONS -> BdspTextSpeedEditing.stageMoveAnimations(bytes, hash, selected)
            BdspEditableSetting.PARTY_BOX -> BdspTextSpeedEditing.stagePartyBox(bytes, hash, selected)
            BdspEditableSetting.CAMERA_VERTICAL -> BdspTextSpeedEditing.stageVerticalCamera(bytes, hash, selected)
            BdspEditableSetting.CAMERA_HORIZONTAL -> BdspTextSpeedEditing.stageHorizontalCamera(bytes, hash, selected)
            BdspEditableSetting.INPUT_MODE -> BdspTextSpeedEditing.stageInputMode(bytes, hash, selected)
            BdspEditableSetting.SHOW_NICKNAMES -> BdspTextSpeedEditing.stageShowNicknames(bytes, hash, selected)
            BdspEditableSetting.NICKNAME_REGISTRATION -> BdspTextSpeedEditing.stageNicknameRegistration(bytes, hash, selected)
            BdspEditableSetting.GYRO_SENSOR -> BdspTextSpeedEditing.stageGyroSensor(bytes, hash, selected)
            BdspEditableSetting.FOSSIL_CAMERA_SHAKE -> BdspTextSpeedEditing.stageFossilCameraShake(bytes, hash, selected)
            BdspEditableSetting.AUTO_REPORT -> BdspTextSpeedEditing.stageAutoReport(bytes, hash, selected)
            BdspEditableSetting.KANJI_SCRIPT -> BdspTextSpeedEditing.stageKanjiScript(bytes, hash, selected)
        } }
        catch (failure: Exception) { status.text = failure.message; return }
        val old = BdspGameOptionsReadOnly.read(bytes)
        setBusy(true)
        var dispatched = false
        AlertDialog.Builder(this).setTitle("Save changed ${setting.label} to a new copy?")
            .setMessage("${old.version.displayName}\n${setting.savedLabel(setting.current(old))} → ${setting.savedLabel(selected.toLong())}\n\nOnly ${setting.label} and the native integrity digest may change. PSE will verify a recovery backup and reopen the saved copy. Choose a NEW empty document; existing files and the selected source cannot be replaced.")
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
                        pendingPlan = null; setBusy(false)
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
            status.text = "Save cancelled or review expired. Nothing was written. Reopen the editor after an activity restart."
            setBusy(false); return
        }
        if (uri.scheme != "content" || uri == sourceUri) {
            status.text = "Choose a new document separate from the source. Nothing was written."
            setBusy(false); return
        }
        setBusy(true)
        status.text = "Saving and verifying a new copy…"
        worker.execute {
            var backupName: String? = null
            val result = runCatching {
                plan.saveNewCopy(
                    readSource = { readSource() },
                    retainBackup = { original ->
                        val dir = File(filesDir, "switch-recovery")
                        check(dir.isDirectory || dir.mkdirs()) { "The recovery folder could not be created." }
                        val file = File.createTempFile("BDSP-${plan.setting.name}-", ".backup", dir)
                        backupName = file.name
                        FileOutputStream(file).use { it.write(original); it.fd.sync() }
                        file.readBytes()
                    },
                    readDestination = { readDocument(uri) },
                    writeDestination = { edited ->
                        val output = contentResolver.openOutputStream(uri, "wt") ?: error("No writable stream.")
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
                    onSuccess = { "Edited copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\nOriginal source unchanged. Tap Inspect verified copy to reopen it in PSE." },
                    onFailure = { (it.message ?: "The copy could not be verified.") + "\nRecovery backup: ${backupName ?: "not created"}" }
                )
                setBusy(false)
            }
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }

    override fun onDestroy() {
        pendingPlan = null
        worker.shutdown()
        super.onDestroy()
    }
}
