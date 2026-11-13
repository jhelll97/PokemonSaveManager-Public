package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import java.util.concurrent.Executors

/** Native PA8 Effort Level editor with reviewed new-copy persistence. */
class SwitchLegendsArceusPokemonEffortActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2171
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: LegendsArceusPokemonArea? = null
    private var pasture = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var values: LegendsArceusPokemonEffortValues? = null
    private var pendingPlan: LegendsArceusPokemonEffortCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var statSpinner: Spinner
    private lateinit var levelSpinner: Spinner
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
        root.addView(TextView(this).apply { text = "Legends: Arceus • Effort Levels"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "Increase one native PA8 Effort Level using its IV-based Ganbaru range. PSE never lowers a saved value and never exceeds level 10."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source, PA8, IVs and Ganbaru values…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        statSpinner = Spinner(this)
        root.addView(statSpinner)
        levelSpinner = Spinner(this)
        root.addView(levelSpinner)
        root.addView(TextView(this).apply {
            text = "This mirrors one-way Grit investment only. PSE does not consume Grit items, alter inventory, recalculate cached party stats, or certify encounter history. Every unselected PA8 field remains exact."
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

        statSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateLevelChoices()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LegendsArceusPokemonEffortEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                bytes to LegendsArceusPokemonEffortEditing.read(bytes, area!!, pasture, position)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, read) ->
                    opened = bytes
                    values = read
                    statSpinner.adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        read.stats.filter { it.effortLevel < 10 }
                    )
                    updateLevelChoices()
                    status.text = describe(read) +
                        "\nSource SHA-256: ${LegendsArceusPokemonEffortEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The selected PA8 could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun describe(read: LegendsArceusPokemonEffortValues): String = buildString {
        append("${LegendsArceusSpeciesNames.name(read.pokemon.species)} (#${read.pokemon.species}) • Form ${read.pokemon.form}\n")
        append("Nickname: ${read.pokemon.nickname.ifBlank { "None" }}\n")
        read.stats.forEach { stat ->
            append("${stat.name}: level ${stat.effortLevel}/10 • IV ${stat.iv} • saved Ganbaru ${stat.storedValue}/${stat.maxStoredValue}\n")
        }
    }.trimEnd()

    private fun updateLevelChoices() {
        if (values == null) return
        val stat = statSpinner.selectedItem as? LegendsArceusEffortStat ?: return
        val levels = (stat.effortLevel..10).toList()
        levelSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            levels
        )
        levelSpinner.setSelection(if (levels.size > 1) 1 else 0)
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null && values?.stats?.any { it.effortLevel < 10 } == true
        statSpinner.isEnabled = !value && ready
        levelSpinner.isEnabled = !value && ready
        review.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        if (values == null) return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        val stat = statSpinner.selectedItem as? LegendsArceusEffortStat ?: return
        val level = levelSpinner.selectedItem as? Int ?: return
        val plan = try {
            LegendsArceusPokemonEffortEditing.stage(
                bytes, hash, selectedArea, pasture, position, stat.index, level
            )
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested Effort Level change is unavailable."
            return
        }
        setBusy(true)
        var dispatched = false
        val beforeStat = plan.before.stats[plan.statIndex]
        val afterStat = plan.after.stats[plan.statIndex]
        val recordSize = if (plan.area == LegendsArceusPokemonArea.PASTURE)
            LegendsArceusPokemonReadOnly.STORED_SIZE else LegendsArceusPokemonReadOnly.PARTY_SIZE
        AlertDialog.Builder(this).setTitle("Save Effort Level to a new copy?")
            .setMessage(
                "${beforeStat.name}: level ${beforeStat.effortLevel} → ${afterStat.effortLevel}\n" +
                    "Saved Ganbaru: ${beforeStat.storedValue} → ${afterStat.storedValue} (IV ${beforeStat.iv}, bias ${beforeStat.baseBias})\n\n" +
                    "Allowed native scope: one Ganbaru byte at 0x${(0xA4 + plan.statIndex).toString(16).uppercase()} inside the selected $recordSize-byte encrypted PA8 at 0x${plan.absoluteOffset.toString(16).uppercase()}, plus its checksum and the native SHA-256 footer. " +
                    "This one-way increase does not consume Grit items or recalculate cached party stats. " +
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
                val selected = plan.after.stats[plan.statIndex]
                status.text = result.fold(
                    onSuccess = {
                        "Effort-Level copy saved and reopened successfully.\n" +
                            "${selected.name}: level ${selected.effortLevel}/10\n" +
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
        return LegendsArceusPokemonEffortEditing.readDocumentBytes(input)
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
