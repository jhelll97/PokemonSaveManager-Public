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
import android.widget.Spinner
import android.widget.TextView
import java.util.concurrent.Executors

/** Gen 1-shaped Pokémon editor for one native Scarlet/Violet PK9 Gender. */
class SwitchScarletVioletPokemonGenderActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2226
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: ScarletVioletPokemonArea? = null
    private var box = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var pendingPlan: ScarletVioletGenderCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var gender: Spinner
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
            position !in 1..30 ||
            (selectedArea == ScarletVioletPokemonArea.BOX && box !in 1..32) ||
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
        root.addView(TextView(this).apply {
            text = "Scarlet/Violet • Pokémon Boxes"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "Edit Pokémon • Gender"
            textSize = 20f
        })
        root.addView(TextView(this).apply {
            text = "$location\nChange the native Gender field in one PK9. " +
                "Review the before/after value, then save a verified new copy."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Gender"; textSize = 16f })
        gender = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SwitchScarletVioletPokemonGenderActivity,
                android.R.layout.simple_spinner_item,
                listOf("Male", "Female", "Genderless")
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            isEnabled = false
        }
        root.addView(gender)
        review = Button(this).apply {
            text = "Review Gender change"
            isEnabled = false
            setOnClickListener { reviewGender() }
        }
        root.addView(review)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"
            visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply {
            text = "Back to Pokémon Details"
            setOnClickListener { if (!busy) finish() }
        })
        OmegaRubyUi.stylePokemonEditor(this, root, status)
        setContentView(OmegaRubyUi.scrollingPage(this, root))

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(ScarletVioletPokemonGenderEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                val validation = ScarletVioletSaveValidation.inspect(bytes)
                require(validation is ScarletVioletValidation.Verified) {
                    "A validated native Scarlet/Violet save is required."
                }
                val storage = ScarletVioletStorageReadOnly.read(bytes)
                val read = if (selectedArea == ScarletVioletPokemonArea.BOX)
                    storage.box(box - 1)[position - 1].read
                else storage.party()[position - 1].read
                val pokemon = (read as? ScarletVioletSlotRead.Pokemon)?.value
                    ?: error("The selected native PK9 is not checksum-valid.")
                Triple(bytes, validation.version.displayName, pokemon)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, displayName, pokemon) ->
                    opened = bytes
                    require(pokemon.gender in 0..2) { "The selected PK9 uses an unsupported native Gender value." }
                    gender.setSelection(pokemon.gender)
                    status.text = "$displayName • Native integrity verified\n" +
                        "$location • ${ScarletVioletSpeciesNames.name(pokemon.species)}\n" +
                        "Current Gender: ${genderName(pokemon.gender)}\n" +
                        "Source SHA-256: ${ScarletVioletPokemonGenderEditing.sha256(bytes)}"
                }.onFailure {
                    status.text = it.message ?: "The selected Pokémon could not be opened safely."
                }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        gender.isEnabled = !value && opened != null
        review.isEnabled = !value && opened != null
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return ScarletVioletPokemonGenderEditing.readDocumentBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray =
        ScarletVioletPokemonGenderEditing.readDocumentBytes(
            contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
        )

    private fun reviewGender() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        val requested = gender.selectedItemPosition
        if (requested !in 0..2) {
            status.text = "Choose Male, Female or Genderless."
            return
        }
        setBusy(true)
        worker.execute {
            val result = runCatching {
                ScarletVioletPokemonGenderEditing.stage(
                    bytes, hash, selectedArea, box, position, requested
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { showReview(it) }
                    .onFailure {
                        status.text = it.message ?: "The Gender could not be staged."
                        setBusy(false)
                    }
            }
        }
    }

    private fun showReview(plan: ScarletVioletGenderCopyPlan) {
        var dispatched = false
        AlertDialog.Builder(this).setTitle("Save this Gender to a new copy?")
            .setMessage(
                "${plan.location} • ${ScarletVioletSpeciesNames.name(plan.before.species)}\n" +
                    "Gender: ${genderName(plan.before.gender)} → ${genderName(plan.gender)}\n\n" +
                    "Allowed encrypted scope: one ${plan.recordSize}-byte PK9 slot at " +
                    "0x${plan.absoluteOffset.toString(16).uppercase()} and the native SHA-256 footer. " +
                    "Inside the decoded PK9, only bits 1–2 of native byte 0x22 may change; every other bit and the separate form byte remain exact. " +
                    "The Pokémon checksum remains valid and every other decoded Pokémon field stays exact. " +
                    "PSE does not infer every species/form gender ratio or fixed encounter restriction; choose only a destination-valid Gender. This action is not a legality certification. " +
                    "Original Trainer name, Trainer ID, Secret ID, PID, species, form, nickname, moves, stats, Tera types, scale, " +
                    "friendship, every other party and PC-box slot, the dedicated ride Pokémon, " +
                    "and all unrelated bytes remain exact. PSE verifies a recovery backup and " +
                    "reopens the persisted copy. Choose a NEW empty document."
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
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }, createCopyRequest)
                    } catch (failure: Exception) {
                        pendingPlan = null
                        setBusy(false)
                        status.text = failure.message ?:
                            "The save picker could not be opened. Nothing was written."
                    }
                }
            }.show()
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
                inspectCopy.visibility = if (verifiedCopy != null)
                    android.view.View.VISIBLE else android.view.View.GONE
                status.text = result.fold(
                    onSuccess = {
                        "Gender copy saved and reopened successfully.\n" +
                            "SHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\n" +
                            "Original source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The Gender copy could not be verified.") +
                            "\nRecovery backup: ${backupName ?: "not created"}"
                    }
                )
                setBusy(false)
            }
        }
    }

    private fun genderName(value: Int): String = when (value) {
        0 -> "Male"
        1 -> "Female"
        2 -> "Genderless"
        else -> "Unknown ($value)"
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() {
        if (!busy) super.onBackPressed()
    }

    override fun onDestroy() {
        pendingPlan = null
        worker.shutdown()
        super.onDestroy()
    }
}
