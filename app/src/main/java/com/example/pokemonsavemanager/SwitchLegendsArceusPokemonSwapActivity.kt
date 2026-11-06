package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Swaps two native PA8 records in one Legends: Arceus box and saves a reviewed new copy. */
class SwitchLegendsArceusPokemonSwapActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2163
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var sourcePasture = 0
    private var sourcePosition = 0
    private var selection: LegendsArceusPokemonSwapSelection? = null
    private var readyPlan: LegendsArceusPokemonSwapCopyPlan? = null
    private var pendingPlan: LegendsArceusPokemonSwapCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var choose: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        sourcePasture = intent?.getIntExtra("pse.switch.pokemon.pasture", 0) ?: 0
        sourcePosition = intent?.getIntExtra("pse.switch.pokemon.position", 0) ?: 0
        if (sourceUri == null || expectedHash.isNullOrBlank() ||
            sourcePasture !in 1..32 || sourcePosition !in 1..30) {
            finish()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Legends: Arceus • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Swap with Pokémon in this Box"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "PSE exchanges two checksum-valid native PA8 records within the selected pasture and writes a reviewed new save copy. " +
                "Eggs, Noble records, invalid native limits, unverified species/forms, party data, every other pasture slot, and the original save remain protected."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source and pasture Pokémon…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        choose = Button(this).apply {
            text = "Choose Pokémon in this Box"
            isEnabled = false
            setOnClickListener { showDestinationChooser() }
        }
        root.addView(choose)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"
            visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply {
            text = "Back to Pokémon Boxes"
            setOnClickListener { if (!busy) finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                LegendsArceusPokemonSwapEditing.inspect(
                    readSource(), expectedHash ?: error("Reopen the source from PSE."),
                    sourcePasture, sourcePosition
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { loaded ->
                    selection = loaded
                    val p = loaded.sourcePokemon
                    status.text = "Native source and PA8 integrity verified\n" +
                        "${LegendsArceusSpeciesNames.name(p.species)} (#${p.species}) • EXP ${p.experience}\n" +
                        "Source: Pasture ${loaded.pasture} • Slot ${loaded.sourcePosition}\n" +
                        "Eligible destinations in this Pasture: ${loaded.candidates.size}\n" +
                        "Source SHA-256: ${expectedHash}"
                }.onFailure {
                    status.text = it.message ?: "The Pokémon swap could not be prepared safely."
                }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        choose.isEnabled = !value && selection != null
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return LegendsArceusPokemonSwapEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = LegendsArceusPokemonSwapEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun showDestinationChooser() {
        if (busy) return
        val loaded = selection ?: return
        setBusy(true)
        var selected = false
        val labels = loaded.candidates.map { candidate ->
            val p = candidate.pokemon
            "Slot ${candidate.position} • ${p.nickname.ifBlank { LegendsArceusSpeciesNames.name(p.species) }} • EXP ${p.experience}"
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Swap with Pokémon in Pasture ${loaded.pasture}")
            .setItems(labels) { _, which ->
                selected = true
                preparePlan(loaded.candidates[which].position)
            }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { if (!selected) setBusy(false) }
            .show()
    }

    private fun preparePlan(destinationPosition: Int) {
        status.text = "Revalidating both native PA8 records…"
        worker.execute {
            val result = runCatching {
                LegendsArceusPokemonSwapEditing.stage(
                    readSource(), expectedHash ?: error("Reopen the source from PSE."),
                    sourcePasture, sourcePosition, destinationPosition
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { plan ->
                    readyPlan = plan
                    setBusy(false)
                    showReview(plan)
                }.onFailure {
                    status.text = it.message ?: "The selected Pokémon could not be swapped safely."
                    setBusy(false)
                }
            }
        }
    }

    private fun showReview(plan: LegendsArceusPokemonSwapCopyPlan) {
        if (busy || plan !== readyPlan) return
        setBusy(true)
        var dispatched = false
        val source = plan.sourcePokemon
        val destination = plan.destinationPokemon
        AlertDialog.Builder(this).setTitle("Swap these Pokémon in a new copy?")
            .setMessage(
                "Pasture ${plan.pasture} • Slot ${plan.sourcePosition}: ${source.nickname.ifBlank { LegendsArceusSpeciesNames.name(source.species) }} (#${source.species}) • EXP ${source.experience}\n" +
                    "Pasture ${plan.pasture} • Slot ${plan.destinationPosition}: ${destination.nickname.ifBlank { LegendsArceusSpeciesNames.name(destination.species) }} (#${destination.species}) • EXP ${destination.experience}\n\n" +
                    "After: the two exact 360-byte PA8 records exchange native slots in Pasture ${plan.pasture}.\n" +
                    "Source PA8 SHA-256: ${plan.sourceRecordSha256}\n" +
                    "Destination PA8 SHA-256: ${plan.destinationRecordSha256}\n\n" +
                    "Allowed encrypted byte scope: the PA8 slots at 0x${plan.sourceAbsoluteOffset.toString(16).uppercase()} and " +
                    "0x${plan.destinationAbsoluteOffset.toString(16).uppercase()}, plus the native SHA-256 footer. " +
                    "All party records and their party-only tails, every other pasture slot, title identity, and unrelated bytes remain exact. " +
                    "PSE verifies the unchanged source, retains a verified recovery backup, reopens the persisted copy, and removes a failed copy when possible. " +
                    "Choose a NEW empty document."
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
            .putExtra("pse.switch.family", SwitchGameFamily.LEGENDS_ARCEUS.name)
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
                            this, SwitchGameFamily.LEGENDS_ARCEUS, original
                        )
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.LEGENDS_ARCEUS, backup.file, backup.sha256
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
                inspectCopy.visibility = if (verifiedCopy != null) android.view.View.VISIBLE
                    else android.view.View.GONE
                status.text = result.fold(
                    onSuccess = {
                        "Pokémon swapped, saved, and reopened successfully.\n" +
                            "Pasture ${plan.pasture} • Slot ${plan.sourcePosition} ↔ Slot ${plan.destinationPosition}\n" +
                            "SHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\n" +
                            "Original source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The swapped copy could not be verified.") +
                            "\nRecovery backup: ${backupName ?: "not created"}"
                    }
                )
                readyPlan = null
                selection = null
                setBusy(false)
            }
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
