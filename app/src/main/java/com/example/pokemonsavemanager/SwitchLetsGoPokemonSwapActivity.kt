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

/** Swaps two unassigned native PB7 records in a reviewed new Let's Go save copy. */
class SwitchLetsGoPokemonSwapActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2160
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var sourceIndex = -1
    private var selection: LetsGoPokemonSwapSelection? = null
    private var readyPlan: LetsGoPokemonSwapCopyPlan? = null
    private var pendingPlan: LetsGoPokemonSwapCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var choose: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        sourceIndex = intent?.getIntExtra("pse.switch.pokemon.index", -1) ?: -1
        if (sourceUri == null || expectedHash.isNullOrBlank() || sourceIndex < 0) {
            finish()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Let's Go • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Swap with Pokémon in this Box"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "PSE exchanges two checksum-valid, unassigned PB7 records in the single native Let's Go Pokémon Box and writes a reviewed new save copy. " +
                "Party Pokémon, the partner Pokémon, Eggs, unverified species/forms, every other storage record, and the original save remain protected."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source and boxed Pokémon…"
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
                val source = readSource()
                LetsGoPokemonSwapEditing.inspect(
                    source, expectedHash ?: error("Reopen the source from PSE."), sourceIndex
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { loaded ->
                    selection = loaded
                    val p = loaded.sourcePokemon
                    status.text = "Native source and PB7 integrity verified\n" +
                        "${LetsGoSpeciesNames.name(p.species)} (#${p.species}) • Level ${p.cachedLevel}\n" +
                        "Source: storage #${loaded.sourceIndex + 1}\n" +
                        "Eligible boxed destinations: ${loaded.candidates.size}\n" +
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
        return LetsGoPokemonSwapEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = LetsGoPokemonSwapEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun showDestinationChooser() {
        if (busy) return
        val loaded = selection ?: return
        setBusy(true)
        var selected = false
        val labels = loaded.candidates.map { candidate ->
            val p = candidate.pokemon
            "Storage #${candidate.index + 1} • ${p.nickname.ifBlank { LetsGoSpeciesNames.name(p.species) }} • Level ${p.cachedLevel}"
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Swap with Pokémon in this Box")
            .setItems(labels) { _, which ->
                selected = true
                preparePlan(loaded.candidates[which].index)
            }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { if (!selected) setBusy(false) }
            .show()
    }

    private fun preparePlan(destinationIndex: Int) {
        status.text = "Revalidating both native PB7 records…"
        worker.execute {
            val result = runCatching {
                LetsGoPokemonSwapEditing.stage(
                    readSource(), expectedHash ?: error("Reopen the source from PSE."),
                    sourceIndex, destinationIndex
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

    private fun showReview(plan: LetsGoPokemonSwapCopyPlan) {
        if (busy || plan !== readyPlan) return
        setBusy(true)
        var dispatched = false
        val source = plan.sourcePokemon
        val destination = plan.destinationPokemon
        AlertDialog.Builder(this).setTitle("Swap these Pokémon in a new copy?")
            .setMessage(
                "Storage #${plan.sourceIndex + 1}: ${source.nickname.ifBlank { LetsGoSpeciesNames.name(source.species) }} (#${source.species}) • Level ${source.cachedLevel}\n" +
                    "Storage #${plan.destinationIndex + 1}: ${destination.nickname.ifBlank { LetsGoSpeciesNames.name(destination.species) }} (#${destination.species}) • Level ${destination.cachedLevel}\n\n" +
                    "After: the two exact 260-byte PB7 records exchange native storage positions.\n" +
                    "Source PB7 SHA-256: ${plan.sourceRecordSha256}\n" +
                    "Destination PB7 SHA-256: ${plan.destinationRecordSha256}\n\n" +
                    "Allowed native byte scope: the PB7 bodies at 0x${plan.sourceOffset.toString(16).uppercase()} and " +
                    "0x${plan.destinationOffset.toString(16).uppercase()}, plus the block 9 CRC word. " +
                    "Storage count, party pointers, partner pointer, every other Pokémon record, and unrelated bytes remain exact. " +
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
                            this, SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, original
                        )
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.LETS_GO_PIKACHU_EEVEE,
                            backup.file, backup.sha256
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
                            "Storage #${plan.sourceIndex + 1} ↔ storage #${plan.destinationIndex + 1}\n" +
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
