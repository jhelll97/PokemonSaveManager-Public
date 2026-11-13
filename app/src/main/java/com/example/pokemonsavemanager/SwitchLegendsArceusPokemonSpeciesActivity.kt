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

/** Gen 1-shaped Pokémon editor for one native Legends: Arceus PA8 species field. */
class SwitchLegendsArceusPokemonSpeciesActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2133
    private var speciesIds = LegendsArceusPokemonSpeciesEditing.supportedSpecies
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: LegendsArceusPokemonArea? = null
    private var pasture = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var current: LegendsArceusPokemonRead? = null
    private var pendingPlan: LegendsArceusSpeciesCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private var speciesEditable = false
    private var levelEditable = false
    private lateinit var status: TextView
    private lateinit var species: Spinner
    private lateinit var review: Button
    private lateinit var level: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        area = intent?.getStringExtra("pse.switch.pokemon.area")?.let { value ->
            LegendsArceusPokemonArea.values().firstOrNull { it.name == value }
        }
        pasture = intent?.getIntExtra("pse.switch.pokemon.pasture", 0) ?: 0
        position = intent?.getIntExtra("pse.switch.pokemon.position", 0) ?: 0
        val selectedArea = area
        if (sourceUri == null || expectedHash.isNullOrBlank() || selectedArea == null ||
            position !in 1..30 || (selectedArea == LegendsArceusPokemonArea.PASTURE && pasture !in 1..32) ||
            (selectedArea == LegendsArceusPokemonArea.PARTY && position !in 1..6)) {
            finish(); return
        }
        val location = if (selectedArea == LegendsArceusPokemonArea.PASTURE) "Pasture $pasture • Slot $position" else "Party $position"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Legends: Arceus • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Edit Pokémon • Species"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "$location\nChange the next Gen 1 Pokémon editor field in one native PA8. Review the before/after species, then save a verified new copy."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Species"; textSize = 16f })
        species = Spinner(this).apply { isEnabled = false }
        updateSpeciesChoices()
        root.addView(species)
        root.addView(TextView(this).apply {
            text = "Choices come from Legends: Arceus's native Hisui Pokédex table. Form, Alpha state, nickname, moves, stats, ball, trainer identity and every other field remain exact. PSE does not claim the resulting combination is game-legal."
            textSize = 14f
        })
        review = Button(this).apply {
            text = "Review species change"; isEnabled = false; setOnClickListener { reviewSpecies() }
        }
        root.addView(review)
        level = Button(this).apply {
            text = "Next field: Level"
            isEnabled = false
            setOnClickListener { openLevel() }
        }
        root.addView(level)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"; visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply { text = "Back to Nickname"; setOnClickListener { if (!busy) finish() } })
        OmegaRubyUi.stylePokemonEditor(this, root, status)
        setContentView(OmegaRubyUi.scrollingPage(this, root))

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LegendsArceusPokemonSpeciesEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                val validation = LegendsArceusSaveValidation.inspect(bytes)
                require(validation is LegendsArceusValidation.Verified) {
                    "A validated native Legends: Arceus save is required."
                }
                val storage = LegendsArceusStorageReadOnly.read(bytes)
                val read = if (selectedArea == LegendsArceusPokemonArea.PASTURE)
                    storage.pasture(pasture - 1)[position - 1].read else storage.party()[position - 1].read
                val pokemon = (read as? LegendsArceusSlotRead.Pokemon)?.value
                    ?: error("The selected native PA8 is not checksum-valid.")
                Triple(bytes, validation.version.displayName, pokemon)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, displayName, pokemon) ->
                    opened = bytes; current = pokemon
                    speciesEditable = !pokemon.isEgg && pokemon.form == 0 && !pokemon.isNoble
                    levelEditable = !pokemon.isEgg
                    if (pokemon.species !in speciesIds) {
                        speciesIds = listOf(pokemon.species) + speciesIds
                        updateSpeciesChoices()
                    }
                    species.setSelection(speciesIds.indexOf(pokemon.species).coerceAtLeast(0))
                    status.text = "$displayName • Native integrity verified\n" +
                        "$location • ${pokemon.nickname.ifBlank { LegendsArceusSpeciesNames.name(pokemon.species) }}\n" +
                        "Current species: #${pokemon.species} ${LegendsArceusSpeciesNames.name(pokemon.species)} • Form ${pokemon.form}\n" +
                        when {
                            pokemon.isEgg -> "Egg species and Level editing remain protected.\n"
                            !speciesEditable -> "Species remains protected for this form or Noble state; continue to Level.\n"
                            else -> "Species editing available; continue to Level when ready.\n"
                        } +
                        "Source SHA-256: ${LegendsArceusPokemonSpeciesEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The selected Pokémon could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun updateSpeciesChoices() {
        species.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            speciesIds.map { "#$it ${LegendsArceusSpeciesNames.name(it)}" })
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null && current != null
        species.isEnabled = !value && ready && speciesEditable
        review.isEnabled = !value && ready && speciesEditable
        level.isEnabled = !value && ready && levelEditable
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun openLevel() {
        if (busy || !levelEditable) return
        val uri = sourceUri ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        startActivity(Intent(this, SwitchLegendsArceusPokemonLevelActivity::class.java).setData(uri)
            .putExtra("pse.switch.sha256", hash)
            .putExtra("pse.switch.pokemon.area", selectedArea.name)
            .putExtra("pse.switch.pokemon.pasture", pasture)
            .putExtra("pse.switch.pokemon.position", position)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return LegendsArceusPokemonSpeciesEditing.readDocumentBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read."))
    }

    private fun readDocument(uri: Uri): ByteArray = LegendsArceusPokemonSpeciesEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read."))

    private fun reviewSpecies() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        val target = speciesIds.getOrNull(species.selectedItemPosition) ?: return
        setBusy(true)
        worker.execute {
            val result = runCatching {
                LegendsArceusPokemonSpeciesEditing.stage(bytes, hash, selectedArea, pasture, position, target)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { showReview(it) }
                    .onFailure { status.text = it.message ?: "The species could not be staged."; setBusy(false) }
            }
        }
    }

    private fun showReview(plan: LegendsArceusSpeciesCopyPlan) {
        var dispatched = false
        AlertDialog.Builder(this).setTitle("Save this species to a new copy?")
            .setMessage(
                "${plan.location} • ${plan.before.nickname.ifBlank { "No nickname" }}\n" +
                    "Species: #${plan.before.species} ${LegendsArceusSpeciesNames.name(plan.before.species)} → " +
                    "#${plan.species} ${LegendsArceusSpeciesNames.name(plan.species)}\n\n" +
                    "Allowed encrypted scope: one ${plan.recordSize}-byte PA8 slot at 0x${plan.absoluteOffset.toString(16).uppercase()} and the native SHA-256 footer. " +
                    "Inside the PA8, only the two-byte species field and checksum may change. Form ${plan.before.form}, Alpha state, nickname, moves, stats, ball, trainer identity, every other party and pasture slot, and all unrelated bytes remain exact. " +
                    "This exact-field edit does not claim the unchanged fields form a game-legal combination. PSE verifies a recovery backup and reopens the persisted copy. Choose a NEW empty document."
            )
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { if (!dispatched) setBusy(false) }
            .setPositiveButton("Choose new copy") { _, _ ->
                if (!dispatched && !isFinishing && !isDestroyed) {
                    dispatched = true; pendingPlan = plan; verifiedCopy = null
                    inspectCopy.visibility = android.view.View.GONE
                    try {
                        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            type = "application/octet-stream"; addCategory(Intent.CATEGORY_OPENABLE)
                            putExtra(Intent.EXTRA_TITLE, "main")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }, createCopyRequest)
                    } catch (failure: Exception) {
                        pendingPlan = null; setBusy(false)
                        status.text = failure.message ?: "The save picker could not be opened. Nothing was written."
                    }
                }
            }.show()
    }

    private fun inspectVerifiedCopy() {
        if (busy) return
        val (uri, hash) = verifiedCopy ?: return
        startActivity(Intent(this, SwitchReadOnlyActivity::class.java).setData(uri)
            .putExtra("pse.switch.family", SwitchGameFamily.LEGENDS_ARCEUS.name)
            .putExtra("pse.switch.sha256", hash).putExtra("pse.switch.section", "storage")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
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
                        val backup = SwitchSafeBackupStore.createVerified(this, SwitchGameFamily.LEGENDS_ARCEUS, original)
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(this, SwitchGameFamily.LEGENDS_ARCEUS, backup.file, backup.sha256)
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
                    onSuccess = { "Species copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\nOriginal source unchanged." },
                    onFailure = { (it.message ?: "The species copy could not be verified.") + "\nRecovery backup: ${backupName ?: "not created"}" }
                )
                setBusy(false)
            }
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
