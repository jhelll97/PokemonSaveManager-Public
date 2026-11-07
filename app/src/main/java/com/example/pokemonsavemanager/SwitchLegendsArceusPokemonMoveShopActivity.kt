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
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.concurrent.Executors

/** Native PA8 move-shop purchase and mastery editor with reviewed new-copy persistence. */
class SwitchLegendsArceusPokemonMoveShopActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2168
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: LegendsArceusPokemonArea? = null
    private var pasture = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var values: LegendsArceusPokemonMoveShopValues? = null
    private var pendingPlan: LegendsArceusPokemonMoveShopCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var moveSpinner: Spinner
    private lateinit var purchased: RadioButton
    private lateinit var purchasedMastered: RadioButton
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
        root.addView(TextView(this).apply { text = "Legends: Arceus • Move Shop"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "Purchase one move permitted by the selected species/form's pinned native PLA move-shop table, then choose whether its mastery flag is set."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source, PA8 and native permit table…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        moveSpinner = Spinner(this)
        root.addView(moveSpinner)
        val states = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        purchased = RadioButton(this).apply { text = "Purchased" }
        purchasedMastered = RadioButton(this).apply { text = "Purchased and mastered" }
        states.addView(purchased)
        states.addView(purchasedMastered)
        root.addView(states)
        root.addView(TextView(this).apply {
            text = "PSE never clears the Purchased flag. Existing unselected purchase and mastery flags, Alpha Move, current moves, relearn moves and every other PA8 value remain exact."
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
        setContentView(ScrollView(this).apply { addView(root) })

        moveSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = values?.offerings?.getOrNull(position) ?: return
                if (selected.mastered) purchasedMastered.isChecked = true else purchased.isChecked = true
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LegendsArceusPokemonMoveShopEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                bytes to LegendsArceusPokemonMoveShopEditing.read(bytes, area!!, pasture, position)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, read) ->
                    if (read.offerings.isEmpty()) {
                        status.text = "This PA8 species/form has no permitted native move-shop offerings."
                        return@onSuccess
                    }
                    opened = bytes
                    values = read
                    moveSpinner.adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        read.offerings
                    )
                    moveSpinner.setSelection(0)
                    status.text = describe(read) +
                        "\nSource SHA-256: ${LegendsArceusPokemonMoveShopEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The selected PA8 could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun describe(read: LegendsArceusPokemonMoveShopValues): String =
        "${LegendsArceusSpeciesNames.name(read.pokemon.species)} (#${read.pokemon.species}) • Form ${read.pokemon.form}\n" +
            "Nickname: ${read.pokemon.nickname.ifBlank { "None" }}\n" +
            "Permitted native move-shop offerings: ${read.offerings.size}"

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null && values?.offerings?.isNotEmpty() == true
        moveSpinner.isEnabled = !value && ready
        purchased.isEnabled = !value && ready
        purchasedMastered.isEnabled = !value && ready
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
        val requestedMastered = when {
            purchasedMastered.isChecked -> true
            purchased.isChecked -> false
            else -> {
                status.text = "Choose Purchased or Purchased and mastered before review."
                return
            }
        }
        val plan = try {
            LegendsArceusPokemonMoveShopEditing.stage(
                bytes, hash, selectedArea, pasture, position, selected.index, requestedMastered
            )
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested move-shop change is unavailable."
            return
        }
        setBusy(true)
        var dispatched = false
        val recordSize = if (plan.area == LegendsArceusPokemonArea.PASTURE)
            LegendsArceusPokemonReadOnly.STORED_SIZE else LegendsArceusPokemonReadOnly.PARTY_SIZE
        AlertDialog.Builder(this).setTitle("Save Move Shop state to a new copy?")
            .setMessage(
                "${plan.beforeSelected.moveName} (#${plan.beforeSelected.moveId})\n" +
                    "Purchased: ${label(plan.beforeSelected.purchased)} → Yes\n" +
                    "Mastered: ${label(plan.beforeSelected.mastered)} → ${label(plan.afterSelected.mastered)}\n\n" +
                    "Allowed native scope: Purchased bit ${plan.selectedIndex} and Mastered bit ${plan.selectedIndex} inside the selected $recordSize-byte encrypted PA8 at 0x${plan.absoluteOffset.toString(16).uppercase()}, plus its checksum and the native SHA-256 footer. " +
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
                        "Move Shop copy saved and reopened successfully.\n" +
                            "${plan.afterSelected.moveName} • purchased Yes • mastered ${label(plan.afterSelected.mastered)}\n" +
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

    private fun label(value: Boolean): String = if (value) "Yes" else "No"

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
