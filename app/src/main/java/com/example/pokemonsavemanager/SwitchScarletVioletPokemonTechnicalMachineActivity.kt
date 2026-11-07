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

private data class OpenedScarletVioletTechnicalMachine(
    val bytes: ByteArray,
    val displayName: String,
    val pokemon: ScarletVioletPokemonRead,
    val choices: List<ScarletVioletTechnicalMachineChoice>
)

/** Scarlet/Violet title-specific editor for one native PK9 Technical Machine history bit. */
class SwitchScarletVioletPokemonTechnicalMachineActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2203
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: ScarletVioletPokemonArea? = null
    private var box = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var current: ScarletVioletPokemonRead? = null
    private var choices = emptyList<ScarletVioletTechnicalMachineChoice>()
    private var pendingPlan: ScarletVioletTechnicalMachineCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var moveSelector: Spinner
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
        root.addView(TextView(this).apply { text = "Record Technical Machine use"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "$location\nRecord one currently known TM move in the native PK9 move-history flags."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Current TM move without a saved record"; textSize = 16f })
        moveSelector = Spinner(this).apply { isEnabled = false }
        root.addView(moveSelector)
        root.addView(TextView(this).apply {
            text = "This one-way action sets the native history flag used to remember a move previously learned from a Technical Machine. It does not change the current move, PP, relearn moves or any other field. PSE limits choices to moves the Pokémon currently knows and that appear in Scarlet/Violet's pinned 230-entry TM order. Species/form TM compatibility and the move's actual origin are not certified; an incompatible record can be illegal."
            textSize = 14f
        })
        review = Button(this).apply {
            text = "Review Technical Machine record"; isEnabled = false
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
                require(ScarletVioletPokemonTechnicalMachineEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                val validation = ScarletVioletSaveValidation.inspect(bytes)
                require(validation is ScarletVioletValidation.Verified) {
                    "A validated native Scarlet/Violet save is required."
                }
                val storage = ScarletVioletStorageReadOnly.read(bytes)
                val read = if (selectedArea == ScarletVioletPokemonArea.BOX)
                    storage.box(box - 1)[position - 1].read else storage.party()[position - 1].read
                val pokemon = (read as? ScarletVioletSlotRead.Pokemon)?.value
                    ?: error("The selected native PK9 is not checksum-valid.")
                val available = ScarletVioletPokemonTechnicalMachineEditing.choices(pokemon)
                require(available.isNotEmpty()) {
                    "No current move has an unrecorded Scarlet/Violet Technical Machine history bit."
                }
                OpenedScarletVioletTechnicalMachine(bytes, validation.version.displayName, pokemon, available)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { openedResult ->
                    opened = openedResult.bytes
                    current = openedResult.pokemon
                    choices = openedResult.choices
                    moveSelector.adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_item,
                        choices
                    ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                    status.text = "${openedResult.displayName} • Native integrity verified\n" +
                        "$location • ${openedResult.pokemon.nickname.ifBlank { ScarletVioletSpeciesNames.name(openedResult.pokemon.species) }}\n" +
                        "Recorded current TM moves: ${ScarletVioletPokemonTechnicalMachineEditing.recordedCurrentMoves(openedResult.pokemon)}\n" +
                        "Source SHA-256: ${ScarletVioletPokemonTechnicalMachineEditing.sha256(openedResult.bytes)}"
                }.onFailure { status.text = it.message ?: "The selected Pokémon could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null && current != null && choices.isNotEmpty()
        moveSelector.isEnabled = !value && ready
        review.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun reviewChange() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        val requested = choices.getOrNull(moveSelector.selectedItemPosition)
        if (requested == null) {
            status.text = "Choose one current Technical Machine move."
            return
        }
        setBusy(true)
        worker.execute {
            val result = runCatching {
                ScarletVioletPokemonTechnicalMachineEditing.stage(
                    bytes, hash, selectedArea, box, position, requested.machineIndex
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess(::showReview).onFailure {
                    status.text = it.message ?: "The Technical Machine record could not be staged."
                    setBusy(false)
                }
            }
        }
    }

    private fun showReview(plan: ScarletVioletTechnicalMachineCopyPlan) {
        var dispatched = false
        AlertDialog.Builder(this).setTitle("Save this Technical Machine record to a new copy?")
            .setMessage(
                "${plan.location} • ${plan.before.nickname.ifBlank { ScarletVioletSpeciesNames.name(plan.before.species) }}\n" +
                    "Record: ${plan.choice}\n" +
                    "Current move and PP: unchanged\n\n" +
                    "Allowed encrypted scope: one 344-byte PK9 slot at 0x${plan.absoluteOffset.toString(16).uppercase()} and the native SHA-256 footer. Inside the PK9, only one documented Technical Machine history bit and the PK9 checksum may change. Current moves, PP, relearn moves, species, form, stats, Hyper Training, Tera data, size, encounter fields, trainer identity, every other party and PC-box slot, the dedicated ride Pokémon and unrelated save data remain exact. PSE does not certify species compatibility or move origin. It verifies a recovery backup and reopens the persisted copy. Choose a NEW empty document."
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
                        "Technical Machine record saved and reopened successfully.\n" +
                            "Recorded: ${plan.choice.move.name}\n" +
                            "SHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\nOriginal source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The Technical Machine copy could not be verified.") +
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

    private fun readDocument(uri: Uri): ByteArray = ScarletVioletPokemonTechnicalMachineEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
