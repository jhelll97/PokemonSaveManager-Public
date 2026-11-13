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

/** Duplicates one native Legends: Arceus PA8 into a reviewed new save copy. */
class SwitchLegendsArceusPokemonDuplicateActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2157
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var sourceArea: LegendsArceusPokemonArea? = null
    private var sourcePasture = 0
    private var sourcePosition = 0
    private var readyPlan: LegendsArceusPokemonDuplicateCopyPlan? = null
    private var pendingPlan: LegendsArceusPokemonDuplicateCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        sourceArea = runCatching {
            LegendsArceusPokemonArea.valueOf(intent?.getStringExtra("pse.switch.pokemon.area") ?: "")
        }.getOrNull()
        sourcePasture = intent?.getIntExtra("pse.switch.pokemon.pasture", 0) ?: 0
        sourcePosition = intent?.getIntExtra("pse.switch.pokemon.position", 0) ?: 0
        if (sourceUri == null || expectedHash.isNullOrBlank() || sourceArea == null || sourcePosition < 1) {
            finish()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Legends: Arceus • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Duplicate Pokémon"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "PSE copies the selected checksum-valid PA8 to the first empty native pasture slot in a new save copy. " +
                "Eggs, Noble records, invalid native limits, unverified species/forms, and full pasture storage remain protected. " +
                "For a party source, its 16-byte party-only tail remains in the unchanged source while the exact 360-byte stored core is duplicated. " +
                "The selected Pokémon, party data, existing pastures, and original save remain unchanged."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source and Pokémon…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        review = Button(this).apply {
            text = "Review duplicate to new copy"
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
                val source = readSource()
                val hash = expectedHash ?: error("Reopen the source from PSE.")
                require(LegendsArceusPokemonDuplicateEditing.sha256(source) == hash) {
                    "The source changed. Reopen it from PSE."
                }
                LegendsArceusPokemonDuplicateEditing.stage(
                    source, hash, sourceArea ?: error("Reopen Pokémon Boxes."), sourcePasture, sourcePosition
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { plan ->
                    readyPlan = plan
                    val p = plan.pokemon
                    status.text = "Native source and PA8 integrity verified\n" +
                        "${LegendsArceusSpeciesNames.name(p.species)} (#${p.species}) • EXP ${p.experience}\n" +
                        "Source: ${plan.sourceLocation}\n" +
                        "Destination: Pasture ${plan.destinationPasture}, slot ${plan.destinationPosition}\n" +
                        "Source SHA-256: ${plan.sourceSha256}\n" +
                        "PA8 source-record SHA-256: ${plan.recordSha256}\n" +
                        "PA8 stored-core SHA-256: ${plan.storedRecordSha256}"
                }.onFailure {
                    status.text = it.message ?: "The Pokémon duplicate could not be prepared safely."
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
        return LegendsArceusPokemonDuplicateEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = LegendsArceusPokemonDuplicateEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun showReview(plan: LegendsArceusPokemonDuplicateCopyPlan) {
        if (busy || plan !== readyPlan) return
        setBusy(true)
        var dispatched = false
        val p = plan.pokemon
        val moves = p.moves.filter { it.id != 0 }
            .joinToString { "#${it.id} (PP ${it.pp})" }.ifBlank { "None" }
        val formatNote = if (plan.partyTailBytes == 0)
            "The complete 360-byte stored PA8 is written unchanged."
        else
            "The party source includes a 16-byte party-only tail with no pasture equivalent. " +
                "Its exact 360-byte stored core is written; the complete source party record remains unchanged."
        AlertDialog.Builder(this).setTitle("Duplicate this Pokémon to a new copy?")
            .setMessage(
                "${LegendsArceusSpeciesNames.name(p.species)} (#${p.species}) • Form ${p.form} • EXP ${p.experience}\n" +
                    "Nickname: ${p.nickname.ifBlank { "None" }} • Shiny: ${if (p.shiny) "Yes" else "No"}\n" +
                    "OT ${p.originalTrainer} • TID ${p.tid} / SID ${p.sid}\nMoves: $moves\n\n" +
                    "$formatNote\n\n" +
                    "Before: ${plan.sourceLocation} remains exact.\n" +
                    "After: one exact PA8 stored-core copy occupies Pasture ${plan.destinationPasture}, slot ${plan.destinationPosition}.\n\n" +
                    "Allowed encrypted byte scope: one previously empty 360-byte PA8 stored core slot at " +
                    "0x${plan.absoluteOffset.toString(16).uppercase()} plus the native SHA-256 footer. " +
                    "Every existing pasture slot, all party data, and unrelated bytes remain exact. " +
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
                        "Pokémon duplicated, saved, and reopened successfully.\n" +
                            "Pasture ${plan.destinationPasture}, slot ${plan.destinationPosition} • SHA-256: ${plan.editedSha256}\n" +
                            "Recovery backup: $backupName\nOriginal source and source Pokémon unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The duplicate copy could not be verified.") +
                            "\nRecovery backup: ${backupName ?: "not created"}"
                    }
                )
                readyPlan = null
                setBusy(false)
            }
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
