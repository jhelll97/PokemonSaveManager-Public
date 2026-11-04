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

/** Explicit Let's Go Pokédex registration editor. It saves only to a reviewed new copy. */
class SwitchLetsGoPokedexActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2109
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var speciesId = -1
    private var opened: ByteArray? = null
    private var pendingPlan: LetsGoDexCopyPlan? = null
    private var busy = false
    private var verifiedCopy: Pair<Uri, String>? = null
    private lateinit var status: TextView
    private lateinit var action: Spinner
    private lateinit var variant: Spinner
    private lateinit var language: Spinner
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        speciesId = intent?.getIntExtra("pse.switch.dex.species", -1) ?: -1
        if ((speciesId !in 1..151 && speciesId !in 808..809) || sourceUri == null || expectedHash.isNullOrBlank()) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Let's Go • Pokédex"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "#${speciesId} ${LetsGoSpeciesNames.name(speciesId)}\n" +
                "Add one native seen or caught registration to a new copy. Existing registrations and size records stay unchanged. PSE retains a verified recovery backup and reopens the copy."
            textSize = 16f
        })
        status = TextView(this).apply { text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true) }
        root.addView(status)
        action = Spinner(this).apply {
            adapter = ArrayAdapter(this@SwitchLetsGoPokedexActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Register as seen", "Register as caught"))
        }
        root.addView(action)
        variant = Spinner(this).apply {
            adapter = ArrayAdapter(this@SwitchLetsGoPokedexActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Male / genderless", "Female", "Shiny male / genderless", "Shiny female"))
        }
        root.addView(variant)
        language = Spinner(this).apply {
            adapter = ArrayAdapter(this@SwitchLetsGoPokedexActivity, android.R.layout.simple_spinner_dropdown_item,
                LetsGoPokedexEditing.languages)
        }
        root.addView(language)
        root.addView(TextView(this).apply { text = "Language is applied only when registering caught."; textSize = 14f })
        review = Button(this).apply { text = "Review and save new copy"; setOnClickListener { reviewChange() } }
        root.addView(review)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"; visibility = android.view.View.GONE; setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply { text = "Back to Pokédex"; setOnClickListener { if (!busy) finish() } })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LetsGoPokedexEditing.sha256(bytes) == expectedHash) { "The source changed. Reopen it from PSE." }
                bytes to LetsGoPokedexReadOnly.read(bytes).entry(speciesId)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, entry) ->
                    opened = bytes
                    status.text = "Seen: ${entry.seen} • Caught: ${entry.caught}\n" +
                        "Languages: ${entry.languages.joinToString().ifBlank { "None recorded" }}\n" +
                        "Source SHA-256: ${LetsGoPokedexEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The save could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null
        action.isEnabled = !value && ready
        variant.isEnabled = !value && ready
        language.isEnabled = !value && ready
        review.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return readDocument(uri)
    }

    private fun readDocument(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
        return LetsGoPokedexEditing.readDocumentBytes(input)
    }

    private fun inspectVerifiedCopy() {
        if (busy) return
        val (uri, hash) = verifiedCopy ?: return
        startActivity(Intent(this, SwitchReadOnlyActivity::class.java).setData(uri)
            .putExtra("pse.switch.family", SwitchGameFamily.LETS_GO_PIKACHU_EEVEE.name)
            .putExtra("pse.switch.sha256", hash).putExtra("pse.switch.section", "pokedex")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val mode = if (action.selectedItemPosition == 0) LetsGoDexEditMode.SEEN else LetsGoDexEditMode.CAUGHT
        val plan = try {
            LetsGoPokedexEditing.stage(bytes, hash, speciesId, variant.selectedItemPosition, mode,
                language.selectedItemPosition.takeIf { mode == LetsGoDexEditMode.CAUGHT })
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested Pokédex registration is unavailable."
            return
        }
        setBusy(true)
        var dispatched = false
        val offsets = plan.allowedOffsets.joinToString { "0x${it.toString(16).uppercase()}" }
        AlertDialog.Builder(this).setTitle("Save Pokédex registration to a new copy?")
            .setMessage("#${plan.speciesId} ${LetsGoSpeciesNames.name(plan.speciesId)}\n" +
                "${plan.beforeLabel} → ${plan.afterLabel}\n\n" +
                "Allowed byte scope: native registration bytes $offsets plus block 4's CRC. " +
                "PSE will verify a recovery backup and reopen the persisted copy. Choose a NEW empty document.")
            .setNegativeButton("Cancel", null).setOnDismissListener { if (!dispatched) setBusy(false) }
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
            status.text = "Save cancelled or review expired. Nothing was written."; setBusy(false); return
        }
        if (uri.scheme != "content" || uri == sourceUri) {
            status.text = "Choose a new document separate from the source. Nothing was written."; setBusy(false); return
        }
        setBusy(true)
        status.text = "Saving and verifying a new copy…"
        worker.execute {
            var backupName: String? = null
            val result = runCatching {
                plan.saveNewCopy(
                    readSource = { readSource() },
                    retainBackup = { original ->
                        val backup = SwitchSafeBackupStore.createVerified(this, SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, original)
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(this, SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, backup.file, backup.sha256)
                    },
                    readDestination = { readDocument(uri) },
                    writeDestination = { edited ->
                        val output = contentResolver.openOutputStream(uri, "wt") ?: error("The new document is not writable.")
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
                    onSuccess = { "Edited copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\nOriginal source unchanged." },
                    onFailure = { (it.message ?: "The copy could not be verified.") + "\nRecovery backup: ${backupName ?: "not created"}" }
                )
                setBusy(false)
            }
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }

    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
