package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import java.util.concurrent.Executors

/** Native PA8 Alpha Move editor with reviewed new-copy persistence. */
class SwitchLegendsArceusPokemonAlphaMoveActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2169
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: LegendsArceusPokemonArea? = null
    private var pasture = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var values: LegendsArceusPokemonAlphaMoveValues? = null
    private var pendingPlan: LegendsArceusPokemonAlphaMoveCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var moveSpinner: Spinner
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        area = runCatching {
            LegendsArceusPokemonArea.valueOf(intent?.getStringExtra("pse.switch.pokemon.area") ?: "")
        }.getOrNull()
        pasture = intent?.getIntExtra("pse.switch.pokemon.pasture", 0) ?: 0
        position = intent?.getIntExtra("pse.switch.pokemon.position", 0) ?: 0
        if (sourceUri == null || expectedHash.isNullOrBlank() || area == null || position <= 0) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Legends: Arceus • Alpha Move"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "Choose the native Alpha Move for one Alpha PA8. Only moves permitted by the selected species/form's pinned PLA move-shop table are offered, and the required Mastered flag is set."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source, Alpha PA8 and native permit table…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        moveSpinner = Spinner(this)
        root.addView(moveSpinner)
        root.addView(TextView(this).apply {
            text = "Changing Alpha Move can break the Pokémon's encounter-RNG relationship. PSE enforces the native species/form permit table and required Mastered flag, but does not certify encounter legality. Purchased flags, every unselected Mastered flag, current moves, relearn moves and every other PA8 value remain exact."
            textSize = 14f
        })
        review = Button(this).apply {
            text = "Review and save new copy"
            setOnClickListener { reviewChange() }
        }
        root.addView(review)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"; visibility = View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply {
            text = "Back to Pokémon Details"
            setOnClickListener { if (!busy) finish() }
        })
        OmegaRubyUi.stylePokemonEditor(this, root, status)
        setContentView(OmegaRubyUi.scrollingPage(this, root))

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LegendsArceusPokemonMoveShopEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                bytes to LegendsArceusPokemonMoveShopEditing.readAlphaMove(bytes, area!!, pasture, position)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, read) ->
                    if (read.offerings.isEmpty()) {
                        status.text = "This Alpha PA8 species/form has no permitted native Alpha Move choices."
                        return@onSuccess
                    }
                    opened = bytes
                    values = read
                    moveSpinner.adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        read.offerings
                    )
                    moveSpinner.setSelection(read.offerings.indexOfFirst { it.moveId == read.alphaMoveId }.coerceAtLeast(0))
                    status.text = describe(read) +
                        "\nSource SHA-256: ${LegendsArceusPokemonMoveShopEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The selected PA8 could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun describe(read: LegendsArceusPokemonAlphaMoveValues): String =
        "${LegendsArceusSpeciesNames.name(read.pokemon.species)} (#${read.pokemon.species}) • Form ${read.pokemon.form}\n" +
            "Nickname: ${read.pokemon.nickname.ifBlank { "None" }}\n" +
            "Saved Alpha Move: ${read.alphaMoveName}${if (read.alphaMoveId == 0) "" else " (#${read.alphaMoveId})"}\n" +
            "Permitted native Alpha Move choices: ${read.offerings.size}"

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null && values?.offerings?.isNotEmpty() == true
        moveSpinner.isEnabled = !value && ready
        review.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        val read = values ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        val selected = read.offerings.getOrNull(moveSpinner.selectedItemPosition) ?: return
        val plan = try {
            LegendsArceusPokemonMoveShopEditing.stageAlphaMove(
                bytes, hash, selectedArea, pasture, position, selected.moveId
            )
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested Alpha Move change is unavailable."
            return
        }
        setBusy(true)
        var dispatched = false
        val recordSize = if (plan.area == LegendsArceusPokemonArea.PASTURE)
            LegendsArceusPokemonReadOnly.STORED_SIZE else LegendsArceusPokemonReadOnly.PARTY_SIZE
        AlertDialog.Builder(this).setTitle("Save Alpha Move to a new copy?")
            .setMessage(
                "${plan.before.alphaMoveName}${if (plan.before.alphaMoveId == 0) "" else " (#${plan.before.alphaMoveId})"} → " +
                    "${plan.after.alphaMoveName} (#${plan.after.alphaMoveId})\n" +
                    "Required Mastered flag: ${if (plan.beforeSelected.mastered) "already set" else "clear"} → set\n\n" +
                    "Allowed native scope: Alpha Move word 0x3E and Mastered bit ${plan.selectedIndex} inside the selected $recordSize-byte encrypted PA8 at 0x${plan.absoluteOffset.toString(16).uppercase()}, plus its checksum and the native SHA-256 footer. " +
                    "Changing this field can break encounter-RNG legality; this review does not certify encounter origin. " +
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
                inspectCopy.visibility = if (verifiedCopy != null) View.VISIBLE else View.GONE
                status.text = result.fold(
                    onSuccess = {
                        "Alpha Move copy saved and reopened successfully.\n" +
                            "${plan.after.alphaMoveName} (#${plan.after.alphaMoveId}) • required Mastered flag set\n" +
                            "SHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\nOriginal source unchanged."
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
            .putExtra("pse.switch.section", "storage")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return readDocument(uri)
    }

    private fun readDocument(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
        return LegendsArceusPokemonMoveShopEditing.readDocumentBytes(input)
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
