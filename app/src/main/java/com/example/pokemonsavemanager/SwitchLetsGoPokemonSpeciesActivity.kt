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

/** Gen 1-shaped Pokémon editor for one native Let's Go PB7 species field. */
class SwitchLetsGoPokemonSpeciesActivity : Activity() {
    private data class OpenedPokemon(
        val bytes: ByteArray,
        val displayName: String,
        val pokemon: LetsGoPokemonRead,
        val speciesEditable: Boolean,
        val levelEditable: Boolean
    )

    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2131
    private val speciesIds = LetsGoPokemonSpeciesEditing.supportedSpecies
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var storageIndex = -1
    private var opened: ByteArray? = null
    private var current: LetsGoPokemonRead? = null
    private var speciesEditable = false
    private var levelEditable = false
    private var pendingPlan: LetsGoSpeciesCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var species: Spinner
    private lateinit var review: Button
    private lateinit var level: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        storageIndex = intent?.getIntExtra("pse.switch.pokemon.index", -1) ?: -1
        if (sourceUri == null || expectedHash.isNullOrBlank() || storageIndex !in 0 until 1000) {
            finish(); return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Let's Go • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Edit Pokémon • Species"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "Change the next Gen 1 Pokémon editor field in one native PB7. Review the before/after species, then save a verified new copy."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Species"; textSize = 16f })
        species = Spinner(this).apply {
            adapter = ArrayAdapter(this@SwitchLetsGoPokemonSpeciesActivity,
                android.R.layout.simple_spinner_dropdown_item,
                speciesIds.map { "#$it ${LetsGoSpeciesNames.name(it)}" })
            isEnabled = false
        }
        root.addView(species)
        root.addView(TextView(this).apply {
            text = "Only native base-form species are offered. Form, nickname, moves, stats, ability data, held item, trainer identity and all other fields remain exact. PSE does not claim the resulting combination is game-legal."
            textSize = 14f
        })
        review = Button(this).apply {
            text = "Review species change"; isEnabled = false; setOnClickListener { reviewSpecies() }
        }
        root.addView(review)
        level = Button(this).apply {
            text = "Next field: Level"
            isEnabled = false
            setOnClickListener {
                val uri = sourceUri ?: return@setOnClickListener
                val hash = expectedHash ?: return@setOnClickListener
                startActivity(Intent(this@SwitchLetsGoPokemonSpeciesActivity,
                    SwitchLetsGoPokemonLevelActivity::class.java)
                    .setData(uri)
                    .putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.index", storageIndex)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        root.addView(level)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"; visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply { text = "Back to Nickname"; setOnClickListener { if (!busy) finish() } })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LetsGoPokemonSpeciesEditing.sha256(bytes) == expectedHash) { "The source changed. Reopen it from PSE." }
                val validation = LetsGoSaveValidation.inspect(bytes)
                require(validation is LetsGoValidation.Verified) { "A validated native Let's Go save is required." }
                val slot = LetsGoStorageReadOnly.read(bytes).slot(storageIndex)
                val pokemon = (slot.read as? LetsGoSlotRead.Pokemon)?.value
                    ?: error("The selected native PB7 is not checksum-valid.")
                OpenedPokemon(
                    bytes,
                    validation.version.displayName,
                    pokemon,
                    !slot.partner && !pokemon.isPartnerForm && !pokemon.isEgg && pokemon.form == 0,
                    !pokemon.isEgg && LetsGoPersonalCatalogue.resolve(pokemon.species, pokemon.form) != null
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { openedPokemon ->
                    val bytes = openedPokemon.bytes
                    val pokemon = openedPokemon.pokemon
                    opened = bytes
                    current = pokemon
                    speciesEditable = openedPokemon.speciesEditable
                    levelEditable = openedPokemon.levelEditable
                    species.setSelection(speciesIds.indexOf(pokemon.species).coerceAtLeast(0))
                    status.text = "${openedPokemon.displayName} • Native integrity verified\n" +
                        "Storage #${storageIndex + 1} • ${pokemon.nickname.ifBlank { LetsGoSpeciesNames.name(pokemon.species) }}\n" +
                        "Current species: #${pokemon.species} ${LetsGoSpeciesNames.name(pokemon.species)} • Form ${pokemon.form}\n" +
                        (if (speciesEditable) "Species editing available.\n"
                        else "Species remains protected for this Egg, form, or partner identity; continue to Level when available.\n") +
                        "Source SHA-256: ${LetsGoPokemonSpeciesEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The selected Pokémon could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null && current != null
        species.isEnabled = !value && ready && speciesEditable
        review.isEnabled = !value && ready && speciesEditable
        level.isEnabled = !value && ready && levelEditable
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return LetsGoPokemonSpeciesEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read."))
    }

    private fun readDocument(uri: Uri): ByteArray = LetsGoPokemonSpeciesEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read."))

    private fun reviewSpecies() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val target = speciesIds.getOrNull(species.selectedItemPosition) ?: return
        setBusy(true)
        worker.execute {
            val result = runCatching { LetsGoPokemonSpeciesEditing.stage(bytes, hash, storageIndex, target) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { showReview(it) }
                    .onFailure { status.text = it.message ?: "The species could not be staged."; setBusy(false) }
            }
        }
    }

    private fun showReview(plan: LetsGoSpeciesCopyPlan) {
        var dispatched = false
        AlertDialog.Builder(this).setTitle("Save this species to a new copy?")
            .setMessage(
                "Storage #${plan.storageIndex + 1} • ${plan.before.nickname.ifBlank { "No nickname" }}\n" +
                    "Species: #${plan.before.species} ${LetsGoSpeciesNames.name(plan.before.species)} → " +
                    "#${plan.species} ${LetsGoSpeciesNames.name(plan.species)}\n\n" +
                    "Allowed native scope: one 260-byte PB7 record at 0x${plan.recordOffset.toString(16).uppercase()} and block 9's two-byte CRC. " +
                    "Inside the PB7, only the two-byte species field and checksum may change. Form ${plan.before.form}, nickname, moves, stats, trainer identity, party and partner pointers, every other Pokémon, and all unrelated bytes remain exact. " +
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

    private fun inspectVerifiedCopy() {
        if (busy) return
        val (uri, hash) = verifiedCopy ?: return
        startActivity(Intent(this, SwitchReadOnlyActivity::class.java).setData(uri)
            .putExtra("pse.switch.family", SwitchGameFamily.LETS_GO_PIKACHU_EEVEE.name)
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
