package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Native PK9 Shiny-status editor with reviewed new-copy persistence. */
class SwitchScarletVioletPokemonShinyActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2218
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: ScarletVioletPokemonArea? = null
    private var box = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var pendingPlan: ScarletVioletPokemonShinyCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var standard: RadioButton
    private lateinit var shinyChoice: RadioButton
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        area = runCatching {
            ScarletVioletPokemonArea.valueOf(intent?.getStringExtra("pse.switch.pokemon.area") ?: "")
        }.getOrNull()
        box = intent?.getIntExtra("pse.switch.pokemon.box", 0) ?: 0
        position = intent?.getIntExtra("pse.switch.pokemon.position", 0) ?: 0
        val selectedArea = area
        if (sourceUri == null || expectedHash.isNullOrBlank() || selectedArea == null ||
            position !in 1..30 || (selectedArea == ScarletVioletPokemonArea.BOX && box !in 1..32) ||
            (selectedArea == ScarletVioletPokemonArea.PARTY && position !in 1..6)) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Scarlet/Violet • Shiny status"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "Change the selected PK9's derived Shiny state through its PID high word. Eggs, fateful-event Pokémon and the dedicated ride record remain protected. Encounter correlation and Shiny-lock legality are not inferred."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source and PK9…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        val choices = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        standard = RadioButton(this).apply { text = "Not shiny" }
        shinyChoice = RadioButton(this).apply { text = "Shiny" }
        choices.addView(standard); choices.addView(shinyChoice); root.addView(choices)
        root.addView(TextView(this).apply {
            text = "PSE preserves the PID low word, Trainer ID and Secret ID and changes only the PID high word to shiny XOR 0 or non-shiny XOR 16, plus the PK9 checksum, selected encrypted record and required save footer. Species, form, size, stats, moves, trainer identity, every other slot and all unrelated save data remain exact."
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
            text = "Back to Pokémon Details"
            setOnClickListener { if (!busy) finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(ScarletVioletPokemonShinyEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                bytes to ScarletVioletPokemonShinyEditing.read(bytes, selectedArea, box, position)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, pokemon) ->
                    opened = bytes
                    if (pokemon.shiny) shinyChoice.isChecked = true else standard.isChecked = true
                    status.text = describe(pokemon) +
                        "\nSource SHA-256: ${ScarletVioletPokemonShinyEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The selected PK9 could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun describe(pokemon: ScarletVioletPokemonRead): String =
        "${ScarletVioletSpeciesNames.name(pokemon.species)} (#${pokemon.species}) • Form ${pokemon.form}\n" +
            "Nickname: ${pokemon.nickname.ifBlank { "None" }}\nSaved Shiny status: ${label(pokemon.shiny)}"

    private fun label(value: Boolean): String = if (value) "Shiny" else "Not shiny"

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null
        standard.isEnabled = !value && ready
        shinyChoice.isEnabled = !value && ready
        review.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        val requested = when {
            shinyChoice.isChecked -> true
            standard.isChecked -> false
            else -> {
                status.text = "Choose Not shiny or Shiny before review."
                return
            }
        }
        val plan = try {
            ScarletVioletPokemonShinyEditing.stage(
                bytes, hash, selectedArea, box, position, requested
            )
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested Shiny change is unavailable."
            return
        }
        setBusy(true)
        var dispatched = false
        val recordSize = ScarletVioletPokemonReadOnly.RECORD_SIZE
        AlertDialog.Builder(this).setTitle("Save Shiny status to a new copy?")
            .setMessage(
                "${ScarletVioletSpeciesNames.name(plan.before.species)} • ${label(plan.before.shiny)} → ${label(plan.after.shiny)}\n\n" +
                    "Allowed native scope: the selected $recordSize-byte encrypted PK9 at 0x${plan.absoluteOffset.toString(16).uppercase()} plus the native SHA-256 footer. " +
                    "Inside PK9, only PID high word 0x1E–0x1F and checksum bytes 0x06–0x07 change; PID low word, Trainer ID and Secret ID remain exact. Every other Pokémon and save value remains exact. This direct PID edit can violate encounter correlation, species-specific Shiny locks or transfer-origin legality and is not a legality certification. " +
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
                        val backup = SwitchSafeBackupStore.createVerified(
                            this, SwitchGameFamily.SCARLET_VIOLET, original
                        )
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.SCARLET_VIOLET, backup.file, backup.sha256
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
                        "Shiny-status copy saved and reopened successfully.\n${describe(plan.after)}\n" +
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
            .putExtra("pse.switch.family", SwitchGameFamily.SCARLET_VIOLET.name)
            .putExtra("pse.switch.sha256", hash)
            .putExtra("pse.switch.section", "storage")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return readDocument(uri)
    }

    private fun readDocument(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
        return ScarletVioletPokemonShinyEditing.readDocumentBytes(input)
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
