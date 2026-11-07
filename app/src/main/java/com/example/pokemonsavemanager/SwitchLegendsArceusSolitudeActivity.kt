package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Native Path of Solitude completion with reviewed new-copy persistence. */
class SwitchLegendsArceusSolitudeActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2166
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var number = 0
    private var opened: ByteArray? = null
    private var loaded: LegendsArceusSolitudeEditRead? = null
    private var pendingPlan: LegendsArceusSolitudeCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        number = intent?.getIntExtra("pse.switch.dex.number", 0) ?: 0
        if (sourceUri == null || expectedHash.isNullOrBlank() || number !in 1..242) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Legends: Arceus • Path of Solitude"; textSize = 28f
        })
        root.addView(TextView(this).apply {
            text = "Mark the selected species' native Path of Solitude completion flag. This is a one-way completion action; PSE does not clear a completed challenge."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply {
            text = "PSE changes only bit 6 of the species' native research flags byte and the required SHA-256 footer. It does not change research counters, rates, reports, other flags, rewards, battle records or story progress."
            textSize = 14f
        })
        review = Button(this).apply {
            text = "Review and save completed flag"
            setOnClickListener { reviewChange() }
        }
        root.addView(review)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"; visibility = View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply {
            text = "Back to Pokédex entry"
            setOnClickListener { if (!busy) finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LegendsArceusSolitudeEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                bytes to LegendsArceusSolitudeEditing.read(bytes, number)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, read) ->
                    opened = bytes
                    loaded = read
                    status.text =
                        "Hisui #$number • ${LegendsArceusSpeciesNames.name(read.entry.speciesId)} " +
                            "(National #${read.entry.speciesId})\n" +
                            "Path of Solitude complete: ${if (read.entry.solitudeComplete) "Yes" else "No"}\n" +
                            "Source SHA-256: ${LegendsArceusSolitudeEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The save could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(state: Boolean) {
        busy = state
        val ready = opened != null && loaded?.entry?.solitudeComplete == false
        review.isEnabled = !state && ready
        inspectCopy.isEnabled = !state && verifiedCopy != null
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val plan = try {
            LegendsArceusSolitudeEditing.stage(bytes, hash, number)
        } catch (failure: Exception) {
            status.text = failure.message ?: "Path of Solitude completion is unavailable."
            return
        }
        setBusy(true)
        var dispatched = false
        AlertDialog.Builder(this).setTitle("Mark Path of Solitude complete in a new copy?")
            .setMessage(
                "Hisui #$number • ${LegendsArceusSpeciesNames.name(plan.before.entry.speciesId)}\n" +
                    "Path of Solitude complete: No → Yes\n\n" +
                    "Allowed native scope: bit 6 in 1 byte at 0x${plan.absoluteOffset.toString(16).uppercase()} plus the native SHA-256 footer. " +
                    "Research counters, rates, reports, every other flag, rewards, species records and unrelated objects remain exact. " +
                    "PSE will verify a recovery backup and reopen the persisted copy. Choose a NEW empty document; the selected source cannot be replaced."
            )
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { if (!dispatched) setBusy(false) }
            .setPositiveButton("Choose new copy") { _, _ ->
                if (!dispatched && !isFinishing && !isDestroyed) {
                    dispatched = true
                    pendingPlan = plan
                    verifiedCopy = null
                    inspectCopy.visibility = View.GONE
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
                inspectCopy.visibility =
                    if (verifiedCopy != null) View.VISIBLE else View.GONE
                status.text = result.fold(
                    onSuccess = {
                        "Path of Solitude copy saved and reopened successfully.\n" +
                            "Hisui #$number • ${LegendsArceusSpeciesNames.name(plan.after.entry.speciesId)}\n" +
                            "Path of Solitude complete: Yes\n" +
                            "SHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\n" +
                            "Original source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The copy could not be verified.") +
                            "\nRecovery backup: ${backupName ?: "not created"}"
                    }
                )
                setBusy(false)
            }
        }
    }

    private fun inspectVerifiedCopy() {
        if (busy) return
        val (uri, hash) = verifiedCopy ?: return
        startActivity(Intent(this, SwitchReadOnlyActivity::class.java).setData(uri)
            .putExtra("pse.switch.family", SwitchGameFamily.LEGENDS_ARCEUS.name)
            .putExtra("pse.switch.sha256", hash)
            .putExtra("pse.switch.section", "pokedex")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return readDocument(uri)
    }

    private fun readDocument(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
        return LegendsArceusSolitudeEditing.readDocumentBytes(input)
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
