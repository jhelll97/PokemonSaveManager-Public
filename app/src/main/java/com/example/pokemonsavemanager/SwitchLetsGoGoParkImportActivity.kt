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
import java.util.concurrent.Executors

/** Imports one structurally validated GP1 into one canonical empty slot in a verified new copy. */
class SwitchLetsGoGoParkImportActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val pickGp1Request = 2121
    private val createCopyRequest = 2122
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var opened: ByteArray? = null
    private var emptySlots: List<Int> = emptyList()
    private var imported: LetsGoGp1ImportRead? = null
    private var pendingPlan: LetsGoGoParkImportCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var target: Spinner
    private lateinit var pick: Button
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data; expectedHash = intent?.getStringExtra("pse.switch.sha256")
        if (sourceUri == null || expectedHash.isNullOrBlank()) { finish(); return }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt(); setPadding(pad, pad * 2, pad, pad) }
        root.addView(TextView(this).apply { text = "Let's Go • Import GO Park GP1"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Import one exact-size native GP1 into one canonical empty GO Park slot. PSE writes only a reviewed new save copy."; textSize = 16f })
        status = TextView(this).apply { text = "Validating the source and empty slots…"; textSize = 16f; setTextIsSelectable(true) }
        root.addView(status)
        target = Spinner(this); root.addView(target)
        pick = Button(this).apply { text = "Choose native GP1 file"; setOnClickListener { pickGp1() } }; root.addView(pick)
        review = Button(this).apply { text = "Review import and save new copy"; setOnClickListener { reviewImport() } }; root.addView(review)
        inspectCopy = Button(this).apply { text = "Inspect verified copy"; visibility = android.view.View.GONE; setOnClickListener { inspectVerifiedCopy() } }; root.addView(inspectCopy)
        root.addView(Button(this).apply { text = "Back to GO Park"; setOnClickListener { if (!busy) finish() } })
        setContentView(ScrollView(this).apply { addView(root) })
        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource(); require(LetsGoGoParkEditing.sha256(bytes) == expectedHash) { "The source changed. Reopen it from PSE." }
                Triple(bytes, LetsGoGoParkEditing.canonicalEmptySlots(bytes), LetsGoGoParkReadOnly.read(bytes))
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, slots, read) ->
                    opened = bytes; emptySlots = slots
                    target.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, slots.map { slotLabel(it) })
                    status.text = "${read.version.displayName} • All 21 native block CRCs passed\n${slots.size} canonical empty GO Park slots available."
                }.onFailure { status.text = it.message ?: "The source could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) { busy = value; target.isEnabled = !value && emptySlots.isNotEmpty(); pick.isEnabled = !value && opened != null && emptySlots.isNotEmpty(); review.isEnabled = !value && imported != null && emptySlots.isNotEmpty(); inspectCopy.isEnabled = !value && verifiedCopy != null }
    private fun slotLabel(index: Int): String = "GO Park ${index / 50 + 1} • Slot ${index % 50 + 1} • Native index $index"
    private fun readSource(): ByteArray { val uri = sourceUri ?: error("Reopen the source from PSE."); require(uri.scheme == "content") { "A document source is required." }; return readDocument(uri) }
    private fun readDocument(uri: Uri): ByteArray { val input = contentResolver.openInputStream(uri) ?: error("The document cannot be read."); return LetsGoGoParkEditing.readDocumentBytes(input) }

    private fun pickGp1() {
        if (busy) return; setBusy(true)
        try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "application/octet-stream"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, pickGp1Request) }
        catch (failure: Exception) { setBusy(false); status.text = failure.message ?: "The GP1 picker could not be opened." }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickGp1Request) { handlePickedGp1(resultCode, data?.data); return }
        if (requestCode != createCopyRequest) return
        val plan = pendingPlan; pendingPlan = null; val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || plan == null) { status.text = "Save cancelled or review expired. Nothing was written."; setBusy(false); return }
        if (uri.scheme != "content" || uri == sourceUri) { status.text = "Choose a new document separate from the source. Nothing was written."; setBusy(false); return }
        setBusy(true); status.text = "Saving and verifying a new copy…"
        worker.execute {
            var backupName: String? = null
            val result = runCatching { plan.saveNewCopy(readSource = { readSource() }, retainBackup = { original ->
                val backup = SwitchSafeBackupStore.createVerified(this, SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, original); backupName = backup.file.name
                SwitchSafeBackupStore.readVerified(this, SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, backup.file, backup.sha256)
            }, readDestination = { readDocument(uri) }, writeDestination = { edited ->
                val output = contentResolver.openOutputStream(uri, "wt") ?: error("The new document is not writable."); output.use { it.write(edited); it.flush() }
            }, discardDestination = { DocumentsContract.deleteDocument(contentResolver, uri) }) }
            runOnUiThread { if (isFinishing || isDestroyed) return@runOnUiThread
                verifiedCopy = result.getOrNull()?.let { uri to plan.editedSha256 }; inspectCopy.visibility = if (verifiedCopy != null) android.view.View.VISIBLE else android.view.View.GONE
                status.text = result.fold(onSuccess = { "Imported copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\nOriginal source unchanged." }, onFailure = { (it.message ?: "The copy could not be verified.") + "\nRecovery backup: ${backupName ?: "not created"}" }); setBusy(false) }
        }
    }

    private fun handlePickedGp1(resultCode: Int, uri: Uri?) {
        if (resultCode != RESULT_OK || uri == null) { status.text = "GP1 selection cancelled."; setBusy(false); return }
        worker.execute {
            val result = runCatching { LetsGoGoParkEditing.inspectGp1(readGp1(uri)) }
            runOnUiThread { if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { read -> imported = read; status.text = "Validated native GP1: #${read.species} ${LetsGoSpeciesNames.name(read.species)} • Level ${read.level} • CP ${read.cp}\nGO IVs ${read.ivHp}/${read.ivAttack}/${read.ivDefense} • Form ${read.form} • Shiny ${if (read.shiny) "Yes" else "No"}" }
                    .onFailure { imported = null; status.text = it.message ?: "The GP1 file is invalid." }; setBusy(false) }
        }
    }

    private fun readGp1(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("The GP1 file cannot be read.")
        return input.use {
            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(512)
            while (true) {
                val remaining = LetsGoGoParkReadOnly.RECORD_SIZE + 1 - output.size()
                val count = it.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                if (count == 0) {
                    val single = it.read(); if (single < 0) break; output.write(single)
                } else output.write(buffer, 0, count)
                require(output.size() <= LetsGoGoParkReadOnly.RECORD_SIZE) { "The GP1 file exceeds 0x1B0 bytes." }
            }
            output.toByteArray()
        }
    }

    private fun reviewImport() {
        if (busy || pendingPlan != null) return; val bytes = opened ?: return; val gp1 = imported ?: return
        val index = emptySlots.getOrNull(target.selectedItemPosition) ?: return
        val plan = try { LetsGoGoParkEditing.stageImport(bytes, expectedHash ?: return, index, gp1.bytes) }
            catch (failure: Exception) { status.text = failure.message ?: "The GP1 import is unavailable."; return }
        setBusy(true); var dispatched = false
        AlertDialog.Builder(this).setTitle("Import this GP1 to a new save copy?")
            .setMessage("Target: ${slotLabel(index)}\nBefore: Canonical empty GP1 slot\nAfter: #${gp1.species} ${LetsGoSpeciesNames.name(gp1.species)} • Level ${gp1.level} • CP ${gp1.cp}\n\nAllowed byte scope: one 0x1B0-byte GP1 slot at 0x${plan.recordOffset.toString(16).uppercase()} plus block 18's CRC. Every other save byte stays unchanged. Choose a NEW empty document.")
            .setNegativeButton("Cancel", null).setOnDismissListener { if (!dispatched) setBusy(false) }
            .setPositiveButton("Choose new copy") { _, _ -> if (!dispatched && !isFinishing && !isDestroyed) { dispatched = true; pendingPlan = plan; verifiedCopy = null; inspectCopy.visibility = android.view.View.GONE
                try { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "application/octet-stream"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_TITLE, "SaveData.bin"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }, createCopyRequest) }
                catch (failure: Exception) { pendingPlan = null; setBusy(false); status.text = failure.message ?: "The save picker could not be opened. Nothing was written." } } }.show()
    }

    private fun inspectVerifiedCopy() { if (busy) return; val (uri, hash) = verifiedCopy ?: return; startActivity(Intent(this, SwitchReadOnlyActivity::class.java).setData(uri).putExtra("pse.switch.family", SwitchGameFamily.LETS_GO_PIKACHU_EEVEE.name).putExtra("pse.switch.sha256", hash).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
    @Deprecated("Legacy Activity back callback") override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
