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

/** Gen 1-shaped Pokémon editor for one native Legends: Z-A PA9 Speed DV / IV. */
class SwitchLegendsZaPokemonSpeedDvActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2183
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: LegendsZaPokemonArea? = null
    private var box = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var current: LegendsZaPokemonRead? = null
    private var pendingPlan: LegendsZaSpeedDvCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var speedIv: EditText
    private lateinit var review: Button
    private lateinit var defenseDv: Button
    private lateinit var specialDv: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        area = intent?.getStringExtra("pse.switch.pokemon.area")?.let { value ->
            LegendsZaPokemonArea.values().firstOrNull { it.name == value }
        }
        box = intent?.getIntExtra("pse.switch.pokemon.box", 0) ?: 0
        position = intent?.getIntExtra("pse.switch.pokemon.position", 0) ?: 0
        val selectedArea = area
        if (sourceUri == null || expectedHash.isNullOrBlank() || selectedArea == null ||
            position !in 1..30 || (selectedArea == LegendsZaPokemonArea.BOX && box !in 1..32) ||
            (selectedArea == LegendsZaPokemonArea.PARTY && position !in 1..6)) {
            finish(); return
        }
        val location = if (selectedArea == LegendsZaPokemonArea.BOX)
            "Box $box • Slot $position" else "Party $position"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Legends: Z-A • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Edit Pokémon • Speed DV / IV"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "$location\nSpeed follows Defense in Gen 1's DV section. Legends: Z-A stores its native counterpart as a 0–31 Speed IV. PSE rebuilds the derived Speed stat, then saves a verified new copy."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Speed DV / IV"; textSize = 16f })
        speedIv = EditText(this).apply {
            hint = "0–31"; inputType = InputType.TYPE_CLASS_NUMBER; isEnabled = false; maxLines = 1
        }
        root.addView(speedIv)
        review = Button(this).apply {
            text = "Review Speed DV / IV change"; isEnabled = false
            setOnClickListener { reviewSpeedDv() }
        }
        root.addView(review)
        defenseDv = Button(this).apply {
            text = "Back to Defense DV / IV"; isEnabled = false
            setOnClickListener {
                val uri = sourceUri ?: return@setOnClickListener
                val hash = expectedHash ?: return@setOnClickListener
                val selected = area ?: return@setOnClickListener
                startActivity(Intent(this@SwitchLegendsZaPokemonSpeedDvActivity,
                    SwitchLegendsZaPokemonDefenseDvActivity::class.java)
                    .setData(uri).putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.area", selected.name)
                    .putExtra("pse.switch.pokemon.box", box)
                    .putExtra("pse.switch.pokemon.position", position)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        root.addView(defenseDv)
        specialDv = Button(this).apply {
            text = "Next: Special DV / IV"; isEnabled = false
            setOnClickListener {
                val uri = sourceUri ?: return@setOnClickListener
                val hash = expectedHash ?: return@setOnClickListener
                val selected = area ?: return@setOnClickListener
                startActivity(Intent(this@SwitchLegendsZaPokemonSpeedDvActivity,
                    SwitchLegendsZaPokemonSpecialDvActivity::class.java)
                    .setData(uri).putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.area", selected.name)
                    .putExtra("pse.switch.pokemon.box", box)
                    .putExtra("pse.switch.pokemon.position", position)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        root.addView(specialDv)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"; visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply {
            text = "Back to Pokémon"; setOnClickListener { if (!busy) finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LegendsZaPokemonSpeedDvEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                val validation = LegendsZaSaveValidation.inspect(bytes)
                require(validation is LegendsZaValidation.Verified) {
                    "A validated native Legends: Z-A save is required."
                }
                val storage = LegendsZaStorageReadOnly.read(bytes)
                val read = if (selectedArea == LegendsZaPokemonArea.BOX)
                    storage.box(box - 1)[position - 1].read else storage.party()[position - 1].read
                val pokemon = (read as? LegendsZaSlotRead.Pokemon)?.value
                    ?: error("The selected native PA9 is not checksum-valid.")
                require(!pokemon.isEgg) { "Egg Speed DV / IV editing is not supported for Legends: Z-A." }
                Triple(bytes, validation.version.displayName, pokemon)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, displayName, pokemon) ->
                    opened = bytes
                    current = pokemon
                    speedIv.setText(pokemon.ivs[3].toString())
                    val speedHyperTrained = pokemon.hyperTrainingFlags and (1 shl 5) != 0
                    status.text = "$displayName • Native integrity verified\n" +
                        "$location • ${pokemon.nickname.ifBlank { ScarletVioletSpeciesNames.name(pokemon.species) }}\n" +
                        "Current Speed IV: ${pokemon.ivs[3]} • Derived Speed: ${pokemon.cachedStats[2]}\n" +
                        "Hyper Training: ${if (speedHyperTrained) "Speed active" else "Not active for Speed"}\n" +
                        "Source SHA-256: ${LegendsZaPokemonSpeedDvEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The selected Pokémon could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null && current != null
        speedIv.isEnabled = !value && ready
        review.isEnabled = !value && ready
        defenseDv.isEnabled = !value && ready
        specialDv.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return LegendsZaPokemonSpeedDvEditing.readDocumentBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = LegendsZaPokemonSpeedDvEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun reviewSpeedDv() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        val requested = speedIv.text.toString().toIntOrNull()
        if (requested == null || requested !in 0..31) {
            status.text = "Speed IV must be a whole number between 0 and 31."
            return
        }
        setBusy(true)
        worker.execute {
            val result = runCatching {
                LegendsZaPokemonSpeedDvEditing.stage(
                    bytes, hash, selectedArea, box, position, requested
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { showReview(it) }
                    .onFailure {
                        status.text = it.message ?: "The Speed DV / IV could not be staged."
                        setBusy(false)
                    }
            }
        }
    }

    private fun showReview(plan: LegendsZaSpeedDvCopyPlan) {
        var dispatched = false
        val speedHyperTrained = plan.before.hyperTrainingFlags and (1 shl 5) != 0
        AlertDialog.Builder(this).setTitle("Save this Speed DV / IV to a new copy?")
            .setMessage(
                "${plan.location} • ${plan.before.nickname.ifBlank { ScarletVioletSpeciesNames.name(plan.before.species) }}\n" +
                    "Speed IV: ${plan.before.ivs[3]} → ${plan.after.speedIv}\n" +
                    "Derived Speed: ${plan.before.cachedStats[2]} → ${plan.after.speed}\n" +
                    "Speed Hyper Training: ${if (speedHyperTrained) "Active (effective IV remains 31)" else "Not active"}\n\n" +
                    "Allowed encrypted scope: one 344-byte PA9 slot at 0x${plan.absoluteOffset.toString(16).uppercase()} and the native SHA-256 footer. " +
                    "Inside the PA9, only the five Speed-IV bits, derived Speed stat, and checksum may change. Other IVs, Hyper Training, EVs, nature, HP and the other four battle stats, Alpha state, ball, scale, friendship, trainer identity, species, nickname, moves, the target slot footer, the unselected storage object, every other party and PC-box slot, and all unrelated bytes remain exact. " +
                    "PSE verifies a recovery backup and reopens the persisted copy. Choose a NEW empty document."
            )
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { if (!dispatched) setBusy(false) }
            .setPositiveButton("Choose new copy") { _, _ ->
                if (!dispatched && !isFinishing && !isDestroyed) {
                    dispatched = true; pendingPlan = plan; verifiedCopy = null
                    inspectCopy.visibility = android.view.View.GONE
                    try {
                        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            type = "application/octet-stream"; addCategory(Intent.CATEGORY_OPENABLE)
                            putExtra(Intent.EXTRA_TITLE, "main")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }, createCopyRequest)
                    } catch (failure: Exception) {
                        pendingPlan = null; setBusy(false)
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
            .putExtra("pse.switch.sha256", hash).putExtra("pse.switch.section", "storage")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != createCopyRequest) return
        val plan = pendingPlan
        pendingPlan = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || plan == null) {
            status.text = "Save cancelled or review expired. Nothing was written."; setBusy(false); return
        }
        if (uri.scheme != "content" || uri == sourceUri) {
            status.text = "Choose a new document separate from the source. Nothing was written."; setBusy(false); return
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
                            this, SwitchGameFamily.LEGENDS_ZA, original)
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.LEGENDS_ZA, backup.file, backup.sha256)
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
                inspectCopy.visibility = if (verifiedCopy != null)
                    android.view.View.VISIBLE else android.view.View.GONE
                status.text = result.fold(
                    onSuccess = {
                        "Speed DV / IV copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\n" +
                            "Recovery backup: $backupName\nOriginal source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The Speed DV / IV copy could not be verified.") +
                            "\nRecovery backup: ${backupName ?: "not created"}"
                    }
                )
                setBusy(false)
            }
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
