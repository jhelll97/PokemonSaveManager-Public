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

/** Deletes one Legends: Z-A PA9 through a reviewed, verified new save copy. */
class SwitchLegendsZaPokemonDeleteActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2175
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var sourceArea: LegendsZaPokemonArea? = null
    private var sourceBox = 0
    private var sourcePosition = 0
    private var readyPlan: LegendsZaPokemonDeleteCopyPlan? = null
    private var pendingPlan: LegendsZaPokemonDeleteCopyPlan? = null
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
            LegendsZaPokemonArea.valueOf(intent?.getStringExtra("pse.switch.pokemon.area") ?: "")
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
        root.addView(TextView(this).apply { text = "Legends: Z-A • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Delete Pokémon"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "PSE clears one native PC-box PA9 body or compacts native party PA9 bodies after removing one Pokémon, then writes a reviewed new save copy. " +
                "Every 64-byte box footer and 136-byte party tail stays in its native position. Eggs, invalid native limits, unverified species/forms, the party's last Pokémon, every unrelated record, and the original save remain protected. " +
                "The verified recovery backup retains the deleted PA9."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source, PA9, party, and PC storage…"
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
                LegendsZaPokemonDeleteEditing.stage(
                    readSource(), expectedHash ?: error("Reopen the source from PSE."),
                    sourceArea ?: error("Reopen Pokémon Boxes."), sourceBox, sourcePosition
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { plan ->
                    readyPlan = plan
                    val selected = plan.selection
                    val p = selected.pokemon
                    status.text = "Native source, PA9, party, and PC storage verified\n" +
                        "${p.nickname.ifBlank { ScarletVioletSpeciesNames.name(p.species) }} (#${p.species}) • Level ${p.cachedLevel}\n" +
                        "Delete from: ${selected.sourceLocation}\n" +
                        "Party: ${selected.partyCountBefore}/6 → ${selected.partyCountAfter}/6\n" +
                        "Source SHA-256: ${plan.sourceSha256}\nPA9 SHA-256: ${plan.sourceRecordSha256}"
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
        return LegendsZaPokemonDeleteEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = LegendsZaPokemonDeleteEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun showReview(plan: LegendsZaPokemonDeleteCopyPlan) {
        if (busy || plan !== readyPlan) return
        setBusy(true)
        var dispatched = false
        val selected = plan.selection
        val p = selected.pokemon
        val moves = p.moves.filter { it.id != 0 }
            .joinToString { "#${it.id} (PP ${it.pp})" }.ifBlank { "None" }
        val nativeChange = if (selected.sourceArea == LegendsZaPokemonArea.BOX) {
            "The selected 344-byte PC-box PA9 body is cleared while its 64-byte native footer remains exact."
        } else {
            "Later party PA9 bodies move forward byte-for-byte, the released body is cleared, and every 136-byte native party tail remains exact. " +
                "The occupied party prefix changes from ${selected.partyCountBefore} to ${selected.partyCountAfter}."
        }
        val bodyScope = plan.changedAbsoluteBodyOffsets.joinToString { offset ->
            "0x${offset.toString(16).uppercase()}–" +
                "0x${(offset + LegendsZaPokemonReadOnly.RECORD_SIZE - 1).toString(16).uppercase()}"
        }
        AlertDialog.Builder(this).setTitle("Delete this Pokémon in a new copy?")
            .setMessage(
                "${p.nickname.ifBlank { ScarletVioletSpeciesNames.name(p.species) }} (#${p.species}) • Form ${p.form} • Level ${p.cachedLevel}\n" +
                    "Nickname: ${p.nickname.ifBlank { "None" }} • Shiny: ${if (p.shiny) "Yes" else "No"}\n" +
                    "OT ${p.originalTrainer} • TID ${p.tid} / SID ${p.sid}\nMoves: $moves\n" +
                    "Location: ${selected.sourceLocation}\nPA9 SHA-256: ${plan.sourceRecordSha256}\n\n" +
                    "$nativeChange\n\nAllowed encrypted byte scope: PA9 body range${if (plan.changedAbsoluteBodyOffsets.size == 1) "" else "s"} $bodyScope and the native SHA-256 footer. " +
                    "Every 64-byte box footer, every 136-byte party tail, and every unrelated Pokémon body remain exact. PSE verifies the unchanged source, retains a byte-identical recovery backup " +
                    "containing the deleted PA9, reopens the persisted copy, and removes a failed copy when possible. Choose a NEW empty document."
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
            .putExtra("pse.switch.family", SwitchGameFamily.LEGENDS_ZA.name)
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
                            this, SwitchGameFamily.LEGENDS_ZA, original
                        )
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.LEGENDS_ZA, backup.file, backup.sha256
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
                            "Removed: ${selected.sourceLocation}\n" +
                            "Party ${selected.partyCountBefore}/6 → ${selected.partyCountAfter}/6\n" +
                            "SHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\n" +
                            "Original source unchanged; the backup retains the deleted PA9."
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

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
