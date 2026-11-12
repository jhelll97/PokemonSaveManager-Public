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

/** Deletes one native Let's Go PB7 through a reviewed, verified new save copy. */
class SwitchLetsGoPokemonDeleteActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2170
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var sourceIndex = -1
    private var readyPlan: LetsGoPokemonDeleteCopyPlan? = null
    private var pendingPlan: LetsGoPokemonDeleteCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var review: Button
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
        root.addView(TextView(this).apply { text = "Delete Pokémon"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "PSE removes the selected native PB7 from a reviewed new save copy, compacts the shared storage pool, and adjusts only affected party and partner pointers. " +
                "The save-bound partner, an Egg, an unverified species/form, and the party's last Pokémon remain protected. " +
                "The original save stays unchanged and the verified recovery backup retains the deleted PB7."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source, PB7, storage pool, and native pointers…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        review = Button(this).apply {
            text = "Review deletion to new copy"
            isEnabled = false
            setOnClickListener { readyPlan?.let { showReview(it) } }
        }
        root.addView(review)
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
        OmegaRubyUi.stylePokemonEditor(this, root, status)
        setContentView(OmegaRubyUi.scrollingPage(this, root))

        setBusy(true)
        worker.execute {
            val result = runCatching {
                LetsGoPokemonDeleteEditing.stage(
                    readSource(), expectedHash ?: error("Reopen the source from PSE."), sourceIndex
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { plan ->
                    readyPlan = plan
                    val selected = plan.selection
                    val p = selected.pokemon
                    status.text = "Native source, PB7, storage pool, and pointers verified\n" +
                        "${p.nickname.ifBlank { LetsGoSpeciesNames.name(p.species) }} (#${p.species}) • Level ${p.cachedLevel}\n" +
                        "Storage record: #${selected.sourceIndex + 1} of ${selected.storageCountBefore}\n" +
                        "Party before: ${partySummary(selected.partyBefore)}\n" +
                        "Party after: ${partySummary(selected.partyAfter)}\n" +
                        "Partner pointer: ${pointerSummary(selected.partnerBefore)} → ${pointerSummary(selected.partnerAfter)}\n" +
                        "Source SHA-256: ${plan.sourceSha256}\nPB7 SHA-256: ${plan.removedRecordSha256}"
                }.onFailure {
                    status.text = it.message ?: "The Pokémon deletion could not be prepared safely."
                }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        review.isEnabled = !value && readyPlan != null
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return LetsGoPokemonDeleteEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = LetsGoPokemonDeleteEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun showReview(plan: LetsGoPokemonDeleteCopyPlan) {
        if (busy || plan !== readyPlan) return
        setBusy(true)
        var dispatched = false
        val selected = plan.selection
        val p = selected.pokemon
        val moves = p.moves.filter { it.id != 0 }
            .joinToString { "#${it.id} (PP ${it.pp})" }.ifBlank { "None" }
        AlertDialog.Builder(this).setTitle("Delete this Pokémon in a new copy?")
            .setMessage(
                "${p.nickname.ifBlank { LetsGoSpeciesNames.name(p.species) }} (#${p.species}) • Form ${p.form} • Level ${p.cachedLevel}\n" +
                    "OT ${p.originalTrainer} • TID ${p.tid} / SID ${p.sid}\nMoves: $moves\n" +
                    "PB7 SHA-256: ${plan.removedRecordSha256}\n\n" +
                    "Before: ${selected.storageCountBefore} stored • Party ${partySummary(selected.partyBefore)} • " +
                    "Partner ${pointerSummary(selected.partnerBefore)}\n" +
                    "After: ${selected.storageCountBefore - 1} stored • Party ${partySummary(selected.partyAfter)} • " +
                    "Partner ${pointerSummary(selected.partnerAfter)}\n\n" +
                    "Allowed native byte scope: party/partner pointer words and storage count at 0x5A00–0x5A0F; " +
                    "the compacted PB7 range 0x${plan.sourceOffset.toString(16).uppercase()}–" +
                    "0x${(plan.oldTailOffset + LetsGoPokemonReadOnly.RECORD_SIZE - 1).toString(16).uppercase()}; " +
                    "and block 8 and block 9 CRC words. Every retained PB7 is copied byte-for-byte to its reviewed compacted index, " +
                    "the released tail record is zeroed, and every unrelated byte remains exact. " +
                    "PSE verifies the unchanged source, retains a byte-identical recovery backup containing the deleted PB7, " +
                    "reopens the persisted copy, and removes a failed copy when possible. Choose a NEW empty document."
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
                        "Delete Pokémon saved and reopened successfully.\n" +
                            "Storage count: ${selected.storageCountBefore} → ${selected.storageCountBefore - 1}\n" +
                            "Party: ${partySummary(selected.partyBefore)} → ${partySummary(selected.partyAfter)}\n" +
                            "SHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\n" +
                            "Original source unchanged; the backup retains the deleted PB7."
                    },
                    onFailure = {
                        (it.message ?: "The deleted copy could not be verified.") +
                            "\nRecovery backup: ${backupName ?: "not created"}"
                    }
                )
                readyPlan = null
                setBusy(false)
            }
        }
    }

    private fun partySummary(party: List<Int>): String = if (party.isEmpty()) "Empty"
        else party.mapIndexed { position, index -> "${position + 1}: storage #${index + 1}" }.joinToString(" • ")

    private fun pointerSummary(index: Int?): String = index?.let { "storage #${it + 1}" } ?: "None"

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
