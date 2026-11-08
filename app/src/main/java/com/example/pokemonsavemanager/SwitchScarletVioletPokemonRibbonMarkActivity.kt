package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.concurrent.Executors

/** Scarlet/Violet editor for one native PK9 ribbon or mark ownership bit. */
class SwitchScarletVioletPokemonRibbonMarkActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2234
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: ScarletVioletPokemonArea? = null
    private var box = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var current: ScarletVioletPokemonRead? = null
    private var pendingPlan: ScarletVioletRibbonMarkCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var ribbonOptions = intArrayOf()
    private var busy = false
    private lateinit var status: TextView
    private lateinit var ribbonChoice: Spinner
    private lateinit var ownershipChoice: Spinner
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
        root.addView(TextView(this).apply { text = "Edit Ribbons and Marks"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "$location\nChoose one native ribbon or mark and review changing its ownership bit."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Ribbon or Mark"; textSize = 16f })
        ribbonChoice = Spinner(this).apply { isEnabled = false }
        root.addView(ribbonChoice)
        root.addView(TextView(this).apply { text = "Ownership"; textSize = 16f })
        ownershipChoice = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SwitchScarletVioletPokemonRibbonMarkActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Not owned", "Owned")
            )
            isEnabled = false
        }
        root.addView(ownershipChoice)
        root.addView(TextView(this).apply {
            text = "PSE changes one documented PK9 ribbon or mark bit. Removing the current Active Title is blocked until Active Title is cleared. Eggs, fateful-event records and the dedicated ride record remain protected."
            textSize = 14f
        })
        review = Button(this).apply {
            text = "Review ribbon or mark change"; isEnabled = false
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
                require(ScarletVioletPokemonRibbonMarkEditing.sha256(bytes) == expectedHash) {
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
                require(!pokemon.isEgg) { "Egg ribbon and mark ownership remains protected." }
                require(!pokemon.fatefulEncounter) {
                    "Fateful-event ribbon and mark ownership remains protected because distribution identity is not inferred."
                }
                require(ScarletVioletPokemonAffixedTitleEditing.isValid(pokemon)) {
                    "Clear or repair the unsupported Active Title before editing ribbon or mark ownership."
                }
                Triple(bytes, validation.version.displayName, pokemon)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, displayName, pokemon) ->
                    opened = bytes
                    current = pokemon
                    ribbonOptions = ScarletVioletPokemonRibbonMarkEditing.options()
                    ribbonChoice.adapter = ArrayAdapter(
                        this, android.R.layout.simple_spinner_dropdown_item,
                        ribbonOptions.map(ScarletVioletPokemonRibbonMarkEditing::name)
                    )
                    ribbonChoice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, selected: Int, id: Long) {
                            val index = ribbonOptions.getOrNull(selected) ?: return
                            ownershipChoice.setSelection(if (pokemon.ribbonFlags[index]) 1 else 0)
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    }
                    ribbonChoice.setSelection(0)
                    ownershipChoice.setSelection(if (pokemon.ribbonFlags[0]) 1 else 0)
                    status.text = "$displayName • Native integrity verified\n" +
                        "$location • ${pokemon.nickname.ifBlank { ScarletVioletSpeciesNames.name(pokemon.species) }}\n" +
                        "Owned ribbons and marks: ${pokemon.ribbonFlags.count { it }} / ${ribbonOptions.size}\n" +
                        "Active Title: ${ScarletVioletPokemonAffixedTitleEditing.name(pokemon.affixedRibbon)}\n" +
                        "Source SHA-256: ${ScarletVioletPokemonRibbonMarkEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The selected Pokémon could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null && current != null
        ribbonChoice.isEnabled = !value && ready
        ownershipChoice.isEnabled = !value && ready
        review.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun reviewChange() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        val requested = ribbonOptions.getOrNull(ribbonChoice.selectedItemPosition) ?: return
        val requestedOwned = ownershipChoice.selectedItemPosition == 1
        setBusy(true)
        worker.execute {
            val result = runCatching {
                ScarletVioletPokemonRibbonMarkEditing.stage(
                    bytes, hash, selectedArea, box, position, requested, requestedOwned
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess(::showReview).onFailure {
                    status.text = it.message ?: "The ribbon or mark change could not be staged."
                    setBusy(false)
                }
            }
        }
    }

    private fun showReview(plan: ScarletVioletRibbonMarkCopyPlan) {
        var dispatched = false
        AlertDialog.Builder(this).setTitle("Save this ribbon or mark change to a new copy?")
            .setMessage(
                "${plan.location} • ${plan.before.nickname.ifBlank { ScarletVioletSpeciesNames.name(plan.before.species) }}\n" +
                    "${ScarletVioletPokemonRibbonMarkEditing.name(plan.ribbonIndex)}: " +
                    "${if (plan.beforeOwned) "Owned" else "Not owned"} → ${if (plan.owned) "Owned" else "Not owned"}\n" +
                    "Native bit: byte 0x${plan.byteOffset.toString(16).uppercase()}, mask 0x${plan.bitMask.toString(16).uppercase().padStart(2, '0')}\n\n" +
                    "Allowed encrypted scope: one 344-byte PK9 slot at 0x${plan.absoluteOffset.toString(16).uppercase()} and the native SHA-256 footer. " +
                    "Inside the PK9, only the selected documented ribbon or mark bit and checksum 0x06–0x07 may change. Active Title remains exact; removing its currently selected ribbon or mark is blocked. " +
                    "Ribbon and mark legality depends on origin game, encounter, species, form and achievement history; this direct ownership edit does not certify legality. " +
                    "Every other ribbon and mark bit, trainer identity, nickname, species, form, moves, stats, encounter data, party and PC-box slot and unrelated save object remains exact. " +
                    "PSE verifies a recovery backup and reopens the persisted copy. Choose a NEW empty document."
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
                        "Ribbon or mark copy saved and reopened successfully.\n" +
                            "${ScarletVioletPokemonRibbonMarkEditing.name(plan.ribbonIndex)}: ${if (plan.owned) "Owned" else "Not owned"}\n" +
                            "SHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\nOriginal source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The ribbon or mark copy could not be verified.") +
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

    private fun readDocument(uri: Uri): ByteArray = ScarletVioletPokemonRibbonMarkEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
