package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.concurrent.Executors

/** Native PK9 Scarlet/Violet Origin Game editor with reviewed new-copy persistence. */
class SwitchScarletVioletPokemonOriginGameActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2230
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: ScarletVioletPokemonArea? = null
    private var box = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var values: ScarletVioletPokemonOriginGameValues? = null
    private var pendingPlan: ScarletVioletPokemonOriginGameCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var originGameSpinner: Spinner
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        area = intent?.getStringExtra("pse.switch.pokemon.area")?.let { value ->
            ScarletVioletPokemonArea.values().firstOrNull { it.name == value }
        }
        box = intent?.getIntExtra("pse.switch.pokemon.box", 0) ?: 0
        position = intent?.getIntExtra("pse.switch.pokemon.position", 0) ?: 0
        val selectedArea = area
        if (sourceUri == null || expectedHash.isNullOrBlank() || selectedArea == null ||
            position !in 1..30 || (selectedArea == ScarletVioletPokemonArea.BOX && box !in 1..32) ||
            (selectedArea == ScarletVioletPokemonArea.PARTY && position !in 1..6)) {
            finish()
            return
        }
        val location = if (selectedArea == ScarletVioletPokemonArea.BOX)
            "Box $box • Slot $position" else "Party $position"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Scarlet/Violet • Pokémon Details"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Edit Origin Game"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "$location\nChange the native PK9 Origin Game between Pokémon Scarlet and Pokémon Violet."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source and native PK9…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        originGameSpinner = Spinner(this).apply { isEnabled = false }
        root.addView(originGameSpinner)
        root.addView(TextView(this).apply {
            text = "PKHeX defines PK9 Version at byte 0xCE; Scarlet is 50 and Violet is 51. PSE edits only records already saved as Scarlet or Violet. Changing Origin Game rewrites provenance metadata without changing species, encounter, met data, trainer identity or any other field, so the result may be illegal. Eggs and fateful-event Pokémon remain protected."
            textSize = 14f
        })
        review = Button(this).apply {
            text = "Review Origin Game change"; isEnabled = false
            setOnClickListener { reviewChange() }
        }
        root.addView(review)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"; visibility = View.GONE
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
                require(ScarletVioletPokemonOriginGameEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                val validation = ScarletVioletSaveValidation.inspect(bytes)
                require(validation is ScarletVioletValidation.Verified) {
                    "A validated native Scarlet/Violet save is required."
                }
                Triple(
                    bytes,
                    validation.version.displayName,
                    ScarletVioletPokemonOriginGameEditing.read(bytes, selectedArea, box, position)
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, displayName, read) ->
                    opened = bytes
                    values = read
                    originGameSpinner.adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        read.choices
                    )
                    originGameSpinner.setSelection(
                        read.choices.indexOfFirst { it.id == read.originGame.id }.coerceAtLeast(0)
                    )
                    status.text = "$displayName • Native integrity verified\n" +
                        "$location • ${read.pokemon.nickname.ifBlank { ScarletVioletSpeciesNames.name(read.pokemon.species) }}\n" +
                        "Saved Origin Game: ${read.originGame}\n" +
                        "Source SHA-256: ${ScarletVioletPokemonOriginGameEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The selected PK9 could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null && values?.choices?.isNotEmpty() == true
        originGameSpinner.isEnabled = !value && ready
        review.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        val read = values ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        val selected = read.choices.getOrNull(originGameSpinner.selectedItemPosition) ?: return
        val plan = try {
            ScarletVioletPokemonOriginGameEditing.stage(
                bytes, hash, selectedArea, box, position, selected.id
            )
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested Origin Game change is unavailable."
            return
        }
        setBusy(true)
        var dispatched = false
        AlertDialog.Builder(this).setTitle("Save this Origin Game to a new copy?")
            .setMessage(
                "${plan.location} • ${plan.before.pokemon.nickname.ifBlank { ScarletVioletSpeciesNames.name(plan.before.pokemon.species) }}\n" +
                    "Origin Game: ${plan.before.originGame} → ${plan.after.originGame}\n\n" +
                    "Allowed encrypted scope: one 344-byte PK9 slot at 0x${plan.absoluteOffset.toString(16).uppercase()} and the native SHA-256 footer. Inside the PK9, only Origin Game byte 0xCE and its checksum may change. Species, form, moves, stats, Tera data, size, Obedience Level, every other party and PC-box slot, the dedicated ride Pokémon and unrelated save data remain exact. This may break encounter or distribution legality because PSE changes provenance metadata without rewriting any dependent field. PSE verifies a recovery backup and reopens the persisted copy. Choose a NEW empty document."
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
                inspectCopy.visibility = if (verifiedCopy != null) View.VISIBLE else View.GONE
                status.text = result.fold(
                    onSuccess = {
                        "Origin Game copy saved and reopened successfully.\n" +
                            "Origin Game: ${plan.after.originGame}\n" +
                            "SHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\nOriginal source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The Origin Game copy could not be verified.") +
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

    private fun readDocument(uri: Uri): ByteArray = ScarletVioletPokemonOriginGameEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
