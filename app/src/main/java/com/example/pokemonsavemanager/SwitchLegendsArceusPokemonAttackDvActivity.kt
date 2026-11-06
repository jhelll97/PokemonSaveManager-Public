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

/** Gen 1-shaped Pokémon editor for one native Legends: Arceus PA8 Attack DV / IV. */
class SwitchLegendsArceusPokemonAttackDvActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2141
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: LegendsArceusPokemonArea? = null
    private var pasture = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var current: LegendsArceusPokemonRead? = null
    private var pendingPlan: LegendsArceusAttackDvCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var attackIv: EditText
    private lateinit var review: Button
    private lateinit var defenseDv: Button
    private lateinit var move4: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        area = intent?.getStringExtra("pse.switch.pokemon.area")?.let { value ->
            LegendsArceusPokemonArea.values().firstOrNull { it.name == value }
        }
        pasture = intent?.getIntExtra("pse.switch.pokemon.pasture", 0) ?: 0
        position = intent?.getIntExtra("pse.switch.pokemon.position", 0) ?: 0
        val selectedArea = area
        if (sourceUri == null || expectedHash.isNullOrBlank() || selectedArea == null ||
            position !in 1..30 || (selectedArea == LegendsArceusPokemonArea.PASTURE && pasture !in 1..32) ||
            (selectedArea == LegendsArceusPokemonArea.PARTY && position !in 1..6)) {
            finish(); return
        }
        val location = if (selectedArea == LegendsArceusPokemonArea.PASTURE)
            "Pasture $pasture • Slot $position" else "Party $position"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Legends: Arceus • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Edit Pokémon • Attack DV / IV"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "$location\nAttack is the first field in Gen 1's DV section. Legends: Arceus stores its native counterpart as a 0–31 Attack IV. Party PA8 records also store derived Attack; pasture PA8 records derive it when moved into the party."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Attack DV / IV"; textSize = 16f })
        attackIv = EditText(this).apply {
            hint = "0–31"; inputType = InputType.TYPE_CLASS_NUMBER; isEnabled = false; maxLines = 1
        }
        root.addView(attackIv)
        review = Button(this).apply {
            text = "Review Attack DV / IV change"; isEnabled = false
            setOnClickListener { reviewAttackDv() }
        }
        root.addView(review)
        defenseDv = Button(this).apply {
            text = "Next: Defense DV / IV"; isEnabled = false
            setOnClickListener {
                val uri = sourceUri ?: return@setOnClickListener
                val hash = expectedHash ?: return@setOnClickListener
                val selected = area ?: return@setOnClickListener
                startActivity(Intent(this@SwitchLegendsArceusPokemonAttackDvActivity,
                    SwitchLegendsArceusPokemonDefenseDvActivity::class.java)
                    .setData(uri).putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.area", selected.name)
                    .putExtra("pse.switch.pokemon.pasture", pasture)
                    .putExtra("pse.switch.pokemon.position", position)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        root.addView(defenseDv)
        move4 = Button(this).apply {
            text = "Back to Move 4"; isEnabled = false
            setOnClickListener {
                val uri = sourceUri ?: return@setOnClickListener
                val hash = expectedHash ?: return@setOnClickListener
                val selected = area ?: return@setOnClickListener
                startActivity(Intent(this@SwitchLegendsArceusPokemonAttackDvActivity,
                    SwitchLegendsArceusPokemonMove4Activity::class.java)
                    .setData(uri).putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.area", selected.name)
                    .putExtra("pse.switch.pokemon.pasture", pasture)
                    .putExtra("pse.switch.pokemon.position", position)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        root.addView(move4)
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
                require(LegendsArceusPokemonAttackDvEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                val validation = LegendsArceusSaveValidation.inspect(bytes)
                require(validation is LegendsArceusValidation.Verified) {
                    "A validated native Legends: Arceus save is required."
                }
                val storage = LegendsArceusStorageReadOnly.read(bytes)
                val read = if (selectedArea == LegendsArceusPokemonArea.PASTURE)
                    storage.pasture(pasture - 1)[position - 1].read else storage.party()[position - 1].read
                val pokemon = (read as? LegendsArceusSlotRead.Pokemon)?.value
                    ?: error("The selected native PA8 is not checksum-valid.")
                require(!pokemon.isEgg) { "Egg Attack DV / IV editing is not supported for Legends: Arceus." }
                Triple(bytes, validation.version.displayName, pokemon)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, displayName, pokemon) ->
                    opened = bytes
                    current = pokemon
                    attackIv.setText(pokemon.ivs[1].toString())
                    val attackHyperTrained = pokemon.hyperTrainingFlags and (1 shl 1) != 0
                    val attackSummary = pokemon.cachedStats?.let { "Derived Attack: ${it[0]}" }
                        ?: "Pasture PA8: no cached Attack tail; the game derives it in party."
                    status.text = "$displayName • Native integrity verified\n" +
                        "$location • ${pokemon.nickname.ifBlank { LegendsArceusSpeciesNames.name(pokemon.species) }}\n" +
                        "Current Attack IV: ${pokemon.ivs[1]} • $attackSummary\n" +
                        "Hyper Training: ${if (attackHyperTrained) "Attack active" else "Not active for Attack"}\n" +
                        "Source SHA-256: ${LegendsArceusPokemonAttackDvEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The selected Pokémon could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null && current != null
        attackIv.isEnabled = !value && ready
        review.isEnabled = !value && ready
        defenseDv.isEnabled = !value && ready
        move4.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return LegendsArceusPokemonAttackDvEditing.readDocumentBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = LegendsArceusPokemonAttackDvEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun reviewAttackDv() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        val requested = attackIv.text.toString().toIntOrNull()
        if (requested == null || requested !in 0..31) {
            status.text = "Attack IV must be a whole number between 0 and 31."
            return
        }
        setBusy(true)
        worker.execute {
            val result = runCatching {
                LegendsArceusPokemonAttackDvEditing.stage(
                    bytes, hash, selectedArea, pasture, position, requested
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { showReview(it) }
                    .onFailure {
                        status.text = it.message ?: "The Attack DV / IV could not be staged."
                        setBusy(false)
                    }
            }
        }
    }

    private fun showReview(plan: LegendsArceusAttackDvCopyPlan) {
        var dispatched = false
        val attackHyperTrained = plan.before.hyperTrainingFlags and (1 shl 1) != 0
        val attackReview = plan.after.attack?.let {
            "Derived Attack: ${plan.before.cachedStats?.get(0)} → $it\n"
        } ?: "Pasture PA8: no cached Attack tail; only the native Attack IV is stored.\n"
        val nativeScope = if (plan.after.attack == null)
            "Inside the pasture PA8, only the five Attack-IV bits and checksum may change."
        else "Inside the party PA8, only the five Attack-IV bits, derived Attack stat, and checksum may change."
        AlertDialog.Builder(this).setTitle("Save this Attack DV / IV to a new copy?")
            .setMessage(
                "${plan.location} • ${plan.before.nickname.ifBlank { LegendsArceusSpeciesNames.name(plan.before.species) }}\n" +
                    "Attack IV: ${plan.before.ivs[1]} → ${plan.after.attackIv}\n" +
                    attackReview +
                    "Attack Hyper Training: ${if (attackHyperTrained) "Active (effective IV remains 31)" else "Not active"}\n\n" +
                    "Allowed encrypted scope: one ${plan.recordSize}-byte PA8 slot at 0x${plan.absoluteOffset.toString(16).uppercase()} and the native SHA-256 footer. " +
                    "$nativeScope Other IVs, Hyper Training, Ganbaru values, nature, HP and the other four battle stats, Alpha and Noble state, friendship, trainer identity, species, nickname, moves, the unselected storage object, every other party and pasture slot, and all unrelated bytes remain exact. " +
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
            .putExtra("pse.switch.family", SwitchGameFamily.LEGENDS_ARCEUS.name)
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
                            this, SwitchGameFamily.LEGENDS_ARCEUS, original)
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.LEGENDS_ARCEUS, backup.file, backup.sha256)
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
                        "Attack DV / IV copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\n" +
                            "Recovery backup: $backupName\nOriginal source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The Attack DV / IV copy could not be verified.") +
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
