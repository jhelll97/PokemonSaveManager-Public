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

/** Gen 1-shaped evolution review for one native BDSP PB8. */
class SwitchBdspPokemonEvolutionActivity : Activity() {
    private data class OpenedPokemon(
        val bytes: ByteArray,
        val displayName: String,
        val pokemon: BdspPokemonRead,
        val choices: List<BdspEvolutionChoice>
    )

    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2177
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: BdspPokemonArea? = null
    private var box = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var current: BdspPokemonRead? = null
    private var choices = emptyList<BdspEvolutionChoice>()
    private var pendingPlan: BdspEvolutionCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var target: Spinner
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        area = intent?.getStringExtra("pse.switch.pokemon.area")?.let { value ->
            BdspPokemonArea.values().firstOrNull { it.name == value }
        }
        box = intent?.getIntExtra("pse.switch.pokemon.box", 0) ?: 0
        position = intent?.getIntExtra("pse.switch.pokemon.position", 0) ?: 0
        val selectedArea = area
        if (sourceUri == null || expectedHash.isNullOrBlank() || selectedArea == null ||
            position !in 1..30 || (selectedArea == BdspPokemonArea.BOX && box !in 1..40) ||
            (selectedArea == BdspPokemonArea.PARTY && position !in 1..6)) {
            finish(); return
        }
        val location = if (selectedArea == BdspPokemonArea.BOX)
            "Box $box • Slot $position" else "Party $position"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "BDSP • Pokémon Details"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Evolve Pokémon"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "$location\nChoose the shared Gen 1 evolution, review the rebuilt native battle values, then save a verified new copy."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Evolution"; textSize = 16f })
        target = Spinner(this).apply { isEnabled = false }
        root.addView(target)
        root.addView(TextView(this).apply {
            text = "PSE changes species and rebuilds only species-dependent current/max HP and five cached battle stats. Form, ability, moves, trainer identity, contest values, IVs, EVs, party placement and every other Pokémon remain exact. The unchanged ability and moves are not a legality certification."
            textSize = 14f
        })
        review = Button(this).apply {
            text = "Review evolution"; isEnabled = false; setOnClickListener { reviewEvolution() }
        }
        root.addView(review)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"; visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply {
            text = "Back to Pokémon Details"; setOnClickListener { if (!busy) finish() }
        })
        OmegaRubyUi.stylePokemonEditor(this, root, status)
        setContentView(OmegaRubyUi.scrollingPage(this, root))

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(BdspPokemonEvolutionEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                val validation = BdspSaveValidation.inspect(bytes)
                require(validation is BdspValidation.Verified) {
                    "A validated native BDSP save is required."
                }
                val storage = BdspStorageReadOnly.read(bytes)
                val read = if (selectedArea == BdspPokemonArea.BOX)
                    storage.box(box - 1)[position - 1].read else storage.party()[position - 1].read
                val pokemon = (read as? BdspSlotRead.Pokemon)?.value
                    ?: error("The selected native PB8 is not checksum-valid.")
                require(!pokemon.isEgg && pokemon.form == 0) {
                    "This Egg or alternate-form PB8 cannot use base-form evolution."
                }
                val available = BdspPokemonEvolutionEditing.choices(pokemon.species)
                require(available.isNotEmpty()) {
                    "This Pokémon has no supported shared Gen 1 evolution in BDSP."
                }
                OpenedPokemon(bytes, validation.version.displayName, pokemon, available)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { value ->
                    opened = value.bytes
                    current = value.pokemon
                    choices = value.choices
                    target.adapter = ArrayAdapter(
                        this@SwitchBdspPokemonEvolutionActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        choices.map { "#${it.species} ${SwordShieldSpeciesNames.name(it.species)} • ${it.method}" }
                    )
                    status.text = "${value.displayName} • Native integrity verified\n" +
                        "$location • ${value.pokemon.nickname.ifBlank { SwordShieldSpeciesNames.name(value.pokemon.species) }}\n" +
                        "Current species: #${value.pokemon.species} ${SwordShieldSpeciesNames.name(value.pokemon.species)}\n" +
                        "Level ${value.pokemon.cachedLevel} • HP ${value.pokemon.currentHp}/${value.pokemon.maximumHp}\n" +
                        "Source SHA-256: ${BdspPokemonEvolutionEditing.sha256(value.bytes)}"
                }.onFailure {
                    status.text = it.message ?: "The selected Pokémon could not be opened safely."
                }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null && current != null && choices.isNotEmpty()
        target.isEnabled = !value && ready
        review.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return BdspPokemonEvolutionEditing.readDocumentBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read."))
    }

    private fun readDocument(uri: Uri): ByteArray = BdspPokemonEvolutionEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read."))

    private fun reviewEvolution() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        val choice = choices.getOrNull(target.selectedItemPosition) ?: return
        setBusy(true)
        worker.execute {
            val result = runCatching {
                BdspPokemonEvolutionEditing.stage(
                    bytes, hash, selectedArea, box, position, choice.species
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { showReview(it) }.onFailure {
                    status.text = it.message ?: "The evolution could not be staged."
                    setBusy(false)
                }
            }
        }
    }

    private fun showReview(plan: BdspEvolutionCopyPlan) {
        var dispatched = false
        val after = plan.after
        AlertDialog.Builder(this).setTitle("Save this evolution to a new copy?")
            .setMessage(
                "${plan.location} • ${plan.before.nickname.ifBlank { "No nickname" }}\n" +
                    "Species: #${plan.before.species} ${SwordShieldSpeciesNames.name(plan.before.species)} → " +
                    "#${after.choice.species} ${SwordShieldSpeciesNames.name(after.choice.species)} (${after.choice.method})\n" +
                    "HP: ${plan.before.currentHp}/${plan.before.maximumHp} → ${after.currentHp}/${after.maximumHp}\n" +
                    "Attack: ${plan.before.cachedStats[0]} → ${after.attack}\n" +
                    "Defense: ${plan.before.cachedStats[1]} → ${after.defense}\n" +
                    "Speed: ${plan.before.cachedStats[2]} → ${after.speed}\n" +
                    "Sp. Atk: ${plan.before.cachedStats[3]} → ${after.specialAttack}\n" +
                    "Sp. Def: ${plan.before.cachedStats[4]} → ${after.specialDefense}\n\n" +
                    "Allowed native scope: one 344-byte PB8 at 0x${plan.absoluteOffset.toString(16).uppercase()} and the whole-file MD5 field. " +
                    "Inside the PB8, only species, current/max HP, five cached battle stats, and checksum may change. " +
                    "Ability, moves and all other fields remain exact and are not a legality certification. PSE verifies a recovery backup and reopens the persisted copy. Choose a NEW empty document."
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

    private fun inspectVerifiedCopy() {
        if (busy) return
        val (uri, hash) = verifiedCopy ?: return
        startActivity(Intent(this, SwitchReadOnlyActivity::class.java).setData(uri)
            .putExtra("pse.switch.family", SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL.name)
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
                        val backup = SwitchSafeBackupStore.createVerified(
                            this, SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, original)
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, backup.file, backup.sha256)
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
                        "Evolution copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\n" +
                            "Recovery backup: $backupName\nOriginal source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The evolution copy could not be verified.") +
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
