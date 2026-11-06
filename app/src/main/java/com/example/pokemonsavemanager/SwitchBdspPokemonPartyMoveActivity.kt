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

/** Moves one BDSP PB8 between party and PC storage in a reviewed new copy. */
class SwitchBdspPokemonPartyMoveActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2168
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var sourceArea: BdspPokemonArea? = null
    private var sourceBox = 0
    private var sourcePosition = 0
    private var readyPlan: BdspPokemonPartyMoveCopyPlan? = null
    private var pendingPlan: BdspPokemonPartyMoveCopyPlan? = null
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
            BdspPokemonArea.valueOf(intent?.getStringExtra("pse.switch.pokemon.area") ?: "")
        }.getOrNull()
        sourceBox = intent?.getIntExtra("pse.switch.pokemon.box", 0) ?: 0
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
        root.addView(TextView(this).apply { text = "BDSP • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Move to PC Box / Move to Party"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "PSE moves the exact selected PB8 between the native six-slot party and PC storage, compacts party order when needed, and writes a reviewed new save copy. " +
                "Eggs, invalid native limits, unverified species/forms, every unrelated Pokémon, and the original save remain protected."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source, PB8, party, and PC storage…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        review = Button(this).apply {
            text = "Review move"
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
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                BdspPokemonPartyMoveEditing.stage(
                    readSource(), expectedHash ?: error("Reopen the source from PSE."),
                    sourceArea ?: error("Reopen Pokémon Boxes."), sourceBox, sourcePosition
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { plan ->
                    readyPlan = plan
                    review.text = plan.selection.direction.label
                    val selected = plan.selection
                    val p = selected.pokemon
                    status.text = "Native source, PB8, party, and PC storage verified\n" +
                        "${p.nickname.ifBlank { SwordShieldSpeciesNames.name(p.species) }} (#${p.species}) • Level ${p.cachedLevel}\n" +
                        "Before: ${selected.sourceLocation} • Party ${selected.partyCountBefore}/6\n" +
                        "After: ${selected.destinationLocation} • Party ${selected.partyCountAfter}/6\n" +
                        "Source SHA-256: ${plan.sourceSha256}\nPB8 SHA-256: ${plan.sourceRecordSha256}"
                }.onFailure {
                    status.text = it.message ?: "The party move could not be prepared safely."
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
        return BdspPokemonPartyMoveEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = BdspPokemonPartyMoveEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun showReview(plan: BdspPokemonPartyMoveCopyPlan) {
        if (busy || plan !== readyPlan) return
        setBusy(true)
        var dispatched = false
        val selected = plan.selection
        val p = selected.pokemon
        val moves = p.moves.filter { it.id != 0 }
            .joinToString { "#${it.id} (PP ${it.pp})" }.ifBlank { "None" }
        AlertDialog.Builder(this).setTitle("${selected.direction.label} in a new copy?")
            .setMessage(
                "${p.nickname.ifBlank { SwordShieldSpeciesNames.name(p.species) }} (#${p.species}) • Form ${p.form} • Level ${p.cachedLevel}\n" +
                    "Nickname: ${p.nickname.ifBlank { "None" }} • Shiny: ${if (p.shiny) "Yes" else "No"}\n" +
                    "OT ${p.originalTrainer} • TID ${p.tid} / SID ${p.sid}\nMoves: $moves\n\n" +
                    "Before: ${selected.sourceLocation} • Party ${selected.partyCountBefore}/6.\n" +
                    "After: the exact 344-byte PB8 occupies ${selected.destinationLocation} • Party ${selected.partyCountAfter}/6.\n" +
                    "PB8 SHA-256: ${plan.sourceRecordSha256}\n\n" +
                    "Allowed native byte scope: one PC-box slot at 0x${plan.boxChangedOffset.toString(16).uppercase()}, " +
                    "party bytes 0x${plan.partyChangedStart.toString(16).uppercase()}–" +
                    "0x${(plan.partyChangedEndExclusive - 1).toString(16).uppercase()}, " +
                    "the party-count byte at 0x${plan.partyCountOffset.toString(16).uppercase()}, and the whole-file MD5. " +
                    "Every unrelated PC-box and party record remains exact. PSE verifies the unchanged source, retains a verified recovery backup, " +
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
                            this, SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, original
                        )
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, backup.file, backup.sha256
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
                            "${selected.sourceLocation} → ${selected.destinationLocation}\n" +
                            "Party ${selected.partyCountBefore}/6 → ${selected.partyCountAfter}/6\n" +
                            "SHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\n" +
                            "Original source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The moved copy could not be verified.") +
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
