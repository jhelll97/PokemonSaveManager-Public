package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.concurrent.Executors

/** Explicit Z-A Pokédex registration editor. It writes a reviewed new copy and never replaces the source. */
class SwitchLegendsZaPokedexActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2113
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var section: LegendsZaDexSection? = null
    private var number = -1
    private var opened: ByteArray? = null
    private var pendingPlan: LegendsZaDexCopyPlan? = null
    private var busy = false
    private var verifiedCopy: Pair<Uri, String>? = null
    private lateinit var status: TextView
    private lateinit var action: Spinner
    private lateinit var form: EditText
    private lateinit var gender: Spinner
    private lateinit var shiny: Spinner
    private lateinit var language: Spinner
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        section = intent?.getStringExtra("pse.switch.dex.section")?.let { name ->
            LegendsZaDexSection.values().firstOrNull { it.name == name }
        }
        number = intent?.getIntExtra("pse.switch.dex.number", -1) ?: -1
        val selectedSection = section
        if (sourceUri == null || expectedHash.isNullOrBlank() || selectedSection == null || number < 1) {
            finish()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Legends: Z-A • Pokédex"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "${selectedSection.displayName} #$number\nAdd one native form registration to a new copy. Display selections, unknown bits, unrelated forms, and every other species stay unchanged."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        action = Spinner(this).apply {
            adapter = ArrayAdapter(this@SwitchLegendsZaPokedexActivity,
                android.R.layout.simple_spinner_dropdown_item, listOf("Register as seen", "Register as caught"))
        }
        root.addView(action)
        form = EditText(this).apply {
            hint = "Native form ID (0–31)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("0")
        }
        root.addView(form)
        gender = Spinner(this).apply {
            adapter = ArrayAdapter(this@SwitchLegendsZaPokedexActivity,
                android.R.layout.simple_spinner_dropdown_item, listOf("Male", "Female", "Genderless"))
        }
        root.addView(gender)
        shiny = Spinner(this).apply {
            adapter = ArrayAdapter(this@SwitchLegendsZaPokedexActivity,
                android.R.layout.simple_spinner_dropdown_item, listOf("Normal", "Shiny"))
        }
        root.addView(shiny)
        language = Spinner(this).apply {
            adapter = ArrayAdapter(this@SwitchLegendsZaPokedexActivity,
                android.R.layout.simple_spinner_dropdown_item, LegendsZaPokedexEditing.languageNames)
        }
        root.addView(language)
        root.addView(TextView(this).apply {
            text = "Language is applied only when registering caught. Native display selections are not changed."
            textSize = 14f
        })
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
            text = "Back to Pokédex"
            setOnClickListener { if (!busy) finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LegendsZaPokedexEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                val dex = LegendsZaPokedexReadOnly.read(bytes)
                val entry = dex.section(selectedSection)?.entry(number)
                    ?: error("Reopen the native Pokédex entry from PSE.")
                Triple(bytes, dex, entry)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, dex, entry) ->
                    opened = bytes
                    entry.displayForm.takeIf { it in 0..31 }?.let { form.setText(it.toString()) }
                    status.text = "${dex.version.displayName} • ${dex.revision?.displayName ?: "Revision unknown"}\n" +
                        "${ScarletVioletSpeciesNames.name(entry.speciesId)} (National #${entry.speciesId})\n" +
                        "Seen ${entry.seen} • Caught ${entry.caught} • Shiny ${entry.shinyRecorded}\n" +
                        "Seen forms: ${entry.seenForms} • Caught forms: ${entry.caughtForms}\n" +
                        "Source SHA-256: ${LegendsZaPokedexEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The save could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null
        action.isEnabled = !value && ready
        form.isEnabled = !value && ready
        gender.isEnabled = !value && ready
        shiny.isEnabled = !value && ready
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
        return LegendsZaPokedexEditing.readDocumentBytes(input)
    }

    private fun inspectVerifiedCopy() {
        if (busy) return
        val (uri, hash) = verifiedCopy ?: return
        try {
            startActivity(Intent(this, SwitchReadOnlyActivity::class.java).setData(uri)
                .putExtra("pse.switch.family", SwitchGameFamily.LEGENDS_ZA.name)
                .putExtra("pse.switch.sha256", hash)
                .putExtra("pse.switch.section", "pokedex")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (failure: Exception) {
            status.text = failure.message ?: "The verified copy could not be opened for inspection."
        }
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedSection = section ?: return
        val mode = if (action.selectedItemPosition == 0) LegendsZaDexEditMode.SEEN else LegendsZaDexEditMode.CAUGHT
        val selectedForm = form.text.toString().toIntOrNull()
        if (selectedForm == null) {
            status.text = "Enter a native form ID from 0 through 31."
            return
        }
        val plan = try {
            LegendsZaPokedexEditing.stage(bytes, hash, selectedSection, number, selectedForm,
                gender.selectedItemPosition, shiny.selectedItemPosition == 1, mode,
                language.selectedItemPosition.takeIf { mode == LegendsZaDexEditMode.CAUGHT })
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested registration is unavailable."
            return
        }
        setBusy(true)
        var dispatched = false
        val ranges = plan.allowedEncryptedRanges.joinToString { range ->
            if (range.first == range.last) "0x${range.first.toString(16).uppercase()}"
            else "0x${range.first.toString(16).uppercase()}–0x${range.last.toString(16).uppercase()}"
        }
        AlertDialog.Builder(this)
            .setTitle("Save Pokédex registration to a new copy?")
            .setMessage(
                "${selectedSection.displayName} #$number ${ScarletVioletSpeciesNames.name(plan.before.speciesId)}\n" +
                    "${plan.beforeLabel} → ${plan.afterLabel}\n" +
                    "Form $selectedForm • ${listOf("Male", "Female", "Genderless")[plan.gender]} • ${if (plan.shiny) "Shiny" else "Normal"}\n\n" +
                    "Allowed encrypted byte ranges: $ranges plus the container SHA-256 footer. Display selections and unrelated native values remain unchanged. " +
                    "PSE will verify a recovery backup and reopen the persisted copy. Choose a NEW empty document."
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
                            putExtra(Intent.EXTRA_TITLE, "main")
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
                        val backup = SwitchSafeBackupStore.createVerified(this, SwitchGameFamily.LEGENDS_ZA, original)
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(this, SwitchGameFamily.LEGENDS_ZA, backup.file, backup.sha256)
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
                        "Edited copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\n" +
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

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
