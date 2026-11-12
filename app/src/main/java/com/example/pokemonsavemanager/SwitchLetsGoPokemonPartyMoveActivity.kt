package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/** Moves one native PB7 between the party pointer list and PC Box in a reviewed new copy. */
class SwitchLetsGoPokemonPartyMoveActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2166
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var sourceIndex = -1
    private var selection: LetsGoPokemonPartyMoveSelection? = null
    private var readyPlan: LetsGoPokemonPartyMoveCopyPlan? = null
    private var pendingPlan: LetsGoPokemonPartyMoveCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var reviewMove: Button
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
        root.addView(TextView(this).apply { text = "Move to PC Box / Move to Party"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "PSE changes only the selected Pokémon's native party assignment in the shared Let's Go storage pool and writes a reviewed new save copy. " +
                "The exact PB7, party order, partner assignment, every other Pokémon, and the original save remain protected."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source, PB7, and native party pointers…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        reviewMove = Button(this).apply {
            text = "Review move"
            isEnabled = false
            setOnClickListener { preparePlan() }
        }
        root.addView(reviewMove)
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
        OmegaRubyUi.styleWorkflowScreen(this, root, status)
        setContentView(OmegaRubyUi.scrollingPage(this, root))

        setBusy(true)
        worker.execute {
            val result = runCatching {
                LetsGoPokemonPartyMoveEditing.inspect(
                    readSource(), expectedHash ?: error("Reopen the source from PSE."), sourceIndex
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { loaded ->
                    selection = loaded
                    reviewMove.text = loaded.direction.label
                    val p = loaded.pokemon
                    status.text = "Native source, PB7, and party pointers verified\n" +
                        "${p.nickname.ifBlank { LetsGoSpeciesNames.name(p.species) }} (#${p.species}) • Level ${p.cachedLevel}\n" +
                        "Storage record: #${loaded.sourceIndex + 1}\n" +
                        "Before: ${partySummary(loaded.partyBefore)}\n" +
                        "After: ${partySummary(loaded.partyAfter)}\n" +
                        "Source SHA-256: ${expectedHash}"
                }.onFailure {
                    status.text = it.message ?: "The party move could not be prepared safely."
                }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        reviewMove.isEnabled = !value && selection != null
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return LetsGoPokemonPartyMoveEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = LetsGoPokemonPartyMoveEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun preparePlan() {
        if (busy || selection == null) return
        setBusy(true)
        status.text = "Revalidating the source PB7 and native party pointers…"
        worker.execute {
            val result = runCatching {
                LetsGoPokemonPartyMoveEditing.stage(
                    readSource(), expectedHash ?: error("Reopen the source from PSE."), sourceIndex
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { plan ->
                    readyPlan = plan
                    setBusy(false)
                    showReview(plan)
                }.onFailure {
                    status.text = it.message ?: "The selected Pokémon could not be moved safely."
                    setBusy(false)
                }
            }
        }
    }

    private fun showReview(plan: LetsGoPokemonPartyMoveCopyPlan) {
        if (busy || plan !== readyPlan) return
        setBusy(true)
        var dispatched = false
        val selected = plan.selection
        val p = selected.pokemon
        val afterLocation = when (selected.direction) {
            LetsGoPokemonPartyMoveDirection.TO_PARTY ->
                "Party position ${selected.partyAfter.indexOf(selected.sourceIndex) + 1}"
            LetsGoPokemonPartyMoveDirection.TO_BOX -> "PC Box storage #${selected.sourceIndex + 1}"
        }
        AlertDialog.Builder(this).setTitle("${selected.direction.label} in a new copy?")
            .setMessage(
                "${p.nickname.ifBlank { LetsGoSpeciesNames.name(p.species) }} (#${p.species}) • Level ${p.cachedLevel}\n" +
                    "Before party: ${partySummary(selected.partyBefore)}\n" +
                    "After party: ${partySummary(selected.partyAfter)}\n" +
                    "Selected Pokémon after: $afterLocation\n\n" +
                    "PB7 SHA-256: ${plan.sourceRecordSha256}\n\n" +
                    "Allowed native byte scope: the six party pointer words at 0x5A00–0x5A0B and the block 8 CRC word. " +
                    "The selected 260-byte PB7 at 0x${plan.sourceOffset.toString(16).uppercase()}, storage count, partner pointer, every PB7 record, and unrelated bytes remain exact. " +
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
                val selected = plan.selection
                status.text = result.fold(
                    onSuccess = {
                        "${selected.direction.label} saved and reopened successfully.\n" +
                            "Before: ${partySummary(selected.partyBefore)}\n" +
                            "After: ${partySummary(selected.partyAfter)}\n" +
                            "SHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\n" +
                            "Original source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The moved copy could not be verified.") +
                            "\nRecovery backup: ${backupName ?: "not created"}"
                    }
                )
                readyPlan = null
                selection = null
                setBusy(false)
            }
        }
    }

    private fun partySummary(party: List<Int>): String = if (party.isEmpty()) "Empty"
        else party.mapIndexed { position, index -> "${position + 1}: storage #${index + 1}" }.joinToString(" • ")

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
