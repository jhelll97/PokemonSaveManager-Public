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

/** Gen 1-shaped evolution review for one native Let's Go PB7. */
class SwitchLetsGoPokemonEvolutionActivity : Activity() {
    private data class OpenedPokemon(
        val bytes: ByteArray,
        val displayName: String,
        val pokemon: LetsGoPokemonRead,
        val choices: List<LetsGoEvolutionChoice>
    )

    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2176
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var storageIndex = -1
    private var opened: ByteArray? = null
    private var current: LetsGoPokemonRead? = null
    private var choices = emptyList<LetsGoEvolutionChoice>()
    private var pendingPlan: LetsGoEvolutionCopyPlan? = null
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
        storageIndex = intent?.getIntExtra("pse.switch.pokemon.index", -1) ?: -1
        if (sourceUri == null || expectedHash.isNullOrBlank() || storageIndex !in 0 until 1000) {
            finish(); return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Let's Go • Pokémon Details"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Evolve Pokémon"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "Choose the native evolution, review the rebuilt battle values, then save a verified new copy."
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
            text = "PSE changes the species and rebuilds only native species-dependent HP, battle stats and CP. Nickname, moves, trainer identity, form, IVs, Awakening Values, party placement and every other Pokémon remain exact."
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
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LetsGoPokemonEvolutionEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                val validation = LetsGoSaveValidation.inspect(bytes)
                require(validation is LetsGoValidation.Verified) {
                    "A validated native Let's Go save is required."
                }
                val slot = LetsGoStorageReadOnly.read(bytes).slot(storageIndex)
                val pokemon = (slot.read as? LetsGoSlotRead.Pokemon)?.value
                    ?: error("The selected native PB7 is not checksum-valid.")
                require(!slot.partner && !pokemon.isPartnerForm && !pokemon.isEgg && pokemon.form == 0) {
                    "This Egg, form, or save-bound partner identity cannot use base-form evolution."
                }
                val available = LetsGoPokemonEvolutionEditing.choices(pokemon.species)
                require(available.isNotEmpty()) { "This Pokémon has no supported native Kanto evolution." }
                OpenedPokemon(bytes, validation.version.displayName, pokemon, available)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { value ->
                    opened = value.bytes
                    current = value.pokemon
                    choices = value.choices
                    target.adapter = ArrayAdapter(
                        this@SwitchLetsGoPokemonEvolutionActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        choices.map { "#${it.species} ${LetsGoSpeciesNames.name(it.species)} • ${it.method}" }
                    )
                    status.text = "${value.displayName} • Native integrity verified\n" +
                        "Storage #${storageIndex + 1} • ${value.pokemon.nickname.ifBlank { LetsGoSpeciesNames.name(value.pokemon.species) }}\n" +
                        "Current species: #${value.pokemon.species} ${LetsGoSpeciesNames.name(value.pokemon.species)}\n" +
                        "Level ${value.pokemon.cachedLevel} • HP ${value.pokemon.currentHp}/${value.pokemon.maximumHp} • CP ${value.pokemon.combatPower}\n" +
                        "Source SHA-256: ${LetsGoPokemonEvolutionEditing.sha256(value.bytes)}"
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
        return LetsGoPokemonEvolutionEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read."))
    }

    private fun readDocument(uri: Uri): ByteArray = LetsGoPokemonEvolutionEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read."))

    private fun reviewEvolution() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val choice = choices.getOrNull(target.selectedItemPosition) ?: return
        setBusy(true)
        worker.execute {
            val result = runCatching {
                LetsGoPokemonEvolutionEditing.stage(bytes, hash, storageIndex, choice.species)
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

    private fun showReview(plan: LetsGoEvolutionCopyPlan) {
        var dispatched = false
        val after = plan.after
        AlertDialog.Builder(this).setTitle("Save this evolution to a new copy?")
            .setMessage(
                "Storage #${plan.storageIndex + 1} • ${plan.before.nickname.ifBlank { "No nickname" }}\n" +
                    "Species: #${plan.before.species} ${LetsGoSpeciesNames.name(plan.before.species)} → " +
                    "#${after.choice.species} ${LetsGoSpeciesNames.name(after.choice.species)} (${after.choice.method})\n" +
                    "HP: ${plan.before.currentHp}/${plan.before.maximumHp} → ${after.currentHp}/${after.maximumHp}\n" +
                    "Attack: ${plan.before.attack} → ${after.attack}\n" +
                    "Defense: ${plan.before.defense} → ${after.defense}\n" +
                    "Speed: ${plan.before.speed} → ${after.speed}\n" +
                    "Sp. Atk: ${plan.before.specialAttack} → ${after.specialAttack}\n" +
                    "Sp. Def: ${plan.before.specialDefense} → ${after.specialDefense}\n" +
                    "CP: ${plan.before.combatPower} → ${after.combatPower}\n\n" +
                    "Allowed native scope: one 260-byte PB7 at 0x${plan.recordOffset.toString(16).uppercase()} and block 9's two-byte CRC. " +
                    "Inside the PB7, only species, cached HP and battle stats, CP, and checksum may change. " +
                    "PSE verifies a recovery backup and reopens the persisted copy. Choose a NEW empty document."
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
                            putExtra(Intent.EXTRA_TITLE, "SaveData.bin")
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
            .putExtra("pse.switch.family", SwitchGameFamily.LETS_GO_PIKACHU_EEVEE.name)
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
                            this, SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, original)
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.LETS_GO_PIKACHU_EEVEE,
                            backup.file, backup.sha256)
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
