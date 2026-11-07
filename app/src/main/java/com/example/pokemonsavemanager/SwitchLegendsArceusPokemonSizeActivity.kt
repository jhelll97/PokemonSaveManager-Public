package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Native PA8 size editor with reviewed new-copy persistence. */
class SwitchLegendsArceusPokemonSizeActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2167
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: LegendsArceusPokemonArea? = null
    private var pasture = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var pendingPlan: LegendsArceusPokemonSizeCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var heightInput: EditText
    private lateinit var weightInput: EditText
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
        root.addView(TextView(this).apply { text = "Legends: Arceus • Size"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "Edit the selected PA8's native Height Scalar and Weight Scalar. Display Scale is kept equal to Height Scalar, as required by the native format."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source and PA8…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Height Scalar (0–255)"; textSize = 16f })
        heightInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "0–255"
        }
        root.addView(heightInput)
        root.addView(TextView(this).apply { text = "Weight Scalar (0–255)"; textSize = 16f })
        weightInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "0–255"
        }
        root.addView(weightInput)
        root.addView(TextView(this).apply {
            text = "Alpha, Noble and Egg records remain protected. PSE validates the native Height/Scale relationship but does not claim encounter legality for a new non-Alpha size."
            textSize = 14f
        })
        root.addView(TextView(this).apply {
            text = "PSE changes only Height Scalar, Weight Scalar, display Scale, the PA8 checksum, the selected encrypted record, and the required save footer. Every other Pokémon and save value remains exact."
            textSize = 14f
        })
        review = Button(this).apply {
            text = "Review and save new copy"
            setOnClickListener { reviewChange() }
        }
        root.addView(review)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"; visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply {
            text = "Back to Pokémon Details"
            setOnClickListener { if (!busy) finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LegendsArceusPokemonSizeEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                bytes to LegendsArceusPokemonSizeEditing.read(bytes, area!!, pasture, position)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, values) ->
                    opened = bytes
                    heightInput.setText(values.heightScalar.toString())
                    weightInput.setText(values.weightScalar.toString())
                    status.text = describe(values) +
                        "\nSource SHA-256: ${LegendsArceusPokemonSizeEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The selected PA8 could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun describe(values: LegendsArceusPokemonSizeValues): String =
        "${LegendsArceusSpeciesNames.name(values.pokemon.species)} (#${values.pokemon.species}) • Form ${values.pokemon.form}\n" +
            "Nickname: ${values.pokemon.nickname.ifBlank { "None" }}\n" +
            "Height Scalar: ${values.heightScalar}\nWeight Scalar: ${values.weightScalar}\n" +
            "Display Scale: ${values.displayScale}"

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null
        heightInput.isEnabled = !value && ready
        weightInput.isEnabled = !value && ready
        review.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        val requestedHeight = heightInput.text.toString().toIntOrNull()
        val requestedWeight = weightInput.text.toString().toIntOrNull()
        if (requestedHeight == null || requestedWeight == null) {
            status.text = "Enter both native scalar values as whole numbers from 0 through 255."
            return
        }
        val plan = try {
            LegendsArceusPokemonSizeEditing.stage(
                bytes, hash, selectedArea, pasture, position, requestedHeight, requestedWeight
            )
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested size change is unavailable."
            return
        }
        setBusy(true)
        var dispatched = false
        val recordSize = if (plan.area == LegendsArceusPokemonArea.PASTURE)
            LegendsArceusPokemonReadOnly.STORED_SIZE else LegendsArceusPokemonReadOnly.PARTY_SIZE
        AlertDialog.Builder(this).setTitle("Save native size to a new copy?")
            .setMessage(
                "${LegendsArceusSpeciesNames.name(plan.before.pokemon.species)}\n" +
                    "Height Scalar: ${plan.before.heightScalar} → ${plan.after.heightScalar}\n" +
                    "Weight Scalar: ${plan.before.weightScalar} → ${plan.after.weightScalar}\n" +
                    "Display Scale: ${plan.before.displayScale} → ${plan.after.displayScale}\n\n" +
                    "Allowed native scope: the selected $recordSize-byte encrypted PA8 at 0x${plan.absoluteOffset.toString(16).uppercase()} plus the native SHA-256 footer. " +
                    "Inside PA8, only the three size bytes and checksum change. Every other Pokémon and save value remains exact. " +
                    "PSE will verify a recovery backup and reopen the persisted copy. Choose a NEW empty document; the selected source cannot be replaced."
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
                inspectCopy.visibility = if (verifiedCopy != null) android.view.View.VISIBLE else android.view.View.GONE
                status.text = result.fold(
                    onSuccess = {
                        "Size copy saved and reopened successfully.\n${describe(plan.after)}\n" +
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
        return LegendsArceusPokemonSizeEditing.readDocumentBytes(input)
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
